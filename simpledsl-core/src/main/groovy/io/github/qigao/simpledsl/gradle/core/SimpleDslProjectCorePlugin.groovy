package io.github.qigao.simpledsl.gradle.core

import io.github.qigao.simpledsl.gradle.capability.CapabilityEngine
import io.github.qigao.simpledsl.gradle.capability.CapabilityPluginRegistry
import io.github.qigao.simpledsl.gradle.capability.CapabilityRegistry
import io.github.qigao.simpledsl.gradle.catalog.DependencyCatalogSnapshot
import io.github.qigao.simpledsl.gradle.catalog.SimpleDslCatalogPlugin
import io.github.qigao.simpledsl.gradle.diagnostics.SimpleDslDoctorTask
import io.github.qigao.simpledsl.gradle.diagnostics.SimpleDslDoctorValidator
import io.github.qigao.simpledsl.gradle.model.SimpleDslProjectModel
import org.gradle.api.Plugin
import org.gradle.api.Project

final class SimpleDslProjectCorePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply(SimpleDslCatalogPlugin)

        DependencyCatalogSnapshot catalog = project.extensions.getByType(DependencyCatalogSnapshot)
        SimpleDslProjectModel model = project.extensions.create('simpledslProjectModel', SimpleDslProjectModel)
        model.capabilities.convention(Collections.emptySet())
        model.platformBindings.convention(Collections.emptySet())

        SimpleDslBackendGuard backendGuard = new SimpleDslBackendGuard(project.path)
        CapabilityRegistry capabilityRegistry = new CapabilityRegistry()
        CapabilityPluginRegistry pluginRegistry = new CapabilityPluginRegistry()
        CapabilityEngine capabilityEngine = new CapabilityEngine(
                project, model, catalog, capabilityRegistry, pluginRegistry)

        project.extensions.add(SimpleDslBackendGuard, 'simpledslBackendGuard', backendGuard)
        project.extensions.add(CapabilityRegistry, 'simpledslCapabilityRegistry', capabilityRegistry)
        project.extensions.add(CapabilityPluginRegistry, 'simpledslCapabilityPluginRegistry', pluginRegistry)
        project.extensions.add(CapabilityEngine, 'simpledslCapabilityEngine', capabilityEngine)

        def sortedCapabilities = model.capabilities.map { values ->
            new ArrayList<String>(new TreeSet<String>(values))
        }
        def sortedBindings = model.platformBindings.map { values ->
            new ArrayList<String>(new TreeSet<String>(values))
        }
        def moduleTypeName = model.moduleType.orElse('NONE')

        def doctor = project.tasks.register('simpledslDoctor', SimpleDslDoctorTask) { task ->
            task.group = 'SimpleDSL'
            task.description = 'Validate SimpleDSL module configuration consistency.'
            task.projectPathInput.set(project.path)
            task.moduleKind.set(moduleTypeName)
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
