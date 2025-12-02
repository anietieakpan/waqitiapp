package com.waqiti.webhook.controller;

import com.waqiti.common.security.SecurityContextUtil;
import com.waqiti.webhook.dto.CreateWebhookSubscriptionRequest;
import com.waqiti.webhook.dto.WebhookSubscriptionDTO;
import com.waqiti.webhook.model.WebhookEventType;
import com.waqiti.webhook.model.WebhookStatus;
import com.waqiti.webhook.service.WebhookManagementService;
import com.waqiti.webhook.service.WebhookRetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for Webhook Management
 * Provides comprehensive API for webhook subscription, delivery, and monitoring
 *
 * SECURITY FIX v2.0: IDOR vulnerability patched - October 23, 2025
 * All user IDs are now extracted from SecurityContext instead of request headers
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhook Management", description = "APIs for managing webhook subscriptions and deliveries")
@RequiredArgsConstructor
@Validated
@Slf4j
public class WebhookController {

    private final WebhookManagementService webhookManagementService;
    private final WebhookRetryService webhookRetryService;

    /**
     * Create a new webhook subscription
     *
     * SECURITY FIX: User ID is now extracted from SecurityContext, not from request header
     * This prevents IDOR attacks where users could create webhooks for other users
     */
    @PostMapping("/subscriptions")
    @Operation(summary = "Create webhook subscription", description = "Create a new webhook subscription for events")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Webhook subscription created"),
        @ApiResponse(responseCode = "400", description = "Invalid webhook data"),
        @ApiResponse(responseCode = "409", description = "Webhook already exists"),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<WebhookSubscriptionDTO> createWebhookSubscription(
            @Valid @RequestBody CreateWebhookSubscriptionRequest request,
            @RequestHeader(value = "X-Client-ID", required = false) String clientId) {

        // SECURITY FIX: Extract user ID from SecurityContext instead of request header
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Creating webhook subscription for authenticated user: {} to URL: {}",
                authenticatedUserId, request.getUrl());

        request.setUserId(authenticatedUserId.toString());
        request.setClientId(clientId);
        
        // Validate webhook URL by sending test request
        CompletableFuture<Boolean> urlValidation = webhookManagementService.validateWebhookUrl(
            request.getUrl(), request.getSecret()
        );
        
        try {
            if (!urlValidation.get()) {
                log.warn("Webhook URL validation failed for: {}", request.getUrl());
                return ResponseEntity.badRequest()
                    .body(WebhookSubscriptionDTO.builder()
                        .error("Webhook URL is not accessible or invalid")
                        .build());
            }
        } catch (Exception e) {
            log.error("Error validating webhook URL: {}", request.getUrl(), e);
            return ResponseEntity.badRequest()
                .body(WebhookSubscriptionDTO.builder()
                    .error("Failed to validate webhook URL: " + e.getMessage())
                    .build());
        }
        
        WebhookSubscriptionDTO subscription = webhookManagementService.createWebhookSubscription(request);
        
        log.info("Webhook subscription created successfully with ID: {}", subscription.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(subscription);
    }

