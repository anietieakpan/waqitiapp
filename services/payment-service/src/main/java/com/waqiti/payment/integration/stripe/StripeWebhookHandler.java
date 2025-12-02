package com.waqiti.payment.integration.stripe;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.*;
import com.stripe.net.Webhook;
import com.waqiti.payment.domain.Transaction;
import com.waqiti.payment.event.*;
import com.waqiti.payment.service.TransactionService;
import com.waqiti.common.event.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookHandler {

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;
    
    @Value("${stripe.webhook.tolerance:300}")
    private long webhookTolerance;
    
    private final TransactionService transactionService;
    private final EventPublisher eventPublisher;
    private final StripeEventProcessor eventProcessor;
    
    @Transactional
    public void handleWebhook(String payload, String signature) {
        log.debug("Processing Stripe webhook");
        
        try {
            // Verify webhook signature
            Event event = Webhook.constructEvent(payload, signature, webhookSecret, webhookTolerance);
            
            log.info("Received Stripe webhook event: {} - {}", event.getId(), event.getType());
            
            // Check if we've already processed this event
            if (eventProcessor.isEventProcessed(event.getId())) {
                log.info("Event {} already processed, skipping", event.getId());
                return;
            }
            
            // Process event based on type
            switch (event.getType()) {
                case "payment_intent.succeeded":
                    handlePaymentIntentSucceeded(event);
                    break;
                    
                case "payment_intent.payment_failed":
                    handlePaymentIntentFailed(event);
                    break;
                    
                case "payment_intent.canceled":
                    handlePaymentIntentCanceled(event);
                    break;
                    
                case "payment_intent.processing":
                    handlePaymentIntentProcessing(event);
                    break;
                    
                case "payment_intent.requires_action":
                    handlePaymentIntentRequiresAction(event);
                    break;
                    
                case "charge.succeeded":
                    handleChargeSucceeded(event);
                    break;
                    
                case "charge.failed":
                    handleChargeFailed(event);
                    break;
                    
                case "charge.refunded":
                    handleChargeRefunded(event);
                    break;
                    
                case "charge.dispute.created":
                    handleDisputeCreated(event);
                    break;
                    
                case "payment_method.attached":
                    handlePaymentMethodAttached(event);
                    break;
                    
                case "payment_method.detached":
                    handlePaymentMethodDetached(event);
                    break;
                    
                case "customer.subscription.created":
                case "customer.subscription.updated":
                case "customer.subscription.deleted":
                    handleSubscriptionEvent(event);
                    break;
                    
                case "transfer.created":
                case "transfer.updated":
                case "transfer.failed":
                    handleTransferEvent(event);
                    break;
                    
                case "payout.created":
                case "payout.updated":
                case "payout.failed":
                case "payout.paid":
                    handlePayoutEvent(event);
                    break;
                    
                case "account.updated":
                    handleAccountUpdated(event);
                    break;
                    
                case "invoice.payment_succeeded":
                case "invoice.payment_failed":
                    handleInvoiceEvent(event);
                    break;
                    
                default:
                    log.warn("Unhandled webhook event type: {}", event.getType());
            }
            
            // Mark event as processed
            eventProcessor.markEventProcessed(event.getId());
            
        } catch (SignatureVerificationException e) {
            log.error("Invalid webhook signature: {}", e.getMessage());
            throw new SecurityException("Invalid webhook signature");
        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process webhook", e);
        }
    }
    
    private void handlePaymentIntentSucceeded(Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject().orElseThrow();
        
        String transactionId = paymentIntent.getMetadata().get("transaction_id");
        if (transactionId == null) {
            log.warn("No transaction ID found in payment intent metadata");
            return;
        }
        
        // Update transaction status
        Transaction transaction = transactionService.updateTransactionStatus(
                UUID.fromString(transactionId),
                Transaction.Status.COMPLETED,
                paymentIntent.toJson()
        );
        
        // Publish success event
        eventPublisher.publish(PaymentCompletedEvent.builder()
                .transactionId(transaction.getId())
                .userId(transaction.getUserId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .paymentMethodType(getPaymentMethodType(paymentIntent))
                .providerTransactionId(paymentIntent.getId())
                .completedAt(convertToLocalDateTime(paymentIntent.getCreated()))
                .build());
        
        log.info("Payment intent succeeded: {} for transaction: {}", 
                paymentIntent.getId(), transactionId);
    }
    
    private void handlePaymentIntentFailed(Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject().orElseThrow();
        
        String transactionId = paymentIntent.getMetadata().get("transaction_id");
        if (transactionId == null) return;
        
        // Get failure reason
        String failureReason = paymentIntent.getLastPaymentError() != null 
                ? paymentIntent.getLastPaymentError().getMessage()
                : "Unknown error";
        
        // Update transaction
        Transaction transaction = transactionService.updateTransactionStatus(
                UUID.fromString(transactionId),
                Transaction.Status.FAILED,
                Map.of(
                        "error", failureReason,
                        "decline_code", paymentIntent.getLastPaymentError() != null && 
                                paymentIntent.getLastPaymentError().getDeclineCode() != null
                                ? paymentIntent.getLastPaymentError().getDeclineCode() : "",
                        "payment_intent", paymentIntent.toJson()
                )
        );
        
        // Publish failure event
        eventPublisher.publish(PaymentFailedEvent.builder()
                .transactionId(transaction.getId())
                .userId(transaction.getUserId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .failureReason(failureReason)
                .failedAt(LocalDateTime.now())
                .build());
        
        log.info("Payment intent failed: {} for transaction: {} - {}", 
                paymentIntent.getId(), transactionId, failureReason);
    }
    
    private void handlePaymentIntentCanceled(Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject().orElseThrow();
        
        String transactionId = paymentIntent.getMetadata().get("transaction_id");
        if (transactionId == null) return;
        
        Transaction transaction = transactionService.updateTransactionStatus(
                UUID.fromString(transactionId),
                Transaction.Status.CANCELLED,
                paymentIntent.toJson()
        );
        
        eventPublisher.publish(PaymentCancelledEvent.builder()
                .transactionId(transaction.getId())
                .userId(transaction.getUserId())
                .amount(transaction.getAmount())
                .cancelledAt(LocalDateTime.now())
                .build());
    }
    
    private void handlePaymentIntentProcessing(Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject().orElseThrow();
        
        String transactionId = paymentIntent.getMetadata().get("transaction_id");
        if (transactionId == null) return;
        
        transactionService.updateTransactionStatus(
                UUID.fromString(transactionId),
                Transaction.Status.PROCESSING,
                paymentIntent.toJson()
        );
    }
    
    private void handlePaymentIntentRequiresAction(Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject().orElseThrow();
        
        String transactionId = paymentIntent.getMetadata().get("transaction_id");
        if (transactionId == null) return;
        
        // Update transaction to pending 3DS
        transactionService.updateTransactionStatus(
                UUID.fromString(transactionId),
                Transaction.Status.PENDING_3DS,
                Map.of(
                        "client_secret", paymentIntent.getClientSecret(),
                        "next_action", paymentIntent.getNextAction() != null 
                                ? paymentIntent.getNextAction() : ""
                )
        );
        
        // Notify user that 3DS authentication is required
        eventPublisher.publish(Payment3DSRequiredEvent.builder()
                .transactionId(UUID.fromString(transactionId))
                .userId(UUID.fromString(paymentIntent.getMetadata().get("user_id")))
                .clientSecret(paymentIntent.getClientSecret())
                .build());
    }
    
    private void handleChargeSucceeded(Event event) {
        Charge charge = (Charge) event.getDataObjectDeserializer()
                .getObject().orElseThrow();
        
        log.info("Charge succeeded: {} for amount: {} {}", 
                charge.getId(), charge.getAmount(), charge.getCurrency());
        
        // Additional charge-specific processing if needed
    }
    
    private void handleChargeFailed(Event event) {
        Charge charge = (Charge) event.getDataObjectDeserializer()
                .getObject().orElseThrow();
        
        log.error("Charge failed: {} - {}", charge.getId(), charge.getFailureMessage());
    }
    
    private void handleChargeRefunded(Event event) {
        Charge charge = (Charge) event.getDataObjectDeserializer()
                .getObject().orElseThrow();
        
        log.info("Charge refunded: {} for amount: {} {}", 
                charge.getId(), charge.getAmountRefunded(), charge.getCurrency());
        
        // Process refund
        if (charge.getPaymentIntent() != null) {
            String transactionId = charge.getMetadata().get("transaction_id");
            if (transactionId != null) {
                eventPublisher.publish(RefundProcessedEvent.builder()
                        .originalTransactionId(UUID.fromString(transactionId))
                        .refundAmount(convertFromStripeAmount(charge.getAmountRefunded(), charge.getCurrency()))
                        .currency(charge.getCurrency().toUpperCase())
                        .refundId(charge.getId())
                        .processedAt(LocalDateTime.now())
                        .build());
            }
        }
    }
    
    private void handleDisputeCreated(Event event) {
        Dispute dispute = (Dispute) event.getDataObjectDeserializer()
                .getObject().orElseThrow();
        
        log.warn("Dispute created: {} for charge: {} amount: {} {}", 
                dispute.getId(), dispute.getCharge(), dispute.getAmount(), dispute.getCurrency());
        
        eventPublisher.publish(DisputeCreatedEvent.builder()
                .disputeId(dispute.getId())
                .chargeId(dispute.getCharge())
                .amount(convertFromStripeAmount(dispute.getAmount(), dispute.getCurrency()))
                .currency(dispute.getCurrency().toUpperCase())
                .reason(dispute.getReason())
                .status(dispute.getStatus())
                .evidenceDueBy(convertToLocalDateTime(dispute.getEvidenceDetails().getDueBy()))
                .build());
    }
    
    private void handlePaymentMethodAttached(Event event) {
        PaymentMethod paymentMethod = (PaymentMethod) event.getDataObjectDeserializer()
                .getObject().orElseThrow();
        
        log.info("Payment method attached: {} to customer: {}", 
                paymentMethod.getId(), paymentMethod.getCustomer());
    }
    
    private void handlePaymentMethodDetached(Event event) {
        PaymentMethod paymentMethod = (PaymentMethod) event.getDataObjectDeserializer()
                .getObject().orElseThrow();
        
        log.info("Payment method detached: {}", paymentMethod.getId());
    }
    
    private void handleSubscriptionEvent(Event event) {
        Subscription subscription = (Subscription) event.getDataObjectDeserializer()
                .getObject().orElseThrow();
        
        log.info("Subscription event: {} - {} status: {}", 
                event.getType(), subscription.getId(), subscription.getStatus());
        
        // Handle subscription lifecycle events
        eventPublisher.publish(SubscriptionEvent.builder()
                .subscriptionId(subscription.getId())
                .customerId(subscription.getCustomer())
                .status(subscription.getStatus())
                .eventType(event.getType())
                .build());
    }
    
    private void handleTransferEvent(Event event) {
        Transfer transfer = (Transfer) event.getDataObjectDeserializer()
                .getObject().orElseThrow();
        
        log.info("Transfer event: {} - {} amount: {} {}", 
                event.getType(), transfer.getId(), transfer.getAmount(), transfer.getCurrency());
    }
    
    private void handlePayoutEvent(Event event) {
        Payout payout = (Payout) event.getDataObjectDeserializer()
                .getObject().orElseThrow();
        
        log.info("Payout event: {} - {} amount: {} {} status: {}", 
                event.getType(), payout.getId(), payout.getAmount(), 
                payout.getCurrency(), payout.getStatus());
    }
    
    private void handleAccountUpdated(Event event) {
        Account account = (Account) event.getDataObjectDeserializer()
                .getObject().orElseThrow();
        
        log.info("Account updated: {} - charges enabled: {}, payouts enabled: {}", 
                account.getId(), account.getChargesEnabled(), account.getPayoutsEnabled());
    }
    
    private void handleInvoiceEvent(Event event) {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                .getObject().orElseThrow();
        
        log.info("Invoice event: {} - {} amount: {} {} status: {}", 
                event.getType(), invoice.getId(), invoice.getAmountPaid(), 
                invoice.getCurrency(), invoice.getStatus());
    }
    
    // Helper methods
    
    private String getPaymentMethodType(PaymentIntent paymentIntent) {
        if (paymentIntent.getPaymentMethodTypes() != null && !paymentIntent.getPaymentMethodTypes().isEmpty()) {
            return paymentIntent.getPaymentMethodTypes().get(0);
        }
        return "unknown";
    }
    
    private LocalDateTime convertToLocalDateTime(Long epochSeconds) {
        if (epochSeconds == null) return null;
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
    }
    
    private BigDecimal convertFromStripeAmount(Long amount, String currency) {
        if (amount == null) return BigDecimal.ZERO;
        
        int scale = getDecimalPlaces(currency);
        return BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(Math.pow(10, scale)), scale, RoundingMode.HALF_UP);
    }
    
    private int getDecimalPlaces(String currency) {
        // Implementation same as in StripePaymentProvider
        return 2; // Default for most currencies
    }
}