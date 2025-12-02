package com.waqiti.common.user;

import com.waqiti.common.notification.NotificationChannel;
import com.waqiti.common.cache.CacheService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Enterprise-grade User Preference Service
 * 
 * Manages comprehensive user preferences for notifications, privacy settings,
 * communication channels, and personalization options. Provides high-performance
 * caching, real-time updates, and compliance features for production environments.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserPreferenceService {

    private final JdbcTemplate jdbcTemplate;
    private final CacheService cacheService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // In-memory cache for frequently accessed preferences
    private final Map<String, UserPreferences> preferencesCache = new ConcurrentHashMap<>();
    
    @Value("${waqiti.preferences.cache-ttl:3600}")
    private int cacheTtlSeconds;
    
    @Value("${waqiti.preferences.default-timezone:UTC}")
    private String defaultTimezone;

    /**
     * Get notification channels for a specific user and event type
     */
    @CircuitBreaker(name = "user-preferences", fallbackMethod = "getNotificationChannelsFallback")
    @Retry(name = "user-preferences")
    @Bulkhead(name = "user-preferences")
    public Set<NotificationChannel> getNotificationChannels(String userId, String eventType) {
        log.debug("Getting notification channels for user: {}, eventType: {}", userId, eventType);

        try {
            // Check cache first
            String cacheKey = buildCacheKey(userId, "notification_channels", eventType);
            Set<NotificationChannel> cached = cacheService.get(cacheKey, Set.class);
            
            if (cached != null) {
                return cached;
            }

            // Load from database
            String sql = """
                SELECT nc.channel_name, nc.enabled, nc.priority
                FROM user_notification_preferences unp
                JOIN notification_channels nc ON unp.preference_id = nc.preference_id
                WHERE unp.user_id = ? 
                AND (unp.event_type = ? OR unp.event_type = 'ALL')
                AND nc.enabled = true
                AND unp.active = true
                ORDER BY nc.priority ASC
                """;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, userId, eventType);
            
            Set<NotificationChannel> channels = results.stream()
                .map(row -> NotificationChannel.valueOf(row.get("channel_name").toString().toUpperCase()))
                .collect(Collectors.toSet());

            // Apply user-specific overrides
            channels = applyChannelOverrides(userId, eventType, channels);
            
            // Apply compliance restrictions
            channels = applyComplianceRestrictions(userId, eventType, channels);
            
            // If no specific preferences found, use defaults
            if (channels.isEmpty()) {
                channels = getDefaultNotificationChannels(userId, eventType);
            }

            // Cache the result
            cacheService.put(cacheKey, channels, Duration.ofSeconds(cacheTtlSeconds));
            
            return channels;

        } catch (Exception e) {
            log.error("Failed to get notification channels for user: {}", userId, e);
            return getDefaultNotificationChannels(userId, eventType);
        }
    }

    /**
     * Update user notification preferences
     */
    @Transactional
    public UserPreferenceUpdateResult updateNotificationPreferences(String userId, 
                                                                   String eventType,
                                                                   Set<NotificationChannel> channels,
                                                                   Map<String, Object> settings) {
        log.info("Updating notification preferences for user: {}, eventType: {}", userId, eventType);

        try {
            // Validate user exists and has permissions
            validateUserPermissions(userId);
            
            // Validate channels are supported
            validateNotificationChannels(channels);
            
            // Begin transaction
            UUID preferenceId = UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now();

            // Insert or update user notification preferences
            String upsertSql = """
                INSERT INTO user_notification_preferences 
                (preference_id, user_id, event_type, active, created_at, updated_at)
                VALUES (?, ?, ?, true, ?, ?)
                ON DUPLICATE KEY UPDATE 
                preference_id = VALUES(preference_id),
                active = true,
                updated_at = VALUES(updated_at)
                """;

            jdbcTemplate.update(upsertSql, preferenceId, userId, eventType, now, now);

            // Delete existing channel preferences for this event type
            String deleteSql = """
                DELETE FROM notification_channels 
                WHERE preference_id IN (
                    SELECT preference_id FROM user_notification_preferences 
                    WHERE user_id = ? AND event_type = ?
                )
                """;

            jdbcTemplate.update(deleteSql, userId, eventType);

            // Insert new channel preferences
            String channelSql = """
                INSERT INTO notification_channels 
                (preference_id, channel_name, enabled, priority, settings, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

            int priority = 1;
            for (NotificationChannel channel : channels) {
                Map<String, Object> channelSettings = extractChannelSettings(channel, settings);
                
                jdbcTemplate.update(channelSql, 
                    preferenceId, 
                    channel.name(), 
                    true, 
                    priority++,
                    convertToJson(channelSettings),
                    now
                );
            }

            // Update user's last preference change timestamp
            updateLastPreferenceChange(userId);

            // Invalidate cache
            invalidateUserPreferenceCache(userId);

            // Publish preference change event
            publishPreferenceChangeEvent(userId, eventType, channels);

            log.info("Successfully updated notification preferences for user: {}", userId);

            return UserPreferenceUpdateResult.builder()
                .userId(userId)
                .eventType(eventType)
                .status(PreferenceUpdateStatus.SUCCESS)
                .updatedChannels(channels)
                .updatedAt(now)
                .build();

        } catch (Exception e) {
            log.error("Failed to update notification preferences for user: {}", userId, e);
            
            return UserPreferenceUpdateResult.builder()
                .userId(userId)
                .eventType(eventType)
                .status(PreferenceUpdateStatus.FAILED)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    /**
     * Get user's webhook URL for notifications
     */
    public String getWebhookUrl(String userId) {
        log.debug("Getting webhook URL for user: {}", userId);

        try {
            String cacheKey = buildCacheKey(userId, "webhook_url");
            String cached = cacheService.get(cacheKey, String.class);
            
            if (cached != null) {
                return cached;
            }

            String sql = """
                SELECT webhook_url, active
                FROM user_webhook_preferences
                WHERE user_id = ? 
                AND active = true
                ORDER BY created_at DESC
                LIMIT 1
                """;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, userId);
            
            if (!results.isEmpty()) {
                String webhookUrl = (String) results.get(0).get("webhook_url");
                cacheService.put(cacheKey, webhookUrl, Duration.ofSeconds(cacheTtlSeconds));
                return webhookUrl;
            }

            return null;

        } catch (Exception e) {
            log.error("Failed to get webhook URL for user: {}", userId, e);
            return null;
        }
    }

    /**
     * Get comprehensive user preferences
     */
    public UserPreferences getUserPreferences(String userId) {
        log.debug("Getting comprehensive preferences for user: {}", userId);

        try {
            // Check cache first
            String cacheKey = buildCacheKey(userId, "all_preferences");
            UserPreferences cached = preferencesCache.get(cacheKey);
            
            if (cached != null && !cached.isExpired()) {
                return cached;
            }

            // Load from database
            UserPreferences preferences = loadUserPreferencesFromDatabase(userId);
            
            // Cache the result
            preferences.setCachedAt(LocalDateTime.now());
            preferencesCache.put(cacheKey, preferences);
            cacheService.put(cacheKey, preferences, Duration.ofSeconds(cacheTtlSeconds));
            
            return preferences;

        } catch (Exception e) {
            log.error("Failed to get user preferences: {}", userId, e);
            return getDefaultUserPreferences(userId);
        }
    }

    /**
     * Update user's timezone preference
     */
    @Transactional
    public void updateTimezone(String userId, String timezone) {
        log.info("Updating timezone for user: {} to {}", userId, timezone);

        try {
            // Validate timezone
            ZoneId.of(timezone); // Throws exception if invalid

            String sql = """
                INSERT INTO user_profile_preferences 
                (user_id, preference_key, preference_value, updated_at)
                VALUES (?, 'timezone', ?, ?)
                ON DUPLICATE KEY UPDATE 
                preference_value = VALUES(preference_value),
                updated_at = VALUES(updated_at)
                """;

            jdbcTemplate.update(sql, userId, timezone, LocalDateTime.now());
            
            // Invalidate cache
            invalidateUserPreferenceCache(userId);
            
            // Publish event
            publishPreferenceChangeEvent(userId, "timezone", timezone);

        } catch (Exception e) {
            log.error("Failed to update timezone for user: {}", userId, e);
            throw new UserPreferenceException("Failed to update timezone", e);
        }
    }

    /**
     * Get user's language preference
     */
    public String getLanguagePreference(String userId) {
        try {
            String sql = """
                SELECT preference_value
                FROM user_profile_preferences
                WHERE user_id = ? AND preference_key = 'language'
                """;

            List<String> results = jdbcTemplate.queryForList(sql, String.class, userId);
            
            return results.isEmpty() ? "en" : results.get(0);

        } catch (Exception e) {
            log.error("Failed to get language preference for user: {}", userId, e);
            return "en"; // Default to English
        }
    }

    /**
     * Update communication frequency preferences
     */
    @Transactional
    public void updateCommunicationFrequency(String userId, CommunicationFrequency frequency) {
        log.info("Updating communication frequency for user: {} to {}", userId, frequency);

        try {
            String sql = """
                INSERT INTO user_communication_preferences 
                (user_id, frequency_type, max_daily_notifications, max_weekly_notifications,
                 quiet_hours_start, quiet_hours_end, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE 
                frequency_type = VALUES(frequency_type),
                max_daily_notifications = VALUES(max_daily_notifications),
                max_weekly_notifications = VALUES(max_weekly_notifications),
                quiet_hours_start = VALUES(quiet_hours_start),
                quiet_hours_end = VALUES(quiet_hours_end),
                updated_at = VALUES(updated_at)
                """;

            jdbcTemplate.update(sql, 
                userId,
                frequency.getType(),
                frequency.getMaxDailyNotifications(),
                frequency.getMaxWeeklyNotifications(),
                frequency.getQuietHoursStart(),
                frequency.getQuietHoursEnd(),
                LocalDateTime.now()
            );

            // Invalidate cache
            invalidateUserPreferenceCache(userId);

        } catch (Exception e) {
            log.error("Failed to update communication frequency for user: {}", userId, e);
            throw new UserPreferenceException("Failed to update communication frequency", e);
        }
    }

    /**
     * Check if user is within quiet hours
     */
    public boolean isInQuietHours(String userId) {
        try {
            UserPreferences preferences = getUserPreferences(userId);
            CommunicationFrequency frequency = preferences.getCommunicationFrequency();
            
            if (frequency == null || frequency.getQuietHoursStart() == null || 
                frequency.getQuietHoursEnd() == null) {
                return false;
            }

            LocalDateTime now = LocalDateTime.now(ZoneId.of(preferences.getTimezone()));
            int currentHour = now.getHour();
            
            int startHour = frequency.getQuietHoursStart();
            int endHour = frequency.getQuietHoursEnd();
            
            // Handle overnight quiet hours (e.g., 22:00 to 08:00)
            if (startHour > endHour) {
                return currentHour >= startHour || currentHour < endHour;
            } else {
                return currentHour >= startHour && currentHour < endHour;
            }

        } catch (Exception e) {
            log.error("Failed to check quiet hours for user: {}", userId, e);
            return false;
        }
    }

    /**
     * Get privacy preferences
     */
    public PrivacyPreferences getPrivacyPreferences(String userId) {
        try {
            String sql = """
                SELECT data_sharing_enabled, marketing_emails_enabled, 
                       analytics_tracking_enabled, third_party_sharing_enabled,
                       data_retention_days
                FROM user_privacy_preferences
                WHERE user_id = ?
                """;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, userId);
            
            if (results.isEmpty()) {
                return getDefaultPrivacyPreferences();
            }

            Map<String, Object> row = results.get(0);
            return PrivacyPreferences.builder()
                .dataSharingEnabled((Boolean) row.get("data_sharing_enabled"))
                .marketingEmailsEnabled((Boolean) row.get("marketing_emails_enabled"))
                .analyticsTrackingEnabled((Boolean) row.get("analytics_tracking_enabled"))
                .thirdPartySharingEnabled((Boolean) row.get("third_party_sharing_enabled"))
                .dataRetentionDays((Integer) row.get("data_retention_days"))
                .build();

        } catch (Exception e) {
            log.error("Failed to get privacy preferences for user: {}", userId, e);
            return getDefaultPrivacyPreferences();
        }
    }

    // Private helper methods

    private Set<NotificationChannel> applyChannelOverrides(String userId, String eventType, 
                                                          Set<NotificationChannel> channels) {
        // Apply user-specific channel overrides based on account type, preferences, etc.
        try {
            String sql = """
                SELECT channel_override, action
                FROM user_channel_overrides
                WHERE user_id = ? AND (event_type = ? OR event_type = 'ALL')
                AND active = true
                """;

            List<Map<String, Object>> overrides = jdbcTemplate.queryForList(sql, userId, eventType);
            
            Set<NotificationChannel> result = new HashSet<>(channels);
            
            for (Map<String, Object> override : overrides) {
                NotificationChannel channel = NotificationChannel.valueOf(
                    override.get("channel_override").toString().toUpperCase());
                String action = override.get("action").toString();
                
                if ("REMOVE".equals(action)) {
                    result.remove(channel);
                } else if ("ADD".equals(action)) {
                    result.add(channel);
                }
            }
            
            return result;

        } catch (Exception e) {
            log.warn("Failed to apply channel overrides for user: {}", userId, e);
            return channels;
        }
    }

    private Set<NotificationChannel> applyComplianceRestrictions(String userId, String eventType, 
                                                               Set<NotificationChannel> channels) {
        // Apply compliance-based restrictions (GDPR, CCPA, etc.)
        try {
            PrivacyPreferences privacy = getPrivacyPreferences(userId);
            
            Set<NotificationChannel> result = new HashSet<>(channels);
            
            // If marketing emails disabled, remove EMAIL for non-transactional events
            if (!privacy.isMarketingEmailsEnabled() && isMarketingEvent(eventType)) {
                result.remove(NotificationChannel.EMAIL);
            }
            
            // If third-party sharing disabled, remove external channels
            if (!privacy.isThirdPartySharingEnabled()) {
                result.remove(NotificationChannel.WEBHOOK);
            }
            
            return result;

        } catch (Exception e) {
            log.warn("Failed to apply compliance restrictions for user: {}", userId, e);
            return channels;
        }
    }

    private Set<NotificationChannel> getDefaultNotificationChannels(String userId, String eventType) {
        // Return sensible defaults based on event type
        return switch (eventType.toLowerCase()) {
            case "transaction_rollback", "fraud_alert", "account_freeze" -> 
                Set.of(NotificationChannel.PUSH, NotificationChannel.EMAIL, NotificationChannel.SMS);
            case "payment_received", "payment_sent" -> 
                Set.of(NotificationChannel.PUSH, NotificationChannel.IN_APP);
            case "weekly_summary", "monthly_statement" -> 
                Set.of(NotificationChannel.EMAIL);
            default -> Set.of(NotificationChannel.PUSH, NotificationChannel.IN_APP);
        };
    }

    private UserPreferences loadUserPreferencesFromDatabase(String userId) {
        // Load comprehensive user preferences from database
        UserPreferences.UserPreferencesBuilder builder = UserPreferences.builder()
            .userId(userId)
            .timezone(getTimezone(userId))
            .language(getLanguagePreference(userId))
            .privacyPreferences(getPrivacyPreferences(userId))
            .communicationFrequency(getCommunicationFrequency(userId));

        // Load notification preferences for all event types
        Map<String, Set<NotificationChannel>> notificationPreferences = loadNotificationPreferences(userId);
        builder.notificationPreferences(notificationPreferences);

        return builder.build();
    }

    private String getTimezone(String userId) {
        try {
            String sql = """
                SELECT preference_value
                FROM user_profile_preferences
                WHERE user_id = ? AND preference_key = 'timezone'
                """;

            List<String> results = jdbcTemplate.queryForList(sql, String.class, userId);
            
            return results.isEmpty() ? defaultTimezone : results.get(0);

        } catch (Exception e) {
            log.error("Failed to get timezone for user: {}", userId, e);
            return defaultTimezone;
        }
    }

    private CommunicationFrequency getCommunicationFrequency(String userId) {
        try {
            String sql = """
                SELECT frequency_type, max_daily_notifications, max_weekly_notifications,
                       quiet_hours_start, quiet_hours_end
                FROM user_communication_preferences
                WHERE user_id = ?
                """;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, userId);
            
            if (results.isEmpty()) {
                return getDefaultCommunicationFrequency();
            }

            Map<String, Object> row = results.get(0);
            return CommunicationFrequency.builder()
                .type(row.get("frequency_type").toString())
                .maxDailyNotifications((Integer) row.get("max_daily_notifications"))
                .maxWeeklyNotifications((Integer) row.get("max_weekly_notifications"))
                .quietHoursStart((Integer) row.get("quiet_hours_start"))
                .quietHoursEnd((Integer) row.get("quiet_hours_end"))
                .build();

        } catch (Exception e) {
            log.error("Failed to get communication frequency for user: {}", userId, e);
            return getDefaultCommunicationFrequency();
        }
    }

    private Map<String, Set<NotificationChannel>> loadNotificationPreferences(String userId) {
        String sql = """
            SELECT unp.event_type, nc.channel_name
            FROM user_notification_preferences unp
            JOIN notification_channels nc ON unp.preference_id = nc.preference_id
            WHERE unp.user_id = ? AND unp.active = true AND nc.enabled = true
            ORDER BY unp.event_type, nc.priority
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, userId);
        
        Map<String, Set<NotificationChannel>> preferences = new HashMap<>();
        
        for (Map<String, Object> row : results) {
            String eventType = row.get("event_type").toString();
            NotificationChannel channel = NotificationChannel.valueOf(
                row.get("channel_name").toString().toUpperCase());
            
            preferences.computeIfAbsent(eventType, k -> new HashSet<>()).add(channel);
        }
        
        return preferences;
    }

    private void validateUserPermissions(String userId) {
        // Validate user exists and has permission to update preferences
        String sql = "SELECT COUNT(*) FROM users WHERE id = ? AND active = true";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId);
        
        if (count == null || count == 0) {
            throw new UserPreferenceException("User not found or inactive: " + userId);
        }
    }

    private void validateNotificationChannels(Set<NotificationChannel> channels) {
        if (channels == null || channels.isEmpty()) {
            throw new UserPreferenceException("At least one notification channel must be specified");
        }
        
        // Validate each channel is supported
        for (NotificationChannel channel : channels) {
            if (!isSupportedChannel(channel)) {
                throw new UserPreferenceException("Unsupported notification channel: " + channel);
            }
        }
    }

    private boolean isSupportedChannel(NotificationChannel channel) {
        return Set.of(
            NotificationChannel.PUSH,
            NotificationChannel.EMAIL,
            NotificationChannel.SMS,
            NotificationChannel.IN_APP,
            NotificationChannel.WEBHOOK
        ).contains(channel);
    }

    private Map<String, Object> extractChannelSettings(NotificationChannel channel, 
                                                      Map<String, Object> allSettings) {
        String prefix = channel.name().toLowerCase() + ".";
        return allSettings.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(prefix))
            .collect(Collectors.toMap(
                entry -> entry.getKey().substring(prefix.length()),
                Map.Entry::getValue
            ));
    }

    private void updateLastPreferenceChange(String userId) {
        String sql = """
            UPDATE users 
            SET last_preference_update = ?
            WHERE id = ?
            """;
        
        jdbcTemplate.update(sql, LocalDateTime.now(), userId);
    }

    private void invalidateUserPreferenceCache(String userId) {
        // Remove from in-memory cache
        preferencesCache.entrySet().removeIf(entry -> entry.getKey().contains(userId));
        
        // Remove from distributed cache
        List<String> keysToRemove = List.of(
            buildCacheKey(userId, "all_preferences"),
            buildCacheKey(userId, "webhook_url"),
            buildCacheKey(userId, "notification_channels", "*")
        );
        
        keysToRemove.forEach(key -> {
            try {
                cacheService.evict(key);
            } catch (Exception e) {
                log.warn("Failed to evict cache key: {}", key, e);
            }
        });
    }

    private void publishPreferenceChangeEvent(String userId, String eventType, Object newValue) {
        try {
            UserPreferenceChangeEvent event = UserPreferenceChangeEvent.builder()
                .userId(userId)
                .preferenceType(eventType)
                .newValue(newValue)
                .timestamp(LocalDateTime.now())
                .build();

            kafkaTemplate.send("user-preference-changes", event);
            
        } catch (Exception e) {
            log.error("Failed to publish preference change event", e);
        }
    }

    private String buildCacheKey(String userId, String... parts) {
        return "user_prefs:" + userId + ":" + String.join(":", parts);
    }

    private String convertToJson(Map<String, Object> settings) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(settings);
        } catch (Exception e) {
            return "{}";
        }
    }

    private boolean isMarketingEvent(String eventType) {
        return Set.of("weekly_summary", "monthly_statement", "promotional_offer", 
                     "product_update", "newsletter").contains(eventType.toLowerCase());
    }

    private UserPreferences getDefaultUserPreferences(String userId) {
        return UserPreferences.builder()
            .userId(userId)
            .timezone(defaultTimezone)
            .language("en")
            .privacyPreferences(getDefaultPrivacyPreferences())
            .communicationFrequency(getDefaultCommunicationFrequency())
            .notificationPreferences(new HashMap<>())
            .build();
    }

    private PrivacyPreferences getDefaultPrivacyPreferences() {
        return PrivacyPreferences.builder()
            .dataSharingEnabled(false)
            .marketingEmailsEnabled(true)
            .analyticsTrackingEnabled(true)
            .thirdPartySharingEnabled(false)
            .dataRetentionDays(2555) // 7 years default
            .build();
    }

    private CommunicationFrequency getDefaultCommunicationFrequency() {
        return CommunicationFrequency.builder()
            .type("NORMAL")
            .maxDailyNotifications(50)
            .maxWeeklyNotifications(200)
            .quietHoursStart(22)
            .quietHoursEnd(8)
            .build();
    }

    // Fallback method
    public Set<NotificationChannel> getNotificationChannelsFallback(String userId, String eventType, Exception ex) {
        log.error("CIRCUIT_BREAKER: User preference service fallback activated for user: {}", userId, ex);
        return getDefaultNotificationChannels(userId, eventType);
    }

    // Supporting DTOs and Enums

    public enum PreferenceUpdateStatus {
        SUCCESS, FAILED, PARTIAL
    }

    @lombok.Builder
    @lombok.Data
    public static class UserPreferences {
        private String userId;
        private String timezone;
        private String language;
        private PrivacyPreferences privacyPreferences;
        private CommunicationFrequency communicationFrequency;
        private Map<String, Set<NotificationChannel>> notificationPreferences;
        private LocalDateTime cachedAt;
        
        public boolean isExpired() {
            return cachedAt == null || cachedAt.isBefore(LocalDateTime.now().minusMinutes(30));
        }
    }

    @lombok.Builder
    @lombok.Data
    public static class UserPreferenceUpdateResult {
        private String userId;
        private String eventType;
        private PreferenceUpdateStatus status;
        private Set<NotificationChannel> updatedChannels;
        private String errorMessage;
        private LocalDateTime updatedAt;
    }

    @lombok.Builder
    @lombok.Data
    public static class PrivacyPreferences {
        private boolean dataSharingEnabled;
        private boolean marketingEmailsEnabled;
        private boolean analyticsTrackingEnabled;
        private boolean thirdPartySharingEnabled;
        private int dataRetentionDays;
    }

    @lombok.Builder
    @lombok.Data
    public static class CommunicationFrequency {
        private String type; // LOW, NORMAL, HIGH
        private int maxDailyNotifications;
        private int maxWeeklyNotifications;
        private Integer quietHoursStart;
        private Integer quietHoursEnd;
    }

    @lombok.Builder
    @lombok.Data
    public static class UserPreferenceChangeEvent {
        private String userId;
        private String preferenceType;
        private Object newValue;
        private LocalDateTime timestamp;
    }

    // Custom exception
    public static class UserPreferenceException extends RuntimeException {
        public UserPreferenceException(String message) {
            super(message);
        }
        
        public UserPreferenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}