package com.waqiti.compliance.client;

import com.waqiti.common.resilience.RateLimited;
import com.waqiti.common.resilience.WithCircuitBreaker;
import com.waqiti.compliance.dto.CustomerDetails;
import com.waqiti.compliance.exception.ServiceIntegrationException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Feign client for customer service integration
 * Provides resilient communication with the customer service
 */
@FeignClient(
    name = "user-service",
    path = "/api/v1/users",
    fallback = CustomerServiceClient.CustomerServiceFallback.class
)
public interface CustomerServiceClient {
    
    /**
     * Get customer details by ID
     */
    @GetMapping("/{customerId}")
    @CircuitBreaker(name = "customer-service", fallbackMethod = "getCustomerDetailsFallback")
    @Retry(name = "customer-service")
    CustomerDetails getCustomerDetails(@PathVariable("customerId") UUID customerId);
    
    /**
     * Get customer KYC status
     */
    @GetMapping("/{customerId}/kyc-status")
    @CircuitBreaker(name = "customer-service")
    CustomerKycStatus getCustomerKycStatus(@PathVariable("customerId") UUID customerId);
    
    /**
     * Get customer risk profile
     */
    @GetMapping("/{customerId}/risk-profile")
    @Cacheable(value = "customer-risk-profiles", key = "#customerId")
    CustomerRiskProfile getCustomerRiskProfile(@PathVariable("customerId") UUID customerId);
    
    /**
     * Get customer account history
     */
    @GetMapping("/{customerId}/account-history")
    AccountHistory getAccountHistory(
        @PathVariable("customerId") UUID customerId,
        @RequestParam(value = "fromDate", required = false) LocalDate fromDate,
        @RequestParam(value = "toDate", required = false) LocalDate toDate
    );
    
    /**
     * Get customer compliance history
     */
    @GetMapping("/{customerId}/compliance-history")
    ComplianceHistory getComplianceHistory(@PathVariable("customerId") UUID customerId);
    
    /**
     * Get customer linked accounts
     */
    @GetMapping("/{customerId}/linked-accounts")
    List<LinkedAccount> getLinkedAccounts(@PathVariable("customerId") UUID customerId);
    
    /**
     * Get customer verification documents
     */
    @GetMapping("/{customerId}/verification-documents")
    List<VerificationDocument> getVerificationDocuments(@PathVariable("customerId") UUID customerId);
    
    /**
     * Check if customer is PEP (Politically Exposed Person)
     */
    @GetMapping("/{customerId}/pep-status")
    PepStatus checkPepStatus(@PathVariable("customerId") UUID customerId);
    
    /**
     * Get customer sanctions screening result
     */
    @GetMapping("/{customerId}/sanctions-status")
    SanctionsStatus getSanctionsStatus(@PathVariable("customerId") UUID customerId);
    
    /**
     * Fallback implementation for circuit breaker
     */
    @Component
    @Slf4j
    @RequiredArgsConstructor
    class CustomerServiceFallback implements CustomerServiceClient {
        
        @Override
        public CustomerDetails getCustomerDetails(UUID customerId) {
            log.warn("Fallback: Unable to fetch customer details for: {}", customerId);
            return CustomerDetails.builder()
                .customerId(customerId)
                .firstName("Unknown")
                .lastName("Unknown")
                .email("unknown@fallback.com")
                .kycStatus("PENDING_VERIFICATION")
                .riskLevel("HIGH") // Default to high risk for safety
                .accountStatus("RESTRICTED")
                .createdAt(LocalDateTime.now())
                .build();
        }
        
        @Override
        public CustomerKycStatus getCustomerKycStatus(UUID customerId) {
            log.warn("Fallback: Unable to fetch KYC status for: {}", customerId);
            return CustomerKycStatus.builder()
                .customerId(customerId)
                .status("PENDING")
                .requiresReview(true)
                .build();
        }
        
        @Override
        public CustomerRiskProfile getCustomerRiskProfile(UUID customerId) {
            log.warn("Fallback: Unable to fetch risk profile for: {}", customerId);
            return CustomerRiskProfile.builder()
                .customerId(customerId)
                .riskScore(100) // Maximum risk score for safety
                .riskLevel("CRITICAL")
                .requiresEnhancedDueDiligence(true)
                .lastAssessmentDate(LocalDateTime.now())
                .build();
        }
        
