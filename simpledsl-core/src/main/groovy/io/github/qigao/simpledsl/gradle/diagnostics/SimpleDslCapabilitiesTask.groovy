package io.github.qigao.simpledsl.gradle.diagnostics

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class SimpleDslCapabilitiesTask extends DefaultTask {
    @Input abstract Property<String> getBackendId()
    @Input abstract Property<String> getModuleType()
    @Input abstract Property<String> getJavaPolicy()
    @Input abstract ListProperty<String> getCapabilities()
    @Input abstract ListProperty<String> getPlatformBindings()

    @TaskAction
    void report() {
        List<String> capabilityValues = capabilities.get()
        List<String> bindings = platformBindings.get()
        Set<String> platforms = bindings.collect { binding ->
            binding.substring(binding.indexOf(':') + 1)
        } as TreeSet<String>

        println "Backend: ${backendId.get()}"
        println "Type: ${moduleType.get()}"
        println "Java: ${javaPolicy.get()}"
        println "Platforms: ${platforms.join(',')}"
        println "Platform bindings: ${bindings.join(',')}"
        println "Features: ${capabilityValues.join(',')}"
        println "Native: ${capabilityValues.contains('native') ? 'enabled' : 'disabled'}"
    }
}
