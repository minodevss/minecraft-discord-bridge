plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.shadow.plugin)
    implementation(libs.spotless.plugin)
    implementation(libs.errorprone.plugin)
}
