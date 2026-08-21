package io.github.qigao.simpledsl.gradle.schema

final class SimpleDslJsonSchemaExtension {
    String source = 'json'
    String packageName
    boolean validation = true
    boolean builders = true
    boolean getters = true
    boolean setters = true
    boolean toString = true
    boolean equalsAndHashCode = true
}
