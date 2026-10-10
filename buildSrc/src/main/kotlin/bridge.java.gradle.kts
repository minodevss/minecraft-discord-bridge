import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    id("bridge.checks")
    id("bridge.format")
}

group = "com.minecraftdiscordbridge"

val catalog = versionCatalogs.named("libs")
fun lib(alias: String) = catalog.findLibrary(alias).get()

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(lib("jspecify"))
    testCompileOnly(lib("jspecify"))
    testImplementation(platform(lib("junit-bom")))
    testImplementation(lib("junit-jupiter"))
    testImplementation(lib("assertj"))
    testRuntimeOnly(lib("junit-launcher"))
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone.check("NullAway", CheckSeverity.OFF)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
