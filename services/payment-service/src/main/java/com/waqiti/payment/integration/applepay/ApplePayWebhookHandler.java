package com.waqiti.payment.integration.applepay;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

/**
 * Apple Pay Webhook Handler for processing webhooks from Apple Pay API
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApplePayWebhookHandler {

    /**
     * Verify Apple Pay webhook signature using public key
     *
     * @param payload Webhook payload to verify
     * @param signature Base64-encoded signature from Apple Pay
     * @param publicKey Public key for verification (typically from Apple's certificate)
     * @return true if signature is valid, false otherwise
     */
    public boolean verifySignature(String payload, String signature, PublicKey publicKey) {
        try {
            // Apple Pay uses ECDSA with SHA-256 for webhook signature verification
            Signature sig = Signature.getInstance("SHA256withECDSA");
            sig.initVerify(publicKey);
            sig.update(payload.getBytes());

            byte[] signatureBytes = Base64.getDecoder().decode(signature);
            return sig.verify(signatureBytes);

        } catch (Exception e) {
            log.error("Failed to verify Apple Pay webhook signature: {}", e.getMessage());
            return false;
        }
    }
}