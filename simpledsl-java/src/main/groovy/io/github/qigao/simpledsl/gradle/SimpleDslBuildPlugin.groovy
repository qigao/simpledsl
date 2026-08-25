package io.github.qigao.simpledsl.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Public SimpleDSL project entry point.
 *
 * Module types, features and schema options are internal SimpleDSL behavior;
 * they are not public Gradle plugin IDs.
 */
class SimpleDslBuildPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply(SimpleDslModulePlugin)
    }
}
