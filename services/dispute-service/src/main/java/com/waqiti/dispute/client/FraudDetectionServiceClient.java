package com.waqiti.dispute.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Feign Client for Fraud Detection Service
 *
 * Analyzes fraud risk for disputed transactions
 * Provides ML-powered fraud scores and recommendations
 *
 * @author Waqiti Platform Team
 * @version 1.0
 */
@FeignClient(
    name = "fraud-detection-service",
    url = "${services.fraud-detection-service.url:http://fraud-detection-service:8080}",
    fallback = FraudDetectionServiceClientFallback.class,
    configuration = FeignClientConfiguration.class
)
public interface FraudDetectionServiceClient {

    /**
     * Analyze fraud risk for a transaction
     */
    @GetMapping("/api/v1/fraud/analyze/{transactionId}")
    FraudAnalysisResponse analyzeFraudRisk(
            @PathVariable("transactionId") UUID transactionId,
            @RequestHeader("X-Service-Auth") String serviceToken);

    /**
     * Perform comprehensive fraud check for dispute
     */
    @PostMapping("/api/v1/fraud/dispute-check")
    DisputeFraudCheckResponse checkDisputeFraud(
            @RequestBody DisputeFraudCheckRequest request,
            @RequestHeader("X-Service-Auth") String serviceToken);

    /**
     * Fraud Analysis Response
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class FraudAnalysisResponse {
        private UUID transactionId;
        private Double fraudScore;  // 0.0 (no risk) to 1.0 (definite fraud)
        private String riskLevel;   // LOW, MEDIUM, HIGH, CRITICAL
        private Boolean isFraudulent;
        private String riskFactors;
        private String recommendation;
        private Map<String, Object> detailedAnalysis;
        private LocalDateTime analyzedAt;
    }

    /**
     * Dispute Fraud Check Request
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class DisputeFraudCheckRequest {
        private UUID disputeId;
        private UUID transactionId;
        private UUID userId;
        private String disputeReason;
        private BigDecimal disputedAmount;
        private String transactionType;
        private LocalDateTime transactionDate;
        private String ipAddress;
        private String deviceId;
        private boolean firstTimeDispute;
    }

    /**
     * Dispute Fraud Check Response
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class DisputeFraudCheckResponse {
        private UUID disputeId;
        private UUID transactionId;
        private Double fraudScore;
        private String riskLevel;
        private Boolean suspectedFraud;
        private Boolean isFirstPartyFraud;  // Customer committed fraud
        private Boolean isThirdPartyFraud;  // External fraudster
        private String fraudType;  // CARD_NOT_PRESENT, ACCOUNT_TAKEOVER, etc.
        private String riskFactors;
        private String recommendation;  // APPROVE_DISPUTE, REJECT_DISPUTE, INVESTIGATE
        private Integer confidenceLevel;  // 0-100
        private Map<String, Object> riskIndicators;
        private LocalDateTime analyzedAt;
    }
}
