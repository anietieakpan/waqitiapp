package com.waqiti.wallet.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.wallet.service.WalletLimitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * P0 DLQ Consumer for wallet limit exceeded failures.
 * Handles critical limit enforcement operations that protect against fraud and regulatory violations.
 */
@Component
@Slf4j
public class WalletLimitExceededNewDlqConsumer extends BaseDlqConsumer {

    private final WalletLimitService limitService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public WalletLimitExceededNewDlqConsumer(DlqHandler dlqHandler,
                                        AuditService auditService,
                                        NotificationService notificationService,
                                        MeterRegistry meterRegistry,
                                        WalletLimitService limitService,
                                        KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.limitService = limitService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 5000, multiplier = 2.0),
        include = {Exception.class}
    )
    @KafkaListener(
        topics = {"wallet-limit-exceeded-dlq"},
        groupId = "wallet-limit-exceeded-new-dlq-consumer-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation.level=read_committed",
            "spring.kafka.consumer.enable.auto.commit=false"
        }
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "wallet-limit-exceeded-new-dlq", fallbackMethod = "handleWalletLimitExceededDlqFallback")
    public void handleWalletLimitExceededDlq(@Payload Object originalMessage,
                                           @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                           @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                           @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                           @Header(KafkaHeaders.OFFSET) long offset,
                                           Acknowledgment acknowledgment,
                                           @Header Map<String, Object> headers) {

        log.info("Processing wallet limit exceeded DLQ message: topic={}, partition={}, offset={}",
            topic, partition, offset);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String walletId = extractWalletId(originalMessage);
            String customerId = extractCustomerId(originalMessage);
            BigDecimal attemptedAmount = extractAttemptedAmount(originalMessage);
            BigDecimal limitAmount = extractLimitAmount(originalMessage);
            String limitType = extractLimitType(originalMessage);
            String transactionId = extractTransactionId(originalMessage);

            log.info("Processing wallet limit exceeded DLQ: walletId={}, customerId={}, attempted={}, limit={}, type={}, messageId={}",
                walletId, customerId, attemptedAmount, limitAmount, limitType, messageId);

            // Check for potential fraud patterns
            assessFraudRisk(walletId, customerId, attemptedAmount, limitType, originalMessage, messageId);

            // Handle regulatory compliance
            handleRegulatoryCompliance(walletId, customerId, attemptedAmount, limitType, originalMessage, messageId);

            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

        } catch (Exception e) {
            log.error("Error in wallet limit exceeded DLQ processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "wallet-limit-exceeded-new-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "WALLET_COMPLIANCE";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        BigDecimal attemptedAmount = extractAttemptedAmount(originalMessage);
        String limitType = extractLimitType(originalMessage);

        // High-value limit breaches are critical
        if (attemptedAmount != null && attemptedAmount.compareTo(new BigDecimal("10000")) >= 0) {
            return true;
        }

        // Daily/monthly limits are critical for AML compliance
        return "DAILY_LIMIT".equals(limitType) || "MONTHLY_LIMIT".equals(limitType);
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String walletId = extractWalletId(originalMessage);
        String customerId = extractCustomerId(originalMessage);
        BigDecimal attemptedAmount = extractAttemptedAmount(originalMessage);
        BigDecimal limitAmount = extractLimitAmount(originalMessage);
        String limitType = extractLimitType(originalMessage);

        try {
            String alertMessage = String.format(
                "🚫 WALLET LIMIT EXCEEDED FAILURE 🚫\n\n" +
                "Critical limit enforcement failed:\n" +
                "Wallet: %s\nCustomer: %s\nAttempted: $%s\nLimit: $%s\nType: %s\nError: %s\n\n" +
                "May indicate fraud attempt or system compromise.",
                walletId, customerId, attemptedAmount, limitAmount, limitType, exceptionMessage);

            notificationService.sendComplianceAlert("CRITICAL: Limit Enforcement Failed", alertMessage, "CRITICAL");

            // Send fraud alert for high-value attempts
            if (attemptedAmount != null && attemptedAmount.compareTo(new BigDecimal("5000")) >= 0) {
                notificationService.sendFraudAlert(
                    "High-Value Limit Breach Failed",
                    String.format("High-value transaction ($%s) limit enforcement failed for customer %s. " +
                        "Review for fraud indicators.", attemptedAmount, customerId),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Failed to send wallet limit exceeded DLQ notifications: {}", e.getMessage());
        }
    }

    private void assessFraudRisk(String walletId, String customerId, BigDecimal attemptedAmount,
                               String limitType, Object originalMessage, String messageId) {
        try {
            if (attemptedAmount != null && attemptedAmount.compareTo(new BigDecimal("1000")) >= 0) {
                kafkaTemplate.send("fraud-assessment-queue", Map.of(
                    "walletId", walletId != null ? walletId : "unknown",
                    "customerId", customerId != null ? customerId : "unknown",
                    "attemptedAmount", attemptedAmount.toString(),
                    "limitType", limitType,
                    "assessmentReason", "LIMIT_EXCEEDED_DLQ_FAILURE",
                    "priority", "HIGH",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }
        } catch (Exception e) {
            log.error("Error assessing fraud risk: {}", e.getMessage());
        }
    }

    private void handleRegulatoryCompliance(String walletId, String customerId, BigDecimal attemptedAmount,
                                          String limitType, Object originalMessage, String messageId) {
        try {
            if (attemptedAmount != null && attemptedAmount.compareTo(new BigDecimal("10000")) >= 0) {
                kafkaTemplate.send("regulatory-compliance-queue", Map.of(
                    "walletId", walletId != null ? walletId : "unknown",
                    "customerId", customerId != null ? customerId : "unknown",
                    "attemptedAmount", attemptedAmount.toString(),
                    "limitType", limitType,
                    "complianceReason", "HIGH_VALUE_LIMIT_BREACH_FAILURE",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }
        } catch (Exception e) {
            log.error("Error handling regulatory compliance: {}", e.getMessage());
        }
    }

    public void handleWalletLimitExceededDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                                   int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String customerId = extractCustomerId(originalMessage);
        BigDecimal attemptedAmount = extractAttemptedAmount(originalMessage);

        try {
            notificationService.sendExecutiveAlert(
                "EMERGENCY: Wallet Limit Exceeded DLQ Circuit Breaker",
                String.format("Limit enforcement circuit breaker triggered for customer %s (amount: $%s). " +
                    "Critical compliance system failure.", customerId, attemptedAmount)
            );
        } catch (Exception e) {
            log.error("Error in wallet limit exceeded DLQ fallback: {}", e.getMessage());
        }
    }

    // Data extraction methods
    private String extractWalletId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object walletId = messageMap.get("walletId");
                return walletId != null ? walletId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract walletId: {}", e.getMessage());
        }
        return null;
    }

    private String extractCustomerId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object customerId = messageMap.get("customerId");
                return customerId != null ? customerId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract customerId: {}", e.getMessage());
        }
        return null;
    }

    private BigDecimal extractAttemptedAmount(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object amount = messageMap.get("attemptedAmount");
                if (amount == null) amount = messageMap.get("amount");
                if (amount instanceof Number) {
                    return new BigDecimal(amount.toString());
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract attemptedAmount: {}", e.getMessage());
        }
        return null;
    }

    private BigDecimal extractLimitAmount(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object limitAmount = messageMap.get("limitAmount");
                if (limitAmount instanceof Number) {
                    return new BigDecimal(limitAmount.toString());
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract limitAmount: {}", e.getMessage());
        }
        return null;
    }

    private String extractLimitType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object limitType = messageMap.get("limitType");
                return limitType != null ? limitType.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract limitType: {}", e.getMessage());
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
            log.debug("Could not extract transactionId: {}", e.getMessage());
        }
        return null;
    }
}