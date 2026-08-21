package io.github.qigao.simpledsl.gradle.schema

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

final class SimpleDslJsonSchemaPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply('org.jsonschema2pojo')

        SimpleDslJsonSchemaExtension schema = project.extensions.create(
                'simpledslJsonSchema',
                SimpleDslJsonSchemaExtension)
        def generatedDir = project.layout.buildDirectory.dir('generated-src/json/main')

        project.pluginManager.withPlugin('java') {
            def sourceSets = project.extensions.getByName('sourceSets')
            sourceSets.getByName('main').java.srcDir(generatedDir)
        }

        project.afterEvaluate {
            if (!schema.packageName) {
                throw new GradleException('simpledslJsonSchema.packageName must be configured')
            }

            def jsonSchema2Pojo = project.extensions.getByName('jsonSchema2Pojo')
            jsonSchema2Pojo.targetPackage = schema.packageName
            jsonSchema2Pojo.source = project.files(project.file(schema.source))
            jsonSchema2Pojo.targetDirectory = generatedDir.get().asFile
            jsonSchema2Pojo.propertyWordDelimiters = '_'
            jsonSchema2Pojo.includeJsr303Annotations = schema.validation
            jsonSchema2Pojo.useJakartaValidation = schema.validation
            jsonSchema2Pojo.includeGetters = schema.getters
            jsonSchema2Pojo.includeSetters = schema.setters
            jsonSchema2Pojo.includeToString = schema.toString
            jsonSchema2Pojo.includeHashcodeAndEquals = schema.equalsAndHashCode
            jsonSchema2Pojo.generateBuilders = schema.builders
        }
    }
}
