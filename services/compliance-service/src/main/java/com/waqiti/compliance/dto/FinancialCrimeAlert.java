package com.waqiti.compliance.dto;

import com.waqiti.compliance.enums.CrimeSeverity;
import com.waqiti.compliance.enums.CrimeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Financial Crime Alert DTO
 *
 * Data Transfer Object for financial crime alert events
 * published to Kafka for downstream processing and notification.
 *
 * Used for:
 * - Kafka event publishing
 * - Real-time crime alerts
 * - Microservice event-driven communication
 *
 * Compliance: Event-driven compliance architecture
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialCrimeAlert {

    /**
     * Alert ID (unique)
     */
    private String alertId;

    /**
     * Crime case ID
     */
    private String caseId;

    /**
     * User ID involved in crime
     */
    private String userId;

    /**
     * Crime type
     */
    private CrimeType crimeType;

    /**
     * Crime severity
     */
    private CrimeSeverity severity;

    /**
     * Alert timestamp
     */
    @Builder.Default
    private LocalDateTime alertTime = LocalDateTime.now();

    /**
     * Alert description
     */
    private String description;

    /**
     * Alert details (flexible JSON)
     */
    private Map<String, Object> details;

    /**
     * Account frozen flag
     */
    @Builder.Default
    private boolean accountFrozen = false;

    /**
     * SAR filed flag
     */
    @Builder.Default
    private boolean sarFiled = false;

    /**
     * SAR reference number (if filed)
     */
    private String sarReferenceNumber;

    /**
     * Law enforcement notified flag
     */
    @Builder.Default
    private boolean lawEnforcementNotified = false;

    /**
     * List of agencies notified (FBI, SEC, etc.)
     */
    @Builder.Default
    private List<String> agenciesNotified = new ArrayList<>();

    /**
     * Assigned investigator
     */
    private String assignedTo;

    /**
     * Alert priority (P0, P1, P2, P3)
     */
    private String priority;

    /**
     * Estimated financial impact (USD)
     */
    private String estimatedImpact;

    /**
     * Number of transactions involved
     */
    private Integer transactionCount;

    /**
     * Transaction IDs involved
     */
    @Builder.Default
    private List<String> transactionIds = new ArrayList<>();

    /**
     * Evidence preserved flag
     */
    @Builder.Default
    private boolean evidencePreserved = false;

    /**
     * Alert source (e.g., "FRAUD_DETECTION_SERVICE", "MANUAL_REPORT")
     */
    private String source;

    /**
     * Event version for Kafka schema evolution
     */
    @Builder.Default
    private String eventVersion = "1.0";

    /**
     * Check if alert is critical
     *
     * @return true if critical
     */
    public boolean isCritical() {
        return severity == CrimeSeverity.CRITICAL || "P0".equals(priority);
    }

    /**
     * Check if alert requires immediate action
     *
     * @return true if immediate action required
     */
    public boolean requiresImmediateAction() {
        return severity != null && severity.requiresImmediateAction();
    }

    /**
     * Add agency to notified list
     *
     * @param agency agency name
     */
    public void addNotifiedAgency(String agency) {
        if (agenciesNotified == null) {
            agenciesNotified = new ArrayList<>();
        }
        if (!agenciesNotified.contains(agency)) {
            agenciesNotified.add(agency);
        }
    }

    /**
     * Add transaction ID to list
     *
     * @param transactionId transaction ID
     */
    public void addTransactionId(String transactionId) {
        if (transactionIds == null) {
            transactionIds = new ArrayList<>();
        }
        if (!transactionIds.contains(transactionId)) {
            transactionIds.add(transactionId);
        }
    }
}
