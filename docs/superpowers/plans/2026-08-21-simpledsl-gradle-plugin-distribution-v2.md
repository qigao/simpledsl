# SimpleDSL Gradle Plugin Distribution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `qigao/simpledsl` as a standalone Gradle plugin product containing settings/bootstrap and project/build-logic artifacts, with the public namespace `io.github.qigao.simpledsl.*`, and prove external consumption through locally published plugin markers before a Plugin Portal release.

**Architecture:** `simpledsl-build-bootstrap` publishes the settings plugin and owns manifest loading, module discovery, release coherence, and the `simpledslDependencyRegistry` shared service. `simpledsl-build-logic` publishes module, feature, and schema binary plugins and consumes the registry only through the existing schema-1 reflective snapshot contract, avoiding cross-plugin-classloader type coupling. The repository builds itself with ordinary Gradle; it never applies SimpleDSL to build SimpleDSL.

**Tech Stack:** Gradle 9.1.0, Java 25 CI JDK, Java 17 plugin bytecode target, Groovy, Gradle TestKit, `com.gradle.plugin-publish` 2.1.1, Tomlj 1.1.1, Spring Boot Gradle plugin 4.1.0, GraalVM Native Gradle plugin 1.1.1, jOOQ 3.21.5, jsonschema2pojo 1.3.3, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-21-durex-gradle-plugin-distribution-design.md`

## Global Constraints

- Product name: `SimpleDSL`.
- Maven group: `io.github.qigao.simpledsl`.
- Public plugin prefix: `io.github.qigao.simpledsl.`.
- Implementation package root: `io.github.qigao.simpledsl.gradle`.
- Public extensions: `simpledslSettings`, `simpledsl`.
- Public tasks: `simpledslProjects`, `simpledslDependencies`, `simpledslCapabilities`, `simpledslDoctor`.
- Default manifest root: `gradle/simpledsl/`.
- Development version: `0.1.0-SNAPSHOT`; release version comes only from `-PreleaseVersion=<tag-version>`.
- `simpledsl-build-bootstrap` and `simpledsl-build-logic` always use the same project version.
- Preserve dependency snapshot schema version `1`.
- No public `durex.*` aliases, no `com.github.durex` implementation packages, and no Durex-branded public diagnostics in the product repository.
- No `includeBuild` in product source or the isolated consumer fixture.
- SimpleDSL-owned Gradle plugin implementations are fixed by the SimpleDSL release: Spring Boot `4.1.0`, GraalVM Native `1.1.1`, jOOQ `3.21.5`, jsonschema2pojo `1.3.3`.
- Do not modify or delete plugin sources in `qigao/durex` in this plan.

---

### Task 1: Bootstrap the standalone multi-project plugin build

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Generate: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`
- Create: `simpledsl-build-bootstrap/build.gradle.kts`
- Create: `simpledsl-build-logic/build.gradle.kts`
- Create: `integration-tests/build.gradle.kts`
- Create: `scripts/verify-product-namespace.sh`

**Interfaces:**
- Produces projects `:simpledsl-build-bootstrap`, `:simpledsl-build-logic`, `:integration-tests` and lifecycle task `verifyProductNamespace`.

- [ ] **Step 1: Add the product namespace guard**

Create `scripts/verify-product-namespace.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail
roots=(settings.gradle.kts build.gradle.kts gradle.properties gradle simpledsl-build-bootstrap simpledsl-build-logic integration-tests README.md)
existing=()
for path in "${roots[@]}"; do [[ -e "$path" ]] && existing+=("$path"); done
if ((${#existing[@]})); then
  if grep -RInE 'com\.github\.durex|\bdurex\.(settings|module|feature|schema|java-library|spring-library|spring-service)|Durex(Configuration|Extension|Module|Capability|Doctor|Settings|Dependency|Registry|Json|Jooq)' "${existing[@]}"; then
    echo 'legacy Durex product namespace leaked into SimpleDSL product files' >&2
    exit 1
  fi
fi
```

