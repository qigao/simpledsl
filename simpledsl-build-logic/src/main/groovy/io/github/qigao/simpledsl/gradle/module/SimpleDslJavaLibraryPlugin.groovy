package io.github.qigao.simpledsl.gradle.module

import io.github.qigao.simpledsl.gradle.ModuleKind
import io.github.qigao.simpledsl.gradle.SimpleDslDependencyAccess
import io.github.qigao.simpledsl.gradle.internal.SimpleDslJavaBasePlugin
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

final class SimpleDslJavaLibraryPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply('java-library')
        new SimpleDslJavaBasePlugin().apply(project)

        SimpleDslModuleModel model = project.extensions.getByType(SimpleDslModuleModel)
        model.claim(ModuleKind.JAVA_LIBRARY, project.path)
        SimpleDslDependencyAccess.add(project, model, 'testImplementation', 'junit-jupiter')
        SimpleDslDependencyAccess.add(project, model, 'testRuntimeOnly', 'junit-platform-launcher')
        project.tasks.withType(Test).configureEach { it.useJUnitPlatform() }
    }
}
