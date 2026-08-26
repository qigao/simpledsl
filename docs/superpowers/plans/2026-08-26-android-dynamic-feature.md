# Android Dynamic Feature Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Android Dynamic Feature as a third SimpleDSL Android module type with explicit base-application topology, settings-owned AGP resolution, public Android Components integration, and a real published App Bundle proof.

**Architecture:** `android-dynamic-feature` is a module type, not a capability. The base application explicitly owns its `dynamicFeatures` paths, each feature explicitly owns `implementation project(baseModule)`, and neither project mutates the other. `simpledsl-android` owns AGP-specific module code; `simpledsl-core` remains backend-neutral and changes only the existing distribution plugin-ownership map for `com.android.dynamic-feature`.

**Tech Stack:** Gradle 9.1.0, Android Gradle Plugin 9.0.1, compileSdk 36, minSdk 24, targetSdk 36 for the base app, Java 21, AGP 9 built-in Kotlin/KGP 2.2.10 runtime baseline, Gradle TestKit, JUnit 5, Android App Bundle ZIP verification.

**Spec:** `docs/superpowers/specs/2026-08-26-android-dynamic-feature-design.md`

## Global Constraints

- Keep Gradle exactly on `9.1.0` and AGP exactly on `9.0.1` for this slice.
- Keep Android repository policy at Java 21 / compileSdk 36 / minSdk 24; the base application continues to use targetSdk 36.
- Use AGP public APIs only: `DynamicFeatureExtension` and `DynamicFeatureAndroidComponentsExtension`; no AGP implementation classes, legacy variant APIs, or task-name interception.
- Use AGP 9 built-in Kotlin; never apply `org.jetbrains.kotlin.android`.
- Dynamic Feature is module type `android-dynamic-feature`; do not model it as a capability.
- The application explicitly registers each feature path; the feature explicitly declares its base-module project dependency.
- Do not configure another project's extensions, tasks, or SimpleDSL model from either side.
- Do not add project topology to repository TOML/YAML or snapshot schema.
- Do not change `SimpleDslModuleModel`, `CapabilitySpec`, `CapabilityEngine`, or `DependencyBridge` for this feature.
- Existing capabilities `compose`, `ksp`, `room`, and `hilt` remain allowed only for `android-application` and `android-library` in this slice.
- Do not add Dynamic Feature Compose/KSP/Room/Hilt support, Hilt feature-DI semantics, Play Feature Delivery runtime APIs, delivery-mode convenience DSL, product flavors, asset packs, feature ProGuard DSL, or custom artifact transforms.
- Preserve configuration-cache support, published consumer verification, backend isolation, and exact-head CI before merge.

## File Map

Create:

```text
simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/SimpleDslAndroidDynamicFeatureSpec.groovy
simpledsl-android/src/main/groovy/io/github/qigao/simpledsl/gradle/android/module/SimpleDslAndroidDynamicFeaturePlugin.groovy
simpledsl-android/src/test/groovy/io/github/qigao/simpledsl/gradle/android/DynamicFeatureModuleTest.groovy
simpledsl-core/src/test/groovy/io/github/qigao/simpledsl/gradle/settings/DynamicFeaturePluginVersionTest.groovy
integration-tests-android/consumer/app/src/main/kotlin/example/app/BaseFeatureContract.kt
integration-tests-android/consumer/payments/build.gradle
integration-tests-android/consumer/payments/src/main/AndroidManifest.xml
integration-tests-android/consumer/payments/src/main/res/values/strings.xml
integration-tests-android/consumer/payments/src/main/kotlin/example/payments/PaymentsFeature.kt
```

Modify:

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

Do not modify `simpledsl-android/build.gradle.kts`: `com.android.dynamic-feature` comes from the existing `com.android.tools.build:gradle:9.0.1` implementation dependency.

