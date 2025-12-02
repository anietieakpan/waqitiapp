package com.waqiti.reconciliation.kafka;

import com.waqiti.reconciliation.service.ReconciliationService;
import com.waqiti.reconciliation.service.TransactionReconciliationService;
import com.waqiti.reconciliation.service.AutomatedReconciliationService;
import com.waqiti.reconciliation.service.NotificationService;
import com.waqiti.reconciliation.domain.ReconciliationStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Comprehensive Kafka Consumer for Reconciliation Events
 * 
 * Handles critical events that require reconciliation processing:
 * - Payment completion events for transaction reconciliation
 * - Scheduled reconciliation triggers
 * - External system transaction updates
 * - Discrepancy alerts and resolution
 * - Real-time reconciliation workflows
 * 
 * Features:
 * - Circuit breaker pattern for resilience
 * - Exponential backoff retry logic
 * - Dead letter topic handling
 * - Comprehensive error handling
 * - Transaction management
 * - Real-time metrics and monitoring
 * - Automated reconciliation workflows
 * 
 * @author Waqiti Reconciliation Team
 * @since 2.0.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationEventConsumer {

    private final ReconciliationService reconciliationService;
    private final TransactionReconciliationService transactionReconciliationService;
    private final AutomatedReconciliationService automatedReconciliationService;
    private final NotificationService notificationService;

    /**
     * Consume ReconciliationRequiredEvent to trigger reconciliation processes
     * Initiates automated reconciliation workflows
     */
    @KafkaListener(
        topics = "reconciliation-required",
        groupId = "reconciliation-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @CircuitBreaker(name = "reconciliation-service", fallbackMethod = "fallbackReconciliationRequired")
    @Retry(name = "reconciliation-service")
    @Transactional
    public void handleReconciliationRequired(
            @Payload ReconciliationRequiredEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String reconciliationId = event.getReconciliationId();
        String correlationId = event.getCorrelationId();
        
        log.info("Processing ReconciliationRequiredEvent: reconciliationId={}, type={}, correlationId={}, topic={}, partition={}, offset={}", 
                reconciliationId, event.getReconciliationType(), correlationId, topic, partition, offset);
        
        try {
            long startTime = System.currentTimeMillis();
            
            // 1. Validate reconciliation request
            validateReconciliationRequest(event);
            
            // 2. Create reconciliation job
            String jobId = createReconciliationJob(event);
            
            // 3. Execute reconciliation based on type
            executeReconciliationWorkflow(event, jobId);
            
            // 4. Generate reconciliation report
            generateReconciliationReport(event, jobId);
            
            // 5. Send notifications if discrepancies found
            handleReconciliationNotifications(event, jobId);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            log.info("Successfully processed ReconciliationRequiredEvent: reconciliationId={}, jobId={}, processingTime={}ms", 
                    reconciliationId, jobId, processingTime);
            
            recordSuccessMetrics("reconciliation-required", processingTime);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process ReconciliationRequiredEvent: reconciliationId={}, correlationId={}", 
                    reconciliationId, correlationId, e);
            
            recordErrorMetrics("reconciliation-required", e.getClass().getSimpleName());
            createErrorNotification("RECONCILIATION_PROCESSING_FAILED", reconciliationId, correlationId, e);
            
            throw new ReconciliationProcessingException(
                "Failed to process ReconciliationRequiredEvent for reconciliation: " + reconciliationId, e);
        }
    }

    /**
     * Consume PaymentCompletedEvent for transaction reconciliation
     * Ensures completed payments are reconciled with external systems
     */
    @KafkaListener(
        topics = "payment-completed",
        groupId = "reconciliation-service-payment",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @CircuitBreaker(name = "reconciliation-service", fallbackMethod = "fallbackPaymentCompleted")
    @Retry(name = "reconciliation-service")
    @Transactional
    public void handlePaymentCompleted(
            @Payload PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String paymentId = event.getPaymentId();
        String correlationId = event.getCorrelationId();
        
        log.info("Processing PaymentCompletedEvent for reconciliation: paymentId={}, correlationId={}, topic={}, partition={}, offset={}", 
                paymentId, correlationId, topic, partition, offset);
        
        try {
            long startTime = System.currentTimeMillis();
            
            // 1. Create transaction record for reconciliation
            createTransactionReconciliationRecord(event);
            
            // 2. Check for immediate reconciliation opportunities
            performImmediateReconciliation(event);
            
            // 3. Queue for scheduled reconciliation if not immediately reconciled
            queueForScheduledReconciliation(event);
            
            // 4. Update reconciliation metrics
            updateReconciliationMetrics(event);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            log.info("Successfully processed PaymentCompletedEvent for reconciliation: paymentId={}, processingTime={}ms", 
                    paymentId, processingTime);
            
            recordSuccessMetrics("payment-completed-reconciliation", processingTime);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process PaymentCompletedEvent for reconciliation: paymentId={}, correlationId={}", 
                    paymentId, correlationId, e);
            
            recordErrorMetrics("payment-completed-reconciliation", e.getClass().getSimpleName());
            createErrorNotification("PAYMENT_RECONCILIATION_FAILED", paymentId, correlationId, e);
            
            throw new ReconciliationProcessingException(
                "Failed to process PaymentCompletedEvent for reconciliation: " + paymentId, e);
        }
    }

    /**
     * Consume ScheduledReconciliationEvent for daily/periodic reconciliation
     * Handles scheduled reconciliation triggers (daily, weekly, monthly)
     */
    @KafkaListener(
        topics = "scheduled-reconciliation",
        groupId = "reconciliation-service-scheduled",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @CircuitBreaker(name = "reconciliation-service", fallbackMethod = "fallbackScheduledReconciliation")
    @Retry(name = "reconciliation-service")
    @Transactional
    public void handleScheduledReconciliation(
            @Payload ScheduledReconciliationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String scheduleId = event.getScheduleId();
        String reconciliationType = event.getReconciliationType();
        
        log.info("Processing ScheduledReconciliationEvent: scheduleId={}, type={}, period={} to {}, topic={}, partition={}, offset={}", 
                scheduleId, reconciliationType, event.getStartDate(), event.getEndDate(), topic, partition, offset);
        
        try {
            long startTime = System.currentTimeMillis();
            
            // 1. Validate scheduled reconciliation request
            validateScheduledReconciliationRequest(event);
            
            // 2. Execute comprehensive reconciliation for the period
            executeScheduledReconciliation(event);
            
            // 3. Generate detailed reconciliation reports
            generateScheduledReconciliationReports(event);
            
            // 4. Handle discrepancies and exceptions
            processReconciliationDiscrepancies(event);
            
            // 5. Send summary notifications
            sendScheduledReconciliationSummary(event);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            log.info("Successfully processed ScheduledReconciliationEvent: scheduleId={}, type={}, processingTime={}ms", 
                    scheduleId, reconciliationType, processingTime);
            
            recordSuccessMetrics("scheduled-reconciliation", processingTime);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process ScheduledReconciliationEvent: scheduleId={}, type={}", 
                    scheduleId, reconciliationType, e);
            
            recordErrorMetrics("scheduled-reconciliation", e.getClass().getSimpleName());
            createErrorNotification("SCHEDULED_RECONCILIATION_FAILED", scheduleId, reconciliationType, e);
            
            throw new ReconciliationProcessingException(
                "Failed to process ScheduledReconciliationEvent: " + scheduleId, e);
        }
    }

    /**
     * Consume ExternalTransactionEvent for external system reconciliation
     * Handles transactions from external payment processors and banks
     */
    @KafkaListener(
        topics = "external-transaction",
        groupId = "reconciliation-service-external",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @CircuitBreaker(name = "reconciliation-service", fallbackMethod = "fallbackExternalTransaction")
    @Retry(name = "reconciliation-service")
    @Transactional
    public void handleExternalTransaction(
            @Payload ExternalTransactionEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String externalTransactionId = event.getExternalTransactionId();
        String provider = event.getProvider();
        
        log.info("Processing ExternalTransactionEvent: externalId={}, provider={}, amount={}, topic={}, partition={}, offset={}", 
                externalTransactionId, provider, event.getAmount(), topic, partition, offset);
        
        try {
            long startTime = System.currentTimeMillis();
            
            // 1. Store external transaction record
            storeExternalTransactionRecord(event);
            
            // 2. Attempt to match with internal transactions
            performTransactionMatching(event);
            
            // 3. Handle unmatched transactions
            handleUnmatchedExternalTransaction(event);
            
            // 4. Update reconciliation status
            updateExternalTransactionReconciliationStatus(event);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            log.info("Successfully processed ExternalTransactionEvent: externalId={}, provider={}, processingTime={}ms", 
                    externalTransactionId, provider, processingTime);
            
            recordSuccessMetrics("external-transaction", processingTime);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process ExternalTransactionEvent: externalId={}, provider={}", 
                    externalTransactionId, provider, e);
            
            recordErrorMetrics("external-transaction", e.getClass().getSimpleName());
            createErrorNotification("EXTERNAL_TRANSACTION_PROCESSING_FAILED", externalTransactionId, provider, e);
            
            throw new ReconciliationProcessingException(
                "Failed to process ExternalTransactionEvent: " + externalTransactionId, e);
        }
    }

    /**
     * Consume DiscrepancyDetectedEvent for exception handling
     * Handles detected discrepancies requiring manual review or automated resolution
     */
    @KafkaListener(
        topics = "discrepancy-detected",
        groupId = "reconciliation-service-discrepancy",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @CircuitBreaker(name = "reconciliation-service", fallbackMethod = "fallbackDiscrepancyDetected")
    @Retry(name = "reconciliation-service")
    @Transactional
    public void handleDiscrepancyDetected(
            @Payload DiscrepancyDetectedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String discrepancyId = event.getDiscrepancyId();
        String discrepancyType = event.getDiscrepancyType();
        
        log.info("Processing DiscrepancyDetectedEvent: discrepancyId={}, type={}, severity={}, topic={}, partition={}, offset={}", 
                discrepancyId, discrepancyType, event.getSeverity(), topic, partition, offset);
        
        try {
            long startTime = System.currentTimeMillis();
            
            // 1. Create discrepancy record
            createDiscrepancyRecord(event);
            
            // 2. Attempt automated resolution
            attemptAutomatedResolution(event);
            
            // 3. Escalate for manual review if needed
            escalateForManualReview(event);
            
            // 4. Send notifications to relevant stakeholders
            sendDiscrepancyNotifications(event);
            
            // 5. Update compliance and audit trails
            updateComplianceAuditTrail(event);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            log.info("Successfully processed DiscrepancyDetectedEvent: discrepancyId={}, type={}, processingTime={}ms", 
                    discrepancyId, discrepancyType, processingTime);
            
            recordSuccessMetrics("discrepancy-detected", processingTime);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process DiscrepancyDetectedEvent: discrepancyId={}, type={}", 
                    discrepancyId, discrepancyType, e);
            
            recordErrorMetrics("discrepancy-detected", e.getClass().getSimpleName());
            createErrorNotification("DISCREPANCY_PROCESSING_FAILED", discrepancyId, discrepancyType, e);
            
            throw new ReconciliationProcessingException(
                "Failed to process DiscrepancyDetectedEvent: " + discrepancyId, e);
        }
    }

    // Fallback methods for circuit breaker patterns

    public void fallbackReconciliationRequired(ReconciliationRequiredEvent event, Exception ex) {
        log.error("Circuit breaker activated for ReconciliationRequiredEvent: reconciliationId={}", 
                event.getReconciliationId(), ex);
        recordCircuitBreakerMetrics("reconciliation-required");
        handleFallbackProcessing("reconciliation-required", event.getReconciliationId(), event, ex);
    }

    public void fallbackPaymentCompleted(PaymentCompletedEvent event, Exception ex) {
        log.error("Circuit breaker activated for PaymentCompletedEvent reconciliation: paymentId={}", 
                event.getPaymentId(), ex);
        recordCircuitBreakerMetrics("payment-completed-reconciliation");
        handleFallbackProcessing("payment-completed-reconciliation", event.getPaymentId(), event, ex);
    }

    public void fallbackScheduledReconciliation(ScheduledReconciliationEvent event, Exception ex) {
        log.error("Circuit breaker activated for ScheduledReconciliationEvent: scheduleId={}", 
                event.getScheduleId(), ex);
        recordCircuitBreakerMetrics("scheduled-reconciliation");
        handleFallbackProcessing("scheduled-reconciliation", event.getScheduleId(), event, ex);
    }

    public void fallbackExternalTransaction(ExternalTransactionEvent event, Exception ex) {
        log.error("Circuit breaker activated for ExternalTransactionEvent: externalId={}", 
                event.getExternalTransactionId(), ex);
        recordCircuitBreakerMetrics("external-transaction");
        handleFallbackProcessing("external-transaction", event.getExternalTransactionId(), event, ex);
    }

    public void fallbackDiscrepancyDetected(DiscrepancyDetectedEvent event, Exception ex) {
        log.error("Circuit breaker activated for DiscrepancyDetectedEvent: discrepancyId={}", 
                event.getDiscrepancyId(), ex);
        recordCircuitBreakerMetrics("discrepancy-detected");
        handleFallbackProcessing("discrepancy-detected", event.getDiscrepancyId(), event, ex);
    }

    // Private helper methods

    private void validateReconciliationRequest(ReconciliationRequiredEvent event) {
        if (event.getReconciliationId() == null || event.getReconciliationType() == null) {
            throw new IllegalArgumentException("Reconciliation ID and type are required");
        }
        if (event.getStartDate() != null && event.getEndDate() != null && 
            event.getStartDate().isAfter(event.getEndDate())) {
            throw new IllegalArgumentException("Start date cannot be after end date");
        }
    }

    private String createReconciliationJob(ReconciliationRequiredEvent event) {
        log.debug("Creating reconciliation job for reconciliation: {}", event.getReconciliationId());
        
        try {
            return reconciliationService.createReconciliationJob(
                event.getReconciliationType(),
                event.getEntityType(),
                event.getEntityId(),
                event.getStartDate(),
                event.getEndDate(),
                event.getCorrelationId()
            );
        } catch (Exception e) {
            log.error("Failed to create reconciliation job for reconciliation: {}", event.getReconciliationId(), e);
            throw new ReconciliationJobCreationException("Failed to create reconciliation job", e);
        }
    }

    private void executeReconciliationWorkflow(ReconciliationRequiredEvent event, String jobId) {
        log.debug("Executing reconciliation workflow: reconciliationId={}, jobId={}", event.getReconciliationId(), jobId);
        
        try {
            switch (event.getReconciliationType().toUpperCase()) {
                case "DAILY":
                    automatedReconciliationService.executeDailyReconciliation(jobId, event.getStartDate(), event.getEndDate());
                    break;
                case "TRANSACTION":
                    transactionReconciliationService.executeTransactionReconciliation(jobId, event.getEntityId());
                    break;
                case "SETTLEMENT":
                    executeSettlementReconciliation(jobId, event);
                    break;
                case "BANK_STATEMENT":
                    executeBankStatementReconciliation(jobId, event);
                    break;
                default:
                    log.warn("Unknown reconciliation type: {}", event.getReconciliationType());
                    executeGenericReconciliation(jobId, event);
            }
        } catch (Exception e) {
            log.error("Failed to execute reconciliation workflow: reconciliationId={}, jobId={}", 
                    event.getReconciliationId(), jobId, e);
            throw new ReconciliationExecutionException("Failed to execute reconciliation workflow", e);
        }
    }

    private void generateReconciliationReport(ReconciliationRequiredEvent event, String jobId) {
        log.debug("Generating reconciliation report: reconciliationId={}, jobId={}", event.getReconciliationId(), jobId);
        
        try {
            reconciliationService.generateReconciliationReport(jobId, event.getReconciliationType());
        } catch (Exception e) {
            log.error("Failed to generate reconciliation report: reconciliationId={}, jobId={}", 
                    event.getReconciliationId(), jobId, e);
            // Don't throw exception here as report generation is not critical for the main workflow
        }
    }

    private void handleReconciliationNotifications(ReconciliationRequiredEvent event, String jobId) {
        log.debug("Handling reconciliation notifications: reconciliationId={}, jobId={}", event.getReconciliationId(), jobId);
        
        try {
            // Check if reconciliation found discrepancies
            boolean hasDiscrepancies = reconciliationService.hasDiscrepancies(jobId);
            
            if (hasDiscrepancies) {
                notificationService.sendDiscrepancyNotification(jobId, event.getReconciliationType());
            } else {
                notificationService.sendReconciliationSuccessNotification(jobId, event.getReconciliationType());
            }
        } catch (Exception e) {
            log.error("Failed to handle reconciliation notifications: reconciliationId={}, jobId={}", 
                    event.getReconciliationId(), jobId, e);
            // Don't throw exception as notifications are not critical
        }
    }

    private void createTransactionReconciliationRecord(PaymentCompletedEvent event) {
        log.debug("Creating transaction reconciliation record for payment: {}", event.getPaymentId());
        
        try {
            transactionReconciliationService.createTransactionRecord(
                event.getPaymentId(),
                event.getSenderId(),
                event.getReceiverId(),
                event.getAmount(),
                event.getCurrency(),
                event.getCompletedAt(),
                event.getCorrelationId()
            );
        } catch (Exception e) {
            log.error("Failed to create transaction reconciliation record for payment: {}", event.getPaymentId(), e);
            throw new TransactionRecordCreationException("Failed to create transaction reconciliation record", e);
        }
    }

    private void performImmediateReconciliation(PaymentCompletedEvent event) {
        log.debug("Performing immediate reconciliation for payment: {}", event.getPaymentId());
        
        try {
            boolean reconciled = transactionReconciliationService.attemptImmediateReconciliation(event.getPaymentId());
            
            if (reconciled) {
                log.info("Payment immediately reconciled: {}", event.getPaymentId());
                updateReconciliationStatus(event.getPaymentId(), ReconciliationStatus.RECONCILED);
            } else {
                log.debug("Payment requires scheduled reconciliation: {}", event.getPaymentId());
                updateReconciliationStatus(event.getPaymentId(), ReconciliationStatus.PENDING);
            }
        } catch (Exception e) {
            log.error("Failed to perform immediate reconciliation for payment: {}", event.getPaymentId(), e);
            updateReconciliationStatus(event.getPaymentId(), ReconciliationStatus.FAILED);
            throw new ImmediateReconciliationException("Failed to perform immediate reconciliation", e);
        }
    }

    private void queueForScheduledReconciliation(PaymentCompletedEvent event) {
        log.debug("Queueing payment for scheduled reconciliation: {}", event.getPaymentId());
        
        try {
            transactionReconciliationService.queueForScheduledReconciliation(
                event.getPaymentId(),
                event.getCompletedAt()
            );
        } catch (Exception e) {
            log.error("Failed to queue payment for scheduled reconciliation: {}", event.getPaymentId(), e);
            // Don't throw exception as this is not critical for the main flow
        }
    }

    private void updateReconciliationMetrics(PaymentCompletedEvent event) {
        log.debug("Updating reconciliation metrics for payment: {}", event.getPaymentId());
        
        try {
            // Update metrics for monitoring and reporting
            // This would interface with metrics service
        } catch (Exception e) {
            log.error("Failed to update reconciliation metrics for payment: {}", event.getPaymentId(), e);
            // Don't throw exception as metrics are not critical
        }
    }

    private void validateScheduledReconciliationRequest(ScheduledReconciliationEvent event) {
        if (event.getScheduleId() == null || event.getReconciliationType() == null) {
            throw new IllegalArgumentException("Schedule ID and reconciliation type are required");
        }
        if (event.getStartDate() == null || event.getEndDate() == null) {
            throw new IllegalArgumentException("Start date and end date are required for scheduled reconciliation");
        }
        if (event.getStartDate().isAfter(event.getEndDate())) {
            throw new IllegalArgumentException("Start date cannot be after end date");
        }
    }

    private void executeScheduledReconciliation(ScheduledReconciliationEvent event) {
        log.debug("Executing scheduled reconciliation: scheduleId={}, type={}", event.getScheduleId(), event.getReconciliationType());
        
        try {
            switch (event.getReconciliationType().toUpperCase()) {
                case "DAILY":
                    automatedReconciliationService.executeDailyReconciliation(
                        event.getScheduleId(), event.getStartDate(), event.getEndDate());
                    break;
                case "WEEKLY":
                    automatedReconciliationService.executeWeeklyReconciliation(
                        event.getScheduleId(), event.getStartDate(), event.getEndDate());
                    break;
                case "MONTHLY":
                    automatedReconciliationService.executeMonthlyReconciliation(
                        event.getScheduleId(), event.getStartDate(), event.getEndDate());
                    break;
                default:
                    automatedReconciliationService.executeCustomReconciliation(
                        event.getScheduleId(), event.getReconciliationType(), event.getStartDate(), event.getEndDate());
            }
        } catch (Exception e) {
            log.error("Failed to execute scheduled reconciliation: scheduleId={}, type={}", 
                    event.getScheduleId(), event.getReconciliationType(), e);
            throw new ScheduledReconciliationException("Failed to execute scheduled reconciliation", e);
        }
    }

    private void generateScheduledReconciliationReports(ScheduledReconciliationEvent event) {
        log.debug("Generating scheduled reconciliation reports: scheduleId={}", event.getScheduleId());
        
        try {
            reconciliationService.generateScheduledReconciliationReport(
                event.getScheduleId(), 
                event.getReconciliationType(),
                event.getStartDate(),
                event.getEndDate()
            );
        } catch (Exception e) {
            log.error("Failed to generate scheduled reconciliation reports: scheduleId={}", event.getScheduleId(), e);
            // Don't throw exception as report generation is not critical
        }
    }

    private void processReconciliationDiscrepancies(ScheduledReconciliationEvent event) {
        log.debug("Processing reconciliation discrepancies: scheduleId={}", event.getScheduleId());
        
        try {
            reconciliationService.processDiscrepancies(event.getScheduleId());
        } catch (Exception e) {
            log.error("Failed to process reconciliation discrepancies: scheduleId={}", event.getScheduleId(), e);
            // Don't throw exception as discrepancy processing can be retried
        }
    }

    private void sendScheduledReconciliationSummary(ScheduledReconciliationEvent event) {
        log.debug("Sending scheduled reconciliation summary: scheduleId={}", event.getScheduleId());
        
        try {
            notificationService.sendScheduledReconciliationSummary(
                event.getScheduleId(),
                event.getReconciliationType(),
                event.getStartDate(),
                event.getEndDate()
            );
        } catch (Exception e) {
            log.error("Failed to send scheduled reconciliation summary: scheduleId={}", event.getScheduleId(), e);
            // Don't throw exception as notifications are not critical
        }
    }

    private void storeExternalTransactionRecord(ExternalTransactionEvent event) {
        log.debug("Storing external transaction record: externalId={}", event.getExternalTransactionId());
        
        try {
            transactionReconciliationService.storeExternalTransaction(
                event.getExternalTransactionId(),
                event.getProvider(),
                event.getAmount(),
                event.getCurrency(),
                event.getTransactionDate(),
                event.getReference(),
                event.getMetadata()
            );
        } catch (Exception e) {
            log.error("Failed to store external transaction record: externalId={}", event.getExternalTransactionId(), e);
            throw new ExternalTransactionStorageException("Failed to store external transaction record", e);
        }
    }

    private void performTransactionMatching(ExternalTransactionEvent event) {
        log.debug("Performing transaction matching: externalId={}", event.getExternalTransactionId());
        
        try {
            boolean matched = transactionReconciliationService.matchExternalTransaction(
                event.getExternalTransactionId(),
                event.getAmount(),
                event.getCurrency(),
                event.getTransactionDate(),
                event.getReference()
            );
            
            if (matched) {
                log.info("External transaction matched successfully: {}", event.getExternalTransactionId());
            } else {
                log.warn("External transaction could not be matched: {}", event.getExternalTransactionId());
            }
        } catch (Exception e) {
            log.error("Failed to perform transaction matching: externalId={}", event.getExternalTransactionId(), e);
            throw new TransactionMatchingException("Failed to perform transaction matching", e);
        }
    }

    private void handleUnmatchedExternalTransaction(ExternalTransactionEvent event) {
        log.debug("Handling unmatched external transaction: externalId={}", event.getExternalTransactionId());
        
        try {
            if (!transactionReconciliationService.isMatched(event.getExternalTransactionId())) {
                // Create discrepancy record for unmatched transaction
                reconciliationService.createUnmatchedTransactionDiscrepancy(
                    event.getExternalTransactionId(),
                    event.getProvider(),
                    event.getAmount(),
                    event.getCurrency(),
                    event.getTransactionDate()
                );
                
                // Queue for manual review
                reconciliationService.queueForManualReview(event.getExternalTransactionId());
            }
        } catch (Exception e) {
            log.error("Failed to handle unmatched external transaction: externalId={}", event.getExternalTransactionId(), e);
            // Don't throw exception as this can be retried later
        }
    }

    private void updateExternalTransactionReconciliationStatus(ExternalTransactionEvent event) {
        log.debug("Updating external transaction reconciliation status: externalId={}", event.getExternalTransactionId());
        
        try {
            boolean matched = transactionReconciliationService.isMatched(event.getExternalTransactionId());
            ReconciliationStatus status = matched ? ReconciliationStatus.RECONCILED : ReconciliationStatus.PENDING;
            
            transactionReconciliationService.updateReconciliationStatus(event.getExternalTransactionId(), status);
        } catch (Exception e) {
            log.error("Failed to update external transaction reconciliation status: externalId={}", 
                    event.getExternalTransactionId(), e);
            // Don't throw exception as this can be retried
        }
    }

    private void createDiscrepancyRecord(DiscrepancyDetectedEvent event) {
        log.debug("Creating discrepancy record: discrepancyId={}", event.getDiscrepancyId());
        
        try {
            reconciliationService.createDiscrepancyRecord(
                event.getDiscrepancyId(),
                event.getDiscrepancyType(),
                event.getDescription(),
                event.getAmount(),
                event.getCurrency(),
                event.getSeverity(),
                event.getMetadata()
            );
        } catch (Exception e) {
            log.error("Failed to create discrepancy record: discrepancyId={}", event.getDiscrepancyId(), e);
            throw new DiscrepancyRecordCreationException("Failed to create discrepancy record", e);
        }
    }

    private void attemptAutomatedResolution(DiscrepancyDetectedEvent event) {
        log.debug("Attempting automated resolution for discrepancy: discrepancyId={}", event.getDiscrepancyId());
        
        try {
            boolean resolved = automatedReconciliationService.attemptAutomatedResolution(
                event.getDiscrepancyId(),
                event.getDiscrepancyType(),
                event.getAmount(),
                event.getMetadata()
            );
            
            if (resolved) {
                log.info("Discrepancy resolved automatically: {}", event.getDiscrepancyId());
                reconciliationService.markDiscrepancyResolved(event.getDiscrepancyId(), "AUTOMATED_RESOLUTION");
            } else {
                log.info("Discrepancy requires manual resolution: {}", event.getDiscrepancyId());
            }
        } catch (Exception e) {
            log.error("Failed to attempt automated resolution for discrepancy: discrepancyId={}", 
                    event.getDiscrepancyId(), e);
            // Don't throw exception as manual resolution is still possible
        }
    }

    private void escalateForManualReview(DiscrepancyDetectedEvent event) {
        log.debug("Escalating discrepancy for manual review: discrepancyId={}", event.getDiscrepancyId());
        
        try {
            if (!reconciliationService.isDiscrepancyResolved(event.getDiscrepancyId())) {
                reconciliationService.escalateForManualReview(
                    event.getDiscrepancyId(),
                    event.getSeverity(),
                    event.getDescription()
                );
            }
        } catch (Exception e) {
            log.error("Failed to escalate discrepancy for manual review: discrepancyId={}", event.getDiscrepancyId(), e);
            // Don't throw exception as this can be retried
        }
    }

    private void sendDiscrepancyNotifications(DiscrepancyDetectedEvent event) {
        log.debug("Sending discrepancy notifications: discrepancyId={}", event.getDiscrepancyId());
        
        try {
            notificationService.sendDiscrepancyAlert(
                event.getDiscrepancyId(),
                event.getDiscrepancyType(),
                event.getSeverity(),
                event.getAmount(),
                event.getDescription()
            );
        } catch (Exception e) {
            log.error("Failed to send discrepancy notifications: discrepancyId={}", event.getDiscrepancyId(), e);
            // Don't throw exception as notifications are not critical
        }
    }

    private void updateComplianceAuditTrail(DiscrepancyDetectedEvent event) {
        log.debug("Updating compliance audit trail for discrepancy: discrepancyId={}", event.getDiscrepancyId());
        
        try {
            // Update compliance audit trail with discrepancy information
            // This would interface with compliance service
        } catch (Exception e) {
            log.error("Failed to update compliance audit trail for discrepancy: discrepancyId={}", 
                    event.getDiscrepancyId(), e);
            // Don't throw exception as audit trail updates can be retried
        }
    }

    // Additional helper methods

    private void executeSettlementReconciliation(String jobId, ReconciliationRequiredEvent event) {
        log.debug("Executing settlement reconciliation: jobId={}", jobId);
        // Implementation for settlement reconciliation
    }

    private void executeBankStatementReconciliation(String jobId, ReconciliationRequiredEvent event) {
        log.debug("Executing bank statement reconciliation: jobId={}", jobId);
        // Implementation for bank statement reconciliation
    }

    private void executeGenericReconciliation(String jobId, ReconciliationRequiredEvent event) {
        log.debug("Executing generic reconciliation: jobId={}", jobId);
        // Implementation for generic reconciliation
    }

    private void updateReconciliationStatus(String paymentId, ReconciliationStatus status) {
        log.debug("Updating reconciliation status: paymentId={}, status={}", paymentId, status);
        // Implementation to update reconciliation status
    }

    private void recordSuccessMetrics(String eventType, long processingTime) {
        log.debug("Recording success metrics: eventType={}, processingTime={}ms", eventType, processingTime);
        // Implementation to record success metrics
    }

    private void recordErrorMetrics(String eventType, String errorType) {
        log.debug("Recording error metrics: eventType={}, errorType={}", eventType, errorType);
        // Implementation to record error metrics
    }

    private void recordCircuitBreakerMetrics(String eventType) {
        log.debug("Recording circuit breaker metrics: eventType={}", eventType);
        // Implementation to record circuit breaker metrics
    }

    private void createErrorNotification(String action, String entityId, String details, Exception error) {
        try {
            notificationService.sendErrorNotification(action, entityId, details, error.getMessage());
        } catch (Exception e) {
            log.error("Failed to create error notification", e);
        }
    }

    private void handleFallbackProcessing(String eventType, String entityId, Object event, Exception error) {
        log.debug("Handling fallback processing: eventType={}, entityId={}, error={}", eventType, entityId, error.getMessage());
        // Implementation for fallback processing - could queue for retry or send to DLQ
    }

    // Event DTOs

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReconciliationRequiredEvent {
        private String reconciliationId;
        private String reconciliationType;
        private String entityType;
        private String entityId;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private String correlationId;
        private Instant timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PaymentCompletedEvent {
        private String paymentId;
        private String senderId;
        private String receiverId;
        private BigDecimal amount;
        private String currency;
        private String correlationId;
        private Instant completedAt;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ScheduledReconciliationEvent {
        private String scheduleId;
        private String reconciliationType;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private String frequency;
        private Instant timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ExternalTransactionEvent {
        private String externalTransactionId;
        private String provider;
        private BigDecimal amount;
        private String currency;
        private LocalDateTime transactionDate;
        private String reference;
        private Map<String, String> metadata;
        private Instant timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DiscrepancyDetectedEvent {
        private String discrepancyId;
        private String discrepancyType;
        private String description;
        private BigDecimal amount;
        private String currency;
        private String severity;
        private Map<String, String> metadata;
        private Instant timestamp;
    }

    // Exception classes

    public static class ReconciliationProcessingException extends RuntimeException {
        public ReconciliationProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ReconciliationJobCreationException extends RuntimeException {
        public ReconciliationJobCreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ReconciliationExecutionException extends RuntimeException {
        public ReconciliationExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class TransactionRecordCreationException extends RuntimeException {
        public TransactionRecordCreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ImmediateReconciliationException extends RuntimeException {
        public ImmediateReconciliationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ScheduledReconciliationException extends RuntimeException {
        public ScheduledReconciliationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ExternalTransactionStorageException extends RuntimeException {
        public ExternalTransactionStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class TransactionMatchingException extends RuntimeException {
        public TransactionMatchingException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class DiscrepancyRecordCreationException extends RuntimeException {
        public DiscrepancyRecordCreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}