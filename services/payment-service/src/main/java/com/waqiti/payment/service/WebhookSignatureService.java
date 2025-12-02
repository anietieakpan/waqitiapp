package com.waqiti.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.dto.CheckWebhookRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

/**
 * Service for verifying webhook signatures from external providers
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookSignatureService {
    
    private final ObjectMapper objectMapper;
    
    @Value("${app.webhook.secret:}")
    private String webhookSecret;
    
    @Value("${app.webhook.tolerance-seconds:300}")
    private long toleranceSeconds; // 5 minutes
    
    /**
     * Verify webhook signature using HMAC-SHA256
     * 
     * @param signature The signature from the webhook header
     * @param payload The webhook payload
     * @return true if signature is valid
     * @throws SecurityException if webhook secret is not configured
     */
    public boolean verifySignature(String signature, CheckWebhookRequest payload) {
        try {
            // SECURITY FIX: Never allow unsigned webhooks in production
            if (webhookSecret == null || webhookSecret.isEmpty()) {
                log.error("SECURITY: Webhook secret not configured - rejecting webhook");
                throw new SecurityException("Webhook secret must be configured for production");
            }
            
            // Parse signature header (format: "sha256=<signature>")
            String expectedPrefix = "sha256=";
            if (!signature.startsWith(expectedPrefix)) {
                log.warn("Invalid signature format: {}", signature);
                return false;
            }
            
            String providedSignature = signature.substring(expectedPrefix.length());
            
            // Convert payload to JSON string
            String payloadJson = objectMapper.writeValueAsString(payload);
            
            // Calculate expected signature
            String expectedSignature = calculateHmacSha256(payloadJson, webhookSecret);
            
            // Compare signatures using constant-time comparison
            boolean isValid = constantTimeEquals(expectedSignature, providedSignature);
            
            if (!isValid) {
                log.warn("Webhook signature verification failed for reference: {}", 
                    payload.getReferenceId());
            }
            
            return isValid;
            
        } catch (Exception e) {
            log.error("Error verifying webhook signature", e);
            return false;
        }
    }
    
    /**
     * Verify webhook signature with timestamp validation
     * 
     * @param signature The signature from the webhook header
     * @param timestamp The timestamp from the webhook header
     * @param payload The webhook payload
     * @return true if signature and timestamp are valid
     */
    public boolean verifySignatureWithTimestamp(String signature, String timestamp, 
                                              CheckWebhookRequest payload) {
        try {
            // Verify timestamp first
            if (!isValidTimestamp(timestamp)) {
                log.warn("Webhook timestamp too old or invalid: {}", timestamp);
                return false;
            }
            
            // Create signed payload with timestamp
            String payloadJson = objectMapper.writeValueAsString(payload);
            String signedPayload = timestamp + "." + payloadJson;
            
            // Parse signature header
            String expectedPrefix = "sha256=";
            if (!signature.startsWith(expectedPrefix)) {
                return false;
            }
            
            String providedSignature = signature.substring(expectedPrefix.length());
            String expectedSignature = calculateHmacSha256(signedPayload, webhookSecret);
            
            return constantTimeEquals(expectedSignature, providedSignature);
            
        } catch (Exception e) {
            log.error("Error verifying webhook signature with timestamp", e);
            return false;
        }
    }
    
    /**
     * Calculate HMAC-SHA256 signature
     */
    private String calculateHmacSha256(String data, String secret) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        hmac.init(secretKey);
        
        byte[] signatureBytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(signatureBytes);
    }
    
    /**
     * Convert bytes to hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    /**
     * Constant-time string comparison to prevent timing attacks
     */
    private boolean constantTimeEquals(String a, String b) {
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
     * Validate webhook timestamp to prevent replay attacks
     */
    private boolean isValidTimestamp(String timestamp) {
        try {
            long webhookTime = Long.parseLong(timestamp);
            long currentTime = Instant.now().getEpochSecond();
            
            return Math.abs(currentTime - webhookTime) <= toleranceSeconds;
        } catch (NumberFormatException e) {
            log.warn("Invalid timestamp format: {}", timestamp);
            return false;
        }
    }
    
    /**
     * Generate signature for outgoing webhooks
     */
    public String generateSignature(Object payload) throws Exception {
        String payloadJson = objectMapper.writeValueAsString(payload);
        String signature = calculateHmacSha256(payloadJson, webhookSecret);
        return "sha256=" + signature;
    }
    
    /**
     * Generate signature with timestamp for outgoing webhooks
     */
    public String generateSignatureWithTimestamp(Object payload, long timestamp) throws Exception {
        String payloadJson = objectMapper.writeValueAsString(payload);
        String signedPayload = timestamp + "." + payloadJson;
        String signature = calculateHmacSha256(signedPayload, webhookSecret);
        return "sha256=" + signature;
    }
}