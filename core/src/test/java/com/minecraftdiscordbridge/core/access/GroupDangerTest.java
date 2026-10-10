package com.minecraftdiscordbridge.core.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GroupDangerTest {
    @Test
    void findsWildcardAndOperatorNodes() {
        assertThat(GroupDanger.find(Map.of("*", true), node -> false)).contains("*");
        assertThat(GroupDanger.find(Map.of("minecraft.command.op", true), node -> false))
                .contains("minecraft.command.op");
        assertThat(GroupDanger.find(Map.of("LuckPerms.*", true), node -> false)).contains("LuckPerms.*");
        assertThat(GroupDanger.find(Map.of("luckperms.user.parent.add", true), node -> false))
                .contains("luckperms.user.parent.add");
    }

    @Test
    void findsOperatorPowerGrantedThroughWildcards() {
        assertThat(GroupDanger.find(Map.of("minecraft.command.*", false), "minecraft.command.op"::equals))
                .contains("minecraft.command.op");
    }

    @Test
    void acceptsOrdinaryGroups() {
        assertThat(GroupDanger.find(Map.of("essentials.fly", true, "*", false), node -> false))
                .isEmpty();
    }
}
