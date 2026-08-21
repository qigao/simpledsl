# SimpleDSL Java 21 Plugin Baseline

Date: 2026-08-21
Status: Accepted implementation correction

SimpleDSL Gradle plugin artifacts require Java 21 or newer at plugin runtime and target Java 21 bytecode.

This supersedes the Java 17 plugin-bytecode statements in the original extraction spec and implementation plan. CI continues to run on Java 25 and Gradle 9.1.0.

## Reason

SimpleDSL owns `org.jooq:jooq-codegen-gradle:3.21.5` as an implementation dependency. The Open Source jOOQ 3.21 distribution is a Java 21+ distribution, and Gradle variant matching rejects that artifact from a plugin project declaring Java 17 compatibility.

Trying to preserve Java 17 only for the SimpleDSL artifact would therefore be misleading: the artifact could not load its owned jOOQ plugin implementation on a Java 17 Gradle runtime anyway.

## Contract

- SimpleDSL plugin runtime minimum: Java 21.
- SimpleDSL plugin artifact bytecode target: Java 21.
- CI build JDK: Java 25.
- Gradle baseline: 9.1.0.
- Consumer project Java toolchains remain controlled by the consumer dependency snapshot. SimpleDSL may configure a consumer project for Java 21, 25, or another supported value independently of the Java version used to run the plugin.
- `simpledsl-build-bootstrap` and `simpledsl-build-logic` use the same Java 21 plugin baseline.

## Source

jOOQ JDK support matrix: https://www.jooq.org/download/support-matrix-jdk
