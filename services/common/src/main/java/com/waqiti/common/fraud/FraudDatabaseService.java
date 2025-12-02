package com.waqiti.common.fraud;

import com.waqiti.common.fraud.model.RoutingNumberFraudStatus;
import com.waqiti.common.fraud.profiling.UserRiskProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing fraud database lookups
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDatabaseService {
    
    // In-memory stores for demo (would be database in production)
    private final Set<String> knownFraudulentRoutingNumbers = ConcurrentHashMap.newKeySet();
    private final Set<String> knownFraudulentAccounts = ConcurrentHashMap.newKeySet();
    private final Map<String, RoutingNumberFraudStatus> routingNumberCache = new ConcurrentHashMap<>();
    
    /**
     * Check if routing number is fraudulent
     */
    public RoutingNumberFraudStatus checkRoutingNumber(String routingNumber) {
        log.debug("Checking routing number: {}", routingNumber);
        
        if (routingNumberCache.containsKey(routingNumber)) {
            return routingNumberCache.get(routingNumber);
        }
        
        // In production, query fraud database
        boolean isFraudulent = knownFraudulentRoutingNumbers.contains(routingNumber);
        
        RoutingNumberFraudStatus.FraudStatus status = isFraudulent ? 
            RoutingNumberFraudStatus.FraudStatus.CONFIRMED_FRAUD : 
            RoutingNumberFraudStatus.FraudStatus.CLEAN;
        
        RoutingNumberFraudStatus result = RoutingNumberFraudStatus.builder()
            .routingNumber(routingNumber)
            .status(status)
            .riskScore(isFraudulent ? 0.95 : 0.05)
            .bankName(getBankName(routingNumber))
            .bankLocation("Unknown")
            .lastAssessment(LocalDateTime.now())
            .blacklistReason(isFraudulent ? "Known fraudulent routing number" : null)
            .isActive(true)
            .build();
        
        routingNumberCache.put(routingNumber, result);
        return result;
    }
    
    /**
     * Check if account is fraudulent
     */
    public boolean isKnownFraudulentAccount(String accountNumber) {
        return knownFraudulentAccounts.contains(accountNumber);
    }
    
    /**
     * Add account to fraud list
     */
    public void reportFraudulentAccount(String accountNumber) {
        log.warn("Adding account to fraud list: {}", accountNumber);
        knownFraudulentAccounts.add(accountNumber);
    }
    
    /**
     * Add routing number to fraud list
     */
    public void reportFraudulentRoutingNumber(String routingNumber) {
        log.warn("Adding routing number to fraud list: {}", routingNumber);
        knownFraudulentRoutingNumbers.add(routingNumber);
        routingNumberCache.remove(routingNumber);
    }
    
    private String getBankName(String routingNumber) {
        // In production, lookup actual bank name
        return "Bank of " + routingNumber.substring(0, 3);
    }
    
    /**
     * Get user risk profile
     */
    public UserRiskProfile getUserRiskProfile(String userId) {
        // In production, query from database
        return UserRiskProfile.builder()
            .userId(userId)
            .riskScore(0.3)
            .riskLevel(UserRiskProfile.RiskLevel.MEDIUM)
            .build();
    }
    
    /**
     * Check external fraud providers
     */
    public Map<String, Object> checkExternalProviders(String routingNumber) {
        // In production, query external fraud databases
        return Map.of(
            "provider", "internal",
            "result", "clean",
            "confidence", 0.9
        );
    }
}