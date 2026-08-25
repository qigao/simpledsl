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

class BackendPolicyManifestTest {
    @TempDir
    Path tempDir

    @Test
    void exportsJavaPolicyInSchemaTwo() {
        Path manifest = write('dependencies.toml', '''
[simpledsl]
java = 25
''')

        Map snapshot = DependencyManifestLoader.load(manifest.toFile()).snapshot()

        assertEquals(2, snapshot.schemaVersion)
        assertEquals([toolchain: 25], snapshot.policies.java)
        assertFalse((snapshot.policies as Map).containsKey('android'))
        assertFalse(snapshot.containsKey('javaVersion'))
    }

    @Test
    void loadsAndroidOnlyPolicyWithoutJavaPolicy() {
        Path manifest = write('dependencies.toml', '''
[simpledsl.android]
java = 21
compile-sdk = 36
min-sdk = 24
''')

        Map snapshot = DependencyManifestLoader.load(manifest.toFile()).snapshot()

        assertEquals(2, snapshot.schemaVersion)
        assertFalse((snapshot.policies as Map).containsKey('java'))
        assertEquals([
                java: 21,
                compileSdk: 36,
                minSdk: 24
        ], snapshot.policies.android)
    }

    @Test
    void loadsIndependentJavaAndAndroidPolicies() {
        Path manifest = write('dependencies.yml', '''
simpledsl:
  java: 25
  android:
    java: 21
    compile-sdk: 36
    min-sdk: 24
    target-sdk: 36
''')

        Map snapshot = DependencyManifestLoader.load(manifest.toFile()).snapshot()

        assertEquals([toolchain: 25], snapshot.policies.java)
        assertEquals([
                java: 21,
                compileSdk: 36,
                minSdk: 24,
                targetSdk: 36
        ], snapshot.policies.android)
    }

    @Test
    void keepsTargetSdkOptionalForLibraryOnlyPolicy() {
        Path manifest = write('dependencies.yml', '''
simpledsl:
  android:
    java: 21
    compile-sdk: 36
    min-sdk: 24
''')

        Map android = DependencyManifestLoader.load(manifest.toFile()).snapshot().policies.android as Map

        assertEquals(21, android.java)
        assertEquals(36, android.compileSdk)
        assertEquals(24, android.minSdk)
        assertFalse(android.containsKey('targetSdk'))
    }

    @Test
    void rejectsInvalidAndroidSdkOrdering() {
        Path manifest = write('dependencies.toml', '''
[simpledsl.android]
java = 21
compile-sdk = 34
min-sdk = 24
target-sdk = 36
''')

        GradleException error = assertThrows(GradleException) {
            DependencyManifestLoader.load(manifest.toFile())
        }

        assertTrue(error.message.contains('SimpleDSL: simpledsl.android'))
        assertTrue(error.message.contains('target-sdk must be <= compile-sdk'))
    }

    @Test
    void rejectsUnknownAndroidPolicyKey() {
        Path manifest = write('dependencies.toml', '''
[simpledsl.android]
java = 21
compile-sdk = 36
min-sdk = 24
namespace = "com.example"
''')

        GradleException error = assertThrows(GradleException) {
            DependencyManifestLoader.load(manifest.toFile())
        }

        assertTrue(error.message.contains('SimpleDSL: simpledsl.android'))
        assertTrue(error.message.contains('unsupported key(s): namespace'))
    }

    private Path write(String name, String text) {
        Path path = tempDir.resolve(name)
        Files.writeString(path, text.stripIndent())
        path
    }
}
