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
        Files.createDirectories(projectDir.resolve('gradle/simpledsl'))
        Files.createDirectories(projectDir.resolve('app'))
        Files.writeString(projectDir.resolve('settings.gradle'), '''
plugins { id 'io.github.qigao.simpledsl.settings' }
rootProject.name = 'consumer'
'''.stripIndent())
        Files.writeString(projectDir.resolve('gradle/simpledsl/dependencies.toml'), '''
[java]
version = 25
'''.stripIndent())
        Files.writeString(projectDir.resolve('app/build.gradle'), '')

        def result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments('simpledslDependencies', 'simpledslProjects', '--stacktrace')
                .withPluginClasspath()
                .build()

        assertTrue(result.output.contains('Java: 25'))
        assertTrue(result.output.contains(':app | app | auto | build.gradle'))
    }
}
