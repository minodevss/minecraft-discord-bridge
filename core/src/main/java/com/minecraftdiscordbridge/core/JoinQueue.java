package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.access.JoinGate;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.i18n.Language;
import com.minecraftdiscordbridge.core.platform.JoinDecision;
import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import com.minecraftdiscordbridge.core.text.TextRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class JoinQueue {
    private record Waiting(PlayerProfile player, boolean bypass, CompletableFuture<JoinDecision> result) {}

    private final Supplier<Optional<JoinGate>> gate;
    private final int dataVersion;
    private final List<Waiting> waiting = new ArrayList<>();
    private volatile boolean booting = true;

    JoinQueue(Supplier<Optional<JoinGate>> gate, int dataVersion) {
        this.gate = gate;
        this.dataVersion = dataVersion;
    }

    void check(PlayerProfile player, boolean bypass, CompletableFuture<JoinDecision> result) {
        Optional<JoinGate> current = gate.get();
        if (current.isPresent()) {
            current.get().decide(player, bypass, result);
        } else if (booting) {
            waiting.add(new Waiting(player, bypass, result));
        } else {
            result.complete(new JoinDecision.Allow());
        }
    }

    JoinDecision fallback(PlayerProfile player, boolean bypass) {
        Optional<JoinGate> current = gate.get();
        if (current.isPresent()) {
            return current.get().fallback(player, bypass);
        }
        if (booting && !bypass) {
            Lang english = Lang.of(Language.EN);
            return new JoinDecision.Deny(TextRenderer.toJson(english.component("game.gate.starting"), dataVersion));
        }
        return new JoinDecision.Allow();
    }

    void release() {
        booting = false;
        List<Waiting> ready = List.copyOf(waiting);
        waiting.clear();
        ready.forEach(join -> check(join.player(), join.bypass(), join.result()));
    }
}
