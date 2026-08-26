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

class ModuleDependencyDslTest {
    @TempDir
    Path projectDir

    @Test
    void addsDefaultAndExplicitProjectDependencies() {
        writeSettings()
        writeLibrary('user', 'example.user')
        writeLibrary('model', 'example.model')
        writeModule('order', '''
plugins { id 'io.github.qigao.simpledsl.android' }

simpledsl {
    androidLibrary {
        namespace = 'example.order'
    }
    dependsOn(':user')
    dependsOn('api', ':model')
}

assert configurations.getByName('implementation').dependencies.any {
    it instanceof org.gradle.api.artifacts.ProjectDependency && it.path == ':user'
}
assert configurations.getByName('api').dependencies.any {
    it instanceof org.gradle.api.artifacts.ProjectDependency && it.path == ':model'
}
''')

        BuildResult result = build(':order:help')

        assertOutputContains(result, 'BUILD SUCCESSFUL')
    }

    @Test
    void rejectsRelativeProjectPath() {
        writeSettings()
        writeLibrary('user', 'example.user')
        writeModule('order', '''
plugins { id 'io.github.qigao.simpledsl.android' }

simpledsl {
    androidLibrary { namespace = 'example.order' }
    dependsOn('user')
}
''')

        BuildResult result = buildAndFail(':order:help')

        assertOutputContains(result, 'SimpleDSL module dependency error')
        assertOutputContains(result, 'Project: :order')
        assertOutputContains(result, 'Configuration: implementation')
        assertOutputContains(result, 'Dependency: user')
        assertOutputContains(result, "Problem: dependency must be an absolute Gradle project path beginning with ':'")
    }

    @Test
    void rejectsSelfDependency() {
        writeSettings()
        writeModule('order', '''
plugins { id 'io.github.qigao.simpledsl.android' }

simpledsl {
    androidLibrary { namespace = 'example.order' }
    dependsOn(':order')
}
''')

        BuildResult result = buildAndFail(':order:help')

        assertOutputContains(result, 'SimpleDSL module dependency error')
        assertOutputContains(result, 'Project: :order')
        assertOutputContains(result, 'Configuration: implementation')
        assertOutputContains(result, 'Dependency: :order')
        assertOutputContains(result, 'Problem: module cannot depend on itself')
    }

    @Test
    void rejectsMissingTargetModule() {
        writeSettings()
        writeModule('order', '''
plugins { id 'io.github.qigao.simpledsl.android' }

simpledsl {
    androidLibrary { namespace = 'example.order' }
    dependsOn(':missing')
}
''')

        BuildResult result = buildAndFail(':order:help')

        assertOutputContains(result, 'SimpleDSL module dependency error')
        assertOutputContains(result, 'Project: :order')
        assertOutputContains(result, 'Configuration: implementation')
        assertOutputContains(result, 'Dependency: :missing')
        assertOutputContains(result, 'Problem: target module was not discovered')
    }

    @Test
    void rejectsMissingConfiguration() {
        writeSettings()
        writeLibrary('user', 'example.user')
        writeModule('order', '''
plugins { id 'io.github.qigao.simpledsl.android' }

simpledsl {
    androidLibrary { namespace = 'example.order' }
    dependsOn('notAConfiguration', ':user')
}
''')

        BuildResult result = buildAndFail(':order:help')

        assertOutputContains(result, 'SimpleDSL module dependency error')
        assertOutputContains(result, 'Project: :order')
        assertOutputContains(result, 'Configuration: notAConfiguration')
        assertOutputContains(result, 'Dependency: :user')
        assertOutputContains(result, 'Problem: configuration does not exist')
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
                    targetSdk: 36
                ]
            ],
            platforms: [:],
            libraries: [:],
            plugins: [:]
        ]
    }
}

gradle.sharedServices.registerIfAbsent('simpledslDependencyRegistry', TestDependencyRegistry) { }

rootProject.name = 'module-dependency-consumer'
include 'order', 'user', 'model'
'''.stripIndent())

        ['order', 'user', 'model'].each { name ->
            Files.createDirectories(projectDir.resolve(name))
        }
    }

    private void writeLibrary(String name, String namespace) {
        writeModule(name, """
plugins { id 'io.github.qigao.simpledsl.android' }

simpledsl {
    androidLibrary { namespace = '${namespace}' }
}
""")
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
            throw new AssertionError("Nested module-dependency build failed:\n${error.buildResult.output}", error)
        }
    }

    private BuildResult buildAndFail(String... arguments) {
        try {
            return runner(arguments).buildAndFail()
        } catch (UnexpectedBuildSuccess error) {
            throw new AssertionError("Nested module-dependency build unexpectedly succeeded:\n${error.buildResult.output}", error)
        }
    }

    private static void assertOutputContains(BuildResult result, String expected) {
        assertTrue(
                result.output.contains(expected),
                "Expected nested module-dependency build output to contain '${expected}'.\nOutput:\n${result.output}")
    }

    private GradleRunner runner(String... arguments) {
        GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments((arguments as List<String>) + ['--stacktrace'])
                .withPluginClasspath()
    }
}
