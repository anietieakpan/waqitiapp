package com.waqiti.notification.api;

import com.waqiti.notification.dto.DeviceTokenRequest;
import com.waqiti.notification.dto.DeviceTokenResponse;
import com.waqiti.notification.service.DeviceTokenService;
import com.waqiti.common.ratelimit.RateLimited;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing device tokens for push notifications
 */
@RestController
@RequestMapping("/api/v1/notifications/device-tokens")
@RequiredArgsConstructor
@Slf4j
public class DeviceTokenController {
    
    private final DeviceTokenService deviceTokenService;
    
    /**
     * Register a new device token
     */
    @PostMapping
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 50, refillTokens = 50, refillPeriodMinutes = 5)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DeviceTokenResponse> registerDeviceToken(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody DeviceTokenRequest request) {
        
        log.info("Register device token request received for user: {}, platform: {}", 
                userDetails.getUsername(), request.getPlatform());
        
        String userId = userDetails.getUsername();
        DeviceTokenResponse response = deviceTokenService.registerDeviceToken(userId, request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Update an existing device token
     */
    @PutMapping
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 50, refillTokens = 50, refillPeriodMinutes = 5)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DeviceTokenResponse> updateDeviceToken(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody DeviceTokenRequest request) {
        
        log.info("Update device token request received for user: {}, device: {}", 
                userDetails.getUsername(), request.getDeviceId());
        
        String userId = userDetails.getUsername();
        DeviceTokenResponse response = deviceTokenService.updateDeviceToken(userId, request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get all device tokens for the authenticated user
     */
    @GetMapping
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 100, refillTokens = 100, refillPeriodMinutes = 1)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DeviceTokenResponse>> getUserDeviceTokens(
            @AuthenticationPrincipal UserDetails userDetails) {
        
        log.info("Get device tokens request received for user: {}", userDetails.getUsername());
        
        String userId = userDetails.getUsername();
        List<DeviceTokenResponse> tokens = deviceTokenService.getUserDeviceTokens(userId);
        
        return ResponseEntity.ok(tokens);
    }
    
    /**
     * Get active device tokens for the authenticated user
     */
    @GetMapping("/active")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 100, refillTokens = 100, refillPeriodMinutes = 1)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DeviceTokenResponse>> getActiveDeviceTokens(
            @AuthenticationPrincipal UserDetails userDetails) {
        
        log.info("Get active device tokens request received for user: {}", userDetails.getUsername());
        
        String userId = userDetails.getUsername();
        List<DeviceTokenResponse> tokens = deviceTokenService.getActiveDeviceTokens(userId);
        
        return ResponseEntity.ok(tokens);
    }
    
    /**
     * Deactivate a specific device token
     */
    @DeleteMapping("/{deviceId}")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 20, refillTokens = 20, refillPeriodMinutes = 5)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deactivateDeviceToken(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String deviceId) {
        
        log.info("Deactivate device token request received for user: {}, device: {}", 
                userDetails.getUsername(), deviceId);
        
        String userId = userDetails.getUsername();
        deviceTokenService.deactivateDeviceToken(userId, deviceId);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Deactivate all device tokens for the authenticated user
     */
    @DeleteMapping
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 10)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deactivateAllDeviceTokens(
            @AuthenticationPrincipal UserDetails userDetails) {
        
        log.info("Deactivate all device tokens request received for user: {}", userDetails.getUsername());
        
        String userId = userDetails.getUsername();
        deviceTokenService.deactivateAllDeviceTokens(userId);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Test push notification to a specific device
     */
    @PostMapping("/{deviceId}/test")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 5, refillTokens = 5, refillPeriodMinutes = 15)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> testPushNotification(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "Test notification from Waqiti") String message) {
        
        log.info("Test push notification request received for user: {}, device: {}", 
                userDetails.getUsername(), deviceId);
        
        String userId = userDetails.getUsername();
        deviceTokenService.sendTestNotification(userId, deviceId, message);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Clean up expired device tokens for the authenticated user
     */
    @DeleteMapping("/cleanup")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 5, refillTokens = 5, refillPeriodMinutes = 60)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Integer> cleanupExpiredTokens(
            @AuthenticationPrincipal UserDetails userDetails) {
        
        log.info("Cleanup expired tokens request received for user: {}", userDetails.getUsername());
        
        String userId = userDetails.getUsername();
        int cleanedCount = deviceTokenService.cleanupExpiredTokens(userId);
        
        return ResponseEntity.ok(cleanedCount);
    }
}