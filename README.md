# SimpleDSL Gradle Platform

SimpleDSL is a Gradle build platform for repository dependency policy and module-oriented builds. The 0.3.0 development line separates backend-neutral infrastructure from independent Java/Spring and Android project backends.

> **Development status:** Phase A (shared core + Java backend), Phase B (Android backend foundation), Phase C (Compose), Phase D (KSP foundation), Phase E (Room), Phase F (Hilt), and the Android Dynamic Feature module foundation are implemented on this branch. `0.3.0` is still under development and has not been released. Other Android capabilities remain later work.

## Requirements

- Gradle 9.1 is the repository baseline.
- SimpleDSL plugin artifacts run on Java 21 or newer.
- The Android backend baseline is AGP 9.0.1 with compileSdk 36.
- Android uses AGP 9 built-in Kotlin support; SimpleDSL does not apply `org.jetbrains.kotlin.android`.
- The Compose Compiler Gradle plugin is managed at 2.2.10 to match the AGP 9.0.1 built-in Kotlin line.
- KSP is managed at 2.3.9 and uses KSP2. KSP 2.3.1 introduced AGP 9 built-in Kotlin support; the older `2.2.10-2.0.2` compatibility floor is not used by SimpleDSL.
- The Room capability baseline uses stable Room3 3.0.1 (`androidx.room3`). Room3 requires KSP and Kotlin code generation.
- The Hilt capability baseline uses Dagger/Hilt 2.60.1. SimpleDSL settings owns `com.google.dagger.hilt.android`, and Hilt processing uses KSP rather than KAPT.
- The Compose library baseline uses Compose BOM 2026.06.00 / Compose 1.11.x. Compose 1.12 moves to the API 37 line and requires a newer AGP baseline than 9.0.1.

## Public plugins

The 0.3.0 development distribution publishes exactly three public plugin IDs:

```text
io.github.qigao.simpledsl.settings
io.github.qigao.simpledsl.java
io.github.qigao.simpledsl.android
```

`io.github.qigao.simpledsl.settings` runs in the Settings lifecycle. It loads the repository TOML/YAML policy, owns managed plugin/dependency versions, exports snapshot schema v2, and performs module discovery.

`io.github.qigao.simpledsl.java` is the Java/Spring project backend.

`io.github.qigao.simpledsl.android` is the Android project backend. It owns Android application, library, and Dynamic Feature module configuration and AGP public API integration.

The 0.2.x project marker `io.github.qigao.simpledsl.build` is removed in 0.3.0. Java consumers migrate to `io.github.qigao.simpledsl.java`.

## 0.3.0 development usage

The repository development version is `0.3.0-SNAPSHOT`. Snapshot coordinates are used by isolated published-consumer tests; they are not a claim that 0.3.0 has been released to the Plugin Portal.

A repository applies the settings plugin once:

```groovy
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id 'io.github.qigao.simpledsl.settings' version '0.3.0-SNAPSHOT'
}

rootProject.name = 'example'

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

The settings plugin resolves the Java and Android backends to the same SimpleDSL release version. An explicitly requested conflicting backend version fails with a `SimpleDSL version conflict` diagnostic. Settings also owns external build-tool versions such as AGP, the Compose Compiler plugin, KSP, and the Hilt Gradle plugin so module-local incompatible overrides fail early. `com.android.application`, `com.android.library`, and `com.android.dynamic-feature` all resolve to the same managed AGP 9.0.1 baseline.

### Java/Spring backend

Repository policy:

```toml
[simpledsl]
java = 25
```

Project build:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.java'
}

simpledsl {
    springService()
    web()
}
```

The Java backend owns `java-library`, `spring-library`, `spring-service`, Java capabilities, and schema helpers such as jOOQ/JSON Schema generation.

### Android backend

Android repository policy is independent from the server-side Java policy:

```toml
[simpledsl.android]
java = 21
compile-sdk = 36
min-sdk = 24
target-sdk = 36
```

