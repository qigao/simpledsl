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

    static String requireBaseModule(Project project, Property<String> baseModule) {
        String value = baseModule.isPresent() ? baseModule.get().trim() : null
        if (value == null || value.isEmpty()) {
            throw new GradleException(
                    'SimpleDSL Android configuration error\n' +
                    "Project: ${project.path}\n" +
                    'Problem: androidDynamicFeature requires baseModule')
        }
        requireExternalProjectPath(
                project,
                'baseModule',
                value,
                'androidDynamicFeature baseModule cannot reference the feature project itself')
    }

    static Set<String> requireDynamicFeaturePaths(Project project, Collection<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>()
        values.each { raw ->
            String value = raw == null ? '' : raw.trim()
            result.add(requireExternalProjectPath(
                    project,
                    'dynamicFeature',
                    value,
                    'androidApplication dynamicFeature cannot reference the application project itself'))
        }
        result
    }

    static JavaVersion javaVersion(CatalogAndroidPolicy policy) {
        JavaVersion.toVersion(policy.javaVersion)
    }

    private static String requireExternalProjectPath(
            Project project,
            String field,
            String value,
            String selfReferenceProblem) {
        if (value == null || value.isEmpty() || !value.startsWith(':')) {
            String diagnosticField = field == 'dynamicFeature' ? 'dynamicFeature path' : field
            String message =
                    'SimpleDSL Android configuration error\n' +
                    "Project: ${project.path}\n" +
                    "Problem: ${diagnosticField} must be an absolute Gradle project path beginning with ':'"
            if (value != null && !value.isEmpty()) {
                message += "\nValue: ${value}"
            }
            throw new GradleException(message)
        }
        if (value == project.path) {
            throw new GradleException(
                    'SimpleDSL Android configuration error\n' +
                    "Project: ${project.path}\n" +
                    "Problem: ${selfReferenceProblem}\n" +
                    "Value: ${value}")
        }
        value
    }

    private SimpleDslAndroidBase() {}
}
