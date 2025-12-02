package com.waqiti.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.user.entity.NotificationPreference;
import com.waqiti.user.entity.NotificationPreference.NotificationType;
import com.waqiti.user.entity.NotificationPreference.NotificationChannel;
import com.waqiti.user.entity.NotificationPreference.NotificationFrequency;
import com.waqiti.user.repository.NotificationPreferenceRepository;
import com.waqiti.user.dto.notification.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Notification Preference Service
 * 
 * Comprehensive user notification preference management providing:
 * - Multi-channel notification configuration (Email, SMS, Push, In-App)
 * - Notification type preferences (Transaction, Security, Marketing, System)
 * - Frequency controls (Real-time, Daily, Weekly, Never)
 * - Quiet hours configuration
 * - Language and timezone preferences
 * - Notification templates and formatting
 * - Opt-in/opt-out management for regulatory compliance
 * - Preference inheritance and defaults
 * 
 * FEATURES:
 * - Granular control per notification type and channel
 * - Batch preference updates
 * - Preference history and audit trail
 * - Redis caching for high-performance lookups
 * - Template-based default preferences
 * - User segment-based defaults (retail, business, etc.)
 * - Regulatory compliance (GDPR, CAN-SPAM, TCPA)
 * 
 * PERFORMANCE:
 * - Redis caching with 30-minute TTL
 * - Batch operations for multiple preference updates
 * - Async event publishing for preference changes
 * 
 * COMPLIANCE:
 * - All preference changes are logged
 * - Opt-out records are permanent
 * - Marketing consent tracking
 * - Data retention policies
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {
    
    private final NotificationPreferenceRepository preferenceRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String CACHE_KEY_PREFIX = "notif:pref:";
    private static final long CACHE_TTL_MINUTES = 30;
    
    private static final Map<String, PreferenceTemplate> DEFAULT_TEMPLATES = Map.of(
        "RETAIL", new PreferenceTemplate(
            Map.of(
                NotificationType.TRANSACTION_COMPLETED, Set.of(NotificationChannel.EMAIL, NotificationChannel.PUSH),
                NotificationType.SECURITY_ALERT, Set.of(NotificationChannel.EMAIL, NotificationChannel.SMS, NotificationChannel.PUSH),
                NotificationType.BALANCE_LOW, Set.of(NotificationChannel.PUSH),
                NotificationType.LARGE_TRANSACTION, Set.of(NotificationChannel.EMAIL, NotificationChannel.SMS),
                NotificationType.MARKETING, Set.of(NotificationChannel.EMAIL)
            ),
            NotificationFrequency.REAL_TIME,
            LocalTime.of(22, 0),
            LocalTime.of(8, 0)
        ),
        "BUSINESS", new PreferenceTemplate(
            Map.of(
                NotificationType.TRANSACTION_COMPLETED, Set.of(NotificationChannel.EMAIL),
                NotificationType.SECURITY_ALERT, Set.of(NotificationChannel.EMAIL, NotificationChannel.SMS),
                NotificationType.PAYMENT_RECEIVED, Set.of(NotificationChannel.EMAIL),
                NotificationType.LARGE_TRANSACTION, Set.of(NotificationChannel.EMAIL, NotificationChannel.SMS),
                NotificationType.RECONCILIATION_REPORT, Set.of(NotificationChannel.EMAIL)
            ),
            NotificationFrequency.REAL_TIME,
            null,
            null
        )
    );
    
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "notification-preferences", fallbackMethod = "initializeDefaultPreferencesFallback")
    @Retry(name = "notification-preferences")
    public List<NotificationPreference> initializeDefaultPreferences(
            String userId,
            String userType,
            String email,
            String phoneNumber,
            String timezone,
            String language) {
        
        log.info("Initializing default notification preferences: userId={} userType={}", 
                userId, userType);
        
        String effectiveUserType = userType != null ? userType : "RETAIL";
        PreferenceTemplate template = DEFAULT_TEMPLATES.getOrDefault(
            effectiveUserType,
            DEFAULT_TEMPLATES.get("RETAIL")
        );
        
        List<NotificationPreference> preferences = new ArrayList<>();
        
        for (Map.Entry<NotificationType, Set<NotificationChannel>> entry : 
                template.typeChannelMap.entrySet()) {
            
            NotificationType type = entry.getKey();
            Set<NotificationChannel> channels = entry.getValue();
            
            for (NotificationChannel channel : channels) {
                boolean enabled = shouldEnableByDefault(type, channel, email, phoneNumber);
                
                NotificationPreference preference = NotificationPreference.builder()
                    .userId(userId)
                    .notificationType(type)
                    .channel(channel)
                    .enabled(enabled)
                    .frequency(template.defaultFrequency)
                    .quietHoursStart(template.quietHoursStart)
                    .quietHoursEnd(template.quietHoursEnd)
                    .timezone(timezone != null ? timezone : "UTC")
                    .language(language != null ? language : "en")
                    .optInDate(enabled ? LocalDateTime.now() : null)
                    .build();
                
                preferences.add(preference);
            }
        }
        
        preferences = preferenceRepository.saveAll(preferences);
        
        invalidateCache(userId);
        
        publishPreferencesInitializedEvent(userId, preferences.size());
        
        log.info("Initialized {} notification preferences for user: {}", preferences.size(), userId);
        
        return preferences;
    }
    
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "notification-preferences", fallbackMethod = "updatePreferenceFallback")
    @Retry(name = "notification-preferences")
    public NotificationPreference updatePreference(
            String userId,
            NotificationType notificationType,
            NotificationChannel channel,
            boolean enabled,
            String updatedBy) {
        
        log.info("Updating notification preference: userId={} type={} channel={} enabled={}", 
                userId, notificationType, channel, enabled);
        
        Optional<NotificationPreference> existingPref = preferenceRepository
            .findByUserIdAndNotificationTypeAndChannel(userId, notificationType, channel);
        
        NotificationPreference preference;
        
        if (existingPref.isPresent()) {
            preference = existingPref.get();
            
            boolean wasEnabled = preference.isEnabled();
            preference.setEnabled(enabled);
            preference.setUpdatedBy(updatedBy);
            
            if (enabled && !wasEnabled) {
                preference.setOptInDate(LocalDateTime.now());
                preference.setOptOutDate(null);
            } else if (!enabled && wasEnabled) {
                preference.setOptOutDate(LocalDateTime.now());
            }
            
        } else {
            preference = NotificationPreference.builder()
                .userId(userId)
                .notificationType(notificationType)
                .channel(channel)
                .enabled(enabled)
                .frequency(NotificationFrequency.REAL_TIME)
                .timezone("UTC")
                .language("en")
                .optInDate(enabled ? LocalDateTime.now() : null)
                .optOutDate(enabled ? null : LocalDateTime.now())
                .build();
        }
        
        preference = preferenceRepository.save(preference);
        
        invalidateCache(userId);
        
        publishPreferenceChangedEvent(userId, notificationType, channel, enabled);
        
        log.info("Notification preference updated: id={} userId={}", preference.getId(), userId);
        
        return preference;
    }
    
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public NotificationPreference updateFrequency(
            String userId,
            NotificationType notificationType,
            NotificationChannel channel,
            NotificationFrequency frequency) {
        
        log.info("Updating notification frequency: userId={} type={} channel={} frequency={}", 
                userId, notificationType, channel, frequency);
        
        NotificationPreference preference = preferenceRepository
            .findByUserIdAndNotificationTypeAndChannel(userId, notificationType, channel)
            .orElseThrow(() -> new IllegalArgumentException(
                "Notification preference not found for user: " + userId));
        
        preference.setFrequency(frequency);
        preference = preferenceRepository.save(preference);
        
        invalidateCache(userId);
        
        return preference;
    }
    
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void updateQuietHours(
            String userId,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd) {
        
        log.info("Updating quiet hours: userId={} start={} end={}", 
                userId, quietHoursStart, quietHoursEnd);
        
        List<NotificationPreference> preferences = preferenceRepository.findByUserId(userId);
        
        for (NotificationPreference preference : preferences) {
            preference.setQuietHoursStart(quietHoursStart);
            preference.setQuietHoursEnd(quietHoursEnd);
        }
        
        preferenceRepository.saveAll(preferences);
        
        invalidateCache(userId);
    }
    
    @Transactional(readOnly = true)
    @CircuitBreaker(name = "notification-preferences", fallbackMethod = "getPreferencesFallback")
    @Retry(name = "notification-preferences")
    public NotificationPreferencesResponse getPreferences(String userId) {
        log.debug("Getting notification preferences for user: {}", userId);
        
        String cacheKey = CACHE_KEY_PREFIX + userId;
        NotificationPreferencesResponse cached = getCachedPreferences(cacheKey);
        
        if (cached != null) {
            log.debug("Cache hit for notification preferences: userId={}", userId);
            return cached;
        }
        
        List<NotificationPreference> preferences = preferenceRepository.findByUserId(userId);
        
        if (preferences.isEmpty()) {
            log.warn("No notification preferences found for user: {}", userId);
            return NotificationPreferencesResponse.builder()
                .userId(userId)
                .preferences(Collections.emptyList())
                .hasPreferences(false)
                .build();
        }
        
        NotificationPreferencesResponse response = buildPreferencesResponse(userId, preferences);
        
        cachePreferences(cacheKey, response);
        
        return response;
    }
    
    @Transactional(readOnly = true)
    public boolean isNotificationEnabled(
            String userId,
            NotificationType notificationType,
            NotificationChannel channel) {
        
        Optional<NotificationPreference> preference = preferenceRepository
            .findByUserIdAndNotificationTypeAndChannel(userId, notificationType, channel);
        
        return preference.map(NotificationPreference::isEnabled).orElse(false);
    }
    
    @Transactional(readOnly = true)
    public List<NotificationChannel> getEnabledChannels(
            String userId,
            NotificationType notificationType) {
        
        return preferenceRepository
            .findEnabledChannelsByUserIdAndType(userId, notificationType)
            .stream()
            .map(NotificationPreference::getChannel)
            .collect(Collectors.toList());
    }
    
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "notification-preferences", fallbackMethod = "enableAllNotificationsFallback")
    @Retry(name = "notification-preferences")
    public void enableAllNotifications(String userId, String updatedBy) {
        log.info("Enabling all notifications for user: {}", userId);
        
        List<NotificationPreference> preferences = preferenceRepository.findByUserId(userId);
        
        for (NotificationPreference preference : preferences) {
            if (!preference.isEnabled()) {
                preference.setEnabled(true);
                preference.setOptInDate(LocalDateTime.now());
                preference.setOptOutDate(null);
                preference.setUpdatedBy(updatedBy);
            }
        }
        
        preferenceRepository.saveAll(preferences);
        
        invalidateCache(userId);
        
        publishBulkPreferenceChangeEvent(userId, "ENABLED_ALL");
    }
    
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "notification-preferences", fallbackMethod = "disableAllNotificationsFallback")
    @Retry(name = "notification-preferences")
    public void disableAllNotifications(String userId, String reason, String updatedBy) {
        log.info("Disabling all notifications for user: {} reason={}", userId, reason);
        
        List<NotificationPreference> preferences = preferenceRepository.findByUserId(userId);
        
        for (NotificationPreference preference : preferences) {
            if (preference.isEnabled()) {
                preference.setEnabled(false);
                preference.setOptOutDate(LocalDateTime.now());
                preference.setOptOutReason(reason);
                preference.setUpdatedBy(updatedBy);
            }
        }
        
        preferenceRepository.saveAll(preferences);
        
        invalidateCache(userId);
        
        publishBulkPreferenceChangeEvent(userId, "DISABLED_ALL");
    }
    
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void optOutOfMarketing(String userId, String reason) {
        log.info("Opting user out of marketing notifications: userId={}", userId);
        
        List<NotificationPreference> marketingPreferences = preferenceRepository
            .findByUserIdAndNotificationType(userId, NotificationType.MARKETING);
        
        for (NotificationPreference preference : marketingPreferences) {
            preference.setEnabled(false);
            preference.setOptOutDate(LocalDateTime.now());
            preference.setOptOutReason(reason);
        }
        
        preferenceRepository.saveAll(marketingPreferences);
        
        invalidateCache(userId);
    }
    
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void batchUpdatePreferences(
            String userId,
            List<PreferenceUpdateRequest> updates,
            String updatedBy) {
        
        log.info("Batch updating {} preferences for user: {}", updates.size(), userId);
        
        List<NotificationPreference> preferencesToUpdate = new ArrayList<>();
        
        for (PreferenceUpdateRequest update : updates) {
            Optional<NotificationPreference> existingPref = preferenceRepository
                .findByUserIdAndNotificationTypeAndChannel(
                    userId,
                    update.getNotificationType(),
                    update.getChannel()
                );
            
            if (existingPref.isPresent()) {
                NotificationPreference preference = existingPref.get();
                preference.setEnabled(update.isEnabled());
                
                if (update.getFrequency() != null) {
                    preference.setFrequency(update.getFrequency());
                }
                
                preference.setUpdatedBy(updatedBy);
                preferencesToUpdate.add(preference);
            }
        }
        
        preferenceRepository.saveAll(preferencesToUpdate);
        
        invalidateCache(userId);
    }
    
    private boolean shouldEnableByDefault(
            NotificationType type,
            NotificationChannel channel,
            String email,
            String phoneNumber) {
        
        if (channel == NotificationChannel.EMAIL && (email == null || email.isBlank())) {
            return false;
        }
        
        if (channel == NotificationChannel.SMS && (phoneNumber == null || phoneNumber.isBlank())) {
            return false;
        }
        
        if (type == NotificationType.MARKETING) {
            return false;
        }
        
        return true;
    }
    
    private NotificationPreferencesResponse buildPreferencesResponse(
            String userId,
            List<NotificationPreference> preferences) {
        
        List<PreferenceDTO> preferenceDTOs = preferences.stream()
            .map(this::toPreferenceDTO)
            .collect(Collectors.toList());
        
        Map<NotificationType, Long> enabledByType = preferences.stream()
            .filter(NotificationPreference::isEnabled)
            .collect(Collectors.groupingBy(
                NotificationPreference::getNotificationType,
                Collectors.counting()
            ));
        
        Map<NotificationChannel, Long> enabledByChannel = preferences.stream()
            .filter(NotificationPreference::isEnabled)
            .collect(Collectors.groupingBy(
                NotificationPreference::getChannel,
                Collectors.counting()
            ));
        
        return NotificationPreferencesResponse.builder()
            .userId(userId)
            .preferences(preferenceDTOs)
            .hasPreferences(true)
            .totalPreferences(preferences.size())
            .enabledPreferences((int) preferences.stream()
                .filter(NotificationPreference::isEnabled).count())
            .enabledByType(enabledByType)
            .enabledByChannel(enabledByChannel)
            .build();
    }
    
    private PreferenceDTO toPreferenceDTO(NotificationPreference preference) {
        return PreferenceDTO.builder()
            .id(preference.getId().toString())
            .notificationType(preference.getNotificationType())
            .channel(preference.getChannel())
            .enabled(preference.isEnabled())
            .frequency(preference.getFrequency())
            .quietHoursStart(preference.getQuietHoursStart())
            .quietHoursEnd(preference.getQuietHoursEnd())
            .timezone(preference.getTimezone())
            .language(preference.getLanguage())
            .optInDate(preference.getOptInDate())
            .optOutDate(preference.getOptOutDate())
            .build();
    }
    
    private void invalidateCache(String userId) {
        try {
            String cacheKey = CACHE_KEY_PREFIX + userId;
            redisTemplate.delete(cacheKey);
            log.debug("Invalidated cache for user: {}", userId);
        } catch (Exception e) {
            log.error("Error invalidating cache for user: {}", userId, e);
        }
    }
    
    private NotificationPreferencesResponse getCachedPreferences(String cacheKey) {
        try {
            String cachedValue = redisTemplate.opsForValue().get(cacheKey);
            if (cachedValue != null) {
                return objectMapper.readValue(cachedValue, NotificationPreferencesResponse.class);
            }
        } catch (Exception e) {
            log.error("Error retrieving cached preferences", e);
        }
        return null;
    }
    
    private void cachePreferences(String cacheKey, NotificationPreferencesResponse response) {
        try {
            String jsonValue = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(
                cacheKey,
                jsonValue,
                CACHE_TTL_MINUTES,
                TimeUnit.MINUTES
            );
        } catch (Exception e) {
            log.error("Error caching preferences", e);
        }
    }
    
    private void publishPreferencesInitializedEvent(String userId, int count) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "PREFERENCES_INITIALIZED",
                "userId", userId,
                "preferenceCount", count,
                "timestamp", LocalDateTime.now()
            );
            kafkaTemplate.send("notification.preference.events", userId, event);
        } catch (Exception e) {
            log.error("Error publishing preferences initialized event", e);
        }
    }
    
    private void publishPreferenceChangedEvent(
            String userId,
            NotificationType type,
            NotificationChannel channel,
            boolean enabled) {
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "PREFERENCE_CHANGED",
                "userId", userId,
                "notificationType", type.name(),
                "channel", channel.name(),
                "enabled", enabled,
                "timestamp", LocalDateTime.now()
            );
            kafkaTemplate.send("notification.preference.events", userId, event);
        } catch (Exception e) {
            log.error("Error publishing preference changed event", e);
        }
    }
    
    private void publishBulkPreferenceChangeEvent(String userId, String action) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "BULK_PREFERENCE_CHANGE",
                "userId", userId,
                "action", action,
                "timestamp", LocalDateTime.now()
            );
            kafkaTemplate.send("notification.preference.events", userId, event);
        } catch (Exception e) {
            log.error("Error publishing bulk preference change event", e);
        }
    }
    
    private List<NotificationPreference> initializeDefaultPreferencesFallback(
            String userId,
            String userType,
            String email,
            String phoneNumber,
            String timezone,
            String language,
            Exception e) {
        
        log.error("Notification preference service unavailable - defaults not initialized (fallback): userId={}", 
                userId, e);
        
        throw new RuntimeException("Failed to initialize notification preferences", e);
    }
    
    private NotificationPreference updatePreferenceFallback(
            String userId,
            NotificationType notificationType,
            NotificationChannel channel,
            boolean enabled,
            String updatedBy,
            Exception e) {
        
        log.error("Notification preference service unavailable - preference not updated (fallback): userId={}", 
                userId, e);
        
        throw new RuntimeException("Failed to update notification preference", e);
    }
    
    private NotificationPreferencesResponse getPreferencesFallback(String userId, Exception e) {
        log.warn("Notification preference service unavailable - returning empty response (fallback): userId={}", 
                userId, e);
        
        return NotificationPreferencesResponse.builder()
            .userId(userId)
            .preferences(Collections.emptyList())
            .hasPreferences(false)
            .totalPreferences(0)
            .enabledPreferences(0)
            .build();
    }
    
    private void enableAllNotificationsFallback(String userId, String updatedBy, Exception e) {
        log.error("Notification preference service unavailable - notifications not enabled (fallback): userId={}", 
                userId, e);
    }
    
    private void disableAllNotificationsFallback(
            String userId,
            String reason,
            String updatedBy,
            Exception e) {
        
        log.error("Notification preference service unavailable - notifications not disabled (fallback): userId={}", 
                userId, e);
    }
    
    private static class PreferenceTemplate {
        final Map<NotificationType, Set<NotificationChannel>> typeChannelMap;
        final NotificationFrequency defaultFrequency;
        final LocalTime quietHoursStart;
        final LocalTime quietHoursEnd;
        
        PreferenceTemplate(
                Map<NotificationType, Set<NotificationChannel>> typeChannelMap,
                NotificationFrequency defaultFrequency,
                LocalTime quietHoursStart,
                LocalTime quietHoursEnd) {
            this.typeChannelMap = typeChannelMap;
            this.defaultFrequency = defaultFrequency;
            this.quietHoursStart = quietHoursStart;
            this.quietHoursEnd = quietHoursEnd;
        }
    }
}