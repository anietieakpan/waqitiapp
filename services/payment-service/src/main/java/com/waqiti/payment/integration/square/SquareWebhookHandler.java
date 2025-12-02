package com.waqiti.payment.integration.square;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class SquareWebhookHandler {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;
    
    @Value("${square.webhook.signature.secret}")
    private String webhookSecret;
    
    @Value("${square.webhook.url}")
    private String notificationUrl;

    public void handleWebhook(String payload, String signature, String hmacSignature) {
        try {
            if (!validateSignature(payload, signature, hmacSignature)) {
                throw new SecurityException("Invalid Square webhook signature");
            }
            
            JsonNode webhook = objectMapper.readTree(payload);
            String eventType = webhook.path("type").asText();
            
            log.info("Processing Square webhook: {}", eventType);
            
            switch (eventType) {
                case "payment.created" -> handlePaymentCreated(webhook);
                case "payment.updated" -> handlePaymentUpdated(webhook);
                case "payment.failed" -> handlePaymentFailed(webhook);
                case "refund.created" -> handleRefundCreated(webhook);
                case "refund.updated" -> handleRefundUpdated(webhook);
                case "refund.failed" -> handleRefundFailed(webhook);
                case "dispute.created" -> handleDisputeCreated(webhook);
                case "dispute.state.updated" -> handleDisputeStateUpdated(webhook);
                default -> log.warn("Unhandled Square webhook event type: {}", eventType);
            }
            
        } catch (Exception e) {
            log.error("Failed to process Square webhook: ", e);
            throw new RuntimeException("Square webhook processing failed", e);
        }
    }

    private boolean validateSignature(String payload, String signature, String hmacSignature) {
        if (webhookSecret == null || webhookSecret.isEmpty()) {
            log.warn("Square webhook secret not configured - skipping signature validation");
            return true;
        }
        
        try {
            // Square uses both URL + body for signature validation
            String signatureBody = notificationUrl + payload;
            
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            );
            hmac.init(secretKey);
            
            byte[] hash = hmac.doFinal(signatureBody.getBytes(StandardCharsets.UTF_8));
            String computedSignature = Base64.getEncoder().encodeToString(hash);
            
            boolean isValid = computedSignature.equals(signature);
            
            // Also validate HMAC signature if provided
            if (hmacSignature != null && !hmacSignature.isEmpty()) {
                String computedHmac = HexFormat.of().formatHex(hash);
                isValid = isValid && computedHmac.equals(hmacSignature);
            }
            
            if (!isValid) {
                log.error("SECURITY: Square signature validation failed. Expected: {}, Got: {}", 
                    computedSignature, signature);
            }
            
            return isValid;
            
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error validating Square webhook signature: ", e);
            return false;
        }
    }

    private void handlePaymentCreated(JsonNode webhook) {
        JsonNode data = webhook.path("data").path("object");
        String paymentId = data.path("id").asText();
        String status = data.path("status").asText();
        
        log.info("Square payment created: id={}, status={}", paymentId, status);
        
        // Update payment status in our system
        paymentService.updatePaymentStatus(paymentId, mapSquareStatus(status));
    }

    private void handlePaymentUpdated(JsonNode webhook) {
        JsonNode data = webhook.path("data").path("object");
        String paymentId = data.path("id").asText();
        String status = data.path("status").asText();
        
        log.info("Square payment updated: id={}, status={}", paymentId, status);
        
        paymentService.updatePaymentStatus(paymentId, mapSquareStatus(status));
    }

    private void handlePaymentFailed(JsonNode webhook) {
        JsonNode data = webhook.path("data").path("object");
        String paymentId = data.path("id").asText();
        String errorCode = data.path("error_code").asText();
        String errorDetail = data.path("error_detail").asText();
        
        log.info("Square payment failed: id={}, error={}", paymentId, errorCode);
        
        paymentService.markPaymentFailed(paymentId, errorCode + ": " + errorDetail);
    }

    private void handleRefundCreated(JsonNode webhook) {
        JsonNode data = webhook.path("data").path("object");
        String refundId = data.path("id").asText();
        String paymentId = data.path("payment_id").asText();
        String status = data.path("status").asText();
        
        log.info("Square refund created: refund_id={}, payment_id={}, status={}", 
            refundId, paymentId, status);
        
        paymentService.updateRefundStatus(refundId, paymentId, mapSquareStatus(status));
    }

    private void handleRefundUpdated(JsonNode webhook) {
        JsonNode data = webhook.path("data").path("object");
        String refundId = data.path("id").asText();
        String paymentId = data.path("payment_id").asText();
        String status = data.path("status").asText();
        
        log.info("Square refund updated: refund_id={}, payment_id={}, status={}", 
            refundId, paymentId, status);
        
        paymentService.updateRefundStatus(refundId, paymentId, mapSquareStatus(status));
    }

    private void handleRefundFailed(JsonNode webhook) {
        JsonNode data = webhook.path("data").path("object");
        String refundId = data.path("id").asText();
        String errorCode = data.path("error_code").asText();
        
        log.info("Square refund failed: refund_id={}, error={}", refundId, errorCode);
        
        paymentService.markRefundFailed(refundId, errorCode);
    }

    private void handleDisputeCreated(JsonNode webhook) {
        JsonNode data = webhook.path("data").path("object");
        String disputeId = data.path("id").asText();
        String paymentId = data.path("disputed_payment").path("payment_id").asText();
        String reason = data.path("reason").asText();
        
        log.info("Square dispute created: dispute_id={}, payment_id={}, reason={}", 
            disputeId, paymentId, reason);
        
        paymentService.createDispute(disputeId, paymentId, reason);
    }

    private void handleDisputeStateUpdated(JsonNode webhook) {
        JsonNode data = webhook.path("data").path("object");
        String disputeId = data.path("id").asText();
        String state = data.path("state").asText();
        
        log.info("Square dispute state updated: dispute_id={}, state={}", disputeId, state);
        
        paymentService.updateDisputeStatus(disputeId, state);
    }

    private String mapSquareStatus(String squareStatus) {
        return switch (squareStatus) {
            case "COMPLETED" -> "COMPLETED";
            case "APPROVED" -> "PROCESSING";
            case "PENDING" -> "PENDING";
            case "CANCELED" -> "CANCELLED";
            case "FAILED" -> "FAILED";
            default -> "UNKNOWN";
        };
    }
}