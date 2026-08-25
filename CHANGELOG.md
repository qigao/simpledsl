# Changelog

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

- The internal dependency snapshot remains schema version 1, so the bootstrap-to-build-logic bridge does not require a protocol migration.
- The built-in Spring integration continues to use the semantic platform alias `spring`.
- The public Plugin Portal surface remains exactly `io.github.qigao.simpledsl.settings` and `io.github.qigao.simpledsl.build`.

### Verification

The release contract is covered by bootstrap tests, build-logic tests, strict manifest validation tests, and a real published-consumer test using a root TOML manifest with included YAML and TOML fragments. Configuration-cache reuse is also verified.

GitHub issue: #4
Pull request: #5
