# Java and Android Backend Split Design

## Context

SimpleDSL 0.2.0 established a repository-root dependency policy with Gradle-shaped TOML/YAML vocabulary, mixed-format includes, strict validation, and a settings-side dependency snapshot. The public project plugin, however, is still Java/Spring-oriented: `simpledsl-build-logic` owns both reusable project infrastructure and Java/Spring-specific module, capability, schema, and third-party plugin dependencies.

Android must not be added as another conditional branch inside that artifact. Spring and AGP have different project lifecycles, dependency graphs, and extension/variant models. Loading both backends from one artifact would leak AGP onto Java builds, leak Spring tooling onto Android builds, and turn the public DSL into a union of unrelated methods.

SimpleDSL 0.3.0 therefore becomes a shared repository-policy layer with two independent project backends.

## Goals

1. Preserve one repository-level dependency manifest and one settings plugin for Java, Android, or mixed monorepos.
2. Publish two independent project backends:
   - `io.github.qigao.simpledsl.java`
   - `io.github.qigao.simpledsl.android`
3. Ensure Java and Android backend artifacts never depend on each other.
4. Keep Spring, GraalVM, jOOQ, and jsonschema2pojo dependencies out of the Android backend.
5. Keep AGP dependencies out of the Java backend.
6. Preserve the existing Java/Spring DSL behavior apart from the public project plugin ID migration.
7. Add first-class Android application and Android library module types using AGP public DSL and Variant APIs.
8. Allow Java and Android modules in the same repository to use independent Java levels.
9. Preserve configuration-cache compatibility and verify the published-plugin consumer path, not only source-project tests.
10. Keep the first Android slice small enough to prove the backend boundary before adding Room, Hilt, KSP, or other Android capabilities.

## Non-goals

The foundational backend split does not implement:

- Room, Hilt, KSP, dynamic features, benchmark modules, Kotlin Multiplatform, or custom artifact transforms;
- legacy AGP variant APIs;
- task-name-based Android lifecycle hooks;
- per-module SDK-version overrides;
- an Android-specific dependency manifest format;
- duplicated manifest or catalog parsers per backend.

Compose is intentionally not part of the foundational split. It is the first planned Android capability after the Android backend and its AGP/Kotlin/compileSdk compatibility baseline are proven independently.

## Considered approaches

### One public build plugin with runtime backend branching

Keep `io.github.qigao.simpledsl.build` and add `androidApplication()` / `androidLibrary()` to the existing extension.

Rejected because one artifact would need both the Java/Spring toolchain and AGP on its implementation classpath. The extension would expose Java and Android methods simultaneously, backend conflicts would become runtime conditionals throughout the codebase, and future backend-specific APIs would continuously increase coupling.

### Two backend artifacts with duplicated project infrastructure

Keep the settings bootstrap as-is and copy catalog, dependency, capability, model, and diagnostics support into Java and Android artifacts.

Rejected because the two backends would immediately fork the same snapshot bridge and policy semantics. Fixes to configuration-cache behavior, dependency binding, or diagnostics would have to be implemented twice.

### Shared core plus independent backends

Recommended.

Rename/evolve the current bootstrap artifact into `simpledsl-core`. It remains the implementation artifact for the public settings plugin and also owns backend-neutral project infrastructure. `simpledsl-java` and `simpledsl-android` both depend on `simpledsl-core` and never on each other.

This keeps the number of product backends at exactly two while avoiding a separate unpublished common artifact.

## Repository structure

The target repository layout is:

```text
simpledsl/
├── simpledsl-core/
│   ├── settings / manifest / discovery / distribution
│   ├── dependency snapshot bridge
│   ├── catalog and dependency binding
│   ├── backend-neutral module model
│   ├── capability engine and registries
│   ├── backend guard
│   └── common diagnostics
│
├── simpledsl-java/
│   ├── Java and Spring module types
│   ├── Java/Spring capabilities
│   ├── Java/Spring internal plugins
│   └── jOOQ / JSON schema helpers
│
├── simpledsl-android/
│   ├── Android application module type
│   ├── Android library module type
│   ├── AGP public DSL adapters
│   └── Android Components / Variant integration
│
├── integration-tests-java/
└── integration-tests-android/
```

The existing `simpledsl-build-bootstrap` and `simpledsl-build-logic` names disappear after migration. Their code is redistributed by responsibility rather than copied wholesale.

## Dependency direction

```text
                 simpledsl-core
                /              \
               v                v
       simpledsl-java    simpledsl-android
```

Rules:

- `simpledsl-core` has no Spring, AGP, GraalVM, jOOQ, or jsonschema2pojo implementation dependency.
- `simpledsl-java` depends on `simpledsl-core` and Java/Spring-side tooling only.
- `simpledsl-android` depends on `simpledsl-core` and AGP only for the foundational slice.
- `simpledsl-java` and `simpledsl-android` never depend on one another.

