pluginManagement {
    includeBuild("build-logic")
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
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\..*") }
        }
    }
}

rootProject.name = "lumen"

include(":app")

include(":core:model")
include(":core:common")
include(":core:network")
include(":core:database")
include(":core:datastore")
include(":core:designsystem")

include(":data:youtube")
include(":data:sponsorblock")
include(":data:dearrow")
include(":data:ryd")
include(":data:library")
include(":data:updater")

include(":player")

include(":feature:home")
include(":feature:search")
include(":feature:catalog")
include(":feature:channel")
include(":feature:playlist")
include(":feature:watch")
include(":feature:library")
include(":feature:settings")
