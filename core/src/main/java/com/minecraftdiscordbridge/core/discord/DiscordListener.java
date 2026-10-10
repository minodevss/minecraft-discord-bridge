package com.minecraftdiscordbridge.core.discord;

public interface DiscordListener {
    void onStatusChanged(DiscordStatus status);

    void onMessage(DiscordMessage message);

    void onCommand(InteractionRequest request, InteractionReply reply);

    void onAutocomplete(AutocompleteRequest request, AutocompleteReply reply);
}
