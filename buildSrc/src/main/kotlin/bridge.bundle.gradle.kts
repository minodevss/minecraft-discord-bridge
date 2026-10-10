import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.gradleup.shadow")
}

val bundle = configurations.create("bundle") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(bundle)
    archiveClassifier = ""
    addMultiReleaseAttribute = false
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    filesNotMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
}

tasks.named<Jar>("jar") {
    archiveClassifier = "slim"
}
