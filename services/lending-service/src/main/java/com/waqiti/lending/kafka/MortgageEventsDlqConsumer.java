package com.waqiti.lending.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.lending.service.MortgageService;
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
 * DLQ Consumer for mortgage events that failed to process.
 * Handles critical mortgage failures affecting home loan operations and real estate collateral.
 */
@Component
@Slf4j
public class MortgageEventsDlqConsumer extends BaseDlqConsumer {

    private final MortgageService mortgageService;

    public MortgageEventsDlqConsumer(DlqHandler dlqHandler,
                                    AuditService auditService,
                                    NotificationService notificationService,
                                    MeterRegistry meterRegistry,
                                    MortgageService mortgageService) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.mortgageService = mortgageService;
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 5000, multiplier = 2.0),
        include = {Exception.class}
    )
    @KafkaListener(
        topics = {"mortgage-events-dlq"},
        groupId = "mortgage-dlq-consumer-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation.level=read_committed",
            "spring.kafka.consumer.enable.auto.commit=false"
        }
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "mortgage-dlq", fallbackMethod = "handleMortgageDlqFallback")
    public void handleMortgageDlq(@Payload Object originalMessage,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                 @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                 @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                 @Header(KafkaHeaders.OFFSET) long offset,
                                 Acknowledgment acknowledgment,
                                 @Header Map<String, Object> headers) {

        String correlationId = extractCorrelationId(headers, originalMessage);
        log.info("Processing mortgage DLQ message: topic={}, partition={}, offset={}, correlationId={}",
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
            String propertyId = extractPropertyId(originalMessage);

            log.info("Processing mortgage DLQ: loanId={}, borrowerId={}, eventType={}, property={}, messageId={}",
                loanId, borrowerId, eventType, propertyId, messageId);

            if (loanId != null) {
                assessPropertyCollateral(loanId, propertyId, originalMessage, messageId);
                handleEscrowIssues(loanId, originalMessage, messageId);
                checkForeclosureRisk(loanId, originalMessage, messageId);
            }

            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

        } catch (Exception e) {
            log.error("Error in mortgage DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "mortgage-events-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "FINANCIAL_MORTGAGE";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        String eventType = extractEventType(originalMessage);
        Double amount = extractAmount(originalMessage);

        // Critical mortgage events
        if ("FORECLOSURE_INITIATION".equals(eventType) || "ESCROW_SHORTAGE".equals(eventType) ||
            "PROPERTY_VALUATION_FAILED".equals(eventType) || "INSURANCE_LAPSE".equals(eventType)) {
            return true;
        }

        // Large mortgage amounts are always critical
        return amount != null && amount > 500000;
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String loanId = extractLoanId(originalMessage);
        String eventType = extractEventType(originalMessage);
        String propertyId = extractPropertyId(originalMessage);
        Double amount = extractAmount(originalMessage);

        try {
            notificationService.sendCriticalAlert(
                String.format("CRITICAL: Mortgage Event Failed - %s", eventType),
                String.format(
                    "Mortgage event %s failed for loan %s (property: %s, amount: $%.2f). Error: %s",
                    eventType, loanId, propertyId, amount != null ? amount : 0.0, exceptionMessage),
                Map.of("loanId", loanId != null ? loanId : "unknown",
                       "eventType", eventType != null ? eventType : "unknown",
                       "propertyId", propertyId != null ? propertyId : "unknown",
                       "messageId", messageId,
                       "businessImpact", "CRITICAL_MORTGAGE_RISK"));

            // Send customer notification for impacting events
            if (isCustomerImpactingEvent(eventType)) {
                String borrowerId = extractBorrowerId(originalMessage);
                if (borrowerId != null) {
                    notificationService.sendNotification(borrowerId,
                        "Mortgage Update",
                        getCustomerNotificationMessage(eventType),
                        messageId);
                }
            }

        } catch (Exception e) {
            log.error("Failed to send mortgage DLQ alerts: {}", e.getMessage());
        }
    }

    private void assessPropertyCollateral(String loanId, String propertyId, Object originalMessage, String messageId) {
        if (propertyId != null) {
            String eventType = extractEventType(originalMessage);
            if ("PROPERTY_VALUATION".equals(eventType) || "PROPERTY_INSPECTION".equals(eventType)) {
                notificationService.sendRiskManagementAlert(
                    "Property Collateral Event Failed",
                    String.format("Property event %s failed for mortgage %s (property: %s). " +
                        "Collateral assessment may be affected.", eventType, loanId, propertyId),
                    "HIGH"
                );
            }
        }
    }

    private void handleEscrowIssues(String loanId, Object originalMessage, String messageId) {
        String eventType = extractEventType(originalMessage);
        if (eventType != null && eventType.contains("ESCROW")) {
            notificationService.sendOperationalAlert(
                "Mortgage Escrow Issue",
                String.format("Escrow event %s failed for mortgage %s. " +
                    "Review property tax and insurance payments.", eventType, loanId),
                "HIGH"
            );
        }
    }

    private void checkForeclosureRisk(String loanId, Object originalMessage, String messageId) {
        String eventType = extractEventType(originalMessage);
        if ("FORECLOSURE_INITIATION".equals(eventType) || "DEFAULT_NOTICE".equals(eventType)) {
            notificationService.sendExecutiveAlert(
                "CRITICAL: Foreclosure Process Failed",
                String.format("Foreclosure-related event %s failed for mortgage %s. " +
                    "Legal and risk teams must be notified immediately.", eventType, loanId)
            );
        }
    }

    public void handleMortgageDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                        int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String loanId = extractLoanId(originalMessage);
        if (loanId != null) {
            try {
                mortgageService.markForUrgentReview(loanId, "MORTGAGE_DLQ_CIRCUIT_BREAKER");
            } catch (Exception e) {
                log.error("Error in mortgage DLQ fallback: {}", e.getMessage());
            }
        }
    }

    private boolean isCustomerImpactingEvent(String eventType) {
        return eventType != null && (eventType.contains("PAYMENT") || eventType.contains("ESCROW") ||
                                   eventType.contains("INSURANCE") || eventType.contains("RATE"));
    }

    private String getCustomerNotificationMessage(String eventType) {
        switch (eventType) {
            case "ESCROW_SHORTAGE":
                return "We're processing an update to your mortgage escrow account. " +
                       "You'll receive details about any required escrow adjustments.";
            case "INSURANCE_VERIFICATION":
                return "We're verifying your homeowner's insurance coverage. " +
                       "Please ensure your insurance is current and covers the full property value.";
            case "RATE_ADJUSTMENT":
                return "We're processing a rate adjustment for your mortgage. " +
                       "You'll receive confirmation of your new payment amount.";
            default:
                return "We're processing an update to your mortgage account. " +
                       "You'll receive confirmation once processing is complete.";
        }
    }

    private String extractLoanId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object loanId = messageMap.get("loanId");
                if (loanId == null) loanId = messageMap.get("mortgageId");
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

    private String extractPropertyId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object propertyId = messageMap.get("propertyId");
                return propertyId != null ? propertyId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract propertyId from message: {}", e.getMessage());
        }
        return null;
    }

    private Double extractAmount(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object amount = messageMap.get("amount");
                if (amount == null) amount = messageMap.get("loanAmount");
                if (amount instanceof Number) {
                    return ((Number) amount).doubleValue();
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract amount from message: {}", e.getMessage());
        }
        return null;
    }
}