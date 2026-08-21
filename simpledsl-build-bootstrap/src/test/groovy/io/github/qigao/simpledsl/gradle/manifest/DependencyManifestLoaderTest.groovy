package io.github.qigao.simpledsl.gradle.manifest

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertEquals

class DependencyManifestLoaderTest {
    @TempDir
    Path tempDir

    @Test
    void loadsIncludedManifest() {
        Files.writeString(tempDir.resolve('dependencies.toml'), '''
include = ["spring.toml"]
[java]
version = 25
'''.stripIndent())
        Files.writeString(tempDir.resolve('spring.toml'), '''
[versions]
spring-boot = "4.1.0"

[platforms.spring]
module = "org.springframework.boot:spring-boot-dependencies"
version.ref = "spring-boot"

[libraries.spring-core]
module = "org.springframework.boot:spring-boot-starter"
platform = "spring"
'''.stripIndent())

        DependencyRegistry registry = DependencyManifestLoader.load(tempDir.resolve('dependencies.toml').toFile())

        assertEquals(25, registry.javaVersion())
        assertEquals('org.springframework.boot:spring-boot-dependencies', registry.platform('spring').module)
        assertEquals('org.springframework.boot:spring-boot-starter', registry.library('spring-core').module)
        assertEquals(1, registry.snapshot().schemaVersion)
    }
}
