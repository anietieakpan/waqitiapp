package com.waqiti.payment.client.fallback;

import com.waqiti.payment.client.FraudDetectionClient;
import com.waqiti.payment.client.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Fallback implementation for FraudDetectionClient
 * Provides safe degradation when fraud detection service is unavailable
 */
@Component
@Slf4j
public class FraudDetectionFallback implements FraudDetectionClient {

    private static final String FALLBACK_MESSAGE = "Fraud detection service temporarily unavailable";

    @Override
    public ResponseEntity<FraudScore> evaluateInstantTransfer(UUID senderId, UUID recipientId, BigDecimal amount, String transferMethod) {
        log.error("CRITICAL SECURITY: Fraud evaluation failed for instant transfer - sender: {}, recipient: {}, amount: {} - BLOCKING TRANSACTION (fail-closed)",
            senderId, recipientId, amount);

        // SECURITY FIX: Fail-closed approach - BLOCK ALL transactions when fraud service unavailable
        // This prevents fraudulent transactions from proceeding during service outages
        FraudScore fallbackScore = FraudScore.builder()
            .score(new BigDecimal("95.0")) // Critical risk score
            .riskLevel("CRITICAL")
            .reason("SECURITY: Transaction blocked - Fraud detection service unavailable (fail-closed)")
            .shouldBlock(true) // ALWAYS block during fallback (fail-closed)
            .details(Map.of(
                "fallback", true,
                "securityMode", "fail-closed",
                "evaluatedAt", LocalDateTime.now().toString(),
                "amount", amount.toString(),
                "reason", "Fraud service outage - all transactions blocked for security"
            ))
            .build();

        return ResponseEntity.ok(fallbackScore);
    }

    @Override
    public ResponseEntity<Object> evaluatePayment(Object request) {
        log.warn("FALLBACK: Payment fraud evaluation failed");
        return ResponseEntity.ok(createGenericFallbackResponse());
    }

    @Override
    public ResponseEntity<Object> evaluateTransfer(Object request) {
        log.warn("FALLBACK: Transfer fraud evaluation failed");
        return ResponseEntity.ok(createGenericFallbackResponse());
    }

    @Override
    public ResponseEntity<Object> evaluateTransaction(Object request) {
        log.warn("FALLBACK: Transaction fraud evaluation failed");
        return ResponseEntity.ok(createGenericFallbackResponse());
    }

    @Override
    public ResponseEntity<Object> evaluateBatch(Object request) {
        log.warn("FALLBACK: Batch fraud evaluation failed");
        return ResponseEntity.ok(createGenericFallbackResponse());
    }

    @Override
    public ResponseEntity<Object> getUserRiskScore(UUID userId) {
        log.warn("FALLBACK: User risk score retrieval failed for user: {}", userId);
        return ResponseEntity.ok(Map.of(
            "userId", userId,
            "riskScore", 50.0,
            "riskLevel", "MEDIUM",
            "fallback", true
        ));
    }

    @Override
    public ResponseEntity<Object> getMerchantRiskScore(UUID merchantId) {
        log.warn("FALLBACK: Merchant risk score retrieval failed for merchant: {}", merchantId);
        return ResponseEntity.ok(Map.of(
            "merchantId", merchantId,
            "riskScore", 50.0,
            "riskLevel", "MEDIUM",
            "fallback", true
        ));
    }

    @Override
    public ResponseEntity<Object> calculateRiskScore(Object request) {
        log.warn("FALLBACK: Risk score calculation failed");
        return ResponseEntity.ok(createGenericFallbackResponse());
    }

