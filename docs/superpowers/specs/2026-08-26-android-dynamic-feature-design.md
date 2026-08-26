# Android Dynamic Feature Module Design

## Context

SimpleDSL 0.3.0 established a shared repository-policy/settings layer with independent Java and Android project backends. The Android backend now supports application and library module types plus Compose, KSP, Room, and Hilt capabilities. The completed architecture deliberately separates **module type** from **capability**:

```text
repository policy / settings
          ↓
      snapshot v2
          ↓
    simpledsl-core
      ├─ simpledsl-java
      └─ simpledsl-android
           ├─ module: android-application
           ├─ module: android-library
           └─ capabilities: compose / ksp / room / hilt
```

Issue #26 tracks later Android expansion after the original backend split. Issue #27 selects the first of those follow-ups: Android Dynamic Feature support.

A dynamic feature is not an optional behavior added to an existing Android module. It is a distinct AGP project/module kind with its own plugin, DSL, variants, packaging behavior, and required relationship to a base application. It therefore belongs in the existing SimpleDSL **module model**, not in the capability engine.

This design adds exactly one new Android module type and the minimum topology needed to connect it to an application. It intentionally does not generalize project graphs, feature delivery, or code generation.

## External platform facts

The implementation is constrained by Android Gradle Plugin 9.0.1, the baseline already owned by SimpleDSL.

AGP exposes first-class public APIs for dynamic features:

- plugin ID: `com.android.dynamic-feature`;
- public DSL: `com.android.build.api.dsl.DynamicFeatureExtension`;
- public components extension: `com.android.build.api.variant.DynamicFeatureAndroidComponentsExtension`;
- public variant types: `DynamicFeatureVariantBuilder` and `DynamicFeatureVariant`.

The AGP 9 API reference describes `DynamicFeatureExtension` as the `android` block created by `com.android.dynamic-feature`, and the Android Components API exposes `DynamicFeatureAndroidComponentsExtension` as the corresponding public components extension.

Official Android feature-delivery documentation also defines the module relationship in both directions:

1. the base application lists feature project paths in `android.dynamicFeatures`;
2. the feature declares an `implementation project(':app')` dependency on its base application.

The feature can directly compile against code in the base module. App Bundle packaging then includes the feature as a separate bundle module.

AGP 9 built-in Kotlin is enabled for AGP modules by default. Dynamic Feature therefore follows the existing SimpleDSL Android baseline: no `org.jetbrains.kotlin.android` plugin is applied.

References:

- https://developer.android.com/reference/tools/gradle-api/9.0/com/android/build/api/dsl/DynamicFeatureExtension
- https://developer.android.com/reference/tools/gradle-api/9.0/com/android/build/api/variant/AndroidComponentsExtension
- https://developer.android.com/guide/playcore/feature-delivery
- https://developer.android.com/build/migrate-to-built-in-kotlin

## Goals

1. Add Dynamic Feature as a first-class Android **module type**.
2. Keep `simpledsl-core` backend-neutral; only extend distribution ownership for the third AGP plugin ID, with no generic module/capability/dependency-engine changes.
3. Reuse the existing Android repository policy for Java level, `compileSdk`, and `minSdk`.
4. Apply `com.android.dynamic-feature` under the same settings-owned AGP 9.0.1 compatibility contract as `com.android.application` and `com.android.library`.
5. Use only AGP public DSL and Android Components APIs.
6. Model the base application / feature relationship explicitly and locally, without cross-project mutation.
7. Preserve AGP 9 built-in Kotlin and keep `org.jetbrains.kotlin.android` unapplied.
8. Preserve configuration-cache compatibility.
9. Prove the feature through a real published consumer that builds an Android App Bundle containing the feature.
10. Avoid introducing new generic module-graph or capability abstractions unless the current primitives prove insufficient.

## Non-goals

This first Dynamic Feature slice does not implement:

