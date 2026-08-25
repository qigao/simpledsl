package io.github.qigao.simpledsl.gradle

import io.github.qigao.simpledsl.gradle.capability.BuiltinCapabilities
import io.github.qigao.simpledsl.gradle.capability.CapabilityEngine
import io.github.qigao.simpledsl.gradle.dependency.DependencyBridge
import io.github.qigao.simpledsl.gradle.model.SimpleDslProjectModel
import io.github.qigao.simpledsl.gradle.module.SimpleDslJavaLibraryPlugin
import io.github.qigao.simpledsl.gradle.module.SimpleDslSpringLibraryPlugin
import io.github.qigao.simpledsl.gradle.module.SimpleDslSpringServicePlugin
import io.github.qigao.simpledsl.gradle.schema.SimpleDslJooqSchemaPlugin
import io.github.qigao.simpledsl.gradle.schema.SimpleDslJsonSchemaPlugin
import org.gradle.api.Action
import org.gradle.api.Project

class SimpleDslExtension {
    private final Project project
    private final SimpleDslProjectModel model
    private final SimpleDslPersistenceExtension persistence

    SimpleDslExtension(Project project, SimpleDslProjectModel model) {
        this.project = project
        this.model = model
        this.persistence = new SimpleDslPersistenceExtension(project, model)
    }

    String library(String alias) {
        DependencyBridge.explicitNotation(project, alias)
    }

    void dependency(String configuration, String alias) {
        DependencyBridge.add(project, model, configuration, alias)
    }

    void capability(String capabilityId) {
        project.extensions.getByType(CapabilityEngine).enable(capabilityId)
    }

    void javaLibrary() {
        project.pluginManager.apply(SimpleDslJavaLibraryPlugin)
    }

    void springLibrary() {
        project.pluginManager.apply(SimpleDslSpringLibraryPlugin)
    }

    void springService() {
        project.pluginManager.apply(SimpleDslSpringServicePlugin)
    }

    void jooqSchema() {
        project.pluginManager.apply(SimpleDslJooqSchemaPlugin)
    }

    void jsonSchema() {
        project.pluginManager.apply(SimpleDslJsonSchemaPlugin)
    }

    SimpleDslPersistenceExtension getPersistence() {
        persistence
    }

    void persistence(Action<? super SimpleDslPersistenceExtension> action) {
        action.execute(persistence)
    }

    void persistence(Closure closure) {
        Closure configured = closure.rehydrate(persistence, closure.owner, closure.thisObject)
        configured.resolveStrategy = Closure.DELEGATE_FIRST
        configured.call()
    }

    void aop() {
        capability(BuiltinCapabilities.AOP.id)
    }

    void transaction() {
        capability(BuiltinCapabilities.TRANSACTION.id)
    }

    void web() {
        capability(BuiltinCapabilities.WEB.id)
    }

    void httpClient() {
        capability(BuiltinCapabilities.HTTP_CLIENT.id)
    }

    void messaging() {
        capability(BuiltinCapabilities.MESSAGING.id)
    }

    void redis() {
        capability(BuiltinCapabilities.REDIS.id)
    }

    void nativeImage() {
        capability(BuiltinCapabilities.NATIVE.id)
    }

    void lombok() {
        capability(BuiltinCapabilities.LOMBOK.id)
    }
}
