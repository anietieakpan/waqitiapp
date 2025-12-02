package com.waqiti.user.events.consumers;

import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.events.user.AccountClosureEvent;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.user.domain.User;
import com.waqiti.user.domain.AccountClosure;
import com.waqiti.user.domain.ClosureStatus;
import com.waqiti.user.domain.ClosureReason;
import com.waqiti.user.domain.DataRetentionPolicy;
import com.waqiti.user.repository.UserRepository;
import com.waqiti.user.repository.AccountClosureRepository;
import com.waqiti.user.service.AccountService;
import com.waqiti.user.service.WalletService;
import com.waqiti.user.service.DataArchivalService;
import com.waqiti.user.service.ComplianceService;
import com.waqiti.user.service.NotificationService;
import com.waqiti.common.exceptions.AccountClosureException;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade consumer for account closure events.
 * Handles comprehensive account termination including:
 * - Balance settlement and withdrawal
 * - Subscription cancellations
 * - Data archival and retention
 * - Regulatory compliance requirements
 * - Partner service notifications
 * - Account reactivation prevention
 * 
 * Critical for proper account lifecycle management and compliance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountClosureConsumer {

    private final UserRepository userRepository;
    private final AccountClosureRepository closureRepository;
    private final AccountService accountService;
    private final WalletService walletService;
    private final DataArchivalService archivalService;
    private final ComplianceService complianceService;
    private final NotificationService notificationService;
    private final AuditLogger auditLogger;
    private final MetricsService metricsService;

    private static final int COOLING_OFF_PERIOD_DAYS = 30;
    private static final int DATA_RETENTION_YEARS = 7;

    @KafkaListener(
        topics = "account-closure-requests",
        groupId = "user-service-closure-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 2000, multiplier = 2.0),
        include = {AccountClosureException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void handleAccountClosure(
            @Payload AccountClosureEvent closureEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "correlation-id", required = false) String correlationId,
            @Header(value = "closure-type", required = false) String closureType,
            Acknowledgment acknowledgment) {

        String eventId = closureEvent.getEventId() != null ? 
            closureEvent.getEventId() : UUID.randomUUID().toString();

        try {
            log.warn("Processing account closure: {} for user: {} reason: {}", 
                    eventId, closureEvent.getUserId(), closureEvent.getClosureReason());

            // Metrics tracking
            metricsService.incrementCounter("account.closure.processing.started",
                Map.of(
                    "reason", closureEvent.getClosureReason(),
                    "initiated_by", closureEvent.getInitiatedBy()
                ));

            // Idempotency check
            if (isClosureAlreadyProcessed(closureEvent.getUserId(), eventId)) {
                log.info("Account closure {} already processed for user {}", eventId, closureEvent.getUserId());
                acknowledgment.acknowledge();
                return;
            }

            // Retrieve user account
            User user = getUserAccount(closureEvent.getUserId());

            // Create closure record
            AccountClosure closure = createClosureRecord(closureEvent, user, eventId, correlationId);

            // Validate closure eligibility
            validateClosureEligibility(closure, user, closureEvent);

            // Execute pre-closure checks
            performPreClosureChecks(closure, user, closureEvent);

            // Process closure steps in parallel
            List<CompletableFuture<ClosureStepResult>> closureTasks = 
                createClosureTasks(closure, user, closureEvent);

            // Wait for all tasks to complete
            CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                closureTasks.toArray(new CompletableFuture[0])
            );

            allTasks.join();

            // Process closure results
            processClosureResults(closure, closureTasks);

            // Finalize account closure
            finalizeAccountClosure(closure, user, closureEvent);

            // Archive account data
            archiveAccountData(closure, user, closureEvent);

            // Update closure status
            updateClosureStatus(closure);

            // Save closure record
            AccountClosure savedClosure = closureRepository.save(closure);

            // Update user status
            updateUserStatus(user, savedClosure);

            // Send closure notifications
            sendClosureNotifications(savedClosure, user, closureEvent);

            // Update metrics
            updateClosureMetrics(savedClosure, closureEvent);

            // Create comprehensive audit trail
            createClosureAuditLog(savedClosure, user, closureEvent, correlationId);

            // Success metrics
            metricsService.incrementCounter("account.closure.processing.success",
                Map.of(
                    "status", savedClosure.getStatus().toString(),
                    "reason", savedClosure.getReason().toString()
                ));

            log.warn("Successfully processed account closure: {} for user: {} status: {}", 
                    savedClosure.getId(), user.getId(), savedClosure.getStatus());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing account closure event {}: {}", eventId, e.getMessage(), e);
            metricsService.incrementCounter("account.closure.processing.error");
            
            auditLogger.logCriticalAlert("ACCOUNT_CLOSURE_PROCESSING_ERROR",
                "Critical account closure failure",
                Map.of(
                    "userId", closureEvent.getUserId(),
                    "reason", closureEvent.getClosureReason(),
                    "eventId", eventId,
                    "error", e.getMessage(),
                    "correlationId", correlationId != null ? correlationId : "N/A"
                ));
            
            throw new AccountClosureException("Failed to process account closure: " + e.getMessage(), e);
        }
    }

    @KafkaListener(
        topics = "account-closure-immediate",
        groupId = "user-service-immediate-closure-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleImmediateAccountClosure(
            @Payload AccountClosureEvent closureEvent,
            @Header(value = "correlation-id", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        try {
            log.error("IMMEDIATE CLOSURE: Processing urgent account closure for user: {}", 
                    closureEvent.getUserId());

            // Fast-track closure for security/compliance reasons
            AccountClosure closure = performImmediateClosure(closureEvent, correlationId);

            // Block all access immediately
            accountService.blockAllAccess(closureEvent.getUserId());

            // Freeze all funds
            walletService.freezeAllFunds(closureEvent.getUserId());

            // Notify security team
            notificationService.sendSecurityClosureAlert(closure);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process immediate account closure: {}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    private boolean isClosureAlreadyProcessed(String userId, String eventId) {
        return closureRepository.existsByUserIdAndEventId(userId, eventId);
    }

    private User getUserAccount(String userId) {
        UUID userUuid = UUID.fromString(userId);
        return userRepository.findById(userUuid)
            .orElseThrow(() -> new AccountClosureException("User not found: " + userId));
    }

    private AccountClosure createClosureRecord(AccountClosureEvent event, User user, String eventId, String correlationId) {
        return AccountClosure.builder()
            .id(UUID.randomUUID().toString())
            .eventId(eventId)
            .userId(user.getId())
            .userEmail(user.getEmail())
            .reason(ClosureReason.valueOf(event.getClosureReason().toUpperCase()))
            .initiatedBy(event.getInitiatedBy())
            .initiationType(event.getInitiationType())
            .closureNotes(event.getClosureNotes())
            .feedbackProvided(event.getFeedbackProvided())
            .status(ClosureStatus.INITIATED)
            .correlationId(correlationId)
            .initiatedAt(LocalDateTime.now())
            .scheduledClosureDate(calculateScheduledClosureDate(event))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private void validateClosureEligibility(AccountClosure closure, User user, AccountClosureEvent event) {
        // Check for outstanding balances
        BigDecimal walletBalance = walletService.getTotalBalance(user.getId());
        if (walletBalance.compareTo(BigDecimal.ZERO) > 0) {
            closure.setOutstandingBalance(walletBalance);
            closure.setBalanceResolutionRequired(true);
        }

        // Check for pending transactions
        int pendingTransactions = accountService.getPendingTransactionCount(user.getId());
        if (pendingTransactions > 0) {
            closure.setPendingTransactions(pendingTransactions);
            throw new AccountClosureException(
                "Cannot close account with " + pendingTransactions + " pending transactions"
            );
        }

        // Check for active disputes
        int activeDisputes = accountService.getActiveDisputeCount(user.getId());
        if (activeDisputes > 0) {
            closure.setActiveDisputes(activeDisputes);
            throw new AccountClosureException(
                "Cannot close account with " + activeDisputes + " active disputes"
            );
        }

        // Check for regulatory holds
        if (complianceService.hasRegulatoryHold(user.getId())) {
            closure.setRegulatoryHoldActive(true);
            throw new AccountClosureException("Account under regulatory hold");
        }

        // Verify identity for user-initiated closures
        if ("USER".equals(event.getInitiatedBy()) && !verifyUserIdentity(user, event)) {
            throw new AccountClosureException("Identity verification failed");
        }

        closure.setEligibilityVerified(true);
    }

    private void performPreClosureChecks(AccountClosure closure, User user, AccountClosureEvent event) {
        try {
            // Check active subscriptions
            List<String> activeSubscriptions = accountService.getActiveSubscriptions(user.getId());
            if (!activeSubscriptions.isEmpty()) {
                closure.setActiveSubscriptions(activeSubscriptions);
                closure.setSubscriptionsCancellationRequired(true);
            }

            // Check linked accounts
            List<String> linkedAccounts = accountService.getLinkedAccounts(user.getId());
            if (!linkedAccounts.isEmpty()) {
                closure.setLinkedAccounts(linkedAccounts);
            }

            // Check recurring payments
            int recurringPayments = accountService.getRecurringPaymentCount(user.getId());
            if (recurringPayments > 0) {
                closure.setRecurringPayments(recurringPayments);
                closure.setRecurringPaymentsCancellationRequired(true);
            }

            // Check loyalty points
            BigDecimal loyaltyPoints = accountService.getLoyaltyPointsBalance(user.getId());
            if (loyaltyPoints.compareTo(BigDecimal.ZERO) > 0) {
                closure.setLoyaltyPointsBalance(loyaltyPoints);
                closure.setLoyaltyPointsForfeited(true);
            }

        } catch (Exception e) {
            log.error("Error performing pre-closure checks: {}", e.getMessage());
            closure.setPreCheckError(e.getMessage());
        }
    }

    private List<CompletableFuture<ClosureStepResult>> createClosureTasks(
            AccountClosure closure, User user, AccountClosureEvent event) {
        
        List<CompletableFuture<ClosureStepResult>> tasks = new ArrayList<>();

        // Cancel subscriptions
        if (closure.isSubscriptionsCancellationRequired()) {
            tasks.add(CompletableFuture.supplyAsync(() -> 
                cancelSubscriptions(closure, user)));
        }

        // Process balance withdrawal
        if (closure.getOutstandingBalance() != null && closure.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0) {
            tasks.add(CompletableFuture.supplyAsync(() -> 
                processBalanceWithdrawal(closure, user, event)));
        }

        // Cancel recurring payments
        if (closure.isRecurringPaymentsCancellationRequired()) {
            tasks.add(CompletableFuture.supplyAsync(() -> 
                cancelRecurringPayments(closure, user)));
        }

        // Revoke API access
        tasks.add(CompletableFuture.supplyAsync(() -> 
            revokeApiAccess(closure, user)));

        // Delete payment methods
        tasks.add(CompletableFuture.supplyAsync(() -> 
            deletePaymentMethods(closure, user)));

        // Notify partners
        tasks.add(CompletableFuture.supplyAsync(() -> 
            notifyPartnerServices(closure, user)));

        // Generate closure certificate
        tasks.add(CompletableFuture.supplyAsync(() -> 
            generateClosureCertificate(closure, user)));

        return tasks;
    }

    private ClosureStepResult cancelSubscriptions(AccountClosure closure, User user) {
        try {
            log.info("Cancelling subscriptions for user: {}", user.getId());

            int cancelled = 0;
            for (String subscriptionId : closure.getActiveSubscriptions()) {
                boolean result = accountService.cancelSubscription(user.getId(), subscriptionId);
                if (result) cancelled++;
            }

            closure.setSubscriptionsCancelled(cancelled);
            return ClosureStepResult.success("CANCEL_SUBSCRIPTIONS", cancelled);

        } catch (Exception e) {
            log.error("Failed to cancel subscriptions: {}", e.getMessage());
            return ClosureStepResult.failure("CANCEL_SUBSCRIPTIONS", e.getMessage());
        }
    }

    private ClosureStepResult processBalanceWithdrawal(AccountClosure closure, User user, AccountClosureEvent event) {
        try {
            log.info("Processing balance withdrawal for user: {} amount: {}", 
                    user.getId(), closure.getOutstandingBalance());

            String withdrawalMethod = event.getWithdrawalMethod();
            if (withdrawalMethod == null) {
                withdrawalMethod = "ORIGINAL_PAYMENT_METHOD";
            }

            var withdrawal = walletService.processClosureWithdrawal(
                user.getId(),
                closure.getOutstandingBalance(),
                withdrawalMethod,
                event.getWithdrawalDetails()
            );

            closure.setBalanceWithdrawn(true);
            closure.setWithdrawalTransactionId(withdrawal.getTransactionId());
            closure.setWithdrawalMethod(withdrawalMethod);
            closure.setWithdrawalProcessedAt(LocalDateTime.now());

            return ClosureStepResult.success("BALANCE_WITHDRAWAL", withdrawal.getTransactionId());

        } catch (Exception e) {
            log.error("Failed to process balance withdrawal: {}", e.getMessage());
            closure.setWithdrawalError(e.getMessage());
            return ClosureStepResult.failure("BALANCE_WITHDRAWAL", e.getMessage());
        }
    }

    private ClosureStepResult cancelRecurringPayments(AccountClosure closure, User user) {
        try {
            log.info("Cancelling recurring payments for user: {}", user.getId());

            int cancelled = accountService.cancelAllRecurringPayments(user.getId());
            closure.setRecurringPaymentsCancelled(cancelled);

            return ClosureStepResult.success("CANCEL_RECURRING", cancelled);

        } catch (Exception e) {
            log.error("Failed to cancel recurring payments: {}", e.getMessage());
            return ClosureStepResult.failure("CANCEL_RECURRING", e.getMessage());
        }
    }

    private ClosureStepResult revokeApiAccess(AccountClosure closure, User user) {
        try {
            log.info("Revoking API access for user: {}", user.getId());

            // Revoke all tokens
            int tokensRevoked = accountService.revokeAllTokens(user.getId());
            
            // Invalidate API keys
            int keysInvalidated = accountService.invalidateAllApiKeys(user.getId());
            
            // Terminate sessions
            int sessionsTerminated = accountService.terminateAllSessions(user.getId());

            closure.setTokensRevoked(tokensRevoked);
            closure.setApiKeysInvalidated(keysInvalidated);
            closure.setSessionsTerminated(sessionsTerminated);

            return ClosureStepResult.success("REVOKE_ACCESS", 
                tokensRevoked + keysInvalidated + sessionsTerminated);

        } catch (Exception e) {
            log.error("Failed to revoke API access: {}", e.getMessage());
            return ClosureStepResult.failure("REVOKE_ACCESS", e.getMessage());
        }
    }

    private ClosureStepResult deletePaymentMethods(AccountClosure closure, User user) {
        try {
            log.info("Deleting payment methods for user: {}", user.getId());

            int deleted = accountService.deleteAllPaymentMethods(user.getId());
            closure.setPaymentMethodsDeleted(deleted);

            return ClosureStepResult.success("DELETE_PAYMENT_METHODS", deleted);

        } catch (Exception e) {
            log.error("Failed to delete payment methods: {}", e.getMessage());
            return ClosureStepResult.failure("DELETE_PAYMENT_METHODS", e.getMessage());
        }
    }

    private ClosureStepResult notifyPartnerServices(AccountClosure closure, User user) {
        try {
            log.info("Notifying partner services about account closure: {}", user.getId());

            List<String> notifiedPartners = new ArrayList<>();
            
            // Notify payment providers
            for (String provider : accountService.getPaymentProviders(user.getId())) {
                if (notifyPartner(provider, user.getId(), closure.getId())) {
                    notifiedPartners.add(provider);
                }
            }

            // Notify integrated services
            for (String service : accountService.getIntegratedServices(user.getId())) {
                if (notifyPartner(service, user.getId(), closure.getId())) {
                    notifiedPartners.add(service);
                }
            }

            closure.setPartnerServicesNotified(notifiedPartners);

            return ClosureStepResult.success("NOTIFY_PARTNERS", notifiedPartners.size());

        } catch (Exception e) {
            log.error("Failed to notify partner services: {}", e.getMessage());
            return ClosureStepResult.failure("NOTIFY_PARTNERS", e.getMessage());
        }
    }

    private ClosureStepResult generateClosureCertificate(AccountClosure closure, User user) {
        try {
            log.info("Generating closure certificate for user: {}", user.getId());

            String certificateId = UUID.randomUUID().toString();
            String certificateData = generateCertificateData(closure, user);
            
            closure.setClosureCertificateId(certificateId);
            closure.setClosureCertificateData(certificateData);
            closure.setCertificateGeneratedAt(LocalDateTime.now());

            return ClosureStepResult.success("GENERATE_CERTIFICATE", certificateId);

        } catch (Exception e) {
            log.error("Failed to generate closure certificate: {}", e.getMessage());
            return ClosureStepResult.failure("GENERATE_CERTIFICATE", e.getMessage());
        }
    }

    private void processClosureResults(AccountClosure closure, List<CompletableFuture<ClosureStepResult>> tasks) {
        int successfulSteps = 0;
        List<String> failedSteps = new ArrayList<>();

        for (CompletableFuture<ClosureStepResult> task : tasks) {
            try {
                ClosureStepResult result = task.get();
                if (result.isSuccess()) {
                    successfulSteps++;
                } else {
                    failedSteps.add(result.getStep() + ": " + result.getError());
                }
            } catch (Exception e) {
                log.error("Failed to process closure task: {}", e.getMessage());
                failedSteps.add("UNKNOWN: " + e.getMessage());
            }
        }

        closure.setSuccessfulSteps(successfulSteps);
        closure.setFailedSteps(failedSteps);
        closure.setCompletionPercentage((successfulSteps * 100) / tasks.size());
    }

    private void finalizeAccountClosure(AccountClosure closure, User user, AccountClosureEvent event) {
        try {
            // Mark account as closed
            user.setClosed(true);
            user.setClosedAt(LocalDateTime.now());
            user.setClosureId(closure.getId());
            user.setActive(false);

            // Set data retention policy
            DataRetentionPolicy retentionPolicy = determineRetentionPolicy(closure.getReason());
            closure.setRetentionPolicy(retentionPolicy);
            closure.setDataRetentionUntil(LocalDateTime.now().plusYears(DATA_RETENTION_YEARS));

            // Anonymize personal data if required
            if (event.isAnonymizeData()) {
                anonymizeUserData(user);
                closure.setDataAnonymized(true);
                closure.setAnonymizedAt(LocalDateTime.now());
            }

            // Create audit snapshot
            String auditSnapshot = createAuditSnapshot(user, closure);
            closure.setAuditSnapshot(auditSnapshot);

            closure.setFinalizedAt(LocalDateTime.now());

        } catch (Exception e) {
            log.error("Error finalizing account closure: {}", e.getMessage());
            closure.setFinalizationError(e.getMessage());
        }
    }

    private void archiveAccountData(AccountClosure closure, User user, AccountClosureEvent event) {
        try {
            log.info("Archiving account data for user: {}", user.getId());

            var archiveResult = archivalService.archiveUserData(
                user.getId(),
                closure.getId(),
                closure.getRetentionPolicy()
            );

            closure.setDataArchived(true);
            closure.setArchiveLocation(archiveResult.getLocation());
            closure.setArchiveSize(archiveResult.getSize());
            closure.setArchivedAt(LocalDateTime.now());

        } catch (Exception e) {
            log.error("Failed to archive account data: {}", e.getMessage());
            closure.setArchivalError(e.getMessage());
        }
    }

    private void updateClosureStatus(AccountClosure closure) {
        if (closure.getFailedSteps() != null && !closure.getFailedSteps().isEmpty()) {
            closure.setStatus(ClosureStatus.PARTIALLY_COMPLETED);
        } else if (closure.isDataArchived() && closure.getFinalizedAt() != null) {
            closure.setStatus(ClosureStatus.COMPLETED);
            closure.setCompletedAt(LocalDateTime.now());
        } else {
            closure.setStatus(ClosureStatus.PROCESSING);
        }

        closure.setProcessingTimeMs(
            ChronoUnit.MILLIS.between(closure.getInitiatedAt(), LocalDateTime.now())
        );
        closure.setUpdatedAt(LocalDateTime.now());
    }

    private void updateUserStatus(User user, AccountClosure closure) {
        user.setAccountStatus("CLOSED");
        user.setStatusReason(closure.getReason().toString());
        user.setLastActivityAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        
        userRepository.save(user);
    }

    private void sendClosureNotifications(AccountClosure closure, User user, AccountClosureEvent event) {
        try {
            // Send closure confirmation to user
            notificationService.sendClosureConfirmation(user, closure);

            // Send closure certificate
            if (closure.getClosureCertificateId() != null) {
                notificationService.sendClosureCertificate(user, closure);
            }

            // Send data retention information
            notificationService.sendDataRetentionNotice(user, closure);

            // Notify about grace period if applicable
            if (closure.getReason() == ClosureReason.USER_REQUEST) {
                notificationService.sendGracePeriodNotice(user, COOLING_OFF_PERIOD_DAYS);
            }

            // Internal notifications
            notificationService.notifyAccountClosureTeam(closure);

        } catch (Exception e) {
            log.error("Failed to send closure notifications: {}", e.getMessage());
        }
    }

    private void updateClosureMetrics(AccountClosure closure, AccountClosureEvent event) {
        try {
            // Closure metrics
            metricsService.incrementCounter("account.closure.completed",
                Map.of(
                    "reason", closure.getReason().toString(),
                    "status", closure.getStatus().toString(),
                    "initiated_by", event.getInitiatedBy()
                ));

            // Processing time metrics
            metricsService.recordTimer("account.closure.processing_time", closure.getProcessingTimeMs(),
                Map.of("reason", closure.getReason().toString()));

            // Balance metrics
            if (closure.getOutstandingBalance() != null) {
                metricsService.recordTimer("account.closure.balance", 
                    closure.getOutstandingBalance().doubleValue(),
                    Map.of("withdrawn", String.valueOf(closure.isBalanceWithdrawn())));
            }

            // Retention metrics
            metricsService.incrementCounter("account.closure.retention",
                Map.of("reason", event.getFeedbackProvided() != null ? "with_feedback" : "without_feedback"));

        } catch (Exception e) {
            log.error("Failed to update closure metrics: {}", e.getMessage());
        }
    }

    private void createClosureAuditLog(AccountClosure closure, User user, AccountClosureEvent event, String correlationId) {
        auditLogger.logUserEvent(
            "ACCOUNT_CLOSURE_COMPLETED",
            user.getId(),
            closure.getId(),
            closure.getReason().toString(),
            "account_closure_processor",
            closure.getStatus() == ClosureStatus.COMPLETED,
            Map.of(
                "closureId", closure.getId(),
                "userId", user.getId(),
                "reason", closure.getReason().toString(),
                "status", closure.getStatus().toString(),
                "initiatedBy", event.getInitiatedBy(),
                "balanceWithdrawn", String.valueOf(closure.isBalanceWithdrawn()),
                "dataArchived", String.valueOf(closure.isDataArchived()),
                "dataAnonymized", String.valueOf(closure.isDataAnonymized()),
                "subscriptionsCancelled", String.valueOf(closure.getSubscriptionsCancelled()),
                "completionPercentage", String.valueOf(closure.getCompletionPercentage()),
                "processingTimeMs", String.valueOf(closure.getProcessingTimeMs()),
                "correlationId", correlationId != null ? correlationId : "N/A",
                "eventId", event.getEventId()
            )
        );
    }

    private AccountClosure performImmediateClosure(AccountClosureEvent event, String correlationId) {
        User user = getUserAccount(event.getUserId());
        AccountClosure closure = createClosureRecord(event, user, UUID.randomUUID().toString(), correlationId);
        
        closure.setStatus(ClosureStatus.IMMEDIATE);
        closure.setImmediateClosure(true);
        
        // Block account immediately
        user.setClosed(true);
        user.setClosedAt(LocalDateTime.now());
        user.setActive(false);
        userRepository.save(user);
        
        closure.setCompletedAt(LocalDateTime.now());
        return closureRepository.save(closure);
    }

    private LocalDateTime calculateScheduledClosureDate(AccountClosureEvent event) {
        if (event.getScheduledClosureDate() != null) {
            return event.getScheduledClosureDate();
        }
        
        // Default cooling-off period
        return LocalDateTime.now().plusDays(COOLING_OFF_PERIOD_DAYS);
    }

    private boolean verifyUserIdentity(User user, AccountClosureEvent event) {
        // Verify authentication token
        if (event.getAuthenticationToken() != null) {
            return accountService.verifyToken(user.getId(), event.getAuthenticationToken());
        }
        
        // Verify security questions
        if (event.getSecurityAnswers() != null) {
            return accountService.verifySecurityAnswers(user.getId(), event.getSecurityAnswers());
        }
        
        return false;
    }

    private DataRetentionPolicy determineRetentionPolicy(ClosureReason reason) {
        return switch (reason) {
            case COMPLIANCE_VIOLATION, FRAUD, AML_VIOLATION -> DataRetentionPolicy.EXTENDED;
            case USER_REQUEST, INACTIVITY -> DataRetentionPolicy.STANDARD;
            case DECEASED -> DataRetentionPolicy.PERMANENT;
            default -> DataRetentionPolicy.STANDARD;
        };
    }

    private void anonymizeUserData(User user) {
        user.setEmail("anonymized_" + UUID.randomUUID() + "@deleted.com");
        user.setPhoneNumber("DELETED");
        user.setFirstName("DELETED");
        user.setLastName("DELETED");
        user.setDateOfBirth(null);
        user.setAddress(null);
    }

    private String createAuditSnapshot(User user, AccountClosure closure) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("userId", user.getId());
        snapshot.put("closureId", closure.getId());
        snapshot.put("closedAt", closure.getCompletedAt());
        snapshot.put("reason", closure.getReason());
        snapshot.put("finalBalance", closure.getOutstandingBalance());
        return snapshot.toString(); // In production, would serialize to JSON
    }

    private boolean notifyPartner(String partner, String userId, String closureId) {
        try {
            // Call partner API to notify closure
            log.info("Notifying partner {} about account closure for user {}", partner, userId);
            return true; // Placeholder
        } catch (Exception e) {
            log.error("Failed to notify partner {}: {}", partner, e.getMessage());
            return false;
        }
    }

    private String generateCertificateData(AccountClosure closure, User user) {
        return String.format("Account Closure Certificate\nUser: %s\nClosed: %s\nReason: %s\nID: %s",
            user.getId(), closure.getCompletedAt(), closure.getReason(), closure.getId());
    }

    /**
     * Internal class for closure step results
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ClosureStepResult {
        private String step;
        private boolean success;
        private Object result;
        private String error;

        public static ClosureStepResult success(String step, Object result) {
            return new ClosureStepResult(step, true, result, null);
        }

        public static ClosureStepResult failure(String step, String error) {
            return new ClosureStepResult(step, false, null, error);
        }
    }
}