`target-sdk` is optional for module types that do not own an application target, but `androidApplication()` requires it.

Application module:

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

`applicationId` defaults to the namespace and can be overridden explicitly:

```groovy
simpledsl {
    androidApplication {
        namespace = 'com.example.app'
        applicationId = 'com.example.product'
    }
}
```

Library module:

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

The backend applies the managed AGP plugin appropriate to the declared module type and configures only AGP 9 public DSL/API surfaces. It does not use legacy `BaseExtension`, `AppExtension`, `applicationVariants`, or task-name guessing.

The Android Java policy controls `compileOptions.sourceCompatibility` and `targetCompatibility`. AGP 9 built-in Kotlin inherits the compatible JVM target; SimpleDSL does not apply a second Kotlin Android plugin.

### Android Dynamic Feature module

Dynamic Feature topology is explicit and bilateral. The base application declares the feature project path:

```groovy
// :app
plugins {
    id 'io.github.qigao.simpledsl.android'
}

simpledsl {
    androidApplication {
        namespace = 'com.example.app'
        dynamicFeature(':payments')
    }
}
```

The feature declares its base application independently:

```groovy
// :payments
plugins {
    id 'io.github.qigao.simpledsl.android'
}

simpledsl {
    androidDynamicFeature {
        namespace = 'com.example.payments'
        baseModule = ':app'
    }
}
```

The application side owns AGP's `dynamicFeatures` set. The Dynamic Feature side owns its normal Gradle `implementation` project dependency on `baseModule`. SimpleDSL does not mutate the other project, evaluate it eagerly, or encode project topology into repository policy/snapshot schema. Paths must be absolute Gradle project paths, and self-references fail with SimpleDSL diagnostics.

`androidDynamicFeature` is a third Android **module type**, not a capability. It reuses the repository Android policy baseline: AGP 9.0.1, compileSdk 36, minSdk 24, and Java 21. The backend integrates through public `DynamicFeatureExtension` and `DynamicFeatureAndroidComponentsExtension`; AGP 9 built-in Kotlin remains active and `org.jetbrains.kotlin.android` is not applied.

Compose, KSP, Room, and Hilt remain intentionally restricted to `android-application` and `android-library`. Enabling one on `android-dynamic-feature` fails through the existing capability/module-type diagnostic. Feature-specific capability support, delivery-mode DSL, and Hilt feature injection are later work rather than implicit behavior in this foundation.

The real published-consumer proof builds a base application plus `:payments`, compiles Kotlin in the feature against a symbol from the base app, runs `:app:bundleDebug`, and opens the generated `.aab` to require `payments/manifest/AndroidManifest.xml`. App Bundle `versionCode` remains base-application publication metadata; the feature inherits it rather than declaring its own version.

### Compose capability

Compose libraries remain repository policy. Declare the BOM and the aliases consumed by the Android capability in the root dependency manifest:

```toml
[versions]
compose-bom = "2026.06.00"

[libraries.compose]
module = "androidx.compose:compose-bom"
version.ref = "compose-bom"

[libraries.compose-runtime]
module = "androidx.compose.runtime:runtime"
platform = "compose"

[libraries.compose-ui]
module = "androidx.compose.ui:ui"
platform = "compose"
```

Enable Compose after declaring the Android module type:

```groovy
simpledsl {
    androidApplication {
        namespace = 'com.example.app'
    }
    jetpackCompose()
}
```

`jetpackCompose()` is the Android DSL sugar for the semantic capability ID `compose`; `capability('compose')` is equivalent. The public sugar is intentionally not named bare `compose()` because a Gradle Groovy configuration closure already inherits Groovy's existing `Closure.compose(Closure)` method, which collides with the intended zero-argument method.

The capability applies the managed `org.jetbrains.kotlin.plugin.compose` plugin, enables `buildFeatures.compose` through AGP public DSL, and binds `compose-runtime` / `compose-ui` through the existing dependency bridge. Because those aliases reference platform `compose`, the BOM is activated automatically and recorded as `implementation:compose`. Module build files do not apply `org.jetbrains.kotlin.android` or declare Compose versions directly.

