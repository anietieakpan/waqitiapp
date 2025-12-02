package com.waqiti.common.model.alert;

import com.waqiti.common.dlq.BaseDlqRecoveryResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.Map;

/**
 * Recovery result for feature flag DLQ processing.
 * Tracks the outcome of attempting to recover failed feature flag operations.
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class FeatureFlagRecoveryResult extends BaseDlqRecoveryResult {

    private String flagId;
    private String flagName;
    private String flagKey;
    private boolean flagEnabled;
    private String targetEnvironment;
    private String targetService;
    private String operationType; // CREATE, UPDATE, DELETE, TOGGLE
    private boolean operationSuccessful;
    private Instant effectiveTimestamp;
    private String rolloutPercentage;
    private Map<String, Object> flagVariants;
    private String targetUserSegment;
    private boolean requiresApproval;
    private String approvedBy;
    private String rollbackRequired;
    private Instant previousState;

    // Extended fields for consumer compatibility
    private String featureFlag;
    private String environment;
    private String actionType;
    private String newState;
    private boolean criticalFlag;
    private java.util.List<String> affectedServices;
    private java.util.Map<String, Object> configurationChanges;
    private java.util.Map<String, Object> lastKnownGoodConfiguration;
    private Integer rolloutPercentageInt;
    private boolean emergencyProtocolExecuted;
    private boolean fallbackImplemented;
    private String auditTrailId;

    @Override
    public String getRecoveryStatus() {
        if (isRecovered()) {
            return String.format("Feature flag recovered: flag=%s, operation=%s, enabled=%s, env=%s",
                    flagName, operationType, flagEnabled, targetEnvironment);
        } else {
            return String.format("Feature flag recovery failed: flag=%s, operation=%s, reason=%s",
                    flagName, operationType, getFailureReason());
        }
    }

    public boolean isProductionFlag() {
        return "production".equalsIgnoreCase(targetEnvironment) ||
               "prod".equalsIgnoreCase(targetEnvironment);
    }

    public boolean isCriticalFlag() {
        return flagName != null &&
               (flagName.contains("payment") ||
                flagName.contains("security") ||
                flagName.contains("compliance") ||
                flagName.contains("regulatory"));
    }

    public boolean isGradualRollout() {
        return rolloutPercentage != null &&
               !rolloutPercentage.equals("0") &&
               !rolloutPercentage.equals("100");
    }

    public boolean requiresRollback() {
        return "true".equalsIgnoreCase(rollbackRequired) || !operationSuccessful;
    }

    public boolean isToggleOperation() {
        return "TOGGLE".equalsIgnoreCase(operationType);
    }

    public boolean requiresNotification() {
        return isProductionFlag() || isCriticalFlag();
    }

    // Compatibility methods

    public String getFeatureFlag() {
        return featureFlag != null ? featureFlag : flagName;
    }

    public String getEnvironment() {
        return environment != null ? environment : targetEnvironment;
    }

    public String getActionType() {
        return actionType != null ? actionType : operationType;
    }

    public String getNewState() {
        return newState != null ? newState : (flagEnabled ? "ENABLED" : "DISABLED");
    }

    public boolean isEmergencyProtocolExecuted() {
        return emergencyProtocolExecuted;
    }

    public boolean isFallbackImplemented() {
        return fallbackImplemented;
    }

    public boolean requiresEmergencyAction() {
        return criticalFlag && !operationSuccessful;
    }
}
