package com.waqiti.ledger.controller;

import com.waqiti.ledger.dto.*;
import com.waqiti.ledger.service.PeriodClosingService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Period Closing Controller
 * 
 * Manages accounting period closing procedures, including month-end,
 * quarter-end, and year-end closing processes with proper validations
 * and audit trails.
 */
@RestController
@RequestMapping("/api/v1/period-closing")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Period Closing", description = "Accounting period closing and management APIs")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN')")
public class PeriodClosingController {

    private final PeriodClosingService periodClosingService;

    @GetMapping("/periods")
    @Operation(summary = "Get accounting periods", 
               description = "Retrieve paginated list of accounting periods")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Accounting periods retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'VIEWER')")
    public ResponseEntity<Page<AccountingPeriodResponse>> getAccountingPeriods(
            @Parameter(description = "Period status filter (OPEN, CLOSED, LOCKED)")
            @RequestParam(required = false) String status,
            @Parameter(description = "Fiscal year filter")
            @RequestParam(required = false) Integer fiscalYear,
            @Parameter(description = "Company ID filter")
            @RequestParam(required = false) UUID companyId,
            @PageableDefault(size = 20, sort = "startDate") Pageable pageable) {
        
        log.info("Retrieving accounting periods with status: {}, fiscalYear: {}, companyId: {}", 
                 status, fiscalYear, companyId);
        
        Page<AccountingPeriodResponse> periods = 
            periodClosingService.getAccountingPeriods(status, fiscalYear, companyId, pageable);
        return ResponseEntity.ok(periods);
    }

    @GetMapping("/periods/{periodId}")
    @Operation(summary = "Get accounting period details", 
               description = "Retrieve detailed accounting period information")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Accounting period retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Period not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'VIEWER')")
    public ResponseEntity<AccountingPeriodDetailResponse> getAccountingPeriodById(
            @Parameter(description = "Period ID") @PathVariable UUID periodId) {
        
        log.info("Retrieving accounting period details for ID: {}", periodId);
        
        AccountingPeriodDetailResponse period = periodClosingService.getAccountingPeriodById(periodId);
        return ResponseEntity.ok(period);
    }

    @GetMapping("/periods/current")
    @Operation(summary = "Get current accounting period", 
               description = "Retrieve the currently active accounting period")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Current period retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "No current period found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'VIEWER')")
    public ResponseEntity<AccountingPeriodResponse> getCurrentAccountingPeriod(
            @Parameter(description = "Company ID filter")
            @RequestParam(required = false) UUID companyId) {
        
        log.info("Retrieving current accounting period for company: {}", companyId);
        
        AccountingPeriodResponse period = periodClosingService.getCurrentAccountingPeriod(companyId);
        return ResponseEntity.ok(period);
    }

    @PostMapping("/periods/{periodId}/pre-close-check")
    @Operation(summary = "Pre-close validation", 
               description = "Perform pre-close validation checks for accounting period")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pre-close check completed successfully"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "404", description = "Period not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<PreCloseCheckResponse> performPreCloseCheck(
            @Parameter(description = "Period ID") @PathVariable UUID periodId) {
        
        log.info("Performing pre-close check for period ID: {}", periodId);
        
        PreCloseCheckResponse checkResult = periodClosingService.performPreCloseCheck(periodId);
        return ResponseEntity.ok(checkResult);
    }

    @PostMapping("/periods/{periodId}/soft-close")
    @Operation(summary = "Soft close accounting period", 
               description = "Perform soft close of accounting period (allows modifications with approval)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Period soft closed successfully"),
        @ApiResponse(responseCode = "400", description = "Period cannot be soft closed"),
        @ApiResponse(responseCode = "404", description = "Period not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<PeriodClosingResponse> softClosePeriod(
            @Parameter(description = "Period ID") @PathVariable UUID periodId,
            @Parameter(description = "Soft close request") @Valid @RequestBody SoftClosePeriodRequest request) {
        
        log.info("Soft closing period ID: {}", periodId);
        
        PeriodClosingResponse result = periodClosingService.softClosePeriod(periodId, request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/periods/{periodId}/hard-close")
    @Operation(summary = "Hard close accounting period", 
               description = "Perform hard close of accounting period (no further modifications allowed)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Period hard closed successfully"),
        @ApiResponse(responseCode = "400", description = "Period cannot be hard closed"),
        @ApiResponse(responseCode = "404", description = "Period not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PeriodClosingResponse> hardClosePeriod(
            @Parameter(description = "Period ID") @PathVariable UUID periodId,
            @Parameter(description = "Hard close request") @Valid @RequestBody HardClosePeriodRequest request) {
        
        log.info("Hard closing period ID: {}", periodId);
        
        PeriodClosingResponse result = periodClosingService.hardClosePeriod(periodId, request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/periods/{periodId}/reopen")
    @Operation(summary = "Reopen closed period", 
               description = "Reopen a previously closed accounting period")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Period reopened successfully"),
        @ApiResponse(responseCode = "400", description = "Period cannot be reopened"),
        @ApiResponse(responseCode = "404", description = "Period not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PeriodClosingResponse> reopenPeriod(
            @Parameter(description = "Period ID") @PathVariable UUID periodId,
            @Parameter(description = "Reopen request") @Valid @RequestBody ReopenPeriodRequest request) {
        
        log.info("Reopening period ID: {}", periodId);
        
        PeriodClosingResponse result = periodClosingService.reopenPeriod(periodId, request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/year-end-close")
    @Operation(summary = "Perform year-end closing", 
               description = "Execute comprehensive year-end closing procedures")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Year-end closing completed successfully"),
        @ApiResponse(responseCode = "400", description = "Year-end closing failed validation"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<YearEndClosingResponse> performYearEndClosing(
            @Parameter(description = "Year-end closing request") @Valid @RequestBody YearEndClosingRequest request) {
        
        log.info("Performing year-end closing for fiscal year: {}", request.getFiscalYear());
        
        YearEndClosingResponse result = periodClosingService.performYearEndClosing(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/periods/{periodId}/adjusting-entries")
    @Operation(summary = "Generate adjusting entries", 
               description = "Generate standard adjusting entries for period closing")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Adjusting entries generated successfully"),
        @ApiResponse(responseCode = "404", description = "Period not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<AdjustingEntriesResponse> generateAdjustingEntries(
            @Parameter(description = "Period ID") @PathVariable UUID periodId,
            @Parameter(description = "Adjusting entries request") @Valid @RequestBody GenerateAdjustingEntriesRequest request) {
        
        log.info("Generating adjusting entries for period ID: {}", periodId);
        
        AdjustingEntriesResponse result = periodClosingService.generateAdjustingEntries(periodId, request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/periods/{periodId}/closing-entries")
    @Operation(summary = "Generate closing entries", 
               description = "Generate closing entries to transfer revenue and expense accounts to retained earnings")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Closing entries generated successfully"),
        @ApiResponse(responseCode = "404", description = "Period not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ClosingEntriesResponse> generateClosingEntries(
            @Parameter(description = "Period ID") @PathVariable UUID periodId,
            @Parameter(description = "Closing entries request") @Valid @RequestBody GenerateClosingEntriesRequest request) {
        
        log.info("Generating closing entries for period ID: {}", periodId);
        
        ClosingEntriesResponse result = periodClosingService.generateClosingEntries(periodId, request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/periods/{periodId}/closing-checklist")
    @Operation(summary = "Get closing checklist", 
               description = "Retrieve period closing checklist with completion status")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Closing checklist retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Period not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'VIEWER')")
    public ResponseEntity<ClosingChecklistResponse> getClosingChecklist(
            @Parameter(description = "Period ID") @PathVariable UUID periodId) {
        
        log.info("Retrieving closing checklist for period ID: {}", periodId);
        
        ClosingChecklistResponse checklist = periodClosingService.getClosingChecklist(periodId);
        return ResponseEntity.ok(checklist);
    }

    @PostMapping("/periods/{periodId}/closing-checklist/{itemId}")
    @Operation(summary = "Complete checklist item", 
               description = "Mark a closing checklist item as completed")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Checklist item completed successfully"),
        @ApiResponse(responseCode = "404", description = "Period or item not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<Void> completeChecklistItem(
            @Parameter(description = "Period ID") @PathVariable UUID periodId,
            @Parameter(description = "Checklist item ID") @PathVariable UUID itemId,
            @Parameter(description = "Completion request") @Valid @RequestBody CompleteChecklistItemRequest request) {
        
        log.info("Completing checklist item ID: {} for period ID: {}", itemId, periodId);
        
        periodClosingService.completeChecklistItem(periodId, itemId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/closing-schedule")
    @Operation(summary = "Get closing schedule", 
               description = "Retrieve upcoming period closing schedule and deadlines")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Closing schedule retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'MANAGER', 'VIEWER')")
    public ResponseEntity<ClosingScheduleResponse> getClosingSchedule(
            @Parameter(description = "Number of periods to include (default: 12)")
            @RequestParam(defaultValue = "12") Integer periods,
            @Parameter(description = "Company ID filter")
            @RequestParam(required = false) UUID companyId) {
        
        log.info("Retrieving closing schedule for {} periods, company: {}", periods, companyId);
        
        ClosingScheduleResponse schedule = periodClosingService.getClosingSchedule(periods, companyId);
        return ResponseEntity.ok(schedule);
    }

    @GetMapping("/closing-history")
    @Operation(summary = "Get closing history", 
               description = "Retrieve historical period closing information and audit trail")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Closing history retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'VIEWER')")
    public ResponseEntity<Page<PeriodClosingHistoryResponse>> getClosingHistory(
            @Parameter(description = "Start date filter")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date filter")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Company ID filter")
            @RequestParam(required = false) UUID companyId,
            @PageableDefault(size = 20, sort = "closedAt") Pageable pageable) {
        
        log.info("Retrieving closing history from {} to {} for company: {}", startDate, endDate, companyId);
        
        Page<PeriodClosingHistoryResponse> history = 
            periodClosingService.getClosingHistory(startDate, endDate, companyId, pageable);
        return ResponseEntity.ok(history);
    }
}