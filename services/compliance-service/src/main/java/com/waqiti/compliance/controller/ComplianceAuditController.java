package com.waqiti.compliance.controller;

import com.waqiti.common.ratelimit.RateLimited;
import com.waqiti.compliance.audit.ComplianceAuditService;
import com.waqiti.compliance.repository.ComplianceAuditRepository;
import com.waqiti.compliance.audit.ComplianceAuditService.*;
import com.waqiti.compliance.domain.ComplianceAuditEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/compliance/audit")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Compliance Audit", description = "Compliance audit trail management")
@SecurityRequirement(name = "bearerAuth")
public class ComplianceAuditController {

    private final ComplianceAuditService auditService;
    private final ComplianceAuditRepository auditRepository;

    @PostMapping("/manual-review")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 50, refillTokens = 50, refillPeriodMinutes = 60)
    @Operation(summary = "Record manual compliance review")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Manual review recorded"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<ComplianceAuditEntry> recordManualReview(
            @Valid @RequestBody ManualReviewRequest request,
            Authentication authentication) {
        
        String reviewerId = authentication.getName();
        
        ComplianceAuditEntry entry = auditService.recordManualReview(
            request.getTransactionId(),
            reviewerId,
            request.getAction(),
            request.getJustification(),
            request.getReviewDetails()
        );
        
        return ResponseEntity.status(201).body(entry);
    }

    @PostMapping("/system-override")
    @PreAuthorize("hasRole('SENIOR_COMPLIANCE') or hasRole('ADMIN')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 60)
    @Operation(summary = "Record system override")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "System override recorded"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<ComplianceAuditEntry> recordSystemOverride(
            @Valid @RequestBody SystemOverrideRequest request,
            Authentication authentication) {
        
        String overrideBy = authentication.getName();
        
        ComplianceAuditEntry entry = auditService.recordSystemOverride(
            request.getTransactionId(),
            overrideBy,
            request.getOriginalDecision(),
            request.getOverrideDecision(),
            request.getOverrideReason(),
            request.getApprovalTicket()
        );
        
        return ResponseEntity.status(201).body(entry);
    }

    @GetMapping("/trail/{transactionId}")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN') or hasRole('AUDITOR')")
    @Operation(summary = "Get audit trail for transaction")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Audit trail retrieved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    public ResponseEntity<Page<ComplianceAuditEntry>> getAuditTrail(
            @PathVariable @NotBlank String transactionId,
            @PageableDefault(size = 50) Pageable pageable) {
        
        AuditSearchCriteria criteria = AuditSearchCriteria.builder()
            .transactionId(transactionId)
            .build();
        
        Page<ComplianceAuditEntry> trail = auditService.searchAuditTrail(criteria, pageable);
        
        return ResponseEntity.ok(trail);
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN') or hasRole('AUDITOR')")
    @Operation(summary = "Search audit trail")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Search results retrieved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<Page<ComplianceAuditEntry>> searchAuditTrail(
            @Parameter(description = "Transaction ID") 
            @RequestParam(required = false) String transactionId,
            
            @Parameter(description = "Decision type")
            @RequestParam(required = false) String decisionType,
            
            @Parameter(description = "Performed by user")
            @RequestParam(required = false) String performedBy,
            
            @Parameter(description = "Start date")
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            
            @Parameter(description = "End date")
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            
            @Parameter(description = "Minimum risk score")
            @RequestParam(required = false) Integer riskScoreMin,
            
            @Parameter(description = "Maximum risk score")
            @RequestParam(required = false) Integer riskScoreMax,
            
            @PageableDefault(size = 20) Pageable pageable) {
        
        AuditSearchCriteria criteria = AuditSearchCriteria.builder()
            .transactionId(transactionId)
            .decisionType(decisionType)
            .performedBy(performedBy)
            .startDate(startDate)
            .endDate(endDate)
            .riskScoreMin(riskScoreMin)
            .riskScoreMax(riskScoreMax)
            .build();
        
        Page<ComplianceAuditEntry> results = auditService.searchAuditTrail(criteria, pageable);
        
        return ResponseEntity.ok(results);
    }

    @GetMapping("/verify/{transactionId}")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN') or hasRole('AUDITOR')")
    @Operation(summary = "Verify audit trail integrity")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Integrity report generated"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<AuditIntegrityReport> verifyIntegrity(
            @PathVariable @NotBlank String transactionId) {
        
        AuditIntegrityReport report = auditService.verifyIntegrity(transactionId);
        
        return ResponseEntity.ok(report);
    }

    @GetMapping("/reports/compliance")
    @PreAuthorize("hasRole('SENIOR_COMPLIANCE') or hasRole('ADMIN')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 5, refillTokens = 5, refillPeriodMinutes = 60)
    @Operation(summary = "Generate compliance report")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Compliance report generated"),
        @ApiResponse(responseCode = "400", description = "Invalid date range"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<ComplianceReport> generateComplianceReport(
            @Parameter(description = "Start date")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            
            @Parameter(description = "End date")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            
            @Parameter(description = "Report type")
            @RequestParam(defaultValue = "STANDARD") String reportType) {
        
        if (startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().build();
        }
        
        ComplianceReport report = auditService.generateComplianceReport(
            startDate, endDate, reportType);
        
        return ResponseEntity.ok(report);
    }

    @GetMapping("/pending-reviews")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    @Operation(summary = "Get entries pending second review")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pending reviews retrieved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<Page<ComplianceAuditEntry>> getPendingReviews(
            @PageableDefault(size = 20) Pageable pageable) {
        
        AuditSearchCriteria criteria = AuditSearchCriteria.builder()
            .build();
        
        Page<ComplianceAuditEntry> pending = auditRepository.findPendingSecondReviews(pageable);
        
        return ResponseEntity.ok(pending);
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    @Operation(summary = "Get audit trail statistics")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Statistics retrieved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<AuditStatistics> getStatistics(
            @Parameter(description = "Days to look back")
            @RequestParam(defaultValue = "30") int days) {
        
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        LocalDateTime now = LocalDateTime.now();
        
        // Get audit entries for the period
        List<ComplianceAuditEntry> entries = auditRepository.findByPerformedAtBetweenOrderByPerformedAt(since, now);
        
        // Calculate statistics
        long totalEntries = entries.size();
        long manualReviews = entries.stream()
            .filter(e -> "MANUAL_REVIEW".equals(e.getActionType()))
            .count();
        long systemOverrides = entries.stream()
            .filter(e -> "SYSTEM_OVERRIDE".equals(e.getActionType()))
            .count();
        long highRiskDecisions = entries.stream()
            .filter(e -> e.getRiskScore() != null && e.getRiskScore() > 80)
            .count();
        double averageRiskScore = entries.stream()
            .filter(e -> e.getRiskScore() != null)
            .mapToInt(ComplianceAuditEntry::getRiskScore)
            .average()
            .orElse(0.0);
        
        // Decision type breakdown
        Map<String, Long> decisionTypeBreakdown = entries.stream()
            .filter(e -> e.getDecisionType() != null)
            .collect(Collectors.groupingBy(
                ComplianceAuditEntry::getDecisionType,
                Collectors.counting()
            ));
            
        // User activity breakdown
        Map<String, Long> userActivityBreakdown = entries.stream()
            .filter(e -> e.getPerformedBy() != null)
            .collect(Collectors.groupingBy(
                ComplianceAuditEntry::getPerformedBy,
                Collectors.counting()
            ));
        
        AuditStatistics stats = AuditStatistics.builder()
            .period(days + " days")
            .totalEntries(totalEntries)
            .manualReviews(manualReviews)
            .systemOverrides(systemOverrides)
            .highRiskDecisions(highRiskDecisions)
            .averageRiskScore(averageRiskScore)
            .decisionTypeBreakdown(decisionTypeBreakdown)
            .userActivityBreakdown(userActivityBreakdown)
            .build();
        
        return ResponseEntity.ok(stats);
    }

    // DTOs

    @lombok.Data
    public static class ManualReviewRequest {
        @NotBlank
        private String transactionId;
        
        @NotBlank
        private String action;
        
        @NotBlank
        private String justification;
        
        private Map<String, Object> reviewDetails;
    }

    @lombok.Data
    public static class SystemOverrideRequest {
        @NotBlank
        private String transactionId;
        
        @NotBlank
        private String originalDecision;
        
        @NotBlank
        private String overrideDecision;
        
        @NotBlank
        private String overrideReason;
        
        @NotBlank
        private String approvalTicket;
    }

    @lombok.Data
    @lombok.Builder
    public static class AuditStatistics {
        private String period;
        private long totalEntries;
        private long manualReviews;
        private long systemOverrides;
        private long highRiskDecisions;
        private double averageRiskScore;
        private Map<String, Long> decisionTypeBreakdown;
        private Map<String, Long> userActivityBreakdown;
    }
}