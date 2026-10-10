package com.minecraftdiscordbridge.core.discord;

import java.util.concurrent.CompletionStage;

interface MessageSender {
    CompletionStage<Void> send(long channelId, Post post);
}
