package com.waqiti.kyc.service;

import com.waqiti.kyc.dto.FraudCheckRequest;
import com.waqiti.kyc.dto.FraudCheckResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Fraud Detection Service (KYC Integration)
 *
 * CRITICAL FIX: This service was missing and causing NullPointerException
 * in IdentityVerificationService.java
 *
 * Integrates with main fraud-detection-service for:
 * - Identity fraud detection
 * - Document fraud detection
 * - Velocity checks (rapid KYC submissions)
 * - Risk scoring
 * - Behavioral analysis
 *
 * This is a lightweight wrapper that calls the main fraud detection service
 * while adding KYC-specific fraud checks.
 *
 * @author Waqiti Platform Team
 * @since 2025-10-31 - CRITICAL FIX
 */
@Slf4j
@Service
public class FraudDetectionService {

    private final RestTemplate restTemplate;

    @Value("${fraud-detection.service.url:http://fraud-detection-service:8090}")
    private String fraudDetectionServiceUrl;

    @Value("${fraud-detection.kyc.velocity.max-submissions-per-day:5}")
    private int maxKycSubmissionsPerDay;

    @Value("${fraud-detection.kyc.velocity.max-failures-per-day:10}")
    private int maxKycFailuresPerDay;

    // In-memory cache for velocity checks (Redis in production)
    private final Map<String, List<LocalDateTime>> submissionHistory = new HashMap<>();
    private final Map<String, List<LocalDateTime>> failureHistory = new HashMap<>();

    public FraudDetectionService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Perform comprehensive fraud check for KYC submission
     *
     * @param userId User ID submitting KYC
     * @param documentType Type of document submitted
     * @param metadata Additional context
     * @return Fraud check result with risk score and recommendations
     */
    public FraudCheckResult checkKycFraud(UUID userId, String documentType, Map<String, Object> metadata) {
        log.info("Performing KYC fraud check for user: {}, documentType: {}", userId, documentType);

        FraudCheckResult result = FraudCheckResult.builder()
            .userId(userId)
            .checkType("KYC_SUBMISSION")
            .timestamp(LocalDateTime.now())
            .build();

        try {
            // 1. Velocity checks
            VelocityCheckResult velocityResult = performVelocityChecks(userId);
            result.addCheck("velocity", velocityResult.isPassed(), velocityResult.getRiskScore());

            // 2. Document fraud checks
            DocumentFraudResult docFraudResult = checkDocumentFraud(documentType, metadata);
            result.addCheck("document_fraud", docFraudResult.isPassed(), docFraudResult.getRiskScore());

            // 3. Identity fraud checks
            IdentityFraudResult identityResult = checkIdentityFraud(userId, metadata);
            result.addCheck("identity_fraud", identityResult.isPassed(), identityResult.getRiskScore());

            // 4. Behavioral analysis
            BehavioralResult behavioralResult = analyzeBehavior(userId, metadata);
            result.addCheck("behavioral", behavioralResult.isPassed(), behavioralResult.getRiskScore());

            // 5. Call main fraud detection service for ML-based scoring
            BigDecimal mlRiskScore = callFraudDetectionService(userId, metadata);
            result.addCheck("ml_scoring", mlRiskScore.compareTo(new BigDecimal("0.7")) < 0, mlRiskScore);

            // Calculate overall risk score (weighted average)
            BigDecimal overallRiskScore = calculateOverallRiskScore(result);
            result.setRiskScore(overallRiskScore);

            // Determine action based on risk score
            result.setRecommendedAction(determineAction(overallRiskScore));

            log.info("KYC fraud check complete for user: {}, riskScore: {}, action: {}",
                userId, overallRiskScore, result.getRecommendedAction());

            return result;

        } catch (Exception e) {
            log.error("Error during KYC fraud check for user: {}", userId, e);

            // Fail-safe: Return high risk on error
            result.setRiskScore(new BigDecimal("0.9"));
            result.setRecommendedAction("MANUAL_REVIEW");
            result.setErrorMessage("Fraud check failed: " + e.getMessage());

            return result;
        }
    }

