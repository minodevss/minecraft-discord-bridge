package com.minecraftdiscordbridge.core.discord;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.dv8tion.jda.api.interactions.callbacks.IAutoCompleteCallback;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class JdaAutocompleteReply implements AutocompleteReply {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");
    private static final int MAX_CHOICE_LENGTH = 100;

    private final IAutoCompleteCallback interaction;
    private final AtomicBoolean answered = new AtomicBoolean();

    JdaAutocompleteReply(IAutoCompleteCallback interaction) {
        this.interaction = interaction;
    }

    @Override
    public void respond(List<Choice> choices) {
        if (!answered.compareAndSet(false, true)) {
            return;
        }
        List<Command.Choice> limited = choices.stream()
                .filter(choice -> !choice.name().isBlank() && !choice.value().isBlank())
                .limit(MAX_CHOICES)
                .map(choice -> new Command.Choice(
                        DiscordText.fit(choice.name(), MAX_CHOICE_LENGTH),
                        DiscordText.fit(choice.value(), MAX_CHOICE_LENGTH)))
                .toList();
        interaction
                .replyChoices(limited)
                .queue(
                        null,
                        failure -> LOGGER.debug("Autocomplete answer was too late: {}", Failures.describe(failure)));
    }
}
