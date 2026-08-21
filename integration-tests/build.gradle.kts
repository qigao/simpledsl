plugins {
    groovy
}

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}
