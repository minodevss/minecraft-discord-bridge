package com.minecraftdiscordbridge.core.config;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class Secrets {
    private static final Secrets NONE = new Secrets("", "");

    private final String discordToken;
    private final String discordTokenSource;

    private Secrets(String discordToken, String discordTokenSource) {
        this.discordToken = discordToken;
        this.discordTokenSource = discordTokenSource;
    }

    static Secrets none() {
        return NONE;
    }

    static Secrets of(String discordToken, String source) {
        return discordToken.isEmpty() ? NONE : new Secrets(discordToken, source);
    }

    public boolean hasDiscordToken() {
        return !discordToken.isEmpty();
    }

    public String discordToken() {
        if (discordToken.isEmpty()) {
            throw new IllegalStateException(
                    "No Discord bot token is set. Put it in secrets.yml or set " + SecretsReader.TOKEN_VARIABLE);
        }
        return discordToken;
    }

    public String discordTokenSource() {
        return discordTokenSource;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Secrets that
                && discordToken.equals(that.discordToken)
                && discordTokenSource.equals(that.discordTokenSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(discordToken, discordTokenSource);
    }

    @Override
    public String toString() {
        return hasDiscordToken()
                ? "Secrets[discordToken=<hidden, from " + discordTokenSource + ">]"
                : "Secrets[discordToken=<not set>]";
    }
}
