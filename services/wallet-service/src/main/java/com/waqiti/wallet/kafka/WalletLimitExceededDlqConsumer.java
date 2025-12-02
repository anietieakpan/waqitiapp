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
 * Production-grade DLQ consumer for wallet limit exceeded events
 * Handles critical wallet limit events that failed normal processing
 * Provides fraud prevention, regulatory compliance, and customer protection
 *
 * Critical for: Transaction limits, fraud prevention, regulatory compliance
 * SLA: Must process DLQ wallet limit events within 20 seconds for customer protection
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WalletLimitExceededDlqConsumer {

    private final WalletLimitService walletLimitService;
    private final TransactionLimitService transactionLimitService;
    private final WalletComplianceService walletComplianceService;
    private final FraudPreventionService fraudPreventionService;
    private final CustomerProtectionService customerProtectionService;
    private final WalletRiskService walletRiskService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 48-hour TTL for DLQ events
    private final Map<String, Instant> processedEventIds = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_HOURS = 48;

    // Metrics
    private final Counter dlqEventCounter = Counter.builder("wallet_limit_exceeded_dlq_processed_total")
            .description("Total number of wallet limit exceeded DLQ events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter highValueLimitCounter = Counter.builder("high_value_wallet_limit_dlq_total")
            .description("Total number of high-value wallet limit DLQ events processed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("wallet_limit_dlq_processing_duration")
            .description("Time taken to process wallet limit exceeded DLQ events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"wallet-limit-exceeded-dlq"},
        groupId = "wallet-service-limit-dlq-processor",
        containerFactory = "dlqKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1500, multiplier = 2.0, maxDelay = 6000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @CircuitBreaker(name = "wallet-limit-dlq-processor", fallbackMethod = "handleWalletLimitDlqFailure")
    @Retry(name = "wallet-limit-dlq-processor")
    public void processWalletLimitExceededDlq(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();

        log.error("WALLET LIMIT DLQ: Processing failed wallet limit event: {} from topic: {} partition: {} offset: {}",
                eventId, topic, partition, offset);

        try {
            // Check idempotency
            if (isEventAlreadyProcessed(eventId)) {
                log.info("Wallet limit DLQ event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract and validate wallet limit data
            WalletLimitData limitData = extractLimitData(event.getPayload());
            validateLimitData(limitData);

            // Mark as processed for idempotency
            markEventAsProcessed(eventId);

            // Enhanced compliance and fraud assessment for DLQ events
            WalletLimitDlqAssessment assessment = assessDlqLimitEvent(limitData, event);

            // Process DLQ wallet limit event
            processDlqWalletLimit(limitData, assessment, event);

            // Record successful processing metrics
            dlqEventCounter.increment();

            if (limitData.getTransactionAmount().compareTo(new BigDecimal("10000")) > 0) {
                highValueLimitCounter.increment();
            }

            // Audit the DLQ processing
            auditWalletLimitDlqProcessing(limitData, event, assessment, "SUCCESS");

            log.error("WALLET LIMIT DLQ: Successfully processed limit DLQ: {} - Risk: {} Type: {} Action: {}",
                    eventId, assessment.getRiskLevel(), limitData.getLimitType(),
                    assessment.getComplianceAction());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("WALLET LIMIT DLQ: Invalid wallet limit DLQ data: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("WALLET LIMIT DLQ: Failed to process wallet limit DLQ event: {}", eventId, e);
            auditWalletLimitDlqProcessing(null, event, null, "FAILED: " + e.getMessage());
            throw new RuntimeException("Wallet limit DLQ processing failed", e);

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

    private WalletLimitData extractLimitData(Map<String, Object> payload) {
        return WalletLimitData.builder()
                .eventId(extractString(payload, "eventId"))
                .walletId(extractString(payload, "walletId"))
                .userId(extractString(payload, "userId"))
                .accountId(extractString(payload, "accountId"))
                .transactionId(extractString(payload, "transactionId"))
                .limitType(extractString(payload, "limitType"))
                .transactionAmount(extractBigDecimal(payload, "transactionAmount"))
                .currency(extractString(payload, "currency"))
                .currentLimit(extractBigDecimal(payload, "currentLimit"))
                .remainingLimit(extractBigDecimal(payload, "remainingLimit"))
                .exceedAmount(extractBigDecimal(payload, "exceedAmount"))
                .limitPeriod(extractString(payload, "limitPeriod"))
                .originalEvent(extractMap(payload, "originalEvent"))
                .failureReason(extractString(payload, "failureReason"))
                .retryCount(extractInteger(payload, "retryCount"))
                .dlqTimestamp(extractInstant(payload, "dlqTimestamp"))
                .originalTimestamp(extractInstant(payload, "originalTimestamp"))
                .transactionType(extractString(payload, "transactionType"))
                .merchantInfo(extractMap(payload, "merchantInfo"))
                .deviceInfo(extractMap(payload, "deviceInfo"))
                .locationData(extractMap(payload, "locationData"))
                .riskIndicators(extractStringList(payload, "riskIndicators"))
                .complianceFlags(extractStringList(payload, "complianceFlags"))
                .customerTier(extractString(payload, "customerTier"))
                .walletType(extractString(payload, "walletType"))
                .businessImpact(extractString(payload, "businessImpact"))
                .regulatoryRequirements(extractStringList(payload, "regulatoryRequirements"))
                .build();
    }

    private void validateLimitData(WalletLimitData limitData) {
        if (limitData.getEventId() == null || limitData.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID is required");
        }

        if (limitData.getWalletId() == null || limitData.getWalletId().trim().isEmpty()) {
            throw new IllegalArgumentException("Wallet ID is required");
        }

        if (limitData.getLimitType() == null || limitData.getLimitType().trim().isEmpty()) {
            throw new IllegalArgumentException("Limit type is required");
        }

        List<String> validLimitTypes = List.of(
            "DAILY_SPENDING_LIMIT", "MONTHLY_SPENDING_LIMIT", "TRANSACTION_LIMIT",
            "VELOCITY_LIMIT", "CUMULATIVE_LIMIT", "MERCHANT_CATEGORY_LIMIT",
            "INTERNATIONAL_LIMIT", "ATM_WITHDRAWAL_LIMIT"
        );
        if (!validLimitTypes.contains(limitData.getLimitType())) {
            throw new IllegalArgumentException("Invalid limit type: " + limitData.getLimitType());
        }

        if (limitData.getTransactionAmount() == null || limitData.getTransactionAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valid transaction amount is required");
        }

        if (limitData.getCurrency() == null || limitData.getCurrency().trim().isEmpty()) {
            throw new IllegalArgumentException("Currency is required");
        }

        if (limitData.getCurrentLimit() == null || limitData.getCurrentLimit().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Valid current limit is required");
        }
    }

    private WalletLimitDlqAssessment assessDlqLimitEvent(WalletLimitData limitData, GenericKafkaEvent event) {
        // Enhanced risk and compliance assessment for DLQ events
        String enhancedRiskLevel = determineEnhancedRiskLevel(limitData);
        String complianceAction = determineComplianceAction(limitData, enhancedRiskLevel);
        boolean requiresCompliance = requiresComplianceReview(limitData);
        List<String> protectionMeasures = determineProtectionMeasures(limitData, enhancedRiskLevel);
        String businessImpact = assessBusinessImpact(limitData);

        return WalletLimitDlqAssessment.builder()
                .riskLevel(enhancedRiskLevel)
                .complianceAction(complianceAction)
                .requiresComplianceReview(requiresCompliance)
                .protectionMeasures(protectionMeasures)
                .businessImpact(businessImpact)
                .executiveEscalation(determineExecutiveEscalation(limitData, enhancedRiskLevel))
                .customerNotificationRequired(determineCustomerNotification(limitData))
                .regulatoryReporting(determineRegulatoryReporting(limitData))
                .fraudAnalysisRequired(determineFraudAnalysis(limitData))
                .limitAdjustmentNeeded(determineLimitAdjustment(limitData))
                .build();
    }

    private String determineEnhancedRiskLevel(WalletLimitData limitData) {
        // DLQ events automatically get elevated risk assessment
        if (limitData.getTransactionAmount().compareTo(new BigDecimal("50000")) > 0 ||
            limitData.getRiskIndicators().contains("SUSPICIOUS_PATTERN") ||
            limitData.getRiskIndicators().contains("FRAUD_INDICATOR")) {
            return "CRITICAL";
        } else if (limitData.getTransactionAmount().compareTo(new BigDecimal("10000")) > 0 ||
                   "INTERNATIONAL_LIMIT".equals(limitData.getLimitType()) ||
                   limitData.getRiskIndicators().contains("UNUSUAL_BEHAVIOR")) {
            return "HIGH";
        } else if (limitData.getTransactionAmount().compareTo(new BigDecimal("1000")) > 0) {
            return "MEDIUM";
        } else {
            return "ELEVATED"; // Even low-value events in DLQ get elevated
        }
    }

    private String determineComplianceAction(WalletLimitData limitData, String riskLevel) {
        if ("CRITICAL".equals(riskLevel)) {
            if (limitData.getTransactionAmount().compareTo(new BigDecimal("50000")) > 0) {
                return "IMMEDIATE_REGULATORY_NOTIFICATION";
            } else if (limitData.getRiskIndicators().contains("FRAUD_INDICATOR")) {
                return "ENHANCED_DUE_DILIGENCE";
            } else {
                return "COMPREHENSIVE_TRANSACTION_REVIEW";
            }
        } else if ("HIGH".equals(riskLevel)) {
            return "DETAILED_COMPLIANCE_CHECK";
        } else {
            return "STANDARD_LIMIT_REVIEW";
        }
    }

    private boolean requiresComplianceReview(WalletLimitData limitData) {
        return limitData.getTransactionAmount().compareTo(new BigDecimal("10000")) > 0 ||
               limitData.getComplianceFlags().contains("AML_REVIEW_REQUIRED") ||
               limitData.getComplianceFlags().contains("KYC_UPDATE_NEEDED") ||
               "INTERNATIONAL_LIMIT".equals(limitData.getLimitType()) ||
               limitData.getRiskIndicators().contains("SUSPICIOUS_PATTERN");
    }

    private List<String> determineProtectionMeasures(WalletLimitData limitData, String riskLevel) {
        if ("CRITICAL".equals(riskLevel)) {
            return List.of(
                "TEMPORARY_WALLET_SUSPENSION",
                "ENHANCED_TRANSACTION_MONITORING",
                "FRAUD_ANALYSIS_INITIATION",
                "COMPLIANCE_TEAM_NOTIFICATION",
                "CUSTOMER_VERIFICATION_REQUIRED",
                "REGULATORY_ALERT_GENERATION"
            );
        } else if ("HIGH".equals(riskLevel)) {
            return List.of(
                "ENHANCED_VERIFICATION",
                "TRANSACTION_MONITORING",
                "COMPLIANCE_REVIEW",
                "CUSTOMER_CONTACT"
            );
        } else {
            return List.of(
                "STANDARD_VERIFICATION",
                "BASIC_MONITORING",
                "LIMIT_REVIEW"
            );
        }
    }

    private String assessBusinessImpact(WalletLimitData limitData) {
        if (limitData.getTransactionAmount().compareTo(new BigDecimal("100000")) > 0) {
            return "SEVERE";
        } else if (limitData.getTransactionAmount().compareTo(new BigDecimal("25000")) > 0) {
            return "HIGH";
        } else if (limitData.getTransactionAmount().compareTo(new BigDecimal("5000")) > 0) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    private boolean determineExecutiveEscalation(WalletLimitData limitData, String riskLevel) {
        return "CRITICAL".equals(riskLevel) ||
               limitData.getTransactionAmount().compareTo(new BigDecimal("100000")) > 0 ||
               limitData.getRiskIndicators().contains("FRAUD_INDICATOR") ||
               limitData.getComplianceFlags().contains("EXECUTIVE_REVIEW_REQUIRED");
    }

    private boolean determineCustomerNotification(WalletLimitData limitData) {
        return limitData.getTransactionAmount().compareTo(new BigDecimal("1000")) > 0 ||
               limitData.getRiskIndicators().contains("UNUSUAL_BEHAVIOR") ||
               "INTERNATIONAL_LIMIT".equals(limitData.getLimitType());
    }

    private boolean determineRegulatoryReporting(WalletLimitData limitData) {
        return limitData.getTransactionAmount().compareTo(new BigDecimal("10000")) > 0 ||
               limitData.getComplianceFlags().contains("REGULATORY_REPORTING_REQUIRED") ||
               limitData.getRegulatoryRequirements().contains("SAR_FILING") ||
               limitData.getRegulatoryRequirements().contains("CTR_FILING");
    }

    private boolean determineFraudAnalysis(WalletLimitData limitData) {
        return limitData.getRiskIndicators().contains("FRAUD_INDICATOR") ||
               limitData.getRiskIndicators().contains("SUSPICIOUS_PATTERN") ||
               limitData.getTransactionAmount().compareTo(new BigDecimal("25000")) > 0;
    }

    private boolean determineLimitAdjustment(WalletLimitData limitData) {
        return limitData.getExceedAmount().compareTo(limitData.getCurrentLimit().multiply(new BigDecimal("0.1"))) > 0 ||
               "VIP".equals(limitData.getCustomerTier()) ||
               limitData.getComplianceFlags().contains("LIMIT_INCREASE_APPROVED");
    }

    private void processDlqWalletLimit(WalletLimitData limitData,
                                     WalletLimitDlqAssessment assessment,
                                     GenericKafkaEvent event) {
        log.error("WALLET LIMIT DLQ: Processing wallet limit DLQ - Wallet: {}, Amount: {}, Type: {}, Risk: {}",
                limitData.getWalletId(), limitData.getTransactionAmount(),
                limitData.getLimitType(), assessment.getRiskLevel());

        try {
            // Apply immediate protection measures
            applyProtectionMeasures(limitData, assessment);

            // Send immediate notifications
            sendImmediateNotifications(limitData, assessment);

            // Compliance review if required
            if (assessment.isRequiresComplianceReview()) {
                initiateComplianceReview(limitData, assessment);
            }

            // Fraud analysis if required
            if (assessment.isFraudAnalysisRequired()) {
                initiateFraudAnalysis(limitData, assessment);
            }

            // Update wallet risk assessment
            updateWalletRiskAssessment(limitData, assessment);

            // Limit adjustment if needed
            if (assessment.isLimitAdjustmentNeeded()) {
                processLimitAdjustment(limitData, assessment);
            }

            // Executive escalation if required
            if (assessment.isExecutiveEscalation()) {
                escalateToExecutives(limitData, assessment);
            }

            // Customer notification if required
            if (assessment.isCustomerNotificationRequired()) {
                notifyCustomer(limitData, assessment);
            }

            // Regulatory reporting if required
            if (assessment.isRegulatoryReporting()) {
                initiateRegulatoryReporting(limitData, assessment);
            }

            log.error("WALLET LIMIT DLQ: Limit DLQ processed - Wallet: {}, ProtectionApplied: {}, BusinessImpact: {}",
                    limitData.getWalletId(), assessment.getProtectionMeasures().size(), assessment.getBusinessImpact());

        } catch (Exception e) {
            log.error("WALLET LIMIT DLQ: Failed to process wallet limit DLQ for wallet: {}", limitData.getWalletId(), e);

            // Emergency fallback procedures
            executeEmergencyFallback(limitData, e);

            throw new RuntimeException("Wallet limit DLQ processing failed", e);
        }
    }

    private void applyProtectionMeasures(WalletLimitData limitData, WalletLimitDlqAssessment assessment) {
        for (String measure : assessment.getProtectionMeasures()) {
            try {
                switch (measure) {
                    case "TEMPORARY_WALLET_SUSPENSION":
                        walletLimitService.temporaryWalletSuspension(limitData.getWalletId(), "DLQ_PROTECTION");
                        break;

                    case "ENHANCED_TRANSACTION_MONITORING":
                        walletRiskService.enhanceTransactionMonitoring(limitData.getWalletId());
                        break;

                    case "FRAUD_ANALYSIS_INITIATION":
                        fraudPreventionService.initiateWalletFraudAnalysis(limitData.getWalletId(), limitData);
                        break;

                    case "CUSTOMER_VERIFICATION_REQUIRED":
                        customerProtectionService.requireEnhancedVerification(limitData.getUserId());
                        break;

                    case "ENHANCED_VERIFICATION":
                        walletLimitService.enableEnhancedVerification(limitData.getWalletId());
                        break;

                    case "TRANSACTION_MONITORING":
                        walletRiskService.enableTransactionMonitoring(limitData.getWalletId());
                        break;

                    default:
                        walletLimitService.applyGenericProtection(measure, limitData.getWalletId());
                }
            } catch (Exception e) {
                log.error("Failed to apply protection measure: {}", measure, e);
            }
        }
    }

    private void sendImmediateNotifications(WalletLimitData limitData, WalletLimitDlqAssessment assessment) {
        // Critical and high-risk DLQ events require immediate escalation
        if ("CRITICAL".equals(assessment.getRiskLevel()) || "HIGH".equals(assessment.getRiskLevel())) {
            notificationService.sendCriticalAlert(
                    "WALLET LIMIT DLQ - COMPLIANCE REVIEW REQUIRED",
                    String.format("Critical wallet limit exceeded event in DLQ: Wallet %s, Amount: %s %s, Type: %s",
                            limitData.getWalletId(), limitData.getTransactionAmount(),
                            limitData.getCurrency(), limitData.getLimitType()),
                    Map.of(
                            "walletId", limitData.getWalletId(),
                            "transactionAmount", limitData.getTransactionAmount().toString(),
                            "currency", limitData.getCurrency(),
                            "limitType", limitData.getLimitType(),
                            "riskLevel", assessment.getRiskLevel(),
                            "complianceAction", assessment.getComplianceAction()
                    )
            );

            // Page compliance team for high-value transactions
            if (limitData.getTransactionAmount().compareTo(new BigDecimal("50000")) > 0) {
                notificationService.pageComplianceTeam(
                        "WALLET_LIMIT_DLQ_HIGH_VALUE",
                        limitData.getLimitType(),
                        assessment.getRiskLevel(),
                        limitData.getTransactionAmount().toString()
                );
            }
        }
    }

    private void initiateComplianceReview(WalletLimitData limitData, WalletLimitDlqAssessment assessment) {
        // Execute compliance action based on assessment
        switch (assessment.getComplianceAction()) {
            case "IMMEDIATE_REGULATORY_NOTIFICATION":
                walletComplianceService.immediateRegulatoryNotification(limitData, assessment);
                break;

            case "ENHANCED_DUE_DILIGENCE":
                walletComplianceService.initiateEnhancedDueDiligence(limitData.getUserId(), limitData);
                break;

            case "COMPREHENSIVE_TRANSACTION_REVIEW":
                walletComplianceService.comprehensiveTransactionReview(limitData.getWalletId(), limitData);
                break;

            case "DETAILED_COMPLIANCE_CHECK":
                walletComplianceService.detailedComplianceCheck(limitData.getUserId(), limitData);
                break;

            default:
                walletComplianceService.standardLimitReview(limitData.getWalletId(), limitData);
        }
    }

    private void initiateFraudAnalysis(WalletLimitData limitData, WalletLimitDlqAssessment assessment) {
        // Initiate fraud analysis for suspicious transactions
        fraudPreventionService.analyzeWalletTransaction(
                limitData.getWalletId(),
                limitData.getTransactionId(),
                limitData.getTransactionAmount(),
                limitData.getRiskIndicators()
        );

        // Update fraud patterns
        fraudPreventionService.updateFraudPatterns(
                limitData.getLimitType(),
                limitData.getTransactionAmount(),
                assessment.getRiskLevel()
        );
    }

    private void updateWalletRiskAssessment(WalletLimitData limitData, WalletLimitDlqAssessment assessment) {
        // Update wallet risk profile
        walletRiskService.updateWalletRiskProfile(
                limitData.getWalletId(),
                assessment.getRiskLevel(),
                limitData.getRiskIndicators()
        );

        // Update customer risk scoring
        walletRiskService.updateCustomerRiskScoring(
                limitData.getUserId(),
                limitData.getTransactionAmount(),
                assessment.getBusinessImpact()
        );
    }

    private void processLimitAdjustment(WalletLimitData limitData, WalletLimitDlqAssessment assessment) {
        // Process limit adjustments for legitimate high-value customers
        if ("VIP".equals(limitData.getCustomerTier()) ||
            limitData.getComplianceFlags().contains("LIMIT_INCREASE_APPROVED")) {

            walletLimitService.processLimitAdjustment(
                    limitData.getWalletId(),
                    limitData.getLimitType(),
                    limitData.getExceedAmount(),
                    assessment.getBusinessImpact()
            );
        }
    }

    private void escalateToExecutives(WalletLimitData limitData, WalletLimitDlqAssessment assessment) {
        notificationService.sendExecutiveAlert(
                "Critical Wallet Limit DLQ Event - Executive Review Required",
                String.format("High-value wallet limit exceeded: Wallet %s, Amount: %s %s, Risk: %s",
                        limitData.getWalletId(),
                        limitData.getTransactionAmount(),
                        limitData.getCurrency(),
                        assessment.getRiskLevel()),
                Map.of(
                        "walletId", limitData.getWalletId(),
                        "transactionAmount", limitData.getTransactionAmount().toString(),
                        "businessImpact", assessment.getBusinessImpact(),
                        "riskLevel", assessment.getRiskLevel()
                )
        );
    }

    private void notifyCustomer(WalletLimitData limitData, WalletLimitDlqAssessment assessment) {
        if (limitData.getUserId() != null) {
            notificationService.sendTransactionAlert(
                    limitData.getUserId(),
                    "Transaction Limit Information",
                    String.format("Your transaction of %s %s exceeded your current %s limit. We're reviewing this for your security.",
                            limitData.getTransactionAmount(), limitData.getCurrency(), limitData.getLimitType()),
                    Map.of("transactionId", limitData.getTransactionId(), "requiresAction", false)
            );
        }
    }

    private void initiateRegulatoryReporting(WalletLimitData limitData, WalletLimitDlqAssessment assessment) {
        // Create regulatory reports for high-value transactions
        walletComplianceService.initiateRegulatoryReporting(
                limitData.getTransactionId(),
                limitData.getTransactionAmount(),
                limitData.getCurrency(),
                limitData.getRegulatoryRequirements(),
                assessment.getBusinessImpact()
        );
    }

    private void executeEmergencyFallback(WalletLimitData limitData, Exception error) {
        log.error("EMERGENCY: Executing emergency wallet limit fallback due to DLQ processing failure");

        try {
            // Emergency wallet protection
            walletLimitService.emergencyWalletProtection(limitData.getWalletId());

            // Emergency notification
            notificationService.sendEmergencyAlert(
                    "CRITICAL: Wallet Limit DLQ Processing Failed",
                    String.format("Failed to process critical wallet limit DLQ for wallet %s: %s",
                            limitData.getWalletId(), error.getMessage())
            );

            // Manual intervention alert
            notificationService.escalateToManualIntervention(
                    limitData.getEventId(),
                    "WALLET_LIMIT_DLQ_PROCESSING_FAILED",
                    error.getMessage()
            );

        } catch (Exception e) {
            log.error("EMERGENCY: Emergency wallet limit fallback procedures also failed", e);
        }
    }

    private void handleValidationError(GenericKafkaEvent event, IllegalArgumentException e) {
        log.error("WALLET LIMIT DLQ: Validation failed for limit DLQ event: {}", event.getEventId(), e);

        auditService.auditSecurityEvent(
                "WALLET_LIMIT_DLQ_VALIDATION_ERROR",
                null,
                "Wallet limit DLQ validation failed: " + e.getMessage(),
                Map.of(
                        "eventId", event.getEventId(),
                        "error", e.getMessage(),
                        "payload", event.getPayload()
                )
        );
    }

    private void auditWalletLimitDlqProcessing(WalletLimitData limitData,
                                             GenericKafkaEvent event,
                                             WalletLimitDlqAssessment assessment,
                                             String status) {
        try {
            auditService.auditSecurityEvent(
                    "WALLET_LIMIT_DLQ_EVENT_PROCESSED",
                    limitData != null ? limitData.getUserId() : null,
                    String.format("Wallet limit DLQ event processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "walletId", limitData != null ? limitData.getWalletId() : "unknown",
                            "limitType", limitData != null ? limitData.getLimitType() : "unknown",
                            "transactionAmount", limitData != null ? limitData.getTransactionAmount().toString() : "0",
                            "riskLevel", assessment != null ? assessment.getRiskLevel() : "unknown",
                            "complianceAction", assessment != null ? assessment.getComplianceAction() : "none",
                            "businessImpact", assessment != null ? assessment.getBusinessImpact() : "unknown",
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit wallet limit DLQ processing", e);
        }
    }

    @DltHandler
    public void handleDlt(@Payload GenericKafkaEvent event,
                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("CRITICAL: Wallet limit DLQ event sent to final DLT - EventId: {}", event.getEventId());

        try {
            WalletLimitData limitData = extractLimitData(event.getPayload());

            // Emergency wallet protection for final DLT events
            walletLimitService.emergencyWalletProtection(limitData.getWalletId());

            // Critical alert for DLT
            notificationService.sendEmergencyAlert(
                    "CRITICAL: Wallet Limit DLQ in Final DLT",
                    "Critical wallet limit event could not be processed even in DLQ - immediate manual intervention required"
            );

        } catch (Exception e) {
            log.error("Failed to handle wallet limit DLQ final DLT event: {}", event.getEventId(), e);
        }
    }

    public void handleWalletLimitDlqFailure(GenericKafkaEvent event, String topic, int partition,
                                          long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("EMERGENCY: Circuit breaker activated for wallet limit DLQ processing - EventId: {}",
                event.getEventId(), e);

        try {
            WalletLimitData limitData = extractLimitData(event.getPayload());

            // Emergency protection
            walletLimitService.emergencyWalletProtection(limitData.getWalletId());

            // Emergency alert
            notificationService.sendEmergencyAlert(
                    "Wallet Limit DLQ Circuit Breaker Open",
                    "Wallet limit DLQ processing is failing - transaction limits severely compromised"
            );

        } catch (Exception ex) {
            log.error("Failed to handle wallet limit DLQ circuit breaker fallback", ex);
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
    public static class WalletLimitData {
        private String eventId;
        private String walletId;
        private String userId;
        private String accountId;
        private String transactionId;
        private String limitType;
        private BigDecimal transactionAmount;
        private String currency;
        private BigDecimal currentLimit;
        private BigDecimal remainingLimit;
        private BigDecimal exceedAmount;
        private String limitPeriod;
        private Map<String, Object> originalEvent;
        private String failureReason;
        private Integer retryCount;
        private Instant dlqTimestamp;
        private Instant originalTimestamp;
        private String transactionType;
        private Map<String, Object> merchantInfo;
        private Map<String, Object> deviceInfo;
        private Map<String, Object> locationData;
        private List<String> riskIndicators;
        private List<String> complianceFlags;
        private String customerTier;
        private String walletType;
        private String businessImpact;
        private List<String> regulatoryRequirements;
    }

    @lombok.Data
    @lombok.Builder
    public static class WalletLimitDlqAssessment {
        private String riskLevel;
        private String complianceAction;
        private boolean requiresComplianceReview;
        private List<String> protectionMeasures;
        private String businessImpact;
        private boolean executiveEscalation;
        private boolean customerNotificationRequired;
        private boolean regulatoryReporting;
        private boolean fraudAnalysisRequired;
        private boolean limitAdjustmentNeeded;
    }
}