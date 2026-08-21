package io.github.qigao.simpledsl.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Public SimpleDSL build entry point.
 *
 * Module types, features and schema options are internal capabilities
 * selected from SimpleDSL TOML configuration; they are not Gradle plugins.
 */
class SimpleDslBuildPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.extensions.create('simpledsl', SimpleDslBuildExtension)
    }
}
