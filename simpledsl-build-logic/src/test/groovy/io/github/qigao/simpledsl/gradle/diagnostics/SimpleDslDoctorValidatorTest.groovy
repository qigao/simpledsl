package io.github.qigao.simpledsl.gradle.diagnostics

import io.github.qigao.simpledsl.gradle.ModuleKind
import io.github.qigao.simpledsl.gradle.capability.CapabilityPluginRegistry
import io.github.qigao.simpledsl.gradle.capability.CapabilityRegistry
import io.github.qigao.simpledsl.gradle.capability.CapabilitySpec
import io.github.qigao.simpledsl.gradle.catalog.DependencyCatalogSnapshot
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

class SimpleDslDoctorValidatorTest {
    @Test
    void releaseOwnedExternalPluginIdsDoNotRequireConsumerCatalogAliases() {
        def project = ProjectBuilder.builder().build()
        def model = project.extensions.create('simpledslModuleModelForTest', SimpleDslModuleModel)
        model.capabilities.convention(Collections.emptySet())
        model.platformBindings.convention(Collections.emptySet())
        model.claim(ModuleKind.SPRING_SERVICE, project.path)
        model.enableCapability('native')

        def registry = new CapabilityRegistry()
        registry.register(
                CapabilitySpec.builder('native')
                        .allow(ModuleKind.SPRING_SERVICE)
                        .externalPluginId('org.graalvm.buildtools.native')
                        .build())

        def pluginRegistry = new CapabilityPluginRegistry()
        pluginRegistry.register('io.github.qigao.simpledsl.feature.native', 'native')

        def catalog = new DependencyCatalogSnapshot(25, [:], [:], [:])

        assertEquals(
                [],
                SimpleDslDoctorValidator.validate(
                        project.path,
                        model,
                        registry,
                        pluginRegistry,
                        catalog))
    }
}
