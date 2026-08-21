package io.github.qigao.simpledsl.gradle.catalog

import org.gradle.api.Plugin
import org.gradle.api.Project

class SimpleDslCatalogPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        if (project.extensions.findByName('simpledslDependencyCatalog') != null) {
            return
        }
        DependencyCatalogSnapshot catalog = SimpleDslRegistryBridge.fromProject(project)
        project.extensions.add(DependencyCatalogSnapshot, 'simpledslDependencyCatalog', catalog)
    }
}
