# SimpleDSL 0.3.0 Phase A Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract backend-neutral SimpleDSL project infrastructure into `simpledsl-core`, migrate the existing Java/Spring product surface into `simpledsl-java`, and introduce snapshot schema v2 without implementing Android yet.

**Architecture:** Phase A keeps one settings-side manifest service and one backend-neutral project core. The core owns manifest/discovery, snapshot/catalog bridging, dependency binding, capability primitives, common model/diagnostics, and backend claiming; the Java artifact owns Java/Spring module types, capabilities, schema helpers, and Java-side third-party tooling. Java consumers migrate from `io.github.qigao.simpledsl.build` to `io.github.qigao.simpledsl.java`; the removed ID fails with a migration diagnostic.

**Tech Stack:** Gradle 9.1, Java 21 plugin runtime, Groovy, Kotlin Gradle build scripts, Tomlj, SnakeYAML Engine, JUnit 5, Gradle TestKit, Gradle Plugin Publish plugin.

**Spec:** `docs/superpowers/specs/2026-08-25-java-android-backend-split-design.md`

## Global Constraints

- This plan implements only Phase A from issue #10; no AGP classes or Android implementation dependencies may be introduced.
- Public project plugin migration is `io.github.qigao.simpledsl.build` -> `io.github.qigao.simpledsl.java`.
- `io.github.qigao.simpledsl.settings` remains the single public settings plugin.
- The old `.build` marker is removed, not retained as a compatibility plugin.
- `simpledsl.java` remains valid manifest syntax but becomes optional during settings evaluation and required only when the Java backend is applied.
- Internal dependency snapshot schema becomes exactly version `2`.
- `platforms`, `libraries`, and `plugins` retain their 0.2.0 snapshot semantics.
- Core must have no implementation dependency on Spring Boot, GraalVM Native Build Tools, jOOQ, jsonschema2pojo, or AGP.
- Java must depend on core; core must not depend on Java.
- Configuration-cache and real published-consumer verification remain release gates.
- Keep Java runtime/toolchain baseline at Java 21 and repository Gradle baseline at 9.1.

---

## File Structure Map

After Phase A the relevant repository shape is:

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
  src/test/groovy/io/github/qigao/simpledsl/gradle/...

simpledsl-java/
  build.gradle.kts
  src/main/groovy/io/github/qigao/simpledsl/gradle/java/
    SimpleDslJavaPlugin.groovy
    SimpleDslJavaExtension.groovy
    internal/
    module/
    capability/
    feature/
    schema/
  src/test/groovy/io/github/qigao/simpledsl/gradle/java/...

integration-tests-java/
  build.gradle.kts
  consumer/
  src/test/groovy/io/github/qigao/simpledsl/PublishedJavaConsumerContractTest.groovy
```

`simpledsl-core` is the implementation artifact for `io.github.qigao.simpledsl.settings` and also contains internal project-side common classes. It does not publish a second project-side marker.

---

### Task 1: Make Java Policy Optional and Introduce Snapshot Schema v2

**Files:**
- Modify: `simpledsl-build-bootstrap/src/test/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyManifestLoaderTest.groovy`
- Modify: `simpledsl-build-bootstrap/src/test/groovy/io/github/qigao/simpledsl/gradle/settings/SimpleDslSettingsPluginTest.groovy`
- Modify: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyManifestLoader.groovy`
- Modify: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyRegistry.groovy`
- Modify: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyRegistryService.groovy`
- Modify: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/settings/SimpleDslSettingsPlugin.groovy`
- Modify: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/settings/SimpleDslDependenciesTask.groovy`

**Interfaces:**
- Produces: `DependencyRegistry.javaVersionOrNull(): Integer`
- Produces snapshot shape:
  ```groovy
  [
      schemaVersion: 2,
      policies: javaVersion == null ? [:] : [java: [toolchain: javaVersion]],
      platforms: ...,
      libraries: ...,
      plugins: ...
  ]
  ```
- Settings evaluation no longer calls a method that requires Java policy to exist.

- [ ] **Step 1: Write failing manifest tests for schema v2 and optional Java policy**

Add tests equivalent to:

```groovy
@Test
void 'manifest without simpledsl java is valid and exports no java policy'() {
    File manifest = write('dependencies.toml', '''
[versions]
junit = "6.0.1"

[libraries.junit]
module = "org.junit.jupiter:junit-jupiter"
version.ref = "junit"
''')

    DependencyRegistry registry = DependencyManifestLoader.load(manifest)
    assertNull(registry.javaVersionOrNull())

    Map snapshot = registry.snapshot()
    assertEquals(2, snapshot.schemaVersion)
    assertEquals([:], snapshot.policies)
}

@Test
void 'simpledsl java is exported as java toolchain policy in schema v2'() {
    File manifest = write('dependencies.toml', '''
[simpledsl]
java = 25
''')

    Map snapshot = DependencyManifestLoader.load(manifest).snapshot()
    assertEquals(2, snapshot.schemaVersion)
    assertEquals([java: [toolchain: 25]], snapshot.policies)
}
```

Update existing snapshot assertions so they no longer expect top-level `javaVersion`.

- [ ] **Step 2: Run the focused manifest test and verify RED**

Run:

```bash
./gradlew :simpledsl-build-bootstrap:test --tests '*DependencyManifestLoaderTest' --no-build-cache --stacktrace
```

Expected: FAIL because the loader still rejects missing `simpledsl.java` and the registry still exports schema v1/top-level `javaVersion`.

- [ ] **Step 3: Implement optional Java policy and schema v2**

Change the loader so this block is removed:

```groovy
if (state.javaVersion == null) {
    fail(rootManifest, null, null, 'missing simpledsl.java')
}
```

Keep duplicate/type validation when `[simpledsl].java` is present.

In `DependencyRegistry`, replace required Java access with:

```groovy
Integer javaVersionOrNull() {
    javaVersion
}

Map<String, Object> snapshot() {
    Map<String, Object> policies = new LinkedHashMap<>()
    if (javaVersion != null) {
        policies.put('java', [toolchain: javaVersion])
    }
    [
        schemaVersion: 2,
        policies: policies,
        platforms: snapshotPlatforms(),
        libraries: snapshotLibraries(),
        plugins: snapshotPlugins()
    ]
}
```

Do not introduce an Android policy node in Phase A.

- [ ] **Step 4: Make settings diagnostics tolerate absent Java policy**

Remove the eager settings-time requirement:

```groovy
serviceProvider.get().javaVersion()
```

Update `SimpleDslDependenciesTask` from a required integer input to an optional textual diagnostic, for example:

```groovy
@Input
abstract Property<String> getJavaPolicy()
```

and configure it from snapshot v2:

```groovy
Map javaPolicy = (snapshot.policies as Map).get('java') as Map
String javaLine = javaPolicy == null ? 'not configured' : javaPolicy.toolchain.toString()
task.javaPolicy.set(javaLine)
```

- [ ] **Step 5: Add settings TestKit coverage for dependency-only manifests**

Add a TestKit case that applies `io.github.qigao.simpledsl.settings` with a root manifest containing only `[versions]`/`[libraries]` and verifies `help` or `simpledslDependencies` succeeds.

- [ ] **Step 6: Run bootstrap tests and verify GREEN**

Run:

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

### Task 2: Extract Backend-Neutral Project Core from Build Logic

**Files:**
- Move from `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/catalog/` to `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/catalog/`
- Move from `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/dependency/` to `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/dependency/`
- Move backend-neutral files from `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/capability/` to bootstrap
- Move backend-neutral diagnostics/model files to bootstrap
- Create: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/core/SimpleDslProjectCorePlugin.groovy`
- Create: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/core/SimpleDslBackendGuard.groovy`
- Create: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/model/SimpleDslModuleModel.groovy`
- Modify: `simpledsl-build-logic/build.gradle.kts`
- Move/adapt tests from build logic to bootstrap for the moved classes

**Interfaces:**
- Produces `SimpleDslModuleModel` properties:
  ```groovy
  abstract Property<String> getBackendId()
  abstract Property<String> getModuleType()
  abstract SetProperty<String> getCapabilities()
  abstract SetProperty<String> getPlatformBindings()
  ```
- Produces `SimpleDslBackendGuard.claim(Project project, String backendId)`.
- Produces internal `SimpleDslProjectCorePlugin` which registers catalog/model/capability/diagnostic infrastructure but does **not** create the public `simpledsl` extension.
- `CapabilitySpec.allowedModules` becomes `Set<String>`.

- [ ] **Step 1: Write RED tests for backend-neutral module IDs and backend claiming**

Create tests in bootstrap such as:

```groovy
@Test
void 'capability allows string module ids'() {
    CapabilitySpec spec = CapabilitySpec.builder('web')
            .allow('spring-service')
            .build()
    assertEquals(['spring-service'] as Set, spec.allowedModules)
}