### KSP capability foundation

KSP is an Android backend capability rather than a separate module system. Enable it after declaring an Android application or library:

```groovy
simpledsl {
    androidApplication {
        namespace = 'com.example.app'
    }
    ksp()
}
```

`ksp()` is sugar for semantic capability ID `ksp`; `capability('ksp')` is equivalent. The capability applies the settings-managed `com.google.devtools.ksp` plugin and exposes the standard KSP configurations while keeping AGP 9 built-in Kotlin enabled. Module builds do not declare a KSP plugin version or apply `org.jetbrains.kotlin.android`.

The foundation deliberately does not own processors, processor arguments, or generated-source conventions. Android capabilities compose the existing `CapabilitySpec` primitives instead of introducing a second code-generation framework. Phase E Room and Phase F Hilt both validate that contract directly: each requires `ksp` and binds its compiler alias to the standard `ksp` configuration without changing `CapabilitySpec`, `CapabilityEngine`, or the dependency bridge.

KSP2 is the supported mode. KSP1 compatibility and opt-outs from AGP built-in Kotlin are not part of the Android backend contract.

### Room capability

Room library versions remain repository policy. Phase E uses stable Room3 3.0.1 and the new `androidx.room3` coordinates:

```toml
[versions]
room3 = "3.0.1"

[libraries.room-runtime]
module = "androidx.room3:room3-runtime"
version.ref = "room3"

[libraries.room-compiler]
module = "androidx.room3:room3-compiler"
version.ref = "room3"
```

Enable Room after declaring the Android module type:

```groovy
simpledsl {
    androidApplication {
        namespace = 'com.example.app'
    }
    room()
}
```

`room()` is sugar for semantic capability ID `room`; `capability('room')` is equivalent. Room is defined entirely through the existing capability model:

```text
room
  requires ksp
  implementation -> room-runtime
  ksp            -> room-compiler
```

Enabling Room therefore transitively enables the managed KSP2 plugin and standard `ksp` configuration. The module does not need to call `ksp()` separately, apply `org.jetbrains.kotlin.android`, or declare the Room compiler directly.

Phase E intentionally does not apply the Room Gradle plugin or introduce schema-directory, migration, processor-argument, or generated-source abstractions. Those concerns have a different lifecycle from dependency/code-generation activation and can be added later if a concrete contract requires them. Room 2.x, KAPT, and Java annotation-processing compatibility are also outside this capability.

The published Android consumer verifies real Room3 code generation: an application containing `@Entity`, `@Dao`, and `@Database` Kotlin sources assembles successfully and produces `AppDatabase_Impl.kt` through KSP, while a library exercises the equivalent generic `capability('room')` path.

### Hilt capability

Hilt uses a settings-managed Gradle plugin plus repository-policy runtime/compiler aliases. The validated Phase F baseline is Dagger/Hilt 2.60.1:

```toml
[versions]
hilt = "2.60.1"

[libraries.hilt-android]
module = "com.google.dagger:hilt-android"
version.ref = "hilt"

[libraries.hilt-compiler]
module = "com.google.dagger:hilt-compiler"
version.ref = "hilt"
```

Enable Hilt after declaring an Android module:

```groovy
simpledsl {
    androidApplication {
        namespace = 'com.example.app'
    }
    hilt()
}
```

`hilt()` is sugar for semantic capability ID `hilt`; `capability('hilt')` is equivalent. The capability is composed from existing primitives:

```text
hilt
  requires ksp
  plugin         -> com.google.dagger.hilt.android
  implementation -> hilt-android
  ksp            -> hilt-compiler
```

SimpleDSL settings owns `com.google.dagger.hilt.android` at 2.60.1, while `simpledsl-android` carries the Hilt Gradle plugin tooling needed to apply it. The runtime and compiler remain consumer dependencies resolved from repository policy. Enabling Hilt transitively enables KSP2; consumers do not need `ksp()`, `kapt`, or `org.jetbrains.kotlin.android`.

