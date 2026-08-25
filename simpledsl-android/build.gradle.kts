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
    implementation(libs.android.gradle)
    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
    }
}

gradlePlugin {
    website = "https://github.com/qigao/simpledsl"
    vcsUrl = "https://github.com/qigao/simpledsl.git"
    plugins {
        create("simpleDslAndroid") {
            id = "io.github.qigao.simpledsl.android"
            implementationClass = "io.github.qigao.simpledsl.gradle.android.SimpleDslAndroidPlugin"
            displayName = "SimpleDSL Android"
            description = "SimpleDSL Android build backend"
            tags = listOf("build-platform", "android")
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
