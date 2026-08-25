package io.github.qigao.simpledsl.gradle.internal

import io.github.qigao.simpledsl.gradle.SimpleDslDependencyAccess
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.api.Plugin
import org.gradle.api.Project

final class SimpleDslSpringBasePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        new SimpleDslJavaBasePlugin().apply(project)
        SimpleDslModuleModel model = project.extensions.getByType(SimpleDslModuleModel)
        ['implementation', 'api', 'testImplementation']
                .findAll { configuration -> project.configurations.findByName(configuration) != null }
                .each { configuration ->
                    SimpleDslDependencyAccess.activatePlatform(project, model, configuration, 'spring')
                }
    }
}
