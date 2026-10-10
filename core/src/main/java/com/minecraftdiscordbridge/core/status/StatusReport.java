package com.minecraftdiscordbridge.core.status;

import com.minecraftdiscordbridge.core.discord.DiscordStatus;
import com.minecraftdiscordbridge.core.i18n.Replies;
import com.minecraftdiscordbridge.core.platform.CommandSource;
import com.minecraftdiscordbridge.core.text.Format.Slot;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;

public final class StatusReport {
    private StatusReport() {}

    public static void send(
            Replies replies,
            CommandSource source,
            DiscordStatus status,
            Optional<String> tokenSource,
            int links,
            long droppedEvents) {
        Component state =
                replies.lang().component("game.state." + status.state().name().toLowerCase(Locale.ROOT));
        replies.send(source, "game.status.state", Slot.component("state", state));
        status.reasons()
                .forEach(reason -> replies.send(
                        source,
                        "game.status.reason",
                        Slot.text(
                                "reason",
                                replies.lang().text("status." + reason.kind().key(), reason.detail()))));
        tokenSource.ifPresentOrElse(
                where -> replies.send(source, "game.status.token", Slot.text("source", where)),
                () -> replies.send(source, "game.status.no-token"));
        if (status.gatewayPingMillis() >= 0) {
            replies.send(source, "game.status.ping", Slot.text("ping", Long.toString(status.gatewayPingMillis())));
        }
        status.channels()
                .forEach((channel, queue) -> replies.send(
                        source,
                        "game.status.channel",
                        Slot.text(
                                "channel", queue.name().map(name -> "#" + name).orElse(channel.toString())),
                        Slot.text("queued", Integer.toString(queue.queued() + queue.inFlight())),
                        Slot.text("dropped", Long.toString(queue.dropped())),
                        Slot.text("limited", Long.toString(queue.rateLimited()))));
        replies.send(source, "game.status.links", Slot.text("count", Integer.toString(links)));
        replies.send(source, "game.status.dropped", Slot.text("count", Long.toString(droppedEvents)));
    }
}
