package io.github.qigao.simpledsl.gradle.android

import org.gradle.api.provider.Property

abstract class SimpleDslAndroidLibrarySpec {
    abstract Property<String> getNamespace()

    void setNamespace(String value) { namespace.set(value) }
}
