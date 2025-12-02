package com.waqiti.kyc.compatibility;

import com.waqiti.kyc.config.FeatureFlagConfiguration;
import com.waqiti.kyc.dto.request.KYCVerificationRequest;
import com.waqiti.kyc.dto.response.KYCStatusResponse;
import com.waqiti.kyc.dto.response.KYCVerificationResponse;
import com.waqiti.kyc.service.KYCService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Compatibility service to manage transition between legacy and new KYC systems
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KYCCompatibilityService {

    private final KYCService newKYCService;
    private final LegacyKYCService legacyKYCService;
    private final FeatureFlagConfiguration featureFlags;
    private final MeterRegistry meterRegistry;

    // Metrics
    private Counter legacyCallCounter;
    private Counter newServiceCallCounter;
    private Counter shadowModeMatchCounter;
    private Counter shadowModeMismatchCounter;
    private Timer legacyCallTimer;
    private Timer newServiceCallTimer;

    private void initializeMetrics() {
        legacyCallCounter = Counter.builder("kyc.compatibility.legacy.calls")
                .description("Number of calls to legacy KYC service")
                .register(meterRegistry);
                
        newServiceCallCounter = Counter.builder("kyc.compatibility.new.calls")
                .description("Number of calls to new KYC service")
                .register(meterRegistry);
                
        shadowModeMatchCounter = Counter.builder("kyc.compatibility.shadow.match")
                .description("Shadow mode result matches")
                .register(meterRegistry);
                
        shadowModeMismatchCounter = Counter.builder("kyc.compatibility.shadow.mismatch")
                .description("Shadow mode result mismatches")
                .register(meterRegistry);
                
        legacyCallTimer = Timer.builder("kyc.compatibility.legacy.duration")
                .description("Legacy KYC service call duration")
                .register(meterRegistry);
                
        newServiceCallTimer = Timer.builder("kyc.compatibility.new.duration")
                .description("New KYC service call duration")
                .register(meterRegistry);
    }

    public KYCVerificationResponse initiateVerification(String userId, KYCVerificationRequest request) {
        if (featureFlags.isEnabled("USE_NEW_KYC_SERVICE")) {
            // Route to new service
            log.debug("Routing KYC verification to new service for user: {}", userId);
            newServiceCallCounter.increment();
            
            return newServiceCallTimer.record(() -> {
                KYCVerificationResponse response = newKYCService.initiateVerification(userId, request);
                
                // Dual write mode - also write to legacy system
                if (featureFlags.isEnabled("DUAL_WRITE_MODE")) {
                    writeLegacyAsync(userId, request, response);
                }
                
                return response;
            });
        } else {
            // Route to legacy service
            log.debug("Routing KYC verification to legacy service for user: {}", userId);
            legacyCallCounter.increment();
            
            KYCVerificationResponse response = legacyCallTimer.record(() -> 
                legacyKYCService.initiateVerification(userId, request)
            );
            
            // Shadow mode - call new service asynchronously and compare
            if (featureFlags.isEnabled("SHADOW_MODE")) {
                shadowCallAsync(userId, request, response);
            }
            
            // Dual write mode - also write to new system
            if (featureFlags.isEnabled("DUAL_WRITE_MODE")) {
                writeNewAsync(userId, request, response);
            }
            
            return response;
        }
    }

    public KYCStatusResponse getUserKYCStatus(String userId) {
        if (featureFlags.isEnabled("USE_NEW_KYC_SERVICE")) {
            log.debug("Getting KYC status from new service for user: {}", userId);
            newServiceCallCounter.increment();
            
            return newServiceCallTimer.record(() -> 
                newKYCService.getUserKYCStatus(userId)
            );
        } else {
            log.debug("Getting KYC status from legacy service for user: {}", userId);
            legacyCallCounter.increment();
            
            KYCStatusResponse response = legacyCallTimer.record(() -> 
                legacyKYCService.getUserKYCStatus(userId)
            );
            
            // Shadow mode comparison
            if (featureFlags.isEnabled("SHADOW_MODE")) {
                shadowStatusCheckAsync(userId, response);
            }
            
            return response;
        }
    }

    public boolean isUserVerified(String userId, String level) {
        if (featureFlags.isEnabled("USE_NEW_KYC_SERVICE")) {
            newServiceCallCounter.increment();
            return newServiceCallTimer.record(() -> 
                newKYCService.isUserVerified(userId, level)
            );
        } else {
            legacyCallCounter.increment();
            boolean result = legacyCallTimer.record(() -> 
                legacyKYCService.isUserVerified(userId, level)
            );
            
            // Shadow mode verification
            if (featureFlags.isEnabled("SHADOW_MODE")) {
                shadowVerificationCheckAsync(userId, level, result);
            }
            
            return result;
        }
    }

    // Async shadow mode operations
    private void shadowCallAsync(String userId, KYCVerificationRequest request, 
                                KYCVerificationResponse legacyResponse) {
        CompletableFuture.runAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();
                KYCVerificationResponse newResponse = newKYCService.initiateVerification(userId, request);
                long duration = System.currentTimeMillis() - startTime;
                
                // Compare results
                boolean matches = compareResponses(legacyResponse, newResponse);
                
                if (matches) {
                    shadowModeMatchCounter.increment();
                    log.debug("Shadow mode: Results match for user {} (duration: {}ms)", userId, duration);
                } else {
                    shadowModeMismatchCounter.increment();
                    log.warn("Shadow mode: Results mismatch for user {} - Legacy: {}, New: {}", 
                            userId, legacyResponse.getStatus(), newResponse.getStatus());
                }
                
                // Log performance comparison
                logPerformanceComparison("initiateVerification", userId, duration);
                
            } catch (Exception e) {
                log.error("Shadow mode error for user {}: {}", userId, e.getMessage());
            }
        });
    }

    private void shadowStatusCheckAsync(String userId, KYCStatusResponse legacyResponse) {
        CompletableFuture.runAsync(() -> {
            try {
                KYCStatusResponse newResponse = newKYCService.getUserKYCStatus(userId);
                
                boolean matches = compareStatusResponses(legacyResponse, newResponse);
                
                if (matches) {
                    shadowModeMatchCounter.increment();
                } else {
                    shadowModeMismatchCounter.increment();
                    log.warn("Shadow mode status mismatch for user {} - Legacy: {}, New: {}", 
                            userId, legacyResponse.getCurrentStatus(), newResponse.getCurrentStatus());
                }
            } catch (Exception e) {
                log.error("Shadow mode status check error for user {}: {}", userId, e.getMessage());
            }
        });
    }

    private void shadowVerificationCheckAsync(String userId, String level, boolean legacyResult) {
        CompletableFuture.runAsync(() -> {
            try {
                boolean newResult = newKYCService.isUserVerified(userId, level);
                
                if (legacyResult == newResult) {
                    shadowModeMatchCounter.increment();
                } else {
                    shadowModeMismatchCounter.increment();
                    log.warn("Shadow mode verification mismatch for user {} level {} - Legacy: {}, New: {}", 
                            userId, level, legacyResult, newResult);
                }
            } catch (Exception e) {
                log.error("Shadow mode verification check error for user {}: {}", userId, e.getMessage());
            }
        });
    }

    // Dual write operations
    private void writeLegacyAsync(String userId, KYCVerificationRequest request, 
                                 KYCVerificationResponse newResponse) {
        CompletableFuture.runAsync(() -> {
            try {
                legacyKYCService.syncFromNewService(userId, newResponse);
                log.debug("Dual write: Successfully wrote to legacy system for user {}", userId);
            } catch (Exception e) {
                log.error("Dual write: Failed to write to legacy system for user {}: {}", userId, e.getMessage());
            }
        });
    }

    private void writeNewAsync(String userId, KYCVerificationRequest request, 
                              KYCVerificationResponse legacyResponse) {
        CompletableFuture.runAsync(() -> {
            try {
                // Convert legacy response to new format and save
                newKYCService.syncFromLegacyService(userId, legacyResponse);
                log.debug("Dual write: Successfully wrote to new system for user {}", userId);
            } catch (Exception e) {
                log.error("Dual write: Failed to write to new system for user {}: {}", userId, e.getMessage());
            }
        });
    }

    // Comparison methods
    private boolean compareResponses(KYCVerificationResponse legacy, KYCVerificationResponse newResp) {
        if (legacy == null || newResp == null) {
            return false;
        }
        
        // Compare key fields
        return legacy.getStatus() == newResp.getStatus() &&
               legacy.getVerificationLevel() == newResp.getVerificationLevel() &&
               legacy.getUserId().equals(newResp.getUserId());
    }

    private boolean compareStatusResponses(KYCStatusResponse legacy, KYCStatusResponse newResp) {
        if (legacy == null || newResp == null) {
            return false;
        }
        
        return legacy.getCurrentStatus() == newResp.getCurrentStatus() &&
               legacy.getCurrentLevel() == newResp.getCurrentLevel() &&
               legacy.getIsActive().equals(newResp.getIsActive());
    }

    private void logPerformanceComparison(String operation, String userId, long newServiceDuration) {
        log.info("Performance comparison - Operation: {}, User: {}, New service duration: {}ms", 
                operation, userId, newServiceDuration);
    }

    // Health check for both services
    public CompatibilityHealthStatus getHealthStatus() {
        CompatibilityHealthStatus status = new CompatibilityHealthStatus();
        
        // Check legacy service health
        try {
            boolean legacyHealthy = legacyKYCService.isHealthy();
            status.setLegacyServiceHealthy(legacyHealthy);
        } catch (Exception e) {
            status.setLegacyServiceHealthy(false);
            status.setLegacyServiceError(e.getMessage());
        }
        
        // Check new service health
        try {
            boolean newServiceHealthy = newKYCService != null;
            status.setNewServiceHealthy(newServiceHealthy);
        } catch (Exception e) {
            status.setNewServiceHealthy(false);
            status.setNewServiceError(e.getMessage());
        }
        
        // Set feature flag states
        status.setUseNewService(featureFlags.isEnabled("USE_NEW_KYC_SERVICE"));
        status.setDualWriteMode(featureFlags.isEnabled("DUAL_WRITE_MODE"));
        status.setShadowMode(featureFlags.isEnabled("SHADOW_MODE"));
        
        return status;
    }
}