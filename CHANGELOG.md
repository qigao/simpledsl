# Changelog

## Unreleased - 0.3.0 development

SimpleDSL 0.3.0 is evolving the project side into independent build backends. Phase A established the shared core and Java backend; Phase B adds the Android backend foundation. The release is still in development and has not been tagged.

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
```

Application:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.android'
}

simpledsl {
    androidApplication {
        namespace = 'com.example.app'
    }
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
}
```

### Not in Phase B

Compose remains Phase C and is intentionally not part of the Android foundation. Room, Hilt, KSP, KMP, dynamic features, benchmark module types, legacy variant APIs, task-name guessing, and per-module SDK overrides are also out of Phase B scope.

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
