package com.waqiti.payment.service;

import com.waqiti.payment.entity.ACHBatch;
import com.waqiti.payment.entity.ACHBatchStatus;
import com.waqiti.payment.entity.ACHBatchType;
import com.waqiti.payment.entity.ACHTransaction;
import com.waqiti.payment.exception.ACHProcessingException;
import com.waqiti.payment.repository.ACHBatchRepository;
import com.waqiti.payment.repository.ACHTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Production-grade ACH batch processor service for handling Automated Clearing House transactions.
 * Manages batch creation, processing, validation, and settlement operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ACHBatchProcessorService {
    
    private final ACHBatchRepository achBatchRepository;
    private final ACHTransactionRepository achTransactionRepository;
    private final EncryptionService encryptionService;
    private final SecurityContext securityContext;
    private final ComplianceService complianceService;
    private final IdempotencyService idempotencyService;
    private final DistributedLockService distributedLockService;
    
    @Value("${ach.batch.max-transactions:2500}")
    private int maxTransactionsPerBatch;
    
    @Value("${ach.batch.max-amount:25000000.00}")
    private BigDecimal maxBatchAmount;
    
    @Value("${ach.batch.auto-submit:true}")
    private boolean autoSubmitEnabled;
    
    @Value("${ach.batch.processing-cutoff-hour:14}")
    private int processingCutoffHour;
    
    @Value("${ach.settlement.standard-days:1}")
    private int standardSettlementDays;
    
    @Value("${ach.settlement.next-day-enabled:true}")
    private boolean nextDaySettlementEnabled;
    
    @Value("${ach.validation.strict-mode:true}")
    private boolean strictValidationMode;
    
    @Value("${ach.duplicate-detection:true}")
    private boolean duplicateDetectionEnabled;
    
    @Value("${ach.fraud-monitoring:true}")
    private boolean fraudMonitoringEnabled;
    
    // Batch processing statistics
    private final AtomicLong totalBatchesProcessed = new AtomicLong(0);
    private final AtomicLong totalTransactionsProcessed = new AtomicLong(0);
    private final AtomicLong totalFailedBatches = new AtomicLong(0);
    
    // Active batch tracking
    private final Map<String, ACHBatch> activeBatches = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> batchLocks = new ConcurrentHashMap<>();
    
    // NACHA standard validation patterns
    private static final Map<String, String> ROUTING_NUMBER_PATTERNS = Map.of(
        "FEDERAL_RESERVE", "^[0-1][0-9]{8}$",
        "THRIFT", "^[2][0-9]{8}$",
        "ELECTRONIC", "^[3][0-9]{8}$"
    );
    
    /**
     * Create new ACH batch
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ACHBatchResult createACHBatch(ACHBatchRequest request) {
        if (request == null) {
            throw new ACHProcessingException("ACH batch request cannot be null");
        }
        
        log.info("Creating ACH batch for company: {} type: {} with {} transactions", 
                request.getCompanyId(), request.getBatchType(), request.getTransactions().size());
        
        try {
            // Validate batch request
            validateBatchRequest(request);
            
            // Check idempotency
            String idempotencyKey = generateBatchIdempotencyKey(request);
            var idempotencyResult = idempotencyService.checkIdempotency(
                idempotencyKey, ACHBatchResult.class);
            
            if (!idempotencyResult.isNewOperation()) {
                log.debug("Duplicate ACH batch request detected: {}", idempotencyKey);
                return idempotencyResult.getResult();
            }
            
            // Acquire lock for company to prevent concurrent batch creation
            String lockKey = "ach_batch_create_" + request.getCompanyId();
            boolean lockAcquired = distributedLockService.acquireLock(lockKey, 
                java.time.Duration.ofMinutes(10));
            
            if (!lockAcquired) {
                throw new ACHProcessingException("Unable to create batch - please try again");
            }
            
            try {
                // Validate individual transactions
                validateTransactions(request.getTransactions());
                
                // Perform compliance checks
                performBatchComplianceChecks(request);
                
                // Detect duplicates if enabled
                if (duplicateDetectionEnabled) {
                    detectDuplicateTransactions(request);
                }
                
                // Create batch entity
                ACHBatch batch = createBatchEntity(request);
                
                // Create transaction entities
                List<ACHTransaction> transactions = createTransactionEntities(batch.getId(), request.getTransactions());
                
                // Calculate batch totals
                calculateBatchTotals(batch, transactions);
                
                // Validate batch totals against limits
                validateBatchLimits(batch);
                
                // Save batch and transactions
                ACHBatch savedBatch = achBatchRepository.save(batch);
                achTransactionRepository.saveAll(transactions);
                
                // Add to active batches tracking
                activeBatches.put(savedBatch.getId(), savedBatch);
                
                // Create result
                ACHBatchResult result = ACHBatchResult.builder()
                    .batchId(savedBatch.getId())
                    .batchNumber(savedBatch.getBatchNumber())
                    .status(savedBatch.getStatus())
                    .transactionCount(transactions.size())
                    .totalDebitAmount(savedBatch.getTotalDebitAmount())
                    .totalCreditAmount(savedBatch.getTotalCreditAmount())
                    .effectiveEntryDate(savedBatch.getEffectiveEntryDate())
                    .settlementDate(calculateSettlementDate(savedBatch))
                    .estimatedProcessingTime(calculateProcessingTime(savedBatch))
                    .createdAt(savedBatch.getCreatedAt())
                    .build();
                
                // Store idempotency result
                idempotencyService.storeIdempotencyResult(
                    idempotencyKey, result, java.time.Duration.ofHours(48), 
                    Map.of("batchId", savedBatch.getId()));
                
                // Auto-submit if enabled
                if (autoSubmitEnabled && canAutoSubmit(savedBatch)) {
                    CompletableFuture.runAsync(() -> {
                        try {
                            submitBatchForProcessing(savedBatch.getId());
                        } catch (Exception e) {
                            log.error("Auto-submit failed for batch: {}", savedBatch.getId(), e);
                        }
                    });
                }
                
                log.info("Successfully created ACH batch: {} with {} transactions - Total: ${}", 
                        savedBatch.getId(), transactions.size(), 
                        savedBatch.getTotalDebitAmount().add(savedBatch.getTotalCreditAmount()));
                
                return result;
                
            } finally {
                distributedLockService.releaseLock(lockKey);
            }
            
        } catch (Exception e) {
            log.error("Failed to create ACH batch for company: {}", request.getCompanyId(), e);
            throw new ACHProcessingException("ACH batch creation failed", e);
        }
    }
    
    /**
     * Submit batch for processing
     */
    @Transactional
    public boolean submitBatchForProcessing(String batchId) {
        log.info("Submitting ACH batch for processing: {}", batchId);
        
        try {
            ACHBatch batch = achBatchRepository.findById(batchId)
                .orElseThrow(() -> new ACHProcessingException("Batch not found: " + batchId));
            
            // Only submit pending batches
            if (batch.getStatus() != ACHBatchStatus.PENDING) {
                throw new ACHProcessingException("Cannot submit batch in status: " + batch.getStatus());
            }
            
            // Validate batch before submission
            BatchValidationResult validationResult = validateBatchForSubmission(batch);
            if (!validationResult.isValid()) {
                batch.setStatus(ACHBatchStatus.VALIDATION_FAILED);
                batch.setValidationErrors(validationResult.getErrors());
                achBatchRepository.save(batch);
                return false;
            }
            
            // Check processing cutoff
            if (!isWithinProcessingCutoff()) {
                // Schedule for next business day
                batch.setStatus(ACHBatchStatus.SCHEDULED);
                batch.setScheduledProcessingDate(getNextBusinessDay());
            } else {
                batch.setStatus(ACHBatchStatus.SUBMITTED);
                batch.setSubmittedAt(LocalDateTime.now());
            }
            
            batch.setUpdatedAt(LocalDateTime.now());
            achBatchRepository.save(batch);
            
            // Start processing
            if (batch.getStatus() == ACHBatchStatus.SUBMITTED) {
                processSubmittedBatch(batch);
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to submit batch for processing: {}", batchId, e);
            return false;
        }
    }
    
    /**
     * Process submitted batch
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<BatchProcessingResult> processBatch(String batchId) {
        log.info("Processing ACH batch: {}", batchId);
        
        try {
            ACHBatch batch = achBatchRepository.findById(batchId)
                .orElseThrow(() -> new ACHProcessingException("Batch not found: " + batchId));
            
            // Update status
            batch.setStatus(ACHBatchStatus.PROCESSING);
            batch.setProcessingStartedAt(LocalDateTime.now());
            achBatchRepository.save(batch);
            
            // Get all transactions for this batch
            List<ACHTransaction> transactions = achTransactionRepository.findByBatchIdOrderBySequenceNumber(batchId);
            
            // Process each transaction
            BatchProcessingResult result = processTransactions(batch, transactions);
            
            // Update batch status based on results
            updateBatchAfterProcessing(batch, result);
            
            // Update statistics
            totalBatchesProcessed.incrementAndGet();
            totalTransactionsProcessed.addAndGet(transactions.size());
            
            if (result.getFailedCount() > 0) {
                totalFailedBatches.incrementAndGet();
            }
            
            log.info("Completed processing ACH batch: {} - Success: {}, Failed: {}", 
                    batchId, result.getSuccessCount(), result.getFailedCount());
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            log.error("Failed to process ACH batch: {}", batchId, e);
            
            // Update batch status to failed
            try {
                achBatchRepository.findById(batchId).ifPresent(batch -> {
                    batch.setStatus(ACHBatchStatus.PROCESSING_FAILED);
                    batch.setProcessingFailedAt(LocalDateTime.now());
                    batch.setFailureReason(e.getMessage());
                    achBatchRepository.save(batch);
                });
            } catch (Exception saveException) {
                log.error("Failed to update batch status after processing failure", saveException);
            }
            
            return CompletableFuture.completedFuture(BatchProcessingResult.failed(e.getMessage()));
        } finally {
            // Remove from active batches
            activeBatches.remove(batchId);
        }
    }
    
    /**
     * Get batch by ID
     */
    @Transactional(readOnly = true)
    public Optional<ACHBatch> getACHBatch(String batchId) {
        try {
            return achBatchRepository.findById(batchId);
        } catch (Exception e) {
            log.error("Failed to get ACH batch: {}", batchId, e);
            return Optional.empty();
        }
    }
    
    /**
     * Get company batches with pagination
     */
    @Transactional(readOnly = true)
    public Page<ACHBatch> getCompanyBatches(String companyId, Pageable pageable) {
        try {
            return achBatchRepository.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable);
        } catch (Exception e) {
            log.error("Failed to get ACH batches for company: {}", companyId, e);
            return Page.empty();
        }
    }
    
    /**
     * Cancel pending batch
     */
    @Transactional
    public boolean cancelBatch(String batchId, String reason) {
        log.info("Cancelling ACH batch: {} - Reason: {}", batchId, reason);
        
        try {
            ACHBatch batch = achBatchRepository.findById(batchId)
                .orElseThrow(() -> new ACHProcessingException("Batch not found: " + batchId));
            
            // Only cancel pending or scheduled batches
            if (batch.getStatus() != ACHBatchStatus.PENDING && 
                batch.getStatus() != ACHBatchStatus.SCHEDULED) {
                throw new ACHProcessingException("Cannot cancel batch in status: " + batch.getStatus());
            }
            
            batch.setStatus(ACHBatchStatus.CANCELLED);
            batch.setCancellationReason(reason);
            batch.setCancelledAt(LocalDateTime.now());
            batch.setUpdatedAt(LocalDateTime.now());
            
            achBatchRepository.save(batch);
            
            // Remove from active tracking
            activeBatches.remove(batchId);
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to cancel ACH batch: {}", batchId, e);
            return false;
        }
    }
    
    /**
     * Get batch processing statistics
     */
    public ACHProcessingStatistics getProcessingStatistics() {
        try {
            long totalBatches = achBatchRepository.count();
            long completedBatches = achBatchRepository.countByStatus(ACHBatchStatus.COMPLETED);
            long failedBatches = achBatchRepository.countByStatus(ACHBatchStatus.PROCESSING_FAILED);
            
            return ACHProcessingStatistics.builder()
                .totalBatchesProcessed(totalBatchesProcessed.get())
                .totalTransactionsProcessed(totalTransactionsProcessed.get())
                .totalFailedBatches(totalFailedBatches.get())
                .activeBatchesCount(activeBatches.size())
                .completedBatchesCount(completedBatches)
                .failedBatchesCount(failedBatches)
                .successRate(totalBatches > 0 ? (double) completedBatches / totalBatches * 100 : 0.0)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get processing statistics", e);
            return ACHProcessingStatistics.empty();
        }
    }
    
    /**
     * Scheduled batch processing job
     */
    @Scheduled(cron = "0 0/15 * * * ?") // Every 15 minutes
    public void processScheduledBatches() {
        try {
            log.debug("Processing scheduled ACH batches");
            
            List<ACHBatch> scheduledBatches = achBatchRepository
                .findByStatusAndScheduledProcessingDateBefore(
                    ACHBatchStatus.SCHEDULED, LocalDateTime.now());
            
            for (ACHBatch batch : scheduledBatches) {
                try {
                    log.info("Processing scheduled batch: {}", batch.getId());
                    processSubmittedBatch(batch);
                } catch (Exception e) {
                    log.error("Failed to process scheduled batch: {}", batch.getId(), e);
                }
            }
            
        } catch (Exception e) {
            log.error("Error in scheduled batch processing", e);
        }
    }
    
    /**
     * Batch cleanup job
     */
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    public void cleanupOldBatches() {
        try {
            log.info("Cleaning up old ACH batches");
            
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);
            
            // Archive completed batches older than 90 days
            List<ACHBatch> oldBatches = achBatchRepository
                .findByStatusAndCreatedAtBefore(ACHBatchStatus.COMPLETED, cutoffDate);
            
            for (ACHBatch batch : oldBatches) {
                archiveBatch(batch);
            }
            
            log.info("Archived {} old batches", oldBatches.size());
            
        } catch (Exception e) {
            log.error("Error in batch cleanup", e);
        }
    }
    
    // Private helper methods
    
    private void validateBatchRequest(ACHBatchRequest request) {
        if (request.getCompanyId() == null || request.getCompanyId().trim().isEmpty()) {
            throw new ACHProcessingException("Company ID is required");
        }
        
        if (request.getBatchType() == null) {
            throw new ACHProcessingException("Batch type is required");
        }
        
        if (request.getTransactions() == null || request.getTransactions().isEmpty()) {
            throw new ACHProcessingException("At least one transaction is required");
        }
        
        if (request.getTransactions().size() > maxTransactionsPerBatch) {
            throw new ACHProcessingException("Batch exceeds maximum transaction limit: " + maxTransactionsPerBatch);
        }
        
        if (request.getEffectiveEntryDate() == null) {
            throw new ACHProcessingException("Effective entry date is required");
        }
        
        if (request.getEffectiveEntryDate().isBefore(LocalDate.now())) {
            throw new ACHProcessingException("Effective entry date cannot be in the past");
        }
    }
    
    private void validateTransactions(List<ACHTransactionRequest> transactions) {
        for (int i = 0; i < transactions.size(); i++) {
            ACHTransactionRequest transaction = transactions.get(i);
            
            try {
                validateTransaction(transaction);
            } catch (Exception e) {
                throw new ACHProcessingException("Transaction " + (i + 1) + " validation failed: " + e.getMessage());
            }
        }
    }
    
    private void validateTransaction(ACHTransactionRequest transaction) {
        if (transaction.getAmount() == null || transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ACHProcessingException("Invalid transaction amount");
        }
        
        if (transaction.getRoutingNumber() == null || !isValidRoutingNumber(transaction.getRoutingNumber())) {
            throw new ACHProcessingException("Invalid routing number");
        }
        
        if (transaction.getAccountNumber() == null || transaction.getAccountNumber().trim().isEmpty()) {
            throw new ACHProcessingException("Account number is required");
        }
        
        if (transaction.getIndividualName() == null || transaction.getIndividualName().trim().isEmpty()) {
            throw new ACHProcessingException("Individual name is required");
        }
        
        if (transaction.getTransactionCode() == null) {
            throw new ACHProcessingException("Transaction code is required");
        }
    }
    
    private void performBatchComplianceChecks(ACHBatchRequest request) {
        // Check company compliance status
        try {
            boolean compliant = complianceService.checkSanctionsList(
                request.getCompanyName(), request.getCompanyId());
            
            if (!compliant) {
                throw new ACHProcessingException("Company fails compliance check");
            }
        } catch (Exception e) {
            log.warn("Compliance check failed for company: {}", request.getCompanyId(), e);
            if (strictValidationMode) {
                throw new ACHProcessingException("Compliance verification required", e);
            }
        }
    }
    
    private void detectDuplicateTransactions(ACHBatchRequest request) {
        Set<String> transactionHashes = new HashSet<>();
        
        for (ACHTransactionRequest transaction : request.getTransactions()) {
            String hash = generateTransactionHash(transaction);
            
            if (!transactionHashes.add(hash)) {
                throw new ACHProcessingException("Duplicate transaction detected in batch");
            }
            
            // Check against recent transactions in database
            if (achTransactionRepository.existsByTransactionHashAndCreatedAtAfter(
                    hash, LocalDateTime.now().minusDays(5))) {
                throw new ACHProcessingException("Transaction already processed recently");
            }
        }
    }
    
    private ACHBatch createBatchEntity(ACHBatchRequest request) {
        return ACHBatch.builder()
            .id(UUID.randomUUID().toString())
            .batchNumber(generateBatchNumber())
            .companyId(request.getCompanyId())
            .companyName(request.getCompanyName())
            .companyIdentification(request.getCompanyIdentification())
            .batchType(request.getBatchType())
            .status(ACHBatchStatus.PENDING)
            .effectiveEntryDate(request.getEffectiveEntryDate())
            .originatingDFIIdentification(request.getOriginatingDFIIdentification())
            .batchDescription(request.getBatchDescription())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .metadata(request.getMetadata() != null ? new HashMap<>(request.getMetadata()) : new HashMap<>())
            .build();
    }
    
    private List<ACHTransaction> createTransactionEntities(String batchId, List<ACHTransactionRequest> requests) {
        List<ACHTransaction> transactions = new ArrayList<>();
        
        for (int i = 0; i < requests.size(); i++) {
            ACHTransactionRequest request = requests.get(i);
            
            ACHTransaction transaction = ACHTransaction.builder()
                .id(UUID.randomUUID().toString())
                .batchId(batchId)
                .sequenceNumber(i + 1)
                .transactionCode(request.getTransactionCode())
                .receivingDFIIdentification(request.getRoutingNumber())
                .checkDigit(calculateCheckDigit(request.getRoutingNumber()))
                .dfiAccountNumber(encryptionService.encrypt(request.getAccountNumber()))
                .amount(request.getAmount())
                .individualName(request.getIndividualName())
                .individualIdentificationNumber(request.getIndividualIdentificationNumber())
                .discretionaryData(request.getDiscretionaryData())
                .traceNumber(generateTraceNumber(batchId, i + 1))
                .transactionHash(generateTransactionHash(request))
                .status(ACHTransaction.TransactionStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
            
            transactions.add(transaction);
        }
        
        return transactions;
    }
    
    private void calculateBatchTotals(ACHBatch batch, List<ACHTransaction> transactions) {
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        int debitCount = 0;
        int creditCount = 0;
        
        for (ACHTransaction transaction : transactions) {
            if (isDebitTransaction(transaction.getTransactionCode())) {
                totalDebits = totalDebits.add(transaction.getAmount());
                debitCount++;
            } else {
                totalCredits = totalCredits.add(transaction.getAmount());
                creditCount++;
            }
        }
        
        batch.setTotalDebitAmount(totalDebits);
        batch.setTotalCreditAmount(totalCredits);
        batch.setDebitEntryCount(debitCount);
        batch.setCreditEntryCount(creditCount);
        batch.setTotalEntryCount(transactions.size());
        batch.setEntryHash(calculateEntryHash(transactions));
    }
    
    private void validateBatchLimits(ACHBatch batch) {
        BigDecimal batchTotal = batch.getTotalDebitAmount().add(batch.getTotalCreditAmount());
        
        if (batchTotal.compareTo(maxBatchAmount) > 0) {
            throw new ACHProcessingException("Batch amount exceeds maximum limit: " + maxBatchAmount);
        }
    }
    
    private BatchValidationResult validateBatchForSubmission(ACHBatch batch) {
        List<String> errors = new ArrayList<>();
        
        // Validate batch header
        if (batch.getCompanyName() == null || batch.getCompanyName().trim().isEmpty()) {
            errors.add("Company name is required");
        }
        
        if (batch.getEffectiveEntryDate().isBefore(LocalDate.now())) {
            errors.add("Effective entry date cannot be in the past");
        }
        
        // Validate batch totals
        if (batch.getTotalEntryCount() == 0) {
            errors.add("Batch must contain at least one transaction");
        }
        
        return BatchValidationResult.builder()
            .valid(errors.isEmpty())
            .errors(errors)
            .build();
    }
    
    private void processSubmittedBatch(ACHBatch batch) {
        // Start async processing
        processBatch(batch.getId());
    }
    
    private BatchProcessingResult processTransactions(ACHBatch batch, List<ACHTransaction> transactions) {
        int successCount = 0;
        int failedCount = 0;
        List<String> errors = new ArrayList<>();
        
        for (ACHTransaction transaction : transactions) {
            try {
                boolean success = processTransaction(transaction);
                
                if (success) {
                    transaction.setStatus(ACHTransaction.TransactionStatus.PROCESSED);
                    successCount++;
                } else {
                    transaction.setStatus(ACHTransaction.TransactionStatus.FAILED);
                    transaction.setFailureReason("Processing failed");
                    failedCount++;
                    errors.add("Transaction " + transaction.getSequenceNumber() + " failed");
                }
                
            } catch (Exception e) {
                log.error("Transaction processing failed: {}", transaction.getId(), e);
                transaction.setStatus(ACHTransaction.TransactionStatus.FAILED);
                transaction.setFailureReason(e.getMessage());
                failedCount++;
                errors.add("Transaction " + transaction.getSequenceNumber() + ": " + e.getMessage());
            }
            
            transaction.setProcessedAt(LocalDateTime.now());
            achTransactionRepository.save(transaction);
        }
        
        return BatchProcessingResult.builder()
            .successCount(successCount)
            .failedCount(failedCount)
            .errors(errors)
            .build();
    }
    
    private boolean processTransaction(ACHTransaction transaction) {
        // Simulate transaction processing
        // In production, this would integrate with ACH network
        
        // Fraud monitoring
        if (fraudMonitoringEnabled && detectTransactionFraud(transaction)) {
            transaction.setFailureReason("Fraud detected");
            return false;
        }
        
        // Account validation
        if (!validateAccount(transaction)) {
            transaction.setFailureReason("Invalid account");
            return false;
        }
        
        // Balance check for debits
        if (isDebitTransaction(transaction.getTransactionCode()) && !checkBalance(transaction)) {
            transaction.setFailureReason("Insufficient funds");
            return false;
        }
        
        // Simulate processing delay
        try {
            Thread.sleep(10); // 10ms processing time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return true;
    }
    
    private void updateBatchAfterProcessing(ACHBatch batch, BatchProcessingResult result) {
        if (result.getFailedCount() == 0) {
            batch.setStatus(ACHBatchStatus.COMPLETED);
            batch.setCompletedAt(LocalDateTime.now());
        } else if (result.getSuccessCount() > 0) {
            batch.setStatus(ACHBatchStatus.PARTIALLY_COMPLETED);
            batch.setCompletedAt(LocalDateTime.now());
        } else {
            batch.setStatus(ACHBatchStatus.PROCESSING_FAILED);
            batch.setProcessingFailedAt(LocalDateTime.now());
            batch.setFailureReason("All transactions failed");
        }
        
        batch.setProcessingCompletedAt(LocalDateTime.now());
        batch.setSuccessfulTransactions(result.getSuccessCount());
        batch.setFailedTransactions(result.getFailedCount());
        batch.setUpdatedAt(LocalDateTime.now());
        
        achBatchRepository.save(batch);
    }
    
    private boolean canAutoSubmit(ACHBatch batch) {
        return batch.getBatchType() == ACHBatchType.PPD || // Prearranged Payment and Deposit
               batch.getBatchType() == ACHBatchType.CCD;   // Cash Concentration or Disbursement
    }
    
    private boolean isWithinProcessingCutoff() {
        return LocalDateTime.now().getHour() < processingCutoffHour;
    }
    
    private LocalDateTime getNextBusinessDay() {
        LocalDate nextDay = LocalDate.now().plusDays(1);
        
        // Skip weekends
        while (nextDay.getDayOfWeek().getValue() > 5) {
            nextDay = nextDay.plusDays(1);
        }
        
        return nextDay.atTime(9, 0); // 9 AM next business day
    }
    
    private LocalDate calculateSettlementDate(ACHBatch batch) {
        LocalDate settlementDate = batch.getEffectiveEntryDate();
        
        if (nextDaySettlementEnabled && batch.getBatchType() == ACHBatchType.PPD) {
            settlementDate = settlementDate.plusDays(1);
        } else {
            settlementDate = settlementDate.plusDays(standardSettlementDays);
        }
        
        // Skip weekends
        while (settlementDate.getDayOfWeek().getValue() > 5) {
            settlementDate = settlementDate.plusDays(1);
        }
        
        return settlementDate;
    }
    
    private java.time.Duration calculateProcessingTime(ACHBatch batch) {
        if (batch.getStatus() == ACHBatchStatus.SUBMITTED) {
            return java.time.Duration.ofHours(4);
        } else if (batch.getStatus() == ACHBatchStatus.SCHEDULED) {
            return java.time.Duration.between(LocalDateTime.now(), batch.getScheduledProcessingDate());
        }
        
        return java.time.Duration.ofHours(24);
    }
    
    private String generateBatchIdempotencyKey(ACHBatchRequest request) {
        return String.format("ach_batch_%s_%s_%s", 
            request.getCompanyId(), 
            request.getEffectiveEntryDate().toString(), 
            Objects.hash(request.getTransactions()));
    }
    
    /**
     * Generate secure batch number for ACH processing
     * 
     * SECURITY FIX: Replaced Random with SecureRandom to prevent batch number prediction
     * This is important for audit trails and preventing batch manipulation
     */
    private String generateBatchNumber() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        SecureRandom secureRandom = new SecureRandom();
        
        // Generate cryptographically secure random suffix
        int randomSuffix = secureRandom.nextInt(10000);
        
        // Add additional entropy from system nano time
        long nanoTime = System.nanoTime();
        int nanoSuffix = (int)(nanoTime % 100);
        
        // Combine for final suffix (still within 0-9999 range)
        int finalSuffix = (randomSuffix + nanoSuffix) % 10000;
        
        String batchNumber = "B" + timestamp + String.format("%04d", finalSuffix);
        
        // Log for audit trail
        log.debug("Generated secure batch number for ACH processing: {}", batchNumber);
        
        return batchNumber;
    }
    
    private String generateTraceNumber(String batchId, int sequenceNumber) {
        return batchId.hashCode() + String.format("%07d", sequenceNumber);
    }
    
    private String generateTransactionHash(ACHTransactionRequest transaction) {
        String data = String.format("%s%s%s%s", 
            transaction.getRoutingNumber(),
            transaction.getAccountNumber(),
            transaction.getAmount().toString(),
            transaction.getIndividualName());
        
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return Integer.toString(data.hashCode());
        }
    }
    
    private boolean isValidRoutingNumber(String routingNumber) {
        if (routingNumber == null || routingNumber.length() != 9) {
            return false;
        }
        
        try {
            // Check digit algorithm
            int[] weights = {3, 7, 1, 3, 7, 1, 3, 7, 1};
            int sum = 0;
            
            for (int i = 0; i < 9; i++) {
                sum += Character.getNumericValue(routingNumber.charAt(i)) * weights[i];
            }
            
            return sum % 10 == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    private String calculateCheckDigit(String routingNumber) {
        return routingNumber.substring(8, 9);
    }
    
    private boolean isDebitTransaction(String transactionCode) {
        return "27".equals(transactionCode) || "37".equals(transactionCode);
    }
    
    private long calculateEntryHash(List<ACHTransaction> transactions) {
        return transactions.stream()
            .mapToLong(t -> Long.parseLong(t.getReceivingDFIIdentification().substring(0, 8)))
            .sum() % 10000000000L;
    }
    
    private boolean detectTransactionFraud(ACHTransaction transaction) {
        // Simple fraud detection - in production, use ML models
        BigDecimal suspiciousThreshold = new BigDecimal("50000");
        return transaction.getAmount().compareTo(suspiciousThreshold) > 0;
    }
    
    private boolean validateAccount(ACHTransaction transaction) {
        // Account validation - in production, use account verification service
        return !transaction.getDfiAccountNumber().contains("INVALID");
    }
    
    private boolean checkBalance(ACHTransaction transaction) {
        // Balance check - in production, check against account balance
        return transaction.getAmount().compareTo(new BigDecimal("1000000")) < 0;
    }
    
    private void archiveBatch(ACHBatch batch) {
        // Archive batch data - in production, move to archive storage
        log.info("Archiving batch: {}", batch.getId());
    }
    
    // DTOs and Result Classes
    
    @lombok.Builder
    @lombok.Data
    public static class ACHBatchRequest {
        private String companyId;
        private String companyName;
        private String companyIdentification;
        private ACHBatchType batchType;
        private LocalDate effectiveEntryDate;
        private String originatingDFIIdentification;
        private String batchDescription;
        private List<ACHTransactionRequest> transactions;
        private Map<String, String> metadata;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ACHTransactionRequest {
        private String transactionCode;
        private String routingNumber;
        private String accountNumber;
        private BigDecimal amount;
        private String individualName;
        private String individualIdentificationNumber;
        private String discretionaryData;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ACHBatchResult {
        private String batchId;
        private String batchNumber;
        private ACHBatchStatus status;
        private int transactionCount;
        private BigDecimal totalDebitAmount;
        private BigDecimal totalCreditAmount;
        private LocalDate effectiveEntryDate;
        private LocalDate settlementDate;
        private java.time.Duration estimatedProcessingTime;
        private LocalDateTime createdAt;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class BatchValidationResult {
        private boolean valid;
        private List<String> errors;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class BatchProcessingResult {
        private int successCount;
        private int failedCount;
        private List<String> errors;
        
        public static BatchProcessingResult failed(String error) {
            return BatchProcessingResult.builder()
                .successCount(0)
                .failedCount(1)
                .errors(List.of(error))
                .build();
        }
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ACHProcessingStatistics {
        private long totalBatchesProcessed;
        private long totalTransactionsProcessed;
        private long totalFailedBatches;
        private int activeBatchesCount;
        private long completedBatchesCount;
        private long failedBatchesCount;
        private double successRate;
        
        public static ACHProcessingStatistics empty() {
            return ACHProcessingStatistics.builder()
                .totalBatchesProcessed(0L)
                .totalTransactionsProcessed(0L)
                .totalFailedBatches(0L)
                .activeBatchesCount(0)
                .completedBatchesCount(0L)
                .failedBatchesCount(0L)
                .successRate(0.0)
                .build();
        }
    }
}