# Android Dynamic Feature Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Android Dynamic Feature as a third SimpleDSL Android module type with explicit base-application topology, settings-owned AGP resolution, public Android Components integration, and a real published App Bundle proof.

**Architecture:** `android-dynamic-feature` is a module type, not a capability. The base application explicitly owns its `dynamicFeatures` paths, each feature explicitly owns `implementation project(baseModule)`, and neither project mutates the other. `simpledsl-android` owns all AGP-specific module code; `simpledsl-core` remains backend-neutral and changes only the existing distribution plugin-ownership map for `com.android.dynamic-feature`.

**Tech Stack:** Gradle 9.1.0, Android Gradle Plugin 9.0.1, compileSdk 36, minSdk 24, targetSdk 36 for the base app, Java 21, AGP 9 built-in Kotlin/KGP 2.2.10 runtime baseline, Gradle TestKit, JUnit 5, Android App Bundle ZIP verification.

**Spec:** `docs/superpowers/specs/2026-08-26-android-dynamic-feature-design.md`

## Global Constraints

- Keep Gradle exactly on `9.1.0` and AGP exactly on `9.0.1` for this slice.
- Keep Android repository policy at Java 21 / compileSdk 36 / minSdk 24; the base application continues to use targetSdk 36.
- Use AGP public APIs only: `DynamicFeatureExtension` and `DynamicFeatureAndroidComponentsExtension`; no AGP implementation classes, legacy variant APIs, or task-name interception.
- Use AGP 9 built-in Kotlin; never apply `org.jetbrains.kotlin.android`.
- Dynamic Feature is module type `android-dynamic-feature`; do not model it as a capability.
- The application must explicitly register each feature path; the feature must explicitly declare its base-module project dependency.
- Do not configure another project's extensions, tasks, or SimpleDSL model from either side.
- Do not add project topology to repository TOML/YAML or snapshot schema.
- Do not change `SimpleDslModuleModel`, `CapabilitySpec`, `CapabilityEngine`, or `DependencyBridge` for this feature.
- Existing capabilities `compose`, `ksp`, `room`, and `hilt` remain allowed only for `android-application` and `android-library` in this slice.
- Do not add Dynamic Feature Compose/KSP/Room/Hilt support, Hilt feature-DI semantics, Play Feature Delivery runtime APIs, delivery-mode convenience DSL, product flavors, asset packs, feature ProGuard DSL, or custom artifact transforms.
- Preserve configuration-cache support, published consumer verification, backend isolation, and exact-head CI before merge.

## File Structure

New files:

```text
simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/
  SimpleDslAndroidDynamicFeatureSpec.groovy
  module/SimpleDslAndroidDynamicFeaturePlugin.groovy

simpledsl-android/src/test/groovy/io/github/qigao/simpledsl/gradle/android/
  DynamicFeatureModuleTest.groovy

simpledsl-core/src/test/groovy/io/github/qigao/simpledsl/gradle/settings/
  DynamicFeaturePluginVersionTest.groovy

integration-tests-android/consumer/payments/
  build.gradle
  src/main/AndroidManifest.xml
  src/main/res/values/strings.xml
  src/main/kotlin/example/payments/PaymentsFeature.kt

integration-tests-android/consumer/app/src/main/kotlin/example/app/
  BaseFeatureContract.kt
```

Existing files changed:

```text
simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/SimpleDslAndroidApplicationSpec.groovy
simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/SimpleDslAndroidExtension.groovy
simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/internal/SimpleDslAndroidBase.groovy
simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/internal/SimpleDslAndroidComponents.groovy
simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/module/SimpleDslAndroidApplicationPlugin.groovy
simpledsl-android/src/test/groovy/io/github/qigao/simpledsl/gradle/android/AndroidComponentsIntegrationTest.groovy
simpledsl-core/src/main/groovy/io/github/qigao/simpledsl/gradle/distribution/SimpleDslDistribution.groovy
integration-tests-android/consumer/app/build.gradle
integration-tests-android/src/test/groovy/io/github/qigao/simpledsl/PublishedAndroidConsumerContractTest.groovy
README.md
CHANGELOG.md
```

No change is expected in `simpledsl-android/build.gradle.kts`: `com.android.dynamic-feature` is provided by the existing `com.android.tools.build:gradle:9.0.1` implementation dependency.

Execution should start from design head `0af61701f78402d2f61f2435a7786267e7ff7005` on an isolated implementation branch such as `feat/android-dynamic-feature`, so the approved spec and this plan travel with the implementation.

---

### Task 1: Define the Dynamic Feature module and Android Components RED contracts

**Files:**
- Create: `simpledsl-android/src/test/groovy/io/github/qigao/simpledsl/gradle/android/DynamicFeatureModuleTest.groovy`
- Modify: `simpledsl-android/src/test/groovy/io/github/qigao/simpledsl/gradle/android/AndroidComponentsIntegrationTest.groovy`

