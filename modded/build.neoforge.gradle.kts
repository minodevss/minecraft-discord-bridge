plugins {
    id("net.neoforged.moddev")
    id("bridge.modded")
    id("bridge.moddev-mutex")
}

neoForge {
    version = sc.properties.get<String>("deps.neoforge")
}

dependencies {
    if (sc.current.parsed < "26.1") compileOnly(libs.jspecify)
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "minecraft" to sc.properties.get<String>("mod.mc_compat"),
    )
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
}
