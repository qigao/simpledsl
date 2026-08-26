package io.github.qigao.simpledsl.gradle.settings

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertTrue

class DynamicFeaturePluginVersionTest {
    @TempDir
    Path projectDir

    @Test
    void rejectsConsumerOverrideOfPinnedDynamicFeatureAgpVersion() {
        Files.createDirectories(projectDir)
        Files.writeString(projectDir.resolve('dependencies.toml'), '''
[simpledsl.android]
java = 21
compile-sdk = 36
min-sdk = 24
target-sdk = 36
'''.stripIndent())

        Files.writeString(projectDir.resolve('settings.gradle'), '''
plugins { id 'io.github.qigao.simpledsl.settings' }
rootProject.name = 'consumer'
include 'payments'
'''.stripIndent())

        Path payments = Files.createDirectories(projectDir.resolve('payments'))
        Files.writeString(payments.resolve('build.gradle'), '''
plugins {
    id 'com.android.dynamic-feature' version '9.9.9'
}
'''.stripIndent())

        def result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments('help', '--stacktrace')
                .withPluginClasspath()
                .buildAndFail()

        assertTrue(result.output.contains('SimpleDSL plugin compatibility error'))
        assertTrue(result.output.contains('Plugin: com.android.dynamic-feature'))
        assertTrue(result.output.contains('Requested: 9.9.9'))
        assertTrue(result.output.contains('Managed: 9.0.1'))
    }
}
