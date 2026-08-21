package io.github.qigao.simpledsl.gradle

import io.github.qigao.simpledsl.gradle.catalog.DependencyCatalogSnapshot
import io.github.qigao.simpledsl.gradle.dependency.DependencyBridge
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.api.GradleException
import org.gradle.api.Project

final class SimpleDslDependencyAccess {
    static int javaVersion(Project project) {
        catalog(project).javaVersion()
    }

    static void activatePlatform(Project project, SimpleDslModuleModel model, String configuration, String platformAlias) {
        DependencyBridge.activatePlatform(project, model, configuration, platformAlias)
    }

    static void add(Project project, SimpleDslModuleModel model, String configuration, String alias) {
        DependencyBridge.add(project, model, configuration, alias)
    }

    static String libraryNotation(Project project, SimpleDslModuleModel model, String alias) {
        DependencyBridge.explicitNotation(project, alias)
    }

    static DependencyCatalogSnapshot catalog(Project project) {
        DependencyCatalogSnapshot catalog = project.extensions.findByType(DependencyCatalogSnapshot)
        if (catalog == null) {
            throw new GradleException(
                    'SimpleDSL dependency catalog error\nProblem: simpledslDependencyCatalog is not available; apply io.github.qigao.simpledsl.module')
        }
        catalog
    }
}
