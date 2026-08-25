plugins {
    base
}

val releaseVersion = providers.gradleProperty("releaseVersion")
val developmentVersion = providers.gradleProperty("simpledslVersion")

allprojects {
    group = "io.github.qigao.simpledsl"
    version = releaseVersion.orElse(developmentVersion).get()
}

tasks.register<Exec>("verifyProductNamespace") {
    commandLine("bash", "scripts/verify-product-namespace.sh")
}

tasks.register("publishToTestPluginRepository") {
    dependsOn(
        ":simpledsl-core:publishAllPublicationsToTestPluginRepository",
        ":simpledsl-build-logic:publishAllPublicationsToTestPluginRepository"
    )
}

tasks.named("check") {
    dependsOn("verifyProductNamespace")
}
