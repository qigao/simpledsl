package io.github.qigao.simpledsl.gradle.manifest

import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlTable

final class TomlManifestReader implements ManifestReader {
    @Override
    Map<String, Object> read(File file) {
        def parsed = Toml.parse(file.toPath())
        if (parsed.hasErrors()) {
            throw new IllegalArgumentException(
                    parsed.errors().collect { error -> error.toString() }.join('; '))
        }
        normalizeTable(parsed)
    }

    private static Map<String, Object> normalizeTable(TomlTable table) {
        Map<String, Object> result = new LinkedHashMap<>()
        table.keySet().each { key -> result.put(key, normalize(table.get(key))) }
        result
    }

    private static Object normalize(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value
        }
        if (value instanceof TomlTable) {
            return normalizeTable(value as TomlTable)
        }
        if (value instanceof TomlArray) {
            TomlArray array = value as TomlArray
            List<Object> result = new ArrayList<>(array.size())
            for (int i = 0; i < array.size(); i++) {
                result.add(normalize(array.get(i)))
            }
            return result
        }
        throw new IllegalArgumentException("unsupported TOML value type '${value.getClass().name}'")
    }
}
