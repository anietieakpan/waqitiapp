package com.waqiti.common.domain.services;

import com.waqiti.common.domain.Money;
import com.waqiti.common.domain.valueobjects.PaymentId;
import com.waqiti.common.domain.valueobjects.UserId;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Payment Validation Domain Service
 * Encapsulates complex payment validation business rules
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentValidationService {
    
    private static final Money MINIMUM_PAYMENT = Money.of(1.0, "NGN");
    private static final Money MAXIMUM_SINGLE_PAYMENT = Money.of(5_000_000.0, "NGN");
    private static final Money DAILY_LIMIT = Money.of(10_000_000.0, "NGN");
    private static final Money HIGH_VALUE_THRESHOLD = Money.of(1_000_000.0, "NGN");
    
    private static final Set<String> RESTRICTED_CURRENCIES = Set.of("BTC", "ETH", "USDT");
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("NGN", "USD", "GHS", "KES", "ZAR", "EUR", "GBP");
    
    // Business hours for instant payments (9 AM to 6 PM)
    private static final LocalTime BUSINESS_START = LocalTime.of(9, 0);
    private static final LocalTime BUSINESS_END = LocalTime.of(18, 0);
    
    /**
     * Validate payment request against business rules
     */
    public PaymentValidationResult validatePayment(PaymentValidationRequest request) {
        log.debug("Validating payment: amount={}, from={}, to={}", 
                request.getAmount(), request.getFromUserId(), request.getToUserId());
        
        List<String> violations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Amount validations
        validateAmount(request.getAmount(), violations);
        
        // User validations
        validateUsers(request.getFromUserId(), request.getToUserId(), violations);
        
        // Currency validations
        validateCurrency(request.getAmount(), violations, warnings);
        
        // Transaction limits
        validateTransactionLimits(request, violations, warnings);
        
        // Business rules
        validateBusinessRules(request, violations, warnings);
        
        // Compliance checks
        validateCompliance(request, violations, warnings);
        
        boolean isValid = violations.isEmpty();
        boolean requiresApproval = requiresManualApproval(request, warnings);
        
        PaymentValidationResult result = PaymentValidationResult.builder()
                .valid(isValid)
                .violations(violations)
                .warnings(warnings)
                .requiresApproval(requiresApproval)
                .riskLevel(calculateRiskLevel(request, warnings))
                .recommendedAction(determineRecommendedAction(isValid, requiresApproval, warnings))
                .build();
        
        log.debug("Payment validation result: valid={}, requiresApproval={}, riskLevel={}", 
                result.isValid(), result.isRequiresApproval(), result.getRiskLevel());
        
        return result;
    }
    
    /**
     * Validate transfer between accounts
     */
    public TransferValidationResult validateTransfer(TransferValidationRequest request) {
        List<String> violations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Cross-account validations
        if (request.getFromAccountId().equals(request.getToAccountId())) {
            violations.add("Cannot transfer to the same account");
        }
        
        // Balance validation
        if (request.getAvailableBalance().isLessThan(request.getAmount())) {
            violations.add("Insufficient funds for transfer");
        }
        
        // Cross-currency transfer validation
        if (!request.getAmount().getCurrencyCode().equals(request.getFromAccountCurrency())) {
            warnings.add("Cross-currency transfer requires exchange rate confirmation");
        }
        
        return TransferValidationResult.builder()
                .valid(violations.isEmpty())
                .violations(violations)
                .warnings(warnings)
                .estimatedFee(calculateTransferFee(request))
                .build();
    }
    
    /**
     * Validate bulk payment operation
     */
    public BulkPaymentValidationResult validateBulkPayment(BulkPaymentValidationRequest request) {
        List<String> violations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Bulk operation limits
        if (request.getPayments().size() > 1000) {
            violations.add("Bulk payment cannot exceed 1000 transactions");
        }
        
        // Total amount validation
        Money totalAmount = request.getPayments().stream()
                .map(PaymentValidationRequest::getAmount)
                .reduce(Money.zero(request.getPayments().get(0).getAmount().getCurrencyCode()), Money::add);
        
        if (totalAmount.isGreaterThan(Money.of(50_000_000.0, "NGN"))) {
            violations.add("Total bulk payment amount exceeds daily limit");
        }
        
        // Individual payment validations
        List<PaymentValidationResult> individualResults = request.getPayments().stream()
                .map(this::validatePayment)
                .toList();
        
        long invalidCount = individualResults.stream()
                .mapToLong(result -> result.isValid() ? 0 : 1)
                .sum();
        
        if (invalidCount > 0) {
            violations.add(invalidCount + " individual payments failed validation");
        }
        
        return BulkPaymentValidationResult.builder()
                .valid(violations.isEmpty())
                .violations(violations)
                .warnings(warnings)
                .totalAmount(totalAmount)
                .validPayments((int) (request.getPayments().size() - invalidCount))
                .invalidPayments((int) invalidCount)
                .individualResults(individualResults)
                .build();
    }
    
    // Private validation methods
    
    private void validateAmount(Money amount, List<String> violations) {
        if (amount == null) {
            violations.add("Payment amount is required");
            return;
        }
        
        if (amount.isZero()) {
            violations.add("Payment amount must be greater than zero");
        }
        
        if (amount.isNegative()) {
            violations.add("Payment amount cannot be negative");
        }
        
        if (amount.isLessThan(MINIMUM_PAYMENT)) {
            violations.add("Payment amount below minimum: " + MINIMUM_PAYMENT);
        }
        
        if (amount.isGreaterThan(MAXIMUM_SINGLE_PAYMENT)) {
            violations.add("Payment amount exceeds maximum: " + MAXIMUM_SINGLE_PAYMENT);
        }
    }
    
    private void validateUsers(UserId fromUserId, UserId toUserId, List<String> violations) {
        if (fromUserId == null) {
            violations.add("From user ID is required");
        }
        
        if (toUserId == null) {
            violations.add("To user ID is required");
        }
        
        if (fromUserId != null && toUserId != null && fromUserId.equals(toUserId)) {
            violations.add("Cannot send payment to yourself");
        }
    }
    
    private void validateCurrency(Money amount, List<String> violations, List<String> warnings) {
        if (amount == null) return;
        
        String currencyCode = amount.getCurrencyCode();
        
        if (RESTRICTED_CURRENCIES.contains(currencyCode)) {
            violations.add("Currency not supported: " + currencyCode);
        }
        
        if (!SUPPORTED_CURRENCIES.contains(currencyCode)) {
            warnings.add("Unusual currency detected: " + currencyCode);
        }
    }
    
    private void validateTransactionLimits(PaymentValidationRequest request, 
                                         List<String> violations, List<String> warnings) {
        Money amount = request.getAmount();
        
        // Daily limit check (would normally check against user's daily spending)
        if (amount.isGreaterThan(DAILY_LIMIT)) {
            violations.add("Payment exceeds daily transaction limit");
        }
        
        // High value warning
        if (amount.isGreaterThan(HIGH_VALUE_THRESHOLD)) {
            warnings.add("High value transaction requires additional verification");
        }
    }
    
    private void validateBusinessRules(PaymentValidationRequest request, 
                                     List<String> violations, List<String> warnings) {
        
        // Business hours validation for instant payments
        if (request.isInstantPayment()) {
            LocalTime now = LocalTime.now();
            if (now.isBefore(BUSINESS_START) || now.isAfter(BUSINESS_END)) {
                warnings.add("Instant payments outside business hours may experience delays");
            }
        }
        
        // Weekend processing warning
        if (isWeekend() && request.getAmount().isGreaterThan(Money.of(100_000.0, "NGN"))) {
            warnings.add("Large payments on weekends may require additional processing time");
        }
        
        // Cross-border validation
        if (request.isCrossBorder()) {
            if (request.getAmount().isGreaterThan(Money.usd(10_000.0))) {
                violations.add("Cross-border payments above $10,000 require regulatory approval");
            }
            warnings.add("Cross-border payment subject to additional fees and processing time");
        }
    }
    
    private void validateCompliance(PaymentValidationRequest request, 
                                  List<String> violations, List<String> warnings) {
        
        // AML/KYC checks
        if (request.getAmount().isGreaterThan(Money.of(1_000_000.0, "NGN"))) {
            if (!request.isKycVerified()) {
                violations.add("KYC verification required for payments above ₦1,000,000");
            }
            
            if (request.getPurpose() == null || request.getPurpose().trim().isEmpty()) {
                violations.add("Payment purpose required for high-value transactions");
            }
        }
        
        // Sanctions screening
        if (request.isSanctionsCheckRequired()) {
            warnings.add("Transaction requires sanctions screening");
        }
        
        // PEP (Politically Exposed Person) check
        if (request.isPepInvolved()) {
            warnings.add("Transaction involves PEP - enhanced due diligence required");
        }
    }
    
    private boolean requiresManualApproval(PaymentValidationRequest request, List<String> warnings) {
        // High value transactions
        if (request.getAmount().isGreaterThan(Money.of(5_000_000.0, "NGN"))) {
            return true;
        }
        
        // Multiple warning flags
        if (warnings.size() >= 3) {
            return true;
        }
        
        // Cross-border high value
        if (request.isCrossBorder() && request.getAmount().isGreaterThan(Money.usd(5_000.0))) {
            return true;
        }
        
        // PEP involvement
        if (request.isPepInvolved()) {
            return true;
        }
        
        return false;
    }
    
    private RiskLevel calculateRiskLevel(PaymentValidationRequest request, List<String> warnings) {
        int riskScore = 0;
        
        // Amount-based risk
        if (request.getAmount().isGreaterThan(HIGH_VALUE_THRESHOLD)) {
            riskScore += 3;
        } else if (request.getAmount().isGreaterThan(Money.of(100_000.0, "NGN"))) {
            riskScore += 1;
        }
        
        // Warning-based risk
        riskScore += warnings.size();
        
        // Cross-border risk
        if (request.isCrossBorder()) {
            riskScore += 2;
        }
        
        // Business hours risk
        LocalTime now = LocalTime.now();
        if (now.isBefore(BUSINESS_START) || now.isAfter(BUSINESS_END)) {
            riskScore += 1;
        }
        
        return switch (riskScore) {
            case 0, 1 -> RiskLevel.LOW;
            case 2, 3, 4 -> RiskLevel.MEDIUM;
            case 5, 6, 7 -> RiskLevel.HIGH;
            default -> RiskLevel.CRITICAL;
        };
    }
    
    private RecommendedAction determineRecommendedAction(boolean isValid, boolean requiresApproval, List<String> warnings) {
        if (!isValid) {
            return RecommendedAction.REJECT;
        }
        
        if (requiresApproval) {
            return RecommendedAction.MANUAL_REVIEW;
        }
        
        if (warnings.size() >= 2) {
            return RecommendedAction.ADDITIONAL_VERIFICATION;
        }
        
        return RecommendedAction.APPROVE;
    }
    
    private Money calculateTransferFee(TransferValidationRequest request) {
        Money amount = request.getAmount();
        
        // Base fee calculation
        Money baseFee = Money.of(100.0, "NGN");
        
        // Percentage fee (0.5%)
        Money percentageFee = amount.multiply(BigDecimal.valueOf(0.005));
        
        // Cross-currency fee
        if (!amount.getCurrencyCode().equals(request.getFromAccountCurrency())) {
            baseFee = baseFee.add(Money.of(500.0, "NGN"));
        }
        
        return baseFee.add(percentageFee);
    }
    
    private boolean isWeekend() {
        return java.time.LocalDate.now().getDayOfWeek().getValue() >= 6;
    }
    
    // Request and Result classes
    
    @Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PaymentValidationRequest {
        private PaymentId paymentId;
        private Money amount;
        private UserId fromUserId;
        private UserId toUserId;
        private String purpose;
        private boolean instantPayment;
        private boolean crossBorder;
        private boolean kycVerified;
        private boolean sanctionsCheckRequired;
        private boolean pepInvolved;
    }
    
    @Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PaymentValidationResult {
        private boolean valid;
        private List<String> violations;
        private List<String> warnings;
        private boolean requiresApproval;
        private RiskLevel riskLevel;
        private RecommendedAction recommendedAction;
    }
    
    @Data
    @Builder
    public static class TransferValidationRequest {
        private String fromAccountId;
        private String toAccountId;
        private Money amount;
        private Money availableBalance;
        private String fromAccountCurrency;
        private String toAccountCurrency;
    }
    
    @Data
    @Builder
    public static class TransferValidationResult {
        private boolean valid;
        private List<String> violations;
        private List<String> warnings;
        private Money estimatedFee;
    }
    
    @Data
    @Builder
    public static class BulkPaymentValidationRequest {
        private List<PaymentValidationRequest> payments;
        private UserId initiatorUserId;
    }
    
    @Data
    @Builder
    public static class BulkPaymentValidationResult {
        private boolean valid;
        private List<String> violations;
        private List<String> warnings;
        private Money totalAmount;
        private int validPayments;
        private int invalidPayments;
        private List<PaymentValidationResult> individualResults;
    }
    
    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    public enum RecommendedAction {
        APPROVE,
        ADDITIONAL_VERIFICATION,
        MANUAL_REVIEW,
        REJECT
    }
}