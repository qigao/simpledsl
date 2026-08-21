package io.github.qigao.simpledsl.gradle.settings

import io.github.qigao.simpledsl.gradle.manifest.DependencyRegistryService
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

class SimpleDslSettingsPlugin implements Plugin<Settings> {
    @Override
    void apply(Settings settings) {
        SimpleDslSettingsExtension extension = settings.extensions.create('simpledslSettings', SimpleDslSettingsExtension)
        extension.repositoryRoot.convention(settings.layout.settingsDirectory)
        extension.dependencyManifest.convention(extension.repositoryRoot.file('gradle/simpledsl/dependencies.toml'))
        extension.modulesManifest.convention(extension.repositoryRoot.file('gradle/simpledsl/modules.toml'))
        extension.moduleDiscovery.convention(true)

        def serviceHolder = [provider: null]
        settings.pluginManagement.resolutionStrategy.eachPlugin { details ->
            def provider = serviceHolder.provider
            if (provider == null) return
            def managed = provider.get().pluginByGradleId(details.requested.id.id)
            if (managed == null) return
            String requested = details.requested.version
            if (requested && requested != managed.version) {
                throw new GradleException(
                        "SimpleDSL dependency version conflict\n" +
                        "Plugin: ${managed.id}\nRequested: ${requested}\nManaged: ${managed.version}")
            }
            if (managed.module) {
                details.useModule(managed.coordinate())
            } else {
                details.useVersion(managed.version)
            }
        }

        settings.gradle.settingsEvaluated {
            def serviceProvider = settings.gradle.sharedServices.registerIfAbsent(
                    'simpledslDependencyRegistry', DependencyRegistryService) { spec ->
                spec.parameters.manifestFile.set(extension.dependencyManifest)
            }
            serviceHolder.provider = serviceProvider
            serviceProvider.get().javaVersion()

            ProjectRegistry projectRegistry = null
            if (extension.moduleDiscovery.get()) {
                projectRegistry = ProjectDiscovery.discover(
                        extension.repositoryRoot.get().asFile,
                        extension.modulesManifest.get().asFile)
                projectRegistry.projects().each { projectSpec ->
                    settings.include(projectSpec.gradlePath)
                    def descriptor = settings.project(projectSpec.gradlePath)
                    descriptor.projectDir = projectSpec.directory
                    descriptor.buildFileName = projectSpec.buildFile
                }
            }

            Map<String, Object> snapshot = serviceProvider.get().snapshot()
            List<String> platformLines = sortedLines(snapshot.platforms as Map) { alias, value ->
                Map entry = value as Map
                "${alias} -> ${entry.module}:${entry.version}"
            }
            List<String> pluginLines = sortedLines(snapshot.plugins as Map) { alias, value ->
                Map entry = value as Map
                "${alias} -> ${entry.id}:${entry.version}"
            }
            List<String> libraryLines = sortedLines(snapshot.libraries as Map) { alias, value ->
                Map entry = value as Map
                String notation = entry.version ? "${entry.module}:${entry.version}" : entry.module as String
                entry.platform ? "${alias} -> ${notation} [platform=${entry.platform}]" : "${alias} -> ${notation}"
            }

            File repositoryRoot = extension.repositoryRoot.get().asFile.canonicalFile
            List<String> projectLines = projectRegistry == null ? [] : projectRegistry.projects().collect { projectSpec ->
                String relative = repositoryRoot.toPath()
                        .relativize(projectSpec.directory.toPath()).toString()
                        .replace('\\', '/')
                "${projectSpec.gradlePath} | ${relative} | ${projectSpec.source} | ${projectSpec.buildFile}"
            }.sort()

            settings.gradle.rootProject { root ->
                root.tasks.register('simpledslDependencies', SimpleDslDependenciesTask) { task ->
                    task.group = 'SimpleDSL'
                    task.description = 'Print SimpleDSL dependency manifest diagnostics.'
                    task.javaVersion.set(snapshot.javaVersion as Integer)
                    task.platformLines.set(platformLines)
                    task.pluginLines.set(pluginLines)
                    task.libraryLines.set(libraryLines)
                }
                root.tasks.register('simpledslProjects', SimpleDslProjectsTask) { task ->
                    task.group = 'SimpleDSL'
                    task.description = 'Print SimpleDSL project discovery diagnostics.'
                    task.projectLines.set(projectLines)
                }
            }
        }
    }

    private static List<String> sortedLines(Map values, Closure<String> formatter) {
        values.keySet().collect { it as String }.sort().collect { alias -> formatter.call(alias, values.get(alias)) }
    }
}
