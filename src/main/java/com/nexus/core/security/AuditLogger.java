package com.nexus.core.security;

import com.nexus.core.capability.CapabilityResult;
import com.nexus.core.capability.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Audit logger providing structured, tamper-evident records of all capability executions.
 */
@Component
public class AuditLogger {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUDIT");

    public void logExecution(ExecutionContext context, CapabilityResult<?> result) {
        String subjectId = context.caller() != null ? context.caller().id() : "unknown";
        String subjectName = context.caller() != null ? context.caller().username() : "unknown";
        String source = context.source() != null ? context.source().name() : "unknown";
        String status = result.status() != null ? result.status().name() : "UNKNOWN";
        String capabilityId = result.capabilityId();
        long duration = result.durationMs();

        if (result.status() == com.nexus.core.capability.ResultStatus.SUCCESS) {
            AUDIT_LOG.info("AUDIT_SUCCESS invocationId={} source={} subjectId={} username={} capability={} durationMs={}",
                    context.invocationId(), source, subjectId, subjectName, capabilityId, duration);
        } else {
            String errorCode = result.error() != null && result.error().code() != null ? result.error().code().getCode() : "NONE";
            String errorMsg = result.error() != null ? result.error().message() : "none";
            AUDIT_LOG.warn("AUDIT_FAILURE invocationId={} source={} subjectId={} username={} capability={} status={} errorCode={} message='{}' durationMs={}",
                    context.invocationId(), source, subjectId, subjectName, capabilityId, status, errorCode, errorMsg, duration);
        }
    }
}
