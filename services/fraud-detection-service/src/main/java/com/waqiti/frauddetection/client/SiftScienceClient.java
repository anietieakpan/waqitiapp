package com.waqiti.frauddetection.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.frauddetection.dto.FraudDetectionRequest;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * P0-001 CRITICAL FIX: Real Sift Science ML Fraud Detection Integration
 *
 * Replaces fake neural network with production-grade Sift Science API.
 *
 * Sift Science provides:
 * - Real-time ML fraud scoring
 * - Global fraud network intelligence
 * - Device fingerprinting
 * - Behavioral biometrics
 * - AML/KYC screening
 *
 * @author Waqiti Fraud Detection Team
 * @version 2.0.0
 * @since 2025-10-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiftScienceClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${fraud-detection.sift.api-key}")
    private String siftApiKey;

    @Value("${fraud-detection.sift.account-id}")
    private String siftAccountId;

    @Value("${fraud-detection.sift.api-url:https://api.sift.com/v205}")
    private String siftApiUrl;

    @Value("${fraud-detection.sift.timeout-ms:5000}")
    private int timeoutMs;

    /**
     * Score a transaction using Sift Science ML models
     */
    public SiftScoreResponse scoreTransaction(FraudDetectionRequest request) {
        try {
            log.debug("Scoring transaction with Sift Science: {}", request.getTransactionId());

            // Build Sift Science event payload
            Map<String, Object> event = buildTransactionEvent(request);

            // Send to Sift Science Events API
            String url = siftApiUrl + "/events";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Basic " + siftApiKey);

            HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(event, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, httpRequest, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> body = response.getBody();

                // Get fraud score from response
                Double riskScore = extractRiskScore(body);
                Map<String, Object> riskFactors = extractRiskFactors(body);
                String scoreId = (String) body.get("score_id");

                log.info("Sift Science score received - transactionId: {}, score: {}",
                    request.getTransactionId(), riskScore);

                return SiftScoreResponse.builder()
                    .fraudScore(riskScore)
                    .scoreId(scoreId)
                    .riskFactors(riskFactors)
                    .success(true)
                    .build();

            } else {
                log.error("Sift Science API returned non-200 status: {}", response.getStatusCode());
                return createFallbackResponse("API error: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to score transaction with Sift Science: {}", request.getTransactionId(), e);
            return createFallbackResponse("Exception: " + e.getMessage());
        }
    }

    /**
     * Get user score from Sift Science
     */
    public SiftScoreResponse getUserScore(String userId) {
        try {
            String url = siftApiUrl + "/score/" + userId;
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + siftApiKey);

            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> body = response.getBody();
                Double riskScore = extractRiskScore(body);
                Map<String, Object> riskFactors = extractRiskFactors(body);

                return SiftScoreResponse.builder()
                    .fraudScore(riskScore)
                    .riskFactors(riskFactors)
                    .success(true)
                    .build();
            }

            return createFallbackResponse("API error: " + response.getStatusCode());

        } catch (Exception e) {
            log.error("Failed to get user score from Sift Science: {}", userId, e);
            return createFallbackResponse("Exception: " + e.getMessage());
        }
    }

    /**
     * Report transaction label (fraud/legitimate) for model training
     */
    public void labelTransaction(String transactionId, boolean isFraud, String reason) {
        try {
            Map<String, Object> label = new HashMap<>();
            label.put("$api_key", siftApiKey);
            label.put("$type", "$label");
            label.put("$is_fraud", isFraud);
            label.put("$reasons", reason);
            label.put("$source", "manual_review");
            label.put("$analyst", "fraud_ops");

            String url = siftApiUrl + "/users/" + transactionId + "/labels";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Basic " + siftApiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(label, headers);
            restTemplate.postForEntity(url, request, Map.class);

            log.info("Labeled transaction in Sift Science: {} - fraud: {}", transactionId, isFraud);

        } catch (Exception e) {
            log.error("Failed to label transaction in Sift Science: {}", transactionId, e);
        }
    }

    // ========================================
    // PRIVATE HELPER METHODS
    // ========================================

    private Map<String, Object> buildTransactionEvent(FraudDetectionRequest request) {
        Map<String, Object> event = new HashMap<>();

        // Required fields
        event.put("$type", "$transaction");
        event.put("$api_key", siftApiKey);
        event.put("$user_id", request.getUserId());
        event.put("$transaction_id", request.getTransactionId());
        event.put("$amount", convertToMicros(request.getAmount()));
        event.put("$currency_code", request.getCurrency());
        event.put("$time", Instant.now().toEpochMilli());

        // Transaction type
        event.put("$transaction_type", "$sale");
        event.put("$transaction_status", "$pending");

        // Payment method
        if (request.getPaymentMethod() != null) {
            Map<String, Object> paymentMethod = new HashMap<>();
            paymentMethod.put("$payment_type", request.getPaymentMethod());
            event.put("$payment_method", paymentMethod);
        }

        // Device and session data
        if (request.getDeviceFingerprint() != null) {
            event.put("$browser", Map.of(
                "$user_agent", request.getUserAgent() != null ? request.getUserAgent() : "unknown",
                "$accept_language", "en-US"
            ));
            event.put("$session_id", request.getDeviceFingerprint());
        }

        // IP address
        if (request.getIpAddress() != null) {
            event.put("$ip", request.getIpAddress());
        }

        // Billing address
        if (request.getBillingAddress() != null) {
            event.put("$billing_address", buildAddress(request.getBillingAddress()));
        }

        // Custom fields for enhanced detection
        Map<String, Object> customFields = new HashMap<>();
        customFields.put("merchant_id", request.getMerchantId());
        customFields.put("platform", "waqiti");
        event.put("$custom_fields", customFields);

        return event;
    }

    private Map<String, Object> buildAddress(Map<String, String> address) {
        Map<String, Object> siftAddress = new HashMap<>();
        siftAddress.put("$name", address.get("name"));
        siftAddress.put("$address_1", address.get("line1"));
        siftAddress.put("$city", address.get("city"));
        siftAddress.put("$region", address.get("state"));
        siftAddress.put("$country", address.get("country"));
        siftAddress.put("$zipcode", address.get("postal_code"));
        return siftAddress;
    }

    private long convertToMicros(BigDecimal amount) {
        // Sift Science expects amounts in micros (1 USD = 1,000,000 micros)
        return amount.multiply(BigDecimal.valueOf(1_000_000)).longValue();
    }

    private Double extractRiskScore(Map<String, Object> response) {
        try {
            // Sift Science returns scores in nested structure
            Map<String, Object> scoreResponse = (Map<String, Object>) response.get("score_response");
            if (scoreResponse != null) {
                Map<String, Object> scores = (Map<String, Object>) scoreResponse.get("scores");
                if (scores != null) {
                    Map<String, Object> paymentAbuse = (Map<String, Object>) scores.get("payment_abuse");
                    if (paymentAbuse != null) {
                        Number score = (Number) paymentAbuse.get("score");
                        // Sift scores are 0-100, normalize to 0-1
                        return score != null ? score.doubleValue() / 100.0 : 0.1;
                    }
                }
            }
            return 0.1; // Default low risk
        } catch (Exception e) {
            log.warn("Failed to extract risk score from Sift response", e);
            return 0.1;
        }
    }

    private Map<String, Object> extractRiskFactors(Map<String, Object> response) {
        Map<String, Object> factors = new HashMap<>();
        try {
            Map<String, Object> scoreResponse = (Map<String, Object>) response.get("score_response");
            if (scoreResponse != null) {
                factors.put("workflow_statuses", scoreResponse.get("workflow_statuses"));
                factors.put("user_score", scoreResponse.get("user_score"));
                factors.put("latest_labels", scoreResponse.get("latest_labels"));
            }
        } catch (Exception e) {
            log.warn("Failed to extract risk factors from Sift response", e);
        }
        return factors;
    }

    private SiftScoreResponse createFallbackResponse(String reason) {
        return SiftScoreResponse.builder()
            .fraudScore(0.25) // Conservative fallback score
            .success(false)
            .errorMessage(reason)
            .riskFactors(Map.of("fallback_reason", reason))
            .build();
    }

    @Data
    @Builder
    public static class SiftScoreResponse {
        private Double fraudScore;
        private String scoreId;
        private Map<String, Object> riskFactors;
        private boolean success;
        private String errorMessage;
    }
}
