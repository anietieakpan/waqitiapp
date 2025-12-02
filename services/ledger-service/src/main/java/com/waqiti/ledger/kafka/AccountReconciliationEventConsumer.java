package com.waqiti.ledger.kafka;

import com.waqiti.common.events.AccountReconciliationEvent;
import com.waqiti.common.events.ReconciliationCompletedEvent;
import com.waqiti.ledger.domain.AccountReconciliation;
import com.waqiti.ledger.domain.ReconciliationStatus;
import com.waqiti.ledger.domain.ReconciliationType;
import com.waqiti.ledger.domain.DiscrepancyRecord;
import com.waqiti.ledger.domain.LedgerEntry;
import com.waqiti.ledger.repository.AccountReconciliationRepository;
import com.waqiti.ledger.repository.DiscrepancyRecordRepository;
import com.waqiti.ledger.repository.LedgerEntryRepository;
import com.waqiti.ledger.service.ReconciliationService;
import com.waqiti.ledger.service.DiscrepancyAnalysisService;
import com.waqiti.ledger.service.ComplianceService;
import com.waqiti.ledger.service.NotificationService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.ledger.service.BalanceVerificationService;
import com.waqiti.ledger.service.TransactionMatchingService;
import com.waqiti.ledger.service.ExternalDataService;
import com.waqiti.ledger.service.AutoCorrectionService;
import com.waqiti.ledger.exception.ReconciliationException;
import com.waqiti.ledger.exception.DiscrepancyException;
import com.waqiti.ledger.exception.BalanceException;
import com.waqiti.common.security.encryption.EncryptionService;
import com.waqiti.common.compliance.ComplianceValidator;
import com.waqiti.common.audit.AuditEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
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
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * CRITICAL ACCOUNT RECONCILIATION EVENT CONSUMER - Consumer 42
 * 
 * Processes account reconciliation events with zero-tolerance 12-step processing:
 * 1. Event validation and sanitization
 * 2. Idempotency and duplicate detection
 * 3. Regulatory compliance verification
 * 4. Account and ledger validation
 * 5. External data retrieval and validation
 * 6. Transaction matching and comparison
 * 7. Balance verification and calculation
 * 8. Discrepancy identification and analysis
 * 9. Auto-correction and adjustment processing
 * 10. Compliance reporting and documentation
 * 11. Audit trail and record creation
 * 12. Notification dispatch and escalation
 * 
 * REGULATORY COMPLIANCE:
 * - SOX (Sarbanes-Oxley) compliance
 * - Basel III reconciliation requirements
 * - GAAP accounting standards
 * - IFRS reporting standards
 * - PCI DSS audit requirements
 * - Anti-Money Laundering (AML) monitoring
 * 
 * RECONCILIATION TYPES SUPPORTED:
 * - Daily balance reconciliation
 * - Transaction-level reconciliation
 * - Nostro/Vostro account reconciliation
 * - Merchant settlement reconciliation
 * - Payment processor reconciliation
 * - Bank statement reconciliation
 * 
 * SLA: 99.99% uptime, <30s processing time
 * 
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
@Validated
public class AccountReconciliationEventConsumer {
    
    private final AccountReconciliationRepository reconciliationRepository;
    private final DiscrepancyRecordRepository discrepancyRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ReconciliationService reconciliationService;
    private final DiscrepancyAnalysisService discrepancyAnalysisService;
    private final ComplianceService complianceService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final BalanceVerificationService balanceVerificationService;
    private final TransactionMatchingService transactionMatchingService;
    private final ExternalDataService externalDataService;
    private final AutoCorrectionService autoCorrectionService;
    private final EncryptionService encryptionService;
    private final ComplianceValidator complianceValidator;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final String RECONCILIATION_COMPLETED_TOPIC = "reconciliation-completed-events";
    private static final String DISCREPANCY_ALERT_TOPIC = "discrepancy-alert-events";
    private static final String COMPLIANCE_ALERT_TOPIC = "compliance-alert-events";
    private static final String AUTO_CORRECTION_TOPIC = "auto-correction-events";
    private static final String DLQ_TOPIC = "account-reconciliation-events-dlq";
    
    private static final BigDecimal MAX_AUTO_CORRECTION_AMOUNT = new BigDecimal("1000.00");
    private static final BigDecimal MATERIALITY_THRESHOLD = new BigDecimal("0.01"); // 1 cent
    private static final BigDecimal CRITICAL_DISCREPANCY_THRESHOLD = new BigDecimal("10000.00");
    private static final int MAX_DISCREPANCIES_PER_RECONCILIATION = 1000;
    private static final int MAX_PROCESSING_DAYS = 90;