        @Override
        public AccountHistory getAccountHistory(UUID customerId, LocalDate fromDate, LocalDate toDate) {
            log.warn("Fallback: Unable to fetch account history for: {}", customerId);
            return AccountHistory.builder()
                .customerId(customerId)
                .accountAge(0)
                .totalTransactions(0)
                .averageMonthlyVolume(0.0)
                .build();
        }
        
        @Override
        public ComplianceHistory getComplianceHistory(UUID customerId) {
            log.warn("Fallback: Unable to fetch compliance history for: {}", customerId);
            return ComplianceHistory.builder()
                .customerId(customerId)
                .hasViolations(true) // Assume violations for safety
                .requiresReview(true)
                .build();
        }
        
        @Override
        public List<LinkedAccount> getLinkedAccounts(UUID customerId) {
            log.warn("Fallback: Unable to fetch linked accounts for: {}", customerId);
            return List.of(); // Return empty list in fallback
        }
        
        @Override
        public List<VerificationDocument> getVerificationDocuments(UUID customerId) {
            log.warn("Fallback: Unable to fetch verification documents for: {}", customerId);
            return List.of();
        }
        
        @Override
        public PepStatus checkPepStatus(UUID customerId) {
            log.warn("Fallback: Unable to check PEP status for: {}", customerId);
            return PepStatus.builder()
                .customerId(customerId)
                .isPep(true) // Assume PEP for safety
                .requiresEnhancedMonitoring(true)
                .build();
        }
        
        @Override
        public SanctionsStatus getSanctionsStatus(UUID customerId) {
            log.warn("Fallback: Unable to check sanctions status for: {}", customerId);
            return SanctionsStatus.builder()
                .customerId(customerId)
                .isOnSanctionsList(true) // Assume sanctioned for safety
                .requiresImmediateAction(true)
                .build();
        }
    }
    
    // DTO classes for responses
    
    @lombok.Data
    @lombok.Builder
    class CustomerKycStatus {
        private UUID customerId;
        private String status;
        private boolean requiresReview;
        private LocalDateTime lastVerificationDate;
        private String verificationLevel;
    }
    
    @lombok.Data
    @lombok.Builder
    class CustomerRiskProfile {
        private UUID customerId;
        private Integer riskScore;
        private String riskLevel;
        private boolean requiresEnhancedDueDiligence;
        private LocalDateTime lastAssessmentDate;
        private List<String> riskFactors;
    }
    
    @lombok.Data
    @lombok.Builder
    class AccountHistory {
        private UUID customerId;
        private Integer accountAge;
        private Long totalTransactions;
        private Double averageMonthlyVolume;
        private LocalDateTime firstTransactionDate;
        private LocalDateTime lastTransactionDate;
    }
    
    @lombok.Data
    @lombok.Builder
    class ComplianceHistory {
        private UUID customerId;
        private boolean hasViolations;
        private boolean requiresReview;
        private List<String> previousViolations;
        private LocalDateTime lastReviewDate;
    }
    
    @lombok.Data
    @lombok.Builder
    class LinkedAccount {
        private String accountId;
        private String accountType;
        private String institutionName;
        private String status;
        private LocalDateTime linkedDate;
    }
    
    @lombok.Data
    @lombok.Builder
    class VerificationDocument {
        private String documentId;
        private String documentType;
        private String status;
        private LocalDateTime uploadedAt;
        private LocalDateTime verifiedAt;
    }
    
    @lombok.Data
    @lombok.Builder
    class PepStatus {
        private UUID customerId;
        private boolean isPep;
        private String pepType;
        private String jurisdiction;
        private boolean requiresEnhancedMonitoring;
        private LocalDateTime lastCheckedDate;
    }
    
    @lombok.Data
    @lombok.Builder
    class SanctionsStatus {
        private UUID customerId;
        private boolean isOnSanctionsList;
        private List<String> matchedLists;
        private Double matchScore;
        private boolean requiresImmediateAction;
        private LocalDateTime lastScreeningDate;
    }
}