Execution starts from design head `0af61701f78402d2f61f2435a7786267e7ff7005` on an isolated branch such as `feat/android-dynamic-feature`, so the approved spec and this plan travel with the implementation.

---

### Task 1: Define the Dynamic Feature module and Android Components RED contracts

**Files:**
- Create: `simpledsl-android/src/test/groovy/io/github/qigao/simpledsl/gradle/android/DynamicFeatureModuleTest.groovy`
- Modify: `simpledsl-android/src/test/groovy/io/github/qigao/simpledsl/gradle/android/AndroidComponentsIntegrationTest.groovy`

**Interfaces:**
- Consumes: public plugin `io.github.qigao.simpledsl.android` and the existing Android policy snapshot test pattern.
- Produces: contract for `androidApplication.dynamicFeature(String)`, `androidDynamicFeature { namespace; baseModule }`, module ID `android-dynamic-feature`, `com.android.dynamic-feature`, `implementation project(':app')`, and Dynamic Feature variant diagnostics.

- [ ] **Step 1: Create a two-module TestKit fixture that cannot fail for incidental manifest reasons**

`DynamicFeatureModuleTest` must create:

```groovy
rootProject.name = 'dynamic-feature-consumer'
include 'app', 'payments'
```

and register the same fake snapshot service used by existing Android tests:

```groovy
Map snapshot() {
    [
        schemaVersion: 2,
        policies: [
            android: [
                java: 21,
                compileSdk: 36,
                minSdk: 24,
                targetSdk: 36
            ]
        ],
        platforms: [:],
        libraries: [:],
        plugins: [:]
    ]
}
```

The helper must write `<manifest />` for `:app`. For `:payments`, write a valid install-time Dynamic Feature manifest and its title resource even though the RED should fail before AGP packaging:

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

```xml
<resources>
    <string name="payments_title">Payments</string>
</resources>
```

This ensures GREEN-stage failures cannot be blamed on a malformed feature fixture.

- [ ] **Step 2: Add the positive module/topology contract**

Use this application build:

```groovy
plugins { id 'io.github.qigao.simpledsl.android' }

simpledsl {
    androidApplication {
        namespace = 'example.app'
        dynamicFeature(':payments')
    }
}

assert extensions.getByName('android').dynamicFeatures == [':payments'] as Set
```

Use this feature build:

```groovy
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
```

Run `:payments:help` and require `BUILD SUCCESSFUL` once production exists.

- [ ] **Step 3: Add exact malformed-topology diagnostics**

Cover missing base module:

```text
SimpleDSL Android configuration error
Project: :payments
Problem: androidDynamicFeature requires baseModule
```

Cover relative base path `baseModule = 'app'`:

```text
Problem: baseModule must be an absolute Gradle project path beginning with ':'
Value: app
```

Cover self-reference `baseModule = ':payments'`:

```text
Problem: androidDynamicFeature baseModule cannot reference the feature project itself
Value: :payments
```

Cover duplicate module declaration:

```groovy
simpledsl {
    androidLibrary { namespace = 'example.payments' }
    androidDynamicFeature {
        namespace = 'example.payments'
        baseModule = ':app'
    }
}
```

Expected:

```text
Problem: exactly one Android module type may be declared
```

Also cover application-side `dynamicFeature('payments')` and `dynamicFeature(':app')` with equivalent absolute-path/self-reference diagnostics. Tests must not require SimpleDSL to inspect or configure the referenced project to produce these errors.

- [ ] **Step 4: Lock the capability boundary in RED**

Use a valid Dynamic Feature declaration followed by:

```groovy
jetpackCompose()
```

Expected output contains:

```text
Capability: compose
android-application
android-library
```

and does not list `android-dynamic-feature` as an allowed type. `BuiltinAndroidCapabilities` must remain unchanged throughout this slice.

- [ ] **Step 5: Add a Dynamic Feature Android Components RED**

Extend `AndroidComponentsIntegrationTest` with a valid `:app` + `:payments` fixture and:

```groovy
@Test
void dynamicFeatureVariantsAreExposedAndConfigurationCacheIsReused() {
    writeDynamicFeatureFixture()

    BuildResult first = build(':payments:simpledslAndroidVariants', '--configuration-cache')
    assertOutputContains(first, 'debug')
    assertOutputContains(first, 'release')

    BuildResult second = build(':payments:simpledslAndroidVariants', '--configuration-cache')
    assertOutputContains(second, 'Reusing configuration cache')
    assertOutputContains(second, 'debug')
    assertOutputContains(second, 'release')
}
```

The fixture must use only SimpleDSL's proposed public module DSL; do not configure raw AGP Dynamic Feature as a test workaround.

- [ ] **Step 6: Prove RED and commit test-only history**

Run:

```bash
./gradlew :simpledsl-android:test \
  --tests '*DynamicFeatureModuleTest' \
  --tests '*AndroidComponentsIntegrationTest.dynamicFeatureVariantsAreExposedAndConfigurationCacheIsReused' \
  --stacktrace
```

Expected: FAIL because `dynamicFeature(...)` and/or `androidDynamicFeature(...)` are absent. The failure must be the intended API absence, not SDK/repository/manifest setup.

Commit:

```bash
git add \
  simpledsl-android/src/test/groovy/io/github/qigao/simpledsl/gradle/android/DynamicFeatureModuleTest.groovy \
  simpledsl-android/src/test/groovy/io/github/qigao/simpledsl/gradle/android/AndroidComponentsIntegrationTest.groovy
git commit -m "test: define Android dynamic feature module contract"
```

---

### Task 2: Define the settings-owned Dynamic Feature AGP RED contract

**Files:**
- Create: `simpledsl-core/src/test/groovy/io/github/qigao/simpledsl/gradle/settings/DynamicFeaturePluginVersionTest.groovy`

**Interfaces:**
- Consumes: existing settings resolution strategy and `androidGradlePluginVersion` distribution metadata.
- Produces: `com.android.dynamic-feature -> com.android.tools.build:gradle -> 9.0.1` ownership contract.

- [ ] **Step 1: Add the independent settings conflict test**

Create:

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

- [ ] **Step 2: Prove the settings RED**

Run:

```bash
./gradlew :simpledsl-core:test \
  --tests '*DynamicFeaturePluginVersionTest' \
  --stacktrace
```

Expected: FAIL because `com.android.dynamic-feature` is not yet settings-owned and therefore does not produce the SimpleDSL compatibility diagnostic.

- [ ] **Step 3: Commit the second RED**

```bash
git add simpledsl-core/src/test/groovy/io/github/qigao/simpledsl/gradle/settings/DynamicFeaturePluginVersionTest.groovy
git commit -m "test: require pinned Android dynamic feature plugin version"
```

Do not change distribution ownership in this commit.

---

### Task 3: Implement the minimal module type and settings ownership

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
- Produces `SimpleDslAndroidDynamicFeatureSpec` with `Property<String> namespace` and `Property<String> baseModule`.
- Produces `androidDynamicFeature(Action)` and `androidDynamicFeature(Closure)`.
- Produces `SimpleDslAndroidApplicationSpec.getDynamicFeatures(): SetProperty<String>` and `dynamicFeature(String)`.
- Produces module claim `android-dynamic-feature`.
- Produces settings ownership of `com.android.dynamic-feature` through existing `androidGradlePluginVersion`.

- [ ] **Step 1: Add application declaration state**

`SimpleDslAndroidApplicationSpec` becomes:

```groovy
package io.github.qigao.simpledsl.gradle.android

import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

abstract class SimpleDslAndroidApplicationSpec {
    abstract Property<String> getNamespace()
    abstract Property<String> getApplicationId()
    abstract SetProperty<String> getDynamicFeatures()

    void dynamicFeature(String projectPath) {
        dynamicFeatures.add(projectPath)
    }
}
```

