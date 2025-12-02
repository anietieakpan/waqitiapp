package com.waqiti.common.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade webhook signature validation service.
 * Provides comprehensive security for webhook endpoints including:
 * - HMAC signature validation
 * - Timestamp verification to prevent replay attacks
 * - Nonce tracking for idempotency
 * - Rate limiting per webhook source
 * - Audit logging for compliance
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecureWebhookValidator {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final WebhookAuditService auditService;
    private final ObjectMapper objectMapper;
    
    @Value("${webhook.signature.algorithm:HmacSHA256}")
    private String signatureAlgorithm;
    
    @Value("${webhook.timestamp.tolerance.seconds:300}")
    private long timestampToleranceSeconds;
    
    @Value("${webhook.nonce.ttl.minutes:60}")
    private long nonceTtlMinutes;
    
    @Value("${webhook.rate.limit.per.minute:100}")
    private int rateLimitPerMinute;
    
    private static final String NONCE_PREFIX = "webhook:nonce:";
    private static final String RATE_LIMIT_PREFIX = "webhook:rate:";
    
    /**
     * Comprehensive webhook validation including signature, timestamp, and replay prevention.
     * 
     * @param request The webhook validation request
     * @return Validation result with detailed information
     */
    public WebhookValidationResult validateWebhook(WebhookValidationRequest request) {
        String webhookId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        
        log.debug("Validating webhook: provider={}, webhookId={}", request.getProvider(), webhookId);
        
        try {
            // Step 1: Validate required fields
            ValidationResult fieldValidation = validateRequiredFields(request);
            if (!fieldValidation.isValid()) {
                return createFailureResult(webhookId, fieldValidation.getError(), request);
            }
            
            // Step 2: Check rate limiting
            if (!checkRateLimit(request.getProvider(), request.getSourceIp())) {
                auditService.logRateLimitExceeded(request.getProvider(), request.getSourceIp());
                return createFailureResult(webhookId, "Rate limit exceeded", request);
            }
            
            // Step 3: Validate timestamp (prevent replay attacks)
            ValidationResult timestampValidation = validateTimestamp(request.getTimestamp());
            if (!timestampValidation.isValid()) {
                auditService.logTimestampViolation(request.getProvider(), request.getSourceIp(), 
                        request.getTimestamp());
                return createFailureResult(webhookId, timestampValidation.getError(), request);
            }
            
            // Step 4: Check nonce for idempotency
            if (request.getNonce() != null) {
                ValidationResult nonceValidation = validateNonce(request.getNonce(), request.getProvider());
                if (!nonceValidation.isValid()) {
                    auditService.logNonceReuse(request.getProvider(), request.getNonce());
                    return createFailureResult(webhookId, nonceValidation.getError(), request);
                }
            }
            
            // Step 5: Validate signature
            boolean signatureValid = validateSignature(
                    request.getPayload(),
                    request.getSignature(),
                    request.getSecret(),
                    request.getTimestamp(),
                    request.getNonce(),
                    request.getProvider()
            );
            
            if (!signatureValid) {
                auditService.logInvalidSignature(request.getProvider(), request.getSourceIp());
                return createFailureResult(webhookId, "Invalid signature", request);
            }
            
            // Step 6: Parse and validate payload
            ValidationResult payloadValidation = validatePayload(request.getPayload(), request.getProvider());
            if (!payloadValidation.isValid()) {
                return createFailureResult(webhookId, payloadValidation.getError(), request);
            }
            
            // Success - store nonce to prevent replay
            if (request.getNonce() != null) {
                storeNonce(request.getNonce(), request.getProvider());
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            auditService.logSuccessfulValidation(request.getProvider(), request.getSourceIp(), 
                    webhookId, processingTime);
            
            return WebhookValidationResult.builder()
                    .webhookId(webhookId)
                    .valid(true)
                    .provider(request.getProvider())
                    .timestamp(Instant.now())
                    .processingTimeMs(processingTime)
                    .build();
            
        } catch (Exception e) {
            log.error("Webhook validation error: provider={}, webhookId={}", 
                    request.getProvider(), webhookId, e);
            
            auditService.logValidationError(request.getProvider(), request.getSourceIp(), 
                    e.getMessage());
            
            return createFailureResult(webhookId, "Validation error: " + e.getMessage(), request);
        }
    }
    
    /**
     * Validate webhook signature using HMAC.
     * 
     * @param payload The webhook payload
     * @param providedSignature The signature provided by the webhook sender
     * @param secret The webhook secret
     * @param timestamp The webhook timestamp
     * @param nonce Optional nonce value
     * @param provider The webhook provider name
     * @return true if signature is valid
     */
    public boolean validateSignature(String payload, String providedSignature, String secret,
                                    String timestamp, String nonce, String provider) {
        try {
            // Build the message to sign (includes timestamp and nonce for additional security)
            StringBuilder messageBuilder = new StringBuilder();
            
            if (timestamp != null) {
                messageBuilder.append(timestamp).append(".");
            }
            
            if (nonce != null) {
                messageBuilder.append(nonce).append(".");
            }
            
            messageBuilder.append(payload);
            
            String message = messageBuilder.toString();
            
            // Generate expected signature
            String expectedSignature = generateSignature(message, secret, provider);
            
            // Use constant-time comparison to prevent timing attacks
            return constantTimeEquals(expectedSignature, providedSignature);
            
        } catch (Exception e) {
            log.error("Signature validation error for provider: {}", provider, e);
            return false;
        }
    }
    
    /**
     * Generate HMAC signature for a message.
     * 
     * @param message The message to sign
     * @param secret The secret key
     * @param provider The provider name (for provider-specific algorithms)
     * @return The generated signature
     */
    private String generateSignature(String message, String secret, String provider) throws Exception {
        // Handle provider-specific signature algorithms
        String algorithm = getProviderAlgorithm(provider);
        
        Mac mac = Mac.getInstance(algorithm);
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm);
        mac.init(secretKey);
        
        byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        
        // Some providers use hex encoding, others use base64
        if (isHexEncodedProvider(provider)) {
            return bytesToHex(hash);
        } else {
            return Base64.getEncoder().encodeToString(hash);
        }
    }
    
    /**
     * Validate timestamp to prevent replay attacks.
     * 
     * @param timestamp The timestamp to validate
     * @return Validation result
     */
    private ValidationResult validateTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return ValidationResult.invalid("Timestamp is required");
        }
        
        try {
            long webhookTimestamp = Long.parseLong(timestamp);
            long currentTimestamp = Instant.now().getEpochSecond();
            
            long difference = Math.abs(currentTimestamp - webhookTimestamp);
            
            if (difference > timestampToleranceSeconds) {
                return ValidationResult.invalid(
                    String.format("Timestamp outside acceptable range. Difference: %d seconds", difference)
                );
            }
            
            return ValidationResult.valid();
            
        } catch (NumberFormatException e) {
            return ValidationResult.invalid("Invalid timestamp format");
        }
    }
    
    /**
     * Validate nonce to prevent replay attacks.
     * 
     * @param nonce The nonce to validate
     * @param provider The provider name
     * @return Validation result
     */
    private ValidationResult validateNonce(String nonce, String provider) {
        String nonceKey = NONCE_PREFIX + provider + ":" + nonce;
        
        // Check if nonce was already used
        if (Boolean.TRUE.equals(redisTemplate.hasKey(nonceKey))) {
            return ValidationResult.invalid("Nonce already used (replay attack prevented)");
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Store nonce to prevent reuse.
     * 
     * @param nonce The nonce to store
     * @param provider The provider name
     */
    private void storeNonce(String nonce, String provider) {
        String nonceKey = NONCE_PREFIX + provider + ":" + nonce;
        redisTemplate.opsForValue().set(nonceKey, true, nonceTtlMinutes, TimeUnit.MINUTES);
    }
    
    /**
     * Check rate limiting for webhook provider.
     * 
     * @param provider The provider name
     * @param sourceIp The source IP address
     * @return true if within rate limit
     */
    private boolean checkRateLimit(String provider, String sourceIp) {
        String rateLimitKey = RATE_LIMIT_PREFIX + provider + ":" + sourceIp;
        
        Long count = redisTemplate.opsForValue().increment(rateLimitKey);
        
        if (count == 1) {
            // First request, set expiry
            redisTemplate.expire(rateLimitKey, 1, TimeUnit.MINUTES);
        }
        
        return count <= rateLimitPerMinute;
    }
    
    /**
     * Validate required fields in webhook request.
     * 
     * @param request The webhook request
     * @return Validation result
     */
    private ValidationResult validateRequiredFields(WebhookValidationRequest request) {
        if (request.getProvider() == null || request.getProvider().isEmpty()) {
            return ValidationResult.invalid("Provider is required");
        }
        
        if (request.getPayload() == null || request.getPayload().isEmpty()) {
            return ValidationResult.invalid("Payload is required");
        }
        
        if (request.getSignature() == null || request.getSignature().isEmpty()) {
            return ValidationResult.invalid("Signature is required");
        }
        
        if (request.getSecret() == null || request.getSecret().isEmpty()) {
            return ValidationResult.invalid("Secret is required");
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Validate webhook payload structure and content.
     * 
     * @param payload The payload to validate
     * @param provider The provider name
     * @return Validation result
     */
    private ValidationResult validatePayload(String payload, String provider) {
        try {
            // Parse JSON payload
            Map<String, Object> payloadMap = objectMapper.readValue(payload, Map.class);
            
            // Check for required fields based on provider
            switch (provider.toLowerCase()) {
                case "stripe":
                    if (!payloadMap.containsKey("type") || !payloadMap.containsKey("data")) {
                        return ValidationResult.invalid("Invalid Stripe webhook payload structure");
                    }
                    break;
                    
                case "paypal":
                    if (!payloadMap.containsKey("event_type") || !payloadMap.containsKey("resource")) {
                        return ValidationResult.invalid("Invalid PayPal webhook payload structure");
                    }
                    break;
                    
                case "razorpay":
                    if (!payloadMap.containsKey("event") || !payloadMap.containsKey("payload")) {
                        return ValidationResult.invalid("Invalid Razorpay webhook payload structure");
                    }
                    break;
                    
                // Add more provider-specific validations as needed
            }
            
            // Check for suspicious patterns
            if (containsSuspiciousContent(payload)) {
                return ValidationResult.invalid("Suspicious content detected in payload");
            }
            
            return ValidationResult.valid();
            
        } catch (Exception e) {
            return ValidationResult.invalid("Invalid JSON payload: " + e.getMessage());
        }
    }
    
    /**
     * Check for suspicious content in payload.
     * 
     * @param payload The payload to check
     * @return true if suspicious content is found
     */
    private boolean containsSuspiciousContent(String payload) {
        // Check for common injection patterns
        String[] suspiciousPatterns = {
            "<script", "javascript:", "onclick=", "onerror=",
            "'; DROP TABLE", "1=1", "OR 1=1", "UNION SELECT",
            "../", "..\\", "%00", "\u0000"
        };
        
        String lowerPayload = payload.toLowerCase();
        for (String pattern : suspiciousPatterns) {
            if (lowerPayload.contains(pattern.toLowerCase())) {
                log.warn("Suspicious pattern detected in webhook payload: {}", pattern);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get provider-specific signature algorithm.
     * 
     * @param provider The provider name
     * @return The signature algorithm
     */
    private String getProviderAlgorithm(String provider) {
        switch (provider.toLowerCase()) {
            case "stripe":
                return "HmacSHA256";
            case "paypal":
                return "HmacSHA256";
            case "razorpay":
                return "HmacSHA256";
            case "shopify":
                return "HmacSHA256";
            case "github":
                return "HmacSHA256";
            case "slack":
                return "HmacSHA256";
            default:
                return signatureAlgorithm;
        }
    }
    
    /**
     * Check if provider uses hex encoding for signatures.
     * 
     * @param provider The provider name
     * @return true if hex encoding is used
     */
    private boolean isHexEncodedProvider(String provider) {
        switch (provider.toLowerCase()) {
            case "stripe":
            case "shopify":
            case "github":
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Convert bytes to hex string.
     * 
     * @param bytes The bytes to convert
     * @return Hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    /**
     * Constant-time string comparison to prevent timing attacks.
     * 
     * @param a First string
     * @param b Second string
     * @return true if strings are equal
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        
        if (a.length() != b.length()) {
            return false;
        }
        
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        
        return result == 0;
    }
    
    /**
     * Create failure result for webhook validation.
     * 
     * @param webhookId The webhook ID
     * @param error The error message
     * @param request The original request
     * @return Failure result
     */
    private WebhookValidationResult createFailureResult(String webhookId, String error, 
                                                       WebhookValidationRequest request) {
        return WebhookValidationResult.builder()
                .webhookId(webhookId)
                .valid(false)
                .error(error)
                .provider(request.getProvider())
                .timestamp(Instant.now())
                .processingTimeMs(System.currentTimeMillis())
                .build();
    }
    
    /**
     * Internal validation result class.
     */
    private static class ValidationResult {
        private final boolean valid;
        private final String error;
        
        private ValidationResult(boolean valid, String error) {
            this.valid = valid;
            this.error = error;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, error);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getError() {
            return error;
        }
    }
}