package com.waqiti.transaction.client;

import com.waqiti.transaction.dto.FraudCheckRequest;
import com.waqiti.transaction.dto.FraudCheckResponse;
import com.waqiti.transaction.dto.FraudScoreResponse;
import com.waqiti.transaction.service.FraudDetectionService;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Feign client for Fraud Detection Service with comprehensive operations.
 * Provides all fraud detection capabilities with resilience patterns.
 *
 * P1 FIX: Changed path from /api/fraud to /api/v1/fraud to match actual endpoints
 */
@FeignClient(
    name = "fraud-detection-service",
    path = "/api/v1/fraud",  // ✅ FIXED: Was /api/fraud
    configuration = FraudDetectionClientConfiguration.class,
    fallbackFactory = FraudDetectionServiceClientFallbackFactory.class
)
public interface FraudDetectionServiceClient {

    /**
     * Perform comprehensive fraud check on a transaction.
     * @param request The fraud check request containing transaction details
     * @return FraudCheckResponse with decision and risk assessment
     */
    @PostMapping("/check")
    FraudCheckResponse performFraudCheck(@RequestBody FraudCheckRequest request);

    /**
     * Get fraud score for a specific transaction.
     * @param transactionId The transaction ID to get the score for
     * @return FraudScoreResponse with detailed score breakdown
     */
    @GetMapping("/transaction/{transactionId}/score")
    FraudScoreResponse getFraudScore(@PathVariable String transactionId);

    /**
     * Block a transaction due to fraud detection.
     * @param transactionId The transaction ID to block
     * @param reason The reason for blocking
     */
    @PostMapping("/transaction/{transactionId}/block")
    void blockTransaction(@PathVariable String transactionId, @RequestParam String reason);

    /**
     * Whitelist a transaction (mark as safe).
     * @param transactionId The transaction ID to whitelist
     */
    @PostMapping("/transaction/{transactionId}/whitelist")
    void whitelistTransaction(@PathVariable String transactionId);

    /**
     * Trigger a fraud investigation.
     * @param request The investigation request details
     */
    @PostMapping("/investigation/trigger")
    void triggerInvestigation(@RequestBody FraudDetectionService.FraudInvestigationRequest request);

    /**
     * Check multiple transactions in batch for fraud.
     * @param requests List of fraud check requests
     * @return List of fraud check responses
     */
    @PostMapping("/check/batch")
    ResponseEntity<List<FraudCheckResponse>> checkTransactionsBatch(@RequestBody List<FraudCheckRequest> requests);

    /**
     * Get fraud statistics for a user.
     * @param userId The user ID to get statistics for
     * @return Fraud statistics response
     */
    @GetMapping("/user/{userId}/statistics")
    ResponseEntity<FraudStatisticsResponse> getUserFraudStatistics(@PathVariable String userId);

    /**
     * Update fraud rules configuration.
     * @param configuration The new fraud rules configuration
     * @return Success response
     */
    @PutMapping("/rules/configuration")
    ResponseEntity<Void> updateFraudRulesConfiguration(@RequestBody FraudRulesConfiguration configuration);

    /**
     * Get current fraud detection system health.
     * @return System health response
     */
    @GetMapping("/health")
    ResponseEntity<FraudSystemHealthResponse> getFraudSystemHealth();

    /**
     * Report a false positive.
     * @param transactionId The transaction ID that was falsely flagged
     * @param details Details about the false positive
     * @return Success response
     */
    @PostMapping("/false-positive/{transactionId}")
    ResponseEntity<Void> reportFalsePositive(@PathVariable String transactionId, @RequestBody FalsePositiveReport details);

    /**
     * Data transfer objects for additional endpoints
     */
    record FraudStatisticsResponse(
        String userId,
        Integer totalTransactions,
        Integer flaggedTransactions,
        Double averageRiskScore,
        String riskProfile,
        java.time.LocalDateTime lastAssessment
    ) {}

    record FraudRulesConfiguration(
        java.util.Map<String, Object> rules,
        java.util.List<String> enabledFeatures,
        java.util.Map<String, Double> thresholds
    ) {}

    record FraudSystemHealthResponse(
        String status,
        Double systemLoad,
        Integer activeChecks,
        java.time.LocalDateTime lastUpdate,
        java.util.Map<String, Object> metrics
    ) {}

    record FalsePositiveReport(
        String reportedBy,
        String reason,
        java.util.Map<String, String> evidence,
        java.time.LocalDateTime reportTime
    ) {}
}