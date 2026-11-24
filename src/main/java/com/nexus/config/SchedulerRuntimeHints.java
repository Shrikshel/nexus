package com.nexus.config;

import com.nexus.capabilities.network.dto.NetworkReport;
import com.nexus.capabilities.network.tailscale.dto.TailscaleOperationRequest;
import com.nexus.capabilities.network.tailscale.dto.TailscaleOperationResult;
import com.nexus.capabilities.network.tailscale.dto.TailscaleStatusReport;
import com.nexus.capabilities.network.telegram.dto.TelegramRecipientResult;
import com.nexus.capabilities.network.telegram.dto.TelegramSendRequest;
import com.nexus.capabilities.network.telegram.dto.TelegramSendResult;
import com.nexus.capabilities.network.telegram.dto.TelegramSendType;
import com.nexus.capabilities.process.dto.ProcessInfo;
import com.nexus.capabilities.process.dto.ProcessListReport;
import com.nexus.capabilities.reminder.dto.*;
import com.nexus.capabilities.resource.dto.CpuReport;
import com.nexus.capabilities.resource.dto.MemoryReport;
import com.nexus.capabilities.scheduler.timer.dto.*;
import com.nexus.capabilities.service.dto.ServiceListReport;
import com.nexus.capabilities.service.dto.ServiceUnitInfo;
import com.nexus.capabilities.storage.dto.DiskListReport;
import com.nexus.capabilities.storage.dto.DiskPartitionReport;
import com.nexus.capabilities.storage.dto.SmartReport;
import com.nexus.capabilities.storage.zfs.dto.*;
import com.nexus.capabilities.system.audit.dto.AuditCheckItem;
import com.nexus.capabilities.system.audit.dto.AuditSeverity;
import com.nexus.capabilities.system.audit.dto.SystemAuditReport;
import com.nexus.capabilities.system.dto.PowerOperationRequest;
import com.nexus.capabilities.system.dto.PowerOperationResult;
import com.nexus.capabilities.system.dto.SystemInfoReport;
import com.nexus.capabilities.system.dto.SystemUptimeReport;
import com.nexus.capabilities.system.package_mgr.dto.PackageOperationRequest;
import com.nexus.capabilities.system.package_mgr.dto.PackageOperationResult;
import com.nexus.capabilities.terminal.tmux.dto.TmuxOperationResult;
import com.nexus.capabilities.terminal.tmux.dto.TmuxSessionInfo;
import com.nexus.capabilities.terminal.tmux.dto.TmuxSessionListReport;
import com.nexus.core.capability.CapabilityError;
import com.nexus.core.capability.CapabilityMetadata;
import com.nexus.core.capability.CapabilityResult;
import com.nexus.core.capability.ErrorCode;
import com.nexus.platform.packagemanager.PackageManagerType;
import com.nexus.platform.scheduler.TimerStateManager;
import com.nexus.platform.service.ServiceOperationResult;
import com.nexus.platform.service.ServiceStatusDetails;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Registers Domain Entities, Capabilities, DTOs, and Reminders
 * for GraalVM Native Image reflection and serialization.
 */
public class SchedulerRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        Class<?>[] reflectionClasses = new Class<?>[] {
                // Reminder Engine DTOs & Records
                ReminderEvent.class,
                ReminderEventState.class,
                ReminderStage.class,
                ReminderStageStatus.class,
                ReminderType.class,
                ReminderRecurrence.class,
                ReminderHistoryEntry.class,
                ReminderCreateRequest.class,
                ReminderUpdateRequest.class,
                ReminderImportResult.class,

                // Core Capabilities & Results
                CapabilityResult.class,
                CapabilityError.class,
                ErrorCode.class,
                CapabilityMetadata.class,

                // Timers & Scheduler
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

                // Telegram
                TelegramSendRequest.class,
                TelegramSendResult.class,
                TelegramRecipientResult.class,
                TelegramSendType.class,

                // System & Host Reports
                SystemInfoReport.class,
                SystemUptimeReport.class,
                CpuReport.class,
                MemoryReport.class,
                DiskListReport.class,
                DiskPartitionReport.class,
                SmartReport.class,
                ProcessListReport.class,
                ProcessInfo.class,
                NetworkReport.class,
                ServiceListReport.class,
                ServiceUnitInfo.class,
                PackageOperationRequest.class,
                PackageOperationResult.class,
                PackageManagerType.class,
                AuditSeverity.class,
                AuditCheckItem.class,
                SystemAuditReport.class,
                PowerOperationRequest.class,
                PowerOperationResult.class,
                ServiceOperationResult.class,
                ServiceStatusDetails.class,
                com.nexus.platform.logging.NexusLogManager.LogFileInfo.class,

                // Storage & ZFS
                ZfsPoolListReport.class,
                ZfsPoolInfo.class,
                ZfsPoolStatusReport.class,
                ZfsDatasetListReport.class,
                ZfsDatasetInfo.class,
                ZfsSnapshotListReport.class,
                ZfsSnapshotInfo.class,
                ZfsDatasetDetailReport.class,

                // Tailscale & Tmux
                TailscaleStatusReport.class,
                TailscaleOperationRequest.class,
                TailscaleOperationResult.class,
                TmuxSessionListReport.class,
                TmuxSessionInfo.class,
                TmuxOperationResult.class
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
