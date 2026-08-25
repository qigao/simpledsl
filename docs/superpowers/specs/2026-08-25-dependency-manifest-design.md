# Dependency Manifest Contract Design

## Context

Issue #4 replaces SimpleDSL's custom Gradle-adjacent dependency manifest with a smaller contract that deliberately borrows Gradle Version Catalog vocabulary, adds YAML as an equivalent serialization, and moves the default dependency manifest out of `gradle/simpledsl/` to the repository root.

The current loader combines TOML parsing, include traversal, schema validation, semantic validation, and registry construction in one class. The new design separates syntax decoding from semantic interpretation so TOML and YAML cannot drift.

## Goals

1. Discover a dependency manifest at repository root as `dependencies.toml`, `dependencies.yml`, or `dependencies.yaml`.
2. Fail fast when more than one default root manifest exists.
3. Keep `simpledslSettings.dependencyManifest` as an explicit override.
4. Use Gradle-shaped dependency vocabulary for `versions`, `libraries`, and `plugins`.
5. Move SimpleDSL-only Java toolchain policy under `[simpledsl]` / `simpledsl:`.
6. Remove the public `[platforms]` manifest namespace. BOM/platform coordinates are ordinary library declarations and a consumer library refers to them by alias with `platform = "alias"`.
7. Support TOML and YAML through one normalized semantic model.
8. Allow `include` files to mix TOML and YAML while preserving duplicate and cycle validation.
9. Do not provide a legacy fallback to `gradle/simpledsl/dependencies.toml`.

## Repository layout

The default layout is:

```text
repo/
├── dependencies.toml          # or dependencies.yml / dependencies.yaml
├── dependencies/
│   ├── spring.toml
│   └── test.yml
├── settings.gradle
└── ...
```

`include` paths are resolved relative to the file that declares them, so nested manifests remain relocatable.

## Root discovery

When `simpledslSettings.dependencyManifest` is explicitly configured, SimpleDSL uses that file exactly.

Otherwise SimpleDSL checks the repository root for these names:

```text
dependencies.toml
dependencies.yml
dependencies.yaml
```

Rules:

- exactly one exists: use it;
- none exists: fail with a dependency manifest error listing the accepted names;
- more than one exists: fail as ambiguous and list the matching files.

No precedence is assigned between TOML and YAML.

## Manifest schema

### TOML

```toml
include = ["dependencies/spring.toml"]

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

### YAML

```yaml
include:
  - dependencies/spring.yml

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
    module: org.springframework.boot:spring-boot-gradle-plugin
    version:
      ref: spring-boot
```

The supported root keys are exactly:

```text
include
simpledsl
versions
libraries
plugins
```

`[simpledsl]` currently supports only `java`.

## Platform semantics

There is no `[platforms]` section. A platform/BOM is declared as a normal library. Any library may select one declared library alias as its version owner:

```toml
[libraries.spring-bom]
module = "org.springframework.boot:spring-boot-dependencies"
version.ref = "spring-boot"

[libraries.spring-web]
module = "org.springframework.boot:spring-boot-starter-web"
platform = "spring-bom"
```

The semantic parser derives the existing bootstrap platform registry from aliases referenced by `platform`. This keeps the internal snapshot contract stable for build-logic consumers while removing a redundant public namespace.

A platform owner library must itself have exactly one normal version owner (`version` or `version.ref`) and cannot itself be platform-owned. A consumer library has exactly one version owner among `version`, `version.ref`, or `platform`.

## Normalization architecture

```text
dependencies.toml ──> TomlManifestReader ──┐
                                           ├─> ManifestDocument (Map/List/scalars)
dependencies.yml  ──> YamlManifestReader ──┘
                                                     │
                                                     v
                                           DependencyManifestLoader
                                           - include traversal
                                           - unknown-key checks
                                           - duplicate checks
                                           - version resolution
                                           - platform derivation
                                                     │
                                                     v
                                              DependencyRegistry
```

Readers are syntax adapters only. They return string-keyed maps, lists, strings, integers, booleans, and null. All schema and semantic validation lives above them.

## YAML parser

Use SnakeYAML Engine as a YAML 1.2 parser. The bootstrap module owns the dependency; consumer builds do not need to configure it.

YAML input must decode to a mapping at the document root. Mapping keys must be strings. Unsupported scalar or collection shapes are rejected by the same semantic validation used for TOML.

## Include behavior

`include` is a list of file names. Each target is resolved against the declaring manifest's parent directory. File type is selected by extension:

```text
.toml          -> TOML reader
.yml / .yaml   -> YAML reader
```

Other extensions fail explicitly.

Cycle detection and load-once behavior use canonical file paths. Duplicate aliases remain errors across included files regardless of serialization format.

## Snapshot compatibility

The shared-service snapshot remains schema version 1 and retains:

```text
javaVersion
platforms
libraries
plugins
```

`platforms` is now derived from platform-owner library declarations rather than parsed from a public `[platforms]` table. Keeping this internal shape avoids coupling issue #4 to a separate bootstrap/build-logic protocol migration.

## Tests

Unit tests cover:

- Gradle-shaped TOML with `[simpledsl]`, libraries, plugins, version refs, and derived platforms;
- YAML producing the same registry snapshot as equivalent TOML;
- mixed-format include traversal;
- include cycles;
- duplicate aliases across included files;
- unknown root keys and unknown nested keys;
- unknown platform aliases;
- platform owners that are missing a version or are themselves platform-owned;
- unsupported manifest extensions.

Settings-plugin tests cover:

- root `dependencies.toml` discovery;
- root YAML discovery;
- ambiguous root manifests fail fast;
- explicit `dependencyManifest` override still works;
- no fallback to `gradle/simpledsl/dependencies.toml`.

The published consumer fixture is migrated to repository-root `dependencies.toml` plus a root `dependencies/` directory.

## Documentation contract

README examples and default-path documentation use the root layout and Gradle-shaped schema. They explicitly state that TOML and YAML are equivalent serializations and that SimpleDSL extends Gradle-style vocabulary rather than importing a Gradle Version Catalog verbatim.
