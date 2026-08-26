# Changelog

## Unreleased - 0.3.0 development

SimpleDSL 0.3.0 is evolving the project side into independent build backends. Phase A established the shared core and Java backend; Phase B added the Android backend foundation; Phase C added Jetpack Compose; Phase D added the KSP foundation; Phase E adds Room on top of that foundation without expanding the generic capability model. The release is still in development and has not been tagged.

### Phase A: shared core and Java backend

- Split the implementation artifacts into backend-neutral `simpledsl-core` and Java/Spring-specific `simpledsl-java`.
- Added the public Java backend plugin `io.github.qigao.simpledsl.java`.
- Removed the ambiguous 0.2.x project marker `io.github.qigao.simpledsl.build`; settings fail with explicit migration guidance for Java consumers.
- Moved catalog bridging, dependency binding, capability primitives, the backend-neutral module model, common diagnostics, and backend claiming into the shared core.
- Moved Java/Spring modules, capabilities, schema helpers, and Java-side third-party Gradle tooling into the Java backend.
- Upgraded the internal dependency snapshot protocol to schema version 2 with backend policy maps.
- `simpledsl.java` remains valid manifest syntax but is no longer required during Settings evaluation; the Java backend requires it when applied.
- Renamed the real published Java integration suite to `integration-tests-java`.

### Phase B: Android backend foundation

- Added independent `simpledsl-android` implementation artifact and public `io.github.qigao.simpledsl.android` plugin marker.
- Added strict `[simpledsl.android]` TOML/YAML policy for Android Java target, `compile-sdk`, `min-sdk`, and optional `target-sdk`.
- Bridged Android policy through snapshot schema v2 without making Java policy mandatory for Android-only repositories.
- Pinned the Phase B baseline to Gradle 9.1.0, AGP 9.0.1, compileSdk 36, and Java 21 Android policy.
- Added managed settings resolution for `com.android.application` and `com.android.library` so the repository owns the AGP version.
- Added `androidApplication()` and `androidLibrary()` using AGP 9 public DSL APIs (`ApplicationExtension` / `LibraryExtension`).
- Android application modules require `target-sdk`; library-only repositories may omit it.
- Android uses AGP 9 built-in Kotlin support; SimpleDSL does not apply `org.jetbrains.kotlin.android`.
- Added public Android Components integration through `beforeVariants` / `onVariants` and the configuration-cache-safe `simpledslAndroidVariants` diagnostic task.
- Added a real `integration-tests-android` published-consumer suite. It resolves SimpleDSL and AGP from the isolated publication path, verifies variant output and configuration-cache reuse, and assembles application/library debug artifacts.
- Expanded the published distribution to exactly three public markers and three implementation artifacts: settings/core, Java, and Android.
- Strengthened `verifyBackendIsolation`: core carries neither backend tooling; Java depends on core but not Android/AGP; Android depends on core + AGP but not Java/Spring tooling.
- Extended CI and release verification so all three plugin projects, both real published consumers, isolation gates, Android SDK baseline, and wrapper metadata are checked before publication.

### Phase C: Jetpack Compose capability

- Added semantic Android capability `compose`, valid for `android-application` and `android-library` modules.
- Added public DSL sugar `jetpackCompose()` and equivalent generic entry `capability('compose')`.
- Deliberately avoided a bare `compose()` Groovy DSL method because Gradle configuration closures inherit Groovy's existing `Closure.compose(Closure)`, which collides with the intended zero-argument method.
- Added managed settings ownership for `org.jetbrains.kotlin.plugin.compose` at 2.2.10, aligned with the AGP 9.0.1 built-in Kotlin line.
- Enabled Compose through AGP public DSL `buildFeatures.compose = true`; no legacy Android DSL or task-name interception was introduced.
- Kept Compose library versions in the repository dependency manifest. The Phase C consumer baseline uses Compose BOM 2026.06.00, with versionless `compose-runtime` and `compose-ui` aliases referencing platform `compose`.
- Reused the existing dependency bridge so enabling Compose activates the BOM and records `implementation:compose`; capability code contains no Compose runtime/UI Maven versions.
- Kept `simpledsl-core` unchanged and backend-neutral. Android-specific capability registration and AGP mutation live in `simpledsl-android`; no generic core activation callback was added.
- Extended backend isolation so Compose compiler tooling is Android-only and absent from core/Java publications.
- Added real published app/library Compose consumers using AGP 9 built-in Kotlin. CI compiles real `@Composable` Kotlin sources, assembles both debug artifacts, verifies `Features: compose` / `implementation:compose`, and proves configuration-cache reuse.
- Kept the repository baseline at AGP 9.0.1 / compileSdk 36. Compose 1.12's API 37 line requires a newer AGP baseline and is intentionally outside Phase C.

