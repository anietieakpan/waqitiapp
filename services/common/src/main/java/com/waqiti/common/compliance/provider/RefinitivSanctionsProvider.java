package com.waqiti.common.compliance.provider;

import com.waqiti.common.compliance.model.OFACScreeningRequest;
import com.waqiti.common.compliance.model.OFACScreeningResult;
import com.waqiti.common.compliance.model.ProviderScreeningResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Refinitiv World-Check sanctions provider
 */
@Component
@Slf4j
public class RefinitivSanctionsProvider implements SanctionsProvider {

    // SECURITY FIX: Use SecureRandom instead of Math.random()
    private static final SecureRandom secureRandom = new SecureRandom();

    @Override
    public ProviderScreeningResult screen(OFACScreeningRequest request) {
        log.debug("Screening entity against Refinitiv World-Check: {}", request.getEntityName());
        
        // In production, this would call Refinitiv API
        // Simplified mock implementation
        
        List<OFACScreeningResult.SanctionMatch> matches = new ArrayList<>();

        // SECURITY FIX: Use SecureRandom instead of Math.random()
        boolean hasMatch = secureRandom.nextDouble() < 0.04; // 4% match rate
        double matchScore = hasMatch ? 0.80 + secureRandom.nextDouble() * 0.20 : secureRandom.nextDouble() * 0.35;
        
        if (hasMatch) {
            matches.add(OFACScreeningResult.SanctionMatch.builder()
                .listName("Refinitiv World-Check")
                .entityName(request.getEntityName())
                .matchScore(matchScore)
                .matchType("ENHANCED_MATCH")
                .reason("International Sanctions")
                .build());
        }
        
        return ProviderScreeningResult.builder()
            .providerName(getProviderName())
            .hasMatch(hasMatch)
            .matchScore(matchScore)
            .matches(matches)
            .screeningTime(LocalDateTime.now())
            // SECURITY FIX: Use SecureRandom instead of Math.random()
            .responseTimeMs(200 + (long)(secureRandom.nextDouble() * 300))
            .success(true)
            .build();
    }
    
    @Override
    public String getProviderName() {
        return "Refinitiv";
    }
    
    @Override
    public boolean isAvailable() {
        return true;
    }
    
    @Override
    public int getPriority() {
        return 3;
    }
}