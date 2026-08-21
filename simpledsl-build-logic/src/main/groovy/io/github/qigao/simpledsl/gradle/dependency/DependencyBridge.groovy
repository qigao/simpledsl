package io.github.qigao.simpledsl.gradle.dependency

import io.github.qigao.simpledsl.gradle.SimpleDslConfigurationException
import io.github.qigao.simpledsl.gradle.catalog.DependencyCatalogSnapshot
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.api.Project

final class DependencyBridge {
    static void add(Project project, SimpleDslModuleModel model, String configuration, String alias) {
        requireConfiguration(project, configuration, alias)
        DependencyCatalogSnapshot catalog = catalog(project)
        def library = catalog.library(alias)
        if (library.platform) {
            activatePlatform(project, model, configuration, library.platform as String)
        }
        project.dependencies.add(configuration, library.notation())
    }

    static void activatePlatform(
            Project project,
            SimpleDslModuleModel model,
            String configuration,
            String platformAlias) {
        requireConfiguration(project, configuration, platformAlias)
        String binding = "${configuration}:${platformAlias}"
        if (model.platformBindings.get().contains(binding)) {
            return
        }
        def platform = catalog(project).platform(platformAlias)
        project.dependencies.add(configuration, project.dependencies.platform(platform.coordinate()))
        model.bindPlatform(configuration, platformAlias)
    }

    static String explicitNotation(Project project, String alias) {
        def library = catalog(project).library(alias)
        if (library.isPlatformManaged()) {
            throw new SimpleDslConfigurationException(
                    'SimpleDSL configuration error\n' +
                    "Project: ${project.path}\n" +
                    "Dependency alias: ${alias}\n" +
                    'Problem: platform-managed library cannot be returned by simpledsl.library(alias)\n' +
                    'Use: simpledsl.dependency(configuration, alias)')
        }
        library.notation()
    }

    private static DependencyCatalogSnapshot catalog(Project project) {
        DependencyCatalogSnapshot value = project.extensions.findByType(DependencyCatalogSnapshot)
        if (value == null) {
            throw new SimpleDslConfigurationException(
                    'SimpleDSL configuration error\n' +
                    "Project: ${project.path}\n" +
                    'Problem: SimpleDSL dependency catalog is unavailable; apply simpledsl.module')
        }
        value
    }

    private static void requireConfiguration(Project project, String configuration, String alias) {
        if (project.configurations.findByName(configuration) == null) {
            throw new SimpleDslConfigurationException(
                    'SimpleDSL configuration error\n' +
                    "Project: ${project.path}\n" +
                    "Configuration: ${configuration}\n" +
                    "Dependency alias: ${alias}\n" +
                    'Problem: target configuration does not exist')
        }
    }
}
