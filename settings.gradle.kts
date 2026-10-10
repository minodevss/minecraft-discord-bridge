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

data class ModNode(val minecraft: String, val loader: String, val gameVersions: List<String>) {
    val name = "$minecraft-$loader"
}

val allNodes = file("modded/stonecutter.properties.toml").readText().split("\n[").drop(1).map { table ->
    val (loader, minecraft) = Regex("""^(\w+)\."(.+)"]""").find(table)!!.destructured
    val gameVersions = Regex("""^mod\.game_versions = \[(.*)]$""", RegexOption.MULTILINE).find(table)
        ?: error("Set mod.game_versions for $minecraft-$loader in modded/stonecutter.properties.toml.")
    ModNode(minecraft, loader, Regex(""""([^"]+)"""").findAll(gameVersions.groupValues[1]).map { it.groupValues[1] }.toList())
}
gradle.extra["modNodes"] = allNodes.map { Triple(it.minecraft, it.loader, it.gameVersions) }

val requestedNodes = providers.gradleProperty("nodes").orNull
val activeNodes = when {
    requestedNodes == "all" -> allNodes
    requestedNodes != null -> allNodes.filterIndexed { index, node ->
        index == 0 || requestedNodes.split(',').map(String::trim).any { it == node.minecraft || it == node.loader || it == node.name }
    }
    else -> allNodes.take(1)
}

stonecutter {
    create("modded") {
        for (node in activeNodes) {
            version(node.name, node.minecraft).buildscript("build.${node.loader}.gradle.kts")
        }
        vcsVersion = allNodes.first().name
    }
}
