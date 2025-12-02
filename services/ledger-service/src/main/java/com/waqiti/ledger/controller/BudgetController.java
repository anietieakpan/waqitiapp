package com.waqiti.ledger.controller;

import com.waqiti.ledger.dto.*;
import com.waqiti.ledger.service.BudgetService;
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
 * Budget Controller
 * 
 * Manages budget creation, monitoring, and variance analysis.
 * Provides endpoints for budget planning and performance tracking.
 */
@RestController
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Budget Management", description = "Budget planning and monitoring APIs")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'MANAGER')")
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping
    @Operation(summary = "Get all budgets", 
               description = "Retrieve paginated list of budgets with optional filtering")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Budgets retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'MANAGER', 'VIEWER')")
    public ResponseEntity<Page<BudgetSummaryResponse>> getAllBudgets(
            @Parameter(description = "Budget status filter")
            @RequestParam(required = false) String status,
            @Parameter(description = "Budget type filter")
            @RequestParam(required = false) String budgetType,
            @Parameter(description = "Company ID filter")
            @RequestParam(required = false) UUID companyId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        
        log.info("Retrieving budgets with status: {}, type: {}, companyId: {}", status, budgetType, companyId);
        
        Page<BudgetSummaryResponse> budgets = budgetService.getAllBudgets(status, budgetType, companyId, pageable);
        return ResponseEntity.ok(budgets);
    }

    @GetMapping("/{budgetId}")
    @Operation(summary = "Get budget by ID", 
               description = "Retrieve detailed budget information including line items")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Budget retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Budget not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'MANAGER', 'VIEWER')")
    public ResponseEntity<BudgetDetailResponse> getBudgetById(
            @Parameter(description = "Budget ID") @PathVariable UUID budgetId) {
        
        log.info("Retrieving budget details for ID: {}", budgetId);
        
        BudgetDetailResponse budget = budgetService.getBudgetById(budgetId);
        return ResponseEntity.ok(budget);
    }

    @PostMapping
    @Operation(summary = "Create new budget", 
               description = "Create a new budget with line items")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Budget created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<BudgetDetailResponse> createBudget(
            @Parameter(description = "Budget creation request") @Valid @RequestBody CreateBudgetRequest request) {
        
        log.info("Creating new budget: {}", request.getBudgetName());
        
        BudgetDetailResponse budget = budgetService.createBudget(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(budget);
    }

    @PutMapping("/{budgetId}")
    @Operation(summary = "Update budget", 
               description = "Update existing budget details")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Budget updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "Budget not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<BudgetDetailResponse> updateBudget(
            @Parameter(description = "Budget ID") @PathVariable UUID budgetId,
            @Parameter(description = "Budget update request") @Valid @RequestBody UpdateBudgetRequest request) {
        
        log.info("Updating budget ID: {}", budgetId);
        
        BudgetDetailResponse budget = budgetService.updateBudget(budgetId, request);
        return ResponseEntity.ok(budget);
    }

    @DeleteMapping("/{budgetId}")
    @Operation(summary = "Delete budget", 
               description = "Soft delete a budget (sets status to INACTIVE)")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Budget deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Budget not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<Void> deleteBudget(
            @Parameter(description = "Budget ID") @PathVariable UUID budgetId) {
        
        log.info("Deleting budget ID: {}", budgetId);
        
        budgetService.deleteBudget(budgetId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{budgetId}/variance-analysis")
    @Operation(summary = "Get budget variance analysis", 
               description = "Analyze budget vs actual performance")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Variance analysis completed successfully"),
        @ApiResponse(responseCode = "404", description = "Budget not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'MANAGER', 'VIEWER')")
    public ResponseEntity<BudgetVarianceAnalysisResponse> getBudgetVarianceAnalysis(
            @Parameter(description = "Budget ID") @PathVariable UUID budgetId,
            @Parameter(description = "Analysis as of date (defaults to today)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
        log.info("Generating variance analysis for budget ID: {} as of: {}", budgetId, effectiveDate);
        
        BudgetVarianceAnalysisResponse analysis = budgetService.performVarianceAnalysis(budgetId, effectiveDate);
        return ResponseEntity.ok(analysis);
    }

    @GetMapping("/{budgetId}/performance")
    @Operation(summary = "Get budget performance metrics", 
               description = "Retrieve budget performance KPIs and metrics")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Performance metrics retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Budget not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'MANAGER', 'VIEWER')")
    public ResponseEntity<BudgetPerformanceResponse> getBudgetPerformance(
            @Parameter(description = "Budget ID") @PathVariable UUID budgetId) {
        
        log.info("Retrieving performance metrics for budget ID: {}", budgetId);
        
        BudgetPerformanceResponse performance = budgetService.getBudgetPerformance(budgetId);
        return ResponseEntity.ok(performance);
    }

    @PostMapping("/{budgetId}/line-items")
    @Operation(summary = "Add budget line item", 
               description = "Add a new line item to an existing budget")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Line item added successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "Budget not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<BudgetLineItemResponse> addBudgetLineItem(
            @Parameter(description = "Budget ID") @PathVariable UUID budgetId,
            @Parameter(description = "Line item creation request") @Valid @RequestBody CreateBudgetLineItemRequest request) {
        
        log.info("Adding line item to budget ID: {}", budgetId);
        
        BudgetLineItemResponse lineItem = budgetService.addBudgetLineItem(budgetId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(lineItem);
    }

    @PutMapping("/{budgetId}/line-items/{lineItemId}")
    @Operation(summary = "Update budget line item", 
               description = "Update existing budget line item")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Line item updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "Line item not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<BudgetLineItemResponse> updateBudgetLineItem(
            @Parameter(description = "Budget ID") @PathVariable UUID budgetId,
            @Parameter(description = "Line item ID") @PathVariable UUID lineItemId,
            @Parameter(description = "Line item update request") @Valid @RequestBody UpdateBudgetLineItemRequest request) {
        
        log.info("Updating line item ID: {} in budget ID: {}", lineItemId, budgetId);
        
        BudgetLineItemResponse lineItem = budgetService.updateBudgetLineItem(budgetId, lineItemId, request);
        return ResponseEntity.ok(lineItem);
    }

    @DeleteMapping("/{budgetId}/line-items/{lineItemId}")
    @Operation(summary = "Delete budget line item", 
               description = "Remove line item from budget")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Line item deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Line item not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<Void> deleteBudgetLineItem(
            @Parameter(description = "Budget ID") @PathVariable UUID budgetId,
            @Parameter(description = "Line item ID") @PathVariable UUID lineItemId) {
        
        log.info("Deleting line item ID: {} from budget ID: {}", lineItemId, budgetId);
        
        budgetService.deleteBudgetLineItem(budgetId, lineItemId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{budgetId}/approve")
    @Operation(summary = "Approve budget", 
               description = "Approve budget for activation")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Budget approved successfully"),
        @ApiResponse(responseCode = "404", description = "Budget not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<BudgetDetailResponse> approveBudget(
            @Parameter(description = "Budget ID") @PathVariable UUID budgetId) {
        
        log.info("Approving budget ID: {}", budgetId);
        
        BudgetDetailResponse budget = budgetService.approveBudget(budgetId);
        return ResponseEntity.ok(budget);
    }

    @PostMapping("/{budgetId}/activate")
    @Operation(summary = "Activate budget", 
               description = "Activate an approved budget")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Budget activated successfully"),
        @ApiResponse(responseCode = "400", description = "Budget cannot be activated"),
        @ApiResponse(responseCode = "404", description = "Budget not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<BudgetDetailResponse> activateBudget(
            @Parameter(description = "Budget ID") @PathVariable UUID budgetId) {
        
        log.info("Activating budget ID: {}", budgetId);
        
        BudgetDetailResponse budget = budgetService.activateBudget(budgetId);
        return ResponseEntity.ok(budget);
    }

    @GetMapping("/alerts")
    @Operation(summary = "Get budget alerts", 
               description = "Retrieve budget threshold alerts and warnings")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Budget alerts retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'MANAGER')")
    public ResponseEntity<BudgetAlertsResponse> getBudgetAlerts(
            @Parameter(description = "Company ID filter")
            @RequestParam(required = false) UUID companyId) {
        
        log.info("Retrieving budget alerts for company: {}", companyId);
        
        BudgetAlertsResponse alerts = budgetService.getBudgetAlerts(companyId);
        return ResponseEntity.ok(alerts);
    }
}