package io.github.qigao.simpledsl.gradle.distribution

final class SimpleDslDistribution {
    static final String GROUP = 'io.github.qigao.simpledsl'
    static final String CORE_ARTIFACT = 'simpledsl-core'
    static final String JAVA_ARTIFACT = 'simpledsl-java'
    static final String ANDROID_ARTIFACT = 'simpledsl-android'
    static final String SETTINGS_PLUGIN_ID = 'io.github.qigao.simpledsl.settings'
    static final String JAVA_PLUGIN_ID = 'io.github.qigao.simpledsl.java'
    static final String ANDROID_PLUGIN_ID = 'io.github.qigao.simpledsl.android'
    static final String REMOVED_BUILD_PLUGIN_ID = 'io.github.qigao.simpledsl.build'

    static final Map<String, String> OWNED_PLUGIN_MODULES = Collections.unmodifiableMap([
            'org.springframework.boot'               : 'org.springframework.boot:spring-boot-gradle-plugin',
            'org.graalvm.buildtools.native'         : 'org.graalvm.buildtools:native-gradle-plugin',
            'org.jooq.jooq-codegen-gradle'          : 'org.jooq:jooq-codegen-gradle',
            'org.jsonschema2pojo'                    : 'org.jsonschema2pojo:jsonschema2pojo-gradle-plugin',
            'com.android.application'                : 'com.android.tools.build:gradle',
            'com.android.library'                    : 'com.android.tools.build:gradle',
            'org.jetbrains.kotlin.plugin.compose'   : 'org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin',
            'com.google.devtools.ksp'               : 'com.google.devtools.ksp:symbol-processing-gradle-plugin',
            'com.google.dagger.hilt.android'        : 'com.google.dagger:hilt-android-gradle-plugin'
    ] as LinkedHashMap<String, String>)

    private static final Map<String, String> OWNED_PLUGIN_VERSION_KEYS = Collections.unmodifiableMap([
            'org.springframework.boot'             : 'springBootPluginVersion',
            'org.graalvm.buildtools.native'       : 'graalvmNativePluginVersion',
            'org.jooq.jooq-codegen-gradle'        : 'jooqPluginVersion',
            'org.jsonschema2pojo'                  : 'jsonschema2pojoPluginVersion',
            'com.android.application'              : 'androidGradlePluginVersion',
            'com.android.library'                  : 'androidGradlePluginVersion',
            'org.jetbrains.kotlin.plugin.compose' : 'kotlinVersion',
            'com.google.devtools.ksp'             : 'kspVersion',
            'com.google.dagger.hilt.android'      : 'hiltVersion'
    ] as LinkedHashMap<String, String>)

    private static final Properties METADATA = loadMetadata()

    static String version() {
        required('version')
    }

    static String coreCoordinate() {
        "${GROUP}:${CORE_ARTIFACT}:${version()}"
    }

    static String javaCoordinate() {
        "${GROUP}:${JAVA_ARTIFACT}:${version()}"
    }

    static String androidCoordinate() {
        "${GROUP}:${ANDROID_ARTIFACT}:${version()}"
    }

    static String ownedPluginVersion(String pluginId) {
        String key = OWNED_PLUGIN_VERSION_KEYS.get(pluginId)
        key == null ? null : required(key)
    }

    static String ownedPluginCoordinate(String pluginId) {
        String module = OWNED_PLUGIN_MODULES.get(pluginId)
        if (module == null) return null
        "${module}:${ownedPluginVersion(pluginId)}"
    }

    private static Properties loadMetadata() {
        def stream = SimpleDslDistribution.getResourceAsStream('/META-INF/simpledsl/distribution.properties')
        if (stream == null) {
            throw new IllegalStateException('SimpleDSL distribution metadata is missing')
        }
        Properties properties = new Properties()
        stream.withCloseable { properties.load(it) }
        properties
    }

    private static String required(String key) {
        String value = METADATA.getProperty(key)
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("SimpleDSL distribution metadata '${key}' is missing")
        }
        value
    }

    private SimpleDslDistribution() {}
}
