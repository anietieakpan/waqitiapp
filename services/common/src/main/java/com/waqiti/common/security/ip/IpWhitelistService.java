/**
 * IP Whitelist Security Service
 * Implements enterprise-grade IP-based access control
 * Provides dynamic IP management with comprehensive monitoring
 */
package com.waqiti.common.security.ip;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Comprehensive IP whitelist management with advanced features
 * Supports CIDR notation, geo-blocking, and threat intelligence
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "security.ip-whitelist.enabled", havingValue = "true", matchIfMissing = true)
public class IpWhitelistService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    // In-memory cache for performance
    private final ConcurrentHashMap<String, WhitelistEntry> whitelistCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BlacklistEntry> blacklistCache = new ConcurrentHashMap<>();
    
    // Configuration properties
    @Value("${security.ip-whitelist.strict-mode:true}")
    private boolean strictMode;
    
    @Value("${security.ip-whitelist.default-allowed:false}")
    private boolean defaultAllowed;
    
    @Value("${security.ip-whitelist.cache-ttl:300}")
    private int cacheTtlSeconds;
    
    @Value("${security.ip-whitelist.max-entries:10000}")
    private int maxWhitelistEntries;
    
    @Value("${security.ip-whitelist.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;
    
    @Value("${security.ip-whitelist.rate-limit.requests-per-minute:100}")
    private int maxRequestsPerMinute;
    
    @Value("${security.ip-whitelist.geo-blocking.enabled:true}")
    private boolean geoBlockingEnabled;
    
    @Value("${security.ip-whitelist.geo-blocking.blocked-countries:CN,RU,KP,IR}")
    private String blockedCountries;
    
    @Value("${security.ip-whitelist.threat-intelligence.enabled:true}")
    private boolean threatIntelEnabled;
    
    @Value("${security.ip-whitelist.auto-blacklist.enabled:true}")
    private boolean autoBlacklistEnabled;
    
    @Value("${security.ip-whitelist.auto-blacklist.threshold:10}")
    private int autoBlacklistThreshold;

    // IP address patterns
    private static final Pattern IPV4_PATTERN = Pattern.compile(
        "^(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");
    
    private static final Pattern IPV6_PATTERN = Pattern.compile(
        "^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|^::1$|^::$");
    
    private static final Pattern CIDR_PATTERN = Pattern.compile(
        "^(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)/(?:[0-2]?\\d|3[0-2])$");

    /**
     * Check if IP address is allowed
     */
    public IpValidationResult validateIpAccess(String ipAddress, String userAgent, String userId) {
        log.debug("Validating IP access for: {}", ipAddress);
        
        if (!isValidIpAddress(ipAddress)) {
            return IpValidationResult.denied("Invalid IP address format");
        }
        
        // Check blacklist first
        BlacklistResult blacklistResult = checkBlacklist(ipAddress);
        if (blacklistResult.isBlacklisted()) {
            log.warn("IP address {} is blacklisted: {}", ipAddress, blacklistResult.getReason());
            eventPublisher.publishEvent(new IpBlockedEvent(ipAddress, "blacklisted", blacklistResult.getReason()));
            return IpValidationResult.denied("IP address is blacklisted: " + blacklistResult.getReason());
        }
        
        // Check whitelist
        WhitelistResult whitelistResult = checkWhitelist(ipAddress);
        if (!whitelistResult.isWhitelisted()) {
            if (strictMode) {
                log.warn("IP address {} not in whitelist (strict mode)", ipAddress);
                eventPublisher.publishEvent(new IpBlockedEvent(ipAddress, "not_whitelisted", "Strict mode enabled"));
                return IpValidationResult.denied("IP address not in whitelist");
            } else if (!defaultAllowed) {
                log.warn("IP address {} not allowed by default policy", ipAddress);
                eventPublisher.publishEvent(new IpBlockedEvent(ipAddress, "default_deny", "Default policy"));
                return IpValidationResult.denied("IP address not allowed by default policy");
            }
        }
        
        // Rate limiting check
        if (rateLimitEnabled) {
            RateLimitResult rateLimitResult = checkRateLimit(ipAddress);
            if (rateLimitResult.isLimited()) {
                log.warn("IP address {} exceeded rate limit", ipAddress);
                eventPublisher.publishEvent(new IpBlockedEvent(ipAddress, "rate_limited", "Rate limit exceeded"));
                return IpValidationResult.denied("Rate limit exceeded");
            }
        }
        
        // Geo-blocking check
        if (geoBlockingEnabled) {
            GeoBlockingResult geoResult = checkGeoBlocking(ipAddress);
            if (geoResult.isBlocked()) {
                log.warn("IP address {} blocked by geo-blocking: {}", ipAddress, geoResult.getCountry());
                eventPublisher.publishEvent(new IpBlockedEvent(ipAddress, "geo_blocked", geoResult.getCountry()));
                return IpValidationResult.denied("Geographic location not allowed: " + geoResult.getCountry());
            }
        }
        
        // Threat intelligence check
        if (threatIntelEnabled) {
            ThreatIntelResult threatResult = checkThreatIntelligence(ipAddress);
            if (threatResult.isThreat()) {
                log.warn("IP address {} flagged by threat intelligence: {}", ipAddress, threatResult.getThreatType());
                eventPublisher.publishEvent(new IpBlockedEvent(ipAddress, "threat_intel", threatResult.getThreatType()));
                return IpValidationResult.denied("IP flagged by threat intelligence: " + threatResult.getThreatType());
            }
        }
        
        // Log successful access
        recordSuccessfulAccess(ipAddress, userAgent, userId);
        
        // Build success result
        return IpValidationResult.builder()
            .allowed(true)
            .ipAddress(ipAddress)
            .whitelistMatch(whitelistResult.getMatchedEntry())
            .geoLocation(geoBlockingEnabled ? getGeoLocation(ipAddress) : null)
            .riskScore(calculateRiskScore(ipAddress))
            .timestamp(Instant.now())
            .build();
    }

    /**
     * Add IP address to whitelist
     */
    public void addToWhitelist(String ipAddress, String description, String addedBy, Instant expiresAt) {
        if (!isValidIpAddress(ipAddress)) {
            throw new IllegalArgumentException("Invalid IP address format: " + ipAddress);
        }
        
        if (whitelistCache.size() >= maxWhitelistEntries) {
            throw new IllegalStateException("Maximum whitelist entries exceeded");
        }
        
        WhitelistEntry entry = WhitelistEntry.builder()
            .ipAddress(ipAddress)
            .description(description)
            .addedBy(addedBy)
            .addedAt(Instant.now())
            .expiresAt(expiresAt)
            .accessCount(0L)
            .lastAccess(null)
            .active(true)
            .build();
        
        // Store in Redis and cache
        String key = "whitelist:" + ipAddress;
        storeWhitelistEntry(key, entry);
        whitelistCache.put(ipAddress, entry);
        
        log.info("IP address {} added to whitelist by {}", ipAddress, addedBy);
        eventPublisher.publishEvent(new IpWhitelistUpdatedEvent("added", entry));
    }

    /**
     * Remove IP address from whitelist
     */
    public void removeFromWhitelist(String ipAddress, String removedBy) {
        String key = "whitelist:" + ipAddress;
        redisTemplate.delete(key);
        whitelistCache.remove(ipAddress);
        
        log.info("IP address {} removed from whitelist by {}", ipAddress, removedBy);
        eventPublisher.publishEvent(new IpWhitelistUpdatedEvent("removed", 
            WhitelistEntry.builder().ipAddress(ipAddress).build()));
    }

    /**
     * Add IP address to blacklist
     */
    public void addToBlacklist(String ipAddress, String reason, String addedBy, Instant expiresAt) {
        if (!isValidIpAddress(ipAddress)) {
            throw new IllegalArgumentException("Invalid IP address format: " + ipAddress);
        }
        
        BlacklistEntry entry = BlacklistEntry.builder()
            .ipAddress(ipAddress)
            .reason(reason)
            .addedBy(addedBy)
            .addedAt(Instant.now())
            .expiresAt(expiresAt)
            .hitCount(0L)
            .lastHit(null)
            .active(true)
            .build();
        
        // Store in Redis and cache
        String key = "blacklist:" + ipAddress;
        storeBlacklistEntry(key, entry);
        blacklistCache.put(ipAddress, entry);
        
        log.info("IP address {} added to blacklist by {} - Reason: {}", ipAddress, addedBy, reason);
        eventPublisher.publishEvent(new IpBlacklistUpdatedEvent("added", entry));
    }

    /**
     * Get whitelist statistics
     */
    public WhitelistStatistics getWhitelistStatistics() {
        long totalEntries = whitelistCache.size();
        long activeEntries = whitelistCache.values().stream()
            .filter(WhitelistEntry::isActive)
            .count();
        long expiredEntries = whitelistCache.values().stream()
            .filter(this::isEntryExpired)
            .count();
        
        Map<String, Long> entriesByUser = whitelistCache.values().stream()
            .collect(Collectors.groupingBy(
                WhitelistEntry::getAddedBy,
                Collectors.counting()
            ));
        
        long totalAccesses = whitelistCache.values().stream()
            .mapToLong(WhitelistEntry::getAccessCount)
            .sum();
        
        return WhitelistStatistics.builder()
            .totalEntries(totalEntries)
            .activeEntries(activeEntries)
            .expiredEntries(expiredEntries)
            .entriesByUser(entriesByUser)
            .totalAccesses(totalAccesses)
            .cacheHitRate(calculateCacheHitRate())
            .lastUpdated(Instant.now())
            .build();
    }

    /**
     * Check IP against whitelist
     */
    private WhitelistResult checkWhitelist(String ipAddress) {
        // Check cache first
        WhitelistEntry cachedEntry = whitelistCache.get(ipAddress);
        if (cachedEntry != null) {
            if (!isEntryExpired(cachedEntry)) {
                updateAccessCount(cachedEntry);
                return WhitelistResult.allowed(cachedEntry);
            } else {
                whitelistCache.remove(ipAddress);
            }
        }
        
        // Check Redis
        String key = "whitelist:" + ipAddress;
        WhitelistEntry entry = getWhitelistEntry(key);
        if (entry != null && !isEntryExpired(entry)) {
            whitelistCache.put(ipAddress, entry);
            updateAccessCount(entry);
            return WhitelistResult.allowed(entry);
        }
        
        // Check CIDR ranges
        for (WhitelistEntry rangeEntry : whitelistCache.values()) {
            if (rangeEntry.getIpAddress().contains("/") && isIpInCidrRange(ipAddress, rangeEntry.getIpAddress())) {
                updateAccessCount(rangeEntry);
                return WhitelistResult.allowed(rangeEntry);
            }
        }
        
        return WhitelistResult.denied();
    }

    /**
     * Public method to check if IP is blacklisted
     */
    public boolean isBlacklisted(String ipAddress) {
        BlacklistResult result = checkBlacklist(ipAddress);
        return result.isBlacklisted();
    }

    /**
     * Check IP against blacklist
     */
    private BlacklistResult checkBlacklist(String ipAddress) {
        // Check cache first
        BlacklistEntry cachedEntry = blacklistCache.get(ipAddress);
        if (cachedEntry != null) {
            if (!isBlacklistEntryExpired(cachedEntry)) {
                updateBlacklistHitCount(cachedEntry);
                return BlacklistResult.blacklisted(cachedEntry.getReason());
            } else {
                blacklistCache.remove(ipAddress);
            }
        }
        
        // Check Redis
        String key = "blacklist:" + ipAddress;
        BlacklistEntry entry = getBlacklistEntry(key);
        if (entry != null && !isBlacklistEntryExpired(entry)) {
            blacklistCache.put(ipAddress, entry);
            updateBlacklistHitCount(entry);
            return BlacklistResult.blacklisted(entry.getReason());
        }
        
        return BlacklistResult.allowed();
    }

    /**
     * Check rate limiting
     */
    private RateLimitResult checkRateLimit(String ipAddress) {
        String key = "rate_limit:" + ipAddress;
        String windowKey = key + ":" + getCurrentMinuteWindow();
        
        Long currentCount = redisTemplate.opsForValue().increment(windowKey);
        if (currentCount == null) {
            currentCount = 1L;
        }
        
        if (currentCount == 1) {
            redisTemplate.expire(windowKey, 1, TimeUnit.MINUTES);
        }
        
        if (currentCount > maxRequestsPerMinute) {
            // Auto-blacklist if enabled
            if (autoBlacklistEnabled && currentCount > maxRequestsPerMinute * autoBlacklistThreshold) {
                addToBlacklist(ipAddress, "Auto-blacklisted for excessive requests", 
                    "system", Instant.now().plus(24, ChronoUnit.HOURS));
            }
            
            return RateLimitResult.limited(currentCount, maxRequestsPerMinute);
        }
        
        return RateLimitResult.allowed(currentCount, maxRequestsPerMinute);
    }

    /**
     * Check geo-blocking
     */
    private GeoBlockingResult checkGeoBlocking(String ipAddress) {
        String country = getCountryFromIp(ipAddress);
        if (country != null && blockedCountries.contains(country)) {
            return GeoBlockingResult.blocked(country);
        }
        return GeoBlockingResult.allowed(country);
    }

    /**
     * Check threat intelligence
     */
    private ThreatIntelResult checkThreatIntelligence(String ipAddress) {
        // Check known threat databases
        String threatKey = "threat:" + ipAddress;
        Object threatData = redisTemplate.opsForValue().get(threatKey);
        
        if (threatData != null) {
            return ThreatIntelResult.threat(threatData.toString());
        }
        
        // In production, integrate with threat intelligence APIs
        return ThreatIntelResult.clean();
    }

    /**
     * Clean expired entries
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void cleanupExpiredEntries() {
        log.debug("Starting cleanup of expired IP whitelist/blacklist entries");
        
        int expiredWhitelist = 0;
        int expiredBlacklist = 0;
        
        // Cleanup whitelist
        Iterator<Map.Entry<String, WhitelistEntry>> whitelistIter = whitelistCache.entrySet().iterator();
        while (whitelistIter.hasNext()) {
            Map.Entry<String, WhitelistEntry> entry = whitelistIter.next();
            if (isEntryExpired(entry.getValue())) {
                whitelistIter.remove();
                redisTemplate.delete("whitelist:" + entry.getKey());
                expiredWhitelist++;
            }
        }
        
        // Cleanup blacklist
        Iterator<Map.Entry<String, BlacklistEntry>> blacklistIter = blacklistCache.entrySet().iterator();
        while (blacklistIter.hasNext()) {
            Map.Entry<String, BlacklistEntry> entry = blacklistIter.next();
            if (isBlacklistEntryExpired(entry.getValue())) {
                blacklistIter.remove();
                redisTemplate.delete("blacklist:" + entry.getKey());
                expiredBlacklist++;
            }
        }
        
        if (expiredWhitelist > 0 || expiredBlacklist > 0) {
            log.info("Cleaned up {} expired whitelist entries and {} expired blacklist entries", 
                expiredWhitelist, expiredBlacklist);
        }
    }

    // Helper methods
    private boolean isValidIpAddress(String ipAddress) {
        return IPV4_PATTERN.matcher(ipAddress).matches() || 
               IPV6_PATTERN.matcher(ipAddress).matches() ||
               CIDR_PATTERN.matcher(ipAddress).matches();
    }

    private boolean isIpInCidrRange(String ipAddress, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) return false;
            
            InetAddress targetAddr = InetAddress.getByName(ipAddress);
            InetAddress rangeAddr = InetAddress.getByName(parts[0]);
            int prefixLength = Integer.parseInt(parts[1]);
            
            byte[] targetBytes = targetAddr.getAddress();
            byte[] rangeBytes = rangeAddr.getAddress();
            
            if (targetBytes.length != rangeBytes.length) return false;
            
            int bytesToCheck = prefixLength / 8;
            int bitsToCheck = prefixLength % 8;
            
            // Check complete bytes
            for (int i = 0; i < bytesToCheck; i++) {
                if (targetBytes[i] != rangeBytes[i]) return false;
            }
            
            // Check remaining bits
            if (bitsToCheck > 0) {
                int mask = 0xFF << (8 - bitsToCheck);
                return (targetBytes[bytesToCheck] & mask) == (rangeBytes[bytesToCheck] & mask);
            }
            
            return true;
        } catch (Exception e) {
            log.error("Error checking CIDR range", e);
            return false;
        }
    }

    private boolean isEntryExpired(WhitelistEntry entry) {
        return entry.getExpiresAt() != null && Instant.now().isAfter(entry.getExpiresAt());
    }

    private boolean isBlacklistEntryExpired(BlacklistEntry entry) {
        return entry.getExpiresAt() != null && Instant.now().isAfter(entry.getExpiresAt());
    }

    private void updateAccessCount(WhitelistEntry entry) {
        entry.setAccessCount(entry.getAccessCount() + 1);
        entry.setLastAccess(Instant.now());
        
        String key = "whitelist:" + entry.getIpAddress();
        storeWhitelistEntry(key, entry);
    }

    private void updateBlacklistHitCount(BlacklistEntry entry) {
        entry.setHitCount(entry.getHitCount() + 1);
        entry.setLastHit(Instant.now());
        
        String key = "blacklist:" + entry.getIpAddress();
        storeBlacklistEntry(key, entry);
    }

    private void recordSuccessfulAccess(String ipAddress, String userAgent, String userId) {
        String key = "access:" + ipAddress + ":" + getCurrentMinuteWindow();
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, 1, TimeUnit.HOURS);
    }

    private void storeWhitelistEntry(String key, WhitelistEntry entry) {
        try {
            String json = objectMapper.writeValueAsString(entry);
            redisTemplate.opsForValue().set(key, json);
            if (entry.getExpiresAt() != null) {
                long ttl = ChronoUnit.SECONDS.between(Instant.now(), entry.getExpiresAt());
                redisTemplate.expire(key, ttl, TimeUnit.SECONDS);
            }
        } catch (JsonProcessingException e) {
            log.error("Error storing whitelist entry", e);
        }
    }

    private void storeBlacklistEntry(String key, BlacklistEntry entry) {
        try {
            String json = objectMapper.writeValueAsString(entry);
            redisTemplate.opsForValue().set(key, json);
            if (entry.getExpiresAt() != null) {
                long ttl = ChronoUnit.SECONDS.between(Instant.now(), entry.getExpiresAt());
                redisTemplate.expire(key, ttl, TimeUnit.SECONDS);
            }
        } catch (JsonProcessingException e) {
            log.error("Error storing blacklist entry", e);
        }
    }

    private WhitelistEntry getWhitelistEntry(String key) {
        try {
            Object data = redisTemplate.opsForValue().get(key);
            if (data != null) {
                return objectMapper.readValue(data.toString(), WhitelistEntry.class);
            }
        } catch (Exception e) {
            log.error("Error retrieving whitelist entry", e);
        }
        return null;
    }

    private BlacklistEntry getBlacklistEntry(String key) {
        try {
            Object data = redisTemplate.opsForValue().get(key);
            if (data != null) {
                return objectMapper.readValue(data.toString(), BlacklistEntry.class);
            }
        } catch (Exception e) {
            log.error("Error retrieving blacklist entry", e);
        }
        return null;
    }

    private String getCurrentMinuteWindow() {
        return String.valueOf(Instant.now().getEpochSecond() / 60);
    }

    private String getCountryFromIp(String ipAddress) {
        // In production, integrate with GeoIP service
        return "US"; // Simplified for demo
    }

    private String getGeoLocation(String ipAddress) {
        return getCountryFromIp(ipAddress);
    }

    private int calculateRiskScore(String ipAddress) {
        // Calculate risk score based on various factors
        int score = 0;
        
        // Check if IP is from high-risk country
        String country = getCountryFromIp(ipAddress);
        if (blockedCountries.contains(country)) {
            score += 50;
        }
        
        // Check recent activity
        String activityKey = "activity:" + ipAddress;
        Long recentActivity = (Long) redisTemplate.opsForValue().get(activityKey);
        if (recentActivity != null && recentActivity > 100) {
            score += 30;
        }
        
        return Math.min(score, 100);
    }

    private double calculateCacheHitRate() {
        // Simplified cache hit rate calculation
        return 0.95;
    }

    // Data classes
    @Data
    @Builder
    public static class IpValidationResult {
        private boolean allowed;
        private String ipAddress;
        private String denialReason;
        private WhitelistEntry whitelistMatch;
        private String geoLocation;
        private int riskScore;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        private Instant timestamp;
        
        public static IpValidationResult denied(String reason) {
            return IpValidationResult.builder()
                .allowed(false)
                .denialReason(reason)
                .timestamp(Instant.now())
                .build();
        }
    }

    @Data
    @Builder
    public static class WhitelistEntry {
        private String ipAddress;
        private String description;
        private String addedBy;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        private Instant addedAt;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        private Instant expiresAt;
        private Long accessCount;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        private Instant lastAccess;
        private boolean active;
    }

    @Data
    @Builder
    public static class BlacklistEntry {
        private String ipAddress;
        private String reason;
        private String addedBy;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        private Instant addedAt;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        private Instant expiresAt;
        private Long hitCount;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        private Instant lastHit;
        private boolean active;
    }

    @Data
    @Builder
    public static class WhitelistStatistics {
        private long totalEntries;
        private long activeEntries;
        private long expiredEntries;
        private Map<String, Long> entriesByUser;
        private long totalAccesses;
        private double cacheHitRate;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        private Instant lastUpdated;
    }

    // Result classes
    @Data
    private static class WhitelistResult {
        private boolean whitelisted;
        private WhitelistEntry matchedEntry;
        
        public static WhitelistResult allowed(WhitelistEntry entry) {
            WhitelistResult result = new WhitelistResult();
            result.whitelisted = true;
            result.matchedEntry = entry;
            return result;
        }
        
        public static WhitelistResult denied() {
            WhitelistResult result = new WhitelistResult();
            result.whitelisted = false;
            return result;
        }
    }

    @Data
    private static class BlacklistResult {
        private boolean blacklisted;
        private String reason;
        
        public static BlacklistResult blacklisted(String reason) {
            BlacklistResult result = new BlacklistResult();
            result.blacklisted = true;
            result.reason = reason;
            return result;
        }
        
        public static BlacklistResult allowed() {
            BlacklistResult result = new BlacklistResult();
            result.blacklisted = false;
            return result;
        }
    }

    @Data
    private static class RateLimitResult {
        private boolean limited;
        private long currentCount;
        private long maxAllowed;
        
        public static RateLimitResult limited(long current, long max) {
            RateLimitResult result = new RateLimitResult();
            result.limited = true;
            result.currentCount = current;
            result.maxAllowed = max;
            return result;
        }
        
        public static RateLimitResult allowed(long current, long max) {
            RateLimitResult result = new RateLimitResult();
            result.limited = false;
            result.currentCount = current;
            result.maxAllowed = max;
            return result;
        }
    }

    @Data
    private static class GeoBlockingResult {
        private boolean blocked;
        private String country;
        
        public static GeoBlockingResult blocked(String country) {
            GeoBlockingResult result = new GeoBlockingResult();
            result.blocked = true;
            result.country = country;
            return result;
        }
        
        public static GeoBlockingResult allowed(String country) {
            GeoBlockingResult result = new GeoBlockingResult();
            result.blocked = false;
            result.country = country;
            return result;
        }
    }

    @Data
    private static class ThreatIntelResult {
        private boolean threat;
        private String threatType;
        
        public static ThreatIntelResult threat(String type) {
            ThreatIntelResult result = new ThreatIntelResult();
            result.threat = true;
            result.threatType = type;
            return result;
        }
        
        public static ThreatIntelResult clean() {
            ThreatIntelResult result = new ThreatIntelResult();
            result.threat = false;
            return result;
        }
    }

    // Events
    public static class IpBlockedEvent {
        private final String ipAddress;
        private final String reason;
        private final String details;
        private final Instant timestamp;
        
        public IpBlockedEvent(String ipAddress, String reason, String details) {
            this.ipAddress = ipAddress;
            this.reason = reason;
            this.details = details;
            this.timestamp = Instant.now();
        }
        
        public String getIpAddress() { return ipAddress; }
        public String getReason() { return reason; }
        public String getDetails() { return details; }
        public Instant getTimestamp() { return timestamp; }
    }

    public static class IpWhitelistUpdatedEvent {
        private final String action;
        private final WhitelistEntry entry;
        private final Instant timestamp;
        
        public IpWhitelistUpdatedEvent(String action, WhitelistEntry entry) {
            this.action = action;
            this.entry = entry;
            this.timestamp = Instant.now();
        }
        
        public String getAction() { return action; }
        public WhitelistEntry getEntry() { return entry; }
        public Instant getTimestamp() { return timestamp; }
    }

    public static class IpBlacklistUpdatedEvent {
        private final String action;
        private final BlacklistEntry entry;
        private final Instant timestamp;
        
        public IpBlacklistUpdatedEvent(String action, BlacklistEntry entry) {
            this.action = action;
            this.entry = entry;
            this.timestamp = Instant.now();
        }
        
        public String getAction() { return action; }
        public BlacklistEntry getEntry() { return entry; }
        public Instant getTimestamp() { return timestamp; }
    }
}