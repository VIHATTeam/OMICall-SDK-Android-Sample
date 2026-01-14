pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven { url = uri("https://jitpack.io") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        mavenLocal()
        maven {
            url = uri("https://maven.pkg.github.com/omicall/OMICall-SDK")
            credentials {
                username = providers.gradleProperty("GITHUB_USERNAME").orNull ?: ""
                password = providers.gradleProperty("GITHUB_TOKEN").orNull ?: ""
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}

rootProject.name = "omicall"
include(":app")
 