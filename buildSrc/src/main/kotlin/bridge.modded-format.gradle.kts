plugins {
    id("bridge.format")
}

// Palantir rewrites //? as // ? and sorts imports across conditions, which breaks Stonecutter; those regions keep their text.
val importLine = """(?:import [^\n]*|//\s?\?[^\n]*|/\*[^\n]*|\*/[^\n]*|)\n"""
val conditionalImports = """(?:import |//\s?\?)[^\n]*\n(?:$importLine)*?//\s?\?[^\n]*\n(?:$importLine)*"""
val conditionalBlock =
    """[ \t]*//\s?\?\s*if\b[^\n]*\n(?:[^\n]*\n)*?(?:[^\n]*//\s?\?\}[ \t]*\n|[ \t]*//\s?\?\}\s*else[ \t]*\n[^\n]*\n)"""

repositories {
    mavenCentral()
}

spotless {
    java {
        target("modded/src/**/*.java")
        toggleOffOnRegex("(?m)^($conditionalImports|$conditionalBlock)")
    }
}
