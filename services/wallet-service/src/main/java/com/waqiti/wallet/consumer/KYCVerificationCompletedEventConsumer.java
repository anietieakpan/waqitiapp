package com.waqiti.wallet.consumer;

import com.waqiti.common.events.KYCVerificationCompletedEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.idempotency.RedisIdempotencyService;
import com.waqiti.wallet.service.WalletLimitService;
import com.waqiti.wallet.service.WalletUnlockService;
import com.waqiti.wallet.service.WalletFeatureService;
import com.waqiti.wallet.repository.ProcessedEventRepository;
import com.waqiti.wallet.model.ProcessedEvent;
import com.waqiti.wallet.model.WalletLimits;
import com.waqiti.wallet.model.Wallet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Consumer for KYCVerificationCompletedEvent - Critical for wallet unlocking
 * Unlocks wallet features after successful KYC verification
 * CRITICAL: Users cannot access full features without KYC completion
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KYCVerificationCompletedEventConsumer {

    private final WalletLimitService walletLimitService;
    private final WalletUnlockService walletUnlockService;
    private final WalletFeatureService walletFeatureService;
    private final ProcessedEventRepository processedEventRepository;
    private final UniversalDLQHandler dlqHandler;
    private final RedisIdempotencyService idempotencyService;
    
    @KafkaListener(
        topics = "kyc.verification.completed",
        groupId = "wallet-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void handleKYCVerificationCompleted(
            @Payload KYCVerificationCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        // Build idempotency key
        String idempotencyKey = idempotencyService.buildIdempotencyKey(
            "wallet-service",
            "KYCVerificationCompletedEvent",
            event.getEventId()
        );

        log.info("Processing KYC completion for user wallet: {}", event.getUserId());

        // Universal idempotency check (30-day TTL for financial operations)
        if (idempotencyService.isProcessed(idempotencyKey)) {
            log.info("⏭️ KYC verification already processed for event: {}", event.getEventId());
            acknowledgment.acknowledge();
            return;
        }
        
        try {
            // Get user's primary wallet
            Wallet primaryWallet = walletService.getPrimaryWallet(event.getUserId());
            
            if (primaryWallet == null) {
                log.error("No primary wallet found for user: {}", event.getUserId());
                throw new RuntimeException("Primary wallet not found for user: " + event.getUserId());
            }
            
            // Process based on KYC verification result
            if (event.isVerificationSuccessful()) {
                processSuccessfulKYCVerification(event, primaryWallet);
            } else {
                processFailedKYCVerification(event, primaryWallet);
            }
            
            // Record event processing
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("KYCVerificationCompletedEvent")
                .processedAt(Instant.now())
                .userId(event.getUserId())
                .walletId(primaryWallet.getId())
                .verificationResult(event.isVerificationSuccessful() ? "SUCCESS" : "FAILED")
                .build();
                
            processedEventRepository.save(processedEvent);

            // Mark as processed (30-day TTL for financial operations)
            idempotencyService.markFinancialOperationProcessed(idempotencyKey);

            log.info("Successfully processed KYC verification for user: {} with result: {}",
                event.getUserId(), event.isVerificationSuccessful());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process KYC verification for event: {}, userId={}, topic={}, partition={}, offset={}, error={}",
                event.getEventId(), event.getUserId(), topic, partition, offset, e.getMessage(), e);

            dlqHandler.handleFailedMessage(event, topic, partition, offset, e)
                .thenAccept(result -> log.info("KYC verification event sent to DLQ: eventId={}, userId={}, destination={}, category={}",
                        event.getEventId(), event.getUserId(), result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for KYC verification event - MESSAGE MAY BE LOST! " +
                            "eventId={}, userId={}, partition={}, offset={}, error={}",
                            event.getEventId(), event.getUserId(), partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("KYC verification processing failed", e);
        }
    }
    
    private void processSuccessfulKYCVerification(KYCVerificationCompletedEvent event, Wallet wallet) {
        log.info("Processing successful KYC verification for wallet: {}", wallet.getId());
        
        // STEP 1: Unlock wallet if it was restricted
        if (wallet.getStatus() == WalletStatus.KYC_PENDING || 
            wallet.getStatus() == WalletStatus.RESTRICTED) {
            
            walletUnlockService.unlockWallet(wallet.getId(), "KYC verification completed successfully");
            log.info("Wallet {} unlocked after successful KYC", wallet.getId());
        }
        
        // STEP 2: Update transaction limits based on KYC level
        WalletLimits newLimits = calculateLimitsForKYCLevel(event.getKycLevel());
        walletLimitService.updateWalletLimits(wallet.getId(), newLimits);
        log.info("Updated wallet limits for KYC level: {}", event.getKycLevel());
        
        // STEP 3: Enable features based on KYC level
        enableFeaturesForKYCLevel(wallet.getId(), event.getKycLevel());
        
        // STEP 4: Send welcome notification for verified users
        notificationService.sendKYCCompletionNotification(
            event.getUserId(),
            "KYC Verification Successful",
            "Your identity verification is complete. You can now access all wallet features.",
            newLimits
        );
        
        // STEP 5: Update user profile with KYC status
        userProfileService.updateKYCStatus(
            event.getUserId(),
            event.getKycLevel(),
            event.getVerificationDate()
        );
    }
    
    private void processFailedKYCVerification(KYCVerificationCompletedEvent event, Wallet wallet) {
        log.warn("Processing failed KYC verification for wallet: {}", wallet.getId());
        
        // STEP 1: Apply restricted limits
        WalletLimits restrictedLimits = WalletLimits.builder()
            .dailySpendLimit(new BigDecimal("500"))
            .monthlySpendLimit(new BigDecimal("2000"))
            .singleTransactionLimit(new BigDecimal("100"))
            .build();
            
        walletLimitService.updateWalletLimits(wallet.getId(), restrictedLimits);
        
        // STEP 2: Disable advanced features
        walletFeatureService.disableFeatures(wallet.getId(), List.of(
            "INTERNATIONAL_TRANSFERS",
            "CRYPTO_TRADING",
            "INVESTMENT_ACCOUNTS",
            "HIGH_VALUE_TRANSFERS"
        ));
        
        // STEP 3: Send notification with next steps
        notificationService.sendKYCFailureNotification(
            event.getUserId(),
            "KYC Verification Requires Attention",
            String.format(
                "Your identity verification needs additional information. Reason: %s. " +
                "Please update your documents to access full wallet features.",
                event.getFailureReason()
            ),
            event.getFailureReason()
        );
        
        // STEP 4: Create support ticket for manual review
        supportService.createKYCReviewTicket(
            event.getUserId(),
            event.getFailureReason(),
            event.getSubmittedDocuments()
        );
    }
    
    private WalletLimits calculateLimitsForKYCLevel(String kycLevel) {
        switch (kycLevel.toUpperCase()) {
            case "BASIC":
                return WalletLimits.builder()
                    .dailySpendLimit(new BigDecimal("1000"))
                    .monthlySpendLimit(new BigDecimal("10000"))
                    .singleTransactionLimit(new BigDecimal("500"))
                    .internationalTransferLimit(BigDecimal.ZERO) // Not allowed
                    .build();
                    
            case "STANDARD":
                return WalletLimits.builder()
                    .dailySpendLimit(new BigDecimal("5000"))
                    .monthlySpendLimit(new BigDecimal("50000"))
                    .singleTransactionLimit(new BigDecimal("2500"))
                    .internationalTransferLimit(new BigDecimal("1000"))
                    .build();
                    
            case "PREMIUM":
                return WalletLimits.builder()
                    .dailySpendLimit(new BigDecimal("25000"))
                    .monthlySpendLimit(new BigDecimal("250000"))
                    .singleTransactionLimit(new BigDecimal("10000"))
                    .internationalTransferLimit(new BigDecimal("10000"))
                    .build();
                    
            case "ENTERPRISE":
                return WalletLimits.builder()
                    .dailySpendLimit(new BigDecimal("100000"))
                    .monthlySpendLimit(new BigDecimal("1000000"))
                    .singleTransactionLimit(new BigDecimal("50000"))
                    .internationalTransferLimit(new BigDecimal("50000"))
                    .build();
                    
            default:
                log.warn("Unknown KYC level: {}, applying basic limits", kycLevel);
                return calculateLimitsForKYCLevel("BASIC");
        }
    }
    
    private void enableFeaturesForKYCLevel(UUID walletId, String kycLevel) {
        List<String> features = new ArrayList<>();
        
        // Base features for all verified users
        features.addAll(List.of(
            "PEER_TO_PEER_TRANSFERS",
            "BILL_PAYMENTS",
            "MOBILE_DEPOSITS"
        ));
        
        // Additional features based on KYC level
        switch (kycLevel.toUpperCase()) {
            case "STANDARD":
            case "PREMIUM":
            case "ENTERPRISE":
                features.addAll(List.of(
                    "INTERNATIONAL_TRANSFERS",
                    "ACH_TRANSFERS",
                    "WIRE_TRANSFERS"
                ));
                break;
        }
        
        if ("PREMIUM".equals(kycLevel.toUpperCase()) || "ENTERPRISE".equals(kycLevel.toUpperCase())) {
            features.addAll(List.of(
                "CRYPTO_TRADING",
                "INVESTMENT_ACCOUNTS",
                "MARGIN_TRADING"
            ));
        }
        
        if ("ENTERPRISE".equals(kycLevel.toUpperCase())) {
            features.addAll(List.of(
                "BULK_PAYMENTS",
                "API_ACCESS",
                "WHITE_LABEL_FEATURES"
            ));
        }
        
        walletFeatureService.enableFeatures(walletId, features);
        log.info("Enabled {} features for KYC level: {}", features.size(), kycLevel);
    }
}