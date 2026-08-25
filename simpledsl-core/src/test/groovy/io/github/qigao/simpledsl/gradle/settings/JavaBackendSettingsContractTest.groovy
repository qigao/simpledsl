package io.github.qigao.simpledsl.gradle.settings

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertTrue

class JavaBackendSettingsContractTest {
    @TempDir
    Path projectDir

    @Test
    void rejectsMismatchedSimpleDslJavaBackendVersion() {
        writeProject('''
plugins {
    id 'io.github.qigao.simpledsl.java' version '9.9.9'
}
''')

        def result = runner('help').buildAndFail()

        assertTrue(result.output.contains('SimpleDSL version conflict'))
        assertTrue(result.output.contains('Plugin: io.github.qigao.simpledsl.java'))
        assertTrue(result.output.contains('Requested: 9.9.9'))
    }

    @Test
    void rejectsRemovedBuildPluginWithMigrationGuidance() {
        writeProject('''
plugins {
    id 'io.github.qigao.simpledsl.build'
}
''')

        def result = runner('help').buildAndFail()

        assertTrue(result.output.contains('io.github.qigao.simpledsl.build was removed in SimpleDSL 0.3.0'))
        assertTrue(result.output.contains('Use io.github.qigao.simpledsl.java'))
    }

    private void writeProject(String buildScript) {
        Files.createDirectories(projectDir.resolve('app'))
        Files.writeString(projectDir.resolve('dependencies.toml'), '''
[simpledsl]
java = 25
'''.stripIndent())
        Files.writeString(projectDir.resolve('settings.gradle'), '''
plugins { id 'io.github.qigao.simpledsl.settings' }
rootProject.name = 'java-backend-contract'
include ':app'
'''.stripIndent())
        Files.writeString(projectDir.resolve('app/build.gradle'), buildScript.stripIndent())
    }

    private GradleRunner runner(String... arguments) {
        GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments((arguments as List<String>) + ['--stacktrace'])
                .withPluginClasspath()
    }
}
