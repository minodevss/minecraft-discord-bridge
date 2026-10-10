plugins {
    id("bridge.java")
    id("bridge.adapter")
}

base.archivesName = "minecraft-discord-bridge-velocity"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/") { name = "PaperMC" }
}

dependencies {
    compileOnly(libs.velocity.api)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("velocity-plugin.json") { expand(props) }
}
