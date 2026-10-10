package com.minecraftdiscordbridge.velocity;

import com.minecraftdiscordbridge.core.platform.ActionOutcome;
import com.minecraftdiscordbridge.core.platform.GameAction;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;

final class VelocityActions {
    private static final String NO_WHITELIST = "Whitelisting is not supported on a proxy";

    private final ProxyServer proxy;

    VelocityActions(ProxyServer proxy) {
        this.proxy = proxy;
    }

    CompletableFuture<ActionOutcome> perform(GameAction action) {
        return switch (action) {
            case GameAction.WhitelistAdd add ->
                CompletableFuture.completedFuture(new ActionOutcome.Failed(NO_WHITELIST));
            case GameAction.WhitelistRemove remove ->
                CompletableFuture.completedFuture(new ActionOutcome.Failed(NO_WHITELIST));
            case GameAction.Kick kick -> CompletableFuture.completedFuture(kick(kick.playerName(), kick.reason()));
            case GameAction.RunConsoleCommand run -> runConsoleCommand(run.command());
        };
    }

    private CompletableFuture<ActionOutcome> runConsoleCommand(String command) {
        VelocityConsoleFeedback source = new VelocityConsoleFeedback();
        return proxy.getCommandManager()
                .executeAsync(source, command)
                .thenApply(known -> known || source.hasFeedback()
                        ? new ActionOutcome.Output(source.feedback())
                        : new ActionOutcome.Failed("Unknown command"));
    }

    private ActionOutcome kick(String name, String reason) {
        return proxy.getPlayer(name)
                .<ActionOutcome>map(player -> {
                    player.disconnect(Component.text(reason));
                    return new ActionOutcome.Done();
                })
                .orElseGet(() -> new ActionOutcome.NoSuchPlayer(name));
    }
}
