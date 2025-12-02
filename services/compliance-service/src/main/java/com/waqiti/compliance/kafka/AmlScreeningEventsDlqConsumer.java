package com.waqiti.compliance.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.compliance.service.AmlService;
import com.waqiti.compliance.service.SanctionsScreeningService;
import com.waqiti.compliance.repository.AmlScreeningRepository;
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

import java.time.Instant;
import java.util.Map;

/**
 * DLQ Consumer for AML screening events that failed to process.
 * Handles critical anti-money laundering screening failures with immediate escalation.
 */
@Component
@Slf4j
public class AmlScreeningEventsDlqConsumer extends BaseDlqConsumer {

    private final AmlService amlService;
    private final SanctionsScreeningService sanctionsScreeningService;
    private final AmlScreeningRepository amlScreeningRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AmlScreeningEventsDlqConsumer(DlqHandler dlqHandler,
                                        AuditService auditService,
                                        NotificationService notificationService,
                                        MeterRegistry meterRegistry,
                                        AmlService amlService,
                                        SanctionsScreeningService sanctionsScreeningService,
                                        AmlScreeningRepository amlScreeningRepository,
                                        KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.amlService = amlService;
        this.sanctionsScreeningService = sanctionsScreeningService;
        this.amlScreeningRepository = amlScreeningRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
        topics = {"aml-screening-events-dlq"},
        groupId = "aml-screening-dlq-consumer-group",
        containerFactory = "criticalComplianceKafkaListenerContainerFactory"
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "aml-screening-dlq", fallbackMethod = "handleAmlScreeningDlqFallback")
    public void handleAmlScreeningDlq(@Payload Object originalMessage,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                     @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                     @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                     @Header(KafkaHeaders.OFFSET) long offset,
                                     Acknowledgment acknowledgment,
                                     @Header Map<String, Object> headers) {

        log.info("Processing AML screening DLQ message: topic={}, partition={}, offset={}",
            topic, partition, offset);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String screeningId = extractScreeningId(originalMessage);
            String userId = extractUserId(originalMessage);
            String screeningType = extractScreeningType(originalMessage);
            String riskScore = extractRiskScore(originalMessage);
            String transactionId = extractTransactionId(originalMessage);
            String correlationId = extractCorrelationId(null, originalMessage);

            log.info("Processing AML screening DLQ: screeningId={}, userId={}, type={}, riskScore={}, messageId={}",
                screeningId, userId, screeningType, riskScore, messageId);

            // Validate AML screening status
            if (screeningId != null) {
                validateAmlScreeningStatus(screeningId, messageId);
                assessAmlRiskImpact(screeningId, userId, riskScore, originalMessage, messageId);
                handleAmlComplianceFailure(screeningId, screeningType, originalMessage, exceptionMessage, messageId);
            }

            // Generate domain-specific alerts - AML failures are ALWAYS critical
            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Handle customer/transaction blocking if high risk
            assessCustomerBlockingRequirement(userId, transactionId, screeningType, riskScore, messageId);

            // Handle specific AML screening failures
            handleSpecificAmlFailure(screeningType, screeningId, userId, originalMessage, messageId);

            // Trigger immediate manual AML review
            triggerEmergencyAmlReview(screeningId, userId, screeningType, riskScore, messageId);

        } catch (Exception e) {
            log.error("Error in AML screening DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "aml-screening-events-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "AML_COMPLIANCE";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        // ALL AML screening failures are critical due to regulatory implications
        return true;
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String screeningId = extractScreeningId(originalMessage);
        String userId = extractUserId(originalMessage);
        String screeningType = extractScreeningType(originalMessage);
        String riskScore = extractRiskScore(originalMessage);
        String transactionId = extractTransactionId(originalMessage);

        try {
            // AML failures require IMMEDIATE executive and regulatory attention
            String alertTitle = "🚨 CRITICAL AML SCREENING FAILURE 🚨";
            String alertMessage = String.format(
                "⚠️ REGULATORY EMERGENCY ⚠️\n\n" +
                "An AML screening process has FAILED and requires IMMEDIATE attention:\n\n" +
                "Screening ID: %s\n" +
                "User ID: %s\n" +
                "Screening Type: %s\n" +
                "Risk Score: %s\n" +
                "Transaction ID: %s\n" +
                "Error: %s\n\n" +
                "🚨 CRITICAL IMPACT:\n" +
                "• Potential money laundering risk exposure\n" +
                "• Regulatory violation risk (BSA/AML)\n" +
                "• Customer may not be properly screened\n" +
                "• Transaction may require blocking\n\n" +
                "🚨 IMMEDIATE ACTIONS REQUIRED:\n" +
                "1. Manual AML review initiated\n" +
                "2. Customer/transaction status evaluation\n" +
                "3. Regulatory notification assessment\n" +
                "4. Legal team consultation",
                screeningId != null ? screeningId : "unknown",
                userId != null ? userId : "unknown",
                screeningType != null ? screeningType : "unknown",
                riskScore != null ? riskScore : "unknown",
                transactionId != null ? transactionId : "unknown",
                exceptionMessage
            );

            // Send IMMEDIATE executive alert
            notificationService.sendExecutiveAlert(alertTitle, alertMessage);

            // Send AML compliance team alert
            notificationService.sendAmlAlert(
                "URGENT: AML Screening System Failure",
                alertMessage,
                "CRITICAL"
            );

            // Send FinCEN/BSA officer alert
            notificationService.sendBsaOfficerAlert(
                "BSA/AML Screening Failure",
                String.format("AML screening %s failed for user %s. BSA compliance review required.",
                    screeningId, userId),
                "URGENT"
            );

            // Send legal team alert for regulatory implications
            notificationService.sendLegalAlert(
                "AML Compliance Risk - Legal Review Required",
                String.format("AML screening failure may constitute BSA violation. " +
                    "Review: Screening ID %s, User %s", screeningId, userId),
                "CRITICAL"
            );

            // Send risk management alert
            notificationService.sendRiskManagementAlert(
                "AML Risk Exposure",
                String.format("AML screening failure creates money laundering risk exposure. " +
                    "Risk score: %s, User: %s", riskScore, userId),
                "CRITICAL"
            );

            // Send operations alert for immediate action
            notificationService.sendOperationalAlert(
                "AML Operations - Immediate Action Required",
                String.format("AML screening failed for user %s. Manual screening required immediately. " +
                    "Consider transaction blocking pending review.", userId),
                "CRITICAL"
            );

            // Customer impact notification (if transaction blocked)
            if (shouldBlockTransaction(riskScore, screeningType) && userId != null) {
                notificationService.sendNotification(userId,
                    "Transaction Security Review",
                    "Your transaction is being reviewed for security compliance. " +
                    "This is a routine security measure. We'll contact you if additional information is needed.",
                    messageId);
            }

        } catch (Exception e) {
            log.error("Failed to send AML screening DLQ alerts: {}", e.getMessage());
        }
    }

    private void validateAmlScreeningStatus(String screeningId, String messageId) {
        try {
            var amlScreening = amlScreeningRepository.findById(screeningId);
            if (amlScreening.isPresent()) {
                String status = amlScreening.get().getStatus();
                String riskLevel = amlScreening.get().getRiskLevel();

                log.info("AML screening status validation for DLQ: screeningId={}, status={}, riskLevel={}, messageId={}",
                    screeningId, status, riskLevel, messageId);

                // Check for high-risk screenings
                if ("HIGH_RISK".equals(riskLevel) || "SUSPICIOUS".equals(riskLevel)) {
                    log.error("HIGH RISK AML screening in DLQ: screeningId={}, riskLevel={}", screeningId, riskLevel);

                    notificationService.sendExecutiveAlert(
                        "EMERGENCY: High-Risk AML Screening Failed",
                        String.format("High-risk AML screening %s (risk: %s) has failed processing. " +
                            "This represents critical money laundering risk exposure.", screeningId, riskLevel)
                    );
                }

                // Check for pending screenings
                if ("PENDING_REVIEW".equals(status) || "IN_PROGRESS".equals(status)) {
                    notificationService.sendAmlAlert(
                        "Active AML Screening Failed",
                        String.format("Active AML screening %s has failed. Customer/transaction may be unscreened.", screeningId),
                        "CRITICAL"
                    );
                }
            } else {
                log.error("AML screening not found for DLQ: screeningId={}, messageId={}", screeningId, messageId);
            }
        } catch (Exception e) {
            log.error("Error validating AML screening status for DLQ: screeningId={}, error={}",
                screeningId, e.getMessage());
        }
    }

    private void assessAmlRiskImpact(String screeningId, String userId, String riskScore,
                                   Object originalMessage, String messageId) {
        try {
            log.info("Assessing AML risk impact: screeningId={}, userId={}, riskScore={}", screeningId, userId, riskScore);

            // Parse risk score if available
            if (riskScore != null) {
                try {
                    double score = Double.parseDouble(riskScore);
                    if (score > 70.0) { // High risk threshold
                        log.error("HIGH RISK AML screening DLQ: screeningId={}, score={}", screeningId, score);

                        notificationService.sendExecutiveAlert(
                            "CRITICAL: High-Risk AML Screening Failed",
                            String.format("AML screening with high risk score (%.1f) failed for user %s. " +
                                "Immediate investigation required to prevent potential money laundering.", score, userId)
                        );

                        // Trigger immediate manual investigation
                        kafkaTemplate.send("emergency-aml-investigation", Map.of(
                            "screeningId", screeningId,
                            "userId", userId,
                            "riskScore", score,
                            "reason", "HIGH_RISK_SCREENING_DLQ",
                            "priority", "EMERGENCY",
                            "messageId", messageId,
                            "timestamp", Instant.now()
                        ));
                    }
                } catch (NumberFormatException e) {
                    log.warn("Could not parse risk score: {}", riskScore);
                }
            }

            // Check customer's AML history
            if (userId != null) {
                boolean hasAmlHistory = amlService.hasAmlHistory(userId);
                if (hasAmlHistory) {
                    log.warn("AML screening DLQ for customer with AML history: userId={}", userId);

                    notificationService.sendAmlAlert(
                        "AML History Customer - Screening Failed",
                        String.format("AML screening failed for customer %s who has prior AML history. " +
                            "Enhanced monitoring required.", userId),
                        "HIGH"
                    );
                }
            }

            // Check for sanctions match potential
            boolean potentialSanctionsMatch = sanctionsScreeningService.hasPotentialMatch(userId);
            if (potentialSanctionsMatch) {
                notificationService.sendExecutiveAlert(
                    "EMERGENCY: Potential Sanctions Match - AML Screening Failed",
                    String.format("AML screening failed for user %s with potential sanctions match. " +
                        "OFAC violation risk - immediate blocking required.", userId)
                );
            }

        } catch (Exception e) {
            log.error("Error assessing AML risk impact: screeningId={}, error={}", screeningId, e.getMessage());
        }
    }

    private void handleAmlComplianceFailure(String screeningId, String screeningType, Object originalMessage,
                                          String exceptionMessage, String messageId) {
        try {
            // Record AML compliance failure for regulatory audit
            amlService.recordAmlComplianceFailure(screeningId, Map.of(
                "failureType", "AML_SCREENING_DLQ",
                "screeningType", screeningType,
                "errorMessage", exceptionMessage,
                "messageId", messageId,
                "timestamp", Instant.now(),
                "regulatoryImpact", "BSA_AML_VIOLATION_RISK",
                "requiresBsaNotification", true
            ));

            // Check if this failure requires SAR consideration
            boolean requiresSarConsideration = amlService.requiresSarConsideration(screeningType, screeningId);
            if (requiresSarConsideration) {
                log.warn("AML screening DLQ may require SAR filing: screeningId={}, screeningType={}",
                    screeningId, screeningType);

                // Alert BSA officer for SAR review
                notificationService.sendBsaOfficerAlert(
                    "SAR Review Required - AML Screening Failure",
                    String.format("AML screening failure for %s may require SAR filing. " +
                        "Review suspicious activity criteria.", screeningId),
                    "HIGH"
                );

                // Create SAR review record
                kafkaTemplate.send("sar-review-queue", Map.of(
                    "screeningId", screeningId,
                    "reviewType", "AML_SCREENING_FAILURE",
                    "priority", "HIGH",
                    "sarOfficerReviewRequired", true,
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

        } catch (Exception e) {
            log.error("Error handling AML compliance failure: screeningId={}, error={}", screeningId, e.getMessage());
        }
    }

    private void assessCustomerBlockingRequirement(String userId, String transactionId, String screeningType,
                                                 String riskScore, String messageId) {
        try {
            if (userId != null && shouldBlockTransaction(riskScore, screeningType)) {
                log.warn("Customer/transaction blocking required due to AML screening DLQ: userId={}, transactionId={}",
                    userId, transactionId);

                // Block customer pending manual review
                amlService.blockCustomerPendingReview(userId, Map.of(
                    "blockReason", "AML_SCREENING_DLQ",
                    "riskScore", riskScore,
                    "screeningType", screeningType,
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));

                // Block specific transaction if applicable
                if (transactionId != null) {
                    kafkaTemplate.send("transaction-blocking-queue", Map.of(
                        "transactionId", transactionId,
                        "userId", userId,
                        "blockReason", "AML_SCREENING_FAILURE",
                        "messageId", messageId,
                        "timestamp", Instant.now()
                    ));
                }

                notificationService.sendOperationalAlert(
                    "Customer Blocked - AML Screening Failure",
                    String.format("Customer %s blocked due to AML screening failure. " +
                        "Manual review required for unblocking.", userId),
                    "HIGH"
                );
            }
        } catch (Exception e) {
            log.error("Error assessing customer blocking requirement: userId={}, error={}", userId, e.getMessage());
        }
    }

    private void handleSpecificAmlFailure(String screeningType, String screeningId, String userId,
                                        Object originalMessage, String messageId) {
        try {
            switch (screeningType) {
                case "CUSTOMER_ONBOARDING":
                    handleOnboardingAmlFailure(screeningId, userId, originalMessage, messageId);
                    break;
                case "TRANSACTION_MONITORING":
                    handleTransactionMonitoringFailure(screeningId, userId, originalMessage, messageId);
                    break;
                case "SANCTIONS_SCREENING":
                    handleSanctionsScreeningFailure(screeningId, userId, originalMessage, messageId);
                    break;
                case "PEP_SCREENING":
                    handlePepScreeningFailure(screeningId, userId, originalMessage, messageId);
                    break;
                case "ONGOING_MONITORING":
                    handleOngoingMonitoringFailure(screeningId, userId, originalMessage, messageId);
                    break;
                default:
                    log.info("No specific handling for AML screening type: {}", screeningType);
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling specific AML failure: screeningType={}, screeningId={}, error={}",
                screeningType, screeningId, e.getMessage());
        }
    }

    private void handleOnboardingAmlFailure(String screeningId, String userId, Object originalMessage, String messageId) {
        notificationService.sendAmlAlert(
            "Customer Onboarding AML Failed",
            String.format("AML screening failed during customer onboarding for user %s. " +
                "Customer may be onboarded without proper AML screening.", userId),
            "CRITICAL"
        );

        // Block customer account pending manual review
        amlService.blockNewCustomerAccount(userId, "ONBOARDING_AML_FAILURE");
    }

    private void handleTransactionMonitoringFailure(String screeningId, String userId, Object originalMessage, String messageId) {
        String transactionId = extractTransactionId(originalMessage);
        notificationService.sendAmlAlert(
            "Transaction Monitoring AML Failed",
            String.format("Transaction monitoring AML failed for user %s, transaction %s. " +
                "Suspicious transactions may go undetected.", userId, transactionId),
            "HIGH"
        );
    }

    private void handleSanctionsScreeningFailure(String screeningId, String userId, Object originalMessage, String messageId) {
        notificationService.sendExecutiveAlert(
            "EMERGENCY: Sanctions Screening Failed",
            String.format("Sanctions screening failed for user %s. " +
                "OFAC violation risk - immediate investigation required.", userId)
        );
    }

    private void handlePepScreeningFailure(String screeningId, String userId, Object originalMessage, String messageId) {
        notificationService.sendAmlAlert(
            "PEP Screening Failed",
            String.format("PEP screening failed for user %s. " +
                "Enhanced due diligence requirements may not be met.", userId),
            "HIGH"
        );
    }

    private void handleOngoingMonitoringFailure(String screeningId, String userId, Object originalMessage, String messageId) {
        notificationService.sendAmlAlert(
            "Ongoing AML Monitoring Failed",
            String.format("Ongoing AML monitoring failed for user %s. " +
                "Continuous compliance monitoring compromised.", userId),
            "MEDIUM"
        );
    }

    private void triggerEmergencyAmlReview(String screeningId, String userId, String screeningType,
                                         String riskScore, String messageId) {
        try {
            // ALL AML DLQ messages require emergency manual review
            kafkaTemplate.send("emergency-aml-review-queue", Map.of(
                "screeningId", screeningId,
                "userId", userId,
                "screeningType", screeningType,
                "riskScore", riskScore,
                "reviewReason", "AML_SCREENING_DLQ",
                "priority", "EMERGENCY",
                "bsaOfficerReviewRequired", true,
                "messageId", messageId,
                "timestamp", Instant.now()
            ));

            log.warn("Triggered emergency AML review for DLQ: screeningId={}, userId={}", screeningId, userId);
        } catch (Exception e) {
            log.error("Error triggering emergency AML review: screeningId={}, error={}", screeningId, e.getMessage());
        }
    }

    // Circuit breaker fallback method
    public void handleAmlScreeningDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                             int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String screeningId = extractScreeningId(originalMessage);
        String userId = extractUserId(originalMessage);

        // This is a CATASTROPHIC situation - AML system complete failure
        try {
            notificationService.sendExecutiveAlert(
                "🚨 EMERGENCY: AML SYSTEM COMPLETE FAILURE 🚨",
                String.format("CATASTROPHIC SYSTEM FAILURE: AML DLQ circuit breaker triggered for screening %s (user: %s). " +
                    "COMPLETE AML SYSTEM FAILURE - IMMEDIATE C-LEVEL AND REGULATORY ESCALATION REQUIRED. " +
                    "BSA/AML COMPLIANCE IS COMPROMISED.", screeningId, userId)
            );

            // Mark as emergency BSA issue
            amlService.markEmergencyBsaIssue(screeningId, "CIRCUIT_BREAKER_AML_DLQ");

            // Notify regulators if required
            notificationService.sendRegulatoryAlert(
                "EMERGENCY BSA/AML System Failure",
                "AML screening system has experienced complete failure. Regulatory notification may be required.",
                "EMERGENCY"
            );

        } catch (Exception e) {
            log.error("Error in AML screening DLQ fallback: {}", e.getMessage());
        }
    }

    // Helper methods
    private boolean shouldBlockTransaction(String riskScore, String screeningType) {
        if (riskScore != null) {
            try {
                double score = Double.parseDouble(riskScore);
                return score > 70.0; // High risk threshold
            } catch (NumberFormatException e) {
                // If we can't parse risk score, err on the side of caution
                return true;
            }
        }
        // Block for certain screening types
        return "SANCTIONS_SCREENING".equals(screeningType) || "PEP_SCREENING".equals(screeningType);
    }

    // Data extraction helper methods
    private String extractScreeningId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object screeningId = messageMap.get("screeningId");
                if (screeningId == null) screeningId = messageMap.get("amlScreeningId");
                return screeningId != null ? screeningId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract screeningId from message: {}", e.getMessage());
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

    private String extractScreeningType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object screeningType = messageMap.get("screeningType");
                if (screeningType == null) screeningType = messageMap.get("type");
                return screeningType != null ? screeningType.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract screeningType from message: {}", e.getMessage());
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
}