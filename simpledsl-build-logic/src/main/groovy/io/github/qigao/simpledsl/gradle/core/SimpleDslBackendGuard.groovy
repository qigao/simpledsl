package io.github.qigao.simpledsl.gradle.core

import io.github.qigao.simpledsl.gradle.SimpleDslConfigurationException

final class SimpleDslBackendGuard {
    private final String projectPath
    private String selectedBackend

    SimpleDslBackendGuard(String projectPath) {
        if (projectPath == null || projectPath.trim().isEmpty()) {
            throw new IllegalArgumentException('project path must be non-empty')
        }
        this.projectPath = projectPath
    }

    synchronized void claim(String requestedBackend) {
        if (requestedBackend == null || requestedBackend.trim().isEmpty()) {
            throw new IllegalArgumentException('backend id must be non-empty')
        }
        if (selectedBackend == null) {
            selectedBackend = requestedBackend
            return
        }
        if (selectedBackend == requestedBackend) {
            return
        }
        throw new SimpleDslConfigurationException(
                'SimpleDSL configuration error\n' +
                "Project: ${projectPath}\n" +
                'Problem: backend conflict\n' +
                "already-selected backend: ${selectedBackend}\n" +
                "requested backend: ${requestedBackend}")
    }

    synchronized String selectedBackend() {
        selectedBackend
    }
}
