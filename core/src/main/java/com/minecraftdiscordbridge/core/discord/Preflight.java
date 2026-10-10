package com.minecraftdiscordbridge.core.discord;

import java.io.IOException;
import net.dv8tion.jda.api.JDAInfo;
import net.dv8tion.jda.api.utils.data.DataObject;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Preflight {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");
    private static final MediaType JSON = MediaType.get("application/json");
    private static final String USER_AGENT = "DiscordBot (" + JDAInfo.GITHUB + ", " + JDAInfo.VERSION + ")";

    private final OkHttpClient http;
    private final HttpUrl application;

    Preflight(OkHttpClient http, String restBaseUrl) {
        this.http = http;
        this.application = HttpUrl.get(restBaseUrl + "applications/@me");
    }

    PreflightResult run(String token, boolean enableMessageContent) {
        Request request;
        try {
            request = authorized(token).get().build();
        } catch (IllegalArgumentException tokenNotAHeader) {
            return new PreflightResult.InvalidToken();
        }
        try (Response response = http.newCall(request).execute()) {
            if (response.code() == 401) {
                return new PreflightResult.InvalidToken();
            }
            if (!response.isSuccessful()) {
                return new PreflightResult.Unreachable("Discord answered HTTP " + response.code());
            }
            ApplicationInfo info =
                    ApplicationInfo.parse(DataObject.fromJson(response.body().string()));
            if (info.messageContentGranted() || !enableMessageContent) {
                return new PreflightResult.Ready(info);
            }
            return new PreflightResult.Ready(enableMessageContent(token, info));
        } catch (IOException | RuntimeException failure) {
            return new PreflightResult.Unreachable(Failures.describe(failure));
        }
    }

    private ApplicationInfo enableMessageContent(String token, ApplicationInfo info) throws IOException {
        long flags = info.flags() | ApplicationInfo.GATEWAY_MESSAGE_CONTENT_LIMITED;
        String body = DataObject.empty().put("flags", flags).toString();
        Request request =
                authorized(token).patch(RequestBody.create(body, JSON)).build();
        try (Response response = http.newCall(request).execute()) {
            if (response.isSuccessful()) {
                LOGGER.info("Turned on the Message Content intent for the bot so Discord messages reach Minecraft");
                return info.withFlags(flags);
            }
            LOGGER.warn(
                    "Could not turn on the Message Content intent (HTTP {}). Turn on Message Content Intent at {} and run"
                            + " /discord reload",
                    response.code(),
                    info.botSettingsUrl());
            return info;
        } catch (IOException failure) {
            LOGGER.warn(
                    "Could not turn on the Message Content intent ({}). Turn on Message Content Intent at {} and run"
                            + " /discord reload",
                    Failures.describe(failure),
                    info.botSettingsUrl());
            return info;
        }
    }

    private Request.Builder authorized(String token) {
        return new Request.Builder()
                .url(application)
                .header("Authorization", "Bot " + token)
                .header("User-Agent", USER_AGENT);
    }
}
