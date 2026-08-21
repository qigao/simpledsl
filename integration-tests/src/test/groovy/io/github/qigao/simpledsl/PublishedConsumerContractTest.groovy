package io.github.qigao.simpledsl

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

class PublishedConsumerContractTest {
    @TempDir
    Path temporaryDirectory

    @Test
    void resolvesPublishedArtifactsWithoutSourceBuildAndReusesConfigurationCache() {
        File fixture = copyFixture('published-consumer')
        assertFalse(new File(fixture, 'settings.gradle').text.contains('includeBuild'))

        List<String> arguments = consumerArguments(
                'simpledslProjects',
                ':app:simpledslCapabilities',
                ':app:simpledslDoctor',
                ':app:test')

        def first = GradleRunner.create()
                .withProjectDir(fixture)
                .withArguments(arguments)
                .build()

        assertTrue(first.output.contains('SimpleDSL Projects'))
        assertTrue(first.output.contains(':app | app | auto | build.gradle'))
        assertTrue(first.output.contains('Type: SPRING_SERVICE'))
        assertTrue(first.output.contains('Features: web'))
        assertTrue(first.output.contains('SimpleDSL Doctor — :app'))
        assertTrue(first.output.contains('Configuration: OK'))
        assertTrue(first.output.contains('Configuration cache entry stored.'))

        def second = GradleRunner.create()
                .withProjectDir(fixture)
                .withArguments(arguments)
                .build()

        assertTrue(
                second.output.contains('Reusing configuration cache.') ||
                second.output.contains('Configuration cache entry reused.'))
    }

    @Test
    void rejectsMismatchedPublishedSimpleDslPluginVersion() {
        File fixture = copyFixture('version-conflict')
        new File(fixture, 'app/build.gradle').text = '''plugins {
    id 'io.github.qigao.simpledsl.spring-service' version '9.9.9'
}
'''

        String managedVersion = requiredProperty('simpledsl.test.version')
        def result = GradleRunner.create()
                .withProjectDir(fixture)
                .withArguments(consumerArguments('help'))
                .buildAndFail()

        assertTrue(result.output.contains('SimpleDSL version conflict'))
        assertTrue(result.output.contains('Plugin: io.github.qigao.simpledsl.spring-service'))
        assertTrue(result.output.contains('Requested: 9.9.9'))
        assertTrue(result.output.contains("Managed: ${managedVersion}"))
    }

    private List<String> consumerArguments(String... tasks) {
        List<String> arguments = new ArrayList<>(Arrays.asList(tasks))
        arguments.add('--configuration-cache')
        arguments.add("-PsimpledslTestRepo=${requiredProperty('simpledsl.test.repo')}".toString())
        arguments.add('--stacktrace')
        arguments
    }

    private File copyFixture(String name) {
        File source = new File(requiredProperty('simpledsl.fixture.dir'))
        File target = temporaryDirectory.resolve(name).toFile()
        copyDirectory(source.toPath(), target.toPath())
        File settings = new File(target, 'settings.gradle')
        settings.text = settings.text.replace(
                '@SIMPLEDSL_VERSION@',
                requiredProperty('simpledsl.test.version'))
        target
    }

    private static void copyDirectory(Path source, Path target) {
        Files.walk(source).withCloseable { paths ->
            paths.forEach { Path path ->
                Path relative = source.relativize(path)
                Path destination = target.resolve(relative)
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination)
                } else {
                    Files.createDirectories(destination.parent)
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name)
        if (!value) {
            throw new IllegalStateException("Missing test system property: ${name}")
        }
        value
    }
}
