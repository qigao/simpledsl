package io.github.qigao.simpledsl.gradle.module

import io.github.qigao.simpledsl.gradle.ModuleKind
import io.github.qigao.simpledsl.gradle.SimpleDslDependencyAccess
import io.github.qigao.simpledsl.gradle.internal.SimpleDslSpringBasePlugin
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

final class SimpleDslSpringLibraryPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply('java-library')
        new SimpleDslSpringBasePlugin().apply(project)

        SimpleDslModuleModel model = project.extensions.getByType(SimpleDslModuleModel)
        model.claim(ModuleKind.SPRING_LIBRARY, project.path)
        SimpleDslDependencyAccess.add(project, model, 'implementation', 'spring-core')
        SimpleDslDependencyAccess.add(project, model, 'testImplementation', 'spring-test')
        SimpleDslDependencyAccess.add(project, model, 'testRuntimeOnly', 'junit-platform-launcher')
        project.tasks.withType(Test).configureEach { it.useJUnitPlatform() }
    }
}