    /**
     * Velocity checks: Detect rapid submissions or excessive failures
     */
    private VelocityCheckResult performVelocityChecks(UUID userId) {
        String userKey = userId.toString();

        // Clean old entries (older than 24 hours)
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        submissionHistory.computeIfPresent(userKey, (k, v) -> {
            v.removeIf(timestamp -> timestamp.isBefore(cutoff));
            return v.isEmpty() ? null : v;
        });

        failureHistory.computeIfPresent(userKey, (k, v) -> {
            v.removeIf(timestamp -> timestamp.isBefore(cutoff));
            return v.isEmpty() ? null : v;
        });

        // Check submission velocity
        int submissionsToday = submissionHistory.getOrDefault(userKey, Collections.emptyList()).size();
        int failuresToday = failureHistory.getOrDefault(userKey, Collections.emptyList()).size();

        // Record this submission
        submissionHistory.computeIfAbsent(userKey, k -> new ArrayList<>()).add(LocalDateTime.now());

        VelocityCheckResult result = new VelocityCheckResult();

        if (submissionsToday >= maxKycSubmissionsPerDay) {
            log.warn("Excessive KYC submissions detected for user: {}, count: {}", userId, submissionsToday);
            result.setPassed(false);
            result.setRiskScore(new BigDecimal("0.8"));
            result.setReason("Excessive submissions: " + submissionsToday + " in 24h");
            return result;
        }

        if (failuresToday >= maxKycFailuresPerDay) {
            log.warn("Excessive KYC failures detected for user: {}, count: {}", userId, failuresToday);
            result.setPassed(false);
            result.setRiskScore(new BigDecimal("0.9"));
            result.setReason("Excessive failures: " + failuresToday + " in 24h");
            return result;
        }

        // Calculate risk based on frequency
        BigDecimal riskScore = BigDecimal.valueOf(submissionsToday * 0.1 + failuresToday * 0.15);
        result.setPassed(true);
        result.setRiskScore(riskScore.min(new BigDecimal("0.7")));

        return result;
    }

    /**
     * Check for document fraud indicators
     */
    private DocumentFraudResult checkDocumentFraud(String documentType, Map<String, Object> metadata) {
        DocumentFraudResult result = new DocumentFraudResult();
        BigDecimal riskScore = BigDecimal.ZERO;

        // Check for suspicious patterns in metadata
        if (metadata.containsKey("image_quality") &&
            ((Number) metadata.get("image_quality")).doubleValue() < 0.3) {
            log.warn("Low quality document image detected");
            riskScore = riskScore.add(new BigDecimal("0.3"));
        }

        if (metadata.containsKey("tampered") &&
            Boolean.TRUE.equals(metadata.get("tampered"))) {
            log.error("Document tampering detected");
            result.setPassed(false);
            result.setRiskScore(new BigDecimal("0.95"));
            result.setReason("Document tampering detected");
            return result;
        }

        if (metadata.containsKey("duplicate_document") &&
            Boolean.TRUE.equals(metadata.get("duplicate_document"))) {
            log.warn("Duplicate document detected");
            riskScore = riskScore.add(new BigDecimal("0.5"));
        }

        result.setPassed(riskScore.compareTo(new BigDecimal("0.6")) < 0);
        result.setRiskScore(riskScore);
        return result;
    }

    /**
     * Check for identity fraud
     */
    private IdentityFraudResult checkIdentityFraud(UUID userId, Map<String, Object> metadata) {
        IdentityFraudResult result = new IdentityFraudResult();
        BigDecimal riskScore = BigDecimal.ZERO;

        // Check for synthetic identity indicators
        if (metadata.containsKey("name_mismatch") &&
            Boolean.TRUE.equals(metadata.get("name_mismatch"))) {
            log.warn("Name mismatch detected for user: {}", userId);
            riskScore = riskScore.add(new BigDecimal("0.4"));
        }

        // Check for stolen identity indicators
        if (metadata.containsKey("age_mismatch") &&
            Boolean.TRUE.equals(metadata.get("age_mismatch"))) {
            log.warn("Age mismatch detected for user: {}", userId);
            riskScore = riskScore.add(new BigDecimal("0.3"));
        }

        // Check for known fraud patterns
        if (metadata.containsKey("fraud_database_hit") &&
            Boolean.TRUE.equals(metadata.get("fraud_database_hit"))) {
            log.error("Fraud database hit for user: {}", userId);
            result.setPassed(false);
            result.setRiskScore(new BigDecimal("1.0"));
            result.setReason("Match in fraud database");
            return result;
        }

        result.setPassed(riskScore.compareTo(new BigDecimal("0.6")) < 0);
        result.setRiskScore(riskScore);
        return result;
    }

