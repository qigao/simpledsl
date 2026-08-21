import org.gradle.api.tasks.WriteProperties

plugins {
    groovy
    `maven-publish`
    alias(libs.plugins.plugin.publish)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
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

val distributionVersion = version.toString()
val springBootPluginVersion = libs.versions.spring.boot.get()
val graalvmNativePluginVersion = libs.versions.graalvm.native.get()
val jooqPluginVersion = libs.versions.jooq.get()
val jsonschema2pojoPluginVersion = libs.versions.jsonschema2pojo.get()

val generateDistributionMetadata = tasks.register<WriteProperties>("generateDistributionMetadata") {
    destinationFile.set(
        layout.buildDirectory.file("generated-resources/simpledsl/META-INF/simpledsl/distribution.properties")
    )
    property("version", distributionVersion)
    property("springBootPluginVersion", springBootPluginVersion)
    property("graalvmNativePluginVersion", graalvmNativePluginVersion)
    property("jooqPluginVersion", jooqPluginVersion)
    property("jsonschema2pojoPluginVersion", jsonschema2pojoPluginVersion)
}

sourceSets {
    named("main") {
        resources.srcDir(layout.buildDirectory.dir("generated-resources/simpledsl"))
    }
}

tasks.processResources {
    dependsOn(generateDistributionMetadata)
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

publishing {
    repositories {
        maven {
            name = "testPlugin"
            url = rootProject.layout.buildDirectory.dir("test-plugin-repo").get().asFile.toURI()
        }
    }
}
