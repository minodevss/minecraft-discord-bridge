package com.minecraftdiscordbridge.core.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PermissionTest {
    @Test
    void namesEachNodeByTheModNamespaceAndItsPath() {
        assertThat(Permission.GATE_BYPASS.node()).isEqualTo("minecraftdiscordbridge.gate.bypass");
        assertThat(Permission.LINK.node()).isEqualTo("minecraftdiscordbridge.command.link");
        for (Permission permission : Permission.values()) {
            assertThat(permission.node()).isEqualTo(Permission.NAMESPACE + "." + permission.path());
        }
    }
}
