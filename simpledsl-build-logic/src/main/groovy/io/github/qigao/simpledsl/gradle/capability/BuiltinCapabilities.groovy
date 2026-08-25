package io.github.qigao.simpledsl.gradle.capability

final class BuiltinCapabilities {
    static final String SPRING_LIBRARY = 'spring-library'
    static final String SPRING_SERVICE = 'spring-service'

    static final CapabilitySpec AOP = CapabilitySpec.builder('aop')
            .allow(SPRING_LIBRARY, SPRING_SERVICE)
            .dependency('implementation', 'spring-aop')
            .build()

    static final CapabilitySpec TRANSACTION = CapabilitySpec.builder('transaction')
            .allow(SPRING_LIBRARY, SPRING_SERVICE)
            .dependency('implementation', 'spring-transaction')
            .build()

    static final CapabilitySpec WEB = CapabilitySpec.builder('web')
            .allow(SPRING_SERVICE)
            .dependency('implementation', 'spring-webmvc')
            .dependency('implementation', 'spring-validation')
            .dependency('testImplementation', 'spring-webmvc-test')
            .build()

    static final CapabilitySpec HTTP_CLIENT = CapabilitySpec.builder('http-client')
            .allow(SPRING_LIBRARY, SPRING_SERVICE)
            .dependency('implementation', 'spring-restclient')
            .dependency('testImplementation', 'spring-restclient-test')
            .build()

    static final CapabilitySpec MESSAGING = CapabilitySpec.builder('messaging')
            .allow(SPRING_LIBRARY, SPRING_SERVICE)
            .dependency('implementation', 'spring-messaging')
            .build()

    static final CapabilitySpec JPA = CapabilitySpec.builder('jpa')
            .allow(SPRING_LIBRARY, SPRING_SERVICE)
            .dependency('implementation', 'spring-jpa')
            .build()

    static final CapabilitySpec JDBC = CapabilitySpec.builder('jdbc')
            .allow(SPRING_LIBRARY, SPRING_SERVICE)
            .dependency('implementation', 'spring-jdbc')
            .build()

    static final CapabilitySpec JOOQ = CapabilitySpec.builder('jooq')
            .allow(SPRING_LIBRARY, SPRING_SERVICE)
            .dependency('implementation', 'spring-jooq')
            .build()

    static final CapabilitySpec REDIS = CapabilitySpec.builder('redis')
            .allow(SPRING_LIBRARY, SPRING_SERVICE)
            .dependency('implementation', 'spring-redis')
            .build()

    static final CapabilitySpec NATIVE = CapabilitySpec.builder('native')
            .allow(SPRING_SERVICE)
            .externalPluginId('org.graalvm.buildtools.native')
            .build()

    static final CapabilitySpec LOMBOK = CapabilitySpec.builder('lombok')
            .dependency('compileOnly', 'lombok')
            .dependency('annotationProcessor', 'lombok')
            .build()

    static void registerAll(CapabilityRegistry registry) {
        [AOP, TRANSACTION, WEB, HTTP_CLIENT, MESSAGING, JPA, JDBC, JOOQ, REDIS, NATIVE, LOMBOK].each { registry.register(it) }
    }

    private BuiltinCapabilities() {}
}
