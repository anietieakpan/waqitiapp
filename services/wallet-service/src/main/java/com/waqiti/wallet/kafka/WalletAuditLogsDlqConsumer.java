package com.waqiti.wallet.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.wallet.service.WalletAuditService;
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

import java.time.Instant;
import java.util.Map;

/**
 * P0 DLQ Consumer for wallet audit log failures.
 * Handles critical audit trail operations that ensure regulatory compliance and financial transparency.
 */
@Component
@Slf4j
public class WalletAuditLogsDlqConsumer extends BaseDlqConsumer {

    private final WalletAuditService auditLogService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public WalletAuditLogsDlqConsumer(DlqHandler dlqHandler,
                                    AuditService auditService,
                                    NotificationService notificationService,
                                    MeterRegistry meterRegistry,
                                    WalletAuditService auditLogService,
                                    KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.auditLogService = auditLogService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 5000, multiplier = 2.0),
        include = {Exception.class}
    )
    @KafkaListener(
        topics = {"wallet-audit-logs-dlq"},
        groupId = "wallet-audit-logs-dlq-consumer-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation.level=read_committed",
            "spring.kafka.consumer.enable.auto.commit=false"
        }
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "wallet-audit-logs-dlq", fallbackMethod = "handleWalletAuditLogsDlqFallback")
    public void handleWalletAuditLogsDlq(@Payload Object originalMessage,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                       @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                       @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                       @Header(KafkaHeaders.OFFSET) long offset,
                                       Acknowledgment acknowledgment,
                                       @Header Map<String, Object> headers) {

        log.info("Processing wallet audit logs DLQ message: topic={}, partition={}, offset={}",
            topic, partition, offset);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String auditId = extractAuditId(originalMessage);
            String walletId = extractWalletId(originalMessage);
            String customerId = extractCustomerId(originalMessage);
            String action = extractAction(originalMessage);
            String actorId = extractActorId(originalMessage);

            log.info("Processing wallet audit logs DLQ: auditId={}, walletId={}, customerId={}, action={}, messageId={}",
                auditId, walletId, customerId, action, messageId);

            // Handle regulatory compliance for audit trail gaps
            handleRegulatoryCompliance(auditId, walletId, customerId, action, originalMessage, messageId);

            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

        } catch (Exception e) {
            log.error("Error in wallet audit logs DLQ processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "wallet-audit-logs-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "WALLET_COMPLIANCE";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        String action = extractAction(originalMessage);
        // All audit log failures are critical for compliance
        return action != null && (action.contains("TRANSFER") || action.contains("PAYMENT") ||
                                action.contains("WITHDRAWAL") || action.contains("DEPOSIT"));
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String auditId = extractAuditId(originalMessage);
        String walletId = extractWalletId(originalMessage);
        String customerId = extractCustomerId(originalMessage);
        String action = extractAction(originalMessage);

        try {
            String alertMessage = String.format(
                "📋 WALLET AUDIT LOG FAILURE 📋\n\n" +
                "Critical audit trail failed:\n" +
                "Audit ID: %s\nWallet: %s\nCustomer: %s\nAction: %s\nError: %s\n\n" +
                "COMPLIANCE RISK: Audit trail gaps may affect regulatory compliance.",
                auditId, walletId, customerId, action, exceptionMessage);

            notificationService.sendComplianceAlert("CRITICAL: Audit Trail Failed", alertMessage, "CRITICAL");

        } catch (Exception e) {
            log.error("Failed to send wallet audit logs DLQ notifications: {}", e.getMessage());
        }
    }

    private void handleRegulatoryCompliance(String auditId, String walletId, String customerId,
                                          String action, Object originalMessage, String messageId) {
        try {
            kafkaTemplate.send("regulatory-audit-compliance-queue", Map.of(
                "auditId", auditId != null ? auditId : "unknown",
                "walletId", walletId != null ? walletId : "unknown",
                "customerId", customerId != null ? customerId : "unknown",
                "action", action,
                "complianceReason", "AUDIT_LOG_DLQ_FAILURE",
                "messageId", messageId,
                "timestamp", Instant.now()
            ));
        } catch (Exception e) {
            log.error("Error handling regulatory compliance: {}", e.getMessage());
        }
    }

    public void handleWalletAuditLogsDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                               int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);
    }

    // Data extraction methods
    private String extractAuditId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object auditId = messageMap.get("auditId");
                if (auditId == null) auditId = messageMap.get("id");
                return auditId != null ? auditId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract auditId: {}", e.getMessage());
        }
        return null;
    }

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

    private String extractAction(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object action = messageMap.get("action");
                return action != null ? action.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract action: {}", e.getMessage());
        }
        return null;
    }

    private String extractActorId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object actorId = messageMap.get("actorId");
                return actorId != null ? actorId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract actorId: {}", e.getMessage());
        }
        return null;
    }
}