    @Override
    public ResponseEntity<Object> updateUserRiskScore(UUID userId, Object update) {
        log.warn("FALLBACK: User risk score update failed for user: {}", userId);
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<List<Object>> getFraudRules(String category) {
        log.warn("FALLBACK: Fraud rules retrieval failed");
        return ResponseEntity.ok(Collections.emptyList());
    }

    @Override
    public ResponseEntity<Object> createFraudRule(Object request) {
        log.warn("FALLBACK: Fraud rule creation failed");
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Object> updateFraudRule(UUID ruleId, Object request) {
        log.warn("FALLBACK: Fraud rule update failed for rule: {}", ruleId);
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Void> deleteFraudRule(UUID ruleId) {
        log.warn("FALLBACK: Fraud rule deletion failed for rule: {}", ruleId);
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Object> testFraudRule(UUID ruleId, Map<String, Object> testData) {
        log.warn("FALLBACK: Fraud rule test failed for rule: {}", ruleId);
        return ResponseEntity.ok(Map.of(
            "ruleId", ruleId,
            "testResult", "INCONCLUSIVE",
            "fallback", true
        ));
    }

    // Default implementations for all other methods - return safe fallback responses
    @Override
    public ResponseEntity<Object> predictFraud(Object request) {
        return ResponseEntity.ok(createGenericFallbackResponse());
    }

    @Override
    public ResponseEntity<Object> triggerModelTraining(Object request) {
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<List<Object>> getMLModels() {
        return ResponseEntity.ok(Collections.emptyList());
    }

    @Override
    public ResponseEntity<Object> getModelPerformance(UUID modelId) {
        return ResponseEntity.ok(createGenericFallbackResponse());
    }

    @Override
    public ResponseEntity<Object> checkBlacklist(String type, String value) {
        return ResponseEntity.ok(Map.of(
            "isBlacklisted", false,
            "fallback", true
        ));
    }

    @Override
    public ResponseEntity<Object> addToBlacklist(Object request) {
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Void> removeFromBlacklist(UUID entryId) {
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Object> checkWhitelist(String type, String value) {
        return ResponseEntity.ok(Map.of(
            "isWhitelisted", false,
            "fallback", true
        ));
    }

    @Override
    public ResponseEntity<Object> addToWhitelist(Object request) {
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Object> monitorTransaction(Object request) {
        return ResponseEntity.ok(createGenericFallbackResponse());
    }

    @Override
    public ResponseEntity<List<Object>> getUserFraudAlerts(UUID userId) {
        return ResponseEntity.ok(Collections.emptyList());
    }

    @Override
    public ResponseEntity<Object> investigateAlert(UUID alertId, Object request) {
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Object> resolveAlert(UUID alertId, Object request) {
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Object> detectPatterns(Object request) {
        return ResponseEntity.ok(createGenericFallbackResponse());
    }

    @Override
    public ResponseEntity<Object> analyzeUserPatterns(UUID userId) {
        return ResponseEntity.ok(createGenericFallbackResponse());
    }

    @Override
    public ResponseEntity<List<Object>> getSuspiciousPatterns(String timeframe) {
        return ResponseEntity.ok(Collections.emptyList());
    }

    @Override
    public ResponseEntity<Object> performVelocityCheck(Object request) {
        return ResponseEntity.ok(Map.of(
            "velocityCheckPassed", true,
            "fallback", true
        ));
    }

    @Override
    public ResponseEntity<List<Object>> getVelocityLimits() {
        return ResponseEntity.ok(Collections.emptyList());
    }

    @Override
    public ResponseEntity<Object> updateVelocityLimit(UUID limitId, Object update) {
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Object> analyzeGeographicRisk(Object request) {
        return ResponseEntity.ok(createGenericFallbackResponse());
    }

    @Override
    public ResponseEntity<List<Object>> getHighRiskCountries() {
        return ResponseEntity.ok(Collections.emptyList());
    }

    @Override
    public ResponseEntity<Object> checkGeographicDistance(Object request) {
        return ResponseEntity.ok(createGenericFallbackResponse());
    }

    @Override
    public ResponseEntity<Object> createDeviceFingerprint(Object request) {
        return ResponseEntity.ok(createGenericFallbackResponse());
    }

    @Override
    public ResponseEntity<Object> getDeviceHistory(String deviceId) {
        return ResponseEntity.ok(createGenericFallbackResponse());
    }

    @Override
    public ResponseEntity<Object> assessDeviceRisk(Object request) {
        return ResponseEntity.ok(Map.of(
            "deviceRiskLevel", "MEDIUM",
            "fallback", true
        ));
    }

    @Override
    public ResponseEntity<Object> performAMLCheck(Object request) {
        return ResponseEntity.ok(Map.of(
            "amlStatus", "PASSED",
            "fallback", true
        ));
    }

    @Override
    public ResponseEntity<Object> performKYCVerification(Object request) {
        return ResponseEntity.ok(Map.of(
            "kycStatus", "PENDING",
            "fallback", true
        ));
    }

    @Override
    public ResponseEntity<Object> checkSanctions(Object request) {
        return ResponseEntity.ok(Map.of(
            "sanctionsMatch", false,
            "fallback", true
        ));
    }

    @Override
    public ResponseEntity<Object> checkPoliticallyExposedPerson(Object request) {
        return ResponseEntity.ok(Map.of(
            "pepMatch", false,
            "fallback", true
        ));
    }

    @Override
    public ResponseEntity<Object> getFraudSummary(String startDate, String endDate) {
        return ResponseEntity.ok(createGenericFallbackResponse());
    }

    @Override
    public ResponseEntity<Object> getFraudTrends(String timeframe) {
        return ResponseEntity.ok(createGenericFallbackResponse());
    }

    @Override
    public ResponseEntity<Object> getPerformanceMetrics(String period) {
        return ResponseEntity.ok(createGenericFallbackResponse());
    }

    @Override
    public ResponseEntity<Object> generateCustomReport(Object request) {
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Object> createFraudCase(Object request) {
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Object> getFraudCase(UUID caseId) {
        return ResponseEntity.ok(createGenericFallbackResponse());
    }

    @Override
    public ResponseEntity<Object> updateCaseStatus(UUID caseId, Object update) {
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Object> addCaseEvidence(UUID caseId, Object request) {
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Object> getFraudThresholds() {
        return ResponseEntity.ok(createGenericFallbackResponse());
    }

    @Override
    public ResponseEntity<Object> updateFraudThresholds(Object thresholds) {
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Map<String, Object>> getFraudParameters() {
        return ResponseEntity.ok(Map.of("fallback", true));
    }

    @Override
    public ResponseEntity<Map<String, Object>> updateFraudParameters(Map<String, Object> parameters) {
        return ResponseEntity.ok(parameters);
    }

    @Override
    public ResponseEntity<Object> getServiceHealth() {
        return ResponseEntity.ok(Map.of(
            "status", "DEGRADED",
            "fallback", true
        ));
    }

    @Override
    public ResponseEntity<Object> getModelStatus() {
        return ResponseEntity.ok(createGenericFallbackResponse());
    }

    @Override
    public ResponseEntity<Object> getRuleEngineStatus() {
        return ResponseEntity.ok(Map.of(
            "status", "UNAVAILABLE",
            "fallback", true
        ));
    }

    // Helper method to create generic fallback responses
    private Map<String, Object> createGenericFallbackResponse() {
        return Map.of(
            "fallback", true,
            "message", FALLBACK_MESSAGE,
            "timestamp", LocalDateTime.now().toString()
        );
    }
}