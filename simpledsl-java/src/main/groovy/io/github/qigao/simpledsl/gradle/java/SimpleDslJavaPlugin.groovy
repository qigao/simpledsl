package io.github.qigao.simpledsl.gradle.java

import io.github.qigao.simpledsl.gradle.capability.BuiltinCapabilities
import io.github.qigao.simpledsl.gradle.capability.CapabilityRegistry
import io.github.qigao.simpledsl.gradle.core.SimpleDslBackendGuard
import io.github.qigao.simpledsl.gradle.core.SimpleDslProjectCorePlugin
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.api.Plugin
import org.gradle.api.Project

class SimpleDslJavaPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        SimpleDslBackendGuard.claim(project, 'java')
        project.pluginManager.apply(SimpleDslProjectCorePlugin)

        SimpleDslModuleModel model = project.extensions.getByType(SimpleDslModuleModel)
        model.backendId.set('java')

        CapabilityRegistry registry = project.extensions.getByType(CapabilityRegistry)
        BuiltinCapabilities.registerAll(registry)

        if (project.extensions.findByName('simpledsl') == null) {
            project.extensions.create('simpledsl', SimpleDslJavaExtension, project, model)
        }
    }
}
