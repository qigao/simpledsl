# Durex Gradle Plugin Distribution Design

Date: 2026-08-21
Status: Approved direction / implementation design
Target repository: `qigao/simpledsl`
Source repository: `qigao/durex`

## 1. Goal

Move the Durex build platform out of the Durex application repository and turn it into an independently built, tested, versioned, and publishable Gradle plugin product.

The migration moves the reusable build infrastructure currently implemented by:

- `durex/build-bootstrap`
- `durex/build-logic`

into `qigao/simpledsl` as two product modules:

- `durex-build-bootstrap`
- `durex-build-logic`

The first public release targets the Gradle Plugin Portal. The existing public plugin IDs remain `durex.*`; this migration does not introduce a plugin namespace rename.

## 2. Product boundary

The new repository owns only reusable Gradle build-platform code and its tests/documentation.

It owns:

- Durex settings/bootstrap plugins;
- dependency-manifest parsing and validation;
- module discovery;
- Durex module model and capability engine;
- public module-type plugins;
- public feature plugins;
- public schema/code-generation plugins;
- diagnostics such as `durexProjects`, `durexDependencies`, `durexCapabilities`, and `durexDoctor`;
- Plugin Portal publication metadata and release automation;
- isolated consumer contract tests.

It does not own:

- Durex application modules such as Music;
- runtime application implementations;
- Durex repository-specific module manifests;
- application integration tests that only make sense inside `qigao/durex`.

## 3. Repository layout

The target repository is a normal Gradle multi-project plugin build rather than a recursive included-build bootstrap.

```text
simpledsl/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── gradlew
├── gradlew.bat
├── durex-build-bootstrap/
│   ├── build.gradle.kts
│   └── src/
├── durex-build-logic/
│   ├── build.gradle.kts
│   └── src/
├── integration-tests/
│   └── consumer/
├── docs/
└── .github/workflows/
```

The root build includes both modules directly. Building the plugin product must not require applying Durex to build Durex itself.

## 4. Publication units

### 4.1 `durex-build-bootstrap`

This artifact contains settings-time infrastructure and publishes at least:

- `durex.settings`

Internal settings/bootstrap implementation can remain in the same artifact when it is required to build or test the product, but internal IDs are not part of the supported consumer API.

Responsibilities:

- load and validate the consumer Durex dependency manifest;
- create the dependency registry service;
- discover and include consumer modules;
- expose root diagnostics;
- establish Durex plugin-version resolution for the rest of the build.

### 4.2 `durex-build-logic`

This artifact contains project plugins and depends on the stable bootstrap/model contracts it needs.

Public plugin surface remains:

```text
durex.module

durex.java-library
durex.spring-library
durex.spring-service

durex.feature.aop
durex.feature.transaction
durex.feature.web
durex.feature.http-client
durex.feature.messaging
durex.feature.jdbc
durex.feature.jooq
durex.feature.jpa
durex.feature.redis
durex.feature.native
durex.feature.lombok

durex.schema.jooq
durex.schema.json
```

The two publication units use the same release version.

## 5. Public plugins become binary plugin entry points

The current public module/feature/schema plugins in `qigao/durex` are largely precompiled script plugins. The distribution repository converts the supported `durex.*` surface into binary Gradle plugin entry points with explicit implementation classes.

Reasons:

- stable public entry points;
- explicit plugin metadata;
- easier TestKit testing;
- predictable external dependency metadata;
- cleaner separation between public and internal implementation;
- simpler Plugin Portal publication and evolution.

Internal convention helpers may remain implementation details where useful, but consumers must never depend on their file layout or internal plugin IDs.

## 6. Consumer contract

A third-party build versions Durex once, at settings time:

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id 'durex.settings' version '0.1.0'
}

rootProject.name = 'sample'
```

Module builds then use Durex public plugins without repeating the version:

```groovy
plugins {
    id 'durex.spring-service'
    id 'durex.feature.web'
}
```

`durex.settings` owns the release-coherence rule for Durex plugin requests:

- an unversioned `durex.*` plugin request resolves to the running Durex distribution version;
- an explicitly requested matching version is accepted;
- an explicitly requested different Durex version fails with a clear version-conflict diagnostic.

The implementation version is embedded in the bootstrap artifact as generated build metadata and is not inferred from the consumer manifest.

## 7. Dependency and version ownership

Durex release dependencies and consumer application dependencies are separate concerns.

### Durex release owns

The plugin product build owns the versions needed to implement the plugins themselves, including:

- Gradle Plugin Publish plugin;
- Spring Boot Gradle plugin API;
- GraalVM Native Gradle plugin API;
- jOOQ code-generation Gradle plugin API;
- jsonschema2pojo Gradle plugin API;
- TOML parser used by bootstrap.

These versions live in the plugin repository build configuration, preferably `gradle/libs.versions.toml`. A consumer manifest cannot replace the implementation classpath of an already released Durex plugin.

### Consumer owns

The consumer Durex manifest continues to own application dependency policy:

- managed Java version;
- Spring/platform BOM version;
- database libraries;
- test libraries;
- application-facing library aliases;
- external application plugin versions that are intentionally part of the consumer policy.

When a consumer manifest declares an entry that conflicts with an implementation-owned Durex plugin requirement, Durex reports an explicit compatibility error rather than silently changing its implementation classpath.

## 8. Self-hosting policy

The new plugin repository does not use `durex.settings` or `durex.internal.build-logic-settings` to bootstrap itself.

It uses ordinary Gradle build configuration to compile and publish Durex.

This breaks the current cycle:

```text
Durex manifest
  -> bootstrap included build
  -> build-logic included build
  -> Durex plugins
```

and replaces it with:

```text
standard Gradle plugin build
  -> durex-build-bootstrap artifact
  -> durex-build-logic artifact
  -> published plugin markers
