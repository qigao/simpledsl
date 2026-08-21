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
            'io.github.qigao.simpledsl.module',
            'io.github.qigao.simpledsl.java-library',
            'io.github.qigao.simpledsl.spring-library',
            'io.github.qigao.simpledsl.spring-service',
            'io.github.qigao.simpledsl.feature.aop',
            'io.github.qigao.simpledsl.feature.transaction',
            'io.github.qigao.simpledsl.feature.web',
            'io.github.qigao.simpledsl.feature.http-client',
            'io.github.qigao.simpledsl.feature.messaging',
            'io.github.qigao.simpledsl.feature.jdbc',
            'io.github.qigao.simpledsl.feature.jooq',
            'io.github.qigao.simpledsl.feature.jpa',
            'io.github.qigao.simpledsl.feature.redis',
            'io.github.qigao.simpledsl.feature.native',
            'io.github.qigao.simpledsl.feature.lombok',
            'io.github.qigao.simpledsl.schema.jooq',
            'io.github.qigao.simpledsl.schema.json'
    ].asImmutable()

    @TempDir
    Path temporaryDirectory

    @Test
    void publishesExactlyThePublicPluginMarkerSurface() {
        File repository = new File(requiredProperty('simpledsl.test.repo'))
        String version = requiredProperty('simpledsl.test.version')

        assertTrue(repository.isDirectory())
        assertEquals(18, PUBLIC_PLUGIN_IDS.size())

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
        assertFalse(actualPluginIds.any { it.startsWith('io.github.qigao.simpledsl.internal.') })
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
                second.output.contains('Configuration cache entry reused.'),
                "Second build did not reuse configuration cache:\n${second.output}".toString())
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