A published-consumer test must inspect/resolve this graph so the boundary is enforced at artifact level, not just source-package level.

## Public plugin surface

0.3.0 publishes exactly three public plugin IDs:

```text
io.github.qigao.simpledsl.settings
io.github.qigao.simpledsl.java
io.github.qigao.simpledsl.android
```

`io.github.qigao.simpledsl.settings` is implemented by `simpledsl-core`.

`io.github.qigao.simpledsl.java` is implemented by `simpledsl-java`.

`io.github.qigao.simpledsl.android` is implemented by `simpledsl-android`.

The 0.2.x project plugin ID:

```text
io.github.qigao.simpledsl.build
```

is removed in 0.3.0. Because SimpleDSL is still pre-1.0, 0.3.0 takes the clean break rather than retaining a compatibility marker that would obscure backend choice. When the settings plugin observes the old ID, it must fail with an explicit migration diagnostic telling the consumer to use `io.github.qigao.simpledsl.java`.

## Distribution and plugin resolution

The settings plugin owns SimpleDSL release alignment. When either backend plugin is requested without a version, it resolves to the same SimpleDSL version as the settings plugin. An explicit conflicting backend version fails with the existing SimpleDSL version-conflict style diagnostic.

Distribution metadata moves from a single build-logic artifact name to explicit backend coordinates:

```text
core artifact
java artifact
android artifact
settings plugin id
java plugin id
android plugin id
```

Because plugin resolution happens in Settings before a project backend is applied, `simpledsl-core` also owns compatibility metadata for external plugins that SimpleDSL manages: plugin IDs, module coordinates, and the versions compatible with the current SimpleDSL release. This is metadata only and must not create implementation dependencies in the core POM.

The actual build-system implementation dependencies remain backend-owned:

- `simpledsl-java` carries the Spring Boot, GraalVM, jOOQ, and jsonschema2pojo implementation dependencies it needs;
- `simpledsl-android` carries the AGP implementation dependency;
- `simpledsl-core` carries neither set.

For the initial Android baseline, SimpleDSL pins Android Gradle Plugin `9.0.1` and maps both:

```text
com.android.application
com.android.library
```

to the AGP module through settings-side compatibility metadata. A consumer that explicitly requests a conflicting AGP version fails rather than silently creating an unsupported SimpleDSL/AGP combination.

## Core project model

The current project model is Java-coupled because `CapabilitySpec` depends on the `ModuleKind` enum. The shared core must become backend-neutral.

The core model becomes conceptually:

```text
backendId: String
moduleType: String
capabilities: Set<String>
platformBindings: Set<String>
```

Initial backend and module IDs are:

```text
backend: java
  java-library
  spring-library
  spring-service

backend: android
  android-application
  android-library
```

`CapabilitySpec.allowedModules` becomes a set of module-type IDs rather than a set of Java-specific enum values.

The core does not register built-in product capabilities. The Java backend registers Java/Spring capabilities. Android capabilities are registered only by the Android backend.

An internal `SimpleDslProjectCorePlugin` initializes this common project model, snapshot/catalog bridge, capability infrastructure, backend guard, and common diagnostics. It has no public Plugin Portal marker and is applied by class from the Java or Android backend implementation.

The core does not create the public `simpledsl` extension; the selected backend creates its backend-specific extension after claiming the project.

## Backend guard

Exactly one SimpleDSL project backend may own a Gradle project.

A shared `SimpleDslBackendGuard` in core records the first backend claim. Applying both Java and Android backends to the same project fails immediately with a diagnostic containing:

```text
project path
already-selected backend
requested backend
```

Mixed repositories are supported because the guard is per project, not per Gradle build.

## Public project DSL isolation

Each backend exposes its own `simpledsl` extension type.

The core contains only reusable helpers for dependency lookup, explicit dependency binding, capability activation, diagnostics, and model access. It does not expose product module methods.

Java consumers see Java/Spring methods only:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.java'
}

simpledsl {
    springService()
    web()
}
```

Android consumers see Android methods only:

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

The Android extension does not expose `springService()`, `jooqSchema()`, or other Java-backend methods. The Java extension does not expose Android module or variant methods.

## Manifest policy model

The repository continues to use the same root `dependencies.toml`, `dependencies.yml`, or `dependencies.yaml` contract introduced in 0.2.0.

Java policy remains backward-compatible with 0.2.0:

```toml
[simpledsl]
java = 25
```

Android policy is added as a nested SimpleDSL table:

```toml
[simpledsl.android]
java = 21
compile-sdk = 36
min-sdk = 24
target-sdk = 36
```

A mixed monorepo may contain both:

```toml
[simpledsl]
java = 25

