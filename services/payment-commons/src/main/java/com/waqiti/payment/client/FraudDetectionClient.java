package com.waqiti.payment.client;

import com.waqiti.payment.client.dto.*;
import com.waqiti.payment.client.fallback.FraudDetectionFallback;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unified Fraud Detection Service Client for all payment services
 * Provides real-time fraud detection, risk scoring, and compliance checks
 *
 * @deprecated This client defines 70+ aspirational endpoints that were never implemented.
 *             Most endpoints will return 404 or trigger circuit breaker fallbacks.
 *
 *             <p><b>Migration Required:</b></p>
 *             Use the actual implemented client instead:
 *             {@link com.waqiti.payment.service.client.FraudDetectionServiceClient}
 *
 *             <p><b>Migration Deadline:</b> 2025-12-01</p>
 *
 *             <p><b>Migration Guide:</b> See docs/FRAUD_CLIENT_MIGRATION.md</p>
 *
 *             <p><b>Implemented Endpoints (use FraudDetectionServiceClient):</b></p>
 *             <ul>
 *               <li>POST /api/v1/fraud/assess - General fraud assessment</li>
 *               <li>POST /api/v1/fraud/assess/nfc - NFC payment fraud check</li>
 *               <li>POST /api/v1/fraud/assess/p2p - P2P transfer fraud check</li>
 *               <li>POST /api/v1/fraud/validate - Transaction validation</li>
 *               <li>POST /api/v1/fraud/report - Report fraud</li>
 *             </ul>
 *
 * @since 1.0
 * @see com.waqiti.payment.service.client.FraudDetectionServiceClient
 */
@Deprecated(since = "2.1", forRemoval = true)
@FeignClient(
    name = "fraud-detection-service",
    path = "/api/v1/fraud",
    fallback = FraudDetectionFallback.class,
    configuration = FraudDetectionClientConfiguration.class
)
public interface FraudDetectionClient {

    // Real-time Fraud Detection
    
    @PostMapping("/evaluate/payment")
    @CircuitBreaker(name = "fraud-detection-service", fallbackMethod = "evaluatePaymentFallback")
    @Retry(name = "fraud-detection-service")
    @TimeLimiter(name = "fraud-detection-service")
    ResponseEntity<FraudEvaluationResponse> evaluatePayment(@RequestBody PaymentFraudRequest request);
    
    @PostMapping("/evaluate/transfer")
    @CircuitBreaker(name = "fraud-detection-service", fallbackMethod = "evaluateTransferFallback")
    @Retry(name = "fraud-detection-service")
    @TimeLimiter(name = "fraud-detection-service")
    ResponseEntity<FraudEvaluationResponse> evaluateTransfer(@RequestBody TransferFraudRequest request);
    
    @PostMapping("/evaluate/instant-transfer")
    @CircuitBreaker(name = "fraud-detection-service", fallbackMethod = "evaluateInstantTransferFallback")
    @Retry(name = "fraud-detection-service")
    @TimeLimiter(name = "fraud-detection-service")
    ResponseEntity<FraudScore> evaluateInstantTransfer(
        @RequestParam UUID senderId,
        @RequestParam UUID recipientId,
        @RequestParam BigDecimal amount,
        @RequestParam String transferMethod
    );
    
    @PostMapping("/evaluate/transaction")
    @CircuitBreaker(name = "fraud-detection-service", fallbackMethod = "evaluateTransactionFallback")
    @Retry(name = "fraud-detection-service")
    @TimeLimiter(name = "fraud-detection-service")
    ResponseEntity<FraudEvaluationResponse> evaluateTransaction(@RequestBody TransactionFraudRequest request);
    
    @PostMapping("/evaluate/batch")
    @CircuitBreaker(name = "fraud-detection-service", fallbackMethod = "evaluateBatchFallback")
    @Retry(name = "fraud-detection-service")
    @TimeLimiter(name = "fraud-detection-service")
    ResponseEntity<BatchFraudEvaluationResponse> evaluateBatch(@RequestBody BatchFraudRequest request);

    // Risk Scoring
    
    @GetMapping("/risk-score/user/{userId}")
    ResponseEntity<UserRiskScore> getUserRiskScore(@PathVariable UUID userId);
    
    @GetMapping("/risk-score/merchant/{merchantId}")
    ResponseEntity<MerchantRiskScore> getMerchantRiskScore(@PathVariable UUID merchantId);
    
