plugins {
    groovy
}

dependencies {
    testImplementation(localGroovy())
    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val consumerFixture = layout.projectDirectory.dir("consumer")
val testPluginRepository = rootProject.layout.buildDirectory.dir("test-plugin-repo")

tasks.test {
    useJUnitPlatform()
    dependsOn(rootProject.tasks.named("publishToTestPluginRepository"))
    inputs.dir(consumerFixture)
    inputs.dir(testPluginRepository)
    systemProperty(
        "simpledsl.test.repo",
        testPluginRepository.get().asFile.absolutePath
    )
    systemProperty("simpledsl.test.version", rootProject.version.toString())
    systemProperty(
        "simpledsl.fixture.dir",
        consumerFixture.asFile.absolutePath
    )
}