    /**
     * Get webhook subscription by ID
     *
     * SECURITY FIX: User ID now extracted from SecurityContext with ownership validation
     */
    @GetMapping("/subscriptions/{subscriptionId}")
    @Operation(summary = "Get webhook subscription", description = "Get webhook subscription by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Webhook subscription found"),
        @ApiResponse(responseCode = "404", description = "Webhook subscription not found"),
        @ApiResponse(responseCode = "403", description = "Access denied - not owner"),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<WebhookSubscriptionDTO> getWebhookSubscription(
            @PathVariable @NotBlank String subscriptionId) {

        // SECURITY FIX: Extract user ID from SecurityContext
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();

        log.debug("Fetching webhook subscription: {} for authenticated user: {}",
                subscriptionId, authenticatedUserId);

        try {
            WebhookSubscriptionDTO subscription = webhookManagementService.getWebhookSubscription(
                    subscriptionId, authenticatedUserId.toString());
            return ResponseEntity.ok(subscription);
        } catch (Exception e) {
            log.error("Error fetching webhook subscription: {}", subscriptionId, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get all webhook subscriptions for a user
     *
     * SECURITY FIX: User ID now extracted from SecurityContext
     */
    @GetMapping("/subscriptions")
    @Operation(summary = "Get user webhook subscriptions", description = "Get all webhook subscriptions for the authenticated user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Webhook subscriptions retrieved"),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Page<WebhookSubscriptionDTO>> getUserWebhookSubscriptions(
            @RequestParam(required = false) WebhookEventType eventType,
            @RequestParam(required = false) WebhookStatus status,
            @RequestParam(required = false) Boolean isActive,
            Pageable pageable) {

        // SECURITY FIX: Extract user ID from SecurityContext
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();

        log.debug("Fetching webhook subscriptions for authenticated user: {} with eventType: {}, status: {}",
                authenticatedUserId, eventType, status);

        WebhookSubscriptionFilter filter = WebhookSubscriptionFilter.builder()
                .userId(authenticatedUserId.toString())
                .eventType(eventType)
                .status(status)
                .isActive(isActive)
                .build();

        Page<WebhookSubscriptionDTO> subscriptions = webhookManagementService
                .getUserWebhookSubscriptions(filter, pageable);

        return ResponseEntity.ok(subscriptions);
    }

    /**
     * Update webhook subscription
     *
     * SECURITY FIX: User ID now extracted from SecurityContext with ownership validation
     */
    @PutMapping("/subscriptions/{subscriptionId}")
    @Operation(summary = "Update webhook subscription", description = "Update an existing webhook subscription")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Webhook subscription updated"),
        @ApiResponse(responseCode = "400", description = "Invalid update data"),
        @ApiResponse(responseCode = "404", description = "Webhook subscription not found"),
        @ApiResponse(responseCode = "403", description = "Access denied - not owner"),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<WebhookSubscriptionDTO> updateWebhookSubscription(
            @PathVariable @NotBlank String subscriptionId,
            @Valid @RequestBody UpdateWebhookSubscriptionRequest request) {

        // SECURITY FIX: Extract user ID from SecurityContext
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Updating webhook subscription: {} by authenticated user: {}", subscriptionId, authenticatedUserId);

        request.setSubscriptionId(subscriptionId);
        request.setUpdatedBy(authenticatedUserId.toString());
        
        try {
            // Validate new URL if provided
            if (request.getUrl() != null) {
                CompletableFuture<Boolean> urlValidation = webhookManagementService
                        .validateWebhookUrl(request.getUrl(), request.getSecret());
                
                if (!urlValidation.get()) {
                    return ResponseEntity.badRequest()
                        .body(WebhookSubscriptionDTO.builder()
                            .error("New webhook URL is not accessible")
                            .build());
                }
            }
            
            WebhookSubscriptionDTO updatedSubscription = webhookManagementService
                    .updateWebhookSubscription(request);
            
            log.info("Webhook subscription updated successfully: {}", subscriptionId);
            return ResponseEntity.ok(updatedSubscription);
            
        } catch (Exception e) {
            log.error("Error updating webhook subscription: {}", subscriptionId, e);
            return ResponseEntity.badRequest()
                .body(WebhookSubscriptionDTO.builder()
                    .error("Failed to update webhook subscription: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Delete webhook subscription
     *
     * SECURITY FIX: User ID now extracted from SecurityContext with ownership validation
     */
    @DeleteMapping("/subscriptions/{subscriptionId}")
    @Operation(summary = "Delete webhook subscription", description = "Delete a webhook subscription")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Webhook subscription deleted"),
        @ApiResponse(responseCode = "404", description = "Webhook subscription not found"),
        @ApiResponse(responseCode = "403", description = "Access denied - not owner"),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Void> deleteWebhookSubscription(
            @PathVariable @NotBlank String subscriptionId) {

        // SECURITY FIX: Extract user ID from SecurityContext
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Deleting webhook subscription: {} by authenticated user: {}", subscriptionId, authenticatedUserId);

        try {
            webhookManagementService.deleteWebhookSubscription(subscriptionId, authenticatedUserId.toString());
            log.info("Webhook subscription deleted successfully: {}", subscriptionId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting webhook subscription: {}", subscriptionId, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Test webhook endpoint
     *
     * SECURITY FIX: User ID now extracted from SecurityContext with ownership validation
     */
    @PostMapping("/subscriptions/{subscriptionId}/test")
    @Operation(summary = "Test webhook", description = "Send a test webhook to verify configuration")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Test webhook sent successfully"),
        @ApiResponse(responseCode = "400", description = "Test webhook failed"),
        @ApiResponse(responseCode = "404", description = "Webhook subscription not found"),
        @ApiResponse(responseCode = "403", description = "Access denied - not owner"),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<WebhookTestResult> testWebhook(
            @PathVariable @NotBlank String subscriptionId,
            @RequestBody(required = false) Map<String, Object> testPayload) {

        // SECURITY FIX: Extract user ID from SecurityContext
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Testing webhook subscription: {} by authenticated user: {}", subscriptionId, authenticatedUserId);

        try {
            WebhookTestResult result = webhookManagementService.testWebhook(
                subscriptionId, authenticatedUserId.toString(), testPayload
            );
            
            if (result.isSuccess()) {
                log.info("Webhook test successful for subscription: {}", subscriptionId);
                return ResponseEntity.ok(result);
            } else {
                log.warn("Webhook test failed for subscription: {} - {}", subscriptionId, result.getErrorMessage());
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("Error testing webhook subscription: {}", subscriptionId, e);
            return ResponseEntity.badRequest()
                .body(WebhookTestResult.builder()
                    .success(false)
                    .errorMessage("Test failed: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Get webhook delivery history
     *
     * SECURITY FIX: User ID now extracted from SecurityContext with ownership validation
     */
    @GetMapping("/subscriptions/{subscriptionId}/deliveries")
    @Operation(summary = "Get webhook deliveries", description = "Get delivery history for a webhook subscription")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Webhook deliveries retrieved"),
        @ApiResponse(responseCode = "403", description = "Access denied - not owner"),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Page<WebhookDeliveryDTO>> getWebhookDeliveries(
            @PathVariable @NotBlank String subscriptionId,
            @RequestParam(required = false) WebhookStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Pageable pageable) {

        // SECURITY FIX: Extract user ID from SecurityContext
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();

        log.debug("Fetching webhook deliveries for subscription: {} by authenticated user: {}", subscriptionId, authenticatedUserId);

        WebhookDeliveryFilter filter = WebhookDeliveryFilter.builder()
                .subscriptionId(subscriptionId)
                .status(status)
                .startDate(startDate)
                .endDate(endDate)
                .build();

        Page<WebhookDeliveryDTO> deliveries = webhookManagementService
                .getWebhookDeliveries(filter, authenticatedUserId.toString(), pageable);
        
        return ResponseEntity.ok(deliveries);
    }

    /**
     * Retry failed webhook delivery
     *
     * SECURITY FIX: User ID now extracted from SecurityContext with ownership validation
     */
    @PostMapping("/deliveries/{deliveryId}/retry")
    @Operation(summary = "Retry webhook delivery", description = "Manually retry a failed webhook delivery")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Retry initiated"),
        @ApiResponse(responseCode = "400", description = "Cannot retry this delivery"),
        @ApiResponse(responseCode = "404", description = "Delivery not found"),
        @ApiResponse(responseCode = "403", description = "Access denied - not owner"),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<WebhookRetryResult> retryWebhookDelivery(
            @PathVariable @NotBlank String deliveryId,
            @RequestParam(defaultValue = "false") boolean forceRetry) {

        // SECURITY FIX: Extract user ID from SecurityContext
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Retrying webhook delivery: {} by authenticated user: {}, force: {}", deliveryId, authenticatedUserId, forceRetry);

        try {
            WebhookRetryResult result = webhookRetryService.retryDelivery(deliveryId, authenticatedUserId.toString(), forceRetry);
            
            if (result.isSuccess()) {
                log.info("Webhook delivery retry initiated: {}", deliveryId);
                return ResponseEntity.accepted().body(result);
            } else {
                log.warn("Webhook delivery retry failed: {} - {}", deliveryId, result.getErrorMessage());
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("Error retrying webhook delivery: {}", deliveryId, e);
            return ResponseEntity.badRequest()
                .body(WebhookRetryResult.builder()
                    .success(false)
                    .errorMessage("Retry failed: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Get webhook statistics
     *
     * SECURITY FIX: User ID now extracted from SecurityContext
     */
    @GetMapping("/statistics")
    @Operation(summary = "Get webhook statistics", description = "Get webhook delivery statistics")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Webhook statistics retrieved"),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<WebhookStatistics> getWebhookStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String subscriptionId) {

        // SECURITY FIX: Extract user ID from SecurityContext
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();

        log.debug("Fetching webhook statistics for authenticated user: {}", authenticatedUserId);

        WebhookStatisticsRequest request = WebhookStatisticsRequest.builder()
                .userId(authenticatedUserId.toString())
                .startDate(startDate)
                .endDate(endDate)
                .subscriptionId(subscriptionId)
                .build();
        
        WebhookStatistics statistics = webhookManagementService.getWebhookStatistics(request);
        return ResponseEntity.ok(statistics);
    }

    /**
     * Bulk update webhook subscriptions
     *
     * SECURITY FIX: Admin ID now extracted from SecurityContext
     */
    @PutMapping("/subscriptions/bulk")
    @Operation(summary = "Bulk update webhooks", description = "Update multiple webhook subscriptions")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bulk update completed"),
        @ApiResponse(responseCode = "403", description = "Admin access required"),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkWebhookUpdateResult> bulkUpdateWebhooks(
            @Valid @RequestBody BulkWebhookUpdateRequest request) {

        // SECURITY FIX: Extract admin ID from SecurityContext
        UUID authenticatedAdminId = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Bulk updating {} webhook subscriptions by authenticated admin: {}",
                request.getSubscriptionIds().size(), authenticatedAdminId);

        request.setUpdatedBy(authenticatedAdminId.toString());
        BulkWebhookUpdateResult result = webhookManagementService.bulkUpdateWebhooks(request);
        
        log.info("Bulk webhook update completed. Success: {}, Failed: {}", 
                result.getSuccessCount(), result.getFailureCount());
        
        return ResponseEntity.ok(result);
    }

    /**
     * Get webhook event types
     */
    @GetMapping("/event-types")
    @Operation(summary = "Get webhook event types", description = "Get all available webhook event types")
    public ResponseEntity<List<WebhookEventTypeInfo>> getWebhookEventTypes() {
        List<WebhookEventTypeInfo> eventTypes = webhookManagementService.getWebhookEventTypes();
        return ResponseEntity.ok(eventTypes);
    }

    /**
     * Webhook endpoint for incoming webhooks (for testing purposes)
     */
    @PostMapping("/incoming/{subscriptionId}")
    @Operation(summary = "Incoming webhook endpoint", description = "Endpoint to receive webhook callbacks")
    public ResponseEntity<Map<String, String>> receiveWebhook(
            @PathVariable @NotBlank String subscriptionId,
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            HttpServletRequest request) {
        
        log.info("Received incoming webhook for subscription: {}", subscriptionId);
        
        try {
            // Verify webhook signature if present
            if (signature != null) {
                boolean isValid = webhookManagementService.verifyWebhookSignature(
                    subscriptionId, payload, signature
                );
                
                if (!isValid) {
                    log.warn("Invalid webhook signature for subscription: {}", subscriptionId);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid signature"));
                }
            }
            
            // Process the webhook
            webhookManagementService.processIncomingWebhook(subscriptionId, payload, request);
            
            log.info("Webhook processed successfully for subscription: {}", subscriptionId);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Webhook processed successfully"
            ));
            
        } catch (Exception e) {
            log.error("Error processing incoming webhook for subscription: {}", subscriptionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to process webhook"));
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if webhook service is running")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = Map.of(
                "status", "UP",
                "service", "webhook-service",
                "timestamp", LocalDateTime.now().toString(),
                "components", Map.of(
                    "retryService", webhookRetryService.isHealthy() ? "UP" : "DOWN",
                    "managementService", "UP"
                )
        );
        
        return ResponseEntity.ok(health);
    }

    /**
     * Get failed webhook deliveries for retry
     */
    @GetMapping("/failed-deliveries")
    @Operation(summary = "Get failed deliveries", description = "Get failed webhook deliveries that need retry")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<WebhookDeliveryDTO>> getFailedDeliveries(
            @RequestParam(required = false) Integer maxRetries,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime failedAfter,
            Pageable pageable) {
        
        log.debug("Fetching failed webhook deliveries with maxRetries: {}", maxRetries);
        
        FailedDeliveryFilter filter = FailedDeliveryFilter.builder()
                .maxRetries(maxRetries)
                .failedAfter(failedAfter)
                .build();
        
        Page<WebhookDeliveryDTO> failedDeliveries = webhookRetryService.getFailedDeliveries(filter, pageable);
        return ResponseEntity.ok(failedDeliveries);
    }

    /**
     * Manually trigger webhook retry processing
     */
    @PostMapping("/retry/process")
    @Operation(summary = "Process webhook retries", description = "Manually trigger webhook retry processing")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WebhookRetryProcessResult> processWebhookRetries(
            @RequestParam(defaultValue = "100") int batchSize) {
        
        log.info("Manually triggering webhook retry processing with batch size: {}", batchSize);
        
        WebhookRetryProcessResult result = webhookRetryService.processRetries(batchSize);
        
        log.info("Webhook retry processing completed. Processed: {}, Failed: {}", 
                result.getProcessedCount(), result.getFailedCount());
        
        return ResponseEntity.ok(result);
    }
}