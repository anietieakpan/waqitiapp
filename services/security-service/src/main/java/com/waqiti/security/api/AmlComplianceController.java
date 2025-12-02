package com.waqiti.security.api;

import com.waqiti.security.domain.*;
import com.waqiti.security.service.AmlMonitoringService;
import com.waqiti.security.service.ComprehensiveAMLService;
import com.waqiti.security.service.RegulatoryReportingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/security/aml")
@RequiredArgsConstructor
@Slf4j
public class AmlComplianceController {

    private final AmlMonitoringService amlMonitoringService;
    private final ComprehensiveAMLService comprehensiveAMLService;
    private final RegulatoryReportingService regulatoryReportingService;

    @PostMapping("/screen-transaction")
    @PreAuthorize("hasAnyRole('SYSTEM', 'COMPLIANCE')")
    public ResponseEntity<AmlScreeningResult> screenTransaction(@RequestBody @Valid AmlScreeningRequest request) {
        log.info("Screening transaction for AML compliance: transactionId={}", request.getTransactionId());
        
        AmlScreeningResult result = amlMonitoringService.screenTransaction(
            request.getTransactionId(),
            request.getUserId(),
            request.getAmount(),
            request.getCurrency(),
            request.getSourceCountry(),
            request.getDestinationCountry()
        );
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/monitor-user")
    @PreAuthorize("hasAnyRole('SYSTEM', 'COMPLIANCE')")
    public ResponseEntity<UserMonitoringResult> monitorUserActivity(@RequestBody @Valid UserMonitoringRequest request) {
        log.info("Monitoring user activity for AML: userId={}", request.getUserId());
        
        UserMonitoringResult result = comprehensiveAMLService.monitorUserActivity(
            request.getUserId(),
            request.getStartDate(),
            request.getEndDate()
        );
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasRole('COMPLIANCE')")
    public ResponseEntity<List<AmlAlert>> getAmlAlerts(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) AmlSeverity severity,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Fetching AML alerts: userId={}, severity={}, status={}", userId, severity, status);
        
        List<AmlAlert> alerts = amlMonitoringService.getAlerts(userId, severity, status, page, size);
        return ResponseEntity.ok(alerts);
    }

    @PostMapping("/alerts/{alertId}/review")
    @PreAuthorize("hasRole('COMPLIANCE')")
    public ResponseEntity<Map<String, Object>> reviewAlert(
            @PathVariable String alertId,
            @RequestBody @Valid AlertReviewRequest request) {
        log.info("Reviewing AML alert: alertId={}, action={}", alertId, request.getAction());
        
        amlMonitoringService.reviewAlert(alertId, request.getAction(), request.getComments(), request.getReviewedBy());
        
        return ResponseEntity.ok(Map.of(
            "alertId", alertId,
            "status", "reviewed",
            "action", request.getAction(),
            "timestamp", LocalDateTime.now()
        ));
    }

    @PostMapping("/sar/file")
    @PreAuthorize("hasRole('COMPLIANCE')")
    public ResponseEntity<SuspiciousActivityReport> fileSAR(@RequestBody @Valid SARRequest request) {
        log.info("Filing Suspicious Activity Report for user: {}", request.getUserId());
        
        SuspiciousActivityReport sar = regulatoryReportingService.fileSAR(
            request.getUserId(),
            request.getActivityType(),
            request.getDescription(),
            request.getAmount(),
            request.getInvolvedParties(),
            request.getEvidence()
        );
        
        return ResponseEntity.status(HttpStatus.CREATED).body(sar);
    }

    @GetMapping("/sar/{sarId}")
    @PreAuthorize("hasRole('COMPLIANCE')")
    public ResponseEntity<SuspiciousActivityReport> getSAR(@PathVariable String sarId) {
        log.info("Fetching SAR: {}", sarId);
        
        SuspiciousActivityReport sar = regulatoryReportingService.getSAR(sarId);
        return ResponseEntity.ok(sar);
    }

    @PostMapping("/reports/generate")
    @PreAuthorize("hasRole('COMPLIANCE')")
    public ResponseEntity<RegulatoryReport> generateRegulatoryReport(@RequestBody @Valid ReportGenerationRequest request) {
        log.info("Generating regulatory report: type={}, period={} to {}", 
            request.getReportType(), request.getStartDate(), request.getEndDate());
        
        RegulatoryReport report = regulatoryReportingService.generateReport(
            request.getReportType(),
            request.getStartDate(),
            request.getEndDate(),
            request.getIncludeDetails()
        );
        
        return ResponseEntity.ok(report);
    }

    @GetMapping("/reports")
    @PreAuthorize("hasRole('COMPLIANCE')")
    public ResponseEntity<List<RegulatoryReport>> getReports(
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        log.info("Fetching regulatory reports: type={}, from={}, to={}", reportType, fromDate, toDate);
        
        List<RegulatoryReport> reports = regulatoryReportingService.getReports(reportType, fromDate, toDate);
        return ResponseEntity.ok(reports);
    }

    @PostMapping("/watchlist/check")
    @PreAuthorize("hasAnyRole('SYSTEM', 'COMPLIANCE')")
    public ResponseEntity<WatchlistCheckResult> checkWatchlist(@RequestBody @Valid WatchlistCheckRequest request) {
        log.info("Checking watchlist for: {}", request.getName());
        
        WatchlistCheckResult result = amlMonitoringService.checkWatchlist(
            request.getName(),
            request.getCountry(),
            request.getIdNumber(),
            request.getDateOfBirth()
        );
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/risk-assessment")
    @PreAuthorize("hasAnyRole('SYSTEM', 'COMPLIANCE')")
    public ResponseEntity<AmlRiskAssessment> assessAmlRisk(@RequestBody @Valid AmlRiskRequest request) {
        log.info("Assessing AML risk for user: {}", request.getUserId());
        
        AmlRiskAssessment assessment = comprehensiveAMLService.assessUserRisk(
            request.getUserId(),
            request.getTransactionHistory(),
            request.getUserProfile()
        );
        
        return ResponseEntity.ok(assessment);
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('COMPLIANCE')")
    public ResponseEntity<AmlStatistics> getAmlStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        log.info("Getting AML statistics from {} to {}", fromDate, toDate);
        
        AmlStatistics stats = amlMonitoringService.getStatistics(fromDate, toDate);
        return ResponseEntity.ok(stats);
    }
}

@lombok.Data
@lombok.Builder
class AmlScreeningRequest {
    private String transactionId;
    private String userId;
    private BigDecimal amount;
    private String currency;
    private String sourceCountry;
    private String destinationCountry;
}

@lombok.Data
@lombok.Builder
class AmlScreeningResult {
    private String transactionId;
    private boolean passed;
    private String riskLevel;
    private List<String> flags;
    private Map<String, Object> details;
    private LocalDateTime screenedAt;
}

@lombok.Data
@lombok.Builder
class UserMonitoringRequest {
    private String userId;
    private LocalDate startDate;
    private LocalDate endDate;
}

@lombok.Data
@lombok.Builder
class UserMonitoringResult {
    private String userId;
    private int totalTransactions;
    private BigDecimal totalVolume;
    private List<String> suspiciousPatterns;
    private String riskLevel;
    private Map<String, Object> analysis;
}

@lombok.Data
@lombok.Builder
class AlertReviewRequest {
    private String action; // DISMISS, ESCALATE, INVESTIGATE
    private String comments;
    private String reviewedBy;
}

@lombok.Data
@lombok.Builder
class SARRequest {
    private String userId;
    private String activityType;
    private String description;
    private BigDecimal amount;
    private List<String> involvedParties;
    private Map<String, Object> evidence;
}

@lombok.Data
@lombok.Builder
class ReportGenerationRequest {
    private String reportType;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean includeDetails;
}

@lombok.Data
@lombok.Builder
class WatchlistCheckRequest {
    private String name;
    private String country;
    private String idNumber;
    private LocalDate dateOfBirth;
}

@lombok.Data
@lombok.Builder
class WatchlistCheckResult {
    private boolean isMatch;
    private List<WatchlistMatch> matches;
    private String checkId;
    private LocalDateTime checkedAt;
}

@lombok.Data
@lombok.Builder
class WatchlistMatch {
    private String listName;
    private String matchedName;
    private double matchScore;
    private String reason;
}

@lombok.Data
@lombok.Builder
class AmlRiskRequest {
    private String userId;
    private Map<String, Object> transactionHistory;
    private Map<String, Object> userProfile;
}

@lombok.Data
@lombok.Builder
class AmlRiskAssessment {
    private String userId;
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private double riskScore;
    private List<String> riskFactors;
    private Map<String, Object> recommendations;
    private LocalDateTime assessedAt;
}

@lombok.Data
@lombok.Builder
class AmlStatistics {
    private long totalScreenings;
    private long flaggedTransactions;
    private long sarsField;
    private Map<String, Long> alertsByType;
    private Map<String, BigDecimal> volumeByRiskLevel;
    private LocalDate fromDate;
    private LocalDate toDate;
}