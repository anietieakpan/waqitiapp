package com.waqiti.payment.validation;

import com.waqiti.payment.domain.*;
import com.waqiti.payment.dto.*;
import com.waqiti.payment.exception.*;
import com.waqiti.payment.repository.*;
import com.waqiti.payment.service.WalletService;
import com.waqiti.payment.service.AccountService;
import com.waqiti.payment.service.LimitService;
import com.waqiti.payment.service.FraudDetectionService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive payment validation service that ensures all financial operations
 * are properly validated before processing. Implements balance checks, limits,
 * fraud detection, and compliance validation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentValidationService {

    private final WalletService walletService;
    private final AccountService accountService;
    private final LimitService limitService;
    private final FraudDetectionService fraudDetectionService;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final ComplianceService complianceService;
    private final BlacklistService blacklistService;
    private final MeterRegistry meterRegistry;
    
    // Validation configuration
    private static final BigDecimal MIN_PAYMENT_AMOUNT = new BigDecimal("0.01");
    private static final BigDecimal MAX_SINGLE_PAYMENT = new BigDecimal("50000.00");
    private static final BigDecimal MAX_DAILY_LIMIT = new BigDecimal("100000.00");
    private static final int MAX_DAILY_TRANSACTIONS = 50;
    private static final int SUSPICIOUS_VELOCITY_THRESHOLD = 5; // transactions per hour

    /**
     * Comprehensive payment validation with all checks.
     * This is the main entry point for payment validation.
     */
    @Transactional(readOnly = true)
    public ValidationResult validatePayment(PaymentRequest request) {
        log.info("Starting comprehensive payment validation for user: {}, amount: {}", 
            request.getUserId(), request.getAmount());
        
        ValidationResult.Builder resultBuilder = ValidationResult.builder()
            .paymentId(request.getPaymentId())
            .timestamp(LocalDateTime.now());
        
        try {
            // Run all validations in parallel for performance
            CompletableFuture<ValidationCheck> amountValidation = 
                CompletableFuture.supplyAsync(() -> validateAmount(request));
            CompletableFuture<ValidationCheck> balanceValidation = 
                CompletableFuture.supplyAsync(() -> validateBalance(request));
            CompletableFuture<ValidationCheck> limitValidation = 
                CompletableFuture.supplyAsync(() -> validateLimits(request));
            CompletableFuture<ValidationCheck> accountValidation = 
                CompletableFuture.supplyAsync(() -> validateAccounts(request));
            CompletableFuture<ValidationCheck> fraudValidation = 
                CompletableFuture.supplyAsync(() -> validateFraud(request));
            CompletableFuture<ValidationCheck> complianceValidation = 
                CompletableFuture.supplyAsync(() -> validateCompliance(request));
            CompletableFuture<ValidationCheck> velocityValidation = 
                CompletableFuture.supplyAsync(() -> validateVelocity(request));
            CompletableFuture<ValidationCheck> blacklistValidation = 
                CompletableFuture.supplyAsync(() -> validateBlacklist(request));
            
            // Wait for all validations to complete
            CompletableFuture.allOf(
                amountValidation, balanceValidation, limitValidation,
                accountValidation, fraudValidation, complianceValidation,
                velocityValidation, blacklistValidation
            ).get(5, TimeUnit.SECONDS);
            
            // Collect results
            List<ValidationCheck> checks = Arrays.asList(
                amountValidation.get(),
                balanceValidation.get(),
                limitValidation.get(),
                accountValidation.get(),
                fraudValidation.get(),
                complianceValidation.get(),
                velocityValidation.get(),
                blacklistValidation.get()
            );
            
            // Determine overall result
            boolean allPassed = checks.stream().allMatch(ValidationCheck::isPassed);
            List<String> failureReasons = new ArrayList<>();
            ValidationSeverity highestSeverity = ValidationSeverity.INFO;
            
            for (ValidationCheck check : checks) {
                if (!check.isPassed()) {
                    failureReasons.add(check.getFailureReason());
                    if (check.getSeverity().ordinal() > highestSeverity.ordinal()) {
                        highestSeverity = check.getSeverity();
                    }
                }
            }
            
            resultBuilder
                .valid(allPassed)
                .checks(checks)
                .failureReasons(failureReasons)
                .severity(highestSeverity);
            
            // Additional validation for high-risk transactions
            if (isHighRiskTransaction(request)) {
                ValidationCheck enhancedCheck = performEnhancedValidation(request);
                resultBuilder.enhancedValidation(enhancedCheck);
                if (!enhancedCheck.isPassed()) {
                    resultBuilder.valid(false);
                    failureReasons.add(enhancedCheck.getFailureReason());
                }
            }
            
            ValidationResult result = resultBuilder.build();
            
            // Log validation result
            logValidationResult(result);
            
            // Update metrics
            updateValidationMetrics(result);
            
            return result;
            
        } catch (Exception e) {
            log.error("Payment validation failed with exception", e);
            return resultBuilder
                .valid(false)
                .failureReasons(List.of("Validation system error: " + e.getMessage()))
                .severity(ValidationSeverity.CRITICAL)
                .build();
        }
    }

    /**
     * Validate payment amount.
     */
    private ValidationCheck validateAmount(PaymentRequest request) {
        log.debug("Validating payment amount: {}", request.getAmount());
        
        BigDecimal amount = request.getAmount();
        
        // Check minimum amount
        if (amount.compareTo(MIN_PAYMENT_AMOUNT) < 0) {
            return ValidationCheck.failed(
                "AMOUNT_VALIDATION",
                "Amount below minimum: " + MIN_PAYMENT_AMOUNT,
                ValidationSeverity.ERROR
            );
        }
        
        // Check maximum amount
        if (amount.compareTo(MAX_SINGLE_PAYMENT) > 0) {
            return ValidationCheck.failed(
                "AMOUNT_VALIDATION",
                "Amount exceeds maximum single payment limit: " + MAX_SINGLE_PAYMENT,
                ValidationSeverity.ERROR
            );
        }
        
        // Check for suspicious amounts (e.g., round numbers that might indicate testing)
        if (isSuspiciousAmount(amount)) {
            return ValidationCheck.warning(
                "AMOUNT_VALIDATION",
                "Suspicious amount pattern detected",
                Map.of("amount", amount, "pattern", "round_number")
            );
        }
        
        return ValidationCheck.passed("AMOUNT_VALIDATION");
    }

    /**
     * Validate account balance.
     */
    @CircuitBreaker(name = "balanceValidation", fallbackMethod = "balanceValidationFallback")
    private ValidationCheck validateBalance(PaymentRequest request) {
        log.debug("Validating balance for user: {}", request.getUserId());
        
        try {
            // Get current balance
            BigDecimal balance = walletService.getAvailableBalance(request.getUserId());
            
            // Include fees in total amount
            BigDecimal totalAmount = calculateTotalAmount(request);
            
            // Check if sufficient balance
            if (balance.compareTo(totalAmount) < 0) {
                return ValidationCheck.failed(
                    "BALANCE_VALIDATION",
                    String.format("Insufficient balance. Required: %s, Available: %s", totalAmount, balance),
                    ValidationSeverity.ERROR
                );
            }
            
            // Check for minimum balance requirement
            BigDecimal minimumBalance = getMinimumBalanceRequirement(request.getUserId());
            BigDecimal remainingBalance = balance.subtract(totalAmount);
            
            if (remainingBalance.compareTo(minimumBalance) < 0) {
                return ValidationCheck.failed(
                    "BALANCE_VALIDATION",
                    "Transaction would breach minimum balance requirement",
                    ValidationSeverity.WARNING
                );
            }
            
            // Check for pending transactions that might affect balance
            BigDecimal pendingDebits = walletService.getPendingDebits(request.getUserId());
            BigDecimal effectiveBalance = balance.subtract(pendingDebits);
            
            if (effectiveBalance.compareTo(totalAmount) < 0) {
                return ValidationCheck.failed(
                    "BALANCE_VALIDATION",
                    "Insufficient balance considering pending transactions",
                    ValidationSeverity.ERROR
                );
            }
            
            return ValidationCheck.passed(
                "BALANCE_VALIDATION",
                Map.of(
                    "availableBalance", balance,
                    "requiredAmount", totalAmount,
                    "pendingDebits", pendingDebits,
                    "effectiveBalance", effectiveBalance
                )
            );
            
        } catch (Exception e) {
            log.error("Balance validation error", e);
            throw e; // Let circuit breaker handle it
        }
    }

    /**
     * Fallback method for balance validation when service is unavailable.
     */
    private ValidationCheck balanceValidationFallback(PaymentRequest request, Exception ex) {
        log.warn("Balance validation fallback activated for user: {}", request.getUserId());
        
        // Conservative approach - allow only small amounts when balance service is down
        if (request.getAmount().compareTo(new BigDecimal("100")) <= 0) {
            return ValidationCheck.warning(
                "BALANCE_VALIDATION",
                "Balance check bypassed for small amount due to service unavailability",
                Map.of("fallback", true, "amount", request.getAmount())
            );
        }
        
        return ValidationCheck.failed(
            "BALANCE_VALIDATION",
            "Balance validation service unavailable",
            ValidationSeverity.ERROR
        );
    }

    /**
     * Validate transaction limits.
     */
    private ValidationCheck validateLimits(PaymentRequest request) {
        log.debug("Validating transaction limits for user: {}", request.getUserId());
        
        try {
            // Get user's limit profile
            LimitProfile limitProfile = limitService.getUserLimitProfile(request.getUserId());
            
            // Check single transaction limit
            BigDecimal singleTransactionLimit = limitProfile.getSingleTransactionLimit();
            if (request.getAmount().compareTo(singleTransactionLimit) > 0) {
                return ValidationCheck.failed(
                    "LIMIT_VALIDATION",
                    "Exceeds single transaction limit: " + singleTransactionLimit,
                    ValidationSeverity.ERROR
                );
            }
            
            // Check daily limit
            DailyLimitStatus dailyStatus = limitService.getDailyLimitStatus(request.getUserId());
            BigDecimal remainingDailyLimit = dailyStatus.getRemainingLimit();
            
            if (request.getAmount().compareTo(remainingDailyLimit) > 0) {
                return ValidationCheck.failed(
                    "LIMIT_VALIDATION",
                    String.format("Exceeds daily limit. Remaining: %s", remainingDailyLimit),
                    ValidationSeverity.ERROR
                );
            }
            
            // Check daily transaction count
            if (dailyStatus.getTransactionCount() >= MAX_DAILY_TRANSACTIONS) {
                return ValidationCheck.failed(
                    "LIMIT_VALIDATION",
                    "Exceeded maximum daily transaction count: " + MAX_DAILY_TRANSACTIONS,
                    ValidationSeverity.ERROR
                );
            }
            
            // Check monthly limit
            MonthlyLimitStatus monthlyStatus = limitService.getMonthlyLimitStatus(request.getUserId());
            if (request.getAmount().compareTo(monthlyStatus.getRemainingLimit()) > 0) {
                return ValidationCheck.warning(
                    "LIMIT_VALIDATION",
                    "Approaching monthly limit",
                    Map.of("remaining", monthlyStatus.getRemainingLimit())
                );
            }
            
            return ValidationCheck.passed(
                "LIMIT_VALIDATION",
                Map.of(
                    "dailyRemaining", remainingDailyLimit,
                    "monthlyRemaining", monthlyStatus.getRemainingLimit(),
                    "transactionCount", dailyStatus.getTransactionCount()
                )
            );
            
        } catch (Exception e) {
            log.error("Limit validation error", e);
            return ValidationCheck.failed(
                "LIMIT_VALIDATION",
                "Unable to validate limits: " + e.getMessage(),
                ValidationSeverity.WARNING
            );
        }
    }

    /**
     * Validate sender and recipient accounts.
     */
    private ValidationCheck validateAccounts(PaymentRequest request) {
        log.debug("Validating accounts for payment");
        
        // Validate sender account
        User sender = userRepository.findById(request.getUserId()).orElse(null);
        if (sender == null) {
            return ValidationCheck.failed(
                "ACCOUNT_VALIDATION",
                "Sender account not found",
                ValidationSeverity.CRITICAL
            );
        }
        
        // Check sender account status
        if (!sender.isActive()) {
            return ValidationCheck.failed(
                "ACCOUNT_VALIDATION",
                "Sender account is not active",
                ValidationSeverity.ERROR
            );
        }
        
        // Check if sender account is verified
        if (!sender.isKycVerified() && request.getAmount().compareTo(new BigDecimal("1000")) > 0) {
            return ValidationCheck.failed(
                "ACCOUNT_VALIDATION",
                "KYC verification required for this amount",
                ValidationSeverity.ERROR
            );
        }
        
        // Validate recipient
        if (request.getRecipientType() == RecipientType.USER) {
            User recipient = userRepository.findById(request.getRecipientId()).orElse(null);
            if (recipient == null) {
                return ValidationCheck.failed(
                    "ACCOUNT_VALIDATION",
                    "Recipient account not found",
                    ValidationSeverity.ERROR
                );
            }
            
            if (!recipient.isActive()) {
                return ValidationCheck.failed(
                    "ACCOUNT_VALIDATION",
                    "Recipient account is not active",
                    ValidationSeverity.ERROR
                );
            }
            
            // Check for self-transfer
            if (sender.getId().equals(recipient.getId())) {
                return ValidationCheck.warning(
                    "ACCOUNT_VALIDATION",
                    "Self-transfer detected",
                    Map.of("userId", sender.getId())
                );
            }
        }
        
        // Validate merchant if applicable
        if (request.getRecipientType() == RecipientType.MERCHANT) {
            Merchant merchant = merchantRepository.findById(request.getRecipientId()).orElse(null);
            if (merchant == null) {
                return ValidationCheck.failed(
                    "ACCOUNT_VALIDATION",
                    "Merchant not found",
                    ValidationSeverity.ERROR
                );
            }
            
            if (!merchant.isActive() || !merchant.isVerified()) {
                return ValidationCheck.failed(
                    "ACCOUNT_VALIDATION",
                    "Merchant is not active or verified",
                    ValidationSeverity.ERROR
                );
            }
        }
        
        return ValidationCheck.passed("ACCOUNT_VALIDATION");
    }

    /**
     * Validate against fraud detection rules.
     */
    private ValidationCheck validateFraud(PaymentRequest request) {
        log.debug("Running fraud validation for payment");
        
        try {
            FraudCheckResult fraudResult = fraudDetectionService.checkPaymentFraud(request);
            
            if (fraudResult.isBlocked()) {
                return ValidationCheck.failed(
                    "FRAUD_VALIDATION",
                    "Payment blocked by fraud detection: " + fraudResult.getReason(),
                    ValidationSeverity.CRITICAL
                );
            }
            
            if (fraudResult.getRiskScore() > 0.8) {
                return ValidationCheck.failed(
                    "FRAUD_VALIDATION",
                    "High fraud risk detected",
                    ValidationSeverity.ERROR
                );
            }
            
            if (fraudResult.getRiskScore() > 0.5) {
                return ValidationCheck.warning(
                    "FRAUD_VALIDATION",
                    "Elevated fraud risk - additional verification may be required",
                    Map.of("riskScore", fraudResult.getRiskScore())
                );
            }
            
            return ValidationCheck.passed(
                "FRAUD_VALIDATION",
                Map.of("riskScore", fraudResult.getRiskScore())
            );
            
        } catch (Exception e) {
            log.error("Fraud validation error", e);
            // Be conservative on fraud check errors
            return ValidationCheck.failed(
                "FRAUD_VALIDATION",
                "Fraud validation service error",
                ValidationSeverity.ERROR
            );
        }
    }

    /**
     * Validate compliance requirements.
     */
    private ValidationCheck validateCompliance(PaymentRequest request) {
        log.debug("Validating compliance for payment");
        
        try {
            ComplianceCheckResult complianceResult = complianceService.checkPaymentCompliance(request);
            
            // Check sanctions
            if (complianceResult.isSanctioned()) {
                return ValidationCheck.failed(
                    "COMPLIANCE_VALIDATION",
                    "Transaction blocked due to sanctions",
                    ValidationSeverity.CRITICAL
                );
            }
            
            // Check AML
            if (complianceResult.getAmlRisk() == AMLRisk.HIGH) {
                return ValidationCheck.failed(
                    "COMPLIANCE_VALIDATION",
                    "High AML risk detected",
                    ValidationSeverity.ERROR
                );
            }
            
            // Check reporting requirements
            if (complianceResult.requiresReporting()) {
                // Flag for reporting but don't block
                log.info("Payment flagged for regulatory reporting: {}", request.getPaymentId());
            }
            
            // Check country restrictions
            if (complianceResult.hasCountryRestrictions()) {
                return ValidationCheck.failed(
                    "COMPLIANCE_VALIDATION",
                    "Transaction restricted due to country regulations",
                    ValidationSeverity.ERROR
                );
            }
            
            return ValidationCheck.passed(
                "COMPLIANCE_VALIDATION",
                Map.of("amlRisk", complianceResult.getAmlRisk())
            );
            
        } catch (Exception e) {
            log.error("Compliance validation error", e);
            return ValidationCheck.failed(
                "COMPLIANCE_VALIDATION",
                "Compliance check failed",
                ValidationSeverity.ERROR
            );
        }
    }

    /**
     * Validate transaction velocity.
     */
    @Cacheable(value = "velocityCheck", key = "#request.userId")
    private ValidationCheck validateVelocity(PaymentRequest request) {
        log.debug("Validating transaction velocity for user: {}", request.getUserId());
        
        try {
            // Check transactions in last hour
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            long recentTransactionCount = paymentRepository.countByUserIdAndCreatedAfter(
                request.getUserId(), oneHourAgo
            );
            
            if (recentTransactionCount >= SUSPICIOUS_VELOCITY_THRESHOLD) {
                return ValidationCheck.warning(
                    "VELOCITY_VALIDATION",
                    "High transaction velocity detected",
                    Map.of("count", recentTransactionCount, "period", "1 hour")
                );
            }
            
            // Check for rapid successive transactions
            LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
            long rapidTransactionCount = paymentRepository.countByUserIdAndCreatedAfter(
                request.getUserId(), fiveMinutesAgo
            );
            
            if (rapidTransactionCount >= 3) {
                return ValidationCheck.failed(
                    "VELOCITY_VALIDATION",
                    "Too many transactions in short period",
                    ValidationSeverity.WARNING
                );
            }
            
            return ValidationCheck.passed("VELOCITY_VALIDATION");
            
        } catch (Exception e) {
            log.error("Velocity validation error", e);
            return ValidationCheck.warning(
                "VELOCITY_VALIDATION",
                "Unable to validate velocity",
                Collections.emptyMap()
            );
        }
    }

    /**
     * Validate against blacklists.
     */
    private ValidationCheck validateBlacklist(PaymentRequest request) {
        log.debug("Validating against blacklists");
        
        try {
            // Check user blacklist
            if (blacklistService.isUserBlacklisted(request.getUserId())) {
                return ValidationCheck.failed(
                    "BLACKLIST_VALIDATION",
                    "User is blacklisted",
                    ValidationSeverity.CRITICAL
                );
            }
            
            // Check recipient blacklist
            if (blacklistService.isAccountBlacklisted(request.getRecipientId())) {
                return ValidationCheck.failed(
                    "BLACKLIST_VALIDATION",
                    "Recipient is blacklisted",
                    ValidationSeverity.CRITICAL
                );
            }
            
            // Check IP blacklist if available
            if (request.getIpAddress() != null && blacklistService.isIpBlacklisted(request.getIpAddress())) {
                return ValidationCheck.failed(
                    "BLACKLIST_VALIDATION",
                    "IP address is blacklisted",
                    ValidationSeverity.ERROR
                );
            }
            
            // Check device blacklist if available
            if (request.getDeviceId() != null && blacklistService.isDeviceBlacklisted(request.getDeviceId())) {
                return ValidationCheck.failed(
                    "BLACKLIST_VALIDATION",
                    "Device is blacklisted",
                    ValidationSeverity.ERROR
                );
            }
            
            return ValidationCheck.passed("BLACKLIST_VALIDATION");
            
        } catch (Exception e) {
            log.error("Blacklist validation error", e);
            return ValidationCheck.warning(
                "BLACKLIST_VALIDATION",
                "Unable to validate blacklist",
                Collections.emptyMap()
            );
        }
    }

    /**
     * Perform enhanced validation for high-risk transactions.
     */
    private ValidationCheck performEnhancedValidation(PaymentRequest request) {
        log.info("Performing enhanced validation for high-risk transaction");
        
        // Additional checks for high-value or suspicious transactions
        Map<String, Object> enhancedChecks = new HashMap<>();
        
        // Check transaction pattern
        boolean unusualPattern = checkUnusualTransactionPattern(request);
        enhancedChecks.put("unusualPattern", unusualPattern);
        
        // Check device consistency
        boolean deviceConsistent = checkDeviceConsistency(request);
        enhancedChecks.put("deviceConsistent", deviceConsistent);
        
        // Check location consistency
        boolean locationConsistent = checkLocationConsistency(request);
        enhancedChecks.put("locationConsistent", locationConsistent);
        
        // Require additional authentication for high-risk
        if (unusualPattern || !deviceConsistent || !locationConsistent) {
            enhancedChecks.put("requiresMFA", true);
            return ValidationCheck.warning(
                "ENHANCED_VALIDATION",
                "Additional authentication required",
                enhancedChecks
            );
        }
        
        return ValidationCheck.passed("ENHANCED_VALIDATION", enhancedChecks);
    }

    // Helper methods

    private boolean isHighRiskTransaction(PaymentRequest request) {
        return request.getAmount().compareTo(new BigDecimal("5000")) > 0 ||
               request.getRecipientType() == RecipientType.EXTERNAL ||
               request.isInternational();
    }

    private boolean isSuspiciousAmount(BigDecimal amount) {
        // Check for testing amounts like 1.00, 100.00, 1000.00
        return amount.remainder(new BigDecimal("100")).compareTo(BigDecimal.ZERO) == 0 &&
               amount.compareTo(new BigDecimal("10000")) <= 0;
    }

    private BigDecimal calculateTotalAmount(PaymentRequest request) {
        BigDecimal fee = calculateTransactionFee(request);
        return request.getAmount().add(fee);
    }

    private BigDecimal calculateTransactionFee(PaymentRequest request) {
        // Simple fee calculation - in production would be more complex
        if (request.isInternational()) {
            return request.getAmount().multiply(new BigDecimal("0.03")); // 3% for international
        }
        return request.getAmount().multiply(new BigDecimal("0.01")); // 1% for domestic
    }

    private BigDecimal getMinimumBalanceRequirement(String userId) {
        // Get minimum balance requirement for user account type
        return accountService.getMinimumBalance(userId);
    }

    private boolean checkUnusualTransactionPattern(PaymentRequest request) {
        // Check if transaction pattern is unusual for this user
        return false; // Simplified implementation
    }

    private boolean checkDeviceConsistency(PaymentRequest request) {
        // Check if device is consistent with user's history
        return true; // Simplified implementation
    }

    private boolean checkLocationConsistency(PaymentRequest request) {
        // Check if location is consistent with user's history
        return true; // Simplified implementation
    }

    private void logValidationResult(ValidationResult result) {
        if (!result.isValid()) {
            log.warn("Payment validation failed: {}", result.getFailureReasons());
        } else {
            log.debug("Payment validation passed");
        }
    }

    private void updateValidationMetrics(ValidationResult result) {
        meterRegistry.counter("payment.validation", 
            "status", result.isValid() ? "passed" : "failed",
            "severity", result.getSeverity().name()
        ).increment();
    }
}