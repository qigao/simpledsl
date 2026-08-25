package io.github.qigao.simpledsl.gradle.core

import org.gradle.api.GradleException
import org.gradle.api.Project

final class SimpleDslBackendGuard {
    static final String KEY = 'io.github.qigao.simpledsl.backend'

    static void claim(Project project, String requested) {
        if (requested == null || requested.trim().isEmpty()) {
            throw new IllegalArgumentException('SimpleDSL backend id must be non-empty')
        }
        def extra = project.extensions.extraProperties
        String selected = extra.has(KEY) ? extra.get(KEY) as String : null
        if (selected == null) {
            extra.set(KEY, requested)
            return
        }
        if (selected != requested) {
            throw new GradleException(
                    'SimpleDSL backend conflict\n' +
                    "Project: ${project.path}\n" +
                    "Already-selected backend: ${selected}\n" +
                    "Requested backend: ${requested}")
        }
    }

    private SimpleDslBackendGuard() {}
}
