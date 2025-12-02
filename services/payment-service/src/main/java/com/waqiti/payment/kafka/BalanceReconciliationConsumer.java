package com.waqiti.payment.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.payment.model.ReconciliationReport;
import com.waqiti.payment.model.ReconciliationStatus;
import com.waqiti.payment.model.DiscrepancyType;
import com.waqiti.payment.repository.ReconciliationRepository;
import com.waqiti.payment.service.AccountService;
import com.waqiti.payment.service.LedgerService;
import com.waqiti.payment.service.NotificationService;
import com.waqiti.payment.service.AuditService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade Kafka consumer for balance reconciliation events
 * Ensures account balance integrity and detects discrepancies
 * 
 * Critical for: Financial accuracy, fraud detection, regulatory compliance
 * SLA: Must complete reconciliation within 5 minutes
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BalanceReconciliationConsumer {

    private final ReconciliationRepository reconciliationRepository;
    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    private static final BigDecimal DISCREPANCY_THRESHOLD = new BigDecimal("0.01");
    private static final BigDecimal CRITICAL_DISCREPANCY_THRESHOLD = new BigDecimal("1000.00");
    private static final int MAX_RETRY_ATTEMPTS = 3;
    
    @KafkaListener(
        topics = "balance-reconciliation",
        groupId = "balance-reconciliation-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @CircuitBreaker(name = "balance-reconciliation-processor", fallbackMethod = "handleReconciliationFailure")
    @Retry(name = "balance-reconciliation-processor")
    public void processBalanceReconciliationEvent(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        log.info("Processing balance reconciliation event: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> payload = event.getPayload();
            ReconciliationRequest request = extractReconciliationRequest(payload);
            
            // Check for duplicate reconciliation
            if (isDuplicateReconciliation(request)) {
                log.warn("Duplicate reconciliation detected for: {}, skipping", request.getAccountId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Validate reconciliation request
            validateReconciliationRequest(request);
            
            // Perform reconciliation
            ReconciliationReport report = performReconciliation(request);
            
            // Analyze discrepancies
            analyzeDiscrepancies(report);
            
            // Handle critical discrepancies
            if (report.hasCriticalDiscrepancies()) {
                handleCriticalDiscrepancies(report);
            }
            
            // Auto-correct minor discrepancies
            if (report.hasMinorDiscrepancies() && request.isAutoCorrectEnabled()) {
                autoCorrectDiscrepancies(report);
            }
            
            // Store reconciliation report
            storeReconciliationReport(report);
            
            // Send notifications
            sendReconciliationNotifications(report);
            
            // Audit the reconciliation
            auditReconciliation(report, event);
            
            // Record metrics
            recordMetrics(report, startTime);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed balance reconciliation for account: {} in {}ms", 
                    request.getAccountId(), 
                    System.currentTimeMillis() - startTime);
            
        } catch (ValidationException e) {
            log.error("Validation failed for reconciliation event: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process reconciliation event: {}", eventId, e);
            handleProcessingError(event, e, acknowledgment);
        }
    }

    private ReconciliationRequest extractReconciliationRequest(Map<String, Object> payload) {
        return ReconciliationRequest.builder()
            .reconciliationId(extractString(payload, "reconciliationId", UUID.randomUUID().toString()))
            .accountId(extractString(payload, "accountId", null))
            .accountType(extractString(payload, "accountType", "STANDARD"))
            .reconciliationType(extractString(payload, "reconciliationType", "DAILY"))
            .startDate(extractLocalDate(payload, "startDate", LocalDate.now().minusDays(1)))
            .endDate(extractLocalDate(payload, "endDate", LocalDate.now()))
            .includeSubAccounts(extractBoolean(payload, "includeSubAccounts", false))
            .autoCorrectEnabled(extractBoolean(payload, "autoCorrectEnabled", false))
            .notificationRequired(extractBoolean(payload, "notificationRequired", true))
            .metadata(extractMap(payload, "metadata"))
            .requestedBy(extractString(payload, "requestedBy", "SYSTEM"))
            .priority(extractString(payload, "priority", "NORMAL"))
            .build();
    }

    private boolean isDuplicateReconciliation(ReconciliationRequest request) {
        return reconciliationRepository.existsByAccountIdAndDateRangeWithinHour(
            request.getAccountId(),
            request.getStartDate(),
            request.getEndDate(),
            Instant.now().minus(1, ChronoUnit.HOURS)
        );
    }

    private void validateReconciliationRequest(ReconciliationRequest request) {
        if (request.getAccountId() == null || request.getAccountId().isEmpty()) {
            throw new ValidationException("Account ID is required for reconciliation");
        }
        
        if (!accountService.accountExists(request.getAccountId())) {
            throw new ValidationException("Account does not exist: " + request.getAccountId());
        }
        
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new ValidationException("Start date cannot be after end date");
        }
        
        if (request.getEndDate().isAfter(LocalDate.now())) {
            throw new ValidationException("End date cannot be in the future");
        }
        
        // Check if account is eligible for reconciliation
        if (!accountService.isEligibleForReconciliation(request.getAccountId())) {
            throw new ValidationException("Account is not eligible for reconciliation");
        }
    }

    private ReconciliationReport performReconciliation(ReconciliationRequest request) {
        log.info("Starting reconciliation for account: {} from {} to {}", 
                request.getAccountId(), request.getStartDate(), request.getEndDate());
        
        ReconciliationReport report = new ReconciliationReport();
        report.setReconciliationId(request.getReconciliationId());
        report.setAccountId(request.getAccountId());
        report.setStartDate(request.getStartDate());
        report.setEndDate(request.getEndDate());
        report.setStartTime(Instant.now());
        
        try {
            // Get account balances
            BigDecimal accountBalance = accountService.getBalance(request.getAccountId());
            BigDecimal availableBalance = accountService.getAvailableBalance(request.getAccountId());
            BigDecimal pendingBalance = accountService.getPendingBalance(request.getAccountId());
            
            // Get ledger balances
            BigDecimal ledgerBalance = ledgerService.calculateBalance(
                request.getAccountId(), 
                request.getStartDate(), 
                request.getEndDate()
            );
            BigDecimal ledgerCredits = ledgerService.getTotalCredits(
                request.getAccountId(), 
                request.getStartDate(), 
                request.getEndDate()
            );
            BigDecimal ledgerDebits = ledgerService.getTotalDebits(
                request.getAccountId(), 
                request.getStartDate(), 
                request.getEndDate()
            );
            
            // Calculate opening and closing balances
            BigDecimal openingBalance = ledgerService.getOpeningBalance(
                request.getAccountId(), 
                request.getStartDate()
            );
            BigDecimal calculatedClosingBalance = openingBalance
                .add(ledgerCredits)
                .subtract(ledgerDebits);
            
            // Store balances in report
            report.setAccountBalance(accountBalance);
            report.setLedgerBalance(ledgerBalance);
            report.setAvailableBalance(availableBalance);
            report.setPendingBalance(pendingBalance);
            report.setOpeningBalance(openingBalance);
            report.setClosingBalance(accountBalance);
            report.setTotalCredits(ledgerCredits);
            report.setTotalDebits(ledgerDebits);
            
            // Check for discrepancies
            BigDecimal discrepancy = accountBalance.subtract(ledgerBalance).abs();
            if (discrepancy.compareTo(DISCREPANCY_THRESHOLD) > 0) {
                report.setHasDiscrepancy(true);
                report.setDiscrepancyAmount(discrepancy);
                report.setDiscrepancyType(determineDiscrepancyType(accountBalance, ledgerBalance));
                
                // Find specific transaction discrepancies
                List<TransactionDiscrepancy> transactionDiscrepancies = findTransactionDiscrepancies(
                    request.getAccountId(),
                    request.getStartDate(),
                    request.getEndDate()
                );
                report.setTransactionDiscrepancies(transactionDiscrepancies);
            }
            
            // Check pending transactions
            List<PendingTransaction> pendingTransactions = ledgerService.getPendingTransactions(
                request.getAccountId()
            );
            report.setPendingTransactions(pendingTransactions);
            report.setPendingTransactionCount(pendingTransactions.size());
            
            // Verify holds and blocks
            BigDecimal totalHolds = accountService.getTotalHolds(request.getAccountId());
            report.setTotalHolds(totalHolds);
            
            // Check for unusual patterns
            List<String> anomalies = detectAnomalies(request);
            report.setAnomalies(anomalies);
            
            // Set status
            if (report.hasDiscrepancy()) {
                if (discrepancy.compareTo(CRITICAL_DISCREPANCY_THRESHOLD) > 0) {
                    report.setStatus(ReconciliationStatus.CRITICAL_DISCREPANCY);
                } else {
                    report.setStatus(ReconciliationStatus.MINOR_DISCREPANCY);
                }
            } else {
                report.setStatus(ReconciliationStatus.BALANCED);
            }
            
        } catch (Exception e) {
            log.error("Error during reconciliation calculation", e);
            report.setStatus(ReconciliationStatus.FAILED);
            report.setErrorMessage(e.getMessage());
            throw new ReconciliationException("Reconciliation calculation failed", e);
        }
        
        report.setEndTime(Instant.now());
        report.setProcessingTimeMs(
            ChronoUnit.MILLIS.between(report.getStartTime(), report.getEndTime())
        );
        
        return report;
    }

    private DiscrepancyType determineDiscrepancyType(BigDecimal accountBalance, BigDecimal ledgerBalance) {
        if (accountBalance.compareTo(ledgerBalance) > 0) {
            return DiscrepancyType.ACCOUNT_HIGHER;
        } else if (accountBalance.compareTo(ledgerBalance) < 0) {
            return DiscrepancyType.LEDGER_HIGHER;
        } else {
            return DiscrepancyType.ROUNDING_ERROR;
        }
    }

    private List<TransactionDiscrepancy> findTransactionDiscrepancies(
            String accountId, LocalDate startDate, LocalDate endDate) {
        
        List<TransactionDiscrepancy> discrepancies = new ArrayList<>();
        
        // Get transactions from account system
        List<Transaction> accountTransactions = accountService.getTransactions(
            accountId, startDate, endDate
        );
        
        // Get transactions from ledger
        List<LedgerEntry> ledgerEntries = ledgerService.getEntries(
            accountId, startDate, endDate
        );
        
        // Find missing in ledger
        for (Transaction tx : accountTransactions) {
            if (!existsInLedger(tx, ledgerEntries)) {
                discrepancies.add(TransactionDiscrepancy.builder()
                    .transactionId(tx.getId())
                    .type(DiscrepancyType.MISSING_IN_LEDGER)
                    .amount(tx.getAmount())
                    .timestamp(tx.getTimestamp())
                    .build());
            }
        }
        
        // Find missing in account
        for (LedgerEntry entry : ledgerEntries) {
            if (!existsInAccount(entry, accountTransactions)) {
                discrepancies.add(TransactionDiscrepancy.builder()
                    .transactionId(entry.getReference())
                    .type(DiscrepancyType.MISSING_IN_ACCOUNT)
                    .amount(entry.getAmount())
                    .timestamp(entry.getTimestamp())
                    .build());
            }
        }
        
        // Find amount mismatches
        for (Transaction tx : accountTransactions) {
            Optional<LedgerEntry> matchingEntry = findMatchingLedgerEntry(tx, ledgerEntries);
            if (matchingEntry.isPresent()) {
                BigDecimal diff = tx.getAmount().subtract(matchingEntry.get().getAmount()).abs();
                if (diff.compareTo(BigDecimal.ZERO) > 0) {
                    discrepancies.add(TransactionDiscrepancy.builder()
                        .transactionId(tx.getId())
                        .type(DiscrepancyType.AMOUNT_MISMATCH)
                        .amount(diff)
                        .accountAmount(tx.getAmount())
                        .ledgerAmount(matchingEntry.get().getAmount())
                        .timestamp(tx.getTimestamp())
                        .build());
                }
            }
        }
        
        return discrepancies;
    }

    private List<String> detectAnomalies(ReconciliationRequest request) {
        List<String> anomalies = new ArrayList<>();
        
        // Check for duplicate transactions
        List<String> duplicates = ledgerService.findDuplicateTransactions(
            request.getAccountId(),
            request.getStartDate(),
            request.getEndDate()
        );
        if (!duplicates.isEmpty()) {
            anomalies.add("Duplicate transactions detected: " + duplicates.size());
        }
        
        // Check for unusual transaction patterns
        if (hasUnusualVelocity(request.getAccountId(), request.getStartDate(), request.getEndDate())) {
            anomalies.add("Unusual transaction velocity detected");
        }
        
        // Check for round amount transactions
        if (hasSuspiciousRoundAmounts(request.getAccountId(), request.getStartDate(), request.getEndDate())) {
            anomalies.add("Multiple round amount transactions detected");
        }
        
        // Check for after-hours activity
        if (hasAfterHoursActivity(request.getAccountId(), request.getStartDate(), request.getEndDate())) {
            anomalies.add("Significant after-hours activity detected");
        }
        
        return anomalies;
    }

    private void analyzeDiscrepancies(ReconciliationReport report) {
        if (!report.hasDiscrepancy()) {
            return;
        }
        
        log.warn("Discrepancy detected for account {}: {} {}", 
                report.getAccountId(), 
                report.getDiscrepancyAmount(),
                report.getDiscrepancyType());
        
        // Categorize discrepancies
        if (report.getDiscrepancyAmount().compareTo(CRITICAL_DISCREPANCY_THRESHOLD) > 0) {
            report.setSeverity("CRITICAL");
            report.setRequiresManualReview(true);
            report.setAutoCorrectible(false);
        } else if (report.getDiscrepancyAmount().compareTo(new BigDecimal("100.00")) > 0) {
            report.setSeverity("HIGH");
            report.setRequiresManualReview(true);
            report.setAutoCorrectible(false);
        } else if (report.getDiscrepancyAmount().compareTo(new BigDecimal("10.00")) > 0) {
            report.setSeverity("MEDIUM");
            report.setRequiresManualReview(false);
            report.setAutoCorrectible(true);
        } else {
            report.setSeverity("LOW");
            report.setRequiresManualReview(false);
            report.setAutoCorrectible(true);
        }
        
        // Identify root cause
        identifyRootCause(report);
    }

    private void identifyRootCause(ReconciliationReport report) {
        List<String> possibleCauses = new ArrayList<>();
        
        if (report.getPendingTransactionCount() > 0) {
            possibleCauses.add("Pending transactions not yet settled");
        }
        
        if (report.getTotalHolds().compareTo(BigDecimal.ZERO) > 0) {
            possibleCauses.add("Active holds on account");
        }
        
        if (!report.getTransactionDiscrepancies().isEmpty()) {
            Map<DiscrepancyType, Long> discrepancyCount = report.getTransactionDiscrepancies()
                .stream()
                .collect(Collectors.groupingBy(TransactionDiscrepancy::getType, Collectors.counting()));
            
            discrepancyCount.forEach((type, count) -> {
                possibleCauses.add(String.format("%s: %d transactions", type, count));
            });
        }
        
        if (!report.getAnomalies().isEmpty()) {
            possibleCauses.addAll(report.getAnomalies());
        }
        
        report.setPossibleCauses(possibleCauses);
    }

    private void handleCriticalDiscrepancies(ReconciliationReport report) {
        log.error("Critical discrepancy detected for account {}: {}", 
                report.getAccountId(), report.getDiscrepancyAmount());
        
        // Freeze account for investigation
        accountService.freezeAccount(
            report.getAccountId(),
            "RECONCILIATION_DISCREPANCY",
            "Critical discrepancy of " + report.getDiscrepancyAmount() + " detected"
        );
        
        // Create investigation case
        String caseId = createInvestigationCase(report);
        report.setInvestigationCaseId(caseId);
        
        // Notify compliance team
        notificationService.notifyComplianceTeam(
            "Critical Reconciliation Discrepancy",
            Map.of(
                "accountId", report.getAccountId(),
                "discrepancyAmount", report.getDiscrepancyAmount(),
                "caseId", caseId
            )
        );
        
        // Send alerts
        alertingService.sendCriticalAlert(
            "Critical Balance Discrepancy",
            String.format("Account %s has discrepancy of %s", 
                report.getAccountId(), report.getDiscrepancyAmount())
        );
    }

    private void autoCorrectDiscrepancies(ReconciliationReport report) {
        if (!report.isAutoCorrectible()) {
            log.info("Discrepancy for account {} is not auto-correctible", report.getAccountId());
            return;
        }
        
        log.info("Auto-correcting minor discrepancy for account {}", report.getAccountId());
        
        try {
            // Create adjustment entry
            String adjustmentId = ledgerService.createAdjustmentEntry(
                report.getAccountId(),
                report.getDiscrepancyAmount(),
                report.getDiscrepancyType() == DiscrepancyType.ACCOUNT_HIGHER ? "DEBIT" : "CREDIT",
                "AUTO_RECONCILIATION_ADJUSTMENT",
                report.getReconciliationId()
            );
            
            // Update account balance
            accountService.adjustBalance(
                report.getAccountId(),
                report.getDiscrepancyAmount(),
                adjustmentId
            );
            
            report.setAutoCorrectApplied(true);
            report.setAdjustmentId(adjustmentId);
            report.setStatus(ReconciliationStatus.CORRECTED);
            
            // Audit the correction
            auditService.auditBalanceAdjustment(
                report.getAccountId(),
                report.getDiscrepancyAmount(),
                adjustmentId,
                "AUTO_CORRECTION",
                report.getReconciliationId()
            );
            
        } catch (Exception e) {
            log.error("Failed to auto-correct discrepancy", e);
            report.setAutoCorrectApplied(false);
            report.setAutoCorrectError(e.getMessage());
        }
    }

    private void storeReconciliationReport(ReconciliationReport report) {
        reconciliationRepository.save(report);
        
        // Store in audit trail
        auditService.storeReconciliationReport(
            report.getReconciliationId(),
            report.getAccountId(),
            report.toJson()
        );
    }

    private void sendReconciliationNotifications(ReconciliationReport report) {
        if (!report.isNotificationRequired()) {
            return;
        }
        
        // Prepare notification data
        Map<String, Object> notificationData = Map.of(
            "accountId", report.getAccountId(),
            "status", report.getStatus().toString(),
            "hasDiscrepancy", report.hasDiscrepancy(),
            "discrepancyAmount", report.getDiscrepancyAmount() != null ? report.getDiscrepancyAmount() : BigDecimal.ZERO,
            "severity", report.getSeverity() != null ? report.getSeverity() : "NONE"
        );
        
        // Send based on severity
        if ("CRITICAL".equals(report.getSeverity())) {
            // Immediate notification to multiple channels
            notificationService.sendCriticalNotification(
                "BALANCE_RECONCILIATION_CRITICAL",
                notificationData
            );
            
            // PagerDuty alert
            alertingService.createPagerDutyIncident(
                "Critical Balance Discrepancy",
                report.getAccountId(),
                "HIGH"
            );
            
        } else if ("HIGH".equals(report.getSeverity()) || "MEDIUM".equals(report.getSeverity())) {
            // Standard notification
            notificationService.sendNotification(
                "BALANCE_RECONCILIATION_DISCREPANCY",
                notificationData
            );
            
        } else if (report.getStatus() == ReconciliationStatus.BALANCED) {
            // Success notification (if configured)
            if (report.isSuccessNotificationEnabled()) {
                notificationService.sendNotification(
                    "BALANCE_RECONCILIATION_SUCCESS",
                    notificationData
                );
            }
        }
    }

    private void auditReconciliation(ReconciliationReport report, GenericKafkaEvent event) {
        auditService.auditReconciliation(
            report.getReconciliationId(),
            report.getAccountId(),
            report.getStatus().toString(),
            report.getDiscrepancyAmount(),
            event.getEventId(),
            report.getRequestedBy()
        );
    }

    private void recordMetrics(ReconciliationReport report, long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        metricsService.recordReconciliationMetrics(
            report.getAccountType(),
            report.getStatus().toString(),
            report.getDiscrepancyAmount() != null ? report.getDiscrepancyAmount() : BigDecimal.ZERO,
            processingTime,
            report.hasDiscrepancy()
        );
    }

    private void handleValidationError(GenericKafkaEvent event, ValidationException e) {
        auditService.logValidationError(event.getEventId(), e.getMessage());
        kafkaTemplate.send("balance-reconciliation-validation-errors", event);
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying reconciliation event {} after {}ms (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            scheduledExecutor.schedule(() -> {
                kafkaTemplate.send("balance-reconciliation-retry", event);
            }, retryDelay, TimeUnit.MILLISECONDS);
            
            acknowledgment.acknowledge();
        } else {
            log.error("Max retries exceeded for reconciliation event {}, sending to DLQ", eventId);
            sendToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "balance-reconciliation");
        
        kafkaTemplate.send("balance-reconciliation.DLQ", event);
        
        alertingService.createDLQAlert(
            "balance-reconciliation",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handleReconciliationFailure(GenericKafkaEvent event, String topic, int partition, 
                                           long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for reconciliation processing: {}", e.getMessage());
        
        failedEventRepository.save(
            FailedEvent.builder()
                .eventId(event.getEventId())
                .topic(topic)
                .payload(event)
                .errorMessage(e.getMessage())
                .createdAt(Instant.now())
                .build()
        );
        
        alertingService.sendCriticalAlert(
            "Balance Reconciliation Circuit Breaker Open",
            "Reconciliation processing is failing. Manual intervention required."
        );
        
        acknowledgment.acknowledge();
    }

    // Helper methods for checking patterns
    private boolean hasUnusualVelocity(String accountId, LocalDate startDate, LocalDate endDate) {
        int transactionCount = ledgerService.getTransactionCount(accountId, startDate, endDate);
        double avgDailyTransactions = accountService.getAverageD dailyTransactions(accountId);
        return transactionCount > avgDailyTransactions * 3;
    }

    private boolean hasSuspiciousRoundAmounts(String accountId, LocalDate startDate, LocalDate endDate) {
        List<BigDecimal> amounts = ledgerService.getTransactionAmounts(accountId, startDate, endDate);
        long roundAmounts = amounts.stream()
            .filter(amount -> amount.remainder(new BigDecimal("100")).compareTo(BigDecimal.ZERO) == 0)
            .count();
        return roundAmounts > amounts.size() * 0.3;
    }

    private boolean hasAfterHoursActivity(String accountId, LocalDate startDate, LocalDate endDate) {
        int afterHoursCount = ledgerService.getAfterHoursTransactionCount(accountId, startDate, endDate);
        int totalCount = ledgerService.getTransactionCount(accountId, startDate, endDate);
        return afterHoursCount > totalCount * 0.2;
    }

    private boolean existsInLedger(Transaction tx, List<LedgerEntry> entries) {
        return entries.stream()
            .anyMatch(entry -> entry.getReference().equals(tx.getId()));
    }

    private boolean existsInAccount(LedgerEntry entry, List<Transaction> transactions) {
        return transactions.stream()
            .anyMatch(tx -> tx.getId().equals(entry.getReference()));
    }

    private Optional<LedgerEntry> findMatchingLedgerEntry(Transaction tx, List<LedgerEntry> entries) {
        return entries.stream()
            .filter(entry -> entry.getReference().equals(tx.getId()))
            .findFirst();
    }

    private String createInvestigationCase(ReconciliationReport report) {
        return UUID.randomUUID().toString();
    }

    // Helper extraction methods
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(value.toString());
    }

    private LocalDate extractLocalDate(Map<String, Object> map, String key, LocalDate defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof LocalDate) return (LocalDate) value;
        return LocalDate.parse(value.toString());
    }

    private boolean extractBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    // Custom exceptions
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class ReconciliationException extends RuntimeException {
        public ReconciliationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}