### Phase D: KSP capability foundation

- Added semantic Android capability `ksp`, valid for `android-application` and `android-library` modules.
- Added public DSL sugar `ksp()` and equivalent generic entry `capability('ksp')`; both route through the existing `CapabilityEngine`.
- Added settings ownership for `com.google.devtools.ksp` and its plugin coordinate so incompatible consumer version overrides fail through the existing compatibility diagnostic.
- Pinned the validated KSP2 baseline to 2.3.9. The initial AGP 9.0 compatibility floor `2.2.10-2.0.2` was rejected by TDD because it still uses `kotlin.sourceSets` under AGP built-in Kotlin; KSP 2.3.1 is the line that introduced AGP 9 built-in Kotlin support.
- Kept AGP 9 built-in Kotlin enabled and continued to forbid `org.jetbrains.kotlin.android`; no compatibility opt-out such as `android.disallowKotlinSourceSets=false` was added.
- Kept `simpledsl-core` and the generic capability model unchanged. No code-generation framework, activation callback, processor DSL, or generated-source abstraction was introduced.
- Kept KSP Gradle tooling Android-only and extended published backend isolation to forbid it in core/Java and require it in the Android artifact.
- Added real published consumers: the application activates `ksp()` while the library exercises `capability('ksp')`; both prove the KSP plugin and standard `ksp` configuration are available alongside Compose and AGP built-in Kotlin.
- Published-consumer CI continues to compile real Kotlin/Compose sources, assemble application/library debug artifacts, and reuse Gradle configuration cache with KSP active.
- Established the composition point used by later code-generation capabilities: `requires('ksp')` plus a dependency bound to the `ksp` configuration.

### Phase E: Room capability

- Added semantic Android capability `room`, valid for `android-application` and `android-library` modules.
- Added public DSL sugar `room()` and equivalent generic entry `capability('room')`.
- Pinned the repository-policy consumer baseline to stable Room3 3.0.1 using `androidx.room3:room3-runtime` and `androidx.room3:room3-compiler`.
- Defined Room entirely with existing capability primitives: `require('ksp')`, `dependency('implementation', 'room-runtime')`, and `dependency('ksp', 'room-compiler')`.
- Enabling Room transitively activates KSP2 2.3.9 and the standard `ksp` configuration; consumers do not call `ksp()` separately.
- Kept `simpledsl-core`, `CapabilitySpec`, `CapabilityEngine`, and the dependency bridge unchanged. Phase E therefore validates that the KSP foundation is sufficient for a real processor-backed capability without a generic code-generation subsystem.
- Kept AGP 9.0.1 built-in Kotlin and continued to forbid `org.jetbrains.kotlin.android`.
- Deliberately left the Room Gradle plugin, schema-directory DSL, schema/migration lifecycle, processor arguments, Room 2.x, KAPT, and Java annotation-processing compatibility out of scope.
- Extended the real published app/library consumers so the application uses `room()` and the library uses `capability('room')`; both prove Room's transitive KSP activation and Room runtime/compiler dependency bindings.
- Added real Room3 Kotlin source (`@Entity`, `@Dao`, and `@Database`) to the published application and verify KSP generates `AppDatabase_Impl.kt` before the debug application/library artifacts are assembled.
- Preserved configuration-cache reuse, Compose coexistence, backend isolation, and the existing publication model; Room runtime/compiler remain consumer dependencies rather than Android plugin tooling dependencies.
- TDD/CI chronology: CI #180 is the test-only RED contract, CI #181 is GREEN for the minimal production capability, and CI #182 is GREEN for real published Room3 code generation.

### 0.2.x to 0.3.0 Java migration

Project builds change only the SimpleDSL project entry plugin:

```groovy
// 0.2.x
plugins {
    id 'io.github.qigao.simpledsl.build'
}

// 0.3.0 development
plugins {
    id 'io.github.qigao.simpledsl.java'
}
```

The repository-root TOML/YAML dependency manifest, `simpledsl.java`, library aliases, platform aliases, Java/Spring module DSL, and existing Java capabilities remain compatible.

### Android policy example

```toml
[simpledsl.android]
java = 21
compile-sdk = 36
min-sdk = 24
target-sdk = 36

[versions]
compose-bom = "2026.06.00"
room3 = "3.0.1"

[libraries.compose]
module = "androidx.compose:compose-bom"
version.ref = "compose-bom"

[libraries.compose-runtime]
module = "androidx.compose.runtime:runtime"
platform = "compose"

[libraries.compose-ui]
module = "androidx.compose.ui:ui"
platform = "compose"

[libraries.room-runtime]
module = "androidx.room3:room3-runtime"
version.ref = "room3"

[libraries.room-compiler]
module = "androidx.room3:room3-compiler"
version.ref = "room3"
```

Application with Compose and Room:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.android'
}

simpledsl {
    androidApplication {
        namespace = 'com.example.app'
    }
    jetpackCompose()
    room()
}
```

Library:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.android'
}

simpledsl {
    androidLibrary {
        namespace = 'com.example.feature'
    }
    capability('room')
}
```

### Later Android phases

Hilt, KMP, dynamic features, benchmark module types, custom artifact transforms, and per-module SDK overrides remain later work. The Room Gradle plugin/schema lifecycle, legacy variant APIs, task-name guessing, KSP1, KAPT, Room 2.x compatibility, and built-in-Kotlin opt-outs remain out of scope by design.

---

## 0.2.0 - 2026-08-25

SimpleDSL 0.2.0 modernizes dependency manifest configuration around a Gradle-shaped vocabulary and adds YAML as a first-class serialization format.

### Highlights

- Dependency manifests now live at the repository root and are discovered as exactly one of `dependencies.toml`, `dependencies.yml`, or `dependencies.yaml`.
- TOML dependency declarations now use the familiar Gradle Version Catalog vocabulary: `versions`, `libraries`, `plugins`, and `version.ref`.
- YAML manifests are supported with the same semantic model as TOML.
- Includes may mix TOML and YAML files and are resolved relative to the declaring file.
- Java policy is now declared as `simpledsl.java`.
- BOM/platform coordinates are normal library declarations and are referenced through `platform`; the public `[platforms]` table has been removed.
- A non-default manifest location can still be selected explicitly through `simpledslSettings.dependencyManifest`.
- Manifest validation remains strict: duplicate aliases, include cycles, malformed coordinates, unknown platform aliases, unsupported keys, and ambiguous root manifests fail fast.

### Breaking changes

Projects upgrading to 0.2.0 must migrate the dependency manifest contract:

- `gradle/simpledsl/dependencies.toml` is no longer used as a legacy fallback. Move the manifest to the repository root or configure `simpledslSettings.dependencyManifest` explicitly.
- Replace `[java] version = ...` with `[simpledsl] java = ...`.
- Replace public `[platforms.<alias>]` declarations with normal `[libraries.<alias>]` BOM declarations.
- If more than one default root manifest exists, configuration fails instead of applying an implicit format precedence.

Example migration:

```toml
include = ["dependencies/spring.toml", "dependencies/test.yml"]

[simpledsl]
java = 25

[versions]
spring-boot = "4.1.0"

[libraries.spring]
module = "org.springframework.boot:spring-boot-dependencies"
version.ref = "spring-boot"

[libraries.spring-web]
module = "org.springframework.boot:spring-boot-starter-web"
platform = "spring"
```

### Compatibility

- The internal dependency snapshot remains schema version 1 in the 0.2.0 release.
- The built-in Spring integration continues to use the semantic platform alias `spring`.
- The public Plugin Portal surface remains `io.github.qigao.simpledsl.settings` and `io.github.qigao.simpledsl.build` for 0.2.0.

### Verification

The 0.2.0 release contract is covered by bootstrap/build-logic tests, strict manifest validation, a real published consumer using root TOML with included YAML/TOML fragments, and configuration-cache reuse.

GitHub issue: #4
Pull request: #5
