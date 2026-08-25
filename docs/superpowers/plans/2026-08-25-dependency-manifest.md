# Dependency Manifest Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace SimpleDSL's custom dependency manifest surface with repository-root Gradle-shaped TOML plus YAML, while preserving the internal dependency snapshot contract.

**Architecture:** Syntax-specific readers decode TOML/YAML into a normalized `Map<String,Object>` document. `DependencyManifestLoader` owns include traversal and all semantic validation, derives the existing platform registry from library aliases referenced through `platform`, and produces the unchanged snapshot schema consumed by build logic. Settings-plugin default discovery is separated into a small locator so root-file ambiguity and explicit override behavior are testable independently.

**Tech Stack:** Gradle 9.1, Groovy/Java 21 plugin runtime, Java 25 CI, Tomlj, SnakeYAML Engine 3.1.1, JUnit 5, Gradle TestKit.

**Spec:** `docs/superpowers/specs/2026-08-25-dependency-manifest-design.md`

## Global Constraints

- Default files are exactly `dependencies.toml`, `dependencies.yml`, and `dependencies.yaml` at repository root.
- More than one default file is an error; there is no precedence rule.
- Explicit `simpledslSettings.dependencyManifest` overrides discovery.
- Public root keys are exactly `include`, `simpledsl`, `versions`, `libraries`, and `plugins`.
- SimpleDSL Java policy is `simpledsl.java`; legacy `[java]` is not accepted.
- Public `[platforms]` is removed; platform/BOM coordinates are library declarations.
- TOML and YAML must resolve to the same semantic registry.
- Includes may mix TOML and YAML and remain relative to the declaring file.
- Internal snapshot schema remains version 1.
- No fallback to `gradle/simpledsl/dependencies.toml`.

---

### Task 1: Root manifest discovery contract

**Files:**
- Create: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/settings/DependencyManifestLocator.groovy`
- Modify: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/settings/SimpleDslSettingsPlugin.groovy`
- Modify/Test: `simpledsl-build-bootstrap/src/test/groovy/io/github/qigao/simpledsl/gradle/settings/SimpleDslSettingsPluginTest.groovy`

**Interfaces:**
- Produces: `DependencyManifestLocator.locate(File repositoryRoot) -> File`
- `SimpleDslSettingsPlugin` uses the explicit `dependencyManifest` property when present, otherwise calls the locator at `settingsEvaluated`.

- [ ] **Step 1: Write failing TestKit tests**

Add cases that create `dependencies.toml` at `projectDir`, use `[simpledsl]\njava = 25`, and expect `simpledslDependencies` to print `Java: 25`. Add a YAML-root case using `dependencies.yml`. Add an ambiguity case with both root files and assert the output contains `ambiguous dependency manifest`, `dependencies.toml`, and `dependencies.yml`. Add an explicit override case whose settings script contains:

```groovy
simpledslSettings {
    dependencyManifest = layout.settingsDirectory.file('config/deps.toml')
}
```

and add a no-fallback case that writes only `gradle/simpledsl/dependencies.toml` and expects failure mentioning the accepted root names.

- [ ] **Step 2: Verify RED**

Run:

```bash
gradle :simpledsl-build-bootstrap:test --tests '*SimpleDslSettingsPluginTest' --stacktrace
```

Expected: root discovery/YAML cases fail because the plugin still conventions `gradle/simpledsl/dependencies.toml` and the loader does not accept the new schema.

- [ ] **Step 3: Implement the locator and settings wiring**

`DependencyManifestLocator` checks exactly three filenames, returns the single match, and throws `GradleException` for zero or multiple matches. Remove the old `dependencyManifest.convention(repositoryRoot.file('gradle/simpledsl/dependencies.toml'))`; resolve an explicit property first and the locator second before registering `DependencyRegistryService`.

- [ ] **Step 4: Re-run focused tests**

The TOML root-discovery path may still fail on `[simpledsl]` until Task 3; the ambiguity and no-fallback diagnostics must now fail/pass for the intended reason only.

- [ ] **Step 5: Commit**

```bash
git add simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/settings \
        simpledsl-build-bootstrap/src/test/groovy/io/github/qigao/simpledsl/gradle/settings/SimpleDslSettingsPluginTest.groovy
git commit -m 'feat: discover root dependency manifest'
```

### Task 2: Normalized TOML/YAML readers

