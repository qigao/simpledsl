package io.github.qigao.simpledsl.gradle

import io.github.qigao.simpledsl.gradle.capability.BuiltinCapabilities
import io.github.qigao.simpledsl.gradle.capability.CapabilityRegistry
import io.github.qigao.simpledsl.gradle.catalog.DependencyCatalogSnapshot
import io.github.qigao.simpledsl.gradle.core.SimpleDslBackendGuard
import io.github.qigao.simpledsl.gradle.core.SimpleDslProjectCorePlugin
import io.github.qigao.simpledsl.gradle.diagnostics.SimpleDslCapabilitiesTask
import io.github.qigao.simpledsl.gradle.model.SimpleDslProjectModel
import org.gradle.api.Plugin
import org.gradle.api.Project

class SimpleDslModulePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply(SimpleDslProjectCorePlugin)

        SimpleDslBackendGuard backendGuard = project.extensions.getByType(SimpleDslBackendGuard)
        backendGuard.claim('java')

        SimpleDslProjectModel model = project.extensions.getByType(SimpleDslProjectModel)
        model.backendId.set('java')

        CapabilityRegistry capabilityRegistry = project.extensions.getByType(CapabilityRegistry)
        BuiltinCapabilities.registerAll(capabilityRegistry)

        project.extensions.create('simpledsl', SimpleDslExtension, project, model)

        DependencyCatalogSnapshot catalog = project.extensions.getByType(DependencyCatalogSnapshot)
        def sortedCapabilities = model.capabilities.map { values ->
            new ArrayList<String>(new TreeSet<String>(values))
        }
        def sortedBindings = model.platformBindings.map { values ->
            new ArrayList<String>(new TreeSet<String>(values))
        }
        def moduleTypeName = model.moduleType.orElse('NONE')

        project.tasks.register('simpledslCapabilities', SimpleDslCapabilitiesTask) { task ->
            task.group = 'SimpleDSL'
            task.description = 'Print SimpleDSL module type and active capabilities.'
            task.moduleKind.set(moduleTypeName)
            task.javaVersion.set(catalog.javaVersion())
            task.capabilities.set(sortedCapabilities)
            task.platformBindings.set(sortedBindings)
        }
    }
}
