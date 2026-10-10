plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("com.gradleup.shadow:shadow-gradle-plugin:9.6.1")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.9.0")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:5.1.1")
}
