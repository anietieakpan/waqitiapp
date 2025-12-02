package com.waqiti.payment.service;

import com.waqiti.common.exception.ValidationException;
import com.waqiti.payment.dto.EnhancedPaymentRequest;
import com.waqiti.payment.entity.PaymentLimit;
import com.waqiti.payment.repository.PaymentLimitRepository;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.user.service.UserService;
import com.waqiti.wallet.service.WalletService;
import com.waqiti.security.service.FraudDetectionService;
import com.waqiti.compliance.service.SanctionScreeningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Comprehensive payment validation service
 * Performs business logic validation beyond basic field validation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentValidationService {

    private final PaymentRepository paymentRepository;
    private final PaymentLimitRepository limitRepository;
    private final UserService userService;
    private final WalletService walletService;
    private final FraudDetectionService fraudDetectionService;
    private final SanctionScreeningService sanctionScreeningService;

    // Validation patterns
    private static final Pattern SUSPICIOUS_DESCRIPTION_PATTERN = Pattern.compile(
        ".*(terrorism|money.*laundering|illegal|drugs|weapons|ransom).*", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Set<String> HIGH_RISK_COUNTRIES = Set.of(
        "IR", "KP", "SY", "CU", "VE", "AF", "LY", "SO", "YE", "ZW"
    );

    /**
     * Perform comprehensive validation of payment request
     */
    @Transactional(readOnly = true)
    public ValidationResult validatePaymentRequest(EnhancedPaymentRequest request) {
        ValidationResult result = new ValidationResult();
        
        // Run validations in parallel for performance
        List<CompletableFuture<Void>> validationFutures = Arrays.asList(
            CompletableFuture.runAsync(() -> validateUserStatus(request, result)),
            CompletableFuture.runAsync(() -> validatePaymentLimits(request, result)),
            CompletableFuture.runAsync(() -> validateBalance(request, result)),
            CompletableFuture.runAsync(() -> validateRecipient(request, result)),
            CompletableFuture.runAsync(() -> validateDuplicatePayment(request, result)),
            CompletableFuture.runAsync(() -> validateFraudRisk(request, result)),
            CompletableFuture.runAsync(() -> validateCompliance(request, result)),
            CompletableFuture.runAsync(() -> validateBusinessRules(request, result))
        );
        
        // Wait for all validations to complete
        try {
            CompletableFuture.allOf(validationFutures.toArray(new CompletableFuture[0]))
                .get(15, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Payment validation timed out after 15 seconds for transaction: {}", request.getTransactionId(), e);
            validationFutures.forEach(f -> f.cancel(true));
            result.addError("VALIDATION_TIMEOUT", "Payment validation timed out");
            result.setValid(false);
            return result;
        } catch (java.util.concurrent.ExecutionException e) {
            log.error("Payment validation execution failed for transaction: {}", request.getTransactionId(), e.getCause());
            result.addError("VALIDATION_ERROR", "Payment validation failed: " + e.getCause().getMessage());
            result.setValid(false);
            return result;
        } catch (java.util.concurrent.InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Payment validation interrupted for transaction: {}", request.getTransactionId(), e);
            result.addError("VALIDATION_INTERRUPTED", "Payment validation interrupted");
            result.setValid(false);
            return result;
        }

        // Set overall validation status
        result.setValid(result.getErrors().isEmpty() && !result.isRequiresAdditionalVerification());
        
        return result;
    }

    /**
     * Validate user status and permissions
     */
    private void validateUserStatus(EnhancedPaymentRequest request, ValidationResult result) {
        try {
            var user = userService.getUserById(request.getSenderId());
            
            if (!user.isActive()) {
                result.addError("INACTIVE_USER", "User account is not active");
                return;
            }
            
            if (user.isBlocked()) {
                result.addError("BLOCKED_USER", "User account is blocked");
                return;
            }
            
            if (!user.isKycVerified() && request.getAmount().compareTo(new BigDecimal("1000")) > 0) {
                result.addError("KYC_REQUIRED", "KYC verification required for payments over $1000");
                return;
            }
            
            // Check if user has required permissions for payment type
            if ("WIRE".equals(request.getPaymentMethod()) && !user.hasPermission("WIRE_TRANSFER")) {
                result.addError("PERMISSION_DENIED", "User not authorized for wire transfers");
            }
            
        } catch (Exception e) {
            log.error("Error validating user status", e);
            result.addError("USER_VALIDATION_ERROR", "Unable to validate user status");
        }
    }

    /**
     * Validate payment limits
     */
    private void validatePaymentLimits(EnhancedPaymentRequest request, ValidationResult result) {
        try {
            PaymentLimit limits = limitRepository.findByUserId(request.getSenderId())
                .orElse(PaymentLimit.getDefaultLimits());
            
            // Daily limit check
            BigDecimal dailyTotal = paymentRepository.getTotalPaymentsForDay(
                request.getSenderId(), LocalDateTime.now().truncatedTo(ChronoUnit.DAYS)
            );
            
            if (dailyTotal.add(request.getAmount()).compareTo(limits.getDailyLimit()) > 0) {
                result.addError("DAILY_LIMIT_EXCEEDED", 
                    String.format("Daily payment limit of %s exceeded", limits.getDailyLimit()));
                return;
            }
            
            // Transaction limit check
            if (request.getAmount().compareTo(limits.getTransactionLimit()) > 0) {
                result.addError("TRANSACTION_LIMIT_EXCEEDED", 
                    String.format("Transaction limit of %s exceeded", limits.getTransactionLimit()));
                return;
            }
            
            // Monthly limit check
            BigDecimal monthlyTotal = paymentRepository.getTotalPaymentsForMonth(
                request.getSenderId(), LocalDateTime.now().withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS)
            );
            
            if (monthlyTotal.add(request.getAmount()).compareTo(limits.getMonthlyLimit()) > 0) {
                result.addError("MONTHLY_LIMIT_EXCEEDED", 
                    String.format("Monthly payment limit of %s exceeded", limits.getMonthlyLimit()));
            }
            
            // Check velocity (number of transactions)
            long recentTransactions = paymentRepository.countRecentTransactions(
                request.getSenderId(), LocalDateTime.now().minusHours(1)
            );
            
            if (recentTransactions > 10) {
                result.setRequiresAdditionalVerification(true);
                result.addWarning("HIGH_VELOCITY", "High number of recent transactions detected");
            }
            
        } catch (Exception e) {
            log.error("Error validating payment limits", e);
            result.addError("LIMIT_VALIDATION_ERROR", "Unable to validate payment limits");
        }
    }

    /**
     * Validate sufficient balance
     */
    private void validateBalance(EnhancedPaymentRequest request, ValidationResult result) {
        try {
            if ("WALLET".equals(request.getPaymentMethod())) {
                BigDecimal balance = walletService.getAvailableBalance(
                    request.getSenderId(), request.getCurrency()
                );
                
                // Include estimated fees
                BigDecimal estimatedFee = calculateEstimatedFee(request);
                BigDecimal totalAmount = request.getAmount().add(estimatedFee);
                
                if (balance.compareTo(totalAmount) < 0) {
                    result.addError("INSUFFICIENT_BALANCE", 
                        String.format("Insufficient balance. Required: %s, Available: %s", 
                            totalAmount, balance));
                }
            }
        } catch (Exception e) {
            log.error("Error validating balance", e);
            result.addError("BALANCE_VALIDATION_ERROR", "Unable to validate balance");
        }
    }

    /**
     * Validate recipient
     */
    private void validateRecipient(EnhancedPaymentRequest request, ValidationResult result) {
        try {
            switch (request.getRecipientType()) {
                case "USER":
                    validateUserRecipient(request, result);
                    break;
                case "MERCHANT":
                    validateMerchantRecipient(request, result);
                    break;
                case "BANK_ACCOUNT":
                    validateBankAccountRecipient(request, result);
                    break;
                case "EMAIL":
                case "PHONE":
                    validateUnregisteredRecipient(request, result);
                    break;
            }
            
            // Check if self-payment
            if (request.getSenderId().toString().equals(request.getRecipientIdentifier())) {
                result.addWarning("SELF_PAYMENT", "Payment to self detected");
                result.setRequiresAdditionalVerification(true);
            }
            
        } catch (Exception e) {
            log.error("Error validating recipient", e);
            result.addError("RECIPIENT_VALIDATION_ERROR", "Unable to validate recipient");
        }
    }

    /**
     * Check for duplicate payments
     */
    private void validateDuplicatePayment(EnhancedPaymentRequest request, ValidationResult result) {
        try {
            // Check for exact duplicate in last 5 minutes
            boolean isDuplicate = paymentRepository.existsDuplicatePayment(
                request.getSenderId(),
                request.getRecipientIdentifier(),
                request.getAmount(),
                request.getCurrency(),
                LocalDateTime.now().minusMinutes(5)
            );
            
            if (isDuplicate) {
                result.addError("DUPLICATE_PAYMENT", 
                    "A similar payment was made recently. Please wait before retrying.");
            }
            
        } catch (Exception e) {
            log.error("Error checking duplicate payment", e);
            // Don't block payment on duplicate check failure
        }
    }

    /**
     * Validate fraud risk
     */
    private void validateFraudRisk(EnhancedPaymentRequest request, ValidationResult result) {
        try {
            var fraudScore = fraudDetectionService.calculateRiskScore(request);
            request.setRiskScore(fraudScore.getScore());
            
            if (fraudScore.getScore() > 0.8) {
                result.addError("HIGH_FRAUD_RISK", "Payment flagged as high risk");
                return;
            }
            
            if (fraudScore.getScore() > 0.5) {
                result.setRequiresAdditionalVerification(true);
                result.addWarning("MEDIUM_FRAUD_RISK", "Additional verification required");
            }
            
            // Check specific fraud indicators
            if (fraudScore.getIndicators().contains("VPN_DETECTED")) {
                result.addWarning("VPN_USAGE", "VPN usage detected");
            }
            
            if (fraudScore.getIndicators().contains("NEW_DEVICE")) {
                result.setRequiresAdditionalVerification(true);
                result.addWarning("NEW_DEVICE", "Payment from new device");
            }
            
        } catch (Exception e) {
            log.error("Error validating fraud risk", e);
            // Be cautious - require verification if fraud check fails
            result.setRequiresAdditionalVerification(true);
            result.addWarning("FRAUD_CHECK_FAILED", "Unable to complete fraud check");
        }
    }

    /**
     * Validate compliance requirements
     */
    private void validateCompliance(EnhancedPaymentRequest request, ValidationResult result) {
        try {
            // Sanctions screening
            if (request.getBeneficiaryName() != null) {
                var sanctionResult = sanctionScreeningService.screenName(request.getBeneficiaryName());
                if (sanctionResult.isMatch()) {
                    result.addError("SANCTIONS_HIT", "Recipient flagged in sanctions screening");
                    return;
                }
            }
            
            // Check high-risk countries
            if (request.getBeneficiaryAddress() != null) {
                String countryCode = extractCountryCode(request.getBeneficiaryAddress());
                if (HIGH_RISK_COUNTRIES.contains(countryCode)) {
                    result.addError("HIGH_RISK_COUNTRY", "Payments to this country are restricted");
                    return;
                }
            }
            
            // Check suspicious patterns in description
            if (request.getDescription() != null && 
                SUSPICIOUS_DESCRIPTION_PATTERN.matcher(request.getDescription()).matches()) {
                result.addError("SUSPICIOUS_DESCRIPTION", "Payment description contains prohibited terms");
            }
            
            // CTR reporting requirement
            if (request.getAmount().compareTo(new BigDecimal("10000")) >= 0) {
                result.addInfo("CTR_REQUIRED", "Currency Transaction Report will be filed");
            }
            
        } catch (Exception e) {
            log.error("Error validating compliance", e);
            result.addError("COMPLIANCE_VALIDATION_ERROR", "Unable to validate compliance requirements");
        }
    }

    /**
     * Validate business rules
     */
    private void validateBusinessRules(EnhancedPaymentRequest request, ValidationResult result) {
        try {
            // Weekend processing for certain payment types
            if (isWeekend() && "WIRE".equals(request.getPaymentMethod())) {
                result.addWarning("WEEKEND_PROCESSING", "Wire transfers are processed on business days only");
            }
            
            // Currency conversion warnings
            if (requiresCurrencyConversion(request)) {
                result.addInfo("CURRENCY_CONVERSION", "Currency conversion will be applied at current market rate");
            }
            
            // Express processing validation
            if (Boolean.TRUE.equals(request.getExpressProcessing())) {
                if (!isExpressProcessingAvailable(request)) {
                    result.addError("EXPRESS_NOT_AVAILABLE", "Express processing not available for this payment type");
                }
            }
            
            // Scheduled payment validation
            if (request.getScheduledDate() != null) {
                if (request.getScheduledDate().isBefore(LocalDateTime.now())) {
                    result.addError("INVALID_SCHEDULE_DATE", "Scheduled date cannot be in the past");
                }
            }
            
        } catch (Exception e) {
            log.error("Error validating business rules", e);
            result.addWarning("BUSINESS_RULE_WARNING", "Some business rules could not be validated");
        }
    }

    // Helper methods

    private void validateUserRecipient(EnhancedPaymentRequest request, ValidationResult result) {
        try {
            UUID recipientId = UUID.fromString(request.getRecipientIdentifier());
            var recipient = userService.getUserById(recipientId);
            
            if (!recipient.isActive()) {
                result.addError("INACTIVE_RECIPIENT", "Recipient account is not active");
            }
            
            if (recipient.isBlocked()) {
                result.addError("BLOCKED_RECIPIENT", "Recipient account is blocked");
            }
        } catch (IllegalArgumentException e) {
            result.addError("INVALID_RECIPIENT_ID", "Invalid recipient user ID format");
        } catch (Exception e) {
            result.addError("RECIPIENT_NOT_FOUND", "Recipient user not found");
        }
    }

    private void validateMerchantRecipient(EnhancedPaymentRequest request, ValidationResult result) {
        // Merchant-specific validation
        // Check merchant status, category restrictions, etc.
    }

    private void validateBankAccountRecipient(EnhancedPaymentRequest request, ValidationResult result) {
        // Bank account validation
        // Verify account status, ownership, etc.
    }

    private void validateUnregisteredRecipient(EnhancedPaymentRequest request, ValidationResult result) {
        // For email/phone recipients who aren't registered users
        result.addInfo("UNREGISTERED_RECIPIENT", "Recipient will need to register to claim payment");
    }

    private BigDecimal calculateEstimatedFee(EnhancedPaymentRequest request) {
        // Fee calculation logic
        return BigDecimal.ZERO; // Simplified for example
    }

    private boolean isWeekend() {
        int dayOfWeek = LocalDateTime.now().getDayOfWeek().getValue();
        return dayOfWeek == 6 || dayOfWeek == 7;
    }

    private boolean requiresCurrencyConversion(EnhancedPaymentRequest request) {
        // Check if sender's default currency differs from payment currency
        return false; // Simplified
    }

    private boolean isExpressProcessingAvailable(EnhancedPaymentRequest request) {
        return !"WIRE".equals(request.getPaymentMethod());
    }

    private String extractCountryCode(String address) {
        // Extract country code from address
        return ""; // Simplified
    }

    /**
     * Validation result container
     */
    @Data
    public static class ValidationResult {
        private boolean valid = true;
        private boolean requiresAdditionalVerification = false;
        private List<ValidationError> errors = new ArrayList<>();
        private List<ValidationWarning> warnings = new ArrayList<>();
        private List<ValidationInfo> info = new ArrayList<>();

        public void addError(String code, String message) {
            errors.add(new ValidationError(code, message));
        }

        public void addWarning(String code, String message) {
            warnings.add(new ValidationWarning(code, message));
        }

        public void addInfo(String code, String message) {
            info.add(new ValidationInfo(code, message));
        }
    }

    @Data
    @AllArgsConstructor
    public static class ValidationError {
        private String code;
        private String message;
    }

    @Data
    @AllArgsConstructor
    public static class ValidationWarning {
        private String code;
        private String message;
    }

    @Data
    @AllArgsConstructor
    public static class ValidationInfo {
        private String code;
        private String message;
    }

    /**
     * Validate ACH transfer request
     */
    public boolean validateACHTransferRequest(UUID transferId, UUID userId, String sourceAccountId,
                                             String targetAccountId, BigDecimal amount) {
        log.debug("Validating ACH transfer request: transferId={}, userId={}, amount={}",
                transferId, userId, amount);

        // Basic validations
        if (transferId == null || userId == null || amount == null) {
            return false;
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        if (sourceAccountId == null || sourceAccountId.trim().isEmpty()) {
            return false;
        }

        if (targetAccountId == null || targetAccountId.trim().isEmpty()) {
            return false;
        }

        // Additional validations would include:
        // - Account ownership verification
        // - Account status checks
        // - Compliance screening

        return true;
    }
}