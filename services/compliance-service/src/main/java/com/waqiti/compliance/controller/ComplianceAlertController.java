package com.waqiti.compliance.controller;

import com.waqiti.compliance.model.ComplianceAlert;
import com.waqiti.compliance.service.ComplianceAlertService;
import com.waqiti.compliance.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Compliance Alert Investigation operations.
 * Provides endpoints for managing OFAC, AML, KYC, Sanctions, and PEP alerts.
 *
 * Security: Requires ADMIN or COMPLIANCE_OFFICER role
 *
 * Regulatory Context:
 * - OFAC screening required by 31 CFR Part 501
 * - AML compliance per Bank Secrecy Act
 * - PEP screening per FATF Recommendation 12
 * - SAR filing required for blocked transactions (31 CFR 1020.320)
 *
 * @author Waqiti Platform Engineering
 * @version 1.0
 */
@RestController
@RequestMapping("/api/admin/compliance")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Compliance Alerts", description = "Compliance Alert Investigation Operations")
@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER')")
public class ComplianceAlertController {

    private final ComplianceAlertService complianceAlertService;

    /**
     * Get all compliance alerts with optional filtering.
     */
    @GetMapping("/alerts")
    @Operation(summary = "Get compliance alerts", description = "Retrieve all compliance alerts with optional filters")
    public ResponseEntity<Page<ComplianceAlert>> getAlerts(
            @Parameter(description = "Filter by alert type") @RequestParam(required = false) String type,
            @Parameter(description = "Filter by severity") @RequestParam(required = false) String severity,
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status,
            @Parameter(description = "Filter by assigned user") @RequestParam(required = false) String assignedTo,
            @Parameter(description = "Filter by user ID") @RequestParam(required = false) String userId,
            Pageable pageable
    ) {
        log.info("Fetching compliance alerts with filters - type: {}, severity: {}, status: {}",
                type, severity, status);

        Page<ComplianceAlert> alerts = complianceAlertService.findAlerts(
                type, severity, status, assignedTo, userId, pageable
        );

        return ResponseEntity.ok(alerts);
    }

