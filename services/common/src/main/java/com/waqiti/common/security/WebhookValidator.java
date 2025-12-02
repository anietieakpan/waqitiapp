package com.waqiti.common.security;

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
import java.util.Arrays;
import java.util.Base64;

/**
 * Secure webhook signature validation service
 * Provides cryptographic verification of webhook authenticity
 */
@Slf4j
@Service
public class WebhookValidator {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SHA256 = "SHA-256";
    private static final long SIGNATURE_TOLERANCE_SECONDS = 300; // 5 minutes

    @Value("${webhook.secret.default:}")
    private String defaultWebhookSecret;

    /**
     * Validates webhook signature using HMAC-SHA256
     * @param payload The raw webhook payload
     * @param signature The signature from webhook headers
     * @param secret The webhook secret for this provider
     * @return true if signature is valid
     */
    public boolean validateSignature(String payload, String signature, String secret) {
        if (payload == null || signature == null || secret == null) {
            log.warn("Webhook validation failed: null parameters");
            return false;
        }

        try {
            String expectedSignature = generateSignature(payload, secret);
            boolean isValid = secureCompare(signature, expectedSignature);
            
            if (!isValid) {
                log.warn("Webhook signature validation failed. Expected signature mismatch.");
            }
            
            return isValid;
        } catch (Exception e) {
            log.error("Error validating webhook signature", e);
            return false;
        }
    }

    /**
     * Validates webhook signature with default secret
     * @param payload The raw webhook payload
     * @param signature The signature from webhook headers
     * @return true if signature is valid
     */
    public boolean validateSignature(String payload, String signature) {
        if (defaultWebhookSecret == null || defaultWebhookSecret.isEmpty()) {
            log.error("Default webhook secret not configured");
            return false;
        }
        return validateSignature(payload, signature, defaultWebhookSecret);
    }

    /**
     * Validates GitHub-style webhook signature (sha256=...)
     * @param payload The raw webhook payload
     * @param signature The signature header value
     * @param secret The webhook secret
     * @return true if signature is valid
     */
    public boolean validateGitHubSignature(String payload, String signature, String secret) {
        if (!signature.startsWith("sha256=")) {
            log.warn("Invalid GitHub signature format: missing sha256= prefix");
            return false;
        }
        
        String actualSignature = signature.substring(7); // Remove "sha256=" prefix
        String expectedSignature = generateSignature(payload, secret);
        
        return secureCompare(actualSignature, expectedSignature);
    }

    /**
     * Validates Stripe-style webhook signature with timestamp
     * @param payload The raw webhook payload
     * @param signature The Stripe signature header
     * @param secret The webhook secret
     * @return true if signature is valid and within time tolerance
     */
    public boolean validateStripeSignature(String payload, String signature, String secret) {
        try {
            // Parse Stripe signature format: t=timestamp,v1=signature
            String[] parts = signature.split(",");
            String timestamp = null;
            String sig = null;
            
            for (String part : parts) {
                if (part.startsWith("t=")) {
                    timestamp = part.substring(2);
                } else if (part.startsWith("v1=")) {
                    sig = part.substring(3);
                }
            }
            
            if (timestamp == null || sig == null) {
                log.warn("Invalid Stripe signature format");
                return false;
            }
            
            // Check timestamp tolerance
            long webhookTime = Long.parseLong(timestamp);
            long currentTime = Instant.now().getEpochSecond();
            
            if (Math.abs(currentTime - webhookTime) > SIGNATURE_TOLERANCE_SECONDS) {
                log.warn("Webhook timestamp outside tolerance window");
                return false;
            }
            
            // Validate signature
            String signedPayload = timestamp + "." + payload;
            String expectedSignature = generateSignature(signedPayload, secret);
            
            return secureCompare(sig, expectedSignature);
            
        } catch (Exception e) {
            log.error("Error validating Stripe webhook signature", e);
            return false;
        }
    }

    /**
     * Validates webhook with custom header format
     * @param payload The raw webhook payload  
     * @param signature The signature value
     * @param secret The webhook secret
     * @param algorithm The HMAC algorithm to use
     * @return true if signature is valid
     */
    public boolean validateSignature(String payload, String signature, String secret, String algorithm) {
        try {
            String expectedSignature = generateSignature(payload, secret, algorithm);
            return secureCompare(signature, expectedSignature);
        } catch (Exception e) {
            log.error("Error validating webhook signature with algorithm: {}", algorithm, e);
            return false;
        }
    }

    /**
     * Generates HMAC-SHA256 signature for payload
     * @param payload The data to sign
     * @param secret The signing secret
     * @return Base64 encoded signature
     */
    private String generateSignature(String payload, String secret) {
        return generateSignature(payload, secret, HMAC_SHA256);
    }

    /**
     * Generates HMAC signature for payload with specified algorithm
     * @param payload The data to sign
     * @param secret The signing secret
     * @param algorithm The HMAC algorithm
     * @return Hex encoded signature
     */
    private String generateSignature(String payload, String secret, String algorithm) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm);
            mac.init(secretKeySpec);
            
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            
            // Return hex encoded signature (common for webhooks)
            StringBuilder hexString = new StringBuilder();
            for (byte b : signature) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new SecurityException("Failed to generate webhook signature", e);
        }
    }

    /**
     * Performs constant-time string comparison to prevent timing attacks
     * @param a First string
     * @param b Second string
     * @return true if strings are equal
     */
    private boolean secureCompare(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        
        if (a.length() != b.length()) {
            return false;
        }
        
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        
        return MessageDigest.isEqual(aBytes, bBytes);
    }

    /**
     * Validates webhook origin IP address against allowed ranges
     * @param clientIP The client IP address
     * @param allowedRanges Array of allowed IP ranges in CIDR notation
     * @return true if IP is in allowed range
     */
    public boolean validateWebhookIP(String clientIP, String[] allowedRanges) {
        if (clientIP == null || allowedRanges == null || allowedRanges.length == 0) {
            return false;
        }
        
        try {
            for (String range : allowedRanges) {
                if (isIPInRange(clientIP, range)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("Error validating webhook IP: {}", clientIP, e);
            return false;
        }
    }

    /**
     * Checks if IP address is within CIDR range
     * @param ip The IP address to check
     * @param cidr The CIDR range (e.g., "192.168.1.0/24")
     * @return true if IP is in range
     */
    private boolean isIPInRange(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                return false;
            }
            
            String rangeIP = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);
            
            // Convert IPs to integers for comparison
            long ipLong = ipToLong(ip);
            long rangeIPLong = ipToLong(rangeIP);
            
            // Create subnet mask
            long mask = (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;
            
            return (ipLong & mask) == (rangeIPLong & mask);
            
        } catch (Exception e) {
            log.warn("Error parsing CIDR range: {}", cidr, e);
            return false;
        }
    }

    /**
     * Converts IPv4 address to long
     * @param ip The IP address string
     * @return IP address as long
     */
    private long ipToLong(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IP address format");
        }
        
        long result = 0;
        for (int i = 0; i < 4; i++) {
            int octet = Integer.parseInt(parts[i]);
            if (octet < 0 || octet > 255) {
                throw new IllegalArgumentException("Invalid IP address octet");
            }
            result = (result << 8) | octet;
        }
        return result;
    }

    /**
     * Generates a secure webhook secret
     * @return Base64 encoded random secret
     */
    public static String generateWebhookSecret() {
        byte[] secretBytes = new byte[32]; // 256 bits
        new java.security.SecureRandom().nextBytes(secretBytes);
        return Base64.getEncoder().encodeToString(secretBytes);
    }
}