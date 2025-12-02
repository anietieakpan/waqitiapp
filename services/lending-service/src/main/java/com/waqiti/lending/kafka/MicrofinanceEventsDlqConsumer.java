package com.waqiti.lending.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.lending.service.MicrofinanceService;
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
 * DLQ Consumer for microfinance events that failed to process.
 * Handles critical microfinance failures affecting small-scale lending operations.
 */
@Component
@Slf4j
public class MicrofinanceEventsDlqConsumer extends BaseDlqConsumer {

    private final MicrofinanceService microfinanceService;

    public MicrofinanceEventsDlqConsumer(DlqHandler dlqHandler,
                                        AuditService auditService,
                                        NotificationService notificationService,
                                        MeterRegistry meterRegistry,
                                        MicrofinanceService microfinanceService) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.microfinanceService = microfinanceService;
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 5000, multiplier = 2.0),
        include = {Exception.class}
    )
    @KafkaListener(
        topics = {"microfinance-events-dlq"},
        groupId = "microfinance-dlq-consumer-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation.level=read_committed",
            "spring.kafka.consumer.enable.auto.commit=false"
        }
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "microfinance-dlq", fallbackMethod = "handleMicrofinanceDlqFallback")
    public void handleMicrofinanceDlq(@Payload Object originalMessage,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                     @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                     @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                     @Header(KafkaHeaders.OFFSET) long offset,
                                     Acknowledgment acknowledgment,
                                     @Header Map<String, Object> headers) {

        String correlationId = extractCorrelationId(headers, originalMessage);
        log.info("Processing microfinance DLQ message: topic={}, partition={}, offset={}, correlationId={}",
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
            String programType = extractProgramType(originalMessage);

            log.info("Processing microfinance DLQ: loanId={}, borrowerId={}, eventType={}, program={}, messageId={}",
                loanId, borrowerId, eventType, programType, messageId);

            if (loanId != null) {
                assessCommunityImpact(loanId, programType, originalMessage, messageId);
                handleGroupLendingIssues(loanId, originalMessage, messageId);
            }

            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

        } catch (Exception e) {
            log.error("Error in microfinance DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "microfinance-events-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "FINANCIAL_MICROFINANCE";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        String eventType = extractEventType(originalMessage);
        return "GROUP_LOAN_DEFAULT".equals(eventType) || "COMMUNITY_PROGRAM_FAILURE".equals(eventType);
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String loanId = extractLoanId(originalMessage);
        String eventType = extractEventType(originalMessage);

        try {
            notificationService.sendCriticalAlert(
                String.format("CRITICAL: Microfinance Event Failed - %s", eventType),
                String.format("Microfinance event %s failed for loan %s. Error: %s", eventType, loanId, exceptionMessage),
                Map.of("loanId", loanId != null ? loanId : "unknown",
                       "eventType", eventType != null ? eventType : "unknown",
                       "messageId", messageId,
                       "businessImpact", "CRITICAL_MICROFINANCE_RISK"));

        } catch (Exception e) {
            log.error("Failed to send microfinance DLQ alerts: {}", e.getMessage());
        }
    }

    private void assessCommunityImpact(String loanId, String programType, Object originalMessage, String messageId) {
        if ("COMMUNITY_DEVELOPMENT".equals(programType)) {
            notificationService.sendCriticalAlert(
                "Community Development Program Failed",
                String.format("Community development microfinance failed for loan %s. " +
                    "Multiple community members may be affected.", loanId),
                Map.of("loanId", loanId, "programType", programType, "urgency", "HIGH")
            );
        }
    }

    private void handleGroupLendingIssues(String loanId, Object originalMessage, String messageId) {
        String groupId = extractGroupId(originalMessage);
        if (groupId != null) {
            notificationService.sendOperationalAlert(
                "Group Lending Issue",
                String.format("Group lending event failed for loan %s (group: %s). " +
                    "Review group dynamics and collective responsibility.", loanId, groupId),
                "HIGH"
            );
        }
    }

    public void handleMicrofinanceDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                            int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String loanId = extractLoanId(originalMessage);
        if (loanId != null) {
            try {
                microfinanceService.markForUrgentReview(loanId, "MICROFINANCE_DLQ_CIRCUIT_BREAKER");
            } catch (Exception e) {
                log.error("Error in microfinance DLQ fallback: {}", e.getMessage());
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

    private String extractProgramType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object programType = messageMap.get("programType");
                return programType != null ? programType.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract programType from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractGroupId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object groupId = messageMap.get("groupId");
                return groupId != null ? groupId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract groupId from message: {}", e.getMessage());
        }
        return null;
    }
}