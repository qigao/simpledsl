package io.github.qigao.simpledsl.gradle.manifest

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode
@ToString(includeNames = true)
final class AndroidPolicy {
    final int javaVersion
    final int compileSdk
    final int minSdk
    final Integer targetSdk

    AndroidPolicy(int javaVersion, int compileSdk, int minSdk, Integer targetSdk) {
        if (javaVersion <= 0) {
            throw new IllegalArgumentException('java must be a positive integer')
        }
        if (compileSdk <= 0) {
            throw new IllegalArgumentException('compile-sdk must be a positive integer')
        }
        if (minSdk <= 0) {
            throw new IllegalArgumentException('min-sdk must be a positive integer')
        }
        if (targetSdk != null && targetSdk <= 0) {
            throw new IllegalArgumentException('target-sdk must be a positive integer')
        }
        if (minSdk > compileSdk) {
            throw new IllegalArgumentException('min-sdk must be <= compile-sdk')
        }
        if (targetSdk != null && targetSdk < minSdk) {
            throw new IllegalArgumentException('target-sdk must be >= min-sdk')
        }
        if (targetSdk != null && targetSdk > compileSdk) {
            throw new IllegalArgumentException('target-sdk must be <= compile-sdk')
        }

        this.javaVersion = javaVersion
        this.compileSdk = compileSdk
        this.minSdk = minSdk
        this.targetSdk = targetSdk
    }

    Map<String, Object> snapshot() {
        Map<String, Object> values = [
                java: javaVersion,
                compileSdk: compileSdk,
                minSdk: minSdk
        ]
        if (targetSdk != null) {
            values.targetSdk = targetSdk
        }
        Collections.unmodifiableMap(values)
    }
}
