package io.github.qigao.simpledsl.gradle.android

import io.github.qigao.simpledsl.gradle.catalog.CatalogAndroidPolicy
import io.github.qigao.simpledsl.gradle.catalog.DependencyCatalogSnapshot
import io.github.qigao.simpledsl.gradle.core.SimpleDslBackendGuard
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class SimpleDslAndroidPluginTest {
    @Test
    void claimsAndroidBackendAndCreatesAndroidOnlyExtension() {
        Project project = androidProject()
        project.pluginManager.apply(androidPluginClass())

        SimpleDslModuleModel model = project.extensions.getByType(SimpleDslModuleModel)
        assertEquals('android', model.backendId.get())

        def extension = project.extensions.findByName('simpledsl')
        assertNotNull(extension)
        assertTrue(extension instanceof SimpleDslAndroidExtension)
        Set<String> methods = extension.class.methods*.name as Set<String>
        assertTrue(methods.contains('androidApplication'))
        assertTrue(methods.contains('androidLibrary'))
        assertFalse(methods.contains('springService'))
        assertFalse(methods.contains('jooqSchema'))
    }

    @Test
    void respectsSharedBackendGuardWithoutDependingOnJavaArtifact() {
        Project project = androidProject()
        SimpleDslBackendGuard.claim(project, 'java')

        GradleException error = assertThrows(GradleException) {
            project.pluginManager.apply(androidPluginClass())
        }

        assertTrue(error.message.contains('SimpleDSL backend conflict'))
        assertTrue(error.message.contains('Already-selected backend: java'))
        assertTrue(error.message.contains('Requested backend: android'))
    }

    private static Class<? extends Plugin> androidPluginClass() {
        Class.forName('io.github.qigao.simpledsl.gradle.android.SimpleDslAndroidPlugin') as Class<? extends Plugin>
    }

    private static Project androidProject() {
        Project project = ProjectBuilder.builder().withName('app').build()
        project.extensions.add(
                DependencyCatalogSnapshot,
                'simpledslDependencyCatalog',
                new DependencyCatalogSnapshot(
                        null,
                        new CatalogAndroidPolicy(21, 36, 24, 36),
                        [:],
                        [:],
                        [:]))
        project
    }
}
