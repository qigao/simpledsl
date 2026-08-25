package io.github.qigao.simpledsl.gradle

import io.github.qigao.simpledsl.gradle.capability.BuiltinCapabilities
import io.github.qigao.simpledsl.gradle.capability.CapabilityRegistry
import io.github.qigao.simpledsl.gradle.core.SimpleDslProjectCorePlugin
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.api.Plugin
import org.gradle.api.Project

class SimpleDslModulePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply(SimpleDslProjectCorePlugin)

        CapabilityRegistry capabilityRegistry = project.extensions.getByType(CapabilityRegistry)
        BuiltinCapabilities.registerAll(capabilityRegistry)

        if (project.extensions.findByName('simpledsl') == null) {
            SimpleDslModuleModel model = project.extensions.getByType(SimpleDslModuleModel)
            project.extensions.create('simpledsl', SimpleDslExtension, project, model)
        }
    }
}
