/**
 * IP Whitelist Management Controller
 * Provides REST API for managing IP whitelist and blacklist
 * Includes comprehensive monitoring and reporting capabilities
 */
package com.waqiti.common.security.ip;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Secure IP management REST API
 * Requires admin privileges for all operations
 */
@Slf4j
@RestController
@RequestMapping("/api/security/ip-whitelist")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "security.ip-whitelist.api.enabled", havingValue = "true", matchIfMissing = true)
public class IpWhitelistController {

    private final IpWhitelistService ipWhitelistService;
    private final IpWhitelistFilter ipWhitelistFilter;

    /**
     * Validate IP address access
     */
    @PostMapping("/validate")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ADMIN')")
    public ResponseEntity<IpWhitelistService.IpValidationResult> validateIpAccess(
            @RequestBody @Valid IpValidationRequest request) {
        
        log.info("Manual IP validation requested for: {}", request.getIpAddress());
        
        IpWhitelistService.IpValidationResult result = ipWhitelistService
            .validateIpAccess(request.getIpAddress(), request.getUserAgent(), request.getUserId());
        
        return ResponseEntity.ok(result);
    }

    /**
     * Add IP address to whitelist
     */
    @PostMapping("/whitelist")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ADMIN')")
    public ResponseEntity<Map<String, Object>> addToWhitelist(
            @RequestBody @Valid WhitelistAddRequest request) {
        
        log.info("Adding IP {} to whitelist - Requested by: {}", 
            request.getIpAddress(), request.getAddedBy());
        
        try {
            ipWhitelistService.addToWhitelist(
                request.getIpAddress(),
                request.getDescription(),
                request.getAddedBy(),
                request.getExpiresAt()
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "IP address added to whitelist");
            response.put("ipAddress", request.getIpAddress());
            response.put("timestamp", Instant.now());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", e.getMessage()));
                
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Remove IP address from whitelist
     */
    @DeleteMapping("/whitelist/{ipAddress}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ADMIN')")
    public ResponseEntity<Map<String, Object>> removeFromWhitelist(
            @PathVariable String ipAddress,
            @RequestParam String removedBy) {
        
        log.info("Removing IP {} from whitelist - Requested by: {}", ipAddress, removedBy);
        
        ipWhitelistService.removeFromWhitelist(ipAddress, removedBy);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "IP address removed from whitelist");
        response.put("ipAddress", ipAddress);
        response.put("timestamp", Instant.now());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Add IP address to blacklist
     */
    @PostMapping("/blacklist")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ADMIN')")
    public ResponseEntity<Map<String, Object>> addToBlacklist(
            @RequestBody @Valid BlacklistAddRequest request) {
        
        log.info("Adding IP {} to blacklist - Requested by: {} - Reason: {}", 
            request.getIpAddress(), request.getAddedBy(), request.getReason());
        
        try {
            ipWhitelistService.addToBlacklist(
                request.getIpAddress(),
                request.getReason(),
                request.getAddedBy(),
                request.getExpiresAt()
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "IP address added to blacklist");
            response.put("ipAddress", request.getIpAddress());
            response.put("reason", request.getReason());
            response.put("timestamp", Instant.now());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Check if IP address is blacklisted
     */
    @GetMapping("/blacklist/check/{ipAddress}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ADMIN') or hasRole('SECURITY_ANALYST')")
    public ResponseEntity<Map<String, Object>> checkBlacklist(@PathVariable String ipAddress) {
        
        boolean isBlacklisted = ipWhitelistService.isBlacklisted(ipAddress);
        
        Map<String, Object> response = new HashMap<>();
        response.put("ipAddress", ipAddress);
        response.put("blacklisted", isBlacklisted);
        response.put("timestamp", Instant.now());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get whitelist statistics
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ADMIN') or hasRole('SECURITY_ANALYST')")
    public ResponseEntity<IpWhitelistService.WhitelistStatistics> getStatistics() {
        
        IpWhitelistService.WhitelistStatistics stats = ipWhitelistService.getWhitelistStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get active request statistics
     */
    @GetMapping("/statistics/requests")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ADMIN') or hasRole('SECURITY_ANALYST')")
    public ResponseEntity<Map<String, Object>> getRequestStatistics() {
        
        Map<String, Object> stats = ipWhitelistFilter.getActiveRequestStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get system health related to IP filtering
     */
    @GetMapping("/health")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ADMIN') or hasRole('SECURITY_ANALYST')")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Test basic functionality
            IpWhitelistService.IpValidationResult testResult = ipWhitelistService
                .validateIpAccess("127.0.0.1", "test-agent", "health-check");
            
            health.put("status", "healthy");
            health.put("ipValidationService", "operational");
            health.put("testResult", testResult.isAllowed() ? "passed" : "blocked");
            
            // Get statistics for health indicators
            IpWhitelistService.WhitelistStatistics stats = ipWhitelistService.getWhitelistStatistics();
            health.put("whitelistEntries", stats.getTotalEntries());
            health.put("cacheHitRate", stats.getCacheHitRate());
            
            Map<String, Object> requestStats = ipWhitelistFilter.getActiveRequestStatistics();
            health.put("activeRequests", requestStats.get("activeRequests"));
            
            health.put("timestamp", Instant.now());
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            log.error("IP whitelist health check failed", e);
            
            health.put("status", "unhealthy");
            health.put("error", e.getMessage());
            health.put("timestamp", Instant.now());
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }
    }

    /**
     * Emergency disable IP filtering
     */
    @PostMapping("/emergency/disable")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> emergencyDisable(
            @RequestParam String adminUser,
            @RequestParam String reason) {
        
        log.error("EMERGENCY: IP filtering disabled by {} - Reason: {}", adminUser, reason);
        
        // In a real implementation, this would disable the filter
        // For now, we just log the emergency action
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Emergency disable logged - Manual configuration change required");
        response.put("disabledBy", adminUser);
        response.put("reason", reason);
        response.put("timestamp", Instant.now());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Bulk import whitelist entries
     */
    @PostMapping("/whitelist/bulk-import")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ADMIN')")
    public ResponseEntity<Map<String, Object>> bulkImportWhitelist(
            @RequestBody @Valid BulkImportRequest request) {
        
        log.info("Bulk whitelist import requested by: {} - {} entries", 
            request.getImportedBy(), request.getEntries().size());
        
        Map<String, Object> results = new HashMap<>();
        int successful = 0;
        int failed = 0;
        
        for (WhitelistEntryRequest entry : request.getEntries()) {
            try {
                ipWhitelistService.addToWhitelist(
                    entry.getIpAddress(),
                    entry.getDescription(),
                    request.getImportedBy(),
                    entry.getExpiresAt()
                );
                successful++;
            } catch (Exception e) {
                log.warn("Failed to import IP {}: {}", entry.getIpAddress(), e.getMessage());
                failed++;
            }
        }
        
        results.put("totalEntries", request.getEntries().size());
        results.put("successful", successful);
        results.put("failed", failed);
        results.put("timestamp", Instant.now());
        
        return ResponseEntity.ok(results);
    }

    // Request DTOs
    @lombok.Data
    public static class IpValidationRequest {
        @NotBlank
        private String ipAddress;
        
        private String userAgent;
        private String userId = "manual-validation";
    }

    @lombok.Data
    public static class WhitelistAddRequest {
        @NotBlank
        private String ipAddress;
        
        @NotBlank
        @Size(max = 255)
        private String description;
        
        @NotBlank
        private String addedBy;
        
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private Instant expiresAt;
    }

    @lombok.Data
    public static class BlacklistAddRequest {
        @NotBlank
        private String ipAddress;
        
        @NotBlank
        @Size(max = 255)
        private String reason;
        
        @NotBlank
        private String addedBy;
        
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private Instant expiresAt;
    }

    @lombok.Data
    public static class BulkImportRequest {
        @NotBlank
        private String importedBy;
        
        @Valid
        private java.util.List<WhitelistEntryRequest> entries;
    }

    @lombok.Data
    public static class WhitelistEntryRequest {
        @NotBlank
        private String ipAddress;
        
        @NotBlank
        @Size(max = 255)
        private String description;
        
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private Instant expiresAt;
    }
}