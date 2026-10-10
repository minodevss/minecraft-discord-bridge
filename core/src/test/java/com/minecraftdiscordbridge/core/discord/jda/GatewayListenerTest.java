package com.minecraftdiscordbridge.core.discord.jda;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.Test;

class GatewayListenerTest {
    private final Generations generations = new Generations();
    private final List<Integer> handled = new ArrayList<>();

    @Test
    void passesOnEventsFromTheCurrentConnection() {
        int current = generations.next();

        new GatewayListener(current, generations, (generation, event) -> handled.add(generation))
                .onEvent(new BareEvent());

        assertThat(handled).containsExactly(current);
    }

    @Test
    void dropsEventsFromAReplacedConnection() {
        int stale = generations.next();
        GatewayListener listener =
                new GatewayListener(stale, generations, (generation, event) -> handled.add(generation));
        generations.next();

        listener.onEvent(new BareEvent());

        assertThat(handled).isEmpty();
    }

    @Test
    void dropsEventsAfterShutdown() {
        int current = generations.next();
        GatewayListener listener =
                new GatewayListener(current, generations, (generation, event) -> handled.add(generation));
        generations.retireAll();

        listener.onEvent(new BareEvent());

        assertThat(handled).isEmpty();
    }

    private static final class BareEvent implements GenericEvent {
        @Override
        public JDA getJDA() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getResponseNumber() {
            return 0;
        }

        @Override
        public DataObject getRawData() {
            return DataObject.empty();
        }
    }
}
