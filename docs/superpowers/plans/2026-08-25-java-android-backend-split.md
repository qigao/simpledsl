# Java and Android Backend Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn SimpleDSL 0.3.0 into one shared repository-policy core with independent Java and Android project backends, while preserving the existing Java/Spring behavior and adding a real AGP 9.0.1 application/library published-consumer contract.

**Architecture:** `simpledsl-core` owns settings, manifest parsing, snapshot v2, catalog/dependency bridges, backend-neutral capability/model infrastructure, backend ownership guard, and common diagnostics. `simpledsl-java` and `simpledsl-android` both depend on core and never on each other; only the Java artifact carries Spring/GraalVM/jOOQ/jsonschema2pojo tooling and only the Android artifact carries AGP. The existing root TOML/YAML dependency policy is shared by both backends.

**Tech Stack:** Gradle 9.1.0, Groovy, Java 21 plugin runtime, Java 25 CI runtime, Tomlj, SnakeYAML Engine, Gradle TestKit, JUnit 5, Android Gradle Plugin 9.0.1, Android SDK 36, AGP public DSL and Android Components APIs.

**Spec:** `docs/superpowers/specs/2026-08-25-java-android-backend-split-design.md`

## Global Constraints

- Public 0.3.0 plugin IDs are exactly `io.github.qigao.simpledsl.settings`, `io.github.qigao.simpledsl.java`, and `io.github.qigao.simpledsl.android`.
- `io.github.qigao.simpledsl.build` is removed and must fail with migration guidance to `.java`.
- Java and Android backend artifacts never depend on one another.
- `simpledsl-core` has no Spring Boot, GraalVM, jOOQ, jsonschema2pojo, or AGP implementation dependency.
- Java policy remains `[simpledsl] java = <int>`; Android policy is `[simpledsl.android]` with `java`, `compile-sdk`, `min-sdk`, and optional `target-sdk`.
- `androidApplication()` requires `target-sdk`; `androidLibrary()` does not.
- Snapshot protocol is schema version 2; no v1/v2 cross-version compatibility shim is added.
- Android foundation pins AGP `9.0.1`, Gradle `9.1.0`, Android SDK `36`, and uses AGP 9 built-in Kotlin.
- Do not use `BaseExtension`, `AppExtension`, `applicationVariants`, task-name guessing, or `org.jetbrains.kotlin.android`.
- `compose()` is not implemented by this plan; it gets a separate plan after the foundation is merged.
- Every semantic change starts with a failing test; every task ends with a focused green test run and commit.

---

## File Structure Map

### Final modules

- `simpledsl-core/`: settings plugin, manifest readers/loader, distribution metadata, snapshot service, project-side catalog bridge, dependency binding, backend-neutral model/capability infrastructure, backend guard, common diagnostics.
- `simpledsl-java/`: Java/Spring public plugin and extension, Java/Spring module types, Java/Spring capabilities/features, schema helpers, Spring/GraalVM/jOOQ/jsonschema2pojo implementation dependencies.
- `simpledsl-android/`: Android public plugin and extension, application/library module configuration, AGP public DSL adapters, Android Components variant diagnostics, AGP implementation dependency.
- `integration-tests-java/`: published Java/Spring consumer copied from the current integration fixture and migrated to `.java`.
- `integration-tests-android/`: published Android application/library consumer using Android-only policy.

### Shared core interfaces established by this plan

```groovy
final class SimpleDslProjectModel {
    Property<String> getBackendId()
    Property<String> getModuleType()
    SetProperty<String> getCapabilities()
    SetProperty<String> getPlatformBindings()
}

final class SimpleDslBackendGuard {
    void claim(String backendId)
    String selectedBackend()
}

final class BackendPolicySnapshot {
    Integer javaToolchain()
    AndroidPolicy android()
}

final class AndroidPolicy {
    final int javaVersion
    final int compileSdk
    final int minSdk
    final Integer targetSdk
}
```

`CapabilitySpec.allowedModules` becomes `Set<String>` and the builder exposes `allow(String... moduleTypes)`.

---

### Task 1: Introduce snapshot schema v2 and optional backend policies in place

