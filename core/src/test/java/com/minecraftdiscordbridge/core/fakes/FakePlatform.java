package com.minecraftdiscordbridge.core.fakes;

import com.minecraftdiscordbridge.core.platform.ActionOutcome;
import com.minecraftdiscordbridge.core.platform.Delivery;
import com.minecraftdiscordbridge.core.platform.GameAction;
import com.minecraftdiscordbridge.core.platform.Platform;
import com.minecraftdiscordbridge.core.platform.PlatformInfo;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;

public final class FakePlatform implements Platform {
    public static final int DATA_VERSION = 3955;

    public final BlockingQueue<Delivery> deliveries = new LinkedBlockingQueue<>();
    public final List<GameAction> actions = new CopyOnWriteArrayList<>();
    public volatile PlatformInfo info = new PlatformInfo("Fake 1.0", "1.21.1", DATA_VERSION, true);
    public volatile Function<GameAction, CompletableFuture<ActionOutcome>> outcomes =
            action -> CompletableFuture.completedFuture(new ActionOutcome.Done());
    private final Path dataDirectory;

    public FakePlatform(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    @Override
    public PlatformInfo info() {
        return info;
    }

    @Override
    public Path dataDirectory() {
        return dataDirectory;
    }

    @Override
    public void deliver(Delivery delivery) {
        deliveries.add(delivery);
    }

    @Override
    public CompletableFuture<ActionOutcome> perform(GameAction action) {
        actions.add(action);
        return outcomes.apply(action);
    }
}
