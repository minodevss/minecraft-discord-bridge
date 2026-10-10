package com.minecraftdiscordbridge.core.access;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

final class GroupDanger {
    static final List<String> PROBES =
            List.of("minecraft.command.op", "minecraft.command.deop", "luckperms.user.parent.add", "luckperms.editor");
    private static final List<String> EXACT =
            List.of("*", "minecraft.*", "minecraft.command.*", "minecraft.command.op");

    private GroupDanger() {}

    static Optional<String> find(Map<String, Boolean> permissions, Predicate<String> grants) {
        for (Map.Entry<String, Boolean> entry : permissions.entrySet()) {
            String node = entry.getKey().toLowerCase(Locale.ROOT);
            if (Boolean.TRUE.equals(entry.getValue()) && (EXACT.contains(node) || node.startsWith("luckperms."))) {
                return Optional.of(entry.getKey());
            }
        }
        return PROBES.stream().filter(grants).findFirst();
    }
}
