package com.waqiti.lending.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.lending.service.AutoLoanService;
import com.waqiti.lending.service.VehicleService;
import com.waqiti.lending.repository.AutoLoanRepository;
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
 * DLQ Consumer for auto loan events that failed to process.
 * Handles critical auto loan lifecycle failures with collateral protection.
 */
@Component
@Slf4j
public class AutoLoanEventsDlqConsumer extends BaseDlqConsumer {

    private final AutoLoanService autoLoanService;
    private final VehicleService vehicleService;
    private final AutoLoanRepository autoLoanRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AutoLoanEventsDlqConsumer(DlqHandler dlqHandler,
                                    AuditService auditService,
                                    NotificationService notificationService,
                                    MeterRegistry meterRegistry,
                                    AutoLoanService autoLoanService,
                                    VehicleService vehicleService,
                                    AutoLoanRepository autoLoanRepository,
                                    KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.autoLoanService = autoLoanService;
        this.vehicleService = vehicleService;
        this.autoLoanRepository = autoLoanRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
        topics = {"auto-loan-events-dlq"},
        groupId = "auto-loan-dlq-consumer-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "auto-loan-dlq", fallbackMethod = "handleAutoLoanDlqFallback")
    public void handleAutoLoanDlq(@Payload Object originalMessage,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                 @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                 @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                 @Header(KafkaHeaders.OFFSET) long offset,
                                 Acknowledgment acknowledgment,
                                 @Header Map<String, Object> headers) {

        log.info("Processing auto loan DLQ message: topic={}, partition={}, offset={}",
            topic, partition, offset);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String loanId = extractLoanId(originalMessage);
            String userId = extractUserId(originalMessage);
            String eventType = extractEventType(originalMessage);
            String vin = extractVin(originalMessage);
            String correlationId = extractCorrelationId(null, originalMessage);

            log.info("Processing auto loan DLQ: loanId={}, eventType={}, vin={}, messageId={}",
                loanId, eventType, vin, messageId);

            // Validate auto loan and vehicle status
            if (loanId != null) {
                validateAutoLoanStatus(loanId, messageId);
                assessCollateralImpact(loanId, vin, originalMessage, messageId);
                handleLoanPaymentIssues(loanId, originalMessage, exceptionMessage, messageId);
            }

            // Generate domain-specific alerts
            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Handle vehicle-specific issues
            if (vin != null) {
                handleVehicleRelatedIssues(vin, loanId, eventType, originalMessage, messageId);
            }

            // Check for insurance and title implications
            assessInsuranceAndTitleImpact(loanId, eventType, originalMessage, messageId);

            // Handle specific auto loan event failures
            handleSpecificAutoLoanEventFailure(eventType, loanId, originalMessage, messageId);

        } catch (Exception e) {
            log.error("Error in auto loan DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "auto-loan-events-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "FINANCIAL_AUTO_LOANS";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        String eventType = extractEventType(originalMessage);
        Double amount = extractAmount(originalMessage);

        // Critical events affecting collateral or payment
        if ("REPOSSESSION_INITIATED".equals(eventType) || "TITLE_TRANSFER".equals(eventType) ||
            "INSURANCE_LAPSE".equals(eventType) || "PAYMENT_DEFAULT".equals(eventType) ||
            "LOAN_MATURITY".equals(eventType)) {
            return true;
        }

        // Large loan amounts are always critical
        return amount != null && amount > 75000;
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String loanId = extractLoanId(originalMessage);
        String userId = extractUserId(originalMessage);
        String eventType = extractEventType(originalMessage);
        String vin = extractVin(originalMessage);
        Double amount = extractAmount(originalMessage);

        try {
            // Send immediate alert to auto loan operations team
            String alertTitle = String.format("CRITICAL: Auto Loan Event Failed - %s", eventType);
            String alertMessage = String.format(
                "Auto loan event processing failed:\n" +
                "Loan ID: %s\n" +
                "User ID: %s\n" +
                "Event Type: %s\n" +
                "VIN: %s\n" +
                "Amount: %s\n" +
                "Error: %s\n" +
                "IMMEDIATE ACTION REQUIRED - Auto loans involve collateral that may be at risk.",
                loanId != null ? loanId : "unknown",
                userId != null ? userId : "unknown",
                eventType != null ? eventType : "unknown",
                vin != null ? vin : "unknown",
                amount != null ? String.format("$%.2f", amount) : "unknown",
                exceptionMessage
            );

            notificationService.sendCriticalAlert(alertTitle, alertMessage,
                Map.of("loanId", loanId != null ? loanId : "unknown",
                       "userId", userId != null ? userId : "unknown",
                       "eventType", eventType != null ? eventType : "unknown",
                       "vin", vin != null ? vin : "unknown",
                       "messageId", messageId,
                       "businessImpact", "CRITICAL_COLLATERAL_RISK"));

            // Send customer notification for customer-facing events
            if (isCustomerImpactingEvent(eventType) && userId != null) {
                String customerMessage = getCustomerNotificationMessage(eventType);
                notificationService.sendNotification(userId,
                    "Auto Loan Service Update",
                    customerMessage,
                    messageId);
            }

            // Alert collections team for payment-related issues
            if (isPaymentRelatedEvent(eventType)) {
                notificationService.sendCollectionsAlert(
                    "Auto Loan Payment DLQ",
                    String.format("Auto loan payment event failed for loan %s (VIN: %s). " +
                        "Review payment status and collateral protection.", loanId, vin),
                    "HIGH"
                );
            }

            // Alert legal team for repossession or title issues
            if (isLegalEvent(eventType)) {
                notificationService.sendLegalAlert(
                    "Auto Loan Legal Event Failed",
                    String.format("Legal event %s failed for auto loan %s (VIN: %s). " +
                        "Review legal documentation and procedures.", eventType, loanId, vin),
                    "URGENT"
                );
            }

            // Insurance team notification for insurance-related events
            if (isInsuranceEvent(eventType)) {
                notificationService.sendInsuranceAlert(
                    "Auto Loan Insurance DLQ",
                    String.format("Insurance event %s failed for vehicle %s (loan: %s). " +
                        "Verify coverage and compliance.", eventType, vin, loanId),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Failed to send auto loan DLQ alerts: {}", e.getMessage());
        }
    }

    private void validateAutoLoanStatus(String loanId, String messageId) {
        try {
            var autoLoan = autoLoanRepository.findById(loanId);
            if (autoLoan.isPresent()) {
                String status = autoLoan.get().getStatus();
                String paymentStatus = autoLoan.get().getPaymentStatus();

                log.info("Auto loan status validation for DLQ: loanId={}, status={}, paymentStatus={}, messageId={}",
                    loanId, status, paymentStatus, messageId);

                // Check for high-risk status combinations
                if ("DEFAULT".equals(paymentStatus) || "DELINQUENT".equals(paymentStatus)) {
                    log.warn("Auto loan DLQ for loan with payment issues: loanId={}, paymentStatus={}",
                        loanId, paymentStatus);

                    notificationService.sendCriticalAlert(
                        "High-Risk Auto Loan DLQ",
                        String.format("Auto loan %s with payment status %s has failed event processing. " +
                            "Collateral may be at risk.", loanId, paymentStatus),
                        Map.of("loanId", loanId, "paymentStatus", paymentStatus, "riskLevel", "HIGH")
                    );
                }

                // Check loan maturity
                if ("MATURED".equals(status) || "NEAR_MATURITY".equals(status)) {
                    notificationService.sendOperationalAlert(
                        "Mature Auto Loan DLQ",
                        String.format("Auto loan %s near/at maturity has failed event processing. " +
                            "Review loan closure procedures.", loanId),
                        "HIGH"
                    );
                }
            } else {
                log.error("Auto loan not found for DLQ: loanId={}, messageId={}",
                    loanId, messageId);
            }
        } catch (Exception e) {
            log.error("Error validating auto loan status for DLQ: loanId={}, error={}",
                loanId, e.getMessage());
        }
    }

    private void assessCollateralImpact(String loanId, String vin, Object originalMessage, String messageId) {
        try {
            if (vin != null) {
                // Check vehicle status and value
                var vehicleInfo = vehicleService.getVehicleInfo(vin);
                if (vehicleInfo != null) {
                    Double currentValue = vehicleInfo.getCurrentValue();
                    String vehicleStatus = vehicleInfo.getStatus();

                    log.info("Assessing collateral impact: loanId={}, vin={}, value=${}, status={}",
                        loanId, vin, currentValue, vehicleStatus);

                    // Check loan-to-value ratio
                    if (loanId != null) {
                        Double loanBalance = autoLoanService.getCurrentBalance(loanId);
                        if (loanBalance != null && currentValue != null) {
                            double ltvRatio = loanBalance / currentValue;
                            if (ltvRatio > 1.2) { // Underwater loan
                                log.warn("DLQ for underwater auto loan: loanId={}, LTV={}%", loanId, ltvRatio * 100);

                                notificationService.sendRiskManagementAlert(
                                    "Underwater Auto Loan DLQ",
                                    String.format("Auto loan %s is underwater (LTV: %.1f%%) and has failed processing. " +
                                        "Enhanced monitoring required.", loanId, ltvRatio * 100),
                                    "HIGH"
                                );
                            }
                        }
                    }

                    // Check vehicle condition issues
                    if ("DAMAGED".equals(vehicleStatus) || "INOPERABLE".equals(vehicleStatus)) {
                        notificationService.sendCriticalAlert(
                            "Damaged Collateral Auto Loan DLQ",
                            String.format("Auto loan %s with damaged vehicle (VIN: %s) has failed processing. " +
                                "Review collateral protection measures.", loanId, vin),
                            Map.of("loanId", loanId, "vin", vin, "vehicleStatus", vehicleStatus)
                        );
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error assessing collateral impact: loanId={}, vin={}, error={}",
                loanId, vin, e.getMessage());
        }
    }

    private void handleLoanPaymentIssues(String loanId, Object originalMessage,
                                       String exceptionMessage, String messageId) {
        try {
            String eventType = extractEventType(originalMessage);

            if (isPaymentRelatedEvent(eventType)) {
                // Record payment failure for collections
                autoLoanService.recordPaymentFailure(loanId, Map.of(
                    "failureType", "AUTO_LOAN_DLQ",
                    "eventType", eventType,
                    "errorMessage", exceptionMessage,
                    "messageId", messageId,
                    "timestamp", Instant.now(),
                    "requiresCollectionsReview", true
                ));

                // Check payment history
                int missedPayments = autoLoanService.getMissedPaymentCount(loanId);
                if (missedPayments >= 2) {
                    log.warn("Auto loan DLQ with multiple missed payments: loanId={}, missedCount={}",
                        loanId, missedPayments);

                    // Trigger pre-collection procedures
                    kafkaTemplate.send("auto-loan-pre-collection-queue", Map.of(
                        "loanId", loanId,
                        "missedPayments", missedPayments,
                        "triggerReason", "DLQ_PAYMENT_FAILURE",
                        "messageId", messageId,
                        "timestamp", Instant.now()
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Error handling loan payment issues: loanId={}, error={}", loanId, e.getMessage());
        }
    }

    private void handleVehicleRelatedIssues(String vin, String loanId, String eventType,
                                          Object originalMessage, String messageId) {
        try {
            // Handle vehicle-specific events
            if ("VEHICLE_DAMAGE_REPORTED".equals(eventType)) {
                notificationService.sendInsuranceAlert(
                    "Vehicle Damage Report Failed",
                    String.format("Damage report processing failed for vehicle %s (loan: %s). " +
                        "Verify insurance claim status.", vin, loanId),
                    "HIGH"
                );
            } else if ("TITLE_TRANSFER".equals(eventType)) {
                notificationService.sendLegalAlert(
                    "Title Transfer Failed",
                    String.format("Title transfer failed for vehicle %s (loan: %s). " +
                        "Review DMV documentation.", vin, loanId),
                    "URGENT"
                );
            } else if ("VEHICLE_INSPECTION".equals(eventType)) {
                notificationService.sendOperationalAlert(
                    "Vehicle Inspection Failed",
                    String.format("Inspection processing failed for vehicle %s (loan: %s). " +
                        "Schedule manual inspection.", vin, loanId),
                    "MEDIUM"
                );
            }

            // Update vehicle status for failed events
            vehicleService.recordEventFailure(vin, eventType, messageId);

        } catch (Exception e) {
            log.error("Error handling vehicle-related issues: vin={}, error={}", vin, e.getMessage());
        }
    }

    private void assessInsuranceAndTitleImpact(String loanId, String eventType,
                                             Object originalMessage, String messageId) {
        try {
            if ("INSURANCE_LAPSE".equals(eventType)) {
                // Insurance lapse is critical for auto loans
                notificationService.sendCriticalAlert(
                    "URGENT: Insurance Lapse Processing Failed",
                    String.format("Insurance lapse processing failed for auto loan %s. " +
                        "Vehicle may be uninsured - immediate action required.", loanId),
                    Map.of("loanId", loanId, "urgency", "IMMEDIATE", "riskType", "UNINSURED_COLLATERAL")
                );

                // Trigger forced-place insurance evaluation
                kafkaTemplate.send("forced-place-insurance-queue", Map.of(
                    "loanId", loanId,
                    "reason", "INSURANCE_LAPSE_DLQ",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

            if ("TITLE_VERIFICATION".equals(eventType)) {
                notificationService.sendLegalAlert(
                    "Title Verification Failed",
                    String.format("Title verification failed for auto loan %s. " +
                        "Review lien status and title documentation.", loanId),
                    "HIGH"
                );
            }
        } catch (Exception e) {
            log.error("Error assessing insurance and title impact: loanId={}, error={}",
                loanId, e.getMessage());
        }
    }

    private void handleSpecificAutoLoanEventFailure(String eventType, String loanId,
                                                  Object originalMessage, String messageId) {
        try {
            switch (eventType) {
                case "LOAN_ORIGINATION":
                    handleOriginationFailure(loanId, originalMessage, messageId);
                    break;
                case "PAYMENT_PROCESSING":
                    handlePaymentProcessingFailure(loanId, originalMessage, messageId);
                    break;
                case "REPOSSESSION_INITIATED":
                    handleRepossessionFailure(loanId, originalMessage, messageId);
                    break;
                case "LOAN_PAYOFF":
                    handlePayoffFailure(loanId, originalMessage, messageId);
                    break;
                case "REFINANCING":
                    handleRefinancingFailure(loanId, originalMessage, messageId);
                    break;
                default:
                    log.info("No specific handling for auto loan event type: {}", eventType);
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling specific auto loan event failure: eventType={}, loanId={}, error={}",
                eventType, loanId, e.getMessage());
        }
    }

    private void handleOriginationFailure(String loanId, Object originalMessage, String messageId) {
        notificationService.sendCriticalAlert(
            "Auto Loan Origination Failed",
            String.format("Auto loan %s origination failed. Customer may be waiting for loan approval.", loanId),
            Map.of("loanId", loanId, "urgency", "HIGH", "customerImpact", "HIGH")
        );
    }

    private void handlePaymentProcessingFailure(String loanId, Object originalMessage, String messageId) {
        Double amount = extractAmount(originalMessage);
        notificationService.sendCriticalAlert(
            "Auto Loan Payment Failed",
            String.format("Payment of $%.2f failed for auto loan %s. Customer account may show incorrect balance.",
                amount != null ? amount : 0.0, loanId),
            Map.of("loanId", loanId, "amount", amount, "urgency", "HIGH")
        );
    }

    private void handleRepossessionFailure(String loanId, Object originalMessage, String messageId) {
        notificationService.sendExecutiveAlert(
            "CRITICAL: Repossession Processing Failed",
            String.format("Repossession processing failed for auto loan %s. " +
                "Legal and collections teams must be notified immediately.", loanId)
        );
    }

    private void handlePayoffFailure(String loanId, Object originalMessage, String messageId) {
        notificationService.sendCriticalAlert(
            "Auto Loan Payoff Failed",
            String.format("Loan payoff failed for %s. Title release may be delayed.", loanId),
            Map.of("loanId", loanId, "urgency", "HIGH", "titleImpact", "DELAYED")
        );
    }

    private void handleRefinancingFailure(String loanId, Object originalMessage, String messageId) {
        notificationService.sendOperationalAlert(
            "Auto Loan Refinancing Failed",
            String.format("Refinancing failed for auto loan %s. Customer rates may be affected.", loanId),
            "HIGH"
        );
    }

    // Circuit breaker fallback method
    public void handleAutoLoanDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                         int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String loanId = extractLoanId(originalMessage);
        String vin = extractVin(originalMessage);

        if (loanId != null) {
            try {
                // Mark auto loan for urgent review
                autoLoanService.markForUrgentReview(loanId, "AUTO_LOAN_DLQ_CIRCUIT_BREAKER");

                // Send executive notification with collateral risk
                notificationService.sendExecutiveAlert(
                    "Critical: Auto Loan DLQ Circuit Breaker Triggered",
                    String.format("Circuit breaker triggered for auto loan DLQ processing on %s (VIN: %s). " +
                        "This indicates a systemic issue affecting secured lending operations.", loanId, vin)
                );
            } catch (Exception e) {
                log.error("Error in auto loan DLQ fallback: {}", e.getMessage());
            }
        }
    }

    // Helper methods for event classification
    private boolean isCustomerImpactingEvent(String eventType) {
        return eventType != null && (eventType.contains("PAYMENT") || eventType.contains("RATE") ||
                                   eventType.contains("PAYOFF") || eventType.contains("INSURANCE"));
    }

    private boolean isPaymentRelatedEvent(String eventType) {
        return eventType != null && (eventType.contains("PAYMENT") || eventType.contains("DEFAULT") ||
                                   eventType.contains("DELINQUENT"));
    }

    private boolean isLegalEvent(String eventType) {
        return eventType != null && (eventType.contains("REPOSSESSION") || eventType.contains("TITLE") ||
                                   eventType.contains("LIEN"));
    }

    private boolean isInsuranceEvent(String eventType) {
        return eventType != null && (eventType.contains("INSURANCE") || eventType.contains("COVERAGE"));
    }

    private String getCustomerNotificationMessage(String eventType) {
        switch (eventType) {
            case "PAYMENT_PROCESSING":
                return "We're experiencing a temporary delay with your auto loan payment processing. " +
                       "Your payment will be processed shortly and no late fees will be applied.";
            case "INSURANCE_LAPSE":
                return "We're working to resolve an issue with your insurance verification. " +
                       "Please ensure your auto insurance is current to avoid any service disruptions.";
            case "RATE_ADJUSTMENT":
                return "We're processing an update to your auto loan rate. " +
                       "You'll receive confirmation once the adjustment is complete.";
            default:
                return "We're experiencing a temporary delay with your auto loan service. " +
                       "Our team has been notified and will resolve this shortly.";
        }
    }

    // Data extraction helper methods
    private String extractLoanId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object loanId = messageMap.get("loanId");
                if (loanId == null) loanId = messageMap.get("autoLoanId");
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

    private String extractVin(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object vin = messageMap.get("vin");
                if (vin == null) vin = messageMap.get("vehicleVin");
                return vin != null ? vin.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract VIN from message: {}", e.getMessage());
        }
        return null;
    }

    private Double extractAmount(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object amount = messageMap.get("amount");
                if (amount == null) amount = messageMap.get("paymentAmount");
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