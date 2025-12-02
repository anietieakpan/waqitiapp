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
 * World-Check One sanctions provider
 */
@Component
@Slf4j
public class WorldCheckProvider implements SanctionsProvider {

    // SECURITY FIX: Use SecureRandom instead of Math.random()
    private static final SecureRandom secureRandom = new SecureRandom();

    @Override
    public ProviderScreeningResult screen(OFACScreeningRequest request) {
        log.debug("Screening entity against World-Check One: {}", request.getEntityName());
        
        // In production, this would call World-Check API
        // Simplified mock implementation
        
        List<OFACScreeningResult.SanctionMatch> matches = new ArrayList<>();

        // SECURITY FIX: Use SecureRandom instead of Math.random()
        boolean hasMatch = secureRandom.nextDouble() < 0.02; // 2% match rate
        double matchScore = hasMatch ? 0.90 + secureRandom.nextDouble() * 0.10 : secureRandom.nextDouble() * 0.20;
        
        if (hasMatch) {
            matches.add(OFACScreeningResult.SanctionMatch.builder()
                .listName("World-Check One")
                .entityName(request.getEntityName())
                .matchScore(matchScore)
                .matchType("EXACT_MATCH")
                .reason("Global Sanctions Database")
                .build());
        }
        
        return ProviderScreeningResult.builder()
            .providerName(getProviderName())
            .hasMatch(hasMatch)
            .matchScore(matchScore)
            .matches(matches)
            .screeningTime(LocalDateTime.now())
            // SECURITY FIX: Use SecureRandom instead of Math.random()
            .responseTimeMs(250 + (long)(secureRandom.nextDouble() * 250))
            .success(true)
            .build();
    }
    
    @Override
    public String getProviderName() {
        return "World-Check";
    }
    
    @Override
    public boolean isAvailable() {
        return true;
    }
    
    @Override
    public int getPriority() {
        return 4;
    }
}