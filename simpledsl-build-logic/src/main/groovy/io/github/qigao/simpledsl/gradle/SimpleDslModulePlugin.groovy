package io.github.qigao.simpledsl.gradle

import io.github.qigao.simpledsl.gradle.capability.BuiltinCapabilities
import io.github.qigao.simpledsl.gradle.capability.CapabilityEngine
import io.github.qigao.simpledsl.gradle.capability.CapabilityPluginRegistry
import io.github.qigao.simpledsl.gradle.capability.CapabilityRegistry
import io.github.qigao.simpledsl.gradle.catalog.DependencyCatalogSnapshot
import io.github.qigao.simpledsl.gradle.diagnostics.SimpleDslCapabilitiesTask
import io.github.qigao.simpledsl.gradle.diagnostics.SimpleDslDoctorTask
import io.github.qigao.simpledsl.gradle.diagnostics.SimpleDslDoctorValidator
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.api.Plugin
import org.gradle.api.Project

class SimpleDslModulePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply('io.github.qigao.simpledsl.internal.catalog')

        DependencyCatalogSnapshot catalog = project.extensions.getByType(DependencyCatalogSnapshot)
        SimpleDslModuleModel model = project.extensions.create('simpledslModuleModel', SimpleDslModuleModel)
        model.capabilities.convention(Collections.emptySet())
        model.platformBindings.convention(Collections.emptySet())

        CapabilityRegistry capabilityRegistry = new CapabilityRegistry()
        CapabilityPluginRegistry pluginRegistry = new CapabilityPluginRegistry()
        CapabilityEngine capabilityEngine = new CapabilityEngine(
                project, model, catalog, capabilityRegistry, pluginRegistry)
        project.extensions.add(CapabilityRegistry, 'simpledslCapabilityRegistry', capabilityRegistry)
        project.extensions.add(CapabilityPluginRegistry, 'simpledslCapabilityPluginRegistry', pluginRegistry)
        project.extensions.add(CapabilityEngine, 'simpledslCapabilityEngine', capabilityEngine)
        BuiltinCapabilities.registerAll(capabilityRegistry)

        project.extensions.create('simpledsl', SimpleDslExtension, project, model)

        def sortedCapabilities = model.capabilities.map { values ->
            new ArrayList<String>(new TreeSet<String>(values))
        }
        def sortedBindings = model.platformBindings.map { values ->
            new ArrayList<String>(new TreeSet<String>(values))
        }
        def moduleKindName = model.moduleKind.map { it.name() }.orElse('NONE')

        project.tasks.register('simpledslCapabilities', SimpleDslCapabilitiesTask) { task ->
            task.group = 'SimpleDSL'
            task.description = 'Print SimpleDSL module type and active capabilities.'
            task.moduleKind.set(moduleKindName)
            task.javaVersion.set(catalog.javaVersion())
            task.capabilities.set(sortedCapabilities)
            task.platformBindings.set(sortedBindings)
        }

        def doctor = project.tasks.register('simpledslDoctor', SimpleDslDoctorTask) { task ->
            task.group = 'SimpleDSL'
            task.description = 'Validate SimpleDSL module configuration consistency.'
            task.projectPathInput.set(project.path)
            task.moduleKind.set(moduleKindName)
            task.capabilities.set(sortedCapabilities)
            task.platformBindings.set(sortedBindings)
            task.violations.convention(Collections.emptyList())
        }

        project.afterEvaluate {
            List<String> violations = SimpleDslDoctorValidator.validate(
                    project.path, model, capabilityRegistry, pluginRegistry, catalog)
            doctor.configure { task ->
                task.violations.set(violations)
            }
        }
    }
}
