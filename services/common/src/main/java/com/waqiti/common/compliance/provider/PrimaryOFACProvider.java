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
 * Primary OFAC sanctions list provider
 */
@Component
@Slf4j
public class PrimaryOFACProvider implements SanctionsProvider {

    // SECURITY FIX: Use SecureRandom instead of Math.random()
    private static final SecureRandom secureRandom = new SecureRandom();

    @Override
    public ProviderScreeningResult screen(OFACScreeningRequest request) {
        log.debug("Screening entity against primary OFAC list: {}", request.getEntityName());
        
        // In production, this would call actual OFAC API
        // Simplified mock implementation
        
        List<OFACScreeningResult.SanctionMatch> matches = new ArrayList<>();

        // Simulate screening logic
        // SECURITY FIX: Use SecureRandom instead of Math.random()
        boolean hasMatch = secureRandom.nextDouble() < 0.05; // 5% match rate for testing
        double matchScore = hasMatch ? 0.85 + secureRandom.nextDouble() * 0.15 : secureRandom.nextDouble() * 0.3;
        
        if (hasMatch) {
            matches.add(OFACScreeningResult.SanctionMatch.builder()
                .listName("OFAC SDN List")
                .entityName(request.getEntityName())
                .matchScore(matchScore)
                .matchType("NAME_MATCH")
                .reason("Specially Designated National")
                .build());
        }
        
        return ProviderScreeningResult.builder()
            .providerName(getProviderName())
            .hasMatch(hasMatch)
            .matchScore(matchScore)
            .matches(matches)
            .screeningTime(LocalDateTime.now())
            // SECURITY FIX: Use SecureRandom instead of Math.random()
            .responseTimeMs(100 + (long)(secureRandom.nextDouble() * 400))
            .success(true)
            .build();
    }
    
    @Override
    public String getProviderName() {
        return "OFAC Primary";
    }
    
    @Override
    public boolean isAvailable() {
        return true;
    }
    
    @Override
    public int getPriority() {
        return 1;
    }
}