package com.waqiti.security.api;

import com.waqiti.security.service.RiskScoringService;
import com.waqiti.security.service.TransactionPatternAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/security/risk")
@RequiredArgsConstructor
@Slf4j
public class RiskAssessmentController {

    private final RiskScoringService riskScoringService;
    private final TransactionPatternAnalyzer patternAnalyzer;

    @PostMapping("/assess")
    @PreAuthorize("hasAnyRole('SYSTEM', 'RISK_MANAGER')")
    public ResponseEntity<RiskAssessmentResponse> assessRisk(@RequestBody @Valid RiskAssessmentRequest request) {
        log.info("Assessing risk for user: {}, transaction: {}", request.getUserId(), request.getTransactionId());
        
        RiskAssessmentResponse response = riskScoringService.assessRisk(
            request.getUserId(),
            request.getTransactionId(),
            request.getAmount(),
            request.getTransactionType(),
            request.getRecipientInfo(),
            request.getContextData()
        );
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/profile/{userId}")
    @PreAuthorize("hasAnyRole('USER', 'RISK_MANAGER')")
    public ResponseEntity<UserRiskProfile> getUserRiskProfile(@PathVariable String userId) {
        log.info("Getting risk profile for user: {}", userId);
        
        UserRiskProfile profile = riskScoringService.getUserRiskProfile(userId);
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/profile/{userId}/update")
    @PreAuthorize("hasRole('RISK_MANAGER')")
    public ResponseEntity<Map<String, Object>> updateRiskProfile(
            @PathVariable String userId,
            @RequestBody @Valid UpdateRiskProfileRequest request) {
        log.info("Updating risk profile for user: {}, new level: {}", userId, request.getRiskLevel());
        
        riskScoringService.updateUserRiskProfile(
            userId,
            request.getRiskLevel(),
            request.getReason(),
            request.getUpdatedBy()
        );
        
        return ResponseEntity.ok(Map.of(
            "userId", userId,
            "status", "updated",
            "newRiskLevel", request.getRiskLevel(),
            "timestamp", LocalDateTime.now()
        ));
    }

    @GetMapping("/patterns/{userId}")
    @PreAuthorize("hasAnyRole('RISK_MANAGER', 'COMPLIANCE')")
    public ResponseEntity<TransactionPatterns> analyzeTransactionPatterns(
            @PathVariable String userId,
            @RequestParam(defaultValue = "30") int days) {
        log.info("Analyzing transaction patterns for user: {} over {} days", userId, days);
        
        TransactionPatterns patterns = patternAnalyzer.analyzeUserPatterns(userId, days);
        return ResponseEntity.ok(patterns);
    }

    @PostMapping("/rules")
    @PreAuthorize("hasRole('RISK_MANAGER')")
    public ResponseEntity<Map<String, Object>> createRiskRule(@RequestBody @Valid RiskRuleRequest request) {
        log.info("Creating risk rule: {}", request.getRuleName());
        
        String ruleId = riskScoringService.createRiskRule(
            request.getRuleName(),
            request.getRuleType(),
            request.getConditions(),
            request.getAction(),
            request.getSeverity()
        );
        
        return ResponseEntity.ok(Map.of(
            "ruleId", ruleId,
            "status", "created",
            "ruleName", request.getRuleName()
        ));
    }

    @GetMapping("/rules")
    @PreAuthorize("hasRole('RISK_MANAGER')")
    public ResponseEntity<List<RiskRule>> getRiskRules(
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) Boolean active) {
        log.info("Fetching risk rules: type={}, active={}", ruleType, active);
        
        List<RiskRule> rules = riskScoringService.getRiskRules(ruleType, active);
        return ResponseEntity.ok(rules);
    }

    @PutMapping("/rules/{ruleId}")
    @PreAuthorize("hasRole('RISK_MANAGER')")
    public ResponseEntity<Map<String, Object>> updateRiskRule(
            @PathVariable String ruleId,
            @RequestBody @Valid UpdateRiskRuleRequest request) {
        log.info("Updating risk rule: {}", ruleId);
        
        riskScoringService.updateRiskRule(ruleId, request.getUpdates());
        
        return ResponseEntity.ok(Map.of(
            "ruleId", ruleId,
            "status", "updated",
            "timestamp", LocalDateTime.now()
        ));
    }

    @DeleteMapping("/rules/{ruleId}")
    @PreAuthorize("hasRole('RISK_MANAGER')")
    public ResponseEntity<Map<String, Object>> deleteRiskRule(@PathVariable String ruleId) {
        log.info("Deleting risk rule: {}", ruleId);
        
        riskScoringService.deleteRiskRule(ruleId);
        
        return ResponseEntity.ok(Map.of(
            "ruleId", ruleId,
            "status", "deleted",
            "timestamp", LocalDateTime.now()
        ));
    }

    @GetMapping("/thresholds")
    @PreAuthorize("hasRole('RISK_MANAGER')")
    public ResponseEntity<RiskThresholds> getRiskThresholds() {
        log.info("Getting risk thresholds");
        
        RiskThresholds thresholds = riskScoringService.getRiskThresholds();
        return ResponseEntity.ok(thresholds);
    }

    @PutMapping("/thresholds")
    @PreAuthorize("hasRole('RISK_MANAGER')")
    public ResponseEntity<Map<String, Object>> updateRiskThresholds(@RequestBody @Valid RiskThresholdsRequest request) {
        log.info("Updating risk thresholds");
        
        riskScoringService.updateRiskThresholds(request);
        
        return ResponseEntity.ok(Map.of(
            "status", "updated",
            "message", "Risk thresholds updated successfully",
            "timestamp", LocalDateTime.now()
        ));
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasRole('RISK_MANAGER')")
    public ResponseEntity<List<RiskAlert>> getRiskAlerts(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        log.info("Fetching risk alerts: severity={}, status={}", severity, status);
        
        List<RiskAlert> alerts = riskScoringService.getRiskAlerts(severity, status, page, size);
        return ResponseEntity.ok(alerts);
    }

    @PostMapping("/alerts/{alertId}/acknowledge")
    @PreAuthorize("hasRole('RISK_MANAGER')")
    public ResponseEntity<Map<String, Object>> acknowledgeAlert(
            @PathVariable String alertId,
            @RequestBody @Valid AcknowledgeAlertRequest request) {
        log.info("Acknowledging risk alert: {}", alertId);
        
        riskScoringService.acknowledgeAlert(alertId, request.getAcknowledgedBy(), request.getNotes());
        
        return ResponseEntity.ok(Map.of(
            "alertId", alertId,
            "status", "acknowledged",
            "timestamp", LocalDateTime.now()
        ));
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasRole('RISK_MANAGER')")
    public ResponseEntity<RiskMetrics> getRiskMetrics(
            @RequestParam(defaultValue = "7") int days) {
        log.info("Getting risk metrics for last {} days", days);
        
        RiskMetrics metrics = riskScoringService.getRiskMetrics(days);
        return ResponseEntity.ok(metrics);
    }
}

@lombok.Data
@lombok.Builder
class RiskAssessmentRequest {
    private String userId;
    private String transactionId;
    private BigDecimal amount;
    private String transactionType;
    private Map<String, Object> recipientInfo;
    private Map<String, Object> contextData;
}

@lombok.Data
@lombok.Builder
class RiskAssessmentResponse {
    private String assessmentId;
    private String userId;
    private String transactionId;
    private double riskScore;
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private List<String> riskFactors;
    private String recommendation; // APPROVE, REVIEW, BLOCK
    private Map<String, Object> details;
    private LocalDateTime assessedAt;
}

@lombok.Data
@lombok.Builder
class UserRiskProfile {
    private String userId;
    private String currentRiskLevel;
    private double baseRiskScore;
    private Map<String, Double> riskFactorScores;
    private List<String> activeFls;
    private int transactionCount;
    private BigDecimal totalVolume;
    private LocalDateTime lastAssessment;
    private LocalDateTime profileCreated;
}

@lombok.Data
@lombok.Builder
class UpdateRiskProfileRequest {
    private String riskLevel;
    private String reason;
    private String updatedBy;
}

@lombok.Data
@lombok.Builder
class TransactionPatterns {
    private String userId;
    private Map<String, Object> timePatterns;
    private Map<String, Object> amountPatterns;
    private Map<String, Object> frequencyPatterns;
    private List<String> unusualPatterns;
    private int analyzedTransactions;
    private LocalDateTime analysisStart;
    private LocalDateTime analysisEnd;
}

@lombok.Data
@lombok.Builder
class RiskRuleRequest {
    private String ruleName;
    private String ruleType;
    private Map<String, Object> conditions;
    private String action;
    private String severity;
}

@lombok.Data
@lombok.Builder
class RiskRule {
    private String ruleId;
    private String ruleName;
    private String ruleType;
    private Map<String, Object> conditions;
    private String action;
    private String severity;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime lastModified;
}

@lombok.Data
@lombok.Builder
class UpdateRiskRuleRequest {
    private Map<String, Object> updates;
}

@lombok.Data
@lombok.Builder
class RiskThresholds {
    private double lowRiskMax;
    private double mediumRiskMax;
    private double highRiskMax;
    private Map<String, Double> transactionTypeThresholds;
    private Map<String, Double> countryRiskScores;
}

@lombok.Data
@lombok.Builder
class RiskThresholdsRequest {
    private Double lowRiskMax;
    private Double mediumRiskMax;
    private Double highRiskMax;
    private Map<String, Double> transactionTypeThresholds;
    private Map<String, Double> countryRiskScores;
}

@lombok.Data
@lombok.Builder
class RiskAlert {
    private String alertId;
    private String userId;
    private String alertType;
    private String severity;
    private String description;
    private Map<String, Object> details;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime acknowledgedAt;
    private String acknowledgedBy;
}

@lombok.Data
@lombok.Builder
class AcknowledgeAlertRequest {
    private String acknowledgedBy;
    private String notes;
}

@lombok.Data
@lombok.Builder
class RiskMetrics {
    private long totalAssessments;
    private Map<String, Long> assessmentsByRiskLevel;
    private double averageRiskScore;
    private long blockedTransactions;
    private long reviewedTransactions;
    private Map<String, Long> alertsBySeverity;
    private List<TopRiskUser> topRiskUsers;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
}

@lombok.Data
@lombok.Builder
class TopRiskUser {
    private String userId;
    private double riskScore;
    private String riskLevel;
    private int flaggedTransactions;
}