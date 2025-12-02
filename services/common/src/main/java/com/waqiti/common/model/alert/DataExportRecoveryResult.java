package com.waqiti.common.model.alert;

import com.waqiti.common.dlq.BaseDlqRecoveryResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Recovery result for data export (GDPR) DLQ processing.
 * Tracks the outcome of attempting to recover failed GDPR data export requests.
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class DataExportRecoveryResult extends BaseDlqRecoveryResult {

    private String exportRequestId;
    private String customerId;
    private String requestType; // GDPR_EXPORT, GDPR_DELETION, CCPA_EXPORT
    private String dataCategory; // PII, FINANCIAL, COMMUNICATIONS, ALL
    private boolean exportCompleted;
    private String exportFormat; // JSON, CSV, PDF
    private String exportUrl;
    private Long exportSizeBytes;
    private Integer recordCount;
    private Instant exportCompletedTimestamp;
    private Instant expirationTimestamp;
    private boolean gdprCompliant;
    private boolean encrypted;
    private String encryptionMethod;
    private Integer regulatoryDeadlineDays;
    private Boolean deadlineBreached;
    private String notificationSent;

    @Override
    public String getRecoveryStatus() {
        if (isRecovered()) {
            return String.format("Data export recovered: requestId=%s, customer=%s, type=%s, completed=%s",
                    exportRequestId, customerId, requestType, exportCompleted);
        } else {
            return String.format("Data export recovery failed: requestId=%s, reason=%s",
                    exportRequestId, getFailureReason());
        }
    }

    public boolean isGdprRequest() {
        return requestType != null &&
               (requestType.contains("GDPR") || requestType.contains("GDPR"));
    }

    public boolean isCcpaRequest() {
        return requestType != null && requestType.contains("CCPA");
    }

    public boolean isDeletionRequest() {
        return requestType != null && requestType.contains("DELETION");
    }

    public boolean isWithinDeadline() {
        if (deadlineBreached != null) {
            return !deadlineBreached;
        }
        if (regulatoryDeadlineDays != null && exportCompletedTimestamp != null) {
            long daysSinceRequest = java.time.Duration.between(
                    getProcessingStartTime(), exportCompletedTimestamp).toDays();
            return daysSinceRequest <= regulatoryDeadlineDays;
        }
        return true;
    }

    public boolean requiresUrgentEscalation() {
        return deadlineBreached || (regulatoryDeadlineDays != null && regulatoryDeadlineDays < 3);
    }

    public boolean isSecureExport() {
        return encrypted && encryptionMethod != null;
    }

    public String getExportSizeFormatted() {
        if (exportSizeBytes == null) return "Unknown";
        double kb = exportSizeBytes / 1024.0;
        double mb = kb / 1024.0;
        if (mb >= 1) {
            return String.format("%.2f MB", mb);
        } else {
            return String.format("%.2f KB", kb);
        }
    }
}
