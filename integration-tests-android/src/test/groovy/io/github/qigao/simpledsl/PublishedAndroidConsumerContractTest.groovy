package io.github.qigao.simpledsl

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

class PublishedAndroidConsumerContractTest {
    @TempDir
    Path temporaryDirectory

    @Test
    void resolvesPublishedAndroidMarkersAndReusesConfigurationCache() {
        File fixture = copyFixture('published-android-consumer')
        assertFalse(new File(fixture, 'settings.gradle').text.contains('includeBuild'))

        List<String> arguments = consumerArguments(
                ':app:simpledslAndroidVariants',
                ':feature:simpledslAndroidVariants')

        BuildResult first = GradleRunner.create()
                .withProjectDir(fixture)
                .withArguments(arguments)
                .build()

        assertTrue(first.output.contains('SimpleDSL Android variant: debug'))
        assertTrue(first.output.contains('SimpleDSL Android variant: release'))
        assertTrue(first.output.contains('Configuration cache entry stored.'))

        BuildResult second = GradleRunner.create()
                .withProjectDir(fixture)
                .withArguments(arguments)
                .build()

        assertTrue(
                second.output.contains('Reusing configuration cache.') ||
                second.output.contains('Configuration cache entry reused.'),
                "Second Android consumer build did not reuse configuration cache:\n${second.output}".toString())
        assertTrue(second.output.contains('SimpleDSL Android variant: debug'))
        assertTrue(second.output.contains('SimpleDSL Android variant: release'))
    }

    @Test
    void assemblesPublishedAndroidApplicationAndLibrary() {
        File fixture = copyFixture('published-android-assemble')

        BuildResult result = GradleRunner.create()
                .withProjectDir(fixture)
                .withArguments(consumerArguments(
                        ':app:assembleDebug',
                        ':feature:assembleDebug'))
                .build()

        assertTrue(result.output.contains('BUILD SUCCESSFUL'))
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
