package com.waqiti.lending.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.lending.service.LoanService;
import com.waqiti.lending.service.BalloonPaymentService;
import com.waqiti.lending.repository.LoanRepository;
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
 * DLQ Consumer for balloon payment events that failed to process.
 * Handles critical loan payment failures with comprehensive error analysis.
 */
@Component
@Slf4j
public class BalloonPaymentEventsDlqConsumer extends BaseDlqConsumer {

    private final LoanService loanService;
    private final BalloonPaymentService balloonPaymentService;
    private final LoanRepository loanRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public BalloonPaymentEventsDlqConsumer(DlqHandler dlqHandler,
                                          AuditService auditService,
                                          NotificationService notificationService,
                                          MeterRegistry meterRegistry,
                                          LoanService loanService,
                                          BalloonPaymentService balloonPaymentService,
                                          LoanRepository loanRepository,
                                          KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.loanService = loanService;
        this.balloonPaymentService = balloonPaymentService;
        this.loanRepository = loanRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
        topics = {"balloon-payment-events-dlq"},
        groupId = "balloon-payment-dlq-consumer-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "balloon-payment-dlq", fallbackMethod = "handleBalloonPaymentDlqFallback")
    public void handleBalloonPaymentDlq(@Payload Object originalMessage,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                       @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                       @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                       @Header(KafkaHeaders.OFFSET) long offset,
                                       Acknowledgment acknowledgment,
                                       @Header Map<String, Object> headers) {

        log.info("Processing balloon payment DLQ message: topic={}, partition={}, offset={}",
            topic, partition, offset);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            // Extract loan information from the failed message
            String loanId = extractLoanId(originalMessage);
            String userId = extractUserId(originalMessage);
            String correlationId = extractCorrelationId(null, originalMessage);

            log.info("Processing balloon payment DLQ for loan: loanId={}, messageId={}", loanId, messageId);

            // Check loan status and balloon payment schedule
            if (loanId != null) {
                validateLoanStatus(loanId, messageId);
                checkBalloonPaymentImpact(loanId, originalMessage, messageId);
                updateLoanPaymentStatus(loanId, originalMessage, exceptionMessage, messageId);
            }

            // Generate domain-specific alerts
            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Check for cascading effects on other loan services
            assessCascadingImpacts(loanId, originalMessage, messageId);

            // Update balloon payment schedule if needed
            handleBalloonPaymentScheduleAdjustment(loanId, originalMessage, messageId);

        } catch (Exception e) {
            log.error("Error in balloon payment DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "balloon-payment-events-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "FINANCIAL_LOANS";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        // Balloon payments are always critical as they represent final loan payments
        return true;
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String loanId = extractLoanId(originalMessage);
        String userId = extractUserId(originalMessage);

        // Send immediate alert to loan operations team
        try {
            notificationService.sendCriticalAlert(
                "CRITICAL: Balloon Payment Processing Failed",
                String.format("Balloon payment processing failed for loan %s (user: %s). " +
                    "This requires immediate attention as it affects loan closure. " +
                    "Error: %s", loanId, userId, exceptionMessage),
                Map.of("loanId", loanId != null ? loanId : "unknown",
                       "userId", userId != null ? userId : "unknown",
                       "messageId", messageId,
                       "businessImpact", "CRITICAL_LOAN_PAYMENT_FAILURE")
            );

            // Send customer notification about payment processing delay
            if (userId != null) {
                notificationService.sendNotification(userId,
                    "Payment Processing Delayed",
                    "We're experiencing a temporary delay with your balloon payment. " +
                    "Our team has been notified and will resolve this shortly. " +
                    "Your account will not be penalized during this period.",
                    messageId);
            }

            // Alert compliance team about potential regulatory impact
            notificationService.sendComplianceAlert(
                "Balloon Payment Processing Failure - Regulatory Review Required",
                String.format("Balloon payment failure for loan %s may require regulatory notification. " +
                    "Review loan terms and customer impact.", loanId),
                "HIGH"
            );

        } catch (Exception e) {
            log.error("Failed to send balloon payment DLQ alerts: {}", e.getMessage());
        }
    }

    private void validateLoanStatus(String loanId, String messageId) {
        try {
            var loan = loanRepository.findById(loanId);
            if (loan.isPresent()) {
                String loanStatus = loan.get().getStatus();
                log.info("Loan status validation for DLQ: loanId={}, status={}, messageId={}",
                    loanId, loanStatus, messageId);

                // Check if loan is in a state where balloon payment is expected
                if (!"ACTIVE".equals(loanStatus) && !"BALLOON_PAYMENT_DUE".equals(loanStatus)) {
                    log.warn("Balloon payment DLQ for loan in unexpected status: loanId={}, status={}",
                        loanId, loanStatus);

                    // Send specific alert for status mismatch
                    notificationService.sendOperationalAlert(
                        "Balloon Payment DLQ - Loan Status Mismatch",
                        String.format("Balloon payment failed for loan %s with status %s. " +
                            "This may indicate a workflow issue.", loanId, loanStatus),
                        "HIGH"
                    );
                }
            } else {
                log.error("Loan not found for balloon payment DLQ: loanId={}, messageId={}",
                    loanId, messageId);
            }
        } catch (Exception e) {
            log.error("Error validating loan status for DLQ: loanId={}, error={}", loanId, e.getMessage());
        }
    }

    private void checkBalloonPaymentImpact(String loanId, Object originalMessage, String messageId) {
        try {
            // Check if this is the final payment for the loan
            boolean isFinalPayment = balloonPaymentService.isFinalPayment(loanId);
            if (isFinalPayment) {
                log.warn("DLQ for FINAL balloon payment: loanId={}, messageId={}", loanId, messageId);

                // This is critical - final payment failure affects loan closure
                notificationService.sendCriticalAlert(
                    "URGENT: Final Balloon Payment Failed",
                    String.format("The final balloon payment for loan %s has failed processing. " +
                        "This prevents loan closure and requires immediate resolution.", loanId),
                    Map.of("loanId", loanId,
                           "paymentType", "FINAL_BALLOON_PAYMENT",
                           "urgency", "CRITICAL")
                );

                // Trigger manual review workflow
                kafkaTemplate.send("loan-manual-review-queue", Map.of(
                    "loanId", loanId,
                    "reviewType", "FINAL_PAYMENT_FAILURE",
                    "priority", "CRITICAL",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

            // Check payment amount impact
            Double paymentAmount = extractPaymentAmount(originalMessage);
            if (paymentAmount != null && paymentAmount > 50000) { // Large payment threshold
                notificationService.sendManagementAlert(
                    "Large Balloon Payment Failure",
                    String.format("Balloon payment of $%.2f failed for loan %s. " +
                        "Management review required due to amount.", paymentAmount, loanId)
                );
            }

        } catch (Exception e) {
            log.error("Error checking balloon payment impact: loanId={}, error={}", loanId, e.getMessage());
        }
    }

    private void updateLoanPaymentStatus(String loanId, Object originalMessage,
                                       String exceptionMessage, String messageId) {
        try {
            if (loanId != null) {
                // Update loan with payment failure information
                loanService.recordPaymentFailure(loanId, Map.of(
                    "failureType", "BALLOON_PAYMENT_DLQ",
                    "errorMessage", exceptionMessage,
                    "messageId", messageId,
                    "timestamp", Instant.now(),
                    "requiresManualIntervention", true
                ));

                log.info("Updated loan payment status for DLQ: loanId={}, messageId={}", loanId, messageId);
            }
        } catch (Exception e) {
            log.error("Error updating loan payment status: loanId={}, error={}", loanId, e.getMessage());
        }
    }

    private void assessCascadingImpacts(String loanId, Object originalMessage, String messageId) {
        try {
            if (loanId != null) {
                // Check for linked accounts, insurance, or other services
                boolean hasLinkedServices = loanService.hasLinkedServices(loanId);
                if (hasLinkedServices) {
                    log.warn("Balloon payment DLQ for loan with linked services: loanId={}", loanId);

                    // Notify linked services about the payment failure
                    kafkaTemplate.send("loan-linked-services-alert", Map.of(
                        "loanId", loanId,
                        "alertType", "BALLOON_PAYMENT_FAILURE",
                        "messageId", messageId,
                        "timestamp", Instant.now()
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Error assessing cascading impacts: loanId={}, error={}", loanId, e.getMessage());
        }
    }

    private void handleBalloonPaymentScheduleAdjustment(String loanId, Object originalMessage, String messageId) {
        try {
            if (loanId != null) {
                // Check if payment schedule needs adjustment due to failure
                balloonPaymentService.assessScheduleAdjustment(loanId, Map.of(
                    "failureReason", "DLQ_PROCESSING_FAILURE",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }
        } catch (Exception e) {
            log.error("Error handling balloon payment schedule adjustment: loanId={}, error={}",
                loanId, e.getMessage());
        }
    }

    // Circuit breaker fallback method
    public void handleBalloonPaymentDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                               int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        // Additional balloon payment specific fallback actions
        String loanId = extractLoanId(originalMessage);
        if (loanId != null) {
            try {
                // Mark loan for urgent manual review
                loanService.markForUrgentReview(loanId, "BALLOON_PAYMENT_DLQ_CIRCUIT_BREAKER");

                // Send executive notification for circuit breaker
                notificationService.sendExecutiveAlert(
                    "Critical: Balloon Payment DLQ Circuit Breaker Triggered",
                    String.format("Circuit breaker triggered for balloon payment DLQ processing on loan %s. " +
                        "This indicates a systemic issue requiring immediate executive attention.", loanId)
                );
            } catch (Exception e) {
                log.error("Error in balloon payment DLQ fallback: {}", e.getMessage());
            }
        }
    }

    // Helper methods for data extraction
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

    private String extractUserId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object userId = messageMap.get("userId");
                return userId != null ? userId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract userId from message: {}", e.getMessage());
        }
        return null;
    }

    private Double extractPaymentAmount(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object amount = messageMap.get("amount");
                if (amount instanceof Number) {
                    return ((Number) amount).doubleValue();
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract payment amount from message: {}", e.getMessage());
        }
        return null;
    }
}