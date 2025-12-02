package com.waqiti.ledger.controller;

import com.waqiti.ledger.dto.*;
import com.waqiti.ledger.service.DoubleEntryLedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Transaction Controller
 * 
 * REST API endpoints for transaction management and journal entries.
 */
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Transactions", description = "Transaction and journal entry management")
public class TransactionController {

    private final DoubleEntryLedgerService ledgerService;

    @PostMapping
    @Operation(summary = "Create transaction", description = "Creates a new double-entry transaction")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Transaction created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid transaction - unbalanced or invalid accounts"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN')")
    public ResponseEntity<CreateTransactionResponse> createTransaction(
            @Valid @RequestBody CreateTransactionRequest request) {
        
        log.info("Creating transaction: {}", request.getDescription());
        CreateTransactionResponse response = ledgerService.createTransaction(request);
        
        if (response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/journal-entry")
    @Operation(summary = "Post journal entry", description = "Posts a manual journal entry")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Journal entry posted successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid journal entry"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN')")
    public ResponseEntity<PostJournalEntryResponse> postJournalEntry(
            @Valid @RequestBody PostJournalEntryRequest request) {
        
        log.info("Posting journal entry: {}", request.getDescription());
        PostJournalEntryResponse response = ledgerService.postJournalEntry(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get transaction", description = "Retrieves a specific transaction by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transaction found"),
        @ApiResponse(responseCode = "404", description = "Transaction not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'VIEWER')")
    public ResponseEntity<TransactionDetailResponse> getTransaction(
            @PathVariable UUID transactionId) {
        
        log.info("Getting transaction: {}", transactionId);
        TransactionDetailResponse transaction = ledgerService.getTransactionDetail(transactionId);
        return ResponseEntity.ok(transaction);
    }

    @PostMapping("/{transactionId}/reverse")
    @Operation(summary = "Reverse transaction", description = "Creates a reversal entry for a transaction")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Transaction reversed successfully"),
        @ApiResponse(responseCode = "404", description = "Transaction not found"),
        @ApiResponse(responseCode = "409", description = "Transaction already reversed"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReverseTransactionResponse> reverseTransaction(
            @PathVariable UUID transactionId,
            @Valid @RequestBody ReverseTransactionRequest request) {
        
        log.info("Reversing transaction: {}", transactionId);
        ReverseTransactionResponse response = ledgerService.reverseTransaction(transactionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List transactions", description = "Retrieves a paginated list of transactions")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transactions retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'VIEWER')")
    public ResponseEntity<Page<TransactionSummary>> listTransactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        
        log.info("Listing transactions from {} to {}", startDate, endDate);
        Page<TransactionSummary> transactions = ledgerService.listTransactions(
            startDate, endDate, transactionType, status, pageable);
        return ResponseEntity.ok(transactions);
    }

    @PostMapping("/batch")
    @Operation(summary = "Batch create transactions", description = "Creates multiple transactions in a batch")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Batch processed successfully"),
        @ApiResponse(responseCode = "207", description = "Partial success"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN')")
    public ResponseEntity<BatchTransactionResponse> batchCreateTransactions(
            @Valid @RequestBody List<CreateTransactionRequest> requests) {
        
        log.info("Processing batch of {} transactions", requests.size());
        BatchTransactionResponse response = ledgerService.batchCreateTransactions(requests);
        
        if (response.hasErrors()) {
            return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/search")
    @Operation(summary = "Search transactions", description = "Search transactions by various criteria")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Search completed successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'VIEWER')")
    public ResponseEntity<Page<TransactionSummary>> searchTransactions(
            @RequestParam String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable) {
        
        log.info("Searching transactions with query: {}", query);
        Page<TransactionSummary> results = ledgerService.searchTransactions(query, startDate, endDate, pageable);
        return ResponseEntity.ok(results);
    }

    @PostMapping("/{transactionId}/approve")
    @Operation(summary = "Approve transaction", description = "Approves a pending transaction")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transaction approved successfully"),
        @ApiResponse(responseCode = "404", description = "Transaction not found"),
        @ApiResponse(responseCode = "409", description = "Transaction not in pending status"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ResponseEntity<ApprovalResponse> approveTransaction(
            @PathVariable UUID transactionId,
            @RequestBody ApprovalRequest request) {
        
        log.info("Approving transaction: {}", transactionId);
        ApprovalResponse response = ledgerService.approveTransaction(transactionId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{transactionId}/reject")
    @Operation(summary = "Reject transaction", description = "Rejects a pending transaction")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transaction rejected successfully"),
        @ApiResponse(responseCode = "404", description = "Transaction not found"),
        @ApiResponse(responseCode = "409", description = "Transaction not in pending status"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ResponseEntity<RejectionResponse> rejectTransaction(
            @PathVariable UUID transactionId,
            @RequestBody RejectionRequest request) {
        
        log.info("Rejecting transaction: {}", transactionId);
        RejectionResponse response = ledgerService.rejectTransaction(transactionId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/pending-approval")
    @Operation(summary = "Get pending approvals", description = "Retrieves transactions pending approval")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pending transactions retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ResponseEntity<Page<TransactionSummary>> getPendingApprovals(Pageable pageable) {
        log.info("Getting pending approval transactions");
        Page<TransactionSummary> pendingTransactions = ledgerService.getPendingApprovals(pageable);
        return ResponseEntity.ok(pendingTransactions);
    }

    @GetMapping("/trial-balance")
    @Operation(summary = "Generate trial balance", description = "Generates a trial balance report")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Trial balance generated successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'VIEWER')")
    public ResponseEntity<TrialBalanceResponse> generateTrialBalance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime asOfDate) {
        
        log.info("Generating trial balance as of {}", asOfDate);
        TrialBalanceResponse trialBalance = ledgerService.generateTrialBalance(asOfDate);
        return ResponseEntity.ok(trialBalance);
    }

    @GetMapping("/account-ledger/{accountId}")
    @Operation(summary = "Get account ledger", description = "Retrieves ledger entries for a specific account")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ledger entries retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Account not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'VIEWER')")
    public ResponseEntity<AccountLedgerResponse> getAccountLedger(
            @PathVariable UUID accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        log.info("Getting ledger for account {} from {} to {}", accountId, fromDate, toDate);
        AccountLedgerResponse ledger = ledgerService.getAccountLedger(accountId, fromDate, toDate, page, size);
        return ResponseEntity.ok(ledger);
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate transaction", description = "Validates a transaction without posting")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Validation completed"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN')")
    public ResponseEntity<TransactionValidationResponse> validateTransaction(
            @Valid @RequestBody CreateTransactionRequest request) {
        
        log.info("Validating transaction: {}", request.getDescription());
        TransactionValidationResponse validation = ledgerService.validateTransaction(request);
        return ResponseEntity.ok(validation);
    }

    @GetMapping("/export")
    @Operation(summary = "Export transactions", description = "Exports transactions to various formats")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Export successful"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN')")
    public ResponseEntity<byte[]> exportTransactions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "CSV") String format) {
        
        log.info("Exporting transactions from {} to {} in format: {}", startDate, endDate, format);
        byte[] exportData = ledgerService.exportTransactions(startDate, endDate, format);
        
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=transactions." + format.toLowerCase())
            .body(exportData);
    }
}