    @KafkaListener(
        topics = "account-reconciliation-events",
        groupId = "account-reconciliation-processor",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "3"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Retryable(
        value = {ReconciliationException.class, DiscrepancyException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 3000, multiplier = 2, maxDelay = 15000)
    )
    public void handleAccountReconciliationEvent(
            @Payload @Valid AccountReconciliationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {
        
        String correlationId = generateCorrelationId(event, partition, offset);
        long processingStartTime = System.currentTimeMillis();
        
        log.info("STEP 1: Processing account reconciliation event - ID: {}, Account: {}, Date: {}, Type: {}, Correlation: {}",
            event.getReconciliationId(), event.getAccountId(), event.getReconciliationDate(), 
            event.getReconciliationType(), correlationId);
        
        try {
            // STEP 1: Event validation and sanitization
            validateAndSanitizeEvent(event, correlationId);
            
            // STEP 2: Idempotency and duplicate detection
            if (checkIdempotencyAndDuplicates(event, correlationId)) {
                acknowledgeAndReturn(acknowledgment, "Duplicate reconciliation event detected");
                return;
            }
            
            // STEP 3: Regulatory compliance verification
            performComplianceVerification(event, correlationId);
            
            // STEP 4: Account and ledger validation
            AccountValidationResult accountValidation = validateAccountAndLedger(event, correlationId);
            
            // STEP 5: External data retrieval and validation
            ExternalDataResult externalData = retrieveAndValidateExternalData(event, correlationId);
            
            // STEP 6: Transaction matching and comparison
            TransactionMatchingResult matchingResult = performTransactionMatchingAndComparison(
                event, accountValidation, externalData, correlationId);
            
            // STEP 7: Balance verification and calculation
            BalanceVerificationResult balanceResult = performBalanceVerificationAndCalculation(
                event, accountValidation, externalData, matchingResult, correlationId);
            
            // STEP 8: Discrepancy identification and analysis
            DiscrepancyAnalysisResult discrepancyResult = identifyAndAnalyzeDiscrepancies(
                event, balanceResult, matchingResult, correlationId);
            
            // STEP 9: Auto-correction and adjustment processing
            AutoCorrectionResult autoCorrectionResult = processAutoCorrectionAndAdjustments(
                event, discrepancyResult, correlationId);
            
            // STEP 10: Compliance reporting and documentation
            performComplianceReportingAndDocumentation(event, discrepancyResult, autoCorrectionResult, correlationId);
            
            // STEP 11: Audit trail and record creation
            AccountReconciliation reconciliation = createAuditTrailAndSaveReconciliation(
                event, accountValidation, externalData, matchingResult, balanceResult, 
                discrepancyResult, autoCorrectionResult, correlationId, processingStartTime);
            
            // STEP 12: Notification dispatch and escalation
            dispatchNotificationsAndEscalation(event, reconciliation, discrepancyResult, 
                autoCorrectionResult, correlationId);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            long processingTime = System.currentTimeMillis() - processingStartTime;
            log.info("Successfully processed account reconciliation - ID: {}, Status: {}, Discrepancies: {}, Time: {}ms, Correlation: {}",
                event.getReconciliationId(), reconciliation.getStatus(), discrepancyResult.getDiscrepancies().size(), 
                processingTime, correlationId);
            
        } catch (DiscrepancyException e) {
            handleDiscrepancyError(event, e, correlationId, acknowledgment);
        } catch (BalanceException e) {
            handleBalanceError(event, e, correlationId, acknowledgment);
        } catch (ReconciliationException e) {
            handleReconciliationError(event, e, correlationId, acknowledgment);
        } catch (Exception e) {
            handleCriticalError(event, e, correlationId, acknowledgment);
        }
    }

    /**
     * STEP 1: Event validation and sanitization
     */
    private void validateAndSanitizeEvent(AccountReconciliationEvent event, String correlationId) {
        log.debug("STEP 1: Validating account reconciliation event - Correlation: {}", correlationId);
        
        if (event == null) {
            throw new IllegalArgumentException("Account reconciliation event cannot be null");
        }
        
        if (event.getReconciliationId() == null || event.getReconciliationId().trim().isEmpty()) {
            throw new IllegalArgumentException("Reconciliation ID is required");
        }
        
        if (event.getAccountId() == null || event.getAccountId().trim().isEmpty()) {
            throw new IllegalArgumentException("Account ID is required");
        }
        
        if (event.getReconciliationDate() == null) {
            throw new IllegalArgumentException("Reconciliation date is required");
        }
        
        if (event.getReconciliationDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Reconciliation date cannot be in the future");
        }
        
        if (event.getReconciliationDate().isBefore(LocalDate.now().minusDays(MAX_PROCESSING_DAYS))) {
            throw new ReconciliationException("Reconciliation date too far in the past: " + MAX_PROCESSING_DAYS + " days");
        }
        
        if (event.getReconciliationType() == null || event.getReconciliationType().trim().isEmpty()) {
            throw new IllegalArgumentException("Reconciliation type is required");
        }
        
        if (event.getExpectedBalance() != null && event.getExpectedBalance().scale() > 2) {
            event.setExpectedBalance(event.getExpectedBalance().setScale(2, RoundingMode.HALF_UP));
        }
        
        // Sanitize string fields
        event.setReconciliationId(sanitizeString(event.getReconciliationId()));
        event.setAccountId(sanitizeString(event.getAccountId()));
        event.setReconciliationType(sanitizeString(event.getReconciliationType().toUpperCase()));
        
        log.debug("STEP 1: Event validation completed - Correlation: {}", correlationId);
    }

    /**
     * STEP 2: Idempotency and duplicate detection
     */
    private boolean checkIdempotencyAndDuplicates(AccountReconciliationEvent event, String correlationId) {
        log.debug("STEP 2: Checking idempotency - Correlation: {}", correlationId);
        
        // Check for existing reconciliation
        boolean isDuplicate = reconciliationRepository.existsByReconciliationIdAndAccountId(
            event.getReconciliationId(), event.getAccountId());
        
        if (isDuplicate) {
            log.warn("Duplicate reconciliation detected - ID: {}, Account: {}, Correlation: {}",
                event.getReconciliationId(), event.getAccountId(), correlationId);

            Map<String, Object> auditData = new HashMap<>();
            auditData.put("accountId", event.getAccountId());
            auditData.put("reconciliationId", event.getReconciliationId());
            auditData.put("eventType", "DUPLICATE_RECONCILIATION_DETECTED");
            auditService.logAuditEvent("DUPLICATE_RECONCILIATION_DETECTED", correlationId, auditData);
        }
        
        return isDuplicate;
    }

    /**
     * STEP 3: Regulatory compliance verification
     */
    private void performComplianceVerification(AccountReconciliationEvent event, String correlationId) {
        log.debug("STEP 3: Performing compliance verification - Correlation: {}", correlationId);
        
        // Verify account compliance status
        if (!complianceService.isAccountCompliant(event.getAccountId())) {
            throw new ReconciliationException("Account not compliant for reconciliation: " + event.getAccountId());
        }
        
        // SOX compliance check for financial accounts
        if (isFinancialAccount(event.getAccountId()) && !complianceService.isSOXCompliant(event.getAccountId())) {
            throw new ReconciliationException("SOX compliance violation for financial account: " + event.getAccountId());
        }
        
        // Basel III reconciliation requirements
        if (isRegulatoryAccount(event.getAccountId()) && !complianceService.isBaselIIICompliant(event)) {
            throw new ReconciliationException("Basel III compliance violation for regulatory account");
        }
        
        // Audit trail requirements
        if (!complianceService.hasRequiredAuditTrail(event.getAccountId(), event.getReconciliationDate())) {
            throw new ReconciliationException("Missing required audit trail for reconciliation period");
        }
        
        log.debug("STEP 3: Compliance verification completed - Correlation: {}", correlationId);
    }

    /**
     * STEP 4: Account and ledger validation
     */
    private AccountValidationResult validateAccountAndLedger(AccountReconciliationEvent event, String correlationId) {
        log.debug("STEP 4: Validating account and ledger - Correlation: {}", correlationId);
        
        // Validate account exists and is active
        if (!reconciliationService.isAccountActive(event.getAccountId())) {
            throw new ReconciliationException("Account is not active: " + event.getAccountId());
        }
        
        // Get ledger entries for reconciliation period
        List<LedgerEntry> ledgerEntries = ledgerEntryRepository.findByAccountIdAndDateRange(
            event.getAccountId(), event.getReconciliationDate(), event.getReconciliationDate());
        
        // Calculate internal balance
        BigDecimal internalBalance = calculateInternalBalance(ledgerEntries);
        
        // Validate ledger integrity
        if (!reconciliationService.validateLedgerIntegrity(ledgerEntries)) {
            throw new ReconciliationException("Ledger integrity check failed for account: " + event.getAccountId());
        }
        
        AccountValidationResult result = AccountValidationResult.builder()
            .accountId(event.getAccountId())
            .ledgerEntries(ledgerEntries)
            .internalBalance(internalBalance)
            .entryCount(ledgerEntries.size())
            .validationPassed(true)
            .build();
        
        log.debug("STEP 4: Account validation completed - Balance: {}, Entries: {}, Correlation: {}",
            internalBalance, ledgerEntries.size(), correlationId);
        
        return result;
    }

    /**
     * STEP 5: External data retrieval and validation
     */
    private ExternalDataResult retrieveAndValidateExternalData(AccountReconciliationEvent event, String correlationId) {
        log.debug("STEP 5: Retrieving external data - Correlation: {}", correlationId);
        
        ExternalDataResult result = externalDataService.retrieveExternalData(
            event.getAccountId(), 
            event.getReconciliationDate(), 
            event.getReconciliationType(),
            event.getExternalReference()
        );
        
        if (!result.isSuccessful()) {
            throw new ReconciliationException("Failed to retrieve external data: " + result.getErrorMessage());
        }
        
        // Validate external data integrity
        if (!externalDataService.validateDataIntegrity(result)) {
            throw new ReconciliationException("External data integrity validation failed");
        }
        
        // Check for data completeness
        if (!externalDataService.isDataComplete(result, event.getReconciliationDate())) {
            log.warn("Incomplete external data detected - Account: {}, Date: {}, Correlation: {}",
                event.getAccountId(), event.getReconciliationDate(), correlationId);
        }
        
        log.debug("STEP 5: External data retrieval completed - Balance: {}, Transactions: {}, Correlation: {}",
            result.getExternalBalance(), result.getTransactions().size(), correlationId);
        
        return result;
    }

    /**
     * STEP 6: Transaction matching and comparison
     */
    private TransactionMatchingResult performTransactionMatchingAndComparison(AccountReconciliationEvent event,
            AccountValidationResult accountValidation, ExternalDataResult externalData, String correlationId) {
        log.debug("STEP 6: Performing transaction matching - Correlation: {}", correlationId);
        
        TransactionMatchingResult result = transactionMatchingService.matchTransactions(
            accountValidation.getLedgerEntries(),
            externalData.getTransactions(),
            event.getReconciliationDate()
        );
        
        // Analyze matching results
        int totalInternal = accountValidation.getLedgerEntries().size();
        int totalExternal = externalData.getTransactions().size();
        int matched = result.getMatchedTransactions().size();
        int unmatchedInternal = result.getUnmatchedInternalTransactions().size();
        int unmatchedExternal = result.getUnmatchedExternalTransactions().size();
        
        double matchRate = totalInternal > 0 ? ((double) matched / totalInternal) * 100 : 100;
        result.setMatchRate(matchRate);
        
        if (matchRate < 90.0) { // Low match rate threshold
            log.warn("Low transaction match rate detected - Rate: {}%, Account: {}, Correlation: {}",
                matchRate, event.getAccountId(), correlationId);
        }
        
        log.debug("STEP 6: Transaction matching completed - Matched: {}, Rate: {}%, Correlation: {}",
            matched, matchRate, correlationId);
        
        return result;
    }

    /**
     * STEP 7: Balance verification and calculation
     */
    private BalanceVerificationResult performBalanceVerificationAndCalculation(AccountReconciliationEvent event,
            AccountValidationResult accountValidation, ExternalDataResult externalData,
            TransactionMatchingResult matchingResult, String correlationId) {
        log.debug("STEP 7: Performing balance verification - Correlation: {}", correlationId);
        
        BalanceVerificationResult result = balanceVerificationService.verifyBalances(
            accountValidation.getInternalBalance(),
            externalData.getExternalBalance(),
            event.getExpectedBalance(),
            matchingResult
        );
        
        // Calculate balance discrepancy
        BigDecimal balanceDiscrepancy = accountValidation.getInternalBalance()
            .subtract(externalData.getExternalBalance()).abs();
        
        result.setBalanceDiscrepancy(balanceDiscrepancy);
        result.setWithinTolerance(balanceDiscrepancy.compareTo(MATERIALITY_THRESHOLD) <= 0);
        
        // Critical discrepancy check
        if (balanceDiscrepancy.compareTo(CRITICAL_DISCREPANCY_THRESHOLD) > 0) {
            log.error("Critical balance discrepancy detected - Amount: {}, Account: {}, Correlation: {}",
                balanceDiscrepancy, event.getAccountId(), correlationId);
            
            result.setCriticalDiscrepancy(true);
        }
        
        log.debug("STEP 7: Balance verification completed - Discrepancy: {}, Critical: {}, Correlation: {}",
            balanceDiscrepancy, result.isCriticalDiscrepancy(), correlationId);
        
        return result;
    }

    /**
     * STEP 8: Discrepancy identification and analysis
     */
    private DiscrepancyAnalysisResult identifyAndAnalyzeDiscrepancies(AccountReconciliationEvent event,
            BalanceVerificationResult balanceResult, TransactionMatchingResult matchingResult, String correlationId) {
        log.debug("STEP 8: Identifying and analyzing discrepancies - Correlation: {}", correlationId);
        
        DiscrepancyAnalysisResult result = discrepancyAnalysisService.analyzeDiscrepancies(
            event, balanceResult, matchingResult);
        
        if (result.getDiscrepancies().size() > MAX_DISCREPANCIES_PER_RECONCILIATION) {
            throw new DiscrepancyException("Too many discrepancies detected: " + result.getDiscrepancies().size());
        }
        
        // Categorize discrepancies by severity
        long criticalDiscrepancies = result.getDiscrepancies().stream()
            .filter(d -> d.getAmount().compareTo(CRITICAL_DISCREPANCY_THRESHOLD) > 0)
            .count();
        
        if (criticalDiscrepancies > 0) {
            log.error("Critical discrepancies detected - Count: {}, Account: {}, Correlation: {}",
                criticalDiscrepancies, event.getAccountId(), correlationId);
            
            result.setHasCriticalDiscrepancies(true);
        }
        
        // Calculate total discrepancy value
        BigDecimal totalDiscrepancyValue = result.getDiscrepancies().stream()
            .map(DiscrepancyRecord::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        result.setTotalDiscrepancyValue(totalDiscrepancyValue);
        
        log.debug("STEP 8: Discrepancy analysis completed - Count: {}, Total Value: {}, Critical: {}, Correlation: {}",
            result.getDiscrepancies().size(), totalDiscrepancyValue, criticalDiscrepancies, correlationId);
        
        return result;
    }

    /**
     * STEP 9: Auto-correction and adjustment processing
     */
    private AutoCorrectionResult processAutoCorrectionAndAdjustments(AccountReconciliationEvent event,
            DiscrepancyAnalysisResult discrepancyResult, String correlationId) {
        log.debug("STEP 9: Processing auto-corrections - Correlation: {}", correlationId);
        
        AutoCorrectionResult result = autoCorrectionService.processAutoCorrections(
            event.getAccountId(), discrepancyResult.getDiscrepancies());
        
        // Apply approved corrections
        for (DiscrepancyRecord discrepancy : result.getCorrectedDiscrepancies()) {
            if (discrepancy.getAmount().abs().compareTo(MAX_AUTO_CORRECTION_AMOUNT) <= 0) {
                // Apply correction
                LedgerEntry correctionEntry = createCorrectionEntry(event, discrepancy, correlationId);
                ledgerEntryRepository.save(correctionEntry);
                
                // Update discrepancy status
                discrepancy.setStatus("AUTO_CORRECTED");
                discrepancy.setCorrectionEntryId(correctionEntry.getId());
                discrepancyRepository.save(discrepancy);
                
                log.info("Auto-correction applied - Amount: {}, Account: {}, Correlation: {}",
                    discrepancy.getAmount(), event.getAccountId(), correlationId);
            }
        }
        
        log.debug("STEP 9: Auto-correction completed - Corrected: {}, Manual Required: {}, Correlation: {}",
            result.getCorrectedDiscrepancies().size(), result.getManualReviewRequired().size(), correlationId);
        
        return result;
    }

    /**
     * STEP 10: Compliance reporting and documentation
     */
    private void performComplianceReportingAndDocumentation(AccountReconciliationEvent event,
            DiscrepancyAnalysisResult discrepancyResult, AutoCorrectionResult autoCorrectionResult, String correlationId) {
        log.debug("STEP 10: Performing compliance reporting - Correlation: {}", correlationId);
        
        // Generate compliance reports asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                complianceService.generateReconciliationReport(event, discrepancyResult, autoCorrectionResult, correlationId);
                
                // SOX documentation for financial accounts
                if (isFinancialAccount(event.getAccountId())) {
                    complianceService.generateSOXReconciliationReport(event, discrepancyResult, correlationId);
                }
                
                // Basel III reporting for regulatory accounts
                if (isRegulatoryAccount(event.getAccountId())) {
                    complianceService.generateBaselIIIReport(event, discrepancyResult, correlationId);
                }
                
                // Critical discrepancy reporting
                if (discrepancyResult.isHasCriticalDiscrepancies()) {
                    complianceService.generateCriticalDiscrepancyReport(event, discrepancyResult, correlationId);
                }
                
            } catch (Exception e) {
                log.error("Failed to generate compliance reports - Correlation: {}", correlationId, e);
            }
        });
        
        log.debug("STEP 10: Compliance reporting initiated - Correlation: {}", correlationId);
    }

