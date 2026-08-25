package io.github.qigao.simpledsl.gradle.capability

import io.github.qigao.simpledsl.gradle.SimpleDslConfigurationException
import io.github.qigao.simpledsl.gradle.catalog.DependencyCatalogSnapshot
import io.github.qigao.simpledsl.gradle.dependency.DependencyBridge
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.api.Project

final class CapabilityEngine {
    private final Project project
    private final SimpleDslModuleModel model
    private final DependencyCatalogSnapshot catalog
    private final CapabilityRegistry registry
    private final CapabilityPluginRegistry pluginRegistry
    private final Deque<String> activationStack = new ArrayDeque<>()

    CapabilityEngine(
            Project project,
            SimpleDslModuleModel model,
            DependencyCatalogSnapshot catalog,
            CapabilityRegistry registry,
            CapabilityPluginRegistry pluginRegistry) {
        this.project = project
        this.model = model
        this.catalog = catalog
        this.registry = registry
        this.pluginRegistry = pluginRegistry
    }

    synchronized void enable(String capabilityId) {
        if (model.capabilities.get().contains(capabilityId)) return

        CapabilitySpec spec = registry.get(capabilityId)
        if (spec == null) fail(capabilityId, "capability '${capabilityId}' is not registered")

        if (activationStack.contains(capabilityId)) {
            List<String> cycle = new ArrayList<>(activationStack)
            int start = cycle.indexOf(capabilityId)
            cycle = cycle.subList(start, cycle.size())
            cycle.add(capabilityId)
            fail(capabilityId, "capability requirement cycle detected: ${cycle.join(' -> ')}")
        }

        validateModule(spec)
        validateConflicts(spec)

        activationStack.addLast(capabilityId)
        try {
            spec.requires.each { requiredId ->
                if (!registry.contains(requiredId)) {
                    fail(capabilityId, "required capability '${requiredId}' is not registered")
                }
                enable(requiredId)
            }
            validateConflicts(spec)
            spec.externalPluginIds.each { pluginId -> project.pluginManager.apply(pluginId) }
            spec.dependencies.each { binding ->
                DependencyBridge.add(project, model, binding.configuration, binding.libraryAlias)
            }
            model.enableCapability(capabilityId)
        } finally {
            activationStack.removeLastOccurrence(capabilityId)
        }
    }

    private void validateModule(CapabilitySpec spec) {
        if (spec.allowedModules.isEmpty()) return
        if (!model.moduleType.isPresent()) {
            fail(spec.id, "capability '${spec.id}' requires a module type; allowed: ${spec.allowedModules.join(',')}")
        }
        String current = model.moduleType.get()
        if (!spec.allowedModules.contains(current)) {
            throw new SimpleDslConfigurationException(
                    'SimpleDSL configuration error\n' +
                    "Project: ${project.path}\n" +
                    "Capability: ${spec.id}\n" +
                    "Module type: ${current}\n" +
                    "Problem: capability '${spec.id}' is not supported by ${current}\n" +
                    "Allowed module types: ${spec.allowedModules.join(',')}")
        }
    }

    private void validateConflicts(CapabilitySpec requested) {
        Set<String> active = model.capabilities.get()
        requested.conflicts.each { conflictId ->
            if (active.contains(conflictId)) conflict(requested.id, conflictId)
        }
        active.each { activeId ->
            CapabilitySpec activeSpec = registry.get(activeId)
            if (activeSpec != null && activeSpec.conflicts.contains(requested.id)) {
                conflict(requested.id, activeId)
            }
        }
    }

    private void conflict(String requested, String existing) {
        throw new SimpleDslConfigurationException(
                'SimpleDSL configuration error\n' +
                "Project: ${project.path}\n" +
                "Capability: ${requested}\n" +
                "Problem: conflicts with enabled capability '${existing}'")
    }

    private void fail(String capabilityId, String problem) {
        throw new SimpleDslConfigurationException(
                'SimpleDSL configuration error\n' +
                "Project: ${project.path}\n" +
                "Capability: ${capabilityId}\n" +
                "Problem: ${problem}")
    }
}
