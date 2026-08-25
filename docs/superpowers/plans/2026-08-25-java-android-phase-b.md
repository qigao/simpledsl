# Android Backend Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the independent `simpledsl-android` backend for Android application/library modules, backed by repository Android policy, AGP 9 public APIs, Android Components variant integration, and real published-consumer verification.

**Architecture:** `simpledsl-core` remains the single settings/manifest/catalog/core implementation and gains Android policy plus Android compatibility metadata only. `simpledsl-android` depends on core and AGP 9.0.1, claims the `android` backend, creates an Android-only `simpledsl` extension, and applies application/library module adapters. Java and Android artifacts never depend on each other. Phase B ends with three public markers and two independent published-consumer suites; Compose remains Phase C.

**Tech Stack:** Gradle 9.1.0, Groovy/Gradle plugin development, Android Gradle Plugin 9.0.1, AGP public DSL (`ApplicationExtension`, `LibraryExtension`), Android Components API (`ApplicationAndroidComponentsExtension`, `LibraryAndroidComponentsExtension`), JUnit 5, Gradle TestKit, GitHub Actions Ubuntu 24.04 Android SDK.

**Spec:** `docs/superpowers/specs/2026-08-25-java-android-backend-split-design.md`

## Global Constraints

- Keep repository Gradle baseline at `9.1.0`.
- Pin Android Gradle Plugin exactly to `9.0.1`; AGP 9.0 requires Gradle 9.1.0 and supports API 36.x.
- Use AGP public DSL/interfaces only; do not reference `BaseExtension`, `AppExtension`, `applicationVariants`, or task-name guessing.
- Use AGP 9 built-in Kotlin; do not apply or declare `org.jetbrains.kotlin.android`.
- Keep `simpledsl-core` free of AGP, Spring Boot, GraalVM, jOOQ, and jsonschema2pojo implementation dependencies.
- Keep `simpledsl-java` free of AGP and free of any dependency on `simpledsl-android`.
- Keep `simpledsl-android` free of Java/Spring tooling and free of any dependency on `simpledsl-java`.
- `simpledsl.java` remains optional at settings load and required only by the Java backend.
- `[simpledsl.android]` is optional at settings load and required only by the Android backend.
- Android policy accepts exactly `java`, `compile-sdk`, `min-sdk`, `target-sdk`; all present values are positive integers.
- Enforce `min-sdk <= compile-sdk`; if target is present enforce `min-sdk <= target-sdk <= compile-sdk`.
- `androidApplication()` requires target SDK; `androidLibrary()` does not.
- Verified Android integration baseline: Android Java 21, compileSdk 36, minSdk 24, targetSdk 36.
- `namespace` is required for application/library; applicationId defaults to namespace.
- Compose, Room, Hilt, KSP, KMP, dynamic features, benchmark modules, custom artifact transforms, and per-module SDK overrides are out of Phase B.

---

### Task 1: Add Android repository policy to snapshot schema v2

**Files:**
- Create: `simpledsl-core/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest/AndroidPolicySpec.groovy`
- Create: `simpledsl-core/src/main/groovy/io/github/qigao/simpledsl/gradle/catalog/CatalogAndroidPolicy.groovy`
- Modify: `simpledsl-core/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyManifestLoader.groovy`
- Modify: `simpledsl-core/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyRegistry.groovy`
- Modify: `simpledsl-core/src/main/groovy/io/github/qigao/simpledsl/gradle/catalog/DependencyCatalogSnapshot.groovy`
- Modify: `simpledsl-core/src/main/groovy/io/github/qigao/simpledsl/gradle/catalog/SimpleDslRegistryBridge.groovy`
- Modify: `simpledsl-core/src/main/groovy/io/github/qigao/simpledsl/gradle/settings/SimpleDslSettingsPlugin.groovy`
- Test: `simpledsl-core/src/test/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyManifestLoaderTest.groovy`
- Test: `simpledsl-core/src/test/groovy/io/github/qigao/simpledsl/gradle/catalog/SimpleDslRegistryBridgeTest.groovy`

**Interfaces:**
- Produces: `AndroidPolicySpec(int javaVersion, int compileSdk, int minSdk, Integer targetSdk)`.
- Produces: `CatalogAndroidPolicy` with `javaVersion`, `compileSdk`, `minSdk`, nullable `targetSdk`.
- Produces: `DependencyRegistry.androidPolicyOrNull()`.
- Produces: `DependencyCatalogSnapshot.androidPolicyOrNull()` and `requireAndroidPolicy(String projectPath, boolean targetRequired)`.
- Snapshot shape when configured:

```groovy
policies: [
    android: [
        java: 21,
        compileSdk: 36,
        minSdk: 24,
        targetSdk: 36
    ]
]
```

- [ ] **Step 1: Write failing manifest tests**

Add exact cases for TOML/YAML equivalence, application policy, library policy without target SDK, duplicate Android policy across includes, unknown Android keys, non-positive values, `min-sdk > compile-sdk`, target below min, and target above compile.

```groovy
@Test
void loadsAndroidPolicyIntoSchemaV2Snapshot() {
    File manifest = writeToml('''
[simpledsl.android]
java = 21
compile-sdk = 36
min-sdk = 24
target-sdk = 36
''')

    Map snapshot = DependencyManifestLoader.load(manifest).snapshot()
    assertEquals([
            java: 21,
            compileSdk: 36,
            minSdk: 24,
            targetSdk: 36
    ], snapshot.policies.android)
}
```

- [ ] **Step 2: Verify RED**

Run: `gradle :simpledsl-core:test --tests '*DependencyManifestLoaderTest' --tests '*SimpleDslRegistryBridgeTest' --stacktrace`

Expected: FAIL because `simpledsl.android` is currently rejected and the snapshot/catalog model has no Android policy.

- [ ] **Step 3: Implement strict Android policy parsing**

`DependencyManifestLoader` must allow `simpledsl` keys `java` and `android`, parse the nested table exactly once across includes, validate positive integral values and ordering constraints, and construct `AndroidPolicySpec`.

```groovy
final class AndroidPolicySpec {
    final int javaVersion
    final int compileSdk
    final int minSdk
    final Integer targetSdk
}
```

- [ ] **Step 4: Export/import Android policy through schema v2**

Keep `schemaVersion = 2`; extend `DependencyRegistry.snapshot()` and `SimpleDslRegistryBridge.fromSnapshot()` without changing `platforms`, `libraries`, or `plugins` semantics. `DependencyCatalogSnapshot.requireAndroidPolicy(path, true)` must fail with a `SimpleDSL configuration error` naming the project and stating that application modules require `simpledsl.android.target-sdk`.

- [ ] **Step 5: Keep settings diagnostics backend-neutral**

Update the settings diagnostics preparation so loading an Android-only manifest does not require Java policy. Do not create an Android project extension in core.

- [ ] **Step 6: Verify GREEN and commit**

Run: `gradle :simpledsl-core:check --stacktrace`

Commit: `feat: add Android repository policy`

---

### Task 2: Add Android distribution metadata and plugin alignment

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `simpledsl-core/build.gradle.kts`
- Modify: `simpledsl-core/src/main/groovy/io/github/qigao/simpledsl/gradle/distribution/SimpleDslDistribution.groovy`
- Modify: `simpledsl-core/src/main/groovy/io/github/qigao/simpledsl/gradle/settings/SimpleDslSettingsPlugin.groovy`
- Test: `simpledsl-core/src/test/groovy/io/github/qigao/simpledsl/gradle/settings/SimpleDslSettingsPluginTest.groovy`

**Interfaces:**
- Produces constants `ANDROID_ARTIFACT = 'simpledsl-android'`, `ANDROID_PLUGIN_ID = 'io.github.qigao.simpledsl.android'`.
- Produces `SimpleDslDistribution.androidCoordinate()`.
- Owns `com.android.application` and `com.android.library` compatibility metadata mapped to `com.android.tools.build:gradle:9.0.1`.

- [ ] **Step 1: Write failing settings tests**

Cover:

```text
io.github.qigao.simpledsl.android -> same SimpleDSL release
com.android.application -> com.android.tools.build:gradle:9.0.1
com.android.library -> com.android.tools.build:gradle:9.0.1
explicit AGP 9.0.2 -> SimpleDSL plugin compatibility error
explicit SimpleDSL Android version != settings version -> SimpleDSL version conflict
```

- [ ] **Step 2: Verify RED**

Run: `gradle :simpledsl-core:test --tests '*SimpleDslSettingsPluginTest' --stacktrace`

Expected: FAIL because Android distribution metadata and resolution rules do not exist.

