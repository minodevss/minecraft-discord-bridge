package com.minecraftdiscordbridge.core.console;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConsoleRedactorTest {
    @Test
    void stripsAnsiColors() {
        assertThat(ConsoleRedactor.redact("\u001B[33;1mWarning\u001B[0m done", List.of()))
                .isEqualTo("Warning done");
    }

    @Test
    void hidesIpAddressesButKeepsTimesAndVersions() {
        assertThat(ConsoleRedactor.redact("Steve[/192.168.0.12:51234] logged in at 12:34:56 on 1.21.1", List.of()))
                .isEqualTo("Steve[/[ip]:51234] logged in at 12:34:56 on 1.21.1");
        assertThat(ConsoleRedactor.redact("from 2001:db8::8a2e:370:7334 and ::1 and fe80:0:0:0:0:0:0:1", List.of()))
                .isEqualTo("from [ip] and [ip] and [ip]");
        assertThat(ConsoleRedactor.redact("Bridge::start and a :: b", List.of()))
                .isEqualTo("Bridge::start and a :: b");
    }

    @Test
    void hidesPasswordCommandArguments() {
        assertThat(ConsoleRedactor.redact("Steve issued server command: /login hunter2", List.of()))
                .isEqualTo("Steve issued server command: /login [hidden]");
        assertThat(ConsoleRedactor.redact("Steve issued server command: /L hunter2", List.of()))
                .isEqualTo("Steve issued server command: /L [hidden]");
        assertThat(ConsoleRedactor.redact("Alex issued server command: /reg pw pw", List.of()))
                .isEqualTo("Alex issued server command: /reg [hidden]");
        assertThat(ConsoleRedactor.redact("Alex issued server command: /changepassword old new", List.of()))
                .isEqualTo("Alex issued server command: /changepassword [hidden]");
        assertThat(ConsoleRedactor.redact("Alex issued server command: /list", List.of()))
                .isEqualTo("Alex issued server command: /list");
    }

    @Test
    void hidesTheTokenSecretsAndWebhookUrls() {
        String token = "MTAwMDAwMDAwMDAwMDAwMDAx.GaBcDe.abcdefghijklmnopqrstuvwxyz0123456789";

        assertThat(ConsoleRedactor.redact("token " + token, List.of())).isEqualTo("token [hidden]");
        assertThat(ConsoleRedactor.redact("short-token used", List.of("short-token")))
                .isEqualTo("[hidden] used");
        assertThat(ConsoleRedactor.redact("posting to https://discord.com/api/webhooks/123/abc-DEF_ghi now", List.of()))
                .isEqualTo("posting to [hidden] now");
        assertThat(ConsoleRedactor.redact("https://canary.discordapp.com/api/v10/webhooks/1/x", List.of()))
                .isEqualTo("[hidden]");
    }
}
