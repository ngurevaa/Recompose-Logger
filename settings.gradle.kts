pluginManagement {
    includeBuild("./gradle-plugin")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "Recompose Logger Compiler"

include(":compiler-plugin")

include(":compiler-plugin:recompose")
include(":compiler-plugin:recompose:logger")
include(":compiler-plugin:recompose:logger:plugin")
include(":compiler-plugin:recompose:logger:runtime")
include(":app")
