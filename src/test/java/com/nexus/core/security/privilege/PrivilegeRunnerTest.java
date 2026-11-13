package com.nexus.core.security.privilege;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrivilegeRunnerTest {

    private PrivilegeRunner runner;

    @BeforeEach
    void setup() {
        runner = new PrivilegeRunner();
    }

    @Test
    @DisplayName("Blocks non-whitelisted binary execution")
    void testForbiddenBinary() {
        assertThatThrownBy(() -> runner.executeWhitelisted("/bin/bash", List.of("-c", "whoami"), 5))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Binary execution forbidden: /bin/bash");
    }

    @Test
    @DisplayName("Blocks shell metacharacters and injection attempts")
    void testCommandInjectionProtection() {
        assertThatThrownBy(() -> runner.executeWhitelisted("/usr/sbin/smartctl", List.of("-a", "/dev/sda; rm -rf /"), 5))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Command argument contains unsafe shell characters");

        assertThatThrownBy(() -> runner.executeWhitelisted("/usr/sbin/smartctl", List.of("-a", "/dev/sda && id"), 5))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Command argument contains unsafe shell characters");
    }
}
