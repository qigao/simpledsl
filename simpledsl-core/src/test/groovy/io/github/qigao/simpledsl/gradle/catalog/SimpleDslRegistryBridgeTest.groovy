package io.github.qigao.simpledsl.gradle.catalog

import org.gradle.api.GradleException
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class SimpleDslRegistryBridgeTest {
    @Test
    void acceptsSchemaTwoSnapshot() {
        def snapshot = SimpleDslRegistryBridge.fromSnapshot([
            schemaVersion: 2,
            policies: [java: [toolchain: 25]],
            platforms: [spring: [module: 'org.springframework.boot:spring-boot-dependencies', version: '4.1.0']],
            libraries: [core: [module: 'org.springframework.boot:spring-boot-starter', platform: 'spring']],
            plugins: [springBoot: [id: 'org.springframework.boot', module: 'org.springframework.boot:spring-boot-gradle-plugin', version: '4.1.0']]
        ])

        assertEquals(25, snapshot.javaToolchainOrNull())
        assertEquals('4.1.0', snapshot.platform('spring').version)
        assertEquals('org.springframework.boot:spring-boot-starter', snapshot.library('core').module)
        assertEquals('org.springframework.boot', snapshot.plugin('springBoot').id)
    }

    @Test
    void acceptsSnapshotWithoutJavaPolicy() {
        def snapshot = SimpleDslRegistryBridge.fromSnapshot([
            schemaVersion: 2,
            policies: [:],
            platforms: [:],
            libraries: [:],
            plugins: [:]
        ])

        assertNull(snapshot.javaToolchainOrNull())
        GradleException error = assertThrows(GradleException) {
            snapshot.requireJavaToolchain(':app')
        }
        assertTrue(error.message.contains('Project: :app'))
        assertTrue(error.message.contains('simpledsl.java'))
    }

    @Test
    void acceptsAndroidPolicyAndDistinguishesApplicationTargetRequirement() {
        def snapshot = SimpleDslRegistryBridge.fromSnapshot([
            schemaVersion: 2,
            policies: [android: [java: 21, compileSdk: 36, minSdk: 24]],
            platforms: [:],
            libraries: [:],
            plugins: [:]
        ])

        def policy = snapshot.requireAndroidPolicy(':feature', false)
        assertEquals(21, policy.javaVersion)
        assertEquals(36, policy.compileSdk)
        assertEquals(24, policy.minSdk)
        assertNull(policy.targetSdk)

        GradleException error = assertThrows(GradleException) {
            snapshot.requireAndroidPolicy(':app', true)
        }
        assertTrue(error.message.contains('Project: :app'))
        assertTrue(error.message.contains('simpledsl.android.target-sdk'))
    }

    @Test
    void requiresAndroidPolicyOnlyWhenAndroidBackendRequestsIt() {
        def snapshot = SimpleDslRegistryBridge.fromSnapshot([
            schemaVersion: 2,
            policies: [:],
            platforms: [:],
            libraries: [:],
            plugins: [:]
        ])

        assertNull(snapshot.androidPolicyOrNull())
        GradleException error = assertThrows(GradleException) {
            snapshot.requireAndroidPolicy(':app', false)
        }
        assertTrue(error.message.contains('Project: :app'))
        assertTrue(error.message.contains('simpledsl.android'))
    }

    @Test
    void rejectsMalformedAndroidPolicyInSnapshot() {
        GradleException error = assertThrows(GradleException) {
            SimpleDslRegistryBridge.fromSnapshot([
                schemaVersion: 2,
                policies: [android: [java: 21, compileSdk: 36, minSdk: '24']],
                platforms: [:],
                libraries: [:],
                plugins: [:]
            ])
        }

        assertTrue(error.message.contains("policy 'android'.minSdk must be an integer"))
    }

    @Test
    void rejectsUnsupportedSnapshotSchema() {
        def error = assertThrows(GradleException) {
            SimpleDslRegistryBridge.fromSnapshot([
                schemaVersion: 99,
                policies: [:],
                platforms: [:],
                libraries: [:],
                plugins: [:]
            ])
        }

        assertTrue(error.message.contains('SimpleDSL bootstrap error'))
        assertTrue(error.message.contains('Problem: unsupported dependency snapshot schema'))
        assertTrue(error.message.contains('Expected: 2'))
        assertTrue(error.message.contains('Actual: 99'))
    }
}
