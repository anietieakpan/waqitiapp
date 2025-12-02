package com.waqiti.common.validation.service;

import com.waqiti.common.validation.model.ValidationModels.DomainReputationResult;
import com.waqiti.common.validation.model.ValidationModels.DomainAge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Domain Reputation Service
 * Public service wrapper for domain reputation assessment
 */
@Service
@Slf4j
public class DomainReputationService {
    
    // Trusted domains
    private final Map<String, Double> domainScores = new HashMap<>();
    
    public DomainReputationService() {
        initializeDomainScores();
    }
    
    private void initializeDomainScores() {
        // Initialize with some well-known domains
        domainScores.put("gmail.com", 0.95);
        domainScores.put("yahoo.com", 0.93);
        domainScores.put("outlook.com", 0.94);
        domainScores.put("hotmail.com", 0.92);
        domainScores.put("icloud.com", 0.94);
        domainScores.put("aol.com", 0.85);
        domainScores.put("protonmail.com", 0.88);
    }
    
    /**
     * Check domain reputation
     */
    public DomainReputationResult checkDomainReputation(String domain) {
        log.debug("Checking reputation for domain: {}", domain);
        
        String normalized = domain.toLowerCase().trim();
        double score = domainScores.getOrDefault(normalized, 0.5); // Default neutral score
        
        // Determine if it's a free email provider
        List<String> freeProviders = Arrays.asList(
            "gmail.com", "yahoo.com", "outlook.com", "hotmail.com", 
            "aol.com", "mail.com", "yandex.com"
        );
        boolean isFreeProvider = freeProviders.contains(normalized);
        
        // Corporate domains typically have better reputation
        boolean isCorporate = !isFreeProvider && !normalized.contains("mail");
        if (isCorporate) {
            score = Math.min(1.0, score + 0.1);
        }
        
        // Check if it's a subdomain (potential risk)
        boolean isSubdomain = normalized.split("\\.").length > 2;
        if (isSubdomain) {
            score = Math.max(0.0, score - 0.2);
        }
        
        DomainAge age = DomainAge.builder()
            .ageInDays(365) // Placeholder - would need WHOIS lookup
            .creationDate(LocalDateTime.now().minusDays(365))
            .registrationDate(LocalDateTime.now().minusDays(365))
            .expirationDate(LocalDateTime.now().plusDays(365))
            .isNewDomain(false)
            .isRecentlyRegistered(false)
            .build();
        
        // Convert double score to Integer (0-100 scale)
        Integer reputationScore = (int) Math.round(score * 100);
        
        String reputationLevel;
        if (score >= 0.9) {
            reputationLevel = "EXCELLENT";
        } else if (score >= 0.8) {
            reputationLevel = "GOOD";
        } else if (score >= 0.6) {
            reputationLevel = "NEUTRAL";
        } else if (score >= 0.4) {
            reputationLevel = "POOR";
        } else {
            reputationLevel = "DANGEROUS";
        }
        
        return DomainReputationResult.builder()
            .domain(normalized)
            .reputationScore(reputationScore)
            .reputationLevel(reputationLevel)
            .isTrusted(score >= 0.8)
            .isSuspicious(score < 0.4)
            .isMalicious(score < 0.2)
            .domainAge(age)
            .securityIndicators(Arrays.asList())
            .associatedThreats(Arrays.asList())
            .assessedAt(LocalDateTime.now())
            .reputationSources(new HashMap<>())
            .build();
    }
    
    /**
     * Check if domain is trusted
     */
    public boolean isTrusted(String domain) {
        DomainReputationResult result = checkDomainReputation(domain);
        return result.isTrusted();
    }
    
    /**
     * Check if domain is suspicious
     */
    public boolean isSuspicious(String domain) {
        DomainReputationResult result = checkDomainReputation(domain);
        return result.isSuspicious();
    }
}