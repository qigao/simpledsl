package io.github.qigao.simpledsl.gradle.android

import org.gradle.api.provider.Property

abstract class SimpleDslAndroidDynamicFeatureSpec {
    abstract Property<String> getNamespace()
    abstract Property<String> getBaseModule()
}
