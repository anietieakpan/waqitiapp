package com.waqiti.payment.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.payment.model.*;
import com.waqiti.payment.repository.FundHoldRepository;
import com.waqiti.payment.service.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade Kafka consumer for merchant fund hold events
 * Manages risk-based fund holds, rolling reserves, and dispute reserves
 * 
 * Critical for: Risk management, dispute protection, platform solvency
 * SLA: Must apply holds within 30 seconds of trigger event
 */
@Component
@Slf4j
@RequiredArgsConstructor  
public class MerchantFundHoldConsumer {

    private final FundHoldRepository fundHoldRepository;
    private final MerchantService merchantService;
    private final AccountService accountService;
    private final RiskService riskService;
    private final DisputeService disputeService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    private static final BigDecimal DEFAULT_HOLD_PERCENTAGE = new BigDecimal("0.10"); // 10%
    private static final BigDecimal HIGH_RISK_HOLD_PERCENTAGE = new BigDecimal("0.30"); // 30%
    private static final int DEFAULT_HOLD_DAYS = 7;
    private static final int HIGH_RISK_HOLD_DAYS = 30;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    
    @KafkaListener(
        topics = "merchant-fund-holds",
        groupId = "merchant-fund-hold-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "merchant-fund-hold-processor", fallbackMethod = "handleFundHoldFailure")
    @Retry(name = "merchant-fund-hold-processor")
    public void processMerchantFundHoldEvent(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        log.info("Processing merchant fund hold event: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> payload = event.getPayload();
            FundHoldRequest holdRequest = extractFundHoldRequest(payload);
            
            // Validate hold request
            validateFundHoldRequest(holdRequest);
            
            // Check for duplicate hold
            if (isDuplicateHold(holdRequest)) {
                log.warn("Duplicate fund hold detected for: {}, skipping", holdRequest.getHoldId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Assess merchant risk
            MerchantRiskProfile riskProfile = assessMerchantRisk(holdRequest.getMerchantId());
            
            // Calculate hold parameters
            HoldParameters holdParams = calculateHoldParameters(holdRequest, riskProfile);
            
            // Apply the hold
            HoldResult holdResult = applyFundHold(holdRequest, holdParams);
            
            // Update merchant status if needed
            updateMerchantStatus(holdRequest.getMerchantId(), holdResult);
            
            // Schedule hold release
            if (holdParams.isAutoRelease()) {
                scheduleHoldRelease(holdResult);
            }
            
            // Send notifications
            sendHoldNotifications(holdRequest, holdResult);
            
            // Audit the hold
            auditFundHold(holdRequest, holdResult, event);
            
            // Record metrics
            recordMetrics(holdRequest, holdResult, startTime);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed fund hold for merchant: {} amount: {} in {}ms", 
                    holdRequest.getMerchantId(), holdResult.getHeldAmount(),
                    System.currentTimeMillis() - startTime);
            
        } catch (ValidationException e) {
            log.error("Validation failed for fund hold event: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (InsufficientFundsException e) {
            log.error("Insufficient funds for hold: {}", eventId, e);
            handleInsufficientFundsError(event, e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process fund hold event: {}", eventId, e);
            handleProcessingError(event, e, acknowledgment);
        }
    }

    private FundHoldRequest extractFundHoldRequest(Map<String, Object> payload) {
        return FundHoldRequest.builder()
            .holdId(extractString(payload, "holdId", UUID.randomUUID().toString()))
            .merchantId(extractString(payload, "merchantId", null))
            .transactionId(extractString(payload, "transactionId", null))
            .holdType(HoldType.fromString(extractString(payload, "holdType", "RISK_BASED")))
            .triggerEvent(extractString(payload, "triggerEvent", null))
            .amount(extractBigDecimal(payload, "amount"))
            .currency(extractString(payload, "currency", "USD"))
            .percentage(extractBigDecimal(payload, "percentage"))
            .reason(extractString(payload, "reason", null))
            .disputeId(extractString(payload, "disputeId", null))
            .expiryDate(extractLocalDate(payload, "expiryDate"))
            .autoRelease(extractBoolean(payload, "autoRelease", true))
            .priority(extractString(payload, "priority", "NORMAL"))
            .metadata(extractMap(payload, "metadata"))
            .requestedBy(extractString(payload, "requestedBy", "SYSTEM"))
            .createdAt(Instant.now())
            .build();
    }

    private void validateFundHoldRequest(FundHoldRequest request) {
        if (request.getMerchantId() == null || request.getMerchantId().isEmpty()) {
            throw new ValidationException("Merchant ID is required for fund hold");
        }
        
        if (!merchantService.exists(request.getMerchantId())) {
            throw new ValidationException("Merchant does not exist: " + request.getMerchantId());
        }
        
        // Validate hold type specific requirements
        switch (request.getHoldType()) {
            case DISPUTE_RESERVE:
                if (request.getDisputeId() == null) {
                    throw new ValidationException("Dispute ID required for dispute reserve hold");
                }
                if (!disputeService.exists(request.getDisputeId())) {
                    throw new ValidationException("Dispute does not exist: " + request.getDisputeId());
                }
                break;
                
            case TRANSACTION_HOLD:
                if (request.getTransactionId() == null) {
                    throw new ValidationException("Transaction ID required for transaction hold");
                }
                break;
                
            case RISK_BASED:
            case ROLLING_RESERVE:
                // No additional validation needed
                break;
                
            default:
                throw new ValidationException("Invalid hold type: " + request.getHoldType());
        }
        
        // Validate amount or percentage
        if (request.getAmount() == null && request.getPercentage() == null) {
            throw new ValidationException("Either amount or percentage must be specified");
        }
        
        if (request.getAmount() != null && request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Hold amount must be positive");
        }
        
        if (request.getPercentage() != null && 
            (request.getPercentage().compareTo(BigDecimal.ZERO) <= 0 || 
             request.getPercentage().compareTo(BigDecimal.ONE) > 0)) {
            throw new ValidationException("Hold percentage must be between 0 and 1");
        }
    }

    private boolean isDuplicateHold(FundHoldRequest request) {
        // Check for existing active hold
        if (request.getTransactionId() != null) {
            return fundHoldRepository.existsActiveHoldForTransaction(
                request.getMerchantId(),
                request.getTransactionId()
            );
        }
        
        if (request.getDisputeId() != null) {
            return fundHoldRepository.existsActiveHoldForDispute(
                request.getMerchantId(),
                request.getDisputeId()
            );
        }
        
        // Check for recent similar hold
        return fundHoldRepository.existsSimilarRecentHold(
            request.getMerchantId(),
            request.getHoldType(),
            request.getAmount(),
            Instant.now().minus(5, ChronoUnit.MINUTES)
        );
    }

    private MerchantRiskProfile assessMerchantRisk(String merchantId) {
        MerchantRiskProfile profile = new MerchantRiskProfile();
        
        // Get merchant details
        Merchant merchant = merchantService.getMerchant(merchantId);
        profile.setMerchantId(merchantId);
        profile.setAccountAge(merchant.getAccountAge());
        profile.setBusinessType(merchant.getBusinessType());
        
        // Calculate risk score
        RiskScore riskScore = riskService.calculateMerchantRisk(merchantId);
        profile.setRiskScore(riskScore.getScore());
        profile.setRiskLevel(riskScore.getLevel());
        
        // Check dispute history
        DisputeHistory disputeHistory = disputeService.getMerchantDisputeHistory(merchantId);
        profile.setTotalDisputes(disputeHistory.getTotalCount());
        profile.setDisputeRate(disputeHistory.getDisputeRate());
        profile.setRecentDisputes(disputeHistory.getRecentCount());
        
        // Check transaction patterns
        TransactionPattern pattern = merchantService.getTransactionPattern(merchantId);
        profile.setAverageTransactionAmount(pattern.getAverageAmount());
        profile.setMonthlyVolume(pattern.getMonthlyVolume());
        profile.setHighRiskTransactions(pattern.getHighRiskCount());
        
        // Check compliance status
        ComplianceStatus compliance = merchantService.getComplianceStatus(merchantId);
        profile.setKycVerified(compliance.isKycVerified());
        profile.setAmlClear(compliance.isAmlClear());
        profile.setHasViolations(compliance.hasViolations());
        
        // Determine overall risk
        profile.setOverallRisk(calculateOverallRisk(profile));
        
        return profile;
    }

    private String calculateOverallRisk(MerchantRiskProfile profile) {
        int riskPoints = 0;
        
        // Risk score contribution
        if (profile.getRiskScore() > 70) {
            riskPoints += 3;
        } else if (profile.getRiskScore() > 50) {
            riskPoints += 2;
        } else if (profile.getRiskScore() > 30) {
            riskPoints += 1;
        }
        
        // Dispute rate contribution
        if (profile.getDisputeRate() > 0.02) { // > 2%
            riskPoints += 3;
        } else if (profile.getDisputeRate() > 0.01) { // > 1%
            riskPoints += 2;
        } else if (profile.getDisputeRate() > 0.005) { // > 0.5%
            riskPoints += 1;
        }
        
        // Recent disputes
        if (profile.getRecentDisputes() > 5) {
            riskPoints += 2;
        } else if (profile.getRecentDisputes() > 2) {
            riskPoints += 1;
        }
        
        // Compliance issues
        if (profile.hasViolations()) {
            riskPoints += 2;
        }
        if (!profile.isKycVerified()) {
            riskPoints += 1;
        }
        
        // Account age
        if (profile.getAccountAge() < 30) {
            riskPoints += 2;
        } else if (profile.getAccountAge() < 90) {
            riskPoints += 1;
        }
        
        // Determine risk level
        if (riskPoints >= 8) {
            return "CRITICAL";
        } else if (riskPoints >= 6) {
            return "HIGH";
        } else if (riskPoints >= 3) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    private HoldParameters calculateHoldParameters(FundHoldRequest request, MerchantRiskProfile riskProfile) {
        HoldParameters params = new HoldParameters();
        
        // Determine hold amount
        if (request.getAmount() != null) {
            params.setHoldAmount(request.getAmount());
        } else if (request.getPercentage() != null) {
            BigDecimal baseAmount = getBaseAmount(request);
            params.setHoldAmount(baseAmount.multiply(request.getPercentage()));
        } else {
            // Calculate based on risk profile
            BigDecimal percentage = getHoldPercentageByRisk(riskProfile.getOverallRisk());
            BigDecimal baseAmount = getBaseAmount(request);
            params.setHoldAmount(baseAmount.multiply(percentage));
        }
        
        // Determine hold duration
        if (request.getExpiryDate() != null) {
            params.setReleaseDate(request.getExpiryDate());
        } else {
            int holdDays = getHoldDaysByRisk(riskProfile.getOverallRisk(), request.getHoldType());
            params.setReleaseDate(LocalDate.now().plusDays(holdDays));
        }
        
        // Set auto-release
        params.setAutoRelease(request.isAutoRelease() && !riskProfile.getOverallRisk().equals("CRITICAL"));
        
        // Set review requirements
        if (riskProfile.getOverallRisk().equals("HIGH") || riskProfile.getOverallRisk().equals("CRITICAL")) {
            params.setRequiresManualReview(true);
            params.setReviewDeadline(Instant.now().plus(24, ChronoUnit.HOURS));
        }
        
        // Calculate maximum hold amount (cap at available balance)
        BigDecimal availableBalance = accountService.getAvailableBalance(
            merchantService.getMerchantAccountId(request.getMerchantId())
        );
        
        if (params.getHoldAmount().compareTo(availableBalance) > 0) {
            params.setHoldAmount(availableBalance);
            params.setPartialHold(true);
        }
        
        return params;
    }

    private BigDecimal getBaseAmount(FundHoldRequest request) {
        if (request.getTransactionId() != null) {
            return merchantService.getTransactionAmount(request.getTransactionId());
        } else if (request.getDisputeId() != null) {
            return disputeService.getDisputeAmount(request.getDisputeId());
        } else {
            // Use merchant's average daily volume
            return merchantService.getAverageDailyVolume(request.getMerchantId());
        }
    }

    private BigDecimal getHoldPercentageByRisk(String riskLevel) {
        switch (riskLevel) {
            case "CRITICAL":
                return new BigDecimal("0.50"); // 50%
            case "HIGH":
                return HIGH_RISK_HOLD_PERCENTAGE;
            case "MEDIUM":
                return new BigDecimal("0.15"); // 15%
            case "LOW":
            default:
                return DEFAULT_HOLD_PERCENTAGE;
        }
    }

    private int getHoldDaysByRisk(String riskLevel, HoldType holdType) {
        if (holdType == HoldType.DISPUTE_RESERVE) {
            return 180; // 6 months for dispute reserves
        }
        
        switch (riskLevel) {
            case "CRITICAL":
                return 90;
            case "HIGH":
                return HIGH_RISK_HOLD_DAYS;
            case "MEDIUM":
                return 14;
            case "LOW":
            default:
                return DEFAULT_HOLD_DAYS;
        }
    }

    private HoldResult applyFundHold(FundHoldRequest request, HoldParameters params) {
        HoldResult result = new HoldResult();
        result.setHoldId(request.getHoldId());
        result.setMerchantId(request.getMerchantId());
        
        try {
            String merchantAccountId = merchantService.getMerchantAccountId(request.getMerchantId());
            
            // Create hold on account
            String holdReference = accountService.createHold(
                merchantAccountId,
                params.getHoldAmount(),
                request.getHoldType().toString(),
                request.getReason()
            );
            
            result.setHoldReference(holdReference);
            result.setHeldAmount(params.getHoldAmount());
            result.setStatus(HoldStatus.ACTIVE);
            
            // Create fund hold record
            FundHold fundHold = FundHold.builder()
                .holdId(request.getHoldId())
                .merchantId(request.getMerchantId())
                .accountId(merchantAccountId)
                .holdType(request.getHoldType())
                .amount(params.getHoldAmount())
                .currency(request.getCurrency())
                .reason(request.getReason())
                .holdReference(holdReference)
                .transactionId(request.getTransactionId())
                .disputeId(request.getDisputeId())
                .status(HoldStatus.ACTIVE)
                .createdAt(Instant.now())
                .expiryDate(params.getReleaseDate())
                .autoRelease(params.isAutoRelease())
                .requiresManualReview(params.isRequiresManualReview())
                .build();
            
            fundHoldRepository.save(fundHold);
            
            // Update merchant metrics
            merchantService.updateHoldMetrics(
                request.getMerchantId(),
                params.getHoldAmount(),
                request.getHoldType()
            );
            
            result.setSuccess(true);
            
        } catch (Exception e) {
            log.error("Failed to apply fund hold", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setStatus(HoldStatus.FAILED);
            throw new HoldApplicationException("Failed to apply hold", e);
        }
        
        return result;
    }

    private void updateMerchantStatus(String merchantId, HoldResult holdResult) {
        if (!holdResult.isSuccess()) {
            return;
        }
        
        // Check total holds
        BigDecimal totalHolds = fundHoldRepository.getTotalActiveHolds(merchantId);
        BigDecimal merchantBalance = merchantService.getAvailableBalance(merchantId);
        
        // Calculate hold percentage
        BigDecimal holdPercentage = merchantBalance.compareTo(BigDecimal.ZERO) > 0 ?
            totalHolds.divide(merchantBalance.add(totalHolds), 2, RoundingMode.HALF_UP) :
            BigDecimal.ONE;
        
        // Update merchant status based on hold levels
        if (holdPercentage.compareTo(new BigDecimal("0.75")) > 0) {
            merchantService.updateStatus(merchantId, MerchantStatus.HIGH_RISK_HOLD);
            
            // Trigger additional risk assessment
            triggerRiskReassessment(merchantId);
            
        } else if (holdPercentage.compareTo(new BigDecimal("0.50")) > 0) {
            merchantService.updateStatus(merchantId, MerchantStatus.ELEVATED_RISK);
            
        } else if (holdPercentage.compareTo(new BigDecimal("0.25")) > 0) {
            merchantService.updateStatus(merchantId, MerchantStatus.UNDER_REVIEW);
        }
        
        // Check if merchant should be suspended
        if (shouldSuspendMerchant(merchantId, totalHolds)) {
            suspendMerchant(merchantId);
        }
    }

    private boolean shouldSuspendMerchant(String merchantId, BigDecimal totalHolds) {
        // Check multiple criteria
        DisputeHistory disputes = disputeService.getMerchantDisputeHistory(merchantId);
        
        return disputes.getDisputeRate() > 0.04 || // > 4% dispute rate
               disputes.getRecentCount() > 10 || // More than 10 recent disputes
               totalHolds.compareTo(new BigDecimal("100000")) > 0; // Holds exceed $100k
    }

    private void suspendMerchant(String merchantId) {
        merchantService.suspendMerchant(
            merchantId,
            "AUTO_SUSPENSION_HIGH_RISK",
            "Merchant suspended due to excessive holds and risk indicators"
        );
        
        // Notify compliance team
        notificationService.notifyComplianceTeam(
            "Merchant Auto-Suspended",
            Map.of(
                "merchantId", merchantId,
                "reason", "High risk holds"
            )
        );
    }

    private void triggerRiskReassessment(String merchantId) {
        CompletableFuture.runAsync(() -> {
            riskService.performDetailedAssessment(merchantId);
        });
    }

    private void scheduleHoldRelease(HoldResult holdResult) {
        if (!holdResult.isSuccess() || !holdResult.isAutoRelease()) {
            return;
        }
        
        LocalDate releaseDate = holdResult.getReleaseDate();
        long delayDays = ChronoUnit.DAYS.between(LocalDate.now(), releaseDate);
        
        if (delayDays > 0) {
            scheduledExecutor.schedule(() -> {
                releaseHold(holdResult.getHoldId());
            }, delayDays, TimeUnit.DAYS);
        }
    }

    private void releaseHold(String holdId) {
        try {
            FundHold hold = fundHoldRepository.findById(holdId)
                .orElseThrow(() -> new IllegalArgumentException("Hold not found"));
            
            if (hold.getStatus() != HoldStatus.ACTIVE) {
                log.warn("Hold {} is not active, skipping release", holdId);
                return;
            }
            
            // Release the hold
            accountService.releaseHold(hold.getHoldReference());
            
            // Update hold status
            hold.setStatus(HoldStatus.RELEASED);
            hold.setReleasedAt(Instant.now());
            fundHoldRepository.save(hold);
            
            // Update merchant metrics
            merchantService.updateHoldReleaseMetrics(
                hold.getMerchantId(),
                hold.getAmount()
            );
            
            // Notify merchant
            notificationService.notifyMerchant(
                hold.getMerchantId(),
                "FUND_HOLD_RELEASED",
                Map.of(
                    "holdId", holdId,
                    "amount", hold.getAmount(),
                    "releasedAt", Instant.now()
                )
            );
            
        } catch (Exception e) {
            log.error("Failed to release hold: {}", holdId, e);
            alertingService.createAlert(
                "HOLD_RELEASE_FAILED",
                "Failed to release hold: " + holdId,
                "HIGH"
            );
        }
    }

    private void sendHoldNotifications(FundHoldRequest request, HoldResult result) {
        Map<String, Object> notificationData = Map.of(
            "holdId", request.getHoldId(),
            "merchantId", request.getMerchantId(),
            "amount", result.getHeldAmount(),
            "holdType", request.getHoldType().toString(),
            "reason", request.getReason() != null ? request.getReason() : "",
            "status", result.getStatus().toString()
        );
        
        // Notify merchant
        notificationService.notifyMerchant(
            request.getMerchantId(),
            "FUND_HOLD_APPLIED",
            notificationData
        );
        
        // Notify risk team for high-value holds
        if (result.getHeldAmount().compareTo(new BigDecimal("10000")) > 0) {
            notificationService.notifyRiskTeam(
                "HIGH_VALUE_HOLD",
                notificationData
            );
        }
        
        // Create webhook if configured
        if (merchantService.hasWebhookEnabled(request.getMerchantId())) {
            merchantService.sendWebhook(
                request.getMerchantId(),
                "fund.hold.created",
                notificationData
            );
        }
    }

    private void auditFundHold(FundHoldRequest request, HoldResult result, GenericKafkaEvent event) {
        auditService.auditFundHold(
            request.getHoldId(),
            request.getMerchantId(),
            result.getHeldAmount(),
            request.getHoldType().toString(),
            result.getStatus().toString(),
            event.getEventId()
        );
    }

    private void recordMetrics(FundHoldRequest request, HoldResult result, long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        metricsService.recordFundHoldMetrics(
            request.getHoldType().toString(),
            result.getHeldAmount(),
            processingTime,
            result.isSuccess()
        );
    }

    // Error handling methods
    private void handleValidationError(GenericKafkaEvent event, ValidationException e) {
        auditService.logValidationError(event.getEventId(), e.getMessage());
        kafkaTemplate.send("merchant-fund-hold-validation-errors", event);
    }

    private void handleInsufficientFundsError(GenericKafkaEvent event, InsufficientFundsException e) {
        String merchantId = event.getPayloadValue("merchantId", String.class);
        
        // Log the issue
        auditService.logInsufficientFundsForHold(merchantId, e.getMessage());
        
        // Create partial hold if possible
        createPartialHold(event);
        
        // Alert risk team
        alertingService.createAlert(
            "INSUFFICIENT_FUNDS_FOR_HOLD",
            "Merchant " + merchantId + " has insufficient funds for hold",
            "MEDIUM"
        );
    }

    private void createPartialHold(GenericKafkaEvent event) {
        // Re-process with available balance as hold amount
        event.setMetadataValue("partialHold", true);
        kafkaTemplate.send("merchant-fund-holds-partial", event);
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying fund hold event {} after {}ms (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            scheduledExecutor.schedule(() -> {
                kafkaTemplate.send("merchant-fund-holds-retry", event);
            }, retryDelay, TimeUnit.MILLISECONDS);
            
            acknowledgment.acknowledge();
        } else {
            log.error("Max retries exceeded for fund hold event {}, sending to DLQ", eventId);
            sendToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "merchant-fund-holds");
        
        kafkaTemplate.send("merchant-fund-holds.DLQ", event);
        
        alertingService.createDLQAlert(
            "merchant-fund-holds",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handleFundHoldFailure(GenericKafkaEvent event, String topic, int partition,
                                      long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for fund hold processing: {}", e.getMessage());
        
        failedEventRepository.save(
            FailedEvent.builder()
                .eventId(event.getEventId())
                .topic(topic)
                .payload(event)
                .errorMessage(e.getMessage())
                .createdAt(Instant.now())
                .build()
        );
        
        alertingService.sendCriticalAlert(
            "Fund Hold Circuit Breaker Open",
            "Fund hold processing is failing. Manual intervention required."
        );
        
        acknowledgment.acknowledge();
    }

    // Helper methods (simplified)
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(value.toString());
    }

    private LocalDate extractLocalDate(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof LocalDate) return (LocalDate) value;
        return LocalDate.parse(value.toString());
    }

    private boolean extractBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    // Custom exceptions
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class InsufficientFundsException extends RuntimeException {
        public InsufficientFundsException(String message) {
            super(message);
        }
    }

    public static class HoldApplicationException extends RuntimeException {
        public HoldApplicationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}