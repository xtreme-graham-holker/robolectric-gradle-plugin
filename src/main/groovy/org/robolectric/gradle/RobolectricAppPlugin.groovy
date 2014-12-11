package org.robolectric.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin

class RobolectricAppPlugin implements Plugin<Project> {


    @Override
    void apply(Project project) {

        project.extensions.create('robolectric', RobolectricTestExtension)

        project.plugins.apply JavaBasePlugin

        def testConfiguration = project.configurations.create('robolectricCompile')
        testConfiguration.extendsFrom project.configurations.getByName('compile')

        project.tasks.create("robolectricTest", RobolectricTestTask)
    }

}
