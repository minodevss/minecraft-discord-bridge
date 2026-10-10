import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("net.neoforged.moddev.legacyforge")
    id("bridge.modded")
    id("bridge.moddev-mutex")
}

legacyForge {
    version = sc.properties.get<String>("deps.forge")
}

dependencies {
    compileOnly(libs.jspecify)
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "minecraft" to sc.properties.get<String>("mod.mc_compat"),
    )
    inputs.properties(props)
    filesMatching("META-INF/mods.toml") { expand(props) }
}

val shadowJar = tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier = "dev-shadow"
}

val reobfShadowJar = obfuscation.reobfuscate(shadowJar, sourceSets.main.get())
reobfShadowJar.configure { archiveClassifier = "" }
tasks.named("assemble") { dependsOn(reobfShadowJar) }
