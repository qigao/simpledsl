package io.github.qigao.simpledsl.gradle.android

import io.github.qigao.simpledsl.gradle.android.capability.BuiltinAndroidCapabilities
import io.github.qigao.simpledsl.gradle.android.capability.ComposeCapabilityConfigurer
import io.github.qigao.simpledsl.gradle.android.module.SimpleDslAndroidApplicationPlugin
import io.github.qigao.simpledsl.gradle.android.module.SimpleDslAndroidDynamicFeaturePlugin
import io.github.qigao.simpledsl.gradle.android.module.SimpleDslAndroidLibraryPlugin
import io.github.qigao.simpledsl.gradle.capability.CapabilityEngine
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project

class SimpleDslAndroidExtension {
    private final Project project
    private final SimpleDslModuleModel model

    SimpleDslAndroidExtension(Project project, SimpleDslModuleModel model) {
        this.project = project
        this.model = model
    }

    void androidApplication(Action<? super SimpleDslAndroidApplicationSpec> action) {
        SimpleDslAndroidApplicationSpec spec = createApplicationSpec()
        action.execute(spec)
        project.pluginManager.apply(SimpleDslAndroidApplicationPlugin)
    }

    void androidApplication(Closure closure) {
        SimpleDslAndroidApplicationSpec spec = createApplicationSpec()
        configure(spec, closure)
        project.pluginManager.apply(SimpleDslAndroidApplicationPlugin)
    }

    void androidLibrary(Action<? super SimpleDslAndroidLibrarySpec> action) {
        SimpleDslAndroidLibrarySpec spec = createLibrarySpec()
        action.execute(spec)
        project.pluginManager.apply(SimpleDslAndroidLibraryPlugin)
    }

    void androidLibrary(Closure closure) {
        SimpleDslAndroidLibrarySpec spec = createLibrarySpec()
        configure(spec, closure)
        project.pluginManager.apply(SimpleDslAndroidLibraryPlugin)
    }

    void androidDynamicFeature(Action<? super SimpleDslAndroidDynamicFeatureSpec> action) {
        SimpleDslAndroidDynamicFeatureSpec spec = createDynamicFeatureSpec()
        action.execute(spec)
        project.pluginManager.apply(SimpleDslAndroidDynamicFeaturePlugin)
    }

    void androidDynamicFeature(Closure closure) {
        SimpleDslAndroidDynamicFeatureSpec spec = createDynamicFeatureSpec()
        configure(spec, closure)
        project.pluginManager.apply(SimpleDslAndroidDynamicFeaturePlugin)
    }

    void capability(String capabilityId) {
        project.extensions.getByType(CapabilityEngine).enable(capabilityId)
        configureBackendCapability(capabilityId)
    }

    void jetpackCompose() {
        capability(BuiltinAndroidCapabilities.COMPOSE.id)
    }

    void ksp() {
        capability(BuiltinAndroidCapabilities.KSP.id)
    }

    void room() {
        capability(BuiltinAndroidCapabilities.ROOM.id)
    }

    void hilt() {
        capability(BuiltinAndroidCapabilities.HILT.id)
    }

    private void configureBackendCapability(String capabilityId) {
        if (BuiltinAndroidCapabilities.COMPOSE.id == capabilityId) {
            ComposeCapabilityConfigurer.configure(project)
        }
    }

    private SimpleDslAndroidApplicationSpec createApplicationSpec() {
        rejectDuplicateModuleDeclaration()
        SimpleDslAndroidApplicationSpec spec = project.extensions.create(
                SimpleDslAndroidApplicationPlugin.SPEC_EXTENSION,
                SimpleDslAndroidApplicationSpec)
        spec.dynamicFeatures.convention(Collections.emptySet())
        spec
    }

    private SimpleDslAndroidLibrarySpec createLibrarySpec() {
        rejectDuplicateModuleDeclaration()
        project.extensions.create(
                SimpleDslAndroidLibraryPlugin.SPEC_EXTENSION,
                SimpleDslAndroidLibrarySpec)
    }

    private SimpleDslAndroidDynamicFeatureSpec createDynamicFeatureSpec() {
        rejectDuplicateModuleDeclaration()
        project.extensions.create(
                SimpleDslAndroidDynamicFeaturePlugin.SPEC_EXTENSION,
                SimpleDslAndroidDynamicFeatureSpec)
    }

    private void rejectDuplicateModuleDeclaration() {
        if (project.extensions.findByName(SimpleDslAndroidApplicationPlugin.SPEC_EXTENSION) != null ||
                project.extensions.findByName(SimpleDslAndroidLibraryPlugin.SPEC_EXTENSION) != null ||
                project.extensions.findByName(SimpleDslAndroidDynamicFeaturePlugin.SPEC_EXTENSION) != null) {
            throw new GradleException(
                    'SimpleDSL Android configuration error\n' +
                    "Project: ${project.path}\n" +
                    'Problem: exactly one Android module type may be declared')
        }
    }

    private static void configure(Object target, Closure closure) {
        Closure configured = closure.rehydrate(target, closure.owner, closure.thisObject)
        configured.resolveStrategy = Closure.DELEGATE_FIRST
        configured.call()
    }
}
