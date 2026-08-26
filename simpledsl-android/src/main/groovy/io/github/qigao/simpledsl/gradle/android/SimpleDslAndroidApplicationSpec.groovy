package io.github.qigao.simpledsl.gradle.android

import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

abstract class SimpleDslAndroidApplicationSpec {
    abstract Property<String> getNamespace()
    abstract Property<String> getApplicationId()
    abstract SetProperty<String> getDynamicFeatures()

    void dynamicFeature(String path) {
        dynamicFeatures.add(path)
    }
}
