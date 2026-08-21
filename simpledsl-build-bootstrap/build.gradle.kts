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
    implementation(libs.tomlj)
    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}
