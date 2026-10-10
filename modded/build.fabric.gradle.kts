import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.task.RemapJarTask

plugins {
    id("bridge.modded")
}

val minecraft = sc.current.version
val unobfuscated = sc.current.parsed >= "26.1"
val javaVersion = when {
    sc.current.parsed >= "26.1" -> 25
    sc.current.parsed >= "1.20.5" -> 21
    else -> 17
}

apply(plugin = if (unobfuscated) "net.fabricmc.fabric-loom" else "net.fabricmc.fabric-loom-remap")
val loom = the<LoomGradleExtensionAPI>()

val modConfiguration = if (unobfuscated) "implementation" else "modImplementation"
val permissionsApi = "me.lucko:fabric-permissions-api:${sc.properties.get<String>("deps.fabric_permissions")}"

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraft")
    if (!unobfuscated) "mappings"(loom.officialMojangMappings())
    modConfiguration("net.fabricmc:fabric-loader:${sc.properties.get<String>("deps.fabric_loader")}")
    modConfiguration("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")
    modConfiguration(permissionsApi)
    "include"(permissionsApi)
    "compileOnly"(libs.jspecify)
}

extensions.configure<JavaPluginExtension> {
    toolchain { languageVersion = JavaLanguageVersion.of(javaVersion) }
}

loom.serverOnlyMinecraftJar()

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = javaVersion
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf(
        "version" to project.version.toString(),
        "minecraft" to sc.properties.get<String>("mod.mc_compat"),
        "java" to javaVersion.toString(),
    )
    inputs.properties(props)
    filesMatching(listOf("fabric.mod.json", "minecraftdiscordbridge.mixins.json")) { expand(props) }
}

if (unobfuscated) {
    loom.nestJars(tasks.named<ShadowJar>("shadowJar"), configurations.named("include"))
} else {
    val shadowJar = tasks.named<ShadowJar>("shadowJar") {
        archiveClassifier = "dev-shadow"
    }
    tasks.named<RemapJarTask>("remapJar") {
        dependsOn(shadowJar)
        inputFile = shadowJar.flatMap { it.archiveFile }
        archiveClassifier = ""
    }
}
