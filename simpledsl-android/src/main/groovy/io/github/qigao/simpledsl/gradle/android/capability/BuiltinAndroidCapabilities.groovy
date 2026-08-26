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

    static final CapabilitySpec KSP = CapabilitySpec.builder('ksp')
            .allow('android-application', 'android-library')
            .externalPluginId('com.google.devtools.ksp')
            .build()

    static final CapabilitySpec ROOM = CapabilitySpec.builder('room')
            .allow('android-application', 'android-library')
            .require('ksp')
            .dependency('implementation', 'room-runtime')
            .dependency('ksp', 'room-compiler')
            .build()

    static final CapabilitySpec HILT = CapabilitySpec.builder('hilt')
            .allow('android-application', 'android-library')
            .require('ksp')
            .externalPluginId('com.google.dagger.hilt.android')
            .dependency('implementation', 'hilt-android')
            .dependency('ksp', 'hilt-compiler')
            .build()

    static void registerAll(CapabilityRegistry registry) {
        registry.register(COMPOSE)
        registry.register(KSP)
        registry.register(ROOM)
        registry.register(HILT)
    }

    private BuiltinAndroidCapabilities() {}
}
