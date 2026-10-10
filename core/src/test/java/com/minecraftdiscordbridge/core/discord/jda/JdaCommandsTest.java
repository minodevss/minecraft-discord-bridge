package com.minecraftdiscordbridge.core.discord.jda;

import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.GUILD_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.OTHER_GUILD_ID;
import static com.minecraftdiscordbridge.core.fakes.Waits.stays;
import static com.minecraftdiscordbridge.core.fakes.Waits.take;
import static com.minecraftdiscordbridge.core.fakes.Waits.until;
import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.discord.AutocompleteReply;
import com.minecraftdiscordbridge.core.discord.AutocompleteRequest;
import com.minecraftdiscordbridge.core.discord.CommandRequest;
import com.minecraftdiscordbridge.core.discord.CommandSpec;
import com.minecraftdiscordbridge.core.discord.DiscordStatus.State;
import com.minecraftdiscordbridge.core.loop.Deadline;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.Test;

class JdaCommandsTest extends JdaTestBase {
    @Test
    void registersSlashCommandsOnlyWhenTheyChange() throws Exception {
        bridge = start(TestSupport.settings(List.of(LIST, WHITELIST)));
        awaitState(State.READY);
        until("registration", () -> registrations().size() == 1);

        DataArray commands = DataArray.fromJson(registrations().get(0).getBody().utf8());
        DataObject list = commands.getObject(0);
        DataObject whitelist = commands.getObject(1);
        assertThat(list.getObject("description_localizations").getString("ko")).isEqualTo("접속 중인 플레이어");
        assertThat(list.isNull("default_member_permissions")).isTrue();
        assertThat(list.getArray("contexts").getInt(0)).isZero();
        assertThat(whitelist.getString("default_member_permissions")).isEqualTo("0");
        assertThat(whitelist.getArray("contexts").length()).isEqualTo(1);
        assertThat(whitelist.getArray("options").getObject(0).getBoolean("autocomplete"))
                .isTrue();
        until("hash saved", () -> commandHashes.stored.containsKey(GUILD));

        bridge.shutdown(List.of(), Deadline.after(SHUTDOWN_BUDGET));
        bridge = start(TestSupport.settings(List.of(LIST, WHITELIST)));
        until(
                "second ready",
                () -> discord.identifies.size() == 2 && bridge.status().state() == State.READY);

        stays("one registration", () -> registrations().size() == 1);
    }

    @Test
    void refusesCommandsFromServersItIsNotSetUpFor() {
        bridge = start(TestSupport.settings(List.of(LIST)));
        awaitState(State.READY);

        discord.dispatch("INTERACTION_CREATE", discord.command(OTHER_GUILD_ID, "list", 2, DataArray.empty()));

        until("refusal", () -> callbacks().size() == 1);
        DataObject callback = DiscordPayloads.body(callbacks().get(0));
        assertThat(callback.getInt("type")).isEqualTo(4);
        assertThat(callback.getObject("data").getString("content"))
                .isEqualTo("This bot is not set up for this server.");
        assertThat(callback.getObject("data").getInt("flags") & 64).isNotZero();
        assertThat(listener.commands).isEmpty();
    }

    @Test
    void handsCommandsToTheCoreWithAReplyHandle() {
        bridge = start(TestSupport.settings(List.of(LIST)));
        awaitState(State.READY);
        DataArray options = DataArray.empty()
                .add(DataObject.empty().put("name", "page").put("type", 4).put("value", 2));

        discord.dispatch("INTERACTION_CREATE", discord.command(GUILD_ID, "list", 2, options));

        CommandRequest request = take(listener.commands);
        assertThat(request.command()).isEqualTo("list");
        assertThat(request.options()).containsEntry("page", "2");
        assertThat(request.userId()).isEqualTo(PLAYER);
        assertThat(request.userDisplayName()).isEqualTo("Alex");
        assertThat(request.locale()).isEqualTo("ko");
        assertThat(request.guildId()).isEqualTo(GUILD);
        assertThat(request.channelId()).isEqualTo(CHANNEL);
        assertThat(callbacks()).isEmpty();

        take(listener.replies).replyEphemeral("3 players online");

        until("reply", () -> callbacks().size() == 1);
        DataObject callback = DiscordPayloads.body(callbacks().get(0));
        assertThat(callback.getInt("type")).isEqualTo(4);
        assertThat(callback.getObject("data").getString("content")).isEqualTo("3 players online");
        assertThat(callback.getObject("data").getInt("flags") & 64).isNotZero();
    }

