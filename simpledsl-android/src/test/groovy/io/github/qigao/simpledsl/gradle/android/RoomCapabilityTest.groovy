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

class RoomCapabilityTest {
    @TempDir
    Path projectDir

    @Test
    void roomSugarEnablesKspAndBindsRoom3ForAndroidApplication() {
        writeSettings('app', 36)
        writeModule('app', '''
plugins {
    id 'io.github.qigao.simpledsl.android'
}

simpledsl {
    androidApplication {
        namespace = 'example.room.app'
    }
    room()
}

assert pluginManager.hasPlugin('com.google.devtools.ksp')
assert !pluginManager.hasPlugin('org.jetbrains.kotlin.android')
assert configurations.findByName('ksp') != null
assert configurations.getByName('implementation').dependencies.any {
    it.group == 'androidx.room3' && it.name == 'room3-runtime' && it.version == '3.0.1'
}
assert configurations.getByName('ksp').dependencies.any {
    it.group == 'androidx.room3' && it.name == 'room3-compiler' && it.version == '3.0.1'
}

def model = extensions.getByName('simpledslModuleModel')
assert model.capabilities.get().contains('ksp')
assert model.capabilities.get().contains('room')
''')

        BuildResult result = build(':app:help')

        assertOutputContains(result, 'BUILD SUCCESSFUL')
    }

    @Test
    void genericRoomCapabilityUsesSameContractForAndroidLibrary() {
        writeSettings('feature', null)
        writeModule('feature', '''
plugins {
    id 'io.github.qigao.simpledsl.android'
}

simpledsl {
    androidLibrary {
        namespace = 'example.room.feature'
    }
    capability('room')
}

assert pluginManager.hasPlugin('com.google.devtools.ksp')
assert !pluginManager.hasPlugin('org.jetbrains.kotlin.android')
assert configurations.findByName('ksp') != null
assert configurations.getByName('implementation').dependencies.any {
    it.group == 'androidx.room3' && it.name == 'room3-runtime' && it.version == '3.0.1'
}
assert configurations.getByName('ksp').dependencies.any {
    it.group == 'androidx.room3' && it.name == 'room3-compiler' && it.version == '3.0.1'
}

def model = extensions.getByName('simpledslModuleModel')
assert model.capabilities.get().contains('ksp')
assert model.capabilities.get().contains('room')
''')

        BuildResult result = build(':feature:help')

        assertOutputContains(result, 'BUILD SUCCESSFUL')
    }

    @Test
    void roomSugarUsesNormalModuleTypeDiagnostic() {
        writeSettings('feature', 36)
        writeModule('feature', '''
plugins {
    id 'io.github.qigao.simpledsl.android'
}

simpledsl {
    room()
}
''')

        BuildResult result = buildAndFail(':feature:help')

        assertOutputContains(result, 'Capability: room')
        assertOutputContains(result, 'requires a module type')
        assertOutputContains(result, 'android-application')
        assertOutputContains(result, 'android-library')
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
            libraries: [
                'room-runtime': [
                    module: 'androidx.room3:room3-runtime',
                    version: '3.0.1',
                    platform: null
                ],
                'room-compiler': [
                    module: 'androidx.room3:room3-compiler',
                    version: '3.0.1',
                    platform: null
                ]
            ],
            plugins: [:]
        ]
    }
}

gradle.sharedServices.registerIfAbsent('simpledslDependencyRegistry', TestDependencyRegistry) { }

rootProject.name = 'android-room-consumer'
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
