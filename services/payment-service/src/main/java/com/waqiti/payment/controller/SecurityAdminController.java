/**
 * SECURITY ENHANCEMENT: Security Administration Controller
 * Provides administrative endpoints for security monitoring and configuration
 */
package com.waqiti.payment.controller;

import com.waqiti.payment.security.ErrorMessageSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SECURITY-FOCUSED controller for administrative security operations
 */
@RestController
@RequestMapping("/api/v1/admin/security")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ANALYST')")
public class SecurityAdminController {
    
    private final ErrorMessageSanitizer errorMessageSanitizer;
    
    /**
     * Get error sanitization statistics
     */
    @GetMapping("/error-sanitization/stats")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ANALYST')")
    public ResponseEntity<Map<String, Object>> getErrorSanitizationStats() {
        log.info("SECURITY: Admin requesting error sanitization statistics");
        
        Map<String, Object> stats = errorMessageSanitizer.getErrorStatistics();
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Manually trigger cleanup of old error tracking entries
     */
    @PostMapping("/error-sanitization/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> cleanupErrorTracking() {
        log.info("SECURITY: Admin manually triggering error tracking cleanup");
        
        try {
            errorMessageSanitizer.cleanupOldEntries();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Error tracking entries cleaned up successfully"
            ));
            
        } catch (Exception e) {
            log.error("SECURITY: Error during manual cleanup", e);
            
            return ResponseEntity.ok(Map.of(
                "status", "error", 
                "message", "Cleanup failed: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get original error message by tracking ID (for support purposes)
     */
    @GetMapping("/error-sanitization/original/{trackingId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<Map<String, String>> getOriginalErrorMessage(@PathVariable String trackingId) {
        log.info("SECURITY: Admin requesting original error message for tracking ID: {}", trackingId);
        
        try {
            String originalMessage = errorMessageSanitizer.getOriginalErrorMessage(trackingId);
            
            if (originalMessage != null) {
                return ResponseEntity.ok(Map.of(
                    "trackingId", trackingId,
                    "originalMessage", originalMessage,
                    "status", "found"
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "trackingId", trackingId,
                    "status", "not_found",
                    "message", "No original message found for this tracking ID"
                ));
            }
            
        } catch (Exception e) {
            log.error("SECURITY: Error retrieving original message for tracking ID: {}", trackingId, e);
            
            return ResponseEntity.ok(Map.of(
                "trackingId", trackingId,
                "status", "error",
                "message", "Error retrieving original message"
            ));
        }
    }
    
    /**
     * Get security configuration status
     */
    @GetMapping("/config/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ANALYST')")
    public ResponseEntity<Map<String, Object>> getSecurityConfigStatus() {
        log.info("SECURITY: Admin requesting security configuration status");
        
        Map<String, Object> config = Map.of(
            "errorSanitizationEnabled", true,
            "trackingEnabled", true,
            "cleanupScheduled", true,
            "securityLoggingEnabled", true,
            "sanitizationPatternsCount", errorMessageSanitizer.getErrorStatistics().get("sensitivePatternCount"),
            "genericMessagesCount", errorMessageSanitizer.getErrorStatistics().get("genericMessageCount")
        );
        
        return ResponseEntity.ok(config);
    }
}