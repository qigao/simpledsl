# SimpleDSL Gradle Plugin Distribution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the reusable Gradle build platform from `qigao/durex` into `qigao/simpledsl`, rename the product surface to SimpleDSL, convert public convention scripts into binary plugins, and prove that a completely independent consumer can resolve the published artifacts exactly as it will from the Gradle Plugin Portal.

**Architecture:** `simpledsl-build-bootstrap` owns the settings-time manifest/discovery service and `io.github.qigao.simpledsl.settings`; `simpledsl-build-logic` owns project/module/feature/schema plugins. The two artifacts share one release version but communicate at runtime through the existing versioned snapshot/reflection boundary (`simpledslDependencyRegistry`) rather than Java type casts across Gradle plugin classloaders. The SimpleDSL repository is built with ordinary Gradle configuration and never bootstraps itself through SimpleDSL.

**Tech Stack:** Gradle 9.1.0 wrapper, Java 25 build JDK, Java 17 plugin bytecode target, Groovy plugin implementation, Gradle TestKit, `com.gradle.plugin-publish` 2.1.1, Tomlj 1.1.1, Spring Boot Gradle plugin 4.1.0, GraalVM Native Gradle plugin 1.1.1, jOOQ Gradle plugin 3.21.5, jsonschema2pojo Gradle plugin 1.3.3, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-21-durex-gradle-plugin-distribution-design.md`

## Global Constraints

- Public product name is `SimpleDSL`; new public code and metadata must not use Durex branding.
- Public plugin IDs use `io.github.qigao.simpledsl.*`.
- Maven group is `io.github.qigao.simpledsl`.
- Implementation packages use `io.github.qigao.simpledsl.gradle.*`.
- Public DSL names are `simpledslSettings` and `simpledsl`.
- Public diagnostic tasks are `simpledslProjects`, `simpledslDependencies`, `simpledslCapabilities`, and `simpledslDoctor`.
- Consumer manifest defaults live under `gradle/simpledsl/`.
- `simpledsl-build-bootstrap` and `simpledsl-build-logic` always publish the same release version.
- No `durex.*` compatibility plugin markers are published from `simpledsl`.
- The plugin repository must not use `includeBuild` or SimpleDSL itself to build SimpleDSL.
- The consumer fixture must not use `includeBuild` or direct project dependencies on the plugin modules.
- SimpleDSL-owned Gradle plugin implementation versions are fixed by the SimpleDSL release, not by the consumer manifest.
- Preserve dependency snapshot schema version `1` during the extraction.
- Gradle wrapper baseline is `9.1.0`; CI runs on Java 25; plugin implementation bytecode targets Java 17.
- Initial public release target is `0.1.0`; development builds use `0.1.0-SNAPSHOT` unless `-PreleaseVersion=` is supplied.
- Do not cut over or delete `qigao/durex/build-bootstrap` or `qigao/durex/build-logic` in this plan.

---

### Task 1: Create the standalone SimpleDSL product build

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create via standard wrapper generation: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`
- Create: `simpledsl-build-bootstrap/build.gradle.kts`
- Create: `simpledsl-build-logic/build.gradle.kts`
- Create: `integration-tests/build.gradle.kts`
- Create: `scripts/verify-product-namespace.sh`

**Interfaces:**
- Consumes: no SimpleDSL plugin; this task uses only standard Gradle configuration.
- Produces: projects `:simpledsl-build-bootstrap`, `:simpledsl-build-logic`, and `:integration-tests`; shared group/version; a fixed implementation dependency catalog; namespace guard task `verifyProductNamespace`.

- [ ] **Step 1: Add the namespace guard before any migrated product source exists**

Create `scripts/verify-product-namespace.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

roots=(
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  gradle
  simpledsl-build-bootstrap
  simpledsl-build-logic
  integration-tests
  README.md
)

existing=()
for path in "${roots[@]}"; do
  [[ -e "$path" ]] && existing+=("$path")
done

if ((${#existing[@]} > 0)); then
  if grep -RInE 'com\.github\.durex|\bdurex\.(settings|module|feature|schema|java-library|spring-library|spring-service)|Durex(Configuration|Extension|Module|Capability|Doctor|Settings|Dependency|Registry|Json|Jooq)' "${existing[@]}"; then
    echo 'legacy Durex product namespace leaked into SimpleDSL product files' >&2
    exit 1
  fi
fi
```

Do not scan `docs/superpowers/specs` because the migration map intentionally names the source Durex APIs.

- [ ] **Step 2: Create the root build skeleton**

Create `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "simpledsl"
include("simpledsl-build-bootstrap", "simpledsl-build-logic", "integration-tests")
```

Create `gradle.properties`:

```properties
org.gradle.configuration-cache=true
org.gradle.parallel=true
org.gradle.caching=true
simpledslVersion=0.1.0-SNAPSHOT
```

Create root `build.gradle.kts`:

```kotlin
plugins {
    base
}

val releaseVersion = providers.gradleProperty("releaseVersion")
val developmentVersion = providers.gradleProperty("simpledslVersion")

allprojects {
    group = "io.github.qigao.simpledsl"
    version = releaseVersion.orElse(developmentVersion).get()
}

tasks.register<Exec>("verifyProductNamespace") {
    commandLine("bash", "scripts/verify-product-namespace.sh")
}

tasks.named("check") {
    dependsOn("verifyProductNamespace")
}
```

- [ ] **Step 3: Create the fixed product dependency catalog**

Create `gradle/libs.versions.toml`:

```toml
[versions]
plugin-publish = "2.1.1"
tomlj = "1.1.1"
spring-boot = "4.1.0"
graalvm-native = "1.1.1"
jooq = "3.21.5"
jsonschema2pojo = "1.3.3"
spock = "2.4-M6-groovy-4.0"

[libraries]
tomlj = { module = "org.tomlj:tomlj", version.ref = "tomlj" }
spring-boot-gradle = { module = "org.springframework.boot:spring-boot-gradle-plugin", version.ref = "spring-boot" }
graalvm-native-gradle = { module = "org.graalvm.buildtools:native-gradle-plugin", version.ref = "graalvm-native" }
jooq-codegen-gradle = { module = "org.jooq:jooq-codegen-gradle", version.ref = "jooq" }
jooq-core = { module = "org.jooq:jooq", version.ref = "jooq" }
jooq-meta = { module = "org.jooq:jooq-meta", version.ref = "jooq" }
jsonschema2pojo-gradle = { module = "org.jsonschema2pojo:jsonschema2pojo-gradle-plugin", version.ref = "jsonschema2pojo" }
spock-core = { module = "org.spockframework:spock-core", version.ref = "spock" }

[plugins]
plugin-publish = { id = "com.gradle.plugin-publish", version.ref = "plugin-publish" }
```

- [ ] **Step 4: Configure the three subprojects without applying SimpleDSL**

Use this base in both plugin module build files:

```kotlin
plugins {
    groovy
    alias(libs.plugins.plugin.publish)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
}

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(libs.spock.core)
}

tasks.test {
    useJUnitPlatform()
}
```

`simpledsl-build-bootstrap` additionally declares `implementation(libs.tomlj)`.

`simpledsl-build-logic` additionally declares:

```kotlin
dependencies {
    implementation(libs.spring.boot.gradle)
    implementation(libs.graalvm.native.gradle)
    implementation(libs.jooq.codegen.gradle)
    implementation(libs.jooq.core)
    implementation(libs.jooq.meta)
    implementation(libs.jsonschema2pojo.gradle)
}
```

Create `integration-tests/build.gradle.kts` with only `groovy`, `gradleTestKit()`, and Spock; it must not depend on either plugin project as a production dependency.

- [ ] **Step 5: Generate the Gradle 9.1.0 wrapper through Gradle**

Run:

```bash
gradle wrapper --gradle-version 9.1.0 --distribution-type bin
./gradlew --version
```

Expected: output contains `Gradle 9.1.0`.

- [ ] **Step 6: Verify the clean product build skeleton**

Run:

```bash
./gradlew projects verifyProductNamespace --stacktrace
```

Expected: the three subprojects are listed and `verifyProductNamespace` passes.

- [ ] **Step 7: Commit**

```bash
git add -- settings.gradle.kts build.gradle.kts gradle.properties gradle simpledsl-build-bootstrap simpledsl-build-logic integration-tests scripts gradlew gradlew.bat
git commit -m "build: bootstrap standalone SimpleDSL plugin build"
```

---

### Task 2: Extract the settings/bootstrap product and distribution version contract

**Files:**
- Create under `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest/`: `DependencyManifestLoader.groovy`, `DependencyRegistry.groovy`, `DependencyRegistryService.groovy`, `LibrarySpec.groovy`, `PlatformSpec.groovy`, `PluginSpec.groovy`, `VersionSpec.groovy`
- Create under `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/settings/`: `SimpleDslSettingsPlugin.groovy`, `SimpleDslSettingsExtension.groovy`, `SimpleDslDependenciesTask.groovy`, `SimpleDslProjectsTask.groovy`, `ProjectDiscovery.groovy`, `ProjectRegistry.groovy`, `ProjectSpec.groovy`
- Create: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/distribution/SimpleDslDistribution.groovy`
- Create: `simpledsl-build-bootstrap/src/main/resources/META-INF/simpledsl/distribution.properties`
- Modify: `simpledsl-build-bootstrap/build.gradle.kts`
- Test: `simpledsl-build-bootstrap/src/test/groovy/io/github/qigao/simpledsl/gradle/settings/SimpleDslSettingsPluginSpec.groovy`
- Test: `simpledsl-build-bootstrap/src/test/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyManifestLoaderSpec.groovy`

**Interfaces:**
- Consumes: consumer root manifest `gradle/simpledsl/dependencies.toml` and optional `gradle/simpledsl/modules.toml`.
- Produces: shared service name `simpledslDependencyRegistry`; snapshot map with `schemaVersion = 1`; settings extension `simpledslSettings`; tasks `simpledslDependencies` and `simpledslProjects`; plugin ID `io.github.qigao.simpledsl.settings`; distribution version available as `SimpleDslDistribution.version()`.

- [ ] **Step 1: Write failing manifest and settings tests**

Add a manifest unit test that loads:

```toml
include = ["spring.toml"]
[java]
version = 25
```

and asserts `registry.javaVersion() == 25` and the included platform/library entries are visible.

Add a TestKit settings test that writes:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.settings'
}
rootProject.name = 'consumer'
```

plus `gradle/simpledsl/dependencies.toml` and a leaf module, then runs:

```groovy
GradleRunner.create()
    .withProjectDir(testProjectDir)
    .withArguments('simpledslDependencies', 'simpledslProjects', '--stacktrace')
    .withPluginClasspath()
    .build()
```

Assertions must include `Java: 25` and the discovered module path.

- [ ] **Step 2: Run the tests to verify RED**

Run:

```bash
./gradlew :simpledsl-build-bootstrap:test --tests '*DependencyManifestLoaderSpec' --tests '*SimpleDslSettingsPluginSpec'
```

Expected: FAIL because the SimpleDSL classes/plugin do not exist.