@Test
void 'backend guard rejects second different backend'() {
    Project project = ProjectBuilder.builder().withName('app').build()
    SimpleDslBackendGuard.claim(project, 'java')

    GradleException error = assertThrows(GradleException) {
        SimpleDslBackendGuard.claim(project, 'android')
    }
    assertTrue(error.message.contains("already-selected backend: java"))
    assertTrue(error.message.contains("requested backend: android"))
}
```

- [ ] **Step 2: Run focused bootstrap tests and verify RED**

Run:

```bash
./gradlew :simpledsl-build-bootstrap:test --tests '*Capability*' --tests '*BackendGuard*' --no-build-cache --stacktrace
```

Expected: compile/test failure because the string-based interfaces and guard do not exist.

- [ ] **Step 3: Refactor `CapabilitySpec` to string module IDs**

Replace:

```groovy
Set<ModuleKind> allowedModules
Builder allow(ModuleKind... kinds)
```

with:

```groovy
Set<String> allowedModules
Builder allow(String... moduleTypes) {
    allowedModules.addAll(Arrays.asList(moduleTypes))
    this
}
```

Preserve sorting/immutability behavior.

- [ ] **Step 4: Replace `ModuleKind` with backend-neutral model properties**

Create the model with Gradle managed properties and conventions initialized by the core plugin:

```groovy
model.capabilities.convention(Collections.emptySet())
model.platformBindings.convention(Collections.emptySet())
```

`backendId` and `moduleType` remain unset until a backend/module claims them.

- [ ] **Step 5: Implement backend guard**

Use an extra-property/internal extension value owned per `Project`. The behavior must be idempotent for the same backend and fail for a different one:

```groovy
static void claim(Project project, String requested) {
    def extra = project.extensions.extraProperties
    String key = 'io.github.qigao.simpledsl.backend'
    String selected = extra.has(key) ? extra.get(key) as String : null
    if (selected == null) {
        extra.set(key, requested)
        return
    }
    if (selected != requested) {
        throw new GradleException(
            "SimpleDSL backend conflict\n" +
            "Project: ${project.path}\n" +
            "Already-selected backend: ${selected}\n" +
            "Requested backend: ${requested}")
    }
}
```

- [ ] **Step 6: Move the catalog/snapshot bridge and adapt it to schema v2**

`SimpleDslRegistryBridge.EXPECTED_SCHEMA_VERSION` becomes `2`. Parse:

```groovy
Map policies = table(raw, 'policies')
Map javaPolicy = optionalEntry(policies.get('java'), "policy 'java'")
Integer javaToolchain = javaPolicy == null ? null : requiredInteger(javaPolicy, 'toolchain', "policy 'java'")
```

`DependencyCatalogSnapshot` stores optional Java policy instead of a required Java integer and exposes:

```groovy
Integer javaToolchainOrNull()
int requireJavaToolchain(String projectPath)
```

`requireJavaToolchain` throws a targeted configuration error mentioning the project path and `simpledsl.java`.

- [ ] **Step 7: Implement `SimpleDslProjectCorePlugin`**

It applies `SimpleDslCatalogPlugin`, creates the model, registers empty capability registries/engine, and common tasks. It must not call `BuiltinCapabilities.registerAll(...)` and must not create a `simpledsl` extension.

Common diagnostic task fields become string-based `backendId`/`moduleType`; Java-specific toolchain text is optional.

- [ ] **Step 8: Make build logic depend on bootstrap/core classes temporarily**

In `simpledsl-build-logic/build.gradle.kts` add:

```kotlin
implementation(project(":simpledsl-build-bootstrap"))
```

Remove duplicated source files after their imports compile from bootstrap.

- [ ] **Step 9: Run both module test suites**

Run:

```bash
./gradlew :simpledsl-build-bootstrap:check :simpledsl-build-logic:check --no-build-cache --stacktrace
```

Expected: PASS after adapting existing validators/tests from enum module kinds to string IDs.

- [ ] **Step 10: Commit**

```bash
git add simpledsl-build-bootstrap simpledsl-build-logic
git commit -m "refactor: extract backend-neutral project core"
```

---

### Task 3: Create the Java Backend Entry Point and Java-Only Extension

**Files:**
- Create: `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/java/SimpleDslJavaPlugin.groovy`
- Create: `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/java/SimpleDslJavaExtension.groovy`
- Move/adapt: existing Java/Spring module, feature, internal, and schema classes under the Java package/module ownership
- Modify: `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/capability/BuiltinCapabilities.groovy` (or replace with Java-owned registry class)
- Modify: Java module tests
- Modify: `simpledsl-build-logic/build.gradle.kts`

**Interfaces:**
- Public plugin implementation class: `io.github.qigao.simpledsl.gradle.java.SimpleDslJavaPlugin`.
- Java backend claims backend ID `java`.
- Java module IDs are exactly `java-library`, `spring-library`, `spring-service`.
- Java extension preserves methods `javaLibrary()`, `springLibrary()`, `springService()`, `jooqSchema()`, `jsonSchema()`, Java/Spring capabilities, and explicit dependency helpers.

- [ ] **Step 1: Write RED tests for Java backend claim and extension isolation**

Add tests that apply `SimpleDslJavaPlugin` via `ProjectBuilder` and assert:

```groovy
assertEquals('java', model.backendId.get())
assertNotNull(project.extensions.findByName('simpledsl'))
assertTrue(project.extensions.getByName('simpledsl') instanceof SimpleDslJavaExtension)
```

Also assert the Java extension class has no methods named `androidApplication` or `androidLibrary`.

- [ ] **Step 2: Run Java tests and verify RED**

Run:

```bash
./gradlew :simpledsl-build-logic:test --tests '*Java*' --no-build-cache --stacktrace
```

Expected: FAIL because the Java-specific public entry point/extension does not exist.

- [ ] **Step 3: Implement Java plugin bootstrap**

The plugin order is:

```groovy
void apply(Project project) {
    SimpleDslBackendGuard.claim(project, 'java')
    project.pluginManager.apply(SimpleDslProjectCorePlugin)
    registerJavaCapabilities(project)
    project.extensions.create('simpledsl', SimpleDslJavaExtension, project,
            project.extensions.getByType(SimpleDslModuleModel))
}
```

Register Java/Spring capabilities only from this backend.

- [ ] **Step 4: Make Java module plugins use string module IDs and required Java policy**

Each module plugin must fail if a different module type is already selected and set one of the exact IDs. Java base setup reads:

```groovy
int javaVersion = project.extensions.getByType(DependencyCatalogSnapshot)
        .requireJavaToolchain(project.path)