- Compose inside dynamic feature modules;
- KSP, Room, or Hilt inside dynamic feature modules;
- Hilt dynamic-feature dependency-injection patterns;
- Play Feature Delivery runtime APIs such as `SplitInstallManager`;
- convenience DSL for install-time, on-demand, conditional, or removable delivery;
- product flavors;
- feature-specific ProGuard convenience DSL;
- asset packs;
- custom Android artifact transforms;
- a generic cross-project dependency graph model;
- automatic mutation of the base application from a feature project;
- new repository manifest schema for project topology.

These can be separate slices if a concrete use case requires them.

## Architectural classification

Dynamic Feature extends the Android module taxonomy:

```text
Android backend
  module types
    android-application
    android-library
    android-dynamic-feature   ← new

  capabilities
    compose
    ksp
    room -> requires ksp
    hilt -> requires ksp
```

This distinction matters. A capability answers:

> What optional behavior/tooling does this already-declared module enable?

A module type answers:

> What kind of Gradle/AGP project is this?

`com.android.dynamic-feature` determines the latter. Treating it as a capability would allow impossible combinations such as an application and dynamic-feature plugin on the same Gradle project and would bypass the existing single-module-type claim contract.

## Considered approaches

### Recommended: explicit relationship on both projects

The base application explicitly registers its feature path. The feature explicitly names its base module.

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

SimpleDSL maps these declarations directly onto the two AGP relationships:

```text
:app spec dynamicFeature(':payments')
        ↓
ApplicationExtension.dynamicFeatures += ':payments'

:payments spec baseModule = ':app'
        ↓
implementation project(':app')
```

Advantages:

- mirrors the official AGP model;
- avoids evaluation-order-dependent cross-project mutation;
- keeps each project's build configuration understandable in isolation;
- requires no new settings schema;
- makes accidental mismatches visible in the consumer configuration and testable;
- remains compatible with Gradle configuration-cache expectations.

This is the selected design.

### Rejected: feature automatically mutates its base application

A single feature declaration could locate `:app` and modify that project's AGP extension to add itself to `dynamicFeatures`.

Rejected because it creates cross-project configuration behavior whose correctness depends on plugin/evaluation ordering. It also makes the base application's packaging topology invisible in the base project itself.

The feature may create a Gradle `ProjectDependency` targeting the base path because that is its own dependency declaration. It must not configure the target project's extensions, tasks, or model.

### Rejected: move project topology into repository policy/settings

Another option is a repository-level graph such as:

```toml
[simpledsl.android.modules.app]
type = "application"
dynamic-features = [":payments"]
```

Rejected because dependency/version policy and project topology are different concerns. The current repository manifest intentionally describes dependency/toolchain policy. Adding module graph semantics for one AGP feature would expand the snapshot schema without a proven second use case.

## Public DSL

### Base application

`SimpleDslAndroidApplicationSpec` gains a set-valued feature path property and one convenience method:

```groovy
simpledsl {
    androidApplication {
        namespace = 'com.example.app'
        dynamicFeature(':payments')
        dynamicFeature(':checkout:promo')
    }
}
```

Conceptually:

```groovy
abstract SetProperty<String> getDynamicFeatures()

void dynamicFeature(String projectPath) {
    dynamicFeatures.add(projectPath)
}
```

The public singular method keeps declaration call sites simple while the model naturally supports multiple features.

The application plugin maps the finalized set to AGP's public `ApplicationExtension.dynamicFeatures` set.

### Dynamic feature

`SimpleDslAndroidExtension` gains:

```groovy
void androidDynamicFeature(Action<? super SimpleDslAndroidDynamicFeatureSpec> action)
void androidDynamicFeature(Closure closure)
```

The spec is intentionally small:

```groovy
abstract class SimpleDslAndroidDynamicFeatureSpec {
    abstract Property<String> getNamespace()
    abstract Property<String> getBaseModule()
}
```

Example:

```groovy
simpledsl {
    androidDynamicFeature {
        namespace = 'com.example.payments'
        baseModule = ':app'
    }
}
```

