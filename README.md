# SimpleDSL Gradle Platform

SimpleDSL is a published Gradle build platform for convention-driven Java and Spring Boot projects. It centralizes dependency policy, module discovery, module types, feature composition, diagnostics, and schema code generation behind a small set of binary Gradle plugins.

The public plugin namespace is `io.github.qigao.simpledsl.*`.

## Requirements

- Gradle 9.1 or newer is the tested baseline for the first release.
- SimpleDSL plugin artifacts run on Java 21 or newer.
- Consumer Java toolchains are configured by the SimpleDSL dependency manifest and may target a newer Java release.

## Installation

Declare the SimpleDSL version once in `settings.gradle`:

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id 'io.github.qigao.simpledsl.settings' version '0.1.0'
}

rootProject.name = 'example'

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

After the settings plugin is loaded, other public SimpleDSL plugins are versionless. The settings plugin resolves them to the same SimpleDSL release:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.spring-service'
    id 'io.github.qigao.simpledsl.feature.web'
}
```

A build must not mix SimpleDSL plugin versions. An explicitly requested conflicting version fails during plugin resolution with a `SimpleDSL version conflict` diagnostic.

## Dependency manifest

The default dependency manifest is:

```text
gradle/simpledsl/dependencies.toml
```

A minimal manifest can include smaller policy files:

```toml
include = ["spring.toml", "test.toml"]

[java]
version = 25
```

The manifest owns consumer dependency policy: Java toolchain version, platforms/BOMs, and libraries. SimpleDSL itself owns the Gradle plugin implementation versions that it is compiled and tested against, including Spring Boot, GraalVM Native, jOOQ code generation, and jsonschema2pojo.

## Module discovery

SimpleDSL discovers Gradle modules from the repository root by default. A directory containing one of the supported build descriptors can be discovered automatically:

```text
build.spring.gradle
build.spring.gradle.kts
build.gradle
build.gradle.kts
```

Transient output directories such as `build/` and `.gradle/` are excluded from discovery. Discovery is configuration-cache stable: changes to ignored output directories do not invalidate the configuration cache, while changes to the normalized module set do.

Optional module discovery overrides use:

```text
gradle/simpledsl/modules.toml
```

The modules file can select automatic, strict automatic, or manual discovery and can provide explicit roots, exclusions, and module declarations.

## Module type plugins

A module selects exactly one primary type:

| Plugin ID | Purpose |
| --- | --- |
| `io.github.qigao.simpledsl.java-library` | Plain Java library conventions |
| `io.github.qigao.simpledsl.spring-library` | Spring library conventions |
| `io.github.qigao.simpledsl.spring-service` | Spring Boot service conventions |

`io.github.qigao.simpledsl.module` provides the underlying module model and diagnostics. It is normally composed by the module type plugins rather than applied directly.

## Feature plugins

Features are orthogonal capabilities layered on a module type:

| Plugin ID | Capability |
| --- | --- |
| `io.github.qigao.simpledsl.feature.aop` | Spring AOP |
| `io.github.qigao.simpledsl.feature.transaction` | Transactions |
| `io.github.qigao.simpledsl.feature.web` | Spring MVC web support |
| `io.github.qigao.simpledsl.feature.http-client` | Spring HTTP client support |
| `io.github.qigao.simpledsl.feature.messaging` | Spring messaging |
| `io.github.qigao.simpledsl.feature.jdbc` | JDBC |
| `io.github.qigao.simpledsl.feature.jooq` | jOOQ runtime integration |
| `io.github.qigao.simpledsl.feature.jpa` | Spring Data JPA |
| `io.github.qigao.simpledsl.feature.redis` | Spring Data Redis |
| `io.github.qigao.simpledsl.feature.native` | GraalVM Native Build Tools |
| `io.github.qigao.simpledsl.feature.lombok` | Lombok compile-time support |

Capabilities validate their allowed module types, dependency requirements, conflicts, platform bindings, and release-owned external Gradle plugins.

## Schema plugins

SimpleDSL publishes two schema/code-generation plugins:

| Plugin ID | Purpose |
| --- | --- |
| `io.github.qigao.simpledsl.schema.jooq` | Generate jOOQ sources from DDL using SimpleDSL conventions |
| `io.github.qigao.simpledsl.schema.json` | Generate Java sources from JSON Schema using SimpleDSL conventions |

The jOOQ plugin exposes the `simpledslJooq` extension. The JSON Schema plugin exposes the `simpledslJsonSchema` extension.

## Diagnostics

The settings plugin registers:

```text
simpledslProjects
simpledslDependencies
```

Each SimpleDSL module registers:

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

## Configuration cache

The published-consumer integration test verifies a real Spring Boot service through locally published plugin marker artifacts and requires the second identical Gradle invocation to reuse the configuration cache.

Configuration-cache compatibility is declared per public plugin in Plugin Portal metadata. Plugins that have not yet received dedicated compatibility coverage are conservatively declared unsupported until that coverage exists.

## Publication model

SimpleDSL is released as two coordinated implementation artifacts with one release version:

```text
io.github.qigao.simpledsl:simpledsl-build-bootstrap
io.github.qigao.simpledsl:simpledsl-build-logic
```

The bootstrap artifact contains the settings lifecycle. The build-logic artifact contains project/module, feature, and schema plugins. The split keeps Settings and Project plugin classpaths separate while presenting one public SimpleDSL version to consumers.

The repository also verifies all 18 public plugin marker artifacts against an isolated Maven test repository. Internal implementation classes are not published as public plugin IDs.

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

Validate Plugin Portal publication metadata without uploading:

```bash
./gradlew \
  :simpledsl-build-bootstrap:publishPlugins \
  :simpledsl-build-logic:publishPlugins \
  -PreleaseVersion=0.1.0 \
  --validate-only
```

Actual Plugin Portal publication is performed by the tag-triggered GitHub Actions release workflow.

## License

See [LICENSE](LICENSE).
