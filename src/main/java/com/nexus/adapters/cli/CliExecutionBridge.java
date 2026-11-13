package com.nexus.adapters.cli;

import com.nexus.core.capability.CapabilityResult;
import com.nexus.core.capability.ExecutionContext;
import com.nexus.core.capability.ExecutionEngine;
import com.nexus.core.capability.InvocationSource;
import com.nexus.core.security.Subject;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.time.Duration;

/**
 * Bridge between Picocli command invocations and the core ExecutionEngine.
 */
@Component
public class CliExecutionBridge {

    private final ExecutionEngine executionEngine;
    private final OutputFormatter outputFormatter;

    public CliExecutionBridge(ExecutionEngine executionEngine, OutputFormatter outputFormatter) {
        this.executionEngine = executionEngine;
        this.outputFormatter = outputFormatter;
    }

    public <I, O> int execute(String capabilityId, I input, boolean json, PrintWriter out, PrintWriter err) {
        ExecutionContext context = ExecutionContext.create(
                InvocationSource.CLI,
                Subject.localCli(),
                Duration.ofSeconds(30)
        );

        CapabilityResult<O> result = executionEngine.execute(capabilityId, context, input);
        outputFormatter.render(result, json, out, err);

        return result.isSuccess() ? 0 : 1;
    }
}
