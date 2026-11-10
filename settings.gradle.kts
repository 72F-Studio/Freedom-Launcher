pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "Eigen Launcher"
include(":app")

// Kelvin, the 72F design system: its own build in design/kelvin (synced from 72F-Kelvin; the commit is in design/kelvin/SYNCED_FROM), with its
// own version catalog. It finds the Android SDK through a local.properties of its own, so it is handed this build's.
file("local.properties").takeIf { it.exists() }?.copyTo(file("design/kelvin/android/local.properties"), overwrite = true)
includeBuild("design/kelvin/android") {
    name = "kelvin-design-system"
    dependencySubstitution { substitute(module("studio72f:kelvin")).using(project(":kelvin")) }
}


include(":iconpack-test")
