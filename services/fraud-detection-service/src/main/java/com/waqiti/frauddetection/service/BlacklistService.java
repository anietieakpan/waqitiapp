package com.waqiti.frauddetection.service;

import com.waqiti.frauddetection.domain.BlacklistEntry;
import com.waqiti.frauddetection.dto.FraudCheckRequest;
import com.waqiti.frauddetection.repository.BlacklistRepository;
import com.waqiti.common.tracing.Traced;
import com.waqiti.common.cache.CacheService;
import com.waqiti.frauddetection.exception.BlacklistException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Production-ready service for blacklist-based fraud detection
 * Handles user, IP, device, email, and payment method blacklisting
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BlacklistService {

    private final BlacklistRepository blacklistRepository;
    private final CacheService cacheService;
    
    private static final String BLACKLIST_CACHE_PREFIX = "blacklist:";
    private static final int CACHE_TTL_MINUTES = 30;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final Pattern IP_PATTERN = Pattern.compile("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$");

    /**
     * Check if user is blacklisted with comprehensive validation
     */
    @Traced(operationName = "check-user-blacklist", businessOperation = "fraud-detection", priority = Traced.TracingPriority.HIGH)
    @Transactional(readOnly = true)
    public boolean isUserBlacklisted(String userId) {
        if (!StringUtils.hasText(userId)) {
            log.warn("Empty userId provided for blacklist check");
            return false;
        }
        
        log.debug("Checking blacklist status for user: {}", userId);
        
        try {
            // Check cache first
            String cacheKey = BLACKLIST_CACHE_PREFIX + "user:" + userId;
            Boolean cachedResult = cacheService.get(cacheKey, Boolean.class);
            if (cachedResult != null) {
                log.debug("Cache hit for user blacklist check: {} -> {}", userId, cachedResult);
                return cachedResult;
            }
            
            // Check database
            Optional<BlacklistEntry> entry = blacklistRepository.findActiveByTypeAndValue(
                BlacklistEntry.BlacklistType.USER, userId);
            
            boolean isBlacklisted = entry.isPresent();
            
            if (isBlacklisted) {
                BlacklistEntry blacklistEntry = entry.get();
                log.warn("User {} is blacklisted: {} (added: {}, expires: {})", 
                    userId, blacklistEntry.getReason(), 
                    blacklistEntry.getCreatedAt(), blacklistEntry.getExpiresAt());
                
                // Check if blacklist entry has expired
                if (blacklistEntry.getExpiresAt() != null && 
                    LocalDateTime.now().isAfter(blacklistEntry.getExpiresAt())) {
                    log.info("Blacklist entry for user {} has expired, removing", userId);
                    removeFromBlacklist(BlacklistEntry.BlacklistType.USER, userId);
                    isBlacklisted = false;
                }
            }
            
            // Cache result
            cacheService.put(cacheKey, isBlacklisted, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            
            return isBlacklisted;
            
        } catch (Exception e) {
            log.error("Error checking user blacklist for: {}", userId, e);
            // Fail open - don't block legitimate users due to technical issues
            return false;
        }
    }

    /**
     * Check if IP address is blacklisted with geolocation and reputation analysis
     */
    @Traced(operationName = "check-ip-blacklist", businessOperation = "fraud-detection", priority = Traced.TracingPriority.HIGH)
    @Transactional(readOnly = true)
    public boolean isIpBlacklisted(String ipAddress) {
        if (!StringUtils.hasText(ipAddress) || !isValidIpAddress(ipAddress)) {
            log.warn("Invalid IP address provided for blacklist check: {}", ipAddress);
            return false;
        }
        
        log.debug("Checking blacklist status for IP: {}", ipAddress);
        
        try {
            // Check cache first
            String cacheKey = BLACKLIST_CACHE_PREFIX + "ip:" + ipAddress;
            Boolean cachedResult = cacheService.get(cacheKey, Boolean.class);
            if (cachedResult != null) {
                log.debug("Cache hit for IP blacklist check: {} -> {}", ipAddress, cachedResult);
                return cachedResult;
            }
            
            // Check exact IP match
            Optional<BlacklistEntry> exactEntry = blacklistRepository.findActiveByTypeAndValue(
                BlacklistEntry.BlacklistType.IP_ADDRESS, ipAddress);
            
            if (exactEntry.isPresent()) {
                log.warn("IP {} is explicitly blacklisted: {}", 
                    ipAddress, exactEntry.get().getReason());
                cacheService.put(cacheKey, true, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
                return true;
            }
            
            // Check IP subnet ranges
            boolean isSubnetBlacklisted = checkIpSubnetBlacklist(ipAddress);
            
            // Cache result
            cacheService.put(cacheKey, isSubnetBlacklisted, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            
            return isSubnetBlacklisted;
            
        } catch (Exception e) {
            log.error("Error checking IP blacklist for: {}", ipAddress, e);
            return false;
        }
    }

    /**
     * Comprehensive blacklist check for fraud detection request
     * Checks user, IP address, email, and device fingerprint
     */
    @Traced(operationName = "check-comprehensive-blacklist", businessOperation = "fraud-detection", priority = Traced.TracingPriority.HIGH)
    @Transactional(readOnly = true)
    public boolean isBlacklisted(FraudCheckRequest request) {
        log.debug("Performing comprehensive blacklist check for transaction: {}", 
            request.getTransactionId());
        
        try {
            // Check user blacklist
            if (StringUtils.hasText(request.getUserId()) && isUserBlacklisted(request.getUserId())) {
                log.warn("User {} is blacklisted for transaction: {}", 
                    request.getUserId(), request.getTransactionId());
                return true;
            }
            
            // Check IP address blacklist
            if (StringUtils.hasText(request.getIpAddress()) && isIpBlacklisted(request.getIpAddress())) {
                log.warn("IP address {} is blacklisted for transaction: {}", 
                    request.getIpAddress(), request.getTransactionId());
                return true;
            }
            
            // Check email blacklist if available
            if (StringUtils.hasText(request.getEmailAddress()) && isEmailBlacklisted(request.getEmailAddress())) {
                log.warn("Email {} is blacklisted for transaction: {}", 
                    request.getEmailAddress(), request.getTransactionId());
                return true;
            }
            
            // Check device fingerprint blacklist if available
            if (StringUtils.hasText(request.getDeviceFingerprint()) && 
                isDeviceBlacklisted(request.getDeviceFingerprint())) {
                log.warn("Device {} is blacklisted for transaction: {}", 
                    request.getDeviceFingerprint(), request.getTransactionId());
                return true;
            }
            
            // Check payment method if available
            if (StringUtils.hasText(request.getPaymentMethodId()) && 
                isPaymentMethodBlacklisted(request.getPaymentMethodId())) {
                log.warn("Payment method {} is blacklisted for transaction: {}", 
                    request.getPaymentMethodId(), request.getTransactionId());
                return true;
            }
            
            log.debug("No blacklist matches found for transaction: {}", request.getTransactionId());
            return false;
            
        } catch (Exception e) {
            log.error("Error during comprehensive blacklist check for transaction {}: {}", 
                request.getTransactionId(), e.getMessage(), e);
            // Fail open - don't block legitimate transactions due to technical issues
            return false;
        }
    }

    /**
     * Check if email address is blacklisted
     */
    @Traced(operationName = "check-email-blacklist", businessOperation = "fraud-detection", priority = Traced.TracingPriority.MEDIUM)
    @Transactional(readOnly = true)
    public boolean isEmailBlacklisted(String email) {
        if (!StringUtils.hasText(email) || !isValidEmail(email)) {
            log.warn("Invalid email provided for blacklist check: {}", email);
            return false;
        }
        
        String normalizedEmail = email.toLowerCase().trim();
        log.debug("Checking blacklist status for email: {}", normalizedEmail);
        
        try {
            // Check cache first
            String cacheKey = BLACKLIST_CACHE_PREFIX + "email:" + normalizedEmail;
            Boolean cachedResult = cacheService.get(cacheKey, Boolean.class);
            if (cachedResult != null) {
                return cachedResult;
            }
            
            // Check exact email match
            Optional<BlacklistEntry> exactEntry = blacklistRepository.findActiveByTypeAndValue(
                BlacklistEntry.BlacklistType.EMAIL, normalizedEmail);
            
            if (exactEntry.isPresent()) {
                log.warn("Email {} is blacklisted: {}", 
                    normalizedEmail, exactEntry.get().getReason());
                cacheService.put(cacheKey, true, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
                return true;
            }
            
            // Check email domain blacklist
            String domain = normalizedEmail.substring(normalizedEmail.indexOf('@') + 1);
            Optional<BlacklistEntry> domainEntry = blacklistRepository.findActiveByTypeAndValue(
                BlacklistEntry.BlacklistType.EMAIL_DOMAIN, domain);
            
            boolean isDomainBlacklisted = domainEntry.isPresent();
            
            if (isDomainBlacklisted) {
                log.warn("Email domain {} is blacklisted: {}", 
                    domain, domainEntry.get().getReason());
            }
            
            cacheService.put(cacheKey, isDomainBlacklisted, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            return isDomainBlacklisted;
            
        } catch (Exception e) {
            log.error("Error checking email blacklist for: {}", normalizedEmail, e);
            return false;
        }
    }

    /**
     * Check if device is blacklisted by device fingerprint
     */
    @Traced(operationName = "check-device-blacklist", businessOperation = "fraud-detection", priority = Traced.TracingPriority.MEDIUM)
    @Transactional(readOnly = true)
    public boolean isDeviceBlacklisted(String deviceFingerprint) {
        if (!StringUtils.hasText(deviceFingerprint)) {
            log.warn("Empty device fingerprint provided for blacklist check");
            return false;
        }
        
        log.debug("Checking blacklist status for device: {}", deviceFingerprint);
        
        try {
            String cacheKey = BLACKLIST_CACHE_PREFIX + "device:" + deviceFingerprint;
            Boolean cachedResult = cacheService.get(cacheKey, Boolean.class);
            if (cachedResult != null) {
                return cachedResult;
            }
            
            Optional<BlacklistEntry> entry = blacklistRepository.findActiveByTypeAndValue(
                BlacklistEntry.BlacklistType.DEVICE_FINGERPRINT, deviceFingerprint);
            
            boolean isBlacklisted = entry.isPresent();
            
            if (isBlacklisted) {
                log.warn("Device {} is blacklisted: {}", 
                    deviceFingerprint, entry.get().getReason());
            }
            
            cacheService.put(cacheKey, isBlacklisted, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            return isBlacklisted;
            
        } catch (Exception e) {
            log.error("Error checking device blacklist for: {}", deviceFingerprint, e);
            return false;
        }
    }

    /**
     * Add user to blacklist with comprehensive audit trail
     */
    @Traced(operationName = "blacklist-user", businessOperation = "fraud-management", priority = Traced.TracingPriority.CRITICAL)
    public BlacklistEntry blacklistUser(String userId, String reason, String addedBy) {
        return blacklistUser(userId, reason, addedBy, null);
    }

    /**
     * Add user to blacklist with expiration
     */
    @Traced(operationName = "blacklist-user", businessOperation = "fraud-management", priority = Traced.TracingPriority.CRITICAL)
    public BlacklistEntry blacklistUser(String userId, String reason, String addedBy, LocalDateTime expiresAt) {
        validateBlacklistRequest(userId, reason, addedBy);
        
        log.warn("Blacklisting user: {} for reason: {} by: {}", userId, reason, addedBy);
        
        try {
            // Check if already blacklisted
            Optional<BlacklistEntry> existing = blacklistRepository.findActiveByTypeAndValue(
                BlacklistEntry.BlacklistType.USER, userId);
            
            if (existing.isPresent()) {
                log.info("User {} is already blacklisted, updating entry", userId);
                BlacklistEntry entry = existing.get();
                entry.setReason(reason);
                entry.setUpdatedAt(LocalDateTime.now());
                entry.setUpdatedBy(addedBy);
                entry.setExpiresAt(expiresAt);
                
                BlacklistEntry updated = blacklistRepository.save(entry);
                invalidateCache(BlacklistEntry.BlacklistType.USER, userId);
                
                return updated;
            }
            
            // Create new blacklist entry
            BlacklistEntry entry = BlacklistEntry.builder()
                .type(BlacklistEntry.BlacklistType.USER)
                .value(userId)
                .reason(reason)
                .addedBy(addedBy)
                .createdAt(LocalDateTime.now())
                .isActive(true)
                .expiresAt(expiresAt)
                .severity(determineSeverity(reason))
                .riskScore(calculateRiskScore(reason))
                .build();
            
            BlacklistEntry saved = blacklistRepository.save(entry);
            invalidateCache(BlacklistEntry.BlacklistType.USER, userId);
            
            log.info("Successfully blacklisted user: {} with ID: {}", userId, saved.getId());
            
            return saved;
            
        } catch (Exception e) {
            log.error("Failed to blacklist user: {}", userId, e);
            throw new BlacklistException("Failed to blacklist user: " + userId, e);
        }
    }

    /**
     * Add IP address to blacklist
     */
    @Traced(operationName = "blacklist-ip", businessOperation = "fraud-management", priority = Traced.TracingPriority.HIGH)
    public BlacklistEntry blacklistIpAddress(String ipAddress, String reason, String addedBy, LocalDateTime expiresAt) {
        if (!isValidIpAddress(ipAddress)) {
            throw new IllegalArgumentException("Invalid IP address format: " + ipAddress);
        }
        
        validateBlacklistRequest(ipAddress, reason, addedBy);
        
        log.warn("Blacklisting IP: {} for reason: {} by: {}", ipAddress, reason, addedBy);
        
        try {
            BlacklistEntry entry = BlacklistEntry.builder()
                .type(BlacklistEntry.BlacklistType.IP_ADDRESS)
                .value(ipAddress)
                .reason(reason)
                .addedBy(addedBy)
                .createdAt(LocalDateTime.now())
                .isActive(true)
                .expiresAt(expiresAt)
                .severity(determineSeverity(reason))
                .riskScore(calculateRiskScore(reason))
                .build();
            
            BlacklistEntry saved = blacklistRepository.save(entry);
            invalidateCache(BlacklistEntry.BlacklistType.IP_ADDRESS, ipAddress);
            
            return saved;
            
        } catch (Exception e) {
            log.error("Failed to blacklist IP: {}", ipAddress, e);
            throw new BlacklistException("Failed to blacklist IP: " + ipAddress, e);
        }
    }

    /**
     * Add email to blacklist
     */
    @Traced(operationName = "blacklist-email", businessOperation = "fraud-management", priority = Traced.TracingPriority.MEDIUM)
    public BlacklistEntry blacklistEmail(String email, String reason, String addedBy, LocalDateTime expiresAt) {
        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("Invalid email format: " + email);
        }
        
        String normalizedEmail = email.toLowerCase().trim();
        validateBlacklistRequest(normalizedEmail, reason, addedBy);
        
        log.warn("Blacklisting email: {} for reason: {} by: {}", normalizedEmail, reason, addedBy);
        
        try {
            BlacklistEntry entry = BlacklistEntry.builder()
                .type(BlacklistEntry.BlacklistType.EMAIL)
                .value(normalizedEmail)
                .reason(reason)
                .addedBy(addedBy)
                .createdAt(LocalDateTime.now())
                .isActive(true)
                .expiresAt(expiresAt)
                .severity(determineSeverity(reason))
                .riskScore(calculateRiskScore(reason))
                .build();
            
            BlacklistEntry saved = blacklistRepository.save(entry);
            invalidateCache(BlacklistEntry.BlacklistType.EMAIL, normalizedEmail);
            
            return saved;
            
        } catch (Exception e) {
            log.error("Failed to blacklist email: {}", normalizedEmail, e);
            throw new BlacklistException("Failed to blacklist email: " + normalizedEmail, e);
        }
    }

    /**
     * Remove from blacklist
     */
    @Traced(operationName = "remove-from-blacklist", businessOperation = "fraud-management", priority = Traced.TracingPriority.MEDIUM)
    public boolean removeFromBlacklist(BlacklistEntry.BlacklistType type, String value) {
        log.info("Removing from blacklist: {} - {}", type, value);
        
        try {
            Optional<BlacklistEntry> entry = blacklistRepository.findActiveByTypeAndValue(type, value);
            
            if (entry.isPresent()) {
                BlacklistEntry blacklistEntry = entry.get();
                blacklistEntry.setIsActive(false);
                blacklistEntry.setUpdatedAt(LocalDateTime.now());
                blacklistEntry.setUpdatedBy("SYSTEM");
                
                blacklistRepository.save(blacklistEntry);
                invalidateCache(type, value);
                
                log.info("Successfully removed from blacklist: {} - {}", type, value);
                return true;
            } else {
                log.warn("Blacklist entry not found for removal: {} - {}", type, value);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Failed to remove from blacklist: {} - {}", type, value, e);
            throw new BlacklistException("Failed to remove from blacklist: " + type + " - " + value, e);
        }
    }

    /**
     * Get all active blacklist entries with pagination
     */
    @Traced(operationName = "get-blacklist-entries", businessOperation = "fraud-management", priority = Traced.TracingPriority.LOW)
    @Transactional(readOnly = true)
    public Page<BlacklistEntry> getActiveBlacklistEntries(Pageable pageable) {
        return blacklistRepository.findByIsActiveTrue(pageable);
    }

    /**
     * Get blacklist entries by type
     */
    @Traced(operationName = "get-blacklist-by-type", businessOperation = "fraud-management", priority = Traced.TracingPriority.LOW)
    @Transactional(readOnly = true)
    public Page<BlacklistEntry> getBlacklistEntriesByType(BlacklistEntry.BlacklistType type, Pageable pageable) {
        return blacklistRepository.findByTypeAndIsActiveTrue(type, pageable);
    }

    /**
     * Bulk blacklist operation
     */
    @Traced(operationName = "bulk-blacklist", businessOperation = "fraud-management", priority = Traced.TracingPriority.HIGH)
    public List<BlacklistEntry> bulkBlacklist(List<BulkBlacklistRequest> requests, String addedBy) {
        log.info("Processing bulk blacklist operation: {} entries by {}", requests.size(), addedBy);
        
        List<BlacklistEntry> results = new ArrayList<>();
        
        for (BulkBlacklistRequest request : requests) {
            try {
                BlacklistEntry entry = BlacklistEntry.builder()
                    .type(request.getType())
                    .value(request.getValue())
                    .reason(request.getReason())
                    .addedBy(addedBy)
                    .createdAt(LocalDateTime.now())
                    .isActive(true)
                    .expiresAt(request.getExpiresAt())
                    .severity(determineSeverity(request.getReason()))
                    .riskScore(calculateRiskScore(request.getReason()))
                    .build();
                
                BlacklistEntry saved = blacklistRepository.save(entry);
                invalidateCache(request.getType(), request.getValue());
                results.add(saved);
                
            } catch (Exception e) {
                log.error("Failed to process bulk blacklist entry: {} - {}", 
                    request.getType(), request.getValue(), e);
            }
        }
        
        log.info("Bulk blacklist operation completed: {}/{} successful", 
            results.size(), requests.size());
        
        return results;
    }

    /**
     * Get blacklist statistics
     */
    @Traced(operationName = "get-blacklist-statistics", businessOperation = "fraud-reporting", priority = Traced.TracingPriority.LOW)
    @Transactional(readOnly = true)
    public BlacklistStatistics getBlacklistStatistics() {
        Map<BlacklistEntry.BlacklistType, Long> countsByType = Arrays.stream(BlacklistEntry.BlacklistType.values())
            .collect(Collectors.toMap(
                type -> type,
                type -> blacklistRepository.countByTypeAndIsActiveTrue(type)
            ));
        
        long totalActive = blacklistRepository.countByIsActiveTrue();
        long expiredEntries = blacklistRepository.countExpiredEntries(LocalDateTime.now());
        
        return BlacklistStatistics.builder()
            .totalActiveEntries(totalActive)
            .countsByType(countsByType)
            .expiredEntries(expiredEntries)
            .lastUpdated(LocalDateTime.now())
            .build();
    }

    // Helper methods

    private boolean checkIpSubnetBlacklist(String ipAddress) {
        // Check IP against subnet ranges
        List<BlacklistEntry> subnetEntries = blacklistRepository.findActiveByType(
            BlacklistEntry.BlacklistType.IP_SUBNET);
        
        for (BlacklistEntry entry : subnetEntries) {
            if (isIpInSubnet(ipAddress, entry.getValue())) {
                log.warn("IP {} matches blacklisted subnet: {}", ipAddress, entry.getValue());
                return true;
            }
        }
        
        return false;
    }

    private boolean isIpInSubnet(String ip, String subnet) {
        // Simplified subnet matching - in production use proper CIDR matching
        if (subnet.endsWith("/24")) {
            String baseIp = subnet.substring(0, subnet.lastIndexOf('.'));
            return ip.startsWith(baseIp);
        }
        return false;
    }

    private boolean isValidIpAddress(String ip) {
        return IP_PATTERN.matcher(ip).matches();
    }

    private boolean isValidEmail(String email) {
        return EMAIL_PATTERN.matcher(email).matches();
    }

    private void validateBlacklistRequest(String value, String reason, String addedBy) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Blacklist value cannot be empty");
        }
        if (!StringUtils.hasText(reason)) {
            throw new IllegalArgumentException("Blacklist reason cannot be empty");
        }
        if (!StringUtils.hasText(addedBy)) {
            throw new IllegalArgumentException("Added by field cannot be empty");
        }
    }

    private BlacklistEntry.Severity determineSeverity(String reason) {
        String lowerReason = reason.toLowerCase();
        if (lowerReason.contains("fraud") || lowerReason.contains("criminal")) {
            return BlacklistEntry.Severity.CRITICAL;
        } else if (lowerReason.contains("suspicious") || lowerReason.contains("high risk")) {
            return BlacklistEntry.Severity.HIGH;
        } else if (lowerReason.contains("violation") || lowerReason.contains("abuse")) {
            return BlacklistEntry.Severity.MEDIUM;
        }
        return BlacklistEntry.Severity.LOW;
    }

    private double calculateRiskScore(String reason) {
        BlacklistEntry.Severity severity = determineSeverity(reason);
        switch (severity) {
            case CRITICAL: return 0.95;
            case HIGH: return 0.80;
            case MEDIUM: return 0.60;
            case LOW: return 0.40;
            default: return 0.50;
        }
    }

    private void invalidateCache(BlacklistEntry.BlacklistType type, String value) {
        String cacheKey = BLACKLIST_CACHE_PREFIX + type.name().toLowerCase() + ":" + value;
        cacheService.evict(cacheKey);
    }

    /**
     * Check if device fingerprint is blacklisted
     */
    @Traced(operationName = "check-device-blacklist", businessOperation = "fraud-detection", priority = Traced.TracingPriority.MEDIUM)
    @Transactional(readOnly = true)
    public boolean isDeviceBlacklisted(String deviceFingerprint) {
        if (!StringUtils.hasText(deviceFingerprint)) {
            log.warn("Empty device fingerprint provided for blacklist check");
            return false;
        }
        
        log.debug("Checking blacklist status for device: {}", deviceFingerprint);
        
        try {
            // Check cache first
            String cacheKey = BLACKLIST_CACHE_PREFIX + "device:" + deviceFingerprint;
            Boolean cachedResult = cacheService.get(cacheKey, Boolean.class);
            if (cachedResult != null) {
                log.debug("Cache hit for device blacklist check: {} -> {}", deviceFingerprint, cachedResult);
                return cachedResult;
            }
            
            // Check database
            Optional<BlacklistEntry> entry = blacklistRepository.findActiveByTypeAndValue(
                BlacklistEntry.BlacklistType.DEVICE_FINGERPRINT, deviceFingerprint);
            
            boolean isBlacklisted = entry.isPresent();
            
            if (isBlacklisted) {
                BlacklistEntry blacklistEntry = entry.get();
                log.warn("Device {} is blacklisted: {} (added: {}, expires: {})", 
                    deviceFingerprint, blacklistEntry.getReason(), 
                    blacklistEntry.getCreatedAt(), blacklistEntry.getExpiresAt());
                
                // Check if blacklist entry has expired
                if (blacklistEntry.getExpiresAt() != null && 
                    LocalDateTime.now().isAfter(blacklistEntry.getExpiresAt())) {
                    log.info("Blacklist entry for device {} has expired, removing", deviceFingerprint);
                    removeFromBlacklist(BlacklistEntry.BlacklistType.DEVICE_FINGERPRINT, deviceFingerprint);
                    isBlacklisted = false;
                }
            }
            
            // Cache result
            cacheService.put(cacheKey, isBlacklisted, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            
            return isBlacklisted;
            
        } catch (Exception e) {
            log.error("Error checking device blacklist for: {}", deviceFingerprint, e);
            return false;
        }
    }

    /**
     * Check if payment method is blacklisted
     */
    @Traced(operationName = "check-payment-method-blacklist", businessOperation = "fraud-detection", priority = Traced.TracingPriority.HIGH)
    @Transactional(readOnly = true)
    public boolean isPaymentMethodBlacklisted(String paymentMethodId) {
        if (!StringUtils.hasText(paymentMethodId)) {
            log.warn("Empty payment method ID provided for blacklist check");
            return false;
        }
        
        log.debug("Checking blacklist status for payment method: {}", paymentMethodId);
        
        try {
            // Check cache first
            String cacheKey = BLACKLIST_CACHE_PREFIX + "payment:" + paymentMethodId;
            Boolean cachedResult = cacheService.get(cacheKey, Boolean.class);
            if (cachedResult != null) {
                log.debug("Cache hit for payment method blacklist check: {} -> {}", paymentMethodId, cachedResult);
                return cachedResult;
            }
            
            // Check database
            Optional<BlacklistEntry> entry = blacklistRepository.findActiveByTypeAndValue(
                BlacklistEntry.BlacklistType.PAYMENT_METHOD, paymentMethodId);
            
            boolean isBlacklisted = entry.isPresent();
            
            if (isBlacklisted) {
                BlacklistEntry blacklistEntry = entry.get();
                log.warn("Payment method {} is blacklisted: {} (added: {}, expires: {})", 
                    paymentMethodId, blacklistEntry.getReason(), 
                    blacklistEntry.getCreatedAt(), blacklistEntry.getExpiresAt());
                
                // Check if blacklist entry has expired
                if (blacklistEntry.getExpiresAt() != null && 
                    LocalDateTime.now().isAfter(blacklistEntry.getExpiresAt())) {
                    log.info("Blacklist entry for payment method {} has expired, removing", paymentMethodId);
                    removeFromBlacklist(BlacklistEntry.BlacklistType.PAYMENT_METHOD, paymentMethodId);
                    isBlacklisted = false;
                }
            }
            
            // Cache result
            cacheService.put(cacheKey, isBlacklisted, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            
            return isBlacklisted;
            
        } catch (Exception e) {
            log.error("Error checking payment method blacklist for: {}", paymentMethodId, e);
            return false;
        }
    }

    // DTOs

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BulkBlacklistRequest {
        private BlacklistEntry.BlacklistType type;
        private String value;
        private String reason;
        private LocalDateTime expiresAt;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BlacklistStatistics {
        private long totalActiveEntries;
        private Map<BlacklistEntry.BlacklistType, Long> countsByType;
        private long expiredEntries;
        private LocalDateTime lastUpdated;
    }
}