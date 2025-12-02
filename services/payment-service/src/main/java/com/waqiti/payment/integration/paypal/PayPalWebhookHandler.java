package com.waqiti.payment.integration.paypal;

import com.paypal.core.PayPalHttpClient;
import com.paypal.http.HttpResponse;
import com.paypal.http.HttpRequest;
import com.waqiti.payment.domain.Transaction;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.TransactionService;
import com.waqiti.common.events.PaymentEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * PayPal Webhook Handler Service
 * 
 * Handles PayPal webhook events for payment status updates,
 * subscription changes, and dispute notifications.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayPalWebhookHandler {

    private final PayPalHttpClient payPalClient;
    private final PaymentService paymentService;
    private final TransactionService transactionService;
    private final PaymentEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    /**
     * Verify PayPal webhook signature
     */
    public boolean verifySignature(String payload, Map<String, String> headers, String webhookId) {
        try {
            // Build the verification request
            ObjectNode verificationRequest = objectMapper.createObjectNode();
            verificationRequest.put("auth_algo", headers.get("PAYPAL-AUTH-ALGO"));
            verificationRequest.put("cert_url", headers.get("PAYPAL-CERT-URL"));
            verificationRequest.put("transmission_id", headers.get("PAYPAL-TRANSMISSION-ID"));
            verificationRequest.put("transmission_sig", headers.get("PAYPAL-TRANSMISSION-SIG"));
            verificationRequest.put("transmission_time", headers.get("PAYPAL-TRANSMISSION-TIME"));
            verificationRequest.put("webhook_id", webhookId);
            
            // Parse the webhook event from payload
            JsonNode webhookEvent = objectMapper.readTree(payload);
            verificationRequest.set("webhook_event", webhookEvent);
            
            // Call PayPal's webhook verification endpoint
            String verificationUrl = getPayPalApiUrl() + "/v1/notifications/verify-webhook-signature";
            
            Mono<JsonNode> responseMono = webClientBuilder.build()
                    .post()
                    .uri(verificationUrl)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
                    .bodyValue(verificationRequest)
                    .retrieve()
                    .bodyToMono(JsonNode.class);
            
            JsonNode response = responseMono.block();
            
            String verificationStatus = response.get("verification_status").asText();
            return "SUCCESS".equals(verificationStatus);
            
        } catch (Exception e) {
            log.error("Failed to verify PayPal webhook signature: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Process PayPal webhook event
     */
    @Transactional
    public void processWebhookEvent(String payload, Map<String, String> headers) {
        try {
            if (!verifySignature(payload, headers, extractWebhookId(headers))) {
                log.warn("Invalid PayPal webhook signature, ignoring event");
                return;
            }

            JsonNode event = objectMapper.readTree(payload);
            
            String eventType = event.get("event_type").asText();
            String resourceType = event.has("resource_type") ? event.get("resource_type").asText() : "";
            log.info("Processing PayPal webhook event: {} for resource: {}", 
                    eventType, resourceType);

            switch (eventType) {
                case "PAYMENT.CAPTURE.COMPLETED":
                    handlePaymentCaptureCompleted(event);
                    break;
                case "PAYMENT.CAPTURE.DENIED":
                    handlePaymentCaptureDenied(event);
                    break;
                case "PAYMENT.CAPTURE.REFUNDED":
                    handlePaymentCaptureRefunded(event);
                    break;
                case "CHECKOUT.ORDER.APPROVED":
                    handleOrderApproved(event);
                    break;
                case "CHECKOUT.ORDER.COMPLETED":
                    handleOrderCompleted(event);
                    break;
                case "BILLING.SUBSCRIPTION.ACTIVATED":
                    handleSubscriptionActivated(event);
                    break;
                case "BILLING.SUBSCRIPTION.CANCELLED":
                    handleSubscriptionCancelled(event);
                    break;
                case "BILLING.SUBSCRIPTION.SUSPENDED":
                    handleSubscriptionSuspended(event);
                    break;
                case "BILLING.SUBSCRIPTION.PAYMENT.FAILED":
                    handleSubscriptionPaymentFailed(event);
                    break;
                case "CUSTOMER.DISPUTE.CREATED":
                    handleDisputeCreated(event);
                    break;
                case "CUSTOMER.DISPUTE.RESOLVED":
                    handleDisputeResolved(event);
                    break;
                case "RISK.DISPUTE.CREATED":
                    handleRiskDisputeCreated(event);
                    break;
                default:
                    log.info("Unhandled PayPal webhook event type: {}", eventType);
            }
            
        } catch (Exception e) {
            log.error("Failed to process PayPal webhook event: {}", e.getMessage(), e);
            throw new RuntimeException("Webhook processing failed", e);
        }
    }

    private void handlePaymentCaptureCompleted(JsonNode event) {
        try {
            String captureId = extractResourceId(event);
            String orderId = extractOrderId(event);
            
            // Update transaction status
            Optional<Transaction> transactionOpt = transactionService.findByProviderTransactionId(orderId);
            if (transactionOpt.isPresent()) {
                Transaction transaction = transactionOpt.get();
                transaction.setStatus(Transaction.Status.COMPLETED);
                transaction.setCompletedAt(LocalDateTime.now());
                transaction.setProviderResponse(event.get("resource").toString());
                
                transactionService.updateTransaction(transaction);
                
                // Publish payment completed event
                eventPublisher.publishPaymentCompleted(transaction);
                
                log.info("Payment capture completed for order: {} capture: {}", orderId, captureId);
            } else {
                log.warn("No transaction found for PayPal order: {}", orderId);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle payment capture completed: {}", e.getMessage());
        }
    }

    private void handlePaymentCaptureDenied(JsonNode event) {
        try {
            String captureId = extractResourceId(event);
            String orderId = extractOrderId(event);
            
            Optional<Transaction> transactionOpt = transactionService.findByProviderTransactionId(orderId);
            if (transactionOpt.isPresent()) {
                Transaction transaction = transactionOpt.get();
                transaction.setStatus(Transaction.Status.FAILED);
                transaction.setFailureReason("Payment capture denied by PayPal");
                transaction.setProviderResponse(event.get("resource").toString());
                
                transactionService.updateTransaction(transaction);
                
                // Publish payment failed event
                eventPublisher.publishPaymentFailed(transaction);
                
                log.info("Payment capture denied for order: {} capture: {}", orderId, captureId);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle payment capture denied: {}", e.getMessage());
        }
    }

    private void handlePaymentCaptureRefunded(JsonNode event) {
        try {
            String refundId = extractResourceId(event);
            BigDecimal refundAmount = extractRefundAmount(event);
            String captureId = extractCaptureId(event);
            
            // Find original transaction by capture ID
            Optional<Transaction> originalTransactionOpt = transactionService.findByProviderTransactionId(captureId);
            if (originalTransactionOpt.isPresent()) {
                Transaction originalTransaction = originalTransactionOpt.get();
                
                // Create refund transaction
                Transaction refundTransaction = Transaction.builder()
                        .userId(originalTransaction.getUserId())
                        .type(Transaction.Type.REFUND)
                        .amount(refundAmount.negate())
                        .currency(originalTransaction.getCurrency())
                        .status(Transaction.Status.COMPLETED)
                        .description("PayPal refund for transaction: " + originalTransaction.getId())
                        .providerTransactionId(refundId)
                        .originalTransactionId(originalTransaction.getId())
                        .providerResponse(event.get("resource").toString())
                        .createdAt(LocalDateTime.now())
                        .completedAt(LocalDateTime.now())
                        .build();
                
                transactionService.createTransaction(refundTransaction);
                
                // Publish refund event
                eventPublisher.publishRefundCompleted(refundTransaction);
                
                log.info("Refund completed for capture: {} amount: {}", captureId, refundAmount);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle payment capture refunded: {}", e.getMessage());
        }
    }

    private void handleOrderApproved(JsonNode event) {
        try {
            String orderId = extractResourceId(event);
            
            Optional<Transaction> transactionOpt = transactionService.findByProviderTransactionId(orderId);
            if (transactionOpt.isPresent()) {
                Transaction transaction = transactionOpt.get();
                transaction.setStatus(Transaction.Status.AUTHORIZED);
                transaction.setProviderResponse(event.get("resource").toString());
                
                transactionService.updateTransaction(transaction);
                
                // Publish payment authorized event
                eventPublisher.publishPaymentAuthorized(transaction);
                
                log.info("PayPal order approved: {}", orderId);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle order approved: {}", e.getMessage());
        }
    }

    private void handleOrderCompleted(JsonNode event) {
        try {
            String orderId = extractResourceId(event);
            
            Optional<Transaction> transactionOpt = transactionService.findByProviderTransactionId(orderId);
            if (transactionOpt.isPresent()) {
                Transaction transaction = transactionOpt.get();
                transaction.setStatus(Transaction.Status.COMPLETED);
                transaction.setCompletedAt(LocalDateTime.now());
                transaction.setProviderResponse(event.get("resource").toString());
                
                transactionService.updateTransaction(transaction);
                
                // Publish payment completed event
                eventPublisher.publishPaymentCompleted(transaction);
                
                log.info("PayPal order completed: {}", orderId);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle order completed: {}", e.getMessage());
        }
    }

    private void handleSubscriptionActivated(JsonNode event) {
        try {
            String subscriptionId = extractResourceId(event);
            
            // Update subscription status in database
            // This would integrate with a subscription management service
            
            log.info("PayPal subscription activated: {}", subscriptionId);
            
        } catch (Exception e) {
            log.error("Failed to handle subscription activated: {}", e.getMessage());
        }
    }

    private void handleSubscriptionCancelled(JsonNode event) {
        try {
            String subscriptionId = extractResourceId(event);
            
            // Update subscription status and stop future billing
            
            log.info("PayPal subscription cancelled: {}", subscriptionId);
            
        } catch (Exception e) {
            log.error("Failed to handle subscription cancelled: {}", e.getMessage());
        }
    }

    private void handleSubscriptionSuspended(JsonNode event) {
        try {
            String subscriptionId = extractResourceId(event);
            
            // Update subscription status to suspended
            
            log.info("PayPal subscription suspended: {}", subscriptionId);
            
        } catch (Exception e) {
            log.error("Failed to handle subscription suspended: {}", e.getMessage());
        }
    }

    private void handleSubscriptionPaymentFailed(JsonNode event) {
        try {
            String subscriptionId = extractResourceId(event);
            
            // Handle failed subscription payment - retry logic, notifications, etc.
            
            log.info("PayPal subscription payment failed: {}", subscriptionId);
            
        } catch (Exception e) {
            log.error("Failed to handle subscription payment failed: {}", e.getMessage());
        }
    }

    private void handleDisputeCreated(JsonNode event) {
        try {
            String disputeId = extractResourceId(event);
            
            // Create dispute record and notify relevant teams
            
            log.warn("PayPal dispute created: {}", disputeId);
            
        } catch (Exception e) {
            log.error("Failed to handle dispute created: {}", e.getMessage());
        }
    }

    private void handleDisputeResolved(JsonNode event) {
        try {
            String disputeId = extractResourceId(event);
            
            // Update dispute status and process resolution
            
            log.info("PayPal dispute resolved: {}", disputeId);
            
        } catch (Exception e) {
            log.error("Failed to handle dispute resolved: {}", e.getMessage());
        }
    }

    private void handleRiskDisputeCreated(JsonNode event) {
        try {
            String disputeId = extractResourceId(event);
            
            // Handle risk-related dispute - enhanced fraud monitoring
            
            log.warn("PayPal risk dispute created: {}", disputeId);
            
        } catch (Exception e) {
            log.error("Failed to handle risk dispute created: {}", e.getMessage());
        }
    }

    // Helper methods for extracting data from webhook events

    // Helper method to get PayPal API URL based on environment
    private String getPayPalApiUrl() {
        // This should be injected from configuration
        return "https://api-m.sandbox.paypal.com"; // Default to sandbox
    }
    
    /**
     * CRITICAL P0 FIX: Get PayPal OAuth access token for webhook verification
     *
     * Previous implementation returned placeholder "ACCESS_TOKEN" causing webhook verification to fail
     * Now properly retrieves OAuth token from PayPal token cache or generates new one
     *
     * @return Valid PayPal OAuth access token
     * @author Waqiti Payment Team - P0 Production Fix
     */
    private String getAccessToken() {
        try {
            // Get cached access token from PaymentCacheService
            String cachedToken = paymentCacheService.getPayPalAccessToken();

            if (cachedToken != null && !cachedToken.isEmpty()) {
                log.debug("PAYPAL: Using cached OAuth access token");
                return cachedToken;
            }

            // Token not cached or expired - generate new token via PayPal OAuth
            log.info("PAYPAL: Generating new OAuth access token for webhook verification");
            String newToken = payPalApiClient.generateAccessToken();

            if (newToken == null || newToken.isEmpty()) {
                log.error("PAYPAL CRITICAL: Failed to generate OAuth access token - webhook verification will fail");
                throw new IllegalStateException("PayPal OAuth token generation failed");
            }

            // Cache token for 8 hours (PayPal tokens expire in 9 hours)
            paymentCacheService.cachePayPalAccessToken(newToken, java.time.Duration.ofHours(8));

            log.info("PAYPAL: Successfully generated and cached new OAuth access token");
            return newToken;

        } catch (Exception e) {
            log.error("PAYPAL CRITICAL: Error obtaining PayPal access token for webhook verification", e);
            throw new RuntimeException("Failed to obtain PayPal OAuth token for webhook verification", e);
        }
    }

    private String extractResourceId(JsonNode event) {
        // Extract resource ID from event
        JsonNode resource = event.get("resource");
        if (resource != null && resource.has("id")) {
            return resource.get("id").asText();
        }
        return "";
    }

    private String extractOrderId(JsonNode event) {
        // Extract order ID from event resource
        JsonNode resource = event.get("resource");
        if (resource != null) {
            if (resource.has("order_id")) {
                return resource.get("order_id").asText();
            } else if (resource.has("supplementary_data") && 
                       resource.get("supplementary_data").has("related_ids") &&
                       resource.get("supplementary_data").get("related_ids").has("order_id")) {
                return resource.get("supplementary_data").get("related_ids").get("order_id").asText();
            }
        }
        return "";
    }

    private String extractCaptureId(JsonNode event) {
        // Extract capture ID from event resource
        JsonNode resource = event.get("resource");
        if (resource != null && resource.has("id")) {
            return resource.get("id").asText();
        }
        return "";
    }

    private BigDecimal extractRefundAmount(JsonNode event) {
        // Extract refund amount from event resource
        JsonNode resource = event.get("resource");
        if (resource != null && resource.has("amount") && resource.get("amount").has("value")) {
            String amountStr = resource.get("amount").get("value").asText();
            return new BigDecimal(amountStr);
        }
        return BigDecimal.ZERO;
    }

    private String extractWebhookId(Map<String, String> headers) {
        return headers.get("PAYPAL-WEBHOOK-ID");
    }
}