Phase F does not add a generic code-generation abstraction, processor DSL, activation callback, or Hilt-specific core hook. `CapabilitySpec`, `CapabilityEngine`, and `DependencyBridge` remain unchanged. Hilt testing, Navigation Compose integration, custom components, advanced processor flags, and KAPT/Java annotation-processing compatibility remain outside the capability.

The real published consumer combines Compose, Room, and Hilt in one application. It compiles an `@HiltAndroidApp` application, verifies Hilt's generated `Hilt_ExampleApplication.java` under `build/generated/hilt/component_sources`, preserves the Room `AppDatabase_Impl.kt` KSP proof, and assembles both application and library debug artifacts. The library exercises the generic `capability('hilt')` path.

### Android Components proof surface

Application, library, and Dynamic Feature modules expose:

```text
simpledslAndroidVariants
```

For example:

```bash
./gradlew :app:simpledslAndroidVariants
./gradlew :payments:simpledslAndroidVariants
```

The task is populated through `beforeVariants`/`onVariants` on each module type's public Android Components API and stores only serializable variant-name inputs, so it is compatible with Gradle configuration cache.

## Dependency manifest

SimpleDSL discovers exactly one dependency manifest at repository root:

```text
dependencies.toml
dependencies.yml
dependencies.yaml
```

If none exists, configuration fails and lists the accepted names. If more than one exists, configuration fails as ambiguous.

A repository may keep the root manifest small and include policy fragments:

```toml
include = ["dependencies/spring.toml", "dependencies/test.yml"]

[simpledsl]
java = 25

[simpledsl.android]
java = 21
compile-sdk = 36
min-sdk = 24
target-sdk = 36

[versions]
spring-boot = "4.1.0"

[libraries.spring]
module = "org.springframework.boot:spring-boot-dependencies"
version.ref = "spring-boot"

[libraries.spring-web]
module = "org.springframework.boot:spring-boot-starter-web"
platform = "spring"
```

TOML and YAML share one semantic model. Includes are resolved relative to the declaring file and may mix `.toml`, `.yml`, and `.yaml`. Duplicate aliases, include cycles, malformed coordinates, unknown platform aliases, unsupported keys, and ambiguous root manifests fail fast.

A BOM/platform coordinate is an ordinary library declaration referenced through `platform`; there is no public `[platforms]` section.

A non-default manifest can be selected explicitly:

```groovy
simpledslSettings {
    dependencyManifest.set(layout.settingsDirectory.file('config/dependencies.yml'))
}
```

## Snapshot schema v2

The shared settings/core layer exports backend policies under snapshot schema version 2.

Java policy remains compatible with 0.2.x syntax:

```toml
[simpledsl]
java = 25
```

It is optional during Settings evaluation and becomes required only when the Java backend is used.

Android policy is exported independently from `[simpledsl.android]`. This allows, for example, a monorepo to use Java 25 for server modules and Java 21 for Android modules without coupling their backend policies.

Dynamic Feature topology is deliberately not part of snapshot schema v2. `dynamicFeature(':path')` and `baseModule = ':path'` are module-local Gradle topology declarations.

## Module discovery

SimpleDSL discovers Gradle modules from repository root by default. Supported build descriptors include:

```text
build.spring.gradle
build.spring.gradle.kts
build.gradle
build.gradle.kts
```

Transient output directories such as `build/` and `.gradle/` are excluded. Optional discovery overrides use `gradle/simpledsl/modules.toml`.

## Java capabilities and schema helpers

The Java backend owns capabilities such as:

```text
aop
transaction
web
http-client
messaging
jdbc
jooq
jpa
redis
native
lombok
```

They are backend DSL/configuration names, not public Plugin Portal IDs. The shared core owns only generic module/capability primitives, dependency bridging, backend claiming, and common diagnostics.

For jOOQ DDL generation:

