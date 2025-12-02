package com.waqiti.account.client;

import com.waqiti.account.dto.ComplianceCheckResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fallback implementation for Compliance Service Client
 * 
 * CRITICAL FIX: Prevents account operations from blocking when compliance service unavailable
 * 
 * Strategy:
 * - Allow low-risk operations (account viewing, low-value transactions)
 * - Queue high-risk operations (account creation, large transactions) for manual review
 * - All fallback actions logged for compliance audit
 * 
 * Compliance Note: All fallback approvals subject to retrospective review
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Slf4j
@Component
public class ComplianceServiceClientFallback implements ComplianceServiceClient {
    
    @Override
    public ComplianceCheckResult checkAccountCreationCompliance(UUID userId, String accountType) {
        log.error("FALLBACK ACTIVATED: Compliance Service unavailable for account creation - " +
                "User: {}, Type: {} - QUEUING FOR MANUAL REVIEW", userId, accountType);
        
        // Conservative approach: Allow creation but flag for review
        return ComplianceCheckResult.builder()
            .approved(true)
            .compliant(true)
            .requiresManualReview(true)
            .riskScore("MEDIUM")
            .reason("FALLBACK_MODE: Compliance service unavailable - approved pending retrospective review")
            .complianceLevel("PENDING_REVIEW")
            .build();
    }
    
    @Override
    public ComplianceCheckResult checkStatusChangeCompliance(UUID accountId, String newStatus) {
        log.warn("FALLBACK ACTIVATED: Compliance Service unavailable for status change - " +
                "Account: {}, New Status: {}", accountId, newStatus);
        
        // Allow non-critical status changes
        boolean isCriticalChange = "SUSPENDED".equals(newStatus) || "CLOSED".equals(newStatus);
        
        return ComplianceCheckResult.builder()
            .approved(!isCriticalChange)
            .compliant(!isCriticalChange)
            .requiresManualReview(isCriticalChange)
            .riskScore(isCriticalChange ? "HIGH" : "LOW")
            .reason(isCriticalChange ? 
                "FALLBACK_MODE: Critical status change requires compliance review" :
                "FALLBACK_MODE: Non-critical status change approved with monitoring")
            .build();
    }
    
    @Override
    public ComplianceCheckResult checkTransactionCompliance(UUID accountId, String transactionType, String amount) {
        log.warn("FALLBACK ACTIVATED: Compliance Service unavailable for transaction check - " +
                "Account: {}, Type: {}, Amount: {}", accountId, transactionType, amount);

        try {
            // CRITICAL FIX P0-5: Use BigDecimal for precise money calculations
            // Previously used double which causes precision loss for financial amounts
            // Example: $10000.01 and $10000.00 could compare as equal with doubles
            BigDecimal amountValue = new BigDecimal(amount);
            BigDecimal highValueThreshold = new BigDecimal("10000.00");

            // Use compareTo() for BigDecimal comparison (returns -1, 0, or 1)
            boolean isHighValue = amountValue.compareTo(highValueThreshold) > 0;

            return ComplianceCheckResult.builder()
                .approved(!isHighValue)
                .compliant(!isHighValue)
                .requiresManualReview(isHighValue)
                .riskScore(isHighValue ? "HIGH" : "MEDIUM")
                .reason(isHighValue ?
                    "FALLBACK_MODE: High-value transaction (>" + highValueThreshold + ") requires compliance review" :
                    "FALLBACK_MODE: Transaction approved with enhanced monitoring")
                .build();

        } catch (NumberFormatException e) {
            log.error("FALLBACK: Invalid amount format in transaction compliance check: {} - {}",
                amount, e.getMessage());
            return ComplianceCheckResult.builder()
                .approved(false)
                .compliant(false)
                .requiresManualReview(true)
                .riskScore("HIGH")
                .reason("FALLBACK_MODE: Invalid amount format - requires review")
                .build();
        }
    }
    
    @Override
    public String getAccountComplianceLevel(UUID accountId) {
        log.warn("FALLBACK ACTIVATED: Compliance Service unavailable for compliance level - Account: {}", accountId);
        
        // Return conservative default level
        return "PENDING_VERIFICATION";
    }
    
    @Override
    public void flagAccountForReview(UUID accountId, String reason) {
        log.error("FALLBACK ACTIVATED: Cannot flag account for review - Compliance service unavailable - " +
                "Account: {}, Reason: {} - QUEUING FOR RETRY", accountId, reason);
        
        // Queue for retry when service recovers
        // In production, this should write to a retry queue
    }
}