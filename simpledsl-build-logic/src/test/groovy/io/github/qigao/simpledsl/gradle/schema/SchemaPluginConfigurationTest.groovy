package io.github.qigao.simpledsl.gradle.schema

import io.github.qigao.simpledsl.gradle.SimpleDslExtension
import io.github.qigao.simpledsl.gradle.catalog.CatalogLibrary
import io.github.qigao.simpledsl.gradle.catalog.DependencyCatalogSnapshot
import io.github.qigao.simpledsl.gradle.model.SimpleDslProjectModel
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertTrue

class SchemaPluginConfigurationTest {
    @Test
    void jooqSchemaExposesDefaultsAndBinaryMarker() {
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply('java')
        addJooqCatalog(project)

        project.pluginManager.apply(SimpleDslJooqSchemaPlugin)

        def schema = project.extensions.getByType(SimpleDslJooqSchemaExtension)
        assertEquals('database/schema/**/*.sql', schema.source)
        assertEquals('semantic', schema.sort)
        assertEquals('Q', schema.tablePrefix)
        assertEquals('R', schema.recordPrefix)
        assertTrue(project.pluginManager.hasPlugin('org.jooq.jooq-codegen-gradle'))
    }

    @Test
    void jsonSchemaExposesDefaultsAndBinaryMarker() {
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply('java')

        project.pluginManager.apply(SimpleDslJsonSchemaPlugin)

        def schema = project.extensions.getByType(SimpleDslJsonSchemaExtension)
        assertEquals('json', schema.source)
        assertTrue(schema.validation)
        assertTrue(schema.builders)
        assertTrue(schema.getters)
        assertTrue(schema.setters)
        assertTrue(schema.toString)
        assertTrue(schema.equalsAndHashCode)
        assertTrue(project.pluginManager.hasPlugin('org.jsonschema2pojo'))
    }

    @Test
    void simpleDslExtensionActivatesJooqSchema() {
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply('java')
        addJooqCatalog(project)
        def model = project.objects.newInstance(SimpleDslProjectModel)
        def simpledsl = new SimpleDslExtension(project, model)

        simpledsl.jooqSchema()

        assertNotNull(project.extensions.findByName('simpledslJooq'))
        assertTrue(project.pluginManager.hasPlugin('org.jooq.jooq-codegen-gradle'))
    }

    @Test
    void simpleDslExtensionActivatesJsonSchema() {
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply('java')
        def model = project.objects.newInstance(SimpleDslProjectModel)
        def simpledsl = new SimpleDslExtension(project, model)

        simpledsl.jsonSchema()

        assertNotNull(project.extensions.findByName('simpledslJsonSchema'))
        assertTrue(project.pluginManager.hasPlugin('org.jsonschema2pojo'))
    }

    private static void addJooqCatalog(project) {
        project.extensions.add(
                DependencyCatalogSnapshot,
                'simpledslDependencyCatalog',
                new DependencyCatalogSnapshot(
                        25,
                        [:],
                        [
                                'jooq-meta-extensions': new CatalogLibrary(
                                        'jooq-meta-extensions',
                                        'org.jooq:jooq-meta-extensions',
                                        '3.21.5',
                                        null),
                                'jooq-core': new CatalogLibrary(
                                        'jooq-core',
                                        'org.jooq:jooq',
                                        '3.21.5',
                                        null)
                        ],
                        [:]))
    }
}
