package com.waqiti.common.model.alert;

import com.waqiti.common.dlq.BaseDlqRecoveryResult;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Result object for system alert DLQ recovery operations
 */
@Getter
@Setter
@SuperBuilder
public class SystemAlertRecoveryResult extends BaseDlqRecoveryResult {

    private String alertId;
    private String alertType;
    private String severity;
    private String sourceService;
    private String resolutionDetails;
    private Instant resolutionTime;
    private boolean autoResolvable;
    private boolean hasAssociatedIncident;
    private String incidentId;
    private boolean criticalAlert;
    private List<String> affectedServices;

    @Override
    public String getRecoveryStatus() {
        if (isRecovered()) {
            return "ALERT_RECOVERED";
        } else if (isCriticalAlert()) {
            return "CRITICAL_ALERT_FAILED";
        } else {
            return "ALERT_RECOVERY_FAILED";
        }
    }

    public boolean isCriticalAlert() {
        return criticalAlert || "CRITICAL".equals(severity) || "EMERGENCY".equals(severity);
    }
}
