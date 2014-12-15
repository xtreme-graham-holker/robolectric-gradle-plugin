package org.robolectric.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer


class RobolectricPlugin implements Plugin<Project> {


    @Override
    void apply(Project project) {

        def extension = project.extensions.create('robolectric', RobolectricTestExtension)

        project.plugins.apply JavaBasePlugin
        def javaConvention = project.convention.getPlugin JavaPluginConvention

        def testConfiguration = project.configurations.create('robolectricCompile')
        testConfiguration.extendsFrom project.configurations.getByName('compile')

        SourceSetContainer sourceSets = javaConvention.getSourceSets()
        extension.sourceSets = sourceSets
        SourceSet testSourceSet = extension.sourceSets.create('test')

        project.tasks.create("robolectricTest", RobolectricTestTask)
    }

}