```

and configures the existing Java toolchain/release behavior unchanged.

- [ ] **Step 5: Keep Java/Spring behavior tests green**

Adapt existing `ModuleTypePluginsTest`, `FeaturePluginsTest`, `SchemaPluginConfigurationTest`, and doctor tests to the new Java extension/model IDs without weakening assertions.

- [ ] **Step 6: Add the `.java` marker while `.build` still exists temporarily**

In `gradlePlugin.plugins` create:

```kotlin
create("simpleDslJava") {
    id = "io.github.qigao.simpledsl.java"
    implementationClass = "io.github.qigao.simpledsl.gradle.java.SimpleDslJavaPlugin"
    displayName = "SimpleDSL Java"
    description = "SimpleDSL Java and Spring build backend"
    tags = listOf("build-platform", "java", "spring")
    compatibility {
        features {
            configurationCache = true
        }
    }
}
```

Do not remove `.build` until settings resolution and the published consumer are migrated in later tasks.

- [ ] **Step 7: Run build-logic checks**

Run:

```bash
./gradlew :simpledsl-build-logic:check --no-build-cache --stacktrace
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add simpledsl-build-logic
git commit -m "feat: add independent Java backend"
```

---

### Task 4: Teach Settings Resolution About the Java Backend and Retire `.build`

**Files:**
- Modify: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/distribution/SimpleDslDistribution.groovy`
- Modify: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/settings/SimpleDslSettingsPlugin.groovy`
- Modify: `simpledsl-build-bootstrap/src/test/groovy/io/github/qigao/simpledsl/gradle/settings/SimpleDslSettingsPluginTest.groovy`
- Modify: `simpledsl-build-logic/build.gradle.kts`

**Interfaces:**
- `JAVA_PLUGIN_ID = 'io.github.qigao.simpledsl.java'`.
- `JAVA_ARTIFACT = 'simpledsl-java'` after Task 5 rename; until then the coordinate helper may still point at `simpledsl-build-logic` and is renamed atomically with Task 5.
- Removed ID `io.github.qigao.simpledsl.build` must fail with migration guidance.

- [ ] **Step 1: Write RED settings tests for `.java`, version conflicts, and old-ID migration error**

Add TestKit cases that prove:

```text
io.github.qigao.simpledsl.java without version -> managed SimpleDSL version
io.github.qigao.simpledsl.java with conflicting explicit version -> SimpleDSL version conflict
io.github.qigao.simpledsl.build -> explicit migration error mentioning io.github.qigao.simpledsl.java
```

The old-ID error should contain:

```text
SimpleDSL plugin migration required
Plugin: io.github.qigao.simpledsl.build
Replacement: io.github.qigao.simpledsl.java
```

- [ ] **Step 2: Run focused settings tests and verify RED**

Run:

```bash
./gradlew :simpledsl-build-bootstrap:test --tests '*SimpleDslSettingsPluginTest' --no-build-cache --stacktrace
```

Expected: FAIL because the resolution strategy only knows `BUILD_PLUGIN_ID`.

- [ ] **Step 3: Replace build-plugin distribution constants with Java-backend constants**

Define:

```groovy
static final String SETTINGS_PLUGIN_ID = 'io.github.qigao.simpledsl.settings'
static final String JAVA_PLUGIN_ID = 'io.github.qigao.simpledsl.java'
static final String REMOVED_BUILD_PLUGIN_ID = 'io.github.qigao.simpledsl.build'
```

and a Java coordinate helper.

- [ ] **Step 4: Update settings resolution strategy**

Handle the removed ID before generic manifest plugins:

```groovy
if (pluginId == SimpleDslDistribution.REMOVED_BUILD_PLUGIN_ID) {
    throw new GradleException(
        'SimpleDSL plugin migration required\n' +
        "Plugin: ${pluginId}\n" +
        "Replacement: ${SimpleDslDistribution.JAVA_PLUGIN_ID}")
}
```

Handle `.java` using the exact settings-plugin release version and reject an explicitly conflicting version.

- [ ] **Step 5: Remove the old `.build` marker from Gradle plugin publication**

Delete the old `simpleDslBuild` registration only after the settings tests above pass with the migration behavior.

- [ ] **Step 6: Run bootstrap + build-logic checks**

Run:

```bash
./gradlew :simpledsl-build-bootstrap:check :simpledsl-build-logic:check --no-build-cache --stacktrace
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add simpledsl-build-bootstrap simpledsl-build-logic
git commit -m "feat: migrate project plugin to Java backend"
```

---

### Task 5: Rename Implementation Projects to `simpledsl-core` and `simpledsl-java`

**Files:**
- Rename directory: `simpledsl-build-bootstrap/` -> `simpledsl-core/`
- Rename directory: `simpledsl-build-logic/` -> `simpledsl-java/`
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `simpledsl-core/build.gradle.kts`
- Modify: `simpledsl-java/build.gradle.kts`
- Modify: `simpledsl-core/src/main/groovy/io/github/qigao/simpledsl/gradle/distribution/SimpleDslDistribution.groovy`
- Modify all Gradle task/project-path references in `.github/workflows/ci.yml`, `.github/workflows/release.yml`, tests, README, scripts

**Interfaces:**
- Maven implementation artifacts become exactly:
  ```text
  io.github.qigao.simpledsl:simpledsl-core
  io.github.qigao.simpledsl:simpledsl-java
  ```
- Project dependency becomes `implementation(project(":simpledsl-core"))`.

- [ ] **Step 1: Add a repository topology assertion before renaming**

In the published-consumer/metadata test area, add an assertion that the implementation publications expected after migration are exactly `simpledsl-core` and `simpledsl-java`. Run it before the rename and verify it fails against the old artifact names.

- [ ] **Step 2: Rename the two directories in one commit-sized operation**

Use `git mv` so history remains visible:

```bash
git mv simpledsl-build-bootstrap simpledsl-core
git mv simpledsl-build-logic simpledsl-java
```

- [ ] **Step 3: Update root project inclusion and test-repository publication aggregation**

`settings.gradle.kts` becomes:

```kotlin
include("simpledsl-core", "simpledsl-java", "integration-tests")
```

At this task keep the integration test project name unchanged until Task 6.

Root test publication aggregation must depend on:

```kotlin
":simpledsl-core:publishAllPublicationsToTestPluginRepository"
":simpledsl-java:publishAllPublicationsToTestPluginRepository"
```

- [ ] **Step 4: Update Java project dependency and distribution coordinates**

Set Java module dependency:

```kotlin
implementation(project(":simpledsl-core"))
```

Set distribution helpers:

```groovy
static final String CORE_ARTIFACT = 'simpledsl-core'
static final String JAVA_ARTIFACT = 'simpledsl-java'