    @PostMapping("/risk-score/calculate")
    ResponseEntity<RiskScoreCalculation> calculateRiskScore(@RequestBody RiskScoreRequest request);
    
    @PutMapping("/risk-score/user/{userId}")
    ResponseEntity<UserRiskScore> updateUserRiskScore(
        @PathVariable UUID userId,
        @RequestBody RiskScoreUpdate update
    );

    // Fraud Rules Management
    
    @GetMapping("/rules")
    ResponseEntity<List<FraudRule>> getFraudRules(@RequestParam(required = false) String category);
    
    @PostMapping("/rules")
    ResponseEntity<FraudRule> createFraudRule(@RequestBody FraudRuleRequest request);
    
    @PutMapping("/rules/{ruleId}")
    ResponseEntity<FraudRule> updateFraudRule(
        @PathVariable UUID ruleId,
        @RequestBody FraudRuleRequest request
    );
    
    @DeleteMapping("/rules/{ruleId}")
    ResponseEntity<Void> deleteFraudRule(@PathVariable UUID ruleId);
    
    @PostMapping("/rules/{ruleId}/test")
    ResponseEntity<FraudRuleTestResult> testFraudRule(
        @PathVariable UUID ruleId,
        @RequestBody Map<String, Object> testData
    );

    // Machine Learning Models
    
    @PostMapping("/ml/predict")
    ResponseEntity<MLPredictionResponse> predictFraud(@RequestBody MLPredictionRequest request);
    
    @PostMapping("/ml/train")
    ResponseEntity<MLTrainingResponse> triggerModelTraining(@RequestBody MLTrainingRequest request);
    
    @GetMapping("/ml/models")
    ResponseEntity<List<MLModel>> getMLModels();
    
    @GetMapping("/ml/models/{modelId}/performance")
    ResponseEntity<MLModelPerformance> getModelPerformance(@PathVariable UUID modelId);

    // Blacklists and Whitelists
    
    @GetMapping("/blacklist/check")
    ResponseEntity<BlacklistCheckResponse> checkBlacklist(@RequestParam String type, @RequestParam String value);
    
    @PostMapping("/blacklist/add")
    ResponseEntity<BlacklistEntry> addToBlacklist(@RequestBody BlacklistRequest request);
    
    @DeleteMapping("/blacklist/{entryId}")
    ResponseEntity<Void> removeFromBlacklist(@PathVariable UUID entryId);
    
    @GetMapping("/whitelist/check")
    ResponseEntity<WhitelistCheckResponse> checkWhitelist(@RequestParam String type, @RequestParam String value);
    
    @PostMapping("/whitelist/add")
    ResponseEntity<WhitelistEntry> addToWhitelist(@RequestBody WhitelistRequest request);

    // Transaction Monitoring
    
    @PostMapping("/monitor/transaction")
    ResponseEntity<MonitoringResult> monitorTransaction(@RequestBody TransactionMonitoringRequest request);
    
    @GetMapping("/monitor/alerts/user/{userId}")
    ResponseEntity<List<FraudAlert>> getUserFraudAlerts(@PathVariable UUID userId);
    
    @PostMapping("/monitor/alerts/{alertId}/investigate")
    ResponseEntity<FraudInvestigation> investigateAlert(
        @PathVariable UUID alertId,
        @RequestBody InvestigationRequest request
    );
    
    @PutMapping("/monitor/alerts/{alertId}/resolve")
    ResponseEntity<FraudAlert> resolveAlert(
        @PathVariable UUID alertId,
        @RequestBody AlertResolutionRequest request
    );

    // Pattern Detection
    
    @PostMapping("/patterns/detect")
    ResponseEntity<PatternDetectionResponse> detectPatterns(@RequestBody PatternDetectionRequest request);
    
    @GetMapping("/patterns/user/{userId}")
    ResponseEntity<UserPatternAnalysis> analyzeUserPatterns(@PathVariable UUID userId);
    
    @GetMapping("/patterns/suspicious")
    ResponseEntity<List<SuspiciousPattern>> getSuspiciousPatterns(
        @RequestParam(required = false) String timeframe
    );

    // Velocity Checks
    
    @PostMapping("/velocity/check")
    ResponseEntity<VelocityCheckResponse> performVelocityCheck(@RequestBody VelocityCheckRequest request);
    
    @GetMapping("/velocity/limits")
    ResponseEntity<List<VelocityLimit>> getVelocityLimits();
    
