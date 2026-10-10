import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("net.neoforged.moddev.legacyforge")
    id("bridge.bundle")
    id("bridge.neoforge-mutex")
}

val minecraft = sc.current.version

base.archivesName = "minecraft-discord-bridge-forge"
version = "${rootProject.version}+$minecraft"

repositories {
    mavenCentral()
}

legacyForge {
    version = sc.properties.get<String>("deps.forge")
}

dependencies {
    implementation(project(":core", "shadowRuntimeElements"))
    "bundle"(project(":core", "shadowRuntimeElements"))
    compileOnly(libs.jspecify)
}

sourceSets.main {
    java {
        exclude("com/minecraftdiscordbridge/modded/fabric/**", "com/minecraftdiscordbridge/modded/neoforge/**")
    }
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "minecraft" to sc.properties.get<String>("mod.mc_compat"),
    )
    inputs.properties(props)
    filesMatching("META-INF/mods.toml") { expand(props) }
    exclude("fabric.mod.json", "minecraftdiscordbridge.mixins.json", "META-INF/neoforge.mods.toml")
}

val shadowJar = tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier = "dev-shadow"
}

val reobfShadowJar = obfuscation.reobfuscate(shadowJar, sourceSets.main.get())
reobfShadowJar.configure { archiveClassifier = "" }
tasks.named("assemble") { dependsOn(reobfShadowJar) }
