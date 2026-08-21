package io.github.qigao.simpledsl.gradle.settings

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertTrue

class SimpleDslSettingsPluginTest {
    @TempDir
    Path projectDir

    @Test
    void exposesDependencyAndProjectDiagnostics() {
        writeBaseConsumer('''
[java]
version = 25
''')
        Files.writeString(projectDir.resolve('app/build.gradle'), '')

        def result = runner('simpledslDependencies', 'simpledslProjects').build()

        assertTrue(result.output.contains('Java: 25'))
        assertTrue(result.output.contains(':app | app | auto | build.gradle'))
    }

    @Test
    void rejectsMismatchedSimpleDslPluginVersion() {
        writeBaseConsumer('''
[java]
version = 25
''')
        Files.writeString(projectDir.resolve('app/build.gradle'), '''
plugins {
    id 'io.github.qigao.simpledsl.module' version '9.9.9'
}
'''.stripIndent())

        def result = runner('help').buildAndFail()

        assertTrue(result.output.contains('SimpleDSL version conflict'))
        assertTrue(result.output.contains('Plugin: io.github.qigao.simpledsl.module'))
        assertTrue(result.output.contains('Requested: 9.9.9'))
        assertTrue(result.output.contains('Managed: 0.1.0-SNAPSHOT'))
    }

    @Test
    void rejectsConsumerOverrideOfOwnedExternalPluginVersion() {
        writeBaseConsumer('''
[java]
version = 25

[plugins.spring-boot]
id = "org.springframework.boot"
module = "org.springframework.boot:spring-boot-gradle-plugin"
version = "9.9.9"
''')
        Files.writeString(projectDir.resolve('app/build.gradle'), '')

        def result = runner('simpledslDependencies').buildAndFail()

        assertTrue(result.output.contains('SimpleDSL plugin compatibility error'))
        assertTrue(result.output.contains('Plugin: org.springframework.boot'))
        assertTrue(result.output.contains('Requested: 9.9.9'))
        assertTrue(result.output.contains('Managed: 4.1.0'))
    }

    private void writeBaseConsumer(String manifest) {
        Files.createDirectories(projectDir.resolve('gradle/simpledsl'))
        Files.createDirectories(projectDir.resolve('app'))
        Files.writeString(projectDir.resolve('settings.gradle'), '''
plugins { id 'io.github.qigao.simpledsl.settings' }
rootProject.name = 'consumer'
'''.stripIndent())
        Files.writeString(projectDir.resolve('gradle/simpledsl/dependencies.toml'), manifest.stripIndent())
    }

    private GradleRunner runner(String... arguments) {
        GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments((arguments as List<String>) + ['--stacktrace'])
                .withPluginClasspath()
    }
}
