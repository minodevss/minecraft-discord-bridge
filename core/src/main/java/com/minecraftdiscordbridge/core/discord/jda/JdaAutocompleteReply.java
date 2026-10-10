package com.minecraftdiscordbridge.core.discord.jda;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.discord.AutocompleteReply;
import com.minecraftdiscordbridge.core.log.Failures;
import com.minecraftdiscordbridge.core.text.Graphemes;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.dv8tion.jda.api.interactions.callbacks.IAutoCompleteCallback;
import net.dv8tion.jda.api.interactions.commands.Command;

final class JdaAutocompleteReply implements AutocompleteReply {
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
                        Graphemes.fit(choice.name(), MAX_CHOICE_LENGTH, Graphemes.ELLIPSIS),
                        Graphemes.fit(choice.value(), MAX_CHOICE_LENGTH, Graphemes.ELLIPSIS)))
                .toList();
        interaction
                .replyChoices(limited)
                .queue(
                        null,
                        failure -> LOGGER.debug("Autocomplete answer was too late: {}", Failures.describe(failure)));
    }
}
