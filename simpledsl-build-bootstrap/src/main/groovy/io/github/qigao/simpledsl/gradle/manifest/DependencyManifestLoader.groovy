package io.github.qigao.simpledsl.gradle.manifest

import org.gradle.api.GradleException

import java.util.Locale

final class DependencyManifestLoader {
    private static final Set<String> ROOT_KEYS = [
            'include', 'simpledsl', 'versions', 'libraries', 'plugins'
    ] as Set
    private static final ManifestReader TOML_READER = new TomlManifestReader()
    private static final ManifestReader YAML_READER = new YamlManifestReader()

    static DependencyRegistry load(File rootManifest) {
        State state = new State()
        loadFile(rootManifest.canonicalFile, state, new LinkedHashSet<File>())
        if (state.javaVersion == null) {
            fail(rootManifest, null, null, 'missing simpledsl.java')
        }

        Map<String, VersionSpec> versions = new LinkedHashMap<>()
        state.versionValues.each { id, raw ->
            versions.put(id, new VersionSpec(id, raw.value as String))
        }

        Map<String, LibrarySpec> libraries = new LinkedHashMap<>()
        state.libraryValues.each { id, raw ->
            int owners = (raw.explicitVersion ? 1 : 0) + (raw.versionRef ? 1 : 0) + (raw.platform ? 1 : 0)
            if (owners != 1) {
                fail(
                        raw.file as File,
                        'Library',
                        id,
                        'exactly one version owner is required: version, version.ref, or platform')
            }
            if (!raw.platform) {
                String version = resolveVersion(raw, versions, raw.file as File, 'Library', id)
                libraries.put(id, new LibrarySpec(id, raw.module as String, version, null))
            }
        }

        Map<String, Map> platformReferences = new LinkedHashMap<>()
        state.libraryValues.each { id, raw ->
            if (raw.platform) {
                String alias = raw.platform as String
                if (!platformReferences.containsKey(alias)) {
                    platformReferences.put(alias, [id: id, file: raw.file])
                }
            }
        }

        Map<String, PlatformSpec> platforms = new LinkedHashMap<>()
        platformReferences.each { alias, reference ->
            Map ownerRaw = state.libraryValues.get(alias)
            if (ownerRaw == null) {
                fail(
                        reference.file as File,
                        'Library',
                        reference.id as String,
                        "unknown platform '${alias}'")
            }
            if (ownerRaw.platform) {
                fail(
                        ownerRaw.file as File,
                        'Library',
                        alias,
                        "platform library '${alias}' cannot itself use platform")
            }
            LibrarySpec owner = libraries.get(alias)
            if (owner == null || owner.version == null) {
                fail(
                        ownerRaw.file as File,
                        'Library',
                        alias,
                        'platform library must have version or version.ref')
            }
            platforms.put(alias, new PlatformSpec(alias, owner.module, owner.version))
        }

        state.libraryValues.each { id, raw ->
            if (raw.platform) {
                libraries.put(id, new LibrarySpec(id, raw.module as String, null, raw.platform as String))
            }
        }

        Map<String, PluginSpec> plugins = new LinkedHashMap<>()
        Map<String, PluginSpec> pluginsByGradleId = new LinkedHashMap<>()
        state.pluginValues.each { alias, raw ->
            int owners = (raw.explicitVersion ? 1 : 0) + (raw.versionRef ? 1 : 0)
            if (owners != 1) {
                fail(raw.file as File, 'Plugin', alias, 'exactly one version owner is required: version or version.ref')
            }
            String version = resolveVersion(raw, versions, raw.file as File, 'Plugin', alias)
            PluginSpec spec = new PluginSpec(alias, raw.id as String, raw.module as String, version)
            PluginSpec previous = pluginsByGradleId.put(spec.id, spec)
            if (previous != null) {
                fail(
                        raw.file as File,
                        'Plugin',
                        alias,
                        "duplicate Gradle plugin id '${spec.id}' (already defined by '${previous.alias}')")
            }
            plugins.put(alias, spec)
        }

        new DependencyRegistry(state.javaVersion as int, versions, platforms, libraries, plugins, pluginsByGradleId)
    }

