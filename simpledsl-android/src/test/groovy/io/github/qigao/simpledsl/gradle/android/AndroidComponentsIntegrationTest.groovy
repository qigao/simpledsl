package io.github.qigao.simpledsl.gradle.android

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertTrue

class AndroidComponentsIntegrationTest {
    @TempDir
    Path projectDir

    @Test
    void applicationVariantsAreExposedAndConfigurationCacheIsReused() {
        writeSettings('app', 36)
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

        BuildResult first = build(':app:simpledslAndroidVariants', '--configuration-cache')
        assertOutputContains(first, 'debug')
        assertOutputContains(first, 'release')

        BuildResult second = build(':app:simpledslAndroidVariants', '--configuration-cache')
        assertOutputContains(second, 'Reusing configuration cache')
        assertOutputContains(second, 'debug')
        assertOutputContains(second, 'release')
    }

    @Test
    void libraryVariantsAreExposedAndConfigurationCacheIsReused() {
        writeSettings('feature', null)
        writeModule('feature', '''
plugins {
    id 'io.github.qigao.simpledsl.android'
}

simpledsl {
    androidLibrary {
        namespace = 'example.feature'
    }
}
''')

        BuildResult first = build(':feature:simpledslAndroidVariants', '--configuration-cache')
        assertOutputContains(first, 'debug')
        assertOutputContains(first, 'release')

        BuildResult second = build(':feature:simpledslAndroidVariants', '--configuration-cache')
        assertOutputContains(second, 'Reusing configuration cache')
        assertOutputContains(second, 'debug')
        assertOutputContains(second, 'release')
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

rootProject.name = 'android-components-consumer'
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
            return GradleRunner.create()
                    .withProjectDir(projectDir.toFile())
                    .withArguments((arguments as List<String>) + ['--stacktrace'])
                    .withPluginClasspath()
                    .build()
        } catch (UnexpectedBuildFailure error) {
            throw new AssertionError("Nested Android Components build failed:\n${error.buildResult.output}", error)
        }
    }

    private static void assertOutputContains(BuildResult result, String expected) {
        assertTrue(
                result.output.contains(expected),
                "Expected nested Android Components build output to contain '${expected}'.\nOutput:\n${result.output}")
    }
}