**Interfaces:**
- Consumes existing public Android backend plugin: `io.github.qigao.simpledsl.android`.
- Produces the test contract for:
  - `androidApplication { dynamicFeature(':payments') }`
  - `androidDynamicFeature { namespace = 'example.payments'; baseModule = ':app' }`
  - module ID `android-dynamic-feature`
  - plugin ID `com.android.dynamic-feature`
  - feature `implementation` project dependency on `:app`
  - Dynamic Feature public Android Components diagnostics.

- [ ] **Step 1: Add a failing positive topology contract**

Create `DynamicFeatureModuleTest.groovy` with a two-project TestKit fixture. The key test body must be equivalent to:

```groovy
@Test
void configuresDynamicFeatureTopologyFromRepositoryPolicy() {
    writeSettings()
    writeAndroidModule('app', '''
plugins { id 'io.github.qigao.simpledsl.android' }

simpledsl {
    androidApplication {
        namespace = 'example.app'
        dynamicFeature(':payments')
    }
}

assert extensions.getByName('android').dynamicFeatures == [':payments'] as Set
''')
    writeAndroidModule('payments', '''
plugins { id 'io.github.qigao.simpledsl.android' }

simpledsl {
    androidDynamicFeature {
        namespace = 'example.payments'
        baseModule = ':app'
    }
}

assert pluginManager.hasPlugin('com.android.dynamic-feature')
assert !pluginManager.hasPlugin('org.jetbrains.kotlin.android')
assert extensions.getByName('simpledslModuleModel').moduleType.get() == 'android-dynamic-feature'

def androidDsl = extensions.getByName('android')
assert androidDsl.namespace == 'example.payments'
assert androidDsl.compileSdk == 36
assert androidDsl.defaultConfig.minSdk == 24
assert androidDsl.compileOptions.sourceCompatibility == JavaVersion.VERSION_21
assert androidDsl.compileOptions.targetCompatibility == JavaVersion.VERSION_21

assert configurations.getByName('implementation').dependencies.any {
    it instanceof org.gradle.api.artifacts.ProjectDependency && it.path == ':app'
}
''')

    BuildResult result = build(':payments:help')
    assertOutputContains(result, 'BUILD SUCCESSFUL')
}
```

The fixture's `settings.gradle` must register the same test dependency snapshot shape already used by `AndroidModuleConfigurationTest`, but include both projects:

```groovy
rootProject.name = 'dynamic-feature-consumer'
include 'app', 'payments'
```

and Android policy:

```groovy
android: [
    java: 21,
    compileSdk: 36,
    minSdk: 24,
    targetSdk: 36
]
```

`writeAndroidModule(name, buildScript)` must create `build.gradle` plus a minimal `src/main/AndroidManifest.xml` containing `<manifest />` so RED is caused by the missing SimpleDSL API, not fixture setup.

- [ ] **Step 2: Add malformed topology and single-module-type contracts**

Add tests that require SimpleDSL-authored diagnostics for these exact cases:

```groovy
simpledsl {
    androidDynamicFeature {
        namespace = 'example.payments'
    }
}
```

Expected output:

```text
SimpleDSL Android configuration error
Project: :payments
Problem: androidDynamicFeature requires baseModule
```

Relative path:

```groovy
baseModule = 'app'
```

Expected output contains:

```text
Problem: baseModule must be an absolute Gradle project path beginning with ':'
Value: app
```

Self-reference:

```groovy
baseModule = ':payments'
```

Expected output contains:

```text
Problem: androidDynamicFeature baseModule cannot reference the feature project itself
Value: :payments
```

Duplicate module declaration:

```groovy
simpledsl {
    androidLibrary { namespace = 'example.payments' }
    androidDynamicFeature {
        namespace = 'example.payments'
        baseModule = ':app'
    }
}
```

Expected output contains the existing contract:

```text
Problem: exactly one Android module type may be declared
```

Also cover malformed application feature paths with `dynamicFeature('payments')` and `dynamicFeature(':app')`; they must use equivalent absolute-path/self-reference diagnostics and must not inspect/configure the target project.

- [ ] **Step 3: Lock the capability boundary**

Add one negative test with a valid base app and feature declaration followed by `jetpackCompose()` in `:payments`:

```groovy
simpledsl {
    androidDynamicFeature {
        namespace = 'example.payments'
        baseModule = ':app'
    }
    jetpackCompose()
}
```

Expected output must contain:

```text
Capability: compose
android-application
android-library
```

and must not claim that `android-dynamic-feature` is supported. This guards the first-slice scope before production code exists.

- [ ] **Step 4: Add the Dynamic Feature Android Components RED**

