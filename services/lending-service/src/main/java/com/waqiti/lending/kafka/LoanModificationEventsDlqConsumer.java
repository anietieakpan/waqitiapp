package com.waqiti.lending.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.lending.service.LoanModificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Map;

/**
 * DLQ Consumer for loan modification events that failed to process.
 * Handles critical loan modification failures affecting borrower relief programs.
 */
@Component
@Slf4j
public class LoanModificationEventsDlqConsumer extends BaseDlqConsumer {

    private final LoanModificationService loanModificationService;

    public LoanModificationEventsDlqConsumer(DlqHandler dlqHandler,
                                           AuditService auditService,
                                           NotificationService notificationService,
                                           MeterRegistry meterRegistry,
                                           LoanModificationService loanModificationService) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.loanModificationService = loanModificationService;
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 5000, multiplier = 2.0),
        include = {Exception.class}
    )
    @KafkaListener(
        topics = {"loan-modification-events-dlq"},
        groupId = "loan-modification-dlq-consumer-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation.level=read_committed",
            "spring.kafka.consumer.enable.auto.commit=false"
        }
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "loan-modification-dlq", fallbackMethod = "handleLoanModificationDlqFallback")
    public void handleLoanModificationDlq(@Payload Object originalMessage,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                         @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                         @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                         @Header(KafkaHeaders.OFFSET) long offset,
                                         Acknowledgment acknowledgment,
                                         @Header Map<String, Object> headers) {

        String correlationId = extractCorrelationId(headers, originalMessage);
        log.info("Processing loan modification DLQ message: topic={}, partition={}, offset={}, correlationId={}",
            topic, partition, offset, correlationId);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String loanId = extractLoanId(originalMessage);
            String borrowerId = extractBorrowerId(originalMessage);
            String eventType = extractEventType(originalMessage);
            String modificationType = extractModificationType(originalMessage);

            log.info("Processing loan modification DLQ: loanId={}, borrowerId={}, eventType={}, type={}, messageId={}",
                loanId, borrowerId, eventType, modificationType, messageId);

            if (loanId != null) {
                assessModificationImpact(loanId, modificationType, originalMessage, messageId);
                handleCustomerRelief(loanId, originalMessage, messageId);
            }

            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

        } catch (Exception e) {
            log.error("Error in loan modification DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "loan-modification-events-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "FINANCIAL_LOAN_MODIFICATION";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        String eventType = extractEventType(originalMessage);
        return "MODIFICATION_DENIAL".equals(eventType) || "MODIFICATION_EXPIRY".equals(eventType);
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String loanId = extractLoanId(originalMessage);
        String eventType = extractEventType(originalMessage);
        String modificationType = extractModificationType(originalMessage);

        try {
            notificationService.sendCriticalAlert(
                String.format("CRITICAL: Loan Modification Event Failed - %s", eventType),
                String.format("Loan modification event %s (%s) failed for loan %s. Error: %s",
                    eventType, modificationType, loanId, exceptionMessage),
                Map.of("loanId", loanId != null ? loanId : "unknown",
                       "eventType", eventType != null ? eventType : "unknown",
                       "modificationType", modificationType != null ? modificationType : "unknown",
                       "messageId", messageId,
                       "businessImpact", "CRITICAL_BORROWER_RELIEF_RISK"));

        } catch (Exception e) {
            log.error("Failed to send loan modification DLQ alerts: {}", e.getMessage());
        }
    }

    private void assessModificationImpact(String loanId, String modificationType, Object originalMessage, String messageId) {
        if ("TERM_EXTENSION".equals(modificationType) || "PAYMENT_REDUCTION".equals(modificationType)) {
            notificationService.sendCriticalAlert(
                "Critical Modification Failed",
                String.format("Critical loan modification %s failed for loan %s. " +
                    "Borrower relief may be delayed.", modificationType, loanId),
                Map.of("loanId", loanId, "modificationType", modificationType, "urgency", "HIGH")
            );
        }
    }

    private void handleCustomerRelief(String loanId, Object originalMessage, String messageId) {
        String borrowerId = extractBorrowerId(originalMessage);
        if (borrowerId != null) {
            notificationService.sendNotification(borrowerId,
                "Loan Modification Update",
                "We're processing your loan modification request. You'll receive an update shortly.",
                messageId);
        }
    }

    public void handleLoanModificationDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                                 int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String loanId = extractLoanId(originalMessage);
        if (loanId != null) {
            try {
                loanModificationService.markForUrgentReview(loanId, "LOAN_MODIFICATION_DLQ_CIRCUIT_BREAKER");
            } catch (Exception e) {
                log.error("Error in loan modification DLQ fallback: {}", e.getMessage());
            }
        }
    }

    private String extractLoanId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object loanId = messageMap.get("loanId");
                return loanId != null ? loanId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract loanId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractBorrowerId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object borrowerId = messageMap.get("borrowerId");
                return borrowerId != null ? borrowerId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract borrowerId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractEventType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object eventType = messageMap.get("eventType");
                return eventType != null ? eventType.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract eventType from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractModificationType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object type = messageMap.get("modificationType");
                return type != null ? type.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract modificationType from message: {}", e.getMessage());
        }
        return null;
    }
}