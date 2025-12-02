package com.waqiti.payment.client;

import com.waqiti.payment.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;

/**
 * Fallback implementation for FraudDetectionServiceClient
 * 
 * SECURITY FIX: Implements fail-secure pattern
 * When fraud detection service is unavailable, transactions are BLOCKED for manual review
 * This prevents fraudulent transactions from being approved during service outages
 * 
 * Previous implementation defaulted to LOW risk - CRITICAL SECURITY VULNERABILITY
 * 
 * @author Waqiti Security Team
 * @version 2.0 - Security Hardened
 */
@Slf4j
@Component
public class FraudDetectionServiceClientFallback implements FraudDetectionServiceClient {

    /**
     * SECURITY FIX: Fail-secure fraud assessment
     * Returns HIGH risk and blocks transaction when fraud service is unavailable
     */
    @Override
    public ResponseEntity<FraudAssessmentResult> assessFraudRisk(FraudAssessmentRequest request) {
        log.error("SECURITY ALERT: FraudDetectionService unavailable for user: {} - BLOCKING transaction for manual review", 
            request.getUserId());
        
        // SECURITY: Fail-secure - block transaction when fraud service is down
        return ResponseEntity.ok(FraudAssessmentResult.builder()
                .riskScore(new BigDecimal("95")) // HIGH RISK
                .riskLevel("HIGH")
                .blocked(true) // BLOCK TRANSACTION
                .triggers(Collections.singletonList("FRAUD_SERVICE_UNAVAILABLE"))
                .mitigationActions(Collections.singletonList("MANUAL_REVIEW_REQUIRED"))
                .message("Fraud detection service unavailable - transaction blocked for manual security review")
                .requiresManualReview(true)
                .build());
    }

    /**
     * SECURITY FIX: Fail-secure NFC payment check
     */
    @Override
    public ResponseEntity<FraudCheckResult> checkNFCPayment(NFCFraudAssessmentRequest request) {
        log.error("SECURITY ALERT: FraudDetectionService unavailable for NFC payment user: {} - BLOCKING", 
            request.getUserId());
        return ResponseEntity.ok(FraudCheckResult.builder()
                .allowed(false) // BLOCK
                .riskScore(new BigDecimal("95"))
                .riskLevel("HIGH")
                .reason("Fraud service unavailable - NFC payment blocked for security review")
                .requiresManualReview(true)
                .build());
    }

    /**
     * SECURITY FIX: Fail-secure P2P fraud assessment
     */
    @Override
    public ResponseEntity<FraudAssessmentResult> assessP2PFraudRisk(P2PFraudAssessmentRequest request) {
        log.error("SECURITY ALERT: FraudDetectionService unavailable for P2P sender: {} - BLOCKING", 
            request.getSenderId());
        return ResponseEntity.ok(FraudAssessmentResult.builder()
                .riskScore(new BigDecimal("95"))
                .riskLevel("HIGH")
                .blocked(true) // BLOCK
                .triggers(Collections.singletonList("FRAUD_SERVICE_UNAVAILABLE"))
                .mitigationActions(Collections.singletonList("MANUAL_REVIEW_REQUIRED"))
                .message("Fraud service unavailable - P2P transfer blocked for security review")
                .requiresManualReview(true)
                .build());
    }

    /**
     * SECURITY FIX: Fail-secure NFC P2P transfer check
     */
    @Override
    public ResponseEntity<FraudCheckResult> checkNFCP2PTransfer(NFCP2PFraudAssessmentRequest request) {
        log.error("SECURITY ALERT: FraudDetectionService unavailable for NFC P2P sender: {} - BLOCKING", 
            request.getSenderId());
        return ResponseEntity.ok(FraudCheckResult.builder()
                .allowed(false) // BLOCK
                .riskScore(new BigDecimal("95"))
                .riskLevel("HIGH")
                .reason("Fraud service unavailable - NFC P2P transfer blocked for security review")
                .requiresManualReview(true)
                .build());
    }

    /**
     * SECURITY FIX: Fail-secure transaction validation
     */
    @Override
    public ResponseEntity<FraudValidationResult> validateTransaction(TransactionValidationRequest request) {
        log.error("SECURITY ALERT: FraudDetectionService unavailable for transaction: {} - FLAGGING AS INVALID", 
            request.getTransactionId());
        return ResponseEntity.ok(FraudValidationResult.builder()
                .valid(false) // INVALID
                // BEST PRACTICE: Use BigDecimal.ZERO instead of new BigDecimal("0")
                .confidence(BigDecimal.ZERO)
                .reason("Fraud service unavailable - transaction flagged for manual security validation")
                .requiresManualReview(true)
                .build());
    }

    @Override
    public ResponseEntity<FraudReportResult> reportFraud(FraudReportRequest request) {
        log.warn("FraudDetectionService fallback: reportFraud for transaction: {}", request.getTransactionId());
        return ResponseEntity.ok(FraudReportResult.builder()
                .reported(false)
                .reportId(null)
                .message("Fraud service unavailable - report not submitted")
                .build());
    }

    @Override
    public ResponseEntity<MerchantFraudRules> getMerchantFraudRules(String merchantId) {
        log.warn("FraudDetectionService fallback: getMerchantFraudRules for merchant: {}", merchantId);
        return ResponseEntity.ok(MerchantFraudRules.builder()
                .merchantId(merchantId)
                .maxDailyAmount(new BigDecimal("10000"))
                .maxTransactionAmount(new BigDecimal("5000"))
                .velocityChecksEnabled(true)
                .build());
    }

    @Override
    public ResponseEntity<Void> updateFraudThreshold(FraudThresholdUpdateRequest request) {
        log.warn("FraudDetectionService fallback: updateFraudThreshold for merchant: {}", request.getMerchantId());
        return ResponseEntity.ok().build();
    }
}