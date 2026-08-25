package io.github.qigao.simpledsl.gradle.settings

import io.github.qigao.simpledsl.gradle.distribution.SimpleDslDistribution
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

class SimpleDslSettingsPluginTest {
    @TempDir
    Path projectDir

    @Test
    void exposesDependencyAndProjectDiagnosticsFromRootToml() {
        writeRootManifest('dependencies.toml', '''
[simpledsl]
java = 25
''')
        Files.createDirectories(projectDir.resolve('app'))
        Files.writeString(projectDir.resolve('app/build.gradle'), '')
        writeSettings()

        def result = runner('simpledslDependencies', 'simpledslProjects').build()

        assertTrue(result.output.contains('Java: 25'))
        assertTrue(result.output.contains(':app | app | auto | build.gradle'))
    }

    @Test
    void discoversRootYamlManifest() {
        writeRootManifest('dependencies.yml', '''
simpledsl:
  java: 25
''')
        writeSettings()

        def result = runner('simpledslDependencies').build()

        assertTrue(result.output.contains('Java: 25'))
    }

    @Test
    void rejectsAmbiguousRootManifests() {
        writeRootManifest('dependencies.toml', '''
[simpledsl]
java = 25
''')
        writeRootManifest('dependencies.yml', '''
simpledsl:
  java: 25
''')
        writeSettings()

        def result = runner('simpledslDependencies').buildAndFail()

        assertTrue(result.output.contains('ambiguous dependency manifest'))
        assertTrue(result.output.contains('dependencies.toml'))
        assertTrue(result.output.contains('dependencies.yml'))
    }

    @Test
    void usesExplicitDependencyManifestOverride() {
        Files.createDirectories(projectDir.resolve('config'))
        Files.writeString(projectDir.resolve('config/deps.toml'), '''
[simpledsl]
java = 25
'''.stripIndent())
        writeSettings('''
simpledslSettings {
    dependencyManifest.set(layout.settingsDirectory.file('config/deps.toml'))
}
''')

        def result = runner('simpledslDependencies').build()

        assertTrue(result.output.contains('Java: 25'))
    }

    @Test
    void doesNotFallbackToLegacyGradleSimpleDslManifest() {
        Files.createDirectories(projectDir.resolve('gradle/simpledsl'))
        Files.writeString(projectDir.resolve('gradle/simpledsl/dependencies.toml'), '''
[java]
version = 25
'''.stripIndent())
        writeSettings()

        def result = runner('simpledslDependencies').buildAndFail()

        assertTrue(result.output.contains('dependency manifest'))
        assertTrue(result.output.contains('dependencies.toml'))
        assertTrue(result.output.contains('dependencies.yml'))
        assertTrue(result.output.contains('dependencies.yaml'))
    }

    @Test
    void rejectsMismatchedSimpleDslBuildPluginVersion() {
        writeRootManifest('dependencies.toml', '''
[simpledsl]
java = 25
''')
        Files.createDirectories(projectDir.resolve('app'))
        Files.writeString(projectDir.resolve('app/build.gradle'), '''
plugins {
    id 'io.github.qigao.simpledsl.build' version '9.9.9'
}
'''.stripIndent())
        writeSettings()

        def result = runner('help').buildAndFail()
        String managedVersion = SimpleDslDistribution.version()

        assertTrue(result.output.contains('SimpleDSL version conflict'))
        assertTrue(result.output.contains('Plugin: io.github.qigao.simpledsl.build'))
        assertTrue(result.output.contains('Requested: 9.9.9'))
        assertTrue(result.output.contains("Managed: ${managedVersion}"))
    }

    @Test
    void doesNotTreatCapabilityNamesAsSimpleDslPlugins() {
        writeRootManifest('dependencies.toml', '''
[simpledsl]
java = 25
''')
        Files.createDirectories(projectDir.resolve('app'))
        Files.writeString(projectDir.resolve('app/build.gradle'), '''
plugins {
    id 'io.github.qigao.simpledsl.feature.web' version '9.9.9'
}
'''.stripIndent())
        writeSettings()

        def result = runner('help').buildAndFail()

        assertFalse(result.output.contains('SimpleDSL version conflict'))
        assertTrue(result.output.contains('io.github.qigao.simpledsl.feature.web'))
    }

    @Test
    void rejectsConsumerOverrideOfOwnedExternalPluginVersion() {
        writeRootManifest('dependencies.toml', '''
[simpledsl]
java = 25

[plugins.spring-boot]
id = "org.springframework.boot"
module = "org.springframework.boot:spring-boot-gradle-plugin"
version = "9.9.9"
''')
        Files.createDirectories(projectDir.resolve('app'))
        Files.writeString(projectDir.resolve('app/build.gradle'), '')
        writeSettings()

        def result = runner('simpledslDependencies').buildAndFail()

        assertTrue(result.output.contains('SimpleDSL plugin compatibility error'))
        assertTrue(result.output.contains('Plugin: org.springframework.boot'))
        assertTrue(result.output.contains('Requested: 9.9.9'))
        assertTrue(result.output.contains('Managed: 4.1.0'))
    }

    private void writeRootManifest(String name, String manifest) {
        Files.createDirectories(projectDir)
        Files.writeString(projectDir.resolve(name), manifest.stripIndent())
    }

    private void writeSettings(String extra = '') {
        Files.writeString(projectDir.resolve('settings.gradle'), ('''
plugins { id 'io.github.qigao.simpledsl.settings' }
rootProject.name = 'consumer'
''' + extra).stripIndent())
    }

    private GradleRunner runner(String... arguments) {
        GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments((arguments as List<String>) + ['--stacktrace'])
                .withPluginClasspath()
    }
}
