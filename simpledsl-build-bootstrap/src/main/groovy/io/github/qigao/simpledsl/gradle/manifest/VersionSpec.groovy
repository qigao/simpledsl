package io.github.qigao.simpledsl.gradle.manifest

final class VersionSpec {
    final String id
    final String value

    VersionSpec(String id, String value) {
        this.id = id
        this.value = value
    }
}
