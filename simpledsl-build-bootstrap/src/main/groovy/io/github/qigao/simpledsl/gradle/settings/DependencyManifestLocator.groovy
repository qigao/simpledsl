package io.github.qigao.simpledsl.gradle.settings

import org.gradle.api.GradleException

final class DependencyManifestLocator {
    static final List<String> DEFAULT_NAMES = [
            'dependencies.toml',
            'dependencies.yml',
            'dependencies.yaml'
    ].asImmutable()

    static File locate(File repositoryRoot) {
        File root = repositoryRoot.canonicalFile
        List<File> matches = DEFAULT_NAMES
                .collect { name -> new File(root, name) }
                .findAll { candidate -> candidate.isFile() }

        if (matches.size() == 1) {
            return matches.first().canonicalFile
        }

        List<String> lines = [
                'SimpleDSL dependency manifest error',
                "Repository: ${root.path}"
        ]
        if (matches.isEmpty()) {
            lines.add("Problem: dependency manifest not found; expected one of: ${DEFAULT_NAMES.join(', ')}")
        } else {
            lines.add("Problem: ambiguous dependency manifest; found: ${matches.collect { it.name }.join(', ')}")
        }
        throw new GradleException(lines.join('\n'))
    }
}
