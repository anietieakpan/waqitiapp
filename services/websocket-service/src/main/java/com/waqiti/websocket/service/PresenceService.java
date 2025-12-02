package com.waqiti.websocket.service;

import com.waqiti.websocket.dto.PresenceUpdate;
import com.waqiti.websocket.dto.UserPresence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String PRESENCE_KEY_PREFIX = "presence:user:";
    private static final String ACTIVE_USERS_KEY = "presence:active";
    private static final String USER_FRIENDS_KEY = "user:friends:";
    private static final Duration PRESENCE_TTL = Duration.ofMinutes(5);
    
    // In-memory cache for quick lookups
    private final Map<String, UserPresence> presenceCache = new ConcurrentHashMap<>();
    
    /**
     * Set user as online
     */
    public void setUserOnline(String userId, String deviceId, String platform) {
        log.info("Setting user {} online from device {} on platform {}", userId, deviceId, platform);
        
        UserPresence presence = UserPresence.builder()
            .userId(userId)
            .isOnline(true)
            .lastSeen(Instant.now())
            .deviceId(deviceId)
            .platform(platform)
            .status("active")
            .build();
        
        // Update Redis
        String key = PRESENCE_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, presence, PRESENCE_TTL);
        redisTemplate.opsForSet().add(ACTIVE_USERS_KEY, userId);
        
        // Update cache
        presenceCache.put(userId, presence);
    }
    
    /**
     * Set user as offline
     */
    public void setUserOffline(String userId, String deviceId) {
        log.info("Setting user {} offline from device {}", userId, deviceId);
        
        UserPresence presence = UserPresence.builder()
            .userId(userId)
            .isOnline(false)
            .lastSeen(Instant.now())
            .deviceId(deviceId)
            .status("offline")
            .build();
        
        // Update Redis
        String key = PRESENCE_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, presence, PRESENCE_TTL);
        redisTemplate.opsForSet().remove(ACTIVE_USERS_KEY, userId);
        
        // Update cache
        presenceCache.put(userId, presence);
    }
    
    /**
     * Get user presence
     */
    public PresenceUpdate getUserPresence(String userId) {
        // Check cache first
        UserPresence cached = presenceCache.get(userId);
        if (cached != null && isPresenceValid(cached)) {
            return toPresenceUpdate(cached);
        }
        
        // Check Redis
        String key = PRESENCE_KEY_PREFIX + userId;
        UserPresence presence = (UserPresence) redisTemplate.opsForValue().get(key);
        
        if (presence == null) {
            presence = UserPresence.builder()
                .userId(userId)
                .isOnline(false)
                .lastSeen(null)
                .status("offline")
                .build();
        }
        
        // Update cache
        presenceCache.put(userId, presence);
        
        return toPresenceUpdate(presence);
    }
    
    /**
     * Get user's friends for presence broadcasting
     */
    public Set<String> getUserFriends(String userId) {
        String key = USER_FRIENDS_KEY + userId;
        Set<Object> friends = redisTemplate.opsForSet().members(key);
        
        if (friends != null) {
            return friends.stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
        }
        
        return new HashSet<>();
    }
    
    /**
     * Get all active users
     */
    public Set<UserPresence> getActiveUsers() {
        Set<Object> activeUserIds = redisTemplate.opsForSet().members(ACTIVE_USERS_KEY);
        
        if (activeUserIds == null || activeUserIds.isEmpty()) {
            return new HashSet<>();
        }
        
        return activeUserIds.stream()
            .map(Object::toString)
            .map(this::getUserPresenceDetails)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }
    
    /**
     * Get active user count
     */
    public long getActiveUserCount() {
        Long count = redisTemplate.opsForSet().size(ACTIVE_USERS_KEY);
        return count != null ? count : 0;
    }
    
    /**
     * Update user status
     */
    public void updateUserStatus(String userId, String status) {
        UserPresence presence = getUserPresenceDetails(userId);
        if (presence != null) {
            presence.setStatus(status);
            presence.setLastSeen(Instant.now());
            
            String key = PRESENCE_KEY_PREFIX + userId;
            redisTemplate.opsForValue().set(key, presence, PRESENCE_TTL);
            presenceCache.put(userId, presence);
        }
    }
    
    /**
     * Heartbeat to keep user online
     */
    public void heartbeat(String userId) {
        UserPresence presence = getUserPresenceDetails(userId);
        if (presence != null && presence.isOnline()) {
            presence.setLastSeen(Instant.now());
            
            String key = PRESENCE_KEY_PREFIX + userId;
            redisTemplate.opsForValue().set(key, presence, PRESENCE_TTL);
            redisTemplate.expire(ACTIVE_USERS_KEY, PRESENCE_TTL);
            
            presenceCache.put(userId, presence);
        }
    }
    
    /**
     * Clean up stale presence data
     */
    public void cleanupStalePresence() {
        log.debug("Cleaning up stale presence data");
        
        Instant staleThreshold = Instant.now().minus(PRESENCE_TTL);
        
        presenceCache.entrySet().removeIf(entry -> {
            UserPresence presence = entry.getValue();
            if (presence.getLastSeen() != null && presence.getLastSeen().isBefore(staleThreshold)) {
                setUserOffline(entry.getKey(), presence.getDeviceId());
                return true;
            }
            return false;
        });
    }
    
    private UserPresence getUserPresenceDetails(String userId) {
        String key = PRESENCE_KEY_PREFIX + userId;
        return (UserPresence) redisTemplate.opsForValue().get(key);
    }
    
    private boolean isPresenceValid(UserPresence presence) {
        if (presence.getLastSeen() == null) {
            return false;
        }
        return presence.getLastSeen().isAfter(Instant.now().minus(PRESENCE_TTL));
    }
    
    private PresenceUpdate toPresenceUpdate(UserPresence presence) {
        return PresenceUpdate.builder()
            .userId(presence.getUserId())
            .isOnline(presence.isOnline())
            .lastSeen(presence.getLastSeen())
            .status(presence.getStatus())
            .build();
    }
}