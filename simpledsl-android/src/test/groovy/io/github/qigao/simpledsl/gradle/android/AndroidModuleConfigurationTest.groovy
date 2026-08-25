package io.github.qigao.simpledsl.gradle.android

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.gradle.testkit.runner.UnexpectedBuildSuccess
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
        writeSettings('app', 36)
        writeModule('app', androidPluginPrelude() + '''

simpledsl {
    androidApplication {
        namespace = 'example.app'
    }
}

assert pluginManager.hasPlugin('com.android.application')
assert !pluginManager.hasPlugin('org.jetbrains.kotlin.android')

def androidDsl = extensions.getByName('android')
def androidComponents = extensions.getByName('androidComponents')
androidComponents.onVariants { variant ->
    assert androidDsl.namespace == 'example.app'
    assert androidDsl.compileSdk == 36
    assert androidDsl.defaultConfig.minSdk == 24
    assert androidDsl.defaultConfig.targetSdk == 36
    assert androidDsl.defaultConfig.applicationId == 'example.app'
    assert androidDsl.compileOptions.sourceCompatibility == JavaVersion.VERSION_21
    assert androidDsl.compileOptions.targetCompatibility == JavaVersion.VERSION_21
}
''')

        BuildResult result = build(':app:help')

        assertOutputContains(result, 'BUILD SUCCESSFUL')
    }

    @Test
    void configuresAndroidLibraryWithoutTargetSdkPolicy() {
        writeSettings('feature', null)
        writeModule('feature', androidPluginPrelude() + '''

simpledsl {
    androidLibrary {
        namespace = 'example.feature'
    }
}

assert pluginManager.hasPlugin('com.android.library')
assert !pluginManager.hasPlugin('org.jetbrains.kotlin.android')

def androidDsl = extensions.getByName('android')
def androidComponents = extensions.getByName('androidComponents')
androidComponents.onVariants { variant ->
    assert androidDsl.namespace == 'example.feature'
    assert androidDsl.compileSdk == 36
    assert androidDsl.defaultConfig.minSdk == 24
    assert androidDsl.compileOptions.sourceCompatibility == JavaVersion.VERSION_21
    assert androidDsl.compileOptions.targetCompatibility == JavaVersion.VERSION_21
}
''')

        BuildResult result = build(':feature:help')

        assertOutputContains(result, 'BUILD SUCCESSFUL')
    }

    @Test
    void rejectsApplicationPolicyWithoutTargetSdk() {
        writeSettings('app', null)
        writeModule('app', androidPluginPrelude() + '''

simpledsl {
    androidApplication {
        namespace = 'example.app'
    }
}
''')

        BuildResult result = buildAndFail(':app:help')

        assertOutputContains(result, 'simpledsl.android.target-sdk')
        assertOutputContains(result, 'Project: :app')
    }

    @Test
    void rejectsMissingNamespace() {
        writeSettings('feature', 36)
        writeModule('feature', androidPluginPrelude() + '''

simpledsl {
    androidLibrary { }
}
''')

        BuildResult result = buildAndFail(':feature:help')

        assertOutputContains(result, 'SimpleDSL Android configuration error')
        assertOutputContains(result, 'namespace is required')
        assertOutputContains(result, 'Project: :feature')
    }

    private static String androidPluginPrelude() {
        '''
plugins {
    id 'io.github.qigao.simpledsl.android'
}
'''.stripIndent()
    }

    private void writeSettings(String moduleName, Integer targetSdk) {
        String target = targetSdk == null ? 'null' : targetSdk.toString()
        Files.writeString(projectDir.resolve('settings.gradle'), """
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

abstract class TestDependencyRegistry implements BuildService<BuildServiceParameters.None> {
    Map snapshot() {
        [
            schemaVersion: 2,
            policies: [
                android: [
                    java: 21,
                    compileSdk: 36,
                    minSdk: 24,
                    targetSdk: ${target}
                ]
            ],
            platforms: [:],
            libraries: [:],
            plugins: [:]
        ]
    }
}

gradle.sharedServices.registerIfAbsent('simpledslDependencyRegistry', TestDependencyRegistry) { }

rootProject.name = 'android-consumer'
include '${moduleName}'
""".stripIndent())
    }

    private void writeModule(String name, String buildScript) {
        Path module = Files.createDirectories(projectDir.resolve(name))
        Files.writeString(module.resolve('build.gradle'), buildScript.stripIndent())
        Path manifest = Files.createDirectories(module.resolve('src/main')).resolve('AndroidManifest.xml')
        Files.writeString(manifest, '<manifest />\n')
    }

    private BuildResult build(String... arguments) {
        try {
            return runner(arguments).build()
        } catch (UnexpectedBuildFailure error) {
            throw new AssertionError("Nested Android build failed:\n${error.buildResult.output}", error)
        }
    }

    private BuildResult buildAndFail(String... arguments) {
        try {
            return runner(arguments).buildAndFail()
        } catch (UnexpectedBuildSuccess error) {
            throw new AssertionError("Nested Android build unexpectedly succeeded:\n${error.buildResult.output}", error)
        }
    }

    private static void assertOutputContains(BuildResult result, String expected) {
        assertTrue(
                result.output.contains(expected),
                "Expected nested Android build output to contain '${expected}'.\nOutput:\n${result.output}")
    }

    private GradleRunner runner(String... arguments) {
        GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments((arguments as List<String>) + ['--stacktrace'])
                .withPluginClasspath()
    }
}
