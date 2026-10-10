plugins {
    id("bridge.adapter")
}

val (minecraft, loader) = name.split('-', limit = 2)
val javaRelease = property("mod.java").toString().toInt()
val minecraftRange = property("mod.mc_compat").toString()
val moddedPackage = "com/minecraftdiscordbridge/modded"
val loaderResources = mapOf(
    "fabric" to listOf("fabric.mod.json", "minecraftdiscordbridge.mixins.json"),
    "neoforge" to listOf("META-INF/neoforge.mods.toml"),
    "forge" to listOf("META-INF/mods.toml", "pack.mcmeta"),
)

base.archivesName = "minecraft-discord-bridge-$loader"
version = "${rootProject.version}+$minecraft"

repositories {
    mavenCentral()
}

val jspecify = versionCatalogs.named("libs").findLibrary("jspecify").get().get()

dependencies {
    // NeoForge 26 pins jspecify strictly; a preferred version lets that pin win.
    compileOnly("${jspecify.module}") { version { prefer(jspecify.versionConstraint.requiredVersion) } }
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(javaRelease) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = javaRelease
}

sourceSets.main {
    java.include("$moddedPackage/common/**", "$moddedPackage/$loader/**")
    resources.include(loaderResources.getValue(loader))
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf(
        "version" to project.version.toString(),
        "minecraft" to minecraftRange,
        "java" to javaRelease.toString(),
    )
    inputs.properties(props)
    filesMatching(loaderResources.getValue(loader)) { expand(props) }
}
