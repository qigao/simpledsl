package io.github.qigao.simpledsl.gradle.feature

import io.github.qigao.simpledsl.gradle.SimpleDslConfigurationException
import io.github.qigao.simpledsl.gradle.capability.BuiltinCapabilities
import io.github.qigao.simpledsl.gradle.catalog.CatalogLibrary
import io.github.qigao.simpledsl.gradle.catalog.CatalogPlatform
import io.github.qigao.simpledsl.gradle.catalog.DependencyCatalogSnapshot
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class FeaturePluginsTest {
    @Test
    void nativeCapabilityUsesFixedExternalPluginId() {
        assertEquals(['org.graalvm.buildtools.native'] as Set, BuiltinCapabilities.NATIVE.externalPluginIds)
    }

    @Test
    void webFeatureActivatesServiceDependencies() {
        def project = projectWithCatalog()
        project.pluginManager.apply('io.github.qigao.simpledsl.spring-service')

        project.pluginManager.apply('io.github.qigao.simpledsl.feature.web')

        def model = project.extensions.getByType(SimpleDslModuleModel)
        assertTrue(model.capabilities.get().contains('web'))
        def modules = project.configurations.getByName('implementation').dependencies.collect { it.group + ':' + it.name }
        assertTrue(modules.contains('org.springframework.boot:spring-boot-starter-webmvc'))
        assertTrue(modules.contains('org.springframework.boot:spring-boot-starter-validation'))
    }

    @Test
    void nativeFeatureRejectsJavaLibrary() {
        def project = projectWithCatalog()
        project.pluginManager.apply('io.github.qigao.simpledsl.java-library')

        def error = assertThrows(GradleException) {
            project.pluginManager.apply('io.github.qigao.simpledsl.feature.native')
        }

        assertTrue(error.cause instanceof SimpleDslConfigurationException)
        def cause = error.cause as SimpleDslConfigurationException
        assertTrue(cause.message.contains('SimpleDSL configuration error'))
        assertTrue(cause.message.contains("capability 'native' is not supported"))
    }

    private static def projectWithCatalog() {
        def project = ProjectBuilder.builder().build()
        def spring = { String alias, String module -> new CatalogLibrary(alias, module, null, 'spring') }
        def catalog = new DependencyCatalogSnapshot(
                25,
                [spring: new CatalogPlatform('spring', 'org.springframework.boot:spring-boot-dependencies', '4.1.0')],
                [
                    'junit-jupiter': new CatalogLibrary('junit-jupiter', 'org.junit.jupiter:junit-jupiter', '5.13.4', null),
                    'junit-platform-launcher': new CatalogLibrary('junit-platform-launcher', 'org.junit.platform:junit-platform-launcher', '1.13.4', null),
                    'spring-core': spring('spring-core', 'org.springframework.boot:spring-boot-starter'),
                    'spring-test': spring('spring-test', 'org.springframework.boot:spring-boot-starter-test'),
                    'spring-webmvc': spring('spring-webmvc', 'org.springframework.boot:spring-boot-starter-webmvc'),
                    'spring-validation': spring('spring-validation', 'org.springframework.boot:spring-boot-starter-validation'),
                    'spring-webmvc-test': spring('spring-webmvc-test', 'org.springframework.boot:spring-boot-starter-webmvc-test')
                ],
                [:])
        project.extensions.add(DependencyCatalogSnapshot, 'simpledslDependencyCatalog', catalog)
        project
    }
}
