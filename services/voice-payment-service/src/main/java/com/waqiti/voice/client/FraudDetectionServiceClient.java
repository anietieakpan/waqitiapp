package com.waqiti.voice.client;

import com.waqiti.voice.client.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Feign client for Fraud Detection Service
 *
 * CRITICAL: Replaces null fraud detection client
 *
 * Handles:
 * - Voice fraud analysis
 * - Transaction risk scoring
 * - Behavioral analysis
 * - Device fingerprinting
 * - Velocity checks
 *
 * Resilience:
 * - Circuit breaker with higher failure threshold (60%)
 * - Fast timeout (10s)
 * - Fallback to safe defaults (allow with logging)
 */
@FeignClient(
    name = "fraud-detection-service",
    url = "${services.fraud-detection-service.url:http://fraud-detection-service}",
    path = "/api/v1",
    configuration = FeignConfig.class,
    fallback = FraudDetectionServiceFallback.class
)
public interface FraudDetectionServiceClient {

    /**
     * Analyze voice payment for fraud
     * CRITICAL: Replaces null fraud detection
     *
     * @param request Fraud analysis request with voice and transaction details
     * @return Fraud analysis result with risk score
     */
    @PostMapping("/fraud/analyze/voice-payment")
    FraudAnalysisResult analyzeVoicePayment(@RequestBody FraudAnalysisRequest request);

    /**
     * Analyze voice command for fraud (general)
     *
     * @param request Voice fraud request
     * @return Fraud analysis result
     */
    @PostMapping("/fraud/analyze/voice-command")
    VoiceFraudResult analyzeVoiceCommand(@RequestBody VoiceFraudRequest request);

    /**
     * Check transaction velocity (rate limiting)
     *
     * @param userId User ID
     * @return Velocity check result
     */
    @GetMapping("/fraud/velocity-check/{userId}")
    VelocityCheckResult checkVelocity(@PathVariable UUID userId);

    /**
     * Analyze device fingerprint
     *
     * @param request Device analysis request
     * @return Device risk assessment
     */
    @PostMapping("/fraud/analyze/device")
    DeviceRiskResult analyzeDevice(@RequestBody DeviceAnalysisRequest request);

    /**
     * Check if transaction matches known fraud patterns
     *
     * @param request Transaction details
     * @return Pattern match result
     */
    @PostMapping("/fraud/pattern-match")
    FraudPatternResult checkFraudPatterns(@RequestBody TransactionPatternRequest request);

    /**
     * Get user's fraud score
     *
     * @param userId User ID
     * @return Current fraud score
     */
    @GetMapping("/fraud/score/{userId}")
    FraudScore getUserFraudScore(@PathVariable UUID userId);

    /**
     * Report suspected fraud
     *
     * @param request Fraud report details
     * @return Report submission result
     */
    @PostMapping("/fraud/report")
    FraudReportResult reportFraud(@RequestBody FraudReportRequest request);

    /**
     * Analyze behavioral anomalies
     *
     * @param userId User ID
     * @param request Behavior analysis request
     * @return Anomaly detection result
     */
    @PostMapping("/fraud/analyze/behavior/{userId}")
    BehaviorAnalysisResult analyzeBehavior(
            @PathVariable UUID userId,
            @RequestBody BehaviorAnalysisRequest request);

    /**
     * Check if IP address is suspicious
     *
     * @param ipAddress IP address
     * @return IP risk assessment
     */
    @GetMapping("/fraud/ip-check")
    IpRiskResult checkIpAddress(@RequestParam String ipAddress);

    /**
     * Verify biometric authenticity (detect spoofing)
     *
     * @param request Biometric verification request
     * @return Spoofing detection result
     */
    @PostMapping("/fraud/verify-biometric")
    BiometricVerificationResult verifyBiometric(@RequestBody BiometricVerificationRequest request);

    /**
     * Update fraud model with transaction outcome
     * (Machine learning feedback loop)
     *
     * @param transactionId Transaction ID
     * @param outcome Actual outcome (fraud/legitimate)
     */
    @PostMapping("/fraud/feedback")
    void provideFeedback(
            @RequestParam String transactionId,
            @RequestParam FraudOutcome outcome);

    /**
     * Health check
     */
    @GetMapping("/health")
    String healthCheck();

    /**
     * Fraud outcome enum for ML feedback
     */
    enum FraudOutcome {
        LEGITIMATE,
        FRAUD_CONFIRMED,
        FALSE_POSITIVE,
        UNDER_INVESTIGATION
    }
}
