import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

interface MinecraftArtifactsMutex : BuildService<BuildServiceParameters.None>

val mutex = gradle.sharedServices.registerIfAbsent("createMinecraftArtifactsMutex", MinecraftArtifactsMutex::class.java) {
    maxParallelUsages = 1
}

tasks.named { it == "createMinecraftArtifacts" }.configureEach {
    usesService(mutex)
}
