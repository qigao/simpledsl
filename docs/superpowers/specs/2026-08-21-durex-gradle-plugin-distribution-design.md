# SimpleDSL Gradle Plugin Distribution Design

Date: 2026-08-21
Status: Revised design after namespace review
Target repository: `qigao/simpledsl`
Source repository: `qigao/durex`

## 1. Goal

Move the reusable Gradle build platform currently implemented inside `qigao/durex` into the independent product repository `qigao/simpledsl`, and publish it as a first-class Gradle Plugin Portal product.

The source implementation currently lives in:

- `durex/build-bootstrap`
- `durex/build-logic`

The target product modules are renamed to:

- `simpledsl-build-bootstrap`
- `simpledsl-build-logic`

The new product does **not** retain `durex` as its public brand or plugin namespace.

## 2. Naming and namespace policy

The public product name is **SimpleDSL**.

Because the Gradle Plugin Portal requires new plugin IDs to trace back to the publisher, the public plugin namespace is:

```text
io.github.qigao.simpledsl.*
```

The Maven group is:

```text
io.github.qigao.simpledsl
```

Implementation packages are rooted at:

```text
io.github.qigao.simpledsl.gradle
```

No new public artifact, plugin ID, extension, task, diagnostic, package, or release metadata uses `durex` naming.

The migration is intentionally breaking because `simpledsl` is a new product boundary rather than a compatibility release of the in-repository Durex build logic.

## 3. Public plugin surface

Target public IDs:

```text
io.github.qigao.simpledsl.settings
io.github.qigao.simpledsl.module

io.github.qigao.simpledsl.java-library
io.github.qigao.simpledsl.spring-library
io.github.qigao.simpledsl.spring-service

io.github.qigao.simpledsl.feature.aop
io.github.qigao.simpledsl.feature.transaction
io.github.qigao.simpledsl.feature.web
io.github.qigao.simpledsl.feature.http-client
io.github.qigao.simpledsl.feature.messaging
io.github.qigao.simpledsl.feature.jdbc
io.github.qigao.simpledsl.feature.jooq
io.github.qigao.simpledsl.feature.jpa
io.github.qigao.simpledsl.feature.redis
io.github.qigao.simpledsl.feature.native
io.github.qigao.simpledsl.feature.lombok

io.github.qigao.simpledsl.schema.jooq
io.github.qigao.simpledsl.schema.json
```

Internal implementation IDs, if retained, use:

```text
io.github.qigao.simpledsl.internal.*
```

They are not documented as consumer API and are not intended as long-term compatibility contracts.

## 4. Old-to-new migration map

The extraction rewrites the public Durex build API as follows:

| Old Durex ID | New SimpleDSL ID |
| --- | --- |
| `durex.settings` | `io.github.qigao.simpledsl.settings` |
| `durex.module` | `io.github.qigao.simpledsl.module` |
| `durex.java-library` | `io.github.qigao.simpledsl.java-library` |
| `durex.spring-library` | `io.github.qigao.simpledsl.spring-library` |
| `durex.spring-service` | `io.github.qigao.simpledsl.spring-service` |
| `durex.feature.aop` | `io.github.qigao.simpledsl.feature.aop` |
| `durex.feature.transaction` | `io.github.qigao.simpledsl.feature.transaction` |
| `durex.feature.web` | `io.github.qigao.simpledsl.feature.web` |
| `durex.feature.http-client` | `io.github.qigao.simpledsl.feature.http-client` |
| `durex.feature.messaging` | `io.github.qigao.simpledsl.feature.messaging` |
| `durex.feature.jdbc` | `io.github.qigao.simpledsl.feature.jdbc` |
| `durex.feature.jooq` | `io.github.qigao.simpledsl.feature.jooq` |
| `durex.feature.jpa` | `io.github.qigao.simpledsl.feature.jpa` |
| `durex.feature.redis` | `io.github.qigao.simpledsl.feature.redis` |
| `durex.feature.native` | `io.github.qigao.simpledsl.feature.native` |
| `durex.feature.lombok` | `io.github.qigao.simpledsl.feature.lombok` |
| `durex.schema.jooq` | `io.github.qigao.simpledsl.schema.jooq` |
| `durex.schema.json` | `io.github.qigao.simpledsl.schema.json` |

The old `durex.*` IDs are **not** published from `simpledsl` as aliases.

Any temporary compatibility bridge belongs only in the downstream `qigao/durex` migration branch and is removed after cutover.

## 5. Repository layout

