import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("bridge.java-conventions")
    id("com.gradleup.shadow")
}

dependencies {
    api(project(":api"))
    implementation(libs.jda) {
        exclude(group = "club.minnced", module = "opus-java")
        exclude(group = "com.google.crypto.tink", module = "tink")
    }
    implementation(libs.adventure.api)
    implementation(libs.adventure.minimessage)
    implementation(libs.adventure.serializer.gson)
    implementation(libs.adventure.serializer.plain)
    implementation(libs.snakeyaml.engine)
    compileOnly(libs.gson)
    compileOnly(libs.slf4j.api)
    compileOnly(libs.log4j.core)
    testImplementation(libs.gson)
    testImplementation(libs.slf4j.api)
    testImplementation(libs.archunit)
    testImplementation(libs.mockwebserver)
}

val relocated = listOf(
    "net.dv8tion.jda",
    "com.neovisionaries.ws",
    "okhttp3",
    "okio",
    "kotlin",
    "com.fasterxml.jackson",
    "gnu.trove",
    "org.apache.commons.collections4",
    "net.kyori",
    "org.snakeyaml.engine",
)

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier = "all"
    addMultiReleaseAttribute = false
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    filesNotMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
    relocated.forEach { relocate(it, "com.minecraftdiscordbridge.libs.$it") }
    exclude(
        "META-INF/*.kotlin_module",
        "META-INF/maven/**",
        "META-INF/proguard/**",
        "META-INF/versions/*/module-info.class",
        "module-info.class",
    )
    dependencies {
        exclude(dependency("com.google.code.gson:.*"))
        exclude(dependency("org.slf4j:.*"))
        exclude(dependency("org.jetbrains:annotations"))
        exclude(dependency("org.jspecify:.*"))
    }
}
