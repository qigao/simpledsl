package io.github.qigao.simpledsl.gradle.core

import io.github.qigao.simpledsl.gradle.capability.CapabilityEngine
import io.github.qigao.simpledsl.gradle.capability.CapabilityPluginRegistry
import io.github.qigao.simpledsl.gradle.capability.CapabilityRegistry
import io.github.qigao.simpledsl.gradle.catalog.DependencyCatalogSnapshot
import io.github.qigao.simpledsl.gradle.catalog.SimpleDslCatalogPlugin
import io.github.qigao.simpledsl.gradle.diagnostics.SimpleDslCapabilitiesTask
import io.github.qigao.simpledsl.gradle.diagnostics.SimpleDslDoctorTask
import io.github.qigao.simpledsl.gradle.diagnostics.SimpleDslDoctorValidator
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.api.Plugin
import org.gradle.api.Project

class SimpleDslProjectCorePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply(SimpleDslCatalogPlugin)

        DependencyCatalogSnapshot catalog = project.extensions.getByType(DependencyCatalogSnapshot)
        SimpleDslModuleModel model = project.extensions.findByType(SimpleDslModuleModel)
        if (model == null) {
            model = project.extensions.create('simpledslModuleModel', SimpleDslModuleModel)
            model.capabilities.convention(Collections.emptySet())
            model.platformBindings.convention(Collections.emptySet())
        }

        CapabilityRegistry capabilityRegistry = project.extensions.findByType(CapabilityRegistry)
        if (capabilityRegistry == null) {
            capabilityRegistry = new CapabilityRegistry()
            project.extensions.add(CapabilityRegistry, 'simpledslCapabilityRegistry', capabilityRegistry)
        }
        CapabilityPluginRegistry pluginRegistry = project.extensions.findByType(CapabilityPluginRegistry)
        if (pluginRegistry == null) {
            pluginRegistry = new CapabilityPluginRegistry()
            project.extensions.add(CapabilityPluginRegistry, 'simpledslCapabilityPluginRegistry', pluginRegistry)
        }
        if (project.extensions.findByType(CapabilityEngine) == null) {
            project.extensions.add(
                    CapabilityEngine,
                    'simpledslCapabilityEngine',
                    new CapabilityEngine(project, model, catalog, capabilityRegistry, pluginRegistry))
        }

        def sortedCapabilities = model.capabilities.map { values -> new ArrayList<String>(new TreeSet<String>(values)) }
        def sortedBindings = model.platformBindings.map { values -> new ArrayList<String>(new TreeSet<String>(values)) }
        def backendName = model.backendId.orElse('NONE')
        def moduleTypeName = model.moduleType.orElse('NONE')
        String javaPolicy = catalog.javaToolchainOrNull() == null ? 'not configured' : catalog.javaToolchainOrNull().toString()

        if (project.tasks.findByName('simpledslCapabilities') == null) {
            project.tasks.register('simpledslCapabilities', SimpleDslCapabilitiesTask) { task ->
                task.group = 'SimpleDSL'
                task.description = 'Print SimpleDSL backend, module type and active capabilities.'
                task.backendId.set(backendName)
                task.moduleType.set(moduleTypeName)
                task.javaPolicy.set(javaPolicy)
                task.capabilities.set(sortedCapabilities)
                task.platformBindings.set(sortedBindings)
            }
        }

        if (project.tasks.findByName('simpledslDoctor') == null) {
            def doctor = project.tasks.register('simpledslDoctor', SimpleDslDoctorTask) { task ->
                task.group = 'SimpleDSL'
                task.description = 'Validate SimpleDSL module configuration consistency.'
                task.projectPathInput.set(project.path)
                task.backendId.set(backendName)
                task.moduleType.set(moduleTypeName)
                task.capabilities.set(sortedCapabilities)
                task.platformBindings.set(sortedBindings)
                task.violations.convention(Collections.emptyList())
            }
            project.afterEvaluate {
                List<String> violations = SimpleDslDoctorValidator.validate(
                        project.path, model, capabilityRegistry, pluginRegistry, catalog)
                doctor.configure { task -> task.violations.set(violations) }
            }
        }
    }
}
