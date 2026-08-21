plugins {
    groovy
}

dependencies {
    testImplementation(localGroovy())
    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}