- [ ] **Step 2: Create root settings and version policy**

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories { gradlePluginPortal(); mavenCentral() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { gradlePluginPortal(); mavenCentral() }
}
rootProject.name = "simpledsl"
include("simpledsl-build-bootstrap", "simpledsl-build-logic", "integration-tests")
```

`gradle.properties`:

```properties
org.gradle.configuration-cache=true
org.gradle.parallel=true
org.gradle.caching=true
simpledslVersion=0.1.0-SNAPSHOT
```

`build.gradle.kts`:

```kotlin
plugins { base }
val releaseVersion = providers.gradleProperty("releaseVersion")
val developmentVersion = providers.gradleProperty("simpledslVersion")
allprojects {
    group = "io.github.qigao.simpledsl"
    version = releaseVersion.orElse(developmentVersion).get()
}
tasks.register<Exec>("verifyProductNamespace") {
    commandLine("bash", "scripts/verify-product-namespace.sh")
}
tasks.named("check") { dependsOn("verifyProductNamespace") }
```

- [ ] **Step 3: Create the product-owned dependency catalog**

`gradle/libs.versions.toml`:

```toml
[versions]
plugin-publish = "2.1.1"
tomlj = "1.1.1"
spring-boot = "4.1.0"
graalvm-native = "1.1.1"
jooq = "3.21.5"
jsonschema2pojo = "1.3.3"
junit = "5.13.4"

[libraries]
tomlj = { module = "org.tomlj:tomlj", version.ref = "tomlj" }
spring-boot-gradle = { module = "org.springframework.boot:spring-boot-gradle-plugin", version.ref = "spring-boot" }
graalvm-native-gradle = { module = "org.graalvm.buildtools:native-gradle-plugin", version.ref = "graalvm-native" }
jooq-codegen-gradle = { module = "org.jooq:jooq-codegen-gradle", version.ref = "jooq" }
jooq-core = { module = "org.jooq:jooq", version.ref = "jooq" }
jooq-meta = { module = "org.jooq:jooq-meta", version.ref = "jooq" }
jsonschema2pojo-gradle = { module = "org.jsonschema2pojo:jsonschema2pojo-gradle-plugin", version.ref = "jsonschema2pojo" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }

[plugins]
plugin-publish = { id = "com.gradle.plugin-publish", version.ref = "plugin-publish" }
```

- [ ] **Step 4: Configure plugin projects without SimpleDSL self-hosting**

Both plugin module build files start with:

```kotlin
plugins {
    groovy
    alias(libs.plugins.plugin.publish)
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(17)) }
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
}
dependencies {
    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
}
tasks.test { useJUnitPlatform() }
```

Bootstrap adds `implementation(libs.tomlj)`.

Build logic adds exactly:

```kotlin
implementation(libs.spring.boot.gradle)
implementation(libs.graalvm.native.gradle)
implementation(libs.jooq.codegen.gradle)
implementation(libs.jooq.core)
implementation(libs.jooq.meta)
implementation(libs.jsonschema2pojo.gradle)
```

`integration-tests/build.gradle.kts` applies `groovy`, uses `gradleTestKit()` + JUnit Jupiter only, and has no production dependency on either plugin module.

- [ ] **Step 5: Generate and verify wrapper**

```bash
gradle wrapper --gradle-version 9.1.0 --distribution-type bin
./gradlew --version
./gradlew projects verifyProductNamespace --stacktrace
```

Expected: `Gradle 9.1.0`, three subprojects, namespace guard PASS.

- [ ] **Step 6: Commit**

```bash
git add -- settings.gradle.kts build.gradle.kts gradle.properties gradle gradlew gradlew.bat simpledsl-build-bootstrap simpledsl-build-logic integration-tests scripts
git commit -m "build: bootstrap standalone SimpleDSL plugin build"
```

---

### Task 2: Extract bootstrap, settings plugin, and immutable distribution metadata

**Files:**
- Create: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest/*.groovy`
- Create: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/settings/{SimpleDslSettingsPlugin,SimpleDslSettingsExtension,SimpleDslDependenciesTask,SimpleDslProjectsTask,ProjectDiscovery,ProjectRegistry,ProjectSpec}.groovy`
- Create: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/distribution/SimpleDslDistribution.groovy`
- Create: `simpledsl-build-bootstrap/src/main/resources/META-INF/simpledsl/distribution.properties`
- Modify: `simpledsl-build-bootstrap/build.gradle.kts`
- Test: `simpledsl-build-bootstrap/src/test/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyManifestLoaderTest.groovy`
- Test: `simpledsl-build-bootstrap/src/test/groovy/io/github/qigao/simpledsl/gradle/settings/SimpleDslSettingsPluginTest.groovy`

**Interfaces:**
- Produces `simpledslDependencyRegistry`, snapshot schema `1`, extension `simpledslSettings`, root tasks `simpledslDependencies` / `simpledslProjects`, and plugin ID `io.github.qigao.simpledsl.settings`.

- [ ] **Step 1: Write RED tests for manifest loading and settings/module discovery**

The manifest test loads:

```toml
include = ["spring.toml"]
[java]
version = 25
```

and asserts Java 25 plus an included Spring platform/library.

The TestKit test applies:

```groovy
plugins { id 'io.github.qigao.simpledsl.settings' }
rootProject.name = 'consumer'
```

