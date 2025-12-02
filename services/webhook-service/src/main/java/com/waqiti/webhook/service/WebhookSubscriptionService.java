package com.waqiti.webhook.service;

import com.waqiti.webhook.entity.*;
import com.waqiti.webhook.model.WebhookPayload;
import com.waqiti.webhook.repository.WebhookSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Webhook subscription management service
 * Manages webhook endpoints and event subscriptions
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookSubscriptionService {
    
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryService deliveryService;
    
    @Value("${webhook.max-subscriptions-per-tenant:10}")
    private int maxSubscriptionsPerTenant;
    
    @Value("${webhook.endpoint-validation.enabled:true}")
    private boolean endpointValidationEnabled;
    
    @Value("${webhook.endpoint-health-check.enabled:true}")
    private boolean healthCheckEnabled;
    
    // Subscription cache for performance
    private final Map<String, List<WebhookSubscription>> eventSubscriptionsCache = new ConcurrentHashMap<>();
    private final Map<String, WebhookSubscription> subscriptionCache = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        loadActiveSubscriptions();
        log.info("Webhook Subscription Service initialized");
    }
    
    /**
     * Create new webhook subscription
     */
    @Transactional
    public WebhookSubscription createSubscription(SubscriptionRequest request) {
        try {
            log.info("Creating webhook subscription for tenant: {} - URL: {}", 
                request.getTenantId(), request.getEndpointUrl());
            
            // Validate request
            validateSubscriptionRequest(request);
            
            // Check subscription limits
            checkSubscriptionLimits(request.getTenantId());
            
            // Validate endpoint URL
            if (endpointValidationEnabled) {
                validateEndpointUrl(request.getEndpointUrl());
            }
            
            // Create subscription
            WebhookSubscription subscription = WebhookSubscription.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(request.getTenantId())
                .name(request.getName())
                .description(request.getDescription())
                .endpointUrl(request.getEndpointUrl())
                .secret(generateWebhookSecret())
                .authType(request.getAuthType())
                .authCredentials(encryptCredentials(request.getAuthCredentials()))
                .events(request.getEvents())
                .httpMethod(request.getHttpMethod() != null ? request.getHttpMethod() : WebhookHttpMethod.POST)
                .customHeaders(request.getCustomHeaders())
                .retryConfig(buildRetryConfig(request))
                .status(SubscriptionStatus.PENDING_VERIFICATION)
                .active(false)
                .createdAt(LocalDateTime.now())
                .lastModifiedAt(LocalDateTime.now())
                .metadata(request.getMetadata())
                .build();
            
            subscription = subscriptionRepository.save(subscription);
            
            // Send verification webhook
            sendVerificationWebhook(subscription);
            
            // Update cache
            updateSubscriptionCache(subscription);
            
            log.info("Webhook subscription created: {} - Status: PENDING_VERIFICATION", subscription.getId());
            
            return subscription;
            
        } catch (Exception e) {
            log.error("Failed to create webhook subscription", e);
            throw new WebhookException("Failed to create subscription: " + e.getMessage(), e);
        }
    }
    
    /**
     * Update webhook subscription
     */
    @Transactional
    public WebhookSubscription updateSubscription(String subscriptionId, SubscriptionUpdateRequest request) {
        try {
            WebhookSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new WebhookException("Subscription not found: " + subscriptionId));
            
            // Update allowed fields
            if (request.getName() != null) {
                subscription.setName(request.getName());
            }
            
            if (request.getDescription() != null) {
                subscription.setDescription(request.getDescription());
            }
            
            if (request.getEvents() != null && !request.getEvents().isEmpty()) {
                subscription.setEvents(request.getEvents());
            }
            
            if (request.getCustomHeaders() != null) {
                subscription.setCustomHeaders(request.getCustomHeaders());
            }
            
            if (request.getRetryConfig() != null) {
                subscription.setRetryConfig(buildRetryConfig(request));
            }
            
            if (request.getActive() != null) {
                subscription.setActive(request.getActive());
            }
            
            subscription.setLastModifiedAt(LocalDateTime.now());
            
            subscription = subscriptionRepository.save(subscription);
            
            // Update cache
            updateSubscriptionCache(subscription);
            
            log.info("Webhook subscription updated: {}", subscriptionId);
            
            return subscription;
            
        } catch (Exception e) {
            log.error("Failed to update webhook subscription: {}", subscriptionId, e);
            throw new WebhookException("Failed to update subscription: " + e.getMessage(), e);
        }
    }
    
    /**
     * Verify webhook endpoint
     */
    @Transactional
    public void verifyEndpoint(String subscriptionId, String verificationToken) {
        try {
            WebhookSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new WebhookException("Subscription not found: " + subscriptionId));
            
            if (!subscription.getVerificationToken().equals(verificationToken)) {
                throw new WebhookException("Invalid verification token");
            }
            
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setActive(true);
            subscription.setVerifiedAt(LocalDateTime.now());
            
            subscriptionRepository.save(subscription);
            
            // Update cache
            updateSubscriptionCache(subscription);
            
            log.info("Webhook endpoint verified: {} - {}", subscriptionId, subscription.getEndpointUrl());
            
        } catch (Exception e) {
            log.error("Failed to verify webhook endpoint", e);
            throw new WebhookException("Endpoint verification failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Process event and send to subscribed webhooks
     */
    @KafkaListener(topics = "platform.events")
    public void processEvent(String eventMessage) {
        try {
            Event event = parseEvent(eventMessage);
            
            log.debug("Processing event for webhook delivery: {} - {}", event.getType(), event.getId());
            
            // Get active subscriptions for this event
            List<WebhookSubscription> subscriptions = getActiveSubscriptionsForEvent(event.getType());
            
            if (subscriptions.isEmpty()) {
                log.debug("No active webhook subscriptions for event: {}", event.getType());
                return;
            }
            
            // Send webhook to each subscription
            for (WebhookSubscription subscription : subscriptions) {
                try {
                    sendWebhookForEvent(subscription, event);
                } catch (Exception e) {
                    log.error("Failed to send webhook for subscription: {}", subscription.getId(), e);
                }
            }
            
            log.info("Event {} sent to {} webhook subscriptions", event.getId(), subscriptions.size());
            
        } catch (Exception e) {
            log.error("Error processing event for webhooks", e);
        }
    }
    
    /**
     * Send webhook for event
     */
    private void sendWebhookForEvent(WebhookSubscription subscription, Event event) {
        try {
            // Build webhook payload
            WebhookPayload payload = WebhookPayload.builder()
                .eventId(event.getId())
                .eventType(event.getType())
                .endpointUrl(subscription.getEndpointUrl())
                .secret(subscription.getSecret())
                .priority(determinePriority(event))
                .timestamp(LocalDateTime.now())
                .data(event.getData())
                .customHeaders(subscription.getCustomHeaders())
                .metadata(Map.of(
                    "subscriptionId", subscription.getId(),
                    "tenantId", subscription.getTenantId()
                ))
                .maxRetries(subscription.getRetryConfig().getMaxAttempts())
                .initialDelayMs(subscription.getRetryConfig().getInitialDelayMs())
                .backoffMultiplier(subscription.getRetryConfig().getBackoffMultiplier())
                .build();
            
            // Send webhook via delivery service
            deliveryService.sendWebhook(payload);
            
            // Update subscription statistics
            updateSubscriptionStats(subscription, true);
            
        } catch (Exception e) {
            log.error("Failed to send webhook for subscription: {}", subscription.getId(), e);
            updateSubscriptionStats(subscription, false);
        }
    }
    
    /**
     * Get active subscriptions for event type
     */
    @Cacheable(value = "webhook-subscriptions", key = "#eventType")
    public List<WebhookSubscription> getActiveSubscriptionsForEvent(String eventType) {
        // Check cache first
        List<WebhookSubscription> cached = eventSubscriptionsCache.get(eventType);
        if (cached != null) {
            return cached;
        }
        
        // Query database
        List<WebhookSubscription> subscriptions = subscriptionRepository
            .findByActiveAndEventsContaining(true, eventType);
        
        // Filter for additional criteria
        subscriptions = subscriptions.stream()
            .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
            .filter(s -> !isEndpointUnhealthy(s))
            .collect(Collectors.toList());
        
        // Update cache
        eventSubscriptionsCache.put(eventType, subscriptions);
        
        return subscriptions;
    }
    
    /**
     * Check endpoint health
     */
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void checkEndpointHealth() {
        if (!healthCheckEnabled) {
            return;
        }
        
        try {
            log.debug("Checking webhook endpoint health");
            
            List<WebhookSubscription> activeSubscriptions = subscriptionRepository.findByActive(true);
            
            for (WebhookSubscription subscription : activeSubscriptions) {
                checkSubscriptionHealth(subscription);
            }
            
        } catch (Exception e) {
            log.error("Error checking endpoint health", e);
        }
    }
    
    /**
     * Check individual subscription health
     */
    private void checkSubscriptionHealth(WebhookSubscription subscription) {
        try {
            // Check recent delivery failures
            int recentFailures = getRecentFailureCount(subscription.getId());
            
            if (recentFailures > 10) {
                log.warn("Webhook subscription {} has {} recent failures - marking unhealthy", 
                    subscription.getId(), recentFailures);
                
                subscription.setHealthy(false);
                subscription.setLastHealthCheckAt(LocalDateTime.now());
                subscription.setHealthCheckMessage("Too many recent failures: " + recentFailures);
                
                // Auto-disable if too many failures
                if (recentFailures > 50) {
                    subscription.setActive(false);
                    subscription.setStatus(SubscriptionStatus.SUSPENDED);
                    log.error("Auto-disabled webhook subscription due to excessive failures: {}", 
                        subscription.getId());
                }
                
                subscriptionRepository.save(subscription);
            } else {
                subscription.setHealthy(true);
                subscription.setLastHealthCheckAt(LocalDateTime.now());
                subscription.setHealthCheckMessage("Healthy");
                subscriptionRepository.save(subscription);
            }
            
        } catch (Exception e) {
            log.error("Error checking subscription health: {}", subscription.getId(), e);
        }
    }
    
    /**
     * Validate subscription request
     */
    private void validateSubscriptionRequest(SubscriptionRequest request) {
        if (request.getTenantId() == null || request.getTenantId().isEmpty()) {
            throw new IllegalArgumentException("Tenant ID is required");
        }
        
        if (request.getEndpointUrl() == null || request.getEndpointUrl().isEmpty()) {
            throw new IllegalArgumentException("Endpoint URL is required");
        }
        
        if (request.getEvents() == null || request.getEvents().isEmpty()) {
            throw new IllegalArgumentException("At least one event subscription is required");
        }
        
        // Validate events exist
        for (String event : request.getEvents()) {
            try {
                WebhookEventType.valueOf(event);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid event type: " + event);
            }
        }
    }
    
    /**
     * Check subscription limits
     */
    private void checkSubscriptionLimits(String tenantId) {
        long existingCount = subscriptionRepository.countByTenantIdAndActive(tenantId, true);
        
        if (existingCount >= maxSubscriptionsPerTenant) {
            throw new WebhookException("Subscription limit exceeded for tenant: " + tenantId);
        }
    }
    
    /**
     * Validate endpoint URL
     */
    private void validateEndpointUrl(String endpointUrl) {
        try {
            URL url = new URL(endpointUrl);
            
            // Ensure HTTPS for production
            if (!"https".equalsIgnoreCase(url.getProtocol()) && !isLocalEnvironment()) {
                throw new WebhookException("HTTPS is required for webhook endpoints");
            }
            
            // Block internal/private IPs
            if (isInternalUrl(url)) {
                throw new WebhookException("Internal URLs are not allowed");
            }
            
        } catch (MalformedURLException e) {
            throw new WebhookException("Invalid endpoint URL: " + endpointUrl);
        }
    }
    
    /**
     * Generate webhook secret
     */
    private String generateWebhookSecret() {
        byte[] secret = new byte[32];
        SECURE_RANDOM.nextBytes(secret);
        return Base64.getEncoder().encodeToString(secret);
    }
    
    /**
     * Send verification webhook
     */
    private void sendVerificationWebhook(WebhookSubscription subscription) {
        String verificationToken = UUID.randomUUID().toString();
        subscription.setVerificationToken(verificationToken);
        
        Map<String, Object> verificationData = Map.of(
            "type", "webhook.verification",
            "subscriptionId", subscription.getId(),
            "verificationToken", verificationToken,
            "verificationUrl", buildVerificationUrl(subscription.getId(), verificationToken)
        );
        
        WebhookPayload payload = WebhookPayload.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("WEBHOOK_VERIFICATION")
            .endpointUrl(subscription.getEndpointUrl())
            .secret(subscription.getSecret())
            .priority(WebhookPriority.HIGH)
            .timestamp(LocalDateTime.now())
            .data(verificationData)
            .maxRetries(3)
            .build();
        
        deliveryService.sendWebhook(payload);
    }
    
    private void loadActiveSubscriptions() {
        // Load active subscriptions into cache
        List<WebhookSubscription> activeSubscriptions = subscriptionRepository.findByActive(true);
        for (WebhookSubscription subscription : activeSubscriptions) {
            subscriptionCache.put(subscription.getId(), subscription);
        }
        log.info("Loaded {} active webhook subscriptions", activeSubscriptions.size());
    }
    
    private void updateSubscriptionCache(WebhookSubscription subscription) {
        subscriptionCache.put(subscription.getId(), subscription);
        eventSubscriptionsCache.clear(); // Clear event cache to force refresh
    }
    
    private boolean isEndpointUnhealthy(WebhookSubscription subscription) {
        return !subscription.isHealthy() || subscription.getStatus() == SubscriptionStatus.SUSPENDED;
    }
    
    private int getRecentFailureCount(String subscriptionId) {
        try {
            LocalDateTime since = LocalDateTime.now().minusHours(24);
            return deliveryService.countFailuresSince(subscriptionId, since);
        } catch (Exception e) {
            log.error("Failed to get recent failure count for subscription: {}", subscriptionId, e);
            return 0;
        }
    }
    
    private boolean isLocalEnvironment() {
        String environment = System.getenv("ENVIRONMENT");
        if (environment == null) {
            environment = System.getProperty("environment", "production");
        }
        return "local".equalsIgnoreCase(environment) || 
               "development".equalsIgnoreCase(environment) || 
               "dev".equalsIgnoreCase(environment);
    }
    
    private boolean isInternalUrl(URL url) {
        String host = url.getHost();
        return host.startsWith("10.") || 
               host.startsWith("192.168.") || 
               host.startsWith("172.") ||
               host.equals("localhost") ||
               host.equals("127.0.0.1");
    }
    
    private String buildVerificationUrl(String subscriptionId, String token) {
        return "/webhooks/verify/" + subscriptionId + "/" + token;
    }
    
    private Event parseEvent(String eventMessage) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> eventMap = mapper.readValue(eventMessage, Map.class);
            
            Event event = new Event();
            event.id = (String) eventMap.get("id");
            event.type = (String) eventMap.get("type");
            event.data = eventMap.get("data");
            
            if (event.id == null || event.type == null) {
                throw new IllegalArgumentException("Event must have id and type fields");
            }
            
            return event;
        } catch (Exception e) {
            log.error("Failed to parse event message: {}", eventMessage, e);
            throw new WebhookException("Failed to parse event: " + e.getMessage(), e);
        }
    }
    
    private WebhookPriority determinePriority(Event event) {
        if (event.type == null) {
            return WebhookPriority.NORMAL;
        }
        
        // High priority events
        List<String> highPriorityEvents = Arrays.asList(
            "PAYMENT_FAILED",
            "PAYMENT_REVERSED",
            "FRAUD_DETECTED",
            "ACCOUNT_FROZEN",
            "WALLET_FROZEN",
            "KYC_EXPIRED",
            "COMPLIANCE_ALERT",
            "AML_ALERT",
            "SAR_FILING_REQUIRED",
            "TRANSACTION_BLOCKED"
        );
        
        if (highPriorityEvents.stream().anyMatch(e -> event.type.toUpperCase().contains(e))) {
            return WebhookPriority.HIGH;
        }
        
        // Critical priority events
        List<String> criticalEvents = Arrays.asList(
            "SECURITY_BREACH",
            "SANCTIONS_MATCH",
            "SYSTEM_FAILURE"
        );
        
        if (criticalEvents.stream().anyMatch(e -> event.type.toUpperCase().contains(e))) {
            return WebhookPriority.CRITICAL;
        }
        
        return WebhookPriority.NORMAL;
    }
    
    private void updateSubscriptionStats(WebhookSubscription subscription, boolean success) {
        try {
            if (success) {
                subscription.setTotalDeliveries(subscription.getTotalDeliveries() + 1);
                subscription.setSuccessfulDeliveries(subscription.getSuccessfulDeliveries() + 1);
                subscription.setLastSuccessfulDeliveryAt(LocalDateTime.now());
                
                // Reset consecutive failures on success
                subscription.setConsecutiveFailures(0);
            } else {
                subscription.setTotalDeliveries(subscription.getTotalDeliveries() + 1);
                subscription.setFailedDeliveries(subscription.getFailedDeliveries() + 1);
                subscription.setLastFailedDeliveryAt(LocalDateTime.now());
                subscription.setConsecutiveFailures(subscription.getConsecutiveFailures() + 1);
                
                // Auto-disable after too many consecutive failures
                if (subscription.getConsecutiveFailures() >= 20) {
                    log.error("Auto-disabling webhook subscription {} due to {} consecutive failures",
                        subscription.getId(), subscription.getConsecutiveFailures());
                    subscription.setActive(false);
                    subscription.setStatus(SubscriptionStatus.SUSPENDED);
                    subscription.setHealthCheckMessage("Auto-disabled: " + subscription.getConsecutiveFailures() + " consecutive failures");
                }
            }
            
            // Calculate success rate
            if (subscription.getTotalDeliveries() > 0) {
                double successRate = (double) subscription.getSuccessfulDeliveries() / subscription.getTotalDeliveries();
                subscription.setSuccessRate(successRate);
            }
            
            subscriptionRepository.save(subscription);
            
        } catch (Exception e) {
            log.error("Failed to update subscription stats: {}", subscription.getId(), e);
        }
    }
    
    private String encryptCredentials(String credentials) {
        if (credentials == null || credentials.isEmpty()) {
            return null;
        }
        
        try {
            // Use AES encryption for credentials
            javax.crypto.KeyGenerator keyGen = javax.crypto.KeyGenerator.getInstance("AES");
            keyGen.init(256, SECURE_RANDOM);
            javax.crypto.SecretKey secretKey = keyGen.generateKey();
            
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey);
            
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            // Combine IV and encrypted data
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            
            return Base64.getEncoder().encodeToString(combined);
            
        } catch (Exception e) {
            log.error("Failed to encrypt credentials", e);
            throw new WebhookException("Failed to encrypt authentication credentials", e);
        }
    }
    
    private RetryConfig buildRetryConfig(Object request) {
        RetryConfig config = new RetryConfig();
        
        try {
            if (request instanceof SubscriptionRequest) {
                SubscriptionRequest req = (SubscriptionRequest) request;
                if (req.getRetryConfig() != null) {
                    config.maxAttempts = req.getRetryConfig().getMaxAttempts() > 0 ? 
                        req.getRetryConfig().getMaxAttempts() : 5;
                    config.initialDelayMs = req.getRetryConfig().getInitialDelayMs() > 0 ? 
                        req.getRetryConfig().getInitialDelayMs() : 1000;
                    config.backoffMultiplier = req.getRetryConfig().getBackoffMultiplier() > 0 ? 
                        req.getRetryConfig().getBackoffMultiplier() : 2.0;
                }
            } else if (request instanceof SubscriptionUpdateRequest) {
                SubscriptionUpdateRequest req = (SubscriptionUpdateRequest) request;
                if (req.getRetryConfig() != null) {
                    config.maxAttempts = req.getRetryConfig().getMaxAttempts() > 0 ? 
                        req.getRetryConfig().getMaxAttempts() : 5;
                    config.initialDelayMs = req.getRetryConfig().getInitialDelayMs() > 0 ? 
                        req.getRetryConfig().getInitialDelayMs() : 1000;
                    config.backoffMultiplier = req.getRetryConfig().getBackoffMultiplier() > 0 ? 
                        req.getRetryConfig().getBackoffMultiplier() : 2.0;
                }
            }
            
            // Apply limits
            config.maxAttempts = Math.min(config.maxAttempts, 10); // Max 10 retries
            config.initialDelayMs = Math.max(config.initialDelayMs, 100); // Min 100ms delay
            config.backoffMultiplier = Math.min(config.backoffMultiplier, 5.0); // Max 5x multiplier
            
        } catch (Exception e) {
            log.warn("Failed to build retry config from request, using defaults", e);
        }
        
        return config;
    }
    
    // Supporting classes would be in separate files
    static class Event {
        String id;
        String type;
        Object data;
        
        public String getId() { return id; }
        public String getType() { return type; }
        public Object getData() { return data; }
    }
    
    static class RetryConfig {
        int maxAttempts = 5;
        long initialDelayMs = 1000;
        double backoffMultiplier = 2.0;
        
        public int getMaxAttempts() { return maxAttempts; }
        public long getInitialDelayMs() { return initialDelayMs; }
        public double getBackoffMultiplier() { return backoffMultiplier; }
    }
}