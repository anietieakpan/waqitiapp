package com.waqiti.ledger.service;

import com.waqiti.ledger.domain.JournalEntry;
import com.waqiti.ledger.domain.LedgerEntry;
import com.waqiti.ledger.domain.AccountingPeriod;
import com.waqiti.ledger.dto.*;
import com.waqiti.ledger.repository.JournalEntryRepository;
import com.waqiti.ledger.repository.LedgerEntryRepository;
import com.waqiti.ledger.repository.AccountingPeriodRepository;
import com.waqiti.ledger.exception.LedgerServiceException;
import com.waqiti.ledger.exception.DoubleEntryValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Comprehensive Ledger Service Implementation
 * 
 * Implements all journal entry operations, audit trails, and
 * integration with the double-entry ledger system.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerServiceImpl implements LedgerService {

    private final JournalEntryRepository journalEntryRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final DoubleEntryLedgerService doubleEntryLedgerService;
    private final ChartOfAccountsService chartOfAccountsService;
    private final AccountResolutionService accountResolutionService; // P2 QUICK WIN: For wallet currency resolution

    /**
     * Creates a new journal entry with validation
     */
    @Transactional
    public JournalEntryResponse createJournalEntry(CreateJournalEntryRequest request) {
        try {
            log.info("Creating journal entry: {}", request.getDescription());
            
            // Validate request
            validateJournalEntryRequest(request);
            
            // Determine accounting period
            AccountingPeriod accountingPeriod = determineAccountingPeriod(request.getEntryDate());
            
            // Create journal entry
            JournalEntry journalEntry = JournalEntry.builder()
                .entryNumber(generateEntryNumber())
                .referenceNumber(request.getReferenceNumber())
                .entryType(JournalEntry.EntryType.valueOf(request.getEntryType()))
                .description(request.getDescription())
                .entryDate(request.getEntryDate())
                .effectiveDate(request.getEffectiveDate())
                .status(JournalEntry.JournalStatus.DRAFT)
                .currency(request.getCurrency())
                .accountingPeriodId(accountingPeriod != null ? accountingPeriod.getPeriodId() : null)
                .sourceSystem(request.getSourceSystem())
                .sourceDocumentId(request.getSourceDocumentId())
                .sourceDocumentType(request.getSourceDocumentType())
                .approvalRequired(request.getApprovalRequired())
                .metadata(request.getMetadata())
                .createdBy(request.getCreatedBy())
                .build();
            
            // Create ledger entries
            List<LedgerEntry> ledgerEntries = createLedgerEntries(request.getLedgerEntries(), journalEntry);
            
            // Calculate totals
            journalEntry.setLedgerEntries(ledgerEntries);
            journalEntry.calculateTotals();
            
            // Validate double-entry balance
            if (!journalEntry.isBalanced()) {
                throw new DoubleEntryValidationException("Journal entry is not balanced: debits=" + 
                    journalEntry.getTotalDebits() + ", credits=" + journalEntry.getTotalCredits());
            }
            
            // Save journal entry
            JournalEntry savedJournalEntry = journalEntryRepository.save(journalEntry);
            
            // Save ledger entries
            ledgerEntries.forEach(entry -> entry.setJournalEntryId(savedJournalEntry.getJournalEntryId()));
            ledgerEntryRepository.saveAll(ledgerEntries);
            
            // Auto-approve if not required
            if (!savedJournalEntry.requiresApproval()) {
                savedJournalEntry.approve(request.getCreatedBy(), "Auto-approved");
                journalEntryRepository.save(savedJournalEntry);
            }
            
            log.info("Successfully created journal entry: {}", savedJournalEntry.getEntryNumber());
            
            return mapToJournalEntryResponse(savedJournalEntry);
            
        } catch (DoubleEntryValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create journal entry", e);
            throw new LedgerServiceException("Failed to create journal entry", e);
        }
    }

    /**
     * Creates multiple journal entries in batch
     */
    @Transactional
    public BatchJournalEntryResponse createBatchJournalEntries(BatchJournalEntryRequest request) {
        try {
            log.info("Creating batch journal entries: {} entries", request.getEntries().size());
            
            List<JournalEntryResponse> successfulEntries = new ArrayList<>();
            List<BatchEntryError> errors = new ArrayList<>();
            
            for (int i = 0; i < request.getEntries().size(); i++) {
                CreateJournalEntryRequest entryRequest = request.getEntries().get(i);
                try {
                    JournalEntryResponse response = createJournalEntry(entryRequest);
                    successfulEntries.add(response);
                } catch (Exception e) {
                    errors.add(BatchEntryError.builder()
                        .entryIndex(i)
                        .referenceNumber(entryRequest.getReferenceNumber())
                        .errorMessage(e.getMessage())
                        .build());
                }
            }
            
            return BatchJournalEntryResponse.builder()
                .totalRequested(request.getEntries().size())
                .successfulCount(successfulEntries.size())
                .errorCount(errors.size())
                .successfulEntries(successfulEntries)
                .errors(errors)
                .batchId(UUID.randomUUID())
                .processedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to create batch journal entries", e);
            throw new LedgerServiceException("Failed to create batch journal entries", e);
        }
    }

    /**
     * Gets journal entries with filtering
     */
    public Page<JournalEntryResponse> getJournalEntries(JournalEntryFilter filter, Pageable pageable) {
        try {
            LocalDateTime startDate = filter.getStartDate() != null ? 
                filter.getStartDate().atStartOfDay() : null;
            LocalDateTime endDate = filter.getEndDate() != null ? 
                filter.getEndDate().atTime(23, 59, 59) : null;
            
            JournalEntry.EntryType entryType = filter.getEntryType() != null ? 
                JournalEntry.EntryType.valueOf(filter.getEntryType()) : null;
            JournalEntry.JournalStatus status = filter.getStatus() != null ? 
                JournalEntry.JournalStatus.valueOf(filter.getStatus()) : null;
            
            Page<JournalEntry> entries = journalEntryRepository.searchJournalEntries(
                entryType, status, startDate, endDate, filter.getReference(), 
                filter.getDescription(), pageable);
            
            List<JournalEntryResponse> responses = entries.getContent().stream()
                .map(this::mapToJournalEntryResponse)
                .collect(Collectors.toList());
            
            return new PageImpl<>(responses, pageable, entries.getTotalElements());
            
        } catch (Exception e) {
            log.error("Failed to get journal entries", e);
            throw new LedgerServiceException("Failed to retrieve journal entries", e);
        }
    }

    /**
     * Gets detailed journal entry information
     */
    public JournalEntryDetailResponse getJournalEntryDetails(UUID entryId) {
        try {
            JournalEntry journalEntry = journalEntryRepository.findById(entryId)
                .orElseThrow(() -> new LedgerServiceException("Journal entry not found: " + entryId));
            
            List<LedgerEntry> ledgerEntries = ledgerEntryRepository.findByJournalEntryIdOrderByCreatedAtAsc(entryId);
            
            return JournalEntryDetailResponse.builder()
                .journalEntry(mapToJournalEntryResponse(journalEntry))
                .ledgerEntries(ledgerEntries.stream()
                    .map(this::mapToLedgerEntryResponse)
                    .collect(Collectors.toList()))
                .auditTrail(getJournalEntryAuditTrail(entryId))
                .relatedEntries(getRelatedEntries(journalEntry))
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get journal entry details: {}", entryId, e);
            throw new LedgerServiceException("Failed to retrieve journal entry details", e);
        }
    }

    /**
     * Reverses a journal entry
     */
    @Transactional
    public JournalEntryResponse reverseJournalEntry(UUID entryId, ReverseJournalEntryRequest request) {
        try {
            log.info("Reversing journal entry: {}", entryId);
            
            JournalEntry originalEntry = journalEntryRepository.findById(entryId)
                .orElseThrow(() -> new LedgerServiceException("Journal entry not found: " + entryId));
            
            if (!originalEntry.canBeReversed()) {
                throw new LedgerServiceException("Journal entry cannot be reversed: " + entryId);
            }
            
            // Create reversal entry
            JournalEntry reversalEntry = createReversalEntry(originalEntry, request);
            JournalEntry savedReversalEntry = journalEntryRepository.save(reversalEntry);
            
            // Create reverse ledger entries
            List<LedgerEntry> originalLedgerEntries = ledgerEntryRepository.findByJournalEntryIdOrderByCreatedAtAsc(entryId);
            List<LedgerEntry> reversalLedgerEntries = createReversalLedgerEntries(originalLedgerEntries, savedReversalEntry);
            ledgerEntryRepository.saveAll(reversalLedgerEntries);
            
            // Mark original entry as reversed
            originalEntry.markAsReversed(request.getReversedBy(), request.getReversalReason());
            journalEntryRepository.save(originalEntry);
            
            // Auto-approve reversal if original didn't require approval
            if (!savedReversalEntry.requiresApproval()) {
                savedReversalEntry.approve(request.getReversedBy(), "Auto-approved reversal");
                journalEntryRepository.save(savedReversalEntry);
            }
            
            log.info("Successfully reversed journal entry: {}", entryId);
            
            return mapToJournalEntryResponse(savedReversalEntry);
            
        } catch (Exception e) {
            log.error("Failed to reverse journal entry: {}", entryId, e);
            throw new LedgerServiceException("Failed to reverse journal entry", e);
        }
    }

    /**
     * Gets audit trail for ledger operations
     */
    public Page<LedgerAuditTrailResponse> getAuditTrail(String entityType, String entityId, 
                                                       LocalDate startDate, LocalDate endDate, 
                                                       Pageable pageable) {
        try {
            log.debug("Retrieving audit trail for entity: {} with ID: {} from {} to {}", 
                entityType, entityId, startDate, endDate);
            
            List<AuditTrailEntry> auditEntries = new ArrayList<>();
            
            // Get journal entry audit events
            if ("JOURNAL_ENTRY".equalsIgnoreCase(entityType)) {
                UUID journalEntryId = UUID.fromString(entityId);
                Optional<JournalEntry> journalEntryOpt = journalEntryRepository.findById(journalEntryId);
                
                if (journalEntryOpt.isPresent()) {
                    JournalEntry journalEntry = journalEntryOpt.get();
                    
                    // Creation event
                    auditEntries.add(AuditTrailEntry.builder()
                        .eventId(UUID.randomUUID())
                        .entityType("JOURNAL_ENTRY")
                        .entityId(journalEntryId.toString())
                        .eventType("CREATED")
                        .eventDescription("Journal entry created: " + journalEntry.getDescription())
                        .performedBy(journalEntry.getCreatedBy())
                        .performedAt(journalEntry.getCreatedAt())
                        .beforeState(null)
                        .afterState(journalEntry.getEntryNumber())
                        .ipAddress("system")
                        .userAgent("ledger-service")
                        .build());
                    
                    // Approval events
                    if (journalEntry.getApprovedAt() != null) {
                        auditEntries.add(AuditTrailEntry.builder()
                            .eventId(UUID.randomUUID())
                            .entityType("JOURNAL_ENTRY")
                            .entityId(journalEntryId.toString())
                            .eventType("APPROVED")
                            .eventDescription("Journal entry approved: " + journalEntry.getApprovalNotes())
                            .performedBy(journalEntry.getApprovedBy())
                            .performedAt(journalEntry.getApprovedAt())
                            .beforeState("DRAFT")
                            .afterState("APPROVED")
                            .ipAddress("system")
                            .userAgent("ledger-service")
                            .build());
                    }
                    
                    // Posting events
                    if (journalEntry.getPostedAt() != null) {
                        auditEntries.add(AuditTrailEntry.builder()
                            .eventId(UUID.randomUUID())
                            .entityType("JOURNAL_ENTRY")
                            .entityId(journalEntryId.toString())
                            .eventType("POSTED")
                            .eventDescription("Journal entry posted to ledger")
                            .performedBy(journalEntry.getPostedBy())
                            .performedAt(journalEntry.getPostedAt())
                            .beforeState("APPROVED")
                            .afterState("POSTED")
                            .ipAddress("system")
                            .userAgent("ledger-service")
                            .build());
                    }
                    
                    // Reversal events
                    if (journalEntry.getReversedAt() != null) {
                        auditEntries.add(AuditTrailEntry.builder()
                            .eventId(UUID.randomUUID())
                            .entityType("JOURNAL_ENTRY")
                            .entityId(journalEntryId.toString())
                            .eventType("REVERSED")
                            .eventDescription("Journal entry reversed: " + journalEntry.getReversalReason())
                            .performedBy(journalEntry.getReversedBy())
                            .performedAt(journalEntry.getReversedAt())
                            .beforeState("POSTED")
                            .afterState("REVERSED")
                            .ipAddress("system")
                            .userAgent("ledger-service")
                            .build());
                    }
                }
            }
            
            // Get ledger entry audit events
            if ("LEDGER_ENTRY".equalsIgnoreCase(entityType)) {
                UUID ledgerEntryId = UUID.fromString(entityId);
                Optional<LedgerEntry> ledgerEntryOpt = ledgerEntryRepository.findById(ledgerEntryId);
                
                if (ledgerEntryOpt.isPresent()) {
                    LedgerEntry ledgerEntry = ledgerEntryOpt.get();
                    
                    auditEntries.add(AuditTrailEntry.builder()
                        .eventId(UUID.randomUUID())
                        .entityType("LEDGER_ENTRY")
                        .entityId(ledgerEntryId.toString())
                        .eventType("CREATED")
                        .eventDescription("Ledger entry created: " + ledgerEntry.getDescription())
                        .performedBy("system")
                        .performedAt(ledgerEntry.getCreatedAt())
                        .beforeState(null)
                        .afterState(ledgerEntry.getStatus().toString())
                        .ipAddress("system")
                        .userAgent("ledger-service")
                        .build());
                }
            }
            
            // Filter by date range
            if (startDate != null || endDate != null) {
                auditEntries = auditEntries.stream()
                    .filter(entry -> {
                        LocalDate entryDate = entry.getPerformedAt().toLocalDate();
                        boolean afterStart = startDate == null || !entryDate.isBefore(startDate);
                        boolean beforeEnd = endDate == null || !entryDate.isAfter(endDate);
                        return afterStart && beforeEnd;
                    })
                    .collect(Collectors.toList());
            }
            
            // Sort by performed date descending
            auditEntries.sort((a, b) -> b.getPerformedAt().compareTo(a.getPerformedAt()));
            
            // Apply pagination
            int start = Math.min(pageable.getPageNumber() * pageable.getPageSize(), auditEntries.size());
            int end = Math.min(start + pageable.getPageSize(), auditEntries.size());
            List<AuditTrailEntry> pageContent = auditEntries.subList(start, end);
            
            // Map to response objects
            List<LedgerAuditTrailResponse> responses = pageContent.stream()
                .map(this::mapToAuditTrailResponse)
                .collect(Collectors.toList());
            
            return new PageImpl<>(responses, pageable, auditEntries.size());
            
        } catch (Exception e) {
            log.error("Failed to get audit trail", e);
            throw new LedgerServiceException("Failed to retrieve audit trail", e);
        }
    }

    // Private helper methods

    private void validateJournalEntryRequest(CreateJournalEntryRequest request) {
        if (request.getLedgerEntries() == null || request.getLedgerEntries().isEmpty()) {
            throw new LedgerServiceException("At least one ledger entry is required");
        }
        
        // Validate all accounts exist
        for (CreateLedgerEntryRequest entry : request.getLedgerEntries()) {
            chartOfAccountsService.validateAccountForTransaction(entry.getAccountId());
        }
        
        // Validate amounts balance
        BigDecimal totalDebits = request.getLedgerEntries().stream()
            .filter(entry -> "DEBIT".equals(entry.getEntryType()))
            .map(CreateLedgerEntryRequest::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        BigDecimal totalCredits = request.getLedgerEntries().stream()
            .filter(entry -> "CREDIT".equals(entry.getEntryType()))
            .map(CreateLedgerEntryRequest::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new DoubleEntryValidationException(
                "Journal entry is not balanced: debits=" + totalDebits + ", credits=" + totalCredits);
        }
    }

    private AccountingPeriod determineAccountingPeriod(LocalDateTime entryDate) {
        return accountingPeriodRepository.findPeriodContainingDate(entryDate.toLocalDate())
            .orElse(null);
    }

    private String generateEntryNumber() {
        Integer nextNumber = journalEntryRepository.getNextEntryNumber();
        return String.format("JE%06d", nextNumber);
    }

    private List<LedgerEntry> createLedgerEntries(List<CreateLedgerEntryRequest> requests, JournalEntry journalEntry) {
        return requests.stream()
            .map(request -> LedgerEntry.builder()
                .transactionId(journalEntry.getJournalEntryId().toString())
                .accountId(request.getAccountId().toString())
                .journalEntryId(journalEntry.getJournalEntryId())
                .entryType(LedgerEntry.EntryType.valueOf(request.getEntryType()))
                .amount(request.getAmount())
                .description(request.getDescription())
                .narrative(request.getNarrative())
                .currency(request.getCurrency())
                .entryDate(journalEntry.getEntryDate().toInstant())
                .status(LedgerEntry.LedgerStatus.POSTED)
                .contraAccountId(request.getContraAccountId() != null ? request.getContraAccountId().toString() : null)
                .metadata(request.getMetadata())
                .build())
            .collect(Collectors.toList());
    }

    private JournalEntry createReversalEntry(JournalEntry originalEntry, ReverseJournalEntryRequest request) {
        return JournalEntry.builder()
            .entryNumber(generateEntryNumber())
            .referenceNumber("REV-" + originalEntry.getReferenceNumber())
            .entryType(JournalEntry.EntryType.REVERSAL)
            .description("Reversal of " + originalEntry.getDescription())
            .entryDate(LocalDateTime.now())
            .effectiveDate(LocalDateTime.now())
            .status(JournalEntry.JournalStatus.DRAFT)
            .totalDebits(originalEntry.getTotalCredits()) // Flip debits and credits
            .totalCredits(originalEntry.getTotalDebits())
            .currency(originalEntry.getCurrency())
            .accountingPeriodId(originalEntry.getAccountingPeriodId())
            .sourceSystem(originalEntry.getSourceSystem())
            .originalJournalEntryId(originalEntry.getJournalEntryId())
            .approvalRequired(originalEntry.getApprovalRequired())
            .createdBy(request.getReversedBy())
            .build();
    }

    private List<LedgerEntry> createReversalLedgerEntries(List<LedgerEntry> originalEntries, JournalEntry reversalEntry) {
        return originalEntries.stream()
            .map(original -> LedgerEntry.builder()
                .transactionId(reversalEntry.getJournalEntryId().toString())
                .accountId(original.getAccountId())
                .journalEntryId(reversalEntry.getJournalEntryId())
                .entryType(original.getEntryType() == LedgerEntry.EntryType.DEBIT ? 
                    LedgerEntry.EntryType.CREDIT : LedgerEntry.EntryType.DEBIT) // Flip type
                .amount(original.getAmount())
                .description("Reversal of " + original.getDescription())
                .narrative(original.getNarrative())
                .currency(original.getCurrency())
                .entryDate(reversalEntry.getEntryDate().toInstant())
                .status(LedgerEntry.LedgerStatus.POSTED)
                .contraAccountId(original.getContraAccountId())
                .originalEntryId(original.getId())
                .build())
            .collect(Collectors.toList());
    }

    private JournalEntryResponse mapToJournalEntryResponse(JournalEntry journalEntry) {
        return JournalEntryResponse.builder()
            .journalEntryId(journalEntry.getJournalEntryId())
            .entryNumber(journalEntry.getEntryNumber())
            .referenceNumber(journalEntry.getReferenceNumber())
            .entryType(journalEntry.getEntryType().toString())
            .description(journalEntry.getDescription())
            .entryDate(journalEntry.getEntryDate())
            .effectiveDate(journalEntry.getEffectiveDate())
            .status(journalEntry.getStatus().toString())
            .totalDebits(journalEntry.getTotalDebits())
            .totalCredits(journalEntry.getTotalCredits())
            .currency(journalEntry.getCurrency())
            .accountingPeriodId(journalEntry.getAccountingPeriodId())
            .sourceSystem(journalEntry.getSourceSystem())
            .sourceDocumentId(journalEntry.getSourceDocumentId())
            .sourceDocumentType(journalEntry.getSourceDocumentType())
            .postedAt(journalEntry.getPostedAt())
            .postedBy(journalEntry.getPostedBy())
            .reversedAt(journalEntry.getReversedAt())
            .reversedBy(journalEntry.getReversedBy())
            .reversalReason(journalEntry.getReversalReason())
            .originalJournalEntryId(journalEntry.getOriginalJournalEntryId())
            .approvalRequired(journalEntry.getApprovalRequired())
            .approvedAt(journalEntry.getApprovedAt())
            .approvedBy(journalEntry.getApprovedBy())
            .approvalNotes(journalEntry.getApprovalNotes())
            .metadata(journalEntry.getMetadata())
            .createdAt(journalEntry.getCreatedAt())
            .lastUpdated(journalEntry.getLastUpdated())
            .createdBy(journalEntry.getCreatedBy())
            .updatedBy(journalEntry.getUpdatedBy())
            .balanced(journalEntry.isBalanced())
            .canBePosted(journalEntry.canBePosted())
            .canBeReversed(journalEntry.canBeReversed())
            .requiresApproval(journalEntry.requiresApproval())
            .isReversalEntry(journalEntry.isReversalEntry())
            .isPeriodEndEntry(journalEntry.isPeriodEndEntry())
            .isSystemGenerated(journalEntry.isSystemGenerated())
            .build();
    }

    private LedgerEntryResponse mapToLedgerEntryResponse(LedgerEntry entry) {
        return LedgerEntryResponse.builder()
            .ledgerId(entry.getId())
            .transactionId(UUID.fromString(entry.getTransactionId()))
            .accountId(UUID.fromString(entry.getAccountId()))
            .entryType(entry.getEntryType().toString())
            .amount(entry.getAmount())
            .runningBalance(entry.getRunningBalance())
            .referenceNumber(entry.getReferenceId())
            .description(entry.getDescription())
            .narrative(entry.getNarrative())
            .currency(entry.getCurrency())
            .transactionDate(LocalDateTime.ofInstant(entry.getEntryDate(), java.time.ZoneId.systemDefault()))
            .valueDate(LocalDateTime.ofInstant(entry.getValueDate(), java.time.ZoneId.systemDefault()))
            .contraAccountId(entry.getContraAccountId() != null ? UUID.fromString(entry.getContraAccountId()) : null)
            .status(entry.getStatus().toString())
            .metadata(entry.getMetadata())
            .createdAt(LocalDateTime.ofInstant(entry.getCreatedAt(), java.time.ZoneId.systemDefault()))
            .build();
    }

    private List<AuditTrailEntry> getJournalEntryAuditTrail(UUID entryId) {
        // Implementation would integrate with audit system
        return new ArrayList<>();
    }

    private List<RelatedJournalEntry> getRelatedEntries(JournalEntry journalEntry) {
        List<RelatedJournalEntry> relatedEntries = new ArrayList<>();
        
        // Add original entry if this is a reversal
        if (journalEntry.getOriginalJournalEntryId() != null) {
            journalEntryRepository.findById(journalEntry.getOriginalJournalEntryId())
                .ifPresent(original -> relatedEntries.add(RelatedJournalEntry.builder()
                    .journalEntryId(original.getJournalEntryId())
                    .entryNumber(original.getEntryNumber())
                    .relationship("ORIGINAL")
                    .description(original.getDescription())
                    .build()));
        }
        
        // Add reversal entries
        List<JournalEntry> reversals = journalEntryRepository.findByOriginalJournalEntryIdOrderByCreatedAtDesc(
            journalEntry.getJournalEntryId());
        reversals.forEach(reversal -> relatedEntries.add(RelatedJournalEntry.builder()
            .journalEntryId(reversal.getJournalEntryId())
            .entryNumber(reversal.getEntryNumber())
            .relationship("REVERSAL")
            .description(reversal.getDescription())
            .build()));
        
        return relatedEntries;
    }
    
    private LedgerAuditTrailResponse mapToAuditTrailResponse(AuditTrailEntry entry) {
        return LedgerAuditTrailResponse.builder()
            .eventId(entry.getEventId())
            .entityType(entry.getEntityType())
            .entityId(entry.getEntityId())
            .eventType(entry.getEventType())
            .eventDescription(entry.getEventDescription())
            .performedBy(entry.getPerformedBy())
            .performedAt(entry.getPerformedAt())
            .beforeState(entry.getBeforeState())
            .afterState(entry.getAfterState())
            .ipAddress(entry.getIpAddress())
            .userAgent(entry.getUserAgent())
            .build();
    }

    /**
     * P1-2 CRITICAL FIX: Get wallet balance from ledger for reconciliation
     *
     * Calculates the authoritative wallet balance by summing all ledger entries
     * for the wallet's liability account. This is the "source of truth" balance
     * used for reconciliation against wallet-service's cached balance.
     *
     * IMPLEMENTATION:
     * 1. Resolve wallet to its ledger liability account via WalletAccountMapping
     * 2. Sum all CREDIT entries (increases) and DEBIT entries (decreases)
     * 3. Return net balance = credits - debits
     * 4. Include metadata (entry count, last transaction time, etc.)
     *
     * @param walletId Wallet UUID
     * @return Wallet balance response with ledger-calculated balance
     */
    @Transactional(readOnly = true)
    @Override
    public WalletBalanceResponse getWalletBalance(UUID walletId) {
        log.debug("P1-2: Calculating wallet balance from ledger: walletId={}", walletId);

        try {
            // Step 1: Resolve wallet to ledger account and get currency
            // Uses AccountResolutionService created in P0-2
            UUID ledgerAccountId = chartOfAccountsService.resolveWalletLiabilityAccount(walletId);

            // P2 QUICK WIN: Get currency from wallet account mapping
            String currency = accountResolutionService.getWalletCurrency(walletId);
            if (currency == null) {
                currency = "USD"; // Fallback to USD if mapping doesn't specify
                log.debug("P2: No currency mapping found for wallet: {}, defaulting to USD", walletId);
            }

            if (ledgerAccountId == null) {
                log.warn("P1-2: No ledger account found for wallet: {} - returning zero balance", walletId);
                return WalletBalanceResponse.builder()
                    .walletId(walletId)
                    .balance("0.00")
                    .currency(currency)
                    .ledgerAccountId(null)
                    .entryCount(0L)
                    .lastTransactionAt(null)
                    .calculatedAt(LocalDateTime.now())
                    .hasPendingTransactions(false)
                    .build();
            }

            // Step 2: Get all ledger entries for this account
            List<LedgerEntry> entries = ledgerEntryRepository.findByAccountId(ledgerAccountId);

            // Step 3: Calculate balance (CREDIT - DEBIT for liability account)
            // CRITICAL FIX: Compare EntryType ENUM, not String!
            BigDecimal totalCredits = entries.stream()
                .filter(entry -> LedgerEntry.EntryType.CREDIT.equals(entry.getEntryType()))
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalDebits = entries.stream()
                .filter(entry -> LedgerEntry.EntryType.DEBIT.equals(entry.getEntryType()))
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal balance = totalCredits.subtract(totalDebits);

            log.debug("Balance calculation: totalCredits={}, totalDebits={}, balance={}",
                totalCredits, totalDebits, balance);

            // Step 4: Get metadata
            LocalDateTime lastTransactionAt = entries.stream()
                .map(LedgerEntry::getTransactionDate)
                .max(Comparator.naturalOrder())
                .orElse(null);

            // Check for pending transactions (draft journal entries)
            boolean hasPending = journalEntryRepository.existsPendingForAccount(ledgerAccountId);

            // Step 5: Build response
            WalletBalanceResponse response = WalletBalanceResponse.builder()
                .walletId(walletId)
                .balance(balance.toPlainString())
                .currency(currency) // P2 QUICK WIN: Dynamic currency from wallet account mapping
                .ledgerAccountId(ledgerAccountId)
                .entryCount((long) entries.size())
                .lastTransactionAt(lastTransactionAt)
                .calculatedAt(LocalDateTime.now())
                .hasPendingTransactions(hasPending)
                .build();

            log.debug("P1-2: Wallet balance calculated from ledger: walletId={}, balance={}, entries={}",
                walletId, balance, entries.size());

            return response;

        } catch (Exception e) {
            log.error("P1-2: Failed to calculate wallet balance from ledger: walletId={}", walletId, e);
            throw new LedgerServiceException("Failed to calculate wallet balance: " + walletId, e);
        }
    }
}