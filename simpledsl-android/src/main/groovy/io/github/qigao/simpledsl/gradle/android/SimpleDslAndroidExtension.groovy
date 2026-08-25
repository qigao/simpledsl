package io.github.qigao.simpledsl.gradle.android

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
        SimpleDslAndroidApplicationSpec spec = project.objects.newInstance(SimpleDslAndroidApplicationSpec)
        action.execute(spec)
        moduleAdapterPending('androidApplication')
    }

    void androidApplication(Closure closure) {
        SimpleDslAndroidApplicationSpec spec = project.objects.newInstance(SimpleDslAndroidApplicationSpec)
        configure(spec, closure)
        moduleAdapterPending('androidApplication')
    }

    void androidLibrary(Action<? super SimpleDslAndroidLibrarySpec> action) {
        SimpleDslAndroidLibrarySpec spec = project.objects.newInstance(SimpleDslAndroidLibrarySpec)
        action.execute(spec)
        moduleAdapterPending('androidLibrary')
    }

    void androidLibrary(Closure closure) {
        SimpleDslAndroidLibrarySpec spec = project.objects.newInstance(SimpleDslAndroidLibrarySpec)
        configure(spec, closure)
        moduleAdapterPending('androidLibrary')
    }

    private static void configure(Object target, Closure closure) {
        Closure configured = closure.rehydrate(target, closure.owner, closure.thisObject)
        configured.resolveStrategy = Closure.DELEGATE_FIRST
        configured.call()
    }

    private static void moduleAdapterPending(String method) {
        throw new GradleException(
                'SimpleDSL Android foundation error\n' +
                "Problem: ${method} module adapter is not configured")
    }
}
