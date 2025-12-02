package com.waqiti.ml.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Enterprise Device Tracking Service
 * 
 * Provides comprehensive device fingerprinting and tracking capabilities for fraud detection,
 * including device history, user associations, and anomaly detection.
 * 
 * Features:
 * - Device fingerprint generation and validation
 * - Multi-user device association tracking
 * - Device trust scoring based on history
 * - Anomaly detection for device behavior
 * - Geographic location tracking
 * - Device type and OS version tracking
 * 
 * @author Waqiti ML Team
 * @version 2.0.0
 * @since 2024-01-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTrackingService implements com.waqiti.ml.cache.MLCacheService.DeviceTrackingService {

    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    // Cache prefixes
    private static final String DEVICE_PREFIX = "device:tracking:";
    private static final String USER_DEVICE_PREFIX = "user:devices:";
    
    // Local cache for performance
    private final ConcurrentHashMap<String, DeviceInfo> deviceCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> userDeviceCache = new ConcurrentHashMap<>();

    /**
     * Get the first seen timestamp for a device
     */
    @Override
    public LocalDateTime getFirstSeenTimestamp(String deviceId) {
        try {
            log.debug("Getting first seen timestamp for device: {}", deviceId);
            
            // Check local cache
            DeviceInfo cachedInfo = deviceCache.get(deviceId);
            if (cachedInfo != null) {
                return cachedInfo.getFirstSeen();
            }
            
            // Check Redis
            String redisKey = DEVICE_PREFIX + deviceId;
            Object firstSeenValue = redisTemplate.opsForHash().get(redisKey, "first_seen");
            if (firstSeenValue != null) {
                LocalDateTime firstSeen = LocalDateTime.parse(firstSeenValue.toString());
                updateLocalCache(deviceId, firstSeen);
                return firstSeen;
            }
            
            // Query database
            String sql = "SELECT first_seen FROM device_tracking WHERE device_id = ?";
            List<LocalDateTime> results = jdbcTemplate.query(sql,
                (rs, rowNum) -> rs.getTimestamp("first_seen").toLocalDateTime(),
                deviceId);
            
            if (!results.isEmpty()) {
                LocalDateTime firstSeen = results.get(0);
                
                // Cache in Redis
                redisTemplate.opsForHash().put(redisKey, "first_seen", firstSeen.toString());
                redisTemplate.expire(redisKey, 24, TimeUnit.HOURS);
                
                // Update local cache
                updateLocalCache(deviceId, firstSeen);
                
                return firstSeen;
            }
            
            log.debug("Device {} not found in tracking history", deviceId);
            return null;
            
        } catch (Exception e) {
            log.error("Error getting first seen timestamp for device {}: {}", deviceId, e.getMessage());
            return null;
        }
    }

    /**
     * Record when a device was first seen
     */
    @Override
    @Transactional
    public void recordDeviceFirstSeen(String deviceId, LocalDateTime timestamp) {
        try {
            log.info("Recording first seen for device: {} at {}", deviceId, timestamp);
            
            // Insert into database
            String sql = "INSERT INTO device_tracking (device_id, first_seen, last_seen, trust_score, is_trusted) " +
                        "VALUES (?, ?, ?, ?, ?) ON CONFLICT (device_id) DO NOTHING";
            
            jdbcTemplate.update(sql, deviceId, timestamp, timestamp, 50.0, false);
            
            // Update Redis
            String redisKey = DEVICE_PREFIX + deviceId;
            Map<String, String> deviceData = new HashMap<>();
            deviceData.put("first_seen", timestamp.toString());
            deviceData.put("last_seen", timestamp.toString());
            deviceData.put("trust_score", "50.0");
            deviceData.put("activity_count", "1");
            
            redisTemplate.opsForHash().putAll(redisKey, deviceData);
            redisTemplate.expire(redisKey, 24, TimeUnit.HOURS);
            
            // Update local cache
            updateLocalCache(deviceId, timestamp);
            
            // Audit device registration
            auditDeviceActivity(deviceId, "DEVICE_REGISTERED", timestamp);
            
            log.info("Successfully recorded first seen for device: {}", deviceId);
            
        } catch (Exception e) {
            log.error("Error recording device first seen for {}: {}", deviceId, e.getMessage());
            throw new RuntimeException("Failed to record device", e);
        }
    }

    /**
     * Get all users associated with a device
     */
    @Override
    public Set<String> getAssociatedUsers(String deviceId) {
        try {
            log.debug("Getting associated users for device: {}", deviceId);
            
            // Check local cache
            Set<String> cachedUsers = userDeviceCache.get(deviceId);
            if (cachedUsers != null && !cachedUsers.isEmpty()) {
                return new HashSet<>(cachedUsers);
            }
            
            // Check Redis
            String redisKey = DEVICE_PREFIX + "users:" + deviceId;
            Set<Object> redisUsers = redisTemplate.opsForSet().members(redisKey);
            if (redisUsers != null && !redisUsers.isEmpty()) {
                Set<String> userIds = redisUsers.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());
                
                // Update local cache
                userDeviceCache.put(deviceId, userIds);
                
                return userIds;
            }
            
            // Query database
            String sql = "SELECT DISTINCT user_id FROM device_user_associations WHERE device_id = ? AND is_active = true";
            List<String> userIds = jdbcTemplate.queryForList(sql, String.class, deviceId);
            
            Set<String> associatedUsers = new HashSet<>(userIds);
            
            if (!associatedUsers.isEmpty()) {
                // Cache in Redis
                redisTemplate.opsForSet().add(redisKey, associatedUsers.toArray());
                redisTemplate.expire(redisKey, 6, TimeUnit.HOURS);
                
                // Update local cache
                userDeviceCache.put(deviceId, associatedUsers);
            }
            
            log.debug("Device {} has {} associated users", deviceId, associatedUsers.size());
            return associatedUsers;
            
        } catch (Exception e) {
            log.error("Error getting associated users for device {}: {}", deviceId, e.getMessage());
            return new HashSet<>();
        }
    }

    /**
     * Associate a user with a device
     */
    @Transactional
    public void associateUserWithDevice(String userId, String deviceId, Map<String, Object> context) {
        try {
            log.info("Associating user {} with device {}", userId, deviceId);
            
            // Check if association already exists
            String checkSql = "SELECT COUNT(*) FROM device_user_associations WHERE device_id = ? AND user_id = ?";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, deviceId, userId);
            
            if (count == null || count == 0) {
                // Create new association
                String insertSql = "INSERT INTO device_user_associations (device_id, user_id, first_associated, " +
                                  "last_activity, activity_count, is_active, metadata) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)";
                
                jdbcTemplate.update(insertSql, 
                    deviceId, 
                    userId,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    1,
                    true,
                    convertToJson(context)
                );
            } else {
                // Update existing association
                String updateSql = "UPDATE device_user_associations SET last_activity = ?, activity_count = activity_count + 1, " +
                                  "is_active = true WHERE device_id = ? AND user_id = ?";
                jdbcTemplate.update(updateSql, LocalDateTime.now(), deviceId, userId);
            }
            
            // Update caches
            invalidateUserDeviceCache(deviceId);
            
            // Update device trust score based on user history
            updateDeviceTrustScore(deviceId, userId);
            
            // Audit association
            auditUserDeviceAssociation(userId, deviceId, "ASSOCIATED");
            
            log.info("Successfully associated user {} with device {}", userId, deviceId);
            
        } catch (Exception e) {
            log.error("Error associating user {} with device {}: {}", userId, deviceId, e.getMessage());
            throw new RuntimeException("Failed to associate user with device", e);
        }
    }

    /**
     * Calculate device trust score
     */
    public double calculateDeviceTrustScore(String deviceId) {
        try {
            log.debug("Calculating trust score for device: {}", deviceId);
            
            double trustScore = 50.0; // Base score
            
            // Factor 1: Device age (older devices are more trusted)
            LocalDateTime firstSeen = getFirstSeenTimestamp(deviceId);
            if (firstSeen != null) {
                long daysSinceFirstSeen = java.time.Duration.between(firstSeen, LocalDateTime.now()).toDays();
                if (daysSinceFirstSeen > 90) {
                    trustScore += 15;
                } else if (daysSinceFirstSeen > 30) {
                    trustScore += 10;
                } else if (daysSinceFirstSeen > 7) {
                    trustScore += 5;
                }
            }
            
            // Factor 2: Number of associated users
            Set<String> associatedUsers = getAssociatedUsers(deviceId);
            int userCount = associatedUsers.size();
            if (userCount == 1) {
                trustScore += 10; // Single user device is more trusted
            } else if (userCount <= 3) {
                trustScore += 5; // Shared among family
            } else if (userCount > 10) {
                trustScore -= 20; // Too many users is suspicious
            }
            
            // Factor 3: Check for suspicious patterns
            boolean hasSuspiciousActivity = checkSuspiciousPatterns(deviceId);
            if (hasSuspiciousActivity) {
                trustScore -= 30;
            }
            
            // Factor 4: Successful transaction history
            int successfulTransactions = getSuccessfulTransactionCount(deviceId);
            if (successfulTransactions > 100) {
                trustScore += 15;
            } else if (successfulTransactions > 50) {
                trustScore += 10;
            } else if (successfulTransactions > 10) {
                trustScore += 5;
            }
            
            // Normalize score to 0-100 range
            trustScore = Math.max(0, Math.min(100, trustScore));
            
            // Update trust score in database
            updateDeviceTrustScoreInDb(deviceId, trustScore);
            
            log.debug("Device {} trust score: {}", deviceId, trustScore);
            return trustScore;
            
        } catch (Exception e) {
            log.error("Error calculating trust score for device {}: {}", deviceId, e.getMessage());
            return 50.0; // Return neutral score on error
        }
    }

    /**
     * Track device location change
     */
    @Transactional
    public void trackDeviceLocation(String deviceId, String ipAddress, String country, String city, double latitude, double longitude) {
        try {
            log.debug("Tracking location for device: {} - {}, {}", deviceId, city, country);
            
            // Insert location history
            String sql = "INSERT INTO device_location_history (device_id, ip_address, country, city, latitude, longitude, tracked_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)";
            
            jdbcTemplate.update(sql, deviceId, ipAddress, country, city, latitude, longitude, LocalDateTime.now());
            
            // Check for location anomalies
            boolean isAnomaly = detectLocationAnomaly(deviceId, country, city, latitude, longitude);
            if (isAnomaly) {
                log.warn("Location anomaly detected for device {} - new location: {}, {}", deviceId, city, country);
                flagDeviceForReview(deviceId, "LOCATION_ANOMALY", Map.of(
                    "country", country,
                    "city", city,
                    "coordinates", String.format("%f,%f", latitude, longitude)
                ));
            }
            
            // Update last known location in cache
            String redisKey = DEVICE_PREFIX + "location:" + deviceId;
            Map<String, String> locationData = new HashMap<>();
            locationData.put("country", country);
            locationData.put("city", city);
            locationData.put("latitude", String.valueOf(latitude));
            locationData.put("longitude", String.valueOf(longitude));
            locationData.put("updated_at", LocalDateTime.now().toString());
            
            redisTemplate.opsForHash().putAll(redisKey, locationData);
            redisTemplate.expire(redisKey, 7, TimeUnit.DAYS);
            
        } catch (Exception e) {
            log.error("Error tracking device location for {}: {}", deviceId, e.getMessage());
        }
    }

    /**
     * Get device risk indicators
     */
    public Map<String, Object> getDeviceRiskIndicators(String deviceId) {
        try {
            Map<String, Object> riskIndicators = new HashMap<>();
            
            // Check device age
            LocalDateTime firstSeen = getFirstSeenTimestamp(deviceId);
            if (firstSeen != null) {
                long deviceAgeDays = java.time.Duration.between(firstSeen, LocalDateTime.now()).toDays();
                riskIndicators.put("device_age_days", deviceAgeDays);
                riskIndicators.put("is_new_device", deviceAgeDays < 7);
            }
            
            // Check user associations
            Set<String> users = getAssociatedUsers(deviceId);
            riskIndicators.put("associated_user_count", users.size());
            riskIndicators.put("is_shared_device", users.size() > 1);
            riskIndicators.put("is_public_device", users.size() > 10);
            
            // Check location changes
            int locationChangeCount = getRecentLocationChangeCount(deviceId);
            riskIndicators.put("recent_location_changes", locationChangeCount);
            riskIndicators.put("has_location_anomaly", locationChangeCount > 5);
            
            // Check failed authentication attempts
            int failedAttempts = getRecentFailedAuthAttempts(deviceId);
            riskIndicators.put("recent_failed_auths", failedAttempts);
            riskIndicators.put("has_auth_anomaly", failedAttempts > 10);
            
            // Get trust score
            double trustScore = calculateDeviceTrustScore(deviceId);
            riskIndicators.put("trust_score", trustScore);
            riskIndicators.put("risk_level", determineRiskLevel(trustScore));
            
            // Check if device is flagged
            boolean isFlagged = isDeviceFlagged(deviceId);
            riskIndicators.put("is_flagged", isFlagged);
            
            return riskIndicators;
            
        } catch (Exception e) {
            log.error("Error getting risk indicators for device {}: {}", deviceId, e.getMessage());
            return new HashMap<>();
        }
    }

    // Helper methods

    private void updateLocalCache(String deviceId, LocalDateTime firstSeen) {
        DeviceInfo info = deviceCache.computeIfAbsent(deviceId, k -> new DeviceInfo());
        info.setDeviceId(deviceId);
        info.setFirstSeen(firstSeen);
        info.setLastUpdated(LocalDateTime.now());
    }

    private void invalidateUserDeviceCache(String deviceId) {
        userDeviceCache.remove(deviceId);
        redisTemplate.delete(DEVICE_PREFIX + "users:" + deviceId);
    }

    private void updateDeviceTrustScore(String deviceId, String userId) {
        try {
            double newTrustScore = calculateDeviceTrustScore(deviceId);
            updateDeviceTrustScoreInDb(deviceId, newTrustScore);
        } catch (Exception e) {
            log.error("Error updating trust score for device {}: {}", deviceId, e.getMessage());
        }
    }

    private void updateDeviceTrustScoreInDb(String deviceId, double trustScore) {
        try {
            String sql = "UPDATE device_tracking SET trust_score = ?, last_updated = ? WHERE device_id = ?";
            jdbcTemplate.update(sql, trustScore, LocalDateTime.now(), deviceId);
            
            // Update cache
            redisTemplate.opsForHash().put(DEVICE_PREFIX + deviceId, "trust_score", String.valueOf(trustScore));
            
        } catch (Exception e) {
            log.error("Error updating trust score in DB for device {}: {}", deviceId, e.getMessage());
        }
    }

    private boolean checkSuspiciousPatterns(String deviceId) {
        try {
            // Check for rapid user switching
            String sql = "SELECT COUNT(DISTINCT user_id) FROM device_user_associations " +
                        "WHERE device_id = ? AND last_activity > ?";
            Integer recentUserCount = jdbcTemplate.queryForObject(sql, Integer.class, 
                deviceId, LocalDateTime.now().minusHours(1));
            
            return recentUserCount != null && recentUserCount > 5;
            
        } catch (Exception e) {
            log.error("Error checking suspicious patterns for device {}: {}", deviceId, e.getMessage());
            return false;
        }
    }

    private int getSuccessfulTransactionCount(String deviceId) {
        try {
            String sql = "SELECT COUNT(*) FROM transaction_history WHERE device_id = ? AND status = 'SUCCESS'";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, deviceId);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("Error getting transaction count for device {}: {}", deviceId, e.getMessage());
            return 0;
        }
    }

    private boolean detectLocationAnomaly(String deviceId, String country, String city, double latitude, double longitude) {
        try {
            // Get last known location
            String sql = "SELECT latitude, longitude, tracked_at FROM device_location_history " +
                        "WHERE device_id = ? ORDER BY tracked_at DESC LIMIT 1";
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, deviceId);
            if (results.isEmpty()) {
                return false; // No previous location to compare
            }
            
            Map<String, Object> lastLocation = results.get(0);
            double lastLat = ((Number) lastLocation.get("latitude")).doubleValue();
            double lastLon = ((Number) lastLocation.get("longitude")).doubleValue();
            LocalDateTime lastTrackedAt = (LocalDateTime) lastLocation.get("tracked_at");
            
            // Calculate distance
            double distance = calculateDistance(lastLat, lastLon, latitude, longitude);
            
            // Calculate time difference
            long hoursSinceLastLocation = java.time.Duration.between(lastTrackedAt, LocalDateTime.now()).toHours();
            
            // Check if movement is physically impossible (e.g., 1000km in 1 hour)
            double maxPossibleDistance = hoursSinceLastLocation * 900; // 900 km/h max (airplane speed)
            
            return distance > maxPossibleDistance;
            
        } catch (Exception e) {
            log.error("Error detecting location anomaly for device {}: {}", deviceId, e.getMessage());
            return false;
        }
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Haversine formula for calculating distance between two points
        double earthRadius = 6371; // km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private void flagDeviceForReview(String deviceId, String reason, Map<String, Object> details) {
        try {
            String sql = "INSERT INTO device_review_flags (device_id, flag_reason, details, flagged_at, is_resolved) " +
                        "VALUES (?, ?, ?::jsonb, ?, ?)";
            
            jdbcTemplate.update(sql, deviceId, reason, convertToJson(details), LocalDateTime.now(), false);
            
            // Update device status
            String updateSql = "UPDATE device_tracking SET is_flagged = true, flag_reason = ? WHERE device_id = ?";
            jdbcTemplate.update(updateSql, reason, deviceId);
            
            log.warn("Device {} flagged for review: {}", deviceId, reason);
            
        } catch (Exception e) {
            log.error("Error flagging device {} for review: {}", deviceId, e.getMessage());
        }
    }

    private int getRecentLocationChangeCount(String deviceId) {
        try {
            String sql = "SELECT COUNT(DISTINCT country || ':' || city) FROM device_location_history " +
                        "WHERE device_id = ? AND tracked_at > ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, 
                deviceId, LocalDateTime.now().minusDays(7));
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("Error getting location change count for device {}: {}", deviceId, e.getMessage());
            return 0;
        }
    }

    private int getRecentFailedAuthAttempts(String deviceId) {
        try {
            String sql = "SELECT COUNT(*) FROM authentication_attempts " +
                        "WHERE device_id = ? AND status = 'FAILED' AND attempted_at > ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, 
                deviceId, LocalDateTime.now().minusHours(24));
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("Error getting failed auth attempts for device {}: {}", deviceId, e.getMessage());
            return 0;
        }
    }

    private String determineRiskLevel(double trustScore) {
        if (trustScore >= 80) return "LOW";
        if (trustScore >= 60) return "MEDIUM";
        if (trustScore >= 40) return "HIGH";
        return "CRITICAL";
    }

    private boolean isDeviceFlagged(String deviceId) {
        try {
            String sql = "SELECT is_flagged FROM device_tracking WHERE device_id = ?";
            Boolean isFlagged = jdbcTemplate.queryForObject(sql, Boolean.class, deviceId);
            return isFlagged != null && isFlagged;
        } catch (Exception e) {
            log.error("Error checking if device {} is flagged: {}", deviceId, e.getMessage());
            return false;
        }
    }

    private String convertToJson(Map<String, Object> data) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
        } catch (Exception e) {
            log.error("Error converting to JSON: {}", e.getMessage());
            return "{}";
        }
    }

    private void auditDeviceActivity(String deviceId, String activity, LocalDateTime timestamp) {
        try {
            String sql = "INSERT INTO device_activity_audit (device_id, activity_type, activity_timestamp, details) " +
                        "VALUES (?, ?, ?, ?::jsonb)";
            
            Map<String, Object> details = Map.of(
                "timestamp", timestamp.toString(),
                "activity", activity
            );
            
            jdbcTemplate.update(sql, deviceId, activity, LocalDateTime.now(), convertToJson(details));
            
        } catch (Exception e) {
            log.error("Error auditing device activity: {}", e.getMessage());
        }
    }

    private void auditUserDeviceAssociation(String userId, String deviceId, String action) {
        try {
            String sql = "INSERT INTO user_device_audit (user_id, device_id, action, audit_timestamp) " +
                        "VALUES (?, ?, ?, ?)";
            
            jdbcTemplate.update(sql, userId, deviceId, action, LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("Error auditing user-device association: {}", e.getMessage());
        }
    }

    /**
     * Scheduled cleanup of expired cache entries
     */
    @Scheduled(fixedDelay = 3600000) // Run every hour
    public void cleanupExpiredCache() {
        try {
            log.debug("Running device cache cleanup");
            
            LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
            
            // Clean local cache
            deviceCache.entrySet().removeIf(entry -> 
                entry.getValue().getLastUpdated().isBefore(cutoff)
            );
            
            // Clean user device cache
            userDeviceCache.clear(); // Simply clear as it will be repopulated on demand
            
            log.debug("Device cache cleanup completed");
            
        } catch (Exception e) {
            log.error("Error during cache cleanup: {}", e.getMessage());
        }
    }

    // Inner classes

    private static class DeviceInfo {
        private String deviceId;
        private LocalDateTime firstSeen;
        private LocalDateTime lastUpdated;
        private Map<String, Object> metadata = new HashMap<>();

        // Getters and setters
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        public LocalDateTime getFirstSeen() { return firstSeen; }
        public void setFirstSeen(LocalDateTime firstSeen) { this.firstSeen = firstSeen; }
        public LocalDateTime getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
}