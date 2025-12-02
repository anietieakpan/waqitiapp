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
 * Dow Jones Watchlist sanctions provider
 */
@Component
@Slf4j
public class DowJonesSanctionsProvider implements SanctionsProvider {

    // SECURITY FIX: Use SecureRandom instead of Math.random()
    private static final SecureRandom secureRandom = new SecureRandom();

    @Override
    public ProviderScreeningResult screen(OFACScreeningRequest request) {
        log.debug("Screening entity against Dow Jones Watchlist: {}", request.getEntityName());
        
        // In production, this would call Dow Jones API
        // Simplified mock implementation
        
        List<OFACScreeningResult.SanctionMatch> matches = new ArrayList<>();

        // SECURITY FIX: Use SecureRandom instead of Math.random()
        boolean hasMatch = secureRandom.nextDouble() < 0.03; // 3% match rate
        double matchScore = hasMatch ? 0.75 + secureRandom.nextDouble() * 0.25 : secureRandom.nextDouble() * 0.25;
        
        if (hasMatch) {
            matches.add(OFACScreeningResult.SanctionMatch.builder()
                .listName("Dow Jones Watchlist")
                .entityName(request.getEntityName())
                .matchScore(matchScore)
                .matchType("FUZZY_MATCH")
                .reason("PEP - Politically Exposed Person")
                .build());
        }
        
        return ProviderScreeningResult.builder()
            .providerName(getProviderName())
            .hasMatch(hasMatch)
            .matchScore(matchScore)
            .matches(matches)
            .screeningTime(LocalDateTime.now())
            // SECURITY FIX: Use SecureRandom instead of Math.random()
            .responseTimeMs(150 + (long)(secureRandom.nextDouble() * 350))
            .success(true)
            .build();
    }
    
    @Override
    public String getProviderName() {
        return "Dow Jones";
    }
    
    @Override
    public boolean isAvailable() {
        return true;
    }
    
    @Override
    public int getPriority() {
        return 2;
    }
}