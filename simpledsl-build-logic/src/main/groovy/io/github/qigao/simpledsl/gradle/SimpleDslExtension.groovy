package io.github.qigao.simpledsl.gradle

import io.github.qigao.simpledsl.gradle.capability.CapabilityEngine
import io.github.qigao.simpledsl.gradle.capability.CapabilityPluginRegistry
import io.github.qigao.simpledsl.gradle.dependency.DependencyBridge
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.UnknownPluginException

class SimpleDslExtension {
    private final Project project
    private final SimpleDslModuleModel model
    private final SimpleDslPersistenceExtension persistence

    SimpleDslExtension(Project project, SimpleDslModuleModel model) {
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

    void capability(String pluginId) {
        try {
            project.pluginManager.apply(pluginId)
        } catch (UnknownPluginException e) {
            throw new SimpleDslConfigurationException(
                    'SimpleDSL configuration error\n' +
                    "Project: ${project.path}\n" +
                    "Gradle plugin id: ${pluginId}\n" +
                    'Problem: capability plugin could not be resolved',
                    e)
        }

        CapabilityPluginRegistry pluginRegistry = project.extensions.getByType(CapabilityPluginRegistry)
        String capabilityId = pluginRegistry.capabilityForPlugin(pluginId)
        if (capabilityId == null) {
            throw new SimpleDslConfigurationException(
                    'SimpleDSL configuration error\n' +
                    "Project: ${project.path}\n" +
                    "Gradle plugin id: ${pluginId}\n" +
                    'Problem: plugin did not register a SimpleDSL primary capability')
        }
        project.extensions.getByType(CapabilityEngine).enable(capabilityId)
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
        project.pluginManager.apply('simpledsl.feature.aop')
    }

    void transaction() {
        project.pluginManager.apply('simpledsl.feature.transaction')
    }

    void web() {
        project.pluginManager.apply('simpledsl.feature.web')
    }

    void httpClient() {
        project.pluginManager.apply('simpledsl.feature.http-client')
    }

    void messaging() {
        project.pluginManager.apply('simpledsl.feature.messaging')
    }

    void redis() {
        project.pluginManager.apply('simpledsl.feature.redis')
    }

    void nativeImage() {
        project.pluginManager.apply('simpledsl.feature.native')
    }

    void lombok() {
        project.pluginManager.apply('simpledsl.feature.lombok')
    }
}