static String javaCoordinate() {
    "${GROUP}:${JAVA_ARTIFACT}:${version()}"
}
```

- [ ] **Step 5: Update CI/release task paths only for renamed projects**

Do not add Android tasks. Replace old bootstrap/build-logic paths with core/java paths while preserving the same verification semantics.

- [ ] **Step 6: Run clean compilation and tests after the structural rename**

Run:

```bash
./gradlew clean :simpledsl-core:check :simpledsl-java:check publishToTestPluginRepository --no-build-cache --stacktrace
```

Expected: PASS.

- [ ] **Step 7: Verify old artifact/project names are gone from active build configuration**

Run:

```bash
git grep -n 'simpledsl-build-bootstrap\|simpledsl-build-logic' -- ':!docs/superpowers/**' ':!CHANGELOG.md'
```

Expected: no active build/runtime matches.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: rename core and Java implementation projects"
```

---

### Task 6: Migrate the Real Published Java Consumer

**Files:**
- Rename directory: `integration-tests/` -> `integration-tests-java/`
- Rename test: `PublishedConsumerContractTest.groovy` -> `PublishedJavaConsumerContractTest.groovy`
- Modify: `integration-tests-java/consumer/app/build.gradle`
- Modify: `integration-tests-java/consumer/settings.gradle`
- Modify: `integration-tests-java/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Modify: root publication aggregation/CI inputs as required

**Interfaces:**
- Consumer project applies `io.github.qigao.simpledsl.java`.
- Published marker set for Phase A is exactly:
  ```text
  io.github.qigao.simpledsl.settings
  io.github.qigao.simpledsl.java
  ```
  with `.build` absent. The final `.android` marker is added only by Phase B.

- [ ] **Step 1: Change the published-consumer marker assertion first and verify RED**

Update the contract expectation to settings + java only and explicitly reject `.build`:

```groovy
assertEquals([
    'io.github.qigao.simpledsl.java',
    'io.github.qigao.simpledsl.settings'
] as Set, markerIds)
assertFalse(markerIds.contains('io.github.qigao.simpledsl.build'))
```

Run the integration test before fixture migration; expected failure is that the old consumer still requests `.build` or the publication set is inconsistent.

- [ ] **Step 2: Rename the integration project and fixture**

```bash
git mv integration-tests integration-tests-java
```

Update root inclusion to `integration-tests-java`.

- [ ] **Step 3: Migrate consumer build plugin ID**

Change:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.build'
}
```

into:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.java'
}
```

Do not change the existing `simpledsl { springService(); web() }` semantics.

- [ ] **Step 4: Preserve fresh published-repository and fixture cache inputs**

Keep both inputs in `integration-tests-java/build.gradle.kts`:

```kotlin
inputs.dir(consumerFixture)
inputs.dir(testPluginRepository)
```

so stale Gradle build cache cannot hide a broken published consumer.

- [ ] **Step 5: Add artifact dependency-isolation assertion for Java**

Inspect the resolved published `simpledsl-java` runtime/POM graph in the test repository and assert no module group/name matches AGP:

```text
com.android.tools.build:gradle
```

This should be a concrete artifact-resolution assertion, not a source-code grep.

- [ ] **Step 6: Run real published consumer twice for configuration-cache proof**

Run:

```bash
./gradlew clean publishToTestPluginRepository :integration-tests-java:test --no-build-cache --stacktrace
./gradlew :integration-tests-java:test --no-build-cache --stacktrace
```

Expected: both PASS; TestKit assertions must confirm configuration-cache reuse on the second consumer invocation.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "test: migrate published Java consumer"
```

---

### Task 7: Enforce Core/Java Dependency Isolation and Publication Metadata

**Files:**
- Modify: `simpledsl-core/build.gradle.kts`
- Modify: `simpledsl-java/build.gradle.kts`
- Modify: `gradle/libs.versions.toml` only if metadata keys need centralization
- Modify: `simpledsl-core/src/main/groovy/io/github/qigao/simpledsl/gradle/distribution/SimpleDslDistribution.groovy`
- Modify: generated `distribution.properties` configuration in core
- Add tests under `simpledsl-core/src/test/.../distribution/` and/or `integration-tests-java`

