package org.robolectric.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention


class RobolectricAppPlugin implements Plugin<Project> {
    private static final String[] SUPPORTED_ANDROID_VERSIONS = ['0.14.', '1.0.']
    private static Logger logger
    private static RobolectricTestExtension extension

    @Override
    void apply(Project project) {
        extension = project.extensions.create('robolectric', RobolectricTestExtension)
        logger = project.logger

//        def androidGradlePlugin = project.buildscript.configurations.classpath.dependencies.find {
//            it.group != null && it.group.equals('com.android.tools.build') && it.name.equals('gradle')
//        }

        def androidPluginWrapper = new AndroidPluginWrapper(project)

        // Apply the base of the 'java' plugin so source set and java compilation is easier.
        project.plugins.apply JavaBasePlugin
        def javaConvention = project.convention.getPlugin(JavaPluginConvention)

        configureProject(project)

        TestTask testTask = project.tasks.create("roboTest", TestTask)
        androidPluginWrapper.variants.all { variant ->
            def androidGradlePluginVersion = project.buildscript.configurations.classpath.dependencies.find {
                it.group != null && it.group.equals('com.android.tools.build') && it.name.equals('gradle')
            }
            if(androidGradlePluginVersion != null && checkAndroidVersion(androidGradlePluginVersion.version)){
                testTask.createSubTasks(variant)
            }
        }

    }

    void configureProject(Project project){

        // Create the configuration for test-only dependencies.
        def testConfiguration = project.configurations.create('robolectricCompile')
        // Make the 'test' configuration extend from the normal 'compile' configuration.
        testConfiguration.extendsFrom project.configurations.getByName('compile')
    }

    boolean checkAndroidVersion(String version) {
        for (String supportedVersion : SUPPORTED_ANDROID_VERSIONS) {
            if (version.startsWith(supportedVersion)) {
                return true
            }
        }
        if (extension.ignoreVersionCheck) {
            logger.warn("The Android Gradle plugin ${version} is not supported.")
            return true
        } else {
            throw new IllegalStateException("The Android Gradle plugin ${version} is not supported.")
        }
    }
}
