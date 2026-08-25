package io.github.qigao.simpledsl.gradle.manifest

interface ManifestReader {
    Map<String, Object> read(File file)
}
