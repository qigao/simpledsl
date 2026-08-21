plugins {
    groovy
}

dependencies {
    testImplementation(localGroovy())
    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    dependsOn(rootProject.tasks.named("publishToTestPluginRepository"))
    systemProperty(
        "simpledsl.test.repo",
        rootProject.layout.buildDirectory.dir("test-plugin-repo").get().asFile.absolutePath
    )
    systemProperty("simpledsl.test.version", rootProject.version.toString())
    systemProperty(
        "simpledsl.fixture.dir",
        layout.projectDirectory.dir("consumer").asFile.absolutePath
    )
}
