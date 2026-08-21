plugins {
    groovy
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
        create("simpleDslModule") {
            id = "io.github.qigao.simpledsl.module"
            implementationClass = "io.github.qigao.simpledsl.gradle.SimpleDslModulePlugin"
            displayName = "SimpleDSL Module"
            description = "SimpleDSL module model, dependency catalog, capabilities, and diagnostics"
            tags = listOf("gradle", "build-platform", "modules")
        }
        create("simpleDslInternalCatalog") {
            id = "io.github.qigao.simpledsl.internal.catalog"
            implementationClass = "io.github.qigao.simpledsl.gradle.catalog.SimpleDslCatalogPlugin"
            displayName = "SimpleDSL Internal Catalog"
            description = "Internal SimpleDSL dependency catalog bridge"
            tags = listOf("gradle", "build-platform")
        }
        create("simpleDslJavaLibrary") {
            id = "io.github.qigao.simpledsl.java-library"
            implementationClass = "io.github.qigao.simpledsl.gradle.module.SimpleDslJavaLibraryPlugin"
            displayName = "SimpleDSL Java Library"
            description = "SimpleDSL Java library convention"
            tags = listOf("gradle", "java", "library")
        }
        create("simpleDslSpringLibrary") {
            id = "io.github.qigao.simpledsl.spring-library"
            implementationClass = "io.github.qigao.simpledsl.gradle.module.SimpleDslSpringLibraryPlugin"
            displayName = "SimpleDSL Spring Library"
            description = "SimpleDSL Spring library convention"
            tags = listOf("gradle", "spring", "library")
        }
        create("simpleDslSpringService") {
            id = "io.github.qigao.simpledsl.spring-service"
            implementationClass = "io.github.qigao.simpledsl.gradle.module.SimpleDslSpringServicePlugin"
            displayName = "SimpleDSL Spring Service"
            description = "SimpleDSL Spring Boot service convention"
            tags = listOf("gradle", "spring", "spring-boot")
        }
    }
}
