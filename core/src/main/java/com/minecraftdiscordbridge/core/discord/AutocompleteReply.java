package com.minecraftdiscordbridge.core.discord;

import java.util.List;

public interface AutocompleteReply {
    int MAX_CHOICES = 25;

    void respond(List<Choice> choices);

    record Choice(String name, String value) {}
}
