package io.github.qigao.simpledsl.gradle

import io.github.qigao.simpledsl.gradle.capability.BuiltinCapabilities
import io.github.qigao.simpledsl.gradle.capability.CapabilityEngine
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
        enable(BuiltinCapabilities.JPA.id)
    }

    void jdbc() {
        enable(BuiltinCapabilities.JDBC.id)
    }

    void jooq() {
        enable(BuiltinCapabilities.JOOQ.id)
    }

    private void enable(String capabilityId) {
        project.extensions.getByType(CapabilityEngine).enable(capabilityId)
    }
}
