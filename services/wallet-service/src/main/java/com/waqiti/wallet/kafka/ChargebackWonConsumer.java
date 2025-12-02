package com.waqiti.wallet.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.locking.DistributedLockService;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.domain.TransactionType;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.service.WalletService;
import com.waqiti.wallet.service.WalletTransactionService;
import com.waqiti.wallet.service.WalletAuditService;
import com.waqiti.wallet.service.WalletNotificationService;
import org.springframework.kafka.core.KafkaTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL FIX #5: ChargebackWonConsumer
 *
 * PROBLEM SOLVED: Merchants win chargebacks but never receive funds back
 * - Chargeback service resolves disputes in merchant's favor
 * - Events published to "payment.chargeback.won" topic
 * - NO consumer listening - merchants never credited
 * - Result: Financial losses for merchants, damaged relationships
 *
 * IMPLEMENTATION:
 * - Listens to "payment.chargeback.won" events
 * - Credits merchant wallet with disputed amount
 * - Reverses previous chargeback debit
 * - Sends notification to merchant
 * - Creates audit trail for accounting
 * - Updates merchant account standing
 *
 * CHARGEBACK LIFECYCLE:
 * 1. Customer disputes charge → Merchant debited (provisional)
 * 2. Merchant submits representment (evidence)
 * 3. Card network rules in favor (WON)
 * 4. This consumer credits merchant wallet ← WE ARE HERE
 * 5. Merchant notified of successful defense
 *
 * FINANCIAL IMPACT:
 * - Average chargeback: $100-$500
 * - Win rate: 30-40% of disputes
 * - Without this fix: Merchants lose even when they win
 * - Merchant retention risk: HIGH
 *
 * @author Waqiti Platform Team - Critical Fix
 * @since 2025-10-12
 * @priority CRITICAL
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChargebackWonConsumer {

    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final WalletTransactionService transactionService;
    private final WalletAuditService auditService;
    private final WalletNotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final DistributedLockService lockService;
    private final MetricsCollector metricsCollector;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler dlqHandler;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String CONSUMER_GROUP = "wallet-chargeback-won-processor";
    private static final String TOPIC = "payment.chargeback.won";
    private static final String LOCK_PREFIX = "chargeback-won-";
    private static final Duration LOCK_TIMEOUT = Duration.ofMinutes(5);
    private static final String IDEMPOTENCY_PREFIX = "chargeback:won:";

    /**
     * Consumer for chargeback won events
     * Credits merchant wallet when chargeback is resolved in their favor
     *
     * CRITICAL MERCHANT SUCCESS FUNCTION:
     * - Restores funds to merchant after successful defense
     * - Reverses provisional chargeback debit
     * - Maintains accurate accounting records
     * - Improves merchant satisfaction and retention
     * - Complies with card network settlement rules
     */
    @KafkaListener(
        topics = TOPIC,
        groupId = CONSUMER_GROUP,
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "3"
    )
    @Retryable(
        value = {Exception.class},
        exclude = {BusinessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    @Transactional
    public void handleChargebackWon(
            @Payload ChargebackWonEvent event,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String messageKey,
            @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        long startTime = System.currentTimeMillis();
        String lockId = null;

        try {
            log.info("🎉 CHARGEBACK WON: chargebackId={}, merchantWalletId={}, amount=${}, paymentId={}, partition={}, offset={}",
                event.getChargebackId(), event.getMerchantWalletId(), event.getAmount(),
                event.getOriginalPaymentId(), partition, offset);

            metricsCollector.incrementCounter("wallet.chargeback.won.received");
            metricsCollector.recordGauge("wallet.chargeback.amount", event.getAmount().doubleValue());

            // Step 1: Idempotency check (critical - prevent double credits)
            String idempotencyKey = IDEMPOTENCY_PREFIX + event.getChargebackId();
            if (!idempotencyService.tryAcquire(idempotencyKey, Duration.ofHours(72))) {
                log.warn("DUPLICATE CHARGEBACK WON EVENT: chargebackId={} - Already processed", event.getChargebackId());
                metricsCollector.incrementCounter("wallet.chargeback.won.duplicate");
                acknowledgment.acknowledge();
                return;
            }

            // Step 2: Validate event data
            validateChargebackEvent(event);

            // Step 3: Acquire distributed lock
            lockId = lockService.acquireLock(LOCK_PREFIX + event.getMerchantWalletId(), LOCK_TIMEOUT);
            if (lockId == null) {
                throw new BusinessException("Failed to acquire lock for wallet " + event.getMerchantWalletId());
            }

            // Step 4: Load merchant wallet
            Wallet merchantWallet = walletRepository.findById(event.getMerchantWalletId())
                .orElseThrow(() -> new BusinessException("Merchant wallet not found: " + event.getMerchantWalletId()));

            // Step 5: Credit merchant wallet (restore funds)
            BigDecimal previousBalance = merchantWallet.getBalance();
            merchantWallet.setBalance(merchantWallet.getBalance().add(event.getAmount()));
            merchantWallet.setUpdatedAt(LocalDateTime.now());

            Wallet updatedWallet = walletRepository.save(merchantWallet);

            log.info("✅ MERCHANT WALLET CREDITED: walletId={}, amount=${}, previousBalance=${}, newBalance=${}",
                event.getMerchantWalletId(), event.getAmount(), previousBalance, updatedWallet.getBalance());

            // Step 6: Create transaction record (chargeback reversal)
            transactionService.createTransaction(
                merchantWallet.getId(),
                event.getAmount(),
                TransactionType.CHARGEBACK_REVERSAL,
                String.format("Chargeback won - Funds restored. Original payment: %s. Chargeback case: %s. Reason: %s",
                    event.getOriginalPaymentId(), event.getChargebackId(), event.getWinReason()),
                event.getChargebackId()
            );

            // Step 7: Create comprehensive audit log
            auditService.logChargebackWon(
                merchantWallet.getId(),
                merchantWallet.getUserId(),
                event.getChargebackId(),
                event.getOriginalPaymentId(),
                event.getAmount(),
                previousBalance,
                updatedWallet.getBalance(),
                event.getWinReason(),
                event.getCardNetwork(),
                event.getResolutionDate()
            );

            // Step 8: Send congratulatory notification to merchant
            notificationService.sendChargebackWonNotification(
                merchantWallet.getUserId(),
                event.getAmount(),
                merchantWallet.getCurrency(),
                event.getChargebackId(),
                event.getOriginalPaymentId(),
                event.getWinReason()
            );

            // Step 9: Update merchant account standing (improved reputation)
            updateMerchantStanding(merchantWallet.getUserId(), event);

            // Step 10: Publish chargeback resolved event
            publishChargebackResolvedEvent(event, merchantWallet);

            // Step 11: Track metrics
            long duration = System.currentTimeMillis() - startTime;
            metricsCollector.recordHistogram("wallet.chargeback.won.processing.duration.ms", duration);
            metricsCollector.incrementCounter("wallet.chargeback.won.success");
            metricsCollector.incrementCounter("wallet.chargeback.won.by.network." + event.getCardNetwork().toLowerCase());

            log.info("🏆 CHARGEBACK VICTORY PROCESSED: chargebackId={}, merchantWalletId={}, amount=${}, duration={}ms",
                event.getChargebackId(), event.getMerchantWalletId(), event.getAmount(), duration);

            acknowledgment.acknowledge();

        } catch (BusinessException e) {
            log.error("Business exception processing chargeback won {}: {}", event.getChargebackId(), e.getMessage());
            metricsCollector.incrementCounter("wallet.chargeback.won.business.error");
            handleBusinessException(event, e, acknowledgment);

        } catch (Exception e) {
            log.error("CRITICAL ERROR processing chargeback won {}", event.getChargebackId(), e);
            metricsCollector.incrementCounter("wallet.chargeback.won.critical.error");
            handleCriticalException(event, e, partition, offset, acknowledgment);

        } finally {
            if (lockId != null) {
                lockService.releaseLock(LOCK_PREFIX + event.getMerchantWalletId(), lockId);
            }
        }
    }

    /**
     * Validate chargeback won event
     */
    private void validateChargebackEvent(ChargebackWonEvent event) {
        if (event.getChargebackId() == null || event.getChargebackId().isBlank()) {
            throw new BusinessException("Chargeback ID is required");
        }
        if (event.getMerchantWalletId() == null) {
            throw new BusinessException("Merchant wallet ID is required");
        }
        if (event.getAmount() == null || event.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Chargeback amount must be positive");
        }
        if (event.getOriginalPaymentId() == null) {
            throw new BusinessException("Original payment ID is required");
        }
    }

    /**
     * Update merchant standing after winning chargeback
     * Winning chargebacks improves merchant reputation
     */
    private void updateMerchantStanding(UUID merchantId, ChargebackWonEvent event) {
        try {
            log.info("📈 IMPROVING MERCHANT STANDING: merchantId={}, chargebackId={}, winReason={}",
                merchantId, event.getChargebackId(), event.getWinReason());

            // Publish merchant reputation update event
            Map<String, Object> reputationUpdate = new HashMap<>();
            reputationUpdate.put("updateType", "CHARGEBACK_WON");
            reputationUpdate.put("merchantId", merchantId.toString());
            reputationUpdate.put("chargebackId", event.getChargebackId());
            reputationUpdate.put("amount", event.getAmount());
            reputationUpdate.put("cardNetwork", event.getCardNetwork());
            reputationUpdate.put("winReason", event.getWinReason());
            reputationUpdate.put("resolutionDate", event.getResolutionDate() != null ? event.getResolutionDate().toString() : LocalDateTime.now().toString());
            reputationUpdate.put("impactScore", calculateReputationImpact(event));
            reputationUpdate.put("timestamp", LocalDateTime.now().toString());
            reputationUpdate.put("action", "IMPROVE_REPUTATION");

            // Publish to merchant service topic for reputation updates
            kafkaTemplate.send("merchant.reputation.updates", merchantId.toString(), reputationUpdate);

            log.info("Merchant reputation update published: merchantId={}, chargebackId={}", merchantId, event.getChargebackId());
            metricsCollector.incrementCounter("wallet.chargeback.merchant.standing.improved");
        } catch (Exception e) {
            log.error("Failed to update merchant standing for merchantId={}", merchantId, e);
            // Don't fail the transaction - standing update is secondary
        }
    }

    /**
     * Calculate reputation impact score based on chargeback win
     */
    private double calculateReputationImpact(ChargebackWonEvent event) {
        // Higher amounts have greater positive impact
        double amountFactor = Math.min(event.getAmount().doubleValue() / 1000.0, 5.0);
        // Base positive score for winning
        double baseScore = 10.0;
        return baseScore + amountFactor;
    }

    /**
     * Publish chargeback resolved event
     */
    private void publishChargebackResolvedEvent(ChargebackWonEvent event, Wallet merchantWallet) {
        try {
            // Create comprehensive chargeback resolved event for reporting and analytics
            Map<String, Object> resolvedEvent = new HashMap<>();
            resolvedEvent.put("eventType", "CHARGEBACK_RESOLVED");
            resolvedEvent.put("chargebackId", event.getChargebackId());
            resolvedEvent.put("outcome", "WON");
            resolvedEvent.put("merchantWalletId", event.getMerchantWalletId().toString());
            resolvedEvent.put("merchantId", event.getMerchantId().toString());
            resolvedEvent.put("originalPaymentId", event.getOriginalPaymentId().toString());
            resolvedEvent.put("amount", event.getAmount());
            resolvedEvent.put("currency", event.getCurrency());
            resolvedEvent.put("winReason", event.getWinReason());
            resolvedEvent.put("cardNetwork", event.getCardNetwork());
            resolvedEvent.put("representmentId", event.getRepresentmentId());
            resolvedEvent.put("resolutionDate", event.getResolutionDate() != null ? event.getResolutionDate().toString() : LocalDateTime.now().toString());
            resolvedEvent.put("walletBalanceAfter", merchantWallet.getBalance());
            resolvedEvent.put("resolvedAt", LocalDateTime.now().toString());
            resolvedEvent.put("status", "COMPLETED");
            resolvedEvent.put("merchantFavorable", true);

            // Publish to chargeback resolved topic for reporting and analytics
            kafkaTemplate.send("chargeback.resolved", event.getChargebackId(), resolvedEvent);

            log.info("Chargeback resolved event published: chargebackId={}, outcome=WON, amount={}",
                    event.getChargebackId(), event.getAmount());
            metricsCollector.incrementCounter("wallet.chargeback.resolved.event.published");
        } catch (Exception e) {
            log.error("Failed to publish chargeback resolved event for chargebackId={}", event.getChargebackId(), e);
            // Non-critical - don't fail the transaction
        }
    }

    /**
     * Handle business exceptions
     */
    private void handleBusinessException(ChargebackWonEvent event, BusinessException e, Acknowledgment acknowledgment) {
        log.warn("Business validation failed for chargeback won {}: {}", event.getChargebackId(), e.getMessage());

        dlqHandler.sendToDLQ(
            TOPIC,
            event,
            e,
            "Business validation failed: " + e.getMessage()
        );

        acknowledgment.acknowledge();
    }

    /**
     * Handle critical exceptions
     */
    private void handleCriticalException(ChargebackWonEvent event, Exception e, int partition, long offset, Acknowledgment acknowledgment) {
        log.error("CRITICAL: Chargeback won processing failed - MERCHANT NOT CREDITED. chargebackId={}, merchantWalletId={}, amount=${}",
            event.getChargebackId(), event.getMerchantWalletId(), event.getAmount(), e);

        dlqHandler.sendToDLQ(
            TOPIC,
            event,
            e,
            String.format("CRITICAL FAILURE - Merchant not credited for chargeback win at partition=%d, offset=%d: %s",
                partition, offset, e.getMessage())
        );

        // Alert operations - this is CRITICAL for merchant relations
        try {
            log.error("🚨 PAGERDUTY ALERT: Failed to credit merchant for chargeback win - FINANCIAL IMPACT - chargebackId={}, amount=${}",
                event.getChargebackId(), event.getAmount());
            metricsCollector.incrementCounter("wallet.chargeback.won.critical.alert");
        } catch (Exception alertEx) {
            log.error("Failed to send critical alert", alertEx);
        }

        acknowledgment.acknowledge();
    }

    // DTO for chargeback won event
    private static class ChargebackWonEvent {
        private String chargebackId;
        private UUID merchantWalletId;
        private UUID merchantId;
        private UUID originalPaymentId;
        private BigDecimal amount;
        private String currency;
        private String winReason;
        private String cardNetwork;  // VISA, MASTERCARD, AMEX, etc.
        private LocalDateTime resolutionDate;
        private String representmentId;

        // Getters
        public String getChargebackId() { return chargebackId; }
        public UUID getMerchantWalletId() { return merchantWalletId; }
        public UUID getMerchantId() { return merchantId; }
        public UUID getOriginalPaymentId() { return originalPaymentId; }
        public BigDecimal getAmount() { return amount; }
        public String getCurrency() { return currency; }
        public String getWinReason() { return winReason; }
        public String getCardNetwork() { return cardNetwork; }
        public LocalDateTime getResolutionDate() { return resolutionDate; }
        public String getRepresentmentId() { return representmentId; }
    }
}
