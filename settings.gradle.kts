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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Linphone SDK lives on Belledonne's own Maven repo, not Maven
        // Central. Scoped to the org.linphone group so it only affects
        // the SIP dependency.
        maven {
            url = uri("https://download.linphone.org/maven_repository/")
            content { includeGroup("org.linphone") }
        }
    }
}

rootProject.name = "Calls Agends"
include(":app")
 