[simpledsl.android]
java = 21
compile-sdk = 36
min-sdk = 24
target-sdk = 36
```

This deliberately allows a server to use Java 25 while Android modules use Java 21.

The YAML representation is semantically identical:

```yaml
simpledsl:
  java: 25
  android:
    java: 21
    compile-sdk: 36
    min-sdk: 24
    target-sdk: 36
```

### Policy validation

`simpledsl.java` becomes optional at settings-load time. It is required only when the Java backend is applied to a project.

`simpledsl.android` is optional at settings-load time. It is required only when the Android backend is applied to a project.

A manifest containing only dependency/plugin policy and no backend policy is valid at settings time. Backend application is the point at which required policy is enforced.

Android policy accepts exactly:

```text
java          required
compile-sdk   required
min-sdk       required
target-sdk    optional at settings-load time
```

Present values are positive integers. The common Android policy must satisfy:

```text
min-sdk <= compile-sdk
```

When `target-sdk` is present it must also satisfy:

```text
min-sdk <= target-sdk <= compile-sdk
```

`androidApplication` requires `target-sdk`; `androidLibrary` does not. This allows a library-only Android repository to omit application policy it cannot use.

The foundational implementation uses `compile-sdk = 36` as its verified integration baseline. Backend compatibility checks, rather than the generic TOML/YAML syntax layer, own AGP-specific support constraints.

Per-module SDK overrides are deferred. Repository policy remains authoritative for the first Android release.

## Snapshot protocol v2

The current internal snapshot schema requires `javaVersion`, which makes Java policy globally mandatory. Android support changes that assumption, so the bootstrap/project bridge intentionally moves to schema version 2.

Conceptual shape:

```groovy
[
    schemaVersion: 2,
    policies: [
        java: [
            toolchain: 25
        ],
        android: [
            java: 21,
            compileSdk: 36,
            minSdk: 24,
            targetSdk: 36
        ]
    ],
    platforms: [...],
    libraries: [...],
    plugins: [...]
]
```

Absent backend policies and absent optional Android policy values are omitted from `policies`.

`platforms`, `libraries`, and `plugins` keep their 0.2.0 semantics. The public manifest still has no `[platforms]` namespace; internal platforms continue to be derived from library ownership.

The core snapshot bridge requires schema 2. No attempt is made to make a 0.3.0 backend consume a 0.2.x bootstrap snapshot or vice versa; the settings plugin aligns all SimpleDSL plugin versions before project configuration.

## Java backend migration

Java behavior moves out of the current build-logic artifact without redesigning the Java DSL.

The Java backend owns:

- Java base configuration;
- Java library, Spring library, and Spring service module types;
- Spring base behavior;
- Java/Spring capability registrations and feature plugins;
- GraalVM native support;
- jOOQ and JSON Schema helpers;
- Java-specific doctor rules.

The consumer migration is intentionally small:

```groovy
// 0.2.x
plugins {
    id 'io.github.qigao.simpledsl.build'
}

