package io.github.qigao.simpledsl.gradle.android.module

import com.android.build.api.dsl.DynamicFeatureExtension
import com.android.build.api.variant.DynamicFeatureAndroidComponentsExtension
import io.github.qigao.simpledsl.gradle.android.SimpleDslAndroidDynamicFeatureSpec
import io.github.qigao.simpledsl.gradle.android.internal.SimpleDslAndroidBase
import io.github.qigao.simpledsl.gradle.android.internal.SimpleDslAndroidComponents
import io.github.qigao.simpledsl.gradle.catalog.CatalogAndroidPolicy
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property

final class SimpleDslAndroidDynamicFeaturePlugin implements Plugin<Project> {
    static final String SPEC_EXTENSION = 'simpledslAndroidDynamicFeatureSpec'

    @Override
    void apply(Project project) {
        SimpleDslModuleModel model = project.extensions.getByType(SimpleDslModuleModel)
        model.claim('android-dynamic-feature', project.path)

        SimpleDslAndroidDynamicFeatureSpec spec = project.extensions.getByType(SimpleDslAndroidDynamicFeatureSpec)
        CatalogAndroidPolicy policy = SimpleDslAndroidBase.requirePolicy(project, false)
        String namespace = SimpleDslAndroidBase.requireNamespace(project, spec.namespace)
        String baseModule = requireBaseModule(project, spec.baseModule)

        project.pluginManager.apply('com.android.dynamic-feature')
        DynamicFeatureExtension android = project.extensions.getByType(DynamicFeatureExtension)
        android.namespace = namespace
        android.compileSdk = policy.compileSdk
        android.defaultConfig.minSdk = policy.minSdk
        android.compileOptions.sourceCompatibility = SimpleDslAndroidBase.javaVersion(policy)
        android.compileOptions.targetCompatibility = SimpleDslAndroidBase.javaVersion(policy)

        project.dependencies.add('implementation', project.project(baseModule))

        DynamicFeatureAndroidComponentsExtension components =
                project.extensions.getByType(DynamicFeatureAndroidComponentsExtension)
        SimpleDslAndroidComponents.configure(project, components)
    }

    private static String requireBaseModule(Project project, Property<String> baseModule) {
        String value = baseModule.isPresent() ? baseModule.get().trim() : null
        if (value == null || value.isEmpty()) {
            throw new GradleException(
                    'SimpleDSL Android configuration error\n' +
                    "Project: ${project.path}\n" +
                    'Problem: androidDynamicFeature requires baseModule')
        }
        if (!value.startsWith(':')) {
            throw new GradleException(
                    'SimpleDSL Android configuration error\n' +
                    "Project: ${project.path}\n" +
                    "Problem: baseModule must be an absolute Gradle project path beginning with ':'\n" +
                    "Value: ${value}")
        }
        if (value == project.path) {
            throw new GradleException(
                    'SimpleDSL Android configuration error\n' +
                    "Project: ${project.path}\n" +
                    'Problem: androidDynamicFeature baseModule cannot reference the feature project itself\n' +
                    "Value: ${value}")
        }
        value
    }
}