Do not inject or look up `Project` in the spec.

- [ ] **Step 2: Add Android-local project-path validation**

Extend `SimpleDslAndroidBase` with:

```groovy
static String requireBaseModule(Project project, Property<String> baseModule) {
    String value = baseModule.isPresent() ? baseModule.get().trim() : null
    if (value == null || value.isEmpty()) {
        throw new GradleException(
                'SimpleDSL Android configuration error\n' +
                "Project: ${project.path}\n" +
                'Problem: androidDynamicFeature requires baseModule')
    }
    requireExternalProjectPath(
            project,
            'baseModule',
            value,
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

The private helper must implement exactly these rules:

```text
blank or !startsWith(':') ->
  SimpleDSL Android configuration error
  Project: <current path>
  Problem: <field> must be an absolute Gradle project path beginning with ':'
  Value: <value>          # include for nonblank values

value == project.path ->
  SimpleDSL Android configuration error
  Project: <current path>
  Problem: <caller-provided self-reference message>
  Value: <value>
```

It must not call `project.project`, `evaluationDependsOn`, target extensions, or target tasks.

- [ ] **Step 3: Add the Dynamic Feature spec and internal module plugin**

`SimpleDslAndroidDynamicFeatureSpec.groovy`:

```groovy
package io.github.qigao.simpledsl.gradle.android

import org.gradle.api.provider.Property

abstract class SimpleDslAndroidDynamicFeatureSpec {
    abstract Property<String> getNamespace()
    abstract Property<String> getBaseModule()
}
```

`SimpleDslAndroidDynamicFeaturePlugin.groovy`:

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

If AGP 9.0.1 requires explicit public generic parameters on either public type, adjust only public type spelling/imports. Do not fall back to AGP implementation classes or name-based production extension lookup.

- [ ] **Step 4: Wire the third module type into `SimpleDslAndroidExtension`**

Add imports and methods:

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

private SimpleDslAndroidDynamicFeatureSpec createDynamicFeatureSpec() {
    rejectDuplicateModuleDeclaration()
    project.extensions.create(
            SimpleDslAndroidDynamicFeaturePlugin.SPEC_EXTENSION,
            SimpleDslAndroidDynamicFeatureSpec)
}
```

Expand `rejectDuplicateModuleDeclaration()` to check the application, library, and Dynamic Feature spec extension names. Keep the existing conflict diagnostic text. Do not change `configureBackendCapability(...)`.

- [ ] **Step 5: Map application feature paths to public AGP DSL**

After `ApplicationExtension` is created/configured in `SimpleDslAndroidApplicationPlugin`, add:

```groovy
Set<String> dynamicFeatures = SimpleDslAndroidBase.requireDynamicFeaturePaths(
        project,
        spec.dynamicFeatures.getOrElse(Collections.emptySet()))
android.dynamicFeatures.addAll(dynamicFeatures)
```

No feature project lookup/configuration is allowed here.

- [ ] **Step 6: Add the typed Dynamic Feature Android Components overload**

`SimpleDslAndroidComponents` adds:

```groovy
import com.android.build.api.variant.DynamicFeatureAndroidComponentsExtension

static void configure(Project project, DynamicFeatureAndroidComponentsExtension components) {
    configureCallbacks(project, components)
}
```

Reuse the existing `selector().all()`, `beforeVariants`, `onVariants`, and cache-safe diagnostic task implementation unchanged.

- [ ] **Step 7: Add the third AGP plugin ID to existing settings ownership**

In `SimpleDslDistribution.OWNED_PLUGIN_MODULES` add:

```groovy
'com.android.dynamic-feature' : 'com.android.tools.build:gradle'
```

In `OWNED_PLUGIN_VERSION_KEYS` add:

```groovy
'com.android.dynamic-feature' : 'androidGradlePluginVersion'
```

Do not add a new version catalog entry, metadata key, or Android backend dependency.

