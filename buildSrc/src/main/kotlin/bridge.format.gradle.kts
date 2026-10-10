plugins {
    id("com.diffplug.spotless")
}

val palantir = versionCatalogs.named("libs").findLibrary("palantir-java-format").get().get()

spotless {
    java {
        palantirJavaFormat(palantir.versionConstraint.requiredVersion)
        removeUnusedImports()
        forbidWildcardImports()
    }
}
