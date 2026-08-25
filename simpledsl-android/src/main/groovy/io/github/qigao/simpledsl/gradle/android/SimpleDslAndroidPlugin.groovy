package io.github.qigao.simpledsl.gradle.android

import io.github.qigao.simpledsl.gradle.core.SimpleDslBackendGuard
import io.github.qigao.simpledsl.gradle.core.SimpleDslProjectCorePlugin
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.api.Plugin
import org.gradle.api.Project

class SimpleDslAndroidPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        SimpleDslBackendGuard.claim(project, 'android')
        project.pluginManager.apply(SimpleDslProjectCorePlugin)

        SimpleDslModuleModel model = project.extensions.getByType(SimpleDslModuleModel)
        model.backendId.set('android')

        if (project.extensions.findByName('simpledsl') == null) {
            project.extensions.create('simpledsl', SimpleDslAndroidExtension, project, model)
        }
    }
}