then runs `simpledslDependencies simpledslProjects` with `withPluginClasspath()` and asserts `Java: 25` and `:app` discovery.

- [ ] **Step 2: Run RED**

```bash
./gradlew :simpledsl-build-bootstrap:test --tests '*DependencyManifestLoaderTest' --tests '*SimpleDslSettingsPluginTest'
```

Expected: FAIL because the extracted implementation does not exist.

- [ ] **Step 3: Port reusable manifest/discovery classes and rename product diagnostics**

Copy the reusable source from `qigao/durex/build-bootstrap`, changing package root to:

```groovy
package io.github.qigao.simpledsl.gradle
```

Keep generic names such as `DependencyRegistry`, `ProjectDiscovery`, `ProjectRegistry`, and `ProjectSpec`. Rename only Durex-branded classes to SimpleDSL. Do not port `DurexBuildLogicPlugin`, `DurexBuildLogicSettingsPlugin`, or `DurexBuildLogicSettingsExtension` because the new repository is not self-hosted.

Default paths are:

```groovy
extension.dependencyManifest.convention(extension.repositoryRoot.file('gradle/simpledsl/dependencies.toml'))
extension.modulesManifest.convention(extension.repositoryRoot.file('gradle/simpledsl/modules.toml'))
```

Service registration is exactly:

```groovy
settings.gradle.sharedServices.registerIfAbsent('simpledslDependencyRegistry', DependencyRegistryService) { spec ->
    spec.parameters.manifestFile.set(extension.dependencyManifest)
}
```

- [ ] **Step 4: Generate distribution metadata from the product build**

Generate `META-INF/simpledsl/distribution.properties` with these keys:

```properties
version=0.1.0-SNAPSHOT
springBootPluginVersion=4.1.0
graalvmNativePluginVersion=1.1.1
jooqPluginVersion=3.21.5
jsonschema2pojoPluginVersion=1.3.3
```

For release builds, only `version` changes to `-PreleaseVersion`; owned plugin versions remain those compiled into that release.

`SimpleDslDistribution` exposes:

```groovy
static final String GROUP = 'io.github.qigao.simpledsl'
static final String BUILD_LOGIC_ARTIFACT = 'simpledsl-build-logic'
static final String PUBLIC_PREFIX = 'io.github.qigao.simpledsl.'
static final String SETTINGS_PLUGIN_ID = 'io.github.qigao.simpledsl.settings'
static final Map<String, String> OWNED_PLUGIN_MODULES = [
  'org.springframework.boot': 'org.springframework.boot:spring-boot-gradle-plugin',
  'org.graalvm.buildtools.native': 'org.graalvm.buildtools:native-gradle-plugin',
  'org.jooq.jooq-codegen-gradle': 'org.jooq:jooq-codegen-gradle',
  'org.jsonschema2pojo': 'org.jsonschema2pojo:jsonschema2pojo-gradle-plugin'
]
```

and methods `version()`, `ownedPluginVersion(String id)`, and `ownedPluginCoordinate(String id)`.

- [ ] **Step 5: Enforce release coherence in settings plugin resolution**

Resolution order is exact:

1. `io.github.qigao.simpledsl.settings` is already resolved and is ignored.
2. Any other `io.github.qigao.simpledsl.*` request must match `SimpleDslDistribution.version()` and uses module `io.github.qigao.simpledsl:simpledsl-build-logic:<version>`.
3. Any SimpleDSL-owned external plugin request must match its compiled version and uses its compiled module coordinate.
4. Any remaining plugin may be resolved from the consumer manifest registry.

Mismatch message for SimpleDSL plugins:

```text
SimpleDSL version conflict
Plugin: <id>
Requested: <requested>
Managed: <distribution-version>
```

Mismatch message for owned external plugins:

```text
SimpleDSL plugin compatibility error
Plugin: org.springframework.boot
Requested: <requested>
Managed: 4.1.0
```

After the registry is loaded, scan `snapshot.plugins`; if it declares one of the four owned plugin IDs with a different version, fail with the same compatibility error even if that plugin is not requested. This prevents silent consumer override declarations.

- [ ] **Step 6: Register the binary settings plugin and metadata**

`simpledsl-build-bootstrap/build.gradle.kts` contains:

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

- [ ] **Step 7: Verify GREEN**

```bash
./gradlew :simpledsl-build-bootstrap:test :simpledsl-build-bootstrap:validatePlugins verifyProductNamespace --stacktrace
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add -- simpledsl-build-bootstrap
git commit -m "feat: extract SimpleDSL settings bootstrap"
```

---