// 0.3.0
plugins {
    id 'io.github.qigao.simpledsl.java'
}
```

Existing `simpledsl.java`, dependency aliases, platform aliases, and Java/Spring DSL calls remain valid.

## Android backend baseline

The first Android backend pins AGP `9.0.1`, aligned with the repository's Gradle 9.1 baseline.

The Android backend uses only AGP public APIs:

- `ApplicationExtension` for application DSL configuration;
- `LibraryExtension` for library DSL configuration;
- the corresponding Android Components extensions for variant access;
- `beforeVariants` / `onVariants` for variant lifecycle integration.

It must not use removed/deprecated legacy contracts such as `BaseExtension`, `AppExtension`, `applicationVariants`, or task-name guessing.

AGP 9 built-in Kotlin is the baseline. The backend does not apply `org.jetbrains.kotlin.android`.

### Android application

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

`namespace` is required. `applicationId` is optional and defaults to `namespace`.

The backend applies `com.android.application`, requires Android policy including `target-sdk`, configures repository-level `compileSdk`, `minSdk`, `targetSdk`, and Java compatibility, and records module type `android-application`.

### Android library

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

`namespace` is required. The backend applies `com.android.library`, requires Android policy for `java`, `compile-sdk`, and `min-sdk`, configures those values, and records module type `android-library`.

`target-sdk` is application policy and is not forced onto Android library modules.

### Variant proof surface

The foundational backend registers a diagnostic task:

```text
simpledslAndroidVariants
```

The task reports the variants observed through the public Android Components API. This is not intended as the final Android feature surface; it is an integration proof that SimpleDSL is connected to the supported variant lifecycle without relying on task names or legacy AGP APIs.

The task must be configuration-cache compatible.

## Compose phase

Compose is the first planned Android capability, but it is intentionally a follow-up after the backend foundation passes published-consumer tests.

The follow-up capability will own:

- Compose build-feature activation;
- a Compose BOM library alias used through the existing `platform` semantics;
- Compose UI/runtime dependency bindings;
- the Compose Compiler Gradle plugin required by the selected Kotlin/Compose baseline;
- Android-module compatibility validation.

The Compose capability must pin a mutually compatible AGP, Kotlin, compileSdk, Compose Compiler, and Compose BOM baseline. The foundational backend split must not be blocked on the newest Compose release if that release requires a newer compileSdk/AGP combination than the chosen AGP 9.0.1 baseline.

## Integration tests

The current published-consumer test is split into backend-specific suites.

### Java published consumer

`integration-tests-java` migrates the existing fixture to `io.github.qigao.simpledsl.java` and proves:

- the existing Spring service build still works;
- root TOML plus included YAML/TOML dependency policy still works;
- Java capability/schema behavior remains available;
- configuration-cache reuse still works;
- the Java backend artifact does not resolve AGP as an implementation dependency.

### Android published consumer

`integration-tests-android` contains at least one application and one library module and proves:

- an Android-only manifest works without `simpledsl.java`;
- a library-only Android policy works without `target-sdk`;
- `androidApplication` and `androidLibrary` configure AGP successfully;
- `:app:assembleDebug` and a library assemble task succeed;
- `simpledslAndroidVariants` observes expected variants through Android Components;
- configuration-cache reuse succeeds;
- applying both Java and Android backends to one project fails;
- a conflicting AGP request fails with a SimpleDSL compatibility diagnostic;
- the Java backend artifact does not resolve AGP as an implementation dependency;
- the Android backend artifact does not resolve Spring Boot, GraalVM, jOOQ, or jsonschema2pojo implementation dependencies.

The CI environment installs/uses Android SDK 36 for the pinned baseline.

## Publication contract

The isolated test repository must contain exactly the three SimpleDSL marker IDs for 0.3.0:

```text
io.github.qigao.simpledsl.settings
io.github.qigao.simpledsl.java
io.github.qigao.simpledsl.android
```

The published-consumer contract must prove that Java and Android backend artifacts can resolve their transitive `simpledsl-core` dependency from the same published test repository.

The release workflow verifies and publishes all three plugin implementation projects before the release is considered valid.

## Diagnostics and failure modes

SimpleDSL fails early and specifically for these cases:

- Java backend applied without `simpledsl.java` policy;
- Android backend applied without `simpledsl.android` policy;
- Android application selected without `target-sdk`;
- Java and Android backends applied to the same project;
- more than one module type selected within one backend project;
- Android application/library missing `namespace`;
- invalid Android SDK ordering;
- conflicting SimpleDSL backend versions;
- conflicting AGP version;
- removed `io.github.qigao.simpledsl.build` plugin ID, with migration guidance;
- unsupported snapshot schema.

Diagnostics must include the project path or manifest location where that context is meaningful.

## CI and release gates

The 0.3.0 build is not considered complete until CI verifies, from a clean workspace:

```text
simpledsl-core tests
simpledsl-java tests
simpledsl-android tests
published test-repository creation
Java published-consumer contract
Android published-consumer contract
configuration-cache reuse for both backends
Plugin Portal marker-set verification
artifact dependency-isolation verification
wrapper metadata
```

The release workflow must use the tag-derived release version for all three implementation artifacts and marker publications.

## Implementation phases

### Phase A: core extraction and Java migration

- create `simpledsl-core` from the settings bootstrap;
- move backend-neutral project infrastructure into core;
- introduce snapshot schema v2 and backend-neutral policy access;
- create `simpledsl-java` from the Java/Spring portions of current build logic;
- publish `io.github.qigao.simpledsl.java`;
- migrate the Java published-consumer fixture;
- remove the old `.build` marker and add a migration diagnostic.

Phase A must leave Java behavior green before Android code is introduced.

### Phase B: Android foundation

- create `simpledsl-android`;
- pin AGP 9.0.1;
- implement Android policy parsing and validation;
- implement `androidApplication` and `androidLibrary`;
- integrate with public Android Components APIs;
- add Android published-consumer and configuration-cache coverage;
- enforce artifact dependency isolation.

### Phase C: first Android capability

After the foundation is merged and stable, define and implement `compose()` as a separate capability change with its own compatibility matrix and tests.

Room, Hilt, KSP, and other capabilities remain later work.

## Success criteria

The design is successful when a single repository can contain, for example:

```text
server/   -> io.github.qigao.simpledsl.java    -> Java 25 / Spring
app/      -> io.github.qigao.simpledsl.android -> Android Java 21 / AGP 9.0.1
```

while sharing one root dependency policy, with neither backend artifact carrying the other's build-system dependencies or DSL surface.