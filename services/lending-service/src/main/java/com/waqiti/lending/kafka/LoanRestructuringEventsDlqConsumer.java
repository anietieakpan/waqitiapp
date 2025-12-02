package com.waqiti.lending.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.lending.service.LoanRestructuringService;
import com.waqiti.lending.service.LoanService;
import com.waqiti.lending.repository.LoanRestructuringRepository;
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
 * DLQ Consumer for loan restructuring events that failed to process.
 * Handles critical loan modification failures affecting borrower relief programs.
 */
@Component
@Slf4j
public class LoanRestructuringEventsDlqConsumer extends BaseDlqConsumer {

    private final LoanRestructuringService loanRestructuringService;
    private final LoanService loanService;
    private final LoanRestructuringRepository loanRestructuringRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public LoanRestructuringEventsDlqConsumer(DlqHandler dlqHandler,
                                             AuditService auditService,
                                             NotificationService notificationService,
                                             MeterRegistry meterRegistry,
                                             LoanRestructuringService loanRestructuringService,
                                             LoanService loanService,
                                             LoanRestructuringRepository loanRestructuringRepository,
                                             KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.loanRestructuringService = loanRestructuringService;
        this.loanService = loanService;
        this.loanRestructuringRepository = loanRestructuringRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 5000, multiplier = 2.0),
        include = {Exception.class}
    )
    @KafkaListener(
        topics = {"loan-restructuring-events-dlq"},
        groupId = "loan-restructuring-dlq-consumer-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation.level=read_committed",
            "spring.kafka.consumer.enable.auto.commit=false"
        }
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "loan-restructuring-dlq", fallbackMethod = "handleLoanRestructuringDlqFallback")
    public void handleLoanRestructuringDlq(@Payload Object originalMessage,
                                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                          @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                          @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                          @Header(KafkaHeaders.OFFSET) long offset,
                                          Acknowledgment acknowledgment,
                                          @Header Map<String, Object> headers) {

        String correlationId = extractCorrelationId(headers, originalMessage);
        log.info("Processing loan restructuring DLQ message: topic={}, partition={}, offset={}, correlationId={}",
            topic, partition, offset, correlationId);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String loanId = extractLoanId(originalMessage);
            String userId = extractUserId(originalMessage);
            String eventType = extractEventType(originalMessage);
            String restructuringType = extractRestructuringType(originalMessage);
            String correlationId = extractCorrelationId(null, originalMessage);

            log.info("Processing loan restructuring DLQ: loanId={}, userId={}, eventType={}, restructuringType={}, messageId={}",
                loanId, userId, eventType, restructuringType, messageId);

            // Validate loan and restructuring status
            if (loanId != null) {
                validateLoanRestructuringStatus(loanId, messageId);
                assessRestructuringImpact(loanId, restructuringType, originalMessage, messageId);
                handleCustomerRelief(loanId, originalMessage, exceptionMessage, messageId);
            }

            // Generate domain-specific alerts
            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Handle regulatory and compliance implications
            assessRestructuringCompliance(loanId, restructuringType, originalMessage, messageId);

            // Check for accounting and provisioning impacts
            assessAccountingImpact(loanId, eventType, originalMessage, messageId);

            // Handle specific restructuring event failures
            handleSpecificRestructuringEventFailure(eventType, loanId, originalMessage, messageId);

        } catch (Exception e) {
            log.error("Error in loan restructuring DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "loan-restructuring-events-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "FINANCIAL_LOAN_RESTRUCTURING";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        String eventType = extractEventType(originalMessage);
        String restructuringType = extractRestructuringType(originalMessage);
        Double amount = extractAmount(originalMessage);

        // Critical events affecting borrower relief
        if ("FORBEARANCE_EXPIRY".equals(eventType) || "MODIFICATION_DENIAL".equals(eventType) ||
            "PAYMENT_PLAN_FAILURE".equals(eventType) || "HARDSHIP_APPROVAL".equals(eventType) ||
            "REGULATORY_RESTRUCTURING".equals(restructuringType)) {
            return true;
        }

        // Large loan amounts undergoing restructuring are always critical
        return amount != null && amount > 100000;
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String loanId = extractLoanId(originalMessage);
        String userId = extractUserId(originalMessage);
        String eventType = extractEventType(originalMessage);
        String restructuringType = extractRestructuringType(originalMessage);
        Double amount = extractAmount(originalMessage);

        try {
            // Send immediate alert to loan restructuring team
            String alertTitle = String.format("CRITICAL: Loan Restructuring Event Failed - %s", eventType);
            String alertMessage = String.format(
                "Loan restructuring event processing failed:\n" +
                "Loan ID: %s\n" +
                "User ID: %s\n" +
                "Event Type: %s\n" +
                "Restructuring Type: %s\n" +
                "Amount: %s\n" +
                "Error: %s\n" +
                "IMMEDIATE ACTION REQUIRED - Borrower relief may be affected.",
                loanId != null ? loanId : "unknown",
                userId != null ? userId : "unknown",
                eventType != null ? eventType : "unknown",
                restructuringType != null ? restructuringType : "unknown",
                amount != null ? String.format("$%.2f", amount) : "unknown",
                exceptionMessage
            );

            notificationService.sendCriticalAlert(alertTitle, alertMessage,
                Map.of("loanId", loanId != null ? loanId : "unknown",
                       "userId", userId != null ? userId : "unknown",
                       "eventType", eventType != null ? eventType : "unknown",
                       "restructuringType", restructuringType != null ? restructuringType : "unknown",
                       "messageId", messageId,
                       "businessImpact", "CRITICAL_BORROWER_RELIEF_RISK"));

            // Send customer notification for customer-facing events
            if (isCustomerImpactingEvent(eventType) && userId != null) {
                String customerMessage = getCustomerNotificationMessage(eventType);
                notificationService.sendNotification(userId,
                    "Loan Modification Update",
                    customerMessage,
                    messageId);
            }

            // Alert collections team for restructuring-related payment issues
            if (isCollectionsRelatedEvent(eventType)) {
                notificationService.sendCollectionsAlert(
                    "Loan Restructuring DLQ",
                    String.format("Loan restructuring event %s failed for loan %s. " +
                        "Review borrower relief programs and payment arrangements.", eventType, loanId),
                    "HIGH"
                );
            }

            // Alert legal team for complex restructuring events
            if (isLegalRestructuringEvent(eventType)) {
                notificationService.sendLegalAlert(
                    "Complex Loan Restructuring Failed",
                    String.format("Legal restructuring event %s failed for loan %s. " +
                        "Review legal documentation and regulatory requirements.", eventType, loanId),
                    "URGENT"
                );
            }

            // Alert compliance team for regulatory restructuring
            if (isRegulatoryRestructuring(restructuringType)) {
                notificationService.sendComplianceAlert(
                    "Regulatory Restructuring Failed",
                    String.format("Regulatory restructuring %s failed for loan %s. " +
                        "Review compliance with borrower relief regulations.", restructuringType, loanId),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Failed to send loan restructuring DLQ alerts: {}", e.getMessage());
        }
    }

    private void validateLoanRestructuringStatus(String loanId, String messageId) {
        try {
            var restructuringRecord = loanRestructuringRepository.findByLoanId(loanId);
            if (restructuringRecord.isPresent()) {
                String status = restructuringRecord.get().getStatus();
                String restructuringType = restructuringRecord.get().getRestructuringType();
                Integer attemptCount = restructuringRecord.get().getAttemptCount();

                log.info("Loan restructuring status validation for DLQ: loanId={}, status={}, type={}, attempts={}, messageId={}",
                    loanId, status, restructuringType, attemptCount, messageId);

                // Check for multiple failed attempts
                if (attemptCount != null && attemptCount > 2) {
                    log.warn("Loan restructuring DLQ with multiple attempts: loanId={}, attemptCount={}",
                        loanId, attemptCount);

                    notificationService.sendCriticalAlert(
                        "Multiple Restructuring Attempts Failed",
                        String.format("Loan %s has failed %d restructuring attempts. " +
                            "Borrower may require manual intervention or alternative relief options.",
                            loanId, attemptCount),
                        Map.of("loanId", loanId, "attemptCount", attemptCount, "riskLevel", "HIGH")
                    );
                }

                // Check for time-sensitive restructuring
                if ("FORBEARANCE_ACTIVE".equals(status) || "PAYMENT_PLAN_ACTIVE".equals(status)) {
                    notificationService.sendOperationalAlert(
                        "Active Restructuring DLQ",
                        String.format("Loan %s with active %s has failed event processing. " +
                            "Review current borrower relief arrangements.", loanId, status),
                        "HIGH"
                    );
                }
            } else {
                log.error("Loan restructuring record not found for DLQ: loanId={}, messageId={}",
                    loanId, messageId);
            }
        } catch (Exception e) {
            log.error("Error validating loan restructuring status for DLQ: loanId={}, error={}",
                loanId, e.getMessage());
        }
    }

    private void assessRestructuringImpact(String loanId, String restructuringType,
                                         Object originalMessage, String messageId) {
        try {
            String eventType = extractEventType(originalMessage);
            Double oldPayment = extractOldPayment(originalMessage);
            Double newPayment = extractNewPayment(originalMessage);

            log.info("Assessing restructuring impact: loanId={}, type={}, oldPayment=${}, newPayment=${}",
                loanId, restructuringType, oldPayment, newPayment);

            // Calculate payment impact
            if (oldPayment != null && newPayment != null) {
                double paymentReduction = oldPayment - newPayment;
                double reductionPercentage = (paymentReduction / oldPayment) * 100;

                if (reductionPercentage > 50) {
                    log.warn("Significant payment reduction in restructuring DLQ: loanId={}, reduction={}%",
                        loanId, reductionPercentage);

                    notificationService.sendRiskManagementAlert(
                        "High Payment Reduction Restructuring DLQ",
                        String.format("Loan %s restructuring with %.1f%% payment reduction has failed. " +
                            "Review risk implications and loss provisioning.", loanId, reductionPercentage),
                        "HIGH"
                    );
                }
            }

            // Check for COVID-19 or disaster relief implications
            if (isDisasterRelief(restructuringType)) {
                notificationService.sendComplianceAlert(
                    "Disaster Relief Restructuring Failed",
                    String.format("Disaster relief restructuring %s failed for loan %s. " +
                        "Review regulatory requirements and borrower communications.", restructuringType, loanId),
                    "HIGH"
                );
            }

            // Check for investor impact on securitized loans
            if (isSecuritizedLoan(loanId)) {
                notificationService.sendInvestorRelationsAlert(
                    "Securitized Loan Restructuring Failed",
                    String.format("Restructuring failed for securitized loan %s. " +
                        "Review investor notification requirements and servicer obligations.", loanId),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Error assessing restructuring impact: loanId={}, error={}", loanId, e.getMessage());
        }
    }

    private void handleCustomerRelief(String loanId, Object originalMessage,
                                    String exceptionMessage, String messageId) {
        try {
            String eventType = extractEventType(originalMessage);
            String userId = extractUserId(originalMessage);

            if (isCustomerReliefEvent(eventType)) {
                // Record relief failure for customer service
                loanRestructuringService.recordReliefFailure(loanId, Map.of(
                    "failureType", "RESTRUCTURING_DLQ",
                    "eventType", eventType,
                    "errorMessage", exceptionMessage,
                    "messageId", messageId,
                    "timestamp", Instant.now(),
                    "requiresCustomerContact", true
                ));

                // Check for hardship status
                String hardshipStatus = loanRestructuringService.getHardshipStatus(loanId);
                if ("ACTIVE_HARDSHIP".equals(hardshipStatus)) {
                    log.warn("Restructuring DLQ for loan with active hardship: loanId={}", loanId);

                    // Trigger manual review for hardship cases
                    kafkaTemplate.send("loan-hardship-review-queue", Map.of(
                        "loanId", loanId,
                        "userId", userId,
                        "hardshipStatus", hardshipStatus,
                        "triggerReason", "RESTRUCTURING_DLQ",
                        "messageId", messageId,
                        "timestamp", Instant.now()
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Error handling customer relief: loanId={}, error={}", loanId, e.getMessage());
        }
    }

    private void assessRestructuringCompliance(String loanId, String restructuringType,
                                             Object originalMessage, String messageId) {
        try {
            if (isRegulatoryRestructuring(restructuringType)) {
                // Check compliance with restructuring regulations
                notificationService.sendComplianceAlert(
                    "Regulatory Restructuring Failed",
                    String.format("Regulatory restructuring %s failed for loan %s. " +
                        "Review compliance with CFPB, OCC, and state regulations on loan modifications.",
                        restructuringType, loanId),
                    "HIGH"
                );

                // Trigger regulatory compliance review
                kafkaTemplate.send("restructuring-compliance-review-queue", Map.of(
                    "loanId", loanId,
                    "restructuringType", restructuringType,
                    "reviewReason", "REGULATORY_DLQ",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

            // Check for fair lending implications
            String eventType = extractEventType(originalMessage);
            if ("MODIFICATION_DENIAL".equals(eventType)) {
                notificationService.sendFairLendingAlert(
                    "Loan Modification Denial Failed",
                    String.format("Modification denial processing failed for loan %s. " +
                        "Review fair lending compliance and documentation requirements.", loanId),
                    "HIGH"
                );
            }
        } catch (Exception e) {
            log.error("Error assessing restructuring compliance: loanId={}, error={}",
                loanId, e.getMessage());
        }
    }

    private void assessAccountingImpact(String loanId, String eventType,
                                      Object originalMessage, String messageId) {
        try {
            if (isAccountingImpactEvent(eventType)) {
                Double amount = extractAmount(originalMessage);
                String restructuringType = extractRestructuringType(originalMessage);

                // Accounting impact assessment
                notificationService.sendFinanceAlert(
                    "Restructuring Accounting Impact",
                    String.format("Restructuring event %s failed for loan %s with amount $%.2f. " +
                        "Review accounting treatment and loss provisioning requirements.",
                        eventType, loanId, amount != null ? amount : 0.0),
                    "HIGH"
                );

                // Check for TDR (Troubled Debt Restructuring) implications
                if (isTdrClassification(restructuringType)) {
                    notificationService.sendComplianceAlert(
                        "TDR Classification Failed",
                        String.format("TDR classification processing failed for loan %s. " +
                            "Review accounting standards and regulatory reporting requirements.", loanId),
                        "HIGH"
                    );
                }
            }
        } catch (Exception e) {
            log.error("Error assessing accounting impact: loanId={}, error={}", loanId, e.getMessage());
        }
    }

    private void handleSpecificRestructuringEventFailure(String eventType, String loanId,
                                                        Object originalMessage, String messageId) {
        try {
            switch (eventType) {
                case "MODIFICATION_REQUEST":
                    handleModificationRequestFailure(loanId, originalMessage, messageId);
                    break;
                case "FORBEARANCE_SETUP":
                    handleForbearanceSetupFailure(loanId, originalMessage, messageId);
                    break;
                case "PAYMENT_PLAN_CREATION":
                    handlePaymentPlanFailure(loanId, originalMessage, messageId);
                    break;
                case "HARDSHIP_ASSESSMENT":
                    handleHardshipAssessmentFailure(loanId, originalMessage, messageId);
                    break;
                case "RESTRUCTURING_APPROVAL":
                    handleRestructuringApprovalFailure(loanId, originalMessage, messageId);
                    break;
                default:
                    log.info("No specific handling for restructuring event type: {}", eventType);
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling specific restructuring event failure: eventType={}, loanId={}, error={}",
                eventType, loanId, e.getMessage());
        }
    }

    private void handleModificationRequestFailure(String loanId, Object originalMessage, String messageId) {
        String userId = extractUserId(originalMessage);
        notificationService.sendCriticalAlert(
            "Loan Modification Request Failed",
            String.format("Loan modification request failed for loan %s (borrower: %s). " +
                "Customer may be waiting for modification decision.", loanId, userId),
            Map.of("loanId", loanId, "userId", userId, "urgency", "HIGH", "customerImpact", "HIGH")
        );
    }

    private void handleForbearanceSetupFailure(String loanId, Object originalMessage, String messageId) {
        Integer forbearanceMonths = extractForbearanceMonths(originalMessage);
        notificationService.sendCriticalAlert(
            "Forbearance Setup Failed",
            String.format("Forbearance setup (%d months) failed for loan %s. " +
                "Borrower relief may be delayed.", forbearanceMonths != null ? forbearanceMonths : 0, loanId),
            Map.of("loanId", loanId, "forbearanceMonths", forbearanceMonths, "urgency", "HIGH")
        );
    }

    private void handlePaymentPlanFailure(String loanId, Object originalMessage, String messageId) {
        Double newPayment = extractNewPayment(originalMessage);
        notificationService.sendCriticalAlert(
            "Payment Plan Creation Failed",
            String.format("Payment plan creation failed for loan %s (new payment: $%.2f). " +
                "Borrower payment arrangements may be affected.", loanId, newPayment != null ? newPayment : 0.0),
            Map.of("loanId", loanId, "newPayment", newPayment, "urgency", "HIGH")
        );
    }

    private void handleHardshipAssessmentFailure(String loanId, Object originalMessage, String messageId) {
        String userId = extractUserId(originalMessage);
        notificationService.sendOperationalAlert(
            "Hardship Assessment Failed",
            String.format("Hardship assessment failed for loan %s (borrower: %s). " +
                "Manual review may be required for borrower relief options.", loanId, userId),
            "HIGH"
        );
    }

    private void handleRestructuringApprovalFailure(String loanId, Object originalMessage, String messageId) {
        String restructuringType = extractRestructuringType(originalMessage);
        notificationService.sendExecutiveAlert(
            "CRITICAL: Restructuring Approval Failed",
            String.format("Restructuring approval (%s) failed for loan %s. " +
                "Executive review may be required for complex restructuring cases.", restructuringType, loanId)
        );
    }

    // Circuit breaker fallback method
    public void handleLoanRestructuringDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                                  int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String loanId = extractLoanId(originalMessage);
        String userId = extractUserId(originalMessage);

        if (loanId != null) {
            try {
                // Mark loan for urgent restructuring review
                loanRestructuringService.markForUrgentReview(loanId, "RESTRUCTURING_DLQ_CIRCUIT_BREAKER");

                // Send executive notification with borrower relief impact
                notificationService.sendExecutiveAlert(
                    "Critical: Loan Restructuring DLQ Circuit Breaker Triggered",
                    String.format("Circuit breaker triggered for loan restructuring DLQ processing on loan %s " +
                        "(borrower: %s). This indicates a systemic issue affecting borrower relief operations.",
                        loanId, userId)
                );
            } catch (Exception e) {
                log.error("Error in loan restructuring DLQ fallback: {}", e.getMessage());
            }
        }
    }

    // Helper methods for event classification
    private boolean isCustomerImpactingEvent(String eventType) {
        return eventType != null && (eventType.contains("MODIFICATION") || eventType.contains("FORBEARANCE") ||
                                   eventType.contains("PAYMENT_PLAN") || eventType.contains("HARDSHIP"));
    }

    private boolean isCollectionsRelatedEvent(String eventType) {
        return eventType != null && (eventType.contains("PAYMENT") || eventType.contains("DELINQUENT") ||
                                   eventType.contains("DEFAULT"));
    }

    private boolean isLegalRestructuringEvent(String eventType) {
        return eventType != null && (eventType.contains("LEGAL") || eventType.contains("COURT") ||
                                   eventType.contains("BANKRUPTCY"));
    }

    private boolean isRegulatoryRestructuring(String restructuringType) {
        return restructuringType != null && (restructuringType.contains("REGULATORY") ||
                                           restructuringType.contains("CFPB") ||
                                           restructuringType.contains("DISASTER_RELIEF"));
    }

    private boolean isCustomerReliefEvent(String eventType) {
        return eventType != null && (eventType.contains("RELIEF") || eventType.contains("HARDSHIP") ||
                                   eventType.contains("FORBEARANCE"));
    }

    private boolean isAccountingImpactEvent(String eventType) {
        return eventType != null && (eventType.contains("TDR") || eventType.contains("PROVISION") ||
                                   eventType.contains("IMPAIRMENT"));
    }

    private boolean isDisasterRelief(String restructuringType) {
        return restructuringType != null && restructuringType.contains("DISASTER");
    }

    private boolean isTdrClassification(String restructuringType) {
        return restructuringType != null && restructuringType.contains("TDR");
    }

    private boolean isSecuritizedLoan(String loanId) {
        try {
            return loanService.isSecuritized(loanId);
        } catch (Exception e) {
            log.debug("Could not determine securitization status for loan: {}", loanId);
            return false;
        }
    }

    private String getCustomerNotificationMessage(String eventType) {
        switch (eventType) {
            case "MODIFICATION_REQUEST":
                return "We're processing your loan modification request. " +
                       "You'll receive an update on your modification status shortly.";
            case "FORBEARANCE_SETUP":
                return "We're setting up your forbearance arrangement. " +
                       "Your payment suspension will be confirmed once processing is complete.";
            case "PAYMENT_PLAN_CREATION":
                return "We're creating your new payment plan. " +
                       "Your updated payment schedule will be available soon.";
            default:
                return "We're processing your loan modification request. " +
                       "Our team will contact you with an update shortly.";
        }
    }

    // Data extraction helper methods
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
                if (userId == null) userId = messageMap.get("borrowerId");
                return userId != null ? userId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract userId from message: {}", e.getMessage());
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

    private String extractRestructuringType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object type = messageMap.get("restructuringType");
                return type != null ? type.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract restructuringType from message: {}", e.getMessage());
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

    private Double extractOldPayment(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object payment = messageMap.get("oldPayment");
                if (payment instanceof Number) {
                    return ((Number) payment).doubleValue();
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract oldPayment from message: {}", e.getMessage());
        }
        return null;
    }

    private Double extractNewPayment(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object payment = messageMap.get("newPayment");
                if (payment instanceof Number) {
                    return ((Number) payment).doubleValue();
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract newPayment from message: {}", e.getMessage());
        }
        return null;
    }

    private Integer extractForbearanceMonths(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object months = messageMap.get("forbearanceMonths");
                if (months instanceof Number) {
                    return ((Number) months).intValue();
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract forbearanceMonths from message: {}", e.getMessage());
        }
        return null;
    }
}