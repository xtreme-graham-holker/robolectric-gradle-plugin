package org.robolectric.gradle

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import org.gradle.api.Project

class AndroidPluginWrapper {
    private final Project project
    private final boolean hasAppPlugin
    private final boolean hasLibPlugin

    AndroidPluginWrapper(Project project) {
        this.project = project
        this.hasAppPlugin = project.plugins.find { p -> p instanceof AppPlugin }
        this.hasLibPlugin = project.plugins.find { p -> p instanceof LibraryPlugin }

        if (!hasAppPlugin && !hasLibPlugin) {
            throw new IllegalStateException("The 'com.android.application' or 'com.android.library' plugin is required.")
        } else if (hasAppPlugin && hasLibPlugin) {
            throw new IllegalStateException("Having both 'com.android.application' and 'com.android.library' plugin is not supported.")
        }
    }

    def getVariants() {
        if (hasLibPlugin) return project.android.libraryVariants
        return project.android.applicationVariants
    }

    def getPlugin() {
        if (hasLibPlugin) return project.plugins.find { p -> p instanceof LibraryPlugin }
        return project.plugins.find { p -> p instanceof AppPlugin }
    }



}
