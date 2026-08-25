import org.gradle.plugin.compatibility.compatibility

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
    implementation(project(":simpledsl-core"))
    implementation(localGroovy())
    implementation(libs.spring.boot.gradle)
    implementation(libs.graalvm.native.gradle)
    implementation(libs.jooq.codegen.gradle)
    implementation(libs.jooq.core)
    implementation(libs.jooq.meta)
    implementation(libs.jsonschema2pojo.gradle)
    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    website = "https://github.com/qigao/simpledsl"
    vcsUrl = "https://github.com/qigao/simpledsl.git"
    plugins {
        create("simpleDslJava") {
            id = "io.github.qigao.simpledsl.java"
            implementationClass = "io.github.qigao.simpledsl.gradle.java.SimpleDslJavaPlugin"
            displayName = "SimpleDSL Java"
            description = "SimpleDSL Java and Spring build backend"
            tags = listOf("build-platform", "java", "spring")
            compatibility {
                features { configurationCache = true }
            }
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
