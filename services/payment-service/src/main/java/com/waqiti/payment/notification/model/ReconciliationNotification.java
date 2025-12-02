package com.waqiti.payment.notification.model;

import com.waqiti.payment.dto.ReconciliationDiscrepancy;
import com.waqiti.payment.dto.ReconciliationRecord;
import com.waqiti.payment.notification.model.NotificationResult.NotificationChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Enterprise Reconciliation Notification Model
 * 
 * Comprehensive notification data for reconciliation operations including:
 * - Complete reconciliation results and discrepancy analysis
 * - Financial variance details and settlement information
 * - Stakeholder-specific routing (accounting, operations, management)
 * - Alert escalation for critical discrepancies
 * - Detailed reporting data for analysis
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationNotification {
    
    // Notification metadata
    private String notificationId;
    private ReconciliationNotificationSubject subject;
    private NotificationPriority priority;
    private NotificationChannel[] preferredChannels;
    
    // Reconciliation details
    private String reconciliationId;
    private String settlementId;
    private LocalDateTime reconciliationPeriodStart;
    private LocalDateTime reconciliationPeriodEnd;
    private ReconciliationStatus reconciliationStatus;
    private long processingTimeMs;
    
    // Financial summary
    private int totalPayments;
    private BigDecimal expectedGrossAmount;
    private BigDecimal actualGrossAmount;
    private BigDecimal expectedNetAmount;
    private BigDecimal actualNetAmount;
    private BigDecimal totalFees;
    private BigDecimal totalVariance;
    private BigDecimal variancePercentage;
    private String currency;
    
    // Discrepancy information
    private int discrepancyCount;
    private List<ReconciliationDiscrepancy> discrepancies;
    private BigDecimal totalDiscrepancyAmount;
    private String discrepancySummary;
    private boolean requiresInvestigation;
    private boolean isCriticalDiscrepancy;
    
    // Stakeholder information
    private String accountingEmail;
    private String operationsEmail;
    private String managementEmail;
    private String financeSlackChannel;
    private String operationsSlackChannel;
    
    // Provider information
    private String paymentProvider;
    private String providerSettlementId;
    private Map<String, Object> providerMetadata;
    
    // Notification content
    private String emailTemplate;
    private String slackTemplate;
    private Map<String, Object> templateVariables;
    private String customMessage;
    
    // Alert escalation
    private boolean requiresEscalation;
    private EscalationLevel escalationLevel;
    private List<String> escalationRecipients;
    private LocalDateTime escalationTime;
    
    // Delivery preferences
    private boolean requireDeliveryConfirmation;
    private boolean enableRetry;
    private int maxRetryAttempts;
    private NotificationChannel fallbackChannel;
    
    // Compliance and audit
    private String auditTrailId;
    private Map<String, Object> complianceMetadata;
    private boolean requiresRegulatorNotification;
    
    // Enums
    public enum ReconciliationNotificationSubject {
        RECONCILIATION_COMPLETED("Reconciliation Successfully Completed"),
        DISCREPANCIES_FOUND("Discrepancies Found in Reconciliation"),
        CRITICAL_DISCREPANCY("Critical Discrepancy Alert"),
        RECONCILIATION_FAILED("Reconciliation Processing Failed"),
        LARGE_VARIANCE("Large Variance Detected"),
        MANUAL_REVIEW_REQUIRED("Manual Review Required"),
        RECONCILIATION_DELAYED("Reconciliation Processing Delayed");
        
        private final String displayName;
        
        ReconciliationNotificationSubject(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public enum NotificationPriority {
        LOW(1),
        NORMAL(2),
        HIGH(3),
        URGENT(4),
        CRITICAL(5);
        
        private final int level;
        
        NotificationPriority(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
    }
    
    public enum ReconciliationStatus {
        COMPLETED,
        COMPLETED_WITH_DISCREPANCIES,
        FAILED,
        REQUIRES_INVESTIGATION,
        PENDING_APPROVAL
    }
    
    public enum EscalationLevel {
        NONE(0),
        TEAM_LEAD(1),
        DEPARTMENT_HEAD(2),
        SENIOR_MANAGEMENT(3),
        C_LEVEL(4);
        
        private final int level;
        
        EscalationLevel(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
    }
    
    // Helper methods
    public boolean hasCriticalVariance() {
        return totalVariance.abs().compareTo(new BigDecimal("10000")) > 0 ||
               (variancePercentage != null && variancePercentage.abs().compareTo(new BigDecimal("5")) > 0);
    }
    
    public boolean hasDiscrepancies() {
        return discrepancyCount > 0;
    }
    
    public String getFormattedVariance() {
        return String.format("%+.2f %s (%.2f%%)", totalVariance, currency, variancePercentage);
    }
    
    public String getFormattedExpectedAmount() {
        return String.format("%.2f %s", expectedGrossAmount, currency);
    }
    
    public String getFormattedActualAmount() {
        return String.format("%.2f %s", actualGrossAmount, currency);
    }
    
    // Static factory methods
    public static ReconciliationNotification fromReconciliationRecord(ReconciliationRecord record,
                                                                      List<ReconciliationDiscrepancy> discrepancies) {
        boolean hasCriticalDiscrepancies = discrepancies.stream()
            .anyMatch(d -> "CRITICAL".equals(d.getSeverity()));
        
        NotificationPriority priority = determinePriority(record, discrepancies);
        ReconciliationNotificationSubject subject = determineSubject(record, discrepancies);
        
        return ReconciliationNotification.builder()
            .reconciliationId(record.getReconciliationId())
            .settlementId(record.getSettlementId())
            .reconciliationStatus(ReconciliationStatus.valueOf(record.getStatus().toString()))
            .totalPayments(record.getTotalPayments())
            .expectedGrossAmount(record.getExpectedGrossAmount())
            .actualGrossAmount(record.getActualGrossAmount())
            .expectedNetAmount(record.getExpectedNetAmount())
            .actualNetAmount(record.getActualNetAmount())
            .totalFees(record.getTotalFees())
            .totalVariance(record.getVariance())
            .currency(record.getCurrency())
            .discrepancyCount(discrepancies.size())
            .discrepancies(discrepancies)
            .requiresInvestigation(hasCriticalDiscrepancies)
            .isCriticalDiscrepancy(hasCriticalDiscrepancies)
            .subject(subject)
            .priority(priority)
            .requireDeliveryConfirmation(hasCriticalDiscrepancies)
            .enableRetry(true)
            .maxRetryAttempts(hasCriticalDiscrepancies ? 5 : 3)
            .build();
    }
    
    public static ReconciliationNotification forAccounting(ReconciliationRecord record,
                                                           List<ReconciliationDiscrepancy> discrepancies) {
        return fromReconciliationRecord(record, discrepancies)
            .toBuilder()
            .accountingEmail("accounting@example.com")
            .financeSlackChannel("#accounting-alerts")
            .emailTemplate("accounting-reconciliation-notification")
            .slackTemplate("accounting-reconciliation-slack")
            .preferredChannels(new NotificationChannel[]{
                NotificationChannel.EMAIL, 
                NotificationChannel.SLACK
            })
            .build();
    }
    
    public static ReconciliationNotification forOperations(ReconciliationRecord record,
                                                           List<ReconciliationDiscrepancy> discrepancies) {
        return fromReconciliationRecord(record, discrepancies)
            .toBuilder()
            .operationsEmail("payments-ops@example.com")
            .operationsSlackChannel("#payment-operations")
            .emailTemplate("operations-reconciliation-notification")
            .slackTemplate("operations-reconciliation-slack")
            .preferredChannels(new NotificationChannel[]{
                NotificationChannel.EMAIL, 
                NotificationChannel.SLACK
            })
            .build();
    }
    
    public static ReconciliationNotification forCriticalAlert(ReconciliationRecord record,
                                                              List<ReconciliationDiscrepancy> discrepancies) {
        return fromReconciliationRecord(record, discrepancies)
            .toBuilder()
            .subject(ReconciliationNotificationSubject.CRITICAL_DISCREPANCY)
            .priority(NotificationPriority.CRITICAL)
            .requiresEscalation(true)
            .escalationLevel(EscalationLevel.DEPARTMENT_HEAD)
            .managementEmail("finance-management@example.com")
            .escalationRecipients(List.of(
                "cfo@example.com",
                "finance-director@example.com",
                "payments-head@example.com"
            ))
            .preferredChannels(new NotificationChannel[]{
                NotificationChannel.EMAIL, 
                NotificationChannel.SLACK,
                NotificationChannel.SMS
            })
            .requireDeliveryConfirmation(true)
            .maxRetryAttempts(5)
            .build();
    }
    
    // Helper methods for factory methods
    private static NotificationPriority determinePriority(ReconciliationRecord record,
                                                          List<ReconciliationDiscrepancy> discrepancies) {
        if (discrepancies.stream().anyMatch(d -> "CRITICAL".equals(d.getSeverity()))) {
            return NotificationPriority.CRITICAL;
        }
        if (record.getVariance().abs().compareTo(new BigDecimal("5000")) > 0) {
            return NotificationPriority.HIGH;
        }
        if (discrepancies.size() > 0) {
            return NotificationPriority.NORMAL;
        }
        return NotificationPriority.LOW;
    }
    
    private static ReconciliationNotificationSubject determineSubject(ReconciliationRecord record,
                                                                      List<ReconciliationDiscrepancy> discrepancies) {
        if (discrepancies.stream().anyMatch(d -> "CRITICAL".equals(d.getSeverity()))) {
            return ReconciliationNotificationSubject.CRITICAL_DISCREPANCY;
        }
        if (record.getVariance().abs().compareTo(new BigDecimal("5000")) > 0) {
            return ReconciliationNotificationSubject.LARGE_VARIANCE;
        }
        if (discrepancies.size() > 0) {
            return ReconciliationNotificationSubject.DISCREPANCIES_FOUND;
        }
        return ReconciliationNotificationSubject.RECONCILIATION_COMPLETED;
    }
}