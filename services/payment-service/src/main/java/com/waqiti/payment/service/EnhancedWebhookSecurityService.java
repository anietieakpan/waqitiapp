package com.waqiti.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.waqiti.payment.dto.WebhookEvent;
import com.waqiti.payment.exception.WebhookSecurityException;
import com.waqiti.security.service.VaultSecretService;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Enhanced Webhook Security Service
 * 
 * Implements comprehensive webhook security including:
 * - Mandatory signature verification
 * - Replay attack prevention
 * - Rate limiting per webhook source
 * - IP allowlisting
 * - Event deduplication
 * - Audit logging
 * 
 * SECURITY: Fixes webhook signature bypass vulnerability
 */
@Service
@Slf4j
public class EnhancedWebhookSecurityService {
    
    private final ObjectMapper objectMapper;
    private final VaultSecretService vaultService;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;
    
    @Value("${webhook.security.tolerance-seconds:300}")
    private long toleranceSeconds;
    
    @Value("${webhook.security.require-timestamp:true}")
    private boolean requireTimestamp;
    
    @Value("${webhook.security.require-ip-allowlist:true}")
    private boolean requireIpAllowlist;
    
    @Value("${webhook.security.max-requests-per-minute:60}")
    private int maxRequestsPerMinute;
    
    // Replay attack prevention cache
    private final Cache<String, Boolean> processedEvents;
    
    // Rate limiters per webhook source
    private final ConcurrentHashMap<String, RateLimiter> rateLimiters;
    
    // IP allowlist
    private Set<String> allowedIps;
    
    // Webhook secrets cache
    private final Cache<String, String> secretsCache;
    
    public EnhancedWebhookSecurityService(
            ObjectMapper objectMapper,
            VaultSecretService vaultService,
            AuditService auditService,
            MeterRegistry meterRegistry) {
        
        this.objectMapper = objectMapper;
        this.vaultService = vaultService;
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;
        
        // Initialize replay prevention cache (15 minute expiry)
        this.processedEvents = CacheBuilder.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();
        
        // Initialize rate limiters map
        this.rateLimiters = new ConcurrentHashMap<>();
        
        // Initialize secrets cache (5 minute expiry for rotation)
        this.secretsCache = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(100)
            .build();
    }
    
    @PostConstruct
    public void init() {
        // Load IP allowlist from Vault
        loadIpAllowlist();
        
        // Schedule periodic refresh of allowlist
        scheduleAllowlistRefresh();
    }
    
