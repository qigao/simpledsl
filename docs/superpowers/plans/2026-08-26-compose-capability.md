# Compose Android Capability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `simpledsl { ... compose() }` as the first Android backend-specific capability while preserving repository policy → snapshot → backend.

**Architecture:** Keep `simpledsl-core` unchanged. Register a Compose `CapabilitySpec` inside `simpledsl-android`; use the existing core engine for module validation, compiler-plugin application, and dependency-alias binding; keep `buildFeatures.compose = true` in an Android-only helper.

**Tech Stack:** Gradle 9.1.0, AGP 9.0.1, built-in Kotlin/KGP 2.2.10, Compose Compiler plugin 2.2.10, compileSdk 36, Java 21, Compose BOM 2026.06.00 / Compose 1.11.x, Gradle TestKit, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-08-25-java-android-backend-split-design.md`

## Global Constraints

- `simpledsl-core` stays backend-neutral: no AGP/Compose dependency or callback hook.
- Keep AGP 9.0.1 / Gradle 9.1.0 / compileSdk 36 / minSdk 24 / targetSdk 36 / Java 21.
- Use AGP 9 built-in Kotlin; never apply `org.jetbrains.kotlin.android`.
- Pin `org.jetbrains.kotlin.plugin.compose` to 2.2.10.
- Use Compose BOM 2026.06.00; do not cross into Compose 1.12 / API 37 / AGP 9.2 in Phase C.
- Capability implementation binds manifest aliases only; Maven library coordinates live in repository manifest data.
- Room, Hilt, KSP, KMP, dynamic features, benchmark modules, custom artifact transforms, and per-module SDK overrides remain out of scope.

---

### Task 1: Define RED Compose DSL contract

**Files:**
- Modify: `simpledsl-android/src/test/groovy/io/github/qigao/simpledsl/gradle/android/AndroidModuleConfigurationTest.groovy`

**Interfaces:**
- Consumes: existing Android application/library DSL and snapshot schema v2.
- Produces: failing `compose()` contract tests.

- [ ] Add fake snapshot platform `compose -> androidx.compose:compose-bom:2026.06.00` and libraries `compose-runtime` / `compose-ui` with `platform: compose`.
- [ ] Add application test using `androidApplication { namespace = 'example.compose.app' }` followed by `compose()`.
- [ ] Assert `org.jetbrains.kotlin.plugin.compose` is applied, `android.buildFeatures.compose` is true inside `onVariants`, capability `compose` is active, and `implementation:compose` platform binding is recorded.
- [ ] Add equivalent Android library test.
- [ ] Add `compose()`-without-module test and expect existing capability module-type diagnostics.
- [ ] Push test-only commit and record expected RED (`compose()` missing / capability unregistered).
- [ ] Commit as `test: define Android Compose capability contract`.

### Task 2: Pin Android Compose compiler tooling

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `simpledsl-android/build.gradle.kts`
- Modify: `simpledsl-core/build.gradle.kts`
- Modify: `simpledsl-core/src/main/groovy/io/github/qigao/simpledsl/gradle/distribution/SimpleDslDistribution.groovy`
- Modify: `scripts/verify-backend-isolation.sh`

**Interfaces:**
- Consumes: settings owned-plugin resolution and Android backend runtime classpath.
- Produces: compiler plugin 2.2.10 available for programmatic application.

- [ ] Add version `kotlin = "2.2.10"` and library alias for `org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin`.
- [ ] Add `implementation(libs.compose.compiler.gradle)` to `simpledsl-android` only.
- [ ] Export `kotlinVersion=2.2.10` through generated distribution metadata.
- [ ] Add `org.jetbrains.kotlin.plugin.compose` to owned plugin module/version maps so existing settings conflict handling pins it.
- [ ] Extend backend isolation: Compose compiler tooling is forbidden in core/Java and allowed in Android.
- [ ] Run plugin/distribution/isolation tests and commit as `build: pin Android Compose compiler plugin`.

### Task 3: Implement the minimal Android capability

**Files:**
- Create: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/capability/BuiltinAndroidCapabilities.groovy`
- Create: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/capability/ComposeCapabilityConfigurer.groovy`
- Modify: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/SimpleDslAndroidPlugin.groovy`
- Modify: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/SimpleDslAndroidExtension.groovy`

**Interfaces:**
- Consumes: `CapabilityRegistry`, `CapabilityEngine`, public AGP Application/Library DSL.
- Produces: capability ID `compose` and public `void compose()`.

- [ ] Define `COMPOSE = CapabilitySpec.builder('compose').allow('android-application','android-library').externalPluginId('org.jetbrains.kotlin.plugin.compose').dependency('implementation','compose-runtime').dependency('implementation','compose-ui').build()`.
- [ ] `BuiltinAndroidCapabilities.registerAll(registry)` registers only Android built-ins.
- [ ] `ComposeCapabilityConfigurer.configure(Project)` finds `ApplicationExtension` or `LibraryExtension` and sets `buildFeatures.compose = true`; otherwise emits an Android Compose configuration error.
- [ ] `SimpleDslAndroidPlugin` registers Android built-ins after core/model initialization.
- [ ] `SimpleDslAndroidExtension.capability(String)` delegates to `CapabilityEngine.enable`; `compose()` enables `COMPOSE` then calls the Android configurer.
- [ ] Run Task 1 tests to GREEN; do not add a generic core hook.
- [ ] Commit as `feat: add Android Compose capability`.

### Task 4: Prove real published Compose consumers

**Files:**
- Modify: `integration-tests-android/consumer/dependencies.toml`
- Modify: `integration-tests-android/consumer/app/build.gradle`
- Modify: `integration-tests-android/consumer/feature/build.gradle`
- Create: `integration-tests-android/consumer/app/src/main/kotlin/example/app/AppContent.kt`
- Create: `integration-tests-android/consumer/feature/src/main/kotlin/example/feature/FeatureContent.kt`
- Modify: `integration-tests-android/src/test/groovy/io/github/qigao/simpledsl/PublishedAndroidConsumerContractTest.groovy`

**Interfaces:**
- Consumes: published settings/Android markers and root dependency manifest.
- Produces: real external consumer proof for built-in Kotlin + Compose compiler + BOM-managed runtime/UI.

- [ ] In the manifest declare version `compose-bom = "2026.06.00"`, BOM alias `compose`, and versionless `compose-runtime` / `compose-ui` pointing at platform `compose`.
- [ ] Enable `compose()` in both app and feature module; module scripts contain no direct Compose versions and no Kotlin Android plugin.
- [ ] Add real Kotlin `@Composable` functions importing `androidx.compose.runtime.Composable` and `androidx.compose.ui.Modifier` in both modules.
- [ ] Keep real `assembleDebug` for app/library and configuration-cache reuse; assert `simpledslCapabilities` reports `compose` and `implementation:compose` where useful.
- [ ] Run published Android consumer twice and commit as `test: prove published Compose consumers`.

### Task 5: Exact-head closeout

**Files:**
- Modify if public docs need it: `README.md`, `CHANGELOG.md`
- Update PR description after evidence exists.

**Interfaces:**
- Consumes: Tasks 1–4.
- Produces: Phase C PR closing #14.

- [ ] Document only the stable `androidApplication/androidLibrary + compose()` surface and repository-managed Compose aliases.
- [ ] Run full CI: SDK baseline, core/Java/Android tests, both published consumers, isolation, wrapper metadata, configuration-cache reuse.
- [ ] Verify exact PR head, reviews, and unresolved threads.
- [ ] Update PR body with actual RED/GREEN workflow numbers and final head, keeping Room/Hilt/KSP explicitly later.
- [ ] Merge only on exact-head success using expected head SHA; `Closes #14` closes the Phase C issue.