### Task 3: Extract the module kernel and preserve the reflective snapshot boundary

**Files:**
- Create under `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/`: `SimpleDslConfigurationException.groovy`, `SimpleDslDependencyAccess.groovy`, `SimpleDslExtension.groovy`, `SimpleDslModulePlugin.groovy`, `ModuleKind.groovy`, `SimpleDslPersistenceExtension.groovy`
- Create/migrate: `capability/**`, `catalog/**`, `dependency/**`, `diagnostics/**`, `model/**`
- Rename `DurexRegistryBridge` -> `SimpleDslRegistryBridge`, `DurexModuleModel` -> `SimpleDslModuleModel`, `DurexCapabilitySupport` -> `SimpleDslCapabilitySupport`, `DurexCapabilitiesTask` -> `SimpleDslCapabilitiesTask`, `DurexDoctorTask` -> `SimpleDslDoctorTask`, `DurexDoctorValidator` -> `SimpleDslDoctorValidator`
- Modify: `simpledsl-build-logic/build.gradle.kts`
- Test: `simpledsl-build-logic/src/test/groovy/io/github/qigao/simpledsl/gradle/catalog/SimpleDslRegistryBridgeTest.groovy`
- Test: `simpledsl-build-logic/src/test/groovy/io/github/qigao/simpledsl/gradle/SimpleDslModulePluginTest.groovy`

**Interfaces:**
- Consumes shared service name `simpledslDependencyRegistry` only through reflective `snapshot()`.
- Produces extension `simpledsl`, model `SimpleDslModuleModel`, capability engine, tasks `simpledslCapabilities` / `simpledslDoctor`, plugin ID `io.github.qigao.simpledsl.module`.

- [ ] **Step 1: Write RED tests for schema-1 and schema mismatch**

Positive snapshot:

```groovy
[schemaVersion: 1, javaVersion: 25, platforms: [:], libraries: [:], plugins: [:]]
```

Negative snapshot uses `schemaVersion: 99` and must fail with:

```text
SimpleDSL bootstrap error
Problem: unsupported dependency snapshot schema
Expected: 1
Actual: 99
```

- [ ] **Step 2: Run RED**

```bash
./gradlew :simpledsl-build-logic:test --tests '*SimpleDslRegistryBridgeTest'
```

- [ ] **Step 3: Port core model with no semantic redesign**

Preserve capability/dependency semantics and schema version. `SimpleDslRegistryBridge.fromProject(Project)` must call:

```groovy
def registration = project.gradle.sharedServices.registrations.findByName('simpledslDependencyRegistry')
Object raw = registration.service.get().getClass().getMethod('snapshot').invoke(registration.service.get())
```

Do not import or cast `DependencyRegistryService` from bootstrap.

- [ ] **Step 4: Port `SimpleDslModulePlugin` and diagnostics**

Create:

```groovy
SimpleDslModuleModel model = project.extensions.create('simpledslModuleModel', SimpleDslModuleModel)
project.extensions.create('simpledsl', SimpleDslExtension, project, model)
```

Register tasks exactly `simpledslCapabilities` and `simpledslDoctor`, group `SimpleDSL`.

- [ ] **Step 5: Register public module plugin**

```kotlin
register("simpleDslModule") {
    id = "io.github.qigao.simpledsl.module"
    implementationClass = "io.github.qigao.simpledsl.gradle.SimpleDslModulePlugin"
    displayName = "SimpleDSL Module"
    description = "SimpleDSL module model, capability engine, dependency API, and diagnostics"
    tags.set(listOf("gradle", "build-platform", "dependencies"))
}
```

- [ ] **Step 6: Verify and commit**

```bash
./gradlew :simpledsl-build-logic:test :simpledsl-build-logic:validatePlugins verifyProductNamespace --stacktrace
git add -- simpledsl-build-logic
git commit -m "feat: extract SimpleDSL module kernel"
```

---

### Task 4: Convert module types and features to binary plugins

**Files:**
- Create: `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/internal/SimpleDslJavaBasePlugin.groovy`
- Create: `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/internal/SimpleDslSpringBasePlugin.groovy`
- Create under `.../module/`: `SimpleDslJavaLibraryPlugin.groovy`, `SimpleDslSpringLibraryPlugin.groovy`, `SimpleDslSpringServicePlugin.groovy`
- Create under `.../feature/`: eleven feature plugin classes
- Modify: `CapabilitySpec.groovy`, `CapabilityEngine.groovy`, `BuiltinCapabilities.groovy`
- Modify: `simpledsl-build-logic/build.gradle.kts`
- Test: `ModuleTypePluginsTest.groovy`, `FeaturePluginsTest.groovy`

