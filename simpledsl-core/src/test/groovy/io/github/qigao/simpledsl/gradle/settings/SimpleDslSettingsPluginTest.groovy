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
    void acceptsDependencyOnlyManifestWithoutJavaPolicy() {
        writeRootManifest('dependencies.toml', '''
[versions]
junit = "6.0.1"

[libraries.junit]
module = "org.junit.jupiter:junit-jupiter"
version.ref = "junit"
''')
        writeSettings()

        def result = runner('simpledslDependencies').build()

        assertTrue(result.output.contains('Java: not configured'))
        assertTrue(result.output.contains('junit -> org.junit.jupiter:junit-jupiter:6.0.1'))
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
    void mapsUnversionedJavaBackendToManagedCoordinate() {
        writeRootManifest('dependencies.toml', '''
[simpledsl]
java = 25
''')
        Files.createDirectories(projectDir.resolve('app'))
        Files.writeString(projectDir.resolve('app/build.gradle'), '''
plugins {
    id 'io.github.qigao.simpledsl.java'
}
'''.stripIndent())
        writeSettings()

        def result = runner('help').buildAndFail()

        assertTrue(result.output.contains(SimpleDslDistribution.javaCoordinate()))
        assertFalse(result.output.contains('SimpleDSL version conflict'))
    }

    @Test
    void mapsUnversionedAndroidBackendToManagedCoordinate() {
        writeAndroidManifest()
        Files.createDirectories(projectDir.resolve('app'))
        Files.writeString(projectDir.resolve('app/build.gradle'), '''
plugins {
    id 'io.github.qigao.simpledsl.android'
}
'''.stripIndent())
        writeSettings()

        def result = runner('help').buildAndFail()

        assertTrue(result.output.contains(SimpleDslDistribution.androidCoordinate()))
        assertFalse(result.output.contains('SimpleDSL version conflict'))
    }

    @Test
    void rejectsMismatchedSimpleDslJavaPluginVersion() {
        writeRootManifest('dependencies.toml', '''
[simpledsl]
java = 25
''')
        Files.createDirectories(projectDir.resolve('app'))
        Files.writeString(projectDir.resolve('app/build.gradle'), '''
plugins {
    id 'io.github.qigao.simpledsl.java' version '9.9.9'
}
'''.stripIndent())
        writeSettings()

        def result = runner('help').buildAndFail()
        String managedVersion = SimpleDslDistribution.version()

        assertTrue(result.output.contains('SimpleDSL version conflict'))
        assertTrue(result.output.contains('Plugin: io.github.qigao.simpledsl.java'))
        assertTrue(result.output.contains('Requested: 9.9.9'))
        assertTrue(result.output.contains("Managed: ${managedVersion}"))
    }

    @Test
    void rejectsMismatchedSimpleDslAndroidPluginVersion() {
        writeAndroidManifest()
        Files.createDirectories(projectDir.resolve('app'))
        Files.writeString(projectDir.resolve('app/build.gradle'), '''
plugins {
    id 'io.github.qigao.simpledsl.android' version '9.9.9'
}
'''.stripIndent())
        writeSettings()

        def result = runner('help').buildAndFail()
        String managedVersion = SimpleDslDistribution.version()

        assertTrue(result.output.contains('SimpleDSL version conflict'))
        assertTrue(result.output.contains('Plugin: io.github.qigao.simpledsl.android'))
        assertTrue(result.output.contains('Requested: 9.9.9'))
        assertTrue(result.output.contains("Managed: ${managedVersion}"))
    }

    @Test
    void mapsUnversionedAndroidApplicationPluginToPinnedAgp() {
        writeAndroidManifest()
        Files.createDirectories(projectDir.resolve('app'))
        Files.writeString(projectDir.resolve('app/build.gradle'), '''
plugins {
    id 'com.android.application'
}
'''.stripIndent())
        writeSettings()

        def result = runner('help').buildAndFail()

        assertTrue(result.output.contains('com.android.tools.build:gradle:9.0.1'))
        assertFalse(result.output.contains('SimpleDSL plugin compatibility error'))
    }

    @Test
    void mapsUnversionedAndroidLibraryPluginToPinnedAgp() {
        writeAndroidManifest()
        Files.createDirectories(projectDir.resolve('feature'))
        Files.writeString(projectDir.resolve('feature/build.gradle'), '''
plugins {
    id 'com.android.library'
}
'''.stripIndent())
        writeSettings()

        def result = runner('help').buildAndFail()

        assertTrue(result.output.contains('com.android.tools.build:gradle:9.0.1'))
        assertFalse(result.output.contains('SimpleDSL plugin compatibility error'))
    }

    @Test
    void rejectsConsumerOverrideOfPinnedAgpVersion() {
        writeAndroidManifest()
        Files.createDirectories(projectDir.resolve('app'))
        Files.writeString(projectDir.resolve('app/build.gradle'), '''
plugins {
    id 'com.android.application' version '9.0.2'
}
'''.stripIndent())
        writeSettings()

        def result = runner('help').buildAndFail()

        assertTrue(result.output.contains('SimpleDSL plugin compatibility error'))
        assertTrue(result.output.contains('Plugin: com.android.application'))
        assertTrue(result.output.contains('Requested: 9.0.2'))
        assertTrue(result.output.contains('Managed: 9.0.1'))
    }

    @Test
    void rejectsConsumerOverrideOfPinnedComposeCompilerVersion() {
        writeAndroidManifest()
        Files.createDirectories(projectDir.resolve('app'))
        Files.writeString(projectDir.resolve('app/build.gradle'), '''
plugins {
    id 'org.jetbrains.kotlin.plugin.compose' version '9.9.9'
}
'''.stripIndent())
        writeSettings()

        def result = runner('help').buildAndFail()

        assertTrue(result.output.contains('SimpleDSL plugin compatibility error'))
        assertTrue(result.output.contains('Plugin: org.jetbrains.kotlin.plugin.compose'))
        assertTrue(result.output.contains('Requested: 9.9.9'))
        assertTrue(result.output.contains('Managed: 2.2.10'))
    }

    @Test
    void rejectsRemovedBuildPluginWithMigrationGuidance() {
        writeRootManifest('dependencies.toml', '''
[simpledsl]
java = 25
''')
        Files.createDirectories(projectDir.resolve('app'))
        Files.writeString(projectDir.resolve('app/build.gradle'), '''
plugins {
    id 'io.github.qigao.simpledsl.build'
}
'''.stripIndent())
        writeSettings()

        def result = runner('help').buildAndFail()

        assertTrue(result.output.contains('SimpleDSL plugin migration required'))
        assertTrue(result.output.contains('Plugin: io.github.qigao.simpledsl.build'))
        assertTrue(result.output.contains('Replacement: io.github.qigao.simpledsl.java'))
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

    private void writeAndroidManifest() {
        writeRootManifest('dependencies.toml', '''
[simpledsl.android]
java = 21
compile-sdk = 36
min-sdk = 24
target-sdk = 36
''')
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
