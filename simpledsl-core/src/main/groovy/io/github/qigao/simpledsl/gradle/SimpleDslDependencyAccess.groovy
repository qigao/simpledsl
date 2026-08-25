package io.github.qigao.simpledsl.gradle

import io.github.qigao.simpledsl.gradle.catalog.DependencyCatalogSnapshot
import io.github.qigao.simpledsl.gradle.dependency.DependencyBridge
import io.github.qigao.simpledsl.gradle.model.SimpleDslProjectModel
import org.gradle.api.Project

final class SimpleDslDependencyAccess {
    static int javaVersion(Project project) {
        catalog(project).javaVersion()
    }

    static Integer javaVersionOrNull(Project project) {
        catalog(project).javaVersionOrNull()
    }

    static def androidPolicy(Project project) {
        catalog(project).androidPolicy()
    }

    static void activatePlatform(
            Project project,
            SimpleDslProjectModel model,
            String configuration,
            String platformAlias) {
        DependencyBridge.activatePlatform(project, model, configuration, platformAlias)
    }

    static void add(Project project, SimpleDslProjectModel model, String configuration, String alias) {
        DependencyBridge.add(project, model, configuration, alias)
    }

    static String libraryNotation(Project project, String alias) {
        catalog(project).library(alias).notation()
    }

    static String explicitNotation(Project project, String alias) {
        DependencyBridge.explicitNotation(project, alias)
    }

    static DependencyCatalogSnapshot catalog(Project project) {
        DependencyCatalogSnapshot value = project.extensions.findByType(DependencyCatalogSnapshot)
        if (value == null) {
            throw new SimpleDslConfigurationException(
                    'SimpleDSL configuration error\n' +
                    "Project: ${project.path}\n" +
                    'Problem: SimpleDSL dependency catalog is unavailable; apply a SimpleDSL project backend')
        }
        value
    }

    private SimpleDslDependencyAccess() {}
}
