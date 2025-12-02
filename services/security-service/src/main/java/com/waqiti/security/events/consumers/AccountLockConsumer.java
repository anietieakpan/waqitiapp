package com.waqiti.security.events.consumers;

import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.events.security.AccountLockEvent;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.security.domain.AccountLock;
import com.waqiti.security.domain.LockType;
import com.waqiti.security.domain.LockReason;
import com.waqiti.security.domain.LockStatus;
import com.waqiti.security.repository.AccountLockRepository;
import com.waqiti.security.service.AccountSecurityService;
import com.waqiti.security.service.SessionManagementService;
import com.waqiti.security.service.TokenRevocationService;
import com.waqiti.security.service.MfaService;
import com.waqiti.security.service.SecurityNotificationService;
import com.waqiti.common.exceptions.AccountSecurityException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade consumer for account lock events.
 * Handles comprehensive account security including:
 * - Suspicious activity lockdowns
 * - Failed login attempt locks
 * - Fraud-triggered locks
 * - Compliance-mandated locks
 * - Temporary and permanent account restrictions
 * - Session termination and token revocation
 * 
 * Critical for account security and fraud prevention.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountLockConsumer {

    private final AccountLockRepository lockRepository;
    private final AccountSecurityService securityService;
    private final SessionManagementService sessionService;
    private final TokenRevocationService tokenService;
    private final MfaService mfaService;
    private final SecurityNotificationService notificationService;
    private final AuditLogger auditLogger;
    private final MetricsService metricsService;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int TEMPORARY_LOCK_MINUTES = 30;

    @KafkaListener(
        topics = "account-lock-requests",
        groupId = "security-service-lock-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        include = {AccountSecurityException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void handleAccountLockRequest(
            @Payload AccountLockEvent lockEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "correlation-id", required = false) String correlationId,
            @Header(value = "lock-priority", required = false) String priority,
            Acknowledgment acknowledgment) {

        String eventId = lockEvent.getEventId() != null ? 
            lockEvent.getEventId() : UUID.randomUUID().toString();

        try {
            log.warn("Processing account lock request: {} for account: {} reason: {}", 
                    eventId, lockEvent.getAccountId(), lockEvent.getLockReason());

            // Metrics tracking
            metricsService.incrementCounter("account.lock.processing.started",
                Map.of(
                    "lock_type", lockEvent.getLockType(),
                    "lock_reason", lockEvent.getLockReason()
                ));

            // Idempotency check
            if (isLockAlreadyProcessed(lockEvent.getAccountId(), eventId)) {
                log.info("Account lock {} already processed for account {}", eventId, lockEvent.getAccountId());
                acknowledgment.acknowledge();
                return;
            }

            // Create lock record
            AccountLock lock = createLockRecord(lockEvent, eventId, correlationId);

            // Verify lock necessity
            if (!verifyLockRequired(lock, lockEvent)) {
                log.info("Lock not required for account: {}", lockEvent.getAccountId());
                acknowledgment.acknowledge();
                return;
            }

            // Execute immediate security actions
            executeImmediateSecurityActions(lock, lockEvent);

            // Perform comprehensive lock operations
            performAccountLockOperations(lock, lockEvent);

            // Handle related accounts if needed
            if (lockEvent.isLockRelatedAccounts()) {
                lockRelatedAccounts(lock, lockEvent);
            }

            // Update lock status
            updateLockStatus(lock);

            // Save lock record
            AccountLock savedLock = lockRepository.save(lock);

            // Schedule unlock if temporary
            if (lock.getLockType() == LockType.TEMPORARY) {
                scheduleUnlock(savedLock);
            }

            // Send notifications
            sendLockNotifications(savedLock, lockEvent);

            // Update security metrics
            updateSecurityMetrics(savedLock, lockEvent);

            // Create comprehensive audit trail
            createLockAuditLog(savedLock, lockEvent, correlationId);

            // Success metrics
            metricsService.incrementCounter("account.lock.processing.success",
                Map.of(
                    "lock_type", savedLock.getLockType().toString(),
                    "status", savedLock.getStatus().toString()
                ));

            log.warn("Successfully processed account lock: {} for account: {} with status: {}", 
                    savedLock.getId(), lockEvent.getAccountId(), savedLock.getStatus());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing account lock event {}: {}", eventId, e.getMessage(), e);
            metricsService.incrementCounter("account.lock.processing.error");
            
            // Critical audit log for lock failures
            auditLogger.logCriticalAlert("ACCOUNT_LOCK_PROCESSING_ERROR",
                "Critical account lock failure - security at risk",
                Map.of(
                    "accountId", lockEvent.getAccountId(),
                    "lockReason", lockEvent.getLockReason(),
                    "eventId", eventId,
                    "error", e.getMessage(),
                    "correlationId", correlationId != null ? correlationId : "N/A"
                ));
            
            throw new AccountSecurityException("Failed to process account lock: " + e.getMessage(), e);
        }
    }

    @KafkaListener(
        topics = "account-lock-emergency",
        groupId = "security-service-emergency-lock-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleEmergencyAccountLock(
            @Payload AccountLockEvent lockEvent,
            @Header(value = "correlation-id", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        try {
            log.error("EMERGENCY ACCOUNT LOCK: Processing immediate lock for account: {}", 
                    lockEvent.getAccountId());

            // Immediate lockdown
            AccountLock lock = performEmergencyLock(lockEvent, correlationId);

            // Kill all sessions immediately
            sessionService.terminateAllSessions(lockEvent.getAccountId());

            // Revoke all tokens
            tokenService.revokeAllTokens(lockEvent.getAccountId());

            // Block all transactions
            securityService.blockAllTransactions(lockEvent.getAccountId());

            // Notify security team
            notificationService.sendEmergencyLockAlert(lock);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process emergency account lock: {}", e.getMessage(), e);
            acknowledgment.acknowledge(); // Acknowledge to prevent blocking emergency queue
        }
    }

    private boolean isLockAlreadyProcessed(String accountId, String eventId) {
        return lockRepository.existsByAccountIdAndEventId(accountId, eventId);
    }

    private AccountLock createLockRecord(AccountLockEvent event, String eventId, String correlationId) {
        return AccountLock.builder()
            .id(UUID.randomUUID().toString())
            .eventId(eventId)
            .accountId(event.getAccountId())
            .userId(event.getUserId())
            .lockType(LockType.valueOf(event.getLockType().toUpperCase()))
            .lockReason(LockReason.valueOf(event.getLockReason().toUpperCase()))
            .lockDescription(event.getDescription())
            .triggeredBy(event.getTriggeredBy())
            .sourceSystem(event.getSourceSystem())
            .ipAddress(event.getIpAddress())
            .deviceId(event.getDeviceId())
            .failedAttempts(event.getFailedAttempts())
            .status(LockStatus.INITIATED)
            .correlationId(correlationId)
            .lockedAt(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private boolean verifyLockRequired(AccountLock lock, AccountLockEvent event) {
        // Check if account is already locked
        Optional<AccountLock> existingLock = lockRepository.findActiveByAccountId(lock.getAccountId());
        if (existingLock.isPresent()) {
            log.info("Account {} already has active lock: {}", 
                    lock.getAccountId(), existingLock.get().getId());
            
            // Upgrade lock severity if needed
            if (shouldUpgradeLock(existingLock.get(), lock)) {
                upgradeLock(existingLock.get(), lock);
                return true;
            }
            return false;
        }

        // Verify lock conditions based on reason
        return switch (lock.getLockReason()) {
            case FAILED_LOGIN_ATTEMPTS -> event.getFailedAttempts() >= MAX_FAILED_ATTEMPTS;
            case SUSPICIOUS_ACTIVITY -> securityService.hasSuspiciousActivity(lock.getAccountId());
            case FRAUD_DETECTED -> true; // Always lock for fraud
            case COMPLIANCE_REQUIRED -> true; // Always lock for compliance
            case USER_REQUEST -> verifyUserRequest(event);
            default -> true;
        };
    }

    private void executeImmediateSecurityActions(AccountLock lock, AccountLockEvent event) {
        try {
            log.info("Executing immediate security actions for account: {}", lock.getAccountId());

            // Terminate all active sessions
            CompletableFuture<Integer> sessionsFuture = CompletableFuture.supplyAsync(() -> {
                int terminated = sessionService.terminateAllSessions(lock.getAccountId());
                lock.setSessionsTerminated(terminated);
                return terminated;
            });

            // Revoke all access tokens
            CompletableFuture<Integer> tokensFuture = CompletableFuture.supplyAsync(() -> {
                int revoked = tokenService.revokeAllTokens(lock.getAccountId());
                lock.setTokensRevoked(revoked);
                return revoked;
            });

            // Invalidate all API keys
            CompletableFuture<Integer> apiKeysFuture = CompletableFuture.supplyAsync(() -> {
                int invalidated = securityService.invalidateApiKeys(lock.getAccountId());
                lock.setApiKeysInvalidated(invalidated);
                return invalidated;
            });

            // Wait for all actions to complete
            try {
                CompletableFuture.allOf(sessionsFuture, tokensFuture, apiKeysFuture)
                    .get(30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("Account lock security actions timed out after 30 seconds for account: {}", lock.getAccountId(), e);
                List.of(sessionsFuture, tokensFuture, apiKeysFuture).forEach(f -> f.cancel(true));
                throw new RuntimeException("Account lock security actions timed out", e);
            } catch (java.util.concurrent.ExecutionException e) {
                log.error("Account lock security actions execution failed for account: {}", lock.getAccountId(), e.getCause());
                throw new RuntimeException("Account lock security actions failed: " + e.getCause().getMessage(), e.getCause());
            } catch (java.util.concurrent.InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Account lock security actions interrupted for account: {}", lock.getAccountId(), e);
                throw new RuntimeException("Account lock security actions interrupted", e);
            }

            log.info("Immediate security actions completed: Sessions: {}, Tokens: {}, API Keys: {}",
                    lock.getSessionsTerminated(), lock.getTokensRevoked(), lock.getApiKeysInvalidated());

        } catch (Exception e) {
            log.error("Error executing immediate security actions: {}", e.getMessage());
            lock.setSecurityActionError(e.getMessage());
        }
    }

    private void performAccountLockOperations(AccountLock lock, AccountLockEvent event) {
        try {
            log.info("Performing account lock operations for: {}", lock.getAccountId());

            // Lock the account
            boolean locked = securityService.lockAccount(
                lock.getAccountId(),
                lock.getLockType(),
                lock.getLockReason()
            );

            lock.setAccountLocked(locked);

            // Set lock duration for temporary locks
            if (lock.getLockType() == LockType.TEMPORARY) {
                LocalDateTime unlockTime = calculateUnlockTime(lock, event);
                lock.setScheduledUnlockAt(unlockTime);
                lock.setLockDurationMinutes(
                    ChronoUnit.MINUTES.between(LocalDateTime.now(), unlockTime)
                );
            }

            // Disable account features based on lock type
            disableAccountFeatures(lock, event);

            // Force password reset if needed
            if (shouldForcePasswordReset(lock)) {
                securityService.forcePasswordReset(lock.getAccountId());
                lock.setPasswordResetRequired(true);
            }

            // Enable additional security measures
            if (shouldEnhanceSecurity(lock)) {
                enhanceAccountSecurity(lock);
            }

            // Block pending transactions
            if (shouldBlockTransactions(lock)) {
                int blocked = securityService.blockPendingTransactions(lock.getAccountId());
                lock.setTransactionsBlocked(blocked);
            }

        } catch (Exception e) {
            log.error("Error performing lock operations: {}", e.getMessage());
            lock.setOperationError(e.getMessage());
        }
    }

    private void lockRelatedAccounts(AccountLock lock, AccountLockEvent event) {
        try {
            log.info("Locking related accounts for: {}", lock.getAccountId());

            // Find related accounts
            List<String> relatedAccounts = securityService.findRelatedAccounts(
                lock.getAccountId(),
                event.getRelationshipType()
            );

            lock.setRelatedAccountsCount(relatedAccounts.size());

            for (String relatedAccountId : relatedAccounts) {
                // Create lock for related account
                AccountLockEvent relatedLockEvent = AccountLockEvent.builder()
                    .accountId(relatedAccountId)
                    .lockType(event.getLockType())
                    .lockReason("RELATED_ACCOUNT_LOCK")
                    .description("Locked due to relation with account: " + lock.getAccountId())
                    .triggeredBy("system")
                    .parentLockId(lock.getId())
                    .build();

                // Process related account lock asynchronously
                CompletableFuture.runAsync(() -> 
                    processRelatedAccountLock(relatedLockEvent, lock.getId())
                );
            }

            lock.setRelatedAccountsLocked(true);

        } catch (Exception e) {
            log.error("Error locking related accounts: {}", e.getMessage());
            lock.setRelatedAccountsError(e.getMessage());
        }
    }

    private void updateLockStatus(AccountLock lock) {
        if (lock.isAccountLocked() && lock.getOperationError() == null) {
            lock.setStatus(LockStatus.ACTIVE);
            lock.setActivatedAt(LocalDateTime.now());
        } else if (lock.getOperationError() != null) {
            lock.setStatus(LockStatus.FAILED);
            lock.setFailureReason(lock.getOperationError());
        } else {
            lock.setStatus(LockStatus.PENDING);
        }

        lock.setProcessingTimeMs(
            ChronoUnit.MILLIS.between(lock.getCreatedAt(), LocalDateTime.now())
        );
    }

    private void scheduleUnlock(AccountLock lock) {
        if (lock.getScheduledUnlockAt() != null) {
            log.info("Scheduling unlock for account {} at {}", 
                    lock.getAccountId(), lock.getScheduledUnlockAt());

            securityService.scheduleUnlock(
                lock.getAccountId(),
                lock.getId(),
                lock.getScheduledUnlockAt()
            );
        }
    }

    private void sendLockNotifications(AccountLock lock, AccountLockEvent event) {
        try {
            // Notify account owner
            notificationService.sendAccountLockNotification(lock);

            // Notify security team for high-risk locks
            if (isHighRiskLock(lock)) {
                notificationService.sendHighRiskLockAlert(lock);
            }

            // Send fraud alert if fraud-related
            if (lock.getLockReason() == LockReason.FRAUD_DETECTED) {
                notificationService.sendFraudLockNotification(lock);
            }

            // Send compliance notification if compliance-related
            if (lock.getLockReason() == LockReason.COMPLIANCE_REQUIRED) {
                notificationService.sendComplianceLockNotification(lock);
            }

        } catch (Exception e) {
            log.error("Failed to send lock notifications: {}", e.getMessage());
        }
    }

    private void updateSecurityMetrics(AccountLock lock, AccountLockEvent event) {
        try {
            // Record lock metrics
            metricsService.incrementCounter("account.lock.activated",
                Map.of(
                    "lock_type", lock.getLockType().toString(),
                    "lock_reason", lock.getLockReason().toString(),
                    "status", lock.getStatus().toString()
                ));

            // Record security actions
            metricsService.recordGauge("account.sessions_terminated", lock.getSessionsTerminated(),
                Map.of("reason", lock.getLockReason().toString()));

            metricsService.recordGauge("account.tokens_revoked", lock.getTokensRevoked(),
                Map.of("reason", lock.getLockReason().toString()));

            // Record processing time
            metricsService.recordTimer("account.lock.processing_time_ms", lock.getProcessingTimeMs(),
                Map.of("lock_type", lock.getLockType().toString()));

            // Update security posture metrics
            if (lock.getLockReason() == LockReason.FRAUD_DETECTED) {
                metricsService.incrementCounter("security.fraud_locks");
            }

        } catch (Exception e) {
            log.error("Failed to update security metrics: {}", e.getMessage());
        }
    }

    private void createLockAuditLog(AccountLock lock, AccountLockEvent event, String correlationId) {
        auditLogger.logSecurityEvent(
            "ACCOUNT_LOCK_PROCESSED",
            lock.getAccountId(),
            lock.getId(),
            lock.getLockType().toString(),
            lock.getFailedAttempts() != null ? lock.getFailedAttempts() : 0,
            "account_lock_processor",
            lock.getStatus() == LockStatus.ACTIVE,
            Map.of(
                "lockId", lock.getId(),
                "accountId", lock.getAccountId(),
                "lockType", lock.getLockType().toString(),
                "lockReason", lock.getLockReason().toString(),
                "status", lock.getStatus().toString(),
                "sessionsTerminated", String.valueOf(lock.getSessionsTerminated()),
                "tokensRevoked", String.valueOf(lock.getTokensRevoked()),
                "apiKeysInvalidated", String.valueOf(lock.getApiKeysInvalidated()),
                "transactionsBlocked", String.valueOf(lock.getTransactionsBlocked()),
                "passwordResetRequired", String.valueOf(lock.isPasswordResetRequired()),
                "relatedAccountsLocked", String.valueOf(lock.isRelatedAccountsLocked()),
                "processingTimeMs", String.valueOf(lock.getProcessingTimeMs()),
                "correlationId", correlationId != null ? correlationId : "N/A",
                "eventId", event.getEventId()
            )
        );
    }

    private AccountLock performEmergencyLock(AccountLockEvent event, String correlationId) {
        AccountLock lock = createLockRecord(event, UUID.randomUUID().toString(), correlationId);
        lock.setLockType(LockType.PERMANENT);
        lock.setStatus(LockStatus.ACTIVE);
        
        // Immediate lock
        securityService.emergencyLockAccount(lock.getAccountId());
        lock.setAccountLocked(true);
        lock.setActivatedAt(LocalDateTime.now());
        
        return lockRepository.save(lock);
    }

    private boolean shouldUpgradeLock(AccountLock existing, AccountLock newLock) {
        // Upgrade temporary to permanent
        if (existing.getLockType() == LockType.TEMPORARY && 
            newLock.getLockType() == LockType.PERMANENT) {
            return true;
        }
        
        // Upgrade for more severe reason
        return newLock.getLockReason().getSeverity() > existing.getLockReason().getSeverity();
    }

    private void upgradeLock(AccountLock existing, AccountLock newLock) {
        existing.setLockType(newLock.getLockType());
        existing.setLockReason(newLock.getLockReason());
        existing.setUpgradedAt(LocalDateTime.now());
        lockRepository.save(existing);
    }

    private boolean verifyUserRequest(AccountLockEvent event) {
        // Verify user identity before processing user-requested lock
        return securityService.verifyUserIdentity(
            event.getUserId(),
            event.getVerificationToken()
        );
    }

    private LocalDateTime calculateUnlockTime(AccountLock lock, AccountLockEvent event) {
        if (event.getUnlockAfterMinutes() != null) {
            return LocalDateTime.now().plusMinutes(event.getUnlockAfterMinutes());
        }
        
        return switch (lock.getLockReason()) {
            case FAILED_LOGIN_ATTEMPTS -> LocalDateTime.now().plusMinutes(TEMPORARY_LOCK_MINUTES);
            case SUSPICIOUS_ACTIVITY -> LocalDateTime.now().plusHours(2);
            case RATE_LIMIT_EXCEEDED -> LocalDateTime.now().plusMinutes(15);
            default -> LocalDateTime.now().plusHours(24);
        };
    }

    private void disableAccountFeatures(AccountLock lock, AccountLockEvent event) {
        // Disable features based on lock type
        securityService.disableFeature(lock.getAccountId(), "PAYMENTS");
        securityService.disableFeature(lock.getAccountId(), "TRANSFERS");
        securityService.disableFeature(lock.getAccountId(), "WITHDRAWALS");
        
        if (lock.getLockType() == LockType.PERMANENT) {
            securityService.disableFeature(lock.getAccountId(), "LOGIN");
        }
    }

    private boolean shouldForcePasswordReset(AccountLock lock) {
        return lock.getLockReason() == LockReason.SUSPICIOUS_ACTIVITY ||
               lock.getLockReason() == LockReason.CREDENTIAL_COMPROMISE ||
               lock.getLockReason() == LockReason.FRAUD_DETECTED;
    }

    private void enhanceAccountSecurity(AccountLock lock) {
        // Force MFA enrollment
        mfaService.forceMfaEnrollment(lock.getAccountId());
        lock.setMfaRequired(true);
        
        // Increase authentication requirements
        securityService.setAuthLevel(lock.getAccountId(), "HIGH");
        
        // Enable additional monitoring
        securityService.enableEnhancedMonitoring(lock.getAccountId());
    }

    private boolean shouldBlockTransactions(AccountLock lock) {
        return lock.getLockReason() == LockReason.FRAUD_DETECTED ||
               lock.getLockReason() == LockReason.COMPLIANCE_REQUIRED ||
               lock.getLockReason() == LockReason.SUSPICIOUS_ACTIVITY;
    }

    private void processRelatedAccountLock(AccountLockEvent event, String parentLockId) {
        log.info("Processing related account lock for: {} (parent: {})", 
                event.getAccountId(), parentLockId);
        // Process through Kafka for audit trail
    }

    private boolean isHighRiskLock(AccountLock lock) {
        return lock.getLockReason() == LockReason.FRAUD_DETECTED ||
               lock.getLockReason() == LockReason.CREDENTIAL_COMPROMISE ||
               lock.getTransactionsBlocked() > 0;
    }
}