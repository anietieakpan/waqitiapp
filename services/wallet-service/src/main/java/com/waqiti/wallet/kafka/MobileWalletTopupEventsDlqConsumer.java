package com.waqiti.wallet.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.wallet.service.*;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.common.notification.NotificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade DLQ consumer for mobile wallet topup events
 * Handles critical mobile wallet topup events that failed normal processing
 * Provides fraud prevention, customer service recovery, and financial reconciliation
 *
 * Critical for: Mobile payments, customer experience, financial integrity
 * SLA: Must process DLQ mobile wallet topup events within 30 seconds for customer service
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MobileWalletTopupEventsDlqConsumer {

    private final MobileWalletService mobileWalletService;
    private final TopupProcessingService topupProcessingService;
    private final PaymentReconciliationService paymentReconciliationService;
    private final CustomerServiceService customerServiceService;
    private final FraudDetectionService fraudDetectionService;
    private final WalletBalanceService walletBalanceService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 48-hour TTL for DLQ events
    private final Map<String, Instant> processedEventIds = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_HOURS = 48;

    // Metrics
    private final Counter dlqEventCounter = Counter.builder("mobile_wallet_topup_dlq_processed_total")
            .description("Total number of mobile wallet topup DLQ events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter failedTopupCounter = Counter.builder("failed_mobile_wallet_topups_dlq_total")
            .description("Total number of failed mobile wallet topups from DLQ events")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("mobile_wallet_topup_dlq_processing_duration")
            .description("Time taken to process mobile wallet topup DLQ events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"mobile-wallet-topup-events-dlq"},
        groupId = "wallet-service-mobile-topup-dlq-processor",
        containerFactory = "dlqKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 8000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @CircuitBreaker(name = "mobile-wallet-topup-dlq-processor", fallbackMethod = "handleMobileTopupDlqFailure")
    @Retry(name = "mobile-wallet-topup-dlq-processor")
    public void processMobileWalletTopupDlq(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();

        log.error("MOBILE TOPUP DLQ: Processing failed mobile wallet topup: {} from topic: {} partition: {} offset: {}",
                eventId, topic, partition, offset);

        try {
            // Check idempotency
            if (isEventAlreadyProcessed(eventId)) {
                log.info("Mobile wallet topup DLQ event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract and validate mobile topup data
            MobileTopupData topupData = extractTopupData(event.getPayload());
            validateTopupData(topupData);

            // Mark as processed for idempotency
            markEventAsProcessed(eventId);

            // Enhanced customer service and fraud assessment for DLQ events
            MobileTopupDlqAssessment assessment = assessDlqTopupEvent(topupData, event);

            // Process DLQ mobile wallet topup event
            processDlqMobileTopup(topupData, assessment, event);

            // Record successful processing metrics
            dlqEventCounter.increment();

            if ("FAILED".equals(topupData.getTopupStatus())) {
                failedTopupCounter.increment();
            }

            // Audit the DLQ processing
            auditMobileTopupDlqProcessing(topupData, event, assessment, "SUCCESS");

            log.error("MOBILE TOPUP DLQ: Successfully processed topup DLQ: {} - Recovery: {} Amount: {} Action: {}",
                    eventId, assessment.getRecoveryAction(), topupData.getTopupAmount(),
                    assessment.getCustomerServiceAction());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("MOBILE TOPUP DLQ: Invalid mobile topup DLQ data: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("MOBILE TOPUP DLQ: Failed to process mobile topup DLQ event: {}", eventId, e);
            auditMobileTopupDlqProcessing(null, event, null, "FAILED: " + e.getMessage());
            throw new RuntimeException("Mobile topup DLQ processing failed", e);

        } finally {
            sample.stop(processingTimer);
            cleanupIdempotencyCache();
        }
    }

    private boolean isEventAlreadyProcessed(String eventId) {
        Instant processedTime = processedEventIds.get(eventId);
        if (processedTime != null) {
            if (ChronoUnit.HOURS.between(processedTime, Instant.now()) < IDEMPOTENCY_TTL_HOURS) {
                return true;
            } else {
                processedEventIds.remove(eventId);
            }
        }
        return false;
    }

    private void markEventAsProcessed(String eventId) {
        processedEventIds.put(eventId, Instant.now());
    }

    private void cleanupIdempotencyCache() {
        Instant cutoff = Instant.now().minus(IDEMPOTENCY_TTL_HOURS, ChronoUnit.HOURS);
        processedEventIds.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    private MobileTopupData extractTopupData(Map<String, Object> payload) {
        return MobileTopupData.builder()
                .eventId(extractString(payload, "eventId"))
                .topupId(extractString(payload, "topupId"))
                .walletId(extractString(payload, "walletId"))
                .userId(extractString(payload, "userId"))
                .mobileNumber(extractString(payload, "mobileNumber"))
                .topupAmount(extractBigDecimal(payload, "topupAmount"))
                .currency(extractString(payload, "currency"))
                .topupStatus(extractString(payload, "topupStatus"))
                .failureReason(extractString(payload, "failureReason"))
                .paymentMethod(extractString(payload, "paymentMethod"))
                .originalEvent(extractMap(payload, "originalEvent"))
                .dlqFailureReason(extractString(payload, "dlqFailureReason"))
                .retryCount(extractInteger(payload, "retryCount"))
                .dlqTimestamp(extractInstant(payload, "dlqTimestamp"))
                .originalTimestamp(extractInstant(payload, "originalTimestamp"))
                .providerId(extractString(payload, "providerId"))
                .providerName(extractString(payload, "providerName"))
                .transactionReference(extractString(payload, "transactionReference"))
                .deviceInfo(extractMap(payload, "deviceInfo"))
                .locationData(extractMap(payload, "locationData"))
                .customerProfile(extractMap(payload, "customerProfile"))
                .fraudIndicators(extractStringList(payload, "fraudIndicators"))
                .businessRules(extractStringList(payload, "businessRules"))
                .reconciliationData(extractMap(payload, "reconciliationData"))
                .customerServiceFlags(extractStringList(payload, "customerServiceFlags"))
                .priorityLevel(extractString(payload, "priorityLevel"))
                .build();
    }

    private void validateTopupData(MobileTopupData topupData) {
        if (topupData.getEventId() == null || topupData.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID is required");
        }

        if (topupData.getTopupId() == null || topupData.getTopupId().trim().isEmpty()) {
            throw new IllegalArgumentException("Topup ID is required");
        }

        if (topupData.getWalletId() == null || topupData.getWalletId().trim().isEmpty()) {
            throw new IllegalArgumentException("Wallet ID is required");
        }

        if (topupData.getMobileNumber() == null || topupData.getMobileNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("Mobile number is required");
        }

        if (topupData.getTopupAmount() == null || topupData.getTopupAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valid topup amount is required");
        }

        if (topupData.getCurrency() == null || topupData.getCurrency().trim().isEmpty()) {
            throw new IllegalArgumentException("Currency is required");
        }

        List<String> validStatuses = List.of("PENDING", "PROCESSING", "FAILED", "TIMEOUT", "CANCELLED");
        if (topupData.getTopupStatus() == null || !validStatuses.contains(topupData.getTopupStatus())) {
            throw new IllegalArgumentException("Valid topup status is required");
        }
    }

    private MobileTopupDlqAssessment assessDlqTopupEvent(MobileTopupData topupData, GenericKafkaEvent event) {
        // Enhanced customer service and recovery assessment for DLQ events
        String recoveryAction = determineRecoveryAction(topupData);
        String customerServiceAction = determineCustomerServiceAction(topupData);
        boolean requiresManualReview = requiresManualReview(topupData);
        List<String> recoveryMeasures = determineRecoveryMeasures(topupData);
        String customerImpact = assessCustomerImpact(topupData);

        return MobileTopupDlqAssessment.builder()
                .recoveryAction(recoveryAction)
                .customerServiceAction(customerServiceAction)
                .requiresManualReview(requiresManualReview)
                .recoveryMeasures(recoveryMeasures)
                .customerImpact(customerImpact)
                .executiveEscalation(determineExecutiveEscalation(topupData))
                .customerNotificationRequired(determineCustomerNotification(topupData))
                .reconciliationRequired(determineReconciliationRequired(topupData))
                .fraudAnalysisRequired(determineFraudAnalysis(topupData))
                .providerEscalationNeeded(determineProviderEscalation(topupData))
                .refundProcessingNeeded(determineRefundProcessing(topupData))
                .build();
    }

    private String determineRecoveryAction(MobileTopupData topupData) {
        if ("FAILED".equals(topupData.getTopupStatus())) {
            if (topupData.getFraudIndicators().contains("SUSPICIOUS_PATTERN")) {
                return "FRAUD_INVESTIGATION_AND_RECOVERY";
            } else if (topupData.getFailureReason().contains("PROVIDER_ERROR")) {
                return "PROVIDER_RECONCILIATION_AND_RETRY";
            } else if (topupData.getFailureReason().contains("INSUFFICIENT_FUNDS")) {
                return "CUSTOMER_NOTIFICATION_AND_ALTERNATIVE_PAYMENT";
            } else {
                return "AUTOMATIC_RETRY_WITH_MONITORING";
            }
        } else if ("TIMEOUT".equals(topupData.getTopupStatus())) {
            return "STATUS_VERIFICATION_AND_RECONCILIATION";
        } else if ("PROCESSING".equals(topupData.getTopupStatus())) {
            return "EXTENDED_MONITORING_AND_TIMEOUT_HANDLING";
        } else {
            return "STANDARD_DLQ_PROCESSING";
        }
    }

    private String determineCustomerServiceAction(MobileTopupData topupData) {
        if ("FAILED".equals(topupData.getTopupStatus())) {
            if (topupData.getTopupAmount().compareTo(new BigDecimal("100")) > 0) {
                return "PROACTIVE_CUSTOMER_CONTACT";
            } else {
                return "AUTOMATED_CUSTOMER_NOTIFICATION";
            }
        } else if ("TIMEOUT".equals(topupData.getTopupStatus())) {
            return "STATUS_UPDATE_TO_CUSTOMER";
        } else {
            return "MONITORING_AND_STATUS_TRACKING";
        }
    }

    private boolean requiresManualReview(MobileTopupData topupData) {
        return topupData.getTopupAmount().compareTo(new BigDecimal("500")) > 0 ||
               topupData.getFraudIndicators().contains("SUSPICIOUS_PATTERN") ||
               topupData.getCustomerServiceFlags().contains("VIP_CUSTOMER") ||
               topupData.getCustomerServiceFlags().contains("FREQUENT_COMPLAINTS") ||
               topupData.getRetryCount() > 3;
    }

    private List<String> determineRecoveryMeasures(MobileTopupData topupData) {
        if ("FAILED".equals(topupData.getTopupStatus())) {
            return List.of(
                "TRANSACTION_STATUS_VERIFICATION",
                "BALANCE_RECONCILIATION",
                "CUSTOMER_NOTIFICATION",
                "PROVIDER_STATUS_CHECK",
                "REFUND_PROCESSING_IF_CHARGED",
                "FRAUD_ANALYSIS"
            );
        } else if ("TIMEOUT".equals(topupData.getTopupStatus())) {
            return List.of(
                "PROVIDER_STATUS_INQUIRY",
                "BALANCE_VERIFICATION",
                "CUSTOMER_STATUS_UPDATE",
                "EXTENDED_MONITORING"
            );
        } else {
            return List.of(
                "STANDARD_MONITORING",
                "STATUS_TRACKING",
                "CUSTOMER_NOTIFICATION"
            );
        }
    }

    private String assessCustomerImpact(MobileTopupData topupData) {
        if (topupData.getTopupAmount().compareTo(new BigDecimal("1000")) > 0) {
            return "HIGH";
        } else if (topupData.getTopupAmount().compareTo(new BigDecimal("100")) > 0) {
            return "MEDIUM";
        } else if (topupData.getCustomerServiceFlags().contains("VIP_CUSTOMER")) {
            return "HIGH";
        } else {
            return "LOW";
        }
    }

    private boolean determineExecutiveEscalation(MobileTopupData topupData) {
        return topupData.getTopupAmount().compareTo(new BigDecimal("5000")) > 0 ||
               topupData.getCustomerServiceFlags().contains("VIP_CUSTOMER") ||
               topupData.getFraudIndicators().contains("LARGE_SCALE_PATTERN") ||
               topupData.getRetryCount() > 5;
    }

    private boolean determineCustomerNotification(MobileTopupData topupData) {
        return "FAILED".equals(topupData.getTopupStatus()) ||
               "TIMEOUT".equals(topupData.getTopupStatus()) ||
               topupData.getTopupAmount().compareTo(new BigDecimal("50")) > 0;
    }

    private boolean determineReconciliationRequired(MobileTopupData topupData) {
        return "FAILED".equals(topupData.getTopupStatus()) ||
               "TIMEOUT".equals(topupData.getTopupStatus()) ||
               topupData.getReconciliationData().containsKey("REQUIRES_RECONCILIATION");
    }

    private boolean determineFraudAnalysis(MobileTopupData topupData) {
        return topupData.getFraudIndicators().size() > 0 ||
               topupData.getTopupAmount().compareTo(new BigDecimal("1000")) > 0 ||
               topupData.getRetryCount() > 3;
    }

    private boolean determineProviderEscalation(MobileTopupData topupData) {
        return topupData.getFailureReason().contains("PROVIDER_ERROR") ||
               topupData.getFailureReason().contains("PROVIDER_TIMEOUT") ||
               topupData.getRetryCount() > 2;
    }

    private boolean determineRefundProcessing(MobileTopupData topupData) {
        return "FAILED".equals(topupData.getTopupStatus()) &&
               topupData.getReconciliationData().containsKey("AMOUNT_CHARGED") &&
               "true".equals(topupData.getReconciliationData().get("AMOUNT_CHARGED").toString());
    }

    private void processDlqMobileTopup(MobileTopupData topupData,
                                     MobileTopupDlqAssessment assessment,
                                     GenericKafkaEvent event) {
        log.error("MOBILE TOPUP DLQ: Processing mobile topup DLQ - Topup: {}, Amount: {}, Status: {}, Recovery: {}",
                topupData.getTopupId(), topupData.getTopupAmount(),
                topupData.getTopupStatus(), assessment.getRecoveryAction());

        try {
            // Execute immediate recovery measures
            executeRecoveryMeasures(topupData, assessment);

            // Send immediate notifications
            sendImmediateNotifications(topupData, assessment);

            // Reconciliation if required
            if (assessment.isReconciliationRequired()) {
                processReconciliation(topupData, assessment);
            }

            // Fraud analysis if required
            if (assessment.isFraudAnalysisRequired()) {
                initiateFraudAnalysis(topupData, assessment);
            }

            // Provider escalation if needed
            if (assessment.isProviderEscalationNeeded()) {
                escalateToProvider(topupData, assessment);
            }

            // Refund processing if needed
            if (assessment.isRefundProcessingNeeded()) {
                processRefund(topupData, assessment);
            }

            // Manual review if required
            if (assessment.isRequiresManualReview()) {
                initiateManualReview(topupData, assessment);
            }

            // Executive escalation if required
            if (assessment.isExecutiveEscalation()) {
                escalateToExecutives(topupData, assessment);
            }

            // Customer notification if required
            if (assessment.isCustomerNotificationRequired()) {
                notifyCustomer(topupData, assessment);
            }

            log.error("MOBILE TOPUP DLQ: Topup DLQ processed - Topup: {}, RecoveryApplied: {}, CustomerImpact: {}",
                    topupData.getTopupId(), assessment.getRecoveryMeasures().size(), assessment.getCustomerImpact());

        } catch (Exception e) {
            log.error("MOBILE TOPUP DLQ: Failed to process mobile topup DLQ for topup: {}", topupData.getTopupId(), e);

            // Emergency fallback procedures
            executeEmergencyFallback(topupData, e);

            throw new RuntimeException("Mobile topup DLQ processing failed", e);
        }
    }

    private void executeRecoveryMeasures(MobileTopupData topupData, MobileTopupDlqAssessment assessment) {
        // Execute recovery action based on assessment
        switch (assessment.getRecoveryAction()) {
            case "FRAUD_INVESTIGATION_AND_RECOVERY":
                fraudDetectionService.investigateTopupFraud(topupData.getTopupId(), topupData);
                topupProcessingService.suspendTopupProcessing(topupData.getTopupId());
                break;

            case "PROVIDER_RECONCILIATION_AND_RETRY":
                paymentReconciliationService.reconcileWithProvider(topupData.getProviderId(), topupData);
                topupProcessingService.scheduleRetry(topupData.getTopupId());
                break;

            case "CUSTOMER_NOTIFICATION_AND_ALTERNATIVE_PAYMENT":
                customerServiceService.notifyInsufficientFunds(topupData.getUserId(), topupData);
                topupProcessingService.offerAlternativePayment(topupData.getTopupId());
                break;

            case "STATUS_VERIFICATION_AND_RECONCILIATION":
                topupProcessingService.verifyProviderStatus(topupData.getTopupId(), topupData.getProviderId());
                paymentReconciliationService.reconcileTransaction(topupData.getTopupId());
                break;

            case "EXTENDED_MONITORING_AND_TIMEOUT_HANDLING":
                topupProcessingService.extendMonitoring(topupData.getTopupId());
                topupProcessingService.setTimeoutHandling(topupData.getTopupId());
                break;

            default:
                topupProcessingService.standardDlqRecovery(topupData.getTopupId(), topupData);
        }

        // Apply additional recovery measures
        for (String measure : assessment.getRecoveryMeasures()) {
            try {
                switch (measure) {
                    case "TRANSACTION_STATUS_VERIFICATION":
                        topupProcessingService.verifyTransactionStatus(topupData.getTopupId());
                        break;

                    case "BALANCE_RECONCILIATION":
                        walletBalanceService.reconcileBalance(topupData.getWalletId(), topupData.getTopupAmount());
                        break;

                    case "PROVIDER_STATUS_CHECK":
                        topupProcessingService.checkProviderStatus(topupData.getProviderId(), topupData.getTopupId());
                        break;

                    case "REFUND_PROCESSING_IF_CHARGED":
                        if (assessment.isRefundProcessingNeeded()) {
                            paymentReconciliationService.processRefund(topupData.getTopupId(), topupData.getTopupAmount());
                        }
                        break;

                    case "FRAUD_ANALYSIS":
                        fraudDetectionService.analyzeTopupTransaction(topupData.getTopupId(), topupData);
                        break;

                    default:
                        topupProcessingService.applyGenericRecovery(measure, topupData.getTopupId());
                }
            } catch (Exception e) {
                log.error("Failed to apply recovery measure: {}", measure, e);
            }
        }
    }

    private void sendImmediateNotifications(MobileTopupData topupData, MobileTopupDlqAssessment assessment) {
        // High customer impact events require immediate notifications
        if ("HIGH".equals(assessment.getCustomerImpact())) {
            notificationService.sendOperationalAlert(
                    "MOBILE TOPUP DLQ - HIGH CUSTOMER IMPACT",
                    String.format("Mobile wallet topup failed with high customer impact: Topup %s, Amount: %s %s, User: %s",
                            topupData.getTopupId(), topupData.getTopupAmount(),
                            topupData.getCurrency(), topupData.getUserId()),
                    Map.of(
                            "topupId", topupData.getTopupId(),
                            "topupAmount", topupData.getTopupAmount().toString(),
                            "currency", topupData.getCurrency(),
                            "userId", topupData.getUserId(),
                            "customerImpact", assessment.getCustomerImpact(),
                            "recoveryAction", assessment.getRecoveryAction()
                    )
            );

            // Page customer service team for VIP customers
            if (topupData.getCustomerServiceFlags().contains("VIP_CUSTOMER")) {
                notificationService.pageCustomerServiceTeam(
                        "MOBILE_TOPUP_DLQ_VIP",
                        topupData.getTopupId(),
                        assessment.getCustomerImpact(),
                        topupData.getTopupAmount().toString()
                );
            }
        }
    }

    private void processReconciliation(MobileTopupData topupData, MobileTopupDlqAssessment assessment) {
        // Process transaction reconciliation
        paymentReconciliationService.reconcileTopupTransaction(
                topupData.getTopupId(),
                topupData.getProviderId(),
                topupData.getTopupAmount(),
                topupData.getReconciliationData()
        );

        // Update wallet balance if necessary
        walletBalanceService.reconcileTopupBalance(
                topupData.getWalletId(),
                topupData.getTopupId(),
                topupData.getTopupAmount()
        );
    }

    private void initiateFraudAnalysis(MobileTopupData topupData, MobileTopupDlqAssessment assessment) {
        // Analyze topup for fraud patterns
        fraudDetectionService.analyzeTopupFraud(
                topupData.getTopupId(),
                topupData.getTopupAmount(),
                topupData.getFraudIndicators(),
                topupData.getDeviceInfo()
        );

        // Update fraud patterns for mobile topups
        fraudDetectionService.updateTopupFraudPatterns(
                topupData.getProviderId(),
                topupData.getTopupAmount(),
                assessment.getCustomerImpact()
        );
    }

    private void escalateToProvider(MobileTopupData topupData, MobileTopupDlqAssessment assessment) {
        // Escalate to mobile service provider
        mobileWalletService.escalateToProvider(
                topupData.getProviderId(),
                topupData.getTopupId(),
                topupData.getFailureReason(),
                assessment.getCustomerImpact()
        );
    }

    private void processRefund(MobileTopupData topupData, MobileTopupDlqAssessment assessment) {
        // Process refund for failed but charged transactions
        paymentReconciliationService.processTopupRefund(
                topupData.getTopupId(),
                topupData.getWalletId(),
                topupData.getTopupAmount(),
                "FAILED_TOPUP_DLQ_RECOVERY"
        );
    }

    private void initiateManualReview(MobileTopupData topupData, MobileTopupDlqAssessment assessment) {
        // Create manual review case
        customerServiceService.createTopupReviewCase(
                topupData.getTopupId(),
                topupData.getUserId(),
                assessment.getCustomerImpact(),
                assessment.getRecoveryAction()
        );
    }

    private void escalateToExecutives(MobileTopupData topupData, MobileTopupDlqAssessment assessment) {
        notificationService.sendExecutiveAlert(
                "Critical Mobile Topup DLQ Event - Executive Review Required",
                String.format("High-value mobile topup failure: Topup %s, Amount: %s %s, Impact: %s",
                        topupData.getTopupId(),
                        topupData.getTopupAmount(),
                        topupData.getCurrency(),
                        assessment.getCustomerImpact()),
                Map.of(
                        "topupId", topupData.getTopupId(),
                        "topupAmount", topupData.getTopupAmount().toString(),
                        "customerImpact", assessment.getCustomerImpact(),
                        "recoveryAction", assessment.getRecoveryAction()
                )
        );
    }

    private void notifyCustomer(MobileTopupData topupData, MobileTopupDlqAssessment assessment) {
        if (topupData.getUserId() != null) {
            String message;
            if ("FAILED".equals(topupData.getTopupStatus())) {
                message = String.format("Your mobile topup of %s %s to %s could not be completed. We're investigating and will update you shortly.",
                        topupData.getTopupAmount(), topupData.getCurrency(), topupData.getMobileNumber());
            } else {
                message = String.format("Your mobile topup of %s %s to %s is being processed. We'll notify you when complete.",
                        topupData.getTopupAmount(), topupData.getCurrency(), topupData.getMobileNumber());
            }

            notificationService.sendTransactionAlert(
                    topupData.getUserId(),
                    "Mobile Topup Status Update",
                    message,
                    Map.of("topupId", topupData.getTopupId(), "status", topupData.getTopupStatus())
            );
        }
    }

    private void executeEmergencyFallback(MobileTopupData topupData, Exception error) {
        log.error("EMERGENCY: Executing emergency mobile topup fallback due to DLQ processing failure");

        try {
            // Emergency customer service alert
            customerServiceService.createEmergencyCase(
                    topupData.getTopupId(),
                    topupData.getUserId(),
                    "MOBILE_TOPUP_DLQ_PROCESSING_FAILED"
            );

            // Emergency notification
            notificationService.sendEmergencyAlert(
                    "CRITICAL: Mobile Topup DLQ Processing Failed",
                    String.format("Failed to process critical mobile topup DLQ for topup %s: %s",
                            topupData.getTopupId(), error.getMessage())
            );

            // Manual intervention alert
            notificationService.escalateToManualIntervention(
                    topupData.getEventId(),
                    "MOBILE_TOPUP_DLQ_PROCESSING_FAILED",
                    error.getMessage()
            );

        } catch (Exception e) {
            log.error("EMERGENCY: Emergency mobile topup fallback procedures also failed", e);
        }
    }

    private void handleValidationError(GenericKafkaEvent event, IllegalArgumentException e) {
        log.error("MOBILE TOPUP DLQ: Validation failed for topup DLQ event: {}", event.getEventId(), e);

        auditService.auditSecurityEvent(
                "MOBILE_TOPUP_DLQ_VALIDATION_ERROR",
                null,
                "Mobile topup DLQ validation failed: " + e.getMessage(),
                Map.of(
                        "eventId", event.getEventId(),
                        "error", e.getMessage(),
                        "payload", event.getPayload()
                )
        );
    }

    private void auditMobileTopupDlqProcessing(MobileTopupData topupData,
                                             GenericKafkaEvent event,
                                             MobileTopupDlqAssessment assessment,
                                             String status) {
        try {
            auditService.auditSecurityEvent(
                    "MOBILE_TOPUP_DLQ_EVENT_PROCESSED",
                    topupData != null ? topupData.getUserId() : null,
                    String.format("Mobile topup DLQ event processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "topupId", topupData != null ? topupData.getTopupId() : "unknown",
                            "topupAmount", topupData != null ? topupData.getTopupAmount().toString() : "0",
                            "topupStatus", topupData != null ? topupData.getTopupStatus() : "unknown",
                            "recoveryAction", assessment != null ? assessment.getRecoveryAction() : "none",
                            "customerImpact", assessment != null ? assessment.getCustomerImpact() : "unknown",
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit mobile topup DLQ processing", e);
        }
    }

    @DltHandler
    public void handleDlt(@Payload GenericKafkaEvent event,
                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("CRITICAL: Mobile topup DLQ event sent to final DLT - EventId: {}", event.getEventId());

        try {
            MobileTopupData topupData = extractTopupData(event.getPayload());

            // Emergency customer service case creation
            customerServiceService.createEmergencyCase(
                    topupData.getTopupId(),
                    topupData.getUserId(),
                    "MOBILE_TOPUP_FINAL_DLT"
            );

            // Critical alert for DLT
            notificationService.sendEmergencyAlert(
                    "CRITICAL: Mobile Topup DLQ in Final DLT",
                    "Critical mobile topup could not be processed even in DLQ - immediate manual intervention required"
            );

        } catch (Exception e) {
            log.error("Failed to handle mobile topup DLQ final DLT event: {}", event.getEventId(), e);
        }
    }

    public void handleMobileTopupDlqFailure(GenericKafkaEvent event, String topic, int partition,
                                          long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("EMERGENCY: Circuit breaker activated for mobile topup DLQ processing - EventId: {}",
                event.getEventId(), e);

        try {
            MobileTopupData topupData = extractTopupData(event.getPayload());

            // Emergency customer service case
            customerServiceService.createEmergencyCase(
                    topupData.getTopupId(),
                    topupData.getUserId(),
                    "MOBILE_TOPUP_DLQ_CIRCUIT_BREAKER"
            );

            // Emergency alert
            notificationService.sendEmergencyAlert(
                    "Mobile Topup DLQ Circuit Breaker Open",
                    "Mobile topup DLQ processing is failing - customer service severely impacted"
            );

        } catch (Exception ex) {
            log.error("Failed to handle mobile topup DLQ circuit breaker fallback", ex);
        }

        acknowledgment.acknowledge();
    }

    // Helper methods
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer extractInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        return new BigDecimal(value.toString());
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Map.of();
    }

    // Data classes
    @lombok.Data
    @lombok.Builder
    public static class MobileTopupData {
        private String eventId;
        private String topupId;
        private String walletId;
        private String userId;
        private String mobileNumber;
        private BigDecimal topupAmount;
        private String currency;
        private String topupStatus;
        private String failureReason;
        private String paymentMethod;
        private Map<String, Object> originalEvent;
        private String dlqFailureReason;
        private Integer retryCount;
        private Instant dlqTimestamp;
        private Instant originalTimestamp;
        private String providerId;
        private String providerName;
        private String transactionReference;
        private Map<String, Object> deviceInfo;
        private Map<String, Object> locationData;
        private Map<String, Object> customerProfile;
        private List<String> fraudIndicators;
        private List<String> businessRules;
        private Map<String, Object> reconciliationData;
        private List<String> customerServiceFlags;
        private String priorityLevel;
    }

    @lombok.Data
    @lombok.Builder
    public static class MobileTopupDlqAssessment {
        private String recoveryAction;
        private String customerServiceAction;
        private boolean requiresManualReview;
        private List<String> recoveryMeasures;
        private String customerImpact;
        private boolean executiveEscalation;
        private boolean customerNotificationRequired;
        private boolean reconciliationRequired;
        private boolean fraudAnalysisRequired;
        private boolean providerEscalationNeeded;
        private boolean refundProcessingNeeded;
    }
}