package com.waqiti.compliance.service;

import com.waqiti.compliance.dto.ComprehensiveScreeningRequest;
import com.waqiti.compliance.dto.ComprehensiveScreeningResult;
import com.waqiti.compliance.entity.ComplianceRecord;
import com.waqiti.compliance.repository.ComplianceRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Separate service for caching compliance screening results
 * This avoids self-invocation issues with @Cacheable annotations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceScreeningCacheService {

    private final ComplianceRecordRepository complianceRecordRepository;
    
    @Value("${compliance.screening.cache-duration-hours:24}")
    private int cacheDurationHours;

    /**
     * Check for recent screening results to avoid duplicate screenings
     */
    @Cacheable(value = "complianceScreening", key = "#request.firstName + '_' + #request.lastName + '_' + #request.dateOfBirth")
    public Optional<ComprehensiveScreeningResult> checkRecentScreening(ComprehensiveScreeningRequest request) {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(cacheDurationHours);
            
            List<ComplianceRecord> recentRecords = complianceRecordRepository
                    .findRecentScreeningsByCustomer(
                            request.getFirstName(),
                            request.getLastName(),
                            request.getDateOfBirth(),
                            cutoffTime);
            
            if (recentRecords.isEmpty()) {
                log.debug("No recent screening found for customer: {} {}", 
                         request.getFirstName(), request.getLastName());
                return Optional.empty();
            }
            
            // Get the most recent record
            ComplianceRecord mostRecent = recentRecords.get(0);
            
            // Convert to screening result
            ComprehensiveScreeningResult result = ComprehensiveScreeningResult.builder()
                    .screeningId(mostRecent.getScreeningId())
                    .clean(mostRecent.isOverallClean())
                    .compositeRiskScore(mostRecent.getRiskScore())
                    .riskLevel(mostRecent.getRiskLevel())
                    .screenedAt(mostRecent.getScreenedAt())
                    .screeningMethod("CACHED")
                    .notes("Retrieved from cache - screening performed at " + mostRecent.getScreenedAt())
                    .build();
            
            log.info("Found recent screening for customer: {} {} (Risk: {}, Score: {})",
                    request.getFirstName(), request.getLastName(), 
                    result.getRiskLevel(), result.getCompositeRiskScore());
            
            return Optional.of(result);
            
        } catch (Exception e) {
            log.error("Error checking recent screening for customer: {} {}", 
                     request.getFirstName(), request.getLastName(), e);
            return Optional.empty();
        }
    }

    /**
     * Cache OFAC screening results
     */
    @Cacheable(value = "ofacScreening", key = "#request.firstName + '_' + #request.lastName")
    public String cacheOFACResult(ComprehensiveScreeningRequest request, String result) {
        return result;
    }

    /**
     * Cache PEP screening results
     */
    @Cacheable(value = "pepScreening", key = "#request.firstName + '_' + #request.lastName + '_' + #request.dateOfBirth")
    public String cachePEPResult(ComprehensiveScreeningRequest request, String result) {
        return result;
    }

    /**
     * Cache sanctions screening results
     */
    @Cacheable(value = "sanctionsScreening", key = "#request.firstName + '_' + #request.lastName")
    public String cacheSanctionsResult(ComprehensiveScreeningRequest request, String result) {
        return result;
    }

    /**
     * Cache watch list screening results
     */
    @Cacheable(value = "watchListScreening", key = "#request.firstName + '_' + #request.lastName")
    public String cacheWatchListResult(ComprehensiveScreeningRequest request, String result) {
        return result;
    }
}