- [ ] **Step 3: Add pinned AGP metadata**

Add:

```toml
[versions]
agp = "9.0.1"

[libraries]
android-gradle = { module = "com.android.tools.build:gradle", version.ref = "agp" }
```

`simpledsl-core` uses the AGP version only as generated compatibility metadata; it must not add `android-gradle` as an implementation dependency.

- [ ] **Step 4: Extend settings resolution**

Resolve the Android backend to `SimpleDslDistribution.androidCoordinate()`. Extend owned plugin metadata for both Android plugin IDs and preserve the existing strict version-conflict diagnostics.

- [ ] **Step 5: Verify GREEN and commit**

Run: `gradle :simpledsl-core:check --stacktrace`

Commit: `feat: align Android plugin distribution`

---

### Task 3: Create the independent `simpledsl-android` backend artifact

**Files:**
- Create: `simpledsl-android/build.gradle.kts`
- Create: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/SimpleDslAndroidPlugin.groovy`
- Create: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/SimpleDslAndroidExtension.groovy`
- Create: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/SimpleDslAndroidApplicationSpec.groovy`
- Create: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/SimpleDslAndroidLibrarySpec.groovy`
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Test: `simpledsl-android/src/test/groovy/io/github/qigao/simpledsl/gradle/android/SimpleDslAndroidPluginTest.groovy`

**Interfaces:**
- Public marker: `io.github.qigao.simpledsl.android`.
- Backend entry: `SimpleDslAndroidPlugin`.
- Public extension: `SimpleDslAndroidExtension`.
- DSL methods:

```groovy
void androidApplication(Action<? super SimpleDslAndroidApplicationSpec> action)
void androidApplication(Closure closure)
void androidLibrary(Action<? super SimpleDslAndroidLibrarySpec> action)
void androidLibrary(Closure closure)
```

- [ ] **Step 1: Write failing backend-surface tests**

Apply `SimpleDslAndroidPlugin` to a project with a supplied catalog snapshot and assert:

```text
backendId == android
simpledsl extension is SimpleDslAndroidExtension
extension exposes androidApplication/androidLibrary
extension does not expose springService/jooqSchema
applying SimpleDslJavaPlugin after Android fails through SimpleDslBackendGuard
```

- [ ] **Step 2: Verify RED**

Run: `gradle :simpledsl-android:test --stacktrace`

Expected: FAIL because the project/module and plugin do not exist.

- [ ] **Step 3: Add Android artifact and marker**

`simpledsl-android` dependencies:

```kotlin
implementation(project(":simpledsl-core"))
implementation(localGroovy())
implementation(libs.android.gradle)
testImplementation(gradleTestKit())
testImplementation(libs.junit.jupiter)
```

Do not depend on `simpledsl-java` and do not add Spring/GraalVM/jOOQ/jsonschema2pojo.

- [ ] **Step 4: Implement backend entry and extension shell**

`SimpleDslAndroidPlugin.apply()` must:

```groovy
SimpleDslBackendGuard.claim(project, 'android')
project.pluginManager.apply(SimpleDslProjectCorePlugin)
model.backendId.set('android')
create simpledsl as SimpleDslAndroidExtension
```

No Android capability is registered in Phase B.

- [ ] **Step 5: Verify GREEN and commit**

Run: `gradle :simpledsl-core:check :simpledsl-java:check :simpledsl-android:check --stacktrace`

Commit: `feat: add Android backend artifact`

---

### Task 4: Implement Android application and library module configuration

**Files:**
- Create: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/internal/SimpleDslAndroidBase.groovy`
- Create: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/module/SimpleDslAndroidApplicationPlugin.groovy`
- Create: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/module/SimpleDslAndroidLibraryPlugin.groovy`
- Modify: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/SimpleDslAndroidExtension.groovy`
- Test: `simpledsl-android/src/test/groovy/io/github/qigao/simpledsl/gradle/android/AndroidModuleConfigurationTest.groovy`

**Interfaces:**
- Application module type: `android-application`.
- Library module type: `android-library`.
- Uses `ApplicationExtension` / `LibraryExtension` only.

- [ ] **Step 1: Write failing module tests**

Use TestKit temporary projects with the test plugin classpath and Android SDK environment. Test both DSLs and assert configuration/build behavior:

```groovy
simpledsl {
    androidApplication {
        namespace = 'example.app'
    }
}
```

