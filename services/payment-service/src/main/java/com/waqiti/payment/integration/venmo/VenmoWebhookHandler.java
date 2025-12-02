package com.waqiti.payment.integration.venmo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Venmo Webhook Handler for processing webhooks from Venmo API
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VenmoWebhookHandler {

    /**
     * Verify Venmo webhook signature
     */
    public boolean verifySignature(String payload, String signature, String secret) {
        try {
            // Venmo uses HMAC-SHA256 for webhook signature verification
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            mac.init(secretKeySpec);
            
            byte[] hash = mac.doFinal(payload.getBytes());
            String computedSignature = Base64.getEncoder().encodeToString(hash);
            
            return computedSignature.equals(signature);
            
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to verify Venmo webhook signature: {}", e.getMessage());
            return false;
        }
    }
}