- [ ] **Step 3: Port manifest parsing with package and diagnostic renaming**

Copy the reusable implementation from `qigao/durex/build-bootstrap/src/main/groovy/com/github/durex/gradle/manifest` and change only product-specific naming in the first pass:

```groovy
package io.github.qigao.simpledsl.gradle.manifest
```

All validation errors begin with `SimpleDSL dependency manifest error`, never `Durex dependency manifest error`.

Keep the snapshot structure and schema version exactly:

```groovy
[
    schemaVersion: 1,
    javaVersion: javaVersion(),
    platforms: ...,
    libraries: ...,
    plugins: ...
]
```

- [ ] **Step 4: Port project discovery and settings tasks**

Rename the public pieces:

```text
DurexSettingsPlugin    -> SimpleDslSettingsPlugin
DurexSettingsExtension -> SimpleDslSettingsExtension
DurexDependenciesTask  -> SimpleDslDependenciesTask
DurexProjectsTask      -> SimpleDslProjectsTask
```

Use these defaults:

```groovy
extension.dependencyManifest.convention(
    extension.repositoryRoot.file('gradle/simpledsl/dependencies.toml'))
extension.modulesManifest.convention(
    extension.repositoryRoot.file('gradle/simpledsl/modules.toml'))
extension.moduleDiscovery.convention(true)
```

Register the shared service as:

```groovy
settings.gradle.sharedServices.registerIfAbsent(
    'simpledslDependencyRegistry', DependencyRegistryService
) { spec ->
    spec.parameters.manifestFile.set(extension.dependencyManifest)
}
```

Register the tasks with group `SimpleDSL` and names `simpledslDependencies` / `simpledslProjects`.

Do **not** port `DurexBuildLogicPlugin`, `DurexBuildLogicSettingsPlugin`, or `DurexBuildLogicSettingsExtension`; those exist only for the old self-hosting cycle.

- [ ] **Step 5: Add immutable distribution metadata and SimpleDSL release coherence**

Create `SimpleDslDistribution.groovy`:

```groovy
package io.github.qigao.simpledsl.gradle.distribution

final class SimpleDslDistribution {
    static final String GROUP = 'io.github.qigao.simpledsl'
    static final String BUILD_LOGIC_ARTIFACT = 'simpledsl-build-logic'
    static final String PUBLIC_PREFIX = 'io.github.qigao.simpledsl.'
    static final String SETTINGS_PLUGIN_ID = 'io.github.qigao.simpledsl.settings'

    static String version() {
        def stream = SimpleDslDistribution.getResourceAsStream('/META-INF/simpledsl/distribution.properties')
        if (stream == null) throw new IllegalStateException('SimpleDSL distribution metadata is missing')
        Properties properties = new Properties()
        stream.withCloseable { properties.load(it) }
        String value = properties.getProperty('version')
        if (!value) throw new IllegalStateException('SimpleDSL distribution version is missing')
        value
    }

    private SimpleDslDistribution() {}
}
```

Generate the properties file from the project version during `processResources`; the generated file content must be:

```properties
version=0.1.0-SNAPSHOT
```

for a normal development build and the exact `-PreleaseVersion` for a release build.

In `SimpleDslSettingsPlugin`, register a plugin resolution strategy before project plugin requests are evaluated:

```groovy
String distributionVersion = SimpleDslDistribution.version()
settings.pluginManagement.resolutionStrategy.eachPlugin { details ->
    String id = details.requested.id.id
    if (id.startsWith(SimpleDslDistribution.PUBLIC_PREFIX) &&
            id != SimpleDslDistribution.SETTINGS_PLUGIN_ID) {
        String requested = details.requested.version
        if (requested && requested != distributionVersion) {
            throw new GradleException(
                "SimpleDSL version conflict\nPlugin: ${id}\nRequested: ${requested}\nManaged: ${distributionVersion}")
        }
        details.useModule(
            "${SimpleDslDistribution.GROUP}:${SimpleDslDistribution.BUILD_LOGIC_ARTIFACT}:${distributionVersion}")
        return
    }

    def provider = serviceHolder.provider
    if (provider == null) return
    def managed = provider.get().pluginByGradleId(id)
    if (managed != null) {
        details.useModule(managed.coordinate())
    }
}
```

Preserve manifest-managed external plugin resolution for consumer-owned plugins, but SimpleDSL public plugin IDs always take precedence.

- [ ] **Step 6: Register the settings plugin as a binary public plugin**

Configure `simpledsl-build-bootstrap/build.gradle.kts`:

```kotlin
gradlePlugin {
    website = "https://github.com/qigao/simpledsl"
    vcsUrl = "https://github.com/qigao/simpledsl.git"
    plugins {
        register("simpleDslSettings") {
            id = "io.github.qigao.simpledsl.settings"
            implementationClass = "io.github.qigao.simpledsl.gradle.settings.SimpleDslSettingsPlugin"
            displayName = "SimpleDSL Settings"
            description = "SimpleDSL dependency manifest and module discovery settings plugin"
            tags.set(listOf("gradle", "build-platform", "module-discovery"))
        }
    }
}
```

- [ ] **Step 7: Verify GREEN and namespace guard**

Run:

```bash
./gradlew :simpledsl-build-bootstrap:test :simpledsl-build-bootstrap:validatePlugins verifyProductNamespace --stacktrace
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add -- simpledsl-build-bootstrap scripts
git commit -m "feat: extract SimpleDSL settings bootstrap"
```

---

### Task 3: Extract the module model, snapshot bridge, dependency API, and diagnostics

