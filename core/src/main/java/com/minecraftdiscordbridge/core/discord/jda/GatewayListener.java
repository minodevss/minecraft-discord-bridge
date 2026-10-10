package com.minecraftdiscordbridge.core.discord.jda;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.EventListener;

final class GatewayListener implements EventListener {
    interface Handler {
        void handle(int generation, GenericEvent event);
    }

    private final int generation;
    private final Generations generations;
    private final Handler handler;

    GatewayListener(int generation, Generations generations, Handler handler) {
        this.generation = generation;
        this.generations = generations;
        this.handler = handler;
    }

    @Override
    public void onEvent(GenericEvent event) {
        if (generations.isCurrent(generation)) {
            handler.handle(generation, event);
        }
    }
}