No delivery-mode fields are added in this slice. Delivery configuration belongs to the feature manifest / future delivery-specific work, not the minimal module topology contract.

## Module claim and duplicate declaration behavior

The new module ID is:

```text
android-dynamic-feature
```

The dynamic-feature internal plugin calls:

```text
SimpleDslModuleModel.claim('android-dynamic-feature', project.path)
```

No change to `SimpleDslModuleModel` is necessary because module IDs are already strings and the claim operation is generic.

`SimpleDslAndroidExtension.rejectDuplicateModuleDeclaration()` expands its check from application/library spec extensions to application/library/dynamic-feature spec extensions. A Gradle project may still declare exactly one SimpleDSL Android module type.

Existing module-conflict diagnostics remain authoritative. No Dynamic Feature-specific conflict subsystem is introduced.

## Validation

SimpleDSL performs validation only where it improves diagnostics without reproducing the AGP/Gradle project model.

### Namespace

Dynamic Feature uses the existing Android namespace validation helper. A blank or absent namespace fails with the same SimpleDSL Android configuration diagnostic used by application/library modules.

### Base module path

`baseModule` is required.

SimpleDSL validates:

- value is present;
- trimmed value is non-empty;
- value is an absolute Gradle project path beginning with `:`;
- value is not equal to the current feature project's own path.

SimpleDSL does **not** eagerly evaluate or configure the target base project to prove that it is an application. The Gradle project dependency and AGP bundle relationship remain the authoritative graph validation. The real consumer test provides the positive integration proof.

### Application feature paths

Every `dynamicFeature(path)` value must be:

- non-empty after trimming;
- an absolute Gradle project path beginning with `:`;
- different from the application project's own path.

Duplicate paths collapse naturally through the set-valued property.

SimpleDSL does not mutate or inspect the target feature project during application configuration.

## Dynamic Feature internal plugin

A new Android-backend-only internal plugin owns the module implementation:

```text
SimpleDslAndroidDynamicFeaturePlugin
```

Its responsibilities are narrowly defined:

1. claim `android-dynamic-feature` in `SimpleDslModuleModel`;
2. read and validate `SimpleDslAndroidDynamicFeatureSpec`;
3. require Android repository policy;
4. apply `com.android.dynamic-feature`;
5. obtain public `DynamicFeatureExtension`;
6. configure namespace, compileSdk, minSdk, and Java compile options from repository policy;
7. create the `implementation` project dependency on `baseModule`;
8. obtain public `DynamicFeatureAndroidComponentsExtension`;
9. connect it to the existing Android Components proof helper.

It does not configure another Gradle project.

## Repository Android policy

The dynamic feature uses the same repository policy as application/library modules:

```text
simpledsl.android.java
simpledsl.android.compile-sdk
simpledsl.android.min-sdk
```

The feature does not own an independent `targetSdk`; the base application remains the package/application authority.

This slice does not add per-module SDK overrides. The shared policy also ensures the dynamic feature's minimum SDK remains aligned with the base application's repository baseline, matching the platform expectation that a feature belongs to the same application.

## AGP public DSL mapping

The implementation uses public AGP 9 interfaces only.

Conceptually:

```text
com.android.dynamic-feature
        ↓
DynamicFeatureExtension
        ├─ namespace
        ├─ compileSdk
        ├─ defaultConfig.minSdk
        └─ compileOptions
```

The production code must not cast to AGP implementation classes or use removed legacy APIs.

No `applicationVariants`, `libraryVariants`, `BaseVariant`, `VariantScope`, internal task classes, or task-name interception is permitted.

## Android Components integration

The current `SimpleDslAndroidComponents` helper has public overloads for application and library components and funnels both into one callback implementation.

Dynamic Feature adds one more typed overload:

```text
configure(Project, DynamicFeatureAndroidComponentsExtension)
```

It then reuses the existing callback behavior:

```text
selector().all()
   ↓
beforeVariants -> validate nonblank variant name
   ↓
onVariants -> add variant name to simpledslAndroidVariants task
```