**Files:**
- Create: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest/ManifestReader.groovy`
- Create: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest/TomlManifestReader.groovy`
- Create: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest/YamlManifestReader.groovy`
- Modify: `gradle/libs.versions.toml`
- Modify: `simpledsl-build-bootstrap/build.gradle.kts`
- Modify/Test: `simpledsl-build-bootstrap/src/test/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyManifestLoaderTest.groovy`

**Interfaces:**
- `ManifestReader.read(File file) -> Map<String,Object>`
- Readers return only string-keyed maps, lists, strings, numbers, booleans, and null.

- [ ] **Step 1: Add YAML dependency**

Add version `snakeyaml-engine = "3.1.1"` and library alias:

```toml
snakeyaml-engine = { module = "org.snakeyaml:snakeyaml-engine", version.ref = "snakeyaml-engine" }
```

Then add `implementation(libs.snakeyaml.engine)` to bootstrap.

- [ ] **Step 2: Write reader-facing failing tests through the loader**

Create equivalent `dependencies.toml` and `dependencies.yml` fixtures containing `simpledsl`, `versions`, `libraries`, and `plugins`, call `DependencyManifestLoader.load(...)` for both, and assert `toml.snapshot() == yaml.snapshot()`.

- [ ] **Step 3: Verify RED**

Run:

```bash
gradle :simpledsl-build-bootstrap:test --tests '*DependencyManifestLoaderTest' --stacktrace
```

Expected: YAML loading fails because only Tomlj is supported.

- [ ] **Step 4: Implement syntax readers**

`TomlManifestReader` parses Tomlj and recursively converts `TomlTable`/`TomlArray` into `LinkedHashMap`/`ArrayList`. `YamlManifestReader` uses SnakeYAML Engine `Load` with safe/default settings, requires one mapping document, recursively normalizes maps/lists, and rejects non-string mapping keys.

- [ ] **Step 5: Add extension dispatch**

`DependencyManifestLoader` chooses `.toml`, `.yml`, or `.yaml`, and reports `unsupported manifest extension` for anything else.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml simpledsl-build-bootstrap/build.gradle.kts \
        simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest \
        simpledsl-build-bootstrap/src/test/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyManifestLoaderTest.groovy
git commit -m 'feat: normalize toml and yaml manifests'
```

### Task 3: Gradle-shaped semantic schema and platform derivation

**Files:**
- Modify: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyManifestLoader.groovy`
- Modify/Test: `simpledsl-build-bootstrap/src/test/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyManifestLoaderTest.groovy`
- Delete: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest/PlatformSpec.groovy` only if no internal constructor/API requires it; otherwise keep it strictly as an internal derived value type.

**Interfaces:**
- Input root keys: `include`, `simpledsl`, `versions`, `libraries`, `plugins`.
- `simpledsl.java` is an integer.
- Library version ownership is exactly one of explicit version, `version.ref`, or `platform`.
- A `platform` value refers to a declared library alias.
- The registry's `platforms` map is derived from referenced platform-owner libraries.

- [ ] **Step 1: Write TOML RED tests**

Use:

```toml
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
```

Assert `registry.javaVersion() == 25`, `registry.platform('spring-bom').notation() == 'org.springframework.boot:spring-boot-dependencies:4.1.0'`, `registry.library('spring-web').platform == 'spring-bom'`, and that the snapshot contains both `libraries.spring-bom` and derived `platforms.spring-bom`.

Add failures for legacy `[java]`, public `[platforms]`, unknown platform aliases, a platform owner with no normal version owner, and a platform owner that is itself platform-owned.

- [ ] **Step 2: Verify RED**

Run the focused loader test and confirm failures are caused by the old schema.

- [ ] **Step 3: Rewrite semantic parsing over normalized maps**

Remove Tomlj types from `DependencyManifestLoader`. Validate string-keyed maps and exact allowed keys. Parse `simpledsl.java`; parse versions; parse libraries into raw records first; resolve normal versions; then collect all aliases referenced by `platform`, validate each owner, and construct derived `PlatformSpec` values from the referenced library's resolved module/version. Finally resolve platform-owned consumer libraries.

- [ ] **Step 4: Keep snapshot schema 1**

Do not change `DependencyRegistry.SNAPSHOT_SCHEMA_VERSION` or the build-logic bridge contract.

- [ ] **Step 5: Verify GREEN**

Run:

```bash
gradle :simpledsl-build-bootstrap:test --tests '*DependencyManifestLoaderTest' --stacktrace
```

Expected: all loader tests pass.

