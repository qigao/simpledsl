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

class DynamicFeatureModuleTest {
    @TempDir
    Path projectDir

    @Test
    void configuresDynamicFeatureTopologyFromRepositoryPolicy() {
        writeSettings()
        writeApp('''
plugins { id 'io.github.qigao.simpledsl.android' }

simpledsl {
    androidApplication {
        namespace = 'example.app'
        dynamicFeature(':payments')
    }
}

assert extensions.getByName('android').dynamicFeatures == [':payments'] as Set
''')
        writePayments('''
plugins { id 'io.github.qigao.simpledsl.android' }

simpledsl {
    androidDynamicFeature {
        namespace = 'example.payments'
        baseModule = ':app'
    }
}

assert pluginManager.hasPlugin('com.android.dynamic-feature')
assert !pluginManager.hasPlugin('org.jetbrains.kotlin.android')
assert extensions.getByName('simpledslModuleModel').moduleType.get() == 'android-dynamic-feature'

def androidDsl = extensions.getByName('android')
assert androidDsl.namespace == 'example.payments'
assert androidDsl.compileSdk == 36
assert androidDsl.defaultConfig.minSdk == 24

assert configurations.getByName('implementation').dependencies.any {
    it instanceof org.gradle.api.artifacts.ProjectDependency && it.path == ':app'
}

tasks.register('verifyDynamicFeaturePolicy') {
    doLast {
        def finalizedAndroidDsl = project.extensions.getByName('android')
        assert finalizedAndroidDsl.compileOptions.sourceCompatibility == JavaVersion.VERSION_21
        assert finalizedAndroidDsl.compileOptions.targetCompatibility == JavaVersion.VERSION_21
    }
}
''')

        BuildResult result = build(':payments:verifyDynamicFeaturePolicy')

        assertOutputContains(result, 'BUILD SUCCESSFUL')
    }

    @Test
    void rejectsDynamicFeatureWithoutBaseModule() {
        writeSettings()
        writePayments('''
plugins { id 'io.github.qigao.simpledsl.android' }

simpledsl {
    androidDynamicFeature {
        namespace = 'example.payments'
    }
}
''')

        BuildResult result = buildAndFail(':payments:help')

        assertOutputContains(result, 'SimpleDSL Android configuration error')
        assertOutputContains(result, 'Project: :payments')
        assertOutputContains(result, 'Problem: androidDynamicFeature requires baseModule')
    }

    @Test
    void rejectsRelativeDynamicFeatureBaseModule() {
        writeSettings()
        writePayments('''
plugins { id 'io.github.qigao.simpledsl.android' }

simpledsl {
    androidDynamicFeature {
        namespace = 'example.payments'
        baseModule = 'app'
    }
}
''')

        BuildResult result = buildAndFail(':payments:help')

        assertOutputContains(result, "Problem: baseModule must be an absolute Gradle project path beginning with ':'")
        assertOutputContains(result, 'Value: app')
    }

    @Test
    void rejectsDynamicFeatureBaseModuleSelfReference() {
        writeSettings()
        writePayments('''
plugins { id 'io.github.qigao.simpledsl.android' }

simpledsl {
    androidDynamicFeature {
        namespace = 'example.payments'
        baseModule = ':payments'
    }
}
''')

        BuildResult result = buildAndFail(':payments:help')

        assertOutputContains(result, 'Problem: androidDynamicFeature baseModule cannot reference the feature project itself')
        assertOutputContains(result, 'Value: :payments')
    }

    @Test
    void rejectsDuplicateAndroidModuleDeclarationWithDynamicFeature() {
        writeSettings()
        writePayments('''
plugins { id 'io.github.qigao.simpledsl.android' }

simpledsl {
    androidLibrary { namespace = 'example.payments' }
    androidDynamicFeature {
        namespace = 'example.payments'
        baseModule = ':app'
    }
}
''')

        BuildResult result = buildAndFail(':payments:help')

        assertOutputContains(result, 'Problem: exactly one Android module type may be declared')
    }

