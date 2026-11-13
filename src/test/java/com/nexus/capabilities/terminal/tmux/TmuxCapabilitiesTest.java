package com.nexus.capabilities.terminal.tmux;

import com.nexus.capabilities.terminal.tmux.dto.*;
import com.nexus.core.capability.CapabilityResult;
import com.nexus.core.capability.ExecutionContext;
import com.nexus.core.capability.InvocationSource;
import com.nexus.core.security.Permission;
import com.nexus.core.security.Role;
import com.nexus.core.security.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TmuxCapabilitiesTest {

    private ExecutionContext context;

    @BeforeEach
    void setup() {
        Subject subject = Subject.of("op-1", "operator", Role.OPERATOR);
        context = ExecutionContext.create(InvocationSource.CLI, subject, Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("TmuxListSessionsCapability parses session format string correctly")
    void testParseTmuxSessions() {
        TmuxListSessionsCapability capability = new TmuxListSessionsCapability();
        assertThat(capability.getId()).isEqualTo("tmux.session.list");
        assertThat(capability.getRequiredPermissions()).contains(Permission.READ_PROCESSES);

        String sampleOutput = """
        dev-session|3|1731500000|1|/home/user/workspace
        build-job|1|1731500500|0|/tmp/build
        """;

        List<TmuxSessionInfo> sessions = capability.parseSessionsOutput(sampleOutput);
        assertThat(sessions).hasSize(2);

        TmuxSessionInfo first = sessions.get(0);
        assertThat(first.name()).isEqualTo("dev-session");
        assertThat(first.windows()).isEqualTo(3);
        assertThat(first.attached()).isTrue();
        assertThat(first.workingDirectory()).isEqualTo("/home/user/workspace");

        TmuxSessionInfo second = sessions.get(1);
        assertThat(second.name()).isEqualTo("build-job");
        assertThat(second.windows()).isEqualTo(1);
        assertThat(second.attached()).isFalse();
        assertThat(second.workingDirectory()).isEqualTo("/tmp/build");
    }

    @Test
    @DisplayName("TmuxListSessionsCapability handles empty or absent sessions gracefully")
    void testParseEmptySessions() {
        TmuxListSessionsCapability capability = new TmuxListSessionsCapability();
        List<TmuxSessionInfo> list1 = capability.parseSessionsOutput("");
        assertThat(list1).isEmpty();

        List<TmuxSessionInfo> list2 = capability.parseSessionsOutput("   \n\n");
        assertThat(list2).isEmpty();
    }

    @Test
    @DisplayName("TmuxNewSessionCapability validates session name")
    void testNewSessionValidation() {
        TmuxNewSessionCapability capability = new TmuxNewSessionCapability();
        assertThat(capability.getId()).isEqualTo("tmux.session.new");
        assertThat(capability.getRequiredPermissions()).contains(Permission.WRITE_PROCESSES);

        TmuxNewSessionRequest invalidReq = new TmuxNewSessionRequest("bad session; rm -rf /", null, null, null);
        CapabilityResult<TmuxOperationResult> result = capability.execute(context, invalidReq);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error().message()).contains("Invalid session name");
    }

    @Test
    @DisplayName("TmuxKillSessionCapability validates required parameters")
    void testKillSessionValidation() {
        TmuxKillSessionCapability capability = new TmuxKillSessionCapability();
        assertThat(capability.getId()).isEqualTo("tmux.session.kill");
        assertThat(capability.getRequiredPermissions()).contains(Permission.WRITE_PROCESSES);

        // Null request
        CapabilityResult<TmuxOperationResult> res1 = capability.execute(context, null);
        assertThat(res1.isSuccess()).isFalse();

        // Empty session name with killAll=false
        TmuxKillSessionRequest emptyReq = new TmuxKillSessionRequest("", false);
        CapabilityResult<TmuxOperationResult> res2 = capability.execute(context, emptyReq);
        assertThat(res2.isSuccess()).isFalse();

        // Invalid characters
        TmuxKillSessionRequest badReq = new TmuxKillSessionRequest("name with spaces", false);
        CapabilityResult<TmuxOperationResult> res3 = capability.execute(context, badReq);
        assertThat(res3.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("TmuxAttachSessionCapability validates session input")
    void testAttachSessionValidation() {
        TmuxAttachSessionCapability capability = new TmuxAttachSessionCapability();
        assertThat(capability.getId()).isEqualTo("tmux.session.attach");
        assertThat(capability.getRequiredPermissions()).contains(Permission.READ_PROCESSES);

        CapabilityResult<TmuxOperationResult> res1 = capability.execute(context, null);
        assertThat(res1.isSuccess()).isFalse();

        TmuxAttachSessionRequest invalidReq = new TmuxAttachSessionRequest("bad name$");
        CapabilityResult<TmuxOperationResult> res2 = capability.execute(context, invalidReq);
        assertThat(res2.isSuccess()).isFalse();
    }
}
