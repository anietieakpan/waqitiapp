package com.waqiti.dispute.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.dispute.service.DisputeService;
import com.waqiti.dispute.service.ChargebackService;
import com.waqiti.dispute.repository.DisputeRepository;
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
import java.time.LocalDate;
import java.util.Map;

/**
 * DLQ Consumer for dispute resolution failures.
 * Handles critical dispute processing errors with regulatory compliance and customer protection.
 */
@Component
@Slf4j
public class DisputeResolutionDlqConsumer extends BaseDlqConsumer {

    private final DisputeService disputeService;
    private final ChargebackService chargebackService;
    private final DisputeRepository disputeRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public DisputeResolutionDlqConsumer(DlqHandler dlqHandler,
                                       AuditService auditService,
                                       NotificationService notificationService,
                                       MeterRegistry meterRegistry,
                                       DisputeService disputeService,
                                       ChargebackService chargebackService,
                                       DisputeRepository disputeRepository,
                                       KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.disputeService = disputeService;
        this.chargebackService = chargebackService;
        this.disputeRepository = disputeRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
        topics = {"dispute-resolution.DLQ"},
        groupId = "dispute-resolution-dlq-consumer-group",
        containerFactory = "criticalDisputeKafkaListenerContainerFactory"
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "dispute-resolution-dlq", fallbackMethod = "handleDisputeResolutionDlqFallback")
    public void handleDisputeResolutionDlq(@Payload Object originalMessage,
                                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                          @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                          @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                          @Header(KafkaHeaders.OFFSET) long offset,
                                          Acknowledgment acknowledgment,
                                          @Header Map<String, Object> headers) {

        log.info("Processing dispute resolution DLQ message: topic={}, partition={}, offset={}",
            topic, partition, offset);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String disputeId = extractDisputeId(originalMessage);
            String transactionId = extractTransactionId(originalMessage);
            String userId = extractUserId(originalMessage);
            String merchantId = extractMerchantId(originalMessage);
            BigDecimal amount = extractAmount(originalMessage);
            String currency = extractCurrency(originalMessage);
            String disputeType = extractDisputeType(originalMessage);
            String reasonCode = extractReasonCode(originalMessage);
            LocalDate dueDate = extractDueDate(originalMessage);
            String correlationId = extractCorrelationId(null, originalMessage);

            log.info("Processing dispute resolution DLQ: disputeId={}, txnId={}, amount={} {}, type={}, messageId={}",
                disputeId, transactionId, amount, currency, disputeType, messageId);

            // Validate dispute status and check for regulatory compliance
            if (disputeId != null) {
                validateDisputeStatus(disputeId, messageId);
                assessRegulatoryCompliance(disputeId, disputeType, dueDate, originalMessage, messageId);
                handleDisputeRecovery(disputeId, disputeType, originalMessage, exceptionMessage, messageId);
            }

            // Generate domain-specific alerts with regulatory urgency
            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Check for chargeback deadline impact
            assessChargebackDeadlines(disputeId, dueDate, reasonCode, originalMessage, messageId);

            // Handle specific dispute failure types
            handleSpecificDisputeFailure(disputeType, disputeId, originalMessage, messageId);

            // Trigger manual dispute review
            triggerManualDisputeReview(disputeId, transactionId, amount, disputeType, messageId);

        } catch (Exception e) {
            log.error("Error in dispute resolution DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "dispute-resolution-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "FINANCIAL_DISPUTES";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        BigDecimal amount = extractAmount(originalMessage);
        String disputeType = extractDisputeType(originalMessage);
        LocalDate dueDate = extractDueDate(originalMessage);

        // Critical if high-value dispute
        if (amount != null && amount.compareTo(new BigDecimal("10000")) > 0) {
            return true;
        }

        // Critical if near deadline
        if (dueDate != null && dueDate.isBefore(LocalDate.now().plusDays(3))) {
            return true;
        }

        // Critical dispute types
        return isCriticalDisputeType(disputeType);
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String disputeId = extractDisputeId(originalMessage);
        String transactionId = extractTransactionId(originalMessage);
        String userId = extractUserId(originalMessage);
        String merchantId = extractMerchantId(originalMessage);
        BigDecimal amount = extractAmount(originalMessage);
        String currency = extractCurrency(originalMessage);
        String disputeType = extractDisputeType(originalMessage);
        String reasonCode = extractReasonCode(originalMessage);
        LocalDate dueDate = extractDueDate(originalMessage);

        try {
            // IMMEDIATE escalation for dispute failures - these have regulatory and customer protection implications
            String alertTitle = String.format("REGULATORY CRITICAL: Dispute Resolution Failed - %s %s",
                amount != null ? amount : "Unknown", currency != null ? currency : "USD");
            String alertMessage = String.format(
                "⚖️ DISPUTE RESOLUTION ALERT ⚖️\n\n" +
                "A dispute resolution event has failed and requires IMMEDIATE attention:\n\n" +
                "Dispute ID: %s\n" +
                "Transaction ID: %s\n" +
                "User ID: %s\n" +
                "Merchant ID: %s\n" +
                "Amount: %s %s\n" +
                "Dispute Type: %s\n" +
                "Reason Code: %s\n" +
                "Due Date: %s\n" +
                "Error: %s\n\n" +
                "🚨 CRITICAL: This failure may result in regulatory violations or customer protection issues.\n" +
                "Immediate dispute operations and legal intervention required.",
                disputeId != null ? disputeId : "unknown",
                transactionId != null ? transactionId : "unknown",
                userId != null ? userId : "unknown",
                merchantId != null ? merchantId : "unknown",
                amount != null ? amount : "unknown",
                currency != null ? currency : "USD",
                disputeType != null ? disputeType : "unknown",
                reasonCode != null ? reasonCode : "unknown",
                dueDate != null ? dueDate : "unknown",
                exceptionMessage
            );

            // Send dispute ops alert for all dispute DLQ issues
            notificationService.sendDisputeOpsAlert(alertTitle, alertMessage, "CRITICAL");

            // Send specific legal alert
            notificationService.sendLegalAlert(
                "URGENT: Dispute Resolution DLQ",
                alertMessage,
                "HIGH"
            );

            // Send customer protection alert
            notificationService.sendCustomerProtectionAlert(
                "Customer Dispute Processing Failed",
                String.format("Customer dispute %s for user %s has failed processing. " +
                    "Review customer protection requirements and regulatory compliance.", disputeId, userId),
                "HIGH"
            );

            // Customer notification for dispute processing issue
            if (userId != null) {
                notificationService.sendNotification(userId,
                    "Dispute Processing Update",
                    String.format("We're experiencing a technical issue processing your dispute for %s %s. " +
                        "Our dispute resolution team is working to resolve this immediately. " +
                        "Your dispute rights remain protected throughout this process.",
                        amount != null ? amount : "the disputed amount", currency != null ? currency : ""),
                    messageId);
            }

            // Merchant notification for dispute processing
            if (merchantId != null) {
                notificationService.sendMerchantAlert(merchantId,
                    "Dispute Processing Issue",
                    String.format("Dispute processing issue for dispute %s (transaction %s). " +
                        "Our dispute operations team is addressing this immediately.", disputeId, transactionId),
                    "HIGH"
                );
            }

            // Compliance alert for regulatory requirements
            notificationService.sendComplianceAlert(
                "Dispute Regulatory Compliance Risk",
                String.format("Dispute %s processing failure may affect regulatory compliance. " +
                    "Review dispute handling requirements and timelines.", disputeId),
                "HIGH"
            );

            // Risk management alert for chargeback risk
            if (isChargebackRisk(disputeType, reasonCode)) {
                notificationService.sendRiskManagementAlert(
                    "Chargeback Risk from Dispute Failure",
                    String.format("Dispute %s failure may increase chargeback risk. " +
                        "Review merchant relationships and chargeback prevention.", disputeId),
                    "MEDIUM"
                );
            }

            // Deadline alert if time-sensitive
            if (dueDate != null && dueDate.isBefore(LocalDate.now().plusDays(2))) {
                notificationService.sendExecutiveAlert(
                    "URGENT: Time-Critical Dispute Failed",
                    String.format("Time-critical dispute %s (due: %s) has failed processing. " +
                        "Immediate executive intervention required to prevent deadline violation.", disputeId, dueDate)
                );
            }

        } catch (Exception e) {
            log.error("Failed to send dispute resolution DLQ notifications: {}", e.getMessage());
        }
    }

    private void validateDisputeStatus(String disputeId, String messageId) {
        try {
            var dispute = disputeRepository.findById(disputeId);
            if (dispute.isPresent()) {
                String status = dispute.get().getStatus();
                String phase = dispute.get().getPhase();

                log.info("Dispute status validation for DLQ: disputeId={}, status={}, phase={}, messageId={}",
                    disputeId, status, phase, messageId);

                // Check for critical dispute phases
                if ("REPRESENTMENT".equals(phase) || "ARBITRATION".equals(phase)) {
                    log.warn("Critical dispute phase in DLQ: disputeId={}, phase={}", disputeId, phase);

                    notificationService.sendLegalAlert(
                        "URGENT: Critical Dispute Phase Failed",
                        String.format("Dispute %s in critical phase %s has failed processing. " +
                            "Immediate legal review required to prevent case loss.", disputeId, phase),
                        "CRITICAL"
                    );
                }

                // Check for time-sensitive statuses
                if ("PENDING_RESPONSE".equals(status) || "AWAITING_EVIDENCE".equals(status)) {
                    notificationService.sendDisputeOpsAlert(
                        "Time-Sensitive Dispute Status Failed",
                        String.format("Time-sensitive dispute %s (status: %s) failed processing. " +
                            "Review response timeline requirements.", disputeId, status),
                        "HIGH"
                    );
                }
            } else {
                log.error("Dispute not found for DLQ: disputeId={}, messageId={}", disputeId, messageId);
            }
        } catch (Exception e) {
            log.error("Error validating dispute status for DLQ: disputeId={}, error={}",
                disputeId, e.getMessage());
        }
    }

    private void assessRegulatoryCompliance(String disputeId, String disputeType, LocalDate dueDate,
                                          Object originalMessage, String messageId) {
        try {
            log.info("Assessing regulatory compliance: disputeId={}, type={}, dueDate={}", disputeId, disputeType, dueDate);

            // Check for Regulation E compliance (Electronic Fund Transfers)
            if (isRegulationEDispute(disputeType)) {
                log.warn("Regulation E dispute failure: disputeId={}", disputeId);

                notificationService.sendComplianceAlert(
                    "CRITICAL: Regulation E Dispute Failed",
                    String.format("Regulation E dispute %s has failed processing. " +
                        "This may violate federal consumer protection regulations. " +
                        "Immediate compliance review required.", disputeId),
                    "CRITICAL"
                );

                // Create regulatory compliance incident
                kafkaTemplate.send("regulatory-compliance-incidents", Map.of(
                    "disputeId", disputeId,
                    "regulationType", "REGULATION_E",
                    "incidentType", "DISPUTE_DLQ_COMPLIANCE_RISK",
                    "severity", "HIGH",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

            // Check for Fair Credit Billing Act compliance
            if (isFairCreditBillingDispute(disputeType)) {
                notificationService.sendLegalAlert(
                    "Fair Credit Billing Act Dispute Failed",
                    String.format("FCBA dispute %s failed processing. " +
                        "Review federal credit billing protection requirements.", disputeId),
                    "HIGH"
                );
            }

            // Check for deadline compliance
            if (dueDate != null && dueDate.isBefore(LocalDate.now().plusDays(1))) {
                notificationService.sendExecutiveAlert(
                    "CRITICAL: Dispute Deadline Violation Risk",
                    String.format("Dispute %s (due: %s) failure creates deadline violation risk. " +
                        "Immediate executive escalation required.", disputeId, dueDate)
                );
            }

            // Assess chargeback network compliance (Visa/MC rules)
            if (isNetworkComplianceRequired(disputeType)) {
                boolean isCompliant = disputeService.assessNetworkCompliance(disputeId, disputeType);
                if (!isCompliant) {
                    notificationService.sendDisputeOpsAlert(
                        "Network Compliance Risk",
                        String.format("Dispute %s failure may violate card network compliance rules. " +
                            "Review Visa/Mastercard dispute handling requirements.", disputeId),
                        "MEDIUM"
                    );
                }
            }

        } catch (Exception e) {
            log.error("Error assessing regulatory compliance: disputeId={}, error={}", disputeId, e.getMessage());
        }
    }

    private void handleDisputeRecovery(String disputeId, String disputeType, Object originalMessage,
                                     String exceptionMessage, String messageId) {
        try {
            // Attempt automatic dispute recovery for recoverable failures
            boolean recoveryAttempted = disputeService.attemptDisputeRecovery(
                disputeId, disputeType, exceptionMessage);

            if (recoveryAttempted) {
                log.info("Automatic dispute recovery attempted: disputeId={}, type={}", disputeId, disputeType);

                kafkaTemplate.send("dispute-recovery-queue", Map.of(
                    "disputeId", disputeId,
                    "disputeType", disputeType,
                    "recoveryType", "AUTOMATIC_DLQ_RECOVERY",
                    "originalError", exceptionMessage,
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            } else {
                // Recovery not possible - escalate for manual intervention
                log.warn("Automatic dispute recovery not possible: disputeId={}", disputeId);

                notificationService.sendDisputeOpsAlert(
                    "Manual Dispute Recovery Required",
                    String.format("Dispute %s requires manual recovery intervention. " +
                        "Automatic recovery was not successful.", disputeId),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Error handling dispute recovery: disputeId={}, error={}", disputeId, e.getMessage());
        }
    }

    private void assessChargebackDeadlines(String disputeId, LocalDate dueDate, String reasonCode,
                                         Object originalMessage, String messageId) {
        try {
            if (dueDate != null) {
                // Check for imminent chargeback deadlines
                long daysUntilDue = LocalDate.now().until(dueDate).getDays();
                if (daysUntilDue <= 1) {
                    log.warn("Critical chargeback deadline for dispute DLQ: disputeId={}, daysLeft={}", disputeId, daysUntilDue);

                    notificationService.sendExecutiveAlert(
                        "CRITICAL: Chargeback Deadline Imminent",
                        String.format("Dispute %s has %d days until chargeback deadline and failed processing. " +
                            "Immediate intervention required to prevent automatic chargeback.", disputeId, daysUntilDue)
                    );

                    // Create emergency chargeback prevention task
                    kafkaTemplate.send("emergency-chargeback-prevention", Map.of(
                        "disputeId", disputeId,
                        "daysUntilDue", daysUntilDue,
                        "reasonCode", reasonCode,
                        "priority", "URGENT",
                        "messageId", messageId,
                        "timestamp", Instant.now()
                    ));
                }

                // Check for specific reason code deadlines
                if (hasStrictReasonCodeDeadline(reasonCode)) {
                    notificationService.sendDisputeOpsAlert(
                        "Strict Deadline Dispute Failed",
                        String.format("Dispute %s with strict deadline reason code %s failed. " +
                            "Review reason code specific requirements.", disputeId, reasonCode),
                        "HIGH"
                    );
                }
            }
        } catch (Exception e) {
            log.error("Error assessing chargeback deadlines: error={}", e.getMessage());
        }
    }

    private void handleSpecificDisputeFailure(String disputeType, String disputeId, Object originalMessage, String messageId) {
        try {
            switch (disputeType) {
                case "CHARGEBACK":
                    handleChargebackDisputeFailure(disputeId, originalMessage, messageId);
                    break;
                case "AUTHORIZATION":
                    handleAuthorizationDisputeFailure(disputeId, originalMessage, messageId);
                    break;
                case "PROCESSING_ERROR":
                    handleProcessingErrorDisputeFailure(disputeId, originalMessage, messageId);
                    break;
                case "CONSUMER_DISPUTE":
                    handleConsumerDisputeFailure(disputeId, originalMessage, messageId);
                    break;
                case "FRAUD_DISPUTE":
                    handleFraudDisputeFailure(disputeId, originalMessage, messageId);
                    break;
                case "NON_RECEIPT":
                    handleNonReceiptDisputeFailure(disputeId, originalMessage, messageId);
                    break;
                case "DUPLICATE_PROCESSING":
                    handleDuplicateProcessingDisputeFailure(disputeId, originalMessage, messageId);
                    break;
                default:
                    log.info("No specific handling for dispute type: {}", disputeType);
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling specific dispute failure: type={}, disputeId={}, error={}",
                disputeType, disputeId, e.getMessage());
        }
    }

    private void handleChargebackDisputeFailure(String disputeId, Object originalMessage, String messageId) {
        notificationService.sendDisputeOpsAlert(
            "Chargeback Dispute Failed",
            String.format("Chargeback dispute %s failed. Review chargeback response procedures and deadlines.", disputeId),
            "CRITICAL"
        );

        // Check chargeback response deadline
        kafkaTemplate.send("chargeback-deadline-check", Map.of(
            "disputeId", disputeId,
            "checkType", "DLQ_TRIGGERED_DEADLINE_CHECK",
            "messageId", messageId,
            "timestamp", Instant.now()
        ));
    }

    private void handleAuthorizationDisputeFailure(String disputeId, Object originalMessage, String messageId) {
        notificationService.sendDisputeOpsAlert(
            "Authorization Dispute Failed",
            String.format("Authorization dispute %s failed. Review authorization documentation requirements.", disputeId),
            "HIGH"
        );
    }

    private void handleProcessingErrorDisputeFailure(String disputeId, Object originalMessage, String messageId) {
        notificationService.sendTechnicalAlert(
            "Processing Error Dispute Failed",
            String.format("Processing error dispute %s failed. Review technical evidence and system logs.", disputeId),
            "MEDIUM"
        );
    }

    private void handleConsumerDisputeFailure(String disputeId, Object originalMessage, String messageId) {
        String userId = extractUserId(originalMessage);
        notificationService.sendCustomerProtectionAlert(
            "Consumer Dispute Failed",
            String.format("Consumer dispute %s for user %s failed. Review consumer protection compliance.", disputeId, userId),
            "HIGH"
        );
    }

    private void handleFraudDisputeFailure(String disputeId, Object originalMessage, String messageId) {
        notificationService.sendFraudAlert(
            "Fraud Dispute Failed",
            String.format("Fraud dispute %s failed. Review fraud evidence and investigation procedures.", disputeId),
            "HIGH"
        );
    }

    private void handleNonReceiptDisputeFailure(String disputeId, Object originalMessage, String messageId) {
        notificationService.sendDisputeOpsAlert(
            "Non-Receipt Dispute Failed",
            String.format("Non-receipt dispute %s failed. Review delivery confirmation requirements.", disputeId),
            "MEDIUM"
        );
    }

    private void handleDuplicateProcessingDisputeFailure(String disputeId, Object originalMessage, String messageId) {
        notificationService.sendTechnicalAlert(
            "Duplicate Processing Dispute Failed",
            String.format("Duplicate processing dispute %s failed. Review transaction deduplication evidence.", disputeId),
            "MEDIUM"
        );
    }

    private void triggerManualDisputeReview(String disputeId, String transactionId, BigDecimal amount,
                                          String disputeType, String messageId) {
        try {
            // All dispute DLQ messages require manual review due to regulatory implications
            kafkaTemplate.send("manual-dispute-review-queue", Map.of(
                "disputeId", disputeId,
                "transactionId", transactionId,
                "amount", amount != null ? amount.toString() : "unknown",
                "disputeType", disputeType,
                "reviewReason", "DLQ_PROCESSING_FAILURE",
                "priority", "HIGH",
                "messageId", messageId,
                "regulatoryCompliance", true,
                "timestamp", Instant.now()
            ));

            log.info("Triggered manual dispute review for DLQ: disputeId={}, txnId={}", disputeId, transactionId);
        } catch (Exception e) {
            log.error("Error triggering manual dispute review: disputeId={}, error={}", disputeId, e.getMessage());
        }
    }

    // Circuit breaker fallback method
    public void handleDisputeResolutionDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                                   int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String disputeId = extractDisputeId(originalMessage);
        String disputeType = extractDisputeType(originalMessage);

        // This is a CRITICAL situation - dispute system circuit breaker
        try {
            notificationService.sendExecutiveAlert(
                "CRITICAL SYSTEM FAILURE: Dispute DLQ Circuit Breaker",
                String.format("EMERGENCY: Dispute DLQ circuit breaker triggered for dispute %s (%s). " +
                    "This represents a complete failure of dispute processing systems. " +
                    "IMMEDIATE C-LEVEL AND LEGAL ESCALATION REQUIRED.", disputeId, disputeType)
            );

            // Mark as emergency legal issue
            disputeService.markEmergencyLegalIssue(disputeId, "CIRCUIT_BREAKER_DISPUTE_DLQ");

        } catch (Exception e) {
            log.error("Error in dispute resolution DLQ fallback: {}", e.getMessage());
        }
    }

    // Helper methods for classification
    private boolean isCriticalDisputeType(String disputeType) {
        return disputeType != null && (
            disputeType.contains("CHARGEBACK") || disputeType.contains("FRAUD") ||
            disputeType.contains("AUTHORIZATION") || disputeType.contains("CONSUMER")
        );
    }

    private boolean isChargebackRisk(String disputeType, String reasonCode) {
        return disputeType != null && disputeType.contains("CHARGEBACK") ||
               reasonCode != null && reasonCode.startsWith("4");
    }

    private boolean isRegulationEDispute(String disputeType) {
        return disputeType != null && (
            disputeType.contains("ELECTRONIC") || disputeType.contains("ATM") ||
            disputeType.contains("DEBIT") || disputeType.contains("EFT")
        );
    }

    private boolean isFairCreditBillingDispute(String disputeType) {
        return disputeType != null && (
            disputeType.contains("BILLING") || disputeType.contains("CREDIT") ||
            disputeType.contains("STATEMENT")
        );
    }

    private boolean isNetworkComplianceRequired(String disputeType) {
        return disputeType != null && (
            disputeType.contains("CHARGEBACK") || disputeType.contains("AUTHORIZATION")
        );
    }

    private boolean hasStrictReasonCodeDeadline(String reasonCode) {
        return reasonCode != null && (
            reasonCode.startsWith("83") || reasonCode.startsWith("4837") ||
            reasonCode.startsWith("4855")
        );
    }

    // Data extraction helper methods
    private String extractDisputeId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object disputeId = messageMap.get("disputeId");
                if (disputeId == null) disputeId = messageMap.get("id");
                return disputeId != null ? disputeId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract disputeId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractTransactionId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object transactionId = messageMap.get("transactionId");
                return transactionId != null ? transactionId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract transactionId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractUserId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object userId = messageMap.get("userId");
                if (userId == null) userId = messageMap.get("customerId");
                return userId != null ? userId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract userId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractMerchantId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object merchantId = messageMap.get("merchantId");
                return merchantId != null ? merchantId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract merchantId from message: {}", e.getMessage());
        }
        return null;
    }

    private BigDecimal extractAmount(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object amount = messageMap.get("amount");
                if (amount != null) {
                    return new BigDecimal(amount.toString());
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract amount from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractCurrency(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object currency = messageMap.get("currency");
                return currency != null ? currency.toString() : "USD";
            }
        } catch (Exception e) {
            log.debug("Could not extract currency from message: {}", e.getMessage());
        }
        return "USD";
    }

    private String extractDisputeType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object disputeType = messageMap.get("disputeType");
                if (disputeType == null) disputeType = messageMap.get("type");
                return disputeType != null ? disputeType.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract disputeType from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractReasonCode(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object reasonCode = messageMap.get("reasonCode");
                return reasonCode != null ? reasonCode.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract reasonCode from message: {}", e.getMessage());
        }
        return null;
    }

    private LocalDate extractDueDate(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object dueDate = messageMap.get("dueDate");
                if (dueDate != null) {
                    return LocalDate.parse(dueDate.toString());
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract dueDate from message: {}", e.getMessage());
        }
        return null;
    }
}