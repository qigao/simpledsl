# SimpleDSL 0.3.0 Phase A Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract backend-neutral infrastructure into `simpledsl-core`, migrate the existing Java/Spring product into `simpledsl-java`, and introduce snapshot schema v2 while keeping Android implementation completely out of Phase A.

**Architecture:** The settings plugin and all backend-neutral project infrastructure live in `simpledsl-core`. The Java/Spring backend lives in `simpledsl-java`, depends on core, registers only Java/Spring module types and capabilities, and publishes `io.github.qigao.simpledsl.java`. The removed `io.github.qigao.simpledsl.build` ID is rejected by settings with a migration diagnostic.

**Tech Stack:** Gradle 9.1, Java 21 plugin runtime, Groovy, Kotlin Gradle build scripts, Tomlj, SnakeYAML Engine, JUnit 5, Gradle TestKit, Gradle Plugin Publish plugin.

**Spec:** `docs/superpowers/specs/2026-08-25-java-android-backend-split-design.md`

## Global Constraints

- Phase A implements issue #10 only; no AGP class, AGP dependency, Android DSL, or Android marker may be added.
- Public settings plugin remains `io.github.qigao.simpledsl.settings`.
- Public Java backend becomes `io.github.qigao.simpledsl.java`.
- `io.github.qigao.simpledsl.build` is removed as a marker and retained only as a migration-diagnostic/test string.
- `simpledsl.java` remains valid syntax, becomes optional during settings evaluation, and is required only by the Java backend.
- Internal dependency snapshot schema becomes exactly `2`.
- Internal `platforms`, `libraries`, and `plugins` keep 0.2.0 semantics.
- `simpledsl-core` must have no implementation dependency on Spring Boot, GraalVM, jOOQ, jsonschema2pojo, or AGP.
- `simpledsl-java` depends on `simpledsl-core`; core never depends on Java.
- Java plugin runtime remains Java 21; repository Gradle baseline remains 9.1.
- Real published-consumer and configuration-cache verification remain merge gates.

## Target File Structure

```text
simpledsl-core/
  build.gradle.kts
  src/main/groovy/io/github/qigao/simpledsl/gradle/
    distribution/
    manifest/
    settings/
    catalog/
    capability/
    dependency/
    diagnostics/
    model/
    core/

simpledsl-java/
  build.gradle.kts
  src/main/groovy/io/github/qigao/simpledsl/gradle/java/
    SimpleDslJavaPlugin.groovy
    SimpleDslJavaExtension.groovy
    JavaBuiltinCapabilities.groovy
    internal/
    module/
    feature/
    schema/

integration-tests-java/
  build.gradle.kts
  consumer/
  src/test/groovy/io/github/qigao/simpledsl/PublishedJavaConsumerContractTest.groovy
```

---

### Task 1: Snapshot v2 and Optional Java Policy

**Files:**
- Modify: `simpledsl-build-bootstrap/src/test/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyManifestLoaderTest.groovy`
- Modify: `simpledsl-build-bootstrap/src/test/groovy/io/github/qigao/simpledsl/gradle/settings/SimpleDslSettingsPluginTest.groovy`
- Modify: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyManifestLoader.groovy`
- Modify: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyRegistry.groovy`
- Modify: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyRegistryService.groovy`
- Modify: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/settings/SimpleDslSettingsPlugin.groovy`
- Modify: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/settings/SimpleDslDependenciesTask.groovy`

**Produces:**

```groovy
Integer DependencyRegistry.javaVersionOrNull()
Map<String, Object> DependencyRegistry.snapshot()
```

with exact snapshot shape:

```groovy
[
    schemaVersion: 2,
    policies: javaVersion == null ? [:] : [java: [toolchain: javaVersion]],
    platforms: ...,
    libraries: ...,
    plugins: ...
]
```

- [ ] **Step 1: Write failing manifest tests**

Add:

```groovy
@Test
void 'dependency-only manifest exports no java policy'() {
    File manifest = write('dependencies.toml', '''
[versions]
junit = "6.0.1"
[libraries.junit]
module = "org.junit.jupiter:junit-jupiter"
version.ref = "junit"
''')
    DependencyRegistry registry = DependencyManifestLoader.load(manifest)
    assertNull(registry.javaVersionOrNull())
    assertEquals(2, registry.snapshot().schemaVersion)
    assertEquals([:], registry.snapshot().policies)
}

