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

val configurationCacheVerifiedPluginIds = setOf(
    "io.github.qigao.simpledsl.module",
    "io.github.qigao.simpledsl.spring-service",
    "io.github.qigao.simpledsl.feature.web"
)

gradlePlugin {
    website = "https://github.com/qigao/simpledsl"
    vcsUrl = "https://github.com/qigao/simpledsl.git"
    plugins {
        create("simpleDslModule") {
            id = "io.github.qigao.simpledsl.module"
            implementationClass = "io.github.qigao.simpledsl.gradle.SimpleDslModulePlugin"
            displayName = "SimpleDSL Module"
            description = "SimpleDSL module model, dependency catalog, capabilities, and diagnostics"
            tags = listOf("build-platform", "modules")
        }
        create("simpleDslJavaLibrary") {
            id = "io.github.qigao.simpledsl.java-library"
            implementationClass = "io.github.qigao.simpledsl.gradle.module.SimpleDslJavaLibraryPlugin"
            displayName = "SimpleDSL Java Library"
            description = "SimpleDSL Java library convention"
            tags = listOf("java", "library")
        }
        create("simpleDslSpringLibrary") {
            id = "io.github.qigao.simpledsl.spring-library"
            implementationClass = "io.github.qigao.simpledsl.gradle.module.SimpleDslSpringLibraryPlugin"
            displayName = "SimpleDSL Spring Library"
            description = "SimpleDSL Spring library convention"
            tags = listOf("spring", "library")
        }
        create("simpleDslSpringService") {
            id = "io.github.qigao.simpledsl.spring-service"
            implementationClass = "io.github.qigao.simpledsl.gradle.module.SimpleDslSpringServicePlugin"
            displayName = "SimpleDSL Spring Service"
            description = "SimpleDSL Spring Boot service convention"
            tags = listOf("spring", "spring-boot")
        }
        create("simpleDslFeatureAop") {
            id = "io.github.qigao.simpledsl.feature.aop"
            implementationClass = "io.github.qigao.simpledsl.gradle.feature.SimpleDslAopPlugin"
            displayName = "SimpleDSL AOP Feature"
            description = "Enable Spring AOP dependencies for a SimpleDSL module"
            tags = listOf("spring", "aop")
        }
        create("simpleDslFeatureTransaction") {
            id = "io.github.qigao.simpledsl.feature.transaction"
            implementationClass = "io.github.qigao.simpledsl.gradle.feature.SimpleDslTransactionPlugin"
            displayName = "SimpleDSL Transaction Feature"
            description = "Enable Spring transaction dependencies for a SimpleDSL module"
            tags = listOf("spring", "transactions")
        }
        create("simpleDslFeatureWeb") {
            id = "io.github.qigao.simpledsl.feature.web"
            implementationClass = "io.github.qigao.simpledsl.gradle.feature.SimpleDslWebPlugin"
            displayName = "SimpleDSL Web Feature"
            description = "Enable Spring MVC dependencies for a SimpleDSL service"
            tags = listOf("spring", "web")
        }
        create("simpleDslFeatureHttpClient") {
            id = "io.github.qigao.simpledsl.feature.http-client"
            implementationClass = "io.github.qigao.simpledsl.gradle.feature.SimpleDslHttpClientPlugin"
            displayName = "SimpleDSL HTTP Client Feature"
            description = "Enable Spring HTTP client dependencies for a SimpleDSL module"
            tags = listOf("spring", "http")
        }
        create("simpleDslFeatureMessaging") {
            id = "io.github.qigao.simpledsl.feature.messaging"
            implementationClass = "io.github.qigao.simpledsl.gradle.feature.SimpleDslMessagingPlugin"
            displayName = "SimpleDSL Messaging Feature"
            description = "Enable Spring messaging dependencies for a SimpleDSL module"
            tags = listOf("spring", "messaging")
        }
        create("simpleDslFeatureJdbc") {
            id = "io.github.qigao.simpledsl.feature.jdbc"
            implementationClass = "io.github.qigao.simpledsl.gradle.feature.SimpleDslJdbcPlugin"
            displayName = "SimpleDSL JDBC Feature"
            description = "Enable Spring JDBC dependencies for a SimpleDSL module"
            tags = listOf("spring", "jdbc")
        }
        create("simpleDslFeatureJooq") {
            id = "io.github.qigao.simpledsl.feature.jooq"
            implementationClass = "io.github.qigao.simpledsl.gradle.feature.SimpleDslJooqPlugin"
            displayName = "SimpleDSL jOOQ Feature"
            description = "Enable Spring jOOQ dependencies for a SimpleDSL module"
            tags = listOf("spring", "jooq")
        }
        create("simpleDslFeatureJpa") {
            id = "io.github.qigao.simpledsl.feature.jpa"
            implementationClass = "io.github.qigao.simpledsl.gradle.feature.SimpleDslJpaPlugin"
            displayName = "SimpleDSL JPA Feature"
            description = "Enable Spring Data JPA dependencies for a SimpleDSL module"
            tags = listOf("spring", "jpa")
        }
        create("simpleDslFeatureRedis") {
            id = "io.github.qigao.simpledsl.feature.redis"
            implementationClass = "io.github.qigao.simpledsl.gradle.feature.SimpleDslRedisPlugin"
            displayName = "SimpleDSL Redis Feature"
            description = "Enable Spring Data Redis dependencies for a SimpleDSL module"
            tags = listOf("spring", "redis")
        }
        create("simpleDslFeatureNative") {
            id = "io.github.qigao.simpledsl.feature.native"
            implementationClass = "io.github.qigao.simpledsl.gradle.feature.SimpleDslNativePlugin"
            displayName = "SimpleDSL Native Feature"
            description = "Enable GraalVM native build support for a SimpleDSL service"
            tags = listOf("graalvm", "native")
        }
        create("simpleDslFeatureLombok") {
            id = "io.github.qigao.simpledsl.feature.lombok"
            implementationClass = "io.github.qigao.simpledsl.gradle.feature.SimpleDslLombokPlugin"
            displayName = "SimpleDSL Lombok Feature"
            description = "Enable Lombok compile-time dependencies for a SimpleDSL module"
            tags = listOf("java", "lombok")
        }
        create("simpleDslSchemaJooq") {
            id = "io.github.qigao.simpledsl.schema.jooq"
            implementationClass = "io.github.qigao.simpledsl.gradle.schema.SimpleDslJooqSchemaPlugin"
            displayName = "SimpleDSL jOOQ Schema"
            description = "Generate jOOQ sources from DDL with SimpleDSL conventions"
            tags = listOf("jooq", "codegen", "schema")
        }
        create("simpleDslSchemaJson") {
            id = "io.github.qigao.simpledsl.schema.json"
            implementationClass = "io.github.qigao.simpledsl.gradle.schema.SimpleDslJsonSchemaPlugin"
            displayName = "SimpleDSL JSON Schema"
            description = "Generate Java sources from JSON Schema with SimpleDSL conventions"
            tags = listOf("json-schema", "codegen", "schema")
        }
        configureEach {
            compatibility {
                features {
                    configurationCache = id in configurationCacheVerifiedPluginIds
                }
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
