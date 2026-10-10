package com.minecraftdiscordbridge.core.discord.jda;

import java.util.Locale;
import mockwebserver3.RecordedRequest;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;

final class DiscordPayloads {
    static final String TOKEN = "test-token";
    static final long APPLICATION_ID = 100000000000000001L;
    static final long BOT_ID = APPLICATION_ID;
    static final long GUILD_ID = 200000000000000002L;
    static final long OTHER_GUILD_ID = 200000000000000099L;
    static final long CHANNEL_ID = 300000000000000003L;
    static final long ROLE_ID = 500000000000000005L;
    static final long BOT_ROLE_ID = 500000000000000055L;
    static final long PLAYER_ID = 400000000000000004L;
    static final long FRIEND_ID = 400000000000000044L;
    static final long OTHER_BOT_ID = 400000000000000444L;
    static final long FOREIGN_WEBHOOK_ID = 700000000000000007L;
    static final int MESSAGE_CONTENT_INTENT = 1 << 15;
    static final String ADMINISTRATOR = "8";
    static final String NO_WEBHOOKS = "68608";
    private static final String TIMESTAMP = "2026-10-10T00:00:00.000000+00:00";

    private DiscordPayloads() {}

    static DataObject body(RecordedRequest request) {
        return DataObject.fromJson(request.getBody().utf8());
    }

    static DataObject application(long flags) {
        DataObject team = DataObject.empty()
                .put("id", "600000000000000006")
                .put("owner_user_id", Long.toString(PLAYER_ID))
                .put(
                        "members",
                        DataArray.empty()
                                .add(teamMember(FRIEND_ID, "developer", 2))
                                .add(teamMember(OTHER_BOT_ID, "read_only", 2)));
        return DataObject.empty()
                .put("id", Long.toString(APPLICATION_ID))
                .put("name", "Bridge")
                .put("flags", flags)
                .put("bot_public", false)
                .put("owner", DataObject.fromJson(user(PLAYER_ID, "Owner", false)))
                .put("team", team);
    }

    static DataObject message(long id, long authorId, String content) {
        return DataObject.empty()
                .put("id", Long.toString(id))
                .put("channel_id", Long.toString(CHANNEL_ID))
                .put("guild_id", Long.toString(GUILD_ID))
                .put("author", DataObject.fromJson(user(authorId, "Author" + authorId, false)))
                .put("content", content)
                .put("timestamp", TIMESTAMP)
                .put("edited_timestamp", null)
                .put("tts", false)
                .put("mention_everyone", false)
                .put("mentions", DataArray.empty())
                .put("mention_roles", DataArray.empty())
                .put("attachments", DataArray.empty())
                .put("embeds", DataArray.empty())
                .put("components", DataArray.empty())
                .put("pinned", false)
                .put("type", 0)
                .put("flags", 0);
    }

    static DataObject command(long id, long guildId, String name, int type, DataArray options) {
        DataObject member = member("Alex")
                .put("user", DataObject.fromJson(user(PLAYER_ID, "Alex", false)))
                .put("permissions", "8");
        return DataObject.empty()
                .put("id", Long.toString(id))
                .put("application_id", Long.toString(APPLICATION_ID))
                .put("type", type)
                .put("token", "interaction-token")
                .put("version", 1)
                .put("guild_id", Long.toString(guildId))
                .put(
                        "guild",
                        DataObject.empty()
                                .put("id", Long.toString(guildId))
                                .put("locale", "en-US")
                                .put("features", DataArray.empty()))
                .put("channel_id", Long.toString(CHANNEL_ID))
                .put(
                        "channel",
                        channel().put("guild_id", Long.toString(guildId)).put("permissions", "8"))
                .put("member", member)
                .put("locale", "ko")
                .put("guild_locale", "en-US")
                .put("app_permissions", "8")
                .put("entitlements", DataArray.empty())
                .put("authorizing_integration_owners", DataObject.empty().put("0", Long.toString(guildId)))
                .put("context", 0)
                .put(
                        "data",
                        DataObject.empty()
                                .put("id", "800000000000000008")
                                .put("name", name)
                                .put("type", 1)
                                .put("options", options));
    }

    static DataObject member(String nick) {
        return DataObject.empty()
                .put("nick", nick)
                .put("roles", DataArray.empty().add(Long.toString(ROLE_ID)))
                .put("joined_at", TIMESTAMP)
                .put("deaf", false)
                .put("mute", false)
                .put("flags", 0);
    }

