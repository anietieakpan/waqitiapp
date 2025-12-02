package com.waqiti.reconciliation.service;

import com.waqiti.reconciliation.domain.*;
import com.waqiti.reconciliation.dto.*;
import com.waqiti.reconciliation.repository.*;
import com.waqiti.reconciliation.client.*;
import com.waqiti.reconciliation.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Automated Reconciliation Service
 * 
 * Comprehensive reconciliation engine providing:
 * - Real-time transaction matching and validation
 * - End-of-day balance reconciliation across all systems
 * - Nostro account reconciliation with external banks
 * - Internal ledger consistency validation
 * - Break detection and investigation workflows
 * - Variance analysis and automatic correction
 * - Settlement tracking and confirmation matching
 * - Multi-currency reconciliation processing
 * - Automated exception handling and alerting
 * - Regulatory reconciliation reporting
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutomatedReconciliationService {

    private final ReconciliationJobRepository reconciliationJobRepository;
    private final ReconciliationBreakRepository reconciliationBreakRepository;
    private final SettlementRepository settlementRepository;
    private final ReconciliationRuleRepository reconciliationRuleRepository;
    private final LedgerServiceClient ledgerServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final ExternalBankServiceClient externalBankServiceClient;
    private final TransactionServiceClient transactionServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final ReconciliationMatchingEngine matchingEngine;
    private final VarianceAnalysisService varianceAnalysisService;
    private final BreakInvestigationService breakInvestigationService;
    private final ApplicationContext applicationContext;

    /**
     * Performs daily settlement batch processing
     * Migrated from batch-service as part of service consolidation
     * Processes merchant settlements, payment settlements, and generates settlement reports
     */
    @Scheduled(cron = "${batch.settlement.schedule:0 0 15 * * *}") // 3 PM daily (configurable)
    @Transactional
    public void processDailySettlementBatch() {
        LocalDate settlementDate = LocalDate.now();

        try {
            log.info("Starting daily settlement batch processing for date: {}", settlementDate);

            // Get all pending settlements for the date
            List<Settlement> pendingSettlements = settlementRepository.findBySettlementDateAndStatus(
                settlementDate, Settlement.SettlementStatus.PENDING);

            if (pendingSettlements.isEmpty()) {
                log.info("No pending settlements for date: {}", settlementDate);
                return;
            }

            log.info("Found {} pending settlements to process", pendingSettlements.size());

            int successCount = 0;
            int failureCount = 0;

            // Process each settlement
            for (Settlement settlement : pendingSettlements) {
                try {
                    SettlementReconciliationRequest request = SettlementReconciliationRequest.builder()
                        .settlementId(settlement.getId())
                        .settlementDate(settlementDate)
                        .build();

                    SettlementReconciliationResult result = reconcileSettlement(request);

                    if (result.isReconciled()) {
                        successCount++;
                    } else {
                        failureCount++;
                        log.warn("Settlement reconciliation failed: settlementId={}, discrepancies={}",
                            settlement.getId(), result.getDiscrepancies());
                    }

                } catch (Exception e) {
                    failureCount++;
                    log.error("Failed to process settlement: settlementId={}", settlement.getId(), e);
                }
            }

            log.info("Daily settlement batch completed: date={}, total={}, success={}, failed={}",
                settlementDate, pendingSettlements.size(), successCount, failureCount);

            // Send summary notification if there were failures
            if (failureCount > 0) {
                sendSettlementBatchFailureAlert(settlementDate, successCount, failureCount);
            }

        } catch (Exception e) {
            log.error("Daily settlement batch processing failed for date: {}", settlementDate, e);
            sendCriticalAlert("SETTLEMENT_BATCH_FAILED",
                "Daily settlement batch failed for " + settlementDate, e);
        }
    }

    /**
     * Performs end-of-day reconciliation for all accounts
     */
    @Scheduled(cron = "${batch.reconciliation.schedule:0 0 1 * * ?}") // 1 AM daily (configurable)
    @Transactional
    public void performEndOfDayReconciliation() {
        LocalDate reconciliationDate = LocalDate.now().minusDays(1);

        try {
            log.info("Starting end-of-day reconciliation for date: {}", reconciliationDate);
            
            ReconciliationJob job = createReconciliationJob(
                ReconciliationJob.JobType.END_OF_DAY, reconciliationDate);
            
            // Step 1: Reconcile all customer accounts
            CustomerAccountReconciliationResult customerResult = reconcileCustomerAccounts(reconciliationDate);
            job.addStepResult("CUSTOMER_ACCOUNTS", customerResult);
            
            // Step 2: Reconcile system accounts
            SystemAccountReconciliationResult systemResult = reconcileSystemAccounts(reconciliationDate);
            job.addStepResult("SYSTEM_ACCOUNTS", systemResult);
            
            // Step 3: Reconcile nostro accounts
            NostroReconciliationResult nostroResult = reconcileNostroAccounts(reconciliationDate);
            job.addStepResult("NOSTRO_ACCOUNTS", nostroResult);
            
            // Step 4: Validate general ledger balance
            GeneralLedgerReconciliationResult glResult = reconcileGeneralLedger(reconciliationDate);
            job.addStepResult("GENERAL_LEDGER", glResult);
            
            // Step 5: Process breaks and exceptions
            BreakProcessingResult breakResult = processReconciliationBreaks(job);
            job.addStepResult("BREAK_PROCESSING", breakResult);
            
            // Determine overall reconciliation status
            ReconciliationStatus overallStatus = determineOverallStatus(job);
            job.setStatus(overallStatus);
            job.setCompletedAt(LocalDateTime.now());
            
            reconciliationJobRepository.save(job);
            
            // Send notifications
            sendReconciliationNotifications(job);
            
            log.info("End-of-day reconciliation completed: date={}, status={}, breaks={}", 
                    reconciliationDate, overallStatus, breakResult.getBreakCount());
            
        } catch (Exception e) {
            log.error("End-of-day reconciliation failed for date: {}", reconciliationDate, e);
            handleReconciliationFailure(reconciliationDate, e);
        }
    }

    /**
     * Performs real-time transaction reconciliation
     */
    @Transactional
    public RealTimeReconciliationResult reconcileTransaction(RealTimeReconciliationRequest request) {
        try {
            log.debug("Performing real-time reconciliation for transaction: {}", request.getTransactionId());
            
            // Get transaction details from transaction service
            TransactionDetails transaction = transactionServiceClient.getTransactionDetails(request.getTransactionId());
            
            // Get corresponding ledger entries
            List<LedgerEntry> ledgerEntries = ledgerServiceClient.getLedgerEntriesByTransaction(request.getTransactionId());
            
            // Validate transaction against ledger entries
            TransactionLedgerMatchResult matchResult = validateTransactionAgainstLedger(transaction, ledgerEntries);
            
            if (!matchResult.isMatched()) {
                // Create reconciliation break
                ReconciliationBreak breakRecord = createReconciliationBreak(
                    ReconciliationBreak.BreakType.TRANSACTION_LEDGER_MISMATCH,
                    transaction.getTransactionId(),
                    matchResult.getVariances(),
                    "Real-time transaction reconciliation failed"
                );
                
                reconciliationBreakRepository.save(breakRecord);
                
                return RealTimeReconciliationResult.builder()
                    .transactionId(request.getTransactionId())
                    .reconciled(false)
                    .breakId(breakRecord.getBreakId())
                    .variances(matchResult.getVariances())
                    .message("Transaction reconciliation break detected")
                    .build();
            }
            
            return RealTimeReconciliationResult.builder()
                .transactionId(request.getTransactionId())
                .reconciled(true)
                .message("Transaction reconciled successfully")
                .build();
                
        } catch (Exception e) {
            log.error("Real-time reconciliation failed for transaction: {}", request.getTransactionId(), e);
            return RealTimeReconciliationResult.builder()
                .transactionId(request.getTransactionId())
                .reconciled(false)
                .message("Reconciliation error: " + e.getMessage())
                .build();
        }
    }

    /**
     * Reconciles account balances across systems
     */
    @Transactional
    public AccountBalanceReconciliationResult reconcileAccountBalance(AccountBalanceReconciliationRequest request) {
        try {
            log.info("Reconciling account balance: accountId={}, asOfDate={}", 
                    request.getAccountId(), request.getAsOfDate());
            
            // Get balance from account service
            AccountBalance accountServiceBalance = accountServiceClient.getAccountBalance(
                request.getAccountId(), request.getAsOfDate());
            
            // Get calculated balance from ledger service
            LedgerCalculatedBalance ledgerBalance = ledgerServiceClient.calculateAccountBalance(
                request.getAccountId(), request.getAsOfDate());
            
            // Compare balances
            BalanceComparisonResult comparison = compareBalances(accountServiceBalance, ledgerBalance);
            
            if (!comparison.isMatched()) {
                // Investigate and attempt to resolve variance
                VarianceResolutionResult resolution = investigateAndResolveVariance(
                    request.getAccountId(), comparison, request.getAsOfDate());
                
                if (!resolution.isResolved()) {
                    // Create reconciliation break
                    ReconciliationBreak breakRecord = createReconciliationBreak(
                        ReconciliationBreak.BreakType.BALANCE_VARIANCE,
                        request.getAccountId(),
                        List.of(comparison.getVariance()),
                        "Account balance variance detected"
                    );
                    
                    reconciliationBreakRepository.save(breakRecord);
                    
                    return AccountBalanceReconciliationResult.builder()
                        .accountId(request.getAccountId())
                        .reconciled(false)
                        .accountServiceBalance(accountServiceBalance.getBalance())
                        .ledgerBalance(ledgerBalance.getBalance())
                        .variance(comparison.getVariance().getAmount())
                        .breakId(breakRecord.getBreakId())
                        .message("Balance variance requires investigation")
                        .build();
                }
            }
            
            return AccountBalanceReconciliationResult.builder()
                .accountId(request.getAccountId())
                .reconciled(true)
                .accountServiceBalance(accountServiceBalance.getBalance())
                .ledgerBalance(ledgerBalance.getBalance())
                .variance(BigDecimal.ZERO)
                .message("Account balance reconciled successfully")
                .build();
                
        } catch (Exception e) {
            log.error("Account balance reconciliation failed: accountId={}", request.getAccountId(), e);
            throw new ReconciliationException("Failed to reconcile account balance", e);
        }
    }

    /**
     * Performs settlement reconciliation with external systems
     */
    @Transactional
    public SettlementReconciliationResult reconcileSettlement(SettlementReconciliationRequest request) {
        try {
            log.info("Reconciling settlement: settlementId={}, date={}", 
                    request.getSettlementId(), request.getSettlementDate());
            
            // Get internal settlement record
            Settlement internalSettlement = settlementRepository.findById(request.getSettlementId())
                .orElseThrow(() -> new SettlementNotFoundException("Settlement not found: " + request.getSettlementId()));
            
            // Get external settlement confirmation
            ExternalSettlementConfirmation externalConfirmation = externalBankServiceClient.getSettlementConfirmation(
                request.getSettlementId(), request.getSettlementDate());
            
            // Match settlement details
            SettlementMatchResult matchResult = matchSettlementDetails(internalSettlement, externalConfirmation);
            
            if (!matchResult.isMatched()) {
                // Create settlement break
                ReconciliationBreak breakRecord = createReconciliationBreak(
                    ReconciliationBreak.BreakType.SETTLEMENT_MISMATCH,
                    request.getSettlementId(),
                    matchResult.getDiscrepancies(),
                    "Settlement reconciliation mismatch"
                );
                
                reconciliationBreakRepository.save(breakRecord);
                
                // Update settlement status
                internalSettlement.setStatus(Settlement.SettlementStatus.RECONCILIATION_PENDING);
                settlementRepository.save(internalSettlement);
                
                return SettlementReconciliationResult.builder()
                    .settlementId(request.getSettlementId())
                    .reconciled(false)
                    .breakId(breakRecord.getBreakId())
                    .discrepancies(matchResult.getDiscrepancies())
                    .message("Settlement reconciliation break detected")
                    .build();
            }
            
            // Update settlement status to reconciled
            internalSettlement.setStatus(Settlement.SettlementStatus.RECONCILED);
            internalSettlement.setReconciledAt(LocalDateTime.now());
            settlementRepository.save(internalSettlement);
            
            return SettlementReconciliationResult.builder()
                .settlementId(request.getSettlementId())
                .reconciled(true)
                .message("Settlement reconciled successfully")
                .build();
                
        } catch (Exception e) {
            log.error("Settlement reconciliation failed: settlementId={}", request.getSettlementId(), e);
            throw new ReconciliationException("Failed to reconcile settlement", e);
        }
    }

    /**
     * Investigates and resolves reconciliation breaks
     */
    @Transactional
    public BreakResolutionResult investigateAndResolveBreak(UUID breakId) {
        try {
            log.info("Investigating reconciliation break: {}", breakId);
            
            ReconciliationBreak breakRecord = reconciliationBreakRepository.findById(breakId)
                .orElseThrow(() -> new ReconciliationBreakNotFoundException("Break not found: " + breakId));
            
            // Update break status to investigating
            breakRecord.setStatus(ReconciliationBreak.BreakStatus.INVESTIGATING);
            breakRecord.setInvestigationStartedAt(LocalDateTime.now());
            reconciliationBreakRepository.save(breakRecord);
            
            // Perform automated investigation
            BreakInvestigationResult investigation = breakInvestigationService.investigateBreak(breakRecord);
            
            BreakResolutionResult resolution;
            
            if (investigation.isAutoResolvable()) {
                // Attempt automatic resolution
                AutoResolutionResult autoResolution = attemptAutoResolution(breakRecord, investigation);
                
                if (autoResolution.isSuccessful()) {
                    breakRecord.setStatus(ReconciliationBreak.BreakStatus.RESOLVED);
                    breakRecord.setResolvedAt(LocalDateTime.now());
                    breakRecord.setResolutionMethod(autoResolution.getResolutionMethod());
                    breakRecord.setResolutionNotes(autoResolution.getResolutionNotes());
                    
                    resolution = BreakResolutionResult.builder()
                        .breakId(breakId)
                        .resolved(true)
                        .resolutionMethod("AUTOMATIC")
                        .resolutionNotes(autoResolution.getResolutionNotes())
                        .build();
                } else {
                    breakRecord.setStatus(ReconciliationBreak.BreakStatus.MANUAL_REVIEW_REQUIRED);
                    
                    resolution = BreakResolutionResult.builder()
                        .breakId(breakId)
                        .resolved(false)
                        .resolutionMethod("MANUAL_REVIEW_REQUIRED")
                        .resolutionNotes("Automatic resolution failed: " + autoResolution.getFailureReason())
                        .build();
                }
            } else {
                // Requires manual investigation
                breakRecord.setStatus(ReconciliationBreak.BreakStatus.MANUAL_REVIEW_REQUIRED);
                
                resolution = BreakResolutionResult.builder()
                    .breakId(breakId)
                    .resolved(false)
                    .resolutionMethod("MANUAL_REVIEW_REQUIRED")
                    .resolutionNotes(investigation.getInvestigationNotes())
                    .build();
            }
            
            reconciliationBreakRepository.save(breakRecord);
            
            log.info("Break investigation completed: breakId={}, resolved={}", 
                    breakId, resolution.isResolved());
            
            return resolution;
            
        } catch (Exception e) {
            log.error("Break investigation failed: breakId={}", breakId, e);
            throw new ReconciliationException("Failed to investigate break", e);
        }
    }

    /**
     * Generates comprehensive reconciliation report
     */
    public ReconciliationReportResult generateReconciliationReport(ReconciliationReportRequest request) {
        try {
            log.info("Generating reconciliation report: type={}, period={} to {}", 
                    request.getReportType(), request.getFromDate(), request.getToDate());
            
            ReconciliationReportData reportData = ReconciliationReportData.builder()
                .reportType(request.getReportType())
                .fromDate(request.getFromDate())
                .toDate(request.getToDate())
                .generatedAt(LocalDateTime.now())
                .build();
            
            switch (request.getReportType()) {
                case DAILY_RECONCILIATION_SUMMARY:
                    reportData.setSummaryData(generateDailyReconciliationSummary(request.getFromDate(), request.getToDate()));
                    break;
                case BREAK_ANALYSIS_REPORT:
                    reportData.setBreakAnalysisData(generateBreakAnalysisReport(request.getFromDate(), request.getToDate()));
                    break;
                case NOSTRO_RECONCILIATION_REPORT:
                    reportData.setNostroReconciliationData(generateNostroReconciliationReport(request.getFromDate(), request.getToDate()));
                    break;
                case VARIANCE_TREND_ANALYSIS:
                    reportData.setVarianceTrendData(generateVarianceTrendAnalysis(request.getFromDate(), request.getToDate()));
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported report type: " + request.getReportType());
            }
            
            return ReconciliationReportResult.builder()
                .reportId(UUID.randomUUID())
                .reportData(reportData)
                .successful(true)
                .message("Report generated successfully")
                .build();
                
        } catch (Exception e) {
            log.error("Reconciliation report generation failed: type={}", request.getReportType(), e);
            return ReconciliationReportResult.builder()
                .successful(false)
                .message("Report generation failed: " + e.getMessage())
                .build();
        }
    }

    // Private helper methods

    private ReconciliationJob createReconciliationJob(ReconciliationJob.JobType jobType, LocalDate reconciliationDate) {
        ReconciliationJob job = ReconciliationJob.builder()
            .jobId(UUID.randomUUID())
            .jobType(jobType)
            .reconciliationDate(reconciliationDate)
            .status(ReconciliationStatus.IN_PROGRESS)
            .startedAt(LocalDateTime.now())
            .stepResults(new HashMap<>())
            .build();
        
        return reconciliationJobRepository.save(job);
    }

    private CustomerAccountReconciliationResult reconcileCustomerAccounts(LocalDate reconciliationDate) {
        try {
            log.info("Reconciling customer accounts for date: {}", reconciliationDate);
            
            // Get all active customer accounts
            List<CustomerAccount> customerAccounts = accountServiceClient.getAllActiveCustomerAccounts();
            
            int totalAccounts = customerAccounts.size();
            int reconciledAccounts = 0;
            int breaksDetected = 0;
            List<AccountReconciliationBreak> breaks = new ArrayList<>();
            
            // Get Spring proxy to avoid self-invocation issues
            AutomatedReconciliationService self = applicationContext.getBean(AutomatedReconciliationService.class);
            
            for (CustomerAccount account : customerAccounts) {
                AccountBalanceReconciliationResult result = self.reconcileAccountBalance(
                    AccountBalanceReconciliationRequest.builder()
                        .accountId(account.getAccountId())
                        .asOfDate(reconciliationDate.atTime(23, 59, 59))
                        .build()
                );
                
                if (result.isReconciled()) {
                    reconciledAccounts++;
                } else {
                    breaksDetected++;
                    breaks.add(AccountReconciliationBreak.builder()
                        .accountId(account.getAccountId())
                        .variance(result.getVariance())
                        .breakId(result.getBreakId())
                        .build());
                }
            }
            
            return CustomerAccountReconciliationResult.builder()
                .reconciliationDate(reconciliationDate)
                .totalAccounts(totalAccounts)
                .reconciledAccounts(reconciledAccounts)
                .breaksDetected(breaksDetected)
                .breaks(breaks)
                .build();
                
        } catch (Exception e) {
            log.error("Customer account reconciliation failed for date: {}", reconciliationDate, e);
            throw new ReconciliationException("Failed to reconcile customer accounts", e);
        }
    }

    private SystemAccountReconciliationResult reconcileSystemAccounts(LocalDate reconciliationDate) {
        // Implementation would reconcile all system accounts
        return SystemAccountReconciliationResult.builder()
            .reconciliationDate(reconciliationDate)
            .totalSystemAccounts(0)
            .reconciledAccounts(0)
            .breaksDetected(0)
            .build();
    }

    private NostroReconciliationResult reconcileNostroAccounts(LocalDate reconciliationDate) {
        // Implementation would reconcile nostro accounts with external banks
        return NostroReconciliationResult.builder()
            .reconciliationDate(reconciliationDate)
            .totalNostroAccounts(0)
            .reconciledAccounts(0)
            .breaksDetected(0)
            .build();
    }

    private GeneralLedgerReconciliationResult reconcileGeneralLedger(LocalDate reconciliationDate) {
        try {
            // Get trial balance from ledger service
            TrialBalanceResponse trialBalance = ledgerServiceClient.generateTrialBalance(
                reconciliationDate.atTime(23, 59, 59));
            
            boolean isBalanced = trialBalance.isBalanced();
            BigDecimal variance = trialBalance.getTotalDebits().subtract(trialBalance.getTotalCredits());
            
            if (!isBalanced) {
                // Create GL reconciliation break
                createReconciliationBreak(
                    ReconciliationBreak.BreakType.GENERAL_LEDGER_IMBALANCE,
                    null,
                    List.of(ReconciliationVariance.builder()
                        .varianceType("GL_IMBALANCE")
                        .amount(variance.abs())
                        .description("General ledger out of balance")
                        .build()),
                    "General ledger trial balance does not balance"
                );
            }
            
            return GeneralLedgerReconciliationResult.builder()
                .reconciliationDate(reconciliationDate)
                .balanced(isBalanced)
                .totalDebits(trialBalance.getTotalDebits())
                .totalCredits(trialBalance.getTotalCredits())
                .variance(variance)
                .build();
                
        } catch (Exception e) {
            log.error("General ledger reconciliation failed for date: {}", reconciliationDate, e);
            throw new ReconciliationException("Failed to reconcile general ledger", e);
        }
    }

    private BreakProcessingResult processReconciliationBreaks(ReconciliationJob job) {
        // Get all unresolved breaks
        List<ReconciliationBreak> unresolvedBreaks = reconciliationBreakRepository.findUnresolvedBreaks();
        
        int totalBreaks = unresolvedBreaks.size();
        int resolvedBreaks = 0;
        
        // Get Spring proxy to avoid self-invocation issues
        AutomatedReconciliationService self = applicationContext.getBean(AutomatedReconciliationService.class);
        
        for (ReconciliationBreak breakRecord : unresolvedBreaks) {
            try {
                BreakResolutionResult resolution = self.investigateAndResolveBreak(breakRecord.getBreakId());
                if (resolution.isResolved()) {
                    resolvedBreaks++;
                }
            } catch (Exception e) {
                log.error("Failed to process break: {}", breakRecord.getBreakId(), e);
            }
        }
        
        return BreakProcessingResult.builder()
            .totalBreaks(totalBreaks)
            .resolvedBreaks(resolvedBreaks)
            .pendingBreaks(totalBreaks - resolvedBreaks)
            .build();
    }

    private ReconciliationStatus determineOverallStatus(ReconciliationJob job) {
        Map<String, Object> stepResults = job.getStepResults();
        
        // Check if any step had breaks
        boolean hasBreaks = stepResults.values().stream()
            .anyMatch(result -> {
                if (result instanceof CustomerAccountReconciliationResult) {
                    CustomerAccountReconciliationResult car = (CustomerAccountReconciliationResult) result;
                    return car.getBreaksDetected() > 0;
                }
                if (result instanceof SystemAccountReconciliationResult) {
                    SystemAccountReconciliationResult sar = (SystemAccountReconciliationResult) result;
                    return sar.getBreaksDetected() > 0;
                }
                if (result instanceof NostroReconciliationResult) {
                    NostroReconciliationResult nrr = (NostroReconciliationResult) result;
                    return nrr.getBreaksDetected() > 0;
                }
                if (result instanceof GeneralLedgerReconciliationResult) {
                    GeneralLedgerReconciliationResult glr = (GeneralLedgerReconciliationResult) result;
                    return !glr.isBalanced();
                }
                return false;
            });
        
        return hasBreaks ? ReconciliationStatus.BREAKS_DETECTED : ReconciliationStatus.RECONCILED;
    }

    private ReconciliationBreak createReconciliationBreak(ReconciliationBreak.BreakType breakType,
                                                        Object entityId,
                                                        List<ReconciliationVariance> variances,
                                                        String description) {
        return ReconciliationBreak.builder()
            .breakId(UUID.randomUUID())
            .breakType(breakType)
            .entityId(entityId != null ? entityId.toString() : null)
            .variances(variances)
            .description(description)
            .status(ReconciliationBreak.BreakStatus.NEW)
            .detectedAt(LocalDateTime.now())
            .build();
    }

    private TransactionLedgerMatchResult validateTransactionAgainstLedger(TransactionDetails transaction, 
                                                                        List<LedgerEntry> ledgerEntries) {
        // Implementation would validate transaction matches ledger entries
        return matchingEngine.matchTransactionToLedger(transaction, ledgerEntries);
    }

    private BalanceComparisonResult compareBalances(AccountBalance accountBalance, LedgerCalculatedBalance ledgerBalance) {
        BigDecimal variance = accountBalance.getBalance().subtract(ledgerBalance.getBalance());
        boolean matched = variance.abs().compareTo(new BigDecimal("0.01")) <= 0; // Allow 1 cent tolerance
        
        ReconciliationVariance varianceObj = null;
        if (!matched) {
            varianceObj = ReconciliationVariance.builder()
                .varianceType("BALANCE_VARIANCE")
                .amount(variance.abs())
                .description("Account balance variance between systems")
                .build();
        }
        
        return BalanceComparisonResult.builder()
            .matched(matched)
            .accountServiceBalance(accountBalance.getBalance())
            .ledgerBalance(ledgerBalance.getBalance())
            .variance(varianceObj)
            .build();
    }

    private VarianceResolutionResult investigateAndResolveVariance(UUID accountId, 
                                                                 BalanceComparisonResult comparison, 
                                                                 LocalDateTime asOfDate) {
        // Implementation would investigate variance and attempt resolution
        return varianceAnalysisService.analyzeAndResolveVariance(accountId, comparison, asOfDate);
    }

    private SettlementMatchResult matchSettlementDetails(Settlement internal, ExternalSettlementConfirmation external) {
        // Implementation would match settlement details
        return matchingEngine.matchSettlementDetails(internal, external);
    }

    private AutoResolutionResult attemptAutoResolution(ReconciliationBreak breakRecord, 
                                                     BreakInvestigationResult investigation) {
        // Implementation would attempt automatic resolution based on investigation findings
        return breakInvestigationService.attemptAutoResolution(breakRecord, investigation);
    }

    private void sendReconciliationNotifications(ReconciliationJob job) {
        try {
            ReconciliationNotificationRequest notification = ReconciliationNotificationRequest.builder()
                .jobId(job.getJobId())
                .reconciliationDate(job.getReconciliationDate())
                .status(job.getStatus())
                .stepResults(job.getStepResults())
                .build();
            
            notificationServiceClient.sendReconciliationNotification(notification);
            
        } catch (Exception e) {
            log.warn("Failed to send reconciliation notifications for job: {}", job.getJobId(), e);
        }
    }

    private void handleReconciliationFailure(LocalDate reconciliationDate, Exception error) {
        // Implementation would handle reconciliation failure
        log.error("Creating failure reconciliation job record for date: {}", reconciliationDate);
        
        ReconciliationJob failedJob = ReconciliationJob.builder()
            .jobId(UUID.randomUUID())
            .jobType(ReconciliationJob.JobType.END_OF_DAY)
            .reconciliationDate(reconciliationDate)
            .status(ReconciliationStatus.FAILED)
            .startedAt(LocalDateTime.now())
            .completedAt(LocalDateTime.now())
            .failureReason(error.getMessage())
            .build();
        
        reconciliationJobRepository.save(failedJob);
    }

    // Report generation methods
    private DailyReconciliationSummaryData generateDailyReconciliationSummary(LocalDate fromDate, LocalDate toDate) {
        List<ReconciliationJob> jobs = reconciliationJobRepository.findByDateRange(fromDate, toDate);
        
        int totalJobs = jobs.size();
        long successfulJobs = jobs.stream().mapToLong(job -> job.getStatus() == ReconciliationStatus.RECONCILED ? 1 : 0).sum();
        long jobsWithBreaks = jobs.stream().mapToLong(job -> job.getStatus() == ReconciliationStatus.BREAKS_DETECTED ? 1 : 0).sum();
        long failedJobs = jobs.stream().mapToLong(job -> job.getStatus() == ReconciliationStatus.FAILED ? 1 : 0).sum();
        
        return DailyReconciliationSummaryData.builder()
            .fromDate(fromDate)
            .toDate(toDate)
            .totalReconciliationJobs(totalJobs)
            .successfulReconciliations(Math.toIntExact(successfulJobs))
            .reconciliationsWithBreaks(Math.toIntExact(jobsWithBreaks))
            .failedReconciliations(Math.toIntExact(failedJobs))
            .reconciliationSuccessRate(totalJobs > 0 ? (double) successfulJobs / totalJobs * 100 : 0.0)
            .build();
    }

    private BreakAnalysisReportData generateBreakAnalysisReport(LocalDate fromDate, LocalDate toDate) {
        List<ReconciliationBreak> breaks = reconciliationBreakRepository.findByDateRange(fromDate, toDate);
        
        Map<ReconciliationBreak.BreakType, Long> breaksByType = breaks.stream()
            .collect(Collectors.groupingBy(ReconciliationBreak::getBreakType, Collectors.counting()));
        
        Map<ReconciliationBreak.BreakStatus, Long> breaksByStatus = breaks.stream()
            .collect(Collectors.groupingBy(ReconciliationBreak::getStatus, Collectors.counting()));
        
        long totalBreaks = breaks.size();
        long resolvedBreaks = breaks.stream().mapToLong(b -> b.getStatus() == ReconciliationBreak.BreakStatus.RESOLVED ? 1 : 0).sum();
        
        return BreakAnalysisReportData.builder()
            .fromDate(fromDate)
            .toDate(toDate)
            .totalBreaks(Math.toIntExact(totalBreaks))
            .resolvedBreaks(Math.toIntExact(resolvedBreaks))
            .pendingBreaks(Math.toIntExact(totalBreaks - resolvedBreaks))
            .breaksByType(breaksByType.entrySet().stream()
                .collect(Collectors.toMap(
                    e -> e.getKey().toString(),
                    e -> Math.toIntExact(e.getValue()))))
            .breaksByStatus(breaksByStatus.entrySet().stream()
                .collect(Collectors.toMap(
                    e -> e.getKey().toString(),
                    e -> Math.toIntExact(e.getValue()))))
            .resolutionRate(totalBreaks > 0 ? (double) resolvedBreaks / totalBreaks * 100 : 0.0)
            .build();
    }

    private NostroReconciliationReportData generateNostroReconciliationReport(LocalDate fromDate, LocalDate toDate) {
        // Implementation would generate nostro reconciliation report
        return NostroReconciliationReportData.builder().build();
    }

    private VarianceTrendAnalysisData generateVarianceTrendAnalysis(LocalDate fromDate, LocalDate toDate) {
        // Implementation would generate variance trend analysis
        return VarianceTrendAnalysisData.builder().build();
    }

    // ========== Settlement Batch Processing Helper Methods (Migrated from batch-service) ==========

    /**
     * Sends alert notification when settlement batch processing has failures
     */
    private void sendSettlementBatchFailureAlert(LocalDate settlementDate, int successCount, int failureCount) {
        try {
            Map<String, Object> alertData = Map.of(
                "alertType", "SETTLEMENT_BATCH_PARTIAL_FAILURE",
                "settlementDate", settlementDate,
                "successCount", successCount,
                "failureCount", failureCount,
                "timestamp", LocalDateTime.now()
            );

            notificationServiceClient.sendAlert(
                "OPERATIONS_TEAM",
                "Settlement Batch Partial Failure",
                String.format("Daily settlement batch for %s completed with %d failures out of %d settlements",
                    settlementDate, failureCount, (successCount + failureCount)),
                alertData
            );

            log.info("Settlement batch failure alert sent: date={}, success={}, failures={}",
                settlementDate, successCount, failureCount);

        } catch (Exception e) {
            log.error("Failed to send settlement batch failure alert", e);
        }
    }

    /**
     * Sends critical alert for complete settlement batch failure
     */
    private void sendCriticalAlert(String alertType, String message, Exception exception) {
        try {
            Map<String, Object> alertData = Map.of(
                "alertType", alertType,
                "message", message,
                "errorMessage", exception.getMessage(),
                "errorClass", exception.getClass().getName(),
                "timestamp", LocalDateTime.now()
            );

            notificationServiceClient.sendCriticalAlert(
                "OPERATIONS_TEAM",
                alertType,
                message,
                alertData
            );

            log.error("CRITICAL ALERT: {} - {}", alertType, message, exception);

        } catch (Exception e) {
            log.error("Failed to send critical alert for: {}", alertType, e);
        }
    }
}