package io.github.qigao.simpledsl.gradle.schema

class SimpleDslJooqSchemaExtension {
    String source = 'database/schema/**/*.sql'
    String packageName
    String sort = 'semantic'
    String tablePrefix = 'Q'
    String recordPrefix = 'R'
}
