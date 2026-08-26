package io.github.qigao.simpledsl.gradle.java

import io.github.qigao.simpledsl.gradle.catalog.CatalogLibrary
import io.github.qigao.simpledsl.gradle.catalog.DependencyCatalogSnapshot
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertTrue

class SimpleDslJavaPluginTest {
    @Test
    void claimsJavaBackendAndCreatesJavaOnlyExtension() {
        def project = javaProject()

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
    void javaModulePathDoesNotLoadLegacyBuildOrModuleEntryPlugins() {
        def project = javaProject()
        project.pluginManager.apply(SimpleDslJavaPlugin)

        (project.extensions.getByName('simpledsl') as SimpleDslJavaExtension).javaLibrary()

        Set<String> appliedPluginClasses = project.plugins.collect { it.class.name } as Set<String>
        assertFalse(appliedPluginClasses.contains('io.github.qigao.simpledsl.gradle.SimpleDslBuildPlugin'))
        assertFalse(appliedPluginClasses.contains('io.github.qigao.simpledsl.gradle.SimpleDslModulePlugin'))
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

    @Test
    void javaBackendSupportsSharedModuleDependencies() {
        def root = ProjectBuilder.builder().withName('root').build()
        ProjectBuilder.builder().withName('user').withParent(root).build()
        ProjectBuilder.builder().withName('model').withParent(root).build()
        def order = ProjectBuilder.builder().withName('order').withParent(root).build()
        addCatalog(order)

        order.pluginManager.apply(SimpleDslJavaPlugin)
        def extension = order.extensions.getByName('simpledsl') as SimpleDslJavaExtension
        extension.javaLibrary()
        extension.dependsOn(':user')
        extension.dependsOn('api', ':model')

        assertTrue(order.configurations.getByName('implementation').dependencies.any {
            it instanceof ProjectDependency && it.path == ':user'
        })
        assertTrue(order.configurations.getByName('api').dependencies.any {
            it instanceof ProjectDependency && it.path == ':model'
        })
    }

    private static def javaProject() {
        def project = ProjectBuilder.builder().withName('app').build()
        addCatalog(project)
        project
    }

    private static void addCatalog(def project) {
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
    }
}
