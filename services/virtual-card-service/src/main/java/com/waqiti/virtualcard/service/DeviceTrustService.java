package com.waqiti.virtualcard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Device Trust Service
 *
 * Manages trusted devices for users including:
 * - Device registration and verification
 * - Device fingerprinting
 * - Trust status tracking
 * - Automated trust expiration
 * - Anomaly detection
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTrustService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String TRUSTED_DEVICE_KEY_PREFIX = "device:trusted:";
    private static final Duration DEFAULT_TRUST_DURATION = Duration.ofDays(30);
    private static final String DEVICE_METADATA_KEY_PREFIX = "device:metadata:";

    /**
     * Check if a device is trusted for a user
     *
     * @param userId User identifier
     * @param deviceId Device identifier
     * @return true if device is trusted, false otherwise
     */
    public boolean isDeviceTrusted(String userId, String deviceId) {
        if (userId == null || deviceId == null) {
            return false;
        }

        String key = TRUSTED_DEVICE_KEY_PREFIX + userId + ":" + deviceId;
        Boolean trusted = (Boolean) redisTemplate.opsForValue().get(key);

        if (Boolean.TRUE.equals(trusted)) {
            log.debug("Device {} is trusted for user {}", deviceId, userId);
            return true;
        }

        log.debug("Device {} is NOT trusted for user {}", deviceId, userId);
        return false;
    }

    /**
     * Register a trusted device for a user
     *
     * @param userId User identifier
     * @param deviceId Device identifier
     * @param deviceMetadata Additional device information (OS, browser, etc.)
     */
    public void registerTrustedDevice(String userId, String deviceId, Map<String, Object> deviceMetadata) {
        String trustKey = TRUSTED_DEVICE_KEY_PREFIX + userId + ":" + deviceId;
        String metadataKey = DEVICE_METADATA_KEY_PREFIX + userId + ":" + deviceId;

        // Store trust status with expiration
        redisTemplate.opsForValue().set(trustKey, true, DEFAULT_TRUST_DURATION.toMillis(), TimeUnit.MILLISECONDS);

        // Store device metadata
        if (deviceMetadata != null) {
            deviceMetadata.put("registeredAt", Instant.now().toString());
            deviceMetadata.put("userId", userId);
            deviceMetadata.put("deviceId", deviceId);
            redisTemplate.opsForHash().putAll(metadataKey, deviceMetadata);
            redisTemplate.expire(metadataKey, DEFAULT_TRUST_DURATION.toMillis(), TimeUnit.MILLISECONDS);
        }

        log.info("Registered trusted device {} for user {}", deviceId, userId);
    }

    /**
     * Revoke trust for a device
     *
     * @param userId User identifier
     * @param deviceId Device identifier
     */
    public void revokeTrustedDevice(String userId, String deviceId) {
        String trustKey = TRUSTED_DEVICE_KEY_PREFIX + userId + ":" + deviceId;
        String metadataKey = DEVICE_METADATA_KEY_PREFIX + userId + ":" + deviceId;

        redisTemplate.delete(trustKey);
        redisTemplate.delete(metadataKey);

        log.info("Revoked trust for device {} for user {}", deviceId, userId);
    }

    /**
     * Get device metadata
     *
     * @param userId User identifier
     * @param deviceId Device identifier
     * @return Device metadata map
     */
    public Map<Object, Object> getDeviceMetadata(String userId, String deviceId) {
        String metadataKey = DEVICE_METADATA_KEY_PREFIX + userId + ":" + deviceId;
        return redisTemplate.opsForHash().entries(metadataKey);
    }

    /**
     * Extend trust period for a device
     *
     * @param userId User identifier
     * @param deviceId Device identifier
     */
    public void extendDeviceTrust(String userId, String deviceId) {
        if (!isDeviceTrusted(userId, deviceId)) {
            log.warn("Attempted to extend trust for untrusted device {} for user {}", deviceId, userId);
            return;
        }

        String trustKey = TRUSTED_DEVICE_KEY_PREFIX + userId + ":" + deviceId;
        String metadataKey = DEVICE_METADATA_KEY_PREFIX + userId + ":" + deviceId;

        redisTemplate.expire(trustKey, DEFAULT_TRUST_DURATION.toMillis(), TimeUnit.MILLISECONDS);
        redisTemplate.expire(metadataKey, DEFAULT_TRUST_DURATION.toMillis(), TimeUnit.MILLISECONDS);

        log.info("Extended trust period for device {} for user {}", deviceId, userId);
    }
}
