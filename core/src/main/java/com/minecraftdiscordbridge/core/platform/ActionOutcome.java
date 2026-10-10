package com.minecraftdiscordbridge.core.platform;

public sealed interface ActionOutcome {
    record Done() implements ActionOutcome {}

    record NoSuchPlayer(String playerName) implements ActionOutcome {}

    record Unchanged() implements ActionOutcome {}

    record Failed(String reason) implements ActionOutcome {}

    record Output(String text) implements ActionOutcome {}

    record Unsupported() implements ActionOutcome {}

    record UnknownCommand() implements ActionOutcome {}

    record ServerStopping() implements ActionOutcome {}
}
