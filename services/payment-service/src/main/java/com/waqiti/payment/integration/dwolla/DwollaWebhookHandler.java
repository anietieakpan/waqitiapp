package com.waqiti.payment.integration.dwolla;

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
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class DwollaWebhookHandler {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;
    
    @Value("${dwolla.webhook.secret}")
    private String webhookSecret;

    public boolean handleWebhook(String payload, String signature) {
        try {
            if (!validateSignature(payload, signature)) {
                log.error("SECURITY: Invalid Dwolla webhook signature");
                return false;
            }
            
            JsonNode webhook = objectMapper.readTree(payload);
            String topic = webhook.path("topic").asText();
            
            log.info("Processing Dwolla webhook: {}", topic);
            
            switch (topic) {
                case "transfer_completed" -> handleTransferCompleted(webhook);
                case "transfer_failed" -> handleTransferFailed(webhook);
                case "transfer_cancelled" -> handleTransferCancelled(webhook);
                case "transfer_created" -> handleTransferCreated(webhook);
                case "customer_verification_document_needed" -> handleDocumentNeeded(webhook);
                case "customer_verification_document_uploaded" -> handleDocumentUploaded(webhook);
                case "customer_verification_document_approved" -> handleDocumentApproved(webhook);
                case "customer_verification_document_failed" -> handleDocumentFailed(webhook);
                case "customer_suspended" -> handleCustomerSuspended(webhook);
                case "customer_activated" -> handleCustomerActivated(webhook);
                case "funding_source_added" -> handleFundingSourceAdded(webhook);
                case "funding_source_removed" -> handleFundingSourceRemoved(webhook);
                case "funding_source_verified" -> handleFundingSourceVerified(webhook);
                case "funding_source_unverified" -> handleFundingSourceUnverified(webhook);
                case "bank_transfer_created" -> handleBankTransferCreated(webhook);
                case "bank_transfer_completed" -> handleBankTransferCompleted(webhook);
                case "bank_transfer_failed" -> handleBankTransferFailed(webhook);
                case "mass_payment_created" -> handleMassPaymentCreated(webhook);
                case "mass_payment_completed" -> handleMassPaymentCompleted(webhook);
                case "mass_payment_cancelled" -> handleMassPaymentCancelled(webhook);
                default -> log.warn("Unhandled Dwolla webhook topic: {}", topic);
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to process Dwolla webhook: ", e);
            return false;
        }
    }

    private boolean validateSignature(String payload, String signature) {
        if (webhookSecret == null || webhookSecret.isEmpty()) {
            log.warn("Dwolla webhook secret not configured - skipping signature validation");
            return true;
        }
        
        if (signature == null || signature.isEmpty()) {
            log.error("SECURITY: Missing Dwolla webhook signature");
            return false;
        }
        
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            );
            hmac.init(secretKey);
            
            byte[] hash = hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedSignature = HexFormat.of().formatHex(hash);
            
            boolean isValid = computedSignature.equalsIgnoreCase(signature);
            
            if (!isValid) {
                log.error("SECURITY: Dwolla signature validation failed. Expected: {}, Got: {}", 
                    computedSignature, signature);
            }
            
            return isValid;
            
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error validating Dwolla webhook signature: ", e);
            return false;
        }
    }

    private void handleTransferCompleted(JsonNode webhook) {
        String transferId = extractResourceId(webhook, "transfers");
        log.info("Dwolla transfer completed: {}", transferId);
        paymentService.updatePaymentStatus(transferId, "COMPLETED");
    }

    private void handleTransferFailed(JsonNode webhook) {
        String transferId = extractResourceId(webhook, "transfers");
        JsonNode resource = webhook.path("_embedded").path("resource");
        String failureReason = resource.path("failure_reason").asText();
        
        log.info("Dwolla transfer failed: {}, reason: {}", transferId, failureReason);
        paymentService.markPaymentFailed(transferId, failureReason);
    }

    private void handleTransferCancelled(JsonNode webhook) {
        String transferId = extractResourceId(webhook, "transfers");
        log.info("Dwolla transfer cancelled: {}", transferId);
        paymentService.updatePaymentStatus(transferId, "CANCELLED");
    }

    private void handleTransferCreated(JsonNode webhook) {
        String transferId = extractResourceId(webhook, "transfers");
        log.info("Dwolla transfer created: {}", transferId);
        paymentService.updatePaymentStatus(transferId, "PROCESSING");
    }

    private void handleDocumentNeeded(JsonNode webhook) {
        String customerId = extractResourceId(webhook, "customers");
        log.info("Dwolla customer verification document needed: {}", customerId);
        // Notify user that additional documentation is required
        paymentService.requestCustomerDocumentation(customerId);
    }

    private void handleDocumentUploaded(JsonNode webhook) {
        String customerId = extractResourceId(webhook, "customers");
        log.info("Dwolla customer verification document uploaded: {}", customerId);
    }

    private void handleDocumentApproved(JsonNode webhook) {
        String customerId = extractResourceId(webhook, "customers");
        log.info("Dwolla customer verification document approved: {}", customerId);
        paymentService.activateCustomerAccount(customerId);
    }

    private void handleDocumentFailed(JsonNode webhook) {
        String customerId = extractResourceId(webhook, "customers");
        JsonNode resource = webhook.path("_embedded").path("resource");
        String failureReason = resource.path("failure_reason").asText();
        
        log.info("Dwolla customer verification document failed: {}, reason: {}", customerId, failureReason);
        paymentService.suspendCustomerAccount(customerId, failureReason);
    }

    private void handleCustomerSuspended(JsonNode webhook) {
        String customerId = extractResourceId(webhook, "customers");
        log.info("Dwolla customer suspended: {}", customerId);
        paymentService.suspendCustomerAccount(customerId, "Account suspended by Dwolla");
    }

    private void handleCustomerActivated(JsonNode webhook) {
        String customerId = extractResourceId(webhook, "customers");
        log.info("Dwolla customer activated: {}", customerId);
        paymentService.activateCustomerAccount(customerId);
    }

    private void handleFundingSourceAdded(JsonNode webhook) {
        String fundingSourceId = extractResourceId(webhook, "funding-sources");
        log.info("Dwolla funding source added: {}", fundingSourceId);
    }

    private void handleFundingSourceRemoved(JsonNode webhook) {
        String fundingSourceId = extractResourceId(webhook, "funding-sources");
        log.info("Dwolla funding source removed: {}", fundingSourceId);
        paymentService.removeFundingSource(fundingSourceId);
    }

    private void handleFundingSourceVerified(JsonNode webhook) {
        String fundingSourceId = extractResourceId(webhook, "funding-sources");
        log.info("Dwolla funding source verified: {}", fundingSourceId);
        paymentService.verifyFundingSource(fundingSourceId);
    }

    private void handleFundingSourceUnverified(JsonNode webhook) {
        String fundingSourceId = extractResourceId(webhook, "funding-sources");
        log.info("Dwolla funding source unverified: {}", fundingSourceId);
        paymentService.unverifyFundingSource(fundingSourceId);
    }

    private void handleBankTransferCreated(JsonNode webhook) {
        String transferId = extractResourceId(webhook, "bank-transfers");
        log.info("Dwolla bank transfer created: {}", transferId);
    }

    private void handleBankTransferCompleted(JsonNode webhook) {
        String transferId = extractResourceId(webhook, "bank-transfers");
        log.info("Dwolla bank transfer completed: {}", transferId);
        paymentService.updatePaymentStatus(transferId, "COMPLETED");
    }

    private void handleBankTransferFailed(JsonNode webhook) {
        String transferId = extractResourceId(webhook, "bank-transfers");
        JsonNode resource = webhook.path("_embedded").path("resource");
        String code = resource.path("code").asText();
        String description = resource.path("description").asText();
        
        log.info("Dwolla bank transfer failed: {}, code: {}, description: {}", 
            transferId, code, description);
        paymentService.markPaymentFailed(transferId, code + ": " + description);
    }

    private void handleMassPaymentCreated(JsonNode webhook) {
        String massPaymentId = extractResourceId(webhook, "mass-payments");
        log.info("Dwolla mass payment created: {}", massPaymentId);
    }

    private void handleMassPaymentCompleted(JsonNode webhook) {
        String massPaymentId = extractResourceId(webhook, "mass-payments");
        log.info("Dwolla mass payment completed: {}", massPaymentId);
        paymentService.completeMassPayment(massPaymentId);
    }

    private void handleMassPaymentCancelled(JsonNode webhook) {
        String massPaymentId = extractResourceId(webhook, "mass-payments");
        log.info("Dwolla mass payment cancelled: {}", massPaymentId);
        paymentService.cancelMassPayment(massPaymentId);
    }

    private String extractResourceId(JsonNode webhook, String resourceType) {
        String resourceUrl = webhook.path("resourceId").asText();
        if (resourceUrl != null && resourceUrl.contains("/" + resourceType + "/")) {
            return resourceUrl.substring(resourceUrl.lastIndexOf("/") + 1);
        }
        return resourceUrl;
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
        log.error("SECURITY AUDIT: Dwolla Webhook Violation - Type: {}, IP: {}, Details: {}, Timestamp: {}", 
            violationType, clientIp, details, LocalDateTime.now());
    }
}