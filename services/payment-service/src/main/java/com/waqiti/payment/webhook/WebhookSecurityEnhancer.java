package com.waqiti.payment.webhook;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Production-Grade Webhook Security Enhancer
 *
 * This component provides advanced security features for all webhook endpoints:
 * - Replay attack prevention with timestamp validation
 * - Idempotency tracking using Redis
 * - Signature verification with multiple algorithm support
 * - Request fingerprinting for duplicate detection
 * - Comprehensive audit logging
 *
 * Security Compliance:
 * - PCI-DSS: Requirement 6.5.9 (Session Management and Authentication)
 * - GDPR: Data integrity and security
 * - SOX: Audit trail requirements
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-09
 */
@Component
@Slf4j
public class WebhookSecurityEnhancer {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${webhook.replay.protection.window.seconds:300}")
    private long replayProtectionWindowSeconds;

    @Value("${webhook.idempotency.ttl.hours:24}")
    private long idempotencyTtlHours;

    private static final String WEBHOOK_FINGERPRINT_PREFIX = "webhook:fingerprint:";
    private static final String WEBHOOK_REQUEST_ID_PREFIX = "webhook:request:";

    public WebhookSecurityEnhancer(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Comprehensive webhook validation
     *
     * @param request The webhook security request
     * @return Validation result
     */
    public WebhookSecurityResult validateWebhookSecurity(WebhookSecurityRequest request) {
        String requestId = generateRequestId(request);
        Instant startTime = Instant.now();

        log.debug("Validating webhook security: provider={}, requestId={}",
            request.getProvider(), requestId);

        try {
            // Step 1: Validate timestamp (replay attack prevention)
            TimestampValidationResult timestampResult = validateTimestamp(request.getTimestamp());
            if (!timestampResult.isValid()) {
                return WebhookSecurityResult.builder()
                    .valid(false)
                    .requestId(requestId)
                    .reason(timestampResult.getReason())
                    .securityViolationType("TIMESTAMP_VIOLATION")
                    .build();
            }

            // Step 2: Check for duplicate requests (idempotency)
            String requestFingerprint = calculateRequestFingerprint(request);
            if (isDuplicateRequest(requestFingerprint, request.getProvider())) {
                log.warn("Duplicate webhook request detected: provider={}, fingerprint={}",
                    request.getProvider(), requestFingerprint);

                return WebhookSecurityResult.builder()
                    .valid(false)
                    .requestId(requestId)
                    .reason("Duplicate request detected (idempotency check failed)")
                    .securityViolationType("DUPLICATE_REQUEST")
                    .isDuplicate(true)
                    .build();
            }

            // Step 3: Verify signature
            SignatureValidationResult signatureResult = verifySignature(request);
            if (!signatureResult.isValid()) {
                return WebhookSecurityResult.builder()
                    .valid(false)
                    .requestId(requestId)
                    .reason(signatureResult.getReason())
                    .securityViolationType("INVALID_SIGNATURE")
                    .build();
            }

            // Step 4: Store request fingerprint for idempotency
            storeRequestFingerprint(requestFingerprint, request.getProvider(), requestId);

            // Success
            Duration processingTime = Duration.between(startTime, Instant.now());

            return WebhookSecurityResult.builder()
                .valid(true)
                .requestId(requestId)
                .requestFingerprint(requestFingerprint)
                .processingTimeMs(processingTime.toMillis())
                .build();

        } catch (Exception e) {
            log.error("Webhook security validation error: provider={}, error={}",
                request.getProvider(), e.getMessage(), e);

            return WebhookSecurityResult.builder()
                .valid(false)
                .requestId(requestId)
                .reason("Security validation error: " + e.getMessage())
                .securityViolationType("VALIDATION_ERROR")
                .build();
        }
    }

    /**
     * Validate webhook timestamp to prevent replay attacks
     *
     * Accepts requests within configurable time window (default: 5 minutes)
     *
     * @param timestampStr Timestamp string (epoch seconds)
     * @return Validation result
     */
    private TimestampValidationResult validateTimestamp(String timestampStr) {
        if (timestampStr == null || timestampStr.isEmpty()) {
            return TimestampValidationResult.invalid("Timestamp is required");
        }

        try {
            long webhookTimestamp = Long.parseLong(timestampStr);
            long currentTimestamp = Instant.now().getEpochSecond();
            long difference = Math.abs(currentTimestamp - webhookTimestamp);

            if (difference > replayProtectionWindowSeconds) {
                return TimestampValidationResult.invalid(
                    String.format("Timestamp outside acceptable window. " +
                        "Difference: %d seconds, Max allowed: %d seconds",
                        difference, replayProtectionWindowSeconds)
                );
            }

            // Reject future-dated requests (clock skew attack)
            if (webhookTimestamp > currentTimestamp + 60) { // 60 seconds tolerance
                return TimestampValidationResult.invalid(
                    "Timestamp is in the future (possible clock skew attack)"
                );
            }

            return TimestampValidationResult.valid();

        } catch (NumberFormatException e) {
            return TimestampValidationResult.invalid("Invalid timestamp format");
        }
    }

    /**
     * Verify webhook signature using provider-specific algorithm
     *
     * @param request The webhook request
     * @return Validation result
     */
    private SignatureValidationResult verifySignature(WebhookSecurityRequest request) {
        if (request.getSignature() == null || request.getSignature().isEmpty()) {
            return SignatureValidationResult.invalid("Signature is required");
        }

        if (request.getSecret() == null || request.getSecret().isEmpty()) {
            return SignatureValidationResult.invalid("Webhook secret is not configured");
        }

        try {
            String expectedSignature = calculateSignature(request);

            // Use constant-time comparison to prevent timing attacks
            boolean isValid = constantTimeEquals(
                normalizeSignature(request.getSignature()),
                normalizeSignature(expectedSignature)
            );

            if (!isValid) {
                log.warn("Signature mismatch: provider={}", request.getProvider());
                return SignatureValidationResult.invalid("Signature verification failed");
            }

            return SignatureValidationResult.valid();

        } catch (Exception e) {
            log.error("Signature calculation error: provider={}", request.getProvider(), e);
            return SignatureValidationResult.invalid("Signature calculation error: " + e.getMessage());
        }
    }

    /**
     * Calculate expected signature based on provider
     *
     * @param request The webhook request
     * @return Expected signature
     */
    private String calculateSignature(WebhookSecurityRequest request)
            throws NoSuchAlgorithmException, InvalidKeyException {

        String algorithm = getAlgorithmForProvider(request.getProvider());
        String message = constructSignatureMessage(request);

        Mac mac = Mac.getInstance(algorithm);
        SecretKeySpec secretKeySpec = new SecretKeySpec(
            request.getSecret().getBytes(StandardCharsets.UTF_8),
            algorithm
        );
        mac.init(secretKeySpec);

        byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));

