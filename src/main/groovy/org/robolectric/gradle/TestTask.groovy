package org.robolectric.gradle

import com.android.build.gradle.api.TestVariant
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.tasks.DefaultSourceSet
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestReport

class TestTask extends TestReport {

    private static final String TEST_TASK_NAME = 'test'
    private static final String TEST_REPORT_DIR = 'test-report'
    private static final String TEST_CLASSES_DIR = 'test-classes'

    private AndroidPluginWrapper androidPlugin
    private FileCollection testCompileClasspath
    private FileCollection testRunClasspath
    private DefaultSourceSet variationSources
    private ConfigurableFileCollection testDestinationDir
    private JavaCompile javaCompile
    private TestVariant testVariant
    private Task testClassesTaskPerVariation
    private Task testCompileTask
    private File processedManifestPath
    private File processedResourcesPath
    private File processedAssetsPath

    public TestTask() {
        super()
        destinationDir = project.file("$project.buildDir/$TEST_REPORT_DIR")
        description = 'Runs all unit tests.'
        group = JavaBasePlugin.VERIFICATION_GROUP
        project.tasks.check.dependsOn this

        androidPlugin = new AndroidPluginWrapper(project)
    }

    void createSubTasks(variant) {

        // Get the build type name (e.g., "Debug", "Release").
        def buildTypeName = variant.buildType.name.capitalize()

        setupPath(variant)
        setupClasses(variant)
        setupSources(buildTypeName)

        createTaskToCompileTestClasses(variant)
        createTaskToRunTestClasses(variant)
    }

    void createTaskToRunTestClasses(variant) {
        def variationName = variant.buildType.name.capitalize();
        def taskRunName = "$TEST_TASK_NAME$variationName"
        def testRunTask = project.tasks.create(taskRunName, Test)
        testRunTask.dependsOn testClassesTaskPerVariation
        testRunTask.inputs.sourceFiles.from.clear()
        testRunTask.classpath = testRunClasspath
        testRunTask.testClassesDir = testCompileTask.destinationDir
        testRunTask.group = JavaBasePlugin.VERIFICATION_GROUP
        testRunTask.description = "Run unit tests for Build '$variationName'."
        testRunTask.reports.html.destination = project.file("$project.buildDir/$TEST_REPORT_DIR/$variant.dirName")
        def androidRuntime = androidPlugin.plugin.getBootClasspath().join(File.pathSeparator)
        testRunTask.classpath = testRunClasspath.plus project.files(androidRuntime)

        // Set the applicationId as the packageName to avoid unknown resource errors when
        // applicationIdSuffix is used.
        def applicationId = project.android.defaultConfig.applicationId
        if (applicationId != null) {
            testRunTask.systemProperties.put('android.package', applicationId)
        }

        // Add the path to the correct manifest, resources, assets as a system property.
        testRunTask.systemProperties.put('android.manifest', processedManifestPath)
        testRunTask.systemProperties.put('android.resources', processedResourcesPath)
        testRunTask.systemProperties.put('android.assets', processedAssetsPath)

        // Set extension properties
        RobolectricTestExtension extension = project.extensions.getByName('robolectric')
        testRunTask.setMaxParallelForks(extension.maxParallelForks)
        testRunTask.setForkEvery(extension.forkEvery)
        testRunTask.setMaxHeapSize(extension.maxHeapSize)
        testRunTask.setJvmArgs(extension.jvmArgs)

        // Set afterTest closure
        if (extension.afterTest != null) {
            testRunTask.afterTest(extension.afterTest)
        }

        def includePatterns = !extension.includePatterns.empty ? extension.includePatterns : ['**/*Test.class']
        testRunTask.include(includePatterns)
        if (!extension.excludePatterns.empty) {
            testRunTask.exclude(extension.excludePatterns)
        }
        testRunTask.ignoreFailures = extension.ignoreFailures

        this.reportOn testRunTask
    }

    void createTaskToCompileTestClasses(variant) {
        testCompileTask = project.tasks.getByName variationSources.compileJavaTaskName
        // Depend on the project compilation (which itself depends on the manifest processing task).
        getLogger().debug("javaCompile: " + variationSources.compileJavaTaskName)
        testCompileTask.dependsOn javaCompile
        testCompileTask.group = null
        testCompileTask.description = null
        testCompileTask.classpath = testCompileClasspath
        testCompileTask.source = variationSources.java
        testCompileTask.destinationDir = testDestinationDir.getSingleFile()
        testCompileTask.options.bootClasspath = androidPlugin.plugin.getBootClasspath().join(File.pathSeparator)


        if (testVariant != null) {
            def prepareTestTask = testVariant.variantData.prepareDependenciesTask
            if (prepareTestTask != null) {
                getLogger().debug("prepareTestTask: " + prepareTestTask)
                // Depend on the prepareDependenciesTask of the TestVariant to prepare the AAR dependencies
                testCompileTask.dependsOn prepareTestTask
            }
        }
        def variationName = variant.buildType.name.capitalize()
        def javaConvention = project.convention.getPlugin(JavaPluginConvention)
        def variationSources1 = javaConvention.sourceSets.getByName("$TEST_TASK_NAME$variationName")
        // Clear out the group/description of the classes plugin so it's not top-level.
        testClassesTaskPerVariation = project.tasks.getByName variationSources1.classesTaskName
        testClassesTaskPerVariation.group = null
        testClassesTaskPerVariation.description = null

        // don't leave test resources behind
        def processResourcesTask = project.tasks.getByName variationSources1.processResourcesTaskName
        processResourcesTask.destinationDir = testDestinationDir.getSingleFile()
    }

    void setupSources(variationName) {
        def javaConvention = project.convention.getPlugin(JavaPluginConvention)
        variationSources = javaConvention.sourceSets.create "$TEST_TASK_NAME$variationName"
        testDestinationDir = project.files("$project.buildDir/$TEST_CLASSES_DIR")
        testRunClasspath = testCompileClasspath.plus testDestinationDir


        variationSources.java.setSrcDirs androidPlugin.getSourceDirs(["java"], [""])
        variationSources.resources.setSrcDirs androidPlugin.getSourceDirs(["res", "resources"], [""])
    }

    void setupClasses(variant) {
        javaCompile = variant.javaCompile
        testVariant = variant.testVariant
        def robolectricConfiguration = project.getConfigurations().getByName('robolectricCompile')
        testCompileClasspath = robolectricConfiguration.plus project.files(javaCompile.destinationDir, javaCompile.classpath)
        // Even though testVariant is marked as Nullable, I haven't seen it being null at all.
        if (testVariant != null) {
            testCompileClasspath.add project.files(testVariant.variantData.variantConfiguration.compileClasspath)
        } else {
            testCompileClasspath.add project.configurations.getByName("robolectricCompile")
        }
    }

    void setupPath(variant){
        // Grab the task which outputs the merged manifest, resources, and assets for this flavor.
        processedManifestPath = variant.outputs[0].processManifest.manifestOutputFile
        processedResourcesPath = variant.mergeResources.outputDir
        processedAssetsPath = variant.mergeAssets.outputDir
    }


}
