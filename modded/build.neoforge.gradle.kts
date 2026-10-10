plugins {
    id("net.neoforged.moddev")
    id("bridge.modded")
    id("bridge.moddev-mutex")
}

val javaVersion = if (sc.current.parsed >= "26.1") 25 else 21

neoForge {
    version = sc.properties.get<String>("deps.neoforge")
}

dependencies {
    if (sc.current.parsed < "26.1") compileOnly(libs.jspecify)
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
}
