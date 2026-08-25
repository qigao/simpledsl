package io.github.qigao.simpledsl.gradle.android

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertTrue

class AndroidModuleConfigurationTest {
    @TempDir
    Path projectDir

    @Test
    void configuresAndroidApplicationFromRepositoryPolicy() {
        writeManifest(true)
        writeSettings()
        writeModule('app', '''
plugins {
    id 'io.github.qigao.simpledsl.android'
}

simpledsl {
    androidApplication {
        namespace = 'example.app'
    }
}

def androidDsl = extensions.getByName('android')
assert pluginManager.hasPlugin('com.android.application')
assert !pluginManager.hasPlugin('org.jetbrains.kotlin.android')
assert androidDsl.namespace == 'example.app'
assert androidDsl.compileSdk == 36
assert androidDsl.defaultConfig.minSdk == 24
assert androidDsl.defaultConfig.targetSdk == 36
assert androidDsl.defaultConfig.applicationId == 'example.app'
assert androidDsl.compileOptions.sourceCompatibility == JavaVersion.VERSION_21
assert androidDsl.compileOptions.targetCompatibility == JavaVersion.VERSION_21
''')

        def result = runner(':app:help').build()

        assertTrue(result.output.contains('BUILD SUCCESSFUL'))
    }

    @Test
    void configuresAndroidLibraryWithoutTargetSdkPolicy() {
        writeManifest(false)
        writeSettings()
        writeModule('feature', '''
plugins {
    id 'io.github.qigao.simpledsl.android'
}

simpledsl {
    androidLibrary {
        namespace = 'example.feature'
    }
}

def androidDsl = extensions.getByName('android')
assert pluginManager.hasPlugin('com.android.library')
assert !pluginManager.hasPlugin('org.jetbrains.kotlin.android')
assert androidDsl.namespace == 'example.feature'
assert androidDsl.compileSdk == 36
assert androidDsl.defaultConfig.minSdk == 24
assert androidDsl.compileOptions.sourceCompatibility == JavaVersion.VERSION_21
assert androidDsl.compileOptions.targetCompatibility == JavaVersion.VERSION_21
''')

        def result = runner(':feature:help').build()

        assertTrue(result.output.contains('BUILD SUCCESSFUL'))
    }

    @Test
    void rejectsApplicationPolicyWithoutTargetSdk() {
        writeManifest(false)
        writeSettings()
        writeModule('app', '''
plugins {
    id 'io.github.qigao.simpledsl.android'
}

simpledsl {
    androidApplication {
        namespace = 'example.app'
    }
}
''')

        def result = runner(':app:help').buildAndFail()

        assertTrue(result.output.contains('simpledsl.android.target-sdk'))
        assertTrue(result.output.contains('Project: :app'))
    }

    @Test
    void rejectsMissingNamespace() {
        writeManifest(true)
        writeSettings()
        writeModule('feature', '''
plugins {
    id 'io.github.qigao.simpledsl.android'
}

simpledsl {
    androidLibrary { }
}
''')

        def result = runner(':feature:help').buildAndFail()

        assertTrue(result.output.contains('SimpleDSL Android configuration error'))
        assertTrue(result.output.contains('namespace is required'))
        assertTrue(result.output.contains('Project: :feature'))
    }

    private void writeManifest(boolean targetSdk) {
        String targetLine = targetSdk ? 'target-sdk = 36\n' : ''
        Files.writeString(projectDir.resolve('dependencies.toml'), """
[simpledsl.android]
java = 21
compile-sdk = 36
min-sdk = 24
${targetLine}""".stripIndent())
    }

    private void writeSettings() {
        Files.writeString(projectDir.resolve('settings.gradle'), '''
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id 'io.github.qigao.simpledsl.settings'
}

rootProject.name = 'android-consumer'
'''.stripIndent())
    }

    private void writeModule(String name, String buildScript) {
        Path module = Files.createDirectories(projectDir.resolve(name))
        Files.writeString(module.resolve('build.gradle'), buildScript.stripIndent())
        Path manifest = Files.createDirectories(module.resolve('src/main')).resolve('AndroidManifest.xml')
        Files.writeString(manifest, '<manifest />\n')
    }

    private GradleRunner runner(String... arguments) {
        GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments((arguments as List<String>) + ['--stacktrace'])
                .withPluginClasspath()
    }
}
