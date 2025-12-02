package com.waqiti.account.kafka;

import com.waqiti.account.domain.Account;
import com.waqiti.account.domain.AccountSuspension;
import com.waqiti.account.repository.AccountRepository;
import com.waqiti.account.repository.AccountSuspensionRepository;
import com.waqiti.account.service.NotificationService;
import com.waqiti.account.service.AuditService;
import com.waqiti.common.events.AccountSuspensionEvent;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.metrics.MetricsService;
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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CRITICAL: Account Suspension Event Consumer
 * 
 * Handles account suspensions for security and compliance reasons.
 * This consumer is essential for:
 * - Immediate account lockdown for fraud/security threats
 * - Compliance-driven suspensions (AML, sanctions)
 * - Protecting customer funds during investigations
 * - Regulatory reporting requirements
 * 
 * SECURITY IMPACT: Without this, compromised accounts remain active
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountSuspensionEventConsumer {

    private final AccountRepository accountRepository;
    private final AccountSuspensionRepository suspensionRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;
    private final MetricsService metricsService;

    private static final String CONSUMER_GROUP = "account-suspension-processor";

    @KafkaListener(
        topics = "account-suspension-events",
        groupId = CONSUMER_GROUP,
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(
        value = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 500, multiplier = 2)
    )
    @Transactional
    public void handleAccountSuspension(
            @Payload AccountSuspensionEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String idempotencyKey = generateIdempotencyKey(event);
        
        log.warn("Processing CRITICAL account suspension: accountId={}, reason={}, severity={}", 
                event.getAccountId(), event.getSuspensionReason(), event.getSeverity());
        
        metricsService.incrementCounter("account.suspension.received");
        
        try {
            // Check idempotency
            if (idempotencyService.isAlreadyProcessed(idempotencyKey)) {
                log.info("Account suspension already processed: {}", event.getAccountId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Find account
            Account account = accountRepository.findById(UUID.fromString(event.getAccountId()))
                .orElseThrow(() -> new IllegalStateException("Account not found: " + event.getAccountId()));
            
            // Check if already suspended
            if (account.getStatus() == Account.AccountStatus.SUSPENDED) {
                log.info("Account already suspended: {}", event.getAccountId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Create suspension record
            AccountSuspension suspension = createSuspensionRecord(event, account);
            
            // Update account status
            Account previousStatus = account.getStatus();
            account.setStatus(Account.AccountStatus.SUSPENDED);
            account.setSuspendedAt(LocalDateTime.now());
            account.setSuspensionReason(event.getSuspensionReason());
            
            // Block all transaction capabilities
            blockAccountCapabilities(account);
            
            // Save changes
            accountRepository.save(account);
            suspensionRepository.save(suspension);
            
            // Handle critical suspensions
            if ("CRITICAL".equals(event.getSeverity()) || 
                "FRAUD".equals(event.getSuspensionType()) ||
                "SECURITY_BREACH".equals(event.getSuspensionType())) {
                handleCriticalSuspension(account, event);
            }
            
            // Send notifications
            sendSuspensionNotifications(account, event);
            
            // Create audit trail
            auditService.logAccountSuspension(
                account.getAccountId(),
                previousStatus,
                Account.AccountStatus.SUSPENDED,
                event.getSuspensionReason(),
                event.getInitiatedBy()
            );
            
            // Mark as processed
            idempotencyService.markAsProcessed(idempotencyKey, suspension.getId());
            
            // Update metrics
            metricsService.incrementCounter("account.suspension.success",
                "type", event.getSuspensionType(),
                "severity", event.getSeverity()
            );
            
            log.warn("Account suspended successfully: accountId={}, type={}", 
                    event.getAccountId(), event.getSuspensionType());
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process account suspension for: {}", event.getAccountId(), e);
            metricsService.incrementCounter("account.suspension.error");
            
            // Critical alert for suspension failures
            createSuspensionFailureAlert(event, e);
            
            throw e;
        }
    }

    private AccountSuspension createSuspensionRecord(AccountSuspensionEvent event, Account account) {
        return AccountSuspension.builder()
            .id(UUID.randomUUID())
            .accountId(account.getAccountId())
            .suspensionType(event.getSuspensionType())
            .suspensionReason(event.getSuspensionReason())
            .severity(event.getSeverity())
            .suspendedAt(LocalDateTime.now())
            .suspendedBy(event.getInitiatedBy())
            .expectedDuration(event.getExpectedDuration())
            .reviewDate(calculateReviewDate(event))
            .evidenceDocuments(event.getEvidenceDocuments())
            .complianceCaseId(event.getComplianceCaseId())
            .automaticUnsuspension(event.isAutomaticUnsuspension())
            .status(AccountSuspension.SuspensionStatus.ACTIVE)
            .build();
    }

    private void blockAccountCapabilities(Account account) {
        // Block all financial operations
        account.setCanSendMoney(false);
        account.setCanReceiveMoney(false);
        account.setCanWithdraw(false);
        account.setCanDeposit(false);
        account.setCanTrade(false);
        account.setCanAccessCard(false);
        
        // Block account modifications
        account.setCanUpdateProfile(false);
        account.setCanAddBeneficiaries(false);
        
        log.info("Blocked all capabilities for suspended account: {}", account.getAccountId());
    }

    private void handleCriticalSuspension(Account account, AccountSuspensionEvent event) {
        log.error("CRITICAL SUSPENSION: Account {} suspended for {} - Immediate action required", 
                account.getAccountId(), event.getSuspensionReason());
        
        // Freeze all pending transactions
        freezePendingTransactions(account.getAccountId());
        
        // Notify security team immediately
        notifySecurityTeam(account, event);
        
        // Initiate fund recovery if fraud
        if ("FRAUD".equals(event.getSuspensionType())) {
            initiateFundRecovery(account, event);
        }
        
        // File regulatory report if required
        if (event.isRegulatoryReportingRequired()) {
            fileRegulatoryReport(account, event);
        }
    }

    private void sendSuspensionNotifications(Account account, AccountSuspensionEvent event) {
        // Notify account holder (unless security breach)
        if (!"SECURITY_BREACH".equals(event.getSuspensionType())) {
            notificationService.sendAccountSuspensionNotification(
                account.getAccountHolderId(),
                account.getAccountId(),
                event.getSuspensionReason(),
                event.getExpectedDuration()
            );
        }
        
        // Notify compliance team
        notificationService.notifyComplianceTeam(
            "ACCOUNT_SUSPENDED",
            String.format("Account %s suspended for %s", 
                account.getAccountNumber(), event.getSuspensionReason())
        );
    }

    private void createSuspensionFailureAlert(AccountSuspensionEvent event, Exception error) {
        log.error("CRITICAL: Failed to suspend account {} - Security risk!", event.getAccountId());
        
        auditService.createCriticalAlert(
            "ACCOUNT_SUSPENSION_FAILURE",
            String.format("Failed to suspend account %s - Manual intervention required", 
                         event.getAccountId()),
            error.getMessage()
        );
    }

    private LocalDateTime calculateReviewDate(AccountSuspensionEvent event) {
        if (event.getExpectedDuration() != null) {
            return LocalDateTime.now().plus(event.getExpectedDuration());
        }
        // Default review in 30 days
        return LocalDateTime.now().plusDays(30);
    }

    private void freezePendingTransactions(UUID accountId) {
        // Implementation would freeze all pending transactions
        log.info("Freezing all pending transactions for account: {}", accountId);
    }

    private void notifySecurityTeam(Account account, AccountSuspensionEvent event) {
        // Implementation would send immediate alert to security team
        log.warn("Notifying security team about critical suspension: {}", account.getAccountId());
    }

    private void initiateFundRecovery(Account account, AccountSuspensionEvent event) {
        // Implementation would start fund recovery process
        log.info("Initiating fund recovery for fraud case: {}", account.getAccountId());
    }

    private void fileRegulatoryReport(Account account, AccountSuspensionEvent event) {
        // Implementation would file regulatory report
        log.info("Filing regulatory report for account suspension: {}", account.getAccountId());
    }

    private String generateIdempotencyKey(AccountSuspensionEvent event) {
        return String.format("suspension-%s-%s", 
                event.getAccountId(), 
                event.getEventId());
    }
}