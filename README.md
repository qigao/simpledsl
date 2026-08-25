# SimpleDSL Gradle Platform

SimpleDSL is a Gradle build platform for repository dependency policy and module-oriented builds. The 0.3.0 development line separates backend-neutral build infrastructure from product-specific project backends so Java/Spring and Android can evolve independently without sharing implementation dependencies.

> **Development status:** this branch contains 0.3.0 Phase A: `simpledsl-core` plus the Java/Spring backend. The Android backend is Phase B work and is not part of the current implementation yet. The latest completed release notes remain under 0.2.0 in `CHANGELOG.md`.

## Requirements

- Gradle 9.1 is the repository baseline.
- SimpleDSL plugin artifacts run on Java 21 or newer.
- Consumer Java toolchains are configured by repository policy and may target a newer Java release.

## Public plugins

Phase A publishes exactly these two Gradle plugin IDs:

```text
io.github.qigao.simpledsl.settings
io.github.qigao.simpledsl.java
```

`io.github.qigao.simpledsl.settings` runs in the Settings lifecycle. It loads SimpleDSL TOML or YAML repository policy, manages dependency/plugin coordinates and versions, exports the backend-neutral dependency snapshot, and performs module discovery.

`io.github.qigao.simpledsl.java` is the Java/Spring project backend. Java module types, capabilities, schema helpers, and third-party Gradle plugins remain implementation/configuration details and do not get separate SimpleDSL marker IDs.

The 0.2.x project marker:

```text
io.github.qigao.simpledsl.build
```

is removed in 0.3.0. Applying it through the 0.3.0 settings plugin fails with migration guidance to use `io.github.qigao.simpledsl.java`.

`io.github.qigao.simpledsl.android` is reserved for the Phase B Android backend and is not published by Phase A.

## 0.3.0 development usage

The repository development version is `0.3.0-SNAPSHOT`. Snapshot coordinates are used by the repository's isolated published-consumer tests; they are not a claim that `0.3.0` has been released to the Plugin Portal.

A settings build uses the SimpleDSL settings entry point:

```groovy
pluginManagement {
    repositories {
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
        mavenCentral()
    }
}
```

A Java/Spring project applies only the Java backend:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.java'
}

simpledsl {
    springService()
    web()
}
```

The settings plugin resolves the Java backend to the same SimpleDSL release version. An explicitly requested conflicting backend version fails with a `SimpleDSL version conflict` diagnostic.

### Migrating from 0.2.x

The project-side migration is intentionally small:

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

The dependency manifest and existing Java/Spring DSL remain compatible.

## Dependency manifest

SimpleDSL discovers the dependency manifest at the repository root. Exactly one of these default names may exist:

```text
dependencies.toml
dependencies.yml
dependencies.yaml
```

If none exists, configuration fails and lists the accepted names. If more than one exists, configuration fails as ambiguous; SimpleDSL does not assign precedence between TOML and YAML.

A typical repository keeps the root manifest small and includes policy fragments:

```text
repo/
├── dependencies.toml
├── dependencies/
│   ├── spring.toml
│   └── test.yml
├── settings.gradle
└── ...
```

TOML uses Gradle Version Catalog vocabulary for versions, libraries, plugins, and `version.ref`, with small SimpleDSL extensions for build policy and includes:

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

[plugins.spring-boot]
id = "org.springframework.boot"
module = "org.springframework.boot:spring-boot-gradle-plugin"
version.ref = "spring-boot"
```

A BOM/platform coordinate is an ordinary library declaration. Libraries versioned by that BOM reference its alias with `platform`; there is no public `[platforms]` section. The built-in Spring module integration activates the platform alias `spring`, so a Spring Boot BOM used by `springService()` or `springLibrary()` should be declared as the `spring` library alias.

YAML is an equivalent serialization of the same semantic model:

```yaml
simpledsl:
  java: 25
versions:
  spring-boot: "4.1.0"
libraries:
  spring:
    module: org.springframework.boot:spring-boot-dependencies
    version:
      ref: spring-boot
  spring-web:
    module: org.springframework.boot:spring-boot-starter-web
    platform: spring
plugins:
  spring-boot:
    id: org.springframework.boot
    version:
      ref: spring-boot
```

Includes are resolved relative to the file that declares them and may mix `.toml`, `.yml`, and `.yaml`. Duplicate aliases and include cycles fail regardless of serialization format.