**Interfaces:**
- Produces three module type IDs and eleven feature IDs.

- [ ] **Step 1: Write RED module type and feature tests**

Seed a `DependencyCatalogSnapshot` in `ProjectBuilder` and assert module kinds:

```text
io.github.qigao.simpledsl.java-library   -> JAVA_LIBRARY
io.github.qigao.simpledsl.spring-library -> SPRING_LIBRARY
io.github.qigao.simpledsl.spring-service -> SPRING_SERVICE
```

Assert web on `SPRING_SERVICE` binds `spring-webmvc`, `spring-validation`, `spring-webmvc-test`; native on `JAVA_LIBRARY` fails with `SimpleDSL configuration error`.

- [ ] **Step 2: Run RED**

```bash
./gradlew :simpledsl-build-logic:test --tests '*ModuleTypePluginsTest' --tests '*FeaturePluginsTest'
```

- [ ] **Step 3: Implement internal base classes**

`SimpleDslJavaBasePlugin` applies `java` and `io.github.qigao.simpledsl.module`, gets Java version from `SimpleDslDependencyAccess.javaVersion(project)`, sets toolchain/release to that consumer-managed value, and sets UTF-8.

`SimpleDslSpringBasePlugin` applies the Java base and activates platform alias `spring` on existing `implementation`, `api`, `testImplementation` configurations.

- [ ] **Step 4: Implement exact module type behavior**

`SimpleDslJavaLibraryPlugin`:

```text
apply java-library
apply SimpleDslJavaBasePlugin
claim JAVA_LIBRARY
add testImplementation -> junit-jupiter
add testRuntimeOnly -> junit-platform-launcher
JUnit Platform for Test tasks
```

`SimpleDslSpringLibraryPlugin`:

```text
apply java-library
apply SimpleDslSpringBasePlugin
claim SPRING_LIBRARY
add implementation -> spring-core
add testImplementation -> spring-test
add testRuntimeOnly -> junit-platform-launcher
JUnit Platform for Test tasks
```

`SimpleDslSpringServicePlugin`:

```text
apply org.springframework.boot
apply SimpleDslSpringBasePlugin
claim SPRING_SERVICE
add implementation -> spring-core
add testImplementation -> spring-test
add testRuntimeOnly -> junit-platform-launcher
JUnit Platform for Test tasks
```

- [ ] **Step 5: Change capability external plugins from aliases to fixed IDs**

Rename `externalPluginAliases` -> `externalPluginIds`, builder method `externalPlugin(...)` -> `externalPluginId(...)`, and apply exact IDs directly in `CapabilityEngine`.

Native becomes:

```groovy
CapabilitySpec.builder('native')
    .allow(ModuleKind.SPRING_SERVICE)
    .externalPluginId('org.graalvm.buildtools.native')
    .build()
```

- [ ] **Step 6: Implement feature entry points using this complete mapping**

| Plugin ID suffix | Implementation class | Capability |
| --- | --- | --- |
| `feature.aop` | `SimpleDslAopPlugin` | `AOP` |
| `feature.transaction` | `SimpleDslTransactionPlugin` | `TRANSACTION` |
| `feature.web` | `SimpleDslWebPlugin` | `WEB` |
| `feature.http-client` | `SimpleDslHttpClientPlugin` | `HTTP_CLIENT` |
| `feature.messaging` | `SimpleDslMessagingPlugin` | `MESSAGING` |
| `feature.jdbc` | `SimpleDslJdbcPlugin` | `JDBC` |
| `feature.jooq` | `SimpleDslJooqPlugin` | `JOOQ` |
| `feature.jpa` | `SimpleDslJpaPlugin` | `JPA` |
| `feature.redis` | `SimpleDslRedisPlugin` | `REDIS` |
| `feature.native` | `SimpleDslNativePlugin` | `NATIVE` |
| `feature.lombok` | `SimpleDslLombokPlugin` | `LOMBOK` |

Every class applies `io.github.qigao.simpledsl.module`, then calls:

```groovy
SimpleDslCapabilitySupport.registerAndEnable(project, '<full plugin id>', BuiltinCapabilities.<constant>)
```

- [ ] **Step 7: Register all fourteen public markers**

Register the three module types and eleven feature IDs in `gradlePlugin.plugins`, each with the exact implementation class above and non-empty English `displayName`, `description`, and tags.

- [ ] **Step 8: Verify and commit**

```bash
./gradlew :simpledsl-build-logic:test :simpledsl-build-logic:validatePlugins verifyProductNamespace --stacktrace
git add -- simpledsl-build-logic
git commit -m "refactor: expose SimpleDSL module and feature binary plugins"
```

