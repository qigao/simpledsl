package io.github.qigao.simpledsl.gradle

import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.api.Project

class SimpleDslPersistenceExtension {
    protected final Project project
    protected final SimpleDslModuleModel model

    SimpleDslPersistenceExtension(Project project, SimpleDslModuleModel model) {
        this.project = project
        this.model = model
    }

    void jpa() {
        project.pluginManager.apply('io.github.qigao.simpledsl.feature.jpa')
    }

    void jdbc() {
        project.pluginManager.apply('io.github.qigao.simpledsl.feature.jdbc')
    }

    void jooq() {
        project.pluginManager.apply('io.github.qigao.simpledsl.feature.jooq')
    }
}
