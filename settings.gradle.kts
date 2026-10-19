import xyz.jpenilla.toothpick.setupToothpickProject

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("xyz.jpenilla.toothpick.settings")
}

val forkName = "Magnet"
rootProject.name = forkName.lowercase()
setupToothpickProject(rootProject, forkName)
