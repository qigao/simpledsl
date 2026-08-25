package io.github.qigao.simpledsl.gradle

import org.gradle.api.GradleException

class SimpleDslConfigurationException extends GradleException {
    SimpleDslConfigurationException(String message) {
        super(message)
    }

    SimpleDslConfigurationException(String message, Throwable cause) {
        super(message, cause)
    }

    static SimpleDslConfigurationException moduleTypeConflict(
            String projectPath,
            String existing,
            String requested) {
        new SimpleDslConfigurationException(
                'SimpleDSL configuration error\n' +
                "Project: ${projectPath}\n" +
                'Problem: module type conflict\n' +
                "Existing: ${existing}\n" +
                "Requested: ${requested}")
    }
}