Extend `AndroidComponentsIntegrationTest.groovy` with a two-module fixture and test:

```groovy
@Test
void dynamicFeatureVariantsAreExposedAndConfigurationCacheIsReused() {
    writeDynamicFeatureSettings()
    writeDynamicFeatureAppAndFeature()

    BuildResult first = build(':payments:simpledslAndroidVariants', '--configuration-cache')
    assertOutputContains(first, 'debug')
    assertOutputContains(first, 'release')

    BuildResult second = build(':payments:simpledslAndroidVariants', '--configuration-cache')
    assertOutputContains(second, 'Reusing configuration cache')
    assertOutputContains(second, 'debug')
    assertOutputContains(second, 'release')
}
```

The application must register `dynamicFeature(':payments')`; the feature must declare `baseModule = ':app'`. Do not use raw AGP Dynamic Feature configuration as a workaround in this test.

- [ ] **Step 5: Run the targeted tests and prove RED**

Run:

```bash
./gradlew \
  :simpledsl-android:test \
  --tests '*DynamicFeatureModuleTest' \
  --tests '*AndroidComponentsIntegrationTest.dynamicFeatureVariantsAreExposedAndConfigurationCacheIsReused' \
  --stacktrace
```

Expected: FAIL because `SimpleDslAndroidApplicationSpec.dynamicFeature(...)` and/or `SimpleDslAndroidExtension.androidDynamicFeature(...)` do not exist. The failure must occur at the intended public contract, not because Android SDK, repositories, or fixture manifests are missing.

- [ ] **Step 6: Commit the test-only RED**

```bash
git add \
  simpledsl-android/src/test/groovy/io/github/qigao/simpledsl/gradle/android/DynamicFeatureModuleTest.groovy \
  simpledsl-android/src/test/groovy/io/github/qigao/simpledsl/gradle/android/AndroidComponentsIntegrationTest.groovy
git commit -m "test: define Android dynamic feature module contract"
```

Do not add production files in this commit.

---

### Task 2: Define the settings-owned `com.android.dynamic-feature` RED contract

**Files:**
- Create: `simpledsl-core/src/test/groovy/io/github/qigao/simpledsl/gradle/settings/DynamicFeaturePluginVersionTest.groovy`

**Interfaces:**
- Consumes the existing settings resolution strategy and `SimpleDslDistribution.ownedPluginVersion(...)` contract.
- Produces the requirement that `com.android.dynamic-feature` maps to `com.android.tools.build:gradle` using existing `androidGradlePluginVersion = 9.0.1` metadata.

- [ ] **Step 1: Add the independent plugin-version conflict test**

Create the test using the same TestKit shape as `HiltPluginVersionTest`, but with the Dynamic Feature plugin:

```groovy
package io.github.qigao.simpledsl.gradle.settings

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertTrue

class DynamicFeaturePluginVersionTest {
    @TempDir
    Path projectDir

    @Test
    void rejectsConsumerOverrideOfPinnedDynamicFeatureAgpVersion() {
        Files.writeString(projectDir.resolve('dependencies.toml'), '''
[simpledsl.android]
java = 21
compile-sdk = 36
min-sdk = 24
target-sdk = 36
'''.stripIndent())

        Files.writeString(projectDir.resolve('settings.gradle'), '''
plugins { id 'io.github.qigao.simpledsl.settings' }
rootProject.name = 'consumer'
include 'payments'
'''.stripIndent())

        Path payments = Files.createDirectories(projectDir.resolve('payments'))
        Files.writeString(payments.resolve('build.gradle'), '''
plugins {
    id 'com.android.dynamic-feature' version '9.9.9'
}
'''.stripIndent())

        def result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments('help', '--stacktrace')
                .withPluginClasspath()
                .buildAndFail()

        assertTrue(result.output.contains('SimpleDSL plugin compatibility error'))
        assertTrue(result.output.contains('Plugin: com.android.dynamic-feature'))
        assertTrue(result.output.contains('Requested: 9.9.9'))
        assertTrue(result.output.contains('Managed: 9.0.1'))
    }
}
```

- [ ] **Step 2: Run the isolated settings test and prove RED**

Run:

```bash
./gradlew :simpledsl-core:test \
  --tests '*DynamicFeaturePluginVersionTest' \
  --stacktrace
```

Expected: FAIL because `com.android.dynamic-feature` is not yet in `SimpleDslDistribution`'s owned plugin maps, so the requested `9.9.9` does not produce the SimpleDSL compatibility diagnostic.

- [ ] **Step 3: Commit the second independent RED**

```bash
git add simpledsl-core/src/test/groovy/io/github/qigao/simpledsl/gradle/settings/DynamicFeaturePluginVersionTest.groovy
git commit -m "test: require pinned Android dynamic feature plugin version"
```

