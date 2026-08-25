package io.github.qigao.simpledsl.gradle.feature

import io.github.qigao.simpledsl.gradle.SimpleDslConfigurationException
import io.github.qigao.simpledsl.gradle.capability.BuiltinCapabilities
import io.github.qigao.simpledsl.gradle.catalog.CatalogLibrary
import io.github.qigao.simpledsl.gradle.catalog.CatalogPlatform
import io.github.qigao.simpledsl.gradle.catalog.DependencyCatalogSnapshot
import io.github.qigao.simpledsl.gradle.java.SimpleDslJavaExtension
import io.github.qigao.simpledsl.gradle.java.SimpleDslJavaPlugin
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
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
    void webCapabilityActivatesServiceDependenciesWithoutPublicFeaturePluginId() {
        def project = javaProjectWithCatalog()
        def simpledsl = project.extensions.getByType(SimpleDslJavaExtension)
        simpledsl.springService()
        simpledsl.web()

        def model = project.extensions.getByType(SimpleDslModuleModel)
        assertTrue(model.capabilities.get().contains('web'))
        def modules = project.configurations.getByName('implementation').dependencies.collect { it.group + ':' + it.name }
        assertTrue(modules.contains('org.springframework.boot:spring-boot-starter-webmvc'))
        assertTrue(modules.contains('org.springframework.boot:spring-boot-starter-validation'))
    }

    @Test
    void convenienceDslAppliesInternalCapabilities() {
        def project = javaProjectWithCatalog()
        def simpledsl = project.extensions.getByType(SimpleDslJavaExtension)
        simpledsl.springService()

        simpledsl.aop()
        simpledsl.transaction()
        simpledsl.web()
        simpledsl.httpClient()
        simpledsl.messaging()
        simpledsl.redis()
        simpledsl.lombok()
        simpledsl.persistence.jpa()
        simpledsl.persistence.jdbc()
        simpledsl.persistence.jooq()

        def capabilities = project.extensions.getByType(SimpleDslModuleModel).capabilities.get()
        assertTrue(capabilities.containsAll([
                'aop', 'transaction', 'web', 'http-client', 'messaging',
                'redis', 'lombok', 'jpa', 'jdbc', 'jooq'
        ]))
    }

    @Test
    void nativeCapabilityRejectsJavaLibrary() {
        def project = javaProjectWithCatalog()
        def simpledsl = project.extensions.getByType(SimpleDslJavaExtension)
        simpledsl.javaLibrary()

        def error = assertThrows(SimpleDslConfigurationException) {
            simpledsl.nativeImage()
        }

        assertTrue(error.message.contains('SimpleDSL configuration error'))
        assertTrue(error.message.contains("capability 'native' is not supported"))
    }

    private static def javaProjectWithCatalog() {
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
                    'spring-aop': spring('spring-aop', 'org.springframework.boot:spring-boot-starter-aop'),
                    'spring-transaction': spring('spring-transaction', 'org.springframework:spring-tx'),
                    'spring-webmvc': spring('spring-webmvc', 'org.springframework.boot:spring-boot-starter-webmvc'),
                    'spring-validation': spring('spring-validation', 'org.springframework.boot:spring-boot-starter-validation'),
                    'spring-webmvc-test': spring('spring-webmvc-test', 'org.springframework.boot:spring-boot-starter-test'),
                    'spring-restclient': spring('spring-restclient', 'org.springframework.boot:spring-boot-starter-restclient'),
                    'spring-restclient-test': spring('spring-restclient-test', 'org.springframework.boot:spring-boot-starter-test'),
                    'spring-messaging': spring('spring-messaging', 'org.springframework:spring-messaging'),
                    'spring-redis': spring('spring-redis', 'org.springframework.boot:spring-boot-starter-data-redis'),
                    'spring-jpa': spring('spring-jpa', 'org.springframework.boot:spring-boot-starter-data-jpa'),
                    'spring-jdbc': spring('spring-jdbc', 'org.springframework.boot:spring-boot-starter-jdbc'),
                    'spring-jooq': spring('spring-jooq', 'org.springframework.boot:spring-boot-starter-jooq'),
                    'lombok': new CatalogLibrary('lombok', 'org.projectlombok:lombok', '1.18.42', null)
                ],
                [:])
        project.extensions.add(DependencyCatalogSnapshot, 'simpledslDependencyCatalog', catalog)
        project.pluginManager.apply(SimpleDslJavaPlugin)
        project
    }
}
