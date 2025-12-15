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
        // Brightcove Maven repository
        maven {
            url = uri("https://repo.brightcove.com/releases")
        }
    }
}

rootProject.name = "Brightcove SDK"
include(":app")  // SDK library module
include(":demo") // Demo/test app module
 