Do not change `SimpleDslDistribution` yet.

---

### Task 3: Implement the minimal Dynamic Feature module type and settings ownership

**Files:**
- Create: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/SimpleDslAndroidDynamicFeatureSpec.groovy`
- Create: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/module/SimpleDslAndroidDynamicFeaturePlugin.groovy`
- Modify: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/SimpleDslAndroidApplicationSpec.groovy`
- Modify: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/SimpleDslAndroidExtension.groovy`
- Modify: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/internal/SimpleDslAndroidBase.groovy`
- Modify: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/internal/SimpleDslAndroidComponents.groovy`
- Modify: `simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/module/SimpleDslAndroidApplicationPlugin.groovy`
- Modify: `simpledsl-core/src/main/groovy/io/github/qigao/simpledsl/gradle/distribution/SimpleDslDistribution.groovy`

**Interfaces:**
- Produces `SimpleDslAndroidDynamicFeatureSpec`:

```groovy
abstract class SimpleDslAndroidDynamicFeatureSpec {
    abstract Property<String> getNamespace()
    abstract Property<String> getBaseModule()
}
```

- Produces public extension methods:

```groovy
void androidDynamicFeature(Action<? super SimpleDslAndroidDynamicFeatureSpec> action)
void androidDynamicFeature(Closure closure)
```

- Produces application spec API:

```groovy
abstract SetProperty<String> getDynamicFeatures()
void dynamicFeature(String projectPath)
```

- Produces module claim `android-dynamic-feature`.
- Adds settings ownership:

```text
com.android.dynamic-feature -> com.android.tools.build:gradle -> androidGradlePluginVersion
```

- [ ] **Step 1: Add the application feature-path API without cross-project behavior**

Change `SimpleDslAndroidApplicationSpec.groovy` to import `SetProperty` and expose:

```groovy
abstract SetProperty<String> getDynamicFeatures()

void dynamicFeature(String projectPath) {
    dynamicFeatures.add(projectPath)
}
```

Do not look up a `Project` here; this object is only declaration state.

- [ ] **Step 2: Add Android-local project-path validation**

Extend `SimpleDslAndroidBase` with focused validation used by both sides of the topology. The behavior must be:

```groovy
static String requireBaseModule(Project project, Property<String> baseModule) {
    String value = baseModule.isPresent() ? baseModule.get().trim() : null
    if (value == null || value.isEmpty()) {
        throw new GradleException(
                'SimpleDSL Android configuration error\n' +
                "Project: ${project.path}\n" +
                'Problem: androidDynamicFeature requires baseModule')
    }
    requireExternalProjectPath(project, 'baseModule', value,
            'androidDynamicFeature baseModule cannot reference the feature project itself')
}

static Set<String> requireDynamicFeaturePaths(Project project, Collection<String> values) {
    LinkedHashSet<String> result = new LinkedHashSet<>()
    values.each { raw ->
        String value = raw == null ? '' : raw.trim()
        result.add(requireExternalProjectPath(
                project,
                'dynamicFeature',
                value,
                'androidApplication dynamicFeature cannot reference the application project itself'))
    }
    result
}
```

The private helper must reject blank/relative paths with an error naming the field, require a leading `:`, reject `value == project.path`, and include `Value: ...` for nonblank invalid values. It must not call `project.project(...)`, `evaluationDependsOn(...)`, or inspect the target project.

- [ ] **Step 3: Create the Dynamic Feature spec and internal module plugin**

`SimpleDslAndroidDynamicFeaturePlugin` must follow the existing application/library plugin shape and contain only feature-local behavior:

```groovy
package io.github.qigao.simpledsl.gradle.android.module

import com.android.build.api.dsl.DynamicFeatureExtension
import com.android.build.api.variant.DynamicFeatureAndroidComponentsExtension
import io.github.qigao.simpledsl.gradle.android.SimpleDslAndroidDynamicFeatureSpec
import io.github.qigao.simpledsl.gradle.android.internal.SimpleDslAndroidBase
import io.github.qigao.simpledsl.gradle.android.internal.SimpleDslAndroidComponents
import io.github.qigao.simpledsl.gradle.catalog.CatalogAndroidPolicy
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.api.Plugin
import org.gradle.api.Project

final class SimpleDslAndroidDynamicFeaturePlugin implements Plugin<Project> {
    static final String SPEC_EXTENSION = 'simpledslAndroidDynamicFeatureSpec'

    @Override
    void apply(Project project) {
        SimpleDslModuleModel model = project.extensions.getByType(SimpleDslModuleModel)
        model.claim('android-dynamic-feature', project.path)

        SimpleDslAndroidDynamicFeatureSpec spec =
                project.extensions.getByType(SimpleDslAndroidDynamicFeatureSpec)
        CatalogAndroidPolicy policy = SimpleDslAndroidBase.requirePolicy(project, false)
        String namespace = SimpleDslAndroidBase.requireNamespace(project, spec.namespace)
        String baseModule = SimpleDslAndroidBase.requireBaseModule(project, spec.baseModule)

        project.pluginManager.apply('com.android.dynamic-feature')
        DynamicFeatureExtension android = project.extensions.getByType(DynamicFeatureExtension)
        android.namespace = namespace
        android.compileSdk = policy.compileSdk
        android.defaultConfig.minSdk = policy.minSdk
        android.compileOptions.sourceCompatibility = SimpleDslAndroidBase.javaVersion(policy)
        android.compileOptions.targetCompatibility = SimpleDslAndroidBase.javaVersion(policy)

        project.dependencies.add(
                'implementation',
                project.dependencies.project([path: baseModule]))

        DynamicFeatureAndroidComponentsExtension components =
                project.extensions.getByType(DynamicFeatureAndroidComponentsExtension)
        SimpleDslAndroidComponents.configure(project, components)
    }
}
```

