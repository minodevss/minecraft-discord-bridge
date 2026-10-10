plugins {
    id("bridge.java-conventions")
    id("bridge.bundle")
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/") { name = "PaperMC" }
}

dependencies {
    compileOnly(libs.velocity.api)
    implementation(project(":core", "shadowRuntimeElements"))
    "bundle"(project(":core", "shadowRuntimeElements"))
    testImplementation(libs.gson)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("velocity-plugin.json") { expand(props) }
}
