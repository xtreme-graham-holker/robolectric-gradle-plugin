package org.robolectric.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention


class RobolectricAppPlugin implements Plugin<Project> {
    private static RobolectricTestExtension extension

    @Override
    void apply(Project project) {
        extension = project.extensions.create('robolectric', RobolectricTestExtension)

        project.plugins.apply JavaBasePlugin
        def javaConvention = project.convention.getPlugin(JavaPluginConvention)

        configureProject(project)

        project.tasks.create("roboTest", TestTask)


    }

    void configureProject(Project project){

        // Create the configuration for test-only dependencies.
        def testConfiguration = project.configurations.create('robolectricCompile')
        // Make the 'test' configuration extend from the normal 'compile' configuration.
        testConfiguration.extendsFrom project.configurations.getByName('compile')
    }


}