- [ ] **Step 8: Run core and Android targeted suites separately, then all plugin checks**

Do not combine core/android test tasks behind one set of `--tests` filters; Gradle can treat nonmatching filters per task as an error.

Run:

```bash
./gradlew :simpledsl-core:test \
  --tests '*DynamicFeaturePluginVersionTest' \
  --stacktrace
```

Then:

```bash
./gradlew :simpledsl-android:test \
  --tests '*DynamicFeatureModuleTest' \
  --tests '*AndroidComponentsIntegrationTest' \
  --stacktrace
```

Then:

```bash
./gradlew \
  verifyProductNamespace \
  :simpledsl-core:check \
  :simpledsl-java:check \
  :simpledsl-android:check \
  --stacktrace
```

Expected: GREEN, including unchanged existing capability allow-lists.

- [ ] **Step 9: Inspect unstaged/staged production diff for architecture leakage**

Before committing, run:

```bash
git diff --name-only
git diff --cached --name-only
```

The production diff must not require changes to:

```text
SimpleDslModuleModel.groovy
CapabilitySpec.groovy
CapabilityEngine.groovy
DependencyBridge.groovy
BuiltinAndroidCapabilities.groovy
simpledsl-android/build.gradle.kts
repository manifest/snapshot schema
```

If one appears necessary solely for Dynamic Feature, stop and revisit the approved spec rather than generalizing the architecture.

- [ ] **Step 10: Commit minimal production GREEN**

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
- Base app: `dynamicFeature(':payments')`.
- Feature: `androidDynamicFeature { namespace = 'example.payments'; baseModule = ':app' }`.
- Real code edge: `example.payments.PaymentsFeature` imports `example.app.BaseFeatureContract`.
- Bundle proof: debug AAB contains `payments/manifest/AndroidManifest.xml`.

- [ ] **Step 1: Register `:payments` from the existing published application**

Modify only the application declaration:

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

Keep all existing Compose/Room/Hilt dependency/plugin assertions.

- [ ] **Step 2: Add a base-app type used only to prove feature compilation**

Create:

```kotlin
package example.app

object BaseFeatureContract {
    const val source: String = "base-app"
}
```

Do not move it to a library: the proof must demonstrate the documented feature-to-base application compile edge.

- [ ] **Step 3: Add the published `:payments` module without capabilities**

`payments/build.gradle`:

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

Do not call Compose/KSP/Room/Hilt methods in this module.

The consumer settings plugin already performs module discovery. Adding `payments/build.gradle` should therefore discover `:payments`; do not add a manual `include` unless a failing published-consumer test proves discovery does not cover the new folder. If that happens, diagnose discovery before changing settings because automatic discovery is part of the established consumer contract.

- [ ] **Step 4: Add valid feature packaging metadata**

`payments/src/main/AndroidManifest.xml`:

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

`payments/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="payments_title">Payments</string>
</resources>
```

This is fixture-level Play Feature Delivery metadata, not a new SimpleDSL delivery DSL.

- [ ] **Step 5: Add the feature source that imports the base application**

Create:

```kotlin
package example.payments

import example.app.BaseFeatureContract

object PaymentsFeature {
    val baseSource: String = BaseFeatureContract.source
}
```

It must compile under AGP built-in Kotlin with no Kotlin Android plugin.

- [ ] **Step 6: Extend published variant/configuration-cache coverage**

In `resolvesPublishedAndroidMarkersAndReusesConfigurationCache()`, add:

```text
:payments:simpledslAndroidVariants
```

to both runs. Keep existing app/library capability assertions. Do not require a feature capability list; this first slice deliberately enables none.

- [ ] **Step 7: Expand the assembly proof to a real base-app bundle and inspect it**

Rename the existing method so the name states Room/Hilt + Dynamic Feature bundle coverage. Build:

