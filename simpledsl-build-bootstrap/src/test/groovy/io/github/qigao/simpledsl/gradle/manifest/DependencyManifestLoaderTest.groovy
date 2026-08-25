package io.github.qigao.simpledsl.gradle.manifest

import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class DependencyManifestLoaderTest {
    @TempDir
    Path tempDir

    @Test
    void loadsGradleShapedTomlAndDerivesPlatform() {
        Path manifest = tempDir.resolve('dependencies.toml')
        Files.writeString(manifest, '''
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
'''.stripIndent())

        DependencyRegistry registry = DependencyManifestLoader.load(manifest.toFile())

        assertEquals(25, registry.javaVersion())
        assertEquals(
                'org.springframework.boot:spring-boot-dependencies:4.1.0',
                registry.platform('spring-bom').coordinate())
        assertEquals(
                'org.springframework.boot:spring-boot-dependencies:4.1.0',
                registry.library('spring-bom').notation())
        assertEquals('spring-bom', registry.library('spring-web').platform)
        assertEquals('4.1.0', registry.plugin('spring-boot').version)
        assertTrue(registry.snapshot().platforms.containsKey('spring-bom'))
        assertTrue(registry.snapshot().libraries.containsKey('spring-bom'))
        assertEquals(1, registry.snapshot().schemaVersion)
    }

    @Test
    void loadsEquivalentTomlAndYamlManifests() {
        Path tomlDir = Files.createDirectories(tempDir.resolve('toml'))
        Path yamlDir = Files.createDirectories(tempDir.resolve('yaml'))
        Path toml = tomlDir.resolve('dependencies.toml')
        Path yaml = yamlDir.resolve('dependencies.yml')

        Files.writeString(toml, '''
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
version.ref = "spring-boot"
'''.stripIndent())

        Files.writeString(yaml, '''
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
    version:
      ref: spring-boot
'''.stripIndent())

        assertEquals(
                DependencyManifestLoader.load(toml.toFile()).snapshot(),
                DependencyManifestLoader.load(yaml.toFile()).snapshot())
    }

    @Test
    void loadsMixedFormatIncludesRelativeToDeclaringFile() {
        Path parts = Files.createDirectories(tempDir.resolve('parts'))
        Files.writeString(tempDir.resolve('dependencies.toml'), '''
include = ["parts/spring.yml"]

[simpledsl]
java = 25
'''.stripIndent())
        Files.writeString(parts.resolve('spring.yml'), '''
include:
  - test.toml
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
'''.stripIndent())
        Files.writeString(parts.resolve('test.toml'), '''
[versions]
junit = "5.13.4"

[libraries.junit]
module = "org.junit.jupiter:junit-jupiter"
version.ref = "junit"
'''.stripIndent())

        DependencyRegistry registry = DependencyManifestLoader.load(tempDir.resolve('dependencies.toml').toFile())

        assertEquals('4.1.0', registry.version('spring-boot').value)
        assertEquals('5.13.4', registry.version('junit').value)
        assertEquals('spring-bom', registry.library('spring-web').platform)
        assertEquals('org.junit.jupiter:junit-jupiter:5.13.4', registry.library('junit').notation())
    }

    @Test
    void detectsIncludeCycleAcrossFormats() {
        Files.writeString(tempDir.resolve('a.toml'), '''
include = ["b.yml"]
[simpledsl]
java = 25
'''.stripIndent())
        Files.writeString(tempDir.resolve('b.yml'), '''
include:
  - a.toml
'''.stripIndent())

        GradleException error = assertThrows(GradleException) {
            DependencyManifestLoader.load(tempDir.resolve('a.toml').toFile())
        }

        assertTrue(error.message.contains('include cycle detected'))
        assertTrue(error.message.contains('a.toml'))
        assertTrue(error.message.contains('b.yml'))
    }

    @Test
    void rejectsDuplicateAliasAcrossFormats() {
        Files.writeString(tempDir.resolve('dependencies.toml'), '''
include = ["versions.yml"]
[simpledsl]
java = 25
[versions]
shared = "1.0"
'''.stripIndent())
        Files.writeString(tempDir.resolve('versions.yml'), '''
versions:
  shared: "2.0"
'''.stripIndent())

        GradleException error = assertThrows(GradleException) {
            DependencyManifestLoader.load(tempDir.resolve('dependencies.toml').toFile())
        }

        assertTrue(error.message.contains("duplicate version id 'shared'"))
    }

    @Test
    void rejectsLegacyJavaTable() {
        Path manifest = tempDir.resolve('dependencies.toml')
        Files.writeString(manifest, '''
[java]
version = 25
'''.stripIndent())

        GradleException error = assertThrows(GradleException) {
            DependencyManifestLoader.load(manifest.toFile())
        }

        assertTrue(error.message.contains('unsupported key(s): java'))
    }

    @Test
    void rejectsPublicPlatformsTable() {
        Path manifest = tempDir.resolve('dependencies.toml')
        Files.writeString(manifest, '''
[java]
version = 25

[platforms.spring]
module = "org.springframework.boot:spring-boot-dependencies"
version = "4.1.0"
'''.stripIndent())

        GradleException error = assertThrows(GradleException) {
            DependencyManifestLoader.load(manifest.toFile())
        }

        assertTrue(error.message.contains('platforms'))
        assertTrue(error.message.contains('unsupported key(s)'))
    }

    @Test
    void rejectsUnknownPlatformLibraryAlias() {
        Path manifest = tempDir.resolve('dependencies.toml')
        Files.writeString(manifest, '''
[simpledsl]
java = 25

[libraries.spring-web]
module = "org.springframework.boot:spring-boot-starter-web"
platform = "missing-bom"
'''.stripIndent())

        GradleException error = assertThrows(GradleException) {
            DependencyManifestLoader.load(manifest.toFile())
        }

        assertTrue(error.message.contains("unknown platform 'missing-bom'"))
    }

    @Test
    void rejectsPlatformOwnerWithoutVersion() {
        Path manifest = tempDir.resolve('dependencies.toml')
        Files.writeString(manifest, '''
[simpledsl]
java = 25

[libraries.spring-bom]
module = "org.springframework.boot:spring-boot-dependencies"

[libraries.spring-web]
module = "org.springframework.boot:spring-boot-starter-web"
platform = "spring-bom"
'''.stripIndent())

        GradleException error = assertThrows(GradleException) {
            DependencyManifestLoader.load(manifest.toFile())
        }

        assertTrue(error.message.contains('Library: spring-bom'))
        assertTrue(error.message.contains('exactly one version owner is required'))
    }

    @Test
    void rejectsPlatformOwnerThatIsPlatformOwned() {
        Path manifest = tempDir.resolve('dependencies.toml')
        Files.writeString(manifest, '''
[simpledsl]
java = 25

[versions]
v = "1.0"

[libraries.base-bom]
module = "example:base-bom"
version.ref = "v"

[libraries.nested-bom]
module = "example:nested-bom"
platform = "base-bom"

[libraries.app]
module = "example:app"
platform = "nested-bom"
'''.stripIndent())

        GradleException error = assertThrows(GradleException) {
            DependencyManifestLoader.load(manifest.toFile())
        }

        assertTrue(error.message.contains("platform library 'nested-bom' cannot itself use platform"))
    }

    @Test
    void rejectsUnsupportedManifestExtension() {
        Path manifest = tempDir.resolve('dependencies.json')
        Files.writeString(manifest, '''
[java]
version = 25
'''.stripIndent())

        GradleException error = assertThrows(GradleException) {
            DependencyManifestLoader.load(manifest.toFile())
        }

        assertTrue(error.message.contains('unsupported manifest extension'))
        assertTrue(error.message.contains('.toml'))
        assertTrue(error.message.contains('.yml'))
        assertTrue(error.message.contains('.yaml'))
    }
}
