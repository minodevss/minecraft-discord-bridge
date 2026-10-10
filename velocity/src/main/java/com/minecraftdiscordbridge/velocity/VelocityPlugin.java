package com.minecraftdiscordbridge.velocity;

import com.google.inject.Inject;
import com.minecraftdiscordbridge.core.Bridge;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import com.minecraftdiscordbridge.core.platform.PlatformInfo;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyPreShutdownEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.ProxyVersion;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

public final class VelocityPlugin {
    private static final int CAMEL_CASE_TEXT_DATA_VERSION = 4189;

    private final ProxyServer proxy;
    private final Path dataDirectory;
    private final AtomicReference<@Nullable Bridge> bridge = new AtomicReference<>();

    @Inject
    public VelocityPlugin(ProxyServer proxy, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        Bridge started = Bridge.create(new VelocityPlatform(proxy, info(), dataDirectory));
        bridge.set(started);
        started.start();
        proxy.getEventManager()
                .register(this, new VelocityEvents(started, new VelocityProfiles(Floodgate.bedrockCheck())));
        BrigadierCommand command = VelocityCommand.create(started);
        CommandManager commands = proxy.getCommandManager();
        commands.register(commands.metaBuilder(command).plugin(this).build(), command);
        started.publish(new GameEvent.ServerStarted(List.of()));
    }

    @Subscribe
    public void onPreShutdown(ProxyPreShutdownEvent event) {
        stop();
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        stop();
    }

    private void stop() {
        Bridge running = bridge.getAndSet(null);
        if (running != null) {
            running.publish(new GameEvent.ServerStopping());
            running.stop();
        }
    }

    private PlatformInfo info() {
        ProxyVersion version = proxy.getVersion();
        return new PlatformInfo(
                (version.getName() + " " + version.getVersion()).trim(),
                ProtocolVersion.MAXIMUM_VERSION.getMostRecentSupportedVersion(),
                CAMEL_CASE_TEXT_DATA_VERSION,
                proxy.getConfiguration().isOnlineMode());
    }
}