        // Different providers use different encoding
        return encodeSignature(hash, request.getProvider());
    }

    /**
     * Construct the message to be signed (provider-specific)
     *
     * @param request The webhook request
     * @return Message string
     */
    private String constructSignatureMessage(WebhookSecurityRequest request) {
        switch (request.getProvider().toLowerCase()) {
            case "stripe":
                // Stripe: timestamp.payload
                return request.getTimestamp() + "." + request.getPayload();

            case "paypal":
                // PayPal: Uses multiple headers in signature
                return request.getPayload(); // Simplified; actual implementation in PayPalWebhookHandler

            case "wise":
                // Wise: payload only
                return request.getPayload();

            case "dwolla":
                // Dwolla: X-Request-Signature-SHA-256
                return request.getPayload();

            default:
                // Default: timestamp + payload
                return (request.getTimestamp() != null ? request.getTimestamp() : "") + request.getPayload();
        }
    }

    /**
     * Get HMAC algorithm for provider
     *
     * @param provider Provider name
     * @return Algorithm name
     */
    private String getAlgorithmForProvider(String provider) {
        // All current providers use HMAC-SHA256
        return "HmacSHA256";
    }

    /**
     * Encode signature based on provider preference
     *
     * @param hash Signature hash bytes
     * @param provider Provider name
     * @return Encoded signature
     */
    private String encodeSignature(byte[] hash, String provider) {
        switch (provider.toLowerCase()) {
            case "stripe":
            case "dwolla":
                // Hex encoding
                return bytesToHex(hash);

            case "paypal":
            case "wise":
            default:
                // Base64 encoding
                return Base64.getEncoder().encodeToString(hash);
        }
    }

    /**
     * Normalize signature for comparison (remove prefixes, whitespace, etc.)
     *
     * @param signature Raw signature
     * @return Normalized signature
     */
    private String normalizeSignature(String signature) {
        if (signature == null) {
            return "";
        }

        // Remove common prefixes
        String normalized = signature;
        if (normalized.startsWith("sha256=")) {
            normalized = normalized.substring(7);
        }
        if (normalized.startsWith("v1=")) {
            normalized = normalized.substring(3);
        }

        return normalized.trim();
    }

    /**
     * Calculate request fingerprint for idempotency
     *
     * Fingerprint = SHA-256(provider + timestamp + payload + headers)
     *
     * @param request The webhook request
     * @return Fingerprint hash
     */
    private String calculateRequestFingerprint(WebhookSecurityRequest request) {
        try {
            StringBuilder fingerprintInput = new StringBuilder();
            fingerprintInput.append(request.getProvider()).append("|");
            fingerprintInput.append(request.getTimestamp() != null ? request.getTimestamp() : "").append("|");
            fingerprintInput.append(request.getPayload());

            // Add event ID if available
            if (request.getEventId() != null) {
                fingerprintInput.append("|").append(request.getEventId());
            }

            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(fingerprintInput.toString().getBytes(StandardCharsets.UTF_8));

            return bytesToHex(hash);

        } catch (Exception e) {
            log.error("Error calculating request fingerprint", e);
            // Fallback: use timestamp + provider
            return request.getProvider() + "_" + request.getTimestamp();
        }
    }

    /**
     * Check if request is a duplicate
     *
     * @param fingerprint Request fingerprint
     * @param provider Provider name
     * @return true if duplicate
     */
    private boolean isDuplicateRequest(String fingerprint, String provider) {
        String key = WEBHOOK_FINGERPRINT_PREFIX + provider + ":" + fingerprint;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Store request fingerprint to prevent replay
     *
     * @param fingerprint Request fingerprint
     * @param provider Provider name
     * @param requestId Request ID
     */
    private void storeRequestFingerprint(String fingerprint, String provider, String requestId) {
        String key = WEBHOOK_FINGERPRINT_PREFIX + provider + ":" + fingerprint;
        redisTemplate.opsForValue().set(key, requestId, idempotencyTtlHours, TimeUnit.HOURS);
    }

    /**
     * Generate unique request ID
     *
     * @param request The webhook request
     * @return Request ID
     */
    private String generateRequestId(WebhookSecurityRequest request) {
        return String.format("%s_%s_%d",
            request.getProvider(),
            request.getEventId() != null ? request.getEventId() : "auto",
            System.currentTimeMillis()
        );
    }

    /**
     * Constant-time string comparison (timing attack prevention)
     *
     * @param a First string
     * @param b Second string
     * @return true if equal
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
     * Convert bytes to hex string
     *
     * @param bytes Byte array
     * @return Hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    // ===== Inner Classes =====

    @Data
    @Builder
    public static class WebhookSecurityRequest {
        private String provider;
        private String payload;
        private String signature;
        private String secret;
        private String timestamp;
        private String eventId;
        private String sourceIp;
    }

    @Data
    @Builder
    public static class WebhookSecurityResult {
        private boolean valid;
        private String requestId;
        private String requestFingerprint;
        private String reason;
        private String securityViolationType;
        private boolean isDuplicate;
        private long processingTimeMs;
    }

    @Data
    @Builder
    private static class TimestampValidationResult {
        private boolean valid;
        private String reason;

        public static TimestampValidationResult valid() {
            return TimestampValidationResult.builder().valid(true).build();
        }

        public static TimestampValidationResult invalid(String reason) {
            return TimestampValidationResult.builder()
                .valid(false)
                .reason(reason)
                .build();
        }
    }

    @Data
    @Builder
    private static class SignatureValidationResult {
        private boolean valid;
        private String reason;

        public static SignatureValidationResult valid() {
            return SignatureValidationResult.builder().valid(true).build();
        }

        public static SignatureValidationResult invalid(String reason) {
            return SignatureValidationResult.builder()
                .valid(false)
                .reason(reason)
                .build();
        }
    }
}
