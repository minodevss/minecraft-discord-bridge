package com.minecraftdiscordbridge.core.discord.jda;

import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.discord.CommandSpec;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.Test;

class CommandRegistrarTest {
    private static final CommandSpec KICK = new CommandSpec(
            "kick",
            "Kick a player",
            Optional.of("플레이어를 추방합니다"),
            List.of(new CommandSpec.Option(
                    CommandSpec.OptionType.STRING, "player", "Player name", Optional.of("플레이어 이름"), true, true)),
            CommandSpec.Access.ADMINS,
            false,
            true);
    private static final CommandSpec LINK = new CommandSpec(
            "link", "Link your account", Optional.empty(), List.of(), CommandSpec.Access.EVERYONE, false, true);

    @Test
    void limitsAdminCommandsToAdministratorsByDefault() {
        DataObject kick = CommandRegistrar.toData(KICK).toData();
        DataObject link = CommandRegistrar.toData(LINK).toData();

        assertThat(kick.getString("default_member_permissions")).isEqualTo("0");
        assertThat(link.isNull("default_member_permissions")).isTrue();
    }

    @Test
    void limitsManageServerCommandsToThatPermissionByDefault() {
        CommandSpec setup = new CommandSpec(
                "setup", "Set up", Optional.empty(), List.of(), CommandSpec.Access.MANAGE_SERVER, true, true);

        assertThat(CommandRegistrar.toData(setup).toData().getString("default_member_permissions"))
                .isEqualTo(Long.toString(Permission.MANAGE_SERVER.getRawValue()));
    }

    @Test
    void offersCommandsOnlyInsideServers() {
        DataObject kick = CommandRegistrar.toData(KICK).toData();

        assertThat(kick.getArray("contexts").length()).isEqualTo(1);
        assertThat(kick.getArray("contexts").getInt(0)).isZero();
    }

    @Test
    void carriesKoreanDescriptionsButKeepsTheEnglishNames() {
        DataObject kick = CommandRegistrar.toData(KICK).toData();
        DataObject option = kick.getArray("options").getObject(0);

        assertThat(kick.getObject("name_localizations").keys()).isEmpty();
        assertThat(kick.getObject("description_localizations").getString("ko")).isEqualTo("플레이어를 추방합니다");
        assertThat(option.getObject("name_localizations").keys()).isEmpty();
        assertThat(option.getObject("description_localizations").getString("ko"))
                .isEqualTo("플레이어 이름");
        assertThat(option.getBoolean("autocomplete")).isTrue();
        assertThat(option.getBoolean("required")).isTrue();
    }

    @Test
    void carriesFixedChoicesWithKoreanNames() {
        CommandSpec setup = new CommandSpec(
                "setup",
                "Set up",
                Optional.empty(),
                List.of(new CommandSpec.Option(
                        CommandSpec.OptionType.STRING,
                        "as",
                        "What this channel shows",
                        Optional.empty(),
                        false,
                        false,
                        List.of(new CommandSpec.Choice("events", "events", Optional.of("게임 소식"))))),
                CommandSpec.Access.MANAGE_SERVER,
                true,
                true);

        DataObject option =
                CommandRegistrar.toData(setup).toData().getArray("options").getObject(0);
        DataObject choice = option.getArray("choices").getObject(0);

        assertThat(option.getBoolean("autocomplete")).isFalse();
        assertThat(choice.getString("name")).isEqualTo("events");
        assertThat(choice.getString("value")).isEqualTo("events");
        assertThat(choice.getObject("name_localizations").getString("ko")).isEqualTo("게임 소식");
    }

    @Test
    void carriesSubcommandsWithTheirOwnOptionsAndKoreanDescriptions() {
        CommandSpec whitelist = new CommandSpec(
                "whitelist",
                "Change the whitelist",
                Optional.empty(),
                List.of(),
                List.of(new CommandSpec.Subcommand(
                        "add",
                        "Add a player",
                        Optional.of("플레이어를 추가합니다"),
                        List.of(new CommandSpec.Option(
                                CommandSpec.OptionType.STRING,
                                "player",
                                "Player name",
                                Optional.empty(),
                                true,
                                false)))),
                CommandSpec.Access.ADMINS,
                true,
                false);

        DataObject add =
                CommandRegistrar.toData(whitelist).toData().getArray("options").getObject(0);

        assertThat(add.getInt("type")).isEqualTo(1);
        assertThat(add.getString("name")).isEqualTo("add");
        assertThat(add.getObject("description_localizations").getString("ko")).isEqualTo("플레이어를 추가합니다");
        assertThat(add.getArray("options").getObject(0).getString("name")).isEqualTo("player");
    }

    @Test
    void hashesTheSameCommandsTheSameWay() {
        String first = new CommandRegistrar(List.of(KICK, LINK), new TestSupport.MemoryCommandHashes()).hash(1);
        String second = new CommandRegistrar(List.of(KICK, LINK), new TestSupport.MemoryCommandHashes()).hash(1);

        assertThat(first).isEqualTo(second).hasSize(64);
    }

    @Test
    void hashesDifferentCommandsOrApplicationsDifferently() {
        CommandRegistrar both = new CommandRegistrar(List.of(KICK, LINK), new TestSupport.MemoryCommandHashes());
        CommandRegistrar one = new CommandRegistrar(List.of(KICK), new TestSupport.MemoryCommandHashes());

        assertThat(both.hash(1)).isNotEqualTo(one.hash(1)).isNotEqualTo(both.hash(2));
    }
}
