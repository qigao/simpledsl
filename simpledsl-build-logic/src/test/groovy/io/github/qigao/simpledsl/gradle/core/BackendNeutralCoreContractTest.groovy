package io.github.qigao.simpledsl.gradle.core

import io.github.qigao.simpledsl.gradle.capability.CapabilitySpec
import org.junit.jupiter.api.Test

import java.lang.reflect.InvocationTargetException

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class BackendNeutralCoreContractTest {
    @Test
    void capabilitySpecUsesStringModuleIds() {
        CapabilitySpec spec = CapabilitySpec.builder('web')
                .allow('spring-service', 'android-application')
                .build()

        assertEquals(['spring-service', 'android-application'] as Set, spec.allowedModules)
    }

    @Test
    void backendGuardAllowsSameBackendAndRejectsDifferentBackend() {
        Class<?> guardType = Class.forName('io.github.qigao.simpledsl.gradle.core.SimpleDslBackendGuard')
        Object guard = guardType.getConstructor(String).newInstance(':app')

        guardType.getMethod('claim', String).invoke(guard, 'java')
        guardType.getMethod('claim', String).invoke(guard, 'java')

        InvocationTargetException error = assertThrows(InvocationTargetException) {
            guardType.getMethod('claim', String).invoke(guard, 'android')
        }

        assertTrue(error.cause.message.contains('Project: :app'))
        assertTrue(error.cause.message.contains('already-selected backend: java'))
        assertTrue(error.cause.message.contains('requested backend: android'))
    }

    @Test
    void projectModelTypeExistsForBackendNeutralCore() {
        Class<?> modelType = Class.forName('io.github.qigao.simpledsl.gradle.model.SimpleDslProjectModel')

        assertTrue(modelType.name.endsWith('SimpleDslProjectModel'))
    }
}
