package io.github.qigao.simpledsl.gradle.settings

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertTrue

class AndroidOnlySettingsPolicyTest {
    @TempDir
    Path projectDir

    @Test
    void configuresSettingsWithoutJavaPolicy() {
        Files.writeString(projectDir.resolve('dependencies.toml'), '''
[simpledsl.android]
java = 21
compile-sdk = 36
min-sdk = 24
'''.stripIndent())
        Files.writeString(projectDir.resolve('settings.gradle'), '''
plugins { id 'io.github.qigao.simpledsl.settings' }
rootProject.name = 'android-only'
'''.stripIndent())

        def result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments('simpledslDependencies', '--stacktrace')
                .withPluginClasspath()
                .build()

        assertTrue(result.output.contains('SimpleDSL Dependencies'))
        assertTrue(result.output.contains('Java: <not configured>'))
    }
}