**Interfaces:**
- Core distribution metadata contains version strings/coordinates needed at settings resolution time, but core POM/runtime dependencies do not carry Java product tooling.
- Java runtime carries Spring/GraalVM/jOOQ/jsonschema2pojo as required by existing Java implementation.

- [ ] **Step 1: Write RED tests that distinguish compatibility metadata from implementation dependencies**

Assert core can answer managed plugin version/coordinate metadata for existing Java-owned external plugins, while the published core runtime graph contains none of:

```text
org.springframework.boot:spring-boot-gradle-plugin
org.graalvm.buildtools:native-gradle-plugin
org.jooq:jooq-codegen-gradle
org.jsonschema2pojo:jsonschema2pojo-gradle-plugin
```

- [ ] **Step 2: Run publication/isolation test and verify RED if any dependency leaked during extraction**

Run:

```bash
./gradlew clean publishToTestPluginRepository :integration-tests-java:test --no-build-cache --stacktrace
```

Expected before fixes: fail if core inherited any Java implementation dependency.

- [ ] **Step 3: Keep compatibility values as generated metadata only**

`simpledsl-core/build.gradle.kts` may read version-catalog values to generate properties:

```kotlin
property("springBootPluginVersion", libs.versions.spring.boot.get())
property("graalvmNativePluginVersion", libs.versions.graalvm.native.get())
property("jooqPluginVersion", libs.versions.jooq.get())
property("jsonschema2pojoPluginVersion", libs.versions.jsonschema2pojo.get())
```

but must not declare those plugin modules as `implementation(...)` dependencies.

- [ ] **Step 4: Verify Java owns implementation dependencies**

`simpledsl-java/build.gradle.kts` retains the Java-side implementation dependencies and `implementation(project(":simpledsl-core"))`.

- [ ] **Step 5: Run publication contract again**

Run:

```bash
./gradlew clean publishToTestPluginRepository :integration-tests-java:test --no-build-cache --stacktrace
```

Expected: PASS with core graph clean and Java behavior functional.

- [ ] **Step 6: Commit**

```bash
git add simpledsl-core simpledsl-java integration-tests-java gradle/libs.versions.toml
git commit -m "test: enforce backend artifact isolation"
```

---

