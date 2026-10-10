package com.minecraftdiscordbridge.paper;

import com.minecraftdiscordbridge.core.Bridge;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import com.minecraftdiscordbridge.core.platform.PlatformInfo;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

public final class PaperPlugin extends JavaPlugin {
    private static final String LOADED_MARKER = "minecraftdiscordbridge.loaded";
    private static final String RELOADED =
            "Minecraft Discord Bridge was reloaded in place. Restart the server instead; /reload is not supported.";

    private @Nullable Bridge bridge;

    @Override
    public void onEnable() {
        if (System.getProperty(LOADED_MARKER) != null) {
            getSLF4JLogger().error(RELOADED);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        System.setProperty(LOADED_MARKER, "true");

        PaperProfiles profiles = new PaperProfiles(Floodgate.bedrockCheck());
        Bridge started = Bridge.create(new PaperPlatform(info(), getDataFolder().toPath(), new PaperActions(this)));
        bridge = started;
        started.start();
        getServer().getPluginManager().registerEvents(new PaperEvents(started, profiles), this);
        getServer().getPluginManager().registerEvents(new PaperJoinGate(started, profiles, getSLF4JLogger()), this);
        getLifecycleManager()
                .registerEventHandler(
                        LifecycleEvents.COMMANDS,
                        event -> event.registrar()
                                .register(PaperCommand.node(started), PaperCommand.DESCRIPTION, List.of()));
    }

    @Override
    public void onDisable() {
        Bridge running = bridge;
        if (running == null) {
            return;
        }
        bridge = null;
        running.publish(new GameEvent.ServerStopping());
        running.stop();
    }

    @SuppressWarnings("deprecation")
    private static PlatformInfo info() {
        return new PlatformInfo(
                (Bukkit.getName() + " " + Bukkit.getVersion()).trim(),
                Bukkit.getMinecraftVersion(),
                Bukkit.getUnsafe().getDataVersion(),
                Bukkit.getOnlineMode(),
                PaperProxy.secureForwarding());
    }
}
