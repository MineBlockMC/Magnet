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
// Magnet - Gradle 9 要求 included project 目录在配置期存在；applyPatches 会清理并重新克隆
rootDir.resolve("$forkName-API").mkdirs()
rootDir.resolve("$forkName-Server").mkdirs()
setupToothpickProject(rootProject, forkName)