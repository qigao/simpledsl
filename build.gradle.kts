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

tasks.register("verifyBackendIsolation") {
    group = "verification"
    description = "Verify published SimpleDSL backend artifact dependency isolation."
    dependsOn(publishToTestPluginRepository)
    inputs.dir(testPluginRepository)

    val verificationVersion = project.version.toString()

    doLast {
        val repository = testPluginRepository.get().asFile

        fun findImplementationPom(artifactId: String): File {
            return repository.walkTopDown().firstOrNull { file ->
                file.isFile &&
                    file.extension == "pom" &&
                    file.parentFile?.name == verificationVersion &&
                    file.parentFile?.parentFile?.name == artifactId
            } ?: throw GradleException(
                "Missing published POM for io.github.qigao.simpledsl:$artifactId:$verificationVersion"
            )
        }

        fun dependencyCoordinates(artifactId: String): Set<String> {
            val document = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(findImplementationPom(artifactId))
            val dependencies = document.getElementsByTagName("dependency")
            return (0 until dependencies.length).map { index ->
                val dependency = dependencies.item(index) as org.w3c.dom.Element
                fun text(tag: String): String =
                    dependency.getElementsByTagName(tag).item(0)?.textContent.orEmpty()
                "${text("groupId")}:${text("artifactId")}" 
            }.toSet()
        }

        val coreDependencies = dependencyCoordinates("simpledsl-core")
        val javaDependencies = dependencyCoordinates("simpledsl-java")

        val forbiddenCore = setOf(
            "org.springframework.boot:spring-boot-gradle-plugin",
            "org.graalvm.buildtools:native-gradle-plugin",
            "org.jooq:jooq-codegen-gradle",
            "org.jsonschema2pojo:jsonschema2pojo-gradle-plugin",
            "com.android.tools.build:gradle"
        )
        val coreLeaks = coreDependencies.intersect(forbiddenCore)
        if (coreLeaks.isNotEmpty()) {
            throw GradleException(
                "SimpleDSL core dependency isolation violation: ${coreLeaks.sorted().joinToString(", ")}"
            )
        }

        if ("com.android.tools.build:gradle" in javaDependencies) {
            throw GradleException(
                "SimpleDSL Java dependency isolation violation: com.android.tools.build:gradle"
            )
        }

        val requiredCore = "io.github.qigao.simpledsl:simpledsl-core"
        if (requiredCore !in javaDependencies) {
            throw GradleException(
                "SimpleDSL Java publication must depend on $requiredCore; actual dependencies: " +
                    javaDependencies.sorted().joinToString(", ")
            )
        }
    }
}

tasks.named("check") {
    dependsOn("verifyProductNamespace")
}
