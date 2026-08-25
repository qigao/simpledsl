package io.github.qigao.simpledsl.gradle.module

import io.github.qigao.simpledsl.gradle.SimpleDslDependencyAccess
import io.github.qigao.simpledsl.gradle.internal.SimpleDslSpringBasePlugin
import io.github.qigao.simpledsl.gradle.model.SimpleDslProjectModel
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

final class SimpleDslSpringServicePlugin implements Plugin<Project> {
    static final String MODULE_TYPE = 'spring-service'

    @Override
    void apply(Project project) {
        project.pluginManager.apply('org.springframework.boot')
        new SimpleDslSpringBasePlugin().apply(project)

        SimpleDslProjectModel model = project.extensions.getByType(SimpleDslProjectModel)
        model.claim(MODULE_TYPE, project.path)
        SimpleDslDependencyAccess.add(project, model, 'implementation', 'spring-core')
        SimpleDslDependencyAccess.add(project, model, 'testImplementation', 'spring-test')
        SimpleDslDependencyAccess.add(project, model, 'testRuntimeOnly', 'junit-platform-launcher')
        project.tasks.withType(Test).configureEach { it.useJUnitPlatform() }
    }
}