**Files:**
- Modify: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyManifestLoader.groovy`
- Modify: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyRegistry.groovy`
- Modify: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyRegistryService.groovy`
- Create: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest/AndroidPolicy.groovy`
- Modify: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/settings/SimpleDslSettingsPlugin.groovy`
- Modify: `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/catalog/DependencyCatalogSnapshot.groovy`
- Modify: `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/catalog/SimpleDslRegistryBridge.groovy`
- Test: `simpledsl-build-bootstrap/src/test/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyManifestLoaderTest.groovy`
- Test: `simpledsl-build-logic/src/test/groovy/io/github/qigao/simpledsl/gradle/catalog/SimpleDslRegistryBridgeTest.groovy`

**Interfaces:**
- Consumes: existing TOML/YAML normalized `Map` model and existing library/plugin/platform snapshot tables.
- Produces: schema-v2 snapshot with `policies.java.toolchain` and optional `policies.android.{java,compileSdk,minSdk,targetSdk}`; `DependencyCatalogSnapshot` exposes Java and Android policy access without requiring Java globally.

- [ ] **Step 1: Write failing manifest tests for Java-only, Android-only, mixed, and library-only Android policy**

Add tests equivalent to:

```groovy
@Test
void loadsAndroidOnlyPolicyWithoutJavaPolicy() {
    File manifest = write('dependencies.toml', '''
[simpledsl.android]
java = 21
compile-sdk = 36
min-sdk = 24
''')

    Map snapshot = DependencyManifestLoader.load(manifest).snapshot()

    assertEquals(2, snapshot.schemaVersion)
    assertFalse((snapshot.policies as Map).containsKey('java'))
    assertEquals([
            java: 21,
            compileSdk: 36,
            minSdk: 24
    ], snapshot.policies.android)
}
```

Also assert `target-sdk` is optional in the parser, invalid ordering fails, unknown Android keys fail, and existing `[simpledsl] java = 25` maps to `policies.java.toolchain == 25`.

- [ ] **Step 2: Run the focused bootstrap tests and verify RED**

Run:

```bash
./gradlew :simpledsl-build-bootstrap:test --tests '*DependencyManifestLoaderTest' --stacktrace
```

Expected: failures because `simpledsl.android` is rejected and schema 1 still exports `javaVersion`.

- [ ] **Step 3: Write failing bridge tests for schema v2**

Add a bridge fixture such as:

```groovy
Map snapshot = [
    schemaVersion: 2,
    policies: [
        android: [java: 21, compileSdk: 36, minSdk: 24]
    ],
    platforms: [:], libraries: [:], plugins: [:]
]