If AGP 9.0.1's public generic signatures require a minor type parameter spelling change at compile time, adjust imports/types only; do not fall back to internal AGP classes or untyped extension-name lookup in production.

- [ ] **Step 4: Wire `androidDynamicFeature` into the Android extension**

Add both Action and Closure forms, matching the existing application/library API:

```groovy
void androidDynamicFeature(Action<? super SimpleDslAndroidDynamicFeatureSpec> action) {
    SimpleDslAndroidDynamicFeatureSpec spec = createDynamicFeatureSpec()
    action.execute(spec)
    project.pluginManager.apply(SimpleDslAndroidDynamicFeaturePlugin)
}

void androidDynamicFeature(Closure closure) {
    SimpleDslAndroidDynamicFeatureSpec spec = createDynamicFeatureSpec()
    configure(spec, closure)
    project.pluginManager.apply(SimpleDslAndroidDynamicFeaturePlugin)
}
```

`createDynamicFeatureSpec()` must call `rejectDuplicateModuleDeclaration()` and create the spec using `SimpleDslAndroidDynamicFeaturePlugin.SPEC_EXTENSION`. Expand the duplicate check to application/library/dynamic-feature spec extension names; keep the existing diagnostic text unchanged.

Do not add any Dynamic Feature branch to `configureBackendCapability(...)`.

- [ ] **Step 5: Map application-declared feature paths onto AGP public DSL**

In `SimpleDslAndroidApplicationPlugin`, after obtaining `ApplicationExtension`, validate and assign the declared paths:

```groovy
Set<String> dynamicFeatures = SimpleDslAndroidBase.requireDynamicFeaturePaths(
        project,
        spec.dynamicFeatures.getOrElse(Collections.emptySet()))
android.dynamicFeatures.addAll(dynamicFeatures)
```

This is the only application-side topology mutation. Do not open/configure the feature projects.

- [ ] **Step 6: Add the typed Dynamic Feature Android Components overload**

Add the import and one overload to `SimpleDslAndroidComponents`:

```groovy
import com.android.build.api.variant.DynamicFeatureAndroidComponentsExtension

static void configure(Project project, DynamicFeatureAndroidComponentsExtension components) {
    configureCallbacks(project, components)
}
```

Do not replace the existing callback body or introduce a new variant abstraction.

- [ ] **Step 7: Add settings ownership for the third AGP plugin ID**

Modify only the existing maps in `SimpleDslDistribution`:

```groovy
'com.android.dynamic-feature' : 'com.android.tools.build:gradle'
```

and:

```groovy
'com.android.dynamic-feature' : 'androidGradlePluginVersion'
```

Do not add a new version-catalog entry, distribution metadata property, or backend dependency: AGP 9.0.1 is already exported as `androidGradlePluginVersion` and already present on `simpledsl-android`'s implementation classpath.

- [ ] **Step 8: Run the RED suites and prove minimal GREEN**

Run:

```bash
./gradlew \
  :simpledsl-core:test \
  :simpledsl-android:test \
  --tests '*DynamicFeaturePluginVersionTest' \
  --tests '*DynamicFeatureModuleTest' \
  --tests '*AndroidComponentsIntegrationTest' \
  --stacktrace
```

Then run the full plugin checks:

```bash
./gradlew \
  verifyProductNamespace \
  :simpledsl-core:check \
  :simpledsl-java:check \
  :simpledsl-android:check \
  --stacktrace
```

Expected: GREEN. Existing capability tests must remain green with their allow-lists unchanged.

- [ ] **Step 9: Inspect the production diff for architecture leakage**

Run:

```bash
git diff --name-only HEAD~1..HEAD
```

before commit (or equivalent staged-file review) and verify there is no production change to:

```text
SimpleDslModuleModel.groovy
CapabilitySpec.groovy
CapabilityEngine.groovy
DependencyBridge.groovy
BuiltinAndroidCapabilities.groovy
simpledsl-android/build.gradle.kts
```

If one of those appears necessary solely for this feature, stop and re-evaluate against the spec instead of expanding the abstraction.

- [ ] **Step 10: Commit the minimal production GREEN**

```bash
git add \
  simpledsl-core/src/main/groovy/io/github/qigao/simpledsl/gradle/distribution/SimpleDslDistribution.groovy \
  simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android
git commit -m "feat: add Android dynamic feature module type"
```

---

### Task 4: Prove a real published base-app + Dynamic Feature App Bundle

**Files:**
- Modify: `integration-tests-android/consumer/app/build.gradle`
- Create: `integration-tests-android/consumer/app/src/main/kotlin/example/app/BaseFeatureContract.kt`
- Create: `integration-tests-android/consumer/payments/build.gradle`
- Create: `integration-tests-android/consumer/payments/src/main/AndroidManifest.xml`
- Create: `integration-tests-android/consumer/payments/src/main/res/values/strings.xml`
- Create: `integration-tests-android/consumer/payments/src/main/kotlin/example/payments/PaymentsFeature.kt`
- Modify: `integration-tests-android/src/test/groovy/io/github/qigao/simpledsl/PublishedAndroidConsumerContractTest.groovy`

**Interfaces:**
- Base app public DSL:

```groovy
androidApplication {
    namespace = 'example.app'
    dynamicFeature(':payments')
}
```

- Feature public DSL:

```groovy
androidDynamicFeature {
    namespace = 'example.payments'
    baseModule = ':app'
}
```

- Real source dependency: feature Kotlin imports `example.app.BaseFeatureContract`.
- Final bundle proof: generated AAB contains `payments/manifest/AndroidManifest.xml`.

- [ ] **Step 1: Register `:payments` from the published base application**

Modify only the application module declaration block:

```groovy
simpledsl {
    androidApplication {
        namespace = 'example.app'
        dynamicFeature(':payments')
    }
    jetpackCompose()
    room()
    hilt()
}
```

Keep all existing Room/Hilt assertions unchanged so Dynamic Feature is proven without weakening the Phase E/F consumer contract.

- [ ] **Step 2: Add a real base-app type for feature compilation**

Create `BaseFeatureContract.kt`:

```kotlin
package example.app

object BaseFeatureContract {
    const val source: String = "base-app"
}
```

Do not move existing Room/Hilt sources or introduce a library merely to make the dependency easier.

- [ ] **Step 3: Add the published Dynamic Feature module**

Create `payments/build.gradle`:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.android'
}

simpledsl {
    androidDynamicFeature {
        namespace = 'example.payments'
        baseModule = ':app'
    }
}

assert pluginManager.hasPlugin('com.android.dynamic-feature')
assert !pluginManager.hasPlugin('org.jetbrains.kotlin.android')
assert extensions.getByName('simpledslModuleModel').moduleType.get() == 'android-dynamic-feature'
assert configurations.getByName('implementation').dependencies.any {
    it instanceof org.gradle.api.artifacts.ProjectDependency && it.path == ':app'
}
```

Do not enable `jetpackCompose()`, `ksp()`, `room()`, `hilt()`, or generic equivalents in this module.

The settings plugin already performs module discovery, so adding the `payments/build.gradle` directory must be enough for the consumer fixture; do not add manual `include ':payments'` unless a failing published-consumer test proves module discovery does not include it. If discovery fails, diagnose that behavior before modifying settings because the repository's established consumer contract intentionally relies on automatic discovery.

- [ ] **Step 4: Add a valid install-time Dynamic Feature manifest**

Create `payments/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:dist="http://schemas.android.com/apk/distribution">
    <dist:module
        dist:instant="false"
        dist:title="@string/payments_title">
        <dist:delivery>
            <dist:install-time />
        </dist:delivery>
        <dist:fusing dist:include="true" />
    </dist:module>
    <application />
</manifest>
```

Create `payments/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="payments_title">Payments</string>
</resources>
```

This is fixture metadata required for bundle packaging, not a SimpleDSL delivery-mode API.

- [ ] **Step 5: Add the real feature-to-base Kotlin dependency proof**

Create `PaymentsFeature.kt`:

```kotlin
package example.payments

import example.app.BaseFeatureContract

