package com.waqiti.notification.service;

import com.waqiti.notification.domain.DeviceToken;
import com.waqiti.notification.dto.DeviceTokenRequest;
import com.waqiti.notification.dto.DeviceTokenResponse;
import com.waqiti.notification.dto.SendNotificationRequest;
import com.waqiti.notification.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing device tokens for push notifications
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceTokenService {
    
    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationService notificationService;
    
    /**
     * Register a new device token or update existing one
     */
    @Transactional
    public DeviceTokenResponse registerDeviceToken(String userId, DeviceTokenRequest request) {
        log.info("Registering device token for user: {}, device: {}, platform: {}", 
                userId, request.getDeviceId(), request.getPlatform());
        
        request.normalize();
        
        // Check if device already exists for this user
        Optional<DeviceToken> existingToken = deviceTokenRepository
                .findByUserIdAndDeviceId(userId, request.getDeviceId());
        
        DeviceToken deviceToken;
        
        if (existingToken.isPresent()) {
            // Update existing token
            deviceToken = existingToken.get();
            deviceToken.updateToken(request.getToken());
            deviceToken.updateDeviceInfo(
                request.getDeviceName(),
                request.getDeviceModel(),
                request.getOsVersion(),
                request.getAppVersion()
            );
            
            // Update additional fields
            if (request.getManufacturer() != null) {
                deviceToken.setManufacturer(request.getManufacturer());
            }
            if (request.getTimezone() != null) {
                deviceToken.setTimezone(request.getTimezone());
            }
            if (request.getLanguage() != null) {
                deviceToken.setLanguage(request.getLanguage());
            }
            
            log.info("Updated existing device token: {}", deviceToken.getId());
        } else {
            // Create new token
            deviceToken = DeviceToken.create(
                userId,
                request.getToken(),
                request.getPlatformEnum(),
                request.getDeviceId()
            );
            
            deviceToken.setDeviceName(request.getDeviceName());
            deviceToken.setDeviceModel(request.getDeviceModel());
            deviceToken.setOsVersion(request.getOsVersion());
            deviceToken.setAppVersion(request.getAppVersion());
            deviceToken.setManufacturer(request.getManufacturer());
            deviceToken.setTimezone(request.getTimezone());
            deviceToken.setLanguage(request.getLanguage());
            
            log.info("Created new device token for device: {}", request.getDeviceId());
        }
        
        deviceToken = deviceTokenRepository.save(deviceToken);
        
        return DeviceTokenResponse.fromEntity(deviceToken);
    }
    
    /**
     * Update an existing device token
     */
    @Transactional
    public DeviceTokenResponse updateDeviceToken(String userId, DeviceTokenRequest request) {
        log.info("Updating device token for user: {}, device: {}", userId, request.getDeviceId());
        
        request.normalize();
        
        DeviceToken deviceToken = deviceTokenRepository
                .findByUserIdAndDeviceId(userId, request.getDeviceId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Device token not found for user: " + userId + ", device: " + request.getDeviceId()));
        
        // Update token and device info
        deviceToken.updateToken(request.getToken());
        deviceToken.updateDeviceInfo(
            request.getDeviceName(),
            request.getDeviceModel(),
            request.getOsVersion(),
            request.getAppVersion()
        );
        
        // Update additional fields
        if (request.getManufacturer() != null) {
            deviceToken.setManufacturer(request.getManufacturer());
        }
        if (request.getTimezone() != null) {
            deviceToken.setTimezone(request.getTimezone());
        }
        if (request.getLanguage() != null) {
            deviceToken.setLanguage(request.getLanguage());
        }
        
        deviceToken = deviceTokenRepository.save(deviceToken);
        
        log.info("Updated device token: {}", deviceToken.getId());
        
        return DeviceTokenResponse.fromEntity(deviceToken);
    }
    
    /**
     * Get all device tokens for a user
     */
    @Transactional(readOnly = true)
    public List<DeviceTokenResponse> getUserDeviceTokens(String userId) {
        log.info("Getting device tokens for user: {}", userId);
        
        List<DeviceToken> tokens = deviceTokenRepository.findByUserIdOrderByLastUsedDesc(userId);
        
        return tokens.stream()
                .map(DeviceTokenResponse::fromEntity)
                .collect(Collectors.toList());
    }
    
    /**
     * Get active device tokens for a user
     */
    @Transactional(readOnly = true)
    public List<DeviceTokenResponse> getActiveDeviceTokens(String userId) {
        log.info("Getting active device tokens for user: {}", userId);
        
        List<DeviceToken> tokens = deviceTokenRepository.findByUserIdAndActiveOrderByLastUsedDesc(userId, true);
        
        return tokens.stream()
                .map(DeviceTokenResponse::fromEntity)
                .collect(Collectors.toList());
    }
    
    /**
     * Get active device tokens entities for internal use
     */
    @Transactional(readOnly = true)
    public List<DeviceToken> getActiveDeviceTokensForUser(String userId) {
        return deviceTokenRepository.findByUserIdAndActiveOrderByLastUsedDesc(userId, true);
    }
    
    /**
     * Deactivate a specific device token
     */
    @Transactional
    public void deactivateDeviceToken(String userId, String deviceId) {
        log.info("Deactivating device token for user: {}, device: {}", userId, deviceId);
        
        DeviceToken deviceToken = deviceTokenRepository
                .findByUserIdAndDeviceId(userId, deviceId)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Device token not found for user: " + userId + ", device: " + deviceId));
        
        deviceToken.deactivate();
        deviceTokenRepository.save(deviceToken);
        
        log.info("Deactivated device token: {}", deviceToken.getId());
    }
    
    /**
     * Deactivate all device tokens for a user
     */
    @Transactional
    public void deactivateAllDeviceTokens(String userId) {
        log.info("Deactivating all device tokens for user: {}", userId);
        
        List<DeviceToken> activeTokens = deviceTokenRepository.findByUserIdAndActiveOrderByLastUsedDesc(userId, true);
        
        for (DeviceToken token : activeTokens) {
            token.deactivate();
        }
        
        deviceTokenRepository.saveAll(activeTokens);
        
        log.info("Deactivated {} device tokens for user: {}", activeTokens.size(), userId);
    }
    
    /**
     * Send a test notification to a specific device
     */
    public void sendTestNotification(String userId, String deviceId, String message) {
        log.info("Sending test notification to user: {}, device: {}", userId, deviceId);
        
        DeviceToken deviceToken = deviceTokenRepository
                .findByUserIdAndDeviceId(userId, deviceId)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Device token not found for user: " + userId + ", device: " + deviceId));
        
        if (!deviceToken.isActive()) {
            throw new IllegalStateException("Device token is not active");
        }
        
        // Create test notification request
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId(userId)
                .templateCode("test_notification")
                .types(new String[]{"PUSH"})
                .parameters(Map.of(
                    "message", message,
                    "timestamp", LocalDateTime.now().toString()
                ))
                .build();
        
        try {
            notificationService.sendNotification(request);
            
            // Mark device as used
            deviceToken.markAsUsed();
            deviceTokenRepository.save(deviceToken);
            
            log.info("Test notification sent successfully to device: {}", deviceId);
        } catch (Exception e) {
            log.error("Failed to send test notification to device: {}", deviceId, e);
            throw new RuntimeException("Failed to send test notification", e);
        }
    }
    
    /**
     * Clean up expired device tokens for a user
     */
    @Transactional
    public int cleanupExpiredTokens(String userId) {
        log.info("Cleaning up expired tokens for user: {}", userId);
        
        List<DeviceToken> userTokens = deviceTokenRepository.findByUserIdOrderByLastUsedDesc(userId);
        
        List<DeviceToken> expiredTokens = userTokens.stream()
                .filter(DeviceToken::isExpired)
                .collect(Collectors.toList());
        
        if (!expiredTokens.isEmpty()) {
            // Deactivate expired tokens instead of deleting them for audit purposes
            for (DeviceToken token : expiredTokens) {
                token.deactivate();
            }
            
            deviceTokenRepository.saveAll(expiredTokens);
            
            log.info("Deactivated {} expired tokens for user: {}", expiredTokens.size(), userId);
        }
        
        return expiredTokens.size();
    }
    
    /**
     * Scheduled cleanup of expired device tokens (runs daily)
     */
    @Scheduled(cron = "0 0 2 * * *") // Run at 2 AM daily
    @Transactional
    public void scheduledCleanupExpiredTokens() {
        log.info("Starting scheduled cleanup of expired device tokens");
        
        try {
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            
            // Find all expired active tokens
            List<DeviceToken> expiredTokens = deviceTokenRepository
                    .findExpiredActiveTokens(thirtyDaysAgo);
            
            if (!expiredTokens.isEmpty()) {
                // Deactivate expired tokens
                for (DeviceToken token : expiredTokens) {
                    token.deactivate();
                }
                
                deviceTokenRepository.saveAll(expiredTokens);
                
                log.info("Scheduled cleanup deactivated {} expired device tokens", expiredTokens.size());
            } else {
                log.info("No expired device tokens found during scheduled cleanup");
            }
        } catch (Exception e) {
            log.error("Error during scheduled cleanup of expired device tokens", e);
        }
    }
    
    /**
     * Get device token by user and device ID
     */
    @Transactional(readOnly = true)
    public Optional<DeviceToken> getDeviceToken(String userId, String deviceId) {
        return deviceTokenRepository.findByUserIdAndDeviceId(userId, deviceId);
    }
    
    /**
     * Get device token by token value (for internal use)
     */
    @Transactional(readOnly = true)
    public Optional<DeviceToken> getDeviceTokenByToken(String token) {
        return deviceTokenRepository.findByTokenAndActive(token, true);
    }
    
    /**
     * Mark device token as used (updates last used timestamp)
     */
    @Transactional
    public void markTokenAsUsed(String token) {
        Optional<DeviceToken> deviceToken = deviceTokenRepository.findByTokenAndActive(token, true);
        
        if (deviceToken.isPresent()) {
            deviceToken.get().markAsUsed();
            deviceTokenRepository.save(deviceToken.get());
        }
    }
    
    /**
     * Get statistics about device tokens
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDeviceTokenStats(String userId) {
        List<DeviceToken> userTokens = deviceTokenRepository.findByUserIdOrderByLastUsedDesc(userId);
        
        long totalTokens = userTokens.size();
        long activeTokens = userTokens.stream()
                .filter(DeviceToken::isActive)
                .count();
        long expiredTokens = userTokens.stream()
                .filter(DeviceToken::isExpired)
                .count();
        long staleTokens = userTokens.stream()
                .filter(DeviceToken::isStale)
                .count();
        
        Map<String, Long> platformCounts = userTokens.stream()
                .filter(DeviceToken::isActive)
                .collect(Collectors.groupingBy(
                    token -> token.getPlatform().getCode(),
                    Collectors.counting()
                ));
        
        return Map.of(
            "totalTokens", totalTokens,
            "activeTokens", activeTokens,
            "expiredTokens", expiredTokens,
            "staleTokens", staleTokens,
            "platformCounts", platformCounts
        );
    }
}