```groovy
BuildResult result = build(
        fixture,
        consumerArguments(':app:bundleDebug', ':feature:assembleDebug'))
```

Keep the existing assertions for:

```text
app/build/generated/ksp/**/AppDatabase_Impl.kt
app/build/generated/hilt/component_sources/**/Hilt_ExampleApplication.java
```

Then locate exactly one `.aab` under:

```text
app/build/outputs/bundle/debug
```

and inspect it:

```groovy
Path bundleRoot = fixture.toPath().resolve('app/build/outputs/bundle/debug')
List<Path> bundles = Files.walk(bundleRoot).withCloseable { paths ->
    paths.filter { path ->
        Files.isRegularFile(path) && path.fileName.toString().endsWith('.aab')
    }.toList()
}
assertTrue(
        bundles.size() == 1,
        "Expected one debug AAB under ${bundleRoot}, found ${bundles}".toString())

new java.util.zip.ZipFile(bundles.single().toFile()).withCloseable { zip ->
    assertTrue(
            zip.getEntry('payments/manifest/AndroidManifest.xml') != null,
            'Debug AAB does not contain the payments Dynamic Feature manifest')
}
```

If AGP 9.0.1 proves a different stable feature-module prefix in the actual AAB, inspect the produced ZIP once and correct only this exact assertion to the observed module entry. Do not weaken the proof to “an AAB exists”.

- [ ] **Step 8: Run the published consumer and isolation gate**

```bash
./gradlew \
  publishToTestPluginRepository \
  verifyBackendIsolation \
  :integration-tests-android:test \
  --stacktrace
```

Expected GREEN proves:

```text
existing app/library Compose + Room + Hilt behavior
payments debug/release variants
configuration-cache store/reuse
feature source -> base app source compilation
:app:bundleDebug
payments feature entry in the AAB
backend isolation
```

`verify-backend-isolation.sh` should not change because Dynamic Feature reuses the already-required `com.android.tools.build:gradle` coordinate.

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
- No workflow change expected.

**Interfaces:**
- Documents three Android module types without adding a fourth public SimpleDSL plugin.
- Keeps #26 as roadmap index and #27 as the Dynamic Feature execution issue.

- [ ] **Step 1: Document the public topology in README**

Update the Android backend description to application/library/dynamic-feature. Add:

```groovy
// :app
simpledsl {
    androidApplication {
        namespace = 'com.example.app'
        dynamicFeature(':payments')
    }
}
```

and:

```groovy
// :payments
simpledsl {
    androidDynamicFeature {
        namespace = 'com.example.payments'
        baseModule = ':app'
    }
}
```

State explicitly:

```text
base application owns AGP dynamicFeatures
feature owns implementation project(baseModule)
no cross-project mutation
dynamic feature reuses AGP 9.0.1 / compileSdk 36 / Java 21 policy
Compose/KSP/Room/Hilt are not yet supported on android-dynamic-feature
```

Do not document delivery-mode DSL or Hilt feature DI as supported.

- [ ] **Step 2: Record the foundation in CHANGELOG**

Under `Unreleased - 0.3.0 development`, add `### Android Dynamic Feature module foundation` and record:

```text
android-dynamic-feature module type
explicit bilateral base/feature topology
settings-owned com.android.dynamic-feature at AGP 9.0.1
public DynamicFeatureExtension / DynamicFeatureAndroidComponentsExtension only
AGP built-in Kotlin; no org.jetbrains.kotlin.android
real published AAB containing payments module
configuration-cache and backend-isolation proof
existing capability allow-lists deliberately unchanged
```

Remove generic `dynamic features` from the later-work list, but keep capability support inside Dynamic Feature, delivery behavior, KMP, benchmark modules, custom artifact transforms, and per-module SDK overrides as later work.

- [ ] **Step 3: Run the local equivalent of every CI gate from clean state**

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

Every command must succeed before completion claims.

- [ ] **Step 4: Review the complete implementation diff against the approved boundary**

