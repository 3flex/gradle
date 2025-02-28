package configurations

import common.Os
import common.applyDefaultSettings
import common.buildToolGradleParameters
import common.checkCleanM2AndAndroidUserHome
import common.compileAllDependency
import common.functionalTestParameters
import common.gradleWrapper
import common.killProcessStep
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildFeatures
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.FailureAction
import jetbrains.buildServer.configs.kotlin.v2019_2.ProjectFeatures
import jetbrains.buildServer.configs.kotlin.v2019_2.RelativeId
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.PullRequests
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import model.CIBuildModel
import model.StageNames

val m2CleanScriptUnixLike = """
    REPO=%teamcity.agent.jvm.user.home%/.m2/repository
    if [ -e ${'$'}REPO ] ; then
        tree ${'$'}REPO
        rm -rf ${'$'}REPO
        echo "${'$'}REPO was polluted during the build"
        exit 1
    else
        echo "${'$'}REPO does not exist"
    fi

""".trimIndent()

val m2CleanScriptWindows = """
    IF exist %teamcity.agent.jvm.user.home%\.m2\repository (
        TREE %teamcity.agent.jvm.user.home%\.m2\repository
        RMDIR /S /Q %teamcity.agent.jvm.user.home%\.m2\repository
        EXIT 1
    )

""".trimIndent()

val checkCleanAndroidUserHomeScriptUnixLike = """
    ANDROID_USER_HOME=%teamcity.agent.jvm.user.home%/.android
    if [ -e ${'$'}ANDROID_USER_HOME ] ; then
        tree ${'$'}ANDROID_USER_HOME
        rm -rf ${'$'}ANDROID_USER_HOME
        echo "${'$'}ANDROID_USER_HOME was polluted during the build"
        # exit 1
    else
        echo "${'$'}ANDROID_USER_HOME does not exist"
    fi
""".trimIndent()

val checkCleanAndroidUserHomeScriptWindows = """
    IF exist %teamcity.agent.jvm.user.home%\.android (
        TREE %teamcity.agent.jvm.user.home%\.android
        RMDIR /S /Q %teamcity.agent.jvm.user.home%\.android
        REM EXIT 1
    )
""".trimIndent()

fun BuildFeatures.publishBuildStatusToGithub(model: CIBuildModel) {
    if (model.publishStatusToGitHub) {
        publishBuildStatusToGithub()
    }
}

fun BuildFeatures.triggeredOnPullRequests(model: CIBuildModel) {
    pullRequests {
        vcsRootExtId = "Gradle_Branches_GradlePersonalBranches"
        provider = github {
            authType = token {
                token = "%github.bot-gradle.token%"
            }
            filterAuthorRole = PullRequests.GitHubRoleFilter.MEMBER
            filterTargetBranch = model.branch.branchFilter()
        }
    }
}

fun BuildFeatures.publishBuildStatusToGithub() {
    commitStatusPublisher {
        vcsRootExtId = "Gradle_Branches_GradlePersonalBranches"
        publisher = github {
            githubUrl = "https://api.github.com"
            authType = personalToken {
                token = "%github.bot-gradle.token%"
            }
        }
    }
}

fun ProjectFeatures.buildReportTab(title: String, startPage: String) {
    feature {
        type = "ReportTab"
        param("startPage", startPage)
        param("title", title)
        param("type", "BuildReportTab")
    }
}

fun BaseGradleBuildType.gradleRunnerStep(model: CIBuildModel, gradleTasks: String, os: Os = Os.LINUX, extraParameters: String = "", daemon: Boolean = true) {
    val buildScanTags = model.buildScanTags + listOfNotNull(stage?.id)
    val parameters = (
        buildToolGradleParameters(daemon) +
            listOf(extraParameters) +
            buildScanTags.map { buildScanTag(it) } +
            functionalTestParameters(os)
        ).joinToString(separator = " ")

    steps {
        gradleWrapper {
            name = "GRADLE_RUNNER"
            tasks = "clean $gradleTasks"
            gradleParams = parameters
        }
    }
}

