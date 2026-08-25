package io.github.qigao.simpledsl.gradle.manifest

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

final class YamlManifestReader implements ManifestReader {
    private static final LoadSettings SETTINGS = LoadSettings.builder()
            .setAllowDuplicateKeys(false)
            .build()

    @Override
    Map<String, Object> read(File file) {
        Object parsed
        file.withReader('UTF-8') { reader ->
            parsed = new Load(SETTINGS).loadFromReader(reader)
        }
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException('YAML document root must be a mapping')
        }
        normalizeMap(parsed as Map)
    }

    private static Map<String, Object> normalizeMap(Map source) {
        Map<String, Object> result = new LinkedHashMap<>()
        source.each { key, value ->
            if (!(key instanceof String)) {
                throw new IllegalArgumentException('YAML mapping keys must be strings')
            }
            result.put(key as String, normalize(value))
        }
        result
    }

    private static Object normalize(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value
        }
        if (value instanceof Map) {
            return normalizeMap(value as Map)
        }
        if (value instanceof List) {
            return (value as List).collect { nested -> normalize(nested) }
        }
        throw new IllegalArgumentException("unsupported YAML value type '${value.getClass().name}'")
    }
}