    @Test
    void defersSlowCommandsBeforeTheCoreAnswers() {
        bridge = start(TestSupport.settings(List.of(WHITELIST)));
        awaitState(State.READY);
        DataArray options = DataArray.empty()
                .add(DataObject.empty().put("name", "player").put("type", 3).put("value", "Steve"));

        discord.dispatch("INTERACTION_CREATE", discord.command(GUILD_ID, "whitelist", 2, options));

        until("deferral", () -> callbacks().size() == 1);
        assertThat(DiscordPayloads.body(callbacks().get(0)).getInt("type")).isEqualTo(5);

        take(listener.replies).reply("Added Steve");

        until("edited reply", () -> originalEdits().size() == 1);
        DataObject edit = DiscordPayloads.body(originalEdits().get(0));
        assertThat(edit.getString("content")).isEqualTo("Added Steve");
        assertThat(edit.getObject("allowed_mentions").getArray("parse").length())
                .isZero();
    }

    @Test
    void answersAutocompleteWithAtMostTwentyFiveChoices() {
        bridge = start(TestSupport.settings(List.of(WHITELIST)));
        awaitState(State.READY);
        DataArray options = DataArray.empty()
                .add(DataObject.empty()
                        .put("name", "player")
                        .put("type", 3)
                        .put("value", "St")
                        .put("focused", true));

        discord.dispatch("INTERACTION_CREATE", discord.command(GUILD_ID, "whitelist", 4, options));

        AutocompleteRequest request = take(listener.autocompletes);
        assertThat(request.option()).isEqualTo("player");
        assertThat(request.typed()).isEqualTo("St");
        List<AutocompleteReply.Choice> choices = IntStream.range(0, 30)
                .mapToObj(i -> new AutocompleteReply.Choice("Steve" + i, "Steve" + i))
                .toList();
        take(listener.autocompleteReplies).respond(choices);

        until("choices", () -> callbacks().size() == 1);
        DataObject callback = DiscordPayloads.body(callbacks().get(0));
        assertThat(callback.getInt("type")).isEqualTo(8);
        assertThat(callback.getObject("data").getArray("choices").length()).isEqualTo(25);
    }

    @Test
    void registersCommandsInEveryServerItIsInUntilAChannelIsChosen() {
        bridge = start(unbound(Set.of(), List.of(LIST)));
        awaitState(State.READY);
        until("registration", () -> registrations().size() == 1);

        discord.dispatch("INTERACTION_CREATE", discord.command(GUILD_ID, "list", 2, DataArray.empty()));

        assertThat(take(listener.commands).guildId()).isEqualTo(GUILD);
    }

    @Test
    void passesTheSubcommandAndWhetherTheMemberCanManageTheServer() {
        CommandSpec whitelist = new CommandSpec(
                "whitelist",
                "Change the whitelist",
                Optional.empty(),
                List.of(),
                List.of(new CommandSpec.Subcommand(
                        "add",
                        "Add a player",
                        Optional.empty(),
                        List.of(new CommandSpec.Option(
                                CommandSpec.OptionType.STRING, "player", "Player", Optional.empty(), true, false)))),
                true,
                false,
                false);
        bridge = start(TestSupport.settings(List.of(whitelist)));
        awaitState(State.READY);
        DataArray options = DataArray.empty()
                .add(DataObject.empty()
                        .put("name", "add")
                        .put("type", 1)
                        .put(
                                "options",
                                DataArray.empty()
                                        .add(DataObject.empty()
                                                .put("name", "player")
                                                .put("type", 3)
                                                .put("value", "Steve"))));

        discord.dispatch("INTERACTION_CREATE", discord.command(GUILD_ID, "whitelist", 2, options));

        CommandRequest request = take(listener.commands);
        assertThat(request.subcommand()).contains("add");
        assertThat(request.options()).containsEntry("player", "Steve");
        assertThat(request.canManageServer()).isTrue();
    }
}