    /**
     * Get a specific alert by ID.
     */
    @GetMapping("/alerts/{alertId}")
    @Operation(summary = "Get alert details", description = "Retrieve detailed information about a specific alert")
    public ResponseEntity<ComplianceAlert> getAlertById(
            @Parameter(description = "Alert ID") @PathVariable String alertId
    ) {
        log.info("Fetching alert details for ID: {}", alertId);

        return complianceAlertService.findAlertById(alertId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get dashboard statistics.
     */
    @GetMapping("/stats")
    @Operation(summary = "Get dashboard statistics", description = "Retrieve aggregated statistics for the dashboard")
    public ResponseEntity<ComplianceDashboardStatsDto> getStats() {
        log.info("Fetching compliance dashboard statistics");

        ComplianceDashboardStatsDto stats = complianceAlertService.getDashboardStats();

        return ResponseEntity.ok(stats);
    }

    /**
     * Assign an alert to a compliance officer.
     */
    @PostMapping("/alerts/{alertId}/assign")
    @Operation(summary = "Assign alert", description = "Assign an alert to a compliance officer")
    public ResponseEntity<ComplianceAlert> assignAlert(
            @Parameter(description = "Alert ID") @PathVariable String alertId,
            @Valid @RequestBody AssignAlertRequest request
    ) {
        log.info("Assigning alert {} to user {}", alertId, request.getAssignedTo());

        ComplianceAlert updatedAlert = complianceAlertService.assignAlert(alertId, request.getAssignedTo());

        return ResponseEntity.ok(updatedAlert);
    }

    /**
     * Start investigation on an alert.
     */
    @PostMapping("/alerts/{alertId}/start-investigation")
    @Operation(summary = "Start investigation", description = "Change alert status to INVESTIGATING")
    public ResponseEntity<ComplianceAlert> startInvestigation(
            @Parameter(description = "Alert ID") @PathVariable String alertId
    ) {
        log.info("Starting investigation for alert {}", alertId);

        ComplianceAlert updatedAlert = complianceAlertService.startInvestigation(alertId);

        return ResponseEntity.ok(updatedAlert);
    }

    /**
     * Make compliance decision on an alert.
     */
    @PostMapping("/alerts/{alertId}/decide")
    @Operation(summary = "Make decision", description = "Make compliance decision (APPROVE, BLOCK, ESCALATE)")
    public ResponseEntity<ComplianceAlert> makeDecision(
            @Parameter(description = "Alert ID") @PathVariable String alertId,
            @Valid @RequestBody MakeDecisionRequest request
    ) {
        log.info("Making decision on alert {} - Decision: {}", alertId, request.getDecision());

        ComplianceAlert decidedAlert = complianceAlertService.makeDecision(
                alertId,
                request.getDecision(),
                request.getInvestigationNotes(),
                request.getDecidedBy()
        );

        // If decision is BLOCK, trigger SAR filing process
        if ("BLOCK".equals(request.getDecision())) {
            complianceAlertService.initiateSarFiling(alertId);
        }

        return ResponseEntity.ok(decidedAlert);
    }

    /**
     * Escalate an alert to senior compliance.
     */
    @PostMapping("/alerts/{alertId}/escalate")
    @Operation(summary = "Escalate alert", description = "Escalate alert to senior compliance officer")
    public ResponseEntity<ComplianceAlert> escalateAlert(
            @Parameter(description = "Alert ID") @PathVariable String alertId,
            @Valid @RequestBody EscalateAlertRequest request
    ) {
        log.info("Escalating alert {} by user {}", alertId, request.getEscalatedBy());

        ComplianceAlert escalatedAlert = complianceAlertService.escalateAlert(
                alertId,
                request.getEscalationNotes(),
                request.getEscalatedBy()
        );

        return ResponseEntity.ok(escalatedAlert);
    }

    /**
     * Get alert history/audit trail.
     */
    @GetMapping("/alerts/{alertId}/history")
    @Operation(summary = "Get alert history", description = "Retrieve the audit trail for an alert")
    public ResponseEntity<List<AlertHistoryDto>> getAlertHistory(
            @Parameter(description = "Alert ID") @PathVariable String alertId
    ) {
        log.info("Fetching history for alert {}", alertId);

        List<AlertHistoryDto> history = complianceAlertService.getAlertHistory(alertId);

        return ResponseEntity.ok(history);
    }

    /**
     * Get critical alerts requiring immediate attention.
     */
    @GetMapping("/alerts/critical")
    @Operation(summary = "Get critical alerts", description = "Retrieve all critical severity alerts")
    public ResponseEntity<List<ComplianceAlert>> getCriticalAlerts() {
        log.info("Fetching critical alerts");

        List<ComplianceAlert> alerts = complianceAlertService.findCriticalAlerts();

        return ResponseEntity.ok(alerts);
    }

    /**
     * Get OFAC/Sanctions matches requiring immediate review.
     */
    @GetMapping("/alerts/ofac-sanctions")
    @Operation(summary = "Get OFAC/Sanctions alerts", description = "Retrieve all OFAC and sanctions alerts")
    public ResponseEntity<List<ComplianceAlert>> getOfacSanctionsAlerts() {
        log.info("Fetching OFAC/Sanctions alerts");

        List<ComplianceAlert> alerts = complianceAlertService.findOfacSanctionsAlerts();

        return ResponseEntity.ok(alerts);
    }

    /**
     * Get AML high-risk alerts.
     */
    @GetMapping("/alerts/aml-high-risk")
    @Operation(summary = "Get AML high-risk alerts", description = "Retrieve AML alerts with risk score >= 80")
    public ResponseEntity<List<ComplianceAlert>> getAmlHighRiskAlerts() {
        log.info("Fetching AML high-risk alerts");

        List<ComplianceAlert> alerts = complianceAlertService.findAmlHighRiskAlerts();

        return ResponseEntity.ok(alerts);
    }

    /**
     * Get aging alerts (pending for more than threshold hours).
     */
    @GetMapping("/alerts/aging")
    @Operation(summary = "Get aging alerts", description = "Retrieve alerts pending longer than threshold")
    public ResponseEntity<List<ComplianceAlert>> getAgingAlerts(
            @Parameter(description = "Hours threshold") @RequestParam(defaultValue = "24") int hours
    ) {
        log.info("Fetching alerts aging more than {} hours", hours);

        List<ComplianceAlert> alerts = complianceAlertService.findAgingAlerts(hours);

        return ResponseEntity.ok(alerts);
    }

    /**
     * Get my assigned alerts (for current user).
     */
    @GetMapping("/alerts/my-alerts")
    @Operation(summary = "Get my assigned alerts", description = "Retrieve alerts assigned to current user")
    public ResponseEntity<List<ComplianceAlert>> getMyAlerts() {
        String currentUser = getCurrentUser();
        log.info("Fetching alerts assigned to user: {}", currentUser);

        List<ComplianceAlert> alerts = complianceAlertService.findAlertsByAssignedTo(currentUser);

        return ResponseEntity.ok(alerts);
    }

    /**
     * Get alert type distribution statistics.
     */
    @GetMapping("/stats/alert-types")
    @Operation(summary = "Get alert type distribution", description = "Get statistics on alert types")
    public ResponseEntity<Map<String, Long>> getAlertTypeDistribution() {
        log.info("Fetching alert type distribution");

        Map<String, Long> distribution = complianceAlertService.getAlertTypeDistribution();

        return ResponseEntity.ok(distribution);
    }

    /**
     * Get severity distribution statistics.
     */
    @GetMapping("/stats/severity")
    @Operation(summary = "Get severity distribution", description = "Get statistics on alert severity levels")
    public ResponseEntity<Map<String, Long>> getSeverityDistribution() {
        log.info("Fetching severity distribution");

        Map<String, Long> distribution = complianceAlertService.getSeverityDistribution();

        return ResponseEntity.ok(distribution);
    }

    /**
     * Get decision metrics.
     */
    @GetMapping("/stats/decision-metrics")
    @Operation(summary = "Get decision metrics", description = "Get metrics on investigation times and decisions")
    public ResponseEntity<DecisionMetricsDto> getDecisionMetrics(
            @Parameter(description = "Start date") @RequestParam(required = false) LocalDateTime startDate,
            @Parameter(description = "End date") @RequestParam(required = false) LocalDateTime endDate
    ) {
        log.info("Fetching decision metrics from {} to {}", startDate, endDate);

        DecisionMetricsDto metrics = complianceAlertService.getDecisionMetrics(startDate, endDate);

        return ResponseEntity.ok(metrics);
    }

    /**
     * Search alerts by user name, transaction ID, or description.
     */
    @GetMapping("/alerts/search")
    @Operation(summary = "Search alerts", description = "Search alerts by various criteria")
    public ResponseEntity<List<ComplianceAlert>> searchAlerts(
            @Parameter(description = "Search query") @RequestParam String query
    ) {
        log.info("Searching alerts with query: {}", query);

        List<ComplianceAlert> alerts = complianceAlertService.searchAlerts(query);

        return ResponseEntity.ok(alerts);
    }

    /**
     * Bulk assign alerts.
     */
    @PostMapping("/alerts/bulk-assign")
    @Operation(summary = "Bulk assign alerts", description = "Assign multiple alerts to a user")
    public ResponseEntity<Map<String, Object>> bulkAssignAlerts(
            @Valid @RequestBody BulkAssignAlertsRequest request
    ) {
        log.info("Bulk assigning {} alerts to user {}", request.getAlertIds().size(), request.getAssignedTo());

        int successCount = complianceAlertService.bulkAssignAlerts(request.getAlertIds(), request.getAssignedTo());

        return ResponseEntity.ok(Map.of(
                "totalRequested", request.getAlertIds().size(),
                "successCount", successCount,
                "failureCount", request.getAlertIds().size() - successCount
        ));
    }

    /**
     * Get alerts by match score range.
     */
    @GetMapping("/alerts/match-score-range")
    @Operation(summary = "Get alerts by match score", description = "Retrieve alerts within match score range")
    public ResponseEntity<List<ComplianceAlert>> getAlertsByMatchScoreRange(
            @Parameter(description = "Minimum match score") @RequestParam int minScore,
            @Parameter(description = "Maximum match score") @RequestParam(defaultValue = "100") int maxScore
    ) {
        log.info("Fetching alerts with match score between {} and {}", minScore, maxScore);

        List<ComplianceAlert> alerts = complianceAlertService.findAlertsByMatchScoreRange(minScore, maxScore);

        return ResponseEntity.ok(alerts);
    }

    /**
     * Get alerts by risk score range.
     */
    @GetMapping("/alerts/risk-score-range")
    @Operation(summary = "Get alerts by risk score", description = "Retrieve alerts within risk score range")
    public ResponseEntity<List<ComplianceAlert>> getAlertsByRiskScoreRange(
            @Parameter(description = "Minimum risk score") @RequestParam int minScore,
            @Parameter(description = "Maximum risk score") @RequestParam(defaultValue = "100") int maxScore
    ) {
        log.info("Fetching alerts with risk score between {} and {}", minScore, maxScore);

        List<ComplianceAlert> alerts = complianceAlertService.findAlertsByRiskScoreRange(minScore, maxScore);

        return ResponseEntity.ok(alerts);
    }

    /**
     * Get regulatory report (for SAR filing).
     */
    @GetMapping("/alerts/{alertId}/regulatory-report")
    @Operation(summary = "Generate regulatory report", description = "Generate SAR filing report for blocked alert")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER', 'SENIOR_COMPLIANCE')")
    public ResponseEntity<RegulatoryReportDto> generateRegulatoryReport(
            @Parameter(description = "Alert ID") @PathVariable String alertId
    ) {
        log.info("Generating regulatory report for alert {}", alertId);

        RegulatoryReportDto report = complianceAlertService.generateRegulatoryReport(alertId);

        return ResponseEntity.ok(report);
    }

    /**
     * Get PEP (Politically Exposed Person) alerts.
     */
    @GetMapping("/alerts/pep")
    @Operation(summary = "Get PEP alerts", description = "Retrieve all Politically Exposed Person alerts")
    public ResponseEntity<List<ComplianceAlert>> getPepAlerts() {
        log.info("Fetching PEP alerts");

        List<ComplianceAlert> alerts = complianceAlertService.findPepAlerts();

        return ResponseEntity.ok(alerts);
    }

    /**
     * Get high-risk country alerts.
     */
    @GetMapping("/alerts/high-risk-country")
    @Operation(summary = "Get high-risk country alerts", description = "Retrieve alerts for high-risk countries")
    public ResponseEntity<List<ComplianceAlert>> getHighRiskCountryAlerts() {
        log.info("Fetching high-risk country alerts");

        List<ComplianceAlert> alerts = complianceAlertService.findHighRiskCountryAlerts();

        return ResponseEntity.ok(alerts);
    }

    /**
     * Helper method to get current authenticated user from Spring Security context.
     * Extracts the username from the JWT token or authentication principal.
     *
     * @return the username of the currently authenticated user
     * @throws IllegalStateException if no authenticated user is present in the security context
     */
    private String getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user in security context");
        }

        // Handle anonymous authentication
        if (authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("Anonymous user cannot perform this operation");
        }

        return authentication.getName();
    }
}
