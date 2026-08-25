# Changelog

## Unreleased - 0.3.0 development

SimpleDSL 0.3.0 is evolving the project side into independent build backends. This entry currently describes Phase A only; the Android backend is intentionally not included yet.

### Phase A: shared core and Java backend

- Split the implementation artifacts into backend-neutral `simpledsl-core` and Java/Spring-specific `simpledsl-java`.
- Added the public Java backend plugin `io.github.qigao.simpledsl.java`.
- Removed the ambiguous 0.2.x project marker `io.github.qigao.simpledsl.build`; settings now fail with an explicit migration diagnostic pointing Java consumers to `io.github.qigao.simpledsl.java`.
- Moved catalog bridging, dependency binding, capability primitives, the backend-neutral module model, common diagnostics, and backend claiming into the shared core.
- Moved Java/Spring modules, capabilities, schema helpers, and Java-side third-party Gradle tooling into the Java backend.
- Upgraded the internal dependency snapshot protocol to schema version 2 with backend policy maps.
- `simpledsl.java` remains valid manifest syntax but is no longer required during Settings evaluation; the Java backend requires it when applied.
- Renamed the published Java integration suite to `integration-tests-java` and kept real published-consumer/configuration-cache verification.
- Added a published-POM `verifyBackendIsolation` gate: core cannot carry Spring Boot, GraalVM Native Build Tools, jOOQ, jsonschema2pojo, or AGP implementation dependencies; Java must depend on core and must not carry AGP.

### 0.2.x to 0.3.0 Java migration

Project builds change only the SimpleDSL project entry plugin:

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

The repository-root TOML/YAML dependency manifest, `simpledsl.java`, library aliases, platform aliases, Java/Spring module DSL, and existing Java capabilities remain compatible.

### Not in Phase A

The `io.github.qigao.simpledsl.android` backend, Android application/library module types, Android policy, AGP integration, and Compose support are Phase B/C work and must land before a final 0.3.0 release is tagged.

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

- The internal dependency snapshot remains schema version 1, so the bootstrap-to-build-logic bridge does not require a protocol migration.
- The built-in Spring integration continues to use the semantic platform alias `spring`.
- The public Plugin Portal surface remains exactly `io.github.qigao.simpledsl.settings` and `io.github.qigao.simpledsl.build`.

### Verification

The release contract is covered by bootstrap tests, build-logic tests, strict manifest validation tests, and a real published-consumer test using a root TOML manifest with included YAML and TOML fragments. Configuration-cache reuse is also verified.

GitHub issue: #4
Pull request: #5
