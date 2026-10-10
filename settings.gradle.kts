pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version "0.9.8"
}

rootProject.name = "minecraft-discord-bridge"

include("core", "paper", "velocity")

val allNodes = listOf(
    "26.1.2" to "fabric",
    "1.21.11" to "fabric",
    "1.21.1" to "fabric",
    "1.20.1" to "fabric",
    "26.2" to "neoforge",
    "1.21.1" to "neoforge",
    "1.20.1" to "forge",
)

val requestedNodes = providers.gradleProperty("nodes").orNull
val activeNodes = when {
    requestedNodes == "all" -> allNodes
    requestedNodes != null -> allNodes.filterIndexed { index, (version, loader) ->
        index == 0 || requestedNodes.split(',').map(String::trim).any { it == version || it == loader || it == "$version-$loader" }
    }
    else -> allNodes.take(1)
}

stonecutter {
    create("modded") {
        for ((version, loader) in activeNodes) {
            version("$version-$loader", version).buildscript("build.$loader.gradle.kts")
        }
        vcsVersion = "26.1.2-fabric"
    }
}
