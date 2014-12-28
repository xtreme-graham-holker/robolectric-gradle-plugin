package org.robolectric.gradle

import com.android.build.gradle.AppPlugin
import com.android.builder.model.JavaArtifact
import com.google.common.io.Files
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.Task
import org.gradle.api.internal.plugins.PluginApplicationException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

import static org.assertj.core.api.Assertions.assertThat

class RobolectricPluginTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none()

    @Test
    public void pluginDetectsLibraryPlugin() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: 'com.android.library'
        project.apply plugin: 'robolectric'
    }

    @Test
    public void pluginDetectsExtendedLibraryPlugin() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: 'extended-android-library'
        project.apply plugin: 'robolectric'
    }

    @Test(expected = PluginApplicationException.class)
    public void pluginFailsWithoutAndroidPlugin() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: 'robolectric'
    }

    @Test
    public void pluginDetectsAppPlugin() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: 'com.android.application'
        project.apply plugin: 'robolectric'
    }

    @Test
    public void pluginDetectsExtendedAppPlugin() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: 'extended-android'
        project.apply plugin: 'robolectric'
    }

    @Test
    public void createsATestTaskForTheDebugVariant() {
        Project project = evaluatableProject()
        project.evaluate()

        assertThat(project.tasks.testDebug).isInstanceOf(org.gradle.api.tasks.testing.Test)
    }

    @Test
    public void createsATestTaskForTheProdDebugLibVariant() {
        Project project = evaluatableLibraryProject()
        project.android {
            productFlavors {
                prod
                beta
            }
        }
        project.evaluate()

        assertThat(project.tasks.testProdDebug).isInstanceOf(org.gradle.api.tasks.testing.Test)
    }

    @Test
    public void createsATestTaskForTheProdDebugAppVariant() {
        Project project = evaluatableProject()
        project.android {
            productFlavors {
                prod
                beta
            }
        }
        project.evaluate()

        assertThat(project.tasks.testProdDebug).isInstanceOf(org.gradle.api.tasks.testing.Test)
    }

    @Test
    public void createsATaskCompilingFilesInDefaultLocation() {
        Project project = evaluatableProject()
        project.evaluate()

        assertThat(project.tasks.compileTestDebugJava.source.files).containsOnly(project.file("src/test/java/SomeTest.java"))
    }

    @Test
    public void createsATaskCompilingFilesInCustomLocation() {
        Project project = evaluatableProject()
        //project.robolectric.sourceDir = 'customTestFolder/src'
        project.robolectric {
            sourceSets {
                test {
                    java.srcDir file('customTestFolder/src')
                }
            }
        }
        project.evaluate()

        assertThat(project.tasks.compileTestDebugJava.source.files).containsOnly(project.file("customTestFolder/src/java/SomeTest.java"))
    }

    @Test
    public void supportsAfterTestListenerForTheTestTask() {
        Project project = evaluatableProject()
        project.robolectric {
            afterTest { descriptor, result ->
                println "Executed ${descriptor.name} with result: ${result.resultType}"
            }
        }
        project.evaluate()

        assertThat(project.tasks.testDebug).isInstanceOf(org.gradle.api.tasks.testing.Test)
    }

    @Ignore
    @Test
    public void validateTestDebugClassesDependency() {
        Project project = evaluatableProject()
        project.evaluate()

        assertThat(project.tasks.assembleDebug.getDependsOn()).contains('testDebugClasses')
    }

    @Test
    public void supportsSettingAnExcludePattern_viaTheAndroidTestExtension() {
        Project project = evaluatableProject()
        project.robolectric {
            exclude "**/lame_tests/**"
        }
        project.evaluate()

        assertThat(project.tasks.testDebug.getExcludes().contains("**/lame_tests/**")).isTrue()
    }

    @Test
    public void supportsAddingJvmArgs_viaTheAndroidTestExtension() {
        Project project = evaluatableProject()
        project.robolectric {
            jvmArgs "-XX:TestArgument0", "-XX:TestArgument1"
        }
        project.evaluate()

        assertThat(project.tasks.testDebug.getJvmArgs()).contains("-XX:TestArgument0")
        assertThat(project.tasks.testDebug.getJvmArgs()).contains("-XX:TestArgument1")
    }

    @Test
    public void supportsAddingMaxHeapSize_viaTheAndroidTestExtension() {
        Project project = evaluatableProject()
        project.robolectric {
            maxHeapSize = "1024m"
        }
        project.evaluate()

        assertThat(project.tasks.testDebug.getMaxHeapSize()).isEqualTo("1024m")
    }

    @Test
    public void supportsAddingMaxParallelForks_viaTheAndroidTestExtension() {
        Project project = evaluatableProject()
        project.robolectric {
            maxParallelForks = 4
        }
        project.evaluate()

        assertThat(project.tasks.testDebug.getMaxParallelForks()).isEqualTo(4)
    }

    @Test
    public void setMaxParallelForksToOne_whenNotConfigured_viaTheAndroidTestExtension() {
        Project project = evaluatableProject()
        project.evaluate()

        assertThat(project.tasks.testDebug.getMaxParallelForks()).isEqualTo(1)
    }

    @Test
    public void shouldThrowException_whenMaxParallelForks_lessThanOne_viaTheAndroidTestExtension() {
        Project project = evaluatableProject()

        thrown.expect(IllegalArgumentException.class)
        thrown.expectMessage("Cannot set maxParallelForks to a value less than 1.")
        project.robolectric {
            maxParallelForks = 0
        }
    }

    @Test
    public void supportsAddingForkEvery_viaTheAndroidTestExtension() {
        Project project = evaluatableProject()
        project.robolectric { forkEvery = 150 }
        project.evaluate()

        assertThat(project.tasks.testDebug.getForkEvery()).isEqualTo(150)
    }

    @Test
    public void setForkEveryToZero_whenNotConfigured_viaTheAndroidTestExtension() {
        Project project = evaluatableProject()
        project.evaluate()

        assertThat(project.tasks.testDebug.getForkEvery()).isEqualTo(0)
    }

    @Test
    public void setForkEveryToZero_whenConfiguredNull_viaTheAndroidTestExtension() {
        Project project = evaluatableProject()
        project.robolectric {
            forkEvery = null
        }
        project.evaluate()

        assertThat(project.tasks.testDebug.getForkEvery()).isEqualTo(0)
    }

    @Test
    public void shouldThrowException_whenForkEveryNegative_viaTheAndroidTestExtension() {
        Project project = evaluatableProject()

        thrown.expect(IllegalArgumentException.class)
        thrown.expectMessage("Cannot set forkEvery to a value less than 0.")
        project.robolectric {
            forkEvery = -1
        }
    }

    @Test
    public void supportsMultipleIncludeAndExcludePatterns() {
        Project project = evaluatableProject()
        project.robolectric {
            exclude "**/lame_tests/**"
            exclude "**/lame_tests2/**", "**/lame_tests_3/**"
            include "**/robo_tests/**"
            include "**/robo_tests2/**", "**/robo_tests3/**"
        }
        project.evaluate()

        assertThat(project.tasks.testDebug.excludes).contains("**/lame_tests/**", "**/lame_tests2/**", "**/lame_tests_3/**")
        assertThat(project.tasks.testDebug.includes).contains("**/robo_tests/**", "**/robo_tests2/**", "**/robo_tests3/**")
    }

    @Test
    public void supportsIgnoreFailures() {
        Project project = evaluatableProject()
        project.robolectric {
            ignoreFailures true
        }
        project.evaluate()

        assertThat(project.tasks.testDebug.ignoreFailures).isTrue()
    }

    @Test
    public void dumpsAllTestClassFilesAndResourcesIntoTheSameDirectory() {
        Project project = evaluatableProject()
        project.android {
            productFlavors {
                prod
                beta
            }
        }
        project.evaluate()

        def expectedDestination = project.files("$project.buildDir/test-classes").singleFile
        assertThat(project.tasks.compileTestProdDebugJava.destinationDir).isEqualTo(expectedDestination)
        assertThat(project.tasks.compileTestBetaDebugJava.destinationDir).isEqualTo(expectedDestination)
        assertThat(project.tasks.processTestProdDebugResources.destinationDir).isEqualTo(expectedDestination)
        assertThat(project.tasks.processTestBetaDebugResources.destinationDir).isEqualTo(expectedDestination)
    }

    @Test
    public void ensureJarDependenciesOnClasspath() {
        Project project = evaluatableProject()
        project.repositories {
            mavenCentral()
        }
        project.dependencies {
            robolectricCompile 'junit:junit:4.10'
        }
        project.evaluate()

        assertThat(project.tasks.compileTestDebugJava.classpath.files.find {
            it.absolutePath.contains("junit${File.separator}junit${File.separator}4.10")
        }).isNotNull()
    }

    @Test
    public void ensureAarDependenciesOnClasspath() {
        Project project = evaluatableProject()
        project.repositories {
            mavenCentral()
        }
        project.dependencies {
            robolectricCompile 'com.squareup.assertj:assertj-android:1.0.0'
        }
        project.evaluate()

        assertThat(project.tasks.compileTestDebugJava.classpath.files.find {
            //it.absolutePath.contains("com.squareup.assertj${File.separator}assertj-android${File.separator}1.0.0${File.separator}classes.aar")
            it.absolutePath.contains("assertj-android-1.0.0.aar")
        }).isNotNull()
    }

    @Test
    public void checkAndroidVersionTest() {
        Project project = evaluatableProject()
        RobolectricTestTask robolectricTestTask = project.tasks.getByName('robolectricTest')
        assertThat(robolectricTestTask.checkAndroidVersion('0.6.0')).isFalse()
        assertThat(robolectricTestTask.checkAndroidVersion('0.8.0')).isFalse()
        assertThat(robolectricTestTask.checkAndroidVersion('0.12.+')).isFalse()
        assertThat(robolectricTestTask.checkAndroidVersion('0.13.1')).isFalse()

        assertThat(robolectricTestTask.checkAndroidVersion('0.14.+')).isTrue()
        assertThat(robolectricTestTask.checkAndroidVersion('0.14.0')).isTrue()
        assertThat(robolectricTestTask.checkAndroidVersion('1.0.0-rc1')).isTrue()
    }

    @Test(expected = ProjectConfigurationException.class)
    public void pluginFailsWithOutdatedAndroidPlugin() {
        Project project = evaluatableProjectWithAndroidVersion('com.android.tools.build:gradle:0.12.0')
        project.evaluate()
    }

    @Test
    public void pluginAcceptsOutdatedAndroidPluginByExtension() {
        Project project = evaluatableProjectWithAndroidVersion('com.android.tools.build:gradle:0.12.0')
        project.robolectric {
            ignoreVersionCheck true
        }
        project.evaluate()
    }

    @Test
    public void pluginAcceptsSupportedAndroidPlugin() {
        Project project = evaluatableProjectWithAndroidVersion('com.android.tools.build:gradle:1.0.0-rc1');
        project.evaluate()
    }

    @Ignore
    @Test
    public void pluginShouldFixImlIssues() {
        Files.copy(new File('src/test/resources/test.iml'), new File("src/test/fixtures/android_app/test.iml"))

        Project project = evaluatableProject();
        project.evaluate()

        assertThat(project.tasks.preBuild.getDependsOn()).contains('initGradleTest')

        project.tasks.initGradleTest.actions.each { Action<? super Task> action ->
            action.execute(project.tasks.initGradleTest)
        }
        assertThat(new File("src/test/fixtures/android_app/test.iml"))
                .hasContentEqualTo(new File("src/test/resources/test-expected.iml"))
    }

    private Project evaluatableProject() {
        return evaluatableProjectWithPluginAndBuildscript('com.android.application', null)
    }

    private Project evaluatableLibraryProject() {
        return evaluatableProjectWithPluginAndBuildscript('com.android.library', null)
    }

    private Project evaluatableProjectWithAndroidVersion(String androidVersion) {
        return evaluatableProjectWithPluginAndBuildscript('com.android.application', {
            repositories {
                mavenCentral()
            }
            dependencies {
                classpath androidVersion
            }
        })
    }

    private Project evaluatableProjectWithPluginAndBuildscript(String plugin, Closure buildscript) {
        Project project = ProjectBuilder.builder().withProjectDir(new File("src/test/fixtures/android_app")).build()
        if (buildscript) {
            project.buildscript buildscript
        }
        project.apply plugin: plugin
        project.apply plugin: 'robolectric'
        project.android {
            compileSdkVersion 21
            buildToolsVersion '21.1.2'
        }
        return project
    }


    @Test
    public void appPluginTest() {
        Project project = ProjectBuilder.builder().withProjectDir(new File("src/test/fixtures/android_app")).build()

        project.apply plugin: 'com.android.application'
        project.apply plugin: 'robolectric'
        project.android {
            compileSdkVersion 21
            buildToolsVersion '21.1.2'
        }

        project.robolectric {
            sourceSets {
                test {
                    java.srcDir file('src/java')
                }
            }
        }

        project.evaluate()

        AppPlugin plugin = project.plugins.findPlugin(AppPlugin.class)

        assertThat(plugin.extraArtifacts)
                .hasSize(2)



        def artifacts = plugin.getExtraJavaArtifacts("debug")
        assertThat(artifacts)
                .hasSize(1)


        JavaArtifact debugArtifact = artifacts.first()
        assertThat(debugArtifact.assembleTaskName)
                .isEqualTo("assembleDebug")

        assertThat(debugArtifact.classesFolder.toString())
                .endsWith("src/test/fixtures/android_app/build/intermediates/classes/debug")

        assertThat(debugArtifact.compileTaskName)
                .isEqualTo("compileDebugJava")

        assertThat(debugArtifact.variantSourceProvider.javaDirectories)
            .hasSize(1)

        assertThat(debugArtifact.variantSourceProvider.javaDirectories.getAt(0).toString())
            .endsWith("/src/test/java/SomeTest.java")

        System.out.println("test")
    }

    @Test
    public void createsATaskCompilingFilesInSrcTestJavaLocation() {
        Project project = evaluatableProject()
        project.robolectric.sourceDir = 'src/test'
        project.evaluate()

        assertThat(project.tasks.compileTestDebugJava.source.files).containsOnly(project.file("src/test/java/SomeTest.java"))
    }

}
