package com.minecraftdiscordbridge.core.discord.jda;

import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import java.util.concurrent.CompletionStage;

interface MessageSender {
    CompletionStage<Void> send(OutboundMessage message);
}
