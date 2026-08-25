package io.github.qigao.simpledsl.gradle.android.module

import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import io.github.qigao.simpledsl.gradle.android.SimpleDslAndroidLibrarySpec
import io.github.qigao.simpledsl.gradle.android.internal.SimpleDslAndroidBase
import io.github.qigao.simpledsl.gradle.android.internal.SimpleDslAndroidComponents
import io.github.qigao.simpledsl.gradle.catalog.CatalogAndroidPolicy
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.api.Plugin
import org.gradle.api.Project

final class SimpleDslAndroidLibraryPlugin implements Plugin<Project> {
    static final String SPEC_EXTENSION = 'simpledslAndroidLibrarySpec'

    @Override
    void apply(Project project) {
        SimpleDslModuleModel model = project.extensions.getByType(SimpleDslModuleModel)
        model.claim('android-library', project.path)

        SimpleDslAndroidLibrarySpec spec = project.extensions.getByType(SimpleDslAndroidLibrarySpec)
        CatalogAndroidPolicy policy = SimpleDslAndroidBase.requirePolicy(project, false)
        String namespace = SimpleDslAndroidBase.requireNamespace(project, spec.namespace)

        project.pluginManager.apply('com.android.library')
        LibraryExtension android = project.extensions.getByType(LibraryExtension)
        android.namespace = namespace
        android.compileSdk = policy.compileSdk
        android.defaultConfig.minSdk = policy.minSdk
        android.compileOptions.sourceCompatibility = SimpleDslAndroidBase.javaVersion(policy)
        android.compileOptions.targetCompatibility = SimpleDslAndroidBase.javaVersion(policy)

        LibraryAndroidComponentsExtension components =
                project.extensions.getByType(LibraryAndroidComponentsExtension)
        SimpleDslAndroidComponents.configure(project, components)
    }
}