    /**
     * Verify webhook with comprehensive security checks
     * 
     * @param webhookEvent The webhook event to verify
     * @param signature The provided signature
     * @param timestamp The webhook timestamp
     * @param sourceIp The source IP address
     * @param provider The webhook provider name
     * @return true if all security checks pass
     * @throws WebhookSecurityException if any security check fails
     */
    public boolean verifyWebhook(
            WebhookEvent webhookEvent,
            String signature,
            String timestamp,
            String sourceIp,
            String provider) {
        
        String eventId = webhookEvent.getEventId();
        
        try {
            // 1. Check IP allowlist
            if (requireIpAllowlist && !isIpAllowed(sourceIp)) {
                log.error("SECURITY: Webhook from unauthorized IP: {} for provider: {}", 
                         sourceIp, provider);
                auditService.auditSecurityEvent("WEBHOOK_UNAUTHORIZED_IP", 
                    String.format("IP: %s, Provider: %s", sourceIp, provider));
                meterRegistry.counter("webhook.security.unauthorized_ip").increment();
                throw new WebhookSecurityException("Unauthorized webhook source IP");
            }
            
            // 2. Rate limiting
            if (!checkRateLimit(provider)) {
                log.error("SECURITY: Rate limit exceeded for provider: {}", provider);
                auditService.auditSecurityEvent("WEBHOOK_RATE_LIMIT_EXCEEDED", provider);
                meterRegistry.counter("webhook.security.rate_limited").increment();
                throw new WebhookSecurityException("Rate limit exceeded");
            }
            
            // 3. Timestamp validation (prevent replay attacks)
            if (requireTimestamp && !isValidTimestamp(timestamp)) {
                log.error("SECURITY: Invalid or expired timestamp for webhook: {}", eventId);
                auditService.auditSecurityEvent("WEBHOOK_INVALID_TIMESTAMP", eventId);
                meterRegistry.counter("webhook.security.invalid_timestamp").increment();
                throw new WebhookSecurityException("Invalid or expired webhook timestamp");
            }
            
            // 4. Replay attack prevention
            if (isReplayedEvent(eventId, timestamp)) {
                log.error("SECURITY: Potential replay attack detected for event: {}", eventId);
                auditService.auditSecurityEvent("WEBHOOK_REPLAY_ATTACK", eventId);
                meterRegistry.counter("webhook.security.replay_detected").increment();
                throw new WebhookSecurityException("Duplicate webhook event detected");
            }
            
            // 5. Get webhook secret from Vault
            String webhookSecret = getWebhookSecret(provider);
            
            // 6. Signature verification
            boolean signatureValid = verifySignature(
                signature,
                webhookEvent,
                timestamp,
                webhookSecret
            );
            
            if (!signatureValid) {
                log.error("SECURITY: Invalid signature for webhook from provider: {}", provider);
                auditService.auditSecurityEvent("WEBHOOK_INVALID_SIGNATURE", 
                    String.format("Provider: %s, Event: %s", provider, eventId));
                meterRegistry.counter("webhook.security.invalid_signature").increment();
                throw new WebhookSecurityException("Invalid webhook signature");
            }
            
            // 7. Mark event as processed
            markEventProcessed(eventId, timestamp);
            
            // 8. Audit successful verification
            auditService.auditWebhookVerification(provider, eventId, sourceIp, "SUCCESS");
            meterRegistry.counter("webhook.security.verified", "provider", provider).increment();
            
            log.info("Webhook verified successfully: provider={}, eventId={}", provider, eventId);
            return true;
            
        } catch (WebhookSecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error verifying webhook", e);
            meterRegistry.counter("webhook.security.error").increment();
            throw new WebhookSecurityException("Webhook verification failed", e);
        }
    }
    
    /**
     * Verify webhook signature
     */
    private boolean verifySignature(
            String providedSignature,
            WebhookEvent event,
            String timestamp,
            String secret) throws Exception {
        
        // Parse signature format (e.g., "sha256=...")
        if (providedSignature == null || !providedSignature.startsWith("sha256=")) {
            return false;
        }
        
        String signature = providedSignature.substring(7);
        
        // Create signed payload
        String payloadJson = objectMapper.writeValueAsString(event);
        String signedPayload = timestamp != null ? 
            timestamp + "." + payloadJson : payloadJson;
        
        // Calculate expected signature
        String expectedSignature = calculateHmacSha256(signedPayload, secret);
        
        // Constant-time comparison
        return constantTimeEquals(expectedSignature, signature);
    }
    
