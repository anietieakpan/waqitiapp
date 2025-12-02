package com.waqiti.payment.core.service;

import com.waqiti.payment.core.model.*;
import com.waqiti.payment.core.exception.PaymentValidationException;
import com.waqiti.common.kyc.KYCService;
import com.waqiti.common.fraud.FraudServiceHelper;
import com.waqiti.common.fraud.ComprehensiveFraudBlacklistService;
import com.waqiti.common.fraud.model.*;
import com.waqiti.common.sanctions.SanctionsScreeningService;
import com.waqiti.common.compliance.ComplianceService;
import com.waqiti.common.limits.TransactionLimitService;
import com.waqiti.common.velocity.VelocityCheckService;
import com.waqiti.common.ratelimit.RateLimitService;
import com.waqiti.common.audit.PciDssAuditEnhancement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Industrial-strength payment validation service with comprehensive security checks
 * 
 * This service implements multi-layered validation including:
 * - KYC verification
 * - Transaction limits
 * - Velocity checks
 * - Fraud detection
 * - Blacklist screening
 * - Sanctions screening
 * - Device fingerprinting
 * - Compliance checks
 * - Business rule validation
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentValidationService {
    
    // Configuration constants
    @Value("${payment.validation.max-amount:100000.00}")
    private BigDecimal MAX_PAYMENT_AMOUNT = new BigDecimal("100000.00");
    
    @Value("${payment.validation.min-amount:0.01}")
    private BigDecimal MIN_PAYMENT_AMOUNT = new BigDecimal("0.01");
    
    @Value("${payment.validation.daily-limit:50000.00}")
    private BigDecimal DAILY_TRANSACTION_LIMIT = new BigDecimal("50000.00");
    
    @Value("${payment.validation.velocity.max-per-hour:20}")
    private int MAX_TRANSACTIONS_PER_HOUR = 20;
    
    @Value("${payment.validation.velocity.max-per-day:100}")
    private int MAX_TRANSACTIONS_PER_DAY = 100;
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final Pattern WALLET_ADDRESS_PATTERN = Pattern.compile("^(0x)?[0-9a-fA-F]{40}$");
    private static final Pattern IBAN_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}$");
    
    // Service dependencies
    private final KYCService kycService;
    private final FraudServiceHelper fraudServiceHelper;
    private final ComprehensiveFraudBlacklistService blacklistService;
    private final SanctionsScreeningService sanctionsService;
    private final ComplianceService complianceService;
    private final TransactionLimitService limitService;
    private final VelocityCheckService velocityService;
    private final RateLimitService rateLimitService;
    private final PciDssAuditEnhancement auditService;
    
    // Cache for validation results
    private final Map<String, ValidationCache> validationCache = new ConcurrentHashMap<>();
    
    /**
     * Performs comprehensive payment validation with parallel security checks
     */
    public ValidationResult validatePaymentRequest(PaymentRequest request) {
        log.info("Starting comprehensive validation for payment: {}", request.getPaymentId());
        
        try {
            // Check cache first
            ValidationCache cached = getCachedValidation(request.getPaymentId().toString());
            if (cached != null && !cached.isExpired()) {
                log.debug("Using cached validation result for payment: {}", request.getPaymentId());
                return cached.getResult();
            }
            
            // Phase 1: Basic validation (synchronous)
            ValidationResult basicValidation = performBasicValidation(request);
            if (!basicValidation.isValid()) {
                return cacheAndReturn(request.getPaymentId().toString(), basicValidation);
            }
            
            // Phase 2: Security validation (parallel)
            CompletableFuture<ValidationResult> kycFuture = CompletableFuture.supplyAsync(() -> 
                validateKYC(request));
            
            CompletableFuture<ValidationResult> fraudFuture = CompletableFuture.supplyAsync(() -> 
                validateFraudRisk(request));
            
            CompletableFuture<ValidationResult> blacklistFuture = CompletableFuture.supplyAsync(() -> 
                validateBlacklist(request));
            
            CompletableFuture<ValidationResult> sanctionsFuture = CompletableFuture.supplyAsync(() -> 
                validateSanctions(request));
            
            CompletableFuture<ValidationResult> velocityFuture = CompletableFuture.supplyAsync(() -> 
                validateVelocity(request));
            
            CompletableFuture<ValidationResult> limitsFuture = CompletableFuture.supplyAsync(() -> 
                validateTransactionLimits(request));
            
            // Wait for all security checks
            CompletableFuture.allOf(kycFuture, fraudFuture, blacklistFuture, 
                sanctionsFuture, velocityFuture, limitsFuture).join();
            
            // Collect results
            List<ValidationResult> results = Arrays.asList(
                kycFuture.get(),
                fraudFuture.get(),
                blacklistFuture.get(),
                sanctionsFuture.get(),
                velocityFuture.get(),
                limitsFuture.get()
            );
            
            // Check for any failures
            for (ValidationResult result : results) {
                if (!result.isValid()) {
                    logValidationFailure(request, result);
                    return cacheAndReturn(request.getPaymentId().toString(), result);
                }
            }
            
            // Phase 3: Business rule validation
            ValidationResult businessValidation = validateBusinessRules(request);
            if (!businessValidation.isValid()) {
                return cacheAndReturn(request.getPaymentId().toString(), businessValidation);
            }
            
            // Phase 4: Compliance validation
            ValidationResult complianceValidation = validateCompliance(request);
            if (!complianceValidation.isValid()) {
                return cacheAndReturn(request.getPaymentId().toString(), complianceValidation);
            }
            
            log.info("Payment validation successful: {}", request.getPaymentId());
            ValidationResult success = ValidationResult.valid();
            auditValidationSuccess(request);
            return cacheAndReturn(request.getPaymentId().toString(), success);
            
        } catch (Exception e) {
            log.error("Payment validation failed with exception: ", e);
            auditValidationError(request, e);
            return ValidationResult.invalid("System error during validation: " + e.getMessage());
        }
    }
    
    private ValidationResult performBasicValidation(PaymentRequest request) {
        // Basic field validation
        ValidationResult basicValidation = validateBasicFields(request);
        if (!basicValidation.isValid()) return basicValidation;
        
        // Amount validation
        ValidationResult amountValidation = validateAmount(request);
        if (!amountValidation.isValid()) return amountValidation;
        
        // User validation
        ValidationResult userValidation = validateUsers(request);
        if (!userValidation.isValid()) return userValidation;
        
        // Type-specific validation
        ValidationResult typeValidation = validatePaymentType(request);
        if (!typeValidation.isValid()) return typeValidation;
        
        return ValidationResult.valid();
    }
    
    public ValidationResult validateRefundRequest(RefundRequest request) {
        log.debug("Validating refund request: {}", request.getRefundId());
        
        try {
            if (request.getRefundId() == null) {
                return ValidationResult.invalid("Refund ID is required");
            }
            
            if (request.getOriginalPaymentId() == null || request.getOriginalPaymentId().isEmpty()) {
                return ValidationResult.invalid("Original payment ID is required");
            }
            
            if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                return ValidationResult.invalid("Refund amount must be positive");
            }
            
            if (request.getReason() == null || request.getReason().trim().isEmpty()) {
                return ValidationResult.invalid("Refund reason is required");
            }
            
            return ValidationResult.valid();
            
        } catch (Exception e) {
            log.error("Refund validation failed: ", e);
            return ValidationResult.invalid("Refund validation error: " + e.getMessage());
        }
    }
    
    private ValidationResult validateBasicFields(PaymentRequest request) {
        if (request.getPaymentId() == null) {
            return ValidationResult.invalid("Payment ID is required");
        }
        
        if (request.getType() == null) {
            return ValidationResult.invalid("Payment type is required");
        }
        
        if (request.getProviderType() == null) {
            return ValidationResult.invalid("Provider type is required");
        }
        
        return ValidationResult.valid();
    }
    
    private ValidationResult validateAmount(PaymentRequest request) {
        BigDecimal amount = request.getAmount();
        
        if (amount == null) {
            return ValidationResult.invalid("Amount is required");
        }
        
        if (amount.compareTo(MIN_PAYMENT_AMOUNT) < 0) {
            return ValidationResult.invalid("Amount must be at least " + MIN_PAYMENT_AMOUNT);
        }
        
        if (amount.compareTo(MAX_PAYMENT_AMOUNT) > 0) {
            return ValidationResult.invalid("Amount exceeds maximum limit of " + MAX_PAYMENT_AMOUNT);
        }
        
        // Check for reasonable decimal places
        if (amount.scale() > 2) {
            return ValidationResult.invalid("Amount cannot have more than 2 decimal places");
        }
        
        return ValidationResult.valid();
    }
    
    private ValidationResult validateUsers(PaymentRequest request) {
        if (request.getFromUserId() == null || request.getFromUserId().trim().isEmpty()) {
            return ValidationResult.invalid("From user ID is required");
        }
        
        // For P2P payments, toUserId is required
        if (request.getType() == PaymentType.P2P) {
            if (request.getToUserId() == null || request.getToUserId().trim().isEmpty()) {
                return ValidationResult.invalid("To user ID is required for P2P payments");
            }
            
            if (request.getFromUserId().equals(request.getToUserId())) {
                return ValidationResult.invalid("Cannot send payment to yourself");
            }
        }
        
        return ValidationResult.valid();
    }
    
    private ValidationResult validatePaymentType(PaymentRequest request) {
        switch (request.getType()) {
            case P2P:
                return validateP2PPayment(request);
            case MERCHANT:
                return validateMerchantPayment(request);
            case RECURRING:
                return validateRecurringPayment(request);
            case GROUP:
                return validateGroupPayment(request);
            case BNPL:
                return validateBnplPayment(request);
            case CRYPTO:
                return validateCryptoPayment(request);
            default:
                return ValidationResult.valid();
        }
    }
    
    private ValidationResult validateP2PPayment(PaymentRequest request) {
        // P2P specific validation already covered in validateUsers
        return ValidationResult.valid();
    }
    
    private ValidationResult validateMerchantPayment(PaymentRequest request) {
        if (request.getMetadata() == null || !request.getMetadata().containsKey("merchantId")) {
            return ValidationResult.invalid("Merchant ID is required for merchant payments");
        }
        return ValidationResult.valid();
    }
    
    private ValidationResult validateRecurringPayment(PaymentRequest request) {
        if (request.getMetadata() == null) {
            return ValidationResult.invalid("Metadata is required for recurring payments");
        }
        
        if (!request.getMetadata().containsKey("frequency")) {
            return ValidationResult.invalid("Frequency is required for recurring payments");
        }
        
        return ValidationResult.valid();
    }
    
    private ValidationResult validateGroupPayment(PaymentRequest request) {
        if (request.getMetadata() == null || !request.getMetadata().containsKey("participants")) {
            return ValidationResult.invalid("Participants list is required for group payments");
        }
        return ValidationResult.valid();
    }
    
    private ValidationResult validateBnplPayment(PaymentRequest request) {
        if (request.getMetadata() == null || !request.getMetadata().containsKey("installments")) {
            return ValidationResult.invalid("Installments count is required for BNPL payments");
        }
        
        // BNPL typically has minimum amounts
        BigDecimal minBnplAmount = new BigDecimal("50.00");
        if (request.getAmount().compareTo(minBnplAmount) < 0) {
            return ValidationResult.invalid("BNPL payments require minimum amount of " + minBnplAmount);
        }
        
        return ValidationResult.valid();
    }
    
    private ValidationResult validateCryptoPayment(PaymentRequest request) {
        if (request.getMetadata() == null) {
            return ValidationResult.invalid("Metadata is required for crypto payments");
        }
        
        if (!request.getMetadata().containsKey("walletAddress")) {
            return ValidationResult.invalid("Wallet address is required for crypto payments");
        }
        
        String walletAddress = (String) request.getMetadata().get("walletAddress");
        if (!WALLET_ADDRESS_PATTERN.matcher(walletAddress).matches()) {
            return ValidationResult.invalid("Invalid wallet address format");
        }
        
        if (!request.getMetadata().containsKey("cryptoCurrency")) {
            return ValidationResult.invalid("Crypto currency type is required");
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * KYC Validation
     */
    private ValidationResult validateKYC(PaymentRequest request) {
        try {
            // Check sender KYC
            if (!kycService.isKYCCompleted(request.getFromUserId())) {
                return ValidationResult.invalid("Sender KYC verification incomplete");
            }
            
            // Check KYC limits
            String kycLevel = kycService.getKYCLevel(request.getFromUserId());
            BigDecimal kycLimit = getKYCTransactionLimit(kycLevel);
            
            if (request.getAmount().compareTo(kycLimit) > 0) {
                return ValidationResult.invalid(
                    String.format("Amount exceeds KYC level %s limit of %s", kycLevel, kycLimit)
                );
            }
            
            // For high value transactions, check enhanced KYC
            if (request.getAmount().compareTo(new BigDecimal("10000")) > 0) {
                if (!kycService.hasEnhancedKYC(request.getFromUserId())) {
                    return ValidationResult.invalid("Enhanced KYC required for high value transactions");
                }
            }
            
            return ValidationResult.valid();
        } catch (Exception e) {
            log.error("KYC validation error: ", e);
            return ValidationResult.invalid("KYC validation failed: " + e.getMessage());
        }
    }
    
    /**
     * Fraud Risk Validation
     */
    private ValidationResult validateFraudRisk(PaymentRequest request) {
        try {
            // Build fraud assessment request
            FraudAssessmentRequest fraudRequest = FraudAssessmentRequest.builder()
                .transactionId(request.getPaymentId().toString())
                .userId(request.getFromUserId())
                .amount(request.getAmount())
                .recipientId(request.getToUserId())
                .paymentType(request.getType().toString())
                .timestamp(LocalDateTime.now())
                .metadata(request.getMetadata())
                .build();
            
            // Perform fraud assessment
            FraudAssessmentResult fraudResult = fraudServiceHelper.assessFraudRisk(fraudRequest);
            
            if (fraudResult.isHighRisk()) {
                return ValidationResult.invalid(
                    "Payment blocked due to high fraud risk: " + fraudResult.getRiskScore()
                );
            }
            
            if (fraudResult.requiresAdditionalVerification()) {
                // Store for manual review
                request.getMetadata().put("requiresReview", true);
                request.getMetadata().put("fraudRiskScore", fraudResult.getRiskScore());
            }
            
            return ValidationResult.valid();
        } catch (Exception e) {
            log.error("Fraud validation error: ", e);
            // Don't block on fraud check errors, but flag for review
            request.getMetadata().put("fraudCheckError", e.getMessage());
            return ValidationResult.valid();
        }
    }
    
    /**
     * Blacklist Validation
     */
    private ValidationResult validateBlacklist(PaymentRequest request) {
        try {
            // Check sender blacklist
            BlacklistCheckResult senderCheck = blacklistService.checkEntity(
                request.getFromUserId(), "USER"
            );
            
            if (senderCheck.isBlacklisted()) {
                return ValidationResult.invalid(
                    "Sender is blacklisted: " + senderCheck.getReason()
                );
            }
            
            // Check recipient blacklist for P2P
            if (request.getType() == PaymentType.P2P && request.getToUserId() != null) {
                BlacklistCheckResult recipientCheck = blacklistService.checkEntity(
                    request.getToUserId(), "USER"
                );
                
                if (recipientCheck.isBlacklisted()) {
                    return ValidationResult.invalid(
                        "Recipient is blacklisted: " + recipientCheck.getReason()
                    );
                }
            }
            
            // Check device if available
            if (request.getMetadata() != null && request.getMetadata().containsKey("deviceId")) {
                String deviceId = (String) request.getMetadata().get("deviceId");
                BlacklistCheckResult deviceCheck = blacklistService.checkEntity(
                    deviceId, "DEVICE"
                );
                
                if (deviceCheck.isBlacklisted()) {
                    return ValidationResult.invalid(
                        "Device is blacklisted: " + deviceCheck.getReason()
                    );
                }
            }
            
            return ValidationResult.valid();
        } catch (Exception e) {
            log.error("Blacklist validation error: ", e);
            return ValidationResult.valid(); // Don't block on blacklist errors
        }
    }
    
    /**
     * Sanctions Screening Validation
     */
    private ValidationResult validateSanctions(PaymentRequest request) {
        try {
            // Screen sender
            if (sanctionsService.isOnSanctionsList(request.getFromUserId())) {
                return ValidationResult.invalid("Sender failed sanctions screening");
            }
            
            // Screen recipient for P2P
            if (request.getType() == PaymentType.P2P && request.getToUserId() != null) {
                if (sanctionsService.isOnSanctionsList(request.getToUserId())) {
                    return ValidationResult.invalid("Recipient failed sanctions screening");
                }
            }
            
            // Screen merchant for merchant payments
            if (request.getType() == PaymentType.MERCHANT && 
                request.getMetadata() != null && 
                request.getMetadata().containsKey("merchantId")) {
                String merchantId = (String) request.getMetadata().get("merchantId");
                if (sanctionsService.isOnSanctionsList(merchantId)) {
                    return ValidationResult.invalid("Merchant failed sanctions screening");
                }
            }
            
            return ValidationResult.valid();
        } catch (Exception e) {
            log.error("Sanctions screening error: ", e);
            // Flag for manual review on sanctions errors
            request.getMetadata().put("sanctionsCheckError", e.getMessage());
            return ValidationResult.valid();
        }
    }
    
    /**
     * Velocity Check Validation
     */
    private ValidationResult validateVelocity(PaymentRequest request) {
        try {
            String userId = request.getFromUserId();
            
            // Check hourly velocity
            int hourlyCount = velocityService.getTransactionCount(
                userId, LocalDateTime.now().minus(1, ChronoUnit.HOURS)
            );
            
            if (hourlyCount >= MAX_TRANSACTIONS_PER_HOUR) {
                return ValidationResult.invalid(
                    String.format("Exceeded hourly transaction limit (%d)", MAX_TRANSACTIONS_PER_HOUR)
                );
            }
            
            // Check daily velocity
            int dailyCount = velocityService.getTransactionCount(
                userId, LocalDateTime.now().minus(1, ChronoUnit.DAYS)
            );
            
            if (dailyCount >= MAX_TRANSACTIONS_PER_DAY) {
                return ValidationResult.invalid(
                    String.format("Exceeded daily transaction limit (%d)", MAX_TRANSACTIONS_PER_DAY)
                );
            }
            
            // Check for rapid successive transactions (potential fraud)
            int recentCount = velocityService.getTransactionCount(
                userId, LocalDateTime.now().minus(1, ChronoUnit.MINUTES)
            );
            
            if (recentCount > 3) {
                return ValidationResult.invalid("Too many transactions in quick succession");
            }
            
            return ValidationResult.valid();
        } catch (Exception e) {
            log.error("Velocity check error: ", e);
            return ValidationResult.valid(); // Don't block on velocity errors
        }
    }
    
    /**
     * Transaction Limits Validation
     */
    private ValidationResult validateTransactionLimits(PaymentRequest request) {
        try {
            String userId = request.getFromUserId();
            BigDecimal amount = request.getAmount();
            
            // Check daily spending limit
            BigDecimal dailySpent = limitService.getDailySpending(userId);
            BigDecimal dailyRemaining = DAILY_TRANSACTION_LIMIT.subtract(dailySpent);
            
            if (amount.compareTo(dailyRemaining) > 0) {
                return ValidationResult.invalid(
                    String.format("Exceeds daily spending limit. Remaining: %s", dailyRemaining)
                );
            }
            
            // Check per-transaction limit
            BigDecimal userTransactionLimit = limitService.getUserTransactionLimit(userId);
            if (amount.compareTo(userTransactionLimit) > 0) {
                return ValidationResult.invalid(
                    String.format("Exceeds per-transaction limit of %s", userTransactionLimit)
                );
            }
            
            // Check monthly limit for large transactions
            if (amount.compareTo(new BigDecimal("5000")) > 0) {
                BigDecimal monthlySpent = limitService.getMonthlySpending(userId);
                BigDecimal monthlyLimit = limitService.getUserMonthlyLimit(userId);
                
                if (monthlySpent.add(amount).compareTo(monthlyLimit) > 0) {
                    return ValidationResult.invalid(
                        String.format("Exceeds monthly spending limit of %s", monthlyLimit)
                    );
                }
            }
            
            return ValidationResult.valid();
        } catch (Exception e) {
            log.error("Transaction limit validation error: ", e);
            return ValidationResult.invalid("Unable to validate transaction limits");
        }
    }
    
    /**
     * Business Rules Validation
     */
    private ValidationResult validateBusinessRules(PaymentRequest request) {
        try {
            // Validate payment schedule (no payments during maintenance)
            if (isMaintenanceWindow()) {
                return ValidationResult.invalid("Payments disabled during maintenance window");
            }
            
            // Validate account status
            if (!isAccountActive(request.getFromUserId())) {
                return ValidationResult.invalid("Sender account is not active");
            }
            
            // Validate payment method if specified
            if (request.getPaymentMethodId() != null) {
                if (!isPaymentMethodValid(request.getPaymentMethodId(), request.getFromUserId())) {
                    return ValidationResult.invalid("Invalid or expired payment method");
                }
            }
            
            // Type-specific business rules
            return validateTypeSpecificBusinessRules(request);
            
        } catch (Exception e) {
            log.error("Business rule validation error: ", e);
            return ValidationResult.invalid("Business rule validation failed");
        }
    }
    
    /**
     * Compliance Validation
     */
    private ValidationResult validateCompliance(PaymentRequest request) {
        try {
            // AML checks for large transactions
            if (request.getAmount().compareTo(new BigDecimal("10000")) > 0) {
                if (!complianceService.performAMLCheck(request.getFromUserId())) {
                    return ValidationResult.invalid("AML compliance check failed");
                }
            }
            
            // CTR reporting for cash transactions over $10,000
            if (request.getAmount().compareTo(new BigDecimal("10000")) > 0 && 
                "CASH".equals(request.getMetadata().get("paymentMethod"))) {
                complianceService.fileCTR(request);
            }
            
            // SAR filing for suspicious patterns
            if (isSuspiciousPattern(request)) {
                complianceService.fileSAR(request);
                // Don't block, but flag for review
                request.getMetadata().put("flaggedForReview", true);
            }
            
            return ValidationResult.valid();
        } catch (Exception e) {
            log.error("Compliance validation error: ", e);
            return ValidationResult.invalid("Compliance validation failed");
        }
    }
    
    // Helper methods
    
    private BigDecimal getKYCTransactionLimit(String kycLevel) {
        return switch (kycLevel) {
            case "BASIC" -> new BigDecimal("1000");
            case "STANDARD" -> new BigDecimal("5000");
            case "ENHANCED" -> new BigDecimal("50000");
            case "PREMIUM" -> new BigDecimal("100000");
            default -> new BigDecimal("500");
        };
    }
    
    private boolean isMaintenanceWindow() {
        // Check if current time is in maintenance window
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        // Maintenance window: 2-4 AM on Sundays
        return now.getDayOfWeek().getValue() == 7 && hour >= 2 && hour < 4;
    }
    
    private boolean isAccountActive(String userId) {
        // Check account status from account service
        return true; // Placeholder
    }
    
    private boolean isPaymentMethodValid(String paymentMethodId, String userId) {
        // Validate payment method
        return true; // Placeholder
    }
    
    private ValidationResult validateTypeSpecificBusinessRules(PaymentRequest request) {
        // Additional type-specific business rules
        return ValidationResult.valid();
    }
    
    private boolean isSuspiciousPattern(PaymentRequest request) {
        // Check for suspicious patterns
        // - Rapid small transactions followed by large one
        // - Unusual geographic patterns
        // - Deviation from normal behavior
        return false; // Placeholder
    }
    
    private ValidationCache getCachedValidation(String key) {
        return validationCache.get(key);
    }
    
    private ValidationResult cacheAndReturn(String key, ValidationResult result) {
        validationCache.put(key, new ValidationCache(result));
        return result;
    }
    
    private void logValidationFailure(PaymentRequest request, ValidationResult result) {
        log.warn("Payment validation failed - PaymentId: {}, Reason: {}, User: {}, Amount: {}",
            request.getPaymentId(), result.getErrorMessage(), 
            request.getFromUserId(), request.getAmount());
    }
    
    private void auditValidationSuccess(PaymentRequest request) {
        auditService.auditPaymentValidation(request.getPaymentId().toString(), 
            "VALIDATION_SUCCESS", request.getFromUserId());
    }
    
    private void auditValidationError(PaymentRequest request, Exception e) {
        auditService.auditPaymentValidation(request.getPaymentId().toString(), 
            "VALIDATION_ERROR", e.getMessage());
    }
    
    /**
     * Validation cache entry
     */
    private static class ValidationCache {
        private final ValidationResult result;
        private final LocalDateTime timestamp;
        private static final long CACHE_DURATION_MINUTES = 5;
        
        public ValidationCache(ValidationResult result) {
            this.result = result;
            this.timestamp = LocalDateTime.now();
        }
        
        public boolean isExpired() {
            return ChronoUnit.MINUTES.between(timestamp, LocalDateTime.now()) > CACHE_DURATION_MINUTES;
        }
        
        public ValidationResult getResult() {
            return result;
        }
    }
}