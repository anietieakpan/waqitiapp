package com.waqiti.common.kafka.dlq.controller;

import com.waqiti.common.kafka.dlq.model.ManualReviewCase;
import com.waqiti.common.kafka.dlq.service.ManualReviewService;
import com.waqiti.common.kafka.dlq.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Manual Review Dashboard operations.
 * Provides endpoints for managing DLQ cases requiring manual intervention.
 *
 * Security: Requires ADMIN or DLQ_REVIEWER role
 *
 * @author Waqiti Platform
 * @version 1.0
 */
@RestController
@RequestMapping("/api/admin/manual-review")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Manual Review", description = "DLQ Manual Review Operations")
@PreAuthorize("hasAnyRole('ADMIN', 'DLQ_REVIEWER')")
public class ManualReviewController {

    private final ManualReviewService manualReviewService;

    /**
     * Get all manual review cases with optional filtering.
     */
    @GetMapping("/cases")
    @Operation(summary = "Get manual review cases", description = "Retrieve all DLQ cases with optional filters")
    public ResponseEntity<Page<ManualReviewCase>> getCases(
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status,
            @Parameter(description = "Filter by priority") @RequestParam(required = false) String priority,
            @Parameter(description = "Filter by error type") @RequestParam(required = false) String errorType,
            @Parameter(description = "Filter by assigned user") @RequestParam(required = false) String assignedTo,
            @Parameter(description = "Filter by topic") @RequestParam(required = false) String topic,
            Pageable pageable
    ) {
        log.info("Fetching manual review cases with filters - status: {}, priority: {}, errorType: {}",
                status, priority, errorType);

        Page<ManualReviewCase> cases = manualReviewService.findCases(
                status, priority, errorType, assignedTo, topic, pageable
        );

        return ResponseEntity.ok(cases);
    }

