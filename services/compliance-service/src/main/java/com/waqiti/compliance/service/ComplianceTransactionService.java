package com.waqiti.compliance.service;

import com.waqiti.common.audit.ComprehensiveAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Compliance Transaction Service
 * 
 * CRITICAL: Manages transaction-level compliance checks and monitoring.
 * Provides real-time transaction screening and compliance validation.
 * 
 * COMPLIANCE IMPACT:
 * - Real-time AML transaction screening
 * - CTR threshold monitoring ($10,000+)
 * - Structuring detection and prevention
 * - OFAC sanctions screening
 * - Enhanced due diligence triggers
 * 
 * BUSINESS IMPACT:
 * - Prevents regulatory violations
 * - Reduces false positive rates
 * - Enables compliant transaction processing
 * - Maintains banking relationships
 * - Protects against financial crime
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ComplianceTransactionService {

    private final ComprehensiveAuditService auditService;
    private final TransactionLimitService transactionLimitService;
    private final RegulatoryFilingService regulatoryFilingService;
    private final SarFilingService sarFilingService;
    private final WalletService walletService;
    private final ComplianceTransactionRepository complianceTransactionRepository;

    /**
     * Screen transaction for compliance before processing
     */
    public ComplianceScreeningResult screenTransaction(UUID userId, String transactionId,
                                                     String transactionType, BigDecimal amount,
                                                     String currency, String counterparty) {
        
        log.debug("COMPLIANCE_TX: Screening transaction {} for user {} type: {} amount: {} {}", 
                transactionId, userId, transactionType, amount, currency);
        
        try {
            ComplianceScreeningResult result = ComplianceScreeningResult.builder()
                .transactionId(transactionId)
                .userId(userId)
                .screeningPassed(true)
                .riskScore(0)
                .build();
            
            // 1. Check transaction limits
            if (transactionLimitService.exceedsLimits(userId, transactionType, amount)) {
                result.setScreeningPassed(false);
                result.addFlag("TRANSACTION_LIMIT_EXCEEDED");
                result.increaseRiskScore(25);
                
                log.warn("COMPLIANCE_TX: Transaction {} exceeds limits for user {}", transactionId, userId);
            }
            
            // 2. Check for CTR threshold
            if (requiresCtrFiling(amount, currency)) {
                result.addFlag("CTR_THRESHOLD_EXCEEDED");
                result.increaseRiskScore(15);
                
                // Create CTR filing
                String ctrId = regulatoryFilingService.submitCurrencyTransactionReport(
                    userId, UUID.fromString(transactionId), amount, currency, transactionType);
                
                result.setCtrFilingId(ctrId);
                
                log.warn("COMPLIANCE_TX: CTR filing {} created for transaction {} amount: {} {}", 
                        ctrId, transactionId, amount, currency);
            }
            
            // 3. Check for structuring patterns
            if (detectStructuring(userId, amount, currency)) {
                result.addFlag("POTENTIAL_STRUCTURING");
                result.increaseRiskScore(40);
                result.setScreeningPassed(false);
                
                log.warn("COMPLIANCE_TX: Potential structuring detected for user {} transaction {}", 
                        userId, transactionId);
            }
            
            // 4. OFAC sanctions screening
            if (isOfacSanctioned(counterparty)) {
                result.addFlag("OFAC_SANCTIONS_MATCH");
                result.increaseRiskScore(100);
                result.setScreeningPassed(false);
                
                // Submit OFAC compliance report
                String ofacReportId = regulatoryFilingService.submitOfacComplianceReport(
                    userId, counterparty, "TRANSACTION_BLOCKED", "COMPLIANCE_CASE_" + UUID.randomUUID());
                
                result.setOfacReportId(ofacReportId);
                
                log.error("COMPLIANCE_TX: OFAC SANCTIONS MATCH - Transaction {} blocked for user {} counterparty: {}", 
                        transactionId, userId, counterparty);
            }
            
            // 5. High-risk transaction patterns
            if (isHighRiskTransaction(userId, transactionType, amount, counterparty)) {
                result.addFlag("HIGH_RISK_TRANSACTION");
                result.increaseRiskScore(30);
                
                if (result.getRiskScore() >= 75) {
                    result.setScreeningPassed(false);
                }
                
                log.warn("COMPLIANCE_TX: High-risk transaction detected for user {} transaction {}", 
                        userId, transactionId);
            }
            
            // 6. Enhanced monitoring requirements
            if (requiresEnhancedMonitoring(result)) {
                result.addFlag("ENHANCED_MONITORING_REQUIRED");
                
                log.warn("COMPLIANCE_TX: Enhanced monitoring required for user {} transaction {}", 
                        userId, transactionId);
            }
            
            // Audit compliance screening
            auditService.auditComplianceEvent(
                "TRANSACTION_COMPLIANCE_SCREENING",
                userId.toString(),
                "Transaction screened for compliance: " + (result.isScreeningPassed() ? "PASSED" : "FAILED"),
                Map.of(
                    "transactionId", transactionId,
                    "userId", userId,
                    "transactionType", transactionType,
                    "amount", amount,
                    "currency", currency,
                    "counterparty", counterparty,
                    "riskScore", result.getRiskScore(),
                    "flags", result.getFlags(),
                    "screeningResult", result.isScreeningPassed() ? "PASSED" : "FAILED"
                )
            );
            
            log.debug("COMPLIANCE_TX: Transaction {} screening completed - Result: {} Risk Score: {}", 
                    transactionId, result.isScreeningPassed() ? "PASSED" : "FAILED", result.getRiskScore());
            
            return result;
            
        } catch (Exception e) {
            log.error("COMPLIANCE_TX: Failed to screen transaction {} for user {}", transactionId, userId, e);
            
            // Return failed screening on error
            return ComplianceScreeningResult.builder()
                .transactionId(transactionId)
                .userId(userId)
                .screeningPassed(false)
                .riskScore(100)
                .build();
        }
    }

    /**
     * Process post-transaction compliance actions
     */
    public void processPostTransactionCompliance(UUID userId, String transactionId,
                                               String transactionType, BigDecimal amount,
                                               String currency, boolean transactionCompleted) {
        
        log.debug("COMPLIANCE_TX: Processing post-transaction compliance for {} user {} completed: {}", 
                transactionId, userId, transactionCompleted);
        
        try {
            if (transactionCompleted) {
                // Record transaction for compliance monitoring
                walletService.recordWalletTransaction(userId, "DEFAULT_WALLET", transactionId,
                    transactionType, amount, currency);
                
                // Update usage against limits
                updateTransactionLimitUsage(userId, transactionType, amount);
                
                // Check for suspicious activity patterns
                checkSuspiciousActivityPatterns(userId, transactionId, transactionType, amount, currency);
            }
            
            // Audit post-transaction processing
            auditService.auditComplianceEvent(
                "POST_TRANSACTION_COMPLIANCE_PROCESSED",
                userId.toString(),
                "Post-transaction compliance processing completed",
                Map.of(
                    "transactionId", transactionId,
                    "userId", userId,
                    "transactionType", transactionType,
                    "amount", amount,
                    "currency", currency,
                    "transactionCompleted", transactionCompleted,
                    "processedAt", LocalDateTime.now()
                )
            );
            
        } catch (Exception e) {
            log.error("COMPLIANCE_TX: Failed to process post-transaction compliance for transaction {}", 
                    transactionId, e);
        }
    }

    // Helper methods

    private boolean requiresCtrFiling(BigDecimal amount, String currency) {
        return "USD".equals(currency) && amount.compareTo(BigDecimal.valueOf(10000)) >= 0;
    }

    private boolean detectStructuring(UUID userId, BigDecimal amount, String currency) {
        // Implementation would check for patterns of transactions just under CTR threshold
        if ("USD".equals(currency) && 
            amount.compareTo(BigDecimal.valueOf(9000)) >= 0 && 
            amount.compareTo(BigDecimal.valueOf(10000)) < 0) {
            
            // Check recent transaction history for structuring patterns
            return hasRecentSimilarTransactions(userId, amount);
        }
        return false;
    }

    private boolean isOfacSanctioned(String counterparty) {
        // Implementation would check against OFAC sanctions list
        return counterparty != null && 
               (counterparty.toLowerCase().contains("sanctioned") || 
                counterparty.toLowerCase().contains("blocked"));
    }

    private boolean isHighRiskTransaction(UUID userId, String transactionType, 
                                        BigDecimal amount, String counterparty) {
        // Implementation would evaluate transaction risk factors
        return amount.compareTo(BigDecimal.valueOf(50000)) >= 0 ||
               "INTERNATIONAL_WIRE".equals(transactionType) ||
               (counterparty != null && counterparty.toLowerCase().contains("high_risk"));
    }

    private boolean requiresEnhancedMonitoring(ComplianceScreeningResult result) {
        return result.getRiskScore() >= 50 || 
               result.getFlags().contains("POTENTIAL_STRUCTURING") ||
               result.getFlags().contains("OFAC_SANCTIONS_MATCH");
    }

    private boolean hasRecentSimilarTransactions(UUID userId, BigDecimal amount) {
        try {
            LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
            BigDecimal lowerBound = amount.multiply(new BigDecimal("0.9"));
            BigDecimal upperBound = amount.multiply(new BigDecimal("1.1"));
            
            Long similarCount = complianceTransactionRepository
                .countSimilarTransactionsByUserIdAndAmountRangeAndCreatedAtAfter(
                    userId.toString(), lowerBound, upperBound, oneDayAgo);
            
            return similarCount != null && similarCount > 2;
        } catch (Exception e) {
            log.error("Failed to check similar transactions for user {}", userId, e);
            return false;
        }
    }

    private void updateTransactionLimitUsage(UUID userId, String transactionType, BigDecimal amount) {
        // Implementation would update limit usage counters
        log.debug("COMPLIANCE_TX: Updating transaction limit usage for user {} type: {} amount: {}", 
                userId, transactionType, amount);
    }

    private void checkSuspiciousActivityPatterns(UUID userId, String transactionId, 
                                               String transactionType, BigDecimal amount, String currency) {
        // Implementation would analyze patterns for SAR filing requirements
        if (amount.compareTo(BigDecimal.valueOf(100000)) >= 0) {
            log.warn("COMPLIANCE_TX: Large transaction detected - potential SAR review required for user {} transaction {}", 
                    userId, transactionId);
        }
    }

    /**
     * Compliance Screening Result
     */
    @lombok.Data
    @lombok.Builder
    public static class ComplianceScreeningResult {
        private String transactionId;
        private UUID userId;
        private boolean screeningPassed;
        private int riskScore;
        private java.util.List<String> flags = new java.util.ArrayList<>();
        private String ctrFilingId;
        private String ofacReportId;
        
        public void addFlag(String flag) {
            if (flags == null) {
                flags = new java.util.ArrayList<>();
            }
            flags.add(flag);
        }
        
        public void increaseRiskScore(int increase) {
            this.riskScore += increase;
        }
    }
}