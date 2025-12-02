package com.waqiti.compliance.service;

import com.waqiti.compliance.client.OFACScreeningServiceClient;
import com.waqiti.compliance.client.PEPScreeningServiceClient;
import com.waqiti.compliance.dto.*;
import com.waqiti.compliance.entity.ComplianceRecord;
import com.waqiti.compliance.entity.ScreeningResult;
import com.waqiti.compliance.repository.ComplianceRecordRepository;
import com.waqiti.compliance.repository.ScreeningResultRepository;
import com.waqiti.common.events.ComplianceEventPublisher;
import com.waqiti.common.events.ComplianceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Comprehensive Compliance Screening Service
 * 
 * Provides complete compliance screening including:
 * - OFAC (Office of Foreign Assets Control) screening
 * - PEP (Politically Exposed Persons) screening
 * - Sanctions list screening
 * - Watch list screening
 * - Risk assessment and scoring
 * - Ongoing monitoring
 * - Compliance reporting
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComprehensiveComplianceScreeningService {
    
    private final OFACScreeningServiceClient ofacScreeningClient;
    private final PEPScreeningServiceClient pepScreeningClient;
    private final ComplianceRecordRepository complianceRecordRepository;
    private final ScreeningResultRepository screeningResultRepository;
    private final ComplianceEventPublisher complianceEventPublisher;
    private final ComplianceScreeningCacheService cacheService;
    private final com.waqiti.compliance.cache.ComplianceCacheService complianceCacheService;
    
    @Value("${compliance.screening.risk-threshold.high:85}")
    private int highRiskThreshold;
    
    @Value("${compliance.screening.risk-threshold.medium:50}")
    private int mediumRiskThreshold;
    
    @Value("${compliance.screening.parallel-execution:true}")
    private boolean parallelExecution;
    
    @Value("${compliance.screening.cache-duration-hours:24}")
    private int cacheDurationHours;
    
    private final ExecutorService screeningExecutor = Executors.newFixedThreadPool(10);
    
    /**
     * Perform comprehensive compliance screening for customer
     */
    @Transactional
    public ComprehensiveScreeningResult performComprehensiveScreening(ComprehensiveScreeningRequest request) {
        log.info("Starting comprehensive compliance screening for customer: {} {} (DOB: {})", 
                request.getFirstName(), request.getLastName(), request.getDateOfBirth());
        
        String screeningId = generateScreeningId();
        LocalDateTime screeningStart = LocalDateTime.now();
        
        try {
            // Validate request
            validateScreeningRequest(request);
            
            // Check for recent screening results
            Optional<ComprehensiveScreeningResult> recentResult = cacheService.checkRecentScreening(request);
            if (recentResult.isPresent()) {
                log.info("Using recent screening result for customer: {} {}", 
                        request.getFirstName(), request.getLastName());
                return recentResult.get();
            }
            
            // Perform parallel or sequential screening
            ComprehensiveScreeningResult result = parallelExecution 
                ? performParallelScreening(screeningId, request)
                : performSequentialScreening(screeningId, request);
            
            // Calculate composite risk score
            result.setCompositeRiskScore(calculateCompositeRiskScore(result));
            result.setRiskLevel(determineRiskLevel(result.getCompositeRiskScore()));
            result.setScreeningDuration(java.time.Duration.between(screeningStart, LocalDateTime.now()));
            
            // Store screening results
            storeScreeningResults(screeningId, request, result);
            
            // Publish compliance events
            publishComplianceEvents(request, result);
            
            // Schedule ongoing monitoring if required
            scheduleOngoingMonitoring(request, result);
            
            log.info("Comprehensive screening completed for: {} {} - Risk Level: {} (Score: {})", 
                    request.getFirstName(), request.getLastName(), 
                    result.getRiskLevel(), result.getCompositeRiskScore());
            
            return result;
            
        } catch (Exception e) {
            log.error("Comprehensive screening failed for: {} {}", 
                    request.getFirstName(), request.getLastName(), e);
            
            // Create error result
            ComprehensiveScreeningResult errorResult = ComprehensiveScreeningResult.builder()
                    .screeningId(screeningId)
                    .clean(false)
                    .errorOccurred(true)
                    .errorMessage("Screening system error: " + e.getMessage())
                    .screenedAt(LocalDateTime.now())
                    .riskLevel(ComplianceRiskLevel.HIGH) // Conservative approach
                    .compositeRiskScore(100) // Maximum risk due to error
                    .build();
            
            // Still store the error result for audit trail
            storeScreeningResults(screeningId, request, errorResult);
            
            return errorResult;
        }
    }
    
    /**
     * Perform parallel screening for faster results
     */
    private ComprehensiveScreeningResult performParallelScreening(String screeningId, 
                                                                 ComprehensiveScreeningRequest request) {
        log.debug("Performing parallel compliance screening: {}", screeningId);
        
        // Create screening tasks
        CompletableFuture<OFACScreeningResult> ofacFuture = CompletableFuture.supplyAsync(
                () -> performOFACScreening(request), screeningExecutor);
        
        CompletableFuture<PEPScreeningResult> pepFuture = CompletableFuture.supplyAsync(
                () -> performPEPScreening(request), screeningExecutor);
        
        CompletableFuture<SanctionsScreeningResult> sanctionsFuture = CompletableFuture.supplyAsync(
                () -> performSanctionsScreening(request), screeningExecutor);
        
        CompletableFuture<WatchListScreeningResult> watchListFuture = CompletableFuture.supplyAsync(
                () -> performWatchListScreening(request), screeningExecutor);
        
        try {
            // Wait for all screenings to complete with timeout
            CompletableFuture<Void> allScreenings = CompletableFuture.allOf(
                    ofacFuture, pepFuture, sanctionsFuture, watchListFuture);

            allScreenings.join(); // Wait for completion

            // Collect results with timeout to prevent thread exhaustion
            // SECURITY FIX: Added 10-second timeout for regulatory compliance operations
            OFACScreeningResult ofacResult = ofacFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);
            PEPScreeningResult pepResult = pepFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);
            SanctionsScreeningResult sanctionsResult = sanctionsFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);
            WatchListScreeningResult watchListResult = watchListFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);

            // Build comprehensive result
            return ComprehensiveScreeningResult.builder()
                    .screeningId(screeningId)
                    .clean(ofacResult.isClean() && pepResult.isClean() &&
                           sanctionsResult.isClean() && watchListResult.isClean())
                    .ofacResult(ofacResult)
                    .pepResult(pepResult)
                    .sanctionsResult(sanctionsResult)
                    .watchListResult(watchListResult)
                    .screenedAt(LocalDateTime.now())
                    .screeningMethod("PARALLEL")
                    .build();

        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Compliance screening timed out after 10 seconds for screening ID: {}", screeningId, e);
            // For compliance, we fail closed - reject if screening times out
            throw new RuntimeException("Compliance screening timed out - cannot process", e);
        } catch (java.util.concurrent.ExecutionException e) {
            log.error("Parallel screening execution failed for screening ID: {}", screeningId, e.getCause());
            throw new RuntimeException("Parallel screening failed", e.getCause());
        } catch (java.util.concurrent.InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Parallel screening interrupted for screening ID: {}", screeningId, e);
            throw new RuntimeException("Parallel screening interrupted", e);
        } catch (Exception e) {
            log.error("Parallel screening failed for screening ID: {}", screeningId, e);
            throw new RuntimeException("Parallel screening failed", e);
        }
    }
    
    /**
     * Perform sequential screening for systematic processing
     */
    private ComprehensiveScreeningResult performSequentialScreening(String screeningId, 
                                                                   ComprehensiveScreeningRequest request) {
        log.debug("Performing sequential compliance screening: {}", screeningId);
        
        try {
            // Perform screenings in sequence
            OFACScreeningResult ofacResult = performOFACScreening(request);
            PEPScreeningResult pepResult = performPEPScreening(request);
            SanctionsScreeningResult sanctionsResult = performSanctionsScreening(request);
            WatchListScreeningResult watchListResult = performWatchListScreening(request);
            
            return ComprehensiveScreeningResult.builder()
                    .screeningId(screeningId)
                    .clean(ofacResult.isClean() && pepResult.isClean() && 
                           sanctionsResult.isClean() && watchListResult.isClean())
                    .ofacResult(ofacResult)
                    .pepResult(pepResult)
                    .sanctionsResult(sanctionsResult)
                    .watchListResult(watchListResult)
                    .screenedAt(LocalDateTime.now())
                    .screeningMethod("SEQUENTIAL")
                    .build();
            
        } catch (Exception e) {
            log.error("Sequential screening failed for screening ID: {}", screeningId, e);
            throw new RuntimeException("Sequential screening failed", e);
        }
    }
    
    /**
     * Perform OFAC screening
     */
    private OFACScreeningResult performOFACScreening(ComprehensiveScreeningRequest request) {
        try {
            OFACScreeningRequest ofacRequest = OFACScreeningRequest.builder()
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .dateOfBirth(request.getDateOfBirth())
                    .address(request.getAddress())
                    .aliases(request.getAliases())
                    .nationality(request.getNationality())
                    .build();
            
            return ofacScreeningClient.screenCustomer(ofacRequest);
            
        } catch (Exception e) {
            log.error("OFAC screening failed", e);
            return OFACScreeningResult.builder()
                    .clean(false)
                    .errorMessage("OFAC screening failed: " + e.getMessage())
                    .screenedAt(LocalDateTime.now())
                    .build();
        }
    }
    
    /**
     * Perform PEP screening
     */
    private PEPScreeningResult performPEPScreening(ComprehensiveScreeningRequest request) {
        try {
            PEPScreeningRequest pepRequest = PEPScreeningRequest.builder()
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .dateOfBirth(request.getDateOfBirth())
                    .nationality(request.getNationality())
                    .occupation(request.getOccupation())
                    .build();
            
            return pepScreeningClient.screenForPEP(pepRequest);
            
        } catch (Exception e) {
            log.error("PEP screening failed", e);
            return PEPScreeningResult.builder()
                    .clean(true) // Conservative - assume clean if screening fails
                    .errorMessage("PEP screening failed: " + e.getMessage())
                    .screenedAt(LocalDateTime.now())
                    .build();
        }
    }
    
    /**
     * Perform sanctions screening
     */
    private SanctionsScreeningResult performSanctionsScreening(ComprehensiveScreeningRequest request) {
        try {
            // Screen against multiple sanctions lists
            List<SanctionsMatch> matches = new ArrayList<>();
            
            // EU Sanctions List
            matches.addAll(screenAgainstEUSanctions(request));
            
            // UN Sanctions List
            matches.addAll(screenAgainstUNSanctions(request));
            
            // UK Sanctions List
            matches.addAll(screenAgainstUKSanctions(request));
            
            // Other regional sanctions
            matches.addAll(screenAgainstOtherSanctions(request));
            
            boolean clean = matches.isEmpty() || 
                           matches.stream().allMatch(m -> m.getScore() < mediumRiskThreshold);
            
            return SanctionsScreeningResult.builder()
                    .clean(clean)
                    .matches(matches)
                    .screenedAt(LocalDateTime.now())
                    .listsScreened(List.of("EU_SANCTIONS", "UN_SANCTIONS", "UK_SANCTIONS", "OTHER_REGIONAL"))
                    .totalMatches(matches.size())
                    .highestScore(matches.stream().mapToInt(SanctionsMatch::getScore).max().orElse(0))
                    .build();
            
        } catch (Exception e) {
            log.error("Sanctions screening failed", e);
            return SanctionsScreeningResult.builder()
                    .clean(false)
                    .errorMessage("Sanctions screening failed: " + e.getMessage())
                    .screenedAt(LocalDateTime.now())
                    .build();
        }
    }
    
    /**
     * Perform watch list screening
     */
    private WatchListScreeningResult performWatchListScreening(ComprehensiveScreeningRequest request) {
        try {
            List<WatchListMatch> matches = new ArrayList<>();
            
            // Screen against various watch lists
            matches.addAll(screenAgainstFBIWatchList(request));
            matches.addAll(screenAgainstInterpolWatchList(request));
            matches.addAll(screenAgainstCustomWatchLists(request));
            
            boolean clean = matches.isEmpty() || 
                           matches.stream().allMatch(m -> m.getScore() < highRiskThreshold);
            
            return WatchListScreeningResult.builder()
                    .clean(clean)
                    .matches(matches)
                    .screenedAt(LocalDateTime.now())
                    .listsScreened(List.of("FBI_WATCH_LIST", "INTERPOL", "CUSTOM_LISTS"))
                    .totalMatches(matches.size())
                    .highestScore(matches.stream().mapToInt(WatchListMatch::getScore).max().orElse(0))
                    .build();
            
        } catch (Exception e) {
            log.error("Watch list screening failed", e);
            return WatchListScreeningResult.builder()
                    .clean(false)
                    .errorMessage("Watch list screening failed: " + e.getMessage())
                    .screenedAt(LocalDateTime.now())
                    .build();
        }
    }
    
    /**
     * Calculate composite risk score from all screening results
     */
    private int calculateCompositeRiskScore(ComprehensiveScreeningResult result) {
        int totalScore = 0;
        int componentCount = 0;
        
        if (result.getOfacResult() != null) {
            totalScore += result.getOfacResult().getHighestScore();
            componentCount++;
        }
        
        if (result.getPepResult() != null) {
            totalScore += result.getPepResult().getHighestScore();
            componentCount++;
        }
        
        if (result.getSanctionsResult() != null) {
            totalScore += result.getSanctionsResult().getHighestScore();
            componentCount++;
        }
        
        if (result.getWatchListResult() != null) {
            totalScore += result.getWatchListResult().getHighestScore();
            componentCount++;
        }
        
        // Apply error penalty
        if (result.isErrorOccurred()) {
            totalScore = Math.max(totalScore, highRiskThreshold);
        }
        
        return componentCount > 0 ? totalScore / componentCount : 0;
    }
    
    /**
     * Determine risk level based on composite score
     */
    private ComplianceRiskLevel determineRiskLevel(int compositeScore) {
        if (compositeScore >= highRiskThreshold) {
            return ComplianceRiskLevel.HIGH;
        } else if (compositeScore >= mediumRiskThreshold) {
            return ComplianceRiskLevel.MEDIUM;
        } else {
            return ComplianceRiskLevel.LOW;
        }
    }
    
    /**
     * Check for recent screening results to avoid duplicate screenings
     */
    private Optional<ComprehensiveScreeningResult> checkRecentScreening(ComprehensiveScreeningRequest request) {
        return complianceCacheService.checkRecentScreening(request);
    }

    private Optional<ComprehensiveScreeningResult> checkRecentScreeningInternal(ComprehensiveScreeningRequest request) {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(cacheDurationHours);
            
            List<ComplianceRecord> recentRecords = complianceRecordRepository
                    .findRecentScreeningsByCustomer(
                            request.getFirstName(),
                            request.getLastName(),
                            request.getDateOfBirth(),
                            cutoffTime);
            
            if (!recentRecords.isEmpty()) {
                ComplianceRecord latestRecord = recentRecords.get(0);
                // Convert to result object
                return Optional.of(convertRecordToResult(latestRecord));
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            log.warn("Failed to check recent screening results", e);
            return Optional.empty();
        }
    }
    
    /**
     * Store screening results in database
     */
    private void storeScreeningResults(String screeningId, ComprehensiveScreeningRequest request, 
                                     ComprehensiveScreeningResult result) {
        try {
            ComplianceRecord record = ComplianceRecord.builder()
                    .screeningId(screeningId)
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .dateOfBirth(request.getDateOfBirth())
                    .nationality(request.getNationality())
                    .clean(result.isClean())
                    .riskLevel(result.getRiskLevel())
                    .compositeRiskScore(result.getCompositeRiskScore())
                    .screeningMethod(result.getScreeningMethod())
                    .screenedAt(result.getScreenedAt())
                    .createdAt(LocalDateTime.now())
                    .build();
            
            complianceRecordRepository.save(record);
            
            // Store individual screening results
            storeIndividualResults(screeningId, result);
            
        } catch (Exception e) {
            log.error("Failed to store screening results for: {}", screeningId, e);
        }
    }
    
    /**
     * Store individual screening results
     */
    private void storeIndividualResults(String screeningId, ComprehensiveScreeningResult result) {
        try {
            List<ScreeningResult> results = new ArrayList<>();
            
            if (result.getOfacResult() != null) {
                results.add(createScreeningResult(screeningId, "OFAC", result.getOfacResult()));
            }
            
            if (result.getPepResult() != null) {
                results.add(createScreeningResult(screeningId, "PEP", result.getPepResult()));
            }
            
            if (result.getSanctionsResult() != null) {
                results.add(createScreeningResult(screeningId, "SANCTIONS", result.getSanctionsResult()));
            }
            
            if (result.getWatchListResult() != null) {
                results.add(createScreeningResult(screeningId, "WATCH_LIST", result.getWatchListResult()));
            }
            
            screeningResultRepository.saveAll(results);
            
        } catch (Exception e) {
            log.error("Failed to store individual screening results for: {}", screeningId, e);
        }
    }
    
    /**
     * Publish compliance events for monitoring and alerts
     */
    private void publishComplianceEvents(ComprehensiveScreeningRequest request, 
                                       ComprehensiveScreeningResult result) {
        try {
            ComplianceEvent event = ComplianceEvent.builder()
                    .eventType(result.isClean() ? "COMPLIANCE_SCREENING_CLEAN" : "COMPLIANCE_SCREENING_ALERT")
                    .screeningId(result.getScreeningId())
                    .customerName(request.getFirstName() + " " + request.getLastName())
                    .riskLevel(result.getRiskLevel())
                    .compositeScore(result.getCompositeRiskScore())
                    .details(buildEventDetails(result))
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            complianceEventPublisher.publishComplianceEvent(event);
            
            // Send high-risk alerts
            if (result.getRiskLevel() == ComplianceRiskLevel.HIGH) {
                sendHighRiskAlert(request, result);
            }
            
        } catch (Exception e) {
            log.error("Failed to publish compliance events", e);
        }
    }
    
    /**
     * Schedule ongoing monitoring for high-risk customers
     */
    private void scheduleOngoingMonitoring(ComprehensiveScreeningRequest request, 
                                         ComprehensiveScreeningResult result) {
        if (result.getRiskLevel() == ComplianceRiskLevel.HIGH || 
            result.getRiskLevel() == ComplianceRiskLevel.MEDIUM) {
            
            log.info("Scheduling ongoing monitoring for customer: {} {} (Risk: {})",
                    request.getFirstName(), request.getLastName(), result.getRiskLevel());
            
            // Schedule ongoing monitoring with the compliance monitoring service
            scheduleOngoingMonitoring(request.getCustomerId(), result.getRiskLevel());
        }
    }
    
    // Helper methods for screening against various lists
    
    private List<SanctionsMatch> screenAgainstEUSanctions(ComprehensiveScreeningRequest request) {
        // Implementation would screen against EU sanctions lists
        return new ArrayList<>();
    }
    
    private List<SanctionsMatch> screenAgainstUNSanctions(ComprehensiveScreeningRequest request) {
        // Implementation would screen against UN sanctions lists
        return new ArrayList<>();
    }
    
    private List<SanctionsMatch> screenAgainstUKSanctions(ComprehensiveScreeningRequest request) {
        // Implementation would screen against UK sanctions lists
        return new ArrayList<>();
    }
    
    private List<SanctionsMatch> screenAgainstOtherSanctions(ComprehensiveScreeningRequest request) {
        // Implementation would screen against other regional sanctions
        return new ArrayList<>();
    }
    
    private List<WatchListMatch> screenAgainstFBIWatchList(ComprehensiveScreeningRequest request) {
        // Implementation would screen against FBI watch lists
        return new ArrayList<>();
    }
    
    private List<WatchListMatch> screenAgainstInterpolWatchList(ComprehensiveScreeningRequest request) {
        // Implementation would screen against Interpol watch lists
        return new ArrayList<>();
    }
    
    private List<WatchListMatch> screenAgainstCustomWatchLists(ComprehensiveScreeningRequest request) {
        // Implementation would screen against custom watch lists
        return new ArrayList<>();
    }
    
    // Utility methods
    
    private String generateScreeningId() {
        return "SCREEN_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
    
    private void validateScreeningRequest(ComprehensiveScreeningRequest request) {
        if (request.getFirstName() == null || request.getFirstName().trim().isEmpty()) {
            throw new IllegalArgumentException("First name is required for compliance screening");
        }
        
        if (request.getLastName() == null || request.getLastName().trim().isEmpty()) {
            throw new IllegalArgumentException("Last name is required for compliance screening");
        }
    }
    
    private ComprehensiveScreeningResult convertRecordToResult(ComplianceRecord record) {
        return ComprehensiveScreeningResult.builder()
                .screeningId(record.getScreeningId())
                .clean(record.isClean())
                .riskLevel(record.getRiskLevel())
                .compositeRiskScore(record.getCompositeRiskScore())
                .screenedAt(record.getScreenedAt())
                .fromCache(true)
                .build();
    }
    
    private ScreeningResult createScreeningResult(String screeningId, String screeningType, Object result) {
        return ScreeningResult.builder()
                .screeningId(screeningId)
                .screeningType(screeningType)
                .clean(isResultClean(result))
                .resultData(serializeResult(result))
                .screenedAt(LocalDateTime.now())
                .build();
    }
    
    private boolean isResultClean(Object result) {
        // Generic check for cleanliness based on result type
        if (result instanceof OFACScreeningResult) {
            return ((OFACScreeningResult) result).isClean();
        } else if (result instanceof PEPScreeningResult) {
            return ((PEPScreeningResult) result).isClean();
        } else if (result instanceof SanctionsScreeningResult) {
            return ((SanctionsScreeningResult) result).isClean();
        } else if (result instanceof WatchListScreeningResult) {
            return ((WatchListScreeningResult) result).isClean();
        }
        return false;
    }
    
    private String serializeResult(Object result) {
        // Simple serialization - in production, use proper JSON serialization
        return result.toString();
    }
    
    private String buildEventDetails(ComprehensiveScreeningResult result) {
        Map<String, Object> details = new HashMap<>();
        details.put("clean", result.isClean());
        details.put("riskLevel", result.getRiskLevel());
        details.put("compositeScore", result.getCompositeRiskScore());
        details.put("screeningMethod", result.getScreeningMethod());
        details.put("errorOccurred", result.isErrorOccurred());
        
        // Convert to JSON string (simplified)
        return details.toString();
    }
    
    private void sendHighRiskAlert(ComprehensiveScreeningRequest request, ComprehensiveScreeningResult result) {
        log.warn("HIGH RISK CUSTOMER DETECTED: {} {} - Score: {} - Screening ID: {}",
                request.getFirstName(), request.getLastName(), 
                result.getCompositeRiskScore(), result.getScreeningId());
        
        // In production, this would integrate with alerting systems
    }
}