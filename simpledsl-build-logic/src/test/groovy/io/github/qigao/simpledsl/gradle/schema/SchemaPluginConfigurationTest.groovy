package io.github.qigao.simpledsl.gradle.schema

import io.github.qigao.simpledsl.gradle.catalog.CatalogLibrary
import io.github.qigao.simpledsl.gradle.catalog.DependencyCatalogSnapshot
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

class SchemaPluginConfigurationTest {
    @Test
    void jooqSchemaExposesDefaultsAndBinaryMarker() {
        def project = ProjectBuilder.builder().build()
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

        project.pluginManager.apply('io.github.qigao.simpledsl.schema.jooq')

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

        project.pluginManager.apply('io.github.qigao.simpledsl.schema.json')

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
}
