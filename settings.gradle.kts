pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BatteryForensics"

include(
    ":app",
    ":core",
    ":battery",
    ":analytics",
    ":diagnostics",
    ":monitoring",
    ":telephony",
    ":wifi",
    ":display",
    ":thermal",
    ":parser",
    ":reporting",
    ":export",
    ":timeline",
    ":ruleengine",
    ":statistics",
    ":ai",
    ":permissions",
    ":settings",
    ":database",
    ":charts",
    ":shizuku",
)
