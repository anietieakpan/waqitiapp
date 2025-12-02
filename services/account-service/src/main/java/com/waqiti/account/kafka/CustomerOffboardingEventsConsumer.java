package com.waqiti.account.kafka;

import com.waqiti.common.events.CustomerOffboardingEvent;
import com.waqiti.account.domain.OffboardingProcess;
import com.waqiti.account.repository.OffboardingProcessRepository;
import com.waqiti.account.service.AccountClosureService;
import com.waqiti.account.service.DataRetentionService;
import com.waqiti.account.service.GDPRComplianceService;
import com.waqiti.account.metrics.AccountMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class CustomerOffboardingEventsConsumer {
    
    private final OffboardingProcessRepository offboardingRepository;
    private final AccountClosureService accountClosureService;
    private final DataRetentionService dataRetentionService;
    private final GDPRComplianceService gdprService;
    private final AccountMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final int CLOSURE_GRACE_PERIOD_DAYS = 30;
    private static final int DATA_RETENTION_YEARS = 7;
    
    @KafkaListener(
        topics = {"customer-offboarding-events", "account-closure-events", "user-deletion-events"},
        groupId = "account-offboarding-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCustomerOffboardingEvent(
            @Payload CustomerOffboardingEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("offboarding-%s-p%d-o%d", 
            event.getUserId(), partition, offset);
        
        log.info("Processing offboarding event: userId={}, type={}", 
            event.getUserId(), event.getEventType());
        
        try {
            switch (event.getEventType()) {
                case CLOSURE_REQUESTED:
                    processClosureRequested(event, correlationId);
                    break;
                case BALANCE_CLEARED:
                    processBalanceCleared(event, correlationId);
                    break;
                case PENDING_TRANSACTIONS_RESOLVED:
                    processPendingTransactionsResolved(event, correlationId);
                    break;
                case RECURRING_PAYMENTS_CANCELLED:
                    processRecurringPaymentsCancelled(event, correlationId);
                    break;
                case LINKED_ACCOUNTS_DISCONNECTED:
                    processLinkedAccountsDisconnected(event, correlationId);
                    break;
                case DATA_EXPORT_REQUESTED:
                    processDataExportRequested(event, correlationId);
                    break;
                case DATA_EXPORT_COMPLETED:
                    processDataExportCompleted(event, correlationId);
                    break;
                case ACCOUNT_DEACTIVATED:
                    processAccountDeactivated(event, correlationId);
                    break;
                case ACCOUNT_CLOSED:
                    processAccountClosed(event, correlationId);
                    break;
                case DATA_ANONYMIZED:
                    processDataAnonymized(event, correlationId);
                    break;
                case GDPR_DELETION_REQUESTED:
                    processGDPRDeletionRequested(event, correlationId);
                    break;
                case DATA_DELETED:
                    processDataDeleted(event, correlationId);
                    break;
                default:
                    log.warn("Unknown offboarding event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logAccountEvent(
                "OFFBOARDING_EVENT_PROCESSED",
                event.getUserId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "reason", event.getClosureReason() != null ? event.getClosureReason() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process offboarding event: {}", e.getMessage(), e);
            kafkaTemplate.send("customer-offboarding-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processClosureRequested(CustomerOffboardingEvent event, String correlationId) {
        log.info("Account closure requested: userId={}, reason={}", 
            event.getUserId(), event.getClosureReason());
        
        OffboardingProcess process = OffboardingProcess.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .accountId(event.getAccountId())
            .closureReason(event.getClosureReason())
            .closureType(event.getClosureType())
            .requestedAt(LocalDateTime.now())
            .status("INITIATED")
            .gracePeriodEnd(LocalDateTime.now().plusDays(CLOSURE_GRACE_PERIOD_DAYS))
            .balanceCleared(false)
            .pendingTransactionsResolved(false)
            .recurringPaymentsCancelled(false)
            .linkedAccountsDisconnected(false)
            .correlationId(correlationId)
            .build();
        
        offboardingRepository.save(process);
        
        accountClosureService.initiateClosureProcess(event.getUserId());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Account Closure Initiated",
            String.format("Your account closure has been initiated. You have %d days to cancel this request. Please ensure all pending transactions are completed and your balance is cleared.", 
                CLOSURE_GRACE_PERIOD_DAYS),
            correlationId
        );
        
        metricsService.recordClosureRequested(event.getClosureReason());
    }
    
    private void processBalanceCleared(CustomerOffboardingEvent event, String correlationId) {
        log.info("Account balance cleared: userId={}", event.getUserId());
        
        OffboardingProcess process = offboardingRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        process.setBalanceCleared(true);
        process.setBalanceClearedAt(LocalDateTime.now());
        offboardingRepository.save(process);
        
        checkOffboardingCompletion(process);
        metricsService.recordBalanceCleared();
    }
    
    private void processPendingTransactionsResolved(CustomerOffboardingEvent event, String correlationId) {
        log.info("Pending transactions resolved: userId={}, count={}", 
            event.getUserId(), event.getResolvedTransactionCount());
        
        OffboardingProcess process = offboardingRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        process.setPendingTransactionsResolved(true);
        process.setPendingTransactionsResolvedAt(LocalDateTime.now());
        process.setResolvedTransactionCount(event.getResolvedTransactionCount());
        offboardingRepository.save(process);
        
        checkOffboardingCompletion(process);
        metricsService.recordPendingTransactionsResolved(event.getResolvedTransactionCount());
    }
    
    private void processRecurringPaymentsCancelled(CustomerOffboardingEvent event, String correlationId) {
        log.info("Recurring payments cancelled: userId={}, count={}", 
            event.getUserId(), event.getCancelledPaymentCount());
        
        OffboardingProcess process = offboardingRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        process.setRecurringPaymentsCancelled(true);
        process.setRecurringPaymentsCancelledAt(LocalDateTime.now());
        process.setCancelledPaymentCount(event.getCancelledPaymentCount());
        offboardingRepository.save(process);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Recurring Payments Cancelled",
            String.format("%d recurring payments have been cancelled as part of your account closure.", 
                event.getCancelledPaymentCount()),
            correlationId
        );
        
        checkOffboardingCompletion(process);
        metricsService.recordRecurringPaymentsCancelled(event.getCancelledPaymentCount());
    }
    
    private void processLinkedAccountsDisconnected(CustomerOffboardingEvent event, String correlationId) {
        log.info("Linked accounts disconnected: userId={}, count={}", 
            event.getUserId(), event.getDisconnectedAccountCount());
        
        OffboardingProcess process = offboardingRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        process.setLinkedAccountsDisconnected(true);
        process.setLinkedAccountsDisconnectedAt(LocalDateTime.now());
        process.setDisconnectedAccountCount(event.getDisconnectedAccountCount());
        offboardingRepository.save(process);
        
        checkOffboardingCompletion(process);
        metricsService.recordLinkedAccountsDisconnected(event.getDisconnectedAccountCount());
    }
    
    private void processDataExportRequested(CustomerOffboardingEvent event, String correlationId) {
        log.info("Data export requested: userId={}, format={}", 
            event.getUserId(), event.getExportFormat());
        
        OffboardingProcess process = offboardingRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        process.setDataExportRequested(true);
        process.setDataExportRequestedAt(LocalDateTime.now());
        process.setExportFormat(event.getExportFormat());
        offboardingRepository.save(process);
        
        dataRetentionService.generateDataExport(event.getUserId(), event.getExportFormat());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Data Export In Progress",
            "We're preparing your data export. You'll receive a download link when it's ready.",
            correlationId
        );
        
        metricsService.recordDataExportRequested(event.getExportFormat());
    }
    
    private void processDataExportCompleted(CustomerOffboardingEvent event, String correlationId) {
        log.info("Data export completed: userId={}, downloadUrl={}", 
            event.getUserId(), event.getDownloadUrl() != null ? "PROVIDED" : "N/A");
        
        OffboardingProcess process = offboardingRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        process.setDataExportCompleted(true);
        process.setDataExportCompletedAt(LocalDateTime.now());
        process.setExportDownloadUrl(event.getDownloadUrl());
        process.setExportExpiresAt(LocalDateTime.now().plusDays(30));
        offboardingRepository.save(process);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Data Export Ready",
            "Your data export is ready for download. The download link will expire in 30 days.",
            correlationId
        );
        
        metricsService.recordDataExportCompleted();
    }
    
    private void processAccountDeactivated(CustomerOffboardingEvent event, String correlationId) {
        log.info("Account deactivated: userId={}", event.getUserId());
        
        OffboardingProcess process = offboardingRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        process.setStatus("DEACTIVATED");
        process.setAccountDeactivatedAt(LocalDateTime.now());
        offboardingRepository.save(process);
        
        accountClosureService.deactivateAccount(event.getUserId());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Account Deactivated",
            "Your account has been deactivated and can no longer be used for transactions.",
            correlationId
        );
        
        metricsService.recordAccountDeactivated();
    }
    
    private void processAccountClosed(CustomerOffboardingEvent event, String correlationId) {
        log.info("Account closed: userId={}", event.getUserId());
        
        OffboardingProcess process = offboardingRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        process.setStatus("CLOSED");
        process.setAccountClosedAt(LocalDateTime.now());
        process.setDataRetentionUntil(LocalDateTime.now().plusYears(DATA_RETENTION_YEARS));
        offboardingRepository.save(process);
        
        accountClosureService.closeAccount(event.getUserId());
        dataRetentionService.scheduleDataAnonymization(event.getUserId(), DATA_RETENTION_YEARS);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Account Closed",
            String.format("Your account has been closed. Your data will be retained for %d years as required by law, then anonymized.", 
                DATA_RETENTION_YEARS),
            correlationId
        );
        
        metricsService.recordAccountClosed(event.getClosureReason());
    }
    
    private void processDataAnonymized(CustomerOffboardingEvent event, String correlationId) {
        log.info("Data anonymized: userId={}", event.getUserId());
        
        OffboardingProcess process = offboardingRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        process.setStatus("ANONYMIZED");
        process.setDataAnonymizedAt(LocalDateTime.now());
        offboardingRepository.save(process);
        
        dataRetentionService.anonymizeUserData(event.getUserId());
        metricsService.recordDataAnonymized();
    }
    
    private void processGDPRDeletionRequested(CustomerOffboardingEvent event, String correlationId) {
        log.info("GDPR deletion requested: userId={}", event.getUserId());
        
        OffboardingProcess process = offboardingRepository.findByUserId(event.getUserId())
            .orElseGet(() -> OffboardingProcess.builder()
                .id(UUID.randomUUID().toString())
                .userId(event.getUserId())
                .closureReason("GDPR_RIGHT_TO_ERASURE")
                .closureType("GDPR_DELETION")
                .requestedAt(LocalDateTime.now())
                .status("GDPR_DELETION_REQUESTED")
                .correlationId(correlationId)
                .build());
        
        process.setGdprDeletionRequested(true);
        process.setGdprDeletionRequestedAt(LocalDateTime.now());
        offboardingRepository.save(process);
        
        gdprService.processErasureRequest(event.getUserId());
        
        notificationService.sendNotification(
            event.getUserId(),
            "GDPR Deletion Request Received",
            "We're processing your data deletion request under GDPR Article 17 (Right to Erasure).",
            correlationId
        );
        
        metricsService.recordGDPRDeletionRequested();
    }
    
    private void processDataDeleted(CustomerOffboardingEvent event, String correlationId) {
        log.info("Data deleted: userId={}", event.getUserId());
        
        OffboardingProcess process = offboardingRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        process.setStatus("DELETED");
        process.setDataDeletedAt(LocalDateTime.now());
        offboardingRepository.save(process);
        
        dataRetentionService.deleteUserData(event.getUserId());
        metricsService.recordDataDeleted();
    }
    
    private void checkOffboardingCompletion(OffboardingProcess process) {
        if (process.isBalanceCleared() &&
            process.isPendingTransactionsResolved() &&
            process.isRecurringPaymentsCancelled() &&
            process.isLinkedAccountsDisconnected()) {
            
            log.info("Offboarding prerequisites completed: userId={}", process.getUserId());
            accountClosureService.proceedWithClosure(process.getUserId());
        }
    }
}