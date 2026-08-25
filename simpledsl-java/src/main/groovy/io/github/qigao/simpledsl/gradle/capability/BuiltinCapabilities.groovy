package io.github.qigao.simpledsl.gradle.capability

final class BuiltinCapabilities {
    static final CapabilitySpec AOP = CapabilitySpec.builder('aop')
            .allow('spring-library', 'spring-service')
            .dependency('implementation', 'spring-aop')
            .build()

    static final CapabilitySpec TRANSACTION = CapabilitySpec.builder('transaction')
            .allow('spring-library', 'spring-service')
            .dependency('implementation', 'spring-transaction')
            .build()

    static final CapabilitySpec WEB = CapabilitySpec.builder('web')
            .allow('spring-service')
            .dependency('implementation', 'spring-webmvc')
            .dependency('implementation', 'spring-validation')
            .dependency('testImplementation', 'spring-webmvc-test')
            .build()

    static final CapabilitySpec HTTP_CLIENT = CapabilitySpec.builder('http-client')
            .allow('spring-library', 'spring-service')
            .dependency('implementation', 'spring-restclient')
            .dependency('testImplementation', 'spring-restclient-test')
            .build()

    static final CapabilitySpec MESSAGING = CapabilitySpec.builder('messaging')
            .allow('spring-library', 'spring-service')
            .dependency('implementation', 'spring-messaging')
            .build()

    static final CapabilitySpec JPA = CapabilitySpec.builder('jpa')
            .allow('spring-library', 'spring-service')
            .dependency('implementation', 'spring-jpa')
            .build()

    static final CapabilitySpec JDBC = CapabilitySpec.builder('jdbc')
            .allow('spring-library', 'spring-service')
            .dependency('implementation', 'spring-jdbc')
            .build()

    static final CapabilitySpec JOOQ = CapabilitySpec.builder('jooq')
            .allow('spring-library', 'spring-service')
            .dependency('implementation', 'spring-jooq')
            .build()

    static final CapabilitySpec REDIS = CapabilitySpec.builder('redis')
            .allow('spring-library', 'spring-service')
            .dependency('implementation', 'spring-redis')
            .build()

    static final CapabilitySpec NATIVE = CapabilitySpec.builder('native')
            .allow('spring-service')
            .externalPluginId('org.graalvm.buildtools.native')
            .build()

    static final CapabilitySpec LOMBOK = CapabilitySpec.builder('lombok')
            .dependency('compileOnly', 'lombok')
            .dependency('annotationProcessor', 'lombok')
            .build()

    static void registerAll(CapabilityRegistry registry) {
        [AOP, TRANSACTION, WEB, HTTP_CLIENT, MESSAGING, JPA, JDBC, JOOQ, REDIS, NATIVE, LOMBOK]
                .each { registry.register(it) }
    }

    private BuiltinCapabilities() {}
}
