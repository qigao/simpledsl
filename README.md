# SimpleDSL Gradle Platform

SimpleDSL is a Gradle build platform for dependency policy and module-oriented Java/Spring builds. Its Plugin Portal surface is intentionally small: SimpleDSL publishes only two entry plugins. Module types, features, schema helpers, and third-party Gradle plugins are implementation/configuration details and are not republished as SimpleDSL plugin IDs.

## Requirements

- Gradle 9.1 or newer is the tested baseline for the first release.
- SimpleDSL plugin artifacts run on Java 21 or newer.
- Consumer Java toolchains are configured by the SimpleDSL dependency manifest and may target a newer Java release.

## Public plugins

SimpleDSL publishes exactly these two Gradle plugin IDs:

```text
io.github.qigao.simpledsl.settings
io.github.qigao.simpledsl.build
```

`io.github.qigao.simpledsl.settings` runs in the Settings lifecycle. It loads SimpleDSL TOML or YAML dependency policy, manages dependency/plugin coordinates and versions, and performs module discovery.

`io.github.qigao.simpledsl.build` is the project-side entry point for SimpleDSL build behavior. Module types and capabilities are composed internally; they do not have Plugin Portal marker IDs.

## Installation

Declare the SimpleDSL release in `settings.gradle`:

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id 'io.github.qigao.simpledsl.settings' version '0.1.1'
}

rootProject.name = 'example'

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

A project that uses SimpleDSL build behavior applies only the project entry plugin:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.build'
}

simpledsl {
    springService()
    web()
}
```

The settings plugin resolves `io.github.qigao.simpledsl.build` to the same SimpleDSL release. An explicitly requested conflicting build-plugin version fails with a `SimpleDSL version conflict` diagnostic.

Names such as `spring-service`, `java-library`, `web`, `jdbc`, `jpa`, `jooq`, and schema options are SimpleDSL module/capability configuration. They are not public Gradle plugin IDs.

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

[libraries.spring-bom]
module = "org.springframework.boot:spring-boot-dependencies"
version.ref = "spring-boot"

[libraries.spring-web]
module = "org.springframework.boot:spring-boot-starter-web"
platform = "spring-bom"

[plugins.spring-boot]
id = "org.springframework.boot"
module = "org.springframework.boot:spring-boot-gradle-plugin"
version.ref = "spring-boot"
```

A BOM/platform coordinate is an ordinary library declaration. Libraries that are versioned by that BOM reference its alias with `platform`; there is no separate public `[platforms]` section.

YAML is an equivalent serialization of the same semantic model:

```yaml
simpledsl:
  java: 25
versions:
  spring-boot: "4.1.0"
libraries:
  spring-bom:
    module: org.springframework.boot:spring-boot-dependencies
    version:
      ref: spring-boot
  spring-web:
    module: org.springframework.boot:spring-boot-starter-web
    platform: spring-bom
plugins:
  spring-boot:
    id: org.springframework.boot
    version:
      ref: spring-boot
```

Includes are resolved relative to the file that declares them and may mix `.toml`, `.yml`, and `.yaml`. Duplicate aliases and include cycles fail regardless of serialization format.

The Gradle-shaped vocabulary is intentionally a compatibility convention rather than a claim that a SimpleDSL manifest is itself a Gradle Version Catalog. `simpledsl`, `include`, and `platform` ownership are SimpleDSL policy semantics layered on top of the familiar dependency notation.

A non-default location can be selected explicitly in `settings.gradle`:

```groovy
simpledslSettings {
    dependencyManifest.set(layout.settingsDirectory.file('config/dependencies.yml'))
}
```

The manifest owns consumer dependency policy: Java toolchain version, BOMs, libraries, and external Gradle-plugin coordinates where needed.

SimpleDSL does not republish third-party software under `io.github.qigao.simpledsl.*`. Spring Boot, GraalVM Native Build Tools, jOOQ, jsonschema2pojo, and application libraries remain their original third-party dependencies/plugins and are referenced by their original coordinates.

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

## Internal module and capability model

SimpleDSL's project entry point owns an internal module model and capability engine. Examples include:

```text
module types:
  java-library
  spring-library
  spring-service

capabilities:
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

These names select internal behavior. There are no public markers such as `io.github.qigao.simpledsl.feature.web`, `io.github.qigao.simpledsl.spring-service`, or `io.github.qigao.simpledsl.schema.jooq`.

Capabilities validate module compatibility, dependency requirements, conflicts, and platform bindings. External Gradle plugins required by a capability are loaded from their original third-party plugin artifacts.

## Schema code generation

Schema generators are activated through the existing `simpledsl` project DSL. They do not have public Plugin Portal IDs.

For jOOQ DDL generation:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.build'
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
    id 'io.github.qigao.simpledsl.build'
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

`jooqSchema()` and `jsonSchema()` activate internal SimpleDSL schema helpers. Consumers do not apply or reference `SimpleDslJooqSchemaPlugin`, `SimpleDslJsonSchemaPlugin`, or any `io.github.qigao.simpledsl.schema.*` marker.

## Diagnostics

The settings entry registers:

```text
simpledslProjects
simpledslDependencies
```

The project entry registers:

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

## Publication model

SimpleDSL has two implementation artifacts:

```text
io.github.qigao.simpledsl:simpledsl-build-bootstrap
io.github.qigao.simpledsl:simpledsl-build-logic
```

They back exactly two Plugin Portal markers:

```text
io.github.qigao.simpledsl.settings
io.github.qigao.simpledsl.build
```

The repository's published-consumer contract scans the isolated test Maven repository and requires this marker set to match exactly. Internal module/capability/schema implementation classes do not generate public marker publications.

## Development

Run the core verification suite:

```bash
./gradlew clean \
  verifyProductNamespace \
  :simpledsl-build-bootstrap:check \
  :simpledsl-build-logic:check \
  publishToTestPluginRepository \
  :integration-tests:test
```

Validate Plugin Portal metadata without uploading:

```bash
./gradlew \
  :simpledsl-build-bootstrap:publishPlugins \
  :simpledsl-build-logic:publishPlugins \
  -PreleaseVersion=0.1.1 \
  --validate-only
```

Actual Plugin Portal publication is performed by the release workflow.

## License

See [LICENSE](LICENSE).
