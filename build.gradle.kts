import me.modmuss50.mpp.ReleaseType

plugins {
    id("net.fabricmc.fabric-loom") version "1.18.3" apply false
    id("net.neoforged.moddev") version "2.0.148" apply false
    id("net.neoforged.moddev.legacyforge") version "2.0.148" apply false
    id("me.modmuss50.mod-publish-plugin") version "2.2.1"
    id("io.papermc.hangar-publish-plugin") version "0.1.4"
}

class ModJar(val minecraft: String, val loader: String, val gameVersions: List<String>) {
    val loaderName = mapOf("fabric" to "Fabric", "neoforge" to "NeoForge", "forge" to "Forge").getValue(loader)
    val platformName = loaderName + minecraft.replace('.', '_')
    val label = "$loaderName ${if (gameVersions.size == 1) gameVersions[0] else "${gameVersions.first()}-${gameVersions.last()}"}"

    fun fileName(version: String) = "minecraft-discord-bridge-$loader-$version+$minecraft.jar"
}

val modJars = listOf(
    ModJar("26.1.2", "fabric", listOf("26.1.2", "26.2", "26.3")),
    ModJar("1.21.11", "fabric", listOf("1.21.11")),
    ModJar("1.21.1", "fabric", listOf("1.21", "1.21.1")),
    ModJar("1.20.1", "fabric", listOf("1.20.1")),
    ModJar("26.2", "neoforge", listOf("26.2", "26.3")),
    ModJar("1.21.1", "neoforge", listOf("1.21", "1.21.1")),
    ModJar("1.20.1", "forge", listOf("1.20.1")),
)
val pluginGameVersions = listOf(
    "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11",
    "26.1", "26.1.1", "26.1.2", "26.2", "26.3",
)
val hangarPaperVersions = listOf("1.21-1.21.11", "26.1-26.3")
val hangarVelocityVersions = listOf("3.4-4.2.0")

val bridgeVersion = version.toString()
val paperJar = "minecraft-discord-bridge-paper-$bridgeVersion.jar"
val velocityJar = "minecraft-discord-bridge-velocity-$bridgeVersion.jar"
val dist = layout.projectDirectory.dir(providers.gradleProperty("publish.dist").orElse("dist"))
val dryRunOnly = providers.gradleProperty("publish.dryRun").isPresent
val releaseType = when {
    "alpha" in bridgeVersion -> ReleaseType.ALPHA
    '-' in bridgeVersion -> ReleaseType.BETA
    else -> ReleaseType.STABLE
}
val releaseNotes = providers.gradleProperty("publish.changelog")
    .flatMap { providers.fileContents(layout.projectDirectory.file(it)).asText }
    .orElse("https://github.com/minodevss/minecraft-discord-bridge/releases/tag/v$bridgeVersion")

fun secret(name: String) = providers.environmentVariable(name).filter(String::isNotBlank)

fun projectId(property: String, site: String) = providers.gradleProperty(property).orElse("").map {
    it.ifBlank { throw GradleException("Set $property in gradle.properties to the $site project ID, or remove the token.") }
}

fun distFile(name: String) = dist.map { it.file(name) }

val modrinthToken = secret("MODRINTH_TOKEN")
val curseforgeToken = secret("CURSEFORGE_TOKEN")
val hangarToken = secret("HANGAR_TOKEN")

publishMods {
    dryRun = dryRunOnly
    type = releaseType
    changelog = releaseNotes

    if (dryRunOnly || modrinthToken.isPresent) {
        val modrinthProject = modrinthOptions {
            accessToken = modrinthToken
            projectId = projectId("modrinth.id", "Modrinth")
        }
        modJars.forEach { jar ->
            modrinth("modrinth${jar.platformName}") {
                from(modrinthProject)
                file = distFile(jar.fileName(bridgeVersion))
                version = "$bridgeVersion+${jar.minecraft}-${jar.loader}"
                displayName = "$bridgeVersion ${jar.label}"
                modLoaders.add(jar.loader)
                minecraftVersions.addAll(jar.gameVersions)
                environment = DEDICATED_SERVER_ONLY
                if (jar.loader == "fabric") requires("fabric-api")
            }
        }
        modrinth("modrinthPaper") {
            from(modrinthProject)
            file = distFile(paperJar)
            version = "$bridgeVersion+paper"
            displayName = "$bridgeVersion Paper"
            modLoaders.addAll("paper", "folia", "purpur")
            minecraftVersions.addAll(pluginGameVersions)
        }
        modrinth("modrinthVelocity") {
            from(modrinthProject)
            file = distFile(velocityJar)
            version = "$bridgeVersion+velocity"
            displayName = "$bridgeVersion Velocity"
            modLoaders.add("velocity")
            minecraftVersions.addAll(pluginGameVersions)
        }
    }

    if (dryRunOnly || curseforgeToken.isPresent) {
        val curseforgeProject = curseforgeOptions {
            accessToken = curseforgeToken
            projectId = projectId("curseforge.id", "CurseForge")
            client = false
            server = true
        }
        modJars.forEach { jar ->
            curseforge("curseforge${jar.platformName}") {
                from(curseforgeProject)
                file = distFile(jar.fileName(bridgeVersion))
                version = "$bridgeVersion+${jar.minecraft}-${jar.loader}"
                displayName = "$bridgeVersion ${jar.label}"
                modLoaders.add(jar.loader)
                minecraftVersions.addAll(jar.gameVersions)
                if (jar.loader == "fabric") requires("fabric-api")
            }
        }
    }
}

hangarPublish {
    if (hangarToken.isPresent) {
        publications.register("plugin") {
            id = projectId("hangar.id", "Hangar")
            version = bridgeVersion
            channel = if (releaseType == ReleaseType.STABLE) "Release" else "Snapshot"
            changelog = releaseNotes
            apiKey = hangarToken
            platforms {
                paper {
                    jar = distFile(paperJar)
                    platformVersions = hangarPaperVersions
                }
                velocity {
                    jar = distFile(velocityJar)
                    platformVersions = hangarVelocityVersions
                }
            }
        }
    }
}
