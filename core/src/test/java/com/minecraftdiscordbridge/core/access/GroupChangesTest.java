package com.minecraftdiscordbridge.core.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GroupChangesTest {
    private static final UUID STEVE = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private final List<UUID> changed = new ArrayList<>();
    private final GroupChanges changes = new GroupChanges(changed::add);

    @Test
    void ignoresRecalculationsThatKeepTheSameGroups() {
        changes.recalculated(STEVE, Set.of("default"));
        changes.recalculated(STEVE, Set.of("default"));

        assertThat(changed).isEmpty();
    }

    @Test
    void reportsARecalculationThatChangesTheGroups() {
        changes.recalculated(STEVE, Set.of("default"));
        changes.recalculated(STEVE, Set.of("default", "vip"));

        assertThat(changed).containsExactly(STEVE);
    }

    @Test
    void startsOverAfterThePlayerIsUnloaded() {
        changes.recalculated(STEVE, Set.of("default"));
        changes.unloaded(STEVE);
        changes.recalculated(STEVE, Set.of("vip"));

        assertThat(changed).isEmpty();
    }

    @Test
    void reportsEveryEditOfGroupMembership() {
        changes.edited(STEVE);

        assertThat(changed).containsExactly(STEVE);
    }
}
