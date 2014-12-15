package org.robolectric.gradle

import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.tasks.DefaultSourceSet
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestReport

class RobolectricTestTask extends TestReport {

    private static final String TEST_TASK_NAME = 'test'
    private static final String TEST_REPORT_DIR = 'test-report'
    private static final String TEST_CLASSES_DIR = 'test-classes'
    private static final String[] SUPPORTED_ANDROID_VERSIONS = ['0.14.', '1.0.']

    private static Logger logger
    private AndroidPluginWrapper androidPlugin
    private FileCollection testCompileClasspath
    private FileCollection testRunClasspath
    private DefaultSourceSet variationSources
    private ConfigurableFileCollection testDestinationDir

    private Task testClassesTaskPerVariation
    private Task testCompileTask

    private File processedManifestPath
    private File processedResourcesPath
    private File processedAssetsPath

    public RobolectricTestTask() {
        super()
        logger = project.getLogger()

        destinationDir = project.file("$project.buildDir/$TEST_REPORT_DIR")
        description = 'Runs all unit tests.'
        group = JavaBasePlugin.VERIFICATION_GROUP
        project.tasks.check.dependsOn this

        createSubTasks()
    }

    void createSubTasks() {

        androidPlugin = new AndroidPluginWrapper(project)
        androidPlugin.variants.all { variant ->

            def androidGradlePlugin = project.buildscript.configurations.classpath.dependencies.find {
                it.group != null && it.group.equals('com.android.tools.build') && it.name.equals('gradle')
            }

            if (androidGradlePlugin != null) {
                if (!checkAndroidVersion(androidGradlePlugin.version))
                    throw new IllegalStateException("The Android Gradle plugin ${androidGradlePlugin.version} is not supported.")
            }

            String variationName = getVariationName(variant)

            setupPath(variant)
            setupClasses(variant)
            setupSources(variant, variationName)

            createTaskToCompileTestClasses(variant, variationName)
            createTaskToRunTestClasses(variant, variationName)
        }
    }

    String getVariationName(variant) {
        String buildTypeName = variant.buildType.name.capitalize()
        String projectFlavorName = getFlavorNames(variant).join()
        String variationName = "$projectFlavorName$buildTypeName"
        variationName
    }

    ArrayList getFlavorNames(variant) {
        def projectFlavorNames = variant.productFlavors.collect { it.name.capitalize() }
        if (projectFlavorNames.isEmpty()) {
            projectFlavorNames = [""]
        }
        projectFlavorNames
    }

    void setupPath(variant){
        // Grab the task which outputs the merged manifest, resources, and assets for this flavor.
        processedManifestPath = variant.outputs[0].processManifest.manifestOutputFile
        processedResourcesPath = variant.mergeResources.outputDir
        processedAssetsPath = variant.mergeAssets.outputDir
    }

    void setupClasses(variant) {
        def robolectricConfiguration = project.getConfigurations().getByName('robolectricCompile')
        testCompileClasspath = robolectricConfiguration.plus project.files(variant.javaCompile.destinationDir, variant.javaCompile.classpath)
        // Even though testVariant is marked as Nullable, I haven't seen it being null at all.
        if (variant.testVariant != null) {
            testCompileClasspath.add project.files(variant.testVariant.variantData.variantConfiguration.compileClasspath)
        } else {
            testCompileClasspath.add robolectricConfiguration
        }
    }

    void setupSources(variant, variationName) {
        def javaConvention = project.convention.getPlugin(JavaPluginConvention)
        variationSources = javaConvention.sourceSets.create "$TEST_TASK_NAME$variationName"
        testDestinationDir = project.files("$project.buildDir/$TEST_CLASSES_DIR")
        testRunClasspath = testCompileClasspath.plus testDestinationDir

        ArrayList flavorNames = getFlavorNames(variant)

        variationSources.java.setSrcDirs getSourceDirs2(["java"], flavorNames)
        //variationSources.resources.setSrcDirs getSourceDirs2(["res", "resources"], flavorNames)
    }

    def getSourceDirs(List<String> sourceTypes, List<String> projectFlavorNames) {
        def dirs = []
        sourceTypes.each { sourceType ->

            String sourceDir = project.robolectric.sourceDir;
            if (project.robolectric.sourceDir == null) {
                sourceDir = "src/test"
            }

            dirs.add(new File(sourceDir + File.separator + sourceType))

            projectFlavorNames.each { flavor ->
                if (flavor) {
                    dirs.add(new File(sourceDir + flavor + File.separator + sourceType))
                }
            }
        }
        return dirs
    }

    def getSourceDirs2(List<String> sourceTypes, List<String> projectFlavorNames) {
        def dirs = []
        sourceTypes.each { sourceType ->
            project.robolectric.sourceSets.test[sourceType].srcDirs.each { testDir ->
                dirs.add(testDir)
            }
            projectFlavorNames.each { flavor ->
                if (flavor) {
                    dirs.addAll(project.robolectric.sourceSets["test$flavor"][sourceType].srcDirs)
                }
            }
        }
        if (dirs.empty){
            dirs.add(project.file(project.projectDir + '/src/test/java'))
        }
        return dirs
    }

    void createTaskToCompileTestClasses(variant, variationName) {

        testCompileTask = project.tasks.getByName variationSources.compileJavaTaskName
        // Depend on the project compilation (which itself depends on the manifest processing task).
        testCompileTask.dependsOn variant.javaCompile
        testCompileTask.group = null
        testCompileTask.description = null
        testCompileTask.classpath = testCompileClasspath
        testCompileTask.source = variationSources.java
        testCompileTask.destinationDir = testDestinationDir.getSingleFile()
        testCompileTask.options.bootClasspath = androidPlugin.plugin.getBootClasspath().join(File.pathSeparator)

        if (variant.testVariant != null) {
            def prepareTestTask = variant.testVariant.variantData.prepareDependenciesTask
            if (prepareTestTask != null) {
                getLogger().debug("prepareTestTask: " + prepareTestTask)
                testCompileTask.dependsOn prepareTestTask
            }
        }
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

    void createTaskToRunTestClasses(variant, variationName) {

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

        // Set the applicationId as the packageName to avoid unknown resource errors when applicationIdSuffix is used.
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

        reportOn testRunTask
    }

    boolean checkAndroidVersion(String version) {
        for (String supportedVersion : SUPPORTED_ANDROID_VERSIONS) {
            if (version.startsWith(supportedVersion)) {
                return true
            }
        }
        if (project.extensions.getByName('robolectric').ignoreVersionCheck) {
            logger.warn("The Android Gradle plugin ${version} is not supported.")
            return true
        } else {
            return false
        }
    }

}