```groovy
simpledsl {
    javaLibrary()
    jooqSchema()
}

simpledslJooq {
    source = 'database/schema/**/*.sql'
    packageName = 'com.example.model'
}
```

For JSON Schema generation:

```groovy
simpledsl {
    javaLibrary()
    jsonSchema()
}

simpledslJsonSchema {
    source = 'json'
    packageName = 'com.example.model'
    validation = true
    builders = true
}
```

## Diagnostics

The settings entry registers:

```text
simpledslProjects
simpledslDependencies
```

Backend-neutral project diagnostics include:

```text
simpledslCapabilities
simpledslDoctor
```

Android additionally exposes `simpledslAndroidVariants` as the public Android Components proof task. Compose + Room + Hilt activation appears in `simpledslCapabilities` as `Features: compose,hilt,ksp,room`: `ksp` is present because Room and Hilt require it transitively, while Compose continues to report `Platform bindings: implementation:compose`. Dynamic Feature modules keep those capability allow-lists unchanged and report their own module type as `android-dynamic-feature`.

## Artifact and publication model

The 0.3.0 development distribution has three implementation artifacts:

```text
io.github.qigao.simpledsl:simpledsl-core
io.github.qigao.simpledsl:simpledsl-java
io.github.qigao.simpledsl:simpledsl-android
```

They back exactly the three public marker IDs listed above.

Dependency boundaries are intentional:

```text
simpledsl-core
  ├── simpledsl-java
  └── simpledsl-android
```

- `simpledsl-core` must not carry Java/Spring tooling, AGP, Compose tooling, KSP tooling, or Hilt Gradle tooling.
- `simpledsl-java` depends on core, must not depend on Android, and must not carry AGP, Compose tooling, KSP tooling, or Hilt Gradle tooling.
- `simpledsl-android` depends on core, AGP 9.0.1, the Compose Compiler Gradle plugin 2.2.10, KSP 2.3.9, and the Hilt Gradle plugin 2.60.1; it must not depend on Java or carry Spring/GraalVM/jOOQ/jsonschema2pojo tooling.

Room and Hilt runtime/compiler libraries are consumer dependencies selected through repository policy; they are not embedded as runtime/compiler dependencies in the Android plugin artifact. The Hilt **Gradle plugin** is Android backend tooling and is therefore intentionally present in `simpledsl-android`.

`verifyBackendIsolation` enforces these rules against the actual POMs published to the isolated test Maven repository.

## Development verification

The 0.3.0 Android verification contract is:

```bash
./gradlew clean \
  verifyProductNamespace \
  :simpledsl-core:check \
  :simpledsl-java:check \
  :simpledsl-android:check \
  publishToTestPluginRepository \
  verifyBackendIsolation \
  :integration-tests-java:test \
  :integration-tests-android:test \
  --stacktrace
```

The Android published-consumer suite does not use `includeBuild` or `withPluginClasspath()`. It resolves the settings and Android plugins from the isolated Maven repository, resolves AGP, the Compose Compiler plugin, KSP, and Hilt through the managed settings/publication contract, and verifies variants/capabilities/configuration-cache reuse. The base application combines Compose, `room()`, and `hilt()` while owning `dynamicFeature(':payments')`; `:payments` is a real `androidDynamicFeature` consumer with a base-app Kotlin compile edge. The suite preserves Room KSP and Hilt generated-source proofs, builds the library consumer, creates the base application's debug App Bundle, and inspects the `.aab` for the `payments` module manifest. Neither application nor library applies KSP directly, so the suite continues to prove transitive KSP activation for both processor-backed capabilities.

`validatePlugins` runs as part of each plugin project's `check`. The release workflow performs the same isolated distribution verification before any Plugin Portal upload; it does not rely on fake credentials or a local `publishPlugins --validate-only` shortcut.

## 0.2.x migration

Java projects change the project-side SimpleDSL entry plugin:

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

The repository-root TOML/YAML dependency model and existing Java/Spring DSL remain compatible.

## License

See [LICENSE](LICENSE).
