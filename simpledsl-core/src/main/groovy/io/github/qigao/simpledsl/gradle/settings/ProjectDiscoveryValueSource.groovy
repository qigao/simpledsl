package io.github.qigao.simpledsl.gradle.settings

import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * Computes the logical module-discovery snapshot without exposing every file-system
 * probe performed by the scanner as an individual Configuration Cache input.
 *
 * Gradle tracks only the normalized value returned by this ValueSource. Therefore
 * transient output directories such as build/ and .gradle/ do not invalidate the
 * cache when they are ignored by ProjectDiscovery, while an actual change to the
 * discovered module set still changes this value and invalidates the cache.
 */
abstract class ProjectDiscoveryValueSource implements ValueSource<List<Map<String, String>>, Parameters> {
    interface Parameters extends ValueSourceParameters {
        Property<String> getRepositoryRootPath()
        Property<String> getModulesManifestPath()
    }

    @Override
    List<Map<String, String>> obtain() {
        File repositoryRoot = new File(parameters.repositoryRootPath.get()).canonicalFile
        File modulesManifest = new File(parameters.modulesManifestPath.get()).canonicalFile
        ProjectRegistry registry = ProjectDiscovery.discover(repositoryRoot, modulesManifest)

        List<Map<String, String>> snapshot = registry.projects().collect { ProjectSpec spec ->
            String relativeDirectory = repositoryRoot.toPath()
                    .relativize(spec.directory.toPath())
                    .toString()
                    .replace('\\', '/')
            Collections.unmodifiableMap(new LinkedHashMap<String, String>([
                    gradlePath       : spec.gradlePath,
                    relativeDirectory: relativeDirectory,
                    source           : spec.source,
                    buildFile        : spec.buildFile
            ]))
        }
        Collections.unmodifiableList(snapshot)
    }
}
