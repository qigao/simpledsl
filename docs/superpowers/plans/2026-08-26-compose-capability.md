# Compose Android Capability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add Jetpack Compose as the first Android backend-specific capability while preserving repository policy → snapshot → backend.

**Final public DSL:** `jetpackCompose()`; semantic capability ID `compose`; generic equivalent `capability('compose')`.

**Architecture:** `simpledsl-core` remains unchanged. `simpledsl-android` registers the Compose `CapabilitySpec`; the existing core engine performs module validation, compiler-plugin application, dependency-alias binding, and model recording. The one AGP-specific mutation (`buildFeatures.compose = true`) remains in an Android-only helper and runs after the core engine succeeds.

**Tech Stack:** Gradle 9.1.0, AGP 9.0.1, built-in Kotlin/KGP 2.2.10, Compose Compiler plugin 2.2.10, compileSdk 36, Java 21, Compose BOM 2026.06.00 / Compose 1.11.x, Gradle TestKit, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-08-25-java-android-backend-split-design.md`

## Outcome adjustments discovered during TDD

The originally proposed bare `compose()` sugar is not a viable Gradle Groovy DSL name. Gradle configures extension closures with a Groovy `Closure`, and `Closure` already owns `compose(Closure)`. The intended zero-argument DSL call therefore resolves into Groovy closure composition before it reaches `SimpleDslAndroidExtension`. This was captured by CI #164/#166.

The collision-safe public sugar is `jetpackCompose()`. The semantic capability remains `compose`, so `capability('compose')` is an equivalent stable generic entry point. CI #167 captured the missing renamed sugar, and CI #168 captured that the generic entry initially activated plugin/dependencies/model but still needed the Android `buildFeatures.compose` mutation. The final Android extension routes both entries through one path.

The compatibility note was also corrected during execution: the repository remains on AGP 9.0.1 / compileSdk 36 with Compose BOM 2026.06.00. Compose 1.12 moves to API 37 and its stable line requires AGP 9.1.2 or newer, so that upgrade belongs to a future baseline phase rather than Phase C.

## Global constraints

- `simpledsl-core` stays backend-neutral: no AGP/Compose dependency and no generic capability activation callback.
- Keep AGP 9.0.1 / Gradle 9.1.0 / compileSdk 36 / minSdk 24 / targetSdk 36 / Java 21.
- Use AGP 9 built-in Kotlin; never apply `org.jetbrains.kotlin.android`.
- Pin `org.jetbrains.kotlin.plugin.compose` to 2.2.10.
- Use Compose BOM 2026.06.00 and stay on the pre-1.12 library line.
- Capability implementation binds manifest aliases only; Compose runtime/UI coordinates and BOM version live in repository manifest data.
- Room, Hilt, KSP, KMP, dynamic features, benchmark modules, custom artifact transforms, and per-module SDK overrides remain out of scope.

---

### Task 1: Define and refine the RED Compose DSL contract

- [x] Added fake snapshot Compose BOM/runtime/UI aliases.
- [x] Added Android application and library Compose contracts.
- [x] Added no-module capability diagnostic contract.
- [x] Captured original bare-`compose()` RED in CI #164.
- [x] Diagnosed the Groovy `Closure.compose(Closure)` collision in CI #166.
- [x] Renamed the public sugar to `jetpackCompose()` and captured the collision-safe missing-method RED in CI #167.
- [x] Added generic `capability('compose')` parity contract and captured missing Android mutation RED in CI #168.

### Task 2: Pin Android Compose compiler tooling

- [x] Added Kotlin/Compose tooling version 2.2.10 to the build catalog.
- [x] Put the Compose Compiler Gradle plugin on `simpledsl-android` implementation classpath only.
- [x] Exported `kotlinVersion=2.2.10` through distribution metadata.
- [x] Added `org.jetbrains.kotlin.plugin.compose` to settings-owned plugin module/version maps.
- [x] Added explicit settings conflict test for a consumer-requested incompatible Compose Compiler version.
- [x] Extended backend isolation so Compose compiler tooling is absent from core/Java and required by Android.

### Task 3: Implement the minimal Android capability

- [x] Added `BuiltinAndroidCapabilities.COMPOSE` for `android-application` and `android-library`.
- [x] Capability binds `compose-runtime` and `compose-ui` and applies `org.jetbrains.kotlin.plugin.compose` through the existing core engine.
- [x] Added Android-only `ComposeCapabilityConfigurer` using public `ApplicationExtension` / `LibraryExtension` and `buildFeatures.compose = true`.
- [x] Registered Android built-in capabilities in `SimpleDslAndroidPlugin`.
- [x] Added `jetpackCompose()` sugar and generic `capability('compose')` with one shared Android backend-mutation path.
- [x] Kept the core capability engine unchanged; no callback/hook was added.
- [x] CI #169 completed successfully on production head `6f6ec7776a8f6c50c677333e08d5c245fa260e1a`.

### Task 4: Prove real published Compose consumers

- [x] Declared Compose BOM 2026.06.00 plus versionless `compose-runtime` / `compose-ui` aliases in the real Android consumer manifest.
- [x] Enabled `jetpackCompose()` in both published application and library fixtures without applying `org.jetbrains.kotlin.android` or declaring module-local Compose versions.
- [x] Added real Kotlin `@Composable` sources to both consumer modules.
- [x] Extended `simpledslCapabilities` proof to require `Features: compose`, `Platforms: compose`, and `Platform bindings: implementation:compose`.
- [x] Kept configuration-cache store/reuse verification and real app/library `assembleDebug`.
- [x] CI #170 completed successfully on exact head `bfd89c43118be5d9052ca4a42d2845a84375f704`, including the real published Compose consumer gate.

### Task 5: Exact-head closeout

- [x] Update README/CHANGELOG to document `jetpackCompose()`, manifest-managed Compose libraries, compiler ownership, and the Groovy name-collision rationale.
- [x] Correct the Compose 1.12 compatibility note from the earlier alpha-era AGP 9.2 assumption to the current stable API 37 / AGP 9.1.2+ requirement.
- [ ] Run the full repository CI on the documentation-closeout head and use that immutable head/run as the final merge gate.
- [ ] Verify PR #15 mergeability, reviews, and unresolved review threads on that same head.
- [ ] Update PR #15 body with the complete RED/GREEN evidence and final exact-head CI result.

The final CI run number is intentionally recorded in PR #15 rather than by another follow-up commit to this plan, so recording the evidence does not create a new unverified head.
