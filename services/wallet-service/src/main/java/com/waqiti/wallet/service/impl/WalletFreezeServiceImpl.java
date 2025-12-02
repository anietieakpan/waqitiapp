package com.waqiti.wallet.service.impl;

import com.waqiti.common.events.AccountFreezeRequestEvent;
import com.waqiti.common.events.FraudDetectionEvent;
import com.waqiti.common.security.SensitiveDataMasker;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.domain.WalletFreezeRecord;
import com.waqiti.wallet.repository.WalletFreezeRecordRepository;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Production-ready implementation of WalletFreezeService with comprehensive fraud detection support.
 *
 * <p>This service handles wallet freeze operations triggered by:
 * <ul>
 *   <li>ML-based fraud detection (fraud-detection-events)</li>
 *   <li>Compliance alerts (AML, OFAC, PEP, KYC)</li>
 *   <li>Legal orders and regulatory requirements</li>
 *   <li>Manual security team actions</li>
 * </ul>
 *
 * <p><b>Compliance:</b>
 * <ul>
 *   <li>PCI DSS 12.10 - Incident response procedures</li>
 *   <li>FinCEN 31 CFR 1022.210 - AML program requirements</li>
 *   <li>OFAC 31 CFR 501 - Sanctions compliance</li>
 *   <li>GDPR Article 32 - Security measures</li>
 * </ul>
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletFreezeServiceImpl implements WalletFreezeService {

    private final WalletRepository walletRepository;
    private final WalletFreezeRecordRepository freezeRecordRepository;
    private final TransactionRestrictionService restrictionService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DistributedLockService lockService;

    // Idempotency tracking for fraud events (in-memory cache with 24h TTL)
    private final Map<UUID, LocalDateTime> processedFraudEvents = new ConcurrentHashMap<>();
    private static final long FRAUD_EVENT_CACHE_HOURS = 24;

    private static final String WALLET_FREEZE_TOPIC = "wallet.freeze.events";
    private static final String COMPLIANCE_ALERT_TOPIC = "compliance.critical.alerts";

    // ========================================
    // Interface Implementation Methods
    // ========================================

    @Override
    @Transactional
    public List<String> freezeAllWalletsImmediately(UUID userId, List<String> walletIds,
                                                     String freezeReason, String severity) {
        log.warn("FREEZE: Immediate freeze initiated - User: {}, Wallets: {}, Reason: {}, Severity: {}",
                SensitiveDataMasker.formatUserIdForLogging(userId), walletIds.size(), freezeReason, severity);

        String lockKey = "wallet:freeze:user:" + userId;
        return lockService.executeWithLock(lockKey, java.time.Duration.ofSeconds(10), () -> {
            List<String> frozenWalletIds = new ArrayList<>();

            for (String walletId : walletIds) {
                try {
                    UUID walletUUID = UUID.fromString(walletId);
                    Optional<Wallet> walletOpt = walletRepository.findById(walletUUID);

                    if (walletOpt.isEmpty()) {
                        log.warn("FREEZE: Wallet not found - Wallet: {}, User: {}",
                                walletId, SensitiveDataMasker.formatUserIdForLogging(userId));
                        continue;
                    }

                    Wallet wallet = walletOpt.get();

                    // Create freeze record
                    WalletFreezeRecord freezeRecord = createFreezeRecord(
                            wallet, userId, freezeReason, severity,
                            AccountFreezeRequestEvent.FreezeScope.COMPLETE_FREEZE
                    );
                    freezeRecordRepository.save(freezeRecord);

                    // Apply transaction restrictions
                    restrictionService.blockAllTransactions(walletUUID, freezeReason);

                    // Publish freeze event
                    publishWalletFreezeEvent(wallet, freezeRecord, severity);

                    frozenWalletIds.add(walletId);

                    log.info("FREEZE: Wallet frozen successfully - Wallet: {}, Freeze ID: {}",
                            walletId, freezeRecord.getFreezeId());

                } catch (Exception e) {
                    log.error("FREEZE: Failed to freeze wallet - Wallet: {}, User: {}, Error: {}",
                            walletId, SensitiveDataMasker.formatUserIdForLogging(userId), e.getMessage());
                }
            }

            // Send notifications
            notificationService.sendWalletFreezeNotification(userId, frozenWalletIds, freezeReason);

            // Audit log
            auditLogService.logWalletFreeze(userId, frozenWalletIds, freezeReason, severity);

            return frozenWalletIds;
        });
    }

    @Override
    @Transactional
    public List<String> freezeWalletsCompletely(UUID userId, List<String> walletIds, String freezeReason) {
        return freezeAllWalletsImmediately(userId, walletIds, freezeReason, "HIGH");
    }

    @Override
    @Transactional
    public List<String> freezeWalletDebits(UUID userId, List<String> walletIds, String freezeReason) {
        log.info("FREEZE: Debit-only freeze initiated - User: {}, Wallets: {}, Reason: {}",
                SensitiveDataMasker.formatUserIdForLogging(userId), walletIds.size(), freezeReason);

        return freezeWalletsByScope(userId, walletIds, AccountFreezeRequestEvent.FreezeScope.DEBITS_ONLY);
    }

    @Override
    @Transactional
    public List<String> freezeHighValueTransactions(UUID userId, List<String> walletIds, BigDecimal threshold) {
        String freezeReason = "High-value transaction restriction: threshold=" + threshold;
        log.info("FREEZE: High-value freeze initiated - User: {}, Wallets: {}, Threshold: {}",
                SensitiveDataMasker.formatUserIdForLogging(userId), walletIds.size(), threshold);

        List<String> frozenWalletIds = new ArrayList<>();

        for (String walletId : walletIds) {
            try {
                UUID walletUUID = UUID.fromString(walletId);
                restrictionService.restrictTransactionsAboveThreshold(walletUUID, threshold, freezeReason);
                frozenWalletIds.add(walletId);
            } catch (Exception e) {
                log.error("FREEZE: Failed to apply high-value restriction - Wallet: {}", walletId, e);
            }
        }

        return frozenWalletIds;
    }

    @Override
    @Transactional
    public List<String> freezeWalletsByScope(UUID userId, List<String> walletIds,
                                             AccountFreezeRequestEvent.FreezeScope scope) {
        log.info("FREEZE: Scoped freeze initiated - User: {}, Wallets: {}, Scope: {}",
                SensitiveDataMasker.formatUserIdForLogging(userId), walletIds.size(), scope);

        List<String> frozenWalletIds = new ArrayList<>();

        for (String walletId : walletIds) {
            try {
                UUID walletUUID = UUID.fromString(walletId);
                Optional<Wallet> walletOpt = walletRepository.findById(walletUUID);

                if (walletOpt.isEmpty()) continue;

                Wallet wallet = walletOpt.get();
                String freezeReason = "Freeze scope: " + scope;

                WalletFreezeRecord freezeRecord = createFreezeRecord(
                        wallet, userId, freezeReason, "MEDIUM", scope
                );
                freezeRecordRepository.save(freezeRecord);

                applyScopeRestrictions(walletUUID, scope, freezeReason);

                frozenWalletIds.add(walletId);

            } catch (Exception e) {
                log.error("FREEZE: Failed to apply scoped freeze - Wallet: {}, Scope: {}",
                        walletId, scope, e);
            }
        }

        return frozenWalletIds;
    }

    @Override
    @Transactional
    public List<String> applyWalletRestrictions(UUID userId, List<String> walletIds,
                                                AccountFreezeRequestEvent.FreezeScope scope,
                                                LocalDateTime reviewDate) {
        log.info("FREEZE: Restrictions with review date - User: {}, Wallets: {}, Scope: {}, Review: {}",
                SensitiveDataMasker.formatUserIdForLogging(userId), walletIds.size(), scope, reviewDate);

        List<String> frozenWalletIds = freezeWalletsByScope(userId, walletIds, scope);

        // Schedule automatic review/unfreeze
        for (String walletId : frozenWalletIds) {
            scheduleReview(UUID.fromString(walletId), reviewDate);
        }

        return frozenWalletIds;
    }

    @Override
    @Transactional
    public void freezeSpecificWallet(String walletId, UUID userId, String freezeReason,
                                    AccountFreezeRequestEvent.FreezeScope scope, String caseId) {
        log.warn("FREEZE: Specific wallet freeze - Wallet: {}, User: {}, Scope: {}, Case: {}",
                walletId, SensitiveDataMasker.formatUserIdForLogging(userId), scope, caseId);

        freezeWalletsByScope(userId, Collections.singletonList(walletId), scope);

        // Link to case management system
        auditLogService.linkToCaseManagement(walletId, caseId, freezeReason);
    }

    @Override
    @Transactional
    public void freezeAllBalances(UUID userId, String freezeReason) {
        log.warn("FREEZE: All balances freeze - User: {}, Reason: {}",
                SensitiveDataMasker.formatUserIdForLogging(userId), freezeReason);

        List<Wallet> userWallets = walletRepository.findByUserId(userId);
        List<String> walletIds = userWallets.stream()
                .map(w -> w.getWalletId().toString())
                .collect(Collectors.toList());

        freezeWalletsCompletely(userId, walletIds, freezeReason);
    }

    @Override
    @Transactional
    public void freezeOutgoingBalances(UUID userId) {
        log.info("FREEZE: Outgoing balances freeze - User: {}",
                SensitiveDataMasker.formatUserIdForLogging(userId));

        List<Wallet> userWallets = walletRepository.findByUserId(userId);
        List<String> walletIds = userWallets.stream()
                .map(w -> w.getWalletId().toString())
                .collect(Collectors.toList());

        freezeWalletDebits(userId, walletIds, "Outgoing transaction freeze");
    }

    @Override
    @Transactional
    public void freezeHighValueBalances(UUID userId, BigDecimal threshold) {
        log.info("FREEZE: High-value balances freeze - User: {}, Threshold: {}",
                SensitiveDataMasker.formatUserIdForLogging(userId), threshold);

        List<Wallet> userWallets = walletRepository.findByUserId(userId);
        List<String> highValueWalletIds = userWallets.stream()
                .filter(w -> w.getBalance().compareTo(threshold) > 0)
                .map(w -> w.getWalletId().toString())
                .collect(Collectors.toList());

        if (!highValueWalletIds.isEmpty()) {
            String freezeReason = "High-value balance freeze: threshold=" + threshold;
            freezeWalletsCompletely(userId, highValueWalletIds, freezeReason);
        }
    }

    @Override
    @Transactional
    public void freezeSpecificCurrencyBalances(UUID userId, List<String> currencies) {
        log.info("FREEZE: Currency-specific freeze - User: {}, Currencies: {}",
                SensitiveDataMasker.formatUserIdForLogging(userId), currencies);

        List<Wallet> userWallets = walletRepository.findByUserId(userId);
        List<String> currencyWalletIds = userWallets.stream()
                .filter(w -> currencies.contains(w.getCurrency()))
                .map(w -> w.getWalletId().toString())
                .collect(Collectors.toList());

        if (!currencyWalletIds.isEmpty()) {
            String freezeReason = "Currency-specific freeze: " + String.join(", ", currencies);
            freezeWalletsCompletely(userId, currencyWalletIds, freezeReason);
        }
    }

    // ========================================
    // Fraud Detection Event Methods
    // ========================================

    /**
     * Check if a fraud event has already been processed (idempotency check).
     *
     * @param eventId fraud event ID
     * @return true if already processed within cache window
     */
    public boolean isFraudEventProcessed(UUID eventId) {
        LocalDateTime processedTime = processedFraudEvents.get(eventId);

        if (processedTime == null) {
            return false;
        }

        // Check if cache entry is still valid (within 24h)
        LocalDateTime expiryTime = processedTime.plusHours(FRAUD_EVENT_CACHE_HOURS);
        if (LocalDateTime.now().isAfter(expiryTime)) {
            processedFraudEvents.remove(eventId);
            return false;
        }

        return true;
    }

    /**
     * Mark a fraud event as processed for idempotency tracking.
     *
     * @param eventId fraud event ID
     */
    public void markFraudEventProcessed(UUID eventId) {
        processedFraudEvents.put(eventId, LocalDateTime.now());

        // Cleanup old entries (simple cache eviction)
        cleanupOldFraudEvents();
    }

    /**
     * Store failed fraud event in DLQ for manual review.
     *
     * @param event fraud detection event
     * @param errorMessage error details
     */
    public void storeFraudEventInDlt(FraudDetectionEvent event, String errorMessage) {
        log.error("DLT: Storing fraud event in DLT - Event: {}, User: {}, Error: {}",
                event.getEventId(), SensitiveDataMasker.formatUserIdForLogging(event.getUserId()),
                errorMessage);

        try {
            Map<String, Object> dltPayload = new HashMap<>();
            dltPayload.put("eventId", event.getEventId().toString());
            dltPayload.put("userId", event.getUserId().toString());
            dltPayload.put("transactionId", event.getTransactionId() != null ? event.getTransactionId().toString() : null);
            dltPayload.put("riskScore", event.getRiskScore());
            dltPayload.put("riskLevel", event.getRiskLevel().toString());
            dltPayload.put("detectedPatterns", event.getDetectedPatterns());
            dltPayload.put("errorMessage", errorMessage);
            dltPayload.put("timestamp", LocalDateTime.now().toString());
            dltPayload.put("dltReason", "FRAUD_EVENT_PROCESSING_FAILURE");

            kafkaTemplate.send("fraud-detection-events.DLT", event.getEventId().toString(), dltPayload);

            // Create compliance alert for DLT event
            Map<String, Object> alert = new HashMap<>();
            alert.put("alertType", "FRAUD_DLT_ENTRY");
            alert.put("severity", "CRITICAL");
            alert.put("eventId", event.getEventId().toString());
            alert.put("userId", event.getUserId().toString());
            alert.put("errorMessage", errorMessage);
            alert.put("requiresManualReview", true);

            kafkaTemplate.send(COMPLIANCE_ALERT_TOPIC, event.getUserId().toString(), alert);

            log.info("DLT: Fraud event stored in DLT with compliance alert - Event: {}",
                    event.getEventId());

        } catch (Exception e) {
            log.error("DLT: CRITICAL - Failed to store fraud event in DLT - Event: {}",
                    event.getEventId(), e);
            // Last resort: log to dedicated DLT failure logger
            logDltFailure(event, errorMessage, e);
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    private WalletFreezeRecord createFreezeRecord(Wallet wallet, UUID userId, String freezeReason,
                                                   String severity, AccountFreezeRequestEvent.FreezeScope scope) {
        WalletFreezeRecord record = new WalletFreezeRecord();
        record.setFreezeId(UUID.randomUUID());
        record.setWalletId(wallet.getWalletId());
        record.setUserId(userId);
        record.setFreezeReason(freezeReason);
        record.setSeverity(severity);
        record.setFreezeScope(scope);
        record.setFrozenAt(LocalDateTime.now());
        record.setFrozenBy("SYSTEM");
        record.setActive(true);
        return record;
    }

    private void applyScopeRestrictions(UUID walletId, AccountFreezeRequestEvent.FreezeScope scope,
                                       String reason) {
        switch (scope) {
            case COMPLETE_FREEZE:
                restrictionService.blockAllTransactions(walletId, reason);
                break;
            case DEBITS_ONLY:
                restrictionService.blockDebitTransactions(walletId, reason);
                break;
            case CREDITS_ONLY:
                restrictionService.blockCreditTransactions(walletId, reason);
                break;
            case WITHDRAWALS_ONLY:
                restrictionService.blockWithdrawals(walletId, reason);
                break;
            case INTERNATIONAL_ONLY:
                restrictionService.blockInternationalTransactions(walletId, reason);
                break;
            default:
                log.warn("FREEZE: Unknown freeze scope - Wallet: {}, Scope: {}", walletId, scope);
        }
    }

    private void publishWalletFreezeEvent(Wallet wallet, WalletFreezeRecord freezeRecord, String severity) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "WALLET_FROZEN");
        event.put("freezeId", freezeRecord.getFreezeId().toString());
        event.put("walletId", wallet.getWalletId().toString());
        event.put("userId", wallet.getUserId().toString());
        event.put("currency", wallet.getCurrency());
        event.put("balance", wallet.getBalance());
        event.put("freezeReason", freezeRecord.getFreezeReason());
        event.put("severity", severity);
        event.put("scope", freezeRecord.getFreezeScope().toString());
        event.put("timestamp", LocalDateTime.now().toString());

        kafkaTemplate.send(WALLET_FREEZE_TOPIC, wallet.getWalletId().toString(), event);
    }

    private void scheduleReview(UUID walletId, LocalDateTime reviewDate) {
        try {
            log.info("FREEZE: Review scheduled - Wallet: {}, Review Date: {}", walletId, reviewDate);

            // Create scheduled review task
            Map<String, Object> reviewTask = new HashMap<>();
            reviewTask.put("taskType", "WALLET_FREEZE_REVIEW");
            reviewTask.put("walletId", walletId.toString());
            reviewTask.put("scheduledDate", reviewDate.toString());
            reviewTask.put("createdAt", LocalDateTime.now().toString());
            reviewTask.put("status", "SCHEDULED");
            reviewTask.put("priority", "MEDIUM");

            // Publish to scheduled tasks topic for processing by job scheduler
            kafkaTemplate.send("wallet.scheduled.reviews", walletId.toString(), reviewTask);

            log.info("FREEZE: Review task scheduled successfully - Wallet: {}, Review Date: {}",
                    walletId, reviewDate);

        } catch (Exception e) {
            log.error("FREEZE: Failed to schedule review - Wallet: {}, Review Date: {}",
                    walletId, reviewDate, e);
            // Don't fail the freeze operation if scheduling fails
        }
    }

    private void cleanupOldFraudEvents() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(FRAUD_EVENT_CACHE_HOURS);
        processedFraudEvents.entrySet()
                .removeIf(entry -> entry.getValue().isBefore(cutoffTime));
    }

    private void logDltFailure(FraudDetectionEvent event, String originalError, Exception dltException) {
        log.error("DLT_FAILURE: CRITICAL ALERT - Unable to store fraud event in DLT. " +
                        "Manual intervention required. Event: {}, User: {}, Original Error: {}, DLT Error: {}",
                event.getEventId(), SensitiveDataMasker.formatUserIdForLogging(event.getUserId()),
                originalError, dltException.getMessage());
        // In production, this should trigger PagerDuty/OpsGenie alert
    }

    // ========================================
    // Cache Management
    // ========================================

    /**
     * Clear the fraud event cache (for testing/maintenance).
     * Should only be called during maintenance windows.
     */
    public void clearFraudEventCache() {
        int size = processedFraudEvents.size();
        processedFraudEvents.clear();
        log.warn("CACHE: Cleared fraud event cache - {} entries removed", size);
    }

    /**
     * Get fraud event cache statistics.
     */
    public Map<String, Object> getFraudEventCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cacheSize", processedFraudEvents.size());
        stats.put("cacheHours", FRAUD_EVENT_CACHE_HOURS);
        stats.put("oldestEntry", processedFraudEvents.values().stream()
                .min(LocalDateTime::compareTo)
                .orElse(null));
        return stats;
    }
}
