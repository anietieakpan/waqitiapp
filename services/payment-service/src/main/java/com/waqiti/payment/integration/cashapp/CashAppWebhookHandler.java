package com.waqiti.payment.integration.cashapp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * CashApp Webhook Handler for processing webhooks from CashApp API
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CashAppWebhookHandler {

    /**
     * Verify CashApp webhook signature
     */
    public boolean verifySignature(String payload, String signature, String timestamp, String secret) {
        try {
            // CashApp uses HMAC-SHA256 for webhook signature verification
            String signatureString = timestamp + ":" + payload;
            
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            mac.init(secretKeySpec);
            
            byte[] hash = mac.doFinal(signatureString.getBytes());
            String computedSignature = "sha256=" + Base64.getEncoder().encodeToString(hash);
            
            return computedSignature.equals(signature);
            
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to verify CashApp webhook signature: {}", e.getMessage());
            return false;
        }
    }
}