@Test
void 'java policy is exported under policies java toolchain'() {
    File manifest = write('dependencies.toml', '''
[simpledsl]
java = 25
''')
    Map snapshot = DependencyManifestLoader.load(manifest).snapshot()
    assertEquals([java: [toolchain: 25]], snapshot.policies)
    assertFalse(snapshot.containsKey('javaVersion'))
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :simpledsl-build-bootstrap:test --tests '*DependencyManifestLoaderTest' --no-build-cache --stacktrace
```

Expected: FAIL because missing `simpledsl.java` is currently rejected and schema v1 is exported.

- [ ] **Step 3: Implement schema v2**

Remove the loader-level `missing simpledsl.java` failure. Preserve duplicate/type validation when `simpledsl.java` is present. Add `javaVersionOrNull()` and export `policies.java.toolchain` only when configured.

- [ ] **Step 4: Remove settings-time Java requirement**

Delete the eager `serviceProvider.get().javaVersion()` call. Change `SimpleDslDependenciesTask` to:

```groovy
@Input
abstract Property<String> getJavaPolicy()
```

and set it with:

```groovy
Map javaPolicy = ((snapshot.policies ?: [:]) as Map).get('java') as Map
javaPolicy == null ? 'not configured' : javaPolicy.toolchain.toString()
```

- [ ] **Step 5: Add settings TestKit case for manifest without Java policy**

Apply `io.github.qigao.simpledsl.settings` to a fixture whose root manifest contains only versions/libraries and assert `simpledslDependencies` succeeds and reports Java policy as `not configured`.

- [ ] **Step 6: Verify GREEN**

```bash
./gradlew :simpledsl-build-bootstrap:check --no-build-cache --stacktrace
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add simpledsl-build-bootstrap
git commit -m "feat: introduce dependency snapshot v2"
```

---

### Task 2: Extract the Backend-Neutral Project Core

**Files:**
- Move `catalog/`, `dependency/`, backend-neutral capability classes, common diagnostics, and model classes from `simpledsl-build-logic` into `simpledsl-build-bootstrap`
- Create: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/core/SimpleDslProjectCorePlugin.groovy`
- Create: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/core/SimpleDslBackendGuard.groovy`
- Replace: `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/ModuleKind.groovy`
- Modify: `simpledsl-build-logic/build.gradle.kts`
- Move matching unit tests into bootstrap

**Produces:**

```groovy
abstract class SimpleDslModuleModel {
    abstract Property<String> getBackendId()
    abstract Property<String> getModuleType()
    abstract SetProperty<String> getCapabilities()
    abstract SetProperty<String> getPlatformBindings()
}

static void SimpleDslBackendGuard.claim(Project project, String backendId)
```

`CapabilitySpec.allowedModules` becomes `Set<String>` and builder API becomes `allow(String... moduleTypes)`.

- [ ] **Step 1: Write RED tests**

```groovy
@Test
void 'capability accepts string module ids'() {
    CapabilitySpec spec = CapabilitySpec.builder('web').allow('spring-service').build()
    assertEquals(['spring-service'] as Set, spec.allowedModules)
}

@Test
void 'backend guard rejects a second backend'() {
    Project project = ProjectBuilder.builder().withName('app').build()
    SimpleDslBackendGuard.claim(project, 'java')
    GradleException error = assertThrows(GradleException) {
        SimpleDslBackendGuard.claim(project, 'android')
    }
    assertTrue(error.message.contains('Already-selected backend: java'))
    assertTrue(error.message.contains('Requested backend: android'))
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :simpledsl-build-bootstrap:test --tests '*Capability*' --tests '*BackendGuard*' --no-build-cache --stacktrace
```

Expected: compile/test failure because these interfaces do not exist in bootstrap.

- [ ] **Step 3: Implement string-based model and backend guard**

Guard behavior is idempotent for the same backend and throws for a different backend. Error includes project path, selected backend, and requested backend.

- [ ] **Step 4: Move snapshot/catalog/dependency bridge and require schema 2**

Set:

```groovy
static final int EXPECTED_SCHEMA_VERSION = 2
```

`DependencyCatalogSnapshot` stores nullable Java toolchain and exposes:

```groovy
Integer javaToolchainOrNull()
int requireJavaToolchain(String projectPath)
```

`requireJavaToolchain` throws a `SimpleDSL configuration error` containing the project path and `simpledsl.java` when absent.

- [ ] **Step 5: Implement internal `SimpleDslProjectCorePlugin`**

It registers catalog/model/capability registries/engine/common diagnostics. It must not register built-in product capabilities and must not create the public `simpledsl` extension.

- [ ] **Step 6: Make existing build logic consume bootstrap core**

Add temporarily:

```kotlin
implementation(project(":simpledsl-build-bootstrap"))
```

Remove duplicate source copies after imports compile from bootstrap.

- [ ] **Step 7: Verify GREEN**

```bash
./gradlew :simpledsl-build-bootstrap:check :simpledsl-build-logic:check --no-build-cache --stacktrace
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add simpledsl-build-bootstrap simpledsl-build-logic
git commit -m "refactor: extract backend-neutral project core"
```

---

### Task 3: Create the Independent Java Backend

**Files:**
- Create: `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/java/SimpleDslJavaPlugin.groovy`
- Create: `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/java/SimpleDslJavaExtension.groovy`
- Create: `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/java/JavaBuiltinCapabilities.groovy`
- Move Java/Spring `internal/`, `module/`, `feature/`, and `schema/` classes under Java backend ownership
- Modify: `simpledsl-build-logic/build.gradle.kts`
- Modify matching Java/Spring tests

**Produces:** public implementation class `io.github.qigao.simpledsl.gradle.java.SimpleDslJavaPlugin` and exact module IDs `java-library`, `spring-library`, `spring-service`.

- [ ] **Step 1: Write RED Java backend tests**

Assert applying `SimpleDslJavaPlugin` produces:

```groovy
assertEquals('java', model.backendId.get())
assertTrue(project.extensions.getByName('simpledsl') instanceof SimpleDslJavaExtension)
assertFalse(SimpleDslJavaExtension.methods*.name.contains('androidApplication'))
assertFalse(SimpleDslJavaExtension.methods*.name.contains('androidLibrary'))
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :simpledsl-build-logic:test --tests '*Java*' --no-build-cache --stacktrace
```

Expected: FAIL because Java entry/extension classes do not exist.

- [ ] **Step 3: Implement Java plugin entry**

```groovy
void apply(Project project) {
    SimpleDslBackendGuard.claim(project, 'java')
    project.pluginManager.apply(SimpleDslProjectCorePlugin)
    JavaBuiltinCapabilities.registerAll(project.extensions.getByType(CapabilityRegistry))
    project.extensions.create('simpledsl', SimpleDslJavaExtension, project,
            project.extensions.getByType(SimpleDslModuleModel))
}
```

- [ ] **Step 4: Migrate Java/Spring module plugins to string IDs**

Each module plugin sets exactly one module type. Java base reads:

```groovy
int javaVersion = project.extensions.getByType(DependencyCatalogSnapshot)
        .requireJavaToolchain(project.path)
```

and keeps current Java toolchain/release behavior.

- [ ] **Step 5: Preserve Java extension surface**

`SimpleDslJavaExtension` keeps `library`, `dependency`, `capability`, `javaLibrary`, `springLibrary`, `springService`, `jooqSchema`, `jsonSchema`, persistence APIs, and existing Java/Spring capability methods.

- [ ] **Step 6: Register temporary `.java` marker**

Add:

```kotlin
create("simpleDslJava") {
    id = "io.github.qigao.simpledsl.java"
    implementationClass = "io.github.qigao.simpledsl.gradle.java.SimpleDslJavaPlugin"
    displayName = "SimpleDSL Java"
    description = "SimpleDSL Java and Spring build backend"
    tags = listOf("build-platform", "java", "spring")
    compatibility { features { configurationCache = true } }
}
```

Keep `.build` only until Task 4 migrates settings resolution.

- [ ] **Step 7: Verify GREEN**

```bash
./gradlew :simpledsl-build-logic:check --no-build-cache --stacktrace
```

Expected: PASS, including existing module/feature/schema tests.

- [ ] **Step 8: Commit**

```bash
git add simpledsl-build-logic
git commit -m "feat: add independent Java backend"
```

---

### Task 4: Migrate Plugin Resolution and Remove `.build`

**Files:**
- Modify: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/distribution/SimpleDslDistribution.groovy`
- Modify: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/settings/SimpleDslSettingsPlugin.groovy`
- Modify: `simpledsl-build-bootstrap/src/test/groovy/io/github/qigao/simpledsl/gradle/settings/SimpleDslSettingsPluginTest.groovy`
- Modify: `simpledsl-build-logic/build.gradle.kts`

**Produces:**

```groovy
SETTINGS_PLUGIN_ID = 'io.github.qigao.simpledsl.settings'
JAVA_PLUGIN_ID = 'io.github.qigao.simpledsl.java'
REMOVED_BUILD_PLUGIN_ID = 'io.github.qigao.simpledsl.build'
```

- [ ] **Step 1: Write RED TestKit cases**

Prove:

```text
.java without version -> managed SimpleDSL version
.java with conflicting version -> SimpleDSL version conflict
.build -> migration diagnostic
```

Old-ID diagnostic must contain:

```text
SimpleDSL plugin migration required
Plugin: io.github.qigao.simpledsl.build
Replacement: io.github.qigao.simpledsl.java
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :simpledsl-build-bootstrap:test --tests '*SimpleDslSettingsPluginTest' --no-build-cache --stacktrace
```

Expected: FAIL because settings still resolves `BUILD_PLUGIN_ID`.

- [ ] **Step 3: Implement `.java` resolution and old-ID rejection**

Handle `REMOVED_BUILD_PLUGIN_ID` before generic manifest-managed plugins. Resolve `.java` to the same SimpleDSL release version as settings; reject a conflicting explicit version.

- [ ] **Step 4: Remove `.build` publication marker**

Delete the old `simpleDslBuild` `gradlePlugin` registration after settings migration tests pass.

- [ ] **Step 5: Verify GREEN**

```bash
./gradlew :simpledsl-build-bootstrap:check :simpledsl-build-logic:check --no-build-cache --stacktrace
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add simpledsl-build-bootstrap simpledsl-build-logic
git commit -m "feat: migrate project plugin to Java backend"
```

---

### Task 5: Rename Artifacts and Java Integration Suite

**Files:**
- Rename: `simpledsl-build-bootstrap/` -> `simpledsl-core/`
- Rename: `simpledsl-build-logic/` -> `simpledsl-java/`
- Rename: `integration-tests/` -> `integration-tests-java/`
- Rename: `PublishedConsumerContractTest.groovy` -> `PublishedJavaConsumerContractTest.groovy`
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `simpledsl-java/build.gradle.kts`
- Modify: `simpledsl-core/.../SimpleDslDistribution.groovy`
- Modify: consumer fixture plugin ID

**Produces Maven artifacts:**

```text
io.github.qigao.simpledsl:simpledsl-core
io.github.qigao.simpledsl:simpledsl-java
```

- [ ] **Step 1: Make published marker expectation RED first**

In the current `PublishedConsumerContractTest`, change expected marker IDs to:

```groovy
[
    'io.github.qigao.simpledsl.java',
    'io.github.qigao.simpledsl.settings'
] as Set
```

and assert `.build` is absent. Run the integration test before fixture migration; expected FAIL because the current consumer still requests `.build`.

- [ ] **Step 2: Rename directories using `git mv`**

```bash
git mv simpledsl-build-bootstrap simpledsl-core
git mv simpledsl-build-logic simpledsl-java
git mv integration-tests integration-tests-java
git mv integration-tests-java/src/test/groovy/io/github/qigao/simpledsl/PublishedConsumerContractTest.groovy \
  integration-tests-java/src/test/groovy/io/github/qigao/simpledsl/PublishedJavaConsumerContractTest.groovy
```

- [ ] **Step 3: Update root projects and dependencies**

`settings.gradle.kts` must include exactly:

```kotlin
include("simpledsl-core", "simpledsl-java", "integration-tests-java")
```

Java backend uses:

```kotlin
implementation(project(":simpledsl-core"))
```

Root `publishToTestPluginRepository` depends on core and Java publication tasks.

- [ ] **Step 4: Update distribution artifact coordinates**

```groovy
static final String CORE_ARTIFACT = 'simpledsl-core'
static final String JAVA_ARTIFACT = 'simpledsl-java'

static String javaCoordinate() {
    "${GROUP}:${JAVA_ARTIFACT}:${version()}"
}
```

- [ ] **Step 5: Migrate real consumer**

Change consumer project plugin to:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.java'
}
```

Preserve `springService()` / `web()` behavior and preserve both Test task cache inputs:

```kotlin
inputs.dir(consumerFixture)
inputs.dir(testPluginRepository)
```

- [ ] **Step 6: Run clean structural verification**

```bash
./gradlew clean :simpledsl-core:check :simpledsl-java:check publishToTestPluginRepository :integration-tests-java:test --no-build-cache --stacktrace
```

Expected: PASS.

- [ ] **Step 7: Verify old active artifact names are gone**

```bash
git grep -n 'simpledsl-build-bootstrap\|simpledsl-build-logic' -- ':!docs/superpowers/**' ':!CHANGELOG.md'
```

Expected: no matches.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: rename core and Java implementation projects"
```

---

### Task 6: Add a Real Backend-Isolation Verification Gate

**Files:**
- Create: `buildSrc` is **not** allowed; implement in root `build.gradle.kts` using resolvable publication/configuration metadata already available in the build
- Modify: `build.gradle.kts`
- Modify: `integration-tests-java/src/test/groovy/io/github/qigao/simpledsl/PublishedJavaConsumerContractTest.groovy`
- Modify: `.github/workflows/ci.yml` in Task 7 to invoke the gate

**Produces:** root task `verifyBackendIsolation`.

- [ ] **Step 1: Write RED integration assertion for the missing gate**

Add a contract assertion that the repository verification command includes/invokes `verifyBackendIsolation`, then run:

```bash
./gradlew verifyBackendIsolation --stacktrace
```

Expected: FAIL with `Task 'verifyBackendIsolation' not found`.

- [ ] **Step 2: Implement `verifyBackendIsolation`**

Register a root verification task that depends on publication to the isolated test repository and inspects the generated Maven POM/runtime dependency metadata for `simpledsl-core` and `simpledsl-java`.

The task must fail if core depends on any of:

```text
org.springframework.boot:spring-boot-gradle-plugin
org.graalvm.buildtools:native-gradle-plugin
org.jooq:jooq-codegen-gradle
org.jsonschema2pojo:jsonschema2pojo-gradle-plugin
com.android.tools.build:gradle
```

and must fail if Java depends on:

```text
com.android.tools.build:gradle
```

The Java artifact must have a dependency on:

```text
io.github.qigao.simpledsl:simpledsl-core
```

- [ ] **Step 3: Keep compatibility metadata in core without implementation dependencies**

`simpledsl-core/build.gradle.kts` may generate properties from version-catalog values:

```kotlin
property("springBootPluginVersion", libs.versions.spring.boot.get())
property("graalvmNativePluginVersion", libs.versions.graalvm.native.get())
property("jooqPluginVersion", libs.versions.jooq.get())
property("jsonschema2pojoPluginVersion", libs.versions.jsonschema2pojo.get())
```

but must not declare those modules as core `implementation` dependencies. Java retains the existing implementation dependencies.

- [ ] **Step 4: Verify GREEN**

```bash
./gradlew clean publishToTestPluginRepository verifyBackendIsolation :integration-tests-java:test --no-build-cache --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add build.gradle.kts simpledsl-core simpledsl-java integration-tests-java
git commit -m "test: enforce backend artifact isolation"
```

---

### Task 7: CI, Release Wiring, Documentation, and Exact-Head Gate

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/release.yml`
- Modify: `scripts/verify-product-namespace.sh`
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Modify: `gradle.properties`

- [ ] **Step 1: Update namespace verification script exactly**

Set roots to:

```bash
roots=(
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  gradle
  simpledsl-core
  simpledsl-java
  integration-tests-java
  README.md
)
```

Set plugin builds to:

```bash
plugin_builds=(
  simpledsl-core/build.gradle.kts
  simpledsl-java/build.gradle.kts
)
```

Keep existing legacy Durex and reserved-tag checks unchanged.

- [ ] **Step 2: Update CI exact Phase A command**

```bash
gradle clean \
  verifyProductNamespace \
  :simpledsl-core:check \
  :simpledsl-java:check \
  publishToTestPluginRepository \
  verifyBackendIsolation \
  :integration-tests-java:test \
  --stacktrace
```

No Android task is added.

- [ ] **Step 3: Update release verification/publication paths**

Release verification uses the same Phase A projects with `-PreleaseVersion="$SIMPLEDSL_RELEASE_VERSION"`. Publication command becomes:

```bash
gradle \
  :simpledsl-core:publishPlugins \
  :simpledsl-java:publishPlugins \
  -PreleaseVersion="$SIMPLEDSL_RELEASE_VERSION" \
  --stacktrace
```

Do not create a 0.3.0 tag in Phase A; Phase B must add Android before the final release.

- [ ] **Step 4: Update docs and development version**

Set:

```properties
simpledslVersion=0.3.0-SNAPSHOT
```

README project usage becomes:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.java'
}
```

Document `.build` as removed in 0.3.0 and add an Unreleased/0.3.0 migration note to `CHANGELOG.md` without claiming a published release.

- [ ] **Step 5: Run full clean local verification**

```bash
./gradlew clean \
  verifyProductNamespace \
  :simpledsl-core:check \
  :simpledsl-java:check \
  publishToTestPluginRepository \
  verifyBackendIsolation \
  :integration-tests-java:test \
  --no-build-cache \
  --stacktrace
```

Expected: PASS.

- [ ] **Step 6: Validate Plugin Portal metadata without upload**

```bash
./gradlew \
  :simpledsl-core:publishPlugins \
  :simpledsl-java:publishPlugins \
  -PreleaseVersion=0.3.0 \
  --validate-only \
  --stacktrace
```

Expected: PASS.

- [ ] **Step 7: Run surface scans**

```bash
git grep -n 'io.github.qigao.simpledsl.build' -- ':!docs/superpowers/**' ':!CHANGELOG.md'
git grep -n 'simpledsl-build-bootstrap\|simpledsl-build-logic' -- ':!docs/superpowers/**' ':!CHANGELOG.md'
git grep -n 'com.android.tools.build' -- simpledsl-core simpledsl-java
```

Expected:
- `.build` matches only intentional migration diagnostic/tests;
- old project/artifact names have no active matches;
- no AGP import/dependency exists in core or Java.

- [ ] **Step 8: Commit final Phase A wiring**

```bash
git add .github scripts README.md CHANGELOG.md gradle.properties
git commit -m "chore: complete 0.3.0 phase A migration"
```

- [ ] **Step 9: Open implementation PR linked to #10**

PR body must state that Phase A intentionally contains no Android backend implementation and closes #10 only when merged.

- [ ] **Step 10: Require exact-head CI and final diff review**

Exact PR-head workflow must pass core tests, Java tests, real published Java consumer, configuration-cache contract, `verifyBackendIsolation`, namespace verification, and wrapper metadata.

Reject the PR if any of these are present:

```text
AGP dependency/import in core or Java
public project-side core marker besides settings
retained io.github.qigao.simpledsl.build marker
Java/Spring implementation dependency in core
snapshot schema version 1
settings-time hard requirement for simpledsl.java
```

Only after exact-head CI and this boundary review are green is Phase A ready to merge.