The target repository is a normal Gradle multi-project plugin build:

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
├── simpledsl-build-bootstrap/
│   ├── build.gradle.kts
│   └── src/
├── simpledsl-build-logic/
│   ├── build.gradle.kts
│   └── src/
├── integration-tests/
│   └── consumer/
├── docs/
└── .github/workflows/
```

The product build must not require applying SimpleDSL to build SimpleDSL itself.

## 6. Publication units

### 6.1 `simpledsl-build-bootstrap`

This artifact contains settings-time infrastructure and publishes:

```text
io.github.qigao.simpledsl.settings
```

Responsibilities:

- load and validate the consumer SimpleDSL dependency manifest;
- create the dependency registry service;
- discover and include consumer modules;
- expose root diagnostics;
- establish coherent SimpleDSL plugin-version resolution for the rest of the build.

### 6.2 `simpledsl-build-logic`

This artifact contains project-level binary Gradle plugins:

```text
io.github.qigao.simpledsl.module
io.github.qigao.simpledsl.java-library
io.github.qigao.simpledsl.spring-library
io.github.qigao.simpledsl.spring-service
io.github.qigao.simpledsl.feature.*
io.github.qigao.simpledsl.schema.*
```

The bootstrap and build-logic artifacts always use one shared release version.

## 7. Public plugins are binary plugins

The existing public Durex module/feature/schema plugins are largely precompiled script plugins. The SimpleDSL product converts the public surface into binary Gradle plugins with explicit implementation classes.

Examples:

```text
io.github.qigao.simpledsl.settings
  -> io.github.qigao.simpledsl.gradle.settings.SimpleDslSettingsPlugin

io.github.qigao.simpledsl.spring-service
  -> io.github.qigao.simpledsl.gradle.module.SpringServicePlugin

io.github.qigao.simpledsl.feature.web
  -> io.github.qigao.simpledsl.gradle.feature.WebFeaturePlugin
```

This creates stable Plugin Portal metadata and external API boundaries while allowing internal helper classes or convention fragments to remain implementation details.

## 8. Consumer contract

A consumer versions SimpleDSL once in `settings.gradle`:

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

rootProject.name = 'sample'
```

Project build files then use the public plugin IDs without repeating a version:

```groovy
plugins {
    id 'io.github.qigao.simpledsl.spring-service'
    id 'io.github.qigao.simpledsl.feature.web'
}
```

`SimpleDslSettingsPlugin` owns the release-coherence rule:

- unversioned `io.github.qigao.simpledsl.*` requests resolve to the running SimpleDSL version;
- an explicit matching version is accepted;
- an explicit conflicting version fails with a clear diagnostic.

## 9. SimpleDSL DSL and diagnostics naming

The consumer-facing DSL is renamed as part of the extraction.

Settings extension:

```text
simpledslSettings
```

Module extension:

```text
simpledsl
```

Diagnostics/tasks:

```text
simpledslProjects
simpledslDependencies
simpledslCapabilities
simpledslDoctor
```

Model and implementation classes are renamed from Durex-specific names to SimpleDSL-specific names where they are part of the extracted product.

Error prefixes use:

```text
SimpleDSL configuration error
SimpleDSL bootstrap error
SimpleDSL module discovery error
SimpleDSL Doctor
```

No public diagnostic recommends `durex.*` IDs.

## 10. Consumer manifest layout

SimpleDSL owns its own consumer configuration namespace.

Recommended layout:

```text
gradle/simpledsl/
├── dependencies.toml
├── modules.toml
└── dependencies/
    ├── spring.toml
    ├── database.toml
    ├── test.toml
    └── utils.toml
```

`dependencies.toml` is the root dependency manifest and may include the split dependency files.

This avoids carrying the historical file name `gradle/dependencies/durex.toml` into the new product.

The settings extension allows callers to override these locations when needed.

## 11. Dependency and version ownership

SimpleDSL release implementation dependencies and consumer application dependency policy are separate.

### SimpleDSL release owns

The plugin repository owns implementation versions for:

- `com.gradle.plugin-publish`;
- Spring Boot Gradle plugin API;
- GraalVM Native Gradle plugin API;
- jOOQ code-generation Gradle plugin API;
- jsonschema2pojo Gradle plugin API;
- TOML parser;
- any other dependency required to execute SimpleDSL itself.

These versions live in the SimpleDSL repository, preferably in `gradle/libs.versions.toml`.

### Consumer owns

The consumer SimpleDSL manifest owns application dependency policy:

- Java version policy;
- Spring/platform BOM versions;
- application/runtime libraries;
- database/test/tool libraries;
- application-level aliases.

A consumer cannot rewrite the already-published implementation classpath of SimpleDSL.

## 12. Self-hosting policy

The SimpleDSL repository builds with ordinary Gradle configuration and does not apply `io.github.qigao.simpledsl.settings` to bootstrap itself.

This deliberately prevents a circular self-hosting dependency.

The product build flow is:

```text
standard Gradle build
  -> simpledsl-build-bootstrap
  -> simpledsl-build-logic
  -> plugin implementation artifacts
  -> plugin marker artifacts
```

## 13. Local publication contract

Before Plugin Portal publication, CI must publish all implementation artifacts and plugin markers into an isolated Maven repository under `build/`.

The standalone consumer fixture resolves only those published artifacts.

It must not use:

- `includeBuild('../simpledsl-build-bootstrap')`;
- `includeBuild('../simpledsl-build-logic')`;
- direct project dependencies on either implementation module.

## 14. Consumer fixture

`integration-tests/consumer` is a standalone external-style build with its own:

- `settings.gradle`;
- `gradle/simpledsl/**` manifests;
- application module;
- Spring source/test.

It verifies:

