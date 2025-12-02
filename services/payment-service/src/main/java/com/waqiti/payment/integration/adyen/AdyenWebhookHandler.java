package com.waqiti.payment.integration.adyen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdyenWebhookHandler {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @Value("${adyen.webhook.hmac.key}")
    private String hmacKey;

    @Value("${adyen.webhook.rate-limit:100}")
    private int maxRequestsPerMinute;

    // Rate limiting
    private final Map<String, LocalDateTime> requestWindows = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();

    public boolean handleWebhook(String payload, String signature) {
        try {
            if (!validateHmacSignature(payload, signature)) {
                log.error("SECURITY: Invalid Adyen webhook HMAC signature");
                return false;
            }
            
            JsonNode webhook = objectMapper.readTree(payload);
            
            // Adyen sends multiple notification items in one webhook
            JsonNode notificationItems = webhook.path("notificationItems");
            
            for (JsonNode item : notificationItems) {
                JsonNode notificationRequestItem = item.path("NotificationRequestItem");
                processNotificationItem(notificationRequestItem);
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to process Adyen webhook: ", e);
            return false;
        }
    }

    private void processNotificationItem(JsonNode notification) {
        String eventCode = notification.path("eventCode").asText();
        String pspReference = notification.path("pspReference").asText();
        String merchantReference = notification.path("merchantReference").asText();
        boolean success = notification.path("success").asBoolean();
        
        log.info("Processing Adyen notification: event={}, psp_ref={}, success={}", 
            eventCode, pspReference, success);
        
        switch (eventCode) {
            case "AUTHORISATION" -> handleAuthorisation(notification, success);
            case "CANCELLATION" -> handleCancellation(notification);
            case "REFUND" -> handleRefund(notification, success);
            case "CAPTURE" -> handleCapture(notification, success);
            case "REFUND_FAILED" -> handleRefundFailed(notification);
            case "CAPTURE_FAILED" -> handleCaptureFailed(notification);
            case "DISPUTE_OPENED" -> handleDisputeOpened(notification);
            case "DISPUTE_CLOSED" -> handleDisputeClosed(notification);
            case "CHARGEBACK" -> handleChargeback(notification);
            default -> log.warn("Unhandled Adyen event code: {}", eventCode);
        }
    }

    private void handleAuthorisation(JsonNode notification, boolean success) {
        String pspReference = notification.path("pspReference").asText();
        String merchantReference = notification.path("merchantReference").asText();
        
        if (success) {
            log.info("Adyen payment authorized: psp_ref={}, merchant_ref={}", pspReference, merchantReference);
            paymentService.updatePaymentStatus(pspReference, "COMPLETED");
        } else {
            String reason = notification.path("reason").asText();
            log.info("Adyen payment failed: psp_ref={}, reason={}", pspReference, reason);
            paymentService.markPaymentFailed(pspReference, reason);
        }
    }

    private void handleCancellation(JsonNode notification) {
        String pspReference = notification.path("pspReference").asText();
        String originalReference = notification.path("originalReference").asText();
        
        log.info("Adyen payment cancelled: psp_ref={}, original_ref={}", pspReference, originalReference);
        paymentService.updatePaymentStatus(originalReference, "CANCELLED");
    }

    private void handleRefund(JsonNode notification, boolean success) {
        String pspReference = notification.path("pspReference").asText();
        String originalReference = notification.path("originalReference").asText();
        
        if (success) {
            log.info("Adyen refund processed: psp_ref={}, original_ref={}", pspReference, originalReference);
            paymentService.updateRefundStatus(pspReference, originalReference, "REFUNDED");
        } else {
            String reason = notification.path("reason").asText();
            log.info("Adyen refund failed: psp_ref={}, reason={}", pspReference, reason);
            paymentService.markRefundFailed(pspReference, reason);
        }
    }

    private void handleCapture(JsonNode notification, boolean success) {
        String pspReference = notification.path("pspReference").asText();
        String originalReference = notification.path("originalReference").asText();
        
        if (success) {
            log.info("Adyen payment captured: psp_ref={}, original_ref={}", pspReference, originalReference);
            paymentService.updatePaymentStatus(originalReference, "COMPLETED");
        } else {
            String reason = notification.path("reason").asText();
            log.info("Adyen capture failed: psp_ref={}, reason={}", pspReference, reason);
            paymentService.markPaymentFailed(originalReference, "Capture failed: " + reason);
        }
    }

    private void handleRefundFailed(JsonNode notification) {
        String pspReference = notification.path("pspReference").asText();
        String reason = notification.path("reason").asText();
        
        log.info("Adyen refund failed: psp_ref={}, reason={}", pspReference, reason);
        paymentService.markRefundFailed(pspReference, reason);
    }

    private void handleCaptureFailed(JsonNode notification) {
        String pspReference = notification.path("pspReference").asText();
        String originalReference = notification.path("originalReference").asText();
        String reason = notification.path("reason").asText();
        
        log.info("Adyen capture failed: psp_ref={}, original_ref={}, reason={}", 
            pspReference, originalReference, reason);
        paymentService.markPaymentFailed(originalReference, "Capture failed: " + reason);
    }

    private void handleDisputeOpened(JsonNode notification) {
        String pspReference = notification.path("pspReference").asText();
        String originalReference = notification.path("originalReference").asText();
        String reason = notification.path("reason").asText();
        
        log.info("Adyen dispute opened: psp_ref={}, original_ref={}, reason={}", 
            pspReference, originalReference, reason);
        paymentService.createDispute(pspReference, originalReference, reason);
    }

    private void handleDisputeClosed(JsonNode notification) {
        String pspReference = notification.path("pspReference").asText();
        String originalReference = notification.path("originalReference").asText();
        
        log.info("Adyen dispute closed: psp_ref={}, original_ref={}", pspReference, originalReference);
        paymentService.updateDisputeStatus(pspReference, "CLOSED");
    }

    private void handleChargeback(JsonNode notification) {
        String pspReference = notification.path("pspReference").asText();
        String originalReference = notification.path("originalReference").asText();
        
        log.info("Adyen chargeback: psp_ref={}, original_ref={}", pspReference, originalReference);
        paymentService.createChargeback(pspReference, originalReference);
    }

    private boolean validateHmacSignature(String payload, String signature) {
        if (hmacKey == null || hmacKey.isEmpty()) {
            log.warn("Adyen webhook HMAC key not configured - skipping signature validation");
            return true;
        }
        
        if (signature == null || signature.isEmpty()) {
            log.error("SECURITY: Missing Adyen HMAC signature");
            return false;
        }
        
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                Base64.getDecoder().decode(hmacKey), "HmacSHA256"
            );
            hmac.init(secretKey);
            
            byte[] hash = hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedSignature = Base64.getEncoder().encodeToString(hash);
            
            boolean isValid = computedSignature.equals(signature);
            
            if (!isValid) {
                log.error("SECURITY: Adyen HMAC signature validation failed. Expected: {}, Got: {}", 
                    computedSignature, signature);
            }
            
            return isValid;
            
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error validating Adyen webhook signature: ", e);
            return false;
        }
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        String xRealIp = request.getHeader("X-Real-IP");
        
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    private boolean checkRateLimit(String clientIp) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = requestWindows.get(clientIp);
        
        if (windowStart == null || windowStart.isBefore(now.minusMinutes(1))) {
            requestWindows.put(clientIp, now);
            requestCounts.put(clientIp, new AtomicInteger(1));
            return true;
        }
        
        AtomicInteger count = requestCounts.get(clientIp);
        if (count == null) {
            requestCounts.put(clientIp, new AtomicInteger(1));
            return true;
        }
        
        return count.incrementAndGet() <= maxRequestsPerMinute;
    }
    
    private boolean isValidJsonPayload(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = payload.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) || 
               (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }
    
    private void auditSecurityViolation(String clientIp, String violationType, String details) {
        log.error("SECURITY AUDIT: Adyen Webhook Violation - Type: {}, IP: {}, Details: {}, Timestamp: {}", 
            violationType, clientIp, details, LocalDateTime.now());
    }
}