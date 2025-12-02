package com.waqiti.lending.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.lending.service.ForbearanceService;
import com.waqiti.lending.service.LoanService;
import com.waqiti.lending.repository.ForbearanceRepository;
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
 * DLQ Consumer for forbearance events that failed to process.
 * Handles critical borrower forbearance relief failures affecting payment suspension.
 */
@Component
@Slf4j
public class ForbearanceEventsDlqConsumer extends BaseDlqConsumer {

    private final ForbearanceService forbearanceService;
    private final LoanService loanService;
    private final ForbearanceRepository forbearanceRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ForbearanceEventsDlqConsumer(DlqHandler dlqHandler,
                                       AuditService auditService,
                                       NotificationService notificationService,
                                       MeterRegistry meterRegistry,
                                       ForbearanceService forbearanceService,
                                       LoanService loanService,
                                       ForbearanceRepository forbearanceRepository,
                                       KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.forbearanceService = forbearanceService;
        this.loanService = loanService;
        this.forbearanceRepository = forbearanceRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 5000, multiplier = 2.0),
        include = {Exception.class}
    )
    @KafkaListener(
        topics = {"forbearance-events-dlq"},
        groupId = "forbearance-dlq-consumer-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation.level=read_committed",
            "spring.kafka.consumer.enable.auto.commit=false"
        }
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "forbearance-dlq", fallbackMethod = "handleForbearanceDlqFallback")
    public void handleForbearanceDlq(@Payload Object originalMessage,
                                    @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                    @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                    @Header(KafkaHeaders.OFFSET) long offset,
                                    Acknowledgment acknowledgment,
                                    @Header Map<String, Object> headers) {

        String correlationId = extractCorrelationId(headers, originalMessage);
        log.info("Processing forbearance DLQ message: topic={}, partition={}, offset={}, correlationId={}",
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
            String forbearanceType = extractForbearanceType(originalMessage);

            log.info("Processing forbearance DLQ: loanId={}, borrowerId={}, eventType={}, type={}, messageId={}",
                loanId, borrowerId, eventType, forbearanceType, messageId);

            if (loanId != null) {
                validateForbearanceStatus(loanId, messageId);
                assessBorrowerRelief(loanId, forbearanceType, originalMessage, messageId);
                handleInterestAccrual(loanId, originalMessage, exceptionMessage, messageId);
            }

            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);
            assessRegulatoryCompliance(loanId, forbearanceType, originalMessage, messageId);
            handleSpecificForbearanceEventFailure(eventType, loanId, originalMessage, messageId);

        } catch (Exception e) {
            log.error("Error in forbearance DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "forbearance-events-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "FINANCIAL_FORBEARANCE";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        String eventType = extractEventType(originalMessage);
        String forbearanceType = extractForbearanceType(originalMessage);

        return "FORBEARANCE_EXPIRY".equals(eventType) || "EMERGENCY_FORBEARANCE".equals(forbearanceType) ||
               "DISASTER_FORBEARANCE".equals(forbearanceType) || "FORBEARANCE_DENIAL".equals(eventType);
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String loanId = extractLoanId(originalMessage);
        String borrowerId = extractBorrowerId(originalMessage);
        String eventType = extractEventType(originalMessage);
        String forbearanceType = extractForbearanceType(originalMessage);

        try {
            String alertTitle = String.format("CRITICAL: Forbearance Event Failed - %s", eventType);
            String alertMessage = String.format(
                "Forbearance event processing failed:\n" +
                "Loan ID: %s\n" +
                "Borrower ID: %s\n" +
                "Event Type: %s\n" +
                "Forbearance Type: %s\n" +
                "Error: %s\n" +
                "IMMEDIATE ACTION REQUIRED - Borrower relief may be affected.",
                loanId != null ? loanId : "unknown",
                borrowerId != null ? borrowerId : "unknown",
                eventType != null ? eventType : "unknown",
                forbearanceType != null ? forbearanceType : "unknown",
                exceptionMessage
            );

            notificationService.sendCriticalAlert(alertTitle, alertMessage,
                Map.of("loanId", loanId != null ? loanId : "unknown",
                       "borrowerId", borrowerId != null ? borrowerId : "unknown",
                       "eventType", eventType != null ? eventType : "unknown",
                       "messageId", messageId,
                       "businessImpact", "CRITICAL_BORROWER_RELIEF_RISK"));

            if (isBorrowerImpactingEvent(eventType) && borrowerId != null) {
                String borrowerMessage = getBorrowerNotificationMessage(eventType);
                notificationService.sendNotification(borrowerId,
                    "Forbearance Update", borrowerMessage, messageId);
            }

        } catch (Exception e) {
            log.error("Failed to send forbearance DLQ alerts: {}", e.getMessage());
        }
    }

    private void validateForbearanceStatus(String loanId, String messageId) {
        try {
            var forbearance = forbearanceRepository.findByLoanId(loanId);
            if (forbearance.isPresent()) {
                String status = forbearance.get().getStatus();
                log.info("Forbearance status validation for DLQ: loanId={}, status={}, messageId={}",
                    loanId, status, messageId);

                if ("ACTIVE".equals(status)) {
                    notificationService.sendCriticalAlert(
                        "Active Forbearance DLQ",
                        String.format("Active forbearance for loan %s has failed event processing. " +
                            "Borrower payment suspension may be affected.", loanId),
                        Map.of("loanId", loanId, "status", status, "riskLevel", "HIGH")
                    );
                }
            }
        } catch (Exception e) {
            log.error("Error validating forbearance status for DLQ: loanId={}, error={}", loanId, e.getMessage());
        }
    }

    private void assessBorrowerRelief(String loanId, String forbearanceType, Object originalMessage, String messageId) {
        try {
            if ("EMERGENCY_FORBEARANCE".equals(forbearanceType) || "DISASTER_FORBEARANCE".equals(forbearanceType)) {
                notificationService.sendCriticalAlert(
                    "Emergency Forbearance Failed",
                    String.format("Emergency/disaster forbearance %s failed for loan %s. " +
                        "Borrower may be in immediate financial distress.", forbearanceType, loanId),
                    Map.of("loanId", loanId, "forbearanceType", forbearanceType, "urgency", "IMMEDIATE")
                );
            }
        } catch (Exception e) {
            log.error("Error assessing borrower relief: loanId={}, error={}", loanId, e.getMessage());
        }
    }

    private void handleInterestAccrual(String loanId, Object originalMessage, String exceptionMessage, String messageId) {
        try {
            String eventType = extractEventType(originalMessage);
            if ("INTEREST_ACCRUAL_SUSPENSION".equals(eventType)) {
                notificationService.sendFinanceAlert(
                    "Interest Accrual Suspension Failed",
                    String.format("Interest accrual suspension failed for loan %s. " +
                        "Review interest calculations and borrower account balances.", loanId),
                    "HIGH"
                );
            }
        } catch (Exception e) {
            log.error("Error handling interest accrual: loanId={}, error={}", loanId, e.getMessage());
        }
    }

    private void assessRegulatoryCompliance(String loanId, String forbearanceType, Object originalMessage, String messageId) {
        try {
            if (isRegulatoryForbearance(forbearanceType)) {
                notificationService.sendComplianceAlert(
                    "Regulatory Forbearance Failed",
                    String.format("Regulatory forbearance %s failed for loan %s. " +
                        "Review compliance with forbearance regulations.", forbearanceType, loanId),
                    "HIGH"
                );
            }
        } catch (Exception e) {
            log.error("Error assessing regulatory compliance: loanId={}, error={}", loanId, e.getMessage());
        }
    }

    private void handleSpecificForbearanceEventFailure(String eventType, String loanId, Object originalMessage, String messageId) {
        try {
            switch (eventType) {
                case "FORBEARANCE_REQUEST":
                    handleForbearanceRequestFailure(loanId, originalMessage, messageId);
                    break;
                case "FORBEARANCE_APPROVAL":
                    handleForbearanceApprovalFailure(loanId, originalMessage, messageId);
                    break;
                case "FORBEARANCE_EXPIRY":
                    handleForbearanceExpiryFailure(loanId, originalMessage, messageId);
                    break;
                default:
                    log.info("No specific handling for forbearance event type: {}", eventType);
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling specific forbearance event failure: eventType={}, loanId={}, error={}",
                eventType, loanId, e.getMessage());
        }
    }

    private void handleForbearanceRequestFailure(String loanId, Object originalMessage, String messageId) {
        String borrowerId = extractBorrowerId(originalMessage);
        notificationService.sendCriticalAlert(
            "Forbearance Request Failed",
            String.format("Forbearance request failed for loan %s (borrower: %s). " +
                "Borrower may be waiting for payment relief approval.", loanId, borrowerId),
            Map.of("loanId", loanId, "borrowerId", borrowerId, "urgency", "HIGH")
        );
    }

    private void handleForbearanceApprovalFailure(String loanId, Object originalMessage, String messageId) {
        notificationService.sendCriticalAlert(
            "Forbearance Approval Failed",
            String.format("Forbearance approval processing failed for loan %s. " +
                "Borrower payment suspension may be delayed.", loanId),
            Map.of("loanId", loanId, "urgency", "HIGH")
        );
    }

    private void handleForbearanceExpiryFailure(String loanId, Object originalMessage, String messageId) {
        notificationService.sendExecutiveAlert(
            "CRITICAL: Forbearance Expiry Failed",
            String.format("Forbearance expiry processing failed for loan %s. " +
                "Borrower may continue payment suspension unexpectedly.", loanId)
        );
    }

    public void handleForbearanceDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                           int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String loanId = extractLoanId(originalMessage);
        if (loanId != null) {
            try {
                forbearanceService.markForUrgentReview(loanId, "FORBEARANCE_DLQ_CIRCUIT_BREAKER");
                notificationService.sendExecutiveAlert(
                    "Critical: Forbearance DLQ Circuit Breaker Triggered",
                    String.format("Circuit breaker triggered for forbearance DLQ processing on loan %s. " +
                        "This indicates a systemic issue affecting borrower relief operations.", loanId)
                );
            } catch (Exception e) {
                log.error("Error in forbearance DLQ fallback: {}", e.getMessage());
            }
        }
    }

    private boolean isBorrowerImpactingEvent(String eventType) {
        return eventType != null && (eventType.contains("REQUEST") || eventType.contains("APPROVAL") ||
                                   eventType.contains("EXPIRY"));
    }

    private boolean isRegulatoryForbearance(String forbearanceType) {
        return forbearanceType != null && (forbearanceType.contains("DISASTER") ||
                                         forbearanceType.contains("EMERGENCY"));
    }

    private String getBorrowerNotificationMessage(String eventType) {
        switch (eventType) {
            case "FORBEARANCE_REQUEST":
                return "We're processing your forbearance request. " +
                       "You'll receive confirmation of your payment suspension status shortly.";
            case "FORBEARANCE_EXPIRY":
                return "We're processing the end of your forbearance period. " +
                       "You'll receive information about resuming your loan payments.";
            default:
                return "We're processing an update to your forbearance arrangement. " +
                       "Our team will contact you with details shortly.";
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
                if (borrowerId == null) borrowerId = messageMap.get("userId");
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

    private String extractForbearanceType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object type = messageMap.get("forbearanceType");
                return type != null ? type.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract forbearanceType from message: {}", e.getMessage());
        }
        return null;
    }
}