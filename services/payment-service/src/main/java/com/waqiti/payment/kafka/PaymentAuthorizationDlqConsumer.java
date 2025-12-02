package com.waqiti.payment.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.AuthorizationService;
import com.waqiti.payment.repository.AuthorizationRepository;
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
 * DLQ Consumer for payment authorization failures.
 * Handles critical payment authorization errors with fraud detection and risk assessment.
 */
@Component
@Slf4j
public class PaymentAuthorizationDlqConsumer extends BaseDlqConsumer {

    private final PaymentService paymentService;
    private final AuthorizationService authorizationService;
    private final AuthorizationRepository authorizationRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentAuthorizationDlqConsumer(DlqHandler dlqHandler,
                                          AuditService auditService,
                                          NotificationService notificationService,
                                          MeterRegistry meterRegistry,
                                          PaymentService paymentService,
                                          AuthorizationService authorizationService,
                                          AuthorizationRepository authorizationRepository,
                                          KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.paymentService = paymentService;
        this.authorizationService = authorizationService;
        this.authorizationRepository = authorizationRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
        topics = {"payment-authorization.DLQ"},
        groupId = "payment-authorization-dlq-consumer-group",
        containerFactory = "criticalPaymentKafkaListenerContainerFactory"
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "payment-authorization-dlq", fallbackMethod = "handlePaymentAuthorizationDlqFallback")
    public void handlePaymentAuthorizationDlq(@Payload Object originalMessage,
                                             @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                             @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                             @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                             @Header(KafkaHeaders.OFFSET) long offset,
                                             Acknowledgment acknowledgment,
                                             @Header Map<String, Object> headers) {

        log.info("Processing payment authorization DLQ message: topic={}, partition={}, offset={}",
            topic, partition, offset);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String authorizationId = extractAuthorizationId(originalMessage);
            String paymentId = extractPaymentId(originalMessage);
            String userId = extractUserId(originalMessage);
            BigDecimal amount = extractAmount(originalMessage);
            String currency = extractCurrency(originalMessage);
            String authorizationCode = extractAuthorizationCode(originalMessage);
            String processorCode = extractProcessorCode(originalMessage);
            String correlationId = extractCorrelationId(null, originalMessage);

            log.info("Processing payment authorization DLQ: authId={}, paymentId={}, amount={} {}, messageId={}",
                authorizationId, paymentId, amount, currency, messageId);

            // Validate authorization status and check for security implications
            if (authorizationId != null) {
                validateAuthorizationStatus(authorizationId, messageId);
                assessSecurityImpact(authorizationId, authorizationCode, originalMessage, messageId);
                handleAuthorizationRecovery(authorizationId, processorCode, originalMessage, exceptionMessage, messageId);
            }

            // Generate domain-specific alerts with security urgency
            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Check for fraud indicators
            assessFraudIndicators(authorizationId, userId, amount, originalMessage, messageId);

            // Handle specific authorization failure types
            handleSpecificAuthorizationFailure(processorCode, authorizationId, originalMessage, messageId);

            // Trigger manual authorization review
            triggerManualAuthorizationReview(authorizationId, paymentId, amount, messageId);

        } catch (Exception e) {
            log.error("Error in payment authorization DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "payment-authorization-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "FINANCIAL_AUTHORIZATION";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        BigDecimal amount = extractAmount(originalMessage);
        String authorizationCode = extractAuthorizationCode(originalMessage);
        String processorCode = extractProcessorCode(originalMessage);

        // Critical if high-value authorization
        if (amount != null && amount.compareTo(new BigDecimal("5000")) > 0) {
            return true;
        }

        // Critical authorization codes (declined for security reasons)
        if (isSecurityRelatedDecline(authorizationCode)) {
            return true;
        }

        // Critical processor codes
        return isCriticalProcessorCode(processorCode);
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String authorizationId = extractAuthorizationId(originalMessage);
        String paymentId = extractPaymentId(originalMessage);
        String userId = extractUserId(originalMessage);
        BigDecimal amount = extractAmount(originalMessage);
        String currency = extractCurrency(originalMessage);
        String authorizationCode = extractAuthorizationCode(originalMessage);
        String processorCode = extractProcessorCode(originalMessage);

        try {
            // IMMEDIATE escalation for authorization failures - these have security and fraud implications
            String alertTitle = String.format("SECURITY CRITICAL: Payment Authorization Failed - %s %s",
                amount != null ? amount : "Unknown", currency != null ? currency : "USD");
            String alertMessage = String.format(
                "🔒 AUTHORIZATION SECURITY ALERT 🔒\n\n" +
                "A payment authorization event has failed and requires IMMEDIATE attention:\n\n" +
                "Authorization ID: %s\n" +
                "Payment ID: %s\n" +
                "Amount: %s %s\n" +
                "Authorization Code: %s\n" +
                "Processor Code: %s\n" +
                "User ID: %s\n" +
                "Error: %s\n\n" +
                "🚨 CRITICAL: This failure may indicate security breach or fraud attempt.\n" +
                "Immediate security and fraud team intervention required.",
                authorizationId != null ? authorizationId : "unknown",
                paymentId != null ? paymentId : "unknown",
                amount != null ? amount : "unknown",
                currency != null ? currency : "USD",
                authorizationCode != null ? authorizationCode : "unknown",
                processorCode != null ? processorCode : "unknown",
                userId != null ? userId : "unknown",
                exceptionMessage
            );

            // Send fraud alert for all authorization DLQ issues
            notificationService.sendFraudAlert(alertTitle, alertMessage, "CRITICAL");

            // Send specific security alert
            notificationService.sendSecurityAlert(
                "URGENT: Authorization Security DLQ",
                alertMessage,
                "HIGH"
            );

            // Send payment ops alert
            notificationService.sendPaymentOpsAlert(
                "Authorization Processing Failure",
                String.format("Authorization %s for payment %s failed. Review authorization flow integrity.",
                    authorizationId, paymentId),
                "HIGH"
            );

            // Customer notification for failed authorization (if appropriate)
            if (userId != null && !isPotentialFraud(authorizationCode)) {
                notificationService.sendNotification(userId,
                    "Payment Authorization Issue",
                    String.format("We're experiencing an issue authorizing your payment of %s %s. " +
                        "This may be a temporary issue. Please try again or contact support.",
                        amount != null ? amount : "the requested amount", currency != null ? currency : ""),
                    messageId);
            }

            // Risk management alert for pattern analysis
            notificationService.sendRiskManagementAlert(
                "Authorization Pattern Alert",
                String.format("Authorization failure pattern detected for user %s. " +
                    "Review risk scoring and fraud indicators.", userId),
                "MEDIUM"
            );

            // Processor health alert if specific processor issues
            if (isProcessorHealthIssue(processorCode)) {
                notificationService.sendTechnicalAlert(
                    "Payment Processor Health Issue",
                    String.format("Processor %s showing authorization failures. " +
                        "Review processor connectivity and health.", processorCode),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Failed to send payment authorization DLQ notifications: {}", e.getMessage());
        }
    }

    private void validateAuthorizationStatus(String authorizationId, String messageId) {
        try {
            var authorization = authorizationRepository.findById(authorizationId);
            if (authorization.isPresent()) {
                String status = authorization.get().getStatus();
                String reason = authorization.get().getDeclineReason();

                log.info("Authorization status validation for DLQ: authId={}, status={}, reason={}, messageId={}",
                    authorizationId, status, reason, messageId);

                // Check for security-related declines
                if ("DECLINED".equals(status) && isSecurityDecline(reason)) {
                    log.warn("Security-related authorization decline in DLQ: authId={}, reason={}", authorizationId, reason);

                    notificationService.sendSecurityAlert(
                        "URGENT: Security Authorization Decline Failed",
                        String.format("Security-related authorization decline %s (reason: %s) has failed processing. " +
                            "Immediate security review required.", authorizationId, reason),
                        "CRITICAL"
                    );
                }

                // Check for processor timeout scenarios
                if ("TIMEOUT".equals(status)) {
                    notificationService.sendTechnicalAlert(
                        "Authorization Timeout Alert",
                        String.format("Authorization %s timed out and failed DLQ processing. " +
                            "Review processor connectivity.", authorizationId),
                        "HIGH"
                    );
                }
            } else {
                log.error("Authorization not found for DLQ: authId={}, messageId={}", authorizationId, messageId);
            }
        } catch (Exception e) {
            log.error("Error validating authorization status for DLQ: authId={}, error={}",
                authorizationId, e.getMessage());
        }
    }

    private void assessSecurityImpact(String authorizationId, String authorizationCode, Object originalMessage, String messageId) {
        try {
            log.info("Assessing security impact: authId={}, code={}", authorizationId, authorizationCode);

            // Check for fraud indicators
            if (authorizationCode != null && isFraudIndicator(authorizationCode)) {
                log.warn("Fraud indicator in authorization DLQ: authId={}, code={}", authorizationId, authorizationCode);

                notificationService.sendFraudAlert(
                    "CRITICAL: Fraud Indicator in Authorization DLQ",
                    String.format("Authorization %s with fraud indicator code %s has failed processing. " +
                        "Immediate fraud investigation required.", authorizationId, authorizationCode),
                    "CRITICAL"
                );

                // Create fraud investigation case
                kafkaTemplate.send("fraud-investigation-queue", Map.of(
                    "authorizationId", authorizationId,
                    "investigationType", "AUTHORIZATION_DLQ_FRAUD",
                    "fraudIndicator", authorizationCode,
                    "priority", "HIGH",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

            // Check for stolen card indicators
            if (authorizationCode != null && isStolenCardIndicator(authorizationCode)) {
                notificationService.sendSecurityAlert(
                    "CRITICAL: Stolen Card Authorization Failed",
                    String.format("Authorization %s indicates stolen card activity and failed processing. " +
                        "Immediate card security team escalation required.", authorizationId),
                    "CRITICAL"
                );
            }

            // Check for velocity rule violations
            if (isVelocityViolation(authorizationCode)) {
                notificationService.sendRiskManagementAlert(
                    "Velocity Rule Violation in DLQ",
                    String.format("Authorization %s failed due to velocity rule violation. " +
                        "Review velocity controls and user behavior.", authorizationId),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Error assessing security impact: authId={}, error={}", authorizationId, e.getMessage());
        }
    }

    private void handleAuthorizationRecovery(String authorizationId, String processorCode, Object originalMessage,
                                           String exceptionMessage, String messageId) {
        try {
            // Attempt automatic authorization recovery for recoverable failures
            boolean recoveryAttempted = authorizationService.attemptAuthorizationRecovery(
                authorizationId, processorCode, exceptionMessage);

            if (recoveryAttempted) {
                log.info("Automatic authorization recovery attempted: authId={}, processor={}", authorizationId, processorCode);

                kafkaTemplate.send("authorization-recovery-queue", Map.of(
                    "authorizationId", authorizationId,
                    "recoveryType", "AUTOMATIC_DLQ_RECOVERY",
                    "processorCode", processorCode,
                    "originalError", exceptionMessage,
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            } else {
                // Recovery not possible - escalate for manual intervention
                log.warn("Automatic authorization recovery not possible: authId={}", authorizationId);

                notificationService.sendPaymentOpsAlert(
                    "Manual Authorization Recovery Required",
                    String.format("Authorization %s requires manual recovery intervention. " +
                        "Automatic recovery was not successful.", authorizationId),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Error handling authorization recovery: authId={}, error={}", authorizationId, e.getMessage());
        }
    }

    private void assessFraudIndicators(String authorizationId, String userId, BigDecimal amount,
                                     Object originalMessage, String messageId) {
        try {
            if (userId != null) {
                // Check for user fraud patterns
                boolean hasFraudPattern = authorizationService.hasFraudPattern(userId, authorizationId);
                if (hasFraudPattern) {
                    log.warn("Fraud pattern detected in authorization DLQ: authId={}, userId={}",
                        authorizationId, userId);

                    notificationService.sendFraudAlert(
                        "Fraud Pattern Detected in Authorization DLQ",
                        String.format("User %s shows fraud patterns in failed authorization %s. " +
                            "Immediate fraud investigation required.", userId, authorizationId),
                        "HIGH"
                    );

                    // Create fraud case
                    kafkaTemplate.send("fraud-case-queue", Map.of(
                        "userId", userId,
                        "authorizationId", authorizationId,
                        "caseType", "AUTHORIZATION_DLQ_PATTERN",
                        "severity", "HIGH",
                        "messageId", messageId,
                        "timestamp", Instant.now()
                    ));
                }

                // Check for high-risk velocity
                if (amount != null && authorizationService.isHighRiskVelocity(userId, amount)) {
                    notificationService.sendRiskManagementAlert(
                        "High-Risk Velocity in Authorization DLQ",
                        String.format("High-risk velocity detected for user %s in failed authorization. " +
                            "Review velocity controls.", userId),
                        "MEDIUM"
                    );
                }
            }
        } catch (Exception e) {
            log.error("Error assessing fraud indicators: error={}", e.getMessage());
        }
    }

    private void handleSpecificAuthorizationFailure(String processorCode, String authorizationId, Object originalMessage, String messageId) {
        try {
            switch (processorCode) {
                case "VISA":
                    handleVisaAuthorizationFailure(authorizationId, originalMessage, messageId);
                    break;
                case "MASTERCARD":
                    handleMastercardAuthorizationFailure(authorizationId, originalMessage, messageId);
                    break;
                case "AMEX":
                    handleAmexAuthorizationFailure(authorizationId, originalMessage, messageId);
                    break;
                case "DISCOVER":
                    handleDiscoverAuthorizationFailure(authorizationId, originalMessage, messageId);
                    break;
                case "INTERNAL":
                    handleInternalAuthorizationFailure(authorizationId, originalMessage, messageId);
                    break;
                default:
                    log.info("No specific handling for processor: {}", processorCode);
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling specific authorization failure: processor={}, authId={}, error={}",
                processorCode, authorizationId, e.getMessage());
        }
    }

    private void handleVisaAuthorizationFailure(String authorizationId, Object originalMessage, String messageId) {
        notificationService.sendPaymentOpsAlert(
            "Visa Authorization Failed",
            String.format("Visa authorization %s failed. Review Visa network connectivity and rules.", authorizationId),
            "HIGH"
        );

        // Check Visa network health
        kafkaTemplate.send("visa-network-health-check", Map.of(
            "authorizationId", authorizationId,
            "checkType", "DLQ_TRIGGERED_HEALTH_CHECK",
            "messageId", messageId,
            "timestamp", Instant.now()
        ));
    }

    private void handleMastercardAuthorizationFailure(String authorizationId, Object originalMessage, String messageId) {
        notificationService.sendPaymentOpsAlert(
            "Mastercard Authorization Failed",
            String.format("Mastercard authorization %s failed. Review Mastercard network status.", authorizationId),
            "HIGH"
        );
    }

    private void handleAmexAuthorizationFailure(String authorizationId, Object originalMessage, String messageId) {
        notificationService.sendPaymentOpsAlert(
            "American Express Authorization Failed",
            String.format("American Express authorization %s failed. Review Amex connectivity.", authorizationId),
            "MEDIUM"
        );
    }

    private void handleDiscoverAuthorizationFailure(String authorizationId, Object originalMessage, String messageId) {
        notificationService.sendPaymentOpsAlert(
            "Discover Authorization Failed",
            String.format("Discover authorization %s failed. Review Discover network status.", authorizationId),
            "MEDIUM"
        );
    }

    private void handleInternalAuthorizationFailure(String authorizationId, Object originalMessage, String messageId) {
        notificationService.sendTechnicalAlert(
            "Internal Authorization System Failed",
            String.format("Internal authorization %s failed. Review internal authorization engine health.", authorizationId),
            "CRITICAL"
        );
    }

    private void triggerManualAuthorizationReview(String authorizationId, String paymentId, BigDecimal amount, String messageId) {
        try {
            // All authorization DLQ messages require manual review due to security implications
            kafkaTemplate.send("manual-authorization-review-queue", Map.of(
                "authorizationId", authorizationId,
                "paymentId", paymentId,
                "amount", amount != null ? amount.toString() : "unknown",
                "reviewReason", "DLQ_PROCESSING_FAILURE",
                "priority", "HIGH",
                "messageId", messageId,
                "securityReview", true,
                "timestamp", Instant.now()
            ));

            log.info("Triggered manual authorization review for DLQ: authId={}, paymentId={}", authorizationId, paymentId);
        } catch (Exception e) {
            log.error("Error triggering manual authorization review: authId={}, error={}", authorizationId, e.getMessage());
        }
    }

    // Circuit breaker fallback method
    public void handlePaymentAuthorizationDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                                     int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String authorizationId = extractAuthorizationId(originalMessage);
        String paymentId = extractPaymentId(originalMessage);

        // This is a CRITICAL situation - authorization system circuit breaker
        try {
            notificationService.sendExecutiveAlert(
                "CRITICAL SYSTEM FAILURE: Authorization DLQ Circuit Breaker",
                String.format("EMERGENCY: Authorization DLQ circuit breaker triggered for auth %s, payment %s. " +
                    "This represents a complete failure of authorization processing systems. " +
                    "IMMEDIATE C-LEVEL AND SECURITY ESCALATION REQUIRED.", authorizationId, paymentId)
            );

            // Mark as emergency security issue
            authorizationService.markEmergencySecurityIssue(authorizationId, "CIRCUIT_BREAKER_AUTHORIZATION_DLQ");

        } catch (Exception e) {
            log.error("Error in payment authorization DLQ fallback: {}", e.getMessage());
        }
    }

    // Helper methods for classification
    private boolean isSecurityRelatedDecline(String authorizationCode) {
        return authorizationCode != null && (
            authorizationCode.contains("FRAUD") || authorizationCode.contains("STOLEN") ||
            authorizationCode.contains("SECURITY") || authorizationCode.contains("RESTRICTED")
        );
    }

    private boolean isCriticalProcessorCode(String processorCode) {
        return processorCode != null && (
            processorCode.contains("VISA") || processorCode.contains("MASTERCARD") ||
            processorCode.contains("INTERNAL")
        );
    }

    private boolean isPotentialFraud(String authorizationCode) {
        return authorizationCode != null && (
            authorizationCode.contains("FRAUD") || authorizationCode.contains("SUSPICIOUS") ||
            authorizationCode.contains("BLOCKED")
        );
    }

    private boolean isSecurityDecline(String reason) {
        return reason != null && (
            reason.contains("FRAUD") || reason.contains("STOLEN") ||
            reason.contains("BLOCKED") || reason.contains("RESTRICTED")
        );
    }

    private boolean isFraudIndicator(String authorizationCode) {
        return authorizationCode != null && (
            authorizationCode.equals("FRAUD_SUSPECTED") || authorizationCode.equals("VELOCITY_EXCEEDED") ||
            authorizationCode.equals("PATTERN_MATCH") || authorizationCode.equals("HIGH_RISK")
        );
    }

    private boolean isStolenCardIndicator(String authorizationCode) {
        return authorizationCode != null && (
            authorizationCode.equals("STOLEN_CARD") || authorizationCode.equals("LOST_CARD") ||
            authorizationCode.equals("PICK_UP_CARD")
        );
    }

    private boolean isVelocityViolation(String authorizationCode) {
        return authorizationCode != null && (
            authorizationCode.contains("VELOCITY") || authorizationCode.contains("FREQUENCY") ||
            authorizationCode.contains("LIMIT_EXCEEDED")
        );
    }

    private boolean isProcessorHealthIssue(String processorCode) {
        return processorCode != null && !processorCode.equals("INTERNAL");
    }

    // Data extraction helper methods
    private String extractAuthorizationId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object authId = messageMap.get("authorizationId");
                if (authId == null) authId = messageMap.get("authId");
                return authId != null ? authId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract authorizationId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractPaymentId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object paymentId = messageMap.get("paymentId");
                return paymentId != null ? paymentId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract paymentId from message: {}", e.getMessage());
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

    private String extractAuthorizationCode(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object authCode = messageMap.get("authorizationCode");
                if (authCode == null) authCode = messageMap.get("responseCode");
                return authCode != null ? authCode.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract authorizationCode from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractProcessorCode(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object processorCode = messageMap.get("processorCode");
                if (processorCode == null) processorCode = messageMap.get("processor");
                return processorCode != null ? processorCode.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract processorCode from message: {}", e.getMessage());
        }
        return null;
    }
}