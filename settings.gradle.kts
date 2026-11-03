// Builds the Hailbell Android library on its own, for JitPack and for local checks.
// Apps that add the dependency from JitPack never see this file.
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

rootProject.name = "hailbell-android"
include(":hailbell")