def model = SimpleDslRegistryBridge.fromSnapshot(snapshot)
assertNull(model.javaVersionOrNull())
assertEquals(36, model.androidPolicy().compileSdk)
```

Also assert schema 1 is rejected explicitly.

- [ ] **Step 4: Run the bridge tests and verify RED**

Run:

```bash
./gradlew :simpledsl-build-logic:test --tests '*SimpleDslRegistryBridgeTest' --stacktrace
```

Expected: failure because `EXPECTED_SCHEMA_VERSION` is still 1 and `javaVersion` is mandatory.

- [ ] **Step 5: Implement schema-v2 parsing and snapshot export**

Create immutable `AndroidPolicy` with constructor validation and change loader state from one mandatory `javaVersion` field to optional Java + Android policy. Export:

```groovy
[
    schemaVersion: 2,
    policies: policies,
    platforms: platformSnapshot,
    libraries: librarySnapshot,
    plugins: pluginSnapshot
]
```

Do not add backend-required checks here; parser validity and backend applicability are separate concerns.

- [ ] **Step 6: Update project-side snapshot bridge**

Make `SimpleDslRegistryBridge.EXPECTED_SCHEMA_VERSION = 2`, parse `policies`, and expose nullable Java policy plus nullable Android policy while preserving the existing library/platform/plugin objects.

- [ ] **Step 7: Remove settings-time unconditional Java requirement**

Replace `serviceProvider.get().javaVersion()` with a snapshot load/validation that does not require Java policy. `simpledslDependencies` may print Java policy only when present; do not fail Android-only builds.

- [ ] **Step 8: Run focused tests and full current tests**

Run:

```bash
./gradlew \
  :simpledsl-build-bootstrap:check \
  :simpledsl-build-logic:check \
  --stacktrace
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add simpledsl-build-bootstrap simpledsl-build-logic
git commit -m "refactor: introduce backend policy snapshot v2"
```

---

### Task 2: Extract `simpledsl-core` and make the project model backend-neutral

**Files:**
- Modify: `settings.gradle.kts`
- Create: `simpledsl-core/build.gradle.kts`
- Move settings/manifest/distribution sources from `simpledsl-build-bootstrap/src/**` to `simpledsl-core/src/**`
- Move shared project-side sources from `simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle/{catalog,dependency,diagnostics,capability,model}/**` into `simpledsl-core/src/main/groovy/...`
- Create: `simpledsl-core/src/main/groovy/io/github/qigao/simpledsl/gradle/core/SimpleDslBackendGuard.groovy`
- Create: `simpledsl-core/src/main/groovy/io/github/qigao/simpledsl/gradle/core/SimpleDslProjectCorePlugin.groovy`
- Replace: `simpledsl-core/src/main/groovy/io/github/qigao/simpledsl/gradle/model/SimpleDslModuleModel.groovy` with backend-neutral `SimpleDslProjectModel.groovy`
- Modify: `simpledsl-core/src/main/groovy/io/github/qigao/simpledsl/gradle/capability/CapabilitySpec.groovy`
- Move relevant bootstrap/build-logic tests into `simpledsl-core/src/test/**`
- Modify: `scripts/verify-product-namespace.sh`

**Interfaces:**
- Consumes: Task 1 snapshot-v2 bridge.
- Produces: public `io.github.qigao.simpledsl.settings` from `simpledsl-core`; internal `SimpleDslProjectCorePlugin`; backend-neutral project model and backend guard available to both backend artifacts.

- [ ] **Step 1: Add RED tests for string module types and backend ownership**

Create `SimpleDslBackendGuardTest.groovy` and update capability tests so they use:

```groovy
CapabilitySpec spec = CapabilitySpec.builder('web')
        .allow('spring-service')
        .build()
assertTrue(spec.allowedModules.contains('spring-service'))
```

Backend guard test:

```groovy
def project = ProjectBuilder.builder().withName('app').build()
def guard = new SimpleDslBackendGuard(project.path)
guard.claim('java')
def error = assertThrows(SimpleDslConfigurationException) {
    guard.claim('android')
}
assertTrue(error.message.contains('already-selected backend: java'))
assertTrue(error.message.contains('requested backend: android'))
```

- [ ] **Step 2: Run tests and verify RED**

Run the existing build-logic tests that reference `ModuleKind`; expected compile/test failure because `CapabilitySpec` still requires enum values and no backend guard exists.

- [ ] **Step 3: Create `simpledsl-core` build and move sources by responsibility**

`simpledsl-core/build.gradle.kts` keeps only backend-neutral dependencies:

```kotlin
dependencies {
    implementation(localGroovy())
    implementation(libs.tomlj)
    implementation(libs.snakeyaml.engine)
    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

It publishes only the settings marker. Do not carry Spring/AGP dependencies into this module.

- [ ] **Step 4: Replace enum-coupled module model with string IDs**

Implement `SimpleDslProjectModel` with `Property<String> backendId`, `Property<String> moduleType`, `SetProperty<String> capabilities`, and `SetProperty<String> platformBindings`. Update `CapabilityEngine`, doctor validation, and diagnostics to compare module-type strings.

- [ ] **Step 5: Implement `SimpleDslProjectCorePlugin` and backend guard**

The internal plugin applies the catalog bridge, creates the common model/registries/engine/guard, and registers common diagnostics. It must not create the public `simpledsl` extension and must not register product-specific capabilities.

- [ ] **Step 6: Update Gradle project structure and namespace script**

Change root includes to contain `simpledsl-core` and temporarily retain `simpledsl-build-logic` until Task 3 migrates Java sources. Update scripts so the new core artifact is accepted and old bootstrap paths are no longer required.

- [ ] **Step 7: Run core tests**

Run:

```bash
./gradlew :simpledsl-core:check --stacktrace
```

Expected: PASS and no backend-specific dependency resolution in the core configuration.

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts simpledsl-core simpledsl-build-bootstrap simpledsl-build-logic scripts
git commit -m "refactor: extract backend-neutral simpledsl core"
```

---

### Task 3: Create the Java backend and remove the ambiguous `.build` public marker

**Files:**
- Create: `simpledsl-java/build.gradle.kts`
- Move Java/Spring sources from `simpledsl-build-logic/src/main/groovy/**` into `simpledsl-java/src/main/groovy/**`
- Create: `simpledsl-java/src/main/groovy/io/github/qigao/simpledsl/gradle/java/SimpleDslJavaPlugin.groovy`
- Create: `simpledsl-java/src/main/groovy/io/github/qigao/simpledsl/gradle/java/SimpleDslJavaExtension.groovy`
- Modify moved Java module/capability/schema classes to use `SimpleDslProjectModel` string module IDs
- Move Java-specific tests into `simpledsl-java/src/test/**`
- Modify: `simpledsl-core/src/main/groovy/io/github/qigao/simpledsl/gradle/distribution/SimpleDslDistribution.groovy`
- Modify: `simpledsl-core/src/main/groovy/io/github/qigao/simpledsl/gradle/settings/SimpleDslSettingsPlugin.groovy`
- Modify: `settings.gradle.kts`
- Delete: `simpledsl-build-logic/` after migration

**Interfaces:**
- Consumes: `SimpleDslProjectCorePlugin`, `SimpleDslBackendGuard`, snapshot-v2 Java policy from core.
- Produces: public `io.github.qigao.simpledsl.java`; existing `javaLibrary()`, `springLibrary()`, `springService()`, Java/Spring capabilities, and schema helpers with the same semantics as 0.2.x.

- [ ] **Step 1: Add RED plugin tests for `.java`, Java policy requirement, and old `.build` migration error**

Add TestKit coverage asserting:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.java'
}

simpledsl {
    springService()
    web()
}
```

works with `[simpledsl] java = 25`, while a Java project without Java policy fails with `SimpleDSL Java policy is missing`.

Add settings-plugin test that a request for `io.github.qigao.simpledsl.build` fails with text containing:

```text
io.github.qigao.simpledsl.build was removed in SimpleDSL 0.3.0
Use io.github.qigao.simpledsl.java
```

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```bash
./gradlew :simpledsl-core:test :simpledsl-build-logic:test --stacktrace
```

Expected: `.java` plugin is unavailable and `.build` still resolves.

- [ ] **Step 3: Create `simpledsl-java` publication**

Use the existing Java/Spring implementation dependencies only in this module:

```kotlin
dependencies {
    implementation(project(":simpledsl-core"))
    implementation(localGroovy())
    implementation(libs.spring.boot.gradle)
    implementation(libs.graalvm.native.gradle)
    implementation(libs.jooq.codegen.gradle)
    implementation(libs.jooq.core)
    implementation(libs.jooq.meta)
    implementation(libs.jsonschema2pojo.gradle)
}
```

Register exactly one public marker in this module: `io.github.qigao.simpledsl.java`.

- [ ] **Step 4: Implement Java entry plugin and extension isolation**

`SimpleDslJavaPlugin.apply()` must:

```groovy
project.pluginManager.apply(SimpleDslProjectCorePlugin)
def guard = project.extensions.getByType(SimpleDslBackendGuard)
guard.claim('java')
// register Java capabilities
// create Java-specific simpledsl extension
```

`SimpleDslJavaExtension` contains the existing Java/Spring DSL methods and no Android methods.

- [ ] **Step 5: Require Java policy at Java backend application/module selection**

Read Java toolchain from the core catalog policy and fail when absent. Preserve Java 25 behavior for the existing fixture.

- [ ] **Step 6: Replace Java `ModuleKind` enum usage with IDs**

Use exact IDs:

```text
java-library
spring-library
spring-service
```

Update capability registrations and doctor checks accordingly.

- [ ] **Step 7: Remove `.build` marker and add settings migration diagnostic**

`SimpleDslDistribution` defines settings/java/android plugin constants; `.build` is recognized only as a removed ID for diagnostic purposes and is never published.

- [ ] **Step 8: Delete the old build-logic module after all Java sources/tests move**

Remove it from `settings.gradle.kts`. No source file should remain under `simpledsl-build-logic/`.

- [ ] **Step 9: Run Java/core checks**

Run:

```bash
./gradlew :simpledsl-core:check :simpledsl-java:check verifyProductNamespace --stacktrace
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add settings.gradle.kts simpledsl-core simpledsl-java simpledsl-build-logic scripts
git commit -m "feat: split SimpleDSL Java backend"
```

---

### Task 4: Migrate the published Java consumer and publication pipeline

**Files:**
- Rename: `integration-tests/` -> `integration-tests-java/`
- Modify: `integration-tests-java/consumer/app/build.gradle`
- Modify: `integration-tests-java/consumer/settings.gradle`
- Modify: `integration-tests-java/src/test/groovy/io/github/qigao/simpledsl/PublishedConsumerContractTest.groovy`
- Modify: `build.gradle.kts`
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/release.yml`
- Modify: `README.md`

**Interfaces:**
- Consumes: published `simpledsl-core` + `simpledsl-java` from Task 3.
- Produces: exact published Java regression gate proving the 0.2.x Spring behavior survives the backend split and configuration-cache reuse still works.

- [ ] **Step 1: Change Java fixture to `.java` and update expected diagnostics**

Replace:

```groovy
id 'io.github.qigao.simpledsl.build'
```

with:

```groovy
id 'io.github.qigao.simpledsl.java'
```

Keep `springService()` and `web()` unchanged.

- [ ] **Step 2: Make marker-surface test temporarily expect settings + java**

Before Android exists, assert the published repository contains exactly:

```groovy
[
  'io.github.qigao.simpledsl.settings',
  'io.github.qigao.simpledsl.java'
]
```

The final three-marker assertion is moved to Task 7 after Android is published.

- [ ] **Step 3: Update root publication aggregation**

`publishToTestPluginRepository` depends on core and Java publication tasks only at this checkpoint.

- [ ] **Step 4: Run the real published Java consumer twice**

Run:

```bash
./gradlew clean publishToTestPluginRepository :integration-tests-java:test --stacktrace
```

Expected: first TestKit build stores configuration cache; second reuses it; Spring service tests pass from the isolated test Maven repository without `includeBuild`.

- [ ] **Step 5: Verify artifact isolation for Java**

Add a test that inspects the resolved/published `simpledsl-java` POM/dependency graph and asserts no dependency group/name matches `com.android.tools.build:gradle`.

- [ ] **Step 6: Update CI/release task names for core + Java checkpoint**

CI verifies `:simpledsl-core:check`, `:simpledsl-java:check`, publication, Java consumer, and wrapper metadata. Release workflow is kept syntactically valid but final Android publishing is added in Task 7.

- [ ] **Step 7: Commit the Java-green Phase A checkpoint**

```bash
git add integration-tests-java build.gradle.kts .github README.md
git commit -m "test: migrate published Java consumer"
```

**Phase A gate:** No AGP source or dependency is introduced before this commit is green in CI.

---

### Task 5: Add Android distribution metadata and settings-side compatibility control

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `simpledsl-core/build.gradle.kts`
- Modify: `simpledsl-core/src/main/groovy/io/github/qigao/simpledsl/gradle/distribution/SimpleDslDistribution.groovy`
- Modify: `simpledsl-core/src/main/groovy/io/github/qigao/simpledsl/gradle/settings/SimpleDslSettingsPlugin.groovy`
- Test: `simpledsl-core/src/test/groovy/io/github/qigao/simpledsl/gradle/settings/SimpleDslSettingsPluginTest.groovy`

**Interfaces:**
- Consumes: backend-coordinate distribution model from Task 3.
- Produces: settings can resolve `.android` to `simpledsl-android` at the same SimpleDSL version and owns AGP 9.0.1 compatibility metadata for `com.android.application` / `com.android.library` without adding AGP to the core implementation classpath.

- [ ] **Step 1: Add RED settings-resolution tests**

Test that `.android` version `9.9.9` fails as a SimpleDSL version conflict and `com.android.application` version different from `9.0.1` fails as a SimpleDSL plugin compatibility error.

- [ ] **Step 2: Run settings tests and verify RED**

Run:

```bash
./gradlew :simpledsl-core:test --tests '*SimpleDslSettingsPluginTest' --stacktrace
```

Expected: `.android` is unknown and AGP is unmanaged.

- [ ] **Step 3: Pin AGP metadata**

Add version catalog entry:

```toml
agp = "9.0.1"
```

Use that value when generating core distribution metadata, but do not add `implementation(libs.agp)` to `simpledsl-core`.

- [ ] **Step 4: Extend distribution constants and settings resolution**

Add `ANDROID_PLUGIN_ID`, `ANDROID_ARTIFACT`, and AGP compatibility entries mapping both Android plugin IDs to `com.android.tools.build:gradle:9.0.1`.

- [ ] **Step 5: Run core tests and inspect core dependencies**

Run:

```bash
./gradlew :simpledsl-core:check :simpledsl-core:dependencies --configuration runtimeClasspath --stacktrace
```

Expected: tests pass and runtimeClasspath does not contain `com.android.tools.build:gradle`.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml simpledsl-core
git commit -m "feat: manage Android backend compatibility"
```

---

### Task 6: Implement the Android application/library backend with public AGP APIs

**Files:**
- Create: `simpledsl-android/build.gradle.kts`
- Create: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/SimpleDslAndroidPlugin.groovy`
- Create: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/SimpleDslAndroidExtension.groovy`
- Create: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/AndroidApplicationSpec.groovy`
- Create: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/AndroidLibrarySpec.groovy`
- Create: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/internal/AndroidPolicySupport.groovy`
- Create: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/internal/SimpleDslAndroidApplicationPlugin.groovy`
- Create: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/internal/SimpleDslAndroidLibraryPlugin.groovy`
- Test: `simpledsl-android/src/test/groovy/io/github/qigao/simpledsl/gradle/android/AndroidBackendTest.groovy`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: core backend guard, project model, Android policy snapshot, settings-managed AGP 9.0.1.
- Produces: public `.android` plugin with mutually exclusive `androidApplication {}` and `androidLibrary {}` module selection.

- [ ] **Step 1: Add RED TestKit tests for application, library, missing policy, missing namespace, and mixed backend claim**

Representative application DSL:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.android'
}

simpledsl {
    androidApplication {
        namespace = 'com.example.app'
        applicationId = 'com.example.app'
    }
}
```

Assert application without `target-sdk` fails; library with only `java`, `compile-sdk`, `min-sdk` succeeds; selecting application then library fails; applying Java + Android plugins fails through `SimpleDslBackendGuard`.

- [ ] **Step 2: Run Android tests and verify RED**

Run:

```bash
./gradlew :simpledsl-android:test --stacktrace
```

Expected initially: project/module does not exist; after scaffolding, behavior tests fail until implementation is complete.

- [ ] **Step 3: Create Android plugin project with AGP only in this backend**

`simpledsl-android/build.gradle.kts` contains:

```kotlin
dependencies {
    implementation(project(":simpledsl-core"))
    implementation(localGroovy())
    implementation("com.android.tools.build:gradle:9.0.1")
    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
}
```

Register only `io.github.qigao.simpledsl.android`.

- [ ] **Step 4: Implement Android entry plugin and backend claim**

Apply `SimpleDslProjectCorePlugin`, claim `android`, create only `SimpleDslAndroidExtension`, and do not register Java/Spring capabilities.

- [ ] **Step 5: Implement application module configuration with `ApplicationExtension`**

Apply `com.android.application`, set:

```groovy
android.compileSdk = policy.compileSdk
android.defaultConfig.minSdk = policy.minSdk
android.defaultConfig.targetSdk = policy.targetSdk
android.namespace = spec.namespace
android.defaultConfig.applicationId = spec.applicationId ?: spec.namespace
android.compileOptions.sourceCompatibility = JavaVersion.toVersion(policy.javaVersion)
android.compileOptions.targetCompatibility = JavaVersion.toVersion(policy.javaVersion)
```

Require namespace and targetSdk before completing configuration. Record module type `android-application`.

- [ ] **Step 6: Implement library module configuration with `LibraryExtension`**

Apply `com.android.library`, set compileSdk/minSdk/namespace/compileOptions, do not force targetSdk, and record `android-library`.

- [ ] **Step 7: Verify built-in Kotlin target alignment**

Do not apply `org.jetbrains.kotlin.android`; rely on AGP 9 built-in Kotlin inheriting JVM target from `compileOptions.targetCompatibility`.

- [ ] **Step 8: Run Android/core/Java unit checks**

Run:

```bash
./gradlew :simpledsl-core:check :simpledsl-java:check :simpledsl-android:check --stacktrace
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add settings.gradle.kts simpledsl-android
git commit -m "feat: add Android application and library backend"
```

---

### Task 7: Prove Android Components integration and published Android consumer

**Files:**
- Create: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/diagnostics/SimpleDslAndroidVariantsTask.groovy`
- Modify: Android application/library internal plugins to register variant callbacks using public Android Components APIs
- Create: `integration-tests-android/build.gradle.kts`
- Create: `integration-tests-android/consumer/settings.gradle`
- Create: `integration-tests-android/consumer/dependencies.toml`
- Create: `integration-tests-android/consumer/app/build.gradle`
- Create: `integration-tests-android/consumer/app/src/main/AndroidManifest.xml`
- Create: `integration-tests-android/consumer/lib/build.gradle`
- Create: `integration-tests-android/consumer/lib/src/main/AndroidManifest.xml`
- Create: `integration-tests-android/src/test/groovy/io/github/qigao/simpledsl/AndroidPublishedConsumerContractTest.groovy`
- Modify: `integration-tests-java/src/test/groovy/io/github/qigao/simpledsl/PublishedConsumerContractTest.groovy`
- Modify: `build.gradle.kts`
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/release.yml`

**Interfaces:**
- Consumes: Android backend from Task 6.
- Produces: public Android variant diagnostic and full three-plugin published-consumer/release contract.

- [ ] **Step 1: Add RED variant diagnostic test**

Use the appropriate typed Android Components extension after the Android plugin is applied and collect variant names in `onVariants`. The task output must include at least `debug` and `release` for the app fixture.

- [ ] **Step 2: Implement `simpledslAndroidVariants` without task-name guessing**

Register variant callbacks through AGP public Android Components API and feed a configuration-cache-safe task input such as sorted `ListProperty<String>`.

- [ ] **Step 3: Create Android-only published consumer fixture**

Root policy:

```toml
[simpledsl.android]
java = 21
compile-sdk = 36
min-sdk = 24
target-sdk = 36
```

Settings applies `io.github.qigao.simpledsl.settings` at `@SIMPLEDSL_VERSION@`; app and lib apply only `.android` without source composite builds.

- [ ] **Step 4: Add published consumer tests**

Test first run:

```text
:app:assembleDebug
:lib:assembleDebug
:app:simpledslAndroidVariants
```

with `--configuration-cache`, then repeat and assert cache reuse.

Add a second copied fixture removing `target-sdk` and app module, then assert the library build succeeds.

- [ ] **Step 5: Add artifact isolation assertions**

Verify published `simpledsl-android` dependency graph contains AGP but not Spring Boot, GraalVM, jOOQ, or jsonschema2pojo; verify `simpledsl-java` still does not contain AGP; verify `simpledsl-core` contains neither backend dependency set.

- [ ] **Step 6: Finalize marker surface to exactly three IDs**

The marker assertion becomes:

```groovy
[
  'io.github.qigao.simpledsl.settings',
  'io.github.qigao.simpledsl.java',
  'io.github.qigao.simpledsl.android'
]
```

and explicitly asserts the old `.build` marker is absent.

- [ ] **Step 7: Update publication aggregation and release workflow**

`publishToTestPluginRepository` publishes core, Java, and Android. Tag-driven release validation runs all three module checks plus both integration suites, then publishes all three plugin projects using the same `-PreleaseVersion`.

- [ ] **Step 8: Update CI to install/use Android SDK 36 and run both published-consumer suites**

Keep Gradle 9.1.0 and Java 25 setup. Add Android SDK setup before Android integration; fail if platform 36 is unavailable rather than silently downgrading compileSdk.

- [ ] **Step 9: Run the complete local verification contract**

Run:

```bash
./gradlew clean \
  verifyProductNamespace \
  :simpledsl-core:check \
  :simpledsl-java:check \
  :simpledsl-android:check \
  publishToTestPluginRepository \
  :integration-tests-java:test \
  :integration-tests-android:test \
  --stacktrace
```

Expected: PASS, including configuration-cache reuse assertions.

- [ ] **Step 10: Commit**

```bash
git add simpledsl-android integration-tests-android integration-tests-java build.gradle.kts .github
git commit -m "test: verify published Android backend"
```

---

### Task 8: Remove transitional names, document 0.3.0 migration, and run exact-head release gates

**Files:**
- Delete any remaining `simpledsl-build-bootstrap/`, `simpledsl-build-logic/`, and `integration-tests/` paths
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Modify: `gradle.properties`
- Modify: `scripts/verify-product-namespace.sh`
- Modify: docs that state the public marker set or old artifact names

**Interfaces:**
- Consumes: fully green foundation from Tasks 1-7.
- Produces: final 0.3.0 source tree and migration documentation; no compatibility facade remains for old artifact/plugin names.

- [ ] **Step 1: Add repository-surface assertions before cleanup**

Extend namespace/publication verification to fail if any of these remain in product output:

```text
io.github.qigao.simpledsl.build
simpledsl-build-bootstrap
simpledsl-build-logic
```

The historical design/plan documents may mention old names; the executable build/publication surface may not.

- [ ] **Step 2: Run verification and confirm RED if transitional paths remain**

Run `./gradlew verifyProductNamespace --stacktrace` and confirm the new checks catch remaining transitional build surface.

- [ ] **Step 3: Remove transitional paths and update docs**

README must show separate Java and Android usage, shared root policy, Android policy example, and old `.build` migration. CHANGELOG gets an unreleased `0.3.0` section describing the backend split and snapshot-v2 breaking change.

- [ ] **Step 4: Advance development version**

Set:

```properties
simpledslVersion=0.3.0-SNAPSHOT
```

Do not create a release tag in this task.

- [ ] **Step 5: Run exact-head clean verification**

Run the complete Task 7 command from a clean workspace, then run plugin publication validation for all three public plugins with `-PreleaseVersion=0.3.0 --validate-only` where supported by the existing plugin-publish workflow.

- [ ] **Step 6: Inspect the exact PR head CI**

Require one workflow run on the final commit where all of these are successful: core tests, Java tests, Android tests, Java published consumer, Android published consumer, configuration-cache reuse, marker-surface check, artifact isolation, wrapper metadata.

- [ ] **Step 7: Commit final cleanup**

```bash
git add -A
git commit -m "docs: complete SimpleDSL 0.3.0 backend migration"
```

- [ ] **Step 8: Request code review and verify before merge**

Review changed-file patches for cross-backend dependency leakage, public plugin surface, snapshot schema, AGP legacy API usage, and test-cache correctness. Resolve Critical/Important findings, re-run exact-head CI, then use `superpowers:verification-before-completion` and `superpowers:finishing-a-development-branch` before merge.

---

## Plan Self-Review

### Spec coverage

- Shared core / independent Java + Android artifacts: Tasks 2, 3, 6.
- Public plugin split and `.build` removal: Tasks 3, 7, 8.
- Snapshot schema v2 / optional backend policy: Task 1.
- Java compatibility: Tasks 3-4.
- Android policy and library-only no-targetSdk case: Tasks 1, 6-7.
- AGP 9.0.1 / Gradle 9.1 / SDK 36: Tasks 5-7.
- Public AGP DSL and Android Components: Tasks 6-7.
- Built-in Kotlin and Java/Kotlin target alignment: Task 6.
- Backend guard: Tasks 2, 6.
- Artifact dependency isolation: Tasks 4, 7.
- Published-consumer + configuration-cache gates: Tasks 4, 7-8.
- Release workflow/public marker set: Tasks 7-8.
- Compose intentionally excluded from foundation: Global Constraints; separate follow-up plan after merge.

### Placeholder scan

The plan contains no `TBD`, `TODO`, unspecified test request, or open design choice. AGP is pinned to `9.0.1`, SDK to `36`, plugin IDs and module-type IDs are explicit.

### Type consistency

`SimpleDslProjectModel` uses string backend/module IDs throughout; `CapabilitySpec.allow(String...)` matches those IDs. Java policy remains nullable in core and mandatory only in Java backend. Android `targetSdk` remains nullable in snapshot/core and mandatory only for `androidApplication()`.
