package com.waqiti.ml.api;

import com.waqiti.ml.service.RiskScoringService;
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
@RequestMapping("/api/v1/ml/risk-scoring")
@RequiredArgsConstructor
@Slf4j
public class RiskScoringController {

    private final RiskScoringService riskScoringService;

    @PostMapping("/score-user")
    @PreAuthorize("hasAnyRole('SYSTEM', 'RISK_MANAGER')")
    public ResponseEntity<Map<String, Object>> scoreUser(@RequestBody @Valid UserRiskRequest request) {
        log.info("Scoring user risk for: {}", request.getUserId());
        
        try {
            Map<String, Object> riskScore = riskScoringService.scoreUser(
                request.getUserId(),
                request.getUserProfile(),
                request.getTransactionHistory(),
                request.getBehaviorData()
            );
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "userId", request.getUserId(),
                "riskScore", riskScore,
                "scoredAt", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to score user risk", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/score-transaction")
    @PreAuthorize("hasAnyRole('SYSTEM', 'RISK_MANAGER')")
    public ResponseEntity<Map<String, Object>> scoreTransaction(@RequestBody @Valid TransactionRiskRequest request) {
        log.info("Scoring transaction risk for: {}", request.getTransactionId());
        
        try {
            Map<String, Object> riskScore = riskScoringService.scoreTransaction(
                request.getTransactionId(),
                request.getUserId(),
                request.getAmount(),
                request.getCurrency(),
                request.getRecipientInfo(),
                request.getTransactionContext()
            );
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "transactionId", request.getTransactionId(),
                "riskScore", riskScore,
                "recommendation", riskScore.get("recommendation"),
                "scoredAt", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to score transaction risk", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/batch-score-users")
    @PreAuthorize("hasRole('RISK_MANAGER')")
    public ResponseEntity<Map<String, Object>> batchScoreUsers(@RequestBody @Valid BatchUserRiskRequest request) {
        log.info("Starting batch user risk scoring for {} users", request.getUserIds().size());
        
        try {
            String jobId = riskScoringService.startBatchUserScoring(
                request.getUserIds(),
                request.getScoringCriteria()
            );
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "jobId", jobId,
                "userCount", request.getUserIds().size(),
                "status", "started",
                "startedAt", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to start batch user scoring", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/user/{userId}/history")
    @PreAuthorize("hasAnyRole('USER', 'RISK_MANAGER')")
    public ResponseEntity<List<Map<String, Object>>> getUserRiskHistory(@PathVariable String userId,
                                                                       @RequestParam(defaultValue = "30") int days) {
        log.info("Getting risk history for user: {} over {} days", userId, days);
        
        try {
            List<Map<String, Object>> history = riskScoringService.getUserRiskHistory(userId, days);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("Failed to get user risk history", e);
            return ResponseEntity.badRequest().body(List.of());
        }
    }

    @GetMapping("/risk-factors")
    @PreAuthorize("hasAnyRole('RISK_MANAGER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getRiskFactors() {
        log.info("Getting risk factors configuration");
        
        Map<String, Object> riskFactors = riskScoringService.getRiskFactors();
        return ResponseEntity.ok(riskFactors);
    }

    @PostMapping("/risk-factors/update")
    @PreAuthorize("hasRole('RISK_MANAGER')")
    public ResponseEntity<Map<String, Object>> updateRiskFactors(@RequestBody @Valid RiskFactorsRequest request) {
        log.info("Updating risk factors configuration");
        
        try {
            riskScoringService.updateRiskFactors(request.getRiskFactors());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Risk factors updated successfully",
                "updatedAt", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to update risk factors", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/calibrate")
    @PreAuthorize("hasRole('RISK_MANAGER')")
    public ResponseEntity<Map<String, Object>> calibrateModel(@RequestBody @Valid CalibrationRequest request) {
        log.info("Starting model calibration with {} samples", request.getCalibrationData().size());
        
        try {
            String calibrationJobId = riskScoringService.startModelCalibration(
                request.getCalibrationData(),
                request.getTargetMetrics()
            );
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "calibrationJobId", calibrationJobId,
                "sampleCount", request.getCalibrationData().size(),
                "status", "started",
                "startedAt", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to start model calibration", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/thresholds")
    @PreAuthorize("hasAnyRole('RISK_MANAGER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getRiskThresholds() {
        log.info("Getting risk score thresholds");
        
        Map<String, Object> thresholds = riskScoringService.getRiskThresholds();
        return ResponseEntity.ok(thresholds);
    }

    @PostMapping("/thresholds/update")
    @PreAuthorize("hasRole('RISK_MANAGER')")
    public ResponseEntity<Map<String, Object>> updateRiskThresholds(@RequestBody @Valid ThresholdsRequest request) {
        log.info("Updating risk score thresholds");
        
        try {
            riskScoringService.updateRiskThresholds(request.getThresholds());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Risk thresholds updated successfully",
                "updatedAt", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to update risk thresholds", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/model-performance")
    @PreAuthorize("hasAnyRole('RISK_MANAGER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getModelPerformance(
            @RequestParam(defaultValue = "30") int days) {
        log.info("Getting risk scoring model performance over {} days", days);
        
        try {
            Map<String, Object> performance = riskScoringService.getModelPerformance(days);
            return ResponseEntity.ok(performance);
        } catch (Exception e) {
            log.error("Failed to get model performance", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/feature-importance")
    @PreAuthorize("hasAnyRole('RISK_MANAGER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> analyzeFeatureImportance(@RequestBody @Valid FeatureAnalysisRequest request) {
        log.info("Analyzing feature importance for risk scoring");
        
        try {
            Map<String, Object> analysis = riskScoringService.analyzeFeatureImportance(
                request.getFeatures(),
                request.getAnalysisType()
            );
            
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            log.error("Failed to analyze feature importance", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('RISK_MANAGER', 'ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getRiskAlerts(
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        log.info("Getting risk alerts with severity: {}", severity);
        
        try {
            List<Map<String, Object>> alerts = riskScoringService.getRiskAlerts(severity, page, size);
            return ResponseEntity.ok(alerts);
        } catch (Exception e) {
            log.error("Failed to get risk alerts", e);
            return ResponseEntity.badRequest().body(List.of());
        }
    }

    @PostMapping("/simulate")
    @PreAuthorize("hasRole('RISK_MANAGER')")
    public ResponseEntity<Map<String, Object>> simulateRiskScenario(@RequestBody @Valid SimulationRequest request) {
        log.info("Simulating risk scenario: {}", request.getScenarioName());
        
        try {
            Map<String, Object> simulation = riskScoringService.simulateRiskScenario(
                request.getScenarioName(),
                request.getScenarioParameters(),
                request.getSimulationCount()
            );
            
            return ResponseEntity.ok(simulation);
        } catch (Exception e) {
            log.error("Failed to simulate risk scenario", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/explain")
    @PreAuthorize("hasAnyRole('USER', 'RISK_MANAGER')")
    public ResponseEntity<Map<String, Object>> explainRiskScore(@RequestBody @Valid ExplanationRequest request) {
        log.info("Explaining risk score for user: {}", request.getUserId());
        
        try {
            Map<String, Object> explanation = riskScoringService.explainRiskScore(
                request.getUserId(),
                request.getTransactionId(),
                request.getExplanationType()
            );
            
            return ResponseEntity.ok(explanation);
        } catch (Exception e) {
            log.error("Failed to explain risk score", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }
}

// Request DTOs
@lombok.Data
@lombok.Builder
class UserRiskRequest {
    private String userId;
    private Map<String, Object> userProfile;
    private List<Map<String, Object>> transactionHistory;
    private Map<String, Object> behaviorData;
}

@lombok.Data
@lombok.Builder
class TransactionRiskRequest {
    private String transactionId;
    private String userId;
    private BigDecimal amount;
    private String currency;
    private Map<String, Object> recipientInfo;
    private Map<String, Object> transactionContext;
}

@lombok.Data
@lombok.Builder
class BatchUserRiskRequest {
    private List<String> userIds;
    private Map<String, Object> scoringCriteria;
}

@lombok.Data
@lombok.Builder
class RiskFactorsRequest {
    private Map<String, Object> riskFactors;
}

@lombok.Data
@lombok.Builder
class CalibrationRequest {
    private List<Map<String, Object>> calibrationData;
    private Map<String, Object> targetMetrics;
}

@lombok.Data
@lombok.Builder
class ThresholdsRequest {
    private Map<String, Double> thresholds;
}

@lombok.Data
@lombok.Builder
class FeatureAnalysisRequest {
    private List<String> features;
    private String analysisType; // SHAP, LIME, permutation
}

@lombok.Data
@lombok.Builder
class SimulationRequest {
    private String scenarioName;
    private Map<String, Object> scenarioParameters;
    private Integer simulationCount;
}

@lombok.Data
@lombok.Builder
class ExplanationRequest {
    private String userId;
    private String transactionId;
    private String explanationType; // LIME, SHAP, feature_importance
}