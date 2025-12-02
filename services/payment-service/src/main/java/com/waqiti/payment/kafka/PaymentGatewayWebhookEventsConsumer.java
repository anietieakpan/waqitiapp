package com.waqiti.payment.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.payment.model.*;
import com.waqiti.payment.repository.WebhookEventRepository;
import com.waqiti.payment.service.*;
import com.waqiti.common.notification.service.NotificationService;
import com.waqiti.common.audit.service.AuditService;
import com.waqiti.common.metrics.service.MetricsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade Kafka consumer for payment gateway webhook events
 * Handles webhook signature verification, payment status synchronization, and retry logic
 * 
 * Critical for: Real-time payment status updates, gateway integration, webhook security
 * SLA: Must process webhook within 3 seconds to prevent gateway retries
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentGatewayWebhookEventsConsumer {

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentService paymentService;
    private final WebhookVerificationService webhookVerificationService;
    private final GatewayIntegrationService gatewayIntegrationService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final AlertingService alertingService;
    private final SecurityService securityService;

    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long WEBHOOK_TIMEOUT_MS = 3000; // 3 seconds
    private static final Set<String> CRITICAL_WEBHOOK_TYPES = Set.of(
        "payment.succeeded", "payment.failed", "payment.refunded", "payment.disputed"
    );

    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        autoCreateTopics = "true",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(
        topics = {"payment-gateway-webhook-events", "stripe-webhook-events", "paypal-webhook-events"},
        groupId = "payment-gateway-webhook-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @CircuitBreaker(name = "payment-gateway-webhook-processor", fallbackMethod = "handleWebhookFailure")
    @Retry(name = "payment-gateway-webhook-processor")
    public void processPaymentGatewayWebhookEvent(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        long startTime = System.currentTimeMillis();
        
        log.info("Processing payment gateway webhook event: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        try {
            Map<String, Object> payload = event.getPayload();
            WebhookEvent webhookEvent = extractWebhookEvent(payload, topic);
            
            // Validate webhook event
            validateWebhookEvent(webhookEvent);
            
            // Check for duplicate webhook
            if (isDuplicateWebhook(webhookEvent)) {
                log.warn("Duplicate webhook detected: {}, skipping", webhookEvent.getWebhookId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Verify webhook signature
            if (!verifyWebhookSignature(webhookEvent)) {
                log.error("Webhook signature verification failed for: {}", webhookEvent.getWebhookId());
                handleSignatureVerificationFailure(webhookEvent, event);
                acknowledgment.acknowledge();
                return;
            }
            
            // Store webhook event for tracking
            WebhookEvent storedEvent = storeWebhookEvent(webhookEvent);
            
            // Process webhook based on type and gateway
            processWebhookByType(webhookEvent, storedEvent);
            
            // Update payment status if applicable
            updatePaymentStatus(webhookEvent);
            
            // Handle gateway-specific processing
            handleGatewaySpecificProcessing(webhookEvent, topic);
            
            // Send confirmations and notifications
            sendWebhookConfirmations(webhookEvent);
            
            // Update webhook processing metrics
            updateWebhookMetrics(webhookEvent, startTime);
            
            // Audit webhook processing
            auditWebhookProcessing(webhookEvent, event);
            
            // Mark webhook as processed
            markWebhookAsProcessed(storedEvent);
            
            acknowledgment.acknowledge();
            
            log.info("Successfully processed webhook event: {} type: {} in {}ms", 
                    webhookEvent.getWebhookId(), webhookEvent.getEventType(),
                    System.currentTimeMillis() - startTime);
            
        } catch (WebhookVerificationException e) {
            log.error("Webhook verification failed for event: {}", eventId, e);
            handleVerificationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (PaymentNotFoundException e) {
            log.error("Payment not found for webhook event: {}", eventId, e);
            handlePaymentNotFoundError(event, e);
            acknowledgment.acknowledge();
            
        } catch (GatewayIntegrationException e) {
            log.error("Gateway integration error for event: {}", eventId, e);
            handleGatewayError(event, e, acknowledgment);
            
        } catch (Exception e) {
            log.error("Failed to process webhook event: {}", eventId, e);
            handleProcessingError(event, e, acknowledgment);
        }
    }

    private WebhookEvent extractWebhookEvent(Map<String, Object> payload, String topic) {
        String gatewayType = determineGatewayType(topic);
        
        return WebhookEvent.builder()
            .webhookId(extractString(payload, "webhookId", UUID.randomUUID().toString()))
            .gatewayType(gatewayType)
            .eventType(extractString(payload, "eventType", "unknown"))
            .paymentId(extractString(payload, "paymentId", null))
            .externalPaymentId(extractString(payload, "externalPaymentId", null))
            .signature(extractString(payload, "signature", null))
            .timestamp(extractInstant(payload, "timestamp"))
            .rawPayload(payload.toString())
            .headers(extractMap(payload, "headers"))
            .amount(extractBigDecimal(payload, "amount"))
            .currency(extractString(payload, "currency", null))
            .status(extractString(payload, "status", null))
            .failureReason(extractString(payload, "failureReason", null))
            .metadata(extractMap(payload, "metadata"))
            .ipAddress(extractString(payload, "ipAddress", null))
            .userAgent(extractString(payload, "userAgent", null))
            .receivedAt(Instant.now())
            .processed(false)
            .verified(false)
            .build();
    }

    private String determineGatewayType(String topic) {
        if (topic.contains("stripe")) return "STRIPE";
        if (topic.contains("paypal")) return "PAYPAL";
        if (topic.contains("square")) return "SQUARE";
        if (topic.contains("adyen")) return "ADYEN";
        return "GENERIC";
    }

    private void validateWebhookEvent(WebhookEvent webhookEvent) {
        if (webhookEvent.getWebhookId() == null || webhookEvent.getWebhookId().isEmpty()) {
            throw new WebhookValidationException("Webhook ID is required");
        }
        
        if (webhookEvent.getEventType() == null || webhookEvent.getEventType().isEmpty()) {
            throw new WebhookValidationException("Event type is required");
        }
        
        if (webhookEvent.getGatewayType() == null) {
            throw new WebhookValidationException("Gateway type is required");
        }
        
        if (webhookEvent.getTimestamp() == null) {
            throw new WebhookValidationException("Timestamp is required");
        }
        
        // Validate timestamp is not too old (prevent replay attacks)
        Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);
        if (webhookEvent.getTimestamp().isBefore(cutoff)) {
            throw new WebhookValidationException("Webhook timestamp is too old: " + webhookEvent.getTimestamp());
        }
        
        // Validate payload size
        if (webhookEvent.getRawPayload().length() > 50000) {
            throw new WebhookValidationException("Webhook payload is too large");
        }
    }

    private boolean isDuplicateWebhook(WebhookEvent webhookEvent) {
        return webhookEventRepository.existsByWebhookIdAndTimestampAfter(
            webhookEvent.getWebhookId(),
            Instant.now().minus(10, ChronoUnit.MINUTES)
        );
    }

    private boolean verifyWebhookSignature(WebhookEvent webhookEvent) {
        try {
            switch (webhookEvent.getGatewayType()) {
                case "STRIPE":
                    return verifyStripeSignature(webhookEvent);
                case "PAYPAL":
                    return verifyPayPalSignature(webhookEvent);
                case "SQUARE":
                    return verifySquareSignature(webhookEvent);
                case "ADYEN":
                    return verifyAdyenSignature(webhookEvent);
                default:
                    return verifyGenericSignature(webhookEvent);
            }
        } catch (Exception e) {
            log.error("Error verifying webhook signature for: {}", webhookEvent.getWebhookId(), e);
            return false;
        }
    }

    private boolean verifyStripeSignature(WebhookEvent webhookEvent) throws Exception {
        String signature = webhookEvent.getSignature();
        if (signature == null) return false;
        
        String webhookSecret = gatewayIntegrationService.getWebhookSecret("STRIPE");
        String payload = webhookEvent.getRawPayload();
        String timestamp = String.valueOf(webhookEvent.getTimestamp().getEpochSecond());
        
        String signedPayload = timestamp + "." + payload;
        String expectedSignature = computeHmacSha256(signedPayload, webhookSecret);
        
        return signature.equals("v1=" + expectedSignature);
    }

    private boolean verifyPayPalSignature(WebhookEvent webhookEvent) throws Exception {
        // PayPal webhook verification logic
        String certId = webhookEvent.getHeaders().get("PAYPAL-CERT-ID").toString();
        String signature = webhookEvent.getSignature();
        
        return webhookVerificationService.verifyPayPalWebhook(
            webhookEvent.getRawPayload(),
            signature,
            certId
        );
    }

    private boolean verifySquareSignature(WebhookEvent webhookEvent) throws Exception {
        String signature = webhookEvent.getSignature();
        String notificationUrl = webhookEvent.getHeaders().get("notification-url").toString();
        String body = webhookEvent.getRawPayload();
        
        return webhookVerificationService.verifySquareWebhook(body, signature, notificationUrl);
    }

    private boolean verifyAdyenSignature(WebhookEvent webhookEvent) throws Exception {
        String signature = webhookEvent.getSignature();
        String payload = webhookEvent.getRawPayload();
        String hmacKey = gatewayIntegrationService.getWebhookSecret("ADYEN");
        
        String expectedSignature = computeHmacSha256(payload, hmacKey);
        return signature.equals(expectedSignature);
    }

    private boolean verifyGenericSignature(WebhookEvent webhookEvent) throws Exception {
        // Generic HMAC SHA256 verification
        String signature = webhookEvent.getSignature();
        if (signature == null) return true; // No signature required for generic
        
        String secret = gatewayIntegrationService.getWebhookSecret("GENERIC");
        String expectedSignature = computeHmacSha256(webhookEvent.getRawPayload(), secret);
        
        return signature.equals(expectedSignature);
    }

    private String computeHmacSha256(String data, String secret) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    private WebhookEvent storeWebhookEvent(WebhookEvent webhookEvent) {
        webhookEvent.setVerified(true);
        webhookEvent.setProcessingStarted(Instant.now());
        return webhookEventRepository.save(webhookEvent);
    }

    private void processWebhookByType(WebhookEvent webhookEvent, WebhookEvent storedEvent) {
        String eventType = webhookEvent.getEventType();
        
        switch (eventType) {
            case "payment.succeeded":
            case "payment.completed":
                handlePaymentSucceededWebhook(webhookEvent);
                break;
                
            case "payment.failed":
            case "payment.declined":
                handlePaymentFailedWebhook(webhookEvent);
                break;
                
            case "payment.pending":
            case "payment.processing":
                handlePaymentPendingWebhook(webhookEvent);
                break;
                
            case "payment.refunded":
            case "refund.succeeded":
                handleRefundWebhook(webhookEvent);
                break;
                
            case "payment.disputed":
            case "chargeback.created":
                handleDisputeWebhook(webhookEvent);
                break;
                
            case "payment.captured":
                handlePaymentCapturedWebhook(webhookEvent);
                break;
                
            case "payment.authorized":
                handlePaymentAuthorizedWebhook(webhookEvent);
                break;
                
            case "payment.cancelled":
            case "payment.voided":
                handlePaymentCancelledWebhook(webhookEvent);
                break;
                
            default:
                handleUnknownWebhookType(webhookEvent);
        }
    }

    private void handlePaymentSucceededWebhook(WebhookEvent webhookEvent) {
        String paymentId = findPaymentId(webhookEvent);
        if (paymentId == null) return;
        
        Payment payment = paymentService.getPayment(paymentId);
        if (payment.getStatus().equals("COMPLETED")) {
            log.info("Payment already completed, skipping webhook: {}", paymentId);
            return;
        }
        
        // Update payment status
        paymentService.updatePaymentStatus(paymentId, "COMPLETED", 
            "Payment confirmed via " + webhookEvent.getGatewayType() + " webhook");
        
        // Update settlement information
        if (webhookEvent.getAmount() != null) {
            paymentService.updateSettlementAmount(paymentId, webhookEvent.getAmount());
        }
        
        // Trigger completion workflows
        triggerPaymentCompletionWorkflows(payment, webhookEvent);
        
        // Send success notifications
        notificationService.sendPaymentSuccessNotification(payment);
    }

    private void handlePaymentFailedWebhook(WebhookEvent webhookEvent) {
        String paymentId = findPaymentId(webhookEvent);
        if (paymentId == null) return;
        
        Payment payment = paymentService.getPayment(paymentId);
        
        // Update payment status with failure reason
        String failureReason = webhookEvent.getFailureReason() != null ? 
            webhookEvent.getFailureReason() : "Payment failed at gateway";
        
        paymentService.updatePaymentStatus(paymentId, "FAILED", failureReason);
        
        // Store failure details
        PaymentFailure failure = PaymentFailure.builder()
            .paymentId(paymentId)
            .gatewayType(webhookEvent.getGatewayType())
            .failureCode(extractFailureCode(webhookEvent))
            .failureMessage(failureReason)
            .failureTime(webhookEvent.getTimestamp())
            .recoverable(isRecoverableFailure(webhookEvent))
            .build();
        
        paymentService.storePaymentFailure(failure);
        
        // Trigger failure workflows
        triggerPaymentFailureWorkflows(payment, webhookEvent);
        
        // Send failure notifications
        notificationService.sendPaymentFailureNotification(payment, failureReason);
    }

    private void handlePaymentPendingWebhook(WebhookEvent webhookEvent) {
        String paymentId = findPaymentId(webhookEvent);
        if (paymentId == null) return;
        
        paymentService.updatePaymentStatus(paymentId, "PENDING", 
            "Payment pending confirmation from " + webhookEvent.getGatewayType());
        
        // Schedule status check for pending payments
        schedulePaymentStatusCheck(paymentId, webhookEvent.getGatewayType());
    }

    private void handleRefundWebhook(WebhookEvent webhookEvent) {
        String paymentId = findPaymentId(webhookEvent);
        if (paymentId == null) return;
        
        Payment payment = paymentService.getPayment(paymentId);
        BigDecimal refundAmount = webhookEvent.getAmount() != null ? 
            webhookEvent.getAmount() : payment.getAmount();
        
        // Create refund record
        Refund refund = Refund.builder()
            .paymentId(paymentId)
            .refundId(UUID.randomUUID().toString())
            .amount(refundAmount)
            .currency(payment.getCurrency())
            .reason("Gateway initiated refund")
            .status("COMPLETED")
            .processedAt(webhookEvent.getTimestamp())
            .gatewayRefundId(webhookEvent.getExternalPaymentId())
            .build();
        
        paymentService.processRefund(refund);
        
        // Update payment status
        boolean isPartialRefund = refundAmount.compareTo(payment.getAmount()) < 0;
        String newStatus = isPartialRefund ? "PARTIALLY_REFUNDED" : "REFUNDED";
        
        paymentService.updatePaymentStatus(paymentId, newStatus, 
            "Refund processed via " + webhookEvent.getGatewayType());
        
        // Send refund notifications
        notificationService.sendRefundNotification(payment, refund);
    }

    private void handleDisputeWebhook(WebhookEvent webhookEvent) {
        String paymentId = findPaymentId(webhookEvent);
        if (paymentId == null) return;
        
        Payment payment = paymentService.getPayment(paymentId);
        
        // Create dispute record
        PaymentDispute dispute = PaymentDispute.builder()
            .paymentId(paymentId)
            .disputeId(UUID.randomUUID().toString())
            .amount(webhookEvent.getAmount() != null ? webhookEvent.getAmount() : payment.getAmount())
            .currency(payment.getCurrency())
            .reason(webhookEvent.getFailureReason())
            .status("OPEN")
            .createdAt(webhookEvent.getTimestamp())
            .gatewayDisputeId(webhookEvent.getExternalPaymentId())
            .evidenceRequired(true)
            .responseDeadline(Instant.now().plus(7, ChronoUnit.DAYS))
            .build();
        
        paymentService.createDispute(dispute);
        
        // Update payment status
        paymentService.updatePaymentStatus(paymentId, "DISPUTED", 
            "Payment disputed via " + webhookEvent.getGatewayType());
        
        // Create high-priority alert for dispute
        alertingService.createDisputeAlert(payment, dispute);
        
        // Send dispute notifications
        notificationService.sendDisputeNotification(payment, dispute);
    }

    private void handlePaymentCapturedWebhook(WebhookEvent webhookEvent) {
        String paymentId = findPaymentId(webhookEvent);
        if (paymentId == null) return;
        
        Payment payment = paymentService.getPayment(paymentId);
        
        // Update capture information
        PaymentCapture capture = PaymentCapture.builder()
            .paymentId(paymentId)
            .capturedAmount(webhookEvent.getAmount() != null ? webhookEvent.getAmount() : payment.getAmount())
            .capturedAt(webhookEvent.getTimestamp())
            .gatewayCaptureId(webhookEvent.getExternalPaymentId())
            .build();
        
        paymentService.recordCapture(capture);
        
        // Update payment status
        paymentService.updatePaymentStatus(paymentId, "CAPTURED", 
            "Payment captured via " + webhookEvent.getGatewayType());
    }

    private void handlePaymentAuthorizedWebhook(WebhookEvent webhookEvent) {
        String paymentId = findPaymentId(webhookEvent);
        if (paymentId == null) return;
        
        // Update authorization information
        PaymentAuthorization auth = PaymentAuthorization.builder()
            .paymentId(paymentId)
            .authorizedAmount(webhookEvent.getAmount())
            .authorizedAt(webhookEvent.getTimestamp())
            .gatewayAuthId(webhookEvent.getExternalPaymentId())
            .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
            .build();
        
        paymentService.recordAuthorization(auth);
        
        // Update payment status
        paymentService.updatePaymentStatus(paymentId, "AUTHORIZED", 
            "Payment authorized via " + webhookEvent.getGatewayType());
    }

    private void handlePaymentCancelledWebhook(WebhookEvent webhookEvent) {
        String paymentId = findPaymentId(webhookEvent);
        if (paymentId == null) return;
        
        paymentService.updatePaymentStatus(paymentId, "CANCELLED", 
            "Payment cancelled via " + webhookEvent.getGatewayType());
        
        // Release any holds or reservations
        paymentService.releasePaymentHolds(paymentId);
    }

    private void handleUnknownWebhookType(WebhookEvent webhookEvent) {
        log.warn("Unknown webhook event type: {} for gateway: {}", 
                webhookEvent.getEventType(), webhookEvent.getGatewayType());
        
        // Store for manual review
        webhookEventRepository.markForManualReview(webhookEvent.getWebhookId(), 
            "Unknown event type: " + webhookEvent.getEventType());
        
        // Create alert for unknown webhook type
        alertingService.createAlert(
            "UNKNOWN_WEBHOOK_TYPE",
            "Unknown webhook event type received: " + webhookEvent.getEventType(),
            "MEDIUM"
        );
    }

    private String findPaymentId(WebhookEvent webhookEvent) {
        if (webhookEvent.getPaymentId() != null) {
            return webhookEvent.getPaymentId();
        }
        
        if (webhookEvent.getExternalPaymentId() != null) {
            return paymentService.findPaymentIdByExternalId(
                webhookEvent.getExternalPaymentId(),
                webhookEvent.getGatewayType()
            );
        }
        
        log.error("Cannot find payment ID for webhook: {}", webhookEvent.getWebhookId());
        return null;
    }

    private void updatePaymentStatus(WebhookEvent webhookEvent) {
        if (webhookEvent.getStatus() == null) return;
        
        String paymentId = findPaymentId(webhookEvent);
        if (paymentId == null) return;
        
        // Map gateway status to internal status
        String internalStatus = mapGatewayStatusToInternal(
            webhookEvent.getStatus(), 
            webhookEvent.getGatewayType()
        );
        
        if (internalStatus != null) {
            paymentService.updatePaymentStatus(paymentId, internalStatus, 
                "Status updated via " + webhookEvent.getGatewayType() + " webhook");
        }
    }

    private String mapGatewayStatusToInternal(String gatewayStatus, String gatewayType) {
        Map<String, Map<String, String>> statusMappings = Map.of(
            "STRIPE", Map.of(
                "succeeded", "COMPLETED",
                "pending", "PENDING",
                "requires_payment_method", "FAILED",
                "requires_confirmation", "PENDING",
                "requires_action", "PENDING",
                "processing", "PROCESSING",
                "requires_capture", "AUTHORIZED",
                "canceled", "CANCELLED"
            ),
            "PAYPAL", Map.of(
                "COMPLETED", "COMPLETED",
                "PENDING", "PENDING",
                "DECLINED", "FAILED",
                "VOIDED", "CANCELLED",
                "PARTIALLY_REFUNDED", "PARTIALLY_REFUNDED",
                "REFUNDED", "REFUNDED"
            ),
            "SQUARE", Map.of(
                "COMPLETED", "COMPLETED",
                "APPROVED", "AUTHORIZED",
                "PENDING", "PENDING",
                "FAILED", "FAILED",
                "CANCELED", "CANCELLED"
            )
        );
        
        return statusMappings.getOrDefault(gatewayType, Map.of()).get(gatewayStatus);
    }

    private void handleGatewaySpecificProcessing(WebhookEvent webhookEvent, String topic) {
        switch (webhookEvent.getGatewayType()) {
            case "STRIPE":
                handleStripeSpecificProcessing(webhookEvent);
                break;
            case "PAYPAL":
                handlePayPalSpecificProcessing(webhookEvent);
                break;
            case "SQUARE":
                handleSquareSpecificProcessing(webhookEvent);
                break;
            case "ADYEN":
                handleAdyenSpecificProcessing(webhookEvent);
                break;
        }
    }

    private void handleStripeSpecificProcessing(WebhookEvent webhookEvent) {
        // Handle Stripe-specific webhook processing
        Map<String, Object> metadata = webhookEvent.getMetadata();
        
        // Process subscription events
        if (metadata.containsKey("subscription_id")) {
            subscriptionService.handleStripeSubscriptionUpdate(
                metadata.get("subscription_id").toString(),
                webhookEvent.getEventType()
            );
        }
        
        // Process Connect account events
        if (metadata.containsKey("account")) {
            connectAccountService.handleAccountUpdate(
                metadata.get("account").toString(),
                webhookEvent
            );
        }
    }

    private void handlePayPalSpecificProcessing(WebhookEvent webhookEvent) {
        // Handle PayPal-specific webhook processing
        if (webhookEvent.getEventType().startsWith("BILLING.SUBSCRIPTION")) {
            paypalSubscriptionService.handleSubscriptionEvent(webhookEvent);
        }
        
        // Handle PayPal disputes
        if (webhookEvent.getEventType().startsWith("CUSTOMER.DISPUTE")) {
            paypalDisputeService.handleDisputeEvent(webhookEvent);
        }
    }

    private void handleSquareSpecificProcessing(WebhookEvent webhookEvent) {
        // Handle Square-specific webhook processing
        if (webhookEvent.getEventType().contains("order")) {
            squareOrderService.handleOrderEvent(webhookEvent);
        }
        
        // Handle Square inventory updates
        if (webhookEvent.getEventType().contains("inventory")) {
            squareInventoryService.handleInventoryEvent(webhookEvent);
        }
    }

    private void handleAdyenSpecificProcessing(WebhookEvent webhookEvent) {
        // Handle Adyen-specific webhook processing
        Map<String, Object> metadata = webhookEvent.getMetadata();
        
        // Process recurring contract events
        if (metadata.containsKey("recurringDetailReference")) {
            adyenRecurringService.handleRecurringEvent(webhookEvent);
        }
        
        // Process marketplace events
        if (metadata.containsKey("accountCode")) {
            adyenMarketplaceService.handleMarketplaceEvent(webhookEvent);
        }
    }

    private void sendWebhookConfirmations(WebhookEvent webhookEvent) {
        // Send confirmation back to gateway if required
        if (requiresConfirmation(webhookEvent.getGatewayType())) {
            gatewayIntegrationService.sendWebhookConfirmation(
                webhookEvent.getGatewayType(),
                webhookEvent.getWebhookId()
            );
        }
        
        // Send internal notifications for critical events
        if (CRITICAL_WEBHOOK_TYPES.contains(webhookEvent.getEventType())) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendWebhookProcessedNotification(webhookEvent);
            });
        }
    }

    private boolean requiresConfirmation(String gatewayType) {
        return Arrays.asList("STRIPE", "ADYEN").contains(gatewayType);
    }

    private void triggerPaymentCompletionWorkflows(Payment payment, WebhookEvent webhookEvent) {
        // Trigger order fulfillment
        CompletableFuture.runAsync(() -> {
            fulfillmentService.triggerOrderFulfillment(payment.getOrderId());
        });
        
        // Update loyalty points
        if (payment.hasLoyaltyProgram()) {
            CompletableFuture.runAsync(() -> {
                loyaltyService.awardPoints(payment.getCustomerId(), payment.getAmount());
            });
        }
        
        // Update merchant analytics
        CompletableFuture.runAsync(() -> {
            analyticsService.recordSuccessfulPayment(payment, webhookEvent.getGatewayType());
        });
    }

    private void triggerPaymentFailureWorkflows(Payment payment, WebhookEvent webhookEvent) {
        // Release inventory holds
        CompletableFuture.runAsync(() -> {
            inventoryService.releaseHolds(payment.getOrderId());
        });
        
        // Update failure analytics
        CompletableFuture.runAsync(() -> {
            analyticsService.recordFailedPayment(payment, webhookEvent);
        });
        
        // Check if retry is appropriate
        if (isRecoverableFailure(webhookEvent)) {
            schedulePaymentRetry(payment.getPaymentId(), webhookEvent.getGatewayType());
        }
    }

    private boolean isRecoverableFailure(WebhookEvent webhookEvent) {
        String failureCode = extractFailureCode(webhookEvent);
        if (failureCode == null) return false;
        
        Set<String> recoverableFailureCodes = Set.of(
            "insufficient_funds", "temporary_failure", "try_again_later",
            "card_declined_temp", "issuer_unavailable"
        );
        
        return recoverableFailureCodes.stream()
            .anyMatch(code -> failureCode.toLowerCase().contains(code));
    }

    private String extractFailureCode(WebhookEvent webhookEvent) {
        Map<String, Object> metadata = webhookEvent.getMetadata();
        
        // Check common failure code fields
        Object failureCode = metadata.get("failure_code");
        if (failureCode != null) return failureCode.toString();
        
        Object errorCode = metadata.get("error_code");
        if (errorCode != null) return errorCode.toString();
        
        Object declineCode = metadata.get("decline_code");
        if (declineCode != null) return declineCode.toString();
        
        return null;
    }

    private void schedulePaymentStatusCheck(String paymentId, String gatewayType) {
        // Schedule status check after 15 minutes for pending payments
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(15 * 60 * 1000); // 15 minutes
                paymentService.checkPaymentStatusWithGateway(paymentId, gatewayType);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Payment status check interrupted for: {}", paymentId);
            } catch (Exception e) {
                log.error("Failed to check payment status for: {}", paymentId, e);
            }
        });
    }

    private void schedulePaymentRetry(String paymentId, String gatewayType) {
        // Schedule retry after 30 minutes
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(30 * 60 * 1000); // 30 minutes
                paymentService.retryPayment(paymentId, gatewayType);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Payment retry interrupted for: {}", paymentId);
            } catch (Exception e) {
                log.error("Failed to retry payment for: {}", paymentId, e);
            }
        });
    }

    private void updateWebhookMetrics(WebhookEvent webhookEvent, long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        metricsService.recordWebhookMetrics(
            webhookEvent.getGatewayType(),
            webhookEvent.getEventType(),
            processingTime,
            processingTime <= WEBHOOK_TIMEOUT_MS
        );
        
        if (CRITICAL_WEBHOOK_TYPES.contains(webhookEvent.getEventType())) {
            metricsService.recordCriticalWebhookProcessing(
                webhookEvent.getEventType(),
                processingTime
            );
        }
    }

    private void auditWebhookProcessing(WebhookEvent webhookEvent, GenericKafkaEvent event) {
        auditService.auditWebhookProcessing(
            webhookEvent.getWebhookId(),
            webhookEvent.getGatewayType(),
            webhookEvent.getEventType(),
            webhookEvent.getPaymentId(),
            event.getEventId()
        );
    }

    private void markWebhookAsProcessed(WebhookEvent webhookEvent) {
        webhookEvent.setProcessed(true);
        webhookEvent.setProcessingCompleted(Instant.now());
        webhookEventRepository.save(webhookEvent);
    }

    // Error handling methods
    private void handleSignatureVerificationFailure(WebhookEvent webhookEvent, GenericKafkaEvent event) {
        securityService.recordWebhookSecurityIncident(
            webhookEvent.getWebhookId(),
            "SIGNATURE_VERIFICATION_FAILED",
            webhookEvent.getIpAddress()
        );
        
        alertingService.createSecurityAlert(
            "WEBHOOK_SIGNATURE_FAILED",
            "Webhook signature verification failed for: " + webhookEvent.getWebhookId(),
            "HIGH"
        );
        
        auditService.auditSecurityEvent(
            "WEBHOOK_SIGNATURE_VERIFICATION_FAILED",
            webhookEvent.getWebhookId(),
            event.getEventId()
        );
    }

    private void handleVerificationError(GenericKafkaEvent event, WebhookVerificationException e) {
        auditService.logWebhookVerificationError(event.getEventId(), e.getMessage());
        
        alertingService.createAlert(
            "WEBHOOK_VERIFICATION_ERROR",
            "Webhook verification error: " + e.getMessage(),
            "MEDIUM"
        );
    }

    private void handlePaymentNotFoundError(GenericKafkaEvent event, PaymentNotFoundException e) {
        auditService.logPaymentNotFoundError(event.getEventId(), e.getMessage());
        
        // Store webhook for manual review
        webhookEventRepository.markForManualReview(
            event.getEventId(),
            "Payment not found: " + e.getMessage()
        );
    }

    private void handleGatewayError(GenericKafkaEvent event, GatewayIntegrationException e, 
                                   Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying webhook event {} after {}ms due to gateway error (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            // Don't acknowledge - let retry mechanism handle it
            return;
        } else {
            log.error("Max retries exceeded for webhook event {}, sending to DLQ", eventId);
            sendWebhookToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            log.warn("Retrying webhook event {} (attempt {})", eventId, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            // Don't acknowledge - let retry mechanism handle it
            return;
        } else {
            log.error("Max retries exceeded for webhook event {}, sending to DLQ", eventId);
            sendWebhookToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendWebhookToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "payment-gateway-webhook-events");
        
        kafkaTemplate.send("payment-gateway-webhook-events.DLQ", event);
        
        alertingService.createDLQAlert(
            "payment-gateway-webhook-events",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handleWebhookFailure(GenericKafkaEvent event, String topic, int partition,
                                   long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for webhook processing: {}", e.getMessage());
        
        failedEventRepository.save(
            FailedEvent.builder()
                .eventId(event.getEventId())
                .topic(topic)
                .payload(event)
                .errorMessage(e.getMessage())
                .createdAt(Instant.now())
                .build()
        );
        
        alertingService.sendCriticalAlert(
            "Webhook Processing Circuit Breaker Open",
            "Webhook processing is failing. Payment status updates may be delayed."
        );
        
        acknowledgment.acknowledge();
    }

    // Helper extraction methods
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(value.toString());
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return Instant.now();
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    // Custom exceptions
    public static class WebhookValidationException extends RuntimeException {
        public WebhookValidationException(String message) {
            super(message);
        }
    }

    public static class WebhookVerificationException extends RuntimeException {
        public WebhookVerificationException(String message) {
            super(message);
        }
    }

    public static class PaymentNotFoundException extends RuntimeException {
        public PaymentNotFoundException(String message) {
            super(message);
        }
    }

    public static class GatewayIntegrationException extends RuntimeException {
        public GatewayIntegrationException(String message) {
            super(message);
        }
    }
}