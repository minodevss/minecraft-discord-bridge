package com.minecraftdiscordbridge.modded.common;

import com.minecraftdiscordbridge.core.platform.AdvancementFrame;
import java.util.Optional;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
//? if >=26.1 {
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
//?}
//? if >=1.21 {
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
//?} else
/*import net.minecraft.advancements.Advancement;*/

final class Advancements {
    //? if >=26.1 {
    // 26.3 turned DisplayInfo into a record with renamed accessors, and one 26.x jar runs on both.
    private static final MethodHandle ANNOUNCE_TO_CHAT = accessor(boolean.class, "announceToChat", "shouldAnnounceChat");
    private static final MethodHandle TITLE = accessor(Component.class, "title", "getTitle");
    private static final MethodHandle TYPE = accessor(AdvancementType.class, "type", "getType");
    //?}

    private Advancements() {}

    record Announcement(Component title, AdvancementFrame frame) {}

    //? if >=1.21 {
    static Optional<Announcement> announcement(AdvancementHolder advancement, ServerPlayer player) {
        Optional<DisplayInfo> display = advancement.value().display();
        if (display.isEmpty() || !announcesToChat(display.get()) || !VanillaRules.announceAdvancements(player)) {
            return Optional.empty();
        }
        return Optional.of(new Announcement(title(display.get()), frame(type(display.get()))));
    }
    //?} else {
    /*static Optional<Announcement> announcement(Advancement advancement, ServerPlayer player) {
        DisplayInfo display = advancement.getDisplay();
        if (display == null || !display.shouldAnnounceChat() || !VanillaRules.announceAdvancements(player)) {
            return Optional.empty();
        }
        return Optional.of(new Announcement(display.getTitle(), frame(display.getFrame())));
    }
    *///?}

    private static AdvancementFrame frame(Enum<?> type) {
        return AdvancementFrame.valueOf(type.name());
    }

    //? if >=26.1 {
    private static boolean announcesToChat(DisplayInfo display) {
        return (boolean) invoke(ANNOUNCE_TO_CHAT, display);
    }

    private static Component title(DisplayInfo display) {
        return (Component) invoke(TITLE, display);
    }

    private static AdvancementType type(DisplayInfo display) {
        return (AdvancementType) invoke(TYPE, display);
    }

    private static Object invoke(MethodHandle accessor, DisplayInfo display) {
        try {
            return accessor.invoke(display);
        } catch (Throwable error) {
            throw new IllegalStateException("Could not read advancement display " + accessor, error);
        }
    }

    private static MethodHandle accessor(Class<?> type, String... names) {
        for (String name : names) {
            Optional<MethodHandle> found = find(type, name);
            if (found.isPresent()) {
                return found.get();
            }
        }
        throw new IllegalStateException("DisplayInfo has none of " + Arrays.toString(names));
    }

    private static Optional<MethodHandle> find(Class<?> type, String name) {
        try {
            return Optional.of(
                    MethodHandles.lookup().findVirtual(DisplayInfo.class, name, MethodType.methodType(type)));
        } catch (NoSuchMethodException | IllegalAccessException missing) {
            return Optional.empty();
        }
    }
    //?} else if >=1.21 {
    /*private static boolean announcesToChat(DisplayInfo display) {
        return display.shouldAnnounceChat();
    }

    private static Component title(DisplayInfo display) {
        return display.getTitle();
    }

    private static AdvancementType type(DisplayInfo display) {
        return display.getType();
    }
    *///?}
}
