package com.waqiti.security.rasp.controller;

import com.waqiti.security.rasp.response.SecurityResponse;
import com.waqiti.security.rasp.service.SecurityEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * RASP management and monitoring controller
 */
@RestController
@RequestMapping("/api/v1/rasp")
@Tag(name = "RASP Management", description = "Runtime Application Self-Protection management")
@Slf4j
@RequiredArgsConstructor
public class RaspManagementController {

    private final SecurityResponse securityResponse;
    private final SecurityEventService securityEventService;
    private final RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/status")
    @Operation(summary = "Get RASP system status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "ACTIVE");
        status.put("timestamp", LocalDateTime.now());
        status.put("version", "1.0.0");
        
        // Get some basic metrics
        try {
            Set<String> blockedIps = redisTemplate.keys("blocked_ip:*");
            status.put("blockedIpsCount", blockedIps != null ? blockedIps.size() : 0);
        } catch (Exception e) {
            status.put("blockedIpsCount", "ERROR");
        }
        
        return ResponseEntity.ok(status);
    }

    @GetMapping("/blocked-ips")
    @Operation(summary = "Get list of blocked IP addresses")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getBlockedIps() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Set<String> blockedKeys = redisTemplate.keys("blocked_ip:*");
            if (blockedKeys != null) {
                Map<String, Object> blockedIps = new HashMap<>();
                for (String key : blockedKeys) {
                    String ip = key.replace("blocked_ip:", "");
                    Long ttl = redisTemplate.getExpire(key);
                    blockedIps.put(ip, Map.of(
                        "remainingSeconds", ttl,
                        "blockedAt", "Unknown" // Could be enhanced to store block time
                    ));
                }
                response.put("blockedIps", blockedIps);
                response.put("totalCount", blockedIps.size());
            } else {
                response.put("blockedIps", Map.of());
                response.put("totalCount", 0);
            }
        } catch (Exception e) {
            log.error("Error retrieving blocked IPs: ", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to retrieve blocked IPs")
            );
        }
        
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/blocked-ips/{ip}")
    @Operation(summary = "Unblock an IP address")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> unblockIp(@PathVariable String ip) {
        try {
            securityResponse.unblockIpAddress(ip);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "IP address unblocked successfully");
            response.put("ip", ip);
            response.put("timestamp", LocalDateTime.now().toString());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error unblocking IP {}: ", ip, e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to unblock IP address")
            );
        }
    }

    @PostMapping("/block-ip")
    @Operation(summary = "Manually block an IP address")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> blockIp(
            @RequestParam String ip,
            @RequestParam(defaultValue = "15") int durationMinutes,
            @RequestParam(defaultValue = "Manual block") String reason) {
        
        try {
            String blockKey = "blocked_ip:" + ip;
            redisTemplate.opsForValue().set(blockKey, "MANUALLY_BLOCKED", 
                java.time.Duration.ofMinutes(durationMinutes));
            
            // Log the manual block action
            securityEventService.logSecurityMetric("manual_ip_block", 1, 
                Map.of("ip", ip, "duration", String.valueOf(durationMinutes), "reason", reason));
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "IP address blocked successfully");
            response.put("ip", ip);
            response.put("duration", durationMinutes + " minutes");
            response.put("reason", reason);
            response.put("timestamp", LocalDateTime.now().toString());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error blocking IP {}: ", ip, e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to block IP address")
            );
        }
    }

    @GetMapping("/metrics")
    @Operation(summary = "Get RASP security metrics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            // Get various metrics from Redis
            Set<String> attackKeys = redisTemplate.keys("attacks:*");
            Set<String> blockedKeys = redisTemplate.keys("blocked_ip:*");
            Set<String> rateLimitKeys = redisTemplate.keys("minute:*");
            
            metrics.put("activeAttackSources", attackKeys != null ? attackKeys.size() : 0);
            metrics.put("blockedIpAddresses", blockedKeys != null ? blockedKeys.size() : 0);
            metrics.put("rateLimitedClients", rateLimitKeys != null ? rateLimitKeys.size() : 0);
            metrics.put("timestamp", LocalDateTime.now());
            
            // Add more detailed metrics here as needed
            
        } catch (Exception e) {
            log.error("Error retrieving metrics: ", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to retrieve metrics")
            );
        }
        
        return ResponseEntity.ok(metrics);
    }

    @PostMapping("/clear-cache")
    @Operation(summary = "Clear RASP cache (rate limits, blocks, etc.)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> clearCache(@RequestParam(defaultValue = "all") String type) {
        try {
            int deletedKeys = 0;
            
            switch (type.toLowerCase()) {
                case "blocks":
                case "blocked_ips":
                    Set<String> blockedKeys = redisTemplate.keys("blocked_ip:*");
                    if (blockedKeys != null && !blockedKeys.isEmpty()) {
                        deletedKeys = Math.toIntExact(redisTemplate.delete(blockedKeys));
                    }
                    break;
                    
                case "rate_limits":
                    Set<String> rateLimitKeys = redisTemplate.keys("minute:*");
                    rateLimitKeys.addAll(redisTemplate.keys("hour:*"));
                    rateLimitKeys.addAll(redisTemplate.keys("burst:*"));
                    if (!rateLimitKeys.isEmpty()) {
                        deletedKeys = Math.toIntExact(redisTemplate.delete(rateLimitKeys));
                    }
                    break;
                    
                case "attacks":
                    Set<String> attackKeys = redisTemplate.keys("attacks:*");
                    if (attackKeys != null && !attackKeys.isEmpty()) {
                        deletedKeys = Math.toIntExact(redisTemplate.delete(attackKeys));
                    }
                    break;
                    
                case "all":
                default:
                    Set<String> allKeys = redisTemplate.keys("blocked_ip:*");
                    allKeys.addAll(redisTemplate.keys("minute:*"));
                    allKeys.addAll(redisTemplate.keys("hour:*"));
                    allKeys.addAll(redisTemplate.keys("burst:*"));
                    allKeys.addAll(redisTemplate.keys("attacks:*"));
                    if (!allKeys.isEmpty()) {
                        deletedKeys = Math.toIntExact(redisTemplate.delete(allKeys));
                    }
                    break;
            }
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Cache cleared successfully");
            response.put("type", type);
            response.put("deletedKeys", String.valueOf(deletedKeys));
            response.put("timestamp", LocalDateTime.now().toString());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error clearing cache: ", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to clear cache")
            );
        }
    }
}