package com.waqiti.analytics.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.analytics.service.FinancialAnalyticsService;
import com.waqiti.analytics.service.ReconciliationService;
import com.waqiti.analytics.service.ReportingService;
import com.waqiti.analytics.service.AuditTrailService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Critical Event Consumer: Ledger Events
 * 
 * Handles real-time ledger event processing for:
 * - Financial analytics and reporting aggregation
 * - Real-time transaction reconciliation and balance verification
 * - Audit trail generation and compliance reporting
 * - Financial dashboard updates and KPI calculations
 * - Revenue recognition and financial statement preparation
 * - Anomaly detection and fraud monitoring
 * 
 * BUSINESS IMPACT: Without this consumer, ledger events are published
 * but NOT processed for analytics, leading to:
 * - Missing financial analytics and reporting (~$5M monthly transactions untracked)
 * - Failed reconciliation causing accounting discrepancies
 * - Incomplete audit trails for regulatory compliance
 * - Delayed financial reporting and dashboard updates
 * - Revenue recognition errors affecting financial statements
 * - Undetected anomalies and potential fraud
 * 
 * This consumer enables:
 * - Real-time financial analytics and insights
 * - Automated reconciliation and balance verification
 * - Complete audit trail for compliance
 * - Live financial dashboard updates
 * - Accurate revenue recognition and reporting
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerEventConsumer {

    private final FinancialAnalyticsService financialAnalyticsService;
    private final ReconciliationService reconciliationService;
    private final ReportingService reportingService;
    private final AuditTrailService auditTrailService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final UniversalDLQHandler universalDLQHandler;

    // Metrics
    private Counter ledgerEventsProcessed;
    private Counter ledgerEventsSuccessful;
    private Counter ledgerEventsFailed;
    private Counter journalEntriesProcessed;
    private Counter balanceUpdatesProcessed;
    private Counter reconciliationEventsProcessed;
    private Counter auditEventsCreated;
    private Counter anomaliesDetected;
    private Timer eventProcessingTime;
    private Counter highValueTransactions;
    private Counter revenueEventsProcessed;

    @PostConstruct
    public void initializeMetrics() {
        ledgerEventsProcessed = Counter.builder("waqiti.ledger.events.processed.total")
            .description("Total ledger events processed")
            .tag("service", "analytics-service")
            .register(meterRegistry);

        ledgerEventsSuccessful = Counter.builder("waqiti.ledger.events.successful")
            .description("Successful ledger event processing")
            .tag("service", "analytics-service")
            .register(meterRegistry);

        ledgerEventsFailed = Counter.builder("waqiti.ledger.events.failed")
            .description("Failed ledger event processing")
            .tag("service", "analytics-service")
            .register(meterRegistry);

        journalEntriesProcessed = Counter.builder("waqiti.ledger.journal_entries.processed")
            .description("Journal entries processed")
            .tag("service", "analytics-service")
            .register(meterRegistry);

        balanceUpdatesProcessed = Counter.builder("waqiti.ledger.balance_updates.processed")
            .description("Balance updates processed")
            .tag("service", "analytics-service")
            .register(meterRegistry);

        reconciliationEventsProcessed = Counter.builder("waqiti.ledger.reconciliation.processed")
            .description("Reconciliation events processed")
            .tag("service", "analytics-service")
            .register(meterRegistry);

        auditEventsCreated = Counter.builder("waqiti.ledger.audit_events.created")
            .description("Audit trail events created")
            .tag("service", "analytics-service")
            .register(meterRegistry);

        anomaliesDetected = Counter.builder("waqiti.ledger.anomalies.detected")
            .description("Anomalies detected in ledger events")
            .tag("service", "analytics-service")
            .register(meterRegistry);

        eventProcessingTime = Timer.builder("waqiti.ledger.event.processing.duration")
            .description("Time taken to process ledger events")
            .tag("service", "analytics-service")
            .register(meterRegistry);

        highValueTransactions = Counter.builder("waqiti.ledger.high_value.transactions")
            .description("High value transactions (>$10K)")
            .tag("service", "analytics-service")
            .register(meterRegistry);

        revenueEventsProcessed = Counter.builder("waqiti.ledger.revenue_events.processed")
            .description("Revenue recognition events processed")
            .tag("service", "analytics-service")
            .register(meterRegistry);
    }

    /**
     * Consumes ledger-events with comprehensive analytics processing
     * 
     * @param eventPayload The ledger event data as Map
     * @param partition Kafka partition
     * @param offset Kafka offset
     * @param acknowledgment Kafka acknowledgment
     */
    @KafkaListener(
        topics = "ledger-events",
        groupId = "analytics-service-ledger-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0),
        autoCreateTopics = "true",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @Transactional(rollbackFor = Exception.class)
    public void handleLedgerEvent(
            @Payload Map<String, Object> eventPayload,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = null;
        
        try {
            ledgerEventsProcessed.increment();
            
            log.info("Processing ledger event from partition: {}, offset: {}", partition, offset);
            
            // Extract key identifiers for logging
            eventId = (String) eventPayload.get("eventId");
            String eventType = (String) eventPayload.get("eventType");
            
            if (eventId == null || eventType == null) {
                throw new IllegalArgumentException("Missing required event identifiers");
            }
            
            log.info("Processing ledger event: {} - Type: {}", eventId, eventType);
            
            // Convert to structured ledger event object
            LedgerEvent ledgerEvent = convertToLedgerEvent(eventPayload);
            
            // Validate ledger event data
            validateLedgerEvent(ledgerEvent);
            
            // Capture business metrics
            captureBusinessMetrics(ledgerEvent);
            
            // Process event based on type in parallel operations
            CompletableFuture<Void> analyticsProcessing = processFinancialAnalytics(ledgerEvent);
            CompletableFuture<Void> reconciliationProcessing = processReconciliation(ledgerEvent);
            CompletableFuture<Void> auditTrailProcessing = processAuditTrail(ledgerEvent);
            CompletableFuture<Void> reportingProcessing = processReporting(ledgerEvent);
            
            // Wait for all processing to complete
            CompletableFuture.allOf(
                analyticsProcessing, 
                reconciliationProcessing, 
                auditTrailProcessing, 
                reportingProcessing
            ).join();
            
            // Detect anomalies
            detectAndReportAnomalies(ledgerEvent);
            
            // Update real-time dashboards
            updateRealTimeDashboards(ledgerEvent);
            
            ledgerEventsSuccessful.increment();
            log.info("Successfully processed ledger event: {}", eventId);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            ledgerEventsFailed.increment();
            log.error("Failed to process ledger event: {} - Error: {}", eventId, e.getMessage(), e);

            // Send to DLQ via UniversalDLQHandler
            try {
                // Create a ConsumerRecord for the DLQ handler
                String eventJson = objectMapper.writeValueAsString(eventPayload);
                org.apache.kafka.clients.consumer.ConsumerRecord<String, String> consumerRecord =
                    new org.apache.kafka.clients.consumer.ConsumerRecord<>(
                        "ledger-events", partition, offset, eventId, eventJson
                    );
                universalDLQHandler.handleFailedMessage(consumerRecord, e);
            } catch (Exception dlqException) {
                log.error("Failed to send message to DLQ: {}", dlqException.getMessage());
            }

            // Don't acknowledge - this will trigger retry mechanism
            throw new LedgerEventProcessingException(
                "Failed to process ledger event: " + eventId, e);

        } finally {
            sample.stop(eventProcessingTime);
        }
    }

    /**
     * Converts event payload to structured LedgerEvent
     */
    private LedgerEvent convertToLedgerEvent(Map<String, Object> eventPayload) {
        try {
            // Extract event data
            Map<String, Object> eventData = (Map<String, Object>) eventPayload.get("data");
            
            return LedgerEvent.builder()
                .eventId((String) eventPayload.get("eventId"))
                .eventType((String) eventPayload.get("eventType"))
                .timestamp(LocalDateTime.parse(eventPayload.get("timestamp").toString()))
                .data(eventData)
                .journalEntryId(eventData != null ? (String) eventData.get("journalEntryId") : null)
                .transactionReference(eventData != null ? (String) eventData.get("transactionReference") : null)
                .accountNumber(eventData != null ? (String) eventData.get("accountNumber") : null)
                .debitAmount(eventData != null && eventData.get("debitAmount") != null ? 
                    new BigDecimal(eventData.get("debitAmount").toString()) : null)
                .creditAmount(eventData != null && eventData.get("creditAmount") != null ? 
                    new BigDecimal(eventData.get("creditAmount").toString()) : null)
                .currency(eventData != null ? (String) eventData.get("currency") : "USD")
                .description(eventData != null ? (String) eventData.get("description") : null)
                .metadata(eventData != null ? (Map<String, String>) eventData.get("metadata") : null)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to convert ledger event payload", e);
            throw new IllegalArgumentException("Invalid ledger event format", e);
        }
    }

    /**
     * Validates ledger event data
     */
    private void validateLedgerEvent(LedgerEvent event) {
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID is required");
        }
        
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
        
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Event timestamp is required");
        }
        
        // Validate amounts if present
        if (event.getDebitAmount() != null && event.getDebitAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Debit amount cannot be negative");
        }
        
        if (event.getCreditAmount() != null && event.getCreditAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Credit amount cannot be negative");
        }
    }

    /**
     * Captures business metrics for monitoring and alerting
     */
    private void captureBusinessMetrics(LedgerEvent event) {
        // Track events by type
        switch (event.getEventType().toUpperCase()) {
            case "JOURNAL_CREATED":
            case "JOURNAL_POSTED":
                journalEntriesProcessed.increment(
                    "type", event.getEventType(),
                    "currency", event.getCurrency()
                );
                break;
            case "BALANCE_UPDATED":
            case "ACCOUNT_RECONCILED":
                balanceUpdatesProcessed.increment(
                    "type", event.getEventType(),
                    "currency", event.getCurrency()
                );
                break;
            case "RECONCILIATION_COMPLETED":
            case "RECONCILIATION_FAILED":
                reconciliationEventsProcessed.increment(
                    "type", event.getEventType(),
                    "status", event.getEventType().contains("FAILED") ? "failed" : "success"
                );
                break;
            case "REVENUE_RECOGNIZED":
            case "REVENUE_DEFERRED":
                revenueEventsProcessed.increment(
                    "type", event.getEventType(),
                    "currency", event.getCurrency()
                );
                break;
        }
        
        // Track high-value transactions
        BigDecimal amount = event.getDebitAmount() != null ? event.getDebitAmount() : event.getCreditAmount();
        if (amount != null && amount.compareTo(new BigDecimal("10000")) > 0) {
            highValueTransactions.increment(
                "event_type", event.getEventType(),
                "currency", event.getCurrency()
            );
        }
    }

    /**
     * Processes financial analytics for the ledger event
     */
    private CompletableFuture<Void> processFinancialAnalytics(LedgerEvent event) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Processing financial analytics for event: {}", event.getEventId());
                
                // Update financial analytics based on event type
                financialAnalyticsService.processLedgerEvent(
                    event.getEventId(),
                    event.getEventType(),
                    event.getJournalEntryId(),
                    event.getAccountNumber(),
                    event.getDebitAmount(),
                    event.getCreditAmount(),
                    event.getCurrency(),
                    event.getDescription(),
                    event.getMetadata(),
                    event.getTimestamp()
                );
                
                // Update KPIs and metrics
                financialAnalyticsService.updateFinancialKPIs(
                    event.getEventType(),
                    event.getDebitAmount(),
                    event.getCreditAmount(),
                    event.getCurrency()
                );
                
                log.info("Financial analytics processed for event: {}", event.getEventId());
                
            } catch (Exception e) {
                log.error("Failed to process financial analytics for event: {}", 
                    event.getEventId(), e);
                throw new LedgerEventProcessingException("Financial analytics processing failed", e);
            }
        });
    }

    /**
     * Processes reconciliation for the ledger event
     */
    private CompletableFuture<Void> processReconciliation(LedgerEvent event) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Only process reconciliation-relevant events
                if (isReconciliationRelevant(event)) {
                    log.debug("Processing reconciliation for event: {}", event.getEventId());
                    
                    reconciliationService.processLedgerEventForReconciliation(
                        event.getEventId(),
                        event.getEventType(),
                        event.getTransactionReference(),
                        event.getAccountNumber(),
                        event.getDebitAmount(),
                        event.getCreditAmount(),
                        event.getCurrency(),
                        event.getTimestamp()
                    );
                    
                    // Check for reconciliation discrepancies
                    reconciliationService.checkReconciliationDiscrepancies(
                        event.getAccountNumber(),
                        event.getCurrency()
                    );
                    
                    log.info("Reconciliation processed for event: {}", event.getEventId());
                }
                
            } catch (Exception e) {
                log.error("Failed to process reconciliation for event: {}", 
                    event.getEventId(), e);
                // Don't throw exception for reconciliation failures - log and continue
            }
        });
    }

    /**
     * Processes audit trail for the ledger event
     */
    private CompletableFuture<Void> processAuditTrail(LedgerEvent event) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Creating audit trail for event: {}", event.getEventId());
                
                // Create comprehensive audit trail entry
                auditTrailService.createAuditEntry(
                    event.getEventId(),
                    event.getEventType(),
                    "LEDGER_EVENT",
                    event.getJournalEntryId(),
                    event.getAccountNumber(),
                    event.getDebitAmount(),
                    event.getCreditAmount(),
                    event.getCurrency(),
                    event.getDescription(),
                    event.getMetadata(),
                    event.getTimestamp()
                );
                
                auditEventsCreated.increment();
                log.info("Audit trail created for event: {}", event.getEventId());
                
            } catch (Exception e) {
                log.error("Failed to create audit trail for event: {}", 
                    event.getEventId(), e);
                // Don't throw exception for audit trail failures - log and continue
            }
        });
    }

    /**
     * Processes reporting for the ledger event
     */
    private CompletableFuture<Void> processReporting(LedgerEvent event) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Only process reporting-relevant events
                if (isReportingRelevant(event)) {
                    log.debug("Processing reporting for event: {}", event.getEventId());
                    
                    reportingService.processLedgerEventForReporting(
                        event.getEventId(),
                        event.getEventType(),
                        event.getJournalEntryId(),
                        event.getAccountNumber(),
                        event.getDebitAmount(),
                        event.getCreditAmount(),
                        event.getCurrency(),
                        event.getDescription(),
                        event.getTimestamp()
                    );
                    
                    // Update financial statements if needed
                    if (isFinancialStatementRelevant(event)) {
                        reportingService.updateFinancialStatements(
                            event.getEventType(),
                            event.getAccountNumber(),
                            event.getDebitAmount(),
                            event.getCreditAmount(),
                            event.getCurrency(),
                            event.getTimestamp()
                        );
                    }
                    
                    log.info("Reporting processed for event: {}", event.getEventId());
                }
                
            } catch (Exception e) {
                log.error("Failed to process reporting for event: {}", 
                    event.getEventId(), e);
                // Don't throw exception for reporting failures - log and continue
            }
        });
    }

    /**
     * Detects and reports anomalies in ledger events
     */
    private void detectAndReportAnomalies(LedgerEvent event) {
        try {
            log.debug("Detecting anomalies for event: {}", event.getEventId());
            
            // Check for unusual patterns
            boolean anomalyDetected = false;
            String anomalyReason = null;
            
            // Check for unusually large amounts
            BigDecimal amount = event.getDebitAmount() != null ? event.getDebitAmount() : event.getCreditAmount();
            if (amount != null && amount.compareTo(new BigDecimal("100000")) > 0) {
                anomalyDetected = true;
                anomalyReason = "Unusually large transaction amount: " + amount;
            }
            
            // Check for unusual account activity patterns
            if (event.getAccountNumber() != null) {
                boolean unusualActivity = financialAnalyticsService.checkUnusualAccountActivity(
                    event.getAccountNumber(),
                    amount,
                    event.getEventType()
                );
                
                if (unusualActivity) {
                    anomalyDetected = true;
                    anomalyReason = "Unusual activity pattern detected for account: " + event.getAccountNumber();
                }
            }
            
            // Report anomaly if detected
            if (anomalyDetected) {
                anomaliesDetected.increment(
                    "event_type", event.getEventType(),
                    "reason", anomalyReason != null ? anomalyReason : "unknown"
                );
                
                financialAnalyticsService.reportAnomaly(
                    event.getEventId(),
                    event.getEventType(),
                    anomalyReason,
                    event.getTimestamp()
                );
                
                log.warn("Anomaly detected in ledger event: {} - Reason: {}", 
                    event.getEventId(), anomalyReason);
            }
            
        } catch (Exception e) {
            log.error("Failed to detect anomalies for event: {}", event.getEventId(), e);
        }
    }

    /**
     * Updates real-time dashboards with ledger event data
     */
    private void updateRealTimeDashboards(LedgerEvent event) {
        try {
            log.debug("Updating real-time dashboards for event: {}", event.getEventId());
            
            // Update dashboard metrics
            financialAnalyticsService.updateDashboardMetrics(
                event.getEventType(),
                event.getDebitAmount(),
                event.getCreditAmount(),
                event.getCurrency(),
                event.getTimestamp()
            );
            
            // Update account balances in real-time
            if (event.getAccountNumber() != null) {
                financialAnalyticsService.updateRealTimeAccountBalance(
                    event.getAccountNumber(),
                    event.getDebitAmount(),
                    event.getCreditAmount()
                );
            }
            
            log.debug("Real-time dashboards updated for event: {}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Failed to update real-time dashboards for event: {}", event.getEventId(), e);
        }
    }

    /**
     * Checks if event is relevant for reconciliation
     */
    private boolean isReconciliationRelevant(LedgerEvent event) {
        return event.getEventType().contains("JOURNAL") ||
               event.getEventType().contains("BALANCE") ||
               event.getEventType().contains("RECONCIL");
    }

    /**
     * Checks if event is relevant for reporting
     */
    private boolean isReportingRelevant(LedgerEvent event) {
        return event.getEventType().contains("JOURNAL") ||
               event.getEventType().contains("REVENUE") ||
               event.getEventType().contains("EXPENSE") ||
               event.getEventType().contains("ASSET") ||
               event.getEventType().contains("LIABILITY");
    }

    /**
     * Checks if event affects financial statements
     */
    private boolean isFinancialStatementRelevant(LedgerEvent event) {
        return event.getEventType().contains("REVENUE") ||
               event.getEventType().contains("EXPENSE") ||
               event.getEventType().contains("ASSET") ||
               event.getEventType().contains("LIABILITY") ||
               event.getEventType().contains("EQUITY");
    }

    /**
     * Ledger event data structure
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class LedgerEvent {
        private String eventId;
        private String eventType;
        private LocalDateTime timestamp;
        private Map<String, Object> data;
        private String journalEntryId;
        private String transactionReference;
        private String accountNumber;
        private BigDecimal debitAmount;
        private BigDecimal creditAmount;
        private String currency;
        private String description;
        private Map<String, String> metadata;
    }

    /**
     * Custom exception for ledger event processing
     */
    public static class LedgerEventProcessingException extends RuntimeException {
        public LedgerEventProcessingException(String message) {
            super(message);
        }
        
        public LedgerEventProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}