There is no need to replace this helper with a new generic variant framework. The existing internal `def components` callback body already represents the shared behavior; the typed overload only establishes the supported public AGP boundary.

The feature consumer must prove that default `debug` and `release` variants are observed.

## Base application registration

`SimpleDslAndroidApplicationPlugin` gains one topology mapping after AGP application setup:

```text
spec.dynamicFeatures
      ↓
ApplicationExtension.dynamicFeatures
```

This is application configuration, not a capability.

No new internal plugin is applied to the feature projects from the application project. No target feature project is opened or configured.

## Base dependency

The feature declares its base relationship as a normal Gradle project dependency on its own `implementation` configuration:

```text
implementation project(baseModule)
```

This dependency is intentionally **not** routed through SimpleDSL's catalog `DependencyBridge`:

- the dependency is project topology, not repository dependency policy;
- it has no library alias or external coordinate;
- routing it through catalog policy would conflate two separate models.

The implementation should create a `ProjectDependency` from the project path without retrieving/configuring the target project's extensions.

## Settings-owned plugin version

SimpleDSL settings already owns:

```text
com.android.application -> com.android.tools.build:gradle -> androidGradlePluginVersion
com.android.library     -> com.android.tools.build:gradle -> androidGradlePluginVersion
```

It adds:

```text
com.android.dynamic-feature -> com.android.tools.build:gradle -> androidGradlePluginVersion
```

Therefore the dynamic-feature plugin uses the exact same managed AGP 9.0.1 version and the existing compatibility diagnostic.

A consumer that explicitly requests an incompatible version such as:

```groovy
id 'com.android.dynamic-feature' version '9.9.9'
```

must fail through the normal SimpleDSL plugin compatibility error and report the requested and managed versions.

No new distribution metadata version key is introduced.

## Backend artifact isolation

`com.android.dynamic-feature` comes from the existing AGP artifact already owned by `simpledsl-android`. Supporting the additional plugin ID does not add a new tooling dependency.

Isolation expectations remain:

```text
simpledsl-core
  no AGP

simpledsl-java
  no AGP

simpledsl-android
  owns AGP tooling
```

The existing published-POM/backend-isolation gate remains required and should not gain a new artifact dependency merely because another AGP plugin ID is managed.

## Capability boundary in the first slice

Existing built-in Android capabilities currently target application/library module types. This design does not broaden those allow-lists.

Consequently:

```groovy
simpledsl {
    androidDynamicFeature {
        namespace = 'com.example.payments'
        baseModule = ':app'
    }
    jetpackCompose()
}
```

is not part of this slice and should fail through the normal capability/module-type diagnostic unless a later dedicated capability slice explicitly adds and proves dynamic-feature support.

This is intentional for all four existing capabilities:

```text
compose
ksp
room
hilt
```

The first Dynamic Feature change proves **module topology and packaging**, not capability compatibility.

This avoids accidentally claiming support for Hilt Dynamic Feature integration, which has semantics beyond merely applying the base Hilt capability.

## Published consumer topology

The real `integration-tests-android` fixture expands from application + library to:

```text
consumer
  :app       android-application
     |
     | dynamicFeatures contains ':payments'
     |
  :payments  android-dynamic-feature
     |
     | implementation project(':app')
     |
  :feature   android-library
```

The existing application/library proofs remain in place.

The new `:payments` module uses only the Dynamic Feature module declaration in the first slice. It does not enable Compose, KSP, Room, or Hilt.

## Real code dependency proof

A synthetic dependency declaration is insufficient. The feature must compile source that consumes a type defined in the base application.

Example shape:

```text
:app
  src/main/kotlin/example/app/BaseFeatureContract.kt

:payments
  src/main/kotlin/example/payments/PaymentsFeature.kt
      imports example.app.BaseFeatureContract
```

This proves the feature-to-base `implementation project(':app')` relationship is real and usable under AGP 9 built-in Kotlin.

