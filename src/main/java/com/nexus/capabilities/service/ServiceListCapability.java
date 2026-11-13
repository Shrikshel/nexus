package com.nexus.capabilities.service;

import com.nexus.capabilities.service.dto.ServiceListReport;
import com.nexus.capabilities.service.dto.ServiceUnitInfo;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Capability providing a listing of system services and their operational states.
 */
@Component
public class ServiceListCapability implements Capability<Void, ServiceListReport> {

    private static final Logger log = LoggerFactory.getLogger(ServiceListCapability.class);
    public static final String ID = "svc.list";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "System Services Status";
    }

    @Override
    public String getDescription() {
        return "Returns installed system services, execution states, and unit descriptions";
    }

    @Override
    public CapabilityCategory getCategory() {
        return CapabilityCategory.SERVICE;
    }

    @Override
    public Set<Permission> getRequiredPermissions() {
        return Set.of(Permission.READ_SERVICES);
    }

    @Override
    public Class<Void> getInputType() {
        return Void.class;
    }

    @Override
    public Class<ServiceListReport> getOutputType() {
        return ServiceListReport.class;
    }

    @Override
    public CapabilityResult<ServiceListReport> execute(ExecutionContext context, Void input) {
        Instant start = Instant.now();
        List<ServiceUnitInfo> list = new ArrayList<>();

        try {
            Process process = new ProcessBuilder("systemctl", "list-units", "--type=service", "--no-pager", "--no-legend").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;
                    String[] parts = trimmed.split("\\s+", 5);
                    if (parts.length >= 4) {
                        String name = parts[0];
                        String load = parts[1];
                        String active = parts[2];
                        String sub = parts[3];
                        String desc = parts.length >= 5 ? parts[4] : "";
                        list.add(new ServiceUnitInfo(name, load, active, sub, desc));
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            log.debug("systemctl query skipped or failed: {}", e.getMessage());
        }

        ServiceListReport report = new ServiceListReport(list, list.size(), Instant.now());
        return CapabilityResult.success(ID, report, start);
    }
}