    @Test
    void rejectsRelativeApplicationDynamicFeaturePath() {
        writeSettings()
        writeApp('''
plugins { id 'io.github.qigao.simpledsl.android' }

simpledsl {
    androidApplication {
        namespace = 'example.app'
        dynamicFeature('payments')
    }
}
''')

        BuildResult result = buildAndFail(':app:help')

        assertOutputContains(result, "Problem: dynamicFeature path must be an absolute Gradle project path beginning with ':'")
        assertOutputContains(result, 'Value: payments')
    }

    @Test
    void rejectsApplicationDynamicFeatureSelfReference() {
        writeSettings()
        writeApp('''
plugins { id 'io.github.qigao.simpledsl.android' }

simpledsl {
    androidApplication {
        namespace = 'example.app'
        dynamicFeature(':app')
    }
}
''')

        BuildResult result = buildAndFail(':app:help')

        assertOutputContains(result, 'Problem: androidApplication dynamicFeature cannot reference the application project itself')
        assertOutputContains(result, 'Value: :app')
    }

    @Test
    void rejectsComposeCapabilityForDynamicFeature() {
        writeSettings()
        writeApp('''
plugins { id 'io.github.qigao.simpledsl.android' }

simpledsl {
    androidApplication {
        namespace = 'example.app'
        dynamicFeature(':payments')
    }
}
''')
        writePayments('''
plugins { id 'io.github.qigao.simpledsl.android' }

simpledsl {
    androidDynamicFeature {
        namespace = 'example.payments'
        baseModule = ':app'
    }
    jetpackCompose()
}
''')

        BuildResult result = buildAndFail(':payments:help')

        assertOutputContains(result, 'Capability: compose')
        assertOutputContains(result, 'android-application')
        assertOutputContains(result, 'android-library')
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

rootProject.name = 'dynamic-feature-consumer'
include 'app', 'payments'
'''.stripIndent())

        ['app', 'payments'].each { name ->
            Path module = Files.createDirectories(projectDir.resolve(name))
            Files.writeString(module.resolve('build.gradle'), '')
        }
    }

    private void writeApp(String buildScript) {
        writeModule('app', buildScript)
    }

    private void writePayments(String buildScript) {
        Path module = Files.createDirectories(projectDir.resolve('payments'))
        Files.writeString(module.resolve('build.gradle'), buildScript.stripIndent())
        Path main = Files.createDirectories(module.resolve('src/main'))
        Files.writeString(main.resolve('AndroidManifest.xml'), '''
<manifest xmlns:dist="http://schemas.android.com/apk/distribution">
    <dist:module
        dist:instant="false"
        dist:title="@string/payments_title">
        <dist:delivery>
            <dist:install-time />
        </dist:delivery>
        <dist:fusing dist:include="true" />
    </dist:module>
    <application />
</manifest>
'''.stripIndent())
        Path values = Files.createDirectories(main.resolve('res/values'))
        Files.writeString(values.resolve('strings.xml'), '''
<resources>
    <string name="payments_title">Payments</string>
</resources>
'''.stripIndent())
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
            throw new AssertionError("Nested Dynamic Feature build failed:\n${error.buildResult.output}", error)
        }
    }

    private BuildResult buildAndFail(String... arguments) {
        try {
            return runner(arguments).buildAndFail()
        } catch (UnexpectedBuildSuccess error) {
            throw new AssertionError("Nested Dynamic Feature build unexpectedly succeeded:\n${error.buildResult.output}", error)
        }
    }

    private static void assertOutputContains(BuildResult result, String expected) {
        assertTrue(
                result.output.contains(expected),
                "Expected nested Dynamic Feature build output to contain '${expected}'.\nOutput:\n${result.output}")
    }

    private GradleRunner runner(String... arguments) {
        GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments((arguments as List<String>) + ['--stacktrace'])
                .withPluginClasspath()
    }
}
