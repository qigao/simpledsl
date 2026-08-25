package io.github.qigao.simpledsl.gradle.android.diagnostics

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class SimpleDslAndroidVariantsTask extends DefaultTask {
    @Input
    abstract ListProperty<String> getVariantNames()

    @TaskAction
    void printVariants() {
        new TreeSet<String>(variantNames.get()).each { String name ->
            println("SimpleDSL Android variant: ${name}")
        }
    }
}
