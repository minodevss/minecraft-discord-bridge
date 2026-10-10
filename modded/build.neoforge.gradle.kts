plugins {
    id("net.neoforged.moddev")
    id("bridge.bundle")
    id("bridge.neoforge-mutex")
}

val minecraft = sc.current.version
val javaVersion = if (sc.current.parsed >= "26.1") 25 else 21

base.archivesName = "minecraft-discord-bridge-neoforge"
version = "${rootProject.version}+$minecraft"

repositories {
    mavenCentral()
}

neoForge {
    version = sc.properties.get<String>("deps.neoforge")
}

dependencies {
    implementation(project(":core", "shadowRuntimeElements"))
    "bundle"(project(":core", "shadowRuntimeElements"))
    if (sc.current.parsed < "26.1") compileOnly(libs.jspecify)
}

sourceSets.main {
    java {
        exclude("com/minecraftdiscordbridge/modded/fabric/**", "com/minecraftdiscordbridge/modded/forge/**")
    }
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(javaVersion) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = javaVersion
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "minecraft" to sc.properties.get<String>("mod.mc_compat"),
    )
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
    exclude("fabric.mod.json", "META-INF/mods.toml")
}
