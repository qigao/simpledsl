package io.github.qigao.simpledsl.gradle.dependency

import io.github.qigao.simpledsl.gradle.SimpleDslConfigurationException
import org.gradle.api.Project

final class ProjectDependencyBridge {
    static void add(Project project, String configuration, String projectPath) {
        String configurationName = configuration == null ? '' : configuration.trim()
        String dependencyPath = projectPath == null ? '' : projectPath.trim()

        if (dependencyPath.isEmpty() || !dependencyPath.startsWith(':')) {
            fail(
                    project,
                    configurationName,
                    dependencyPath,
                    "dependency must be an absolute Gradle project path beginning with ':'")
        }
        if (dependencyPath == project.path) {
            fail(project, configurationName, dependencyPath, 'module cannot depend on itself')
        }
        if (project.rootProject.findProject(dependencyPath) == null) {
            fail(project, configurationName, dependencyPath, 'target module was not discovered')
        }
        if (configurationName.isEmpty() || project.configurations.findByName(configurationName) == null) {
            fail(project, configurationName, dependencyPath, 'configuration does not exist')
        }

        project.dependencies.add(
                configurationName,
                project.dependencies.project([path: dependencyPath]))
    }

    private static void fail(
            Project project,
            String configuration,
            String dependency,
            String problem) {
        throw new SimpleDslConfigurationException(
                'SimpleDSL module dependency error\n' +
                "Project: ${project.path}\n" +
                "Configuration: ${configuration}\n" +
                "Dependency: ${dependency}\n" +
                "Problem: ${problem}")
    }

    private ProjectDependencyBridge() {}
}