    /**
     * Get a specific case by ID.
     */
    @GetMapping("/cases/{caseId}")
    @Operation(summary = "Get case details", description = "Retrieve detailed information about a specific case")
    public ResponseEntity<ManualReviewCase> getCaseById(
            @Parameter(description = "Case ID") @PathVariable String caseId
    ) {
        log.info("Fetching case details for ID: {}", caseId);

        return manualReviewService.findCaseById(caseId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get dashboard statistics.
     */
    @GetMapping("/stats")
    @Operation(summary = "Get dashboard statistics", description = "Retrieve aggregated statistics for the dashboard")
    public ResponseEntity<DashboardStatsDto> getStats() {
        log.info("Fetching dashboard statistics");

        DashboardStatsDto stats = manualReviewService.getDashboardStats();

        return ResponseEntity.ok(stats);
    }

    /**
     * Assign a case to a user.
     */
    @PostMapping("/cases/{caseId}/assign")
    @Operation(summary = "Assign case", description = "Assign a case to a user for review")
    public ResponseEntity<ManualReviewCase> assignCase(
            @Parameter(description = "Case ID") @PathVariable String caseId,
            @Valid @RequestBody AssignCaseRequest request
    ) {
        log.info("Assigning case {} to user {}", caseId, request.getAssignedTo());

        ManualReviewCase updatedCase = manualReviewService.assignCase(caseId, request.getAssignedTo());

        return ResponseEntity.ok(updatedCase);
    }

    /**
     * Resolve a case with resolution notes.
     */
    @PostMapping("/cases/{caseId}/resolve")
    @Operation(summary = "Resolve case", description = "Mark a case as resolved with resolution details")
    public ResponseEntity<ManualReviewCase> resolveCase(
            @Parameter(description = "Case ID") @PathVariable String caseId,
            @Valid @RequestBody ResolveCaseRequest request
    ) {
        log.info("Resolving case {} with action: {}", caseId, request.getResolutionAction());

        // PRODUCTION FIX: Service expects ResolveCaseRequest object, not individual parameters
        ManualReviewCase resolvedCase = manualReviewService.resolveCase(caseId, request);

        // If resolution action is REPROCESS or FIX_DATA, requeue the event
        if ("REPROCESS".equals(request.getResolutionAction()) ||
            "FIX_DATA".equals(request.getResolutionAction())) {
            manualReviewService.requeueEvent(caseId);
        }

        return ResponseEntity.ok(resolvedCase);
    }

    /**
     * Reject a case.
     */
    @PostMapping("/cases/{caseId}/reject")
    @Operation(summary = "Reject case", description = "Mark a case as rejected")
    public ResponseEntity<ManualReviewCase> rejectCase(
            @Parameter(description = "Case ID") @PathVariable String caseId,
            @Valid @RequestBody RejectCaseRequest request
    ) {
        log.info("Rejecting case {} by user {}", caseId, request.getRejectedBy());

        // PRODUCTION FIX: Service expects RejectCaseRequest object, not individual parameters
        ManualReviewCase rejectedCase = manualReviewService.rejectCase(caseId, request);

        return ResponseEntity.ok(rejectedCase);
    }

    /**
     * Retry a case (requeue the event).
     */
    @PostMapping("/cases/{caseId}/retry")
    @Operation(summary = "Retry case", description = "Requeue the event for retry processing")
    public ResponseEntity<Void> retryCase(
            @Parameter(description = "Case ID") @PathVariable String caseId
    ) {
        log.info("Retrying case {}", caseId);

        manualReviewService.requeueEvent(caseId);

        return ResponseEntity.ok().build();
    }

    /**
     * Get case history/audit trail.
     */
    @GetMapping("/cases/{caseId}/history")
    @Operation(summary = "Get case history", description = "Retrieve the audit trail for a case")
    public ResponseEntity<List<CaseHistoryDto>> getCaseHistory(
            @Parameter(description = "Case ID") @PathVariable String caseId
    ) {
        log.info("Fetching history for case {}", caseId);

        List<CaseHistoryDto> history = manualReviewService.getCaseHistory(caseId);

        return ResponseEntity.ok(history);
    }

    /**
     * Bulk assign cases.
     */
    @PostMapping("/cases/bulk-assign")
    @Operation(summary = "Bulk assign cases", description = "Assign multiple cases to a user")
    public ResponseEntity<Map<String, Object>> bulkAssignCases(
            @Valid @RequestBody BulkAssignRequest request
    ) {
        log.info("Bulk assigning {} cases to user {}", request.getCaseIds().size(), request.getAssignedTo());

        List<ManualReviewCase> results = manualReviewService.bulkAssignCases(request.getCaseIds(), request.getAssignedTo());
        int successCount = results.size();

        return ResponseEntity.ok(Map.of(
                "totalRequested", request.getCaseIds().size(),
                "successCount", successCount,
                "failureCount", request.getCaseIds().size() - successCount
        ));
    }

    /**
     * Bulk resolve cases.
     */
    @PostMapping("/cases/bulk-resolve")
    @Operation(summary = "Bulk resolve cases", description = "Resolve multiple cases with the same action")
    public ResponseEntity<Map<String, Object>> bulkResolveCases(
            @Valid @RequestBody BulkResolveRequest request
    ) {
        log.info("Bulk resolving {} cases with action: {}",
                request.getCaseIds().size(), request.getResolutionAction());

        List<ManualReviewCase> results = manualReviewService.bulkResolveCases(
                request.getCaseIds(),
                request.getResolutionAction(),
                request.getResolutionNotes(),
                request.getResolvedBy()
        );
        int successCount = results.size();

        return ResponseEntity.ok(Map.of(
                "totalRequested", request.getCaseIds().size(),
                "successCount", successCount,
                "failureCount", request.getCaseIds().size() - successCount
        ));
    }

    /**
     * Get cases by priority for quick filtering.
     */
    @GetMapping("/cases/priority/{priority}")
    @Operation(summary = "Get cases by priority", description = "Retrieve all cases of a specific priority")
    public ResponseEntity<List<ManualReviewCase>> getCasesByPriority(
            @Parameter(description = "Priority level") @PathVariable String priority
    ) {
        log.info("Fetching cases with priority: {}", priority);

        List<ManualReviewCase> cases = manualReviewService.findCasesByPriority(priority);

        return ResponseEntity.ok(cases);
    }

    /**
     * Get critical cases requiring immediate attention.
     */
    @GetMapping("/cases/critical")
    @Operation(summary = "Get critical cases", description = "Retrieve all critical priority cases")
    public ResponseEntity<List<ManualReviewCase>> getCriticalCases() {
        log.info("Fetching critical cases");

        List<ManualReviewCase> cases = manualReviewService.findCriticalCases();

        return ResponseEntity.ok(cases);
    }

    /**
     * Get aging cases (pending for more than threshold hours).
     */
    @GetMapping("/cases/aging")
    @Operation(summary = "Get aging cases", description = "Retrieve cases pending longer than threshold")
    public ResponseEntity<List<ManualReviewCase>> getAgingCases(
            @Parameter(description = "Hours threshold") @RequestParam(defaultValue = "24") int hours
    ) {
        log.info("Fetching cases aging more than {} hours", hours);

        List<ManualReviewCase> cases = manualReviewService.findAgingCases(hours);

        return ResponseEntity.ok(cases);
    }

    /**
     * Get my assigned cases (for current user).
     */
    @GetMapping("/cases/my-cases")
    @Operation(summary = "Get my assigned cases", description = "Retrieve cases assigned to current user")
    public ResponseEntity<List<ManualReviewCase>> getMyCases() {
        String currentUser = getCurrentUser();
        log.info("Fetching cases assigned to user: {}", currentUser);

        List<ManualReviewCase> cases = manualReviewService.findCasesByAssignedTo(currentUser);

        return ResponseEntity.ok(cases);
    }

    /**
     * Get error type distribution statistics.
     */
    @GetMapping("/stats/error-types")
    @Operation(summary = "Get error type distribution", description = "Get statistics on error types")
    public ResponseEntity<Map<String, Long>> getErrorTypeDistribution() {
        log.info("Fetching error type distribution");

        Map<String, Long> distribution = manualReviewService.getErrorTypeDistribution();

        return ResponseEntity.ok(distribution);
    }

    /**
     * Get topic distribution statistics.
     */
    @GetMapping("/stats/topics")
    @Operation(summary = "Get topic distribution", description = "Get statistics on topics with most errors")
    public ResponseEntity<Map<String, Long>> getTopicDistribution() {
        log.info("Fetching topic distribution");

        Map<String, Long> distribution = manualReviewService.getTopicDistribution();

        return ResponseEntity.ok(distribution);
    }

    /**
     * Get resolution metrics.
     */
    @GetMapping("/stats/resolution-metrics")
    @Operation(summary = "Get resolution metrics", description = "Get metrics on resolution times and actions")
    public ResponseEntity<ResolutionMetricsDto> getResolutionMetrics(
            @Parameter(description = "Start date") @RequestParam(required = false) LocalDateTime startDate,
            @Parameter(description = "End date") @RequestParam(required = false) LocalDateTime endDate
    ) {
        log.info("Fetching resolution metrics from {} to {}", startDate, endDate);

        ResolutionMetricsDto metrics = manualReviewService.getResolutionMetrics(startDate, endDate);

        return ResponseEntity.ok(metrics);
    }

    /**
     * Search cases by event ID or error message.
     */
    @GetMapping("/cases/search")
    @Operation(summary = "Search cases", description = "Search cases by event ID or error message")
    public ResponseEntity<List<ManualReviewCase>> searchCases(
            @Parameter(description = "Search query") @RequestParam String query
    ) {
        log.info("Searching cases with query: {}", query);

        List<ManualReviewCase> cases = manualReviewService.searchCases(query);

        return ResponseEntity.ok(cases);
    }

    /**
     * Helper method to get current user from security context.
     * In production, this would extract from JWT or Spring Security context.
     */
    private String getCurrentUser() {
        // TODO: Extract from SecurityContextHolder in production
        return "current-user";
    }
}