- [ ] **Step 6: Commit**

```bash
git add simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest \
        simpledsl-build-bootstrap/src/test/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyManifestLoaderTest.groovy
git commit -m 'refactor: adopt gradle-shaped dependency schema'
```

### Task 4: Mixed-format includes and strict validation

**Files:**
- Modify: `simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyManifestLoader.groovy`
- Modify/Test: `simpledsl-build-bootstrap/src/test/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyManifestLoaderTest.groovy`

**Interfaces:**
- Includes are resolved relative to the declaring file.
- Canonical paths drive cycle and load-once behavior.
- Duplicate aliases are errors across all included documents.

- [ ] **Step 1: Write RED tests**

Create a TOML root that includes `parts/spring.yml`, and let that YAML file include `test.toml`. Assert successful resolution. Add a cycle `a.toml -> b.yml -> a.toml`, duplicate version/library aliases split across formats, and an include with `.json` that must fail with `unsupported manifest extension`.

- [ ] **Step 2: Verify RED**

Run focused loader tests and confirm mixed-format traversal/diagnostics fail for the expected missing behavior.

- [ ] **Step 3: Implement generic include traversal**

Load each included file through extension dispatch, preserve canonical-path stack diagnostics, and use one shared semantic `State` for all formats.

- [ ] **Step 4: Verify GREEN**

Run all bootstrap tests:

```bash
gradle :simpledsl-build-bootstrap:check --stacktrace
```

- [ ] **Step 5: Commit**

```bash
git add simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyManifestLoader.groovy \
        simpledsl-build-bootstrap/src/test/groovy/io/github/qigao/simpledsl/gradle/manifest/DependencyManifestLoaderTest.groovy
git commit -m 'test: cover mixed dependency manifests'
```

### Task 5: Consumer migration and documentation

**Files:**
- Create: `integration-tests/consumer/dependencies.toml`
- Create directory/files: `integration-tests/consumer/dependencies/spring.toml`, `integration-tests/consumer/dependencies/test.toml`
- Delete: `integration-tests/consumer/gradle/simpledsl/dependencies.toml`
- Delete: `integration-tests/consumer/gradle/simpledsl/spring.toml`
- Delete: `integration-tests/consumer/gradle/simpledsl/test.toml`
- Modify: `README.md`

**Interfaces:**
- Published consumer uses only the new root default.
- Spring BOM is declared as `libraries.spring-bom`; Spring libraries use `platform = "spring-bom"`.

- [ ] **Step 1: Migrate the integration fixture**

Root file:

```toml
include = ["dependencies/spring.toml", "dependencies/test.toml"]

[simpledsl]
java = 25
```

Spring file declares `spring-bom` under `[libraries]` and uses it as the `platform` owner for Spring starters.

- [ ] **Step 2: Update README contract**

Replace `gradle/simpledsl/dependencies.toml` with repository-root discovery, document TOML/YAML filename alternatives, show `[simpledsl] java`, and explain that BOMs are libraries referenced through `platform`.

- [ ] **Step 3: Run published consumer verification**

```bash
gradle publishToTestPluginRepository :integration-tests:test --stacktrace
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add README.md integration-tests/consumer
git commit -m 'docs: migrate consumer dependency manifest'
```

### Task 6: Full verification and PR

**Files:**
- Review all branch changes; no new product files unless a CI failure proves one is needed.

**Interfaces:**
- CI contract remains the existing Ubuntu/Java 25/Gradle 9.1 workflow.

- [ ] **Step 1: Run the complete repository verification**

```bash
gradle clean \
  verifyProductNamespace \
  :simpledsl-build-bootstrap:check \
  :simpledsl-build-logic:check \
  publishToTestPluginRepository \
  :integration-tests:test \
  --stacktrace
```

Expected: PASS with no test failures.

- [ ] **Step 2: Review branch diff**

Confirm there is no fallback string `gradle/simpledsl/dependencies.toml` in product code/docs/fixtures, no public parser support for root `java` or `platforms`, and the internal snapshot remains schema 1.

- [ ] **Step 3: Open PR**

Title:

```text
feat: adopt Gradle-shaped dependency manifests
```

Body must summarize root discovery, TOML/YAML normalization, platform flattening, fixture migration, and `Closes #4`.

- [ ] **Step 4: Verify PR CI**

Wait for the current PR-head workflow result only. If it fails, inspect the failed job log, fix the concrete failure in a new commit, and re-check the new head.
