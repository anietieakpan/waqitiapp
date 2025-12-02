package com.waqiti.accounting.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.accounting.service.AccountingService;
import com.waqiti.accounting.service.LedgerReconciliationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountingDlqConsumer extends BaseDlqConsumer {

    private final AccountingService accountingService;
    private final LedgerReconciliationService ledgerReconciliationService;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Timer processingTimer;

    public AccountingDlqConsumer(AccountingService accountingService,
                                 LedgerReconciliationService ledgerReconciliationService,
                                 MeterRegistry meterRegistry) {
        super("accounting-dlq");
        this.accountingService = accountingService;
        this.ledgerReconciliationService = ledgerReconciliationService;
        this.meterRegistry = meterRegistry;
        this.processedCounter = Counter.builder("accounting_dlq_processed_total")
                .description("Total accounting DLQ events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("accounting_dlq_errors_total")
                .description("Total accounting DLQ errors")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("accounting_dlq_duration")
                .description("Accounting DLQ processing duration")
                .register(meterRegistry);
    }

    @KafkaListener(
        topics = "accounting-dlq",
        groupId = "accounting-service-accounting-dlq-group",
        containerFactory = "kafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation-level=read_committed",
            "spring.kafka.consumer.auto-offset-reset=earliest",
            "spring.kafka.consumer.max-poll-interval-ms=600000",
            "spring.kafka.consumer.session-timeout-ms=30000",
            "spring.kafka.consumer.heartbeat-interval-ms=10000",
            "spring.kafka.consumer.enable-auto-commit=false"
        }
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30000),
        autoCreateTopics = "true",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class},
        traversingCauses = "true",
        retryTopicSuffix = "-retry",
        dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "accounting-dlq", fallbackMethod = "handleAccountingDlqFallback")
    public void handleAccountingDlq(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            @Header(value = "X-Transaction-Type", required = false) String transactionType,
            @Header(value = "X-Original-Error", required = false) String originalError,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(record, topic, partition, offset);
        String correlationId = generateCorrelationId();

        try {
            if (isAlreadyProcessed(eventId)) {
                log.debug("Accounting DLQ event already processed: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing accounting DLQ event: topic={}, partition={}, offset={}, key={}, " +
                    "correlationId={}, transactionType={}, originalError={}",
                     topic, partition, offset, record.key(), correlationId, transactionType, originalError);

            String accountingData = record.value();
            validateAccountingData(accountingData, eventId);

            // Process accounting DLQ with double-entry verification
            AccountingRecoveryResult result = accountingService.processAccountingDlq(
                accountingData,
                record.key(),
                correlationId,
                transactionType,
                originalError,
                Instant.ofEpochMilli(timestamp != null ? timestamp : System.currentTimeMillis())
            );

            // Perform ledger reconciliation
            performLedgerReconciliation(result, correlationId);

            // Handle recovery result
            if (result.isBalanced()) {
                handleBalancedRecovery(result, correlationId);
            } else {
                handleImbalancedRecovery(result, eventId, correlationId);
            }

            markAsProcessed(eventId);
            processedCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed accounting DLQ event: eventId={}, transactionId={}, " +
                    "correlationId={}, debitTotal={}, creditTotal={}",
                    eventId, result.getTransactionId(), correlationId,
                    result.getDebitTotal(), result.getCreditTotal());

        } catch (ValidationException e) {
            errorCounter.increment();
            log.error("Validation error in accounting DLQ: eventId={}, correlationId={}, error={}",
                     eventId, correlationId, e.getMessage());
            handleValidationFailure(record, e, correlationId);
            acknowledgment.acknowledge();
        } catch (DoubleEntryException e) {
            errorCounter.increment();
            log.error("Double-entry violation in accounting DLQ: eventId={}, correlationId={}, error={}",
                     eventId, correlationId, e.getMessage());
            handleDoubleEntryViolation(record, e, correlationId);
            throw e; // Critical accounting errors must be retried
        } catch (ReconciliationException e) {
            errorCounter.increment();
            log.error("Reconciliation failure in accounting DLQ: eventId={}, correlationId={}, error={}",
                     eventId, correlationId, e.getMessage());
            handleReconciliationFailure(record, e, correlationId);
            throw e;
        } catch (Exception e) {
            errorCounter.increment();
            log.error("Critical error in accounting DLQ: eventId={}, correlationId={}",
                     eventId, correlationId, e);
            handleCriticalAccountingFailure(record, e, correlationId);
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    @DltHandler
    public void handleDlt(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
            @Header(KafkaHeaders.ORIGINAL_OFFSET) long originalOffset,
            @Header(KafkaHeaders.ORIGINAL_PARTITION) int originalPartition,
            @Header(value = "X-Retry-Count", required = false) Integer retryCount) {

        String correlationId = generateCorrelationId();
        log.error("Accounting event sent to DLT - CRITICAL FINANCIAL INTEGRITY ISSUE: " +
                 "topic={}, originalPartition={}, originalOffset={}, correlationId={}, " +
                 "retryCount={}, error={}",
                 topic, originalPartition, originalOffset, correlationId, retryCount, exceptionMessage);

        // Execute critical financial recovery protocol
        executeCriticalFinancialRecoveryProtocol(record, topic, exceptionMessage, correlationId);

        // Store for forensic audit
        storeForForensicAudit(record, topic, exceptionMessage, correlationId, retryCount);

        // Send SOX compliance alert
        sendSoxComplianceAlert(record, topic, exceptionMessage, correlationId);

        // Trigger emergency reconciliation
        triggerEmergencyReconciliation(record, correlationId);

        // Update critical DLT metrics
        Counter.builder("accounting_dlt_critical_events_total")
                .description("Critical accounting events sent to DLT")
                .tag("topic", topic)
                .tag("severity", "critical")
                .tag("compliance_impact", "sox")
                .register(meterRegistry)
                .increment();
    }

    public void handleAccountingDlqFallback(
            ConsumerRecord<String, String> record,
            String topic, int partition, long offset, Long timestamp,
            String transactionType, String originalError,
            Acknowledgment acknowledgment, Exception ex) {

        String correlationId = generateCorrelationId();
        log.error("Circuit breaker activated for accounting DLQ: correlationId={}, error={}",
                 correlationId, ex.getMessage());

        // Store in high-priority recovery queue
        storeInHighPriorityRecoveryQueue(record, correlationId);

        // Send CFO alert
        sendCfoAlert(correlationId, ex);

        // Initiate manual reconciliation process
        initiateManualReconciliation(record, correlationId);

        // Acknowledge to prevent blocking
        acknowledgment.acknowledge();

        // Update circuit breaker metrics
        Counter.builder("accounting_dlq_circuit_breaker_activations_total")
                .tag("severity", "critical")
                .tag("financial_impact", "high")
                .register(meterRegistry)
                .increment();
    }

    private void validateAccountingData(String accountingData, String eventId) {
        if (accountingData == null || accountingData.trim().isEmpty()) {
            throw new ValidationException("Accounting data is null or empty for eventId: " + eventId);
        }

        // Validate required accounting fields
        if (!accountingData.contains("transactionId")) {
            throw new ValidationException("Accounting data missing transactionId for eventId: " + eventId);
        }

        if (!accountingData.contains("debitAccount") || !accountingData.contains("creditAccount")) {
            throw new ValidationException("Accounting data missing debit/credit accounts for eventId: " + eventId);
        }

        if (!accountingData.contains("amount")) {
            throw new ValidationException("Accounting data missing amount for eventId: " + eventId);
        }

        // Validate double-entry bookkeeping rules
        validateDoubleEntryRules(accountingData, eventId);

        // Validate SOX compliance requirements
        validateSoxCompliance(accountingData, eventId);
    }

    private void validateDoubleEntryRules(String accountingData, String eventId) {
        try {
            JsonNode data = objectMapper.readTree(accountingData);
            BigDecimal debitAmount = new BigDecimal(data.get("debitAmount").asText());
            BigDecimal creditAmount = new BigDecimal(data.get("creditAmount").asText());

            if (debitAmount.compareTo(creditAmount) != 0) {
                throw new DoubleEntryException("Debit and credit amounts don't balance for eventId: " + eventId);
            }

            if (debitAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("Invalid amount (must be positive) for eventId: " + eventId);
            }
        } catch (Exception e) {
            throw new ValidationException("Failed to validate double-entry rules: " + e.getMessage());
        }
    }

    private void validateSoxCompliance(String accountingData, String eventId) {
        // Validate SOX compliance requirements
        if (!accountingData.contains("approvalChain")) {
            log.warn("Missing approval chain for SOX compliance in eventId: {}", eventId);
        }

        if (!accountingData.contains("auditTrail")) {
            throw new ComplianceException("Missing audit trail for SOX compliance in eventId: " + eventId);
        }
    }

    private void performLedgerReconciliation(AccountingRecoveryResult result, String correlationId) {
        try {
            ReconciliationResult reconciliation = ledgerReconciliationService.reconcile(
                result.getTransactionId(),
                result.getDebitEntries(),
                result.getCreditEntries(),
                correlationId
            );

            if (!reconciliation.isReconciled()) {
                throw new ReconciliationException(
                    "Ledger reconciliation failed for transaction: " + result.getTransactionId()
                );
            }

            log.info("Ledger reconciliation successful: transactionId={}, correlationId={}",
                    result.getTransactionId(), correlationId);

        } catch (Exception e) {
            log.error("Ledger reconciliation error: transactionId={}, correlationId={}",
                     result.getTransactionId(), correlationId, e);
            throw new ReconciliationException("Reconciliation failed: " + e.getMessage(), e);
        }
    }

    private void handleBalancedRecovery(AccountingRecoveryResult result, String correlationId) {
        log.info("Accounting recovery successful and balanced: transactionId={}, correlationId={}",
                result.getTransactionId(), correlationId);

        // Post to general ledger
        generalLedgerService.postEntries(
            result.getTransactionId(),
            result.getJournalEntries(),
            correlationId
        );

        // Update financial statements
        financialStatementService.updateStatements(
            result.getAffectedAccounts(),
            result.getPeriod(),
            correlationId
        );

        // Send success notification
        notificationService.sendAccountingRecoveryNotification(
            result.getTransactionId(),
            RecoveryStatus.BALANCED,
            correlationId
        );

        // Update audit trail
        auditService.recordAccountingRecovery(
            result.getTransactionId(),
            result.getRecoveryMethod(),
            correlationId,
            AuditStatus.SUCCESS
        );
    }

    private void handleImbalancedRecovery(AccountingRecoveryResult result, String eventId, String correlationId) {
        BigDecimal discrepancy = result.getDebitTotal().subtract(result.getCreditTotal());

        log.error("Accounting recovery imbalanced: transactionId={}, discrepancy={}, correlationId={}",
                result.getTransactionId(), discrepancy, correlationId);

        // Create suspense account entry
        suspenseAccountService.createEntry(
            result.getTransactionId(),
            discrepancy,
            "DLQ recovery imbalance",
            correlationId
        );

        // Escalate to finance team
        financeEscalationService.escalateImbalance(
            result.getTransactionId(),
            discrepancy,
            eventId,
            correlationId,
            EscalationPriority.CRITICAL
        );

        // Create forensic audit case
        forensicAuditService.createCase(
            CaseType.ACCOUNTING_IMBALANCE,
            result.getTransactionId(),
            discrepancy,
            correlationId
        );
    }

    private void handleDoubleEntryViolation(ConsumerRecord<String, String> record,
                                           DoubleEntryException e, String correlationId) {
        // This is a critical accounting integrity issue
        criticalAccountingViolationRepository.save(
            CriticalAccountingViolation.builder()
                .transactionId(extractTransactionId(record.value()))
                .violationType(ViolationType.DOUBLE_ENTRY)
                .description(e.getMessage())
                .correlationId(correlationId)
                .severity(Severity.CRITICAL)
                .timestamp(Instant.now())
                .requiresSoxReporting(true)
                .build()
        );

        // Send immediate CFO alert
        cfoAlertService.sendCriticalAlert(
            AlertType.DOUBLE_ENTRY_VIOLATION,
            e.getMessage(),
            correlationId
        );

        // Freeze related accounts
        accountFreezeService.freezeAccountsForTransaction(
            extractTransactionId(record.value()),
            FreezeReason.ACCOUNTING_VIOLATION,
            correlationId
        );
    }

    private void handleReconciliationFailure(ConsumerRecord<String, String> record,
                                            ReconciliationException e, String correlationId) {
        // Create reconciliation failure record
        reconciliationFailureRepository.save(
            ReconciliationFailure.builder()
                .transactionId(extractTransactionId(record.value()))
                .failureReason(e.getMessage())
                .correlationId(correlationId)
                .attemptCount(1)
                .nextRetryTime(Instant.now().plus(Duration.ofHours(1)))
                .status(ReconciliationStatus.PENDING_MANUAL_REVIEW)
                .build()
        );

        // Queue for manual reconciliation
        manualReconciliationQueue.add(
            ManualReconciliationRequest.builder()
                .transactionId(extractTransactionId(record.value()))
                .originalData(record.value())
                .failureReason(e.getMessage())
                .correlationId(correlationId)
                .priority(Priority.HIGH)
                .assignedTo("FINANCE_TEAM")
                .build()
        );
    }

    private void executeCriticalFinancialRecoveryProtocol(ConsumerRecord<String, String> record,
                                                          String topic, String exceptionMessage,
                                                          String correlationId) {
        try {
            // Execute comprehensive financial recovery
            FinancialRecoveryResult recovery = financialRecoveryService.executeCriticalRecovery(
                record.key(),
                record.value(),
                topic,
                exceptionMessage,
                correlationId
            );

            if (recovery.isPartiallyRecovered()) {
                log.info("Partial financial recovery achieved: correlationId={}, recoveredAmount={}",
                        correlationId, recovery.getRecoveredAmount());

                // Create adjustment entries
                adjustmentService.createAdjustmentEntries(
                    recovery.getAdjustmentEntries(),
                    correlationId
                );
            }
        } catch (Exception e) {
            log.error("Critical financial recovery protocol failed: correlationId={}", correlationId, e);
        }
    }

    private void storeForForensicAudit(ConsumerRecord<String, String> record, String topic,
                                       String exceptionMessage, String correlationId, Integer retryCount) {
        forensicAuditRepository.save(
            ForensicAuditRecord.builder()
                .sourceTopic(topic)
                .transactionId(extractTransactionId(record.value()))
                .messageKey(record.key())
                .messageValue(record.value())
                .failureReason(exceptionMessage)
                .correlationId(correlationId)
                .retryCount(retryCount != null ? retryCount : 0)
                .timestamp(Instant.now())
                .status(ForensicStatus.PENDING_INVESTIGATION)
                .soxRelevant(true)
                .financialImpact(calculateFinancialImpact(record.value()))
                .build()
        );
    }

    private void sendSoxComplianceAlert(ConsumerRecord<String, String> record, String topic,
                                       String exceptionMessage, String correlationId) {
        soxComplianceService.sendCriticalAlert(
            SoxAlertLevel.CRITICAL,
            "Accounting transaction permanently failed - SOX compliance at risk",
            Map.of(
                "topic", topic,
                "transactionId", extractTransactionId(record.value()),
                "error", exceptionMessage,
                "correlationId", correlationId,
                "complianceImpact", "HIGH",
                "requiredAction", "Immediate CFO review required",
                "regulatoryRisk", "SOX Section 404 violation risk"
            )
        );
    }

    private void triggerEmergencyReconciliation(ConsumerRecord<String, String> record, String correlationId) {
        emergencyReconciliationService.trigger(
            EmergencyReconciliationRequest.builder()
                .transactionId(extractTransactionId(record.value()))
                .originalData(record.value())
                .correlationId(correlationId)
                .priority(Priority.CRITICAL)
                .notificationList(List.of("CFO", "Controller", "Chief Accountant"))
                .requiredApprovals(2)
                .deadline(Instant.now().plus(Duration.ofHours(4)))
                .build()
        );
    }

    private boolean isAlreadyProcessed(String eventId) {
        Long processTime = processedEvents.get(eventId);
        if (processTime != null) {
            return System.currentTimeMillis() - processTime < Duration.ofHours(24).toMillis();
        }
        return false;
    }

    private void markAsProcessed(String eventId) {
        processedEvents.put(eventId, System.currentTimeMillis());
        if (processedEvents.size() > 10000) {
            cleanupOldProcessedEvents();
        }
    }

    private void cleanupOldProcessedEvents() {
        long cutoffTime = System.currentTimeMillis() - Duration.ofHours(24).toMillis();
        processedEvents.entrySet().removeIf(entry -> entry.getValue() < cutoffTime);
    }

    private String extractTransactionId(String value) {
        try {
            return objectMapper.readTree(value).get("transactionId").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private BigDecimal calculateFinancialImpact(String value) {
        try {
            return new BigDecimal(objectMapper.readTree(value).get("amount").asText());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}