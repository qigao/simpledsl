package io.github.qigao.simpledsl.gradle.internal

import io.github.qigao.simpledsl.gradle.SimpleDslDependencyAccess
import io.github.qigao.simpledsl.gradle.core.SimpleDslProjectCorePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion

final class SimpleDslJavaBasePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply('java')
        project.pluginManager.apply(SimpleDslProjectCorePlugin)

        int javaVersion = SimpleDslDependencyAccess.javaVersion(project)
        project.extensions.getByType(JavaPluginExtension).toolchain.languageVersion
                .set(JavaLanguageVersion.of(javaVersion))
        project.tasks.withType(JavaCompile).configureEach { task ->
            task.options.release.set(javaVersion)
            task.options.encoding = 'UTF-8'
        }
    }
}
