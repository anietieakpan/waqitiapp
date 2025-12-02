package com.waqiti.account.kafka;

import com.waqiti.common.events.AccountClosureReviewEvent;
import com.waqiti.account.domain.AccountClosureRequest;
import com.waqiti.account.repository.AccountClosureRequestRepository;
import com.waqiti.account.repository.AccountRepository;
import com.waqiti.account.service.AccountClosureService;
import com.waqiti.account.service.FinalSettlementService;
import com.waqiti.account.metrics.AccountMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
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
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class AccountClosureReviewsConsumer {
    
    private final AccountClosureRequestRepository closureRequestRepository;
    private final AccountRepository accountRepository;
    private final AccountClosureService closureService;
    private final FinalSettlementService settlementService;
    private final AccountMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    
    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds
    
    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;
    
    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("account_closure_reviews_processed_total")
            .description("Total number of successfully processed account closure review events")
            .register(meterRegistry);
        errorCounter = Counter.builder("account_closure_reviews_errors_total")
            .description("Total number of account closure review processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("account_closure_reviews_processing_duration")
            .description("Time taken to process account closure review events")
            .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = {"account-closure-reviews", "account-closure-workflow", "account-termination-requests"},
        groupId = "account-closure-review-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "account-closure-reviews", fallbackMethod = "handleAccountClosureReviewEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAccountClosureReviewEvent(
            @Payload AccountClosureReviewEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("closure-%s-p%d-o%d", event.getAccountId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getAccountId(), event.getEventType(), event.getTimestamp());
        
        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }
            
            log.info("Processing account closure review: accountId={}, status={}, reason={}",
                event.getAccountId(), event.getReviewStatus(), event.getClosureReason());
            
            // Clean expired entries periodically
            cleanExpiredEntries();
            
            switch (event.getEventType()) {
                case CLOSURE_REQUESTED:
                    processClosureRequest(event, correlationId);
                    break;
                    
                case REVIEW_INITIATED:
                    initiateClosureReview(event, correlationId);
                    break;
                    
                case REVIEW_APPROVED:
                    approveClosureRequest(event, correlationId);
                    break;
                    
                case REVIEW_REJECTED:
                    rejectClosureRequest(event, correlationId);
                    break;
                    
                case SETTLEMENT_PROCESSING:
                    processFinalSettlement(event, correlationId);
                    break;
                    
                case CLOSURE_COMPLETED:
                    completeAccountClosure(event, correlationId);
                    break;
                    
                case CLOSURE_CANCELLED:
                    cancelClosureRequest(event, correlationId);
                    break;
                    
                default:
                    log.warn("Unknown account closure event type: {}", event.getEventType());
                    break;
            }
            
            // Mark event as processed
            markEventAsProcessed(eventKey);
            
            auditService.logAccountEvent("ACCOUNT_CLOSURE_EVENT_PROCESSED", event.getAccountId(),
                Map.of("eventType", event.getEventType(), "reviewStatus", event.getReviewStatus(),
                    "closureReason", event.getClosureReason(), "correlationId", correlationId, 
                    "timestamp", Instant.now()));
            
            successCounter.increment();
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process account closure review event: {}", e.getMessage(), e);
            
            // Send fallback event
            kafkaTemplate.send("account-closure-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));
            
            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }
    
    public void handleAccountClosureReviewEventFallback(
            AccountClosureReviewEvent event, 
            int partition, 
            long offset, 
            Acknowledgment acknowledgment, 
            Exception ex) {
        
        String correlationId = String.format("closure-fallback-%s-p%d-o%d", event.getAccountId(), partition, offset);
        
        log.error("Circuit breaker fallback triggered for account closure review: accountId={}, error={}", 
            event.getAccountId(), ex.getMessage());
        
        // Send to dead letter queue
        kafkaTemplate.send("account-closure-reviews-dlq", Map.of(
            "originalEvent", event, 
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId, 
            "timestamp", Instant.now()));
        
        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Account Closure Review Circuit Breaker Triggered",
                String.format("Account %s closure review failed: %s", event.getAccountId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }
        
        acknowledgment.acknowledge();
    }
    
    @DltHandler
    public void handleDltAccountClosureReviewEvent(
            @Payload AccountClosureReviewEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        String correlationId = String.format("dlt-closure-%s-%d", event.getAccountId(), System.currentTimeMillis());
        
        log.error("Dead letter topic handler - Account closure review permanently failed: accountId={}, topic={}, error={}", 
            event.getAccountId(), topic, exceptionMessage);
        
        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("ACCOUNT_CLOSURE_DLT_EVENT", event.getAccountId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));
        
        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Account Closure Review Dead Letter Event",
                String.format("Account %s closure review sent to DLT: %s", event.getAccountId(), exceptionMessage),
                Map.of("accountId", event.getAccountId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }
    
    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }
        
        // Check if the entry has expired
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }
        
        return true;
    }
    
    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }
    
    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry -> 
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }
    
    private void processClosureRequest(AccountClosureReviewEvent event, String correlationId) {
        AccountClosureRequest request = AccountClosureRequest.builder()
            .accountId(event.getAccountId())
            .userId(event.getUserId())
            .closureReason(event.getClosureReason())
            .requestedBy(event.getRequestedBy())
            .status("PENDING_REVIEW")
            .requestedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        closureRequestRepository.save(request);
        
        closureService.validateClosureEligibility(event.getAccountId());
        
        notificationService.sendNotification(event.getUserId(), "Account Closure Request Received",
            "We've received your account closure request and will review it within 5 business days.",
            correlationId);
        
        kafkaTemplate.send("account-closure-workflow", Map.of(
            "accountId", event.getAccountId(),
            "eventType", "REVIEW_INITIATED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordClosureRequested(event.getClosureReason());
        
        log.info("Account closure requested: accountId={}, reason={}", 
            event.getAccountId(), event.getClosureReason());
    }
    
    private void initiateClosureReview(AccountClosureReviewEvent event, String correlationId) {
        AccountClosureRequest request = closureRequestRepository.findByAccountId(event.getAccountId())
            .orElseThrow(() -> new RuntimeException("Closure request not found"));
        
        request.setStatus("UNDER_REVIEW");
        request.setReviewStartedAt(LocalDateTime.now());
        request.setReviewedBy(event.getReviewedBy());
        closureRequestRepository.save(request);
        
        closureService.performComplianceChecks(event.getAccountId());
        closureService.checkOutstandingObligations(event.getAccountId());
        
        metricsService.recordClosureReviewInitiated();
        
        log.info("Account closure review initiated: accountId={}, reviewedBy={}", 
            event.getAccountId(), event.getReviewedBy());
    }
    
    private void approveClosureRequest(AccountClosureReviewEvent event, String correlationId) {
        AccountClosureRequest request = closureRequestRepository.findByAccountId(event.getAccountId())
            .orElseThrow(() -> new RuntimeException("Closure request not found"));
        
        request.setStatus("APPROVED");
        request.setApprovedAt(LocalDateTime.now());
        request.setApprovalNotes(event.getReviewNotes());
        closureRequestRepository.save(request);
        
        notificationService.sendNotification(event.getUserId(), "Account Closure Approved",
            "Your account closure request has been approved. We will process the final settlement and close your account.",
            correlationId);
        
        kafkaTemplate.send("account-closure-workflow", Map.of(
            "accountId", event.getAccountId(),
            "eventType", "SETTLEMENT_PROCESSING",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordClosureApproved();
        
        log.info("Account closure approved: accountId={}", event.getAccountId());
    }
    
    private void rejectClosureRequest(AccountClosureReviewEvent event, String correlationId) {
        AccountClosureRequest request = closureRequestRepository.findByAccountId(event.getAccountId())
            .orElseThrow(() -> new RuntimeException("Closure request not found"));
        
        request.setStatus("REJECTED");
        request.setRejectedAt(LocalDateTime.now());
        request.setRejectionReason(event.getRejectionReason());
        request.setRejectionNotes(event.getReviewNotes());
        closureRequestRepository.save(request);
        
        notificationService.sendNotification(event.getUserId(), "Account Closure Request Not Approved",
            String.format("We're unable to close your account at this time. Reason: %s", 
                event.getRejectionReason()),
            correlationId);
        
        metricsService.recordClosureRejected(event.getRejectionReason());
        
        log.warn("Account closure rejected: accountId={}, reason={}", 
            event.getAccountId(), event.getRejectionReason());
    }
    
    private void processFinalSettlement(AccountClosureReviewEvent event, String correlationId) {
        AccountClosureRequest request = closureRequestRepository.findByAccountId(event.getAccountId())
            .orElseThrow(() -> new RuntimeException("Closure request not found"));
        
        request.setStatus("SETTLEMENT_IN_PROGRESS");
        closureRequestRepository.save(request);
        
        settlementService.processAccountBalance(event.getAccountId());
        settlementService.cancelPendingTransactions(event.getAccountId());
        settlementService.closeLinkedServices(event.getAccountId());
        settlementService.archiveAccountData(event.getAccountId());
        
        kafkaTemplate.send("account-closure-workflow", Map.of(
            "accountId", event.getAccountId(),
            "eventType", "CLOSURE_COMPLETED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordSettlementProcessed();
        
        log.info("Final settlement processed: accountId={}", event.getAccountId());
    }
    
    private void completeAccountClosure(AccountClosureReviewEvent event, String correlationId) {
        AccountClosureRequest request = closureRequestRepository.findByAccountId(event.getAccountId())
            .orElseThrow(() -> new RuntimeException("Closure request not found"));
        
        request.setStatus("CLOSED");
        request.setClosedAt(LocalDateTime.now());
        closureRequestRepository.save(request);
        
        var account = accountRepository.findById(event.getAccountId()).orElseThrow();
        account.setStatus("CLOSED");
        account.setClosedAt(LocalDateTime.now());
        account.setClosureReason(event.getClosureReason());
        accountRepository.save(account);
        
        notificationService.sendNotification(event.getUserId(), "Account Closed",
            "Your account has been successfully closed. Thank you for banking with us.",
            correlationId);
        
        kafkaTemplate.send("account-lifecycle-events", Map.of(
            "accountId", event.getAccountId(),
            "userId", event.getUserId(),
            "eventType", "ACCOUNT_CLOSED",
            "closureReason", event.getClosureReason(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordAccountClosed(event.getClosureReason());
        
        log.info("Account closure completed: accountId={}, reason={}", 
            event.getAccountId(), event.getClosureReason());
    }
    
    private void cancelClosureRequest(AccountClosureReviewEvent event, String correlationId) {
        AccountClosureRequest request = closureRequestRepository.findByAccountId(event.getAccountId())
            .orElseThrow(() -> new RuntimeException("Closure request not found"));
        
        request.setStatus("CANCELLED");
        request.setCancelledAt(LocalDateTime.now());
        request.setCancellationReason(event.getCancellationReason());
        closureRequestRepository.save(request);
        
        notificationService.sendNotification(event.getUserId(), "Account Closure Cancelled",
            "Your account closure request has been cancelled as requested.",
            correlationId);
        
        metricsService.recordClosureCancelled();
        
        log.info("Account closure cancelled: accountId={}, reason={}", 
            event.getAccountId(), event.getCancellationReason());
    }
}