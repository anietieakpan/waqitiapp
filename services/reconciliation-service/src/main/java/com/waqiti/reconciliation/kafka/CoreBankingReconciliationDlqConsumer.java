package com.waqiti.reconciliation.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.reconciliation.service.ReconciliationService;
import com.waqiti.reconciliation.service.ReconciliationValidationService;
import com.waqiti.reconciliation.repository.ReconciliationRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * DLQ Consumer for core banking reconciliation failures.
 * Handles critical reconciliation processing errors with immediate escalation for data integrity.
 */
@Component
@Slf4j
public class CoreBankingReconciliationDlqConsumer extends BaseDlqConsumer {

    private final ReconciliationService reconciliationService;
    private final ReconciliationValidationService reconciliationValidationService;
    private final ReconciliationRecordRepository reconciliationRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public CoreBankingReconciliationDlqConsumer(DlqHandler dlqHandler,
                                              AuditService auditService,
                                              NotificationService notificationService,
                                              MeterRegistry meterRegistry,
                                              ReconciliationService reconciliationService,
                                              ReconciliationValidationService reconciliationValidationService,
                                              ReconciliationRecordRepository reconciliationRepository,
                                              KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.reconciliationService = reconciliationService;
        this.reconciliationValidationService = reconciliationValidationService;
        this.reconciliationRepository = reconciliationRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
        topics = {"core-banking-reconciliation.DLQ"},
        groupId = "core-banking-reconciliation-dlq-consumer-group",
        containerFactory = "criticalReconciliationKafkaListenerContainerFactory"
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "core-banking-reconciliation-dlq", fallbackMethod = "handleCoreBankingReconciliationDlqFallback")
    public void handleCoreBankingReconciliationDlq(@Payload Object originalMessage,
                                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                                 @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                                 @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                                 @Header(KafkaHeaders.OFFSET) long offset,
                                                 Acknowledgment acknowledgment,
                                                 @Header Map<String, Object> headers) {

        log.info("Processing core banking reconciliation DLQ message: topic={}, partition={}, offset={}",
            topic, partition, offset);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String reconciliationId = extractReconciliationId(originalMessage);
            String accountId = extractAccountId(originalMessage);
            String reconciliationType = extractReconciliationType(originalMessage);
            String reconciliationDate = extractReconciliationDate(originalMessage);
            String discrepancyAmount = extractDiscrepancyAmount(originalMessage);
            String reconciliationStatus = extractReconciliationStatus(originalMessage);
            String correlationId = extractCorrelationId(null, originalMessage);

            log.info("Processing core banking reconciliation DLQ: reconciliationId={}, accountId={}, type={}, discrepancy={}, messageId={}",
                reconciliationId, accountId, reconciliationType, discrepancyAmount, messageId);

            // Validate reconciliation state and integrity
            if (reconciliationId != null) {
                validateReconciliationState(reconciliationId, messageId);
                assessReconciliationIntegrity(reconciliationId, originalMessage, messageId);
                handleReconciliationRecovery(reconciliationId, accountId, originalMessage, exceptionMessage, messageId);
            }

            // Generate critical alerts
            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Check for data integrity and discrepancy impact
            assessDataIntegrityImpact(reconciliationId, reconciliationType, discrepancyAmount, originalMessage, messageId);

            // Handle regulatory reporting impact
            assessRegulatoryReconciliationImpact(reconciliationId, reconciliationType, discrepancyAmount, originalMessage, messageId);

            // Trigger manual reconciliation review
            triggerManualReconciliationReview(reconciliationId, accountId, reconciliationType, discrepancyAmount, messageId);

        } catch (Exception e) {
            log.error("Error in core banking reconciliation DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "core-banking-reconciliation-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "CORE_BANKING";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        String reconciliationType = extractReconciliationType(originalMessage);
        String discrepancyAmount = extractDiscrepancyAmount(originalMessage);

        // All reconciliation failures are critical for data integrity
        if (isCriticalReconciliationType(reconciliationType)) {
            return true;
        }

        // High discrepancy amounts are critical
        if (isHighDiscrepancy(discrepancyAmount)) {
            return true;
        }

        // Daily reconciliations are critical
        return isDailyReconciliation(reconciliationType);
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String reconciliationId = extractReconciliationId(originalMessage);
        String accountId = extractAccountId(originalMessage);
        String reconciliationType = extractReconciliationType(originalMessage);
        String reconciliationDate = extractReconciliationDate(originalMessage);
        String discrepancyAmount = extractDiscrepancyAmount(originalMessage);
        String reconciliationStatus = extractReconciliationStatus(originalMessage);

        try {
            // CRITICAL escalation for reconciliation failures
            String alertTitle = String.format("CORE BANKING CRITICAL: Reconciliation Processing Failed - %s",
                reconciliationType != null ? reconciliationType : "Unknown Reconciliation");
            String alertMessage = String.format(
                "🏦 CORE BANKING RECONCILIATION FAILURE 🏦\n\n" +
                "A core banking reconciliation operation has FAILED and requires IMMEDIATE attention:\n\n" +
                "Reconciliation ID: %s\n" +
                "Account ID: %s\n" +
                "Reconciliation Type: %s\n" +
                "Reconciliation Date: %s\n" +
                "Discrepancy Amount: %s\n" +
                "Reconciliation Status: %s\n" +
                "Error: %s\n\n" +
                "🚨 CRITICAL: Reconciliation failures can cause:\n" +
                "- Data integrity compromises\n" +
                "- Financial reporting inaccuracies\n" +
                "- Regulatory compliance violations\n" +
                "- Audit trail gaps\n" +
                "- Operational risk exposure\n\n" +
                "IMMEDIATE reconciliation operations and financial controls escalation required.",
                reconciliationId != null ? reconciliationId : "unknown",
                accountId != null ? accountId : "unknown",
                reconciliationType != null ? reconciliationType : "unknown",
                reconciliationDate != null ? reconciliationDate : "unknown",
                discrepancyAmount != null ? discrepancyAmount : "unknown",
                reconciliationStatus != null ? reconciliationStatus : "unknown",
                exceptionMessage
            );

            // Send reconciliation operations alert
            notificationService.sendReconciliationAlert(alertTitle, alertMessage, "CRITICAL");

            // Send core banking operations alert
            notificationService.sendCoreBankingAlert(
                "URGENT: Core Banking Reconciliation Failed",
                alertMessage,
                "CRITICAL"
            );

            // Send financial controls alert
            notificationService.sendFinancialControlsAlert(
                "Financial Controls Reconciliation Alert",
                String.format("Reconciliation %s failed processing. " +
                    "Review financial controls and data integrity procedures.", reconciliationId),
                "HIGH"
            );

            // High discrepancy specific alerts
            if (isHighDiscrepancy(discrepancyAmount)) {
                notificationService.sendHighDiscrepancyAlert(
                    "HIGH DISCREPANCY RECONCILIATION FAILED",
                    String.format("High-discrepancy reconciliation %s (%s) failed. " +
                        "Immediate review for financial impact and data integrity.", reconciliationId, discrepancyAmount),
                    "CRITICAL"
                );
            }

            // Daily reconciliation specific alerts
            if (isDailyReconciliation(reconciliationType)) {
                notificationService.sendDailyReconciliationAlert(
                    "Daily Reconciliation Failed",
                    String.format("Daily reconciliation %s failed. " +
                        "This impacts daily financial reporting and regulatory compliance.", reconciliationId),
                    "HIGH"
                );
            }

            // End-of-day reconciliation alerts
            if (isEndOfDayReconciliation(reconciliationType)) {
                notificationService.sendEndOfDayAlert(
                    "End of Day Reconciliation Failed",
                    String.format("End-of-day reconciliation %s failed. " +
                        "Review day-end procedures and financial position reporting.", reconciliationId),
                    "HIGH"
                );
            }

            // Regulatory reporting alert
            if (isRegulatoryReconciliation(reconciliationType)) {
                notificationService.sendRegulatoryAlert(
                    "Regulatory Reconciliation Failed",
                    String.format("Regulatory reconciliation %s failed. " +
                        "Review compliance requirements and regulatory reporting impact.", reconciliationId),
                    "HIGH"
                );
            }

            // Data integrity alert
            notificationService.sendDataIntegrityAlert(
                "Data Integrity Risk",
                String.format("Reconciliation %s failure may indicate data integrity issues. " +
                    "Immediate data consistency review required.", reconciliationId),
                "HIGH"
            );

            // Operations alert for reconciliation infrastructure
            notificationService.sendOperationsAlert(
                "Reconciliation Infrastructure Alert",
                String.format("Reconciliation processing failure may indicate infrastructure issues. " +
                    "Reconciliation: %s, Type: %s", reconciliationId, reconciliationType),
                "HIGH"
            );

            // Audit alert for trail completeness
            notificationService.sendAuditAlert(
                "Audit Trail Reconciliation Alert",
                String.format("Reconciliation %s failure may create audit trail gaps. " +
                    "Review audit logging and reconciliation completeness.", reconciliationId),
                "MEDIUM"
            );

            // Technology escalation for system integrity
            notificationService.sendTechnologyAlert(
                "Reconciliation System Integrity Alert",
                String.format("Reconciliation processing failure may indicate system integrity issues. " +
                    "Reconciliation: %s, Account: %s", reconciliationId, accountId),
                "HIGH"
            );

        } catch (Exception e) {
            log.error("Failed to send core banking reconciliation DLQ notifications: {}", e.getMessage());
        }
    }

    private void validateReconciliationState(String reconciliationId, String messageId) {
        try {
            var reconciliation = reconciliationRepository.findById(reconciliationId);
            if (reconciliation.isPresent()) {
                String status = reconciliation.get().getStatus();
                String state = reconciliation.get().getState();

                log.info("Reconciliation state validation: reconciliationId={}, status={}, state={}, messageId={}",
                    reconciliationId, status, state, messageId);

                // Check for critical reconciliation states
                if ("IN_PROGRESS".equals(status) || "PROCESSING".equals(status)) {
                    log.warn("Critical reconciliation state in DLQ: reconciliationId={}, status={}", reconciliationId, status);

                    notificationService.sendReconciliationAlert(
                        "CRITICAL: Reconciliation State Inconsistency",
                        String.format("Reconciliation %s in critical state %s found in DLQ. " +
                            "Immediate reconciliation state reconciliation required.", reconciliationId, status),
                        "CRITICAL"
                    );
                }

                // Check for time-sensitive states
                if ("PENDING_EOD".equals(state) || "AWAITING_APPROVAL".equals(state)) {
                    notificationService.sendFinancialControlsAlert(
                        "Time-Sensitive Reconciliation Failed",
                        String.format("Reconciliation %s with time-sensitive state %s failed. " +
                            "Review time-critical reconciliation procedures.", reconciliationId, state),
                        "HIGH"
                    );
                }
            } else {
                log.warn("Reconciliation not found despite reconciliationId present: reconciliationId={}, messageId={}",
                    reconciliationId, messageId);
            }

        } catch (Exception e) {
            log.error("Error validating reconciliation state: reconciliationId={}, error={}",
                reconciliationId, e.getMessage());
        }
    }

    private void assessReconciliationIntegrity(String reconciliationId, Object originalMessage, String messageId) {
        try {
            // Validate reconciliation data integrity
            boolean integrityValid = reconciliationValidationService.validateReconciliationIntegrity(reconciliationId);
            if (!integrityValid) {
                log.error("Reconciliation integrity validation failed: reconciliationId={}", reconciliationId);

                notificationService.sendReconciliationAlert(
                    "CRITICAL: Reconciliation Data Integrity Failure",
                    String.format("Reconciliation %s failed data integrity validation in DLQ processing. " +
                        "Immediate reconciliation consistency review required.", reconciliationId),
                    "CRITICAL"
                );

                // Create data integrity incident
                kafkaTemplate.send("data-integrity-incidents", Map.of(
                    "reconciliationId", reconciliationId != null ? reconciliationId : "unknown",
                    "incidentType", "RECONCILIATION_INTEGRITY_FAILURE",
                    "severity", "CRITICAL",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

        } catch (Exception e) {
            log.error("Error assessing reconciliation integrity: reconciliationId={}, error={}",
                reconciliationId, e.getMessage());
        }
    }

    private void handleReconciliationRecovery(String reconciliationId, String accountId, Object originalMessage,
                                            String exceptionMessage, String messageId) {
        try {
            // Attempt automatic reconciliation recovery
            boolean recoveryAttempted = reconciliationService.attemptReconciliationRecovery(
                reconciliationId, accountId, exceptionMessage);

            if (recoveryAttempted) {
                log.info("Automatic reconciliation recovery attempted: reconciliationId={}, accountId={}",
                    reconciliationId, accountId);

                kafkaTemplate.send("reconciliation-recovery-queue", Map.of(
                    "reconciliationId", reconciliationId != null ? reconciliationId : "unknown",
                    "accountId", accountId != null ? accountId : "unknown",
                    "recoveryType", "AUTOMATIC_DLQ_RECOVERY",
                    "originalError", exceptionMessage,
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            } else {
                log.warn("Automatic reconciliation recovery not possible: reconciliationId={}", reconciliationId);

                notificationService.sendReconciliationAlert(
                    "Manual Reconciliation Recovery Required",
                    String.format("Reconciliation %s requires manual recovery intervention. " +
                        "Automatic recovery was not successful.", reconciliationId),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Error handling reconciliation recovery: reconciliationId={}, error={}",
                reconciliationId, e.getMessage());
        }
    }

    private void assessDataIntegrityImpact(String reconciliationId, String reconciliationType, String discrepancyAmount,
                                         Object originalMessage, String messageId) {
        try {
            if (isDataIntegrityAffectingReconciliation(reconciliationType)) {
                log.warn("Data integrity affecting reconciliation failure: reconciliationId={}, type={}, discrepancy={}",
                    reconciliationId, reconciliationType, discrepancyAmount);

                notificationService.sendDataIntegrityAlert(
                    "CRITICAL: Data Integrity Risk",
                    String.format("Data integrity affecting reconciliation (type: %s, discrepancy: %s) failed for %s. " +
                        "This may compromise financial data integrity. " +
                        "Immediate data consistency verification and correction required.",
                        reconciliationType, discrepancyAmount, reconciliationId),
                    "CRITICAL"
                );

                // Create data integrity incident
                kafkaTemplate.send("data-integrity-incidents", Map.of(
                    "reconciliationId", reconciliationId != null ? reconciliationId : "unknown",
                    "reconciliationType", reconciliationType,
                    "discrepancyAmount", discrepancyAmount,
                    "incidentType", "DATA_INTEGRITY_RECONCILIATION_FAILURE",
                    "dataRisk", true,
                    "severity", "HIGH",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

        } catch (Exception e) {
            log.error("Error assessing data integrity impact: reconciliationId={}, error={}",
                reconciliationId, e.getMessage());
        }
    }

    private void assessRegulatoryReconciliationImpact(String reconciliationId, String reconciliationType, String discrepancyAmount,
                                                    Object originalMessage, String messageId) {
        try {
            if (isRegulatoryReportableReconciliation(reconciliationType, discrepancyAmount)) {
                log.warn("Regulatory reportable reconciliation failure: reconciliationId={}, type={}, discrepancy={}",
                    reconciliationId, reconciliationType, discrepancyAmount);

                notificationService.sendRegulatoryAlert(
                    "CRITICAL: Regulatory Reconciliation Failed",
                    String.format("Regulatory reportable reconciliation %s (type: %s, discrepancy: %s) failed. " +
                        "This may impact regulatory reporting and compliance. " +
                        "Immediate compliance team review required.", reconciliationId, reconciliationType, discrepancyAmount),
                    "CRITICAL"
                );

                // Create regulatory compliance incident
                kafkaTemplate.send("regulatory-compliance-incidents", Map.of(
                    "reconciliationId", reconciliationId != null ? reconciliationId : "unknown",
                    "reconciliationType", reconciliationType,
                    "discrepancyAmount", discrepancyAmount,
                    "incidentType", "REGULATORY_RECONCILIATION_FAILURE",
                    "reportingImpact", true,
                    "severity", "HIGH",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

        } catch (Exception e) {
            log.error("Error assessing regulatory reconciliation impact: reconciliationId={}, error={}",
                reconciliationId, e.getMessage());
        }
    }

    private void triggerManualReconciliationReview(String reconciliationId, String accountId, String reconciliationType,
                                                 String discrepancyAmount, String messageId) {
        try {
            // All reconciliation DLQ requires manual review due to data integrity impact
            kafkaTemplate.send("manual-reconciliation-review-queue", Map.of(
                "reconciliationId", reconciliationId != null ? reconciliationId : "unknown",
                "accountId", accountId != null ? accountId : "unknown",
                "reconciliationType", reconciliationType,
                "discrepancyAmount", discrepancyAmount,
                "reviewReason", "CORE_BANKING_RECONCILIATION_DLQ_FAILURE",
                "priority", "HIGH",
                "messageId", messageId,
                "requiresIntegrityCheck", true,
                "dataImpact", true,
                "timestamp", Instant.now()
            ));

            log.info("Triggered manual reconciliation review for DLQ: reconciliationId={}", reconciliationId);
        } catch (Exception e) {
            log.error("Error triggering manual reconciliation review: reconciliationId={}, error={}",
                reconciliationId, e.getMessage());
        }
    }

    // Circuit breaker fallback method
    public void handleCoreBankingReconciliationDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                                          int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String reconciliationId = extractReconciliationId(originalMessage);
        String accountId = extractAccountId(originalMessage);
        String reconciliationType = extractReconciliationType(originalMessage);

        // EMERGENCY situation - reconciliation circuit breaker
        try {
            notificationService.sendExecutiveAlert(
                "EMERGENCY: Core Banking Reconciliation DLQ Circuit Breaker",
                String.format("CRITICAL SYSTEM FAILURE: Core banking reconciliation DLQ circuit breaker triggered " +
                    "for reconciliation %s (account %s, type %s). " +
                    "This represents complete failure of reconciliation processing and data integrity. " +
                    "IMMEDIATE C-LEVEL, FINANCIAL CONTROLS, TECHNOLOGY, AND OPERATIONS ESCALATION REQUIRED.",
                    reconciliationId, accountId, reconciliationType)
            );

            // Mark as emergency financial operations issue
            reconciliationService.markEmergencyReconciliationIssue(reconciliationId, accountId, "CIRCUIT_BREAKER_RECONCILIATION_DLQ");

        } catch (Exception e) {
            log.error("Error in core banking reconciliation DLQ fallback: {}", e.getMessage());
        }
    }

    // Helper methods for reconciliation classification
    private boolean isCriticalReconciliationType(String reconciliationType) {
        return reconciliationType != null && (
            reconciliationType.contains("DAILY") || reconciliationType.contains("EOD") ||
            reconciliationType.contains("REGULATORY") || reconciliationType.contains("NOSTRO")
        );
    }

    private boolean isHighDiscrepancy(String discrepancyAmount) {
        try {
            if (discrepancyAmount != null) {
                BigDecimal discrepancy = new BigDecimal(discrepancyAmount);
                return discrepancy.abs().compareTo(new BigDecimal("1000")) >= 0; // $1,000+
            }
        } catch (Exception e) {
            log.debug("Could not parse discrepancy amount: {}", discrepancyAmount);
        }
        return false;
    }

    private boolean isDailyReconciliation(String reconciliationType) {
        return reconciliationType != null && reconciliationType.contains("DAILY");
    }

    private boolean isEndOfDayReconciliation(String reconciliationType) {
        return reconciliationType != null && (
            reconciliationType.contains("EOD") || reconciliationType.contains("END_OF_DAY")
        );
    }

    private boolean isRegulatoryReconciliation(String reconciliationType) {
        return reconciliationType != null && (
            reconciliationType.contains("REGULATORY") || reconciliationType.contains("COMPLIANCE")
        );
    }

    private boolean isDataIntegrityAffectingReconciliation(String reconciliationType) {
        return reconciliationType != null && (
            reconciliationType.contains("BALANCE") || reconciliationType.contains("LEDGER") ||
            reconciliationType.contains("TRANSACTION") || reconciliationType.contains("ACCOUNT")
        );
    }

    private boolean isRegulatoryReportableReconciliation(String reconciliationType, String discrepancyAmount) {
        return isRegulatoryReconciliation(reconciliationType) || isHighDiscrepancy(discrepancyAmount);
    }

    // Data extraction helper methods
    private String extractReconciliationId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object reconciliationId = messageMap.get("reconciliationId");
                if (reconciliationId == null) reconciliationId = messageMap.get("id");
                return reconciliationId != null ? reconciliationId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract reconciliationId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractAccountId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object accountId = messageMap.get("accountId");
                if (accountId == null) accountId = messageMap.get("nostroAccountId");
                return accountId != null ? accountId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract accountId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractReconciliationType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object type = messageMap.get("reconciliationType");
                if (type == null) type = messageMap.get("type");
                return type != null ? type.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract reconciliationType from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractReconciliationDate(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object date = messageMap.get("reconciliationDate");
                if (date == null) date = messageMap.get("date");
                return date != null ? date.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract reconciliationDate from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractDiscrepancyAmount(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object amount = messageMap.get("discrepancyAmount");
                if (amount == null) amount = messageMap.get("discrepancy");
                return amount != null ? amount.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract discrepancyAmount from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractReconciliationStatus(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object status = messageMap.get("reconciliationStatus");
                if (status == null) status = messageMap.get("status");
                return status != null ? status.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract reconciliationStatus from message: {}", e.getMessage());
        }
        return null;
    }
}