object PaymentsFeature {
    val baseSource: String = BaseFeatureContract.source
}
```

The published consumer must compile this source successfully under AGP built-in Kotlin without applying `org.jetbrains.kotlin.android`.

- [ ] **Step 6: Extend the configuration-cache/variant published proof**

In `resolvesPublishedAndroidMarkersAndReusesConfigurationCache()`, add:

```groovy
':payments:simpledslAndroidVariants'
```

to both first and second builds. Existing assertions for debug/release and `Features: compose,hilt,ksp,room` remain. Do not assert the application/library capability list for `:payments`; the feature intentionally has no capabilities in this slice.

- [ ] **Step 7: Add the real AAB packaging test**

Add a third published-consumer test or rename the existing assemble test to make the expanded proof explicit. Build with:

```groovy
BuildResult result = build(
        fixture,
        consumerArguments(':app:bundleDebug', ':feature:assembleDebug'))
```

Keep the existing Room and Hilt generated-source assertions. Then locate the AAB under:

```text
app/build/outputs/bundle/debug
```

using `Files.walk`, require exactly one regular file ending in `.aab`, and inspect it with `java.util.zip.ZipFile`:

```groovy
Path bundleRoot = fixture.toPath().resolve('app/build/outputs/bundle/debug')
List<Path> bundles = Files.walk(bundleRoot).withCloseable { paths ->
    paths.filter { path ->
        Files.isRegularFile(path) && path.fileName.toString().endsWith('.aab')
    }.toList()
}
assertTrue(bundles.size() == 1, "Expected one debug AAB under ${bundleRoot}, found ${bundles}")

new java.util.zip.ZipFile(bundles.single().toFile()).withCloseable { zip ->
    assertTrue(
            zip.getEntry('payments/manifest/AndroidManifest.xml') != null,
            'Debug AAB does not contain the payments Dynamic Feature manifest')
}
```

If the AGP 9.0.1 bundle uses a confirmed different stable feature-prefix representation, inspect the produced AAB once, document the actual public bundle structure in the test assertion, and change only this proof path; do not weaken the test to merely check that some `.aab` exists.

- [ ] **Step 8: Run the real published consumer and backend isolation**

Run:

```bash
./gradlew \
  publishToTestPluginRepository \
  verifyBackendIsolation \
  :integration-tests-android:test \
  --stacktrace
```

Expected: GREEN for:

```text
existing app/library Compose + Room + Hilt consumer
payments debug/release Android Components variants
configuration-cache store/reuse
feature Kotlin -> base app type compilation
:app:bundleDebug
payments/manifest/AndroidManifest.xml inside the AAB
backend isolation
```

The isolation script should require no new coordinate because Dynamic Feature reuses the already-required `com.android.tools.build:gradle` dependency.

- [ ] **Step 9: Commit the published consumer proof**

```bash
git add integration-tests-android
git commit -m "test: prove published Android dynamic feature bundle"
```

---

### Task 5: Documentation, final review, PR, and exact-head gate

**Files:**
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- No expected workflow file changes.

**Interfaces:**
- Documents the third Android module type without changing the three public SimpleDSL plugin IDs.
- Records Dynamic Feature as module topology, not capability behavior.
- Preserves #26 as the future-roadmap index and #27 as the execution issue.

- [ ] **Step 1: Update README Android module documentation**

Change the Android backend description from application/library-only to application/library/dynamic-feature module configuration. Add one focused section after the library example:

```groovy
// :app
simpledsl {
    androidApplication {
        namespace = 'com.example.app'
        dynamicFeature(':payments')
    }
}
```

```groovy
// :payments
simpledsl {
    androidDynamicFeature {
        namespace = 'com.example.payments'
        baseModule = ':app'
    }
}
```

Explain in prose:

```text
- the base application owns the AGP dynamicFeatures set;
- the feature owns implementation project(baseModule);
- SimpleDSL does not mutate projects across the graph;
- Dynamic Feature uses the same AGP 9.0.1 / compileSdk 36 / Java 21 repository policy;
- existing Compose/KSP/Room/Hilt capabilities are not yet enabled for android-dynamic-feature.
```

Do not describe delivery-mode DSL or imply Hilt Dynamic Feature support.

- [ ] **Step 2: Update CHANGELOG without renumbering the release**

Under `Unreleased - 0.3.0 development`, add a new section such as:

```markdown
### Android Dynamic Feature module foundation
```

Record:

```text
android-dynamic-feature module type
explicit bilateral base/feature topology
settings-owned com.android.dynamic-feature at AGP 9.0.1
public DynamicFeatureExtension / DynamicFeatureAndroidComponentsExtension only
AGP built-in Kotlin, no org.jetbrains.kotlin.android
real published AAB containing payments feature
configuration-cache + backend-isolation proof
existing capabilities deliberately not broadened
```

Remove `dynamic features` from the generic “later Android phases” list, while leaving KMP, benchmark modules, custom artifact transforms, per-module SDK overrides, and Dynamic Feature capability/delivery follow-ups as later work.

- [ ] **Step 3: Run the local equivalent of every CI gate from a clean workspace**

Run exactly:

```bash
./gradlew clean --stacktrace

