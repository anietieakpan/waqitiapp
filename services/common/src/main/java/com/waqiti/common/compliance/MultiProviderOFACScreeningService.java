package com.waqiti.common.compliance;

import com.waqiti.common.compliance.model.*;
import com.waqiti.common.compliance.provider.*;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.cache.DistributedCacheService;
import com.waqiti.common.events.compliance.ComplianceAuditEvent;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PostConstruct;

/**
 * ENTERPRISE-GRADE MULTI-PROVIDER OFAC SCREENING SERVICE
 * 
 * Implements redundant OFAC sanctions screening with multiple backup providers
 * to prevent the critical security vulnerability where OFAC screening failures
 * resulted in "manual review required" responses, potentially allowing
 * sanctioned entities to bypass screening.
 * 
 * Features:
 * - Primary OFAC provider with automatic failover
 * - Multiple backup screening providers (Dow Jones, Refinitiv, World-Check)
 * - Real-time sanctions data synchronization
 * - Distributed caching for performance
 * - Comprehensive audit logging
 * - Regulatory compliance reporting
 * - Emergency override capabilities for authorized personnel
 * 
 * Compliance Standards:
 * - OFAC Regulations (31 CFR 501)
 * - BSA/AML Requirements
 * - EU Sanctions Regulations
 * - UN Security Council Sanctions
 * 
 * @author Waqiti Compliance Team
 * @since 2.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MultiProviderOFACScreeningService {

    private final PrimaryOFACProvider primaryOFACProvider;
    private final DowJonesSanctionsProvider dowJonesProvider;
    private final RefinitivSanctionsProvider refinitivProvider;
    private final WorldCheckProvider worldCheckProvider;
    private final AuditService auditService;
    private final DistributedCacheService cacheService;
    private final MeterRegistry meterRegistry;
    
    // Configuration
    @Value("${compliance.ofac.primary.enabled:true}")
    private boolean primaryProviderEnabled;
    
    @Value("${compliance.ofac.backup.enabled:true}")
    private boolean backupProvidersEnabled;
    
    @Value("${compliance.ofac.cache.ttl.minutes:30}")
    private int cacheTimeToLiveMinutes;
    
    @Value("${compliance.ofac.screening.timeout.seconds:10}")
    private int screeningTimeoutSeconds;
    
    @Value("${compliance.ofac.consensus.required:2}")
    private int consensusRequired; // Minimum providers that must agree
    
    // Enterprise-grade metrics with lazy initialization and thread safety
    private volatile boolean metricsInitialized = false;
    private final Object metricsLock = new Object();
    
    // Immutable metrics - initialized once and thread-safe
    private volatile Counter screeningRequestsCounter;
    private volatile Counter sanctionsMatchesCounter;
    private volatile Counter providerFailuresCounter;
    private volatile Counter cacheHitsCounter;
    private volatile Counter consensusFailuresCounter;
    private volatile Counter providerTimeoutsCounter;
    private volatile Timer screeningLatencyTimer;
    private volatile Gauge activeScreeningsGauge;
    
    /**
     * Enterprise-grade metrics initialization with comprehensive observability
     * Follows double-checked locking pattern for thread-safe lazy initialization
     */
    @PostConstruct
    private void initializeEnterpriseMetrics() {
        if (!metricsInitialized) {
            synchronized (metricsLock) {
                if (!metricsInitialized) {
                    try {
                        log.info("Initializing enterprise OFAC screening metrics");
                        
                        // Core screening metrics with comprehensive tags
                        this.screeningRequestsCounter = Counter.builder("waqiti.ofac.screening.requests.total")
                            .description("Total number of OFAC screening requests processed")
                            .tag("service", "ofac-screening")
                            .tag("component", "multi-provider")
                            .tag("compliance", "ofac")
                            .register(meterRegistry);
                            
                        this.sanctionsMatchesCounter = Counter.builder("waqiti.ofac.sanctions.matches.total")
                            .description("Total number of positive sanctions matches detected")
                            .tag("service", "ofac-screening")
                            .tag("severity", "critical")
                            .tag("compliance", "ofac")
                            .register(meterRegistry);
                            
                        this.providerFailuresCounter = Counter.builder("waqiti.ofac.provider.failures.total")
                            .description("Total number of OFAC provider failures")
                            .tag("service", "ofac-screening")
                            .tag("component", "provider-integration")
                            .register(meterRegistry);
                            
                        this.cacheHitsCounter = Counter.builder("waqiti.ofac.cache.hits.total")
                            .description("Total number of OFAC cache hits for performance optimization")
                            .tag("service", "ofac-screening")
                            .tag("component", "cache")
                            .register(meterRegistry);
                            
                        // Advanced enterprise metrics for operational excellence
                        this.consensusFailuresCounter = Counter.builder("waqiti.ofac.consensus.failures.total")
                            .description("Total number of provider consensus failures requiring manual review")
                            .tag("service", "ofac-screening")
                            .tag("severity", "high")
                            .tag("requires", "manual-review")
                            .register(meterRegistry);
                            
                        this.providerTimeoutsCounter = Counter.builder("waqiti.ofac.provider.timeouts.total")
                            .description("Total number of provider timeout failures")
                            .tag("service", "ofac-screening")
                            .tag("component", "provider-integration")
                            .tag("error", "timeout")
                            .register(meterRegistry);
                            
                        // Performance and latency metrics
                        this.screeningLatencyTimer = Timer.builder("waqiti.ofac.screening.duration")
                            .description("Time taken to complete OFAC screening including all providers")
                            .tag("service", "ofac-screening")
                            .tag("unit", "seconds")
                            .register(meterRegistry);
                            
                        // Real-time operational metrics
                        this.activeScreeningsGauge = Gauge.builder("waqiti.ofac.screening.active.current", this, MultiProviderOFACScreeningService::getActiveScreeningCount)
                            .description("Current number of active OFAC screenings in progress")
                            .tag("service", "ofac-screening")
                            .tag("component", "concurrent-processing")
                            .register(meterRegistry);
                        
                        metricsInitialized = true;
                        
                        log.info("Enterprise OFAC screening metrics initialized successfully - {} counters, {} timers, {} gauges", 
                            6, 1, 1);
                            
                        // Register metrics health check
                        registerMetricsHealthCheck();
                        
                    } catch (Exception e) {
                        log.error("Critical failure initializing OFAC screening metrics: {}", e.getMessage(), e);
                        throw new IllegalStateException("Failed to initialize enterprise metrics for OFAC screening", e);
                    }
                }
            }
        }
    }
    
    /**
     * Enterprise-grade metrics health validation
     */
    private void registerMetricsHealthCheck() {
        // Validate all metrics are properly registered
        boolean allMetricsValid = screeningRequestsCounter != null && 
                                 sanctionsMatchesCounter != null && 
                                 providerFailuresCounter != null && 
                                 cacheHitsCounter != null &&
                                 consensusFailuresCounter != null &&
                                 providerTimeoutsCounter != null &&
                                 screeningLatencyTimer != null &&
                                 activeScreeningsGauge != null;
        
        if (!allMetricsValid) {
            throw new IllegalStateException("OFAC screening metrics validation failed - incomplete initialization");
        }
        
        log.info("OFAC screening metrics health check passed - all {} metrics properly registered", 8);
    }
    
    /**
     * Get current active screening count for gauge metric
     */
    private double getActiveScreeningCount() {
        // Implementation would track concurrent screenings
        // This is a placeholder for the actual concurrent screening counter
        return 0.0;
    }

    /**
     * Comprehensive OFAC screening with multi-provider redundancy.
     * This method replaces the vulnerable fallback that returned "manual review required".
     * 
     * @param request The screening request containing entity details
     * @return Comprehensive screening result with high confidence
     */
    @Timed(value = "waqiti.ofac.screening.duration", description = "Time taken for OFAC screening")
    public OFACScreeningResult screenEntity(OFACScreeningRequest request) {
        // Enterprise-grade metrics timing with proper resource management
        Timer.Sample timerSample = Timer.start(meterRegistry);
        
        try {
            log.info("Starting comprehensive OFAC screening for entity: {} [Request ID: {}]", 
                request.getEntityName(), request.getRequestId());
            
            // Increment request counter with enhanced tags
            meterRegistry.counter("waqiti.ofac.screening.requests.total",
                "entity_type", request.getEntityType(),
                "screening_level", request.getScreeningLevel(),
                "jurisdiction", request.getJurisdiction()
            ).increment();
            
            // Check cache first for performance optimization
            String cacheKey = generateCacheKey(request);
            OFACScreeningResult cachedResult = getCachedResult(cacheKey);
            if (cachedResult != null) {
                log.debug("Cache hit for OFAC screening: {} [Cache Key: {}]", 
                    request.getEntityName(), cacheKey);
                
                meterRegistry.counter("waqiti.ofac.cache.hits.total",
                    "cache_type", "distributed",
                    "entity_type", request.getEntityType()
                ).increment();
                
                // Update last checked timestamp for audit trail
                cachedResult.setLastChecked(LocalDateTime.now());
                cachedResult.setCacheHit(true);
                
                // Record cache hit latency
                timerSample.stop(Timer.builder("waqiti.ofac.cache.lookup.duration")
                    .description("Time taken for cache lookup")
                    .tag("result", "hit")
                    .register(meterRegistry));
                
                return cachedResult;
            }
            
            // Perform multi-provider screening
            List<ProviderScreeningResult> providerResults = new ArrayList<>();
            
            // Screen with primary provider
            if (primaryProviderEnabled) {
                ProviderScreeningResult primaryResult = screenWithProvider(
                    primaryOFACProvider, request, "PRIMARY_OFAC");
                if (primaryResult != null) {
                    providerResults.add(primaryResult);
                }
            }
            
            // Screen with backup providers if needed
            if (backupProvidersEnabled && (providerResults.isEmpty() || requiresAdditionalVerification(providerResults))) {
                
                // Dow Jones screening
                ProviderScreeningResult dowJonesResult = screenWithProvider(
                    dowJonesProvider, request, "DOW_JONES");
                if (dowJonesResult != null) {
                    providerResults.add(dowJonesResult);
                }
                
                // Refinitiv screening
                ProviderScreeningResult refinitivResult = screenWithProvider(
                    refinitivProvider, request, "REFINITIV");
                if (refinitivResult != null) {
                    providerResults.add(refinitivResult);
                }
                
                // World-Check screening for high-risk cases
                if (hasHighRiskIndicators(providerResults)) {
                    ProviderScreeningResult worldCheckResult = screenWithProvider(
                        worldCheckProvider, request, "WORLD_CHECK");
                    if (worldCheckResult != null) {
                        providerResults.add(worldCheckResult);
                    }
                }
            }
            
            // Analyze results and build consensus
            OFACScreeningResult finalResult = analyzeProviderResults(request, providerResults);
            
            // Cache the result
            cacheResult(cacheKey, finalResult);
            
            // Audit the screening
            auditScreeningResult(request, finalResult, providerResults);
            
            // Update metrics
            if (finalResult.isMatch()) {
                sanctionsMatchesCounter.increment();
            }
            
            log.info("OFAC screening completed for entity: {} - Match: {}, Confidence: {:.2f}", 
                request.getEntityName(), finalResult.isMatch(), finalResult.getConfidenceScore());
            
            return finalResult;
            
        } catch (Exception e) {
            log.error("Critical error in OFAC screening for entity: {}", request.getEntityName(), e);
            
            // For critical errors, fail secure - treat as potential match requiring investigation
            OFACScreeningResult errorResult = OFACScreeningResult.builder()
                .entityName(request.getEntityName())
                .match(true) // FAIL SECURE - assume match until proven otherwise
                .confidenceScore(0.5)
                .riskLevel(RiskLevel.HIGH)
                .screeningStatus(ScreeningStatus.ERROR)
                .errorMessage("Critical OFAC screening error - manual investigation required")
                .requiresInvestigation(true)
                .screenedAt(LocalDateTime.now())
                .lastChecked(LocalDateTime.now())
                .build();
            
            // Audit the error
            auditService.auditComplianceEvent(ComplianceAuditEvent.builder()
                .eventType("OFAC_SCREENING_ERROR")
                .entityName(request.getEntityName())
                .errorMessage(e.getMessage())
                .riskLevel(RiskLevel.CRITICAL)
                .requiresImmediateAction(true)
                .occurredAt(LocalDateTime.now())
                .build());
            
            return errorResult;
        }
    }

    /**
     * Bulk screening operation for multiple entities with optimized processing.
     */
    @Async
    @Timed(value = "ofac_bulk_screening_duration", description = "Time taken for bulk OFAC screening")
    public CompletableFuture<List<OFACScreeningResult>> bulkScreenEntities(List<OFACScreeningRequest> requests) {
        try {
            log.info("Starting bulk OFAC screening for {} entities", requests.size());
            
            List<CompletableFuture<OFACScreeningResult>> futures = requests.stream()
                .map(request -> CompletableFuture.supplyAsync(() -> screenEntity(request)))
                .toList();
            
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
            
            return allFutures.thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .toList());
                
        } catch (Exception e) {
            log.error("Error in bulk OFAC screening", e);
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }

    /**
     * Real-time sanctions list update processing.
     */
    @Timed(value = "ofac_list_update_duration", description = "Time taken to update sanctions lists")
    public void updateSanctionsList() {
        try {
            log.info("Starting sanctions list update from all providers");
            
            // Update from primary OFAC source
            if (primaryProviderEnabled) {
                primaryOFACProvider.updateSanctionsList();
            }
            
            // Update from backup providers
            if (backupProvidersEnabled) {
                CompletableFuture.allOf(
                    CompletableFuture.runAsync(() -> dowJonesProvider.updateSanctionsList()),
                    CompletableFuture.runAsync(() -> refinitivProvider.updateSanctionsList()),
                    CompletableFuture.runAsync(() -> worldCheckProvider.updateSanctionsList())
                ).get(300, TimeUnit.SECONDS); // 5 minute timeout
            }
            
            // Clear cache after updates
            cacheService.evictAll("ofac-screening");
            
            log.info("Sanctions list update completed successfully");
            
        } catch (Exception e) {
            log.error("Error updating sanctions lists", e);
            
            // Alert compliance team
            auditService.auditComplianceEvent(ComplianceAuditEvent.builder()
                .eventType("SANCTIONS_LIST_UPDATE_FAILED")
                .errorMessage(e.getMessage())
                .riskLevel(RiskLevel.HIGH)
                .requiresImmediateAction(true)
                .occurredAt(LocalDateTime.now())
                .build());
        }
    }

    /**
     * Emergency override for authorized compliance officers.
     */
    public OFACScreeningResult emergencyOverride(String entityName, String overrideReason, 
                                                String authorizedBy, String authorizationCode) {
        try {
            log.warn("Emergency OFAC override requested for entity: {} by: {}", entityName, authorizedBy);
            
            // Validate authorization (this would integrate with security service)
            if (!isValidEmergencyAuthorization(authorizationCode, authorizedBy)) {
                throw new SecurityException("Invalid emergency authorization");
            }
            
            OFACScreeningResult overrideResult = OFACScreeningResult.builder()
                .entityName(entityName)
                .match(false) // Override allows transaction
                .confidenceScore(0.0)
                .riskLevel(RiskLevel.LOW)
                .screeningStatus(ScreeningStatus.EMERGENCY_OVERRIDE)
                .overrideReason(overrideReason)
                .overrideAuthorizedBy(authorizedBy)
                .requiresPostTransactionReview(true)
                .screenedAt(LocalDateTime.now())
                .lastChecked(LocalDateTime.now())
                .build();
            
            // Audit the override
            auditService.auditComplianceEvent(ComplianceAuditEvent.builder()
                .eventType("OFAC_EMERGENCY_OVERRIDE")
                .entityName(entityName)
                .overrideReason(overrideReason)
                .authorizedBy(authorizedBy)
                .riskLevel(RiskLevel.CRITICAL)
                .requiresImmediateAction(true)
                .occurredAt(LocalDateTime.now())
                .build());
            
            return overrideResult;
            
        } catch (Exception e) {
            log.error("Error processing emergency OFAC override", e);
            throw new RuntimeException("Emergency override failed: " + e.getMessage(), e);
        }
    }

    // Private helper methods

    private ProviderScreeningResult screenWithProvider(SanctionsProvider provider, 
                                                      OFACScreeningRequest request, 
                                                      String providerName) {
        try {
            log.debug("Screening with provider: {}", providerName);
            
            ProviderScreeningResult result = provider.screenEntity(request);
            result.setProviderName(providerName);
            result.setScreenedAt(LocalDateTime.now());
            
            return result;
            
        } catch (Exception e) {
            log.error("Provider {} failed for entity: {}", providerName, request.getEntityName(), e);
            
            meterRegistry.counter("waqiti.ofac.provider.failures.total",
                "provider", providerName
            ).increment();
            
            return null;
        }
    }

    private boolean requiresAdditionalVerification(List<ProviderScreeningResult> results) {
        if (results.isEmpty()) {
            return true; // No results, need backup providers
        }
        
        // Check if we have conflicting results
        boolean hasMatch = results.stream().anyMatch(ProviderScreeningResult::isMatch);
        boolean hasNoMatch = results.stream().anyMatch(result -> !result.isMatch());
        
        return hasMatch && hasNoMatch; // Conflicting results require additional verification
    }

    private boolean hasHighRiskIndicators(List<ProviderScreeningResult> results) {
        return results.stream().anyMatch(result -> 
            result.isMatch() && result.getConfidenceScore() > 0.8);
    }

    private OFACScreeningResult analyzeProviderResults(OFACScreeningRequest request, 
                                                      List<ProviderScreeningResult> providerResults) {
        if (providerResults.isEmpty()) {
            // No provider results - this is a critical situation
            log.error("NO OFAC PROVIDERS AVAILABLE - BLOCKING TRANSACTION for entity: {}", request.getEntityName());
            
            return OFACScreeningResult.builder()
                .entityName(request.getEntityName())
                .match(true) // FAIL SECURE - block transaction
                .confidenceScore(1.0)
                .riskLevel(RiskLevel.CRITICAL)
                .screeningStatus(ScreeningStatus.NO_PROVIDERS_AVAILABLE)
                .errorMessage("CRITICAL: No OFAC providers available - transaction blocked")
                .requiresInvestigation(true)
                .screenedAt(LocalDateTime.now())
                .lastChecked(LocalDateTime.now())
                .build();
        }
        
        // Analyze consensus among providers
        long matchCount = providerResults.stream().mapToLong(result -> result.isMatch() ? 1 : 0).sum();
        double averageConfidence = providerResults.stream()
            .mapToDouble(ProviderScreeningResult::getConfidenceScore)
            .average()
            .orElse(0.0);
        
        boolean isMatch = matchCount >= consensusRequired;
        RiskLevel riskLevel = determineRiskLevel(matchCount, providerResults.size(), averageConfidence);
        ScreeningStatus status = isMatch ? ScreeningStatus.MATCH_FOUND : ScreeningStatus.NO_MATCH;
        
        // Collect all match details
        List<SanctionsMatch> allMatches = providerResults.stream()
            .filter(ProviderScreeningResult::isMatch)
            .flatMap(result -> result.getMatches().stream())
            .toList();
        
        return OFACScreeningResult.builder()
            .entityName(request.getEntityName())
            .match(isMatch)
            .confidenceScore(averageConfidence)
            .riskLevel(riskLevel)
            .screeningStatus(status)
            .matches(allMatches)
            .providerResults(providerResults)
            .providersChecked(providerResults.size())
            .providersWithMatches((int) matchCount)
            .requiresInvestigation(isMatch && averageConfidence > 0.5)
            .screenedAt(LocalDateTime.now())
            .lastChecked(LocalDateTime.now())
            .build();
    }

    private RiskLevel determineRiskLevel(long matchCount, int totalProviders, double averageConfidence) {
        if (matchCount == 0) {
            return RiskLevel.LOW;
        }
        
        double matchRatio = (double) matchCount / totalProviders;
        
        if (matchRatio >= 0.75 && averageConfidence > 0.8) {
            return RiskLevel.CRITICAL;
        } else if (matchRatio >= 0.5 || averageConfidence > 0.6) {
            return RiskLevel.HIGH;
        } else {
            return RiskLevel.MEDIUM;
        }
    }

    private String generateCacheKey(OFACScreeningRequest request) {
        return String.format("ofac:%s:%s:%s", 
            request.getEntityName().toLowerCase().replaceAll("\\s+", ""),
            Optional.ofNullable(request.getAddress()).orElse("").toLowerCase().replaceAll("\\s+", ""),
            Optional.ofNullable(request.getDateOfBirth()).map(Object::toString).orElse(""));
    }

    private OFACScreeningResult getCachedResult(String cacheKey) {
        try {
            return cacheService.get(cacheKey, OFACScreeningResult.class).orElse(null);
        } catch (Exception e) {
            log.debug("Cache miss or error for key: {}", cacheKey);
            return null;
        }
    }

    private void cacheResult(String cacheKey, OFACScreeningResult result) {
        try {
            cacheService.put(cacheKey, result, Duration.ofMinutes(cacheTimeToLiveMinutes));
        } catch (Exception e) {
            log.warn("Failed to cache OFAC result for key: {}", cacheKey, e);
        }
    }

    private void auditScreeningResult(OFACScreeningRequest request, OFACScreeningResult result, 
                                    List<ProviderScreeningResult> providerResults) {
        auditService.auditComplianceEvent(ComplianceAuditEvent.builder()
            .eventType("OFAC_SCREENING_COMPLETED")
            .entityName(request.getEntityName())
            .screeningResult(result)
            .providersUsed(providerResults.stream().map(ProviderScreeningResult::getProviderName).toList())
            .isMatch(result.isMatch())
            .confidenceScore(result.getConfidenceScore())
            .riskLevel(result.getRiskLevel())
            .requiresInvestigation(result.isRequiresInvestigation())
            .occurredAt(LocalDateTime.now())
            .build());
    }

    private boolean isValidEmergencyAuthorization(String authorizationCode, String authorizedBy) {
        // This would integrate with the security service to validate emergency authorization
        // For now, implement basic validation
        return authorizationCode != null && authorizationCode.length() >= 8 && 
               authorizedBy != null && !authorizedBy.trim().isEmpty();
    }
}