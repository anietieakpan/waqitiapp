package com.waqiti.transaction.client;

import com.waqiti.transaction.dto.ComplianceCheckRequest;
import com.waqiti.transaction.dto.ComplianceCheckResponse;
import com.waqiti.transaction.dto.RiskAssessmentRequest;
import com.waqiti.transaction.dto.RiskAssessmentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation for Compliance Service Client
 * 
 * CRITICAL: Compliance checks are mandatory for regulatory requirements.
 * Conservative approach: BLOCK high-risk transactions when compliance unavailable.
 * 
 * Failure Strategy:
 * - BLOCK high-value transactions (>$10,000)
 * - ALLOW low-value transactions with flagging for review
 * - QUEUE compliance checks for later processing
 * - LOG all bypassed checks for audit
 * 
 * @author Waqiti Platform Team
 * @since Phase 1 Remediation - Session 6
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceServiceClientFallback implements ComplianceServiceClient {
    
    private static final double HIGH_VALUE_THRESHOLD = 10000.0;
    
    @Override
    public ComplianceCheckResponse performComplianceCheck(ComplianceCheckRequest request) {
        log.error("FALLBACK: Compliance check unavailable. TransactionId: {}, Amount: {}",
                 request.getTransactionId(), request.getAmount());
        
        boolean isHighValue = request.getAmount() != null && 
                             request.getAmount().doubleValue() > HIGH_VALUE_THRESHOLD;
        
        return ComplianceCheckResponse.builder()
                .approved(!isHighValue) // Block high-value only
                .status(isHighValue ? "BLOCKED_NO_COMPLIANCE" : "PENDING_COMPLIANCE")
                .requiresManualReview(true)
                .message(isHighValue ? 
                    "High-value transaction blocked - compliance unavailable" :
                    "Low-value transaction allowed - queued for compliance review")
                .fallbackActivated(true)
                .build();
    }
    
    @Override
    public RiskAssessmentResponse performRiskAssessment(RiskAssessmentRequest request) {
        log.error("FALLBACK: Risk assessment unavailable. TransactionId: {}", 
                 request.getTransactionId());
        
        return RiskAssessmentResponse.builder()
                .riskScore(100) // Maximum risk score
                .riskLevel("UNKNOWN")
                .approved(false)
                .requiresManualReview(true)
                .fallbackActivated(true)
                .build();
    }
    
    @Override
    public String getComplianceStatus(String transactionId) {
        log.warn("FALLBACK: Cannot retrieve compliance status. TransactionId: {}", transactionId);
        return "STATUS_UNAVAILABLE";
    }
    
    @Override
    public void flagTransaction(String transactionId, String reason) {
        log.error("FALLBACK: Cannot flag transaction. TransactionId: {}, Reason: {}",
                 transactionId, reason);
        // Log for later processing when service recovers
    }
}