./gradlew \
  verifyProductNamespace \
  :simpledsl-core:check \
  :simpledsl-java:check \
  :simpledsl-android:check \
  --stacktrace

./gradlew \
  publishToTestPluginRepository \
  verifyBackendIsolation \
  :integration-tests-java:test \
  :integration-tests-android:test \
  --stacktrace

grep -Fq 'gradle-9.1.0-bin.zip' gradle/wrapper/gradle-wrapper.properties
test -s gradle/wrapper/gradle-wrapper.jar
```

Expected: every command succeeds. Do not claim the implementation complete from targeted tests alone.

- [ ] **Step 4: Review the complete diff against the spec before documentation commit**

Check:

```bash
git diff --name-only 0af61701f78402d2f61f2435a7786267e7ff7005...HEAD
```

Expected production surface is limited to the files listed in this plan. Specifically verify:

```text
SimpleDslModuleModel.groovy            absent
CapabilitySpec.groovy                  absent
CapabilityEngine.groovy                absent
DependencyBridge.groovy                absent
BuiltinAndroidCapabilities.groovy      absent
simpledsl-android/build.gradle.kts      absent
settings/snapshot manifest schema      absent
```

Also search the production diff for forbidden implementation patterns:

```text
applicationVariants
BaseExtension
AppExtension
evaluationDependsOn
project.evaluationDependsOn
taskGraph
org.jetbrains.kotlin.android
```

Any occurrence introduced by this slice requires investigation before proceeding.

- [ ] **Step 5: Commit docs/final branch state**

```bash
git add README.md CHANGELOG.md
git commit -m "docs: document Android dynamic feature module"
```

- [ ] **Step 6: Push the implementation branch and open a draft PR for #27**

Use title:

```text
feat: add Android dynamic feature module
```

PR body must summarize:

```text
- public androidDynamicFeature module DSL
- explicit base app dynamicFeature(path) + feature baseModule relationship
- no cross-project mutation
- settings-owned com.android.dynamic-feature / AGP 9.0.1
- public DynamicFeatureExtension + DynamicFeatureAndroidComponentsExtension
- published feature Kotlin -> base type compile proof
- debug AAB contains payments feature module
- configuration-cache and backend-isolation proof
- existing capability allow-lists unchanged
- RED/GREEN commit and CI chronology

Closes #27
Ref #26
```

Keep the PR draft until all remote gates are green.

- [ ] **Step 7: Run/fetch remote CI and require exact-head GREEN**

The repository CI already runs the required steps; do not edit `.github/workflows/ci.yml` merely for this feature. For the final PR head, verify one CI run whose head SHA is exactly the current branch head and whose `verify` job has every step successful:

```text
Verify Android SDK baseline
Clean workspace
Verify plugin build
Verify published consumer contracts
Verify wrapper metadata
```

If CI fails, diagnose the specific failure, add the smallest corrective commit, and repeat the exact-head gate. Preserve RED/GREEN history; do not squash away evidence during development.

- [ ] **Step 8: Synchronize #27 and PR evidence without creating a new code head**

Update #27 and the PR body with:

```text
contract RED commit/run
settings ownership RED commit/run
minimal production GREEN commit/run
published AAB consumer GREEN commit/run
final exact-head SHA/run
```

Issue/PR metadata updates must not create a new git commit after the final exact-head run.

- [ ] **Step 9: Mark PR ready and perform the final merge gate**

Freshly verify on the same head:

```text
PR open
mergeable true
not draft
no unresolved review threads
no blocking reviews/comments
exact-head CI success
base master has not invalidated mergeability
```

Do not merge until a separate explicit user authorization after reporting this gate. When authorized, merge with `expected_head_sha=<final-head-sha>`, then verify PR merged, #27 closed by `Closes #27`, and `master` points to the returned merge commit.

---

## Self-Review Checklist

Before execution handoff, verify the plan against the approved spec:

- [ ] Module/capability distinction is preserved.
- [ ] Both sides of Dynamic Feature topology are explicit.
- [ ] No cross-project mutation is introduced.
- [ ] `com.android.dynamic-feature` reuses existing AGP 9.0.1 ownership and artifact.
- [ ] Dynamic Feature repository policy omits targetSdk ownership.
- [ ] Public Android Components proof covers debug/release and configuration cache.
- [ ] Existing capabilities remain application/library-only.
- [ ] Real feature source compiles against base-app source.
- [ ] Final proof builds a base application AAB and inspects the feature entry.
- [ ] Existing Room/Hilt generated-source proofs remain intact.
- [ ] Backend isolation remains unchanged and green.
- [ ] No generic core graph/codegen/capability abstraction is introduced.
- [ ] Final merge remains a separate explicitly authorized action after exact-head GREEN.