must apply `com.android.application`, default applicationId to namespace, configure compileSdk 36/minSdk 24/targetSdk 36, Java source/target compatibility 21, and claim `android-application`.

Library test must apply `com.android.library`, configure compileSdk/minSdk/Java, claim `android-library`, and succeed with no target SDK in the manifest.

Also add negative tests for missing namespace and application policy without target SDK.

- [ ] **Step 2: Verify RED**

Run: `gradle :simpledsl-android:test --tests '*AndroidModuleConfigurationTest' --stacktrace`

Expected: FAIL because module plugins/configuration do not exist.

- [ ] **Step 3: Implement application adapter**

Apply `com.android.application`, obtain `ApplicationExtension`, configure:

```groovy
android.namespace = namespace
android.compileSdk = policy.compileSdk
android.defaultConfig.minSdk = policy.minSdk
android.defaultConfig.targetSdk = policy.targetSdk
android.defaultConfig.applicationId = applicationId ?: namespace
android.compileOptions.sourceCompatibility = JavaVersion.toVersion(policy.javaVersion)
android.compileOptions.targetCompatibility = JavaVersion.toVersion(policy.javaVersion)
```

- [ ] **Step 4: Implement library adapter**

Apply `com.android.library`, obtain `LibraryExtension`, configure namespace, compileSdk, minSdk, and Java compatibility only. Do not force targetSdk onto libraries.

- [ ] **Step 5: Keep built-in Kotlin baseline**

Do not apply `org.jetbrains.kotlin.android`. Add a regression assertion that the plugin is absent after both module methods.

- [ ] **Step 6: Verify GREEN and commit**

Run: `gradle :simpledsl-android:check --stacktrace`

Commit: `feat: configure Android modules with AGP public DSL`

---

### Task 5: Prove Android Components variant integration

**Files:**
- Create: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/diagnostics/SimpleDslAndroidVariantsTask.groovy`
- Create: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/internal/SimpleDslAndroidComponents.groovy`
- Modify: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/module/SimpleDslAndroidApplicationPlugin.groovy`
- Modify: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/module/SimpleDslAndroidLibraryPlugin.groovy`
- Test: `simpledsl-android/src/test/groovy/io/github/qigao/simpledsl/gradle/android/AndroidComponentsIntegrationTest.groovy`

**Interfaces:**
- Diagnostic task: `simpledslAndroidVariants`.
- Uses `ApplicationAndroidComponentsExtension` / `LibraryAndroidComponentsExtension`.
- Calls public `beforeVariants` and `onVariants` APIs.

- [ ] **Step 1: Write failing TestKit contract**

For both application and library fixtures run twice with configuration cache:

```text
simpledslAndroidVariants --configuration-cache
```

Assert output contains `debug` and `release` and second run reports configuration-cache reuse.

- [ ] **Step 2: Verify RED**

Run: `gradle :simpledsl-android:test --tests '*AndroidComponentsIntegrationTest' --stacktrace`

Expected: FAIL because the task/API adapter is absent.

- [ ] **Step 3: Implement cache-safe diagnostic task**

Task type contains only serializable Gradle properties:

```groovy
@Input
abstract ListProperty<String> getVariantNames()
```

`@TaskAction` prints sorted unique names. No Project/Variant/extension object may be stored in task fields.

- [ ] **Step 4: Register public variant callbacks**

Use `beforeVariants(selector().all(), Action<...>)` only for supported configuration-time validation and `onVariants(selector().all(), Action<...>)` to add `variant.name` to the task provider. Do not inspect task names.

- [ ] **Step 5: Verify GREEN and commit**

Run: `gradle :simpledsl-android:check --stacktrace`

Commit: `feat: expose Android Components variant proof`

---

### Task 6: Add the real published Android consumer and three-artifact isolation gate

**Files:**
- Create: `integration-tests-android/build.gradle.kts`
- Create: `integration-tests-android/consumer/settings.gradle`
- Create: `integration-tests-android/consumer/dependencies.toml`
- Create: `integration-tests-android/consumer/app/build.gradle`
- Create: `integration-tests-android/consumer/app/src/main/AndroidManifest.xml`
- Create: `integration-tests-android/consumer/feature/build.gradle`
- Create: `integration-tests-android/consumer/feature/src/main/AndroidManifest.xml`
- Create: `integration-tests-android/src/test/groovy/io/github/qigao/simpledsl/PublishedAndroidConsumerContractTest.groovy`
- Modify: `integration-tests-java/src/test/groovy/io/github/qigao/simpledsl/PublishedJavaConsumerContractTest.groovy`
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `scripts/verify-backend-isolation.sh`

