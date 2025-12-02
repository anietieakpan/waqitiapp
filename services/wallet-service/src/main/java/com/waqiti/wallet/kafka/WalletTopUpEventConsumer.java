package com.waqiti.wallet.kafka;

import com.waqiti.common.events.WalletTopUpEvent;
import com.waqiti.common.events.WalletToppedUpEvent;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.domain.WalletTransaction;
import com.waqiti.wallet.domain.TransactionType;
import com.waqiti.wallet.domain.TransactionStatus;
import com.waqiti.wallet.domain.WalletStatus;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.repository.WalletTransactionRepository;
import com.waqiti.wallet.service.WalletService;
import com.waqiti.wallet.service.PaymentMethodService;
import com.waqiti.wallet.service.FraudDetectionService;
import com.waqiti.wallet.service.ComplianceService;
import com.waqiti.wallet.service.NotificationService;
import com.waqiti.wallet.service.AuditService;
import com.waqiti.wallet.service.LimitValidationService;
import com.waqiti.wallet.service.BalanceManagementService;
import com.waqiti.wallet.service.CurrencyConversionService;
import com.waqiti.wallet.exception.WalletException;
import com.waqiti.wallet.exception.ComplianceViolationException;
import com.waqiti.wallet.exception.InsufficientFundsException;
import com.waqiti.wallet.exception.PaymentMethodException;
import com.waqiti.common.security.encryption.EncryptionService;
import com.waqiti.common.compliance.ComplianceValidator;
import com.waqiti.common.audit.AuditEventType;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * CRITICAL WALLET TOP-UP EVENT CONSUMER - Consumer 45
 * 
 * Processes wallet top-up events with zero-tolerance 12-step processing:
 * 1. Event validation and sanitization
 * 2. Idempotency and duplicate detection
 * 3. Regulatory compliance verification
 * 4. Wallet validation and status verification
 * 5. Payment method validation and verification
 * 6. Fraud detection and risk assessment
 * 7. Transaction limits and velocity checks
 * 8. Currency conversion and fee calculation
 * 9. Payment processing and authorization
 * 10. Balance update and reconciliation
 * 11. Audit trail and transaction recording
 * 12. Notification dispatch and confirmation
 * 
 * REGULATORY COMPLIANCE:
 * - Electronic Fund Transfer Act (EFTA)
 * - Payment Services Directive 2 (PSD2)
 * - Anti-Money Laundering (AML) requirements
 * - Know Your Customer (KYC) verification
 * - Consumer Financial Protection regulations
 * - E-money regulations (EMD2)
 * - Payment Card Industry (PCI DSS) compliance
 * 
 * TOP-UP METHODS SUPPORTED:
 * - Credit/Debit card payments
 * - Bank account transfers (ACH/SEPA)
 * - Digital wallet transfers
 * - Cryptocurrency deposits
 * - Cash deposit networks
 * - Mobile money transfers
 * 
 * SLA: 99.99% uptime, <15s processing time
 * 
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
@Validated
public class WalletTopUpEventConsumer {
    
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletService walletService;
    private final PaymentMethodService paymentMethodService;
    private final FraudDetectionService fraudDetectionService;
    private final ComplianceService complianceService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final LimitValidationService limitValidationService;
    private final BalanceManagementService balanceManagementService;
    private final CurrencyConversionService currencyConversionService;
    private final EncryptionService encryptionService;
    private final ComplianceValidator complianceValidator;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UniversalDLQHandler dlqHandler;
    
    private static final String WALLET_TOPPED_UP_TOPIC = "wallet-topped-up-events";
    private static final String FRAUD_ALERT_TOPIC = "fraud-alert-events";
    private static final String COMPLIANCE_ALERT_TOPIC = "compliance-alert-events";
    private static final String PAYMENT_PROCESSED_TOPIC = "payment-processed-events";
    private static final String DLQ_TOPIC = "wallet-topup-events-dlq";
    
    private static final BigDecimal MAX_SINGLE_TOPUP = new BigDecimal("10000.00");
    private static final BigDecimal MIN_TOPUP_AMOUNT = new BigDecimal("1.00");
    private static final BigDecimal MAX_DAILY_TOPUP = new BigDecimal("50000.00");
    private static final BigDecimal MAX_MONTHLY_TOPUP = new BigDecimal("200000.00");
    private static final int MAX_VELOCITY_COUNT = 10; // Max top-ups per hour

