package com.waqiti.webhook.kafka;

import com.waqiti.webhook.event.WebhookEvent;
import com.waqiti.webhook.service.WebhookDeliveryService;
import com.waqiti.webhook.service.IntegrationService;
import com.waqiti.webhook.service.EventMappingService;
import com.waqiti.webhook.service.RetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade Kafka consumer for webhook and integration events
 * Handles: webhook-events, webhook.dead-letter-queue, search-indexing,
 * content-amplification, receipt-generation
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookIntegrationConsumer {

    private final WebhookDeliveryService webhookDeliveryService;
    private final IntegrationService integrationService;
    private final EventMappingService eventMappingService;
    private final RetryService retryService;

    @KafkaListener(topics = {"webhook-events", "webhook.dead-letter-queue", "search-indexing",
                             "content-amplification", "receipt-generation"}, 
                   groupId = "webhook-integration-processor")
    @Transactional
    public void processWebhookEvent(@Payload WebhookEvent event,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                  @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                  @Header(KafkaHeaders.OFFSET) long offset,
                                  Acknowledgment acknowledgment) {
        try {
            log.info("Processing webhook/integration event: {} - Type: {} - Target: {} - Event: {}", 
                    event.getEventId(), event.getEventType(), event.getTargetUrl(), event.getOriginalEvent());
            
            // Process based on topic
            switch (topic) {
                case "webhook-events" -> handleWebhookEvent(event);
                case "webhook.dead-letter-queue" -> handleWebhookDlq(event);
                case "search-indexing" -> handleSearchIndexing(event);
                case "content-amplification" -> handleContentAmplification(event);
                case "receipt-generation" -> handleReceiptGeneration(event);
            }
            
            // Update webhook metrics
            updateWebhookMetrics(event);
            
            // Acknowledge
            acknowledgment.acknowledge();
            
            log.info("Successfully processed webhook/integration event: {}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Failed to process webhook/integration event {}: {}", 
                    event.getEventId(), e.getMessage(), e);
            throw new RuntimeException("Webhook/integration processing failed", e);
        }
    }

    private void handleWebhookEvent(WebhookEvent event) {
        String webhookType = event.getWebhookType();
        
        switch (webhookType) {
            case "OUTBOUND_WEBHOOK" -> {
                // Process outbound webhook delivery
                deliverOutboundWebhook(event);
            }
            case "WEBHOOK_REGISTRATION" -> {
                // Register new webhook endpoint
                registerWebhookEndpoint(event);
            }
            case "WEBHOOK_SUBSCRIPTION" -> {
                // Handle webhook subscription
                handleWebhookSubscription(event);
            }
            case "WEBHOOK_VERIFICATION" -> {
                // Verify webhook endpoint
                verifyWebhookEndpoint(event);
            }
            case "WEBHOOK_DEACTIVATION" -> {
                // Deactivate webhook endpoint
                deactivateWebhookEndpoint(event);
            }
        }
    }

    private void deliverOutboundWebhook(WebhookEvent event) {
        String webhookId = event.getWebhookId();
        String targetUrl = event.getTargetUrl();
        
        // Map event to webhook payload
        Map<String, Object> payload = eventMappingService.mapEventToWebhookPayload(
            event.getOriginalEvent(),
            event.getEventType(),
            event.getMappingConfig()
        );
        
        // Add webhook metadata
        payload.put("webhook_id", webhookId);
        payload.put("event_id", event.getEventId());
        payload.put("timestamp", LocalDateTime.now());
        payload.put("version", event.getWebhookVersion());
        
        // Generate webhook signature
        String signature = webhookDeliveryService.generateWebhookSignature(
            payload,
            event.getWebhookSecret()
        );
        
        // Prepare headers
        Map<String, String> headers = Map.of(
            "Content-Type", "application/json",
            "X-Webhook-Signature", signature,
            "X-Event-Type", event.getEventType(),
            "X-Webhook-ID", webhookId,
            "User-Agent", "Waqiti-Webhook/1.0"
        );
        
        // Deliver webhook with retry logic
        CompletableFuture<Void> deliveryFuture = webhookDeliveryService.deliverWebhook(
            targetUrl,
            payload,
            headers,
            event.getTimeoutMs()
        );
        
        // Handle delivery result
        deliveryFuture.handle((result, throwable) -> {
            if (throwable == null) {
                // Webhook delivered successfully
                webhookDeliveryService.recordSuccessfulDelivery(
                    webhookId,
                    event.getEventId(),
                    targetUrl,
                    LocalDateTime.now()
                );
            } else {
                // Webhook delivery failed
                log.error("Webhook delivery failed: {} -> {}", webhookId, throwable.getMessage());
                
                // Schedule retry if configured
                if (event.isRetryEnabled() && event.getRetryCount() < event.getMaxRetries()) {
                    retryService.scheduleWebhookRetry(
                        webhookId,
                        event.getEventId(),
                        payload,
                        headers,
                        event.getRetryCount() + 1,
                        event.getRetryDelay()
                    );
                } else {
                    // Send to DLQ
                    webhookDeliveryService.sendToDeadLetterQueue(
                        webhookId,
                        event,
                        throwable.getMessage()
                    );
                }
                
                // Record failed delivery
                webhookDeliveryService.recordFailedDelivery(
                    webhookId,
                    event.getEventId(),
                    targetUrl,
                    throwable.getMessage(),
                    LocalDateTime.now()
                );
            }
            return null;
        });
        
        // Update webhook statistics
        webhookDeliveryService.updateWebhookStatistics(
            webhookId,
            event.getEventType(),
            targetUrl
        );
    }

    private void registerWebhookEndpoint(WebhookEvent event) {
        String merchantId = event.getMerchantId();
        String targetUrl = event.getTargetUrl();
        
        // Validate webhook URL
        boolean isValidUrl = webhookDeliveryService.validateWebhookUrl(targetUrl);
        
        if (!isValidUrl) {
            log.error("Invalid webhook URL: {}", targetUrl);
            webhookDeliveryService.rejectWebhookRegistration(
                event.getRegistrationId(),
                "INVALID_URL",
                "The provided webhook URL is not valid"
            );
            return;
        }
        
        // Test webhook endpoint
        boolean isReachable = webhookDeliveryService.testWebhookEndpoint(
            targetUrl,
            event.getTestPayload(),
            event.getTimeoutMs()
        );
        
        if (!isReachable) {
            log.error("Webhook endpoint not reachable: {}", targetUrl);
            webhookDeliveryService.rejectWebhookRegistration(
                event.getRegistrationId(),
                "UNREACHABLE",
                "The webhook endpoint is not reachable"
            );
            return;
        }
        
        // Register webhook
        String webhookId = webhookDeliveryService.registerWebhook(
            merchantId,
            targetUrl,
            event.getEventTypes(),
            event.getWebhookSecret(),
            event.getWebhookConfig()
        );
        
        // Send registration confirmation
        webhookDeliveryService.sendRegistrationConfirmation(
            merchantId,
            webhookId,
            targetUrl,
            event.getEventTypes()
        );
        
        // Log registration
        webhookDeliveryService.logWebhookRegistration(
            webhookId,
            merchantId,
            targetUrl,
            event.getEventTypes(),
            LocalDateTime.now()
        );
    }

    private void handleWebhookSubscription(WebhookEvent event) {
        String webhookId = event.getWebhookId();
        String subscriptionAction = event.getSubscriptionAction();
        
        switch (subscriptionAction) {
            case "SUBSCRIBE" -> {
                // Add event subscription
                webhookDeliveryService.subscribeToEvents(
                    webhookId,
                    event.getEventTypes(),
                    event.getFilterCriteria()
                );
            }
            case "UNSUBSCRIBE" -> {
                // Remove event subscription
                webhookDeliveryService.unsubscribeFromEvents(
                    webhookId,
                    event.getEventTypes()
                );
            }
            case "UPDATE_FILTERS" -> {
                // Update subscription filters
                webhookDeliveryService.updateSubscriptionFilters(
                    webhookId,
                    event.getEventTypes(),
                    event.getFilterCriteria()
                );
            }
        }
        
        // Update subscription metadata
        webhookDeliveryService.updateSubscriptionMetadata(
            webhookId,
            event.getSubscriptionMetadata(),
            LocalDateTime.now()
        );
    }

    private void verifyWebhookEndpoint(WebhookEvent event) {
        String webhookId = event.getWebhookId();
        String targetUrl = event.getTargetUrl();
        
        // Generate verification challenge
        String challenge = webhookDeliveryService.generateVerificationChallenge();
        
        // Send verification request
        Map<String, Object> verificationPayload = Map.of(
            "challenge", challenge,
            "webhook_id", webhookId,
            "verification_token", event.getVerificationToken()
        );
        
        CompletableFuture<String> verificationFuture = webhookDeliveryService.sendVerificationChallenge(
            targetUrl,
            verificationPayload,
            event.getTimeoutMs()
        );
        
        // Handle verification response
        verificationFuture.handle((response, throwable) -> {
            if (throwable == null && challenge.equals(response)) {
                // Verification successful
                webhookDeliveryService.markWebhookAsVerified(
                    webhookId,
                    LocalDateTime.now()
                );
                
                // Send verification success notification
                webhookDeliveryService.sendVerificationSuccess(
                    event.getMerchantId(),
                    webhookId,
                    targetUrl
                );
            } else {
                // Verification failed
                webhookDeliveryService.markWebhookAsUnverified(
                    webhookId,
                    throwable != null ? throwable.getMessage() : "Challenge mismatch",
                    LocalDateTime.now()
                );
                
                // Send verification failure notification
                webhookDeliveryService.sendVerificationFailure(
                    event.getMerchantId(),
                    webhookId,
                    targetUrl,
                    throwable != null ? throwable.getMessage() : "Challenge mismatch"
                );
            }
            return null;
        });
    }

    private void deactivateWebhookEndpoint(WebhookEvent event) {
        String webhookId = event.getWebhookId();
        String deactivationReason = event.getDeactivationReason();
        
        // Deactivate webhook
        webhookDeliveryService.deactivateWebhook(
            webhookId,
            deactivationReason,
            event.getDeactivatedBy(),
            LocalDateTime.now()
        );
        
        // Cancel pending deliveries
        webhookDeliveryService.cancelPendingDeliveries(webhookId);
        
        // Send deactivation notification
        webhookDeliveryService.sendDeactivationNotification(
            event.getMerchantId(),
            webhookId,
            deactivationReason
        );
        
        // Archive webhook data
        if (event.isArchiveData()) {
            webhookDeliveryService.archiveWebhookData(
                webhookId,
                event.getArchiveLocation(),
                event.getRetentionPeriod()
            );
        }
    }

    private void handleWebhookDlq(WebhookEvent event) {
        String dlqReason = event.getDlqReason();
        String webhookId = event.getWebhookId();
        
        log.error("Processing webhook DLQ event: {} - Reason: {} - Retry Count: {}", 
                webhookId, dlqReason, event.getRetryCount());
        
        switch (dlqReason) {
            case "MAX_RETRIES_EXCEEDED" -> {
                // Handle max retries exceeded
                webhookDeliveryService.handleMaxRetriesExceeded(
                    webhookId,
                    event.getEventId(),
                    event.getOriginalPayload(),
                    event.getRetryCount()
                );
                
                // Notify merchant of persistent failure
                webhookDeliveryService.notifyMerchantOfPersistentFailure(
                    event.getMerchantId(),
                    webhookId,
                    event.getTargetUrl(),
                    event.getFailureReason()
                );
            }
            case "ENDPOINT_UNREACHABLE" -> {
                // Handle unreachable endpoint
                webhookDeliveryService.handleUnreachableEndpoint(
                    webhookId,
                    event.getTargetUrl(),
                    event.getFailureDetails()
                );
                
                // Potentially deactivate webhook
                if (event.shouldAutoDeactivate()) {
                    webhookDeliveryService.autoDeactivateWebhook(
                        webhookId,
                        "ENDPOINT_UNREACHABLE",
                        LocalDateTime.now()
                    );
                }
            }
            case "INVALID_RESPONSE" -> {
                // Handle invalid response
                webhookDeliveryService.handleInvalidResponse(
                    webhookId,
                    event.getEventId(),
                    event.getResponseCode(),
                    event.getResponseBody()
                );
            }
            case "TIMEOUT" -> {
                // Handle timeout
                webhookDeliveryService.handleTimeout(
                    webhookId,
                    event.getEventId(),
                    event.getTimeoutMs()
                );
            }
        }
        
        // Store DLQ event for analysis
        webhookDeliveryService.storeDlqEvent(
            webhookId,
            event.getEventId(),
            dlqReason,
            event.getFailureDetails(),
            LocalDateTime.now()
        );
        
        // Update failure metrics
        webhookDeliveryService.updateFailureMetrics(
            webhookId,
            dlqReason,
            event.getRetryCount()
        );
    }

    private void handleSearchIndexing(WebhookEvent event) {
        String indexingType = event.getIndexingType();
        
        switch (indexingType) {
            case "DOCUMENT_INDEX" -> {
                // Index document
                integrationService.indexDocument(
                    event.getDocumentId(),
                    event.getDocumentType(),
                    event.getDocumentContent(),
                    event.getIndexMetadata()
                );
            }
            case "DOCUMENT_UPDATE" -> {
                // Update indexed document
                integrationService.updateIndexedDocument(
                    event.getDocumentId(),
                    event.getUpdatedFields(),
                    event.getUpdateTimestamp()
                );
            }
            case "DOCUMENT_DELETE" -> {
                // Delete from index
                integrationService.deleteFromIndex(
                    event.getDocumentId(),
                    event.getIndexName()
                );
            }
            case "BULK_INDEX" -> {
                // Bulk indexing operation
                integrationService.bulkIndex(
                    event.getIndexName(),
                    event.getDocuments(),
                    event.getBulkConfig()
                );
            }
            case "INDEX_REFRESH" -> {
                // Refresh search index
                integrationService.refreshSearchIndex(
                    event.getIndexName(),
                    event.getRefreshType()
                );
            }
        }
        
        // Update indexing metrics
        integrationService.updateIndexingMetrics(
            event.getIndexName(),
            indexingType,
            event.getProcessingTime(),
            event.getDocumentCount()
        );
    }

    private void handleContentAmplification(WebhookEvent event) {
        String amplificationType = event.getAmplificationType();
        
        switch (amplificationType) {
            case "SOCIAL_SHARE" -> {
                // Amplify content via social media
                integrationService.amplifyViaSocialMedia(
                    event.getContentId(),
                    event.getContentType(),
                    event.getSocialPlatforms(),
                    event.getAmplificationMessage()
                );
            }
            case "EMAIL_CAMPAIGN" -> {
                // Amplify via email campaign
                integrationService.amplifyViaEmailCampaign(
                    event.getContentId(),
                    event.getCampaignId(),
                    event.getTargetAudience(),
                    event.getEmailTemplate()
                );
            }
            case "PUSH_NOTIFICATION" -> {
                // Amplify via push notifications
                integrationService.amplifyViaPushNotification(
                    event.getContentId(),
                    event.getNotificationMessage(),
                    event.getTargetSegments(),
                    event.getScheduledTime()
                );
            }
            case "CONTENT_SYNDICATION" -> {
                // Syndicate content to partners
                integrationService.syndicateContent(
                    event.getContentId(),
                    event.getPartnerNetworks(),
                    event.getSyndicationRules()
                );
            }
            case "SEO_OPTIMIZATION" -> {
                // Optimize content for SEO
                integrationService.optimizeForSeo(
                    event.getContentId(),
                    event.getSeoKeywords(),
                    event.getOptimizationStrategies()
                );
            }
        }
        
        // Track amplification metrics
        integrationService.trackAmplificationMetrics(
            event.getContentId(),
            amplificationType,
            event.getReachMetrics(),
            event.getEngagementMetrics()
        );
    }

    private void handleReceiptGeneration(WebhookEvent event) {
        String receiptType = event.getReceiptType();
        
        switch (receiptType) {
            case "PAYMENT_RECEIPT" -> {
                // Generate payment receipt
                String receiptId = integrationService.generatePaymentReceipt(
                    event.getTransactionId(),
                    event.getPaymentDetails(),
                    event.getReceiptTemplate(),
                    event.getReceiptFormat()
                );
                
                // Store receipt
                integrationService.storeReceipt(
                    receiptId,
                    event.getUserId(),
                    event.getTransactionId(),
                    event.getReceiptData()
                );
                
                // Send receipt to customer
                if (event.isSendToCustomer()) {
                    integrationService.sendReceiptToCustomer(
                        receiptId,
                        event.getUserId(),
                        event.getDeliveryMethod(),
                        event.getContactDetails()
                    );
                }
            }
            case "INVOICE_RECEIPT" -> {
                // Generate invoice receipt
                integrationService.generateInvoiceReceipt(
                    event.getInvoiceId(),
                    event.getInvoiceDetails(),
                    event.getReceiptTemplate()
                );
            }
            case "REFUND_RECEIPT" -> {
                // Generate refund receipt
                integrationService.generateRefundReceipt(
                    event.getRefundId(),
                    event.getOriginalTransactionId(),
                    event.getRefundDetails(),
                    event.getReceiptTemplate()
                );
            }
            case "SUBSCRIPTION_RECEIPT" -> {
                // Generate subscription receipt
                integrationService.generateSubscriptionReceipt(
                    event.getSubscriptionId(),
                    event.getBillingPeriod(),
                    event.getSubscriptionDetails(),
                    event.getReceiptTemplate()
                );
            }
        }
        
        // Update receipt generation metrics
        integrationService.updateReceiptMetrics(
            receiptType,
            event.getReceiptFormat(),
            event.getGenerationTime()
        );
    }

    private void updateWebhookMetrics(WebhookEvent event) {
        // Update webhook processing metrics
        webhookDeliveryService.updateWebhookMetrics(
            event.getWebhookType(),
            event.getEventType(),
            event.getProcessingTime(),
            event.isSuccessful()
        );
        
        // Update integration metrics
        integrationService.updateIntegrationMetrics(
            event.getIntegrationType(),
            event.getTargetSystem(),
            event.getResponseTime()
        );
    }
}