    @PutMapping("/velocity/limits/{limitId}")
    ResponseEntity<VelocityLimit> updateVelocityLimit(
        @PathVariable UUID limitId,
        @RequestBody VelocityLimitUpdate update
    );

    // Geographic Analysis
    
    @PostMapping("/geo/analyze")
    ResponseEntity<GeoAnalysisResponse> analyzeGeographicRisk(@RequestBody GeoAnalysisRequest request);
    
    @GetMapping("/geo/high-risk-countries")
    ResponseEntity<List<HighRiskCountry>> getHighRiskCountries();
    
    @PostMapping("/geo/distance-check")
    ResponseEntity<DistanceCheckResponse> checkGeographicDistance(@RequestBody DistanceCheckRequest request);

    // Device Fingerprinting
    
    @PostMapping("/device/fingerprint")
    ResponseEntity<DeviceFingerprintResponse> createDeviceFingerprint(@RequestBody DeviceFingerprintRequest request);
    
    @GetMapping("/device/{deviceId}/history")
    ResponseEntity<DeviceHistory> getDeviceHistory(@PathVariable String deviceId);
    
    @PostMapping("/device/risk-assessment")
    ResponseEntity<DeviceRiskAssessment> assessDeviceRisk(@RequestBody DeviceRiskRequest request);

    // Compliance Checks
    
    @PostMapping("/compliance/aml")
    ResponseEntity<AMLCheckResponse> performAMLCheck(@RequestBody AMLCheckRequest request);
    
    @PostMapping("/compliance/kyc")
    ResponseEntity<KYCVerificationResponse> performKYCVerification(@RequestBody KYCVerificationRequest request);
    
    @PostMapping("/compliance/sanctions")
    ResponseEntity<SanctionsCheckResponse> checkSanctions(@RequestBody SanctionsCheckRequest request);
    
    @PostMapping("/compliance/pep")
    ResponseEntity<PEPCheckResponse> checkPoliticallyExposedPerson(@RequestBody PEPCheckRequest request);

    // Reporting and Analytics
    
    @GetMapping("/reports/summary")
    ResponseEntity<FraudSummaryReport> getFraudSummary(
        @RequestParam String startDate,
        @RequestParam String endDate
    );
    
    @GetMapping("/reports/trends")
    ResponseEntity<FraudTrendsReport> getFraudTrends(@RequestParam String timeframe);
    
    @GetMapping("/reports/performance")
    ResponseEntity<FraudDetectionPerformance> getPerformanceMetrics(@RequestParam String period);
    
    @PostMapping("/reports/generate")
    ResponseEntity<GeneratedReport> generateCustomReport(@RequestBody ReportGenerationRequest request);

    // Case Management
    
    @PostMapping("/cases/create")
    ResponseEntity<FraudCase> createFraudCase(@RequestBody FraudCaseRequest request);
    
    @GetMapping("/cases/{caseId}")
    ResponseEntity<FraudCase> getFraudCase(@PathVariable UUID caseId);
    
    @PutMapping("/cases/{caseId}/status")
    ResponseEntity<FraudCase> updateCaseStatus(
        @PathVariable UUID caseId,
        @RequestBody CaseStatusUpdate update
    );
    
    @PostMapping("/cases/{caseId}/evidence")
    ResponseEntity<CaseEvidence> addCaseEvidence(
        @PathVariable UUID caseId,
        @RequestBody EvidenceRequest request
    );

    // Configuration and Settings
    
    @GetMapping("/config/thresholds")
    ResponseEntity<FraudThresholds> getFraudThresholds();
    
    @PutMapping("/config/thresholds")
    ResponseEntity<FraudThresholds> updateFraudThresholds(@RequestBody FraudThresholds thresholds);
    
    @GetMapping("/config/parameters")
    ResponseEntity<Map<String, Object>> getFraudParameters();
    
    @PutMapping("/config/parameters")
    ResponseEntity<Map<String, Object>> updateFraudParameters(@RequestBody Map<String, Object> parameters);

    // Health and Status
    
    @GetMapping("/health")
    ResponseEntity<ServiceHealth> getServiceHealth();
    
    @GetMapping("/status/models")
    ResponseEntity<ModelStatusReport> getModelStatus();
    
    @GetMapping("/status/rules")
    ResponseEntity<RuleEngineStatus> getRuleEngineStatus();
}