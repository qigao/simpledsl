plugins {
    base
}

val releaseVersion = providers.gradleProperty("releaseVersion")
val developmentVersion = providers.gradleProperty("simpledslVersion")

allprojects {
    group = "io.github.qigao.simpledsl"
    version = releaseVersion.orElse(developmentVersion).get()
}

val testPluginRepository = layout.buildDirectory.dir("test-plugin-repo")

val publishToTestPluginRepository = tasks.register("publishToTestPluginRepository") {
    dependsOn(
        ":simpledsl-core:publishAllPublicationsToTestPluginRepository",
        ":simpledsl-java:publishAllPublicationsToTestPluginRepository"
    )
}

tasks.register<Exec>("verifyProductNamespace") {
    commandLine("bash", "scripts/verify-product-namespace.sh")
}

tasks.register<Exec>("verifyBackendIsolation") {
    group = "verification"
    description = "Verify published SimpleDSL backend artifact dependency isolation."
    dependsOn(publishToTestPluginRepository)

    val verifier = layout.projectDirectory.file("scripts/verify-backend-isolation.sh")
    inputs.file(verifier)
    inputs.dir(testPluginRepository)

    commandLine(
        "bash",
        verifier.asFile.absolutePath,
        testPluginRepository.get().asFile.absolutePath,
        project.version.toString()
    )
}

tasks.named("check") {
    dependsOn("verifyProductNamespace")
}
