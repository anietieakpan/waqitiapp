package com.waqiti.frauddetection.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.frauddetection.service.FraudDetectionService;
import com.waqiti.frauddetection.service.FraudValidationService;
import com.waqiti.frauddetection.repository.FraudDetectionRepository;
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
 * DLQ Consumer for fraud detection failures.
 * Handles critical fraud detection processing errors with immediate security escalation.
 */
@Component
@Slf4j
public class FraudDetectionDlqConsumer extends BaseDlqConsumer {

    private final FraudDetectionService fraudDetectionService;
    private final FraudValidationService fraudValidationService;
    private final FraudDetectionRepository fraudDetectionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public FraudDetectionDlqConsumer(DlqHandler dlqHandler,
                                   AuditService auditService,
                                   NotificationService notificationService,
                                   MeterRegistry meterRegistry,
                                   FraudDetectionService fraudDetectionService,
                                   FraudValidationService fraudValidationService,
                                   FraudDetectionRepository fraudDetectionRepository,
                                   KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.fraudDetectionService = fraudDetectionService;
        this.fraudValidationService = fraudValidationService;
        this.fraudDetectionRepository = fraudDetectionRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
        topics = {"fraud-detection.DLQ"},
        groupId = "fraud-detection-dlq-consumer-group",
        containerFactory = "criticalFraudKafkaListenerContainerFactory"
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "fraud-detection-dlq", fallbackMethod = "handleFraudDetectionDlqFallback")
    public void handleFraudDetectionDlq(@Payload Object originalMessage,
                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                      @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                      @Header(KafkaHeaders.OFFSET) long offset,
                                      Acknowledgment acknowledgment,
                                      @Header Map<String, Object> headers) {

        log.info("Processing fraud detection DLQ message: topic={}, partition={}, offset={}",
            topic, partition, offset);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String detectionId = extractDetectionId(originalMessage);
            String transactionId = extractTransactionId(originalMessage);
            String customerId = extractCustomerId(originalMessage);
            String fraudType = extractFraudType(originalMessage);
            String riskScore = extractRiskScore(originalMessage);
            String detectionStatus = extractDetectionStatus(originalMessage);
            String correlationId = extractCorrelationId(null, originalMessage);

            log.info("Processing fraud detection DLQ: detectionId={}, transactionId={}, customerId={}, fraudType={}, riskScore={}, messageId={}",
                detectionId, transactionId, customerId, fraudType, riskScore, messageId);

            // Validate fraud detection state and integrity
            if (detectionId != null || transactionId != null) {
                validateFraudDetectionState(detectionId, transactionId, messageId);
                assessFraudDetectionIntegrity(detectionId, originalMessage, messageId);
                handleFraudDetectionRecovery(detectionId, transactionId, customerId, originalMessage, exceptionMessage, messageId);
            }

            // Generate critical security alerts
            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Check for immediate security threat
            assessSecurityThreatLevel(fraudType, riskScore, customerId, originalMessage, messageId);

            // Handle customer protection measures
            handleCustomerProtectionMeasures(customerId, fraudType, transactionId, originalMessage, messageId);

            // Trigger manual fraud investigation
            triggerManualFraudInvestigation(detectionId, transactionId, customerId, fraudType, riskScore, messageId);

        } catch (Exception e) {
            log.error("Error in fraud detection DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "fraud-detection-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "FRAUD_PREVENTION";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        String fraudType = extractFraudType(originalMessage);
        String riskScore = extractRiskScore(originalMessage);

        // All fraud detection failures are critical for security
        if (isHighRiskFraud(riskScore)) {
            return true;
        }

        // Critical fraud types always escalated
        if (isCriticalFraudType(fraudType)) {
            return true;
        }

        // Account takeover and identity theft are critical
        return isAccountCompromiseFraud(fraudType);
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String detectionId = extractDetectionId(originalMessage);
        String transactionId = extractTransactionId(originalMessage);
        String customerId = extractCustomerId(originalMessage);
        String fraudType = extractFraudType(originalMessage);
        String riskScore = extractRiskScore(originalMessage);
        String detectionStatus = extractDetectionStatus(originalMessage);

        try {
            // CRITICAL security escalation for fraud detection failures
            String alertTitle = String.format("SECURITY CRITICAL: Fraud Detection Failed - %s",
                fraudType != null ? fraudType : "Unknown Fraud Type");
            String alertMessage = String.format(
                "🚨 FRAUD DETECTION SYSTEM FAILURE 🚨\n\n" +
                "A fraud detection operation has FAILED and requires IMMEDIATE security attention:\n\n" +
                "Detection ID: %s\n" +
                "Transaction ID: %s\n" +
                "Customer ID: %s\n" +
                "Fraud Type: %s\n" +
                "Risk Score: %s\n" +
                "Detection Status: %s\n" +
                "Error: %s\n\n" +
                "🚨 CRITICAL: Fraud detection failures can cause:\n" +
                "- Undetected fraudulent activity\n" +
                "- Customer financial losses\n" +
                "- Regulatory compliance violations\n" +
                "- Reputational damage\n" +
                "- Security system compromise\n\n" +
                "IMMEDIATE fraud operations and security escalation required.",
                detectionId != null ? detectionId : "unknown",
                transactionId != null ? transactionId : "unknown",
                customerId != null ? customerId : "unknown",
                fraudType != null ? fraudType : "unknown",
                riskScore != null ? riskScore : "unknown",
                detectionStatus != null ? detectionStatus : "unknown",
                exceptionMessage
            );

            // Send fraud operations alert
            notificationService.sendFraudAlert(alertTitle, alertMessage, "CRITICAL");

            // Send security operations alert
            notificationService.sendSecurityAlert(
                "URGENT: Fraud Detection System Failed",
                alertMessage,
                "CRITICAL"
            );

            // Send risk management alert
            notificationService.sendRiskManagementAlert(
                "Fraud Detection Risk Alert",
                String.format("Fraud detection failure for customer %s may expose security vulnerabilities. " +
                    "Review fraud detection infrastructure and customer protection.", customerId),
                "HIGH"
            );

            // High-risk fraud specific alerts
            if (isHighRiskFraud(riskScore)) {
                notificationService.sendHighRiskAlert(
                    "HIGH RISK FRAUD DETECTION FAILED",
                    String.format("High-risk fraud detection %s (score: %s) failed for customer %s. " +
                        "IMMEDIATE security review and customer protection measures required.", detectionId, riskScore, customerId),
                    "CRITICAL"
                );
            }

            // Account takeover specific alerts
            if (isAccountTakeoverFraud(fraudType)) {
                notificationService.sendAccountSecurityAlert(
                    "Account Takeover Detection Failed",
                    String.format("Account takeover detection failed for customer %s. " +
                        "IMMEDIATE account security review and customer notification required.", customerId),
                    "CRITICAL"
                );
            }

            // Identity theft specific alerts
            if (isIdentityTheftFraud(fraudType)) {
                notificationService.sendIdentitySecurityAlert(
                    "Identity Theft Detection Failed",
                    String.format("Identity theft detection failed for customer %s. " +
                        "Review identity verification and customer protection procedures.", customerId),
                    "HIGH"
                );
            }

            // Transaction fraud specific alerts
            if (isTransactionFraud(fraudType)) {
                notificationService.sendTransactionSecurityAlert(
                    "Transaction Fraud Detection Failed",
                    String.format("Transaction fraud detection failed for transaction %s. " +
                        "Review transaction monitoring and authorization procedures.", transactionId),
                    "HIGH"
                );
            }

            // Customer service alert for customer impact
            notificationService.sendCustomerServiceAlert(
                "Customer Security Issue",
                String.format("Customer %s may be at risk due to fraud detection system failure. " +
                    "Monitor customer account for suspicious activity and provide enhanced support.", customerId),
                "HIGH"
            );

            // Compliance alert for regulatory requirements
            notificationService.sendComplianceAlert(
                "Fraud Detection Compliance Risk",
                String.format("Fraud detection failure may impact regulatory compliance. " +
                    "Review fraud monitoring requirements and customer protection obligations."),
                "MEDIUM"
            );

            // Technology escalation for system integrity
            notificationService.sendTechnologyAlert(
                "Fraud Detection System Alert",
                String.format("Fraud detection system failure may indicate infrastructure issues. " +
                    "Detection: %s, Customer: %s", detectionId, customerId),
                "HIGH"
            );

        } catch (Exception e) {
            log.error("Failed to send fraud detection DLQ notifications: {}", e.getMessage());
        }
    }

    private void validateFraudDetectionState(String detectionId, String transactionId, String messageId) {
        try {
            if (detectionId != null) {
                var detection = fraudDetectionRepository.findById(detectionId);
                if (detection.isPresent()) {
                    String status = detection.get().getStatus();
                    String state = detection.get().getState();

                    log.info("Fraud detection state validation: detectionId={}, status={}, state={}, messageId={}",
                        detectionId, status, state, messageId);

                    // Check for critical detection states
                    if ("PROCESSING".equals(status) || "ANALYZING".equals(status)) {
                        log.warn("Critical fraud detection state in DLQ: detectionId={}, status={}", detectionId, status);

                        notificationService.sendFraudAlert(
                            "CRITICAL: Fraud Detection State Inconsistency",
                            String.format("Fraud detection %s in critical state %s found in DLQ. " +
                                "Immediate fraud detection state reconciliation required.", detectionId, status),
                            "CRITICAL"
                        );
                    }

                    // Check for security-sensitive states
                    if ("HIGH_RISK_DETECTED".equals(state) || "THREAT_IDENTIFIED".equals(state)) {
                        notificationService.sendSecurityAlert(
                            "Security Critical Detection Failed",
                            String.format("Fraud detection %s with security state %s failed. " +
                                "IMMEDIATE security intervention and customer protection required.", detectionId, state),
                            "CRITICAL"
                        );
                    }
                } else {
                    log.warn("Fraud detection not found despite detectionId present: detectionId={}, messageId={}",
                        detectionId, messageId);
                }
            }

        } catch (Exception e) {
            log.error("Error validating fraud detection state: detectionId={}, error={}",
                detectionId, e.getMessage());
        }
    }

    private void assessFraudDetectionIntegrity(String detectionId, Object originalMessage, String messageId) {
        try {
            // Validate fraud detection data integrity
            boolean integrityValid = fraudValidationService.validateFraudDetectionIntegrity(detectionId);
            if (!integrityValid) {
                log.error("Fraud detection integrity validation failed: detectionId={}", detectionId);

                notificationService.sendFraudAlert(
                    "CRITICAL: Fraud Detection Data Integrity Failure",
                    String.format("Fraud detection %s failed data integrity validation in DLQ processing. " +
                        "Immediate fraud detection consistency review required.", detectionId),
                    "CRITICAL"
                );

                // Create security integrity incident
                kafkaTemplate.send("security-integrity-incidents", Map.of(
                    "detectionId", detectionId != null ? detectionId : "unknown",
                    "incidentType", "FRAUD_DETECTION_INTEGRITY_FAILURE",
                    "severity", "CRITICAL",
                    "securityImpact", true,
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

        } catch (Exception e) {
            log.error("Error assessing fraud detection integrity: detectionId={}, error={}",
                detectionId, e.getMessage());
        }
    }

    private void handleFraudDetectionRecovery(String detectionId, String transactionId, String customerId,
                                            Object originalMessage, String exceptionMessage, String messageId) {
        try {
            // Attempt automatic fraud detection recovery
            boolean recoveryAttempted = fraudDetectionService.attemptFraudDetectionRecovery(
                detectionId, transactionId, customerId, exceptionMessage);

            if (recoveryAttempted) {
                log.info("Automatic fraud detection recovery attempted: detectionId={}, transactionId={}, customerId={}",
                    detectionId, transactionId, customerId);

                kafkaTemplate.send("fraud-detection-recovery-queue", Map.of(
                    "detectionId", detectionId != null ? detectionId : "unknown",
                    "transactionId", transactionId != null ? transactionId : "unknown",
                    "customerId", customerId != null ? customerId : "unknown",
                    "recoveryType", "AUTOMATIC_DLQ_RECOVERY",
                    "originalError", exceptionMessage,
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            } else {
                log.warn("Automatic fraud detection recovery not possible: detectionId={}", detectionId);

                notificationService.sendFraudAlert(
                    "Manual Fraud Detection Recovery Required",
                    String.format("Fraud detection %s requires manual recovery intervention. " +
                        "Automatic recovery was not successful.", detectionId),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Error handling fraud detection recovery: detectionId={}, error={}",
                detectionId, e.getMessage());
        }
    }

    private void assessSecurityThreatLevel(String fraudType, String riskScore, String customerId,
                                         Object originalMessage, String messageId) {
        try {
            if (isHighSecurityThreat(fraudType, riskScore)) {
                log.warn("High security threat fraud detection failure: customerId={}, fraudType={}, riskScore={}",
                    customerId, fraudType, riskScore);

                notificationService.sendSecurityAlert(
                    "CRITICAL: High Security Threat Detection Failed",
                    String.format("High security threat fraud detection (type: %s, risk: %s) failed for customer %s. " +
                        "This represents immediate security risk. " +
                        "EMERGENCY security team response and customer protection required.",
                        fraudType, riskScore, customerId),
                    "CRITICAL"
                );

                // Create security threat incident
                kafkaTemplate.send("security-threat-incidents", Map.of(
                    "customerId", customerId != null ? customerId : "unknown",
                    "fraudType", fraudType,
                    "riskScore", riskScore,
                    "incidentType", "HIGH_THREAT_DETECTION_FAILURE",
                    "threatLevel", "HIGH",
                    "securityRisk", true,
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

        } catch (Exception e) {
            log.error("Error assessing security threat level: customerId={}, error={}",
                customerId, e.getMessage());
        }
    }

    private void handleCustomerProtectionMeasures(String customerId, String fraudType, String transactionId,
                                                 Object originalMessage, String messageId) {
        try {
            if (customerId != null && requiresImmediateProtection(fraudType)) {
                log.warn("Customer requires immediate protection due to fraud detection failure: customerId={}, fraudType={}",
                    customerId, fraudType);

                // Trigger customer protection measures
                kafkaTemplate.send("customer-protection-queue", Map.of(
                    "customerId", customerId,
                    "transactionId", transactionId != null ? transactionId : "unknown",
                    "fraudType", fraudType,
                    "protectionReason", "FRAUD_DETECTION_FAILURE",
                    "urgency", "HIGH",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));

                // Send customer notification
                notificationService.sendCustomerSecurityAlert(
                    "Account Security Review",
                    String.format("We're reviewing your account security as a precautionary measure. " +
                        "If you notice any suspicious activity, please contact us immediately."),
                    customerId
                );

                // Alert customer service for enhanced monitoring
                notificationService.sendCustomerServiceAlert(
                    "Enhanced Customer Monitoring Required",
                    String.format("Customer %s requires enhanced monitoring due to fraud detection system failure. " +
                        "Monitor for suspicious activity and provide priority support.", customerId),
                    "HIGH"
                );
            }
        } catch (Exception e) {
            log.error("Error handling customer protection measures: customerId={}, error={}",
                customerId, e.getMessage());
        }
    }

    private void triggerManualFraudInvestigation(String detectionId, String transactionId, String customerId,
                                               String fraudType, String riskScore, String messageId) {
        try {
            // All fraud detection DLQ requires manual investigation due to security impact
            kafkaTemplate.send("manual-fraud-investigation-queue", Map.of(
                "detectionId", detectionId != null ? detectionId : "unknown",
                "transactionId", transactionId != null ? transactionId : "unknown",
                "customerId", customerId != null ? customerId : "unknown",
                "fraudType", fraudType,
                "riskScore", riskScore,
                "investigationReason", "FRAUD_DETECTION_DLQ_FAILURE",
                "priority", "HIGH",
                "messageId", messageId,
                "securityImpact", true,
                "requiresImmediateReview", true,
                "timestamp", Instant.now()
            ));

            log.info("Triggered manual fraud investigation for DLQ: detectionId={}, customerId={}", detectionId, customerId);
        } catch (Exception e) {
            log.error("Error triggering manual fraud investigation: detectionId={}, error={}",
                detectionId, e.getMessage());
        }
    }

    // Circuit breaker fallback method
    public void handleFraudDetectionDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                               int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String detectionId = extractDetectionId(originalMessage);
        String customerId = extractCustomerId(originalMessage);
        String fraudType = extractFraudType(originalMessage);

        // EMERGENCY situation - fraud detection circuit breaker
        try {
            notificationService.sendExecutiveAlert(
                "EMERGENCY: Fraud Detection DLQ Circuit Breaker",
                String.format("CRITICAL SECURITY FAILURE: Fraud detection DLQ circuit breaker triggered " +
                    "for detection %s (customer %s, fraud type %s). " +
                    "This represents complete failure of fraud detection systems. " +
                    "IMMEDIATE C-LEVEL, SECURITY, AND FRAUD OPERATIONS ESCALATION REQUIRED.",
                    detectionId, customerId, fraudType)
            );

            // Mark as emergency security issue
            fraudDetectionService.markEmergencySecurityIssue(detectionId, customerId, "CIRCUIT_BREAKER_FRAUD_DETECTION_DLQ");

        } catch (Exception e) {
            log.error("Error in fraud detection DLQ fallback: {}", e.getMessage());
        }
    }

    // Helper methods for fraud classification
    private boolean isHighRiskFraud(String riskScore) {
        try {
            if (riskScore != null) {
                BigDecimal score = new BigDecimal(riskScore);
                return score.compareTo(new BigDecimal("80")) >= 0; // 80+ risk score
            }
        } catch (Exception e) {
            log.debug("Could not parse risk score: {}", riskScore);
        }
        return false;
    }

    private boolean isCriticalFraudType(String fraudType) {
        return fraudType != null && (
            fraudType.contains("ACCOUNT_TAKEOVER") || fraudType.contains("IDENTITY_THEFT") ||
            fraudType.contains("CARD_SKIMMING") || fraudType.contains("MONEY_LAUNDERING")
        );
    }

    private boolean isAccountCompromiseFraud(String fraudType) {
        return fraudType != null && (
            fraudType.contains("ACCOUNT_TAKEOVER") || fraudType.contains("CREDENTIAL_THEFT") ||
            fraudType.contains("SESSION_HIJACKING")
        );
    }

    private boolean isAccountTakeoverFraud(String fraudType) {
        return fraudType != null && fraudType.contains("ACCOUNT_TAKEOVER");
    }

    private boolean isIdentityTheftFraud(String fraudType) {
        return fraudType != null && (
            fraudType.contains("IDENTITY_THEFT") || fraudType.contains("IDENTITY_FRAUD")
        );
    }

    private boolean isTransactionFraud(String fraudType) {
        return fraudType != null && (
            fraudType.contains("TRANSACTION_FRAUD") || fraudType.contains("PAYMENT_FRAUD")
        );
    }

    private boolean isHighSecurityThreat(String fraudType, String riskScore) {
        return isCriticalFraudType(fraudType) || isHighRiskFraud(riskScore);
    }

    private boolean requiresImmediateProtection(String fraudType) {
        return fraudType != null && (
            fraudType.contains("ACCOUNT_TAKEOVER") || fraudType.contains("IDENTITY_THEFT") ||
            fraudType.contains("CREDENTIAL_THEFT")
        );
    }

    // Data extraction helper methods
    private String extractDetectionId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object detectionId = messageMap.get("detectionId");
                if (detectionId == null) detectionId = messageMap.get("id");
                if (detectionId == null) detectionId = messageMap.get("fraudDetectionId");
                return detectionId != null ? detectionId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract detectionId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractTransactionId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object transactionId = messageMap.get("transactionId");
                if (transactionId == null) transactionId = messageMap.get("txnId");
                return transactionId != null ? transactionId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract transactionId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractCustomerId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object customerId = messageMap.get("customerId");
                if (customerId == null) customerId = messageMap.get("userId");
                return customerId != null ? customerId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract customerId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractFraudType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object fraudType = messageMap.get("fraudType");
                if (fraudType == null) fraudType = messageMap.get("type");
                return fraudType != null ? fraudType.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract fraudType from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractRiskScore(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object riskScore = messageMap.get("riskScore");
                if (riskScore == null) riskScore = messageMap.get("score");
                return riskScore != null ? riskScore.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract riskScore from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractDetectionStatus(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object status = messageMap.get("detectionStatus");
                if (status == null) status = messageMap.get("status");
                return status != null ? status.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract detectionStatus from message: {}", e.getMessage());
        }
        return null;
    }
}