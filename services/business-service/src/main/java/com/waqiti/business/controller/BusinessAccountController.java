package com.waqiti.business.controller;

import com.waqiti.business.dto.*;
import com.waqiti.business.service.BusinessAccountService;
import com.waqiti.business.service.BusinessAnalyticsService;
import com.waqiti.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/business/accounts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Business Account Management", description = "Advanced business account operations")
public class BusinessAccountController {

    private final BusinessAccountService businessAccountService;
    private final BusinessAnalyticsService analyticsService;

    // Account Management
    @PostMapping("/onboard")
    @Operation(summary = "Complete business onboarding")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<BusinessOnboardingResponse>> onboardBusiness(
            @Valid @RequestBody BusinessOnboardingRequest request) {
        log.info("Onboarding business: {}", request.getBusinessName());
        
        BusinessOnboardingResponse response = businessAccountService.onboardBusiness(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/{accountId}/profile")
    @Operation(summary = "Get business profile")
    @PreAuthorize("hasRole('BUSINESS_OWNER') or hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<ApiResponse<BusinessProfileResponse>> getBusinessProfile(
            @PathVariable UUID accountId) {
        log.info("Fetching business profile: {}", accountId);
        
        BusinessProfileResponse profile = businessAccountService.getBusinessProfile(accountId);
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @PutMapping("/{accountId}/profile")
    @Operation(summary = "Update business profile")
    @PreAuthorize("hasRole('BUSINESS_OWNER') or hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<ApiResponse<BusinessProfileResponse>> updateBusinessProfile(
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateBusinessProfileRequest request) {
        log.info("Updating business profile: {}", accountId);
        
        BusinessProfileResponse profile = businessAccountService.updateBusinessProfile(accountId, request);
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    // Multi-account Management
    @PostMapping("/{accountId}/sub-accounts")
    @Operation(summary = "Create business sub-account")
    @PreAuthorize("hasRole('BUSINESS_OWNER') or hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<ApiResponse<SubAccountResponse>> createSubAccount(
            @PathVariable UUID accountId,
            @Valid @RequestBody CreateSubAccountRequest request) {
        log.info("Creating sub-account for business: {}", accountId);
        
        SubAccountResponse response = businessAccountService.createSubAccount(accountId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/{accountId}/sub-accounts")
    @Operation(summary = "Get business sub-accounts")
    @PreAuthorize("hasRole('BUSINESS_OWNER') or hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<ApiResponse<List<SubAccountResponse>>> getSubAccounts(
            @PathVariable UUID accountId) {
        log.info("Fetching sub-accounts for business: {}", accountId);
        
        List<SubAccountResponse> subAccounts = businessAccountService.getSubAccounts(accountId);
        return ResponseEntity.ok(ApiResponse.success(subAccounts));
    }

    // Employee Management
    @PostMapping("/{accountId}/employees")
    @Operation(summary = "Add business employee")
    @PreAuthorize("hasRole('BUSINESS_OWNER') or hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<ApiResponse<BusinessEmployeeResponse>> addEmployee(
            @PathVariable UUID accountId,
            @Valid @RequestBody AddEmployeeRequest request) {
        log.info("Adding employee to business: {}", accountId);
        
        BusinessEmployeeResponse response = businessAccountService.addEmployee(accountId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/{accountId}/employees")
    @Operation(summary = "Get business employees")
    @PreAuthorize("hasRole('BUSINESS_OWNER') or hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<ApiResponse<Page<BusinessEmployeeResponse>>> getEmployees(
            @PathVariable UUID accountId,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        log.info("Fetching employees for business: {}", accountId);
        
        EmployeeFilter filter = EmployeeFilter.builder()
                .department(department)
                .role(role)
                .status(status)
                .build();
        
        Page<BusinessEmployeeResponse> employees = businessAccountService.getEmployees(accountId, filter, pageable);
        return ResponseEntity.ok(ApiResponse.success(employees));
    }

    @PutMapping("/{accountId}/employees/{employeeId}")
    @Operation(summary = "Update employee information")
    @PreAuthorize("hasRole('BUSINESS_OWNER') or hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<ApiResponse<BusinessEmployeeResponse>> updateEmployee(
            @PathVariable UUID accountId,
            @PathVariable UUID employeeId,
            @Valid @RequestBody UpdateEmployeeRequest request) {
        log.info("Updating employee: {} for business: {}", employeeId, accountId);
        
        BusinessEmployeeResponse response = businessAccountService.updateEmployee(accountId, employeeId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Expense Management
    @PostMapping("/{accountId}/expenses")
    @Operation(summary = "Create expense entry")
    @PreAuthorize("hasRole('BUSINESS_OWNER') or hasRole('BUSINESS_ADMIN') or hasRole('BUSINESS_EMPLOYEE')")
    public ResponseEntity<ApiResponse<ExpenseResponse>> createExpense(
            @PathVariable UUID accountId,
            @Valid @RequestBody CreateExpenseRequest request) {
        log.info("Creating expense for business: {}", accountId);
        
        ExpenseResponse response = businessAccountService.createExpense(accountId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/{accountId}/expenses")
    @Operation(summary = "Get business expenses")
    @PreAuthorize("hasRole('BUSINESS_OWNER') or hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<ApiResponse<Page<ExpenseResponse>>> getExpenses(
            @PathVariable UUID accountId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable) {
        log.info("Fetching expenses for business: {}", accountId);
        
        ExpenseFilter filter = ExpenseFilter.builder()
                .category(category)
                .status(status)
                .employeeId(employeeId)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        
        Page<ExpenseResponse> expenses = businessAccountService.getExpenses(accountId, filter, pageable);
        return ResponseEntity.ok(ApiResponse.success(expenses));
    }

    @PostMapping("/{accountId}/expenses/{expenseId}/approve")
    @Operation(summary = "Approve expense")
    @PreAuthorize("hasRole('BUSINESS_OWNER') or hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<ApiResponse<ExpenseResponse>> approveExpense(
            @PathVariable UUID accountId,
            @PathVariable UUID expenseId,
            @Valid @RequestBody ApproveExpenseRequest request) {
        log.info("Approving expense: {} for business: {}", expenseId, accountId);
        
        ExpenseResponse response = businessAccountService.approveExpense(accountId, expenseId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Invoice Management
    @PostMapping("/{accountId}/invoices")
    @Operation(summary = "Create invoice")
    @PreAuthorize("hasRole('BUSINESS_OWNER') or hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> createInvoice(
            @PathVariable UUID accountId,
            @Valid @RequestBody CreateInvoiceRequest request) {
        log.info("Creating invoice for business: {}", accountId);
        
        InvoiceResponse response = businessAccountService.createInvoice(accountId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/{accountId}/invoices")
    @Operation(summary = "Get business invoices")
    @PreAuthorize("hasRole('BUSINESS_OWNER') or hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<ApiResponse<Page<InvoiceResponse>>> getInvoices(
            @PathVariable UUID accountId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String customerName,
            Pageable pageable) {
        log.info("Fetching invoices for business: {}", accountId);
        
        InvoiceFilter filter = InvoiceFilter.builder()
                .status(status)
                .customerName(customerName)
                .build();
        
        Page<InvoiceResponse> invoices = businessAccountService.getInvoices(accountId, filter, pageable);
        return ResponseEntity.ok(ApiResponse.success(invoices));
    }

    @PostMapping("/{accountId}/invoices/{invoiceId}/send")
    @Operation(summary = "Send invoice to customer")
    @PreAuthorize("hasRole('BUSINESS_OWNER') or hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> sendInvoice(
            @PathVariable UUID accountId,
            @PathVariable UUID invoiceId) {
        log.info("Sending invoice: {} for business: {}", invoiceId, accountId);
        
        businessAccountService.sendInvoice(accountId, invoiceId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // Financial Analytics
    @GetMapping("/{accountId}/analytics/dashboard")
    @Operation(summary = "Get business analytics dashboard")
    @PreAuthorize("hasRole('BUSINESS_OWNER') or hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<ApiResponse<BusinessDashboardResponse>> getAnalyticsDashboard(
            @PathVariable UUID accountId,
            @RequestParam(required = false, defaultValue = "30") Integer days) {
        log.info("Fetching analytics dashboard for business: {}", accountId);
        
        BusinessDashboardResponse dashboard = analyticsService.getBusinessDashboard(accountId, days);
        return ResponseEntity.ok(ApiResponse.success(dashboard));
    }

    @GetMapping("/{accountId}/analytics/cash-flow")
    @Operation(summary = "Get cash flow analysis")
    @PreAuthorize("hasRole('BUSINESS_OWNER') or hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<ApiResponse<CashFlowAnalysisResponse>> getCashFlowAnalysis(
            @PathVariable UUID accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Fetching cash flow analysis for business: {}", accountId);
        
        CashFlowAnalysisResponse analysis = analyticsService.getCashFlowAnalysis(accountId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(analysis));
    }

    @GetMapping("/{accountId}/analytics/profit-loss")
    @Operation(summary = "Get profit & loss statement")
    @PreAuthorize("hasRole('BUSINESS_OWNER') or hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<ApiResponse<ProfitLossResponse>> getProfitLossStatement(
            @PathVariable UUID accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Fetching P&L statement for business: {}", accountId);
        
        ProfitLossResponse statement = analyticsService.getProfitLossStatement(accountId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(statement));
    }

    // Tax and Compliance
    @GetMapping("/{accountId}/tax/documents")
    @Operation(summary = "Get tax documents")
    @PreAuthorize("hasRole('BUSINESS_OWNER') or hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<ApiResponse<List<TaxDocumentResponse>>> getTaxDocuments(
            @PathVariable UUID accountId,
            @RequestParam Integer year) {
        log.info("Fetching tax documents for business: {} year: {}", accountId, year);
        
        List<TaxDocumentResponse> documents = businessAccountService.getTaxDocuments(accountId, year);
        return ResponseEntity.ok(ApiResponse.success(documents));
    }

    @PostMapping("/{accountId}/tax/generate")
    @Operation(summary = "Generate tax report")
    @PreAuthorize("hasRole('BUSINESS_OWNER') or hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<ApiResponse<TaxReportResponse>> generateTaxReport(
            @PathVariable UUID accountId,
            @Valid @RequestBody GenerateTaxReportRequest request) {
        log.info("Generating tax report for business: {}", accountId);
        
        TaxReportResponse report = businessAccountService.generateTaxReport(accountId, request);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    // Account Settings
    @GetMapping("/{accountId}/settings")
    @Operation(summary = "Get business account settings")
    @PreAuthorize("hasRole('BUSINESS_OWNER') or hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<ApiResponse<BusinessSettingsResponse>> getBusinessSettings(
            @PathVariable UUID accountId) {
        log.info("Fetching settings for business: {}", accountId);
        
        BusinessSettingsResponse settings = businessAccountService.getBusinessSettings(accountId);
        return ResponseEntity.ok(ApiResponse.success(settings));
    }

    @PutMapping("/{accountId}/settings")
    @Operation(summary = "Update business account settings")
    @PreAuthorize("hasRole('BUSINESS_OWNER') or hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<ApiResponse<BusinessSettingsResponse>> updateBusinessSettings(
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateBusinessSettingsRequest request) {
        log.info("Updating settings for business: {}", accountId);
        
        BusinessSettingsResponse settings = businessAccountService.updateBusinessSettings(accountId, request);
        return ResponseEntity.ok(ApiResponse.success(settings));
    }

    // Health Check
    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.success("Business Account service is healthy"));
    }
}