```

The consumer still receives Durex's manifest/capability model; only the product's own build stops depending on that consumer mechanism.

## 9. Local publication contract

Before any Plugin Portal upload, the repository must publish both plugin modules and plugin markers into an isolated local Maven repository under `build/`.

The consumer integration fixture must resolve from that Maven repository and must not use:

- `includeBuild('../durex-build-bootstrap')`;
- `includeBuild('../durex-build-logic')`;
- direct project dependencies on either plugin module.

This proves the same artifact/marker resolution path used by external consumers.

## 10. Consumer fixture

`integration-tests/consumer` is a standalone sample build with its own:

- `settings.gradle`;
- `gradle/dependencies/*.toml` fixture manifest;
- module directory;
- Spring service source/test.

It verifies:

1. `durex.settings` resolves from the local Maven publication by plugin marker;
2. unversioned project-level `durex.*` plugins resolve to the same distribution version;
3. module discovery works without access to the source repository;
4. `durexProjects` and `durexDependencies` work;
5. `durexCapabilities` and `durexDoctor` work;
6. one Spring service compiles/tests;
7. configuration cache can be reused;
8. a mismatched explicit Durex plugin version fails with a specific diagnostic.

## 11. Plugin Portal configuration

The publication build applies the current `com.gradle.plugin-publish` 2.x line and declares complete Plugin Portal metadata for every public plugin:

- plugin ID;
- implementation class;
- display name;
- description;
- tags;
- website;
- VCS URL.

The repository must contain an English README describing installation and usage because Plugin Portal approval requires useful public documentation.

Pre-release verification includes:

```text
./gradlew check
./gradlew publishToLocalTestRepository
./gradlew consumerContract
./gradlew publishPlugins --validate-only
```

Actual publication uses Plugin Portal credentials supplied only through CI/release secrets:

```text
GRADLE_PUBLISH_KEY
GRADLE_PUBLISH_SECRET
```

No publish credential is committed to the repository.

## 12. Versioning and release

Initial target release: `0.1.0`.

The release version is one value shared by both plugin modules and every marker artifact. Releases are immutable.

Recommended release flow:

```text
PR CI
  -> build + tests + local consumer contract + validate-only
merge to master
  -> no automatic public release
tag v0.1.0
  -> release workflow
  -> publishPlugins
  -> Plugin Portal review/approval for initial IDs
```

A later release changes both publication units together even when only one implementation module changed. This avoids mixed bootstrap/build-logic compatibility states.

## 13. Java and Gradle baseline

The first migration preserves the existing Durex platform baseline:

- Gradle 9.1.x;
- Java 25 as the default managed application toolchain.

The plugin implementation should avoid unnecessarily emitting Java-25-only bytecode where Gradle API compatibility permits a lower target. Broadening the supported Gradle/JVM matrix is a follow-up compatibility task and must not block the first extraction.

## 14. Source migration

Migration is selective, not a blind directory copy.

Move from `qigao/durex`:

- reusable `build-bootstrap/src` implementation;
- reusable bootstrap fixtures/tests;
- reusable `build-logic/src` implementation;
- generic build-logic fixtures/tests;
- plugin namespace/diagnostic tests;
- schema plugin smoke fixtures that do not depend on application modules.

Rewrite during migration:

- build files;
- self-bootstrap settings;
- public precompiled script plugin entry points;
- implementation dependency wiring;
- paths that assume the Durex application repository root;
- CI workflows.

Do not move:

- Music migration fixtures;
- application/runtime source modules;
- legacy migration plans unrelated to plugin distribution;
- Durex application CI.

## 15. Downstream Durex cutover

`qigao/durex` does not delete its in-repository plugin sources until the extracted repository passes the isolated consumer contract.

Cutover sequence:

1. build and locally publish the extracted plugin product;
2. prove the standalone consumer fixture;
3. publish/approve the first Plugin Portal release;
4. change `qigao/durex` to consume `durex.settings` by released version;
5. remove `build-bootstrap` and `build-logic` from `qigao/durex`;
6. keep Durex application-specific dependency/module manifests in `qigao/durex`;
7. run the full Durex Spring CI against the published plugin product.

This keeps the application repository buildable throughout the extraction.

## 16. CI gates

Every PR in `simpledsl` must verify:

- plugin compilation;
- unit/TestKit tests;
- plugin validation;
- local Maven publication metadata;
- plugin marker resolution;
- isolated consumer contract;
- configuration cache;
- legacy/internal plugin namespace guards;
- `publishPlugins --validate-only` without credentials when supported by the publish plugin configuration.

Release publication is a separate tag-triggered workflow with GitHub Environment/secrets protection.

## 17. Non-goals

The first extraction does not:

- rename `durex.*` to `simpledsl.*`;
- redesign the capability DSL;
- add observability/OpenAPI/Protobuf features;
- publish Durex application runtime libraries;
- automatically publish on every merge;
- guarantee compatibility with Gradle versions older than the current platform baseline;
- delete the original implementation before the extracted consumer contract and first release are proven.

## 18. Success criteria

The migration is complete when all of the following are true:

1. `qigao/simpledsl` builds Durex plugins without depending on `qigao/durex` source or `includeBuild` bootstrap;
2. `durex-build-bootstrap` and `durex-build-logic` are independently produced artifacts with one shared version;
3. every supported public `durex.*` plugin has Plugin Portal-compatible marker metadata;
4. an isolated consumer resolves and uses the plugins only through published test artifacts;
5. `publishPlugins --validate-only` succeeds;
6. public README and plugin metadata satisfy Plugin Portal review requirements;
7. the repository is ready for a credentialed `v0.1.0` publication;
8. after Portal approval, `qigao/durex` can consume the released plugins and remove its local copies.
