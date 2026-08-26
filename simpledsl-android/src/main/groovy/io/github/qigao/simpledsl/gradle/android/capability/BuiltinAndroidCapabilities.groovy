package io.github.qigao.simpledsl.gradle.android.capability

import io.github.qigao.simpledsl.gradle.capability.CapabilityRegistry
import io.github.qigao.simpledsl.gradle.capability.CapabilitySpec

final class BuiltinAndroidCapabilities {
    static final CapabilitySpec COMPOSE = CapabilitySpec.builder('compose')
            .allow('android-application', 'android-library')
            .externalPluginId('org.jetbrains.kotlin.plugin.compose')
            .dependency('implementation', 'compose-runtime')
            .dependency('implementation', 'compose-ui')
            .build()

    static void registerAll(CapabilityRegistry registry) {
        registry.register(COMPOSE)
    }

    private BuiltinAndroidCapabilities() {}
}
