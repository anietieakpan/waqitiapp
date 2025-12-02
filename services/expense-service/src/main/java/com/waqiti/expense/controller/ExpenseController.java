package com.waqiti.expense.controller;

import com.waqiti.common.security.ResourceType;
import com.waqiti.common.security.ValidateOwnership;
import com.waqiti.expense.dto.*;
import com.waqiti.expense.service.ExpenseService;
import com.waqiti.expense.service.ExpenseReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Expense Management", description = "APIs for managing expenses and expense reports")
@SecurityRequirement(name = "bearer-jwt")
public class ExpenseController {
    
    private final ExpenseService expenseService;
    private final ExpenseReportService expenseReportService;
    
    @PostMapping
    @Operation(summary = "Create new expense", 
               description = "Create a new expense entry with optional receipt upload")
    @ApiResponse(responseCode = "201", description = "Expense created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid expense data")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ExpenseResponseDto> createExpense(
            @Valid @RequestBody CreateExpenseRequestDto request) {
        
        log.info("Creating expense for amount: {} {}", request.getAmount(), request.getCurrency());
        
        ExpenseResponseDto expense = expenseService.createExpense(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(expense);
    }
    
    @PostMapping("/{expenseId}/receipt")
    @Operation(summary = "Upload expense receipt",
               description = "Upload receipt file for an existing expense")
    @ApiResponse(responseCode = "200", description = "Receipt uploaded successfully")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.EXPENSE, resourceIdParam = "expenseId")  // SECURITY FIX: Validate ownership
    public ResponseEntity<ReceiptUploadResponseDto> uploadReceipt(
            @PathVariable UUID expenseId,
            @RequestParam("file") @Valid MultipartFile file,  // SECURITY FIX: Add validation
            @RequestParam(value = "ocrEnabled", defaultValue = "false") boolean ocrEnabled) {  // SECURITY FIX: Default OCR to false
        
        log.info("Uploading receipt for expense: {}", expenseId);
        
        ReceiptUploadResponseDto response = expenseService.uploadReceipt(expenseId, file, ocrEnabled);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping
    @Operation(summary = "Get user expenses", 
               description = "Get paginated list of user's expenses with optional filters")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<ExpenseResponseDto>> getUserExpenses(
            @Parameter(description = "Expense category filter")
            @RequestParam(required = false) String category,
            
            @Parameter(description = "Expense status filter") 
            @RequestParam(required = false) String status,
            
            @Parameter(description = "Start date filter")
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            
            @Parameter(description = "End date filter")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            
            @PageableDefault(size = 20) Pageable pageable) {
        
        log.debug("Getting expenses with filters - category: {}, status: {}", category, status);
        
        ExpenseFilterDto filter = ExpenseFilterDto.builder()
            .category(category)
            .status(status)
            .fromDate(fromDate != null ? fromDate.atStartOfDay() : null)
            .toDate(toDate != null ? toDate.atTime(23, 59, 59) : null)
            .build();
        
        Page<ExpenseResponseDto> expenses = expenseService.getUserExpenses(filter, pageable);
        return ResponseEntity.ok(expenses);
    }
    
    @GetMapping("/{expenseId}")
    @Operation(summary = "Get expense details", 
               description = "Get detailed information about a specific expense")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ExpenseResponseDto> getExpense(@PathVariable UUID expenseId) {
        log.debug("Getting expense details for: {}", expenseId);
        
        ExpenseResponseDto expense = expenseService.getExpenseById(expenseId);
        return ResponseEntity.ok(expense);
    }
    
    @PutMapping("/{expenseId}")
    @Operation(summary = "Update expense", 
               description = "Update an existing expense (only if in DRAFT or REJECTED status)")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ExpenseResponseDto> updateExpense(
            @PathVariable UUID expenseId,
            @Valid @RequestBody UpdateExpenseRequestDto request) {
        
        log.info("Updating expense: {}", expenseId);
        
        ExpenseResponseDto expense = expenseService.updateExpense(expenseId, request);
        return ResponseEntity.ok(expense);
    }
    
    @PostMapping("/{expenseId}/submit")
    @Operation(summary = "Submit expense for approval", 
               description = "Submit expense for manager approval")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ExpenseResponseDto> submitExpense(@PathVariable UUID expenseId) {
        log.info("Submitting expense for approval: {}", expenseId);
        
        ExpenseResponseDto expense = expenseService.submitExpense(expenseId);
        return ResponseEntity.ok(expense);
    }
    
    @PostMapping("/{expenseId}/approve")
    @Operation(summary = "Approve expense", 
               description = "Approve or reject an expense (manager/admin only)")
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<ExpenseResponseDto> approveExpense(
            @PathVariable UUID expenseId,
            @Valid @RequestBody ApproveExpenseRequestDto request) {
        
        log.info("Processing approval for expense: {}, approved: {}", expenseId, request.isApproved());
        
        ExpenseResponseDto expense = expenseService.approveExpense(expenseId, request);
        return ResponseEntity.ok(expense);
    }
    
    @DeleteMapping("/{expenseId}")
    @Operation(summary = "Delete expense", 
               description = "Delete an expense (only if in DRAFT status)")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deleteExpense(@PathVariable UUID expenseId) {
        log.info("Deleting expense: {}", expenseId);
        
        expenseService.deleteExpense(expenseId);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/summary")
    @Operation(summary = "Get expense summary", 
               description = "Get summary statistics for user's expenses")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ExpenseSummaryDto> getExpenseSummary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        
        log.debug("Getting expense summary from {} to {}", fromDate, toDate);
        
        LocalDateTime start = fromDate != null ? fromDate.atStartOfDay() : 
                             LocalDateTime.now().minusMonths(1);
        LocalDateTime end = toDate != null ? toDate.atTime(23, 59, 59) : 
                           LocalDateTime.now();
        
        ExpenseSummaryDto summary = expenseService.getExpenseSummary(start, end);
        return ResponseEntity.ok(summary);
    }
    
    @GetMapping("/categories")
    @Operation(summary = "Get expense categories", 
               description = "Get list of available expense categories")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<String>> getExpenseCategories() {
        log.debug("Getting expense categories");
        
        List<String> categories = expenseService.getExpenseCategories();
        return ResponseEntity.ok(categories);
    }
    
    @GetMapping("/analytics")
    @Operation(summary = "Get expense analytics", 
               description = "Get detailed analytics and trends for user's expenses")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ExpenseAnalyticsDto> getExpenseAnalytics(
            @RequestParam(defaultValue = "12") int months) {
        
        log.debug("Getting expense analytics for {} months", months);
        
        ExpenseAnalyticsDto analytics = expenseService.getExpenseAnalytics(months);
        return ResponseEntity.ok(analytics);
    }
    
    @PostMapping("/bulk-import")
    @Operation(summary = "Bulk import expenses", 
               description = "Import multiple expenses from CSV file")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BulkImportResponseDto> bulkImportExpenses(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "skipValidation", defaultValue = "false") boolean skipValidation) {
        
        log.info("Bulk importing expenses from file: {}", file.getOriginalFilename());
        
        BulkImportResponseDto response = expenseService.bulkImportExpenses(file, skipValidation);
        return ResponseEntity.ok(response);
    }
    
