# SimpleDSL Gradle Platform

SimpleDSL is a Gradle build platform for repository dependency policy and module-oriented builds. The 0.3.0 development line separates backend-neutral infrastructure from independent Java/Spring and Android project backends.

> **Development status:** Phase A (shared core + Java backend), Phase B (Android backend foundation), Phase C (Compose), and Phase D (KSP foundation) are implemented on this branch. `0.3.0` is still under development and has not been released. Room, Hilt, and other Android capabilities remain later work.

## Requirements

- Gradle 9.1 is the repository baseline.
- SimpleDSL plugin artifacts run on Java 21 or newer.
- The Android backend baseline is AGP 9.0.1 with compileSdk 36.
- Android uses AGP 9 built-in Kotlin support; SimpleDSL does not apply `org.jetbrains.kotlin.android`.
- The Compose Compiler Gradle plugin is managed at 2.2.10 to match the AGP 9.0.1 built-in Kotlin line.
- KSP is managed at 2.3.9 and uses KSP2. KSP 2.3.1 introduced AGP 9 built-in Kotlin support; the older `2.2.10-2.0.2` compatibility floor is not used by SimpleDSL.
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

`io.github.qigao.simpledsl.android` is the Android project backend. It owns Android application/library module configuration and AGP public API integration.

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

The settings plugin resolves the Java and Android backends to the same SimpleDSL release version. An explicitly requested conflicting backend version fails with a `SimpleDSL version conflict` diagnostic. Settings also owns external build-tool versions such as AGP, the Compose Compiler plugin, and KSP so module-local incompatible overrides fail early.

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

`target-sdk` is optional for an Android-library-only repository, but `androidApplication()` requires it.

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

The backend applies `com.android.application` or `com.android.library` and configures only AGP 9 public DSL/API surfaces (`ApplicationExtension`, `LibraryExtension`, and Android Components APIs). It does not use legacy `BaseExtension`, `AppExtension`, `applicationVariants`, or task-name guessing.

The Android Java policy controls `compileOptions.sourceCompatibility` and `targetCompatibility`. AGP 9 built-in Kotlin inherits the compatible JVM target; SimpleDSL does not apply a second Kotlin Android plugin.

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

`jetpackCompose()` is the Android DSL sugar for the semantic capability ID `compose`; `capability('compose')` is equivalent. The public sugar is intentionally not named bare `compose()` because a Gradle Groovy configuration closure already inherits Groovy's `Closure.compose(Closure)` method, which collides with a zero-argument DSL method.

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

The foundation deliberately does not own processors, processor arguments, or generated-source conventions. Later Android capabilities can compose existing primitives instead of introducing another code-generation framework. For example, a Room capability can require `ksp` and bind its compiler alias to the `ksp` configuration through the existing `CapabilitySpec` dependency mechanism.

KSP2 is the supported mode. KSP1 compatibility and opt-outs from AGP built-in Kotlin are not part of the Android backend contract.

### Android Components proof surface

Application and library modules expose:

```text
simpledslAndroidVariants
```

For example:

```bash
./gradlew :app:simpledslAndroidVariants
```

The task is populated through `beforeVariants`/`onVariants` on the public Android Components API and stores only serializable variant-name inputs, so it is compatible with Gradle configuration cache.

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

Android additionally exposes `simpledslAndroidVariants` as the public Android Components proof task. Compose + KSP activation appears in `simpledslCapabilities` as `Features: compose,ksp`, while Compose continues to report `Platform bindings: implementation:compose`.

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

- `simpledsl-core` must not carry Java/Spring tooling, AGP, Compose tooling, or KSP tooling.
- `simpledsl-java` depends on core, must not depend on Android, and must not carry AGP, Compose tooling, or KSP tooling.
- `simpledsl-android` depends on core, AGP 9.0.1, the Compose Compiler Gradle plugin 2.2.10, and KSP 2.3.9; it must not depend on Java or carry Spring/GraalVM/jOOQ/jsonschema2pojo tooling.

`verifyBackendIsolation` enforces these rules against the actual POMs published to the isolated test Maven repository.

## Development verification

The Phase D verification contract is:

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

The Android published-consumer suite does not use `includeBuild` or `withPluginClasspath()`. It resolves the settings and Android plugins from the isolated Maven repository, resolves AGP, the Compose Compiler plugin, and KSP through the managed settings contract, verifies variants/capabilities/configuration-cache reuse, and assembles real application/library debug artifacts containing `@Composable` Kotlin sources with KSP active.

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
