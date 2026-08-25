package io.github.qigao.simpledsl.gradle

final class SimpleDslConfigurationException extends RuntimeException {
    SimpleDslConfigurationException(String message) {
        super(message)
    }

    static SimpleDslConfigurationException moduleTypeConflict(
            String projectPath,
            String existing,
            String requested) {
        new SimpleDslConfigurationException(
                'SimpleDSL configuration error\n' +
                "Project: ${projectPath}\n" +
                'Problem: exactly one module type is allowed\n' +
                "Existing: ${existing}\n" +
                "Requested: ${requested}")
    }
}
