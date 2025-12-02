package com.waqiti.ledger.controller;

import com.waqiti.ledger.dto.*;
import com.waqiti.ledger.service.BankReconciliationService;
import com.waqiti.ledger.service.InterCompanyReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Reconciliation Controller
 * 
 * Manages bank reconciliation, inter-company reconciliation, and other
 * reconciliation processes for ensuring data accuracy and completeness.
 */
@RestController
@RequestMapping("/api/v1/reconciliations")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reconciliation Management", description = "Bank and inter-company reconciliation APIs")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN')")
public class ReconciliationController {

    private final BankReconciliationService bankReconciliationService;
    private final InterCompanyReconciliationService interCompanyReconciliationService;

    // Bank Reconciliation Endpoints
    
    @GetMapping("/bank")
    @Operation(summary = "Get bank reconciliations", 
               description = "Retrieve paginated list of bank reconciliations")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bank reconciliations retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'VIEWER')")
    public ResponseEntity<Page<BankReconciliationSummaryResponse>> getBankReconciliations(
            @Parameter(description = "Bank account ID filter")
            @RequestParam(required = false) UUID bankAccountId,
            @Parameter(description = "Reconciliation status filter")
            @RequestParam(required = false) String status,
            @Parameter(description = "Start date filter")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date filter")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 20, sort = "reconciliationDate") Pageable pageable) {
        
        log.info("Retrieving bank reconciliations with filters - bankAccountId: {}, status: {}, startDate: {}, endDate: {}", 
                 bankAccountId, status, startDate, endDate);
        
        Page<BankReconciliationSummaryResponse> reconciliations = 
            bankReconciliationService.getBankReconciliations(bankAccountId, status, startDate, endDate, pageable);
        return ResponseEntity.ok(reconciliations);
    }

    @GetMapping("/bank/{reconciliationId}")
    @Operation(summary = "Get bank reconciliation details", 
               description = "Retrieve detailed bank reconciliation information")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bank reconciliation retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Reconciliation not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'VIEWER')")
    public ResponseEntity<BankReconciliationDetailResponse> getBankReconciliationById(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID reconciliationId) {
        
        log.info("Retrieving bank reconciliation details for ID: {}", reconciliationId);
        
        BankReconciliationDetailResponse reconciliation = 
            bankReconciliationService.getBankReconciliationById(reconciliationId);
        return ResponseEntity.ok(reconciliation);
    }

    @PostMapping("/bank")
    @Operation(summary = "Start bank reconciliation", 
               description = "Initiate a new bank reconciliation process")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Bank reconciliation started successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<BankReconciliationDetailResponse> startBankReconciliation(
            @Parameter(description = "Bank reconciliation request") @Valid @RequestBody StartBankReconciliationRequest request) {
        
        log.info("Starting bank reconciliation for account: {}, statement date: {}", 
                 request.getBankAccountId(), request.getStatementDate());
        
        BankReconciliationDetailResponse reconciliation = 
            bankReconciliationService.startBankReconciliation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(reconciliation);
    }

    @PostMapping("/bank/{reconciliationId}/auto-match")
    @Operation(summary = "Auto-match bank transactions", 
               description = "Automatically match bank statement items with ledger entries")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Auto-matching completed successfully"),
        @ApiResponse(responseCode = "404", description = "Reconciliation not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<AutoMatchResultResponse> autoMatchBankTransactions(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID reconciliationId,
            @Parameter(description = "Auto-match configuration") @Valid @RequestBody AutoMatchConfigRequest config) {
        
        log.info("Auto-matching transactions for reconciliation ID: {}", reconciliationId);
        
        AutoMatchResultResponse result = bankReconciliationService.autoMatchTransactions(reconciliationId, config);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/bank/{reconciliationId}/manual-match")
    @Operation(summary = "Manual match bank transaction", 
               description = "Manually match specific bank statement item with ledger entry")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Manual matching completed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid matching request"),
        @ApiResponse(responseCode = "404", description = "Reconciliation not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<Void> manualMatchBankTransaction(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID reconciliationId,
            @Parameter(description = "Manual match request") @Valid @RequestBody ManualMatchRequest request) {
        
        log.info("Manual matching transaction for reconciliation ID: {}", reconciliationId);
        
        bankReconciliationService.manualMatchTransaction(reconciliationId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/bank/{reconciliationId}/finalize")
    @Operation(summary = "Finalize bank reconciliation", 
               description = "Complete and finalize the bank reconciliation process")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bank reconciliation finalized successfully"),
        @ApiResponse(responseCode = "400", description = "Reconciliation cannot be finalized"),
        @ApiResponse(responseCode = "404", description = "Reconciliation not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<BankReconciliationDetailResponse> finalizeBankReconciliation(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID reconciliationId,
            @Parameter(description = "Finalization request") @Valid @RequestBody FinalizeBankReconciliationRequest request) {
        
        log.info("Finalizing bank reconciliation ID: {}", reconciliationId);
        
        BankReconciliationDetailResponse reconciliation = 
            bankReconciliationService.finalizeBankReconciliation(reconciliationId, request);
        return ResponseEntity.ok(reconciliation);
    }

    @GetMapping("/bank/{reconciliationId}/unmatched")
    @Operation(summary = "Get unmatched items", 
               description = "Retrieve unmatched bank statement items and ledger entries")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Unmatched items retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Reconciliation not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'VIEWER')")
    public ResponseEntity<UnmatchedItemsResponse> getUnmatchedItems(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID reconciliationId) {
        
        log.info("Retrieving unmatched items for reconciliation ID: {}", reconciliationId);
        
        UnmatchedItemsResponse unmatchedItems = 
            bankReconciliationService.getUnmatchedItems(reconciliationId);
        return ResponseEntity.ok(unmatchedItems);
    }

    // Inter-Company Reconciliation Endpoints

    @GetMapping("/intercompany")
    @Operation(summary = "Get inter-company reconciliations", 
               description = "Retrieve paginated list of inter-company reconciliations")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Inter-company reconciliations retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'VIEWER')")
    public ResponseEntity<Page<InterCompanyReconciliationSummaryResponse>> getInterCompanyReconciliations(
            @Parameter(description = "Company A ID filter")
            @RequestParam(required = false) UUID companyAId,
            @Parameter(description = "Company B ID filter")
            @RequestParam(required = false) UUID companyBId,
            @Parameter(description = "Reconciliation status filter")
            @RequestParam(required = false) String status,
            @Parameter(description = "Start date filter")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date filter")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 20, sort = "reconciliationDate") Pageable pageable) {
        
        log.info("Retrieving inter-company reconciliations with filters - companyA: {}, companyB: {}, status: {}", 
                 companyAId, companyBId, status);
        
        Page<InterCompanyReconciliationSummaryResponse> reconciliations = 
            interCompanyReconciliationService.getInterCompanyReconciliations(
                companyAId, companyBId, status, startDate, endDate, pageable);
        return ResponseEntity.ok(reconciliations);
    }

    @PostMapping("/intercompany")
    @Operation(summary = "Start inter-company reconciliation", 
               description = "Initiate inter-company reconciliation between two entities")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Inter-company reconciliation started successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<InterCompanyReconciliationDetailResponse> startInterCompanyReconciliation(
            @Parameter(description = "Inter-company reconciliation request") @Valid @RequestBody StartInterCompanyReconciliationRequest request) {
        
        log.info("Starting inter-company reconciliation between companies: {} and {}", 
                 request.getCompanyAId(), request.getCompanyBId());
        
        InterCompanyReconciliationDetailResponse reconciliation = 
            interCompanyReconciliationService.startInterCompanyReconciliation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(reconciliation);
    }

    @GetMapping("/intercompany/{reconciliationId}")
    @Operation(summary = "Get inter-company reconciliation details", 
               description = "Retrieve detailed inter-company reconciliation information")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Inter-company reconciliation retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Reconciliation not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'VIEWER')")
    public ResponseEntity<InterCompanyReconciliationDetailResponse> getInterCompanyReconciliationById(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID reconciliationId) {
        
        log.info("Retrieving inter-company reconciliation details for ID: {}", reconciliationId);
        
        InterCompanyReconciliationDetailResponse reconciliation = 
            interCompanyReconciliationService.getInterCompanyReconciliationById(reconciliationId);
        return ResponseEntity.ok(reconciliation);
    }

    @PostMapping("/intercompany/{reconciliationId}/finalize")
    @Operation(summary = "Finalize inter-company reconciliation", 
               description = "Complete and finalize the inter-company reconciliation process")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Inter-company reconciliation finalized successfully"),
        @ApiResponse(responseCode = "400", description = "Reconciliation cannot be finalized"),
        @ApiResponse(responseCode = "404", description = "Reconciliation not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<InterCompanyReconciliationDetailResponse> finalizeInterCompanyReconciliation(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID reconciliationId) {
        
        log.info("Finalizing inter-company reconciliation ID: {}", reconciliationId);
        
        InterCompanyReconciliationDetailResponse reconciliation = 
            interCompanyReconciliationService.finalizeInterCompanyReconciliation(reconciliationId);
        return ResponseEntity.ok(reconciliation);
    }

    @GetMapping("/summary")
    @Operation(summary = "Get reconciliation summary", 
               description = "Get summary of all reconciliation activities")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reconciliation summary retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'MANAGER', 'VIEWER')")
    public ResponseEntity<ReconciliationSummaryResponse> getReconciliationSummary(
            @Parameter(description = "Start date filter")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date filter")  
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        log.info("Retrieving reconciliation summary from {} to {}", startDate, endDate);
        
        ReconciliationSummaryResponse summary = 
            bankReconciliationService.getReconciliationSummary(startDate, endDate);
        return ResponseEntity.ok(summary);
    }
}