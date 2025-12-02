/**
 * SECURITY ENHANCEMENT: Fraud Cache Administration Controller
 * Provides admin endpoints for cache validation monitoring and management
 */
package com.waqiti.frauddetection.controller;

import com.waqiti.frauddetection.cache.FraudCacheValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SECURITY-FOCUSED controller for fraud detection cache administration
 */
@RestController
@RequestMapping("/api/v1/fraud/admin/cache")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ANALYST') or hasRole('FRAUD_ANALYST')")
public class FraudCacheAdminController {
    
    private final FraudCacheValidationService cacheValidationService;
    
    /**
     * Get cache validation statistics
     */
    @GetMapping("/validation/stats")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ANALYST') or hasRole('FRAUD_ANALYST')")
    public ResponseEntity<Map<String, Object>> getCacheValidationStats() {
        log.info("SECURITY: Admin requesting cache validation statistics");
        
        try {
            Map<String, Object> stats = cacheValidationService.getCacheValidationStatistics();
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("SECURITY: Error retrieving cache validation stats", e);
            return ResponseEntity.ok(Map.of(
                "status", "error",
                "message", "Failed to retrieve cache validation statistics"
            ));
        }
    }
    
    /**
     * Manually trigger cache integrity check
     */
    @PostMapping("/validation/check")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> triggerIntegrityCheck() {
        log.info("SECURITY: Admin manually triggering cache integrity check");
        
        try {
            cacheValidationService.scheduledIntegrityCheck();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Cache integrity check completed successfully"
            ));
            
        } catch (Exception e) {
            log.error("SECURITY: Error during manual integrity check", e);
            return ResponseEntity.ok(Map.of(
                "status", "error",
                "message", "Integrity check failed: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Clear all suspicious cache entries
     */
    @PostMapping("/validation/clear-suspicious")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> clearSuspiciousEntries() {
        log.info("SECURITY: Admin clearing suspicious cache entries");
        
        try {
            cacheValidationService.clearSuspiciousEntries();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Suspicious cache entries cleared successfully"
            ));
            
        } catch (Exception e) {
            log.error("SECURITY: Error clearing suspicious entries", e);
            return ResponseEntity.ok(Map.of(
                "status", "error",
                "message", "Failed to clear suspicious entries: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Check if a specific key is marked as suspicious
     */
    @GetMapping("/validation/suspicious/{key}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ANALYST')")
    public ResponseEntity<Map<String, Object>> checkSuspiciousKey(@PathVariable String key) {
        log.info("SECURITY: Admin checking if key is suspicious: {}", key);
        
        try {
            boolean isSuspicious = cacheValidationService.isSuspiciousKey(key);
            
            return ResponseEntity.ok(Map.of(
                "key", key,
                "isSuspicious", isSuspicious,
                "status", "success"
            ));
            
        } catch (Exception e) {
            log.error("SECURITY: Error checking suspicious key: {}", key, e);
            return ResponseEntity.ok(Map.of(
                "key", key,
                "status", "error",
                "message", "Failed to check key status"
            ));
        }
    }
    
    /**
     * Get cache validation configuration
     */
    @GetMapping("/validation/config")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ANALYST')")
    public ResponseEntity<Map<String, Object>> getCacheValidationConfig() {
        log.info("SECURITY: Admin requesting cache validation configuration");
        
        Map<String, Object> config = Map.of(
            "validationEnabled", true,
            "integrityCheckEnabled", true,
            "scheduledCheckEnabled", true,
            "maxScoreDeviation", 0.3,
            "suspiciousPatternThreshold", 5,
            "cacheTTLMinutes", 15
        );
        
        return ResponseEntity.ok(config);
    }
}