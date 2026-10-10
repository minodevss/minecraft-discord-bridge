import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
}

group = "com.minecraftdiscordbridge"

val catalog = versionCatalogs.named("libs")
fun lib(alias: String) = catalog.findLibrary(alias).get()

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    compileOnly(lib("jspecify"))
    testCompileOnly(lib("jspecify"))
    errorprone(lib("errorprone-core"))
    errorprone(lib("nullaway"))
    testImplementation(platform(lib("junit-bom")))
    testImplementation(lib("junit-jupiter"))
    testImplementation(lib("assertj"))
    testRuntimeOnly(lib("junit-launcher"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing,-classfile,-serial,-options", "-Werror"))
    options.errorprone {
        disableWarningsInGeneratedCode = true
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:OnlyNullMarked", "true")
    }
}

tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone.check("NullAway", CheckSeverity.OFF)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

spotless {
    java {
        palantirJavaFormat(lib("palantir-java-format").get().versionConstraint.requiredVersion)
        removeUnusedImports()
        forbidWildcardImports()
    }
}