**Files:**
- Create under `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/`: `SimpleDslConfigurationException.groovy`, `SimpleDslDependencyAccess.groovy`, `SimpleDslExtension.groovy`, `SimpleDslModulePlugin.groovy`, `ModuleKind.groovy`, `SimpleDslPersistenceExtension.groovy`
- Create/migrate packages: `capability/**`, `catalog/**`, `dependency/**`, `diagnostics/**`, `model/**`
- Rename: `DurexRegistryBridge` -> `SimpleDslRegistryBridge`, `DurexModuleModel` -> `SimpleDslModuleModel`, `DurexCapabilitySupport` -> `SimpleDslCapabilitySupport`, `DurexCapabilitiesTask` -> `SimpleDslCapabilitiesTask`, `DurexDoctorTask` -> `SimpleDslDoctorTask`, `DurexDoctorValidator` -> `SimpleDslDoctorValidator`
- Modify: `simpledsl-build-logic/build.gradle.kts`
- Test: `simpledsl-build-logic/src/test/groovy/io/github/qigao/simpledsl/gradle/catalog/SimpleDslRegistryBridgeSpec.groovy`
- Test: `simpledsl-build-logic/src/test/groovy/io/github/qigao/simpledsl/gradle/SimpleDslModulePluginSpec.groovy`

**Interfaces:**
- Consumes: shared service `simpledslDependencyRegistry` with reflective `snapshot()` method returning schema version `1`.
- Produces: `DependencyCatalogSnapshot`, `SimpleDslModuleModel`, extension `simpledsl`, capability registry/engine, tasks `simpledslCapabilities` and `simpledslDoctor`, plugin ID `io.github.qigao.simpledsl.module`.

- [ ] **Step 1: Write failing snapshot bridge tests**

Cover a positive schema-1 snapshot and this negative snapshot:

```groovy
[
    schemaVersion: 99,
    javaVersion: 25,
    platforms: [:],
    libraries: [:],
    plugins: [:]
]
```

Expected error text:

```text
SimpleDSL bootstrap error
Problem: unsupported dependency snapshot schema
Expected: 1
Actual: 99
```

- [ ] **Step 2: Run RED**

```bash
./gradlew :simpledsl-build-logic:test --tests '*SimpleDslRegistryBridgeSpec'
```

Expected: FAIL because the bridge has not been migrated.

- [ ] **Step 3: Port the core model with SimpleDSL naming**

Port the existing capability/catalog/dependency/model logic without changing semantics. Replace package roots with `io.github.qigao.simpledsl.gradle` and all user-facing error prefixes with `SimpleDSL`.

`SimpleDslRegistryBridge.fromProject(Project)` must look up exactly:

```groovy
def registration = project.gradle.sharedServices.registrations
    .findByName('simpledslDependencyRegistry')
```

Continue to invoke `snapshot()` reflectively. Do not cast the service to a bootstrap module type.

- [ ] **Step 4: Port `SimpleDslModulePlugin`**

The binary plugin must create:

```groovy
SimpleDslModuleModel model = project.extensions.create('simpledslModuleModel', SimpleDslModuleModel)
project.extensions.create('simpledsl', SimpleDslExtension, project, model)
```

and tasks:

```text
simpledslCapabilities
simpledslDoctor
```

The task group is `SimpleDSL`.

- [ ] **Step 5: Register the module plugin**

Add to the build-logic `gradlePlugin` block:

```kotlin
register("simpleDslModule") {
    id = "io.github.qigao.simpledsl.module"
    implementationClass = "io.github.qigao.simpledsl.gradle.SimpleDslModulePlugin"
    displayName = "SimpleDSL Module"
    description = "SimpleDSL module model, capability engine, dependency API, and diagnostics"
    tags.set(listOf("gradle", "build-platform", "dependencies"))
}
```

- [ ] **Step 6: Verify unit/model behavior**

Run:

```bash
./gradlew :simpledsl-build-logic:test :simpledsl-build-logic:validatePlugins verifyProductNamespace --stacktrace
```

Expected: PASS, including the schema mismatch error test.

- [ ] **Step 7: Commit**

```bash
git add -- simpledsl-build-logic
git commit -m "feat: extract SimpleDSL module kernel"
```

---

### Task 4: Replace module-type precompiled scripts with binary plugins

**Files:**
- Create: `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/internal/SimpleDslJavaBasePlugin.groovy`
- Create: `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/internal/SimpleDslSpringBasePlugin.groovy`
- Create: `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/module/SimpleDslJavaLibraryPlugin.groovy`
- Create: `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/module/SimpleDslSpringLibraryPlugin.groovy`
- Create: `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/module/SimpleDslSpringServicePlugin.groovy`
- Modify: `simpledsl-build-logic/build.gradle.kts`
- Test: `simpledsl-build-logic/src/test/groovy/io/github/qigao/simpledsl/gradle/module/ModuleTypePluginsSpec.groovy`

**Interfaces:**
- Consumes: `SimpleDslModuleModel`, `SimpleDslDependencyAccess`, consumer snapshot aliases such as `spring-core`, `spring-test`, `junit-jupiter`.
- Produces: public plugin IDs `io.github.qigao.simpledsl.java-library`, `.spring-library`, `.spring-service`; internal binary composition without public internal markers.

- [ ] **Step 1: Write failing module-type tests**

Use `ProjectBuilder` with a seeded `DependencyCatalogSnapshot` extension and assert:

```text
java-library   -> ModuleKind.JAVA_LIBRARY
spring-library -> ModuleKind.SPRING_LIBRARY
spring-service -> ModuleKind.SPRING_SERVICE
```

Also assert the Java toolchain/release is taken from `catalog.javaVersion()` and tests use JUnit Platform.