Run:

```bash
git diff --name-only 0af61701f78402d2f61f2435a7786267e7ff7005...HEAD
```

Verify these production files are absent:

```text
SimpleDslModuleModel.groovy
CapabilitySpec.groovy
CapabilityEngine.groovy
DependencyBridge.groovy
BuiltinAndroidCapabilities.groovy
simpledsl-android/build.gradle.kts
manifest/snapshot schema files
```

Search introduced production code for forbidden patterns:

```text
applicationVariants
BaseExtension
AppExtension
evaluationDependsOn
taskGraph
org.jetbrains.kotlin.android
```

Any introduced occurrence requires investigation before proceeding.

- [ ] **Step 5: Commit documentation/final branch state**

```bash
git add README.md CHANGELOG.md
git commit -m "docs: document Android dynamic feature module"
```

- [ ] **Step 6: Push and open a draft PR for #27**

Title:

```text
feat: add Android dynamic feature module
```

Body must cover:

```text
public androidDynamicFeature module DSL
explicit app dynamicFeature(path) + feature baseModule topology
no cross-project mutation
settings-owned com.android.dynamic-feature / AGP 9.0.1
public DynamicFeatureExtension + DynamicFeatureAndroidComponentsExtension
feature Kotlin -> base app type compile proof
debug AAB contains payments feature module
configuration-cache + backend-isolation proof
existing capability allow-lists unchanged
RED/GREEN evidence

Closes #27
Ref #26
```

Keep it draft until remote exact-head CI is green.

- [ ] **Step 7: Require remote exact-head GREEN**

Use the existing CI unchanged. On the final PR head, require one CI run whose head SHA exactly matches the branch head and whose `verify` job has all of these successful:

```text
Verify Android SDK baseline
Clean workspace
Verify plugin build
Verify published consumer contracts
Verify wrapper metadata
```

If CI fails, diagnose the specific failure, make the smallest corrective commit, and repeat the exact-head gate. Preserve RED/GREEN history.

- [ ] **Step 8: Synchronize #27 and PR evidence without creating a new git head**

Record in issue/PR metadata:

```text
contract RED commit/run
settings ownership RED commit/run
minimal production GREEN commit/run
published AAB consumer GREEN commit/run
final exact-head SHA/run
```

Do not commit evidence-only metadata after the final exact-head run.

- [ ] **Step 9: Ready/merge gate remains separately authorized**

Freshly verify on the same head:

```text
PR open
mergeable true
not draft
no unresolved review threads
no blocking reviews/comments
exact-head CI success
base master still compatible
```

Report that gate and stop. Do not merge until a separate explicit user authorization. On authorization, merge with `expected_head_sha=<final-head-sha>`, then verify the PR is merged, #27 closed by `Closes #27`, and `master` points to the returned merge commit.

---

## Self-Review Checklist

- [x] Every approved spec requirement maps to a Task above.
- [x] No placeholder/TBD implementation step remains.
- [x] Dynamic Feature remains a module type, never a capability.
- [x] Both topology directions are explicit and local.
- [x] No cross-project mutation or repository graph schema is introduced.
- [x] Settings ownership reuses existing AGP 9.0.1 metadata/artifact.
- [x] Dynamic Feature does not own targetSdk.
- [x] Public Android Components coverage includes debug/release + configuration cache.
- [x] Existing capabilities remain application/library-only.
- [x] Real feature source imports a real base-app type.
- [x] Final packaging proof inspects the AAB feature entry.
- [x] Existing Room/Hilt generated-source proofs remain intact.
- [x] Backend isolation remains unchanged and required.
- [x] Generic core module/capability/dependency mechanics remain unchanged.
- [x] Core/android `--tests` filters are executed in separate invocations.
- [x] TestKit Dynamic Feature fixtures include valid feature packaging metadata.
- [x] Final merge remains a separately authorized action after exact-head GREEN.
