package io.github.qigao.simpledsl.gradle.android.capability

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import io.github.qigao.simpledsl.gradle.SimpleDslConfigurationException
import org.gradle.api.Project

final class ComposeCapabilityConfigurer {
    static void configure(Project project) {
        ApplicationExtension application = project.extensions.findByType(ApplicationExtension)
        if (application != null) {
            application.buildFeatures.compose = true
            return
        }

        LibraryExtension library = project.extensions.findByType(LibraryExtension)
        if (library != null) {
            library.buildFeatures.compose = true
            return
        }

        throw new SimpleDslConfigurationException(
                'SimpleDSL Android configuration error\n' +
                "Project: ${project.path}\n" +
                'Capability: compose\n' +
                'Problem: Android application or library extension is not available')
    }

    private ComposeCapabilityConfigurer() {}
}