    static String user(long id, String name, boolean bot) {
        return DataObject.empty()
                .put("id", Long.toString(id))
                .put("username", name.toLowerCase(Locale.ROOT))
                .put("global_name", name)
                .put("discriminator", "0")
                .put("avatar", null)
                .put("bot", bot)
                .put("public_flags", 0)
                .toString();
    }

    static DataObject channel() {
        return DataObject.empty()
                .put("id", Long.toString(CHANNEL_ID))
                .put("type", 0)
                .put("guild_id", Long.toString(GUILD_ID))
                .put("name", "minecraft")
                .put("position", 0)
                .put("permission_overwrites", DataArray.empty())
                .put("topic", null)
                .put("nsfw", false)
                .put("parent_id", null)
                .put("rate_limit_per_user", 0)
                .put("last_message_id", null);
    }

    static DataObject guild(boolean botManagesRoles, String everyonePermissions) {
        DataArray botRoles = botManagesRoles ? DataArray.empty().add(Long.toString(BOT_ROLE_ID)) : DataArray.empty();
        DataArray roles = DataArray.empty()
                .add(role(GUILD_ID, "@everyone", 0, everyonePermissions))
                .add(role(ROLE_ID, "Builders", 0x3498db, "0"));
        if (botManagesRoles) {
            roles.add(role(BOT_ROLE_ID, "Bridge", 0, Long.toString(1L << 28)));
        }
        DataObject bot = DataObject.empty()
                .put("user", DataObject.fromJson(user(BOT_ID, "Bridge", true)))
                .put("roles", botRoles)
                .put("joined_at", TIMESTAMP)
                .put("deaf", false)
                .put("mute", false)
                .put("flags", 0);
        return DataObject.empty()
                .put("id", Long.toString(GUILD_ID))
                .put("name", "Test Server")
                .put("icon", null)
                .put("owner_id", Long.toString(PLAYER_ID))
                .put("unavailable", false)
                .put("member_count", 2)
                .put("large", false)
                .put("roles", roles)
                .put("channels", DataArray.empty().add(channel()))
                .put("threads", DataArray.empty())
                .put("guild_scheduled_events", DataArray.empty())
                .put("emojis", DataArray.empty())
                .put("stickers", DataArray.empty())
                .put("soundboard_sounds", DataArray.empty())
                .put("voice_states", DataArray.empty())
                .put("presences", DataArray.empty())
                .put("features", DataArray.empty())
                .put("members", DataArray.empty().add(bot))
                .put("premium_tier", 0)
                .put("verification_level", 0)
                .put("default_message_notifications", 0)
                .put("explicit_content_filter", 0)
                .put("mfa_level", 0)
                .put("nsfw_level", 0)
                .put("afk_timeout", 300)
                .put("system_channel_flags", 0)
                .put("premium_progress_bar_enabled", false)
                .put("preferred_locale", "en-US");
    }

    private static DataObject role(long id, String name, int color, String permissions) {
        return DataObject.empty()
                .put("id", Long.toString(id))
                .put("name", name)
                .put("color", color)
                .put(
                        "colors",
                        DataObject.empty()
                                .put("primary_color", color)
                                .put("secondary_color", null)
                                .put("tertiary_color", null))
                .put("hoist", false)
                .put("position", id == GUILD_ID ? 0 : id == BOT_ROLE_ID ? 2 : 1)
                .put("permissions", permissions)
                .put("managed", false)
                .put("mentionable", true)
                .put("flags", 0);
    }

    static DataObject webhook(long id) {
        return DataObject.empty()
                .put("id", Long.toString(id))
                .put("type", 1)
                .put("guild_id", Long.toString(GUILD_ID))
                .put("channel_id", Long.toString(CHANNEL_ID))
                .put("name", WebhookSender.WEBHOOK_NAME)
                .put("avatar", null)
                .put("token", "webhook-token-" + id)
                .put("application_id", Long.toString(APPLICATION_ID))
                .put("user", DataObject.fromJson(user(BOT_ID, "Bridge", true)));
    }

    private static DataObject teamMember(long userId, String role, int state) {
        return DataObject.empty()
                .put("membership_state", state)
                .put("team_id", "600000000000000006")
                .put("role", role)
                .put("user", DataObject.fromJson(user(userId, "Member" + userId, false)));
    }
}