    /**
     * Analyze user behavior patterns
     */
    private BehavioralResult analyzeBehavior(UUID userId, Map<String, Object> metadata) {
        BehavioralResult result = new BehavioralResult();
        BigDecimal riskScore = BigDecimal.ZERO;

        // Check for suspicious timing (e.g., submissions at unusual hours)
        LocalDateTime now = LocalDateTime.now();
        if (now.getHour() >= 2 && now.getHour() <= 5) {
            log.info("KYC submission during unusual hours: {}", now.getHour());
            riskScore = riskScore.add(new BigDecimal("0.1"));
        }

        // Check for rapid progression (account created recently)
        if (metadata.containsKey("account_age_days")) {
            int accountAgeDays = ((Number) metadata.get("account_age_days")).intValue();
            if (accountAgeDays < 1) {
                log.warn("KYC submission for very new account: {} days", accountAgeDays);
                riskScore = riskScore.add(new BigDecimal("0.3"));
            }
        }

        // Check device fingerprint
        if (metadata.containsKey("suspicious_device") &&
            Boolean.TRUE.equals(metadata.get("suspicious_device"))) {
            log.warn("Suspicious device detected for user: {}", userId);
            riskScore = riskScore.add(new BigDecimal("0.2"));
        }

        result.setPassed(riskScore.compareTo(new BigDecimal("0.5")) < 0);
        result.setRiskScore(riskScore);
        return result;
    }

    /**
     * Call main fraud detection service for ML-based scoring
     */
    private BigDecimal callFraudDetectionService(UUID userId, Map<String, Object> metadata) {
        try {
            FraudCheckRequest request = FraudCheckRequest.builder()
                .userId(userId.toString())
                .checkType("KYC_SUBMISSION")
                .metadata(metadata)
                .build();

            String url = fraudDetectionServiceUrl + "/api/v1/fraud/check";

            // Call fraud detection service
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

            if (response != null && response.containsKey("riskScore")) {
                return new BigDecimal(response.get("riskScore").toString());
            }

            log.warn("No risk score returned from fraud detection service");
            return new BigDecimal("0.5"); // Default medium risk

        } catch (Exception e) {
            log.error("Error calling fraud detection service", e);
            return new BigDecimal("0.5"); // Default medium risk on error
        }
    }

    /**
     * Calculate overall risk score (weighted average)
     */
    private BigDecimal calculateOverallRiskScore(FraudCheckResult result) {
        // Weights for each check type
        Map<String, BigDecimal> weights = Map.of(
            "velocity", new BigDecimal("0.15"),
            "document_fraud", new BigDecimal("0.25"),
            "identity_fraud", new BigDecimal("0.30"),
            "behavioral", new BigDecimal("0.10"),
            "ml_scoring", new BigDecimal("0.20")
        );

        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;

        for (Map.Entry<String, BigDecimal> entry : weights.entrySet()) {
            BigDecimal checkScore = result.getCheckScores().getOrDefault(entry.getKey(), BigDecimal.ZERO);
            totalScore = totalScore.add(checkScore.multiply(entry.getValue()));
            totalWeight = totalWeight.add(entry.getValue());
        }

        return totalWeight.compareTo(BigDecimal.ZERO) > 0 ?
            totalScore.divide(totalWeight, 4, java.math.RoundingMode.HALF_UP) :
            new BigDecimal("0.5");
    }

    /**
     * Determine recommended action based on risk score
     */
    private String determineAction(BigDecimal riskScore) {
        if (riskScore.compareTo(new BigDecimal("0.8")) >= 0) {
            return "REJECT";
        } else if (riskScore.compareTo(new BigDecimal("0.6")) >= 0) {
            return "MANUAL_REVIEW";
        } else if (riskScore.compareTo(new BigDecimal("0.3")) >= 0) {
            return "ENHANCED_DUE_DILIGENCE";
        } else {
            return "APPROVE";
        }
    }

    /**
     * Record KYC failure for velocity tracking
     */
    public void recordKycFailure(UUID userId) {
        String userKey = userId.toString();
        failureHistory.computeIfAbsent(userKey, k -> new ArrayList<>()).add(LocalDateTime.now());
        log.info("Recorded KYC failure for user: {}", userId);
    }

    // Inner result classes
    private static class VelocityCheckResult {
        private boolean passed = true;
        private BigDecimal riskScore = BigDecimal.ZERO;
        private String reason;

        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
        public BigDecimal getRiskScore() { return riskScore; }
        public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    private static class DocumentFraudResult {
        private boolean passed = true;
        private BigDecimal riskScore = BigDecimal.ZERO;
        private String reason;

        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
        public BigDecimal getRiskScore() { return riskScore; }
        public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    private static class IdentityFraudResult {
        private boolean passed = true;
        private BigDecimal riskScore = BigDecimal.ZERO;
        private String reason;

        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
        public BigDecimal getRiskScore() { return riskScore; }
        public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    private static class BehavioralResult {
        private boolean passed = true;
        private BigDecimal riskScore = BigDecimal.ZERO;

        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
        public BigDecimal getRiskScore() { return riskScore; }
        public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }
    }
}
