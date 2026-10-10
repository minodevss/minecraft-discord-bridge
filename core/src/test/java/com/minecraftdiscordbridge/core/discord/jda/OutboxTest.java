package com.minecraftdiscordbridge.core.discord.jda;

import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.discord.Author;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordStatus;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OutboxTest {
    private static final DiscordId CHANNEL = new DiscordId(1);
    private static final DiscordId OTHER_CHANNEL = new DiscordId(2);
    private static final Author SERVER = Author.server("Survival", null);
    private static final Author STEVE = Author.player("1", "Steve", null);
    private static final Author ALEX = Author.player("2", "Alex", null);

    private final FakeSender sender = new FakeSender();
    private final Outbox outbox = new Outbox(SERVER, sender, Runnable::run);

    @BeforeEach
    void openTheOutbox() {
        outbox.open();
    }

    @Test
    void holdsMessagesUntilDiscordIsReady() {
        Outbox waiting = new Outbox(SERVER, sender, Runnable::run);
        waiting.enqueue(OutboundMessage.of(CHANNEL, SERVER, "Server started"));

        assertThat(sender.sent).isEmpty();
        waiting.open();
        assertThat(sender.message(0).text()).isEqualTo("Server started");
    }

    @Test
    void holdsNewMessagesWhileTheConnectionIsReplaced() {
        outbox.hold();
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "one"));

        assertThat(sender.sent).isEmpty();
        outbox.open();
        assertThat(sender.sent).hasSize(1);
    }

    @Test
    void sendsTheFirstMessageRightAway() {
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "hi"));

        assertThat(sender.sent).hasSize(1);
        assertThat(sender.message(0)).isEqualTo(new OutboundMessage(CHANNEL, STEVE, "hi", Set.of()));
    }

    @Test
    void keepsOneRequestInFlightPerChannel() {
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "one"));
        outbox.enqueue(OutboundMessage.of(CHANNEL, ALEX, "two"));

        assertThat(sender.sent).hasSize(1);
        sender.complete(0);
        assertThat(sender.sent).hasSize(2);
        assertThat(sender.message(1).text()).isEqualTo("two");
    }

    @Test
    void doesNotHoldBackOtherChannels() {
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "one"));
        outbox.enqueue(OutboundMessage.of(OTHER_CHANNEL, STEVE, "two"));

        assertThat(sender.sent).extracting(sent -> sent.message().channelId()).containsExactly(CHANNEL, OTHER_CHANNEL);
    }

    @Test
    void mergesConsecutiveLinesFromTheSameAuthorWhileWaiting() {
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "one"));
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "two"));
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "three"));

        sender.complete(0);

        assertThat(sender.message(1)).isEqualTo(new OutboundMessage(CHANNEL, STEVE, "two\nthree", Set.of()));
    }

    @Test
    void keepsAuthorsApartWhenOnlyAFewMessagesWait() {
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "one"));
        outbox.enqueue(OutboundMessage.of(CHANNEL, ALEX, "two"));
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "three"));

        sender.complete(0);
        sender.complete(1);

        assertThat(sender.message(1)).isEqualTo(new OutboundMessage(CHANNEL, ALEX, "two", Set.of()));
        assertThat(sender.message(2)).isEqualTo(new OutboundMessage(CHANNEL, STEVE, "three", Set.of()));
    }

    @Test
    void mergesFiveOrMoreWaitingMessagesFromDifferentAuthorsIntoOneServerMessage() {
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "first"));
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "one"));
        outbox.enqueue(OutboundMessage.of(CHANNEL, ALEX, "two"));
        outbox.enqueue(OutboundMessage.of(CHANNEL, SERVER, "Alex joined the game"));
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "three"));
        outbox.enqueue(OutboundMessage.of(CHANNEL, ALEX, "four"));

        sender.complete(0);

        assertThat(sender.message(1).author()).isEqualTo(SERVER);
        assertThat(sender.message(1).text())
                .isEqualTo("**Steve**: one\n**Alex**: two\nAlex joined the game\n**Steve**: three\n**Alex**: four");
        assertThat(sender.sent).hasSize(2);
    }

    @Test
    void escapesMarkdownInNamesOfMergedLines() {
        Author tricky = Author.player("3", "Under_score*", null);
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "first"));
        for (int i = 0; i < 5; i++) {
            outbox.enqueue(OutboundMessage.of(CHANNEL, i % 2 == 0 ? tricky : ALEX, "line"));
        }

        sender.complete(0);

        assertThat(sender.message(1).text()).startsWith("**Under\\_score\\***: line\n**Alex**: line");
    }

    @Test
    void neverSendsMoreThanTwoThousandCharacters() {
        String block = "x".repeat(900);
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "first"));
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, block));
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, block));
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, block));

        sender.complete(0);
        sender.complete(1);

        assertThat(sender.message(1).text()).isEqualTo(block + "\n" + block);
        assertThat(sender.message(2).text()).isEqualTo(block);
    }

    @Test
    void shortensASingleOverlongMessage() {
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "y".repeat(2500)));

        assertThat(sender.message(0).text()).hasSize(2000).endsWith("…");
    }

    @Test
    void dropsTheOldestBeyondTwoHundredAndSaysHowManyWereSkipped() {
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "first"));
        for (int i = 0; i < 205; i++) {
            outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "m" + i));
        }

        assertThat(outbox.snapshot().get(CHANNEL).queued()).isEqualTo(200);
        sender.complete(0);

        assertThat(sender.message(1).author()).isEqualTo(SERVER);
        assertThat(sender.message(1).text()).startsWith("(5 messages skipped)\n**Steve**: m5\n**Steve**: m6");
        assertThat(outbox.snapshot().get(CHANNEL).dropped()).isEqualTo(5);
    }

    @Test
    void countsAFailedSendAsDroppedAndDoesNotRetryIt() {
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "lost"));

        sender.fail(0);

        assertThat(sender.sent).hasSize(1);
        assertThat(outbox.snapshot().get(CHANNEL).dropped()).isEqualTo(1);
        assertThat(outbox.snapshot().get(CHANNEL).inFlight()).isZero();
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "next"));
        assertThat(sender.message(1).text()).isEqualTo("next");
    }

    @Test
    void treatsASenderThatThrowsAsAFailedSend() {
        Outbox throwing = new Outbox(
                SERVER,
                message -> {
                    throw new IllegalStateException("boom");
                },
                Runnable::run);
        throwing.open();

        throwing.enqueue(OutboundMessage.of(CHANNEL, STEVE, "lost"));

        assertThat(throwing.snapshot().get(CHANNEL).dropped()).isEqualTo(1);
    }

    @Test
    void allowsEveryPingOfTheMergedLines() {
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "first"));
        outbox.enqueue(new OutboundMessage(CHANNEL, STEVE, "<@10>", Set.of(new DiscordId(10))));
        outbox.enqueue(new OutboundMessage(CHANNEL, STEVE, "<@11>", Set.of(new DiscordId(11))));

        sender.complete(0);

        assertThat(sender.message(1).pingUserIds()).containsExactlyInAnyOrder(new DiscordId(10), new DiscordId(11));
    }

    @Test
    void reportsQueueDepthAndTheRequestInFlight() {
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "one"));
        outbox.enqueue(OutboundMessage.of(CHANNEL, ALEX, "two"));

        assertThat(outbox.snapshot().get(CHANNEL)).isEqualTo(new DiscordStatus.ChannelStatus(1, 1, 0, 0));
    }

    @Test
    void ignoresBlankText() {
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "  "));

        assertThat(sender.sent).isEmpty();
    }

    @Test
    void sendsNothingAfterClosing() {
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "one"));
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "two"));

        outbox.close();
        sender.complete(0);
        outbox.enqueue(OutboundMessage.of(CHANNEL, STEVE, "three"));

        assertThat(sender.sent).hasSize(1);
    }

    record Sent(OutboundMessage message, CompletableFuture<Void> result) {}

    static final class FakeSender implements MessageSender {
        final List<Sent> sent = new ArrayList<>();

        @Override
        public CompletionStage<Void> send(OutboundMessage message) {
            CompletableFuture<Void> result = new CompletableFuture<>();
            sent.add(new Sent(message, result));
            return result;
        }

        OutboundMessage message(int index) {
            return sent.get(index).message();
        }

        void complete(int index) {
            sent.get(index).result().complete(null);
        }

        void fail(int index) {
            sent.get(index).result().completeExceptionally(new IllegalStateException("timeout"));
        }
    }
}
