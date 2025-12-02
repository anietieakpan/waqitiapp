package com.waqiti.wallet.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.wallet.service.WalletService;
import com.waqiti.wallet.service.BalanceService;
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
 * P0 DLQ Consumer for balance update failures.
 * Handles critical balance synchronization operations that ensure financial accuracy.
 */
@Component
@Slf4j
public class BalanceUpdatesDlqConsumer extends BaseDlqConsumer {

    private final WalletService walletService;
    private final BalanceService balanceService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public BalanceUpdatesDlqConsumer(DlqHandler dlqHandler,
                                   AuditService auditService,
                                   NotificationService notificationService,
                                   MeterRegistry meterRegistry,
                                   WalletService walletService,
                                   BalanceService balanceService,
                                   KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.walletService = walletService;
        this.balanceService = balanceService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 5000, multiplier = 2.0),
        include = {Exception.class}
    )
    @KafkaListener(
        topics = {"balance-updates-dlq"},
        groupId = "balance-updates-dlq-consumer-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation.level=read_committed",
            "spring.kafka.consumer.enable.auto.commit=false"
        }
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "balance-updates-dlq", fallbackMethod = "handleBalanceUpdatesDlqFallback")
    public void handleBalanceUpdatesDlq(@Payload Object originalMessage,
                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                      @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                      @Header(KafkaHeaders.OFFSET) long offset,
                                      Acknowledgment acknowledgment,
                                      @Header Map<String, Object> headers) {

        log.info("Processing balance updates DLQ message: topic={}, partition={}, offset={}",
            topic, partition, offset);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String walletId = extractWalletId(originalMessage);
            String customerId = extractCustomerId(originalMessage);
            BigDecimal amount = extractAmount(originalMessage);
            String updateType = extractUpdateType(originalMessage);
            String transactionId = extractTransactionId(originalMessage);

            log.info("Processing balance update DLQ: walletId={}, customerId={}, amount={}, type={}, messageId={}",
                walletId, customerId, amount, updateType, messageId);

            // Validate balance consistency and reconciliation
            if (walletId != null) {
                validateBalanceConsistency(walletId, amount, messageId);
                performBalanceReconciliation(walletId, customerId, amount, originalMessage, messageId);
            }

            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Trigger balance integrity check
            triggerBalanceIntegrityCheck(walletId, customerId, amount, updateType, messageId);

        } catch (Exception e) {
            log.error("Error in balance updates DLQ processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "balance-updates-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "WALLET_BALANCE";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        BigDecimal amount = extractAmount(originalMessage);
        String updateType = extractUpdateType(originalMessage);

        // Any balance update failure is critical for financial integrity
        return amount != null && amount.compareTo(BigDecimal.ZERO) != 0;
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String walletId = extractWalletId(originalMessage);
        String customerId = extractCustomerId(originalMessage);
        BigDecimal amount = extractAmount(originalMessage);
        String updateType = extractUpdateType(originalMessage);

        try {
            String alertMessage = String.format(
                "💰 BALANCE UPDATE FAILURE 💰\n\n" +
                "Critical balance update failed:\n" +
                "Wallet ID: %s\nCustomer: %s\nAmount: $%s\nUpdate Type: %s\nError: %s\n\n" +
                "IMMEDIATE financial reconciliation required.",
                walletId, customerId, amount, updateType, exceptionMessage);

            notificationService.sendFinancialAlert("CRITICAL: Balance Update Failed", alertMessage, "CRITICAL");

            // Send balance reconciliation request
            kafkaTemplate.send("balance-reconciliation-queue", Map.of(
                "walletId", walletId != null ? walletId : "unknown",
                "customerId", customerId != null ? customerId : "unknown",
                "amount", amount != null ? amount.toString() : "0",
                "messageId", messageId,
                "timestamp", Instant.now()
            ));

        } catch (Exception e) {
            log.error("Failed to send balance updates DLQ notifications: {}", e.getMessage());
        }
    }

    private void validateBalanceConsistency(String walletId, BigDecimal amount, String messageId) {
        try {
            boolean consistent = balanceService.validateBalanceConsistency(walletId);
            if (!consistent) {
                notificationService.sendFinancialAlert(
                    "CRITICAL: Balance Inconsistency Detected",
                    String.format("Balance inconsistency detected for wallet %s during DLQ processing. " +
                        "Immediate reconciliation required.", walletId),
                    "CRITICAL"
                );
            }
        } catch (Exception e) {
            log.error("Error validating balance consistency: walletId={}, error={}", walletId, e.getMessage());
        }
    }

    private void performBalanceReconciliation(String walletId, String customerId, BigDecimal amount,
                                            Object originalMessage, String messageId) {
        try {
            balanceService.initiateReconciliation(walletId, customerId, amount);
            log.info("Initiated balance reconciliation for wallet: {}", walletId);
        } catch (Exception e) {
            log.error("Error performing balance reconciliation: walletId={}, error={}", walletId, e.getMessage());
        }
    }

    private void triggerBalanceIntegrityCheck(String walletId, String customerId, BigDecimal amount,
                                            String updateType, String messageId) {
        try {
            kafkaTemplate.send("balance-integrity-check-queue", Map.of(
                "walletId", walletId != null ? walletId : "unknown",
                "customerId", customerId != null ? customerId : "unknown",
                "amount", amount != null ? amount.toString() : "0",
                "updateType", updateType,
                "checkReason", "BALANCE_UPDATE_DLQ_FAILURE",
                "messageId", messageId,
                "timestamp", Instant.now()
            ));
        } catch (Exception e) {
            log.error("Error triggering balance integrity check: {}", e.getMessage());
        }
    }

    public void handleBalanceUpdatesDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                              int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String walletId = extractWalletId(originalMessage);
        String customerId = extractCustomerId(originalMessage);

        try {
            notificationService.sendExecutiveAlert(
                "EMERGENCY: Balance Updates DLQ Circuit Breaker",
                String.format("Balance updates circuit breaker triggered for wallet %s (customer %s). " +
                    "Complete balance system failure - IMMEDIATE escalation required.", walletId, customerId)
            );
        } catch (Exception e) {
            log.error("Error in balance updates DLQ fallback: {}", e.getMessage());
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

    private BigDecimal extractAmount(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object amount = messageMap.get("amount");
                if (amount instanceof Number) {
                    return new BigDecimal(amount.toString());
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract amount: {}", e.getMessage());
        }
        return null;
    }

    private String extractUpdateType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object updateType = messageMap.get("updateType");
                if (updateType == null) updateType = messageMap.get("type");
                return updateType != null ? updateType.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract updateType: {}", e.getMessage());
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