package io.github.qigao.simpledsl.gradle.java

import io.github.qigao.simpledsl.gradle.catalog.CatalogLibrary
import io.github.qigao.simpledsl.gradle.catalog.DependencyCatalogSnapshot
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertTrue

class SimpleDslJavaPluginTest {
    @Test
    void claimsJavaBackendAndCreatesJavaOnlyExtension() {
        def project = ProjectBuilder.builder().withName('app').build()
        project.extensions.add(
                DependencyCatalogSnapshot,
                'simpledslDependencyCatalog',
                new DependencyCatalogSnapshot(
                        25,
                        [:],
                        [
                                'junit-jupiter': new CatalogLibrary(
                                        'junit-jupiter', 'org.junit.jupiter:junit-jupiter', '5.13.4', null),
                                'junit-platform-launcher': new CatalogLibrary(
                                        'junit-platform-launcher', 'org.junit.platform:junit-platform-launcher', '1.13.4', null)
                        ],
                        [:]))

        project.pluginManager.apply(SimpleDslJavaPlugin)

        def model = project.extensions.getByType(SimpleDslModuleModel)
        assertEquals('java', model.backendId.get())

        def extension = project.extensions.findByName('simpledsl')
        assertNotNull(extension)
        assertTrue(extension instanceof SimpleDslJavaExtension)
        def extensionMethods = extension.class.methods*.name as Set
        assertFalse(extensionMethods.contains('androidApplication'))
        assertFalse(extensionMethods.contains('androidLibrary'))
    }

    @Test
    void JavaExtensionPreservesExistingModuleMethods() {
        def methods = SimpleDslJavaExtension.methods*.name as Set

        assertTrue(methods.contains('javaLibrary'))
        assertTrue(methods.contains('springLibrary'))
        assertTrue(methods.contains('springService'))
        assertTrue(methods.contains('jooqSchema'))
        assertTrue(methods.contains('jsonSchema'))
        assertFalse(methods.contains('androidApplication'))
        assertFalse(methods.contains('androidLibrary'))
    }
}
