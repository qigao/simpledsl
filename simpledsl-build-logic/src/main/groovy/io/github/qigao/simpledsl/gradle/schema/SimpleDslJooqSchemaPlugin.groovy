package io.github.qigao.simpledsl.gradle.schema

import io.github.qigao.simpledsl.gradle.SimpleDslDependencyAccess
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jooq.meta.jaxb.MatcherTransformType

final class SimpleDslJooqSchemaPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply('org.jooq.jooq-codegen-gradle')
        project.pluginManager.apply('io.github.qigao.simpledsl.internal.catalog')

        SimpleDslJooqSchemaExtension schema = project.extensions.create(
                'simpledslJooq',
                SimpleDslJooqSchemaExtension)
        def catalog = SimpleDslDependencyAccess.catalog(project)

        project.dependencies.add(
                'jooqCodegen',
                catalog.library('jooq-meta-extensions').notation())
        project.dependencies.add(
                'implementation',
                catalog.library('jooq-core').notation())

        project.pluginManager.withPlugin('java') {
            project.tasks.named('compileJava').configure {
                dependsOn(project.tasks.named('jooqCodegen'))
            }
        }

        project.afterEvaluate {
            if (!schema.packageName) {
                throw new GradleException('simpledslJooq.packageName must be configured')
            }

            def jooq = project.extensions.getByName('jooq')
            jooq.configuration {
                generator {
                    database {
                        name = 'org.jooq.meta.extensions.ddl.DDLDatabase'
                        properties {
                            property {
                                key = 'scripts'
                                value = schema.source
                            }
                            property {
                                key = 'sort'
                                value = schema.sort
                            }
                            property {
                                key = 'unqualifiedSchema'
                                value = 'none'
                            }
                            property {
                                key = 'defaultNameCase'
                                value = 'as_is'
                            }
                        }
                    }
                    strategy {
                        matchers {
                            tables {
                                table {
                                    tableClass {
                                        transform = MatcherTransformType.PASCAL
                                        expression = "${schema.tablePrefix}_\$0"
                                    }
                                    recordClass {
                                        transform = MatcherTransformType.PASCAL
                                        expression = "${schema.recordPrefix}_\$0"
                                    }
                                }
                            }
                        }
                    }
                    generate {
                        records = true
                        fluentSetters = true
                    }
                    target {
                        packageName = schema.packageName
                        directory = project.layout.buildDirectory
                                .dir('generated-src/jooq/main')
                                .get()
                                .asFile
                                .absolutePath
                    }
                }
            }
        }
    }
}
