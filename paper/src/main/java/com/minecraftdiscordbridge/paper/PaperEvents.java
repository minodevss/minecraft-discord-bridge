package com.minecraftdiscordbridge.paper;

import com.minecraftdiscordbridge.core.Bridge;
import com.minecraftdiscordbridge.core.platform.AdvancementFrame;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import com.minecraftdiscordbridge.core.platform.RichText;
import io.papermc.paper.advancement.AdvancementDisplay;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.metadata.MetadataValue;

public final class PaperEvents implements Listener {
    private final Bridge bridge;
    private final PaperProfiles profiles;

    PaperEvents(Bridge bridge, PaperProfiles profiles) {
        this.bridge = bridge;
        this.profiles = profiles;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerLoad(ServerLoadEvent event) {
        if (event.getType() == ServerLoadEvent.LoadType.STARTUP) {
            bridge.publish(new GameEvent.ServerStarted(
                    Bukkit.getOnlinePlayers().stream().map(profiles::of).toList()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        bridge.publish(new GameEvent.PlayerChatted(profiles.of(event.getPlayer()), plain(event.message())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        bridge.publish(new GameEvent.PlayerJoined(profiles.of(player), vanished(player)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // Paper kicks every player before onDisable, so core learns of the stop too late to skip these.
        if (Bukkit.isStopping()) {
            return;
        }
        Player player = event.getPlayer();
        bridge.publish(new GameEvent.PlayerLeft(profiles.of(player), vanished(player)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Component message = event.deathMessage();
        if (message != null) {
            bridge.publish(new GameEvent.PlayerDied(profiles.of(event.getEntity()), rich(message)));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        AdvancementDisplay display = event.getAdvancement().getDisplay();
        if (event.message() != null && display != null) {
            bridge.publish(new GameEvent.AdvancementEarned(
                    profiles.of(event.getPlayer()), rich(display.title()), frame(display.frame())));
        }
    }

    private static boolean vanished(Player player) {
        return player.getMetadata("vanished").stream().anyMatch(MetadataValue::asBoolean);
    }

    private static RichText rich(Component component) {
        return new RichText(GsonComponentSerializer.gson().serialize(component), plain(component));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static AdvancementFrame frame(AdvancementDisplay.Frame frame) {
        return switch (frame) {
            case GOAL -> AdvancementFrame.GOAL;
            case CHALLENGE -> AdvancementFrame.CHALLENGE;
            default -> AdvancementFrame.TASK;
        };
    }
}
