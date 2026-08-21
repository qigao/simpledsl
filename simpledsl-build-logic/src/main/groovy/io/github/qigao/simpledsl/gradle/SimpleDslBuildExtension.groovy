package io.github.qigao.simpledsl.gradle

import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

/**
 * Public DSL container. Concrete capabilities are loaded from TOML.
 */
class SimpleDslBuildExtension {
    final Map<String, Object> capabilities = new LinkedHashMap<>()

    @Inject
    SimpleDslBuildExtension(ObjectFactory objects) {
    }

    void capability(String name, Object value = true) {
        capabilities.put(name, value)
    }
}
