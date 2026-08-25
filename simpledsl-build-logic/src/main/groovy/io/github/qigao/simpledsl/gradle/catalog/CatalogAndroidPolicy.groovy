package io.github.qigao.simpledsl.gradle.catalog

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode
@ToString(includeNames = true)
final class CatalogAndroidPolicy {
    final int javaVersion
    final int compileSdk
    final int minSdk
    final Integer targetSdk

    CatalogAndroidPolicy(int javaVersion, int compileSdk, int minSdk, Integer targetSdk) {
        this.javaVersion = javaVersion
        this.compileSdk = compileSdk
        this.minSdk = minSdk
        this.targetSdk = targetSdk
    }
}