private
fun BuildType.attachFileLeakDetector() {
    steps {
        script {
            name = "ATTACH_FILE_LEAK_DETECTOR"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
            "%windows.java11.openjdk.64bit%\bin\java" gradle/AttachAgentToDaemon.java
        """.trimIndent()
        }
    }
}

private
fun BuildType.dumpOpenFiles() {
    steps {
        // This is a workaround for https://youtrack.jetbrains.com/issue/TW-24782
        script {
            name = "SET_BUILD_SUCCESS_ENV"
            executionMode = BuildStep.ExecutionMode.RUN_ON_SUCCESS
            scriptContent = """
                echo "##teamcity[setParameter name='env.PREV_BUILD_STATUS' value='SUCCESS']"
            """.trimIndent()
        }
        script {
            name = "DUMP_OPEN_FILES_ON_FAILURE"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
                "%windows.java11.openjdk.64bit%\bin\java" gradle\DumpOpenFilesOnFailure.java
            """.trimIndent()
        }
    }
}

fun applyDefaults(model: CIBuildModel, buildType: BaseGradleBuildType, gradleTasks: String, notQuick: Boolean = false, os: Os = Os.LINUX, extraParameters: String = "", timeout: Int = 90, extraSteps: BuildSteps.() -> Unit = {}, daemon: Boolean = true) {
    buildType.applyDefaultSettings(os, timeout = timeout)

    buildType.killProcessStep("KILL_LEAKED_PROCESSES_FROM_PREVIOUS_BUILDS", daemon)
    buildType.gradleRunnerStep(model, gradleTasks, os, extraParameters, daemon)

    buildType.steps {
        extraSteps()
        checkCleanM2AndAndroidUserHome(os)
    }

    applyDefaultDependencies(model, buildType, notQuick)
}

fun applyTestDefaults(
    model: CIBuildModel,
    buildType: BaseGradleBuildType,
    gradleTasks: String,
    notQuick: Boolean = false,
    os: Os = Os.LINUX,
    extraParameters: String = "",
    timeout: Int = 90,
    extraSteps: BuildSteps.() -> Unit = {}, // the steps after runner steps
    daemon: Boolean = true,
    preSteps: BuildSteps.() -> Unit = {} // the steps before runner steps
) {
    buildType.applyDefaultSettings(os, timeout = timeout)

    buildType.steps {
        preSteps()
    }

    if (os == Os.WINDOWS) {
        buildType.attachFileLeakDetector()
    }

    buildType.killProcessStep("KILL_LEAKED_PROCESSES_FROM_PREVIOUS_BUILDS", daemon)

    buildType.gradleRunnerStep(model, gradleTasks, os, extraParameters, daemon)

    if (os == Os.WINDOWS) {
        buildType.dumpOpenFiles()
    }
    buildType.killProcessStep("KILL_PROCESSES_STARTED_BY_GRADLE", daemon)

    buildType.steps {
        extraSteps()
        checkCleanM2AndAndroidUserHome(os)
    }

    applyDefaultDependencies(model, buildType, notQuick)
}

fun buildScanTag(tag: String) = """"-Dscan.tag.$tag""""
fun buildScanCustomValue(key: String, value: String) = """"-Dscan.value.$key=$value""""
fun applyDefaultDependencies(model: CIBuildModel, buildType: BuildType, notQuick: Boolean = false) {
    if (notQuick) {
        // wait for quick feedback phase to finish successfully
        buildType.dependencies {
            dependency(RelativeId(stageTriggerId(model, StageNames.QUICK_FEEDBACK_LINUX_ONLY))) {
                snapshot {
                    onDependencyFailure = FailureAction.CANCEL
                    onDependencyCancel = FailureAction.CANCEL
                }
            }
        }
    }
    if (buildType !is CompileAllProduction) {
        buildType.dependencies {
            compileAllDependency(CompileAllProduction.buildTypeId(model))
        }
    }
}
