package com.waqiti.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing email bounces and suppression lists
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BounceManagementService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    // In-memory cache for bounce data (in production, use database)
    private final Map<String, BounceRecord> bounceList = new ConcurrentHashMap<>();
    private final Map<String, SuppressedEmail> suppressionList = new ConcurrentHashMap<>();
    private final Map<String, ComplaintRecord> complaintList = new ConcurrentHashMap<>();
    
    private static final String BOUNCE_LIST_KEY = "email:bounce:list";
    private static final String SUPPRESSION_LIST_KEY = "email:suppression:list";
    private static final String COMPLAINT_LIST_KEY = "email:complaint:list";
    private static final Duration BOUNCE_EXPIRY = Duration.ofDays(30);
    private static final Duration SUPPRESSION_EXPIRY = Duration.ofDays(90);
    
    public boolean isInBounceList(String email) {
        if (email == null) return false;
        
        email = email.toLowerCase().trim();
        
        // Check in-memory cache first
        BounceRecord record = bounceList.get(email);
        if (record != null && !record.isExpired()) {
            return true;
        }
        
        // Check Redis cache
        try {
            Boolean inBounceList = (Boolean) redisTemplate.opsForHash().get(BOUNCE_LIST_KEY, email);
            return Boolean.TRUE.equals(inBounceList);
        } catch (Exception e) {
            log.error("Error checking bounce list in Redis for {}: {}", email, e.getMessage());
            return false;
        }
    }
    
    public void addToBounceList(String email, String bounceType, String reason) {
        if (email == null || email.trim().isEmpty()) {
            return;
        }
        
        email = email.toLowerCase().trim();
        
        log.info("Adding email {} to bounce list. Type: {}, Reason: {}", email, bounceType, reason);
        
        BounceRecord record = new BounceRecord();
        record.setEmail(email);
        record.setBounceType(bounceType);
        record.setReason(reason);
        record.setBouncedAt(LocalDateTime.now());
        record.setExpiresAt(LocalDateTime.now().plus(BOUNCE_EXPIRY));
        
        // Add to in-memory cache
        bounceList.put(email, record);
        
        // Add to Redis
        try {
            redisTemplate.opsForHash().put(BOUNCE_LIST_KEY, email, true);
            redisTemplate.expire(BOUNCE_LIST_KEY, BOUNCE_EXPIRY);
        } catch (Exception e) {
            log.error("Error adding to bounce list in Redis for {}: {}", email, e.getMessage());
        }
    }
    
    public void removeFromBounceList(String email) {
        if (email == null) return;
        
        email = email.toLowerCase().trim();
        
        log.info("Removing email {} from bounce list", email);
        
        // Remove from in-memory cache
        bounceList.remove(email);
        
        // Remove from Redis
        try {
            redisTemplate.opsForHash().delete(BOUNCE_LIST_KEY, email);
        } catch (Exception e) {
            log.error("Error removing from bounce list in Redis for {}: {}", email, e.getMessage());
        }
    }
    
    public boolean isSuppressed(String email) {
        if (email == null) return false;
        
        email = email.toLowerCase().trim();
        
        // Check in-memory cache first
        SuppressedEmail record = suppressionList.get(email);
        if (record != null && !record.isExpired()) {
            return true;
        }
        
        // Check Redis cache
        try {
            Boolean suppressed = (Boolean) redisTemplate.opsForHash().get(SUPPRESSION_LIST_KEY, email);
            return Boolean.TRUE.equals(suppressed);
        } catch (Exception e) {
            log.error("Error checking suppression list in Redis for {}: {}", email, e.getMessage());
            return false;
        }
    }
    
    public void suppressEmail(String email) {
        suppressEmail(email, "Manual suppression", "MANUAL");
    }
    
    public void suppressEmail(String email, String reason, String source) {
        if (email == null || email.trim().isEmpty()) {
            return;
        }
        
        email = email.toLowerCase().trim();
        
        log.info("Suppressing email {}. Reason: {}, Source: {}", email, reason, source);
        
        SuppressedEmail record = new SuppressedEmail();
        record.setEmail(email);
        record.setReason(reason);
        record.setSource(source);
        record.setSuppressedAt(LocalDateTime.now());
        record.setExpiresAt(LocalDateTime.now().plus(SUPPRESSION_EXPIRY));
        
        // Add to in-memory cache
        suppressionList.put(email, record);
        
        // Add to Redis
        try {
            redisTemplate.opsForHash().put(SUPPRESSION_LIST_KEY, email, true);
            redisTemplate.expire(SUPPRESSION_LIST_KEY, SUPPRESSION_EXPIRY);
        } catch (Exception e) {
            log.error("Error adding to suppression list in Redis for {}: {}", email, e.getMessage());
        }
    }
    
    public void unsuppressEmail(String email) {
        if (email == null) return;
        
        email = email.toLowerCase().trim();
        
        log.info("Unsuppressing email {}", email);
        
        // Remove from in-memory cache
        suppressionList.remove(email);
        
        // Remove from Redis
        try {
            redisTemplate.opsForHash().delete(SUPPRESSION_LIST_KEY, email);
        } catch (Exception e) {
            log.error("Error removing from suppression list in Redis for {}: {}", email, e.getMessage());
        }
    }
    
    public void recordComplaint(String email, String complaintType, String source) {
        if (email == null || email.trim().isEmpty()) {
            return;
        }
        
        email = email.toLowerCase().trim();
        
        log.warn("Recording complaint for email {}. Type: {}, Source: {}", email, complaintType, source);
        
        ComplaintRecord record = new ComplaintRecord();
        record.setEmail(email);
        record.setComplaintType(complaintType);
        record.setSource(source);
        record.setComplainedAt(LocalDateTime.now());
        
        // Add to in-memory cache
        complaintList.put(email, record);
        
        // Add to Redis
        try {
            redisTemplate.opsForHash().put(COMPLAINT_LIST_KEY, email, record);
        } catch (Exception e) {
            log.error("Error recording complaint in Redis for {}: {}", email, e.getMessage());
        }
        
        // Auto-suppress emails with spam complaints
        if ("spam".equalsIgnoreCase(complaintType)) {
            suppressEmail(email, "Spam complaint received", "COMPLAINT");
        }
    }
    
    public List<BounceRecord> getBounceHistory(String email) {
        // Get bounce history from both cache and persistent storage
        return getBounceHistoryFromStorage(email);
    }
    
    /**
     * Get bounce history from persistent storage
     */
    private List<BounceRecord> getBounceHistoryFromStorage(String email) {
        try {
            String normalizedEmail = email.toLowerCase().trim();
            
            // First check in-memory cache
            BounceRecord cachedRecord = bounceList.get(normalizedEmail);
            
            // In production, this would query the database for complete history
            // For now, return cached record if available
            if (cachedRecord != null) {
                return List.of(cachedRecord);
            }
            
            // In production, this would be:
            // return bounceRepository.findByEmailOrderByBounceTimeDesc(normalizedEmail);
            
            return Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Error retrieving bounce history for {}: {}", email, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    public BounceStatistics getBounceStatistics() {
        BounceStatistics stats = new BounceStatistics();
        
        long totalBounces = bounceList.size();
        long activeBounces = bounceList.values().stream()
            .filter(record -> !record.isExpired())
            .count();
        
        long hardBounces = bounceList.values().stream()
            .filter(record -> "Permanent".equals(record.getBounceType()))
            .count();
        
        long softBounces = bounceList.values().stream()
            .filter(record -> "Temporary".equals(record.getBounceType()))
            .count();
        
        long suppressions = suppressionList.size();
        long activeSuppressions = suppressionList.values().stream()
            .filter(record -> !record.isExpired())
            .count();
        
        long complaints = complaintList.size();
        
        stats.setTotalBounces(totalBounces);
        stats.setActiveBounces(activeBounces);
        stats.setHardBounces(hardBounces);
        stats.setSoftBounces(softBounces);
        stats.setSuppressions(suppressions);
        stats.setActiveSuppressions(activeSuppressions);
        stats.setComplaints(complaints);
        
        return stats;
    }
    
    public void cleanupExpiredRecords() {
        LocalDateTime now = LocalDateTime.now();
        
        // Clean up bounce list
        bounceList.entrySet().removeIf(entry -> entry.getValue().isExpired());
        
        // Clean up suppression list
        suppressionList.entrySet().removeIf(entry -> entry.getValue().isExpired());
        
        log.info("Cleaned up expired bounce and suppression records");
    }
    
    // Inner classes for data models
    
    public static class BounceRecord {
        private String email;
        private String bounceType;
        private String reason;
        private LocalDateTime bouncedAt;
        private LocalDateTime expiresAt;
        
        public boolean isExpired() {
            return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
        }
        
        // Getters and setters
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public String getBounceType() { return bounceType; }
        public void setBounceType(String bounceType) { this.bounceType = bounceType; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        
        public LocalDateTime getBouncedAt() { return bouncedAt; }
        public void setBouncedAt(LocalDateTime bouncedAt) { this.bouncedAt = bouncedAt; }
        
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    }
    
    public static class SuppressedEmail {
        private String email;
        private String reason;
        private String source;
        private LocalDateTime suppressedAt;
        private LocalDateTime expiresAt;
        
        public boolean isExpired() {
            return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
        }
        
        // Getters and setters
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        
        public LocalDateTime getSuppressedAt() { return suppressedAt; }
        public void setSuppressedAt(LocalDateTime suppressedAt) { this.suppressedAt = suppressedAt; }
        
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    }
    
    public static class ComplaintRecord {
        private String email;
        private String complaintType;
        private String source;
        private LocalDateTime complainedAt;
        
        // Getters and setters
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public String getComplaintType() { return complaintType; }
        public void setComplaintType(String complaintType) { this.complaintType = complaintType; }
        
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        
        public LocalDateTime getComplainedAt() { return complainedAt; }
        public void setComplainedAt(LocalDateTime complainedAt) { this.complainedAt = complainedAt; }
    }
    
    public static class BounceStatistics {
        private long totalBounces;
        private long activeBounces;
        private long hardBounces;
        private long softBounces;
        private long suppressions;
        private long activeSuppressions;
        private long complaints;
        
        // Getters and setters
        public long getTotalBounces() { return totalBounces; }
        public void setTotalBounces(long totalBounces) { this.totalBounces = totalBounces; }
        
        public long getActiveBounces() { return activeBounces; }
        public void setActiveBounces(long activeBounces) { this.activeBounces = activeBounces; }
        
        public long getHardBounces() { return hardBounces; }
        public void setHardBounces(long hardBounces) { this.hardBounces = hardBounces; }
        
        public long getSoftBounces() { return softBounces; }
        public void setSoftBounces(long softBounces) { this.softBounces = softBounces; }
        
        public long getSuppressions() { return suppressions; }
        public void setSuppressions(long suppressions) { this.suppressions = suppressions; }
        
        public long getActiveSuppressions() { return activeSuppressions; }
        public void setActiveSuppressions(long activeSuppressions) { this.activeSuppressions = activeSuppressions; }
        
        public long getComplaints() { return complaints; }
        public void setComplaints(long complaints) { this.complaints = complaints; }
    }
}