package io.github.qigao.simpledsl.gradle.android.internal

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.DynamicFeatureAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import io.github.qigao.simpledsl.gradle.android.diagnostics.SimpleDslAndroidVariantsTask
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

final class SimpleDslAndroidComponents {
    static final String VARIANTS_TASK = 'simpledslAndroidVariants'

    static void configure(Project project, ApplicationAndroidComponentsExtension components) {
        configureCallbacks(project, components)
    }

    static void configure(Project project, LibraryAndroidComponentsExtension components) {
        configureCallbacks(project, components)
    }

    static void configure(Project project, DynamicFeatureAndroidComponentsExtension components) {
        configureCallbacks(project, components)
    }

    private static void configureCallbacks(Project project, def components) {
        String projectPath = project.path
        TaskProvider<SimpleDslAndroidVariantsTask> variantsTask = registerTask(project)
        def selector = components.selector().all()

        components.beforeVariants(selector, { builder ->
            String name = builder.name
            if (name == null || name.trim().isEmpty()) {
                throw new GradleException(
                        'SimpleDSL Android configuration error\n' +
                        "Project: ${projectPath}\n" +
                        'Problem: Android Components returned a blank variant name')
            }
        } as Action)

        components.onVariants(selector, { variant ->
            String name = variant.name
            variantsTask.configure { task -> task.variantNames.add(name) }
        } as Action)
    }

    private static TaskProvider<SimpleDslAndroidVariantsTask> registerTask(Project project) {
        project.tasks.register(VARIANTS_TASK, SimpleDslAndroidVariantsTask) { task ->
            task.group = 'SimpleDSL'
            task.description = 'Print Android variants discovered through Android Components.'
            task.variantNames.convention(Collections.emptyList())
        }
    }

    private SimpleDslAndroidComponents() {
    }
}
