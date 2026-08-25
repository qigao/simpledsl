package io.github.qigao.simpledsl.gradle.feature

import io.github.qigao.simpledsl.gradle.capability.BuiltinCapabilities
import io.github.qigao.simpledsl.gradle.capability.SimpleDslCapabilitySupport
import org.gradle.api.Plugin
import org.gradle.api.Project

final class SimpleDslTransactionPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.pluginManager.apply('io.github.qigao.simpledsl.module')
        SimpleDslCapabilitySupport.registerAndEnable(project, 'io.github.qigao.simpledsl.feature.transaction', BuiltinCapabilities.TRANSACTION)
    }
}
