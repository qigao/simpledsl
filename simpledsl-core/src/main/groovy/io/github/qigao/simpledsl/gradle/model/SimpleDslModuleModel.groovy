package io.github.qigao.simpledsl.gradle.model

import io.github.qigao.simpledsl.gradle.SimpleDslConfigurationException
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

abstract class SimpleDslModuleModel {
    abstract Property<String> getBackendId()
    abstract Property<String> getModuleType()
    abstract SetProperty<String> getCapabilities()
    abstract SetProperty<String> getPlatformBindings()

    void claim(String requested, String projectPath) {
        if (!moduleType.isPresent()) {
            moduleType.set(requested)
            return
        }
        String existing = moduleType.get()
        if (existing != requested) {
            throw SimpleDslConfigurationException.moduleTypeConflict(projectPath, existing, requested)
        }
    }

    void enableCapability(String capability) {
        capabilities.add(capability)
    }

    void bindPlatform(String configuration, String platformAlias) {
        platformBindings.add("${configuration}:${platformAlias}" as String)
    }
}