- [ ] **Step 2: Run RED**

```bash
./gradlew :simpledsl-build-logic:test --tests '*ModuleTypePluginsSpec'
```

Expected: FAIL because the binary module-type plugins do not exist.

- [ ] **Step 3: Implement internal Java/Spring base classes**

`SimpleDslJavaBasePlugin` applies `java` and `io.github.qigao.simpledsl.module`, then configures:

```groovy
int javaVersion = SimpleDslDependencyAccess.javaVersion(project)
project.extensions.getByType(JavaPluginExtension).toolchain.languageVersion
    .set(JavaLanguageVersion.of(javaVersion))
project.tasks.withType(JavaCompile).configureEach {
    options.release.set(javaVersion)
    options.encoding = 'UTF-8'
}
```

`SimpleDslSpringBasePlugin` applies the Java base and activates the `spring` platform on every existing `implementation`, `api`, and `testImplementation` configuration.

These are implementation classes only; do not publish `io.github.qigao.simpledsl.internal.*` markers unless Gradle composition later proves a marker is technically required.

- [ ] **Step 4: Implement the three public module plugins**

For example, `SimpleDslSpringServicePlugin.apply(Project project)` performs the binary equivalent of the old script:

```groovy
project.pluginManager.apply('org.springframework.boot')
new SimpleDslSpringBasePlugin().apply(project)
SimpleDslModuleModel model = project.extensions.getByType(SimpleDslModuleModel)
model.claim(ModuleKind.SPRING_SERVICE, project.path)
SimpleDslDependencyAccess.add(project, model, 'implementation', 'spring-core')
SimpleDslDependencyAccess.add(project, model, 'testImplementation', 'spring-test')
SimpleDslDependencyAccess.add(project, model, 'testRuntimeOnly', 'junit-platform-launcher')
project.tasks.withType(Test).configureEach { useJUnitPlatform() }
```

Use the equivalent old semantics for Java and Spring libraries.

- [ ] **Step 5: Register the three public plugin markers**

Register exactly these IDs in `gradlePlugin.plugins`:

```text
io.github.qigao.simpledsl.java-library
io.github.qigao.simpledsl.spring-library
io.github.qigao.simpledsl.spring-service
```

Each entry has an implementation class, English display name, English description, and tags.

- [ ] **Step 6: Verify GREEN**

```bash
./gradlew :simpledsl-build-logic:test :simpledsl-build-logic:validatePlugins verifyProductNamespace
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add -- simpledsl-build-logic
git commit -m "refactor: expose SimpleDSL module types as binary plugins"
```

---

### Task 5: Replace feature precompiled scripts and remove consumer control of implementation plugin versions

**Files:**
- Modify: `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/capability/CapabilitySpec.groovy`
- Modify: `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/capability/CapabilityEngine.groovy`
- Modify: `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/capability/BuiltinCapabilities.groovy`
- Create under `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/feature/`: `SimpleDslAopPlugin.groovy`, `SimpleDslTransactionPlugin.groovy`, `SimpleDslWebPlugin.groovy`, `SimpleDslHttpClientPlugin.groovy`, `SimpleDslMessagingPlugin.groovy`, `SimpleDslJdbcPlugin.groovy`, `SimpleDslJooqPlugin.groovy`, `SimpleDslJpaPlugin.groovy`, `SimpleDslRedisPlugin.groovy`, `SimpleDslNativePlugin.groovy`, `SimpleDslLombokPlugin.groovy`
- Modify: `simpledsl-build-logic/build.gradle.kts`
- Test: `simpledsl-build-logic/src/test/groovy/io/github/qigao/simpledsl/gradle/feature/FeaturePluginsSpec.groovy`

**Interfaces:**
- Consumes: capability engine and fixed external plugin implementations already on the build-logic artifact runtime classpath.
- Produces: eleven public `io.github.qigao.simpledsl.feature.*` binary plugins.

- [ ] **Step 1: Write failing capability tests for fixed external plugin IDs**

Change the target capability model from aliases to exact IDs. The native capability expectation is:

```groovy
assert BuiltinCapabilities.NATIVE.externalPluginIds == ['org.graalvm.buildtools.native'] as Set
```

Also assert applying native to a Java library fails with `SimpleDSL configuration error` and applying web to a Spring service adds `spring-webmvc` and `spring-validation` bindings.

- [ ] **Step 2: Run RED**

```bash
./gradlew :simpledsl-build-logic:test --tests '*FeaturePluginsSpec'
```

Expected: FAIL because the binary feature classes and `externalPluginIds` model are missing.

- [ ] **Step 3: Make external plugin ownership explicit**

Rename `CapabilitySpec.externalPluginAliases` to `externalPluginIds` and the builder method to:

```groovy
Builder externalPluginId(String pluginId) {
    externalPluginIds.add(pluginId)
    this
}
```

Change `CapabilityEngine` from consumer-catalog lookup:

```groovy
def plugin = catalog.plugin(pluginAlias)
project.pluginManager.apply(plugin.id)
```

to fixed-ID application:

```groovy
spec.externalPluginIds.each { pluginId ->
    project.pluginManager.apply(pluginId)
}
```

Define native as:

```groovy
static final CapabilitySpec NATIVE = CapabilitySpec.builder('native')
    .allow(ModuleKind.SPRING_SERVICE)
    .externalPluginId('org.graalvm.buildtools.native')
    .build()
```

The implementation dependency version remains fixed in `gradle/libs.versions.toml` and the build-logic POM.

- [ ] **Step 4: Implement the eleven thin binary feature entry points**

Each class follows this exact pattern:

```groovy
final class SimpleDslWebPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply('io.github.qigao.simpledsl.module')
        SimpleDslCapabilitySupport.registerAndEnable(
            project,
            'io.github.qigao.simpledsl.feature.web',
            BuiltinCapabilities.WEB)
    }
}
```

Use the matching plugin ID and `BuiltinCapabilities` constant in every class.

- [ ] **Step 5: Register all eleven feature plugins**

The build must publish markers for:

```text
io.github.qigao.simpledsl.feature.aop
io.github.qigao.simpledsl.feature.transaction
io.github.qigao.simpledsl.feature.web
io.github.qigao.simpledsl.feature.http-client
io.github.qigao.simpledsl.feature.messaging
io.github.qigao.simpledsl.feature.jdbc
io.github.qigao.simpledsl.feature.jooq
io.github.qigao.simpledsl.feature.jpa
io.github.qigao.simpledsl.feature.redis
io.github.qigao.simpledsl.feature.native
io.github.qigao.simpledsl.feature.lombok
```

- [ ] **Step 6: Verify GREEN**

```bash
./gradlew :simpledsl-build-logic:test :simpledsl-build-logic:validatePlugins verifyProductNamespace
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add -- simpledsl-build-logic
git commit -m "refactor: expose SimpleDSL features as binary plugins"
```

---

### Task 6: Convert jOOQ and JSON schema conventions into binary plugins

**Files:**
- Create: `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/schema/SimpleDslJooqSchemaExtension.groovy`
- Create: `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/schema/SimpleDslJooqSchemaPlugin.groovy`
- Create: `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/schema/SimpleDslJsonSchemaExtension.groovy`
- Create: `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/schema/SimpleDslJsonSchemaPlugin.groovy`
- Modify: `simpledsl-build-logic/build.gradle.kts`
- Test: `simpledsl-build-logic/src/test/groovy/io/github/qigao/simpledsl/gradle/schema/SchemaPluginConfigurationSpec.groovy`

**Interfaces:**
- Consumes: jOOQ and jsonschema2pojo plugin implementations from the SimpleDSL artifact classpath; consumer library aliases `jooq-meta-extensions` and `jooq-core` for generated-code dependencies.
- Produces: plugin IDs `io.github.qigao.simpledsl.schema.jooq` and `io.github.qigao.simpledsl.schema.json`; extensions `simpledslJooq` and `simpledslJsonSchema`.

- [ ] **Step 1: Write failing extension/configuration tests**

Assert defaults:

```groovy
simpledslJooq.source == 'database/schema/**/*.sql'
simpledslJooq.sort == 'semantic'
simpledslJooq.tablePrefix == 'Q'
simpledslJooq.recordPrefix == 'R'

simpledslJsonSchema.source == 'json'
simpledslJsonSchema.validation
simpledslJsonSchema.builders
simpledslJsonSchema.getters
simpledslJsonSchema.setters
```

Assert missing package names fail with:

```text
simpledslJooq.packageName must be configured
simpledslJsonSchema.packageName must be configured
```

- [ ] **Step 2: Run RED**

```bash
./gradlew :simpledsl-build-logic:test --tests '*SchemaPluginConfigurationSpec'
```

Expected: FAIL because the schema binary plugins are missing.

- [ ] **Step 3: Implement the jOOQ binary plugin**

Port the behavior of the old `durex.schema.jooq.gradle` into `SimpleDslJooqSchemaPlugin`. Apply `org.jooq.jooq-codegen-gradle`, obtain the SimpleDSL catalog, wire `jooqCodegen`, and configure generated sources under:

```text
build/generated-src/jooq/main
```

The public extension is:

```groovy
def schema = project.extensions.create('simpledslJooq', SimpleDslJooqSchemaExtension)
```

Keep the Q/R matcher semantics and `compileJava.dependsOn(jooqCodegen)`.

- [ ] **Step 4: Implement the JSON Schema binary plugin**

Port the old JSON behavior, apply `org.jsonschema2pojo`, register generated sources under:

```text
build/generated-src/json/main
```

and expose:

```groovy
def schema = project.extensions.create(
    'simpledslJsonSchema', SimpleDslJsonSchemaExtension)
```

Keep Jakarta Validation, builders, getters/setters, `toString`, and equals/hashCode options.

- [ ] **Step 5: Register both public schema markers and verify**

Run:

```bash
./gradlew :simpledsl-build-logic:test :simpledsl-build-logic:validatePlugins verifyProductNamespace
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -- simpledsl-build-logic
git commit -m "feat: add SimpleDSL schema binary plugins"
```

---

### Task 7: Prove published-artifact resolution with an isolated consumer contract

**Files:**
- Modify: root `build.gradle.kts`
- Modify: `simpledsl-build-bootstrap/build.gradle.kts`
- Modify: `simpledsl-build-logic/build.gradle.kts`
- Modify: `integration-tests/build.gradle.kts`
- Create: `integration-tests/src/test/groovy/io/github/qigao/simpledsl/PublishedConsumerContractSpec.groovy`
- Create fixture: `integration-tests/consumer/settings.gradle`
- Create fixture: `integration-tests/consumer/gradle/simpledsl/dependencies.toml`
- Create fixture: `integration-tests/consumer/gradle/simpledsl/spring.toml`
- Create fixture: `integration-tests/consumer/gradle/simpledsl/test.toml`
- Create fixture: `integration-tests/consumer/app/build.gradle`
- Create fixture: `integration-tests/consumer/app/src/main/java/example/Application.java`
- Create fixture: `integration-tests/consumer/app/src/test/java/example/ApplicationTest.java`
- Create schema fixtures under `integration-tests/consumer/schema-*` for jOOQ and JSON generation