---

### Task 5: Convert schema plugins to binary implementations

**Files:**
- Create under `.../schema/`: `SimpleDslJooqSchemaExtension.groovy`, `SimpleDslJooqSchemaPlugin.groovy`, `SimpleDslJsonSchemaExtension.groovy`, `SimpleDslJsonSchemaPlugin.groovy`
- Modify: `simpledsl-build-logic/build.gradle.kts`
- Test: `SchemaPluginConfigurationTest.groovy`

**Interfaces:**
- Produces `io.github.qigao.simpledsl.schema.jooq`, extension `simpledslJooq`; and `io.github.qigao.simpledsl.schema.json`, extension `simpledslJsonSchema`.

- [ ] **Step 1: Write RED tests for extension defaults and missing package validation**

jOOQ defaults:

```text
source=database/schema/**/*.sql
sort=semantic
tablePrefix=Q
recordPrefix=R
```

JSON defaults:

```text
source=json
validation=true
builders=true
getters=true
setters=true
toString=true
equalsAndHashCode=true
```

Missing package diagnostics are exactly `simpledslJooq.packageName must be configured` and `simpledslJsonSchema.packageName must be configured`.

- [ ] **Step 2: Run RED**

```bash
./gradlew :simpledsl-build-logic:test --tests '*SchemaPluginConfigurationTest'
```

- [ ] **Step 3: Implement jOOQ binary plugin**

Apply `org.jooq.jooq-codegen-gradle`; create `simpledslJooq`; add `jooq-meta-extensions` to `jooqCodegen`, `jooq-core` to `implementation`; make `compileJava` depend on `jooqCodegen`; preserve `DDLDatabase`, semantic sort, Q/R matcher transforms, records, fluent setters, and target directory `build/generated-src/jooq/main`.

- [ ] **Step 4: Implement JSON Schema binary plugin**

Apply `org.jsonschema2pojo`; create `simpledslJsonSchema`; register `build/generated-src/json/main` as main Java source; preserve Jakarta Validation, builder/getter/setter/toString/equals/hashCode options.

- [ ] **Step 5: Register both schema markers, verify, commit**

```bash
./gradlew :simpledsl-build-logic:test :simpledsl-build-logic:validatePlugins verifyProductNamespace --stacktrace
git add -- simpledsl-build-logic
git commit -m "feat: add SimpleDSL schema binary plugins"
```

---

### Task 6: Publish to an isolated Maven repository and prove a real consumer

**Files:**
- Modify: root `build.gradle.kts`
- Modify: both plugin module build files
- Modify: `integration-tests/build.gradle.kts`
- Create: `integration-tests/src/test/groovy/io/github/qigao/simpledsl/PublishedConsumerContractTest.groovy`
- Create standalone fixture under `integration-tests/consumer/**`

**Interfaces:**
- Produces repository `build/test-plugin-repo`, root task `publishToTestPluginRepository`, and a consumer contract with no `includeBuild`.

- [ ] **Step 1: Configure Maven test publication on both plugin modules**

For each module:

```kotlin
publishing {
    repositories {
        maven {
            name = "testPlugin"
            url = rootProject.layout.buildDirectory.dir("test-plugin-repo")
        }
    }
}
```

The generated task is `publishAllPublicationsToTestPluginRepository` in each module. Root task:

```kotlin
tasks.register("publishToTestPluginRepository") {
    dependsOn(
        ":simpledsl-build-bootstrap:publishAllPublicationsToTestPluginRepository",
        ":simpledsl-build-logic:publishAllPublicationsToTestPluginRepository"
    )
}
```

- [ ] **Step 2: Create exact standalone consumer settings**

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

- [ ] **Step 3: Create exact consumer manifests**

`gradle/simpledsl/dependencies.toml`:

```toml
include = ["spring.toml", "test.toml", "database.toml"]
[java]
version = 25
```

`spring.toml`:

```toml
[versions]
spring-boot = "4.1.0"

[platforms.spring]
module = "org.springframework.boot:spring-boot-dependencies"
version.ref = "spring-boot"

[libraries.spring-core]
module = "org.springframework.boot:spring-boot-starter"
platform = "spring"
[libraries.spring-test]
module = "org.springframework.boot:spring-boot-starter-test"
platform = "spring"
[libraries.spring-webmvc]
module = "org.springframework.boot:spring-boot-starter-webmvc"
platform = "spring"
[libraries.spring-validation]
module = "org.springframework.boot:spring-boot-starter-validation"
platform = "spring"
[libraries.spring-webmvc-test]
module = "org.springframework.boot:spring-boot-starter-webmvc-test"
platform = "spring"
```

