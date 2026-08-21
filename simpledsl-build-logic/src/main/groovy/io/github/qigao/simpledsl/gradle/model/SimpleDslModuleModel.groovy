package io.github.qigao.simpledsl.gradle.model

import io.github.qigao.simpledsl.gradle.SimpleDslConfigurationException
import io.github.qigao.simpledsl.gradle.ModuleKind
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

abstract class SimpleDslModuleModel {
    abstract Property<ModuleKind> getModuleKind()
    abstract SetProperty<String> getCapabilities()
    abstract SetProperty<String> getPlatformBindings()

    void claim(ModuleKind requested, String projectPath) {
        if (!moduleKind.isPresent()) {
            moduleKind.set(requested)
            return
        }
        ModuleKind existing = moduleKind.get()
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
