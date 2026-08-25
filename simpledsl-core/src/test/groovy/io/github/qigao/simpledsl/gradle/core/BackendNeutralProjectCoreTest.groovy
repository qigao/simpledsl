package io.github.qigao.simpledsl.gradle.core

import io.github.qigao.simpledsl.gradle.capability.CapabilitySpec
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class BackendNeutralProjectCoreTest {
    @Test
    void capabilityAllowsStringModuleIds() {
        CapabilitySpec spec = CapabilitySpec.builder('web')
                .allow('spring-service')
                .build()

        assertEquals(['spring-service'] as Set, spec.allowedModules)
    }

    @Test
    void backendGuardIsIdempotentForSameBackend() {
        def project = ProjectBuilder.builder().withName('app').build()

        SimpleDslBackendGuard.claim(project, 'java')
        SimpleDslBackendGuard.claim(project, 'java')

        assertEquals('java', project.extensions.extraProperties.get('io.github.qigao.simpledsl.backend'))
    }

    @Test
    void backendGuardRejectsSecondDifferentBackend() {
        def project = ProjectBuilder.builder().withName('app').build()
        SimpleDslBackendGuard.claim(project, 'java')

        GradleException error = assertThrows(GradleException) {
            SimpleDslBackendGuard.claim(project, 'android')
        }

        assertTrue(error.message.contains('SimpleDSL backend conflict'))
        assertTrue(error.message.contains('Project: :'))
        assertTrue(error.message.contains('Already-selected backend: java'))
        assertTrue(error.message.contains('Requested backend: android'))
    }
}
