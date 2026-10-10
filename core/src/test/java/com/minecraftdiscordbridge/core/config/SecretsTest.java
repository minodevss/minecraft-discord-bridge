package com.minecraftdiscordbridge.core.config;

import static com.minecraftdiscordbridge.core.config.ConfigFiles.errors;
import static com.minecraftdiscordbridge.core.config.ConfigFiles.loaded;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecretsTest {
    private static final String TOKEN = "MTIzNDU2Nzg5MDEyMzQ1Njc4.GabcDE.secret-part_xyz";

    @TempDir
    Path directory;

    @Test
    void createsAnOwnerOnlySecretsFileOnFirstStart() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));

        Secrets secrets = load(Map.of());

        Path file = directory.resolve("secrets.yml");
        assertThat(file).hasContent(SecretsReader.TEMPLATE.stripTrailing());
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file)))
                .isEqualTo("rw-------");
        assertThat(secrets.hasDiscordToken()).isFalse();
    }

    @Test
    void readsTheTokenFromSecretsFile() throws IOException {
        writeSecrets("discord-token: \"  " + TOKEN + "  \"\n");

        Secrets secrets = load(Map.of());

        assertThat(secrets.hasDiscordToken()).isTrue();
        assertThat(secrets.discordToken()).isEqualTo(TOKEN);
        assertThat(secrets.discordTokenSource()).isEqualTo("secrets.yml");
    }

    @Test
    void environmentTokenOverridesTheFile() throws IOException {
        writeSecrets("discord-token: \"from-file\"\n");

        Secrets secrets = load(Map.of("MCDB_DISCORD_TOKEN", TOKEN));

        assertThat(secrets.discordToken()).isEqualTo(TOKEN);
        assertThat(secrets.discordTokenSource()).isEqualTo("MCDB_DISCORD_TOKEN");
    }

    @Test
    void readsAndTrimsTheTokenFileNamedByTheEnvironment() throws IOException {
        writeSecrets("discord-token: \"from-file\"\n");
        Path tokenFile = Files.writeString(directory.resolve("token.txt"), "\n " + TOKEN + "\n\n");

        Secrets secrets = load(Map.of("MCDB_DISCORD_TOKEN_FILE", tokenFile.toString()));

        assertThat(secrets.discordToken()).isEqualTo(TOKEN);
        assertThat(secrets.discordTokenSource()).isEqualTo("MCDB_DISCORD_TOKEN_FILE");
    }

    @Test
    void failsWhenTheTokenFileCannotBeRead() {
        Path missing = directory.resolve("missing-token");

        ConfigResult result = new ConfigLoader(directory, Map.of("MCDB_DISCORD_TOKEN_FILE", missing.toString())).load();

        assertThat(errors(result))
                .containsExactly("MCDB_DISCORD_TOKEN_FILE: cannot read " + missing
                        + " (NoSuchFileException). Check the path and the file permissions.");
    }

    @Test
    void neverRewritesAnExistingSecretsFile() throws IOException {
        String content = "# mine\ndiscord-token: " + TOKEN + "\n";
        writeSecrets(content);

        load(Map.of());
        load(Map.of("MCDB_DISCORD_TOKEN", "other"));

        assertThat(directory.resolve("secrets.yml")).hasContent(content.stripTrailing());
    }

    @Test
    void keepsTheTokenOutOfToStringAndErrors() throws IOException {
        writeSecrets("discord-token: " + TOKEN + "\ndiscord-tokn: [" + TOKEN + "]\n");

        ConfigResult result = new ConfigLoader(directory, Map.of()).load();

        assertThat(result.toString()).doesNotContain(TOKEN).contains("<hidden, from secrets.yml>");
        assertThat(ConfigFiles.warnings(result))
                .containsExactly("secrets.yml:2:1 discord-tokn: unknown key is ignored; did you mean discord-token?");
    }

    @Test
    void keepsAPastedTokenOutOfParseErrors() throws IOException {
        for (String pasted :
                new String[] {TOKEN + "\n", "discord-token: \"" + TOKEN + "\n", "discord-token: [" + TOKEN + "]\n"}) {
            writeSecrets(pasted);

            ConfigResult result = new ConfigLoader(directory, Map.of()).load();

            assertThat(errors(result))
                    .isNotEmpty()
                    .allSatisfy(error -> assertThat(error)
                            .startsWith("secrets.yml")
                            .contains("discord-token: \"your token\"")
                            .doesNotContain("MTIz", "secret-part"));
        }
    }

    @Test
    void refusesToHandOutAMissingToken() {
        Secrets secrets = Secrets.none();

        assertThat(secrets.hasDiscordToken()).isFalse();
        assertThat(secrets).hasToString("Secrets[discordToken=<not set>]");
        assertThatThrownBy(secrets::discordToken).isInstanceOf(IllegalStateException.class);
    }

    private Secrets load(Map<String, String> environment) {
        return loaded(new ConfigLoader(directory, environment).load()).secrets();
    }

    private void writeSecrets(String content) throws IOException {
        Files.writeString(directory.resolve("secrets.yml"), content);
    }
}