**Interfaces:**
- Consumes: plugin implementation publications and plugin marker publications from `build/test-plugin-repo`.
- Produces: hard external-consumer proof with no source `includeBuild`; tests release coherence, module discovery, dependencies, capabilities, doctor, Spring compilation/test, schema generation, and configuration-cache reuse.

- [ ] **Step 1: Configure an isolated local Maven test repository**

For both plugin subprojects, configure Maven Publish repository name `testPlugin` pointing to:

```kotlin
rootProject.layout.buildDirectory.dir("test-plugin-repo")
```

Create a root task:

```kotlin
tasks.register("publishToTestPluginRepository") {
    dependsOn(
        ":simpledsl-build-bootstrap:publishAllPublicationsToTestPluginRepositoryRepository",
        ":simpledsl-build-logic:publishAllPublicationsToTestPluginRepositoryRepository"
    )
}
```

Use the actual generated task names from `./gradlew tasks --all`; if Gradle names the repository task differently, update the dependencies to those exact generated names before committing.

- [ ] **Step 2: Write the standalone consumer before wiring the TestKit runner**

`integration-tests/consumer/settings.gradle`:

```groovy
pluginManagement {
    repositories {
        maven { url = uri(providers.gradleProperty('simpledslTestRepo').get()) }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id 'io.github.qigao.simpledsl.settings' version providers.gradleProperty('simpledslVersion').get()
}

rootProject.name = 'simpledsl-consumer'
```

`app/build.gradle`:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.spring-service'
    id 'io.github.qigao.simpledsl.feature.web'
}
```

The fixture must contain no `includeBuild` string anywhere.

- [ ] **Step 3: Add the minimal consumer manifest**

`gradle/simpledsl/dependencies.toml`:

```toml
include = ["spring.toml", "test.toml"]

[java]
version = 25
```

`spring.toml` defines the Spring Boot 4.1.0 platform and the exact library aliases required by Spring service/web tests. `test.toml` defines `junit-jupiter` and `junit-platform-launcher`. Do not declare SimpleDSL-owned plugin IDs in the consumer manifest.

- [ ] **Step 4: Write the published consumer TestKit test**

The test first asserts the fixture has no source shortcut:

```groovy
assert !new File(fixtureDir, 'settings.gradle').text.contains('includeBuild')
```

Then run:

```groovy
def result = GradleRunner.create()
    .withProjectDir(fixtureDir)
    .withArguments(
        'simpledslProjects',
        ':app:simpledslCapabilities',
        ':app:simpledslDoctor',
        ':app:test',
        '--configuration-cache',
        "-PsimpledslTestRepo=${testRepo.toURI()}",
        "-PsimpledslVersion=${projectVersion}",
        '--stacktrace')
    .build()
```

Assert discovery contains `:app`, capabilities contain `web`, doctor succeeds, tests pass, and the configuration cache is stored.

Run the same arguments a second time and assert output contains configuration-cache reuse.

- [ ] **Step 5: Add release-coherence negative coverage**

Create a temporary build file that requests:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.spring-service' version '9.9.9'
}
```

Expected failure includes:

```text
SimpleDSL version conflict
Plugin: io.github.qigao.simpledsl.spring-service
Requested: 9.9.9
Managed: 0.1.0-SNAPSHOT
```

Use the actual current project version in the final assertion instead of hard-coding the managed value in test code.

- [ ] **Step 6: Add published schema smoke coverage**

The fixture applies the published schema plugins by ID, generates one jOOQ table class and one JSON model, and asserts the files exist under the expected generated directories. No schema test may use project dependencies on the plugin modules.

- [ ] **Step 7: Make integration tests depend on local publication**

Configure `:integration-tests:test` to depend on `publishToTestPluginRepository` and pass:

```text
simpledsl.test.repo=<root>/build/test-plugin-repo
simpledsl.test.version=<root project version>
```

as test system properties.

- [ ] **Step 8: Run the complete consumer contract**

```bash
./gradlew clean publishToTestPluginRepository :integration-tests:test verifyProductNamespace --stacktrace
```

Expected: PASS; the standalone consumer resolves plugin markers and implementation artifacts only from the Maven test repository.

- [ ] **Step 9: Commit**

```bash
git add -- build.gradle.kts simpledsl-build-bootstrap simpledsl-build-logic integration-tests
git commit -m "test: prove published SimpleDSL consumer contract"
```

---

### Task 8: Add Plugin Portal metadata, public documentation, CI, and protected release workflow

**Files:**
- Create: `README.md`
- Modify: `simpledsl-build-bootstrap/build.gradle.kts`
- Modify: `simpledsl-build-logic/build.gradle.kts`
- Create: `.github/workflows/ci.yml`
- Create: `.github/workflows/release.yml`

**Interfaces:**
- Consumes: all public plugin registrations and passing consumer contract.
- Produces: Plugin Portal validation-ready publications and a tag-triggered credentialed release path.

- [ ] **Step 1: Complete public Plugin Portal metadata for every plugin**

Set the same repository metadata on both plugin projects:

```kotlin
gradlePlugin {
    website = "https://github.com/qigao/simpledsl"
    vcsUrl = "https://github.com/qigao/simpledsl.git"
}
```

Every public plugin registration must have non-empty `displayName`, `description`, and `tags`. Do not publish internal IDs.

- [ ] **Step 2: Write the English README as the consumer contract**

The README contains a minimal installation example:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.settings' version '0.1.0'
}
```

and a module example:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.spring-service'
    id 'io.github.qigao.simpledsl.feature.web'
}
```

Document the default manifest files:

```text
gradle/simpledsl/dependencies.toml
gradle/simpledsl/modules.toml
```

Document `simpledslProjects`, `simpledslDependencies`, `simpledslCapabilities`, and `simpledslDoctor`.

- [ ] **Step 3: Add PR CI**

`.github/workflows/ci.yml` uses checkout v4, setup-java with Temurin 25, and Gradle setup. Its verification command is:

```bash
./gradlew clean check publishToTestPluginRepository :integration-tests:test \
  :simpledsl-build-bootstrap:publishPlugins \
  :simpledsl-build-logic:publishPlugins \
  --validate-only --stacktrace
```

If Gradle rejects combining normal tasks with the `publishPlugins` option, split validation into a second command containing only the two `publishPlugins` task paths with `--validate-only`.

No publication credentials are present in PR CI.

- [ ] **Step 4: Add the tag-triggered release workflow**

Trigger only tags matching `v*`. Derive the version exactly:

```bash
VERSION="${GITHUB_REF_NAME#v}"
test -n "$VERSION"
echo "SIMPLEDSL_RELEASE_VERSION=$VERSION" >> "$GITHUB_ENV"
```

Run the same full verification using `-PreleaseVersion=$SIMPLEDSL_RELEASE_VERSION`, then publish:

```bash
./gradlew \
  :simpledsl-build-bootstrap:publishPlugins \
  :simpledsl-build-logic:publishPlugins \
  -PreleaseVersion="$SIMPLEDSL_RELEASE_VERSION" \
  --stacktrace
```

Set workflow environment variables only from GitHub secrets:

```yaml
env:
  GRADLE_PUBLISH_KEY: ${{ secrets.GRADLE_PUBLISH_KEY }}
  GRADLE_PUBLISH_SECRET: ${{ secrets.GRADLE_PUBLISH_SECRET }}
```

Never echo either secret.

- [ ] **Step 5: Verify Plugin Portal validation locally/CI-style**

Run:

```bash
./gradlew check publishToTestPluginRepository :integration-tests:test --stacktrace
./gradlew :simpledsl-build-bootstrap:publishPlugins :simpledsl-build-logic:publishPlugins --validate-only --stacktrace
```

Expected: all tasks PASS without upload credentials.

- [ ] **Step 6: Run the final product namespace scan**

```bash
./gradlew verifyProductNamespace
```

Expected: no Durex product identifiers in build/product/consumer/public documentation files.

- [ ] **Step 7: Commit**

```bash
git add -- README.md simpledsl-build-bootstrap simpledsl-build-logic .github
git commit -m "ci: prepare SimpleDSL for Gradle Plugin Portal"
```

---

### Task 9: Final extraction verification and publication candidate

**Files:**
- Modify only files required by failures discovered by the commands below.
- Do not modify `qigao/durex` in this task.

**Interfaces:**
- Consumes: Tasks 1-8.
- Produces: a reviewable SimpleDSL branch that is ready for a `v0.1.0` tag after merge and Plugin Portal credentials are configured.

- [ ] **Step 1: Run all verification from a clean checkout state**

```bash
./gradlew clean check --stacktrace
./gradlew publishToTestPluginRepository :integration-tests:test --stacktrace
./gradlew :simpledsl-build-bootstrap:publishPlugins :simpledsl-build-logic:publishPlugins --validate-only --stacktrace
./gradlew verifyProductNamespace
```

Expected: all commands PASS.

- [ ] **Step 2: Inspect publication coordinates and marker artifacts**

Under `build/test-plugin-repo`, verify implementation modules exist at:

```text
io/github/qigao/simpledsl/simpledsl-build-bootstrap/<version>/
io/github/qigao/simpledsl/simpledsl-build-logic/<version>/
```

Verify marker modules exist for all eighteen public IDs: settings, module, three module types, eleven features, and two schema plugins.

- [ ] **Step 3: Verify no source shortcuts remain**

Run:

```bash
if grep -RIn 'includeBuild' integration-tests/consumer simpledsl-build-bootstrap simpledsl-build-logic; then
  echo 'source includeBuild shortcut found' >&2
  exit 1
fi
```

Expected: no matches.

- [ ] **Step 4: Verify product naming contract**

Run:

```bash
bash scripts/verify-product-namespace.sh
```

Expected: PASS. Historical migration docs may still mention Durex; product source/build/consumer/README may not.

- [ ] **Step 5: Commit any verification-only corrections**

Stage only files actually changed by verification fixes:

```bash
git status --short
git add -- <exact changed paths>
git commit -m "fix: finalize SimpleDSL publication candidate"
```

Skip the commit if verification required no corrections.

- [ ] **Step 6: Open a draft PR to `master`**

PR title:

```text
feat: extract SimpleDSL Gradle plugin platform
```

PR body must state:

```text
- extracts bootstrap/build-logic from qigao/durex
- renames the public product surface to io.github.qigao.simpledsl.*
- converts public convention scripts to binary Gradle plugins
- proves published marker/artifact consumption without includeBuild
- validates Gradle Plugin Portal publications
- does not cut over qigao/durex yet
```

Keep the PR draft until CI is green.

---

## Follow-up plan after the first Plugin Portal release

After `v0.1.0` is approved and visible on the Gradle Plugin Portal, create a separate `qigao/durex` cutover plan. That plan will replace `includeBuild('build-bootstrap')` / `includeBuild('build-logic')` with `io.github.qigao.simpledsl.settings` version `0.1.0`, migrate build files from `durex.*` to `io.github.qigao.simpledsl.*`, rename Durex build DSL/task usage to SimpleDSL equivalents, run the full Spring/Native CI against the released product, and only then delete the local plugin source trees.