Neither module applies `org.jetbrains.kotlin.android`.

## App Bundle proof

Dynamic Feature exists for App Bundle packaging, so the final integration proof must build the base application bundle rather than stopping at separate module compilation.

The published consumer runs:

```text
:app:bundleDebug
```

Production SimpleDSL code must not depend on that task name; invoking the documented build lifecycle in an integration test is only a consumer-level proof.

The test then opens the generated `.aab` as a ZIP archive and asserts that a separate `payments` bundle module is present, including its manifest entry under the feature module prefix.

The proof should not rely only on `:payments:assembleDebug`, because that would not establish that the base application's `dynamicFeatures` relationship participates in final bundle packaging.

## Variant proof

The dynamic feature participates in the same public Android Components diagnostic surface:

```text
:payments:simpledslAndroidVariants
```

For the unflavored first slice it must report:

```text
debug
release
```

This verifies public `DynamicFeatureAndroidComponentsExtension` integration independently of the AAB packaging check.

Product flavors remain out of scope, so the test does not attempt to prove variant matching beyond the default build types.

## Configuration cache

The published consumer must prove configuration-cache storage and reuse for the Dynamic Feature path.

At minimum, the integration contract runs the Dynamic Feature variant proof twice with configuration cache enabled and validates reuse. The bundle build must also remain configuration-cache compatible with no SimpleDSL-created cross-project configuration callbacks.

The design avoids three common cache hazards:

- no dynamic project evaluation;
- no task graph interception;
- no task actions capturing arbitrary `Project` state.

The existing cache-safe `simpledslAndroidVariants` task remains the diagnostic output boundary.

## Diagnostics

New SimpleDSL-authored errors are limited to malformed module declarations that can be diagnosed before AGP execution.

Expected diagnostics include:

### Missing base module

```text
SimpleDSL Android configuration error
Project: :payments
Problem: androidDynamicFeature requires baseModule
```

### Invalid project path

```text
SimpleDSL Android configuration error
Project: :payments
Problem: baseModule must be an absolute Gradle project path beginning with ':'
Value: app
```

### Self-reference

```text
SimpleDSL Android configuration error
Project: :payments
Problem: androidDynamicFeature baseModule cannot reference the feature project itself
Value: :payments
```

Application `dynamicFeature(...)` validation uses equivalent wording for blank/relative/self paths.

Errors caused by a nonexistent project, a target project that is not a valid base application, or an inconsistent AGP graph remain Gradle/AGP errors unless SimpleDSL can diagnose them without cross-project configuration.

## Test strategy

Implementation must follow RED -> GREEN and preserve the evidence chronology.

### Contract RED

Before production support exists, tests define:

- `androidDynamicFeature { namespace; baseModule }` public DSL;
- module model reports `android-dynamic-feature`;
- `com.android.dynamic-feature` is applied;
- repository compileSdk/minSdk/Java policy is applied;
- `implementation` contains the base project dependency;
- `org.jetbrains.kotlin.android` is absent;
- normal duplicate-module diagnostics include Dynamic Feature;
- malformed/missing base paths fail with SimpleDSL diagnostics.

The expected RED must be the absence of the module API/plugin wiring, not a broken fixture.

### Settings ownership RED

An independent test requires settings to own `com.android.dynamic-feature` at the same AGP version and rejects an incompatible explicit consumer version through the existing compatibility error.

### Minimal production GREEN

Implement only the module type, plugin ownership, application registration, base dependency, and Android Components overload needed by the contract.

Existing Java/Android consumer and isolation tests must remain green.

### Real published consumer GREEN

Extend the isolated consumer with `:payments` and prove:

- feature code compiles against a base-app type;
- base app registers `:payments` in `dynamicFeatures`;
- Dynamic Feature variant diagnostic reports debug/release;
- `:app:bundleDebug` succeeds;
- the `.aab` contains a separate `payments` feature module/manifest entry;
- configuration cache stores and reuses successfully;
- existing app/library Compose + Room + Hilt proof remains green.

