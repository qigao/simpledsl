package io.github.qigao.simpledsl.gradle.capability

import io.github.qigao.simpledsl.gradle.SimpleDslConfigurationException

final class CapabilityPluginRegistry {
    private final Map<String, String> mappings = new LinkedHashMap<>()

    synchronized void register(String pluginId, String capabilityId) {
        String existing = mappings.get(pluginId)
        if (existing == null) {
            mappings.put(pluginId, capabilityId)
            return
        }
        if (existing != capabilityId) {
            throw new SimpleDslConfigurationException(
                    'SimpleDSL configuration error\n' +
                    "Gradle plugin id: ${pluginId}\n" +
                    "Problem: plugin is already mapped to primary capability '${existing}'\n" +
                    "Requested capability: ${capabilityId}")
        }
    }

    synchronized String capabilityForPlugin(String pluginId) {
        mappings.get(pluginId)
    }

    synchronized Map<String, String> all() {
        Collections.unmodifiableMap(new LinkedHashMap<>(mappings))
    }
}
