package io.github.qigao.simpledsl

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

class PublishedConsumerContractTest {
    private static final List<String> PUBLIC_PLUGIN_IDS = [
            'io.github.qigao.simpledsl.settings',
            'io.github.qigao.simpledsl.build'
    ].asImmutable()

    @TempDir
    Path temporaryDirectory

    @Test
    void publishesExactlyThePublicPluginMarkerSurface() {
        File repository = new File(requiredProperty('simpledsl.test.repo'))
        String version = requiredProperty('simpledsl.test.version')

        assertTrue(repository.isDirectory())
        assertEquals(2, PUBLIC_PLUGIN_IDS.size())

        Set<String> actualPluginIds = new TreeSet<>()
        repository.eachFileRecurse { File file ->
            if (!file.isFile() || !file.name.endsWith('.pom')) return
            if (file.parentFile?.name != version) return

            File artifactDirectory = file.parentFile.parentFile
            String artifactId = artifactDirectory?.name
            if (artifactId?.endsWith('.gradle.plugin')) {
                actualPluginIds.add(
                        artifactId.substring(0, artifactId.length() - '.gradle.plugin'.length()))
            }
        }

        Set<String> expectedPluginIds = new TreeSet<>(PUBLIC_PLUGIN_IDS)
        assertEquals(
                expectedPluginIds,
                actualPluginIds,
                "Unexpected published plugin marker surface. Expected ${expectedPluginIds}, actual ${actualPluginIds}".toString())
    }

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
        assertTrue(first.output.contains('Type: spring-service'))
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
                second.output.contains('Configuration cache entry reused.'),
                "Second build did not reuse configuration cache:\n${second.output}".toString())
    }

    @Test
    void rejectsMismatchedPublishedSimpleDslBuildVersion() {
        File fixture = copyFixture('version-conflict')
        new File(fixture, 'app/build.gradle').text = '''plugins {
    id 'io.github.qigao.simpledsl.build' version '9.9.9'
}
'''

        String managedVersion = requiredProperty('simpledsl.test.version')
        def result = GradleRunner.create()
                .withProjectDir(fixture)
                .withArguments(consumerArguments('help'))
                .buildAndFail()

        assertTrue(result.output.contains('SimpleDSL version conflict'))
        assertTrue(result.output.contains('Plugin: io.github.qigao.simpledsl.build'))
        assertTrue(result.output.contains('Requested: 9.9.9'))
        assertTrue(result.output.contains("Managed: ${managedVersion}"))
    }

    private List<String> consumerArguments(String... tasks) {
        List<String> arguments = new ArrayList<>(Arrays.asList(tasks))
        arguments.add('--configuration-cache')
        arguments.add("-PsimpledslTestRepo=${requiredProperty('simpledsl.test.repo')}".toString())
        arguments.add("-PsimpledslVersion=${requiredProperty('simpledsl.test.version')}".toString())
        arguments.add('--stacktrace')
        arguments
    }

    private File copyFixture(String name) {
        Path fixtureRoot = Path.of(requiredProperty('simpledsl.fixture.dir'))
        Path source = fixtureRoot
        Path target = temporaryDirectory.resolve(name)
        copyRecursively(source, target)
        target.toFile()
    }

    private static void copyRecursively(Path source, Path target) {
        Files.walk(source).withCloseable { paths ->
            paths.forEach { current ->
                Path relative = source.relativize(current)
                Path destination = target.resolve(relative)
                if (Files.isDirectory(current)) {
                    Files.createDirectories(destination)
                } else {
                    Files.createDirectories(destination.parent)
                    Files.copy(current, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name)
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing system property ${name}")
        }
        value
    }
}
