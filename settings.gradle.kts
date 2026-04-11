pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "Somn"

include(":app")
include(":core:data")
include(":core:domain")
include(":core:ui")
include(":feature:tracking")
include(":feature:alarm")
include(":feature:analytics")
include(":feature:settings")
include(":feature:onboarding")
