package com.waqiti.reconciliation.controller;

import com.waqiti.common.api.ApiResponse;
import com.waqiti.common.security.SecurityContextUtil;
import com.waqiti.common.security.AdminOperationValidator;
import com.waqiti.reconciliation.dto.*;
import com.waqiti.reconciliation.service.ReconciliationService;
import com.waqiti.reconciliation.service.MatchingService;
import com.waqiti.reconciliation.service.DiscrepancyResolutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reconciliation")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reconciliation Management", description = "Transaction and account reconciliation operations")
@Validated
public class ReconciliationController {

    private final ReconciliationService reconciliationService;
    private final MatchingService matchingService;
    private final DiscrepancyResolutionService resolutionService;
    private final AdminOperationValidator adminOperationValidator;

    // Reconciliation Process Endpoints
    @PostMapping("/initiate")
    @Operation(summary = "Initiate reconciliation process")
    @PreAuthorize("hasRole('RECONCILER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReconciliationResponse>> initiateReconciliation(
            @Valid @RequestBody InitiateReconciliationRequest request) {
        log.info("Initiating reconciliation: {}", request.getReconciliationType());
        
        ReconciliationResponse response = reconciliationService.initiateReconciliation(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/batch")
    @Operation(summary = "Initiate batch reconciliation")
    @PreAuthorize("hasRole('RECONCILER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BatchReconciliationResponse>> initiateBatchReconciliation(
            @Valid @RequestBody BatchReconciliationRequest request) {
        log.info("Initiating batch reconciliation for {} items", request.getReconciliationItems().size());
        
        BatchReconciliationResponse response = reconciliationService.initiateBatchReconciliation(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "Get reconciliations")
    @PreAuthorize("hasRole('RECONCILER') or hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<ApiResponse<Page<ReconciliationResponse>>> getReconciliations(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable) {
        
        ReconciliationFilter filter = ReconciliationFilter.builder()
                .type(type)
                .status(status)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        
        Page<ReconciliationResponse> reconciliations = reconciliationService.getReconciliations(filter, pageable);
        return ResponseEntity.ok(ApiResponse.success(reconciliations));
    }

    @GetMapping("/{reconciliationId}")
    @Operation(summary = "Get reconciliation details")
    @PreAuthorize("hasRole('RECONCILER') or hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<ApiResponse<ReconciliationDetailResponse>> getReconciliationDetails(
            @PathVariable UUID reconciliationId) {
        
        ReconciliationDetailResponse details = reconciliationService.getReconciliationDetails(reconciliationId);
        return ResponseEntity.ok(ApiResponse.success(details));
    }

    @PostMapping("/{reconciliationId}/complete")
    @Operation(summary = "Complete reconciliation")
    @PreAuthorize("hasRole('RECONCILER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReconciliationResponse>> completeReconciliation(
            @PathVariable UUID reconciliationId,
            @Valid @RequestBody CompleteReconciliationRequest request) {
        log.info("Completing reconciliation: {}", reconciliationId);
        
        ReconciliationResponse response = reconciliationService.completeReconciliation(reconciliationId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Matching Endpoints
    @PostMapping("/match/auto")
    @Operation(summary = "Perform automatic matching")
    @PreAuthorize("hasRole('RECONCILER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MatchingResultResponse>> performAutoMatching(
            @Valid @RequestBody AutoMatchingRequest request) {
        log.info("Performing auto-matching for reconciliation: {}", request.getReconciliationId());
        
        MatchingResultResponse result = matchingService.performAutoMatching(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/match/manual")
    @Operation(summary = "Perform manual matching")
    @PreAuthorize("hasRole('RECONCILER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MatchingResultResponse>> performManualMatching(
            @Valid @RequestBody ManualMatchingRequest request) {
        log.info("Performing manual matching: {} items", request.getMatchPairs().size());
        
        MatchingResultResponse result = matchingService.performManualMatching(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/match/suggestions")
    @Operation(summary = "Get matching suggestions")
    @PreAuthorize("hasRole('RECONCILER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<MatchingSuggestionResponse>>> getMatchingSuggestions(
            @RequestParam UUID reconciliationId,
            @RequestParam(required = false) Double confidenceThreshold) {
        
        List<MatchingSuggestionResponse> suggestions = 
                matchingService.getMatchingSuggestions(reconciliationId, confidenceThreshold);
        return ResponseEntity.ok(ApiResponse.success(suggestions));
    }

    @PostMapping("/match/{matchId}/unmatch")
    @Operation(summary = "Unmatch items")
    @PreAuthorize("hasRole('RECONCILER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> unmatchItems(
            @PathVariable UUID matchId,
            @Valid @RequestBody UnmatchRequest request) {
        log.info("Unmatching items: {}", matchId);
        
        matchingService.unmatchItems(matchId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // Discrepancy Endpoints
    @GetMapping("/discrepancies")
    @Operation(summary = "Get discrepancies")
    @PreAuthorize("hasRole('RECONCILER') or hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<ApiResponse<Page<DiscrepancyResponse>>> getDiscrepancies(
            @RequestParam(required = false) UUID reconciliationId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            Pageable pageable) {
        
        DiscrepancyFilter filter = DiscrepancyFilter.builder()
                .reconciliationId(reconciliationId)
                .type(type)
                .status(status)
                .severity(severity)
                .build();
        
        Page<DiscrepancyResponse> discrepancies = resolutionService.getDiscrepancies(filter, pageable);
        return ResponseEntity.ok(ApiResponse.success(discrepancies));
    }

    @PostMapping("/discrepancies/{discrepancyId}/resolve")
    @Operation(summary = "Resolve discrepancy")
    @PreAuthorize("hasRole('RECONCILER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DiscrepancyResponse>> resolveDiscrepancy(
            @PathVariable UUID discrepancyId,
            @Valid @RequestBody ResolveDiscrepancyRequest request) {
        log.info("Resolving discrepancy: {}", discrepancyId);
        
        DiscrepancyResponse response = resolutionService.resolveDiscrepancy(discrepancyId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/discrepancies/{discrepancyId}/escalate")
    @Operation(summary = "Escalate discrepancy")
    @PreAuthorize("hasRole('RECONCILER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DiscrepancyResponse>> escalateDiscrepancy(
            @PathVariable UUID discrepancyId,
            @Valid @RequestBody EscalateDiscrepancyRequest request) {
        log.info("Escalating discrepancy: {}", discrepancyId);
        
        DiscrepancyResponse response = resolutionService.escalateDiscrepancy(discrepancyId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Import/Export Endpoints
    @PostMapping("/import/bank-statement")
    @Operation(summary = "Import bank statement with security validation")
    @PreAuthorize("hasRole('RECONCILER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ImportResultResponse>> importBankStatement(
            @RequestParam("file") MultipartFile file,
            @RequestParam("bankAccountId") String bankAccountId,
            @RequestParam("format") String format) {
        log.info("Importing bank statement for account: {} (size: {} bytes)", 
                bankAccountId, file.getSize());
        
        // Additional security checks for sensitive financial data
        if (file.getSize() == 0) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("File is empty"));
        }
        
        if (file.getOriginalFilename() == null || file.getOriginalFilename().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid filename"));
        }
        
        ImportBankStatementRequest request = ImportBankStatementRequest.builder()
                .file(file)
                .bankAccountId(bankAccountId)
                .format(format)
                .build();
        
        ImportResultResponse result = reconciliationService.importBankStatement(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/import/transactions")
    @Operation(summary = "Import transactions with security validation")
    @PreAuthorize("hasRole('RECONCILER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ImportResultResponse>> importTransactions(
            @RequestParam("file") MultipartFile file,
            @RequestParam("source") String source,
            @RequestParam("format") String format) {
        log.info("Importing transactions from source: {} (size: {} bytes)", 
                source, file.getSize());
        
        // Additional security checks for sensitive transaction data
        if (file.getSize() == 0) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("File is empty"));
        }
        
        if (file.getOriginalFilename() == null || file.getOriginalFilename().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid filename"));
        }
        
        ImportTransactionsRequest request = ImportTransactionsRequest.builder()
                .file(file)
                .source(source)
                .format(format)
                .build();
        
        ImportResultResponse result = reconciliationService.importTransactions(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/export/{reconciliationId}")
    @Operation(summary = "Export reconciliation report")
    @PreAuthorize("hasRole('RECONCILER') or hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<byte[]> exportReconciliationReport(
            @PathVariable UUID reconciliationId,
            @RequestParam(defaultValue = "pdf") String format) {
        
        // SECURITY FIX: Enhanced authorization validation for sensitive data export
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();
        String userRole = SecurityContextUtil.getHighestRole();
        
        log.info("SECURITY: User {} with role {} attempting to export reconciliation report {}", 
                authenticatedUserId, userRole, reconciliationId);
        
        // SECURITY FIX: Additional validation for export operations
        adminOperationValidator.validateExportOperation(
                authenticatedUserId, 
                reconciliationId, 
                "RECONCILIATION_EXPORT",
                userRole);
        
        // SECURITY FIX: Validate format parameter to prevent injection
        if (!isValidExportFormat(format)) {
            log.warn("SECURITY: Invalid export format attempted by user {}: {}", authenticatedUserId, format);
            throw new IllegalArgumentException("Invalid export format: " + format);
        }
        
        // SECURITY FIX: Validate reconciliation exists and user has access
        if (!reconciliationService.hasAccessToReconciliation(reconciliationId, authenticatedUserId)) {
            log.warn("SECURITY: User {} attempted to access unauthorized reconciliation {}", 
                    authenticatedUserId, reconciliationId);
            throw new SecurityException("Access denied to reconciliation data");
        }
        
        byte[] reportData = reconciliationService.exportReconciliationReport(reconciliationId, format);
        
        // SECURITY FIX: Audit log for sensitive export operation
        log.info("SECURITY: Successfully exported reconciliation report {} by user {} (format: {}, size: {} bytes)", 
                reconciliationId, authenticatedUserId, format, reportData.length);
        
        String contentType = format.equals("csv") ? "text/csv" : "application/pdf";
        String filename = "reconciliation-report-" + reconciliationId + "." + format;
        
        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .header("Content-Disposition", "attachment; filename=" + filename)
                .header("X-Audit-User", authenticatedUserId.toString())
                .header("X-Audit-Operation", "RECONCILIATION_EXPORT")
                .body(reportData);
    }
    
    /**
     * SECURITY FIX: Validate export format to prevent injection attacks
     */
    private boolean isValidExportFormat(String format) {
        return format != null && 
               (format.equals("pdf") || format.equals("csv") || format.equals("xlsx")) &&
               format.matches("^[a-zA-Z0-9]+$"); // Only alphanumeric characters
    }

    // Analytics Endpoints
    @GetMapping("/analytics/summary")
    @Operation(summary = "Get reconciliation analytics")
    @PreAuthorize("hasRole('RECONCILER') or hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<ApiResponse<ReconciliationAnalyticsResponse>> getReconciliationAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String type) {
        
        ReconciliationAnalyticsResponse analytics = 
                reconciliationService.getReconciliationAnalytics(startDate, endDate, type);
        return ResponseEntity.ok(ApiResponse.success(analytics));
    }

    @GetMapping("/analytics/trends")
    @Operation(summary = "Get reconciliation trends")
    @PreAuthorize("hasRole('RECONCILER') or hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<ApiResponse<ReconciliationTrendsResponse>> getReconciliationTrends(
            @RequestParam(defaultValue = "30") Integer days) {
        
        ReconciliationTrendsResponse trends = reconciliationService.getReconciliationTrends(days);
        return ResponseEntity.ok(ApiResponse.success(trends));
    }

    // Configuration Endpoints
    @GetMapping("/rules")
    @Operation(summary = "Get reconciliation rules")
    @PreAuthorize("hasRole('RECONCILER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ReconciliationRuleResponse>>> getReconciliationRules(
            @RequestParam(required = false) String type) {
        
        List<ReconciliationRuleResponse> rules = reconciliationService.getReconciliationRules(type);
        return ResponseEntity.ok(ApiResponse.success(rules));
    }

    @PostMapping("/rules")
    @Operation(summary = "Create reconciliation rule")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReconciliationRuleResponse>> createReconciliationRule(
            @Valid @RequestBody CreateReconciliationRuleRequest request) {
        
        // SECURITY FIX: Enhanced authorization for rule creation
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();
        String userRole = SecurityContextUtil.getHighestRole();
        
        log.info("SECURITY: User {} with role {} attempting to create reconciliation rule: {}", 
                authenticatedUserId, userRole, request.getRuleName());
        
        // SECURITY FIX: Validate admin operation with additional checks
        adminOperationValidator.validateAdminOperation(
                authenticatedUserId, 
                "RECONCILIATION_RULE_CREATE",
                userRole,
                request.getRuleName());
        
        // SECURITY FIX: Validate rule configuration for security issues
        validateRuleConfiguration(request);
        
        ReconciliationRuleResponse rule = reconciliationService.createReconciliationRule(request);
        
        // SECURITY FIX: Audit log for rule creation
        log.info("SECURITY: Successfully created reconciliation rule {} by user {}", 
                rule.getId(), authenticatedUserId);
        
        return ResponseEntity.ok(ApiResponse.success(rule));
    }

    @PutMapping("/rules/{ruleId}")
    @Operation(summary = "Update reconciliation rule")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReconciliationRuleResponse>> updateReconciliationRule(
            @PathVariable UUID ruleId,
            @Valid @RequestBody UpdateReconciliationRuleRequest request) {
        
        // SECURITY FIX: Enhanced authorization for rule modification
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();
        String userRole = SecurityContextUtil.getHighestRole();
        
        log.info("SECURITY: User {} with role {} attempting to update reconciliation rule: {}", 
                authenticatedUserId, userRole, ruleId);
        
        // SECURITY FIX: Validate admin operation for rule modification
        adminOperationValidator.validateAdminOperation(
                authenticatedUserId, 
                "RECONCILIATION_RULE_UPDATE",
                userRole,
                ruleId.toString());
        
        // SECURITY FIX: Validate that rule exists and user has access
        if (!reconciliationService.hasAccessToRule(ruleId, authenticatedUserId)) {
            log.warn("SECURITY: User {} attempted to access unauthorized rule {}", 
                    authenticatedUserId, ruleId);
            throw new SecurityException("Access denied to reconciliation rule");
        }
        
        // SECURITY FIX: Validate rule configuration for security issues
        validateUpdateRuleConfiguration(request);
        
        ReconciliationRuleResponse rule = reconciliationService.updateReconciliationRule(ruleId, request);
        
        // SECURITY FIX: Audit log for rule modification
        log.info("SECURITY: Successfully updated reconciliation rule {} by user {}", 
                ruleId, authenticatedUserId);
        
        return ResponseEntity.ok(ApiResponse.success(rule));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.success("Reconciliation service is healthy"));
    }
    
    /**
     * SECURITY FIX: Validate rule configuration for security vulnerabilities
     */
    private void validateRuleConfiguration(CreateReconciliationRuleRequest request) {
        // SECURITY: Validate rule name for injection attacks
        if (request.getRuleName() == null || !request.getRuleName().matches("^[a-zA-Z0-9_\\-\\s]+$")) {
            throw new IllegalArgumentException("Invalid rule name format");
        }
        
        // SECURITY: Validate rule criteria for potential security issues
        if (request.getCriteria() != null && request.getCriteria().toString().contains("DROP")) {
            throw new IllegalArgumentException("Invalid rule criteria containing dangerous keywords");
        }
        
        // SECURITY: Validate rule expressions for injection
        if (request.getMatchingExpression() != null && 
            (request.getMatchingExpression().contains("script") || 
             request.getMatchingExpression().contains("eval") ||
             request.getMatchingExpression().contains("exec"))) {
            throw new IllegalArgumentException("Invalid rule expression containing dangerous functions");
        }
        
        log.debug("SECURITY: Rule configuration validation passed for rule: {}", request.getRuleName());
    }
    
    /**
     * SECURITY FIX: Validate update rule configuration for security vulnerabilities
     */
    private void validateUpdateRuleConfiguration(UpdateReconciliationRuleRequest request) {
        // SECURITY: Similar validation as create but for update operations
        if (request.getRuleName() != null && !request.getRuleName().matches("^[a-zA-Z0-9_\\-\\s]+$")) {
            throw new IllegalArgumentException("Invalid rule name format");
        }
        
        if (request.getCriteria() != null && request.getCriteria().toString().contains("DROP")) {
            throw new IllegalArgumentException("Invalid rule criteria containing dangerous keywords");
        }
        
        if (request.getMatchingExpression() != null && 
            (request.getMatchingExpression().contains("script") || 
             request.getMatchingExpression().contains("eval") ||
             request.getMatchingExpression().contains("exec"))) {
            throw new IllegalArgumentException("Invalid rule expression containing dangerous functions");
        }
        
        log.debug("SECURITY: Update rule configuration validation passed");
    }
}