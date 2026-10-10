import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.gradleup.shadow")
}

val shadedCore = configurations.dependencyScope("shadedCore")
val shadedCoreClasspath = configurations.resolvable("shadedCoreClasspath") {
    extendsFrom(shadedCore.get())
}

configurations.named("implementation") {
    extendsFrom(shadedCore.get())
}

dependencies {
    shadedCore(project(":core", "shadowRuntimeElements"))
}

val legalFiles = isolated.rootProject.projectDirectory.let { root ->
    listOf(root.file("LICENSE"), root.file("NOTICE"), root.file("THIRD-PARTY-NOTICES"))
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(shadedCoreClasspath.get())
    archiveClassifier = ""
    addMultiReleaseAttribute = false
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    filesNotMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
    // Bundled libraries' own LICENSE and NOTICE files would shadow ours; THIRD-PARTY-NOTICES carries their text.
    filesMatching(listOf("META-INF/LICENSE", "META-INF/LICENSE.*", "META-INF/NOTICE", "META-INF/NOTICE.*")) { exclude() }
    from(legalFiles) { into("META-INF") }
}

tasks.named<Jar>("jar") {
    archiveClassifier = "slim"
}
