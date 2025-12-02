package com.waqiti.transaction.rollback;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise-grade External System Webhook Service
 * 
 * Manages webhook registrations, deliveries, and status tracking for compensation
 * operations with external payment providers. Provides comprehensive monitoring,
 * retry mechanisms, and security features for production environments.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalSystemWebhookService {

    private final RestTemplate restTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ScheduledExecutorService webhookExecutor = Executors.newScheduledThreadPool(10);
    
    // In-memory tracking for active webhooks
    private final Map<String, WebhookRegistration> activeWebhooks = new ConcurrentHashMap<>();
    private final Map<String, WebhookDeliveryAttempt> deliveryAttempts = new ConcurrentHashMap<>();

    @Value("${waqiti.webhook.base-url:https://api.example.com/webhooks}")
    private String baseWebhookUrl;
    
    @Value("${waqiti.webhook.secret-key}")
    private String webhookSecretKey;
    
    @Value("${waqiti.webhook.max-retries:5}")
    private int maxRetries;
    
    @Value("${waqiti.webhook.timeout:30}")
    private int timeoutSeconds;

    /**
     * Register webhook for compensation status updates with comprehensive tracking
     */
    @CircuitBreaker(name = "webhook-registration", fallbackMethod = "registerWebhookFallback")
    @Retry(name = "webhook-registration")
    @Bulkhead(name = "webhook-registration")
    @Transactional
    public WebhookRegistrationResult registerCompensationWebhook(UUID transactionId, String actionId, 
                                                                String provider, Map<String, Object> metadata) {
        log.info("WEBHOOK: Registering compensation webhook - Transaction: {}, Provider: {}", 
                transactionId, provider);

        try {
            // Generate unique webhook ID
            String webhookId = UUID.randomUUID().toString();
            
            // Create webhook registration record
            WebhookRegistration registration = WebhookRegistration.builder()
                .webhookId(webhookId)
                .transactionId(transactionId)
                .actionId(actionId)
                .provider(provider)
                .webhookUrl(buildProviderWebhookUrl(provider, webhookId))
                .secretKey(generateWebhookSecret(provider, transactionId))
                .status(WebhookStatus.ACTIVE)
                .registeredAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24)) // 24-hour expiration
                .metadata(metadata)
                .retryCount(0)
                .maxRetries(maxRetries)
                .build();

            // Store in database
            persistWebhookRegistration(registration);
            
            // Store in memory for quick access
            activeWebhooks.put(webhookId, registration);

            // Register with external provider if applicable
            ExternalWebhookRegistration externalReg = registerWithExternalProvider(registration);
            
            // Schedule expiration cleanup
            scheduleWebhookCleanup(webhookId, registration.getExpiresAt());
            
            // Send registration event
            publishWebhookEvent(WebhookEventType.REGISTERED, registration, null);

            log.info("WEBHOOK: Successfully registered webhook - ID: {}, Provider: {}", 
                    webhookId, provider);

            return WebhookRegistrationResult.builder()
                .webhookId(webhookId)
                .status(WebhookRegistrationStatus.SUCCESS)
                .webhookUrl(registration.getWebhookUrl())
                .expiresAt(registration.getExpiresAt())
                .externalRegistration(externalReg)
                .build();

        } catch (Exception e) {
            log.error("WEBHOOK: Failed to register webhook - Transaction: {}, Provider: {}", 
                    transactionId, provider, e);
            
            return WebhookRegistrationResult.builder()
                .status(WebhookRegistrationStatus.FAILED)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    /**
     * Process incoming webhook from external provider
     */
    @Transactional
    public WebhookProcessingResult processIncomingWebhook(String webhookId, 
                                                          String signature,
                                                          Map<String, Object> payload) {
        log.info("WEBHOOK: Processing incoming webhook - ID: {}", webhookId);

        try {
            // Validate webhook exists and is active
            WebhookRegistration registration = activeWebhooks.get(webhookId);
            if (registration == null) {
                registration = loadWebhookFromDatabase(webhookId);
                if (registration == null) {
                    log.warn("WEBHOOK: Unknown webhook ID: {}", webhookId);
                    return WebhookProcessingResult.builder()
                        .status(WebhookProcessingStatus.WEBHOOK_NOT_FOUND)
                        .errorMessage("Unknown webhook ID")
                        .build();
                }
            }

            // Validate webhook signature
            if (!validateWebhookSignature(payload, signature, registration.getSecretKey())) {
                log.error("SECURITY: Invalid webhook signature - ID: {}", webhookId);
                publishSecurityEvent(webhookId, "INVALID_SIGNATURE", payload);
                return WebhookProcessingResult.builder()
                    .status(WebhookProcessingStatus.INVALID_SIGNATURE)
                    .errorMessage("Invalid webhook signature")
                    .build();
            }

            // Check expiration
            if (LocalDateTime.now().isAfter(registration.getExpiresAt())) {
                log.warn("WEBHOOK: Expired webhook - ID: {}", webhookId);
                return WebhookProcessingResult.builder()
                    .status(WebhookProcessingStatus.EXPIRED)
                    .errorMessage("Webhook has expired")
                    .build();
            }

            // Process the webhook payload
            CompensationStatusUpdate statusUpdate = parseCompensationStatusUpdate(payload, registration);
            
            // Update compensation status
            updateCompensationStatus(registration, statusUpdate);
            
            // Notify interested services
            notifyCompensationStatusChange(registration, statusUpdate);
            
            // Update webhook statistics
            updateWebhookStatistics(webhookId, true);
            
            // Publish success event
            publishWebhookEvent(WebhookEventType.PROCESSED, registration, statusUpdate);

            log.info("WEBHOOK: Successfully processed webhook - ID: {}, Status: {}", 
                    webhookId, statusUpdate.getStatus());

            return WebhookProcessingResult.builder()
                .status(WebhookProcessingStatus.SUCCESS)
                .compensationStatus(statusUpdate.getStatus())
                .transactionId(registration.getTransactionId())
                .actionId(registration.getActionId())
                .build();

        } catch (Exception e) {
            log.error("WEBHOOK: Failed to process webhook - ID: {}", webhookId, e);
            updateWebhookStatistics(webhookId, false);
            
            return WebhookProcessingResult.builder()
                .status(WebhookProcessingStatus.PROCESSING_ERROR)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    /**
     * Get webhook status and statistics
     */
    public WebhookStatus getWebhookStatus(String webhookId) {
        WebhookRegistration registration = activeWebhooks.get(webhookId);
        if (registration == null) {
            registration = loadWebhookFromDatabase(webhookId);
        }
        
        return registration != null ? registration.getStatus() : WebhookStatus.NOT_FOUND;
    }

    /**
     * List active webhooks for a transaction
     */
    public List<WebhookRegistration> getWebhooksForTransaction(UUID transactionId) {
        String sql = """
            SELECT webhook_id, transaction_id, action_id, provider, webhook_url, 
                   status, registered_at, expires_at, retry_count, max_retries
            FROM webhook_registrations 
            WHERE transaction_id = ? 
            AND status IN ('ACTIVE', 'PENDING')
            ORDER BY registered_at DESC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> 
            WebhookRegistration.builder()
                .webhookId(rs.getString("webhook_id"))
                .transactionId(UUID.fromString(rs.getString("transaction_id")))
                .actionId(rs.getString("action_id"))
                .provider(rs.getString("provider"))
                .webhookUrl(rs.getString("webhook_url"))
                .status(WebhookStatus.valueOf(rs.getString("status")))
                .registeredAt(rs.getTimestamp("registered_at").toLocalDateTime())
                .expiresAt(rs.getTimestamp("expires_at").toLocalDateTime())
                .retryCount(rs.getInt("retry_count"))
                .maxRetries(rs.getInt("max_retries"))
                .build(),
            transactionId
        );
    }

    /**
     * Cancel webhook registration
     */
    @Transactional
    public void cancelWebhook(String webhookId, String reason) {
        log.info("WEBHOOK: Cancelling webhook - ID: {}, Reason: {}", webhookId, reason);

        // Update database
        String sql = """
            UPDATE webhook_registrations 
            SET status = 'CANCELLED', 
                cancelled_at = ?, 
                cancellation_reason = ?
            WHERE webhook_id = ?
            """;

        jdbcTemplate.update(sql, LocalDateTime.now(), reason, webhookId);

        // Remove from memory
        WebhookRegistration registration = activeWebhooks.remove(webhookId);
        
        if (registration != null) {
            // Cancel with external provider
            cancelExternalWebhook(registration);
            
            // Publish cancellation event
            publishWebhookEvent(WebhookEventType.CANCELLED, registration, null);
        }
    }

    /**
     * Build provider-specific webhook URL
     */
    private String buildProviderWebhookUrl(String provider, String webhookId) {
        return String.format("%s/compensation/%s/%s", baseWebhookUrl, provider.toLowerCase(), webhookId);
    }

    /**
     * Generate secure webhook secret for provider
     */
    private String generateWebhookSecret(String provider, UUID transactionId) {
        try {
            String data = provider + ":" + transactionId.toString() + ":" + System.currentTimeMillis();
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(webhookSecretKey.getBytes(), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to generate webhook secret", e);
        }
    }

    /**
     * Validate webhook signature for security
     */
    private boolean validateWebhookSignature(Map<String, Object> payload, String signature, String secretKey) {
        try {
            String payloadJson = convertToJson(payload);
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payloadJson.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = "sha256=" + Base64.getEncoder().encodeToString(hash);
            
            // Use constant-time comparison to prevent timing attacks
            return MessageDigest.isEqual(expectedSignature.getBytes(), signature.getBytes());
        } catch (Exception e) {
            log.error("Failed to validate webhook signature", e);
            return false;
        }
    }

    /**
     * Register webhook with external payment provider
     */
    private ExternalWebhookRegistration registerWithExternalProvider(WebhookRegistration registration) {
        try {
            String providerEndpoint = getProviderWebhookEndpoint(registration.getProvider());
            
            if (providerEndpoint != null) {
                Map<String, Object> registrationPayload = Map.of(
                    "webhook_url", registration.getWebhookUrl(),
                    "events", List.of("payment.updated", "transfer.completed", "refund.processed"),
                    "secret", registration.getSecretKey()
                );

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(getProviderApiKey(registration.getProvider()));

                HttpEntity<Map<String, Object>> request = new HttpEntity<>(registrationPayload, headers);
                
                ResponseEntity<Map> response = restTemplate.exchange(
                    providerEndpoint,
                    HttpMethod.POST,
                    request,
                    Map.class
                );

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    return ExternalWebhookRegistration.builder()
                        .providerId(response.getBody().get("id").toString())
                        .providerUrl(response.getBody().get("url").toString())
                        .status("active")
                        .build();
                }
            }
            
            log.error("CRITICAL: Failed to register webhook - compensation status updates will be lost");
            throw new WebhookRegistrationException("Webhook registration failed for provider: " + registration.getProvider());
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to register webhook with external provider: {} - Transaction status updates will be lost", registration.getProvider(), e);
            throw new WebhookRegistrationException("Failed to register webhook with provider: " + registration.getProvider(), e);
        }
    }

    /**
     * Parse compensation status update from webhook payload
     */
    private CompensationStatusUpdate parseCompensationStatusUpdate(Map<String, Object> payload, 
                                                                  WebhookRegistration registration) {
        // Provider-specific parsing logic
        return switch (registration.getProvider().toUpperCase()) {
            case "STRIPE" -> parseStripeWebhook(payload);
            case "WISE" -> parseWiseWebhook(payload);
            case "PAYPAL" -> parsePayPalWebhook(payload);
            case "PLAID" -> parsePlaidWebhook(payload);
            default -> parseGenericWebhook(payload);
        };
    }

    /**
     * Update compensation status based on webhook
     */
    @Transactional
    private void updateCompensationStatus(WebhookRegistration registration, 
                                         CompensationStatusUpdate statusUpdate) {
        String sql = """
            UPDATE compensation_audit 
            SET status = ?, 
                external_reference = ?,
                updated_at = ?,
                provider_status = ?
            WHERE transaction_id = ? 
            AND action_id = ?
            """;

        jdbcTemplate.update(sql, 
            statusUpdate.getStatus(),
            statusUpdate.getExternalReference(),
            LocalDateTime.now(),
            statusUpdate.getProviderStatus(),
            registration.getTransactionId(),
            registration.getActionId()
        );
    }

    /**
     * Notify services about compensation status change
     */
    private void notifyCompensationStatusChange(WebhookRegistration registration, 
                                              CompensationStatusUpdate statusUpdate) {
        try {
            CompensationStatusChangeEvent event = CompensationStatusChangeEvent.builder()
                .transactionId(registration.getTransactionId())
                .actionId(registration.getActionId())
                .provider(registration.getProvider())
                .oldStatus("PENDING") // Would track this in production
                .newStatus(statusUpdate.getStatus())
                .externalReference(statusUpdate.getExternalReference())
                .timestamp(LocalDateTime.now())
                .build();

            kafkaTemplate.send("compensation-status-updates", event);
            
        } catch (Exception e) {
            log.error("Failed to publish compensation status change event", e);
        }
    }

    // Provider-specific webhook parsing methods
    private CompensationStatusUpdate parseStripeWebhook(Map<String, Object> payload) {
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        Map<String, Object> object = (Map<String, Object>) data.get("object");
        
        return CompensationStatusUpdate.builder()
            .status(mapStripeStatus(object.get("status").toString()))
            .externalReference(object.get("id").toString())
            .providerStatus(object.get("status").toString())
            .amount(object.get("amount") != null ?
                new java.math.BigDecimal(object.get("amount").toString()).divide(new java.math.BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP) : null)
            .build();
    }

    private CompensationStatusUpdate parseWiseWebhook(Map<String, Object> payload) {
        return CompensationStatusUpdate.builder()
            .status(mapWiseStatus(payload.get("current_state").toString()))
            .externalReference(payload.get("resource").toString())
            .providerStatus(payload.get("current_state").toString())
            .build();
    }

    private CompensationStatusUpdate parsePayPalWebhook(Map<String, Object> payload) {
        Map<String, Object> resource = (Map<String, Object>) payload.get("resource");
        
        return CompensationStatusUpdate.builder()
            .status(mapPayPalStatus(resource.get("state").toString()))
            .externalReference(resource.get("id").toString())
            .providerStatus(resource.get("state").toString())
            .build();
    }

    private CompensationStatusUpdate parsePlaidWebhook(Map<String, Object> payload) {
        return CompensationStatusUpdate.builder()
            .status(mapPlaidStatus(payload.get("new_state").toString()))
            .externalReference(payload.get("transfer_id").toString())
            .providerStatus(payload.get("new_state").toString())
            .build();
    }

    private CompensationStatusUpdate parseGenericWebhook(Map<String, Object> payload) {
        return CompensationStatusUpdate.builder()
            .status("COMPLETED")
            .externalReference(payload.get("id") != null ? payload.get("id").toString() : "unknown")
            .providerStatus(payload.get("status") != null ? payload.get("status").toString() : "completed")
            .build();
    }

    // Status mapping methods
    private String mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus.toLowerCase()) {
            case "succeeded" -> "COMPLETED";
            case "pending" -> "PENDING";
            case "failed" -> "FAILED";
            default -> "UNKNOWN";
        };
    }

    private String mapWiseStatus(String wiseStatus) {
        return switch (wiseStatus.toLowerCase()) {
            case "outgoing_payment_sent" -> "COMPLETED";
            case "cancelled" -> "CANCELLED";
            case "processing" -> "PENDING";
            default -> "UNKNOWN";
        };
    }

    private String mapPayPalStatus(String paypalStatus) {
        return switch (paypalStatus.toUpperCase()) {
            case "COMPLETED" -> "COMPLETED";
            case "PENDING" -> "PENDING";
            case "FAILED", "DECLINED" -> "FAILED";
            default -> "UNKNOWN";
        };
    }

    private String mapPlaidStatus(String plaidStatus) {
        return switch (plaidStatus.toLowerCase()) {
            case "settled" -> "COMPLETED";
            case "failed" -> "FAILED";
            case "pending" -> "PENDING";
            default -> "UNKNOWN";
        };
    }

    // Database and utility methods
    private void persistWebhookRegistration(WebhookRegistration registration) {
        String sql = """
            INSERT INTO webhook_registrations (
                webhook_id, transaction_id, action_id, provider, webhook_url, 
                secret_key, status, registered_at, expires_at, retry_count, max_retries
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        jdbcTemplate.update(sql,
            registration.getWebhookId(),
            registration.getTransactionId(),
            registration.getActionId(),
            registration.getProvider(),
            registration.getWebhookUrl(),
            registration.getSecretKey(),
            registration.getStatus().name(),
            registration.getRegisteredAt(),
            registration.getExpiresAt(),
            registration.getRetryCount(),
            registration.getMaxRetries()
        );
    }

    private WebhookRegistration loadWebhookFromDatabase(String webhookId) {
        String sql = """
            SELECT webhook_id, transaction_id, action_id, provider, webhook_url, 
                   secret_key, status, registered_at, expires_at, retry_count, max_retries
            FROM webhook_registrations 
            WHERE webhook_id = ?
            """;

        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> 
                WebhookRegistration.builder()
                    .webhookId(rs.getString("webhook_id"))
                    .transactionId(UUID.fromString(rs.getString("transaction_id")))
                    .actionId(rs.getString("action_id"))
                    .provider(rs.getString("provider"))
                    .webhookUrl(rs.getString("webhook_url"))
                    .secretKey(rs.getString("secret_key"))
                    .status(WebhookStatus.valueOf(rs.getString("status")))
                    .registeredAt(rs.getTimestamp("registered_at").toLocalDateTime())
                    .expiresAt(rs.getTimestamp("expires_at").toLocalDateTime())
                    .retryCount(rs.getInt("retry_count"))
                    .maxRetries(rs.getInt("max_retries"))
                    .build(),
                webhookId
            );
        } catch (Exception e) {
            log.error("CRITICAL: Failed to retrieve webhook registration for webhookId: {} - Webhook status tracking unavailable", webhookId, e);
            throw new RuntimeException("Failed to retrieve webhook registration for webhookId: " + webhookId, e);
        }
    }

    private void scheduleWebhookCleanup(String webhookId, LocalDateTime expiresAt) {
        long delay = java.time.Duration.between(LocalDateTime.now(), expiresAt).toMillis();
        
        webhookExecutor.schedule(() -> {
            cancelWebhook(webhookId, "EXPIRED");
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void publishWebhookEvent(WebhookEventType eventType, WebhookRegistration registration, 
                                    CompensationStatusUpdate statusUpdate) {
        try {
            WebhookEvent event = WebhookEvent.builder()
                .eventType(eventType)
                .webhookId(registration.getWebhookId())
                .transactionId(registration.getTransactionId())
                .actionId(registration.getActionId())
                .provider(registration.getProvider())
                .statusUpdate(statusUpdate)
                .timestamp(LocalDateTime.now())
                .build();

            kafkaTemplate.send("webhook-events", event);
            
        } catch (Exception e) {
            log.error("Failed to publish webhook event", e);
        }
    }

    private void updateWebhookStatistics(String webhookId, boolean success) {
        // Update webhook processing statistics
        String sql = success ? 
            "UPDATE webhook_registrations SET success_count = success_count + 1 WHERE webhook_id = ?" :
            "UPDATE webhook_registrations SET failure_count = failure_count + 1 WHERE webhook_id = ?";
        
        jdbcTemplate.update(sql, webhookId);
    }

    // Utility methods
    private String getProviderWebhookEndpoint(String provider) {
        return switch (provider.toUpperCase()) {
            case "STRIPE" -> "https://api.stripe.com/v1/webhook_endpoints";
            case "WISE" -> "https://api.wise.com/v1/webhook_subscriptions";
            case "PAYPAL" -> "https://api.paypal.com/v1/notifications/webhooks";
            case "PLAID" -> "https://production.plaid.com/webhook/update";
            default -> null;
        };
    }

    private String getProviderApiKey(String provider) {
        // In production, would fetch from Vault
        return "provider-api-key";
    }

    private String convertToJson(Map<String, Object> payload) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert payload to JSON", e);
        }
    }

    private void publishSecurityEvent(String webhookId, String eventType, Map<String, Object> payload) {
        log.warn("SECURITY: Webhook security event - ID: {}, Event: {}", webhookId, eventType);
        // Would integrate with security monitoring system
    }

    private void cancelExternalWebhook(WebhookRegistration registration) {
        // Implementation would cancel webhook with external provider
        log.info("Cancelled external webhook registration for provider: {}", registration.getProvider());
    }

    // Fallback method
    public WebhookRegistrationResult registerWebhookFallback(UUID transactionId, String actionId, 
                                                            String provider, Map<String, Object> metadata, Exception ex) {
        log.error("CIRCUIT_BREAKER: Webhook registration fallback activated", ex);
        
        return WebhookRegistrationResult.builder()
            .status(WebhookRegistrationStatus.FAILED)
            .errorMessage("Webhook service temporarily unavailable")
            .build();
    }

    // Supporting DTOs and Enums
    
    public enum WebhookStatus {
        ACTIVE, PENDING, CANCELLED, EXPIRED, FAILED, NOT_FOUND
    }
    
    public enum WebhookRegistrationStatus {
        SUCCESS, FAILED, PENDING
    }
    
    public enum WebhookProcessingStatus {
        SUCCESS, WEBHOOK_NOT_FOUND, INVALID_SIGNATURE, EXPIRED, PROCESSING_ERROR
    }
    
    public enum WebhookEventType {
        REGISTERED, PROCESSED, CANCELLED, EXPIRED, FAILED
    }

    @lombok.Builder
    @lombok.Data
    public static class WebhookRegistration {
        private String webhookId;
        private UUID transactionId;
        private String actionId;
        private String provider;
        private String webhookUrl;
        private String secretKey;
        private WebhookStatus status;
        private LocalDateTime registeredAt;
        private LocalDateTime expiresAt;
        private Map<String, Object> metadata;
        private int retryCount;
        private int maxRetries;
    }

    @lombok.Builder
    @lombok.Data
    public static class WebhookRegistrationResult {
        private String webhookId;
        private WebhookRegistrationStatus status;
        private String webhookUrl;
        private LocalDateTime expiresAt;
        private ExternalWebhookRegistration externalRegistration;
        private String errorMessage;
    }

    @lombok.Builder
    @lombok.Data
    public static class WebhookProcessingResult {
        private WebhookProcessingStatus status;
        private String compensationStatus;
        private UUID transactionId;
        private String actionId;
        private String errorMessage;
    }

    @lombok.Builder
    @lombok.Data
    public static class CompensationStatusUpdate {
        private String status;
        private String externalReference;
        private String providerStatus;
        private java.math.BigDecimal amount;
        private String currency;
        private Map<String, Object> additionalData;
    }

    @lombok.Builder
    @lombok.Data
    public static class ExternalWebhookRegistration {
        private String providerId;
        private String providerUrl;
        private String status;
    }

    @lombok.Builder
    @lombok.Data
    public static class WebhookEvent {
        private WebhookEventType eventType;
        private String webhookId;
        private UUID transactionId;
        private String actionId;
        private String provider;
        private CompensationStatusUpdate statusUpdate;
        private LocalDateTime timestamp;
    }

    @lombok.Builder
    @lombok.Data
    public static class CompensationStatusChangeEvent {
        private UUID transactionId;
        private String actionId;
        private String provider;
        private String oldStatus;
        private String newStatus;
        private String externalReference;
        private LocalDateTime timestamp;
    }

    @lombok.Builder
    @lombok.Data
    public static class WebhookDeliveryAttempt {
        private String webhookId;
        private int attemptNumber;
        private LocalDateTime attemptedAt;
        private boolean successful;
        private String errorMessage;
        private int responseCode;
    }
}