**Interfaces:**
- Root `publishToTestPluginRepository` publishes core + Java + Android.
- Global public marker set becomes exactly:

```text
io.github.qigao.simpledsl.settings
io.github.qigao.simpledsl.java
io.github.qigao.simpledsl.android
```

- [ ] **Step 1: Write the Android published-consumer test first**

Fixture manifest:

```toml
[simpledsl.android]
java = 21
compile-sdk = 36
min-sdk = 24
target-sdk = 36
```

Application fixture uses `androidApplication`, library fixture uses `androidLibrary`. The test runs from the isolated Maven repository, not source plugin classpath:

```text
:app:simpledslAndroidVariants
:feature:simpledslAndroidVariants
:app:assembleDebug
:feature:assembleDebug
```

Run a configuration-only invocation twice with `--configuration-cache` and assert reuse.

- [ ] **Step 2: Verify RED**

Run: `gradle publishToTestPluginRepository :integration-tests-android:test --stacktrace`

Expected: FAIL until Android publication/root wiring is complete.

- [ ] **Step 3: Extend exact publication surface assertions**

Update the Java contract so the isolated distribution expects three implementation/marker surfaces instead of the Phase A two-marker distribution.

- [ ] **Step 4: Strengthen artifact isolation**

`verify-backend-isolation.sh` must assert:

```text
core: no AGP, Spring Boot, GraalVM, jOOQ, jsonschema2pojo
java: depends on core; no AGP; no simpledsl-android
android: depends on core; depends on com.android.tools.build:gradle; no Java/Spring tooling; no simpledsl-java
```

- [ ] **Step 5: Verify GREEN and commit**

Run:

```bash
gradle \
  publishToTestPluginRepository \
  verifyBackendIsolation \
  :integration-tests-java:test \
  :integration-tests-android:test \
  --stacktrace
```

Commit: `test: add published Android consumer contract`

---

### Task 7: Wire Phase B into CI/release/docs and perform exact-head verification

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/release.yml`
- Modify: `scripts/verify-product-namespace.sh`
- Modify: `README.md`
- Modify: `CHANGELOG.md`

**Interfaces:**
- CI must verify core, Java, Android, both published consumers, artifact isolation, and wrapper metadata.
- Release workflow publishes three plugin implementation artifacts/markers after the full distribution contract succeeds.

- [ ] **Step 1: Update product namespace and release surface**

Add `simpledsl-android` and `integration-tests-android` to namespace roots and plugin-build checks. Keep reserved Plugin Portal tag checks.

- [ ] **Step 2: Update CI**

CI plugin build step:

```text
verifyProductNamespace
:simpledsl-core:check
:simpledsl-java:check
:simpledsl-android:check
```

Published contract step:

```text
publishToTestPluginRepository
verifyBackendIsolation
:integration-tests-java:test
:integration-tests-android:test
```

Do not install a second Android SDK manually on `ubuntu-24.04`; the hosted image already supplies `ANDROID_HOME`, API 36, and Build Tools 36.0.0. Fail explicitly if `ANDROID_HOME` or platform 36 is unexpectedly unavailable.

- [ ] **Step 3: Update release workflow**

Before publish, run both consumer suites and isolation gate with the release version. Publish:

```text
:simpledsl-core:publishPlugins
:simpledsl-java:publishPlugins
:simpledsl-android:publishPlugins
```

- [ ] **Step 4: Update README and CHANGELOG**

Document Phase B as 0.3.0 development, the three public plugin IDs, Android policy, application/library examples, AGP 9.0.1 baseline, built-in Kotlin, and the fact that Compose is still Phase C. Do not claim 0.3.0 is released.

- [ ] **Step 5: Run fresh exact-head verification**

Run through CI on the exact PR head and require all steps green. Review the full PR diff against the approved spec; fix every Critical/Important finding before marking Ready for review.

- [ ] **Step 6: Update PR and stop before merge**

PR body must include RED/GREEN workflow evidence, exact-head SHA/CI, `Closes #12`, and `Ref #7`. Mark Ready for review only after fresh exact-head success. Do not merge without a separate user instruction.
