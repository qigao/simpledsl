package io.github.qigao.simpledsl.gradle.module

import io.github.qigao.simpledsl.gradle.ModuleKind
import io.github.qigao.simpledsl.gradle.catalog.CatalogLibrary
import io.github.qigao.simpledsl.gradle.catalog.CatalogPlatform
import io.github.qigao.simpledsl.gradle.catalog.DependencyCatalogSnapshot
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

class ModuleTypePluginsTest {
    @Test
    void javaLibraryClaimsModuleAndUsesConsumerJavaVersion() {
        def project = projectWithCatalog()

        project.pluginManager.apply('io.github.qigao.simpledsl.java-library')

        assertEquals(ModuleKind.JAVA_LIBRARY, project.extensions.getByType(SimpleDslModuleModel).moduleKind.get())
        assertEquals(25, project.extensions.getByType(JavaPluginExtension).toolchain.languageVersion.get().asInt())
        project.tasks.withType(JavaCompile).each { task -> assertEquals(25, task.options.release.get()) }
    }

    @Test
    void springLibraryClaimsModule() {
        def project = projectWithCatalog()

        project.pluginManager.apply('io.github.qigao.simpledsl.spring-library')

        assertEquals(ModuleKind.SPRING_LIBRARY, project.extensions.getByType(SimpleDslModuleModel).moduleKind.get())
    }

    @Test
    void springServiceClaimsModule() {
        def project = projectWithCatalog()

        project.pluginManager.apply('io.github.qigao.simpledsl.spring-service')

        assertEquals(ModuleKind.SPRING_SERVICE, project.extensions.getByType(SimpleDslModuleModel).moduleKind.get())
    }

    private static def projectWithCatalog() {
        def project = ProjectBuilder.builder().build()
        def catalog = new DependencyCatalogSnapshot(
                25,
                [spring: new CatalogPlatform('spring', 'org.springframework.boot:spring-boot-dependencies', '4.1.0')],
                [
                    'junit-jupiter': new CatalogLibrary('junit-jupiter', 'org.junit.jupiter:junit-jupiter', '5.13.4', null),
                    'junit-platform-launcher': new CatalogLibrary('junit-platform-launcher', 'org.junit.platform:junit-platform-launcher', '1.13.4', null),
                    'spring-core': new CatalogLibrary('spring-core', 'org.springframework.boot:spring-boot-starter', null, 'spring'),
                    'spring-test': new CatalogLibrary('spring-test', 'org.springframework.boot:spring-boot-starter-test', null, 'spring')
                ],
                [:])
        project.extensions.add(DependencyCatalogSnapshot, 'simpledslDependencyCatalog', catalog)
        project
    }
}
