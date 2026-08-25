package io.github.qigao.simpledsl.gradle

import io.github.qigao.simpledsl.gradle.catalog.DependencyCatalogSnapshot
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

import java.lang.reflect.InvocationTargetException

import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class JavaBackendContractTest {
    @Test
    void JavaBackendRejectsMissingJavaPolicyExplicitly() {
        def project = ProjectBuilder.builder().withName('app').build()
        project.extensions.add(
                DependencyCatalogSnapshot,
                'simpledslDependencyCatalog',
                new DependencyCatalogSnapshot(null, null, [:], [:], [:]))

        Class<?> pluginType = Class.forName('io.github.qigao.simpledsl.gradle.java.SimpleDslJavaPlugin')

        Throwable error = assertThrows(Throwable) {
            try {
                project.pluginManager.apply(pluginType)
            } catch (InvocationTargetException wrapper) {
                throw wrapper.cause
            }
        }

        Throwable root = error
        while (root.cause != null && root.cause != root) {
            root = root.cause
        }
        assertTrue((error.message ?: root.message).contains('SimpleDSL Java policy is missing'))
        assertTrue((error.message ?: root.message).contains('Project: :app'))
    }
}