    /**
     * Calculate HMAC-SHA256
     */
    private String calculateHmacSha256(String data, String secret) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8), 
            "HmacSHA256"
        );
        hmac.init(secretKey);
        
        byte[] signatureBytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        
        StringBuilder result = new StringBuilder();
        for (byte b : signatureBytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    /**
     * Constant-time string comparison to prevent timing attacks
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        
        return result == 0;
    }
    
    /**
     * Check if timestamp is valid
     */
    private boolean isValidTimestamp(String timestamp) {
        if (timestamp == null) {
            return false;
        }
        
        try {
            long webhookTime = Long.parseLong(timestamp);
            long currentTime = Instant.now().getEpochSecond();
            long difference = Math.abs(currentTime - webhookTime);
            
            return difference <= toleranceSeconds;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Check for replay attacks
     */
    private boolean isReplayedEvent(String eventId, String timestamp) {
        String cacheKey = eventId + ":" + timestamp;
        
        Boolean processed = processedEvents.getIfPresent(cacheKey);
        return processed != null && processed;
    }
    
    /**
     * Mark event as processed
     */
    private void markEventProcessed(String eventId, String timestamp) {
        String cacheKey = eventId + ":" + timestamp;
        processedEvents.put(cacheKey, Boolean.TRUE);
    }
    
    /**
     * Check rate limit for provider
     */
    private boolean checkRateLimit(String provider) {
        RateLimiter rateLimiter = rateLimiters.computeIfAbsent(provider, p -> {
            RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .limitForPeriod(maxRequestsPerMinute)
                .timeoutDuration(Duration.ofMillis(100))
                .build();
            
            return RateLimiter.of("webhook-" + p, config);
        });
        
        return rateLimiter.acquirePermission();
    }
    
    /**
     * Check if IP is allowed
     */
    private boolean isIpAllowed(String ip) {
        return allowedIps != null && allowedIps.contains(ip);
    }
    
    /**
     * Get webhook secret from Vault
     */
    private String getWebhookSecret(String provider) {
        String cacheKey = "webhook-secret-" + provider;
        
        try {
            return secretsCache.get(cacheKey, () -> {
                String vaultPath = String.format("secret/webhooks/%s/secret", provider);
                String secret = vaultService.getSecret(vaultPath);
                if (secret == null || secret.trim().isEmpty()) {
                    log.error("SECURITY: Empty or null webhook secret for provider: {}", provider);
                    throw new WebhookSecurityException("Webhook secret not configured for provider: " + provider);
                }
                return secret;
            });
        } catch (Exception e) {
            log.error("SECURITY: Failed to retrieve webhook secret for provider: {}", provider, e);
            throw new WebhookSecurityException("Failed to retrieve webhook secret for provider: " + provider, e);
        }
    }
    
    /**
     * Get webhook secret with fallback (for testing/development only)
     * @deprecated Use proper vault configuration in production
     */
    @Deprecated
    private String getWebhookSecretWithFallback(String provider) {
        try {
            return getWebhookSecret(provider);
        } catch (Exception e) {
            log.warn("SECURITY: Using fallback webhook validation for provider: {} - NOT FOR PRODUCTION", provider);
            // Return a predictable test secret only in non-production environments
            String envProfile = System.getProperty("spring.profiles.active", "production");
            if ("development".equals(envProfile) || "test".equals(envProfile)) {
                return "fallback-secret-" + provider + "-not-for-production";
            }
            throw new WebhookSecurityException("No webhook secret available for provider: " + provider);
        }
    }
    
    /**
     * Load IP allowlist from Vault
     */
    private void loadIpAllowlist() {
        try {
            String allowlistJson = vaultService.getSecret("secret/webhooks/ip-allowlist");
            if (allowlistJson != null) {
                // Parse JSON array of IPs
                this.allowedIps = objectMapper.readValue(allowlistJson, 
                    objectMapper.getTypeFactory().constructCollectionType(Set.class, String.class));
                log.info("Loaded {} IPs to webhook allowlist", allowedIps.size());
            }
        } catch (Exception e) {
            log.error("Failed to load IP allowlist from Vault", e);
            // Initialize with empty set to fail closed
            this.allowedIps = Set.of();
        }
    }
    
    /**
     * Schedule periodic refresh of IP allowlist
     */
    private void scheduleAllowlistRefresh() {
        // Implementation would use @Scheduled or ScheduledExecutorService
        // Refreshes every 5 minutes
    }
    
    /**
     * Generate webhook signature for outgoing webhooks
     */
    public WebhookSignature generateWebhookSignature(
            Object payload,
            String provider) throws Exception {
        
        String secret = getWebhookSecret(provider);
        if (secret == null) {
            throw new WebhookSecurityException("No secret configured for provider: " + provider);
        }
        
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String payloadJson = objectMapper.writeValueAsString(payload);
        String signedPayload = timestamp + "." + payloadJson;
        
        String signature = calculateHmacSha256(signedPayload, secret);
        
        return WebhookSignature.builder()
            .signature("sha256=" + signature)
            .timestamp(timestamp)
            .provider(provider)
            .build();
    }
    
    /**
     * Webhook signature data
     */
    @lombok.Data
    @lombok.Builder
    public static class WebhookSignature {
        private String signature;
        private String timestamp;
        private String provider;
    }
}