package io.github.qigao.simpledsl.gradle.manifest

final class AndroidPolicySpec {
    final int javaVersion
    final int compileSdk
    final int minSdk
    final Integer targetSdk

    AndroidPolicySpec(int javaVersion, int compileSdk, int minSdk, Integer targetSdk) {
        this.javaVersion = javaVersion
        this.compileSdk = compileSdk
        this.minSdk = minSdk
        this.targetSdk = targetSdk
    }

    Map<String, Object> snapshot() {
        Map<String, Object> value = [
                java: javaVersion,
                compileSdk: compileSdk,
                minSdk: minSdk
        ] as LinkedHashMap<String, Object>
        if (targetSdk != null) {
            value.put('targetSdk', targetSdk)
        }
        value
    }
}
