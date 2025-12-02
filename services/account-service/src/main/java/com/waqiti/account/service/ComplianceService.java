package com.waqiti.account.service;

import com.waqiti.account.client.ComplianceServiceClient;
import com.waqiti.common.exception.ServiceException;
//import com.waqiti.common.resilience.CircuitBreakerService;
import com.waqiti.common.service.CircuitBreakerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Compliance Service Adapter for Account Service
 * 
 * Acts as an adapter/facade to communicate with the compliance-service microservice
 * via Feign client, providing local caching and fallback mechanisms.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceService {
    
    @Lazy
    private final ComplianceService self;
    
    private final ComplianceServiceClient complianceServiceClient;
    private final CircuitBreakerService circuitBreaker;
    
    private static final String COMPLIANCE_CACHE = "compliance_checks";
    
    /**
     * Perform compliance check for user and account type
     */
    @Cacheable(value = COMPLIANCE_CACHE, key = "#userId + '_' + #accountType")
    public ComplianceCheckResult performComplianceCheck(UUID userId, String accountType, BigDecimal amount) {
        log.info("Performing compliance check for user: {} with account type: {}", userId, accountType);
        
        return circuitBreaker.executeWithFallback(
            "compliance-service",
            () -> {
                // Call compliance service via Feign client
                var response = complianceServiceClient.performComplianceCheck(
                    userId.toString(), 
                    accountType, 
                    amount
                );
                
                return new ComplianceCheckResult(
                    response.isPassed(),
                    response.getReason(),
                    response.isRequiresAdditionalVerification()
                );
            },
            () -> {
                // Fallback: Allow with warning for amounts under threshold
                log.warn("Compliance service unavailable, using fallback for user: {}", userId);
                
                if (amount != null && amount.compareTo(new BigDecimal("1000")) < 0) {
                    return new ComplianceCheckResult(
                        true,
                        "Fallback: Allowed for low-risk amount",
                        false
                    );
                } else {
                    return new ComplianceCheckResult(
                        false,
                        "Compliance service unavailable - manual review required",
                        true
                    );
                }
            }
        );
    }
    
    /**
     * Perform basic compliance check
     */
    public ComplianceCheckResult performBasicCheck(UUID userId) {
        return self.performComplianceCheck(userId, "BASIC", BigDecimal.ZERO);
    }
    
    /**
     * Perform full compliance check including sanctions
     */
    public ComplianceCheckResult performFullCheck(UUID userId) {
        try {
            var sanctionsResult = complianceServiceClient.checkSanctions(userId.toString());
            var amlResult = complianceServiceClient.checkAML(userId.toString());
            
            boolean passed = sanctionsResult.isPassed() && amlResult.isPassed();
            String reason = !passed ? 
                (sanctionsResult.isPassed() ? amlResult.getReason() : sanctionsResult.getReason()) : 
                "All checks passed";
            
            return new ComplianceCheckResult(passed, reason, false);
            
        } catch (Exception e) {
            log.error("Full compliance check failed for user: {}", userId, e);
            throw new ServiceException("Compliance check unavailable", e);
        }
    }
    
    /**
     * Screen for PEP (Politically Exposed Person)
     */
    public boolean screenForPEP(UUID userId) {
        try {
            var result = complianceServiceClient.screenPEP(userId.toString());
            return !result.isPep();
        } catch (Exception e) {
            log.error("PEP screening failed for user: {}", userId, e);
            return false; // Fail safe - assume PEP if screening fails
        }
    }
    
    /**
     * Get compliance status for user
     */
    public ComplianceStatus getComplianceStatus(UUID userId) {
        try {
            var status = complianceServiceClient.getComplianceStatus(userId.toString());
            return ComplianceStatus.valueOf(status.getStatus());
        } catch (Exception e) {
            log.error("Failed to get compliance status for user: {}", userId, e);
            return ComplianceStatus.UNKNOWN;
        }
    }
}

