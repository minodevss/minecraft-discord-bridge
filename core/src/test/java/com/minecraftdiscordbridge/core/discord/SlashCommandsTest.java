package com.minecraftdiscordbridge.core.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.Test;

class SlashCommandsTest {
    private static final CommandSpec KICK = new CommandSpec(
            "kick",
            "Kick a player",
            new CommandSpec.Localized("추방", "플레이어를 추방합니다"),
            List.of(new CommandSpec.Option(
                    CommandSpec.OptionType.STRING,
                    "player",
                    "Player name",
                    new CommandSpec.Localized("플레이어", "플레이어 이름"),
                    true,
                    true)),
            true,
            false,
            true);
    private static final CommandSpec LINK =
            new CommandSpec("link", "Link your account", null, List.of(), false, false, true);

    @Test
    void limitsAdminCommandsToAdministratorsByDefault() {
        DataObject kick = SlashCommands.toData(KICK).toData();
        DataObject link = SlashCommands.toData(LINK).toData();

        assertThat(kick.getString("default_member_permissions")).isEqualTo("0");
        assertThat(link.isNull("default_member_permissions")).isTrue();
    }

    @Test
    void offersCommandsOnlyInsideServers() {
        DataObject kick = SlashCommands.toData(KICK).toData();

        assertThat(kick.getArray("contexts").length()).isEqualTo(1);
        assertThat(kick.getArray("contexts").getInt(0)).isZero();
    }

    @Test
    void carriesKoreanNamesAndAutocomplete() {
        DataObject kick = SlashCommands.toData(KICK).toData();
        DataObject option = kick.getArray("options").getObject(0);

        assertThat(kick.getObject("name_localizations").getString("ko")).isEqualTo("추방");
        assertThat(kick.getObject("description_localizations").getString("ko")).isEqualTo("플레이어를 추방합니다");
        assertThat(option.getObject("name_localizations").getString("ko")).isEqualTo("플레이어");
        assertThat(option.getBoolean("autocomplete")).isTrue();
        assertThat(option.getBoolean("required")).isTrue();
    }

    @Test
    void carriesSubcommandsWithTheirOwnOptionsAndKoreanDescriptions() {
        CommandSpec whitelist = new CommandSpec(
                "whitelist",
                "Change the whitelist",
                null,
                List.of(),
                List.of(new CommandSpec.Subcommand(
                        "add",
                        "Add a player",
                        new CommandSpec.Localized("add", "플레이어를 추가합니다"),
                        List.of(new CommandSpec.Option(
                                CommandSpec.OptionType.STRING, "player", "Player name", null, true, false)))),
                true,
                true,
                false);

        DataObject add =
                SlashCommands.toData(whitelist).toData().getArray("options").getObject(0);

        assertThat(add.getInt("type")).isEqualTo(1);
        assertThat(add.getString("name")).isEqualTo("add");
        assertThat(add.getObject("description_localizations").getString("ko")).isEqualTo("플레이어를 추가합니다");
        assertThat(add.getArray("options").getObject(0).getString("name")).isEqualTo("player");
    }

    @Test
    void hashesTheSameCommandsTheSameWay() {
        String first = new SlashCommands(List.of(KICK, LINK), new TestSupport.MemoryCommandHashes()).hash(1);
        String second = new SlashCommands(List.of(KICK, LINK), new TestSupport.MemoryCommandHashes()).hash(1);

        assertThat(first).isEqualTo(second).hasSize(64);
    }

    @Test
    void hashesDifferentCommandsOrApplicationsDifferently() {
        SlashCommands both = new SlashCommands(List.of(KICK, LINK), new TestSupport.MemoryCommandHashes());
        SlashCommands one = new SlashCommands(List.of(KICK), new TestSupport.MemoryCommandHashes());

        assertThat(both.hash(1)).isNotEqualTo(one.hash(1)).isNotEqualTo(both.hash(2));
    }
}