    /**
     * STEP 11: Audit trail and record creation
     */
    private AccountReconciliation createAuditTrailAndSaveReconciliation(AccountReconciliationEvent event,
            AccountValidationResult accountValidation, ExternalDataResult externalData, 
            TransactionMatchingResult matchingResult, BalanceVerificationResult balanceResult,
            DiscrepancyAnalysisResult discrepancyResult, AutoCorrectionResult autoCorrectionResult,
            String correlationId, long processingStartTime) {
        log.debug("STEP 11: Creating audit trail - Correlation: {}", correlationId);
        
        // Determine reconciliation status
        ReconciliationStatus status = determineReconciliationStatus(discrepancyResult, balanceResult);
        
        AccountReconciliation reconciliation = AccountReconciliation.builder()
            .reconciliationId(event.getReconciliationId())
            .accountId(event.getAccountId())
            .reconciliationDate(event.getReconciliationDate())
            .reconciliationType(ReconciliationType.valueOf(event.getReconciliationType()))
            .status(status)
            .internalBalance(accountValidation.getInternalBalance())
            .externalBalance(externalData.getExternalBalance())
            .expectedBalance(event.getExpectedBalance())
            .balanceDiscrepancy(balanceResult.getBalanceDiscrepancy())
            .transactionCount(accountValidation.getEntryCount())
            .matchedTransactions(matchingResult.getMatchedTransactions().size())
            .unmatchedTransactions(matchingResult.getUnmatchedInternalTransactions().size() + 
                                 matchingResult.getUnmatchedExternalTransactions().size())
            .discrepancyCount(discrepancyResult.getDiscrepancies().size())
            .totalDiscrepancyValue(discrepancyResult.getTotalDiscrepancyValue())
            .autoCorrectedCount(autoCorrectionResult.getCorrectedDiscrepancies().size())
            .manualReviewRequired(autoCorrectionResult.getManualReviewRequired().size())
            .withinTolerance(balanceResult.isWithinTolerance())
            .hasCriticalIssues(discrepancyResult.isHasCriticalDiscrepancies())
            .externalReference(event.getExternalReference())
            .correlationId(correlationId)
            .processedAt(LocalDateTime.now())
            .processingTimeMs(System.currentTimeMillis() - processingStartTime)
            .build();
        
        reconciliation = reconciliationRepository.save(reconciliation);
        
        // Save discrepancy records
        for (DiscrepancyRecord discrepancy : discrepancyResult.getDiscrepancies()) {
            discrepancy.setReconciliationId(reconciliation.getReconciliationId());
            discrepancyRepository.save(discrepancy);
        }
        
        // Create detailed audit log
        Map<String, Object> reconciliationAuditData = new HashMap<>();
        reconciliationAuditData.put("reconciliationId", reconciliation.getReconciliationId());
        reconciliationAuditData.put("accountId", event.getAccountId());
        reconciliationAuditData.put("status", reconciliation.getStatus().toString());
        reconciliationAuditData.put("reconciliationType", reconciliation.getReconciliationType().toString());
        reconciliationAuditData.put("discrepancyCount", discrepancyResult != null ? discrepancyResult.getDiscrepancies().size() : 0);
        reconciliationAuditData.put("balanceMatched", balanceResult != null && balanceResult.isBalanced());
        reconciliationAuditData.put("processingTimeMs", System.currentTimeMillis() - processingStartTime);
        auditService.logAuditEvent("RECONCILIATION_COMPLETED", correlationId, reconciliationAuditData);
        
        log.debug("STEP 11: Audit trail created - Reconciliation ID: {}, Status: {}, Correlation: {}",
            reconciliation.getId(), reconciliation.getStatus(), correlationId);
        
        return reconciliation;
    }

