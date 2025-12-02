package com.waqiti.payment.webhook;

import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Charge;
import com.stripe.model.Refund;
import com.stripe.model.Dispute;
import com.stripe.model.Source;
import com.stripe.net.Webhook;
import com.waqiti.common.audit.TransactionAuditService;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.StripePaymentProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;

/**
 * CRITICAL SECURITY: Stripe Webhook Controller with comprehensive security and idempotency
 * Handles all Stripe webhook events with proper validation, signature verification, and audit trail
 */
@RestController
@RequestMapping("/webhooks/stripe")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private static final Set<String> STRIPE_WEBHOOK_IPS = Set.of(
        "3.18.12.63", "3.130.192.231", "13.235.14.237",
        "13.235.122.149", "18.211.135.69", "35.154.171.200",
        "3.77.5.145", "13.52.254.243", "18.179.48.96",
        "54.187.174.169", "54.187.205.235", "54.187.216.72",
        "54.241.31.99", "54.241.31.102", "54.241.34.107"
    );

    private final PaymentService paymentService;
    private final StripePaymentProcessor stripeProcessor;
    private final TransactionAuditService auditService;
    private final IdempotencyService idempotencyService;
    private final PaymentProviderFallbackService fallbackService;
    private final ObjectMapper objectMapper;

    @Value("${stripe.webhook.endpoint-secret}")
    private String webhookSecret;

    @Value("${stripe.webhook.fallback-enabled:true}")
    private boolean fallbackEnabled;
    
    /**
     * CRITICAL SECURITY: Main Stripe webhook endpoint with signature verification
     */
    @PostMapping
    @Transactional
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader,
            HttpServletRequest request) {
        
        try {
            // 1. CRITICAL SECURITY: Verify source IP address
            String clientIp = getClientIpAddress(request);
            if (!isValidStripeIP(clientIp)) {
                log.error("SECURITY VIOLATION: Stripe webhook from unauthorized IP: {}", clientIp);
                
                auditService.auditSecurityEvent(
                    "system",
                    "WEBHOOK_IP_VIOLATION",
                    "REJECTED",
                    clientIp,
                    request.getHeader("User-Agent"),
                    Map.of(
                        "provider", "STRIPE",
                        "expectedIPs", STRIPE_WEBHOOK_IPS.toString(),
                        "actualIP", clientIp
                    )
                );
                
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized source IP");
            }
            
            log.info("SECURITY: Stripe webhook IP validation passed: {}", clientIp);
            
            // 2. CRITICAL SECURITY: Verify webhook signature
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            
            // 2. Extract event details for logging
            String eventId = event.getId();
            String eventType = event.getType();
            String idempotencyKey = "stripe-webhook-" + eventId;
            
            log.info("SECURITY: Processing Stripe webhook: eventId={}, type={}", eventId, eventType);
            
            // 3. CRITICAL: Use idempotency to prevent duplicate processing
            return idempotencyService.executeIdempotentWithPersistence(
                "stripe-webhook-service",
                "process-webhook",
                idempotencyKey,
                () -> processStripeWebhookEvent(event),
                java.time.Duration.ofHours(24)
            );
            
        } catch (com.stripe.exception.SignatureVerificationException e) {
            log.error("SECURITY VIOLATION: Invalid Stripe webhook signature from IP: {}", 
                    request.getRemoteAddr(), e);
            
            // Audit security violation
            auditService.auditSecurityEvent(
                "system",
                "WEBHOOK_SIGNATURE_VIOLATION",
                "REJECTED",
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                Map.of(
                    "provider", "STRIPE",
                    "signature", sigHeader,
                    "payloadLength", payload.length()
                )
            );
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
            
        } catch (Exception e) {
            log.error("CRITICAL: Error processing Stripe webhook", e);
            
            // Audit webhook processing failure
            auditService.auditSecurityEvent(
                "system",
                "WEBHOOK_PROCESSING_ERROR",
                "FAILED",
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                Map.of(
                    "provider", "STRIPE",
                    "error", e.getMessage()
                )
            );
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook processing failed");
        }
    }
    
    /**
     * Process individual Stripe webhook events with comprehensive handling
     */
    private ResponseEntity<String> processStripeWebhookEvent(Event event) {
        String eventType = event.getType();
        
        try {
            switch (eventType) {
                // Payment Intent Events
                case "payment_intent.succeeded":
                    handlePaymentIntentSucceeded(event);
                    break;
                    
                case "payment_intent.payment_failed":
                    handlePaymentIntentFailed(event);
                    break;
                    
                case "payment_intent.canceled":
                    handlePaymentIntentCanceled(event);
                    break;
                    
                case "payment_intent.requires_action":
                    handlePaymentIntentRequiresAction(event);
                    break;
                    
                // Charge Events
                case "charge.succeeded":
                    handleChargeSucceeded(event);
                    break;
                    
                case "charge.failed":
                    handleChargeFailed(event);
                    break;
                    
                case "charge.captured":
                    handleChargeCaptured(event);
                    break;
                    
                case "charge.dispute.created":
                    handleChargeDisputeCreated(event);
                    break;
                    
                case "charge.dispute.updated":
                    handleChargeDisputeUpdated(event);
                    break;
                    
                case "charge.dispute.closed":
                    handleChargeDisputeClosed(event);
                    break;
                    
                // Refund Events
                case "refund.created":
                    handleRefundCreated(event);
                    break;
                    
                case "refund.updated":
                    handleRefundUpdated(event);
                    break;
                    
                case "refund.failed":
                    handleRefundFailed(event);
                    break;
                    
                // Source Events (for alternative payment methods)
                case "source.chargeable":
                    handleSourceChargeable(event);
                    break;
                    
                case "source.failed":
                    handleSourceFailed(event);
                    break;
                    
                case "source.canceled":
                    handleSourceCanceled(event);
                    break;
                    
                // Customer Events
                case "customer.source.created":
                    handleCustomerSourceCreated(event);
                    break;
                    
                case "customer.source.deleted":
                    handleCustomerSourceDeleted(event);
                    break;
                    
                // Invoice Events
                case "invoice.payment_succeeded":
                    handleInvoicePaymentSucceeded(event);
                    break;
                    
                case "invoice.payment_failed":
                    handleInvoicePaymentFailed(event);
                    break;
                    
                default:
                    log.info("WEBHOOK: Unhandled Stripe event type: {}", eventType);
                    return ResponseEntity.ok("Event type not handled");
            }
            
            log.info("SECURITY: Successfully processed Stripe webhook: eventId={}, type={}", 
                    event.getId(), eventType);
            
            return ResponseEntity.ok("Webhook processed successfully");
            
        } catch (Exception e) {
            log.error("CRITICAL: Error processing Stripe webhook event: {}", eventType, e);
            throw new RuntimeException("Webhook event processing failed", e);
        }
    }
    
    /**
     * CRITICAL: Handle successful payment intent
     */
    private void handlePaymentIntentSucceeded(Event event) {
        PaymentIntent paymentIntent = extractPaymentIntent(event);
        String paymentId = paymentIntent.getId();
        
        log.info("STRIPE: Payment intent succeeded: {}", paymentId);
        
        // Update payment status
        paymentService.updatePaymentStatus(paymentId, "COMPLETED");
        
        // Audit successful payment
        auditService.auditPaymentOperation(
            paymentId,
            "STRIPE",
            "PAYMENT_COMPLETED",
            convertFromStripeAmount(paymentIntent.getAmount()),
            paymentIntent.getCurrency().toUpperCase(),
            "COMPLETED",
            "stripe",
            Map.of(
                "stripePaymentIntentId", paymentId,
                "stripeCustomerId", paymentIntent.getCustomer(),
                "paymentMethod", paymentIntent.getPaymentMethod(),
                "receiptEmail", paymentIntent.getReceiptEmail()
            )
        );
    }
    
    /**
     * Handle failed payment intent with intelligent fallback
     */
    private void handlePaymentIntentFailed(Event event) {
        PaymentIntent paymentIntent = extractPaymentIntent(event);
        String paymentId = paymentIntent.getId();
        String failureReason = paymentIntent.getLastPaymentError() != null ?
                paymentIntent.getLastPaymentError().getMessage() : "Unknown error";

        log.warn("STRIPE: Payment intent failed: {} - {}", paymentId, failureReason);

        // Attempt fallback to alternative provider if enabled and failure is retryable
        if (fallbackEnabled && isRetryableFailure(failureReason)) {
            try {
                log.info("Attempting fallback for failed Stripe payment: {}", paymentId);

                // Create payment request from failed Stripe payment
                PaymentRequest fallbackRequest = createFallbackPaymentRequest(paymentIntent);

                // Attempt payment with fallback service (excluding Stripe)
                PaymentResult fallbackResult = fallbackService.processPaymentWithFallback(fallbackRequest);

                if (fallbackResult.isSuccessful()) {
                    log.info("FALLBACK SUCCESS: Payment recovered with provider {} after Stripe failure",
                            fallbackResult.getProvider());

                    // Update payment with fallback provider details
                    paymentService.updatePaymentWithFallbackProvider(
                            paymentId,
                            fallbackResult.getProvider(),
                            fallbackResult.getTransactionId());

                    // Audit successful fallback
                    auditService.auditPaymentOperation(
                        paymentId,
                        fallbackResult.getProvider(),
                        "PAYMENT_FALLBACK_SUCCESS",
                        convertFromStripeAmount(paymentIntent.getAmount()),
                        paymentIntent.getCurrency().toUpperCase(),
                        "COMPLETED",
                        "system",
                        Map.of(
                            "originalProvider", "STRIPE",
                            "fallbackProvider", fallbackResult.getProvider(),
                            "stripeFailureReason", failureReason,
                            "stripePaymentIntentId", paymentId
                        )
                    );

                    return; // Success - no need to mark as failed
                }
            } catch (Exception fallbackEx) {
                log.error("Fallback failed for Stripe payment: {}", paymentId, fallbackEx);
                // Continue to mark as failed
            }
        }

        // Update payment status as failed (either fallback disabled or failed)
        paymentService.markPaymentFailed(paymentId, failureReason);

        // Audit failed payment
        auditService.auditPaymentOperation(
            paymentId,
            "STRIPE",
            "PAYMENT_FAILED",
            convertFromStripeAmount(paymentIntent.getAmount()),
            paymentIntent.getCurrency().toUpperCase(),
            "FAILED",
            "stripe",
            Map.of(
                "stripePaymentIntentId", paymentId,
                "failureReason", failureReason,
                "stripeCustomerId", paymentIntent.getCustomer(),
                "paymentMethod", paymentIntent.getPaymentMethod(),
                "fallbackAttempted", fallbackEnabled
            )
        );
    }

    /**
     * Check if failure is retryable with different provider
     */
    private boolean isRetryableFailure(String failureReason) {
        if (failureReason == null) return false;

        String reason = failureReason.toLowerCase();

        // Non-retryable failures (these are customer/card issues, not provider issues)
        if (reason.contains("insufficient funds") ||
            reason.contains("card declined") ||
            reason.contains("invalid card") ||
            reason.contains("expired card") ||
            reason.contains("incorrect cvc") ||
            reason.contains("fraudulent")) {
            return false;
        }

        // Retryable failures (these are provider/system issues)
        return reason.contains("processing error") ||
               reason.contains("gateway") ||
               reason.contains("timeout") ||
               reason.contains("service unavailable") ||
               reason.contains("rate limit");
    }

    /**
     * Create fallback payment request from failed Stripe payment
     */
    private PaymentRequest createFallbackPaymentRequest(PaymentIntent paymentIntent) {
        return PaymentRequest.builder()
                .paymentId(paymentIntent.getId())
                .amount(convertFromStripeAmount(paymentIntent.getAmount()))
                .currency(paymentIntent.getCurrency().toUpperCase())
                .customerId(paymentIntent.getCustomer())
                .paymentType("CARD")
                .description(paymentIntent.getDescription())
                .metadata(paymentIntent.getMetadata())
                .excludeProviders(Set.of("stripe")) // Exclude Stripe from fallback
                .build();
    }
    
    /**
     * Handle canceled payment intent
     */
    private void handlePaymentIntentCanceled(Event event) {
        PaymentIntent paymentIntent = extractPaymentIntent(event);
        String paymentId = paymentIntent.getId();
        
        log.info("STRIPE: Payment intent canceled: {}", paymentId);
        
        paymentService.updatePaymentStatus(paymentId, "CANCELED");
        
        // Audit canceled payment
        auditService.auditPaymentOperation(
            paymentId,
            "STRIPE",
            "PAYMENT_CANCELED",
            convertFromStripeAmount(paymentIntent.getAmount()),
            paymentIntent.getCurrency().toUpperCase(),
            "CANCELED",
            "stripe",
            Map.of(
                "stripePaymentIntentId", paymentId,
                "stripeCustomerId", paymentIntent.getCustomer()
            )
        );
    }
    
    /**
     * Handle payment intent requiring additional action
     */
    private void handlePaymentIntentRequiresAction(Event event) {
        PaymentIntent paymentIntent = extractPaymentIntent(event);
        String paymentId = paymentIntent.getId();
        
        log.info("STRIPE: Payment intent requires action: {}", paymentId);
        
        paymentService.updatePaymentStatus(paymentId, "REQUIRES_ACTION");
        
        // Audit action required
        auditService.auditPaymentOperation(
            paymentId,
            "STRIPE",
            "PAYMENT_ACTION_REQUIRED",
            convertFromStripeAmount(paymentIntent.getAmount()),
            paymentIntent.getCurrency().toUpperCase(),
            "REQUIRES_ACTION",
            "stripe",
            Map.of(
                "stripePaymentIntentId", paymentId,
                "nextAction", paymentIntent.getNextAction().getType(),
                "stripeCustomerId", paymentIntent.getCustomer()
            )
        );
    }
    
    /**
     * Handle successful charge
     */
    private void handleChargeSucceeded(Event event) {
        Charge charge = extractCharge(event);
        String chargeId = charge.getId();
        
        log.info("STRIPE: Charge succeeded: {}", chargeId);
        
        paymentService.updatePaymentStatus(chargeId, "COMPLETED");
        
        // Audit successful charge
        auditService.auditPaymentOperation(
            chargeId,
            "STRIPE",
            "CHARGE_COMPLETED",
            convertFromStripeAmount(charge.getAmount()),
            charge.getCurrency().toUpperCase(),
            "COMPLETED",
            "stripe",
            Map.of(
                "stripeChargeId", chargeId,
                "receiptUrl", charge.getReceiptUrl(),
                "paymentMethodType", charge.getPaymentMethodDetails().getType(),
                "captured", charge.getCaptured()
            )
        );
    }
    
    /**
     * Handle failed charge
     */
    private void handleChargeFailed(Event event) {
        Charge charge = extractCharge(event);
        String chargeId = charge.getId();
        String failureReason = charge.getFailureMessage() != null ? 
                charge.getFailureMessage() : "Unknown error";
        
        log.warn("STRIPE: Charge failed: {} - {}", chargeId, failureReason);
        
        paymentService.markPaymentFailed(chargeId, failureReason);
        
        // Audit failed charge
        auditService.auditPaymentOperation(
            chargeId,
            "STRIPE",
            "CHARGE_FAILED",
            convertFromStripeAmount(charge.getAmount()),
            charge.getCurrency().toUpperCase(),
            "FAILED",
            "stripe",
            Map.of(
                "stripeChargeId", chargeId,
                "failureCode", charge.getFailureCode(),
                "failureReason", failureReason,
                "declineCode", charge.getDeclineCode()
            )
        );
    }
    
    /**
     * Handle charge captured
     */
    private void handleChargeCaptured(Event event) {
        Charge charge = extractCharge(event);
        String chargeId = charge.getId();
        
        log.info("STRIPE: Charge captured: {}", chargeId);
        
        paymentService.updatePaymentStatus(chargeId, "CAPTURED");
        
        // Audit charge capture
        auditService.auditPaymentOperation(
            chargeId,
            "STRIPE",
            "CHARGE_CAPTURED",
            convertFromStripeAmount(charge.getAmountCaptured()),
            charge.getCurrency().toUpperCase(),
            "CAPTURED",
            "stripe",
            Map.of(
                "stripeChargeId", chargeId,
                "amountCaptured", charge.getAmountCaptured(),
                "receiptUrl", charge.getReceiptUrl()
            )
        );
    }
    
    /**
     * CRITICAL: Handle dispute created
     */
    private void handleChargeDisputeCreated(Event event) {
        Dispute dispute = extractDispute(event);
        String disputeId = dispute.getId();
        String chargeId = dispute.getCharge();
        
        log.warn("STRIPE: Dispute created: {} for charge: {}", disputeId, chargeId);
        
        paymentService.createDispute(disputeId, chargeId, dispute.getReason());
        
        // Audit dispute creation
        auditService.auditPaymentOperation(
            chargeId,
            "STRIPE",
            "DISPUTE_CREATED",
            convertFromStripeAmount(dispute.getAmount()),
            dispute.getCurrency().toUpperCase(),
            "DISPUTED",
            "stripe",
            Map.of(
                "disputeId", disputeId,
                "reason", dispute.getReason(),
                "status", dispute.getStatus(),
                "evidence", dispute.getEvidenceDetails()
            )
        );
    }
    
    /**
     * Handle dispute updated
     */
    private void handleChargeDisputeUpdated(Event event) {
        Dispute dispute = extractDispute(event);
        String disputeId = dispute.getId();
        
        log.info("STRIPE: Dispute updated: {} - status: {}", disputeId, dispute.getStatus());
        
        paymentService.updateDisputeStatus(disputeId, dispute.getStatus());
        
        // Audit dispute update
        auditService.auditPaymentOperation(
            dispute.getCharge(),
            "STRIPE",
            "DISPUTE_UPDATED",
            convertFromStripeAmount(dispute.getAmount()),
            dispute.getCurrency().toUpperCase(),
            dispute.getStatus().toUpperCase(),
            "stripe",
            Map.of(
                "disputeId", disputeId,
                "status", dispute.getStatus(),
                "reason", dispute.getReason()
            )
        );
    }
    
    /**
     * Handle dispute closed
     */
    private void handleChargeDisputeClosed(Event event) {
        Dispute dispute = extractDispute(event);
        String disputeId = dispute.getId();
        
        log.info("STRIPE: Dispute closed: {} - status: {}", disputeId, dispute.getStatus());
        
        paymentService.updateDisputeStatus(disputeId, dispute.getStatus());
        
        // Audit dispute closure
        auditService.auditPaymentOperation(
            dispute.getCharge(),
            "STRIPE",
            "DISPUTE_CLOSED",
            convertFromStripeAmount(dispute.getAmount()),
            dispute.getCurrency().toUpperCase(),
            dispute.getStatus().toUpperCase(),
            "stripe",
            Map.of(
                "disputeId", disputeId,
                "status", dispute.getStatus(),
                "reason", dispute.getReason()
            )
        );
    }
    
    /**
     * Handle refund created
     */
    private void handleRefundCreated(Event event) {
        Refund refund = extractRefund(event);
        String refundId = refund.getId();
        String chargeId = refund.getCharge();
        
        log.info("STRIPE: Refund created: {} for charge: {}", refundId, chargeId);
        
        paymentService.updateRefundStatus(refundId, chargeId, "PROCESSING");
        
        // Audit refund creation
        auditService.auditPaymentOperation(
            chargeId,
            "STRIPE",
            "REFUND_CREATED",
            convertFromStripeAmount(refund.getAmount()),
            refund.getCurrency().toUpperCase(),
            "PROCESSING",
            "stripe",
            Map.of(
                "refundId", refundId,
                "reason", refund.getReason(),
                "status", refund.getStatus()
            )
        );
    }
    
    /**
     * Handle refund updated
     */
    private void handleRefundUpdated(Event event) {
        Refund refund = extractRefund(event);
        String refundId = refund.getId();
        String chargeId = refund.getCharge();
        
        log.info("STRIPE: Refund updated: {} - status: {}", refundId, refund.getStatus());
        
        paymentService.updateRefundStatus(refundId, chargeId, refund.getStatus().toUpperCase());
        
        // Audit refund update
        auditService.auditPaymentOperation(
            chargeId,
            "STRIPE",
            "REFUND_UPDATED",
            convertFromStripeAmount(refund.getAmount()),
            refund.getCurrency().toUpperCase(),
            refund.getStatus().toUpperCase(),
            "stripe",
            Map.of(
                "refundId", refundId,
                "status", refund.getStatus(),
                "reason", refund.getReason()
            )
        );
    }
    
    /**
     * Handle refund failed
     */
    private void handleRefundFailed(Event event) {
        Refund refund = extractRefund(event);
        String refundId = refund.getId();
        String failureReason = refund.getFailureReason();
        
        log.warn("STRIPE: Refund failed: {} - {}", refundId, failureReason);
        
        paymentService.markRefundFailed(refundId, failureReason);
        
        // Audit refund failure
        auditService.auditPaymentOperation(
            refund.getCharge(),
            "STRIPE",
            "REFUND_FAILED",
            convertFromStripeAmount(refund.getAmount()),
            refund.getCurrency().toUpperCase(),
            "FAILED",
            "stripe",
            Map.of(
                "refundId", refundId,
                "failureReason", failureReason,
                "status", refund.getStatus()
            )
        );
    }
    
    /**
     * Handle source chargeable (for alternative payment methods)
     */
    private void handleSourceChargeable(Event event) {
        Source source = extractSource(event);
        String sourceId = source.getId();
        
        log.info("STRIPE: Source chargeable: {} - type: {}", sourceId, source.getType());
        
        // Process the chargeable source
        stripeProcessor.processChargeableSource(source);
    }
    
    /**
     * Handle source failed
     */
    private void handleSourceFailed(Event event) {
        Source source = extractSource(event);
        String sourceId = source.getId();
        
        log.warn("STRIPE: Source failed: {} - type: {}", sourceId, source.getType());
        
        // Handle failed source
        stripeProcessor.handleFailedSource(source);
    }
    
    /**
     * Handle source canceled
     */
    private void handleSourceCanceled(Event event) {
        Source source = extractSource(event);
        String sourceId = source.getId();
        
        log.info("STRIPE: Source canceled: {} - type: {}", sourceId, source.getType());
        
        // Handle canceled source
        stripeProcessor.handleCanceledSource(source);
    }
    
    /**
     * Handle customer source created
     */
    private void handleCustomerSourceCreated(Event event) {
        // Extract customer and source information from event
        log.info("STRIPE: Customer source created");
        
        // Handle new payment method added to customer
        // Implementation depends on your customer management system
    }
    
    /**
     * Handle customer source deleted
     */
    private void handleCustomerSourceDeleted(Event event) {
        // Extract customer and source information from event
        log.info("STRIPE: Customer source deleted");
        
        // Handle payment method removed from customer
        // Implementation depends on your customer management system
    }
    
    /**
     * Handle successful invoice payment
     */
    private void handleInvoicePaymentSucceeded(Event event) {
        log.info("STRIPE: Invoice payment succeeded");
        // Handle successful subscription or invoice payment
    }
    
    /**
     * Handle failed invoice payment
     */
    private void handleInvoicePaymentFailed(Event event) {
        log.warn("STRIPE: Invoice payment failed");
        // Handle failed subscription or invoice payment
    }
    
    // Helper methods for extracting objects from events
    
    private PaymentIntent extractPaymentIntent(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        if (deserializer.getObject().isPresent()) {
            return (PaymentIntent) deserializer.getObject().get();
        } else {
            throw new RuntimeException("Failed to deserialize PaymentIntent from webhook event");
        }
    }
    
    private Charge extractCharge(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        if (deserializer.getObject().isPresent()) {
            return (Charge) deserializer.getObject().get();
        } else {
            throw new RuntimeException("Failed to deserialize Charge from webhook event");
        }
    }
    
    private Dispute extractDispute(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        if (deserializer.getObject().isPresent()) {
            return (Dispute) deserializer.getObject().get();
        } else {
            throw new RuntimeException("Failed to deserialize Dispute from webhook event");
        }
    }
    
    private Refund extractRefund(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        if (deserializer.getObject().isPresent()) {
            return (Refund) deserializer.getObject().get();
        } else {
            throw new RuntimeException("Failed to deserialize Refund from webhook event");
        }
    }
    
    private Source extractSource(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        if (deserializer.getObject().isPresent()) {
            return (Source) deserializer.getObject().get();
        } else {
            throw new RuntimeException("Failed to deserialize Source from webhook event");
        }
    }
    
    /**
     * Convert Stripe amount (in cents) to decimal amount
     */
    private java.math.BigDecimal convertFromStripeAmount(Long amountInCents) {
        if (amountInCents == null) return java.math.BigDecimal.ZERO;
        return java.math.BigDecimal.valueOf(amountInCents).divide(java.math.BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp.trim();
        }
        
        return request.getRemoteAddr();
    }
    
    private boolean isValidStripeIP(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return false;
        }
        
        return STRIPE_WEBHOOK_IPS.contains(clientIp);
    }
}