1. `io.github.qigao.simpledsl.settings` resolves through a plugin marker;
2. project-level SimpleDSL plugins resolve to the same version;
3. module discovery works without access to SimpleDSL source;
4. `simpledslProjects` and `simpledslDependencies` work;
5. `simpledslCapabilities` and `simpledslDoctor` work;
6. one Spring service compiles and tests;
7. configuration cache is reusable;
8. an explicit mismatched SimpleDSL plugin version fails with a precise diagnostic;
9. no output or public metadata contains stale `durex.*` names.

## 15. Plugin Portal configuration

The publication build uses the current `com.gradle.plugin-publish` 2.x line and Gradle plugin development metadata.

Maven group:

```text
io.github.qigao.simpledsl
```

Every public plugin declares:

- plugin ID;
- implementation class;
- display name;
- English description;
- tags;
- website;
- VCS URL;
- declared Gradle feature compatibility such as configuration-cache support where applicable.

The project README is English-first for Plugin Portal review and contains installation examples using only `io.github.qigao.simpledsl.*`.

Pre-release verification includes:

```text
./gradlew check
./gradlew publishToLocalTestRepository
./gradlew consumerContract
./gradlew publishPlugins --validate-only
```

Credentials are supplied only through:

```text
GRADLE_PUBLISH_KEY
GRADLE_PUBLISH_SECRET
```

## 16. Versioning and release

Initial release target:

```text
0.1.0
```

All SimpleDSL implementation artifacts and plugin marker artifacts use the same immutable release version.

Recommended release flow:

```text
PR CI
  -> build + tests + local publication + consumer contract + validate-only
merge to master
  -> no public publish
tag v0.1.0
  -> release workflow
  -> publishPlugins
  -> initial Plugin Portal review
```

## 17. Source migration

Migration is selective rather than a blind copy.

Move/rewrite from `qigao/durex`:

- reusable manifest parser/registry code;
- module discovery and project registry code;
- module model/capability engine;
- reusable diagnostics;
- generic build logic tests/fixtures;
- schema plugin implementations/tests;
- module and feature plugin behavior.

Rename while moving:

- Java/Groovy packages from `com.github.durex...` to `io.github.qigao.simpledsl...`;
- classes named `Durex*` to `SimpleDsl*` where they belong to the product;
- Gradle extension/task names from `durex*` to `simpledsl*`;
- plugin IDs from `durex.*` to `io.github.qigao.simpledsl.*`;
- manifest defaults and diagnostic strings.

Do not move:

- Music/runtime application modules;
- Durex application CI;
- application-specific manifests;
- legacy Durex migration documentation not required by the plugin product.

## 18. Downstream `qigao/durex` cutover

`qigao/durex` keeps its local implementation until SimpleDSL proves the external consumer contract.

Cutover sequence:

1. extract, rename, and build SimpleDSL;
2. publish to local test repository;
3. prove standalone consumer fixture;
4. publish and obtain approval for `0.1.0` on the Plugin Portal;
5. update `qigao/durex` settings/build files from `durex.*` to `io.github.qigao.simpledsl.*`;
6. rename Durex repository manifest paths to the SimpleDSL consumer layout;
7. run full Durex CI using the released SimpleDSL version;
8. delete local `build-bootstrap` and `build-logic` from `qigao/durex`;
9. remove any temporary Durex-only compatibility bridge.

The old plugin IDs are not part of the SimpleDSL release contract.

## 19. CI gates

Every PR in `simpledsl` verifies:

- plugin compilation;
- unit/TestKit tests;
- Gradle plugin validation;
- local Maven publication;
- plugin marker resolution;
- standalone consumer contract;
- configuration cache;
- public namespace guard rejecting `durex.*` leakage;
- package/diagnostic guard rejecting stale `com.github.durex` / `Durex` public naming;
- `publishPlugins --validate-only` where supported.

Release publication runs only from protected release/tag workflow with Plugin Portal secrets.

## 20. Non-goals

The first release does not:

- publish compatibility aliases under `durex.*`;
- add observability/OpenAPI/Protobuf features;
- redesign the capability semantics beyond the naming/product extraction needed for SimpleDSL;
- publish Durex runtime libraries;
- automatically publish every merge;
- delete Durex local build logic before external consumer validation succeeds.

## 21. Success criteria

The migration is complete when:

1. `qigao/simpledsl` builds independently of `qigao/durex`;
2. the public product namespace is exclusively `io.github.qigao.simpledsl.*`;
3. Maven implementation coordinates use `io.github.qigao.simpledsl`;
4. public source packages/DSL/tasks/diagnostics use SimpleDSL naming rather than Durex naming;
5. `simpledsl-build-bootstrap` and `simpledsl-build-logic` publish with one release version;
6. an isolated consumer resolves all required plugins only through published artifacts and plugin markers;
7. `publishPlugins --validate-only` succeeds;
8. README and plugin metadata satisfy Plugin Portal review requirements;
9. the `0.1.0` release is ready for Plugin Portal submission;
10. `qigao/durex` can migrate to the released SimpleDSL plugin IDs and remove its in-repository copies.
