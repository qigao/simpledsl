package io.github.qigao.simpledsl.gradle.catalog

import org.gradle.api.GradleException
import org.gradle.api.Project

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

final class SimpleDslRegistryBridge {
    static final int EXPECTED_SCHEMA_VERSION = 2

    static DependencyCatalogSnapshot fromProject(Project project) {
        def registration = project.gradle.sharedServices.registrations.findByName('simpledslDependencyRegistry')
        if (registration == null) {
            fail('simpledslDependencyRegistry is not available; apply io.github.qigao.simpledsl.settings in this build')
        }

        Object service = registration.service.get()
        Object raw
        try {
            Method snapshotMethod = service.getClass().getMethod('snapshot')
            raw = snapshotMethod.invoke(service)
        } catch (NoSuchMethodException e) {
            fail('simpledslDependencyRegistry does not expose the snapshot() contract', e)
            return null
        } catch (InvocationTargetException e) {
            Throwable cause = e.cause ?: e
            throw new GradleException(
                    "SimpleDSL bootstrap error\nProblem: dependency snapshot export failed\nCause: ${cause.message}",
                    cause)
        } catch (ReflectiveOperationException e) {
            fail("cannot invoke dependency snapshot(): ${e.message}", e)
            return null
        }

        if (!(raw instanceof Map)) {
            fail("dependency snapshot() returned ${raw == null ? 'null' : raw.getClass().name}, expected java.util.Map")
        }
        fromSnapshot(raw as Map)
    }

    static DependencyCatalogSnapshot fromSnapshot(Map raw) {
        Object schema = raw.get('schemaVersion')
        if (!(schema instanceof Number)) {
            fail('dependency snapshot schemaVersion must be an integer')
        }
        int actualSchema = (schema as Number).intValue()
        if (actualSchema != EXPECTED_SCHEMA_VERSION) {
            throw new GradleException(
                    'SimpleDSL bootstrap error\n' +
                    'Problem: unsupported dependency snapshot schema\n' +
                    "Expected: ${EXPECTED_SCHEMA_VERSION}\n" +
                    "Actual: ${actualSchema}")
        }

        Map policies = table(raw, 'policies')
        Integer javaToolchain = null
        Object javaNode = policies.get('java')
        if (javaNode != null) {
            Map javaPolicy = entry(javaNode, "policy 'java'")
            javaToolchain = requiredInteger(javaPolicy, 'toolchain', "policy 'java'")
        }

        Map platformsRaw = table(raw, 'platforms')
        Map librariesRaw = table(raw, 'libraries')
        Map pluginsRaw = table(raw, 'plugins')

        Map<String, CatalogPlatform> platforms = new LinkedHashMap<>()
        platformsRaw.each { alias, node ->
            Map item = entry(node, "platform '${alias}'")
            platforms.put(alias as String, new CatalogPlatform(
                    alias as String,
                    requiredString(item, 'module', "platform '${alias}'"),
                    requiredString(item, 'version', "platform '${alias}'")))
        }

        Map<String, CatalogLibrary> libraries = new LinkedHashMap<>()
        librariesRaw.each { alias, node ->
            Map item = entry(node, "library '${alias}'")
            libraries.put(alias as String, new CatalogLibrary(
                    alias as String,
                    requiredString(item, 'module', "library '${alias}'"),
                    optionalString(item, 'version', "library '${alias}'"),
                    optionalString(item, 'platform', "library '${alias}'")))
        }

        Map<String, CatalogPlugin> plugins = new LinkedHashMap<>()
        pluginsRaw.each { alias, node ->
            Map item = entry(node, "plugin '${alias}'")
            plugins.put(alias as String, new CatalogPlugin(
                    alias as String,
                    requiredString(item, 'id', "plugin '${alias}'"),
                    optionalString(item, 'module', "plugin '${alias}'"),
                    requiredString(item, 'version', "plugin '${alias}'")))
        }

        new DependencyCatalogSnapshot(javaToolchain, platforms, libraries, plugins)
    }

    private static Map table(Map raw, String key) {
        Object value = raw.get(key)
        if (!(value instanceof Map)) {
            fail("dependency snapshot '${key}' must be a map")
        }
        value as Map
    }

    private static Map entry(Object value, String subject) {
        if (!(value instanceof Map)) {
            fail("dependency snapshot ${subject} must be a map")
        }
        value as Map
    }

    private static int requiredInteger(Map entry, String key, String subject) {
        Object value = entry.get(key)
        if (!(value instanceof Number)) {
            fail("dependency snapshot ${subject}.${key} must be an integer")
        }
        int result = (value as Number).intValue()
        if (result <= 0 || (value as Number).longValue() != result) {
            fail("dependency snapshot ${subject}.${key} must be a positive integer")
        }
        result
    }

    private static String requiredString(Map entry, String key, String subject) {
        Object value = entry.get(key)
        if (!(value instanceof String) || (value as String).trim().isEmpty()) {
            fail("dependency snapshot ${subject}.${key} must be a non-empty string")
        }
        value as String
    }

    private static String optionalString(Map entry, String key, String subject) {
        Object value = entry.get(key)
        if (value == null) return null
        if (!(value instanceof String) || (value as String).trim().isEmpty()) {
            fail("dependency snapshot ${subject}.${key} must be a non-empty string when present")
        }
        value as String
    }

    private static void fail(String problem, Throwable cause = null) {
        String message = "SimpleDSL bootstrap error\nProblem: ${problem}"
        if (cause == null) {
            throw new GradleException(message)
        }
        throw new GradleException(message, cause)
    }
}