### Final exact-head gate

Before merge, exact PR head CI must pass:

- Android SDK baseline;
- plugin/core/Java/Android build;
- published Java consumer;
- published Android application/library/dynamic-feature consumer;
- backend isolation;
- dynamic feature AAB proof;
- configuration-cache proof;
- wrapper metadata.

## Expected production surface

The design should be implementable with focused changes in the existing Android backend and distribution policy rather than a new subsystem.

Expected additions/changes include:

```text
simpledsl-android/
  SimpleDslAndroidExtension
  SimpleDslAndroidApplicationSpec
  SimpleDslAndroidDynamicFeatureSpec          new
  module/SimpleDslAndroidApplicationPlugin
  module/SimpleDslAndroidDynamicFeaturePlugin new
  internal/SimpleDslAndroidComponents

simpledsl-core/
  distribution/SimpleDslDistribution          add plugin-id ownership only

integration-tests-android/
  consumer application registration
  consumer :payments module + source
  published consumer contract
```

`SimpleDslModuleModel`, `CapabilitySpec`, `CapabilityEngine`, and `DependencyBridge` should not require production changes for this design.

If implementation discovers that one of those generic core abstractions must change merely to support Dynamic Feature, that is an architectural surprise: stop and re-evaluate before broadening the design.

## Compatibility and release boundary

This work stays on the existing technical baseline unless a concrete incompatibility is proven:

```text
Gradle 9.1
AGP 9.0.1
compileSdk 36
Android Java 21
AGP built-in Kotlin / KGP 2.2.10 runtime baseline
KSP2 2.3.9 for the existing KSP capability
```

Dynamic Feature adds no new external version coordinate.

The issue is deliberately not assigned a new release number in advance. Release/version policy should follow the repository's next release decision; the architectural requirement is independent of whether the eventual release remains on the 0.3.x line or moves to a later minor version.

## Acceptance criteria

The design is complete when implementation can demonstrate all of the following without expanding scope:

1. `androidDynamicFeature` is a third Android module type backed by `com.android.dynamic-feature`.
2. The base application explicitly owns its Dynamic Feature path set.
3. The feature explicitly owns its base application project dependency.
4. No project mutates another project's extensions or tasks.
5. Settings owns `com.android.dynamic-feature` through the existing AGP 9.0.1 version contract.
6. Dynamic Feature uses public `DynamicFeatureExtension` and `DynamicFeatureAndroidComponentsExtension` only.
7. Repository Android policy configures namespace/compileSdk/minSdk/Java as designed.
8. `org.jetbrains.kotlin.android` remains unapplied.
9. No existing capability is implicitly broadened to Dynamic Feature.
10. Real feature Kotlin compiles against a real base-app type.
11. The base app produces a debug AAB containing the feature as a separate bundle module.
12. Dynamic Feature debug/release variants are observed through Android Components.
13. Configuration cache is stored and reused.
14. Backend isolation remains intact.
15. No generic module-graph/codegen/capability abstraction is introduced without an additional concrete need.
16. Final exact-head CI is GREEN before merge.

## Follow-up boundary

Once this module foundation is proven, future work can independently evaluate:

- Compose capability support for `android-dynamic-feature`;
- KSP/Room support for `android-dynamic-feature`;
- Hilt's documented Dynamic Feature integration pattern;
- Play Feature Delivery install-time/on-demand/conditional DSL;
- flavor-aware feature variant matching;
- feature-specific shrinker rules;
- artifact APIs.

Those are not implied by this issue and should receive separate design/TDD issues when selected.

## Decision

Add Android Dynamic Feature as a **third Android module type** using explicit bilateral topology:

```text
base application
  dynamicFeatures += featurePath

feature module
  implementation project(baseModule)
```

Keep that topology local to the two projects, reuse existing module claiming, Android policy, plugin-version ownership, Android Components diagnostics, published-consumer verification, and backend isolation. Do not modify the generic capability engine or introduce a general project-graph abstraction.