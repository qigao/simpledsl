package io.github.qigao.simpledsl.gradle.android.module

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import io.github.qigao.simpledsl.gradle.android.SimpleDslAndroidApplicationSpec
import io.github.qigao.simpledsl.gradle.android.internal.SimpleDslAndroidBase
import io.github.qigao.simpledsl.gradle.android.internal.SimpleDslAndroidComponents
import io.github.qigao.simpledsl.gradle.catalog.CatalogAndroidPolicy
import io.github.qigao.simpledsl.gradle.model.SimpleDslModuleModel
import org.gradle.api.GradleException
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
        Set<String> dynamicFeatures = validatedDynamicFeatures(project, spec)

        project.pluginManager.apply('com.android.application')
        ApplicationExtension android = project.extensions.getByType(ApplicationExtension)
        android.namespace = namespace
        android.compileSdk = policy.compileSdk
        android.defaultConfig.minSdk = policy.minSdk
        android.defaultConfig.targetSdk = policy.targetSdk
        android.defaultConfig.applicationId = applicationId
        android.compileOptions.sourceCompatibility = SimpleDslAndroidBase.javaVersion(policy)
        android.compileOptions.targetCompatibility = SimpleDslAndroidBase.javaVersion(policy)
        android.dynamicFeatures.addAll(dynamicFeatures)

        ApplicationAndroidComponentsExtension components =
                project.extensions.getByType(ApplicationAndroidComponentsExtension)
        SimpleDslAndroidComponents.configure(project, components)
    }

    private static Set<String> validatedDynamicFeatures(Project project, SimpleDslAndroidApplicationSpec spec) {
        Set<String> values = new LinkedHashSet<>()
        spec.dynamicFeatures.get().each { String raw ->
            String value = raw == null ? '' : raw.trim()
            if (!value.startsWith(':')) {
                throw new GradleException(
                        'SimpleDSL Android configuration error\n' +
                        "Project: ${project.path}\n" +
                        "Problem: dynamicFeature path must be an absolute Gradle project path beginning with ':'\n" +
                        "Value: ${value}")
            }
            if (value == project.path) {
                throw new GradleException(
                        'SimpleDSL Android configuration error\n' +
                        "Project: ${project.path}\n" +
                        'Problem: androidApplication dynamicFeature cannot reference the application project itself\n' +
                        "Value: ${value}")
            }
            values.add(value)
        }
        Collections.unmodifiableSet(values)
    }
}