    // Manager/Admin endpoints
    
    @GetMapping("/pending-approval")
    @Operation(summary = "Get expenses pending approval", 
               description = "Get list of expenses waiting for approval (manager/admin only)")
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<Page<ExpenseResponseDto>> getPendingApprovals(
            @PageableDefault(size = 20) Pageable pageable) {
        
        log.debug("Getting expenses pending approval");
        
        Page<ExpenseResponseDto> expenses = expenseService.getExpensesPendingApproval(pageable);
        return ResponseEntity.ok(expenses);
    }
    
    @GetMapping("/team-summary")
    @Operation(summary = "Get team expense summary", 
               description = "Get expense summary for team members (manager only)")
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getTeamExpenseSummary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        
        log.debug("Getting team expense summary");
        
        LocalDateTime start = fromDate != null ? fromDate.atStartOfDay() : 
                             LocalDateTime.now().minusMonths(1);
        LocalDateTime end = toDate != null ? toDate.atTime(23, 59, 59) : 
                           LocalDateTime.now();
        
        Map<String, Object> summary = expenseService.getTeamExpenseSummary(start, end);
        return ResponseEntity.ok(summary);
    }
    
    // Report endpoints
    
    @PostMapping("/reports/generate")
    @Operation(summary = "Generate expense report", 
               description = "Generate expense report in specified format")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ExpenseReportDto> generateExpenseReport(
            @Valid @RequestBody GenerateReportRequestDto request) {
        
        log.info("Generating expense report: {} format", request.getFormat());
        
        ExpenseReportDto report = expenseReportService.generateReport(request);
        return ResponseEntity.ok(report);
    }
    
    @GetMapping("/reports/{reportId}/download")
    @Operation(summary = "Download expense report", 
               description = "Download generated expense report file")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<byte[]> downloadExpenseReport(@PathVariable UUID reportId) {
        log.info("Downloading expense report: {}", reportId);
        
        return expenseReportService.downloadReport(reportId);
    }
    
    @GetMapping("/mileage/calculate")
    @Operation(summary = "Calculate mileage expense", 
               description = "Calculate mileage-based expense amount")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<MileageCalculationDto> calculateMileageExpense(
            @RequestParam BigDecimal distance,
            @RequestParam String unit,
            @RequestParam(required = false) String vehicleType) {
        
        log.debug("Calculating mileage expense for {} {}", distance, unit);
        
        MileageCalculationDto calculation = expenseService.calculateMileageExpense(
            distance, unit, vehicleType);
        return ResponseEntity.ok(calculation);
    }
}