    private static void loadFile(File file, State state, LinkedHashSet<File> stack) {
        File canonical = file.canonicalFile
        if (stack.contains(canonical)) {
            List<File> cycleFiles = new ArrayList<>(stack)
            cycleFiles.add(canonical)
            fail(canonical, null, null, "include cycle detected: ${cycleFiles.collect { it.name }.join(' -> ')}")
        }
        if (state.loaded.contains(canonical)) {
            return
        }
        if (!canonical.isFile()) {
            fail(canonical, null, null, 'manifest file does not exist')
        }

        Map<String, Object> parsed = readManifest(canonical)
        rejectUnknownKeys(parsed.keySet(), ROOT_KEYS, canonical, null, null)

        stack.add(canonical)
        Object includeNode = parsed.get('include')
        if (includeNode != null) {
            if (!(includeNode instanceof List)) {
                fail(canonical, null, null, 'include must be an array of file names')
            }
            (includeNode as List).each { value ->
                if (!(value instanceof String) || (value as String).trim().isEmpty()) {
                    fail(canonical, null, null, 'include entries must be non-empty strings')
                }
                loadFile(new File(canonical.parentFile, value as String), state, stack)
            }
        }

        Map simpleDsl = optionalTable(parsed, 'simpledsl', canonical, 'SimpleDSL', 'simpledsl')
        if (simpleDsl != null) {
            rejectUnknownKeys(simpleDsl.keySet() as Set<String>, ['java'] as Set, canonical, 'SimpleDSL', 'simpledsl')
            Object javaNode = simpleDsl.get('java')
            if (!isInteger(javaNode)) {
                fail(canonical, 'SimpleDSL', 'simpledsl', 'java must be an integer')
            }
            if (state.javaVersion != null) {
                fail(canonical, 'SimpleDSL', 'simpledsl', 'duplicate simpledsl.java')
            }
            state.javaVersion = (javaNode as Number).intValue()
        }

        parseVersions(optionalTable(parsed, 'versions', canonical, 'Versions', 'versions'), canonical, state)
        parseLibraries(optionalTable(parsed, 'libraries', canonical, 'Libraries', 'libraries'), canonical, state)
        parsePlugins(optionalTable(parsed, 'plugins', canonical, 'Plugins', 'plugins'), canonical, state)

        stack.remove(canonical)
        state.loaded.add(canonical)
    }

    private static Map<String, Object> readManifest(File file) {
        String name = file.name.toLowerCase(Locale.ROOT)
        ManifestReader reader
        if (name.endsWith('.toml')) {
            reader = TOML_READER
        } else if (name.endsWith('.yml') || name.endsWith('.yaml')) {
            reader = YAML_READER
        } else {
            fail(file, null, null, 'unsupported manifest extension; expected .toml, .yml, or .yaml')
            return [:]
        }

        try {
            return reader.read(file)
        } catch (GradleException error) {
            throw error
        } catch (Exception error) {
            String problem = error.message ?: error.getClass().simpleName
            fail(file, null, null, "cannot parse manifest: ${problem}")
            return [:]
        }
    }

    private static void parseVersions(Map table, File file, State state) {
        if (table == null) return
        table.each { id, value ->
            if (!(value instanceof String)) {
                fail(file, 'Version', id as String, 'value must be a string')
            }
            putUnique(
                    state.versionValues,
                    id as String,
                    [value: value, file: file],
                    file,
                    'Version')
        }
    }

    private static void parseLibraries(Map table, File file, State state) {
        if (table == null) return
        table.each { id, node ->
            Map value = requireTableNode(node, file, 'Library', id as String)
            rejectUnknownKeys(
                    value.keySet() as Set<String>,
                    ['module', 'version', 'platform'] as Set,
                    file,
                    'Library',
                    id as String)
            String module = requireString(value, 'module', file, 'Library', id as String)
            validateModule(module, file, 'Library', id as String)
            Map version = readVersion(value, file, 'Library', id as String)
            Object platformNode = value.get('platform')
            if (platformNode != null && (!(platformNode instanceof String) || (platformNode as String).trim().isEmpty())) {
                fail(file, 'Library', id as String, 'platform must be a non-empty string')
            }
            putUnique(
                    state.libraryValues,
                    id as String,
                    [
                            module: module,
                            explicitVersion: version.explicit,
                            versionRef: version.ref,
                            platform: platformNode,
                            file: file
                    ],
                    file,
                    'Library')
        }
    }