The Gradle-shaped vocabulary is a compatibility convention rather than a claim that a SimpleDSL manifest is itself a Gradle Version Catalog. `simpledsl`, `include`, and `platform` ownership are SimpleDSL policy semantics layered on the familiar dependency notation.

A non-default location can be selected explicitly in `settings.gradle`:

```groovy
simpledslSettings {
    dependencyManifest.set(layout.settingsDirectory.file('config/dependencies.yml'))
}
```

### Java policy in snapshot schema v2

The 0.3.0 internal dependency snapshot uses schema version 2. Java policy is exported under a backend policy map instead of a globally required top-level Java version.

`simpledsl.java` is therefore optional during Settings evaluation. It becomes required when `io.github.qigao.simpledsl.java` is applied to a project:

```toml
[simpledsl]
java = 25
```

This change is what allows a future Android-only repository to load the shared settings/core layer without inventing a server-side Java policy.

## Module discovery

SimpleDSL discovers Gradle modules from the repository root by default. A directory containing one of the supported build descriptors can be discovered automatically:

```text
build.spring.gradle
build.spring.gradle.kts
build.gradle
build.gradle.kts
```

Transient output directories such as `build/` and `.gradle/` are excluded from discovery.

Optional discovery overrides use:

```text
gradle/simpledsl/modules.toml
```

The modules file can select automatic, strict automatic, or manual discovery and can provide explicit roots, exclusions, and module declarations.

## Java module and capability model

The Java backend owns these module types:

```text
java-library
spring-library
spring-service
```

and capabilities such as:

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

They are backend DSL/configuration names, not public Plugin Portal IDs. The shared core owns the generic module model, dependency bridge, capability engine primitives, backend guard, and common diagnostics; Java/Spring registrations live only in `simpledsl-java`.

Capabilities validate module compatibility, dependency requirements, conflicts, and platform bindings. External Gradle plugins required by Java capabilities are loaded from their original third-party plugin artifacts.

## Schema code generation

Schema generators remain Java-backend capabilities and do not have public Plugin Portal IDs.

For jOOQ DDL generation:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.java'
}

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
plugins {
    id 'io.github.qigao.simpledsl.java'
}

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

The Java backend registers:

```text
simpledslCapabilities
simpledslDoctor
```

Typical usage:

```bash
./gradlew simpledslProjects
./gradlew :app:simpledslCapabilities
./gradlew :app:simpledslDoctor
```

`simpledslDoctor` fails the build when module type, capabilities, dependency aliases, or platform bindings are inconsistent.

## Artifact and publication model

Phase A has two implementation artifacts:

```text
io.github.qigao.simpledsl:simpledsl-core
io.github.qigao.simpledsl:simpledsl-java
```

They back exactly these current marker IDs:

```text
io.github.qigao.simpledsl.settings
io.github.qigao.simpledsl.java
```

`simpledsl-core` contains the settings plugin plus backend-neutral project infrastructure. Its publication must not carry Spring Boot, GraalVM Native Build Tools, jOOQ, jsonschema2pojo, or AGP implementation dependencies.

`simpledsl-java` depends on `simpledsl-core` and owns the existing Java/Spring implementation tooling. It must not depend on AGP.

The root `verifyBackendIsolation` task checks these invariants against the actual POMs published into the isolated test Maven repository, rather than relying on source-code inspection.

## Development

Run the Phase A verification suite:

```bash
./gradlew clean \
  verifyProductNamespace \
  :simpledsl-core:check \
  :simpledsl-java:check \
  publishToTestPluginRepository \
  verifyBackendIsolation \
  :integration-tests-java:test \
  --no-build-cache \
  --stacktrace
```

The Java published-consumer suite uses a real isolated Maven repository, root TOML with included YAML/TOML policy fragments, and verifies configuration-cache reuse.

Validate Plugin Portal metadata without uploading:

```bash
./gradlew \
  :simpledsl-core:publishPlugins \
  :simpledsl-java:publishPlugins \
  -PreleaseVersion=0.3.0 \
  --validate-only \
  --stacktrace
```

This validates publication metadata only. A final 0.3.0 release must not be tagged until the Android backend required by the approved 0.3.0 design is implemented and verified.

## License

See [LICENSE](LICENSE).