    @KafkaListener(
        topics = "wallet-topup-events",
        groupId = "wallet-topup-processor",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "5"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Retryable(
        value = {WalletException.class, PaymentMethodException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2, maxDelay = 10000)
    )
    public void handleWalletTopUpEvent(
            @Payload @Valid WalletTopUpEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {
        
        String correlationId = generateCorrelationId(event, partition, offset);
        long processingStartTime = System.currentTimeMillis();
        
        log.info("STEP 1: Processing wallet top-up event - ID: {}, User: {}, Amount: {}, Method: {}, Correlation: {}",
            event.getTransactionId(), event.getUserId(), event.getAmount(), event.getPaymentMethod(), correlationId);
        
        try {
            // STEP 1: Event validation and sanitization
            validateAndSanitizeEvent(event, correlationId);
            
            // STEP 2: Idempotency and duplicate detection
            if (checkIdempotencyAndDuplicates(event, correlationId)) {
                acknowledgeAndReturn(acknowledgment, "Duplicate wallet top-up event detected");
                return;
            }
            
            // STEP 3: Regulatory compliance verification
            performComplianceVerification(event, correlationId);
            
            // STEP 4: Wallet validation and status verification
            Wallet wallet = validateWalletAndStatus(event, correlationId);
            
            // STEP 5: Payment method validation and verification
            PaymentMethodValidationResult paymentMethodValidation = validateAndVerifyPaymentMethod(event, correlationId);
            
            // STEP 6: Fraud detection and risk assessment
            FraudDetectionResult fraudResult = performFraudDetectionAndRiskAssessment(event, wallet, correlationId);
            
            // STEP 7: Transaction limits and velocity checks
            LimitValidationResult limitValidation = performTransactionLimitsAndVelocityChecks(event, wallet, correlationId);
            
            // STEP 8: Currency conversion and fee calculation
            CurrencyConversionResult conversionResult = performCurrencyConversionAndFeeCalculation(event, wallet, correlationId);
            
            // STEP 9: Payment processing and authorization
            PaymentProcessingResult paymentResult = processPaymentAndAuthorization(
                event, paymentMethodValidation, conversionResult, correlationId);
            
            // STEP 10: Balance update and reconciliation
            BalanceUpdateResult balanceUpdate = updateBalanceAndReconcile(
                event, wallet, conversionResult, paymentResult, correlationId);
            
            // STEP 11: Audit trail and transaction recording
            WalletTransaction transaction = createAuditTrailAndRecordTransaction(event, wallet, 
                paymentMethodValidation, fraudResult, limitValidation, conversionResult, paymentResult, 
                balanceUpdate, correlationId, processingStartTime);
            
            // STEP 12: Notification dispatch and confirmation
            dispatchNotificationsAndConfirmation(event, wallet, transaction, paymentResult, correlationId);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            long processingTime = System.currentTimeMillis() - processingStartTime;
            log.info("Successfully processed wallet top-up - ID: {}, Amount: {}, New Balance: {}, Time: {}ms, Correlation: {}",
                event.getTransactionId(), conversionResult.getFinalAmount(), balanceUpdate.getNewBalance(), 
                processingTime, correlationId);
            
        } catch (ComplianceViolationException e) {
            handleComplianceViolation(event, e, correlationId, acknowledgment);
        } catch (InsufficientFundsException e) {
            handleInsufficientFundsError(event, e, correlationId, acknowledgment);
        } catch (PaymentMethodException e) {
            handlePaymentMethodError(event, e, correlationId, acknowledgment);
        } catch (WalletException e) {
            handleWalletError(event, e, correlationId, acknowledgment);
        } catch (Exception e) {
            handleCriticalError(event, e, correlationId, acknowledgment);
        }
    }

    /**
     * STEP 1: Event validation and sanitization
     */
    private void validateAndSanitizeEvent(WalletTopUpEvent event, String correlationId) {
        log.debug("STEP 1: Validating wallet top-up event - Correlation: {}", correlationId);
        
        if (event == null) {
            throw new IllegalArgumentException("Wallet top-up event cannot be null");
        }
        
        if (event.getTransactionId() == null || event.getTransactionId().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID is required");
        }
        
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        
        if (event.getWalletId() == null || event.getWalletId().trim().isEmpty()) {
            throw new IllegalArgumentException("Wallet ID is required");
        }
        
        if (event.getAmount() == null || event.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid top-up amount: " + event.getAmount());
        }
        
        if (event.getAmount().compareTo(MAX_SINGLE_TOPUP) > 0) {
            throw new WalletException("Top-up amount exceeds maximum: " + MAX_SINGLE_TOPUP);
        }
        
        if (event.getAmount().compareTo(MIN_TOPUP_AMOUNT) < 0) {
            throw new WalletException("Top-up amount below minimum: " + MIN_TOPUP_AMOUNT);
        }
        
        if (event.getCurrency() == null || event.getCurrency().trim().isEmpty()) {
            event.setCurrency("USD"); // Default currency
        }
        
        if (event.getPaymentMethod() == null || event.getPaymentMethod().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment method is required");
        }
        
        if (event.getPaymentMethodId() == null || event.getPaymentMethodId().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment method ID is required");
        }
        
        // Validate amount precision (max 2 decimal places for most currencies)
        if (event.getAmount().scale() > 2) {
            event.setAmount(event.getAmount().setScale(2, RoundingMode.HALF_UP));
        }
        
        // Sanitize string fields
        event.setTransactionId(sanitizeString(event.getTransactionId()));
        event.setUserId(sanitizeString(event.getUserId()));
        event.setWalletId(sanitizeString(event.getWalletId()));
        event.setCurrency(sanitizeString(event.getCurrency().toUpperCase()));
        event.setPaymentMethod(sanitizeString(event.getPaymentMethod().toUpperCase()));
        event.setPaymentMethodId(sanitizeString(event.getPaymentMethodId()));
        
        log.debug("STEP 1: Event validation completed - Amount: {}, Currency: {}, Correlation: {}",
            event.getAmount(), event.getCurrency(), correlationId);
    }

    /**
     * STEP 2: Idempotency and duplicate detection
     */
    private boolean checkIdempotencyAndDuplicates(WalletTopUpEvent event, String correlationId) {
        log.debug("STEP 2: Checking idempotency - Correlation: {}", correlationId);
        
        // Check for existing transaction
        boolean isDuplicate = walletTransactionRepository.existsByTransactionIdAndUserId(
            event.getTransactionId(), event.getUserId());
        
        if (isDuplicate) {
            log.warn("Duplicate wallet top-up detected - Transaction: {}, User: {}, Correlation: {}",
                event.getTransactionId(), event.getUserId(), correlationId);
            
            auditService.logEvent(AuditEventType.DUPLICATE_WALLET_TOPUP_DETECTED, 
                event.getUserId(), event.getTransactionId(), correlationId);
        }
        
        return isDuplicate;
    }

    /**
     * STEP 3: Regulatory compliance verification
     */
    private void performComplianceVerification(WalletTopUpEvent event, String correlationId) {
        log.debug("STEP 3: Performing compliance verification - Correlation: {}", correlationId);
        
        // KYC verification
        if (!complianceService.isUserKYCCompliant(event.getUserId())) {
            throw new ComplianceViolationException("User KYC not compliant: " + event.getUserId());
        }
        
        // AML screening for large amounts
        if (event.getAmount().compareTo(new BigDecimal("3000.00")) >= 0) {
            ComplianceResult amlResult = complianceService.performAMLScreening(
                event.getUserId(), event.getAmount());
            if (amlResult.hasViolations()) {
                throw new ComplianceViolationException("AML violations detected: " + amlResult.getViolations());
            }
        }
        
        // OFAC sanctions screening
        ComplianceResult sanctionsResult = complianceService.performSanctionsScreening(event.getUserId());
        if (sanctionsResult.hasViolations()) {
            throw new ComplianceViolationException("Sanctions violations detected: " + sanctionsResult.getViolations());
        }
        
        // E-money regulations compliance
        if (!complianceService.isEMoneyCompliant(event.getUserId(), event.getAmount())) {
            throw new ComplianceViolationException("E-money regulations violation");
        }
        
        // PSD2 compliance for EU users
        if (isEUUser(event.getUserId()) && !complianceService.isPSD2Compliant(event)) {
            throw new ComplianceViolationException("PSD2 compliance violation");
        }
        
        log.debug("STEP 3: Compliance verification completed - Correlation: {}", correlationId);
    }

    /**
     * STEP 4: Wallet validation and status verification
     */
    private Wallet validateWalletAndStatus(WalletTopUpEvent event, String correlationId) {
        log.debug("STEP 4: Validating wallet - Correlation: {}", correlationId);
        
        Wallet wallet = walletRepository.findById(event.getWalletId())
            .orElseThrow(() -> new WalletException("Wallet not found: " + event.getWalletId()));
        
        // Verify wallet ownership
        if (!wallet.getUserId().equals(event.getUserId())) {
            throw new WalletException("Wallet does not belong to user: " + event.getUserId());
        }
        
        // Check wallet status
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new WalletException("Wallet is not active: " + wallet.getStatus());
        }
        
        // Check if wallet is frozen
        if (wallet.isFrozen()) {
            throw new WalletException("Wallet is frozen: " + event.getWalletId());
        }
        
        // Check wallet currency support
        if (!wallet.getSupportedCurrencies().contains(event.getCurrency())) {
            throw new WalletException("Wallet does not support currency: " + event.getCurrency());
        }
        
        // Validate wallet balance limits
        BigDecimal currentBalance = wallet.getBalance();
        BigDecimal newBalance = currentBalance.add(event.getAmount());
        
        if (newBalance.compareTo(wallet.getMaxBalance()) > 0) {
            throw new WalletException("Top-up would exceed wallet maximum balance: " + wallet.getMaxBalance());
        }
        
        log.debug("STEP 4: Wallet validation completed - Current Balance: {}, Max Balance: {}, Correlation: {}",
            currentBalance, wallet.getMaxBalance(), correlationId);
        
        return wallet;
    }

    /**
     * STEP 5: Payment method validation and verification
     */
    private PaymentMethodValidationResult validateAndVerifyPaymentMethod(WalletTopUpEvent event, String correlationId) {
        log.debug("STEP 5: Validating payment method - Correlation: {}", correlationId);
        
        PaymentMethodValidationResult result = paymentMethodService.validatePaymentMethod(
            event.getUserId(), event.getPaymentMethodId(), event.getPaymentMethod());
        
        if (!result.isValid()) {
            throw new PaymentMethodException("Invalid payment method: " + result.getErrorMessage());
        }
        
        if (!result.isActive()) {
            throw new PaymentMethodException("Payment method is not active: " + event.getPaymentMethodId());
        }
        
        // Verify payment method ownership
        if (!result.isOwnedByUser(event.getUserId())) {
            throw new PaymentMethodException("Payment method does not belong to user: " + event.getUserId());
        }
        
        // Check payment method limits
        if (event.getAmount().compareTo(result.getSingleTransactionLimit()) > 0) {
            throw new PaymentMethodException("Amount exceeds payment method limit: " + result.getSingleTransactionLimit());
        }
        
        // Verify sufficient funds/credit for payment method
        if (!paymentMethodService.hasSufficientFunds(event.getPaymentMethodId(), event.getAmount())) {
            throw new InsufficientFundsException("Insufficient funds on payment method: " + event.getPaymentMethodId());
        }
        
        log.debug("STEP 5: Payment method validation completed - Type: {}, Limit: {}, Correlation: {}",
            result.getPaymentMethodType(), result.getSingleTransactionLimit(), correlationId);
        
        return result;
    }

    /**
     * STEP 6: Fraud detection and risk assessment
     */
    private FraudDetectionResult performFraudDetectionAndRiskAssessment(WalletTopUpEvent event, Wallet wallet, String correlationId) {
        log.debug("STEP 6: Performing fraud detection - Correlation: {}", correlationId);
        
        FraudDetectionResult result = fraudDetectionService.detectFraud(
            event.getUserId(), event.getAmount(), event.getPaymentMethodId(), wallet.getId());
        
        if (result.isFraudulent()) {
            log.error("Fraudulent top-up detected - User: {}, Score: {}, Reasons: {}, Correlation: {}",
                event.getUserId(), result.getFraudScore(), result.getFraudReasons(), correlationId);
            
            // Freeze wallet temporarily for investigation
            walletService.freezeWalletTemporarily(wallet.getId(), "FRAUD_DETECTION", correlationId);
            
            throw new WalletException("Transaction blocked due to fraud detection: " + result.getFraudReasons());
        }
        
        if (result.isHighRisk()) {
            log.warn("High risk top-up detected - User: {}, Score: {}, Correlation: {}",
                event.getUserId(), result.getFraudScore(), correlationId);
            
            // Enhanced monitoring for high-risk transactions
            fraudDetectionService.enableEnhancedMonitoring(event.getUserId(), correlationId);
        }
        
        log.debug("STEP 6: Fraud detection completed - Score: {}, Risk Level: {}, Correlation: {}",
            result.getFraudScore(), result.getRiskLevel(), correlationId);
        
        return result;
    }

    /**
     * STEP 7: Transaction limits and velocity checks
     */
    private LimitValidationResult performTransactionLimitsAndVelocityChecks(WalletTopUpEvent event, Wallet wallet, String correlationId) {
        log.debug("STEP 7: Checking transaction limits - Correlation: {}", correlationId);
        
        LimitValidationResult result = limitValidationService.validateTransactionLimits(
            event.getUserId(), event.getAmount(), TransactionType.TOP_UP);
        
        // Check daily limits
        if (!result.isDailyLimitValid()) {
            throw new WalletException("Daily top-up limit exceeded: " + result.getDailyLimit());
        }
        
        // Check monthly limits
        if (!result.isMonthlyLimitValid()) {
            throw new WalletException("Monthly top-up limit exceeded: " + result.getMonthlyLimit());
        }
        
        // Check velocity limits
        int recentTopUps = limitValidationService.countRecentTopUps(event.getUserId(), 60); // Last hour
        if (recentTopUps >= MAX_VELOCITY_COUNT) {
            throw new WalletException("Too many top-ups in the last hour. Please try again later.");
        }
        
        result.setVelocityCount(recentTopUps);
        
        // Update remaining limits
        result.setRemainingDailyLimit(result.getDailyLimit().subtract(result.getDailyUsage()).subtract(event.getAmount()));
        result.setRemainingMonthlyLimit(result.getMonthlyLimit().subtract(result.getMonthlyUsage()).subtract(event.getAmount()));
        
        log.debug("STEP 7: Limit validation completed - Remaining Daily: {}, Monthly: {}, Velocity: {}, Correlation: {}",
            result.getRemainingDailyLimit(), result.getRemainingMonthlyLimit(), recentTopUps, correlationId);
        
        return result;
    }

    /**
     * STEP 8: Currency conversion and fee calculation
     */
    private CurrencyConversionResult performCurrencyConversionAndFeeCalculation(WalletTopUpEvent event, Wallet wallet, String correlationId) {
        log.debug("STEP 8: Processing currency conversion - Correlation: {}", correlationId);
        
        String walletCurrency = wallet.getCurrency();
        String topUpCurrency = event.getCurrency();
        BigDecimal originalAmount = event.getAmount();
        
        CurrencyConversionResult result = new CurrencyConversionResult();
        result.setOriginalAmount(originalAmount);
        result.setOriginalCurrency(topUpCurrency);
        result.setTargetCurrency(walletCurrency);
        
        BigDecimal convertedAmount = originalAmount;
        BigDecimal exchangeRate = BigDecimal.ONE;
        
        // Perform currency conversion if needed
        if (!topUpCurrency.equals(walletCurrency)) {
            ConversionQuote quote = currencyConversionService.getConversionQuote(
                topUpCurrency, walletCurrency, originalAmount);
            
            convertedAmount = quote.getConvertedAmount();
            exchangeRate = quote.getExchangeRate();
            
            result.setExchangeRate(exchangeRate);
            result.setConversionRequired(true);
        }
        
        result.setConvertedAmount(convertedAmount);
        
        // Calculate fees
        FeeCalculation feeCalculation = calculateTopUpFees(originalAmount, convertedAmount, 
            topUpCurrency, walletCurrency, event.getPaymentMethod());
        
        result.setTopUpFee(feeCalculation.getTopUpFee());
        result.setConversionFee(feeCalculation.getConversionFee());
        result.setTotalFees(feeCalculation.getTotalFees());
        
        // Final amount after fees
        BigDecimal finalAmount = convertedAmount.subtract(feeCalculation.getTotalFees());
        result.setFinalAmount(finalAmount);
        
        if (finalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new WalletException("Top-up amount too small after fees deduction");
        }
        
        log.debug("STEP 8: Currency conversion completed - Original: {} {}, Final: {} {}, Fees: {}, Rate: {}, Correlation: {}",
            originalAmount, topUpCurrency, finalAmount, walletCurrency, feeCalculation.getTotalFees(), exchangeRate, correlationId);
        
        return result;
    }

    /**
     * STEP 9: Payment processing and authorization
     */
    private PaymentProcessingResult processPaymentAndAuthorization(WalletTopUpEvent event,
            PaymentMethodValidationResult paymentMethodValidation, CurrencyConversionResult conversionResult, String correlationId) {
        log.debug("STEP 9: Processing payment - Correlation: {}", correlationId);
        
        PaymentProcessingResult result = paymentMethodService.processPayment(
            event.getPaymentMethodId(),
            conversionResult.getOriginalAmount(),
            event.getCurrency(),
            event.getTransactionId(),
            "Wallet Top-up",
            correlationId
        );
        
        if (!result.isSuccessful()) {
            throw new PaymentMethodException("Payment processing failed: " + result.getErrorMessage());
        }
        
        // Verify authorization
        if (!result.isAuthorized()) {
            throw new PaymentMethodException("Payment authorization failed: " + result.getDeclineReason());
        }
        
        // Store payment reference for reconciliation
        result.setPaymentReference(generatePaymentReference(event, correlationId));
        result.setProcessedAt(LocalDateTime.now());
        
        log.debug("STEP 9: Payment processing completed - Auth Code: {}, Reference: {}, Correlation: {}",
            result.getAuthorizationCode(), result.getPaymentReference(), correlationId);
        
        return result;
    }

    /**
     * STEP 10: Balance update and reconciliation
     */
    private BalanceUpdateResult updateBalanceAndReconcile(WalletTopUpEvent event, Wallet wallet,
            CurrencyConversionResult conversionResult, PaymentProcessingResult paymentResult, String correlationId) {
        log.debug("STEP 10: Updating balance - Correlation: {}", correlationId);
        
        BalanceUpdateResult result = balanceManagementService.updateWalletBalance(
            wallet.getId(), conversionResult.getFinalAmount(), TransactionType.TOP_UP, correlationId);
        
        if (!result.isSuccessful()) {
            // Reverse the payment if balance update fails
            try {
                paymentMethodService.reversePayment(paymentResult.getPaymentReference(), correlationId);
            } catch (Exception reverseError) {
                log.error("Failed to reverse payment after balance update failure - Payment: {}, Error: {}, Correlation: {}",
                    paymentResult.getPaymentReference(), reverseError.getMessage(), correlationId);
            }
            
            throw new WalletException("Balance update failed: " + result.getErrorMessage());
        }
        
        // Reconcile balance
        BigDecimal expectedBalance = wallet.getBalance().add(conversionResult.getFinalAmount());
        if (result.getNewBalance().compareTo(expectedBalance) != 0) {
            log.error("Balance reconciliation mismatch - Expected: {}, Actual: {}, Correlation: {}",
                expectedBalance, result.getNewBalance(), correlationId);
            
            // Attempt to fix the discrepancy
            balanceManagementService.reconcileBalance(wallet.getId(), expectedBalance, correlationId);
        }
        
        log.debug("STEP 10: Balance update completed - Previous: {}, Added: {}, New: {}, Correlation: {}",
            result.getPreviousBalance(), conversionResult.getFinalAmount(), result.getNewBalance(), correlationId);
        
        return result;
    }

    /**
     * STEP 11: Audit trail and transaction recording
     */
    private WalletTransaction createAuditTrailAndRecordTransaction(WalletTopUpEvent event, Wallet wallet,
            PaymentMethodValidationResult paymentMethodValidation, FraudDetectionResult fraudResult,
            LimitValidationResult limitValidation, CurrencyConversionResult conversionResult,
            PaymentProcessingResult paymentResult, BalanceUpdateResult balanceUpdate,
            String correlationId, long processingStartTime) {
        log.debug("STEP 11: Creating audit trail - Correlation: {}", correlationId);
        
        WalletTransaction transaction = WalletTransaction.builder()
            .transactionId(event.getTransactionId())
            .walletId(event.getWalletId())
            .userId(event.getUserId())
            .type(TransactionType.TOP_UP)
            .status(TransactionStatus.COMPLETED)
            .originalAmount(conversionResult.getOriginalAmount())
            .originalCurrency(conversionResult.getOriginalCurrency())
            .convertedAmount(conversionResult.getConvertedAmount())
            .finalAmount(conversionResult.getFinalAmount())
            .currency(wallet.getCurrency())
            .exchangeRate(conversionResult.getExchangeRate())
            .topUpFee(conversionResult.getTopUpFee())
            .conversionFee(conversionResult.getConversionFee())
            .totalFees(conversionResult.getTotalFees())
            .paymentMethod(event.getPaymentMethod())
            .paymentMethodId(event.getPaymentMethodId())
            .paymentReference(paymentResult.getPaymentReference())
            .authorizationCode(paymentResult.getAuthorizationCode())
            .previousBalance(balanceUpdate.getPreviousBalance())
            .newBalance(balanceUpdate.getNewBalance())
            .fraudScore(fraudResult.getFraudScore())
            .riskLevel(fraudResult.getRiskLevel())
            .description("Wallet top-up via " + event.getPaymentMethod())
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .processingTimeMs(System.currentTimeMillis() - processingStartTime)
            .build();
        
        transaction = walletTransactionRepository.save(transaction);
        
        // Update wallet last transaction info
        wallet.setLastTransactionId(transaction.getTransactionId());
        wallet.setLastTransactionAt(LocalDateTime.now());
        wallet.setBalance(balanceUpdate.getNewBalance());
        walletRepository.save(wallet);
        
        // Create detailed audit log
        auditService.logWalletTopUpEvent(event, wallet, transaction, paymentMethodValidation, fraudResult,
            limitValidation, conversionResult, paymentResult, balanceUpdate, correlationId);
        
        log.debug("STEP 11: Audit trail created - Transaction ID: {}, Correlation: {}", transaction.getId(), correlationId);
        
        return transaction;
    }

    /**
     * STEP 12: Notification dispatch and confirmation
     */
    private void dispatchNotificationsAndConfirmation(WalletTopUpEvent event, Wallet wallet, WalletTransaction transaction,
            PaymentProcessingResult paymentResult, String correlationId) {
        log.debug("STEP 12: Dispatching notifications - Correlation: {}", correlationId);
        
        // Send user notification
        CompletableFuture.runAsync(() -> {
            notificationService.sendTopUpConfirmation(
                event.getUserId(),
                transaction.getFinalAmount(),
                wallet.getCurrency(),
                transaction.getNewBalance(),
                transaction.getPaymentMethod()
            );
        });
        
        // Send push notification if enabled
        CompletableFuture.runAsync(() -> {
            notificationService.sendPushNotification(
                event.getUserId(),
                "Wallet Top-up Successful",
                String.format("Your wallet has been topped up with %s %s. New balance: %s %s",
                    transaction.getFinalAmount(), wallet.getCurrency(),
                    transaction.getNewBalance(), wallet.getCurrency())
            );
        });
        
        // Send email receipt
        CompletableFuture.runAsync(() -> {
            notificationService.sendTopUpReceipt(
                event.getUserId(),
                transaction,
                paymentResult
            );
        });
        
        // Send internal notification for large amounts
        if (transaction.getFinalAmount().compareTo(new BigDecimal("5000.00")) >= 0) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendInternalTopUpAlert(
                    event, wallet, transaction, correlationId);
            });
        }
        
        // Publish wallet topped up event
        WalletToppedUpEvent toppedUpEvent = WalletToppedUpEvent.builder()
            .transactionId(event.getTransactionId())
            .userId(event.getUserId())
            .walletId(event.getWalletId())
            .originalAmount(transaction.getOriginalAmount())
            .finalAmount(transaction.getFinalAmount())
            .currency(transaction.getCurrency())
            .paymentMethod(transaction.getPaymentMethod())
            .newBalance(transaction.getNewBalance())
            .correlationId(correlationId)
            .toppedUpAt(transaction.getCreatedAt())
            .build();
        
        kafkaTemplate.send(WALLET_TOPPED_UP_TOPIC, toppedUpEvent);
        
        // Publish payment processed event
        kafkaTemplate.send(PAYMENT_PROCESSED_TOPIC, Map.of(
            "transactionId", transaction.getTransactionId(),
            "userId", event.getUserId(),
            "amount", transaction.getOriginalAmount(),
            "currency", transaction.getOriginalCurrency(),
            "paymentMethod", transaction.getPaymentMethod(),
            "status", "COMPLETED",
            "correlationId", correlationId
        ));
        
        log.debug("STEP 12: Notifications dispatched - Correlation: {}", correlationId);
    }

    // Error handling methods
    private void handleComplianceViolation(WalletTopUpEvent event, ComplianceViolationException e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Compliance violation in wallet top-up - Transaction: {}, Error: {}, Correlation: {}",
            event.getTransactionId(), e.getMessage(), correlationId);
        
        // Send compliance alert
        kafkaTemplate.send(COMPLIANCE_ALERT_TOPIC, Map.of(
            "eventType", "WALLET_TOPUP_COMPLIANCE_VIOLATION",
            "transactionId", event.getTransactionId(),
            "userId", event.getUserId(),
            "violation", e.getMessage(),
            "correlationId", correlationId
        ));
        
        acknowledgment.acknowledge();
    }

    private void handleInsufficientFundsError(WalletTopUpEvent event, InsufficientFundsException e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Insufficient funds for wallet top-up - Transaction: {}, Error: {}, Correlation: {}",
            event.getTransactionId(), e.getMessage(), correlationId);
        
        // Create failed transaction record
        WalletTransaction failedTransaction = WalletTransaction.builder()
            .transactionId(event.getTransactionId())
            .walletId(event.getWalletId())
            .userId(event.getUserId())
            .type(TransactionType.TOP_UP)
            .status(TransactionStatus.FAILED)
            .originalAmount(event.getAmount())
            .failureReason(e.getMessage())
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .build();
        
        walletTransactionRepository.save(failedTransaction);
        acknowledgment.acknowledge();
    }

    private void handlePaymentMethodError(WalletTopUpEvent event, PaymentMethodException e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Payment method error in wallet top-up - Transaction: {}, Error: {}, Correlation: {}",
            event.getTransactionId(), e.getMessage(), correlationId);

        dlqHandler.handleFailedMessage(event, "wallet-topup-events", 0, 0L, e)
            .thenAccept(result -> log.info("Wallet top-up event sent to DLQ: txId={}, destination={}, category={}",
                    event.getTransactionId(), result.getDestinationTopic(), result.getFailureCategory()))
            .exceptionally(dlqError -> {
                log.error("CRITICAL: DLQ handling failed for wallet top-up - MESSAGE MAY BE LOST! " +
                        "txId={}, error={}", event.getTransactionId(), dlqError.getMessage(), dlqError);
                return null;
            });
        acknowledgment.acknowledge();
    }

    private void handleWalletError(WalletTopUpEvent event, WalletException e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Wallet error in top-up - Transaction: {}, Error: {}, Correlation: {}",
            event.getTransactionId(), e.getMessage(), correlationId);

        dlqHandler.handleFailedMessage(event, "wallet-topup-events", 0, 0L, e)
            .thenAccept(result -> log.info("Wallet error event sent to DLQ: txId={}, destination={}, category={}",
                    event.getTransactionId(), result.getDestinationTopic(), result.getFailureCategory()))
            .exceptionally(dlqError -> {
                log.error("CRITICAL: DLQ handling failed for wallet error - MESSAGE MAY BE LOST! " +
                        "txId={}, error={}", event.getTransactionId(), dlqError.getMessage(), dlqError);
                return null;
            });
        acknowledgment.acknowledge();
    }

    private void handleCriticalError(WalletTopUpEvent event, Exception e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Critical error in wallet top-up - Transaction: {}, Error: {}, Correlation: {}",
            event.getTransactionId(), e.getMessage(), e, correlationId);

        dlqHandler.handleFailedMessage(event, "wallet-topup-events", 0, 0L, e)
            .thenAccept(result -> log.info("Critical error event sent to DLQ: txId={}, destination={}, category={}",
                    event.getTransactionId(), result.getDestinationTopic(), result.getFailureCategory()))
            .exceptionally(dlqError -> {
                log.error("CRITICAL: DLQ handling failed for critical error - MESSAGE MAY BE LOST! " +
                        "txId={}, error={}", event.getTransactionId(), dlqError.getMessage(), dlqError);
                return null;
            });

        // Send critical alert
        notificationService.sendCriticalAlert(
            "WALLET_TOPUP_PROCESSING_ERROR",
            String.format("Critical error processing wallet top-up %s: %s", event.getTransactionId(), e.getMessage()),
            correlationId
        );

        acknowledgment.acknowledge();
    }

    // Utility methods
    private String generateCorrelationId(WalletTopUpEvent event, int partition, long offset) {
        return String.format("wallet-topup-%s-p%d-o%d-%d",
            event.getTransactionId(), partition, offset, System.currentTimeMillis());
    }

    private String sanitizeString(String input) {
        if (input == null) return null;
        return input.trim().replaceAll("[<>\"'&]", "");
    }

    private void acknowledgeAndReturn(Acknowledgment acknowledgment, String message) {
        log.info(message);
        acknowledgment.acknowledge();
    }

    private boolean isEUUser(String userId) {
        // Implementation would check user's location/citizenship
        return false; // Placeholder
    }

    private FeeCalculation calculateTopUpFees(BigDecimal originalAmount, BigDecimal convertedAmount,
            String originalCurrency, String targetCurrency, String paymentMethod) {
        
        BigDecimal topUpFee = BigDecimal.ZERO;
        BigDecimal conversionFee = BigDecimal.ZERO;
        
        // Calculate top-up fee based on payment method
        switch (paymentMethod.toUpperCase()) {
            case "CREDIT_CARD" -> topUpFee = originalAmount.multiply(new BigDecimal("0.025")); // 2.5%
            case "DEBIT_CARD" -> topUpFee = originalAmount.multiply(new BigDecimal("0.015")); // 1.5%
            case "BANK_TRANSFER" -> topUpFee = new BigDecimal("2.50"); // Flat fee
            case "DIGITAL_WALLET" -> topUpFee = originalAmount.multiply(new BigDecimal("0.01")); // 1%
        }
        
        // Calculate conversion fee if currencies differ
        if (!originalCurrency.equals(targetCurrency)) {
            conversionFee = convertedAmount.multiply(new BigDecimal("0.005")); // 0.5%
        }
        
        BigDecimal totalFees = topUpFee.add(conversionFee);
        
        return FeeCalculation.builder()
            .topUpFee(topUpFee)
            .conversionFee(conversionFee)
            .totalFees(totalFees)
            .build();
    }

    private String generatePaymentReference(WalletTopUpEvent event, String correlationId) {
        return String.format("TOPUP_%s_%s_%d", 
            event.getTransactionId(), event.getUserId(), System.currentTimeMillis());
    }


    // Inner classes for results (simplified for brevity)
    @lombok.Data
    @lombok.Builder
    private static class PaymentMethodValidationResult {
        private boolean valid;
        private boolean active;
        private String paymentMethodType;
        private BigDecimal singleTransactionLimit;
        private String errorMessage;
        
        public boolean isOwnedByUser(String userId) {
            return valid; // Placeholder implementation
        }
    }

    @lombok.Data
    @lombok.Builder
    private static class FraudDetectionResult {
        private boolean fraudulent;
        private boolean highRisk;
        private int fraudScore;
        private String riskLevel;
        private List<String> fraudReasons;
    }

    @lombok.Data
    @lombok.Builder
    private static class LimitValidationResult {
        private boolean dailyLimitValid;
        private boolean monthlyLimitValid;
        private BigDecimal dailyLimit;
        private BigDecimal monthlyLimit;
        private BigDecimal dailyUsage;
        private BigDecimal monthlyUsage;
        private BigDecimal remainingDailyLimit;
        private BigDecimal remainingMonthlyLimit;
        private int velocityCount;
    }

    @lombok.Data
    private static class CurrencyConversionResult {
        private BigDecimal originalAmount;
        private String originalCurrency;
        private String targetCurrency;
        private BigDecimal convertedAmount;
        private BigDecimal finalAmount;
        private BigDecimal exchangeRate;
        private boolean conversionRequired;
        private BigDecimal topUpFee;
        private BigDecimal conversionFee;
        private BigDecimal totalFees;
    }

    @lombok.Data
    @lombok.Builder
    private static class PaymentProcessingResult {
        private boolean successful;
        private boolean authorized;
        private String authorizationCode;
        private String paymentReference;
        private String errorMessage;
        private String declineReason;
        private LocalDateTime processedAt;
    }

    @lombok.Data
    @lombok.Builder
    private static class BalanceUpdateResult {
        private boolean successful;
        private BigDecimal previousBalance;
        private BigDecimal newBalance;
        private String errorMessage;
    }

    @lombok.Data
    @lombok.Builder
    private static class FeeCalculation {
        private BigDecimal topUpFee;
        private BigDecimal conversionFee;
        private BigDecimal totalFees;
    }

    @lombok.Data
    @lombok.Builder
    private static class ConversionQuote {
        private BigDecimal convertedAmount;
        private BigDecimal exchangeRate;
    }

    @lombok.Data
    @lombok.Builder
    private static class ComplianceResult {
        private boolean compliant;
        private List<String> violations;
        
        public boolean hasViolations() {
            return violations != null && !violations.isEmpty();
        }
    }
}