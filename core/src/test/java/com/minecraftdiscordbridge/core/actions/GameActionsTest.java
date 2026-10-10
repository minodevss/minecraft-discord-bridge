package com.minecraftdiscordbridge.core.actions;

import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.fakes.FakePlatform;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.i18n.Language;
import com.minecraftdiscordbridge.core.platform.ActionOutcome;
import com.minecraftdiscordbridge.core.platform.GameAction;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameActionsTest {
    private static final GameAction KICK = new GameAction.Kick("Steve", "spam");
    private static final Lang ENGLISH = Lang.of(Language.EN);
    private static final Lang KOREAN = Lang.of(Language.KO);

    @TempDir
    Path directory;

    @Test
    void phrasesOutcomesThePlatformCannotPutIntoWordsInTheUsersLanguage() {
        assertThat(run(new ActionOutcome.Unsupported(), ENGLISH))
                .isEqualTo(new ActionOutcome.Failed("this server cannot do that"));
        assertThat(run(new ActionOutcome.UnknownCommand(), ENGLISH))
                .isEqualTo(new ActionOutcome.Failed("the server has no such command"));
        assertThat(run(new ActionOutcome.ServerStopping(), ENGLISH))
                .isEqualTo(new ActionOutcome.Failed("the Minecraft server is stopping"));
        assertThat(run(new ActionOutcome.ServerStopping(), KOREAN))
                .isEqualTo(new ActionOutcome.Failed("마인크래프트 서버가 종료되는 중입니다"));
    }

    @Test
    void passesOtherOutcomesThrough() {
        assertThat(run(new ActionOutcome.Done(), ENGLISH)).isEqualTo(new ActionOutcome.Done());
        assertThat(run(new ActionOutcome.Output("Done"), ENGLISH)).isEqualTo(new ActionOutcome.Output("Done"));
        assertThat(run(new ActionOutcome.Failed("locked"), ENGLISH)).isEqualTo(new ActionOutcome.Failed("locked"));
    }

    private ActionOutcome run(ActionOutcome given, Lang lang) {
        return run(CompletableFuture.completedFuture(given), lang);
    }

    private ActionOutcome run(CompletableFuture<ActionOutcome> given, Lang lang) {
        FakePlatform platform = new FakePlatform(directory);
        platform.outcomes = action -> given;
        AtomicReference<ActionOutcome> answer = new AtomicReference<>();

        new GameActions(platform, Runnable::run, Duration.ofSeconds(5)).run(KICK, lang, answer::set);

        assertThat(answer.get()).isNotNull();
        return answer.get();
    }
}
