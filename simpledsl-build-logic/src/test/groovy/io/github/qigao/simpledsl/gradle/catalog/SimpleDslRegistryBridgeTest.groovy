package io.github.qigao.simpledsl.gradle.catalog

import org.gradle.api.GradleException
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class SimpleDslRegistryBridgeTest {
    @Test
    void acceptsSchemaOneSnapshot() {
        def snapshot = SimpleDslRegistryBridge.fromSnapshot([
            schemaVersion: 1,
            javaVersion: 25,
            platforms: [spring: [module: 'org.springframework.boot:spring-boot-dependencies', version: '4.1.0']],
            libraries: [core: [module: 'org.springframework.boot:spring-boot-starter', platform: 'spring']],
            plugins: [springBoot: [id: 'org.springframework.boot', module: 'org.springframework.boot:spring-boot-gradle-plugin', version: '4.1.0']]
        ])

        assertEquals(25, snapshot.javaVersion)
        assertEquals('4.1.0', snapshot.platform('spring').version)
        assertEquals('org.springframework.boot:spring-boot-starter', snapshot.library('core').module)
        assertEquals('org.springframework.boot', snapshot.plugin('springBoot').id)
    }

    @Test
    void rejectsUnsupportedSnapshotSchema() {
        def error = assertThrows(GradleException) {
            SimpleDslRegistryBridge.fromSnapshot([
                schemaVersion: 99,
                javaVersion: 25,
                platforms: [:],
                libraries: [:],
                plugins: [:]
            ])
        }

        assertTrue(error.message.contains('SimpleDSL bootstrap error'))
        assertTrue(error.message.contains('Problem: unsupported dependency snapshot schema'))
        assertTrue(error.message.contains('Expected: 1'))
        assertTrue(error.message.contains('Actual: 99'))
    }
}
