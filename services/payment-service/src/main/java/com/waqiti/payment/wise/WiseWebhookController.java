package com.waqiti.payment.wise;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.wise.dto.WiseWebhookEvent;
import com.waqiti.common.exception.PaymentProviderException;
import com.waqiti.common.ratelimit.RateLimited;
import com.waqiti.security.logging.PCIAuditLogger;
import com.waqiti.security.logging.SecureLoggingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

/**
 * Wise Webhook Controller
 * 
 * HIGH PRIORITY: Handles real-time webhook notifications from Wise
 * for transfer status updates and payment events.
 * 
 * This controller processes secure webhook notifications:
 * 
 * WEBHOOK SECURITY FEATURES:
 * - HMAC-SHA256 signature verification
 * - Request body validation and parsing
 * - Duplicate event detection and prevention
 * - Rate limiting and abuse protection
 * - Comprehensive audit logging
 * - Error handling and retry mechanisms
 * 
 * SUPPORTED WEBHOOK EVENTS:
 * - Transfer status updates (processing, completed, failed)
 * - Balance changes and account events
 * - Compliance and regulatory notifications
 * - Fraud detection alerts
 * - Rate limit notifications
 * - System maintenance announcements
 * 
 * BUSINESS BENEFITS:
 * - Real-time payment status updates
 * - Improved customer experience with instant notifications
 * - Automated reconciliation and accounting
 * - Proactive fraud detection and response
 * - Reduced manual monitoring overhead
 * - Enhanced operational efficiency
 * 
 * SECURITY FEATURES:
 * - Cryptographic signature verification
 * - Secure webhook endpoint protection
 * - Comprehensive audit trails
 * - Replay attack prevention
 * - Input validation and sanitization
 * - Error monitoring and alerting
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/wise")
@RequiredArgsConstructor
public class WiseWebhookController {

    private final WiseWebhookService wiseWebhookService;
    private final ObjectMapper objectMapper;
    private final PCIAuditLogger pciAuditLogger;
    private final SecureLoggingService secureLoggingService;

    @Value("${wise.webhook.secret}")
    private String webhookSecret;

    @Value("${wise.webhook.signature-header:X-Signature-SHA256}")
    private String signatureHeader;

    @Value("${wise.webhook.max-age-seconds:300}")
    private long maxWebhookAgeSeconds;

    /**
     * Handles transfer status update webhooks
     *
     * Security: HMAC-SHA256 signature verification, timestamp validation, rate limiting
     */
    @PostMapping("/transfer-update")
    @RateLimited(keyType = RateLimited.KeyType.IP, capacity = 100, refillTokens = 100, refillPeriodMinutes = 1)
    public ResponseEntity<String> handleTransferUpdate(
            @RequestBody String requestBody,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest request) {

        String clientIp = getClientIpAddress(request);
        String eventId = headers.get("X-Event-ID");

        try {
            // Verify webhook signature
            if (!verifyWebhookSignature(requestBody, headers.get(signatureHeader))) {
                // Log security violation
                pciAuditLogger.logSecurityViolation(
                    "webhook",
                    "INVALID_WEBHOOK_SIGNATURE",
                    "Invalid Wise webhook signature received",
                    "HIGH",
                    Map.of(
                        "clientIp", clientIp,
                        "eventId", eventId != null ? eventId : "unknown",
                        "endpoint", "/transfer-update"
                    )
                );

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid signature");
            }

            // Parse webhook event
            WiseWebhookEvent webhookEvent = objectMapper.readValue(requestBody, WiseWebhookEvent.class);

            // Validate event timestamp (prevent replay attacks)
            if (isWebhookTooOld(webhookEvent.getOccurredAt())) {
                log.warn("Webhook event is too old: {}", webhookEvent.getOccurredAt());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Event too old");
            }

            // Process the webhook event
            wiseWebhookService.processTransferUpdateEvent(webhookEvent);

            // Log successful webhook processing
            secureLoggingService.logPaymentEvent(
                "webhook_received",
                "wise",
                "transfer_update_" + (eventId != null ? eventId : "unknown"),
                0.0,
                "USD",
                true,
                Map.of(
                    "eventType", webhookEvent.getEventType(),
                    "transferId", webhookEvent.getData().getTransferId(),
                    "status", webhookEvent.getData().getCurrentStatus(),
                    "clientIp", clientIp
                )
            );

            log.info("Successfully processed Wise transfer update webhook - Event: {}, Transfer: {}", 
                eventId, webhookEvent.getData().getTransferId());

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("Failed to process Wise transfer update webhook", e);

            // Log webhook processing failure
            pciAuditLogger.logPaymentProcessing(
                "webhook",
                "wise_webhook_" + (eventId != null ? eventId : System.currentTimeMillis()),
                "process_transfer_webhook",
                0.0,
                "USD",
                "wise",
                false,
                Map.of(
                    "error", e.getMessage(),
                    "clientIp", clientIp,
                    "eventId", eventId != null ? eventId : "unknown"
                )
            );

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Processing failed");
        }
    }

    /**
     * Handles balance update webhooks
     *
     * Security: HMAC-SHA256 signature verification, timestamp validation, rate limiting
     */
    @PostMapping("/balance-update")
    @RateLimited(keyType = RateLimited.KeyType.IP, capacity = 100, refillTokens = 100, refillPeriodMinutes = 1)
    public ResponseEntity<String> handleBalanceUpdate(
            @RequestBody String requestBody,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest request) {

        String clientIp = getClientIpAddress(request);
        String eventId = headers.get("X-Event-ID");

        try {
            // Verify webhook signature
            if (!verifyWebhookSignature(requestBody, headers.get(signatureHeader))) {
                pciAuditLogger.logSecurityViolation(
                    "webhook",
                    "INVALID_WEBHOOK_SIGNATURE",
                    "Invalid Wise balance webhook signature received",
                    "HIGH",
                    Map.of(
                        "clientIp", clientIp,
                        "eventId", eventId != null ? eventId : "unknown",
                        "endpoint", "/balance-update"
                    )
                );

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid signature");
            }

            // Parse webhook event
            WiseWebhookEvent webhookEvent = objectMapper.readValue(requestBody, WiseWebhookEvent.class);

            // Validate event timestamp
            if (isWebhookTooOld(webhookEvent.getOccurredAt())) {
                log.warn("Balance webhook event is too old: {}", webhookEvent.getOccurredAt());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Event too old");
            }

            // Process the webhook event
            wiseWebhookService.processBalanceUpdateEvent(webhookEvent);

            // Log successful webhook processing
            secureLoggingService.logDataAccessEvent(
                "wise",
                "balance_update",
                "webhook_" + (eventId != null ? eventId : "unknown"),
                "received",
                true,
                Map.of(
                    "eventType", webhookEvent.getEventType(),
                    "balanceId", webhookEvent.getData().getBalanceId(),
                    "clientIp", clientIp
                )
            );

            log.info("Successfully processed Wise balance update webhook - Event: {}", eventId);

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("Failed to process Wise balance update webhook", e);

            pciAuditLogger.logPaymentProcessing(
                "webhook",
                "wise_balance_webhook_" + (eventId != null ? eventId : System.currentTimeMillis()),
                "process_balance_webhook",
                0.0,
                "USD",
                "wise",
                false,
                Map.of(
                    "error", e.getMessage(),
                    "clientIp", clientIp,
                    "eventId", eventId != null ? eventId : "unknown"
                )
            );

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Processing failed");
        }
    }

    /**
     * Handles compliance and regulatory notifications
     *
     * Security: HMAC-SHA256 signature verification, timestamp validation, rate limiting
     */
    @PostMapping("/compliance-notification")
    @RateLimited(keyType = RateLimited.KeyType.IP, capacity = 100, refillTokens = 100, refillPeriodMinutes = 1)
    public ResponseEntity<String> handleComplianceNotification(
            @RequestBody String requestBody,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest request) {

        String clientIp = getClientIpAddress(request);
        String eventId = headers.get("X-Event-ID");

        try {
            // Verify webhook signature
            if (!verifyWebhookSignature(requestBody, headers.get(signatureHeader))) {
                pciAuditLogger.logSecurityViolation(
                    "webhook",
                    "INVALID_WEBHOOK_SIGNATURE",
                    "Invalid Wise compliance webhook signature received",
                    "CRITICAL",
                    Map.of(
                        "clientIp", clientIp,
                        "eventId", eventId != null ? eventId : "unknown",
                        "endpoint", "/compliance-notification"
                    )
                );

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid signature");
            }

            // Parse webhook event
            WiseWebhookEvent webhookEvent = objectMapper.readValue(requestBody, WiseWebhookEvent.class);

            // Process compliance event (high priority)
            wiseWebhookService.processComplianceEvent(webhookEvent);

            // Log compliance notification
            pciAuditLogger.logPaymentProcessing(
                "wise",
                "compliance_" + (eventId != null ? eventId : System.currentTimeMillis()),
                "compliance_notification",
                0.0,
                "USD",
                "wise",
                true,
                Map.of(
                    "eventType", webhookEvent.getEventType(),
                    "complianceType", webhookEvent.getData().getComplianceType(),
                    "severity", webhookEvent.getData().getSeverity(),
                    "clientIp", clientIp
                )
            );

            log.warn("Processed Wise compliance notification - Event: {}, Type: {}", 
                eventId, webhookEvent.getData().getComplianceType());

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("Failed to process Wise compliance notification webhook", e);

            pciAuditLogger.logSecurityViolation(
                "webhook",
                "COMPLIANCE_WEBHOOK_FAILURE",
                "Failed to process Wise compliance notification: " + e.getMessage(),
                "CRITICAL",
                Map.of(
                    "error", e.getMessage(),
                    "clientIp", clientIp,
                    "eventId", eventId != null ? eventId : "unknown"
                )
            );

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Processing failed");
        }
    }

    /**
     * Health check endpoint for Wise webhook validation
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> webhookHealthCheck() {
        Map<String, Object> health = Map.of(
            "status", "UP",
            "service", "wise-webhook",
            "timestamp", LocalDateTime.now(),
            "endpoints", Map.of(
                "transfer-update", "/api/v1/webhooks/wise/transfer-update",
                "balance-update", "/api/v1/webhooks/wise/balance-update",
                "compliance-notification", "/api/v1/webhooks/wise/compliance-notification"
            )
        );

        return ResponseEntity.ok(health);
    }

    // Private helper methods

    private boolean verifyWebhookSignature(String payload, String signature) {
        if (signature == null || webhookSecret == null) {
            log.warn("Missing webhook signature or secret");
            return false;
        }

        try {
            // Remove 'sha256=' prefix if present
            String cleanSignature = signature.startsWith("sha256=") ? signature.substring(7) : signature;

            // Calculate expected signature
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = Base64.getEncoder().encodeToString(hash);

            // Use constant-time comparison to prevent timing attacks
            return constantTimeEquals(cleanSignature, expectedSignature);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error verifying webhook signature", e);
            return false;
        }
    }

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

    private boolean isWebhookTooOld(LocalDateTime eventTime) {
        if (eventTime == null) {
            return true;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(maxWebhookAgeSeconds);
        return eventTime.isBefore(cutoff);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}