    private static void parsePlugins(Map table, File file, State state) {
        if (table == null) return
        table.each { alias, node ->
            Map value = requireTableNode(node, file, 'Plugin', alias as String)
            rejectUnknownKeys(
                    value.keySet() as Set<String>,
                    ['id', 'module', 'version'] as Set,
                    file,
                    'Plugin',
                    alias as String)
            String id = requireString(value, 'id', file, 'Plugin', alias as String)
            Object moduleNode = value.get('module')
            if (moduleNode != null && (!(moduleNode instanceof String) || (moduleNode as String).trim().isEmpty())) {
                fail(file, 'Plugin', alias as String, 'module must be a non-empty string')
            }
            if (moduleNode != null) {
                validateModule(moduleNode as String, file, 'Plugin', alias as String)
            }
            Map version = readVersion(value, file, 'Plugin', alias as String)
            putUnique(
                    state.pluginValues,
                    alias as String,
                    [
                            id: id,
                            module: moduleNode,
                            explicitVersion: version.explicit,
                            versionRef: version.ref,
                            file: file
                    ],
                    file,
                    'Plugin')
        }
    }

    private static Map readVersion(Map table, File file, String kind, String id) {
        Object node = table.get('version')
        if (node == null) return [explicit: null, ref: null]
        if (node instanceof String) {
            if ((node as String).trim().isEmpty()) {
                fail(file, kind, id, 'version must be a non-empty string')
            }
            return [explicit: node, ref: null]
        }
        if (node instanceof Map) {
            Map nested = node as Map
            rejectUnknownKeys(nested.keySet() as Set<String>, ['ref'] as Set, file, kind, id)
            String ref = requireString(nested, 'ref', file, kind, id)
            return [explicit: null, ref: ref]
        }
        fail(file, kind, id, 'version must be a string or version.ref table')
        [:]
    }

    private static String resolveVersion(
            Map raw,
            Map<String, VersionSpec> versions,
            File file,
            String kind,
            String id) {
        if (raw.explicitVersion) return raw.explicitVersion as String
        String ref = raw.versionRef as String
        VersionSpec spec = versions.get(ref)
        if (spec == null) {
            fail(file, kind, id, "unknown version.ref '${ref}'")
        }
        spec.value
    }

    private static Map optionalTable(
            Map<String, Object> parent,
            String key,
            File file,
            String kind,
            String id) {
        Object node = parent.get(key)
        if (node == null) return null
        requireTableNode(node, file, kind, id)
    }

    private static Map requireTableNode(Object node, File file, String kind, String id) {
        if (!(node instanceof Map)) {
            fail(file, kind, id, 'definition must be a table')
        }
        node as Map
    }

    private static String requireString(Map table, String key, File file, String kind, String id) {
        Object value = table.get(key)
        if (!(value instanceof String) || (value as String).trim().isEmpty()) {
            fail(file, kind, id, "${key} must be a non-empty string")
        }
        value as String
    }

    private static boolean isInteger(Object value) {
        value instanceof Byte ||
                value instanceof Short ||
                value instanceof Integer ||
                value instanceof Long ||
                value instanceof BigInteger
    }

    private static void validateModule(String module, File file, String kind, String id) {
        String[] parts = module.split(':', -1)
        if (parts.length != 2 || parts.any { part -> part.trim().isEmpty() }) {
            fail(file, kind, id, "malformed module coordinate '${module}', expected group:name")
        }
    }

    private static void rejectUnknownKeys(
            Set<String> actual,
            Set<String> allowed,
            File file,
            String kind,
            String id) {
        Set<String> unknown = new LinkedHashSet<>(actual)
        unknown.removeAll(allowed)
        if (!unknown.isEmpty()) {
            fail(file, kind, id, "unsupported key(s): ${unknown.sort().join(', ')}")
        }
    }

    private static void putUnique(Map values, String id, Object value, File file, String kind) {
        if (values.containsKey(id)) {
            fail(file, kind, id, "duplicate ${kind.toLowerCase()} id '${id}'")
        }
        values.put(id, value)
    }

    private static void fail(File file, String kind, String id, String problem) {
        List<String> lines = [
                'SimpleDSL dependency manifest error',
                "File: ${file.canonicalPath}"
        ]
        if (kind != null && id != null) {
            lines.add("${kind}: ${id}")
        }
        lines.add("Problem: ${problem}")
        throw new GradleException(lines.join('\n'))
    }

    private static final class State {
        Integer javaVersion
        final Set<File> loaded = new LinkedHashSet<>()
        final Map<String, Map> versionValues = new LinkedHashMap<>()
        final Map<String, Map> libraryValues = new LinkedHashMap<>()
        final Map<String, Map> pluginValues = new LinkedHashMap<>()
    }
}
