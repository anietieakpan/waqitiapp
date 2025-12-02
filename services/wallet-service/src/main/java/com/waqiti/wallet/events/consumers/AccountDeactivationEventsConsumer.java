package com.waqiti.wallet.events.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.events.user.AccountDeactivationEvent;
import com.waqiti.common.exceptions.ServiceIntegrationException;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.wallet.client.ComplianceServiceClient;
import com.waqiti.wallet.client.NotificationServiceClient;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.domain.WalletStatus;
import com.waqiti.wallet.dto.*;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.service.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Production-ready Account Deactivation Events Consumer for Wallet Service
 * 
 * Consumes account deactivation events to handle wallet cleanup, fund transfers,
 * and account closure when user accounts are deactivated.
 * 
 * Key Responsibilities:
 * - Freeze all user wallets
 * - Process pending transactions
 * - Calculate final balances
 * - Initiate refund/withdrawal processes
 * - Close or suspend wallets based on deactivation type
 * - Notify user of wallet status changes
 * - Archive wallet data for compliance
 * 
 * Deactivation Types:
 * - TEMPORARY: Freeze wallets, preserve data
 * - PERMANENT: Close wallets, initiate refunds
 * - SUSPENDED: Freeze for security review
 * - COMPLIANCE: Regulatory requirement closure
 * 
 * Integration Points:
 * - notification-service: Wallet closure notifications
 * - payment-service: Pending transaction cancellation
 * - compliance-service: Regulatory reporting
 * - analytics-service: Churn analytics
 * 
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountDeactivationEventsConsumer {

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;

    // Repository
    private final WalletRepository walletRepository;

    // Services
    private final WalletFreezeService walletFreezeService;
    private final RefundProcessingService refundProcessingService;
    private final PendingTransactionService pendingTransactionService;
    private final WalletArchivalService walletArchivalService;
    private final NotificationServiceClient notificationServiceClient;
    private final ComplianceServiceClient complianceServiceClient;
    private final EventProcessingTrackingService eventProcessingTrackingService;
    private final UniversalDLQHandler dlqHandler;

    // Metrics
    private final Counter successCounter = Counter.builder("account_deactivation_events_processed_total")
            .description("Total number of account deactivation events successfully processed")
            .register(meterRegistry);

    private final Counter failureCounter = Counter.builder("account_deactivation_events_failed_total")
            .description("Total number of account deactivation events that failed processing")
            .register(meterRegistry);

    private final Timer processingTimer = Timer.builder("account_deactivation_event_processing_duration")
            .description("Time taken to process account deactivation events")
            .register(meterRegistry);

    private final Counter walletsFrozenCounter = Counter.builder("wallets_frozen_due_to_deactivation_total")
            .description("Total number of wallets frozen due to account deactivation")
            .register(meterRegistry);

    /**
     * Main Kafka listener for account deactivation events
     */
    @KafkaListener(
        topics = {"${kafka.topics.account-deactivation-events:account-deactivation-events}", 
                  "${kafka.topics.user-account-deactivated:user-account-deactivated}"},
        groupId = "${kafka.consumer.group-id:wallet-service-consumer-group}",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "${kafka.consumer.concurrency:3}"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        include = {ServiceIntegrationException.class, Exception.class},
        dltTopicSuffix = "-dlt",
        autoCreateTopics = "true",
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRED, timeout = 30, rollbackFor = Exception.class)
    public void handleAccountDeactivationEvent(
            @Payload AccountDeactivationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = "correlationId", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        LocalDateTime processingStartTime = LocalDateTime.now();
        
        // Generate correlation ID if not provided
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = event.getCorrelationId() != null ? 
                event.getCorrelationId() : UUID.randomUUID().toString();
        }

        log.info("Processing account deactivation event: eventId={}, userId={}, deactivationType={}, " +
                "correlationId={}, topic={}, partition={}, offset={}", 
                event.getEventId(), event.getUserId(), event.getDeactivationType(), 
                correlationId, topic, partition, offset);

        try {
            // 1. Validate event
            validateAccountDeactivationEvent(event);

            // 2. Check for duplicate processing
            if (eventProcessingTrackingService.isDuplicateEvent(event.getEventId(), "ACCOUNT_DEACTIVATION_EVENT")) {
                log.warn("Duplicate account deactivation event detected, skipping: eventId={}, userId={}", 
                        event.getEventId(), event.getUserId());
                acknowledgment.acknowledge();
                return;
            }

            // 3. Track event processing start
            eventProcessingTrackingService.trackEventProcessingStart(
                event.getEventId(), 
                "ACCOUNT_DEACTIVATION_EVENT", 
                correlationId,
                Map.of(
                    "userId", event.getUserId().toString(),
                    "deactivationType", event.getDeactivationType() != null ? event.getDeactivationType() : "UNKNOWN",
                    "deactivationReason", event.getDeactivationReason() != null ? event.getDeactivationReason() : "UNKNOWN"
                )
            );

            // 4. Process wallet deactivation
            processWalletDeactivation(event, correlationId);

            // 5. Track successful processing
            eventProcessingTrackingService.trackEventProcessingSuccess(
                event.getEventId(),
                Map.of(
                    "processingTimeMs", processingTimer.stop(sample).longValue(),
                    "walletsProcessed", "true",
                    "processingStartTime", processingStartTime.toString()
                )
            );

            successCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed account deactivation event: eventId={}, userId={}, " +
                    "processingTimeMs={}",
                    event.getEventId(), event.getUserId(),
                    processingTimer.stop(sample).longValue());

        } catch (Exception e) {
            processingTimer.stop(sample);
            failureCounter.increment();

            log.error("Failed to process account deactivation event: eventId={}, userId={}, " +
                     "attempt={}, error={}",
                     event.getEventId(), event.getUserId(),
                     RetrySynchronizationManager.getContext() != null ?
                         RetrySynchronizationManager.getContext().getRetryCount() + 1 : 1,
                     e.getMessage(), e);

            // Track processing failure
            eventProcessingTrackingService.trackEventProcessingFailure(
                event.getEventId(),
                e.getClass().getSimpleName(),
                e.getMessage(),
                Map.of(
                    "processingTimeMs", processingTimer.stop(sample).longValue(),
                    "attempt", String.valueOf(RetrySynchronizationManager.getContext() != null ?
                        RetrySynchronizationManager.getContext().getRetryCount() + 1 : 1)
                )
            );

            // Audit critical failure
            auditService.logAccountDeactivationEventProcessingFailure(
                event.getEventId(),
                event.getUserId().toString(),
                correlationId,
                event.getDeactivationType(),
                e.getClass().getSimpleName(),
                e.getMessage(),
                Map.of(
                    "topic", topic,
                    "partition", String.valueOf(partition),
                    "offset", String.valueOf(offset),
                    "deactivationReason", event.getDeactivationReason() != null ? event.getDeactivationReason() : "UNKNOWN"
                )
            );

            dlqHandler.handleFailedMessage(event, topic, partition, offset, e)
                .thenAccept(result -> log.info("Account deactivation event sent to DLQ: eventId={}, userId={}, destination={}, category={}",
                        event.getEventId(), event.getUserId(), result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for account deactivation event - MESSAGE MAY BE LOST! " +
                            "eventId={}, userId={}, partition={}, offset={}, error={}",
                            event.getEventId(), event.getUserId(), partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new ServiceIntegrationException("Account deactivation event processing failed", e);
        }
    }

    /**
     * Process wallet deactivation for the user
     */
    private void processWalletDeactivation(AccountDeactivationEvent event, String correlationId) {
        log.info("Processing wallet deactivation for user: userId={}, deactivationType={}", 
                event.getUserId(), event.getDeactivationType());

        // Get all wallets for the user
        List<Wallet> userWallets = walletRepository.findByUserId(event.getUserId());
        
        if (userWallets.isEmpty()) {
            log.warn("No wallets found for user: userId={}", event.getUserId());
            return;
        }

        log.info("Found {} wallets for user: userId={}", userWallets.size(), event.getUserId());

        // Process based on deactivation type
        switch (event.getDeactivationType().toUpperCase()) {
            case "TEMPORARY" -> processTemporaryDeactivation(userWallets, event, correlationId);
            case "PERMANENT" -> processPermanentDeactivation(userWallets, event, correlationId);
            case "SUSPENDED" -> processSuspendedDeactivation(userWallets, event, correlationId);
            case "COMPLIANCE" -> processComplianceDeactivation(userWallets, event, correlationId);
            default -> {
                log.warn("Unknown deactivation type: {} for userId={}", 
                        event.getDeactivationType(), event.getUserId());
                processTemporaryDeactivation(userWallets, event, correlationId);
            }
        }

        // Send wallet closure notifications
        sendWalletClosureNotifications(userWallets, event, correlationId);

        // Update compliance reporting
        reportWalletClosureToCompliance(userWallets, event, correlationId);
    }

    /**
     * Process temporary deactivation - freeze wallets, preserve data
     */
    private void processTemporaryDeactivation(List<Wallet> wallets, AccountDeactivationEvent event, 
                                             String correlationId) {
        log.info("Processing temporary deactivation for {} wallets: userId={}", 
                wallets.size(), event.getUserId());

        for (Wallet wallet : wallets) {
            try {
                // Freeze wallet
                walletFreezeService.freezeWallet(
                    wallet.getId(),
                    "ACCOUNT_TEMPORARILY_DEACTIVATED",
                    event.getDeactivationReason(),
                    event.getScheduledReactivation(),
                    correlationId
                );

                wallet.setStatus(WalletStatus.FROZEN);
                wallet.setFrozenReason(event.getDeactivationReason());
                wallet.setFrozenAt(LocalDateTime.now());
                walletRepository.save(wallet);

                walletsFrozenCounter.increment();

                log.info("Wallet frozen for temporary deactivation: walletId={}, userId={}", 
                        wallet.getId(), event.getUserId());

            } catch (Exception e) {
                log.error("Failed to freeze wallet: walletId={}, userId={}, error={}", 
                         wallet.getId(), event.getUserId(), e.getMessage(), e);
                // Continue processing other wallets
            }
        }

        // Audit
        auditService.logWalletsTemporarilyDeactivated(
            event.getEventId(),
            event.getUserId().toString(),
            correlationId,
            wallets.size(),
            event.getDeactivationReason(),
            event.getScheduledReactivation(),
            Map.of()
        );
    }

    /**
     * Process permanent deactivation - close wallets, initiate refunds
     */
    private void processPermanentDeactivation(List<Wallet> wallets, AccountDeactivationEvent event, 
                                             String correlationId) {
        log.info("Processing permanent deactivation for {} wallets: userId={}", 
                wallets.size(), event.getUserId());

        BigDecimal totalRefundAmount = BigDecimal.ZERO;
        List<RefundRequest> refundRequests = new ArrayList<>();

        for (Wallet wallet : wallets) {
            try {
                // Cancel pending transactions
                pendingTransactionService.cancelPendingTransactionsForWallet(
                    wallet.getId(),
                    "ACCOUNT_PERMANENTLY_CLOSED",
                    correlationId
                );

                // Calculate final balance
                BigDecimal finalBalance = wallet.getAvailableBalance();
                
                if (finalBalance.compareTo(BigDecimal.ZERO) > 0) {
                    totalRefundAmount = totalRefundAmount.add(finalBalance);
                    
                    // Create refund request
                    RefundRequest refundRequest = RefundRequest.builder()
                            .userId(event.getUserId())
                            .walletId(wallet.getId())
                            .amount(finalBalance)
                            .currency(wallet.getCurrency())
                            .refundReason("ACCOUNT_CLOSURE")
                            .refundMethod(event.getRefundMethod())
                            .correlationId(correlationId)
                            .build();
                    
                    refundRequests.add(refundRequest);
                }

                // Close wallet
                wallet.setStatus(WalletStatus.CLOSED);
                wallet.setClosedReason(event.getDeactivationReason());
                wallet.setClosedAt(LocalDateTime.now());
                wallet.setClosedBy(event.getDeactivatedBy());
                walletRepository.save(wallet);

                // Archive wallet data
                walletArchivalService.archiveWallet(wallet.getId(), correlationId);

                log.info("Wallet closed for permanent deactivation: walletId={}, userId={}, finalBalance={} {}", 
                        wallet.getId(), event.getUserId(), finalBalance, wallet.getCurrency());

            } catch (Exception e) {
                log.error("Failed to close wallet: walletId={}, userId={}, error={}", 
                         wallet.getId(), event.getUserId(), e.getMessage(), e);
                // Continue processing other wallets
            }
        }

        // Process all refunds
        if (!refundRequests.isEmpty()) {
            processRefunds(refundRequests, event, correlationId);
        }

        // Audit
        auditService.logWalletsPermanentlyDeactivated(
            event.getEventId(),
            event.getUserId().toString(),
            correlationId,
            wallets.size(),
            totalRefundAmount,
            event.getDeactivationReason(),
            Map.of("refundsInitiated", String.valueOf(refundRequests.size()))
        );
    }

    /**
     * Process suspended deactivation - freeze for security review
     */
    private void processSuspendedDeactivation(List<Wallet> wallets, AccountDeactivationEvent event, 
                                             String correlationId) {
        log.info("Processing suspended deactivation for {} wallets: userId={}", 
                wallets.size(), event.getUserId());

        for (Wallet wallet : wallets) {
            try {
                // Freeze wallet for security review
                walletFreezeService.freezeWallet(
                    wallet.getId(),
                    "ACCOUNT_SUSPENDED_SECURITY_REVIEW",
                    event.getDeactivationReason(),
                    null, // No scheduled reactivation for suspended accounts
                    correlationId
                );

                wallet.setStatus(WalletStatus.SUSPENDED);
                wallet.setFrozenReason("SECURITY_REVIEW: " + event.getDeactivationReason());
                wallet.setFrozenAt(LocalDateTime.now());
                walletRepository.save(wallet);

                walletsFrozenCounter.increment();

                log.info("Wallet suspended for security review: walletId={}, userId={}", 
                        wallet.getId(), event.getUserId());

            } catch (Exception e) {
                log.error("Failed to suspend wallet: walletId={}, userId={}, error={}", 
                         wallet.getId(), event.getUserId(), e.getMessage(), e);
            }
        }

        // Audit
        auditService.logWalletsSuspended(
            event.getEventId(),
            event.getUserId().toString(),
            correlationId,
            wallets.size(),
            event.getDeactivationReason(),
            Map.of("ticketId", event.getTicketId() != null ? event.getTicketId() : "N/A")
        );
    }

    /**
     * Process compliance deactivation - regulatory requirement closure
     */
    private void processComplianceDeactivation(List<Wallet> wallets, AccountDeactivationEvent event, 
                                              String correlationId) {
        log.info("Processing compliance deactivation for {} wallets: userId={}", 
                wallets.size(), event.getUserId());

        // Compliance closures are similar to permanent but with additional reporting
        processPermanentDeactivation(wallets, event, correlationId);

        // Additional compliance reporting
        reportComplianceClosureToRegulators(wallets, event, correlationId);

        // Audit
        auditService.logComplianceWalletClosure(
            event.getEventId(),
            event.getUserId().toString(),
            correlationId,
            wallets.size(),
            event.getDeactivationReason(),
            event.getRegulationReference(),
            Map.of()
        );
    }

    /**
     * Process refunds for closed wallets
     */
    @CircuitBreaker(name = "refund-service", fallbackMethod = "processRefundsFallback")
    @Retry(name = "refund-service")
    private void processRefunds(List<RefundRequest> refundRequests, AccountDeactivationEvent event, 
                              String correlationId) {
        log.info("Processing {} refunds for account deactivation: userId={}", 
                refundRequests.size(), event.getUserId());

        for (RefundRequest refundRequest : refundRequests) {
            try {
                RefundResponse refundResponse = refundProcessingService.initiateRefund(refundRequest);
                
                if (refundResponse.isSuccess()) {
                    log.info("Refund initiated successfully: userId={}, walletId={}, amount={} {}, refundId={}", 
                            event.getUserId(), refundRequest.getWalletId(), 
                            refundRequest.getAmount(), refundRequest.getCurrency(),
                            refundResponse.getRefundId());
                } else {
                    log.warn("Refund initiation failed: userId={}, walletId={}, reason={}", 
                            event.getUserId(), refundRequest.getWalletId(), 
                            refundResponse.getFailureReason());
                }
            } catch (Exception e) {
                log.error("Failed to process refund: userId={}, walletId={}, error={}", 
                         event.getUserId(), refundRequest.getWalletId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Fallback for refund processing
     */
    private void processRefundsFallback(List<RefundRequest> refundRequests, 
                                       AccountDeactivationEvent event,
                                       String correlationId, Exception ex) {
        log.error("Refund processing fallback triggered for account deactivation: userId={}, error={}", 
                event.getUserId(), ex.getMessage());
        
        // Log for manual processing
        auditService.logRefundProcessingFallback(
            event.getEventId(),
            event.getUserId().toString(),
            correlationId,
            refundRequests.size(),
            ex.getMessage(),
            Map.of("requiresManualIntervention", "true")
        );
    }

    /**
     * Send wallet closure notifications
     */
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendWalletClosureNotificationsFallback")
    @Retry(name = "notification-service")
    private void sendWalletClosureNotifications(List<Wallet> wallets, AccountDeactivationEvent event, 
                                               String correlationId) {
        try {
            WalletClosureNotificationRequest notificationRequest = WalletClosureNotificationRequest.builder()
                    .userId(event.getUserId())
                    .walletCount(wallets.size())
                    .deactivationType(event.getDeactivationType())
                    .deactivationReason(event.getDeactivationReason())
                    .scheduledReactivation(event.getScheduledReactivation())
                    .correlationId(correlationId)
                    .build();

            notificationServiceClient.sendWalletClosureNotification(notificationRequest);
            
            log.info("Wallet closure notifications sent: userId={}, walletCount={}", 
                    event.getUserId(), wallets.size());
                    
        } catch (Exception e) {
            log.error("Failed to send wallet closure notifications: userId={}, error={}", 
                     event.getUserId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Fallback for wallet closure notifications
     */
    private void sendWalletClosureNotificationsFallback(List<Wallet> wallets, 
                                                       AccountDeactivationEvent event,
                                                       String correlationId, Exception ex) {
        log.warn("Wallet closure notification fallback triggered: userId={}, error={}", 
                event.getUserId(), ex.getMessage());
    }

    /**
     * Report wallet closure to compliance
     */
    @CircuitBreaker(name = "compliance-service")
    @Retry(name = "compliance-service")
    private void reportWalletClosureToCompliance(List<Wallet> wallets, AccountDeactivationEvent event, 
                                                String correlationId) {
        try {
            WalletClosureComplianceRequest complianceRequest = WalletClosureComplianceRequest.builder()
                    .userId(event.getUserId())
                    .walletIds(wallets.stream().map(Wallet::getId).toList())
                    .deactivationType(event.getDeactivationType())
                    .deactivationReason(event.getDeactivationReason())
                    .deactivatedBy(event.getDeactivatedBy())
                    .ticketId(event.getTicketId())
                    .correlationId(correlationId)
                    .build();

            complianceServiceClient.reportWalletClosure(complianceRequest);
            
            log.debug("Wallet closure reported to compliance: userId={}, walletCount={}", 
                     event.getUserId(), wallets.size());
                     
        } catch (Exception e) {
            log.warn("Failed to report wallet closure to compliance: userId={}, error={}", 
                    event.getUserId(), e.getMessage());
            // Don't fail for compliance reporting issues
        }
    }

    /**
     * Report compliance closure to regulators
     */
    private void reportComplianceClosureToRegulators(List<Wallet> wallets, AccountDeactivationEvent event, 
                                                    String correlationId) {
        try {
            RegulatoryClosureRequest regulatoryRequest = RegulatoryClosureRequest.builder()
                    .userId(event.getUserId())
                    .walletCount(wallets.size())
                    .regulationReference(event.getRegulationReference())
                    .deactivationReason(event.getDeactivationReason())
                    .reportingAuthority(event.getReportingAuthority())
                    .correlationId(correlationId)
                    .build();

            complianceServiceClient.reportRegulatoryWalletClosure(regulatoryRequest);
            
            log.info("Compliance closure reported to regulators: userId={}, regulation={}", 
                    event.getUserId(), event.getRegulationReference());
                    
        } catch (Exception e) {
            log.error("Failed to report compliance closure to regulators: userId={}, error={}", 
                     event.getUserId(), e.getMessage(), e);
        }
    }

    /**
     * Validate account deactivation event
     */
    private void validateAccountDeactivationEvent(AccountDeactivationEvent event) {
        Set<ConstraintViolation<AccountDeactivationEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder("Account deactivation event validation failed: ");
            for (ConstraintViolation<AccountDeactivationEvent> violation : violations) {
                sb.append(violation.getPropertyPath()).append(" ").append(violation.getMessage()).append("; ");
            }
            throw new IllegalArgumentException(sb.toString());
        }

        // Additional business validation
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID is required");
        }
        if (event.getUserId() == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (event.getDeactivationType() == null || event.getDeactivationType().trim().isEmpty()) {
            throw new IllegalArgumentException("Deactivation type is required");
        }
    }

    /**
     * Dead Letter Topic handler for failed account deactivation events
     */
    @DltHandler
    public void handleDltAccountDeactivationEvent(
            @Payload AccountDeactivationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
            @Header(value = "correlationId", required = false) String correlationId) {
        
        log.error("Account deactivation event sent to DLT: eventId={}, userId={}, " +
                 "deactivationType={}, topic={}, error={}", 
                 event.getEventId(), event.getUserId(), event.getDeactivationType(),
                 topic, exceptionMessage);

        // Generate correlation ID if not provided
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = event.getCorrelationId() != null ? 
                event.getCorrelationId() : UUID.randomUUID().toString();
        }

        // Track DLT event
        eventProcessingTrackingService.trackEventDLT(
            event.getEventId(),
            "ACCOUNT_DEACTIVATION_EVENT",
            exceptionMessage,
            Map.of(
                "topic", topic,
                "userId", event.getUserId().toString(),
                "deactivationType", event.getDeactivationType() != null ? event.getDeactivationType() : "UNKNOWN",
                "deactivationReason", event.getDeactivationReason() != null ? event.getDeactivationReason() : "UNKNOWN"
            )
        );

        // Critical audit for DLT events
        auditService.logAccountDeactivationEventDLT(
            event.getEventId(),
            event.getUserId().toString(),
            correlationId,
            topic,
            exceptionMessage,
            Map.of(
                "deactivationType", event.getDeactivationType() != null ? event.getDeactivationType() : "UNKNOWN",
                "deactivationReason", event.getDeactivationReason() != null ? event.getDeactivationReason() : "UNKNOWN",
                "requiresManualIntervention", "true",
                "criticalityLevel", "HIGH"
            )
        );
    }
}