### Task 8: Update CI, Release Wiring, Docs, and Perform Exact-Head Verification

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/release.yml`
- Modify: `README.md`
- Modify: `CHANGELOG.md` only with an `Unreleased`/0.3.0 migration note, not a release tag claim
- Modify: `scripts/verify-product-namespace.sh` if it encodes the old marker/artifact names
- Modify: `gradle.properties` to `simpledslVersion=0.3.0-SNAPSHOT`

**Interfaces:**
- CI verifies core, Java, published Java consumer, wrapper, and marker/artifact isolation.
- Release workflow publishes core/settings and Java marker/implementation using tag-derived `releaseVersion`.

- [ ] **Step 1: Update CI project paths and exact Phase A gates**

The CI verification command must include:

```bash
gradle clean \
  verifyProductNamespace \
  :simpledsl-core:check \
  :simpledsl-java:check \
  publishToTestPluginRepository \
  :integration-tests-java:test \
  --stacktrace
```

Do not add Android tasks in Phase A.

- [ ] **Step 2: Update release publication paths**

Release verification uses the same Phase A project set with `-PreleaseVersion="$SIMPLEDSL_RELEASE_VERSION"`.

Publication step publishes:

```bash
gradle \
  :simpledsl-core:publishPlugins \
  :simpledsl-java:publishPlugins \
  -PreleaseVersion="$SIMPLEDSL_RELEASE_VERSION" \
  --stacktrace
```

Phase A is not itself tagged as 0.3.0; Phase B must add Android publication before the final 0.3.0 release tag is created.

- [ ] **Step 3: Update README migration examples**

Show:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.settings' version '0.3.0-SNAPSHOT'
}
```

for development docs where appropriate, and project-side:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.java'
}
```

Document that `.build` was the 0.2.x ID and is removed in 0.3.0.

- [ ] **Step 4: Advance development version**

Set:

```properties
simpledslVersion=0.3.0-SNAPSHOT
```

- [ ] **Step 5: Run the full clean local verification contract**

Run exactly:

```bash
./gradlew clean \
  verifyProductNamespace \
  :simpledsl-core:check \
  :simpledsl-java:check \
  publishToTestPluginRepository \
  :integration-tests-java:test \
  --no-build-cache \
  --stacktrace
```

Expected: PASS.

- [ ] **Step 6: Validate publication metadata without uploading**

Run:

```bash
./gradlew \
  :simpledsl-core:publishPlugins \
  :simpledsl-java:publishPlugins \
  -PreleaseVersion=0.3.0 \
  --validate-only \
  --stacktrace
```

Expected: PASS; this validates metadata only and does not publish a release.

- [ ] **Step 7: Run repository surface scans**

Run:

```bash
git grep -n 'io.github.qigao.simpledsl.build' -- ':!docs/superpowers/**' ':!CHANGELOG.md'
git grep -n 'simpledsl-build-bootstrap\|simpledsl-build-logic' -- ':!docs/superpowers/**' ':!CHANGELOG.md'
git grep -n 'com.android.tools.build' -- simpledsl-core simpledsl-java
```

Expected:
- old `.build` appears only in the intentional migration-diagnostic/test strings;
- old project/artifact names have no active runtime/build references;
- no AGP dependency/import exists in either Phase A product module.

- [ ] **Step 8: Commit**

```bash
git add .github README.md CHANGELOG.md scripts gradle.properties
git commit -m "chore: complete 0.3.0 phase A migration"
```

- [ ] **Step 9: Push implementation branch and open a PR linked to #10**

PR summary must state that Android is intentionally absent and Phase A proves the independent Java backend/core split only.

- [ ] **Step 10: Require exact-head GitHub Actions success before merge**

Verify the workflow associated with the final PR head SHA completes successfully and that its job steps include core tests, Java tests, the real published Java consumer, marker/artifact isolation, and wrapper metadata.

- [ ] **Step 11: Review the final PR diff for boundary violations**

Reject the PR if any of these are present:

```text
AGP implementation dependency in core or Java
public core project plugin marker besides settings
retained io.github.qigao.simpledsl.build marker
Java/Spring implementation dependency moved into core
snapshot schema still version 1
settings-time hard requirement for simpledsl.java
```

Only after this review and exact-head CI are green is Phase A ready to merge.
