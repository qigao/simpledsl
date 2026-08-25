package io.github.qigao.simpledsl.gradle.catalog

import org.gradle.api.GradleException
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class SimpleDslRegistryBridgeV2Test {
    @Test
    void acceptsJavaPolicyInSchemaTwo() {
        def snapshot = SimpleDslRegistryBridge.fromSnapshot([
                schemaVersion: 2,
                policies: [java: [toolchain: 25]],
                platforms: [:],
                libraries: [:],
                plugins: [:]
        ])

        assertEquals(25, snapshot.javaVersionOrNull())
        assertNull(snapshot.androidPolicy())
    }

    @Test
    void acceptsAndroidOnlyPolicyInSchemaTwo() {
        def snapshot = SimpleDslRegistryBridge.fromSnapshot([
                schemaVersion: 2,
                policies: [android: [java: 21, compileSdk: 36, minSdk: 24]],
                platforms: [:],
                libraries: [:],
                plugins: [:]
        ])

        assertNull(snapshot.javaVersionOrNull())
        assertEquals(21, snapshot.androidPolicy().javaVersion)
        assertEquals(36, snapshot.androidPolicy().compileSdk)
        assertEquals(24, snapshot.androidPolicy().minSdk)
        assertNull(snapshot.androidPolicy().targetSdk)
    }

    @Test
    void rejectsSchemaOneSnapshotAfterProtocolBreak() {
        GradleException error = assertThrows(GradleException) {
            SimpleDslRegistryBridge.fromSnapshot([
                    schemaVersion: 1,
                    javaVersion: 25,
                    platforms: [:],
                    libraries: [:],
                    plugins: [:]
            ])
        }

        assertTrue(error.message.contains('unsupported dependency snapshot schema'))
        assertTrue(error.message.contains('Expected: 2'))
        assertTrue(error.message.contains('Actual: 1'))
    }
}
