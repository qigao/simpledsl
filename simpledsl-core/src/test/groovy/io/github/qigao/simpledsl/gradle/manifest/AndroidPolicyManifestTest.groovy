package io.github.qigao.simpledsl.gradle.manifest

import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class AndroidPolicyManifestTest {
    @TempDir
    Path tempDir

    @Test
    void exportsAndroidApplicationPolicyInSchemaV2() {
        Path manifest = write('dependencies.toml', '''
[simpledsl.android]
java = 21
compile-sdk = 36
min-sdk = 24
target-sdk = 36
''')

        Map snapshot = DependencyManifestLoader.load(manifest.toFile()).snapshot()

        assertEquals(2, snapshot.schemaVersion)
        assertEquals([
                java: 21,
                compileSdk: 36,
                minSdk: 24,
                targetSdk: 36
        ], snapshot.policies.android)
        assertFalse(snapshot.policies.containsKey('java'))
    }

    @Test
    void allowsAndroidLibraryPolicyWithoutTargetSdk() {
        Path manifest = write('dependencies.toml', '''
[simpledsl.android]
java = 21
compile-sdk = 36
min-sdk = 24
''')

        Map android = DependencyManifestLoader.load(manifest.toFile()).snapshot().policies.android as Map

        assertEquals([java: 21, compileSdk: 36, minSdk: 24], android)
        assertFalse(android.containsKey('targetSdk'))
    }

    @Test
    void tomlAndYamlAndroidPoliciesAreEquivalent() {
        Path tomlDir = Files.createDirectories(tempDir.resolve('toml'))
        Path yamlDir = Files.createDirectories(tempDir.resolve('yaml'))
        Path toml = tomlDir.resolve('dependencies.toml')
        Path yaml = yamlDir.resolve('dependencies.yml')
        Files.writeString(toml, '''
[simpledsl]
java = 25

[simpledsl.android]
java = 21
compile-sdk = 36
min-sdk = 24
target-sdk = 36
'''.stripIndent())
        Files.writeString(yaml, '''
simpledsl:
  java: 25
  android:
    java: 21
    compile-sdk: 36
    min-sdk: 24
    target-sdk: 36
'''.stripIndent())

        assertEquals(
                DependencyManifestLoader.load(toml.toFile()).snapshot(),
                DependencyManifestLoader.load(yaml.toFile()).snapshot())
    }

    @Test
    void rejectsDuplicateAndroidPolicyAcrossIncludes() {
        write('dependencies.toml', '''
include = ["android.yml"]

[simpledsl.android]
java = 21
compile-sdk = 36
min-sdk = 24
''')
        write('android.yml', '''
simpledsl:
  android:
    java: 21
    compile-sdk: 36
    min-sdk: 24
''')

        GradleException error = assertThrows(GradleException) {
            DependencyManifestLoader.load(tempDir.resolve('dependencies.toml').toFile())
        }

        assertTrue(error.message.contains('duplicate simpledsl.android'))
    }

    @Test
    void rejectsUnknownAndroidPolicyKey() {
        Path manifest = write('dependencies.toml', '''
[simpledsl.android]
java = 21
compile-sdk = 36
min-sdk = 24
build-tools = 36
''')

        GradleException error = assertThrows(GradleException) {
            DependencyManifestLoader.load(manifest.toFile())
        }

        assertTrue(error.message.contains('unsupported key(s): build-tools'))
    }

    @Test
    void rejectsNonPositiveAndroidPolicyValues() {
        ['java', 'compile-sdk', 'min-sdk', 'target-sdk'].each { key ->
            Path dir = Files.createDirectories(tempDir.resolve(key))
            Path manifest = dir.resolve('dependencies.toml')
            Files.writeString(manifest, """
[simpledsl.android]
java = ${key == 'java' ? 0 : 21}
compile-sdk = ${key == 'compile-sdk' ? 0 : 36}
min-sdk = ${key == 'min-sdk' ? 0 : 24}
target-sdk = ${key == 'target-sdk' ? 0 : 36}
""".stripIndent())

            GradleException error = assertThrows(GradleException) {
                DependencyManifestLoader.load(manifest.toFile())
            }
            assertTrue(error.message.contains("${key} must be a positive integer"), key)
        }
    }

    @Test
    void rejectsInvalidAndroidSdkOrdering() {
        [
                [compile: 23, min: 24, target: 23, problem: 'min-sdk must be <= compile-sdk'],
                [compile: 36, min: 24, target: 23, problem: 'target-sdk must be >= min-sdk'],
                [compile: 36, min: 24, target: 37, problem: 'target-sdk must be <= compile-sdk']
        ].eachWithIndex { values, index ->
            Path dir = Files.createDirectories(tempDir.resolve("case-${index}"))
            Path manifest = dir.resolve('dependencies.toml')
            Files.writeString(manifest, """
[simpledsl.android]
java = 21
compile-sdk = ${values.compile}
min-sdk = ${values.min}
target-sdk = ${values.target}
""".stripIndent())

            GradleException error = assertThrows(GradleException) {
                DependencyManifestLoader.load(manifest.toFile())
            }
            assertTrue(error.message.contains(values.problem as String), values.problem as String)
        }
    }

    private Path write(String name, String content) {
        Path path = tempDir.resolve(name)
        Files.writeString(path, content.stripIndent())
        path
    }
}
