package com.waqiti.corebanking.service;

import com.waqiti.corebanking.client.ComplianceServiceClient;
import com.waqiti.corebanking.client.ComplianceServiceClient.*;
import com.waqiti.corebanking.domain.Account;
import com.waqiti.corebanking.domain.Transaction;
import com.waqiti.corebanking.repository.AccountRepository;
import com.waqiti.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Compliance Integration Service
 * 
 * Provides core banking service integration with the compliance service
 * for real-time transaction screening and regulatory compliance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceIntegrationService {

    private final ComplianceServiceClient complianceServiceClient;
    private final AccountRepository accountRepository;

    /**
     * Perform comprehensive compliance check for a transaction
     */
    public ComplianceCheckResult performTransactionComplianceCheck(Transaction transaction) {
        log.info("Performing compliance check for transaction: {}", transaction.getId());
        
        try {
            // Get account details for screening
            Account sourceAccount = accountRepository.findById(transaction.getSourceAccountId())
                .orElse(null);
            
            if (sourceAccount == null) {
                return ComplianceCheckResult.builder()
                    .transactionId(transaction.getId())
                    .approved(false)
                    .riskLevel("HIGH")
                    .alerts(List.of("Source account not found"))
                    .requiresManualReview(true)
                    .build();
            }

            // 1. AML Screening
            AMLScreeningResponse amlResult = performAMLScreening(transaction, sourceAccount);
            
            // 2. Sanctions Screening
            SanctionsScreeningResponse sanctionsResult = performSanctionsScreening(sourceAccount);
            
            // 3. Risk Assessment
            RiskAssessmentResponse riskResult = performRiskAssessment(transaction, sourceAccount);

            // Aggregate results
            return aggregateComplianceResults(transaction.getId(), amlResult, sanctionsResult, riskResult);

        } catch (Exception e) {
            log.error("Error during compliance check for transaction: {}", transaction.getId(), e);
            return ComplianceCheckResult.builder()
                .transactionId(transaction.getId())
                .approved(false)
                .riskLevel("HIGH")
                .alerts(List.of("Compliance check failed: " + e.getMessage()))
                .requiresManualReview(true)
                .build();
        }
    }

    /**
     * Perform account compliance screening during account creation or updates
     */
    public AccountComplianceResult performAccountComplianceCheck(Account account) {
        log.info("Performing compliance check for account: {}", account.getId());
        
        try {
            // Sanctions screening for account holder
            SanctionsScreeningResponse sanctionsResult = performSanctionsScreening(account);
            
            // Risk assessment for account
            RiskAssessmentResponse riskResult = performAccountRiskAssessment(account);

            return AccountComplianceResult.builder()
                .accountId(account.getId())
                .approved(!sanctionsResult.isHasMatches())
                .riskLevel(riskResult.getRiskLevel())
                .riskScore(riskResult.getRiskScore())
                .sanctions(sanctionsResult)
                .riskAssessment(riskResult)
                .requiresEnhancedDueDiligence(riskResult.isRequiresEnhancedDueDiligence())
                .checkedAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Error during account compliance check: {}", account.getId(), e);
            return AccountComplianceResult.builder()
                .accountId(account.getId())
                .approved(false)
                .riskLevel("HIGH")
                .requiresEnhancedDueDiligence(true)
                .checkedAt(LocalDateTime.now())
                .build();
        }
    }

    /**
     * Request regulatory report generation
     */
    public RegulatoryReportResponse requestRegulatoryReport(String reportType, 
                                                           LocalDateTime startDate, 
                                                           LocalDateTime endDate) {
        log.info("Requesting regulatory report: {} for period {} to {}", reportType, startDate, endDate);
        
        try {
            GenerateReportRequest request = GenerateReportRequest.builder()
                .reportType(reportType)
                .startDate(startDate)
                .endDate(endDate)
                .format("JSON")
                .build();

            ApiResponse<RegulatoryReportResponse> response = complianceServiceClient.generateRegulatoryReport(request);
            
            if (response.isSuccess()) {
                return response.getData();
            } else {
                log.error("Failed to generate regulatory report: {}", response.getMessage());
                throw new RuntimeException("Regulatory report generation failed: " + response.getMessage());
            }

        } catch (Exception e) {
            log.error("Error requesting regulatory report", e);
            throw new RuntimeException("Failed to request regulatory report", e);
        }
    }

    // Private helper methods

    private AMLScreeningResponse performAMLScreening(Transaction transaction, Account account) {
        try {
            AMLScreeningRequest request = AMLScreeningRequest.builder()
                .transactionId(transaction.getId())
                .customerId(account.getUserId())
                .customerName(account.getAccountHolderName())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .transactionType(transaction.getType().toString())
                .transactionDate(transaction.getCreatedAt())
                .build();

            ApiResponse<AMLScreeningResponse> response = complianceServiceClient.screenTransaction(request);
            
            if (response.isSuccess()) {
                return response.getData();
            } else {
                log.warn("AML screening failed: {}", response.getMessage());
                return createFallbackAMLResponse(transaction.getId());
            }

        } catch (Exception e) {
            log.error("Error during AML screening", e);
            return createFallbackAMLResponse(transaction.getId());
        }
    }

    private SanctionsScreeningResponse performSanctionsScreening(Account account) {
        try {
            SanctionsScreeningRequest request = SanctionsScreeningRequest.builder()
                .entityId(account.getId())
                .entityType("ACCOUNT_HOLDER")
                .fullName(account.getAccountHolderName())
                .build();

            ApiResponse<SanctionsScreeningResponse> response = complianceServiceClient.screenEntity(request);
            
            if (response.isSuccess()) {
                return response.getData();
            } else {
                log.warn("Sanctions screening failed: {}", response.getMessage());
                return createFallbackSanctionsResponse(account.getId());
            }

        } catch (Exception e) {
            log.error("Error during sanctions screening", e);
            return createFallbackSanctionsResponse(account.getId());
        }
    }

    private RiskAssessmentResponse performRiskAssessment(Transaction transaction, Account account) {
        try {
            List<String> additionalFactors = new ArrayList<>();
            
            // Add transaction-specific risk factors
            if (transaction.getAmount().compareTo(java.math.BigDecimal.valueOf(10000)) >= 0) {
                additionalFactors.add("LARGE_TRANSACTION");
            }
            
            if (account.getComplianceLevel() == Account.ComplianceLevel.MONITORED) {
                additionalFactors.add("MONITORED_ACCOUNT");
            }

            RiskAssessmentRequest request = RiskAssessmentRequest.builder()
                .entityId(transaction.getId())
                .entityType("TRANSACTION")
                .customerId(account.getUserId())
                .customerName(account.getAccountHolderName())
                .transactionAmount(transaction.getAmount())
                .transactionType(transaction.getType().toString())
                .additionalFactors(additionalFactors)
                .build();

            ApiResponse<RiskAssessmentResponse> response = complianceServiceClient.performRiskAssessment(request);
            
            if (response.isSuccess()) {
                return response.getData();
            } else {
                log.warn("Risk assessment failed: {}", response.getMessage());
                return createFallbackRiskResponse(transaction.getId());
            }

        } catch (Exception e) {
            log.error("Error during risk assessment", e);
            return createFallbackRiskResponse(transaction.getId());
        }
    }

    private RiskAssessmentResponse performAccountRiskAssessment(Account account) {
        try {
            List<String> additionalFactors = new ArrayList<>();
            
            if (account.getCurrentBalance().compareTo(java.math.BigDecimal.valueOf(100000)) >= 0) {
                additionalFactors.add("HIGH_BALANCE_ACCOUNT");
            }

            RiskAssessmentRequest request = RiskAssessmentRequest.builder()
                .entityId(account.getId())
                .entityType("ACCOUNT")
                .customerId(account.getUserId())
                .customerName(account.getAccountHolderName())
                .additionalFactors(additionalFactors)
                .build();

            ApiResponse<RiskAssessmentResponse> response = complianceServiceClient.performRiskAssessment(request);
            
            if (response.isSuccess()) {
                return response.getData();
            } else {
                return createFallbackRiskResponse(account.getId());
            }

        } catch (Exception e) {
            return createFallbackRiskResponse(account.getId());
        }
    }

    private ComplianceCheckResult aggregateComplianceResults(UUID transactionId,
                                                           AMLScreeningResponse amlResult,
                                                           SanctionsScreeningResponse sanctionsResult,
                                                           RiskAssessmentResponse riskResult) {
        
        List<String> alerts = new ArrayList<>();
        boolean approved = true;
        boolean requiresManualReview = false;
        String riskLevel = "LOW";

        // Process AML results
        if (!amlResult.isApproved()) {
            approved = false;
            alerts.add("AML screening failed");
        }
        if (amlResult.getAlerts() != null) {
            alerts.addAll(amlResult.getAlerts());
        }
        if (amlResult.isRequiresManualReview()) {
            requiresManualReview = true;
        }

        // Process sanctions results
        if (sanctionsResult.isHasMatches()) {
            approved = false;
            alerts.add("Sanctions list match detected");
            riskLevel = "HIGH";
            requiresManualReview = true;
        }

        // Process risk assessment
        if ("HIGH".equals(riskResult.getRiskLevel())) {
            riskLevel = "HIGH";
            requiresManualReview = true;
        } else if ("MEDIUM".equals(riskResult.getRiskLevel()) && !"HIGH".equals(riskLevel)) {
            riskLevel = "MEDIUM";
        }

        if (riskResult.isRequiresEnhancedDueDiligence()) {
            requiresManualReview = true;
            alerts.add("Enhanced due diligence required");
        }

        return ComplianceCheckResult.builder()
            .transactionId(transactionId)
            .approved(approved)
            .riskLevel(riskLevel)
            .riskScore(riskResult.getRiskScore())
            .alerts(alerts)
            .requiresManualReview(requiresManualReview)
            .amlResult(amlResult)
            .sanctionsResult(sanctionsResult)
            .riskResult(riskResult)
            .checkedAt(LocalDateTime.now())
            .build();
    }

    // Fallback response creators
    
    private AMLScreeningResponse createFallbackAMLResponse(UUID transactionId) {
        return AMLScreeningResponse.builder()
            .transactionId(transactionId)
            .approved(true)
            .status("FALLBACK_APPROVED")
            .riskScore(50)
            .riskLevel("MEDIUM")
            .requiresManualReview(true)
            .reviewReason("AML screening service unavailable")
            .screenedAt(LocalDateTime.now())
            .build();
    }

    private SanctionsScreeningResponse createFallbackSanctionsResponse(UUID entityId) {
        return SanctionsScreeningResponse.builder()
            .screeningId(UUID.randomUUID())
            .entityId(entityId)
            .hasMatches(false)
            .screeningStatus("FALLBACK_CLEAR")
            .screenedAt(LocalDateTime.now())
            .build();
    }

    private RiskAssessmentResponse createFallbackRiskResponse(UUID entityId) {
        return RiskAssessmentResponse.builder()
            .assessmentId(UUID.randomUUID())
            .entityId(entityId)
            .riskLevel("MEDIUM")
            .riskScore(50)
            .requiresEnhancedDueDiligence(false)
            .recommendation("Manual review recommended - compliance service unavailable")
            .assessedAt(LocalDateTime.now())
            .build();
    }

    // DTOs

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ComplianceCheckResult {
        private UUID transactionId;
        private boolean approved;
        private String riskLevel;
        private Integer riskScore;
        private List<String> alerts;
        private boolean requiresManualReview;
        private AMLScreeningResponse amlResult;
        private SanctionsScreeningResponse sanctionsResult;
        private RiskAssessmentResponse riskResult;
        private LocalDateTime checkedAt;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AccountComplianceResult {
        private UUID accountId;
        private boolean approved;
        private String riskLevel;
        private Integer riskScore;
        private SanctionsScreeningResponse sanctions;
        private RiskAssessmentResponse riskAssessment;
        private boolean requiresEnhancedDueDiligence;
        private LocalDateTime checkedAt;
    }
}