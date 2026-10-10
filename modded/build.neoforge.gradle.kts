plugins {
    id("net.neoforged.moddev")
    id("bridge.modded")
    id("bridge.moddev-mutex")
}

neoForge {
    version = sc.properties.get<String>("deps.neoforge")
}

