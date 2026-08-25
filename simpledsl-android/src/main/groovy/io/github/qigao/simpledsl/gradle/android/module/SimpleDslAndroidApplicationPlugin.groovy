package io.github.qigao.simpledsl.gradle.android.module

import com.android.build.api.dsl.ApplicationExtension
import io.github.qigao.simpledsl.gradle.android.SimpleDslAndroidApplicationSpec
import io.github.qigao.simpledsl.gradle.android.internal.SimpleDslAndroidBase
import io.github.qigao.simpledsl.gradle.catalog.CatalogAndroidPolicy
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.api.Plugin
import org.gradle.api.Project

final class SimpleDslAndroidApplicationPlugin implements Plugin<Project> {
    static final String SPEC_EXTENSION = 'simpledslAndroidApplicationSpec'

    @Override
    void apply(Project project) {
        SimpleDslModuleModel model = project.extensions.getByType(SimpleDslModuleModel)
        model.claim('android-application', project.path)

        SimpleDslAndroidApplicationSpec spec = project.extensions.getByType(SimpleDslAndroidApplicationSpec)
        CatalogAndroidPolicy policy = SimpleDslAndroidBase.requirePolicy(project, true)
        String namespace = SimpleDslAndroidBase.requireNamespace(project, spec.namespace)
        String applicationId = spec.applicationId.isPresent() ? spec.applicationId.get().trim() : namespace
        if (applicationId.isEmpty()) applicationId = namespace

        project.pluginManager.apply('com.android.application')
        ApplicationExtension android = project.extensions.getByType(ApplicationExtension)
        android.namespace = namespace
        android.compileSdk = policy.compileSdk
        android.defaultConfig.minSdk = policy.minSdk
        android.defaultConfig.targetSdk = policy.targetSdk
        android.defaultConfig.applicationId = applicationId
        android.compileOptions.sourceCompatibility = SimpleDslAndroidBase.javaVersion(policy)
        android.compileOptions.targetCompatibility = SimpleDslAndroidBase.javaVersion(policy)
    }
}
