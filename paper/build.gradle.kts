plugins {
    id("bridge.java-conventions")
    id("bridge.bundle")
}

base.archivesName = "minecraft-discord-bridge-paper"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/") { name = "PaperMC" }
}

dependencies {
    compileOnly(libs.paper.api)
    implementation(project(":core", "shadowRuntimeElements"))
    "bundle"(project(":core", "shadowRuntimeElements"))
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}
