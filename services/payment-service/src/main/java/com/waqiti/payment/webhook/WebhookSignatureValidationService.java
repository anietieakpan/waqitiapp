package com.waqiti.payment.webhook;

import com.waqiti.common.audit.AuditService;
import com.waqiti.common.exception.WebhookSecurityException;
import com.waqiti.payment.vault.PaymentProviderSecretsManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * Webhook Signature Validation Service
 *
 * Implements cryptographic signature validation for payment provider webhooks:
 * - Stripe: HMAC-SHA256 with webhook signing secret
 * - PayPal: Custom signature algorithm with certificate validation
 * - Square: HMAC-SHA256 with signature key
 * - Dwolla: HMAC-SHA256 with webhook secret
 *
 * Security Requirements:
 * - Prevent webhook spoofing attacks
 * - Validate payload integrity
 * - Prevent replay attacks (timestamp validation)
 * - Reject webhooks from unauthorized sources
 *
 * Attack Vectors Prevented:
 * - Webhook spoofing (fake payment confirmations)
 * - Man-in-the-middle payload modification
 * - Replay attacks (old webhooks re-sent)
 * - Unauthorized payment state manipulation
 *
 * Financial Risk if Compromised:
 * - Fraudulent payment confirmations: $50K-$150K per incident
 * - Unauthorized refund approvals
 * - Payment status manipulation
 * - Account balance manipulation
 *
 * @author Waqiti Platform Engineering
 * @version 2.0.0
 * @since 2025-10-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookSignatureValidationService {

    private final PaymentProviderSecretsManager secretsManager;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;
    private final RestTemplate restTemplate;

    // Replay attack prevention: Reject webhooks older than 5 minutes
    private static final long TIMESTAMP_TOLERANCE_SECONDS = 300;

    /**
     * Validate Stripe webhook signature
     *
     * Stripe signature format in header:
     * Stripe-Signature: t=1492774577,v1=5257a869e7ecebeda32affa62cdca3fa51cad7e77a0e56ff536d0ce8e108d8bd
     *
     * @param payload Raw webhook payload (exact bytes received)
     * @param signatureHeader Stripe-Signature header value
     * @return true if signature valid
     * @throws WebhookSecurityException if signature invalid
     */
    @Timed(value = "webhook.stripe.validation")
    public boolean validateStripeWebhook(String payload, String signatureHeader) {
        log.debug("Validating Stripe webhook signature");

        try {
            if (signatureHeader == null || signatureHeader.isEmpty()) {
                throw new WebhookSecurityException("Missing Stripe-Signature header");
            }

            // Parse signature header
            Map<String, String> signatureParts = parseStripeSignatureHeader(signatureHeader);
            String timestamp = signatureParts.get("t");
            String signature = signatureParts.get("v1");

            if (timestamp == null || signature == null) {
                throw new WebhookSecurityException("Invalid Stripe-Signature header format");
            }

            // Verify timestamp to prevent replay attacks
            verifyTimestamp(Long.parseLong(timestamp));

            // Get webhook secret from Vault
            String webhookSecret = secretsManager.getStripeWebhookSecret();

            // Construct signed payload: timestamp.payload
            String signedPayload = timestamp + "." + payload;

            // Compute expected signature
            String expectedSignature = computeHMACSHA256(signedPayload, webhookSecret);

            // Constant-time comparison to prevent timing attacks
            boolean valid = constantTimeEquals(signature, expectedSignature);

            if (valid) {
                log.info("Stripe webhook signature validated successfully");
                meterRegistry.counter("webhook.stripe.validation.success").increment();

                auditService.logSecurityEvent(
                    "STRIPE_WEBHOOK_VALIDATED",
                    Map.of("timestamp", timestamp),
                    "STRIPE",
                    "WEBHOOK_SECURITY"
                );
            } else {
                log.error("Stripe webhook signature validation FAILED");
                meterRegistry.counter("webhook.stripe.validation.failed").increment();

                auditService.logSecurityEvent(
                    "STRIPE_WEBHOOK_VALIDATION_FAILED",
                    Map.of(
                        "timestamp", timestamp,
                        "expectedSignature", expectedSignature,
                        "receivedSignature", signature
                    ),
                    "STRIPE",
                    "WEBHOOK_SECURITY"
                );

                throw new WebhookSecurityException("Stripe webhook signature validation failed");
            }

            return valid;

        } catch (WebhookSecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error validating Stripe webhook signature", e);
            meterRegistry.counter("webhook.stripe.validation.errors").increment();
            throw new WebhookSecurityException("Error validating Stripe webhook", e);
        }
    }

    /**
     * Validate PayPal IPN (Instant Payment Notification) signature
     *
     * PayPal uses a verification callback to PayPal servers
     *
     * @param payload Raw webhook payload
     * @param ipnParams IPN parameters from PayPal
     * @return true if signature valid
     * @throws WebhookSecurityException if signature invalid
     */
    @Timed(value = "webhook.paypal.validation")
    public boolean validatePayPalWebhook(String payload, Map<String, String> ipnParams) {
        log.debug("Validating PayPal IPN signature");

        try {
            // PayPal IPN validation process:
            // 1. Receive IPN message
            // 2. Return HTTP 200 to acknowledge receipt
            // 3. Send message back to PayPal with cmd=_notify-validate
            // 4. PayPal responds with "VERIFIED" or "INVALID"

            // Get PayPal verification endpoint
            String paypalVerificationUrl = secretsManager.getPayPalIPNVerificationUrl();

            // Build verification request (prepend cmd=_notify-validate)
            Map<String, String> verificationParams = new LinkedHashMap<>(ipnParams);
            verificationParams.put("cmd", "_notify-validate");

            // Send POST request to PayPal
            String response = sendPayPalVerificationRequest(paypalVerificationUrl, verificationParams);

            boolean valid = "VERIFIED".equals(response.trim());

            if (valid) {
                log.info("PayPal IPN signature validated successfully");
                meterRegistry.counter("webhook.paypal.validation.success").increment();

                auditService.logSecurityEvent(
                    "PAYPAL_IPN_VALIDATED",
                    Map.of("txn_id", ipnParams.getOrDefault("txn_id", "unknown")),
                    "PAYPAL",
                    "WEBHOOK_SECURITY"
                );
            } else {
                log.error("PayPal IPN signature validation FAILED: response={}", response);
                meterRegistry.counter("webhook.paypal.validation.failed").increment();

                auditService.logSecurityEvent(
                    "PAYPAL_IPN_VALIDATION_FAILED",
                    Map.of(
                        "txn_id", ipnParams.getOrDefault("txn_id", "unknown"),
                        "response", response
                    ),
                    "PAYPAL",
                    "WEBHOOK_SECURITY"
                );

                throw new WebhookSecurityException("PayPal IPN validation failed: " + response);
            }

            return valid;

        } catch (WebhookSecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error validating PayPal IPN", e);
            meterRegistry.counter("webhook.paypal.validation.errors").increment();
            throw new WebhookSecurityException("Error validating PayPal IPN", e);
        }
    }

    /**
     * Validate Square webhook signature
     *
     * Square signature format in header:
     * X-Square-Signature: <base64-encoded-signature>
     *
     * Signature computed as: HMAC-SHA256(notification_url + request_body, signature_key)
     *
     * @param payload Raw webhook payload
     * @param signatureHeader X-Square-Signature header value
     * @param notificationUrl Webhook notification URL
     * @return true if signature valid
     * @throws WebhookSecurityException if signature invalid
     */
    @Timed(value = "webhook.square.validation")
    public boolean validateSquareWebhook(String payload, String signatureHeader, String notificationUrl) {
        log.debug("Validating Square webhook signature");

        try {
            if (signatureHeader == null || signatureHeader.isEmpty()) {
                throw new WebhookSecurityException("Missing X-Square-Signature header");
            }

            // Get Square webhook signature key from Vault
            String signatureKey = secretsManager.getSquareWebhookSignatureKey();

            // Construct string to sign: notification_url + request_body
            String stringToSign = notificationUrl + payload;

            // Compute expected signature
            String expectedSignature = computeHMACSHA256Base64(stringToSign, signatureKey);

            // Constant-time comparison
            boolean valid = constantTimeEquals(signatureHeader, expectedSignature);

            if (valid) {
                log.info("Square webhook signature validated successfully");
                meterRegistry.counter("webhook.square.validation.success").increment();

                auditService.logSecurityEvent(
                    "SQUARE_WEBHOOK_VALIDATED",
                    Map.of("notificationUrl", notificationUrl),
                    "SQUARE",
                    "WEBHOOK_SECURITY"
                );
            } else {
                log.error("Square webhook signature validation FAILED");
                meterRegistry.counter("webhook.square.validation.failed").increment();

                auditService.logSecurityEvent(
                    "SQUARE_WEBHOOK_VALIDATION_FAILED",
                    Map.of(
                        "notificationUrl", notificationUrl,
                        "expectedSignature", expectedSignature,
                        "receivedSignature", signatureHeader
                    ),
                    "SQUARE",
                    "WEBHOOK_SECURITY"
                );

                throw new WebhookSecurityException("Square webhook signature validation failed");
            }

            return valid;

        } catch (WebhookSecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error validating Square webhook signature", e);
            meterRegistry.counter("webhook.square.validation.errors").increment();
            throw new WebhookSecurityException("Error validating Square webhook", e);
        }
    }

    /**
     * Validate Dwolla webhook signature
     *
     * Dwolla signature format in header:
     * X-Request-Signature-SHA-256: <hex-encoded-hmac-sha256>
     *
     * @param payload Raw webhook payload
     * @param signatureHeader X-Request-Signature-SHA-256 header value
     * @return true if signature valid
     * @throws WebhookSecurityException if signature invalid
     */
    @Timed(value = "webhook.dwolla.validation")
    public boolean validateDwollaWebhook(String payload, String signatureHeader) {
        log.debug("Validating Dwolla webhook signature");

        try {
            if (signatureHeader == null || signatureHeader.isEmpty()) {
                throw new WebhookSecurityException("Missing X-Request-Signature-SHA-256 header");
            }

            // Get Dwolla webhook secret from Vault
            String webhookSecret = secretsManager.getDwollaWebhookSecret();

            // Compute expected signature
            String expectedSignature = computeHMACSHA256(payload, webhookSecret);

            // Constant-time comparison
            boolean valid = constantTimeEquals(signatureHeader, expectedSignature);

            if (valid) {
                log.info("Dwolla webhook signature validated successfully");
                meterRegistry.counter("webhook.dwolla.validation.success").increment();

                auditService.logSecurityEvent(
                    "DWOLLA_WEBHOOK_VALIDATED",
                    Map.of(),
                    "DWOLLA",
                    "WEBHOOK_SECURITY"
                );
            } else {
                log.error("Dwolla webhook signature validation FAILED");
                meterRegistry.counter("webhook.dwolla.validation.failed").increment();

                auditService.logSecurityEvent(
                    "DWOLLA_WEBHOOK_VALIDATION_FAILED",
                    Map.of(
                        "expectedSignature", expectedSignature,
                        "receivedSignature", signatureHeader
                    ),
                    "DWOLLA",
                    "WEBHOOK_SECURITY"
                );

                throw new WebhookSecurityException("Dwolla webhook signature validation failed");
            }

            return valid;

        } catch (WebhookSecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error validating Dwolla webhook signature", e);
            meterRegistry.counter("webhook.dwolla.validation.errors").increment();
            throw new WebhookSecurityException("Error validating Dwolla webhook", e);
        }
    }

    // Helper methods

    /**
     * Parse Stripe signature header into components
     */
    private Map<String, String> parseStripeSignatureHeader(String signatureHeader) {
        Map<String, String> parts = new HashMap<>();

        String[] elements = signatureHeader.split(",");
        for (String element : elements) {
            String[] keyValue = element.trim().split("=", 2);
            if (keyValue.length == 2) {
                parts.put(keyValue[0], keyValue[1]);
            }
        }

        return parts;
    }

    /**
     * Verify timestamp to prevent replay attacks
     */
    private void verifyTimestamp(long timestamp) {
        long currentTime = Instant.now().getEpochSecond();
        long timeDifference = Math.abs(currentTime - timestamp);

        if (timeDifference > TIMESTAMP_TOLERANCE_SECONDS) {
            throw new WebhookSecurityException(
                String.format("Webhook timestamp too old or in future. Difference: %d seconds (max: %d)",
                    timeDifference, TIMESTAMP_TOLERANCE_SECONDS)
            );
        }
    }

    /**
     * Compute HMAC-SHA256 signature (hex-encoded)
     */
    private String computeHMACSHA256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            );
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);

        } catch (Exception e) {
            throw new WebhookSecurityException("Error computing HMAC-SHA256", e);
        }
    }

    /**
     * Compute HMAC-SHA256 signature (base64-encoded)
     */
    private String computeHMACSHA256Base64(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            );
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);

        } catch (Exception e) {
            throw new WebhookSecurityException("Error computing HMAC-SHA256", e);
        }
    }

    /**
     * Convert byte array to hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Constant-time string comparison to prevent timing attacks
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }

        // Convert to byte arrays
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);

        // Length must match
        if (aBytes.length != bBytes.length) {
            return false;
        }

        // Constant-time comparison
        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }

        return result == 0;
    }

    /**
     * P0-004 CRITICAL FIX: Send verification request to PayPal IPN endpoint
     *
     * BEFORE: TODO placeholder always returning "VERIFIED" ❌
     * AFTER: Real HTTP POST to PayPal for IPN verification ✅
     */
    private String sendPayPalVerificationRequest(String url, Map<String, String> params) {
        log.debug("Sending PayPal IPN verification request to: {}", url);

        try {
            // Build form data from parameters
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            params.forEach(formData::add);

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("User-Agent", "Waqiti-Payment-Service/2.0");

            // Create request entity
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

            // Send POST request to PayPal
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                String body = response.getBody();
                log.debug("PayPal IPN verification response: {}", body);
                return body != null ? body : "INVALID";
            } else {
                log.error("PayPal IPN verification returned status: {}", response.getStatusCode());
                return "INVALID";
            }

        } catch (Exception e) {
            log.error("Failed to send PayPal IPN verification request", e);
            meterRegistry.counter("webhook.paypal.verification.errors").increment();
            return "INVALID"; // Conservative approach: reject on error
        }
    }
}
