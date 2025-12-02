package com.waqiti.dispute.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.UUID;

/**
 * Fallback implementation for Fraud Detection Service Client
 *
 * Provides safe defaults when fraud detection service is unavailable
 * Returns conservative fraud scores to ensure manual review
 *
 * @author Waqiti Platform Team
 * @version 1.0
 */
@Slf4j
@Component
public class FraudDetectionServiceClientFallback implements FraudDetectionServiceClient {

    @Override
    public FraudAnalysisResponse analyzeFraudRisk(UUID transactionId, String serviceToken) {
        log.warn("Fraud detection service unavailable - using fallback for transaction: {}", transactionId);

        // Return medium risk to trigger manual review
        return FraudAnalysisResponse.builder()
                .transactionId(transactionId)
                .fraudScore(0.5)  // Medium risk
                .riskLevel("MEDIUM")
                .isFraudulent(false)
                .riskFactors("Service unavailable - manual review required")
                .recommendation("MANUAL_REVIEW")
                .detailedAnalysis(new HashMap<>())
                .analyzedAt(LocalDateTime.now())
                .build();
    }

    @Override
    public DisputeFraudCheckResponse checkDisputeFraud(DisputeFraudCheckRequest request, String serviceToken) {
        log.warn("Fraud detection service unavailable - using fallback for dispute: {}", request.getDisputeId());

        // Return medium risk requiring investigation
        return DisputeFraudCheckResponse.builder()
                .disputeId(request.getDisputeId())
                .transactionId(request.getTransactionId())
                .fraudScore(0.5)
                .riskLevel("MEDIUM")
                .suspectedFraud(false)
                .isFirstPartyFraud(false)
                .isThirdPartyFraud(false)
                .fraudType("UNKNOWN")
                .riskFactors("Service unavailable - manual review required")
                .recommendation("INVESTIGATE")
                .confidenceLevel(0)
                .riskIndicators(new HashMap<>())
                .analyzedAt(LocalDateTime.now())
                .build();
    }
}
