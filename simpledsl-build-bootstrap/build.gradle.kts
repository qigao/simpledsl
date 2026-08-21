plugins {
    groovy
    alias(libs.plugins.plugin.publish)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
}

dependencies {
    implementation(localGroovy())
    implementation(libs.tomlj)
    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

val springBootPluginVersion = libs.versions.spring.boot.get()
val graalvmNativePluginVersion = libs.versions.graalvm.native.get()
val jooqPluginVersion = libs.versions.jooq.get()
val jsonschema2pojoPluginVersion = libs.versions.jsonschema2pojo.get()

tasks.processResources {
    inputs.property("simpledslVersion", project.version.toString())
    inputs.property("springBootPluginVersion", springBootPluginVersion)
    inputs.property("graalvmNativePluginVersion", graalvmNativePluginVersion)
    inputs.property("jooqPluginVersion", jooqPluginVersion)
    inputs.property("jsonschema2pojoPluginVersion", jsonschema2pojoPluginVersion)
    filesMatching("META-INF/simpledsl/distribution.properties") {
        expand(
            mapOf(
                "version" to project.version.toString(),
                "springBootPluginVersion" to springBootPluginVersion,
                "graalvmNativePluginVersion" to graalvmNativePluginVersion,
                "jooqPluginVersion" to jooqPluginVersion,
                "jsonschema2pojoPluginVersion" to jsonschema2pojoPluginVersion,
            )
        )
    }
}

gradlePlugin {
    website = "https://github.com/qigao/simpledsl"
    vcsUrl = "https://github.com/qigao/simpledsl.git"
    plugins {
        create("simpleDslSettings") {
            id = "io.github.qigao.simpledsl.settings"
            implementationClass = "io.github.qigao.simpledsl.gradle.settings.SimpleDslSettingsPlugin"
            displayName = "SimpleDSL Settings"
            description = "SimpleDSL dependency manifest and module discovery settings plugin"
            tags = listOf("gradle", "build-platform", "module-discovery")
        }
    }
}
