package com.waqiti.payment.service;

import com.waqiti.common.kyc.annotation.RequireKYCVerification;
import com.waqiti.common.kyc.service.KYCClientService;
import com.waqiti.payment.domain.PaymentMethod;
import com.waqiti.payment.dto.CreatePaymentRequest;
import com.waqiti.payment.dto.PaymentLimits;
import com.waqiti.payment.exception.KYCVerificationRequiredException;
import com.waqiti.payment.exception.PaymentLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payment service with integrated KYC verification checks
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KYCIntegratedPaymentService {

    private final PaymentService paymentService;
    private final PaymentMethodService paymentMethodService;
    private final KYCClientService kycClientService;

    // Payment limits based on KYC level
    private static final BigDecimal BASIC_KYC_DAILY_LIMIT = new BigDecimal("500");
    private static final BigDecimal BASIC_KYC_TRANSACTION_LIMIT = new BigDecimal("100");
    
    private static final BigDecimal INTERMEDIATE_KYC_DAILY_LIMIT = new BigDecimal("5000");
    private static final BigDecimal INTERMEDIATE_KYC_TRANSACTION_LIMIT = new BigDecimal("1000");
    
    private static final BigDecimal ADVANCED_KYC_DAILY_LIMIT = new BigDecimal("50000");
    private static final BigDecimal ADVANCED_KYC_TRANSACTION_LIMIT = new BigDecimal("10000");
    
    private static final BigDecimal UNVERIFIED_DAILY_LIMIT = new BigDecimal("50");
    private static final BigDecimal UNVERIFIED_TRANSACTION_LIMIT = new BigDecimal("20");

    /**
     * Create payment with KYC verification
     */
    @Transactional
    @RequireKYCVerification(level = RequireKYCVerification.VerificationLevel.BASIC, 
                           message = "KYC verification required to send payments")
    public void createPaymentWithKYC(UUID userId, CreatePaymentRequest request) {
        log.info("Creating payment with KYC verification for user: {}", userId);
        
        // Check transaction limits based on KYC level
        validateTransactionLimits(userId, request.getAmount());
        
        // For high-value transactions, require advanced KYC
        if (request.getAmount().compareTo(INTERMEDIATE_KYC_TRANSACTION_LIMIT) > 0) {
            if (!kycClientService.isUserAdvancedVerified(userId.toString())) {
                throw new KYCVerificationRequiredException(
                    "Advanced KYC verification required for transactions over " + INTERMEDIATE_KYC_TRANSACTION_LIMIT
                );
            }
        }
        
        // Proceed with payment
        paymentService.createPayment(request);
    }

    /**
     * Add payment method with KYC verification
     */
    @RequireKYCVerification(level = RequireKYCVerification.VerificationLevel.INTERMEDIATE,
                           message = "Intermediate KYC verification required to add payment methods")
    public void addPaymentMethodWithKYC(UUID userId, PaymentMethod paymentMethod) {
        log.info("Adding payment method with KYC verification for user: {}", userId);
        
        // Bank accounts and crypto require advanced KYC
        if (paymentMethod.getMethodType() == PaymentMethod.PaymentMethodType.BANK_ACCOUNT ||
            paymentMethod.getMethodType() == PaymentMethod.PaymentMethodType.CRYPTOCURRENCY) {
            
            if (!kycClientService.isUserAdvancedVerified(userId.toString())) {
                throw new KYCVerificationRequiredException(
                    "Advanced KYC verification required for bank accounts and cryptocurrency"
                );
            }
        }
        
        // Proceed with adding payment method
        // paymentMethodService.createPaymentMethod(userId, paymentMethod);
    }

    /**
     * Get payment limits based on user's KYC level
     */
    public PaymentLimits getUserPaymentLimits(UUID userId) {
        log.debug("Getting payment limits for user: {}", userId);
        
        String userIdStr = userId.toString();
        
        PaymentLimits limits = new PaymentLimits();
        
        if (kycClientService.isUserAdvancedVerified(userIdStr)) {
            limits.setDailyLimit(ADVANCED_KYC_DAILY_LIMIT);
            limits.setTransactionLimit(ADVANCED_KYC_TRANSACTION_LIMIT);
            limits.setLevel("ADVANCED");
        } else if (kycClientService.isUserIntermediateVerified(userIdStr)) {
            limits.setDailyLimit(INTERMEDIATE_KYC_DAILY_LIMIT);
            limits.setTransactionLimit(INTERMEDIATE_KYC_TRANSACTION_LIMIT);
            limits.setLevel("INTERMEDIATE");
        } else if (kycClientService.isUserBasicVerified(userIdStr)) {
            limits.setDailyLimit(BASIC_KYC_DAILY_LIMIT);
            limits.setTransactionLimit(BASIC_KYC_TRANSACTION_LIMIT);
            limits.setLevel("BASIC");
        } else {
            limits.setDailyLimit(UNVERIFIED_DAILY_LIMIT);
            limits.setTransactionLimit(UNVERIFIED_TRANSACTION_LIMIT);
            limits.setLevel("UNVERIFIED");
        }
        
        return limits;
    }

    /**
     * Check if user can perform international transfer
     */
    public boolean canUserMakeInternationalTransfer(UUID userId) {
        return kycClientService.canUserMakeInternationalTransfer(userId.toString());
    }

    /**
     * Check if user can purchase cryptocurrency
     */
    public boolean canUserPurchaseCrypto(UUID userId) {
        return kycClientService.canUserPurchaseCrypto(userId.toString());
    }

    /**
     * Validate transaction against KYC-based limits
     */
    private void validateTransactionLimits(UUID userId, BigDecimal amount) {
        PaymentLimits limits = getUserPaymentLimits(userId);
        
        if (amount.compareTo(limits.getTransactionLimit()) > 0) {
            throw new PaymentLimitExceededException(
                String.format("Transaction amount exceeds your limit of %s. Current KYC level: %s",
                    limits.getTransactionLimit(), limits.getLevel())
            );
        }
        
        // Check daily limit (would need to aggregate daily transactions)
        BigDecimal dailyTotal = calculateDailyTransactionTotal(userId);
        if (dailyTotal.add(amount).compareTo(limits.getDailyLimit()) > 0) {
            throw new PaymentLimitExceededException(
                String.format("Transaction would exceed your daily limit of %s. Current daily total: %s",
                    limits.getDailyLimit(), dailyTotal)
            );
        }
    }

    /**
     * Calculate total transactions for the day
     */
    private BigDecimal calculateDailyTransactionTotal(UUID userId) {
        // This would query the payment repository for today's transactions
        // Simplified for now
        return BigDecimal.ZERO;
    }

    /**
     * Process high-risk payment with enhanced KYC checks
     */
    @RequireKYCVerification(action = "HIGH_VALUE_TRANSFER")
    public void processHighRiskPayment(UUID userId, CreatePaymentRequest request) {
        log.info("Processing high-risk payment for user: {} amount: {}", userId, request.getAmount());
        
        // Additional fraud checks for high-risk payments
        if (request.getAmount().compareTo(new BigDecimal("5000")) > 0) {
            // Verify user has been KYC verified for at least 30 days
            // Additional checks could be implemented here
        }
        
        paymentService.createPayment(request);
    }
}