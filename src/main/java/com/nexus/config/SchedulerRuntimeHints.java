package com.nexus.config;

import com.nexus.capabilities.network.telegram.dto.TelegramRecipientResult;
import com.nexus.capabilities.network.telegram.dto.TelegramSendRequest;
import com.nexus.capabilities.network.telegram.dto.TelegramSendResult;
import com.nexus.capabilities.network.telegram.dto.TelegramSendType;
import com.nexus.capabilities.scheduler.timer.dto.*;
import com.nexus.capabilities.system.audit.dto.AuditCheckItem;
import com.nexus.capabilities.system.audit.dto.AuditSeverity;
import com.nexus.capabilities.system.audit.dto.SystemAuditReport;
import com.nexus.capabilities.system.package_mgr.dto.PackageOperationRequest;
import com.nexus.capabilities.system.package_mgr.dto.PackageOperationResult;
import com.nexus.platform.packagemanager.PackageManagerType;
import com.nexus.platform.scheduler.TimerStateManager;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Registers Scheduler, Telegram, Audit and Package DTOs and state records
 * for GraalVM Native Image reflection and serialization.
 */
public class SchedulerRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        Class<?>[] reflectionClasses = new Class<?>[] {
                TimerStateManager.StateFile.class,
                TimerStateManager.TimerRecord.class,
                TimerScope.class,
                TimerInfo.class,
                TimerListReport.class,
                TimerCreateResult.class,
                TimerStatusResult.class,
                TimerUpdateResult.class,
                TimerRunResult.class,
                TimerDeleteResult.class,
                TelegramSendRequest.class,
                TelegramSendResult.class,
                TelegramRecipientResult.class,
                TelegramSendType.class,
                PackageOperationRequest.class,
                PackageOperationResult.class,
                PackageManagerType.class,
                AuditSeverity.class,
                AuditCheckItem.class,
                SystemAuditReport.class
        };

        for (Class<?> clazz : reflectionClasses) {
            hints.reflection().registerType(clazz,
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.PUBLIC_FIELDS,
                    MemberCategory.DECLARED_FIELDS);
        }
    }
}