    /**
     * STEP 12: Notification dispatch and escalation
     */
    private void dispatchNotificationsAndEscalation(AccountReconciliationEvent event, AccountReconciliation reconciliation,
            DiscrepancyAnalysisResult discrepancyResult, AutoCorrectionResult autoCorrectionResult, String correlationId) {
        log.debug("STEP 12: Dispatching notifications - Correlation: {}", correlationId);
        
        // Send reconciliation completion notification
        CompletableFuture.runAsync(() -> {
            notificationService.sendReconciliationCompletionNotification(
                event.getAccountId(),
                reconciliation.getStatus(),
                reconciliation.getBalanceDiscrepancy(),
                reconciliation.getDiscrepancyCount()
            );
        });
        
        // Send discrepancy alerts
        if (reconciliation.getDiscrepancyCount() > 0) {
            kafkaTemplate.send(DISCREPANCY_ALERT_TOPIC, Map.of(
                "reconciliationId", reconciliation.getReconciliationId(),
                "accountId", event.getAccountId(),
                "discrepancyCount", reconciliation.getDiscrepancyCount(),
                "totalValue", reconciliation.getTotalDiscrepancyValue(),
                "critical", reconciliation.isHasCriticalIssues(),
                "correlationId", correlationId
            ));
        }
        
        // Critical escalation
        if (reconciliation.isHasCriticalIssues()) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendCriticalReconciliationAlert(
                    event.getAccountId(),
                    reconciliation.getTotalDiscrepancyValue(),
                    discrepancyResult.getDiscrepancies().size(),
                    correlationId
                );
            });
        }
        
        // Compliance alerts
        if (requiresComplianceReview(reconciliation)) {
            kafkaTemplate.send(COMPLIANCE_ALERT_TOPIC, Map.of(
                "eventType", "RECONCILIATION_COMPLIANCE_REVIEW",
                "reconciliationId", reconciliation.getReconciliationId(),
                "accountId", event.getAccountId(),
                "reason", "Critical discrepancies require compliance review",
                "correlationId", correlationId
            ));
        }
        
        // Auto-correction notifications
        if (autoCorrectionResult.getCorrectedDiscrepancies().size() > 0) {
            kafkaTemplate.send(AUTO_CORRECTION_TOPIC, Map.of(
                "reconciliationId", reconciliation.getReconciliationId(),
                "accountId", event.getAccountId(),
                "correctionsApplied", autoCorrectionResult.getCorrectedDiscrepancies().size(),
                "correlationId", correlationId
            ));
        }
        
        // Publish reconciliation completed event
        ReconciliationCompletedEvent completedEvent = ReconciliationCompletedEvent.builder()
            .reconciliationId(event.getReconciliationId())
            .accountId(event.getAccountId())
            .reconciliationDate(event.getReconciliationDate())
            .status(reconciliation.getStatus().toString())
            .balanceDiscrepancy(reconciliation.getBalanceDiscrepancy())
            .discrepancyCount(reconciliation.getDiscrepancyCount())
            .withinTolerance(reconciliation.isWithinTolerance())
            .requiresManualReview(reconciliation.getManualReviewRequired() > 0)
            .correlationId(correlationId)
            .completedAt(reconciliation.getProcessedAt())
            .build();
        
        kafkaTemplate.send(RECONCILIATION_COMPLETED_TOPIC, completedEvent);
        
        log.debug("STEP 12: Notifications dispatched - Correlation: {}", correlationId);
    }

    // Error handling methods
    private void handleDiscrepancyError(AccountReconciliationEvent event, DiscrepancyException e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Discrepancy error in reconciliation - ID: {}, Error: {}, Correlation: {}",
            event.getReconciliationId(), e.getMessage(), correlationId);
        
        // Create failed reconciliation record
        AccountReconciliation failedReconciliation = AccountReconciliation.builder()
            .reconciliationId(event.getReconciliationId())
            .accountId(event.getAccountId())
            .reconciliationDate(event.getReconciliationDate())
            .status(ReconciliationStatus.FAILED)
            .failureReason(e.getMessage())
            .correlationId(correlationId)
            .processedAt(LocalDateTime.now())
            .build();
        
        reconciliationRepository.save(failedReconciliation);
        acknowledgment.acknowledge();
    }

    private void handleBalanceError(AccountReconciliationEvent event, BalanceException e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Balance error in reconciliation - ID: {}, Error: {}, Correlation: {}",
            event.getReconciliationId(), e.getMessage(), correlationId);
        
        sendToDeadLetterQueue(event, e, correlationId);
        acknowledgment.acknowledge();
    }

    private void handleReconciliationError(AccountReconciliationEvent event, ReconciliationException e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Reconciliation processing error - ID: {}, Error: {}, Correlation: {}",
            event.getReconciliationId(), e.getMessage(), correlationId);
        
        sendToDeadLetterQueue(event, e, correlationId);
        acknowledgment.acknowledge();
    }

    private void handleCriticalError(AccountReconciliationEvent event, Exception e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Critical error in reconciliation processing - ID: {}, Error: {}, Correlation: {}",
            event.getReconciliationId(), e.getMessage(), e, correlationId);
        
        sendToDeadLetterQueue(event, e, correlationId);
        
        // Send critical alert
        notificationService.sendCriticalAlert(
            "RECONCILIATION_PROCESSING_ERROR",
            String.format("Critical error processing reconciliation %s: %s", event.getReconciliationId(), e.getMessage()),
            correlationId
        );
        
        acknowledgment.acknowledge();
    }

    // Utility methods
    private String generateCorrelationId(AccountReconciliationEvent event, int partition, long offset) {
        return String.format("recon-%s-p%d-o%d-%d",
            event.getReconciliationId(), partition, offset, System.currentTimeMillis());
    }

    private String sanitizeString(String input) {
        if (input == null) return null;
        return input.trim().replaceAll("[<>\"'&]", "");
    }

    private void acknowledgeAndReturn(Acknowledgment acknowledgment, String message) {
        log.info(message);
        acknowledgment.acknowledge();
    }

    private boolean isFinancialAccount(String accountId) {
        return accountId != null && (accountId.startsWith("FIN") || accountId.startsWith("CASH"));
    }

    private boolean isRegulatoryAccount(String accountId) {
        return accountId != null && (accountId.startsWith("REG") || accountId.startsWith("CAP"));
    }

    private BigDecimal calculateInternalBalance(List<LedgerEntry> ledgerEntries) {
        return ledgerEntries.stream()
            .map(entry -> entry.getDebitAmount().subtract(entry.getCreditAmount()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ReconciliationStatus determineReconciliationStatus(DiscrepancyAnalysisResult discrepancyResult, 
            BalanceVerificationResult balanceResult) {
        if (discrepancyResult.isHasCriticalDiscrepancies()) {
            return ReconciliationStatus.FAILED;
        } else if (discrepancyResult.getDiscrepancies().size() > 0) {
            return ReconciliationStatus.COMPLETED_WITH_DISCREPANCIES;
        } else if (balanceResult.isWithinTolerance()) {
            return ReconciliationStatus.COMPLETED;
        } else {
            return ReconciliationStatus.REVIEW_REQUIRED;
        }
    }

    private boolean requiresComplianceReview(AccountReconciliation reconciliation) {
        return reconciliation.isHasCriticalIssues() || 
               reconciliation.getTotalDiscrepancyValue().compareTo(CRITICAL_DISCREPANCY_THRESHOLD) > 0;
    }

    private LedgerEntry createCorrectionEntry(AccountReconciliationEvent event, DiscrepancyRecord discrepancy, 
            String correlationId) {
        return LedgerEntry.builder()
            .accountId(event.getAccountId())
            .transactionId("AUTO_CORRECTION_" + discrepancy.getId())
            .description("Auto-correction for reconciliation discrepancy")
            .debitAmount(discrepancy.getAmount().compareTo(BigDecimal.ZERO) > 0 ? discrepancy.getAmount() : BigDecimal.ZERO)
            .creditAmount(discrepancy.getAmount().compareTo(BigDecimal.ZERO) < 0 ? discrepancy.getAmount().abs() : BigDecimal.ZERO)
            .entryDate(LocalDate.now())
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .build();
    }

    private void sendToDeadLetterQueue(AccountReconciliationEvent event, Exception error, String correlationId) {
        try {
            Map<String, Object> dlqMessage = Map.of(
                "originalEvent", event,
                "errorMessage", error.getMessage(),
                "errorClass", error.getClass().getName(),
                "correlationId", correlationId,
                "failedAt", Instant.now(),
                "service", "ledger-service"
            );
            
            kafkaTemplate.send(DLQ_TOPIC, dlqMessage);
            log.warn("Sent failed reconciliation to DLQ - ID: {}, Correlation: {}",
                event.getReconciliationId(), correlationId);
                
        } catch (Exception dlqError) {
            log.error("Failed to send reconciliation to DLQ - Correlation: {}", correlationId, dlqError);
        }
    }

    // Inner classes for results
    @lombok.Data
    @lombok.Builder
    private static class AccountValidationResult {
        private String accountId;
        private List<LedgerEntry> ledgerEntries;
        private BigDecimal internalBalance;
        private int entryCount;
        private boolean validationPassed;
    }

    @lombok.Data
    @lombok.Builder
    private static class ExternalDataResult {
        private boolean successful;
        private BigDecimal externalBalance;
        private List<Object> transactions;
        private String errorMessage;
        private boolean dataComplete;
    }

    @lombok.Data
    @lombok.Builder
    private static class TransactionMatchingResult {
        private List<Object> matchedTransactions;
        private List<LedgerEntry> unmatchedInternalTransactions;
        private List<Object> unmatchedExternalTransactions;
        private double matchRate;
    }

    @lombok.Data
    @lombok.Builder
    private static class BalanceVerificationResult {
        private BigDecimal balanceDiscrepancy;
        private boolean withinTolerance;
        private boolean criticalDiscrepancy;
        private Map<String, BigDecimal> balanceBreakdown;
    }

    @lombok.Data
    @lombok.Builder
    private static class DiscrepancyAnalysisResult {
        private List<DiscrepancyRecord> discrepancies;
        private BigDecimal totalDiscrepancyValue;
        private boolean hasCriticalDiscrepancies;
        private Map<String, Integer> discrepancyCategories;
    }

    @lombok.Data
    @lombok.Builder
    private static class AutoCorrectionResult {
        private List<DiscrepancyRecord> correctedDiscrepancies;
        private List<DiscrepancyRecord> manualReviewRequired;
        private BigDecimal totalCorrectionValue;
    }
}