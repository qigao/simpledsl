package io.github.qigao.simpledsl.gradle.android.internal

import io.github.qigao.simpledsl.gradle.catalog.CatalogAndroidPolicy
import io.github.qigao.simpledsl.gradle.catalog.DependencyCatalogSnapshot
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.provider.Property

final class SimpleDslAndroidBase {
    static CatalogAndroidPolicy requirePolicy(Project project, boolean targetRequired) {
        project.extensions.getByType(DependencyCatalogSnapshot)
                .requireAndroidPolicy(project.path, targetRequired)
    }

    static String requireNamespace(Project project, Property<String> namespace) {
        String value = namespace.isPresent() ? namespace.get().trim() : null
        if (value == null || value.isEmpty()) {
            throw new GradleException(
                    'SimpleDSL Android configuration error\n' +
                    "Project: ${project.path}\n" +
                    'Problem: namespace is required')
        }
        value
    }

    static JavaVersion javaVersion(CatalogAndroidPolicy policy) {
        JavaVersion.toVersion(policy.javaVersion)
    }

    private SimpleDslAndroidBase() {}
}