`test.toml`:

```toml
[versions]
junit = "5.13.4"

[libraries.junit-jupiter]
module = "org.junit.jupiter:junit-jupiter"
version.ref = "junit"
[libraries.junit-platform-launcher]
module = "org.junit.platform:junit-platform-launcher"
version = "1.13.4"
```

`database.toml`:

```toml
[versions]
jooq = "3.21.5"

[libraries.jooq-core]
module = "org.jooq:jooq"
version.ref = "jooq"
[libraries.jooq-meta-extensions]
module = "org.jooq:jooq-meta-extensions"
version.ref = "jooq"
```

- [ ] **Step 4: Create exact Spring consumer module**

`app/build.gradle`:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.spring-service'
    id 'io.github.qigao.simpledsl.feature.web'
}
```

`Application.java`:

```java
package example;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
public class Application {
    public static void main(String[] args) { SpringApplication.run(Application.class, args); }
}
```

`ApplicationTest.java`:

```java
package example;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest
class ApplicationTest {
    @Test void contextLoads() {}
}
```

- [ ] **Step 5: Add schema fixture modules**

`schema-jooq/build.gradle` applies `io.github.qigao.simpledsl.java-library` and `io.github.qigao.simpledsl.schema.jooq`, sets:

```groovy
simpledslJooq { packageName = 'example.schema' }
```

and `database/schema/music.sql` contains:

```sql
create table music (id integer primary key, title varchar(255) not null);
```

`schema-json/build.gradle` applies `io.github.qigao.simpledsl.java-library` and `io.github.qigao.simpledsl.schema.json`, sets:

```groovy
simpledslJsonSchema { packageName = 'example.model' }
```

and `json/music.json` contains a Draft-07 object schema with required string property `title`.

- [ ] **Step 6: Write published-artifact TestKit contract**

The test verifies no fixture file contains `includeBuild`, then runs:

```text
simpledslProjects
:app:simpledslCapabilities
:app:simpledslDoctor
:app:test
:schema-jooq:jooqCodegen
:schema-json:generateJsonSchema2Pojo
--configuration-cache
-PsimpledslTestRepo=<absolute build/test-plugin-repo URI>
-PsimpledslVersion=<root project version>
```

Assert `:app` discovery, `web` capability, doctor success, test success, generated jOOQ and JSON files, and `Configuration cache entry stored` on first run. Run the same command a second time and assert configuration-cache reuse.

- [ ] **Step 7: Add version mismatch and owned-plugin mismatch tests**

A temporary module requesting:

```groovy
plugins { id 'io.github.qigao.simpledsl.spring-service' version '9.9.9' }
```

must fail with `SimpleDSL version conflict`.

A consumer manifest declaring:

```toml
[plugins.spring-boot]
id = "org.springframework.boot"
module = "org.springframework.boot:spring-boot-gradle-plugin"
version = "4.0.0"
```

must fail during settings evaluation with `SimpleDSL plugin compatibility error`, requested `4.0.0`, managed `4.1.0`.

- [ ] **Step 8: Wire and run the contract**

`:integration-tests:test` depends on root `publishToTestPluginRepository` and receives system properties `simpledsl.test.repo` and `simpledsl.test.version`.

Run:

```bash
./gradlew clean publishToTestPluginRepository :integration-tests:test verifyProductNamespace --stacktrace
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add -- build.gradle.kts simpledsl-build-bootstrap simpledsl-build-logic integration-tests
git commit -m "test: prove published SimpleDSL consumer contract"
```

---

### Task 7: Add Plugin Portal metadata, README, CI, and tag release

**Files:**
- Create: `README.md`
- Modify: both plugin module build files
- Create: `.github/workflows/ci.yml`
- Create: `.github/workflows/release.yml`

**Interfaces:**
- Produces validation-ready Plugin Portal publications and a secret-backed tag release path.

- [ ] **Step 1: Complete metadata for all eighteen public plugin IDs**

Both plugin projects set:

```kotlin
website = "https://github.com/qigao/simpledsl"
vcsUrl = "https://github.com/qigao/simpledsl.git"
```

Public IDs are exactly: settings; module; java-library; spring-library; spring-service; eleven feature IDs; two schema IDs. Internal implementation helpers have no published marker.

- [ ] **Step 2: Write README consumer documentation**

README must contain these exact install examples:

```groovy
plugins { id 'io.github.qigao.simpledsl.settings' version '0.1.0' }
```

and:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.spring-service'
    id 'io.github.qigao.simpledsl.feature.web'
}
```

It documents `gradle/simpledsl/dependencies.toml`, optional `gradle/simpledsl/modules.toml`, and the four public diagnostic tasks.

