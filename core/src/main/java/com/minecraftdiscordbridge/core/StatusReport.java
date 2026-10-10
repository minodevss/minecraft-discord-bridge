package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.discord.DiscordStatus;
import com.minecraftdiscordbridge.core.i18n.Replies;
import com.minecraftdiscordbridge.core.platform.CommandSource;
import com.minecraftdiscordbridge.core.text.Format.Slot;
import java.util.Locale;
import net.kyori.adventure.text.Component;

final class StatusReport {
    private StatusReport() {}

    static void send(Replies replies, CommandSource source, DiscordStatus status, int links, long droppedEvents) {
        Component state =
                replies.lang().component("game.state." + status.state().name().toLowerCase(Locale.ROOT));
        replies.send(source, "game.status.state", Slot.component("state", state));
        String reason = status.reason();
        if (reason != null) {
            replies.send(source, "game.status.reason", Slot.text("reason", reason));
        }
        if (status.gatewayPingMillis() >= 0) {
            replies.send(source, "game.status.ping", Slot.text("ping", Long.toString(status.gatewayPingMillis())));
        }
        status.channels()
                .forEach((channel, queue) -> replies.send(
                        source,
                        "game.status.channel",
                        Slot.text("channel", Long.toUnsignedString(channel)),
                        Slot.text("queued", Integer.toString(queue.queued() + queue.inFlight())),
                        Slot.text("dropped", Long.toString(queue.dropped())),
                        Slot.text("limited", Long.toString(queue.rateLimited()))));
        replies.send(source, "game.status.links", Slot.text("count", Integer.toString(links)));
        replies.send(source, "game.status.dropped", Slot.text("count", Long.toString(droppedEvents)));
    }
}
