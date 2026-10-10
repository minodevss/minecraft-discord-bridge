package com.minecraftdiscordbridge.core.discord;

public interface DiscordListener {
    void onStatusChanged(DiscordStatus status);

    void onMessage(DiscordMessage message);

    void onCommand(CommandRequest request, CommandReply reply);

    void onAutocomplete(AutocompleteRequest request, AutocompleteReply reply);

    void onMemberUpdated(MemberLookup.Member member);

    void onMemberLeft(DiscordId userId);
}