- [ ] **Step 3: Add PR CI**

CI uses checkout v4, Temurin 25, `gradle/actions/setup-gradle@v4`, then:

```bash
./gradlew clean check publishToTestPluginRepository :integration-tests:test --stacktrace
./gradlew :simpledsl-build-bootstrap:publishPlugins :simpledsl-build-logic:publishPlugins --validate-only --stacktrace
```

No Plugin Portal secret is exposed to pull requests.

- [ ] **Step 4: Add tag release workflow**

Trigger `v*`, compute:

```bash
VERSION="${GITHUB_REF_NAME#v}"
test -n "$VERSION"
echo "SIMPLEDSL_RELEASE_VERSION=$VERSION" >> "$GITHUB_ENV"
```

Verify with `-PreleaseVersion="$SIMPLEDSL_RELEASE_VERSION"`, then publish both modules:

```bash
./gradlew :simpledsl-build-bootstrap:publishPlugins :simpledsl-build-logic:publishPlugins \
  -PreleaseVersion="$SIMPLEDSL_RELEASE_VERSION" --stacktrace
```

Credentials come only from `GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET` GitHub secrets.

- [ ] **Step 5: Verify publication validation and namespace**

```bash
./gradlew clean check publishToTestPluginRepository :integration-tests:test --stacktrace
./gradlew :simpledsl-build-bootstrap:publishPlugins :simpledsl-build-logic:publishPlugins --validate-only --stacktrace
./gradlew verifyProductNamespace
```

Expected: PASS without upload credentials.

- [ ] **Step 6: Commit**

```bash
git add -- README.md simpledsl-build-bootstrap simpledsl-build-logic .github
git commit -m "ci: prepare SimpleDSL for Gradle Plugin Portal"
```

---

### Task 8: Final publication candidate verification and draft PR

**Files:**
- Modify only concrete files implicated by a failing verification command.

**Interfaces:**
- Produces a branch ready to merge and tag `v0.1.0`; downstream Durex cutover remains out of scope.

- [ ] **Step 1: Run clean final verification**

```bash
./gradlew clean check --stacktrace
./gradlew publishToTestPluginRepository :integration-tests:test --stacktrace
./gradlew :simpledsl-build-bootstrap:publishPlugins :simpledsl-build-logic:publishPlugins --validate-only --stacktrace
./gradlew verifyProductNamespace
```

Expected: all PASS.

- [ ] **Step 2: Verify implementation publications**

Confirm directories exist:

```text
build/test-plugin-repo/io/github/qigao/simpledsl/simpledsl-build-bootstrap/0.1.0-SNAPSHOT/
build/test-plugin-repo/io/github/qigao/simpledsl/simpledsl-build-logic/0.1.0-SNAPSHOT/
```

Confirm marker modules exist for all eighteen public IDs by listing `build/test-plugin-repo` and matching each plugin ID path converted from dots to slashes.

- [ ] **Step 3: Verify source isolation**

```bash
if grep -RIn 'includeBuild' simpledsl-build-bootstrap simpledsl-build-logic integration-tests/consumer; then
  echo 'includeBuild shortcut found' >&2
  exit 1
fi
bash scripts/verify-product-namespace.sh
```

Expected: no matches and namespace guard PASS.

- [ ] **Step 4: Inspect working tree and commit only real corrections**

```bash
git status --short
```

If files changed because of a concrete verification fix, stage each path explicitly with `git add -- path/to/file` and commit `fix: finalize SimpleDSL publication candidate`. If `git status --short` is empty, create no commit.

- [ ] **Step 5: Open draft PR**

Title:

```text
feat: extract SimpleDSL Gradle plugin platform
```

Body:

```text
- extracts reusable Gradle bootstrap/build logic from qigao/durex
- publishes the new io.github.qigao.simpledsl.* product namespace
- converts public convention scripts into binary Gradle plugins
- fixes SimpleDSL-owned external plugin versions at release build time
- proves plugin marker/artifact consumption through an isolated Maven repository
- validates both Plugin Portal publications
- intentionally leaves qigao/durex cutover for the post-0.1.0 release plan
```

Keep the PR draft until all CI checks pass.

---

## Follow-up after Plugin Portal approval

After `v0.1.0` is approved and visible on the Gradle Plugin Portal, create a separate `qigao/durex` plan. That plan replaces the two local `includeBuild` entries with `io.github.qigao.simpledsl.settings` version `0.1.0`, migrates all module plugin IDs and DSL/task names, runs the entire Spring/Native CI against the published product, and only then deletes `qigao/durex/build-bootstrap` and `qigao/durex/build-logic`.
