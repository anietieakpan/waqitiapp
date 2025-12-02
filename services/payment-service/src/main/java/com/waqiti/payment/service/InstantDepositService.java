package com.waqiti.payment.service;

// Import client classes
import com.waqiti.payment.client.CardNetworkClient;
import com.waqiti.payment.client.CardNetworkClient.*;
import com.waqiti.payment.client.FraudDetectionClient;
import com.waqiti.payment.client.UnifiedWalletServiceClient;
import com.waqiti.payment.dto.*;
import com.waqiti.payment.entity.*;
import com.waqiti.payment.exception.*;
import com.waqiti.payment.repository.ACHTransferRepository;
import com.waqiti.payment.repository.InstantDepositRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for processing instant deposits with fee calculation
 * Allows users to receive ACH deposits immediately for a fee
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InstantDepositService {
    
    private final ACHTransferRepository achTransferRepository;
    private final InstantDepositRepository instantDepositRepository;
    private final CardNetworkClient cardNetworkClient;
    private final FraudDetectionClient fraudDetectionClient;
    private final UnifiedWalletServiceClient walletServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    
    @Value("${instant-deposit.fee-percentage:0.015}")
    private BigDecimal feePercentage; // Default 1.5%
    
    @Value("${instant-deposit.minimum-fee:0.25}")
    private BigDecimal minimumFee; // Default $0.25
    
    @Value("${instant-deposit.maximum-fee:15.00}")
    private BigDecimal maximumFee; // Default $15.00
    
    @Value("${instant-deposit.minimum-amount:1.00}")
    private BigDecimal minimumAmount; // Minimum deposit amount eligible
    
    @Value("${instant-deposit.maximum-amount:5000.00}")
    private BigDecimal maximumAmount; // Maximum per instant deposit
    
    @Value("${instant-deposit.daily-limit:10000.00}")
    private BigDecimal dailyLimit; // Daily limit per user
    
    @Value("${instant-deposit.processing-enabled:true}")
    private boolean processingEnabled;
    
    private static final String INSTANT_DEPOSIT_TOPIC = "instant-deposits";
    private static final String METRICS_PREFIX = "instant_deposit";
    
    /**
     * Calculate fee for instant deposit
     */
    public InstantDepositFeeResponse calculateInstantDepositFee(UUID achTransferId) {
        log.info("Calculating instant deposit fee for ACH transfer: {}", achTransferId);
        
        ACHTransfer achTransfer = achTransferRepository.findById(achTransferId)
            .orElseThrow(() -> new ValidationException("ACH transfer not found"));
            
        // Validate eligibility
        validateEligibility(achTransfer);
        
        // Calculate fee
        BigDecimal fee = calculateFee(achTransfer.getAmount());
        BigDecimal netAmount = achTransfer.getAmount().subtract(fee);
        
        // Estimate completion time (usually 30 minutes or less)
        LocalDateTime estimatedCompletion = LocalDateTime.now().plusMinutes(30);
        
        return InstantDepositFeeResponse.builder()
            .achTransferId(achTransferId)
            .originalAmount(achTransfer.getAmount())
            .feeAmount(fee)
            .feePercentage(feePercentage.multiply(BigDecimal.valueOf(100)))
            .netAmount(netAmount)
            .estimatedCompletionTime(estimatedCompletion)
            .eligible(true)
            .build();
    }
    
    /**
     * Process instant deposit request
     */
    @Transactional
    @CircuitBreaker(name = "instant-deposit", fallbackMethod = "handleInstantDepositFallback")
    @Retry(name = "instant-deposit")
    public CompletableFuture<InstantDepositResponse> processInstantDeposit(
            InstantDepositRequest request) {
        
        log.info("Processing instant deposit for ACH transfer: {}", request.getAchTransferId());
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate request
                ACHTransfer achTransfer = validateAndGetACHTransfer(request.getAchTransferId());
                
                // Check if already processed
                checkForDuplicateRequest(achTransfer);
                
                // Validate user acceptance
                if (!request.isAcceptedFee()) {
                    throw new ValidationException("User must accept the fee to proceed");
                }
                
                // Calculate fee again for security
                BigDecimal fee = calculateFee(achTransfer.getAmount());
                BigDecimal netAmount = achTransfer.getAmount().subtract(fee);
                
                // Check daily limits
                checkDailyLimits(achTransfer.getUserId(), achTransfer.getAmount());
                
                // Enhanced fraud detection for instant deposits
                performEnhancedFraudCheck(achTransfer, request);
                
                // Create instant deposit record
                InstantDeposit instantDeposit = createInstantDeposit(
                    achTransfer, fee, netAmount, request);
                
                // Process through card networks
                processCardNetworkTransfer(instantDeposit);
                
                // Credit wallet immediately
                creditWallet(instantDeposit);
                
                // Update ACH transfer status
                updateACHTransferStatus(achTransfer);
                
                // Send notifications
                sendNotifications(instantDeposit);
                
                // Record metrics
                recordSuccessMetrics(instantDeposit);

                meterRegistry.timer(METRICS_PREFIX + ".processing.duration").stop(sample);

                return buildSuccessResponse(instantDeposit);
                
            } catch (Exception e) {
                meterRegistry.timer(METRICS_PREFIX + ".processing.duration",
                    "status", "error").stop(sample);
                recordErrorMetrics(e);
                throw e;
            }
        });
    }
    
    /**
     * Calculate fee based on amount and business rules
     */
    private BigDecimal calculateFee(BigDecimal amount) {
        // Calculate percentage-based fee
        BigDecimal calculatedFee = amount.multiply(feePercentage)
            .setScale(2, RoundingMode.UP);
        
        // Apply minimum fee
        if (calculatedFee.compareTo(minimumFee) < 0) {
            calculatedFee = minimumFee;
        }
        
        // Apply maximum fee cap
        if (calculatedFee.compareTo(maximumFee) > 0) {
            calculatedFee = maximumFee;
        }
        
        return calculatedFee;
    }
    
    /**
     * Validate ACH transfer eligibility for instant deposit
     */
    private void validateEligibility(ACHTransfer achTransfer) {
        // Check if processing is enabled
        if (!processingEnabled) {
            throw new ServiceUnavailableException("Instant deposit service is temporarily unavailable");
        }
        
        // Must be a deposit (not withdrawal)
        if (achTransfer.getDirection() != TransferDirection.DEPOSIT) {
            throw new ValidationException("Only deposits are eligible for instant processing");
        }
        
        // Must be in PROCESSING status
        if (achTransfer.getStatus() != ACHTransferStatus.PROCESSING) {
            throw new ValidationException("ACH transfer must be in processing status");
        }
        
        // Check amount limits
        if (achTransfer.getAmount().compareTo(minimumAmount) < 0) {
            throw new ValidationException(
                "Amount must be at least $" + minimumAmount + " for instant deposit");
        }
        
        if (achTransfer.getAmount().compareTo(maximumAmount) > 0) {
            throw new ValidationException(
                "Amount exceeds maximum instant deposit limit of $" + maximumAmount);
        }
        
        // Check if user has a verified debit card on file
        if (!hasVerifiedDebitCard(achTransfer.getUserId())) {
            throw new ValidationException(
                "A verified debit card is required for instant deposits");
        }
    }
    
    /**
     * Validate and retrieve ACH transfer
     */
    private ACHTransfer validateAndGetACHTransfer(UUID achTransferId) {
        ACHTransfer achTransfer = achTransferRepository.findById(achTransferId)
            .orElseThrow(() -> new ValidationException("ACH transfer not found"));
            
        validateEligibility(achTransfer);
        return achTransfer;
    }
    
    /**
     * Check for duplicate instant deposit request
     */
    private void checkForDuplicateRequest(ACHTransfer achTransfer) {
        boolean exists = instantDepositRepository
            .existsByAchTransferId(achTransfer.getId());
            
        if (exists) {
            throw new DuplicateRequestException(
                "Instant deposit already processed for this transfer");
        }
    }
    
    /**
     * Check daily instant deposit limits
     */
    private void checkDailyLimits(UUID userId, BigDecimal amount) {
        BigDecimal dailyTotal = getDailyInstantDepositTotal(userId);
        
        if (dailyTotal.add(amount).compareTo(dailyLimit) > 0) {
            BigDecimal remaining = dailyLimit.subtract(dailyTotal);
            throw new LimitExceededException(String.format(
                "Daily instant deposit limit exceeded. Limit: $%s, Remaining: $%s",
                dailyLimit, remaining.max(BigDecimal.ZERO)
            ));
        }
    }
    
    /**
     * Get daily instant deposit total for user
     */
    public BigDecimal getDailyInstantDepositTotal(UUID userId) {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        
        return instantDepositRepository
            .sumDailyInstantDeposits(userId, startOfDay)
            .orElse(BigDecimal.ZERO);
    }
    
    /**
     * Perform enhanced fraud check for instant deposits
     */
    private void performEnhancedFraudCheck(ACHTransfer achTransfer, 
                                          InstantDepositRequest request) {
        log.info("Performing enhanced fraud check for instant deposit");
        
        FraudCheckRequest fraudRequest = FraudCheckRequest.builder()
            .userId(achTransfer.getUserId())
            .transactionId(achTransfer.getId())
            .amount(achTransfer.getAmount())
            .transactionType("INSTANT_DEPOSIT")
            .deviceId(request.getDeviceId())
            .ipAddress(request.getIpAddress())
            .userAgent(request.getUserAgent())
            .build();
            
        FraudCheckResponse fraudResponse = fraudDetectionClient
            .performEnhancedCheck(fraudRequest);
            
        if (fraudResponse.isHighRisk()) {
            log.warn("High fraud risk detected for instant deposit: {}", 
                achTransfer.getId());
            throw new FraudDetectedException(
                "Transaction flagged for review. Please try again later.");
        }
        
        if (fraudResponse.requiresAdditionalVerification()) {
            throw new AdditionalVerificationRequiredException(
                "Additional verification required for this transaction");
        }
    }
    
    /**
     * Create instant deposit record
     */
    private InstantDeposit createInstantDeposit(ACHTransfer achTransfer, 
                                               BigDecimal fee, 
                                               BigDecimal netAmount,
                                               InstantDepositRequest request) {
        InstantDeposit instantDeposit = new InstantDeposit();
        instantDeposit.setAchTransferId(achTransfer.getId());
        instantDeposit.setUserId(achTransfer.getUserId());
        instantDeposit.setWalletId(achTransfer.getWalletId());
        instantDeposit.setOriginalAmount(achTransfer.getAmount());
        instantDeposit.setFeeAmount(fee);
        instantDeposit.setNetAmount(netAmount);
        instantDeposit.setStatus(InstantDepositStatus.PENDING);
        instantDeposit.setDebitCardId(request.getDebitCardId());
        instantDeposit.setDeviceId(request.getDeviceId());
        instantDeposit.setIpAddress(request.getIpAddress());
        instantDeposit.setCreatedAt(LocalDateTime.now());
        instantDeposit.setUpdatedAt(LocalDateTime.now());
        
        return instantDepositRepository.save(instantDeposit);
    }
    
    /**
     * Process transfer through card networks (Visa Direct/Mastercard Send)
     */
    private void processCardNetworkTransfer(InstantDeposit instantDeposit) {
        log.info("Processing instant deposit through card networks: {}", 
            instantDeposit.getId());
            
        try {
            instantDeposit.setStatus(InstantDepositStatus.PROCESSING);
            instantDeposit.setProcessingStartedAt(LocalDateTime.now());
            instantDepositRepository.save(instantDeposit);
            
            // Get user's debit card details
            DebitCardDetails cardDetails = getDebitCardDetails(
                instantDeposit.getUserId(), 
                instantDeposit.getDebitCardId()
            );
            
            // Process through appropriate network
            CardNetworkResponse response;
            if (cardDetails.isVisa()) {
                response = cardNetworkClient.processVisaDirect(
                    buildVisaDirectRequest(instantDeposit, cardDetails)
                );
            } else if (cardDetails.isMastercard()) {
                response = cardNetworkClient.processMastercardSend(
                    buildMastercardSendRequest(instantDeposit, cardDetails)
                );
            } else {
                throw new UnsupportedCardNetworkException(
                    "Card network not supported for instant deposits");
            }
            
            if (!response.isSuccessful()) {
                throw new CardNetworkException(
                    "Card network processing failed: " + response.getErrorMessage());
            }
            
            instantDeposit.setNetworkReferenceId(response.getReferenceId());
            instantDeposit.setNetworkResponseCode(response.getResponseCode());
            instantDeposit.setStatus(InstantDepositStatus.COMPLETED);
            instantDeposit.setCompletedAt(LocalDateTime.now());
            instantDepositRepository.save(instantDeposit);
            
        } catch (Exception e) {
            instantDeposit.setStatus(InstantDepositStatus.FAILED);
            instantDeposit.setFailureReason(e.getMessage());
            instantDeposit.setFailedAt(LocalDateTime.now());
            instantDepositRepository.save(instantDeposit);
            throw new InstantDepositException("Card network processing failed", e);
        }
    }
    
    /**
     * Credit user's wallet with instant deposit amount
     */
    private void creditWallet(InstantDeposit instantDeposit) {
        log.info("Crediting wallet for instant deposit: {}", instantDeposit.getId());
        
        WalletCreditRequest creditRequest = WalletCreditRequest.builder()
            .walletId(instantDeposit.getWalletId())
            .amount(instantDeposit.getNetAmount())
            .transactionType("INSTANT_DEPOSIT")
            .referenceId(instantDeposit.getId().toString())
            .description("Instant Deposit (Fee: $" + instantDeposit.getFeeAmount() + ")")
            .metadata(Map.of(
                "achTransferId", instantDeposit.getAchTransferId().toString(),
                "feeAmount", instantDeposit.getFeeAmount().toString(),
                "originalAmount", instantDeposit.getOriginalAmount().toString()
            ))
            .build();
            
        walletServiceClient.creditWallet(creditRequest);
    }
    
    /**
     * Update ACH transfer to reflect instant deposit
     */
    private void updateACHTransferStatus(ACHTransfer achTransfer) {
        achTransfer.setInstantDepositProcessed(true);
        achTransfer.setInstantDepositAt(LocalDateTime.now());
        achTransfer.setUpdatedAt(LocalDateTime.now());
        achTransferRepository.save(achTransfer);
    }
    
    /**
     * Send notifications about instant deposit
     */
    private void sendNotifications(InstantDeposit instantDeposit) {
        InstantDepositEvent event = InstantDepositEvent.builder()
            .instantDepositId(instantDeposit.getId())
            .userId(instantDeposit.getUserId())
            .status(instantDeposit.getStatus())
            .originalAmount(instantDeposit.getOriginalAmount())
            .feeAmount(instantDeposit.getFeeAmount())
            .netAmount(instantDeposit.getNetAmount())
            .timestamp(java.time.Instant.now())
            .build();
            
        kafkaTemplate.send(INSTANT_DEPOSIT_TOPIC, event);
        
        // Send push notification
        NotificationRequest notification = NotificationRequest.builder()
            .userId(instantDeposit.getUserId())
            .type("INSTANT_DEPOSIT_COMPLETED")
            .title("Instant Deposit Complete")
            .message(String.format(
                "Your instant deposit of $%.2f is now available (Fee: $%.2f)",
                instantDeposit.getNetAmount(), instantDeposit.getFeeAmount()
            ))
            .data(Map.of(
                "instantDepositId", instantDeposit.getId().toString(),
                "amount", instantDeposit.getNetAmount().toString()
            ))
            .build();
            
        kafkaTemplate.send("notifications", notification);
    }
    
    /**
     * Check if user has verified debit card
     */
    private boolean hasVerifiedDebitCard(UUID userId) {
        // This would check with a card management service
        // For now, return true for demo
        return true;
    }
    
    /**
     * Get debit card details
     */
    private DebitCardDetails getDebitCardDetails(UUID userId, UUID debitCardId) {
        // This would fetch from card management service
        // For now, return mock data
        return DebitCardDetails.builder()
            .cardId(debitCardId)
            .userId(userId)
            .lastFour("1234")
            .network("VISA")
            .isVisa(true)
            .isMastercard(false)
            .build();
    }
    
    /**
     * Build Visa Direct request
     */
    private VisaDirectRequest buildVisaDirectRequest(InstantDeposit instantDeposit, 
                                                     DebitCardDetails cardDetails) {
        return VisaDirectRequest.builder()
            .transactionId(instantDeposit.getId().toString())
            .cardNumber(cardDetails.getCardNumber())
            .amount(instantDeposit.getNetAmount())
            .currency("USD")
            .merchantCategoryCode("6012") // Financial institutions
            .acquirerCountryCode("840") // USA
            .build();
    }
    
    /**
     * Build Mastercard Send request
     */
    private MastercardSendRequest buildMastercardSendRequest(InstantDeposit instantDeposit,
                                                            DebitCardDetails cardDetails) {
        return MastercardSendRequest.builder()
            .transactionReference(instantDeposit.getId().toString())
            .fundingCard(cardDetails.getCardNumber())
            .amount(instantDeposit.getNetAmount())
            .currency("USD")
            .transactionType("PAYMENT")
            .build();
    }
    
    /**
     * Build success response
     */
    private InstantDepositResponse buildSuccessResponse(InstantDeposit instantDeposit) {
        return InstantDepositResponse.builder()
            .instantDepositId(instantDeposit.getId())
            .achTransferId(instantDeposit.getAchTransferId())
            .status(instantDeposit.getStatus())
            .originalAmount(instantDeposit.getOriginalAmount())
            .feeAmount(instantDeposit.getFeeAmount())
            .netAmount(instantDeposit.getNetAmount())
            .completedAt(instantDeposit.getCompletedAt())
            .message("Instant deposit completed successfully")
            .build();
    }
    
    /**
     * Record success metrics
     */
    private void recordSuccessMetrics(InstantDeposit instantDeposit) {
        meterRegistry.counter(METRICS_PREFIX + ".completed").increment();
        meterRegistry.summary(METRICS_PREFIX + ".fee.amount")
            .record(instantDeposit.getFeeAmount().doubleValue());
        meterRegistry.summary(METRICS_PREFIX + ".net.amount")
            .record(instantDeposit.getNetAmount().doubleValue());
    }
    
    /**
     * Record error metrics
     */
    private void recordErrorMetrics(Exception e) {
        meterRegistry.counter(METRICS_PREFIX + ".error", 
            "type", e.getClass().getSimpleName()).increment();
    }
    
    /**
     * Fallback method for circuit breaker
     */
    private CompletableFuture<InstantDepositResponse> handleInstantDepositFallback(
            InstantDepositRequest request, Exception ex) {
        log.error("Instant deposit fallback triggered", ex);
        
        return CompletableFuture.completedFuture(
            InstantDepositResponse.builder()
                .status(InstantDepositStatus.FAILED)
                .message("Instant deposit service is temporarily unavailable. Please try again later.")
                .build()
        );
    }
    
    /**
     * Get instant deposit by ID
     */
    public InstantDepositResponse getInstantDepositById(UUID instantDepositId) {
        log.info("Retrieving instant deposit details for ID: {}", instantDepositId);
        
        InstantDeposit instantDeposit = instantDepositRepository.findById(instantDepositId)
            .orElseThrow(() -> new RuntimeException("Instant deposit not found"));
        
        return buildSuccessResponse(instantDeposit);
    }
    
    /**
     * Get instant deposit history for user
     */
    public List<InstantDeposit> getUserInstantDepositHistory(UUID userId, int limit) {
        return instantDepositRepository.findByUserIdOrderByCreatedAtDesc(userId, 
            org.springframework.data.domain.PageRequest.of(0, limit));
    }
    
    /**
     * Calculate total fees collected
     */
    public BigDecimal getTotalFeesCollected(LocalDateTime startDate, LocalDateTime endDate) {
        return instantDepositRepository.sumFeesCollected(startDate, endDate)
            .orElse(BigDecimal.ZERO);
    }
}