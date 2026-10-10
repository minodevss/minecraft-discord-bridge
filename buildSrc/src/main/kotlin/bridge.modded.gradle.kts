plugins {
    id("bridge.adapter")
}

val (minecraft, loader) = name.split('-', limit = 2)
val javaRelease = property("mod.java").toString().toInt()
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
