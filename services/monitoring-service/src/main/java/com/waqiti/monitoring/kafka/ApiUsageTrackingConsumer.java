package com.waqiti.monitoring.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.kafka.KafkaRetryHandler;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.monitoring.model.MonitoringEvent;
import com.waqiti.monitoring.service.AlertingService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
public class ApiUsageTrackingConsumer extends BaseKafkaConsumer {
    
    public ApiUsageTrackingConsumer(
            MetricsService metricsService,
            AlertingService alertingService,
            KafkaRetryHandler retryHandler,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.metricsService = metricsService;
        this.alertingService = alertingService;
        this.retryHandler = retryHandler;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }
    private static final Logger logger = LoggerFactory.getLogger(ApiUsageTrackingConsumer.class);
    private static final String CONSUMER_GROUP_ID = "api-usage-tracking-group";
    private static final String DLQ_TOPIC = "api-usage-tracking-dlq";
    
    private final MetricsService metricsService;
    private final AlertingService alertingService;
    private final KafkaRetryHandler retryHandler;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    
    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Value("${monitoring.api.rate-limit-threshold:1000}")
    private int rateLimitThreshold;
    
    @Value("${monitoring.api.error-rate-threshold:0.05}")
    private double errorRateThreshold;
    
    @Value("${monitoring.api.latency-threshold-ms:500}")
    private long latencyThresholdMs;
    
    @Value("${monitoring.api.quota-warning-percentage:80}")
    private double quotaWarningPercentage;
    
    @Value("${monitoring.api.throttle-threshold:100}")
    private int throttleThreshold;
    
    @Value("${monitoring.api.usage-window-minutes:5}")
    private int usageWindowMinutes;
    
    @Value("${monitoring.api.billing-calculation-interval-hours:1}")
    private int billingCalculationIntervalHours;
    
    @Value("${monitoring.api.deprecated-api-threshold-days:30}")
    private int deprecatedApiThresholdDays;
    
    @Value("${monitoring.api.abuse-detection-enabled:true}")
    private boolean abuseDetectionEnabled;
    
    @Value("${monitoring.api.cost-tracking-enabled:true}")
    private boolean costTrackingEnabled;
    
    @Value("${monitoring.api.analytics-retention-days:90}")
    private int analyticsRetentionDays;
    
    private final Map<String, ApiEndpointMetrics> endpointMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, ClientUsageMetrics> clientUsageMap = new ConcurrentHashMap<>();
    private final Map<String, RateLimitMetrics> rateLimitMap = new ConcurrentHashMap<>();
    private final Map<String, QuotaMetrics> quotaMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, ApiKeyMetrics> apiKeyMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, RequestMetrics> requestMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, ResponseMetrics> responseMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, AuthenticationMetrics> authMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, ThrottleMetrics> throttleMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, ApiVersionMetrics> versionMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, GeographicMetrics> geographicMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, BillingMetrics> billingMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, DeprecationMetrics> deprecationMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, ApiAnalytics> analyticsMap = new ConcurrentHashMap<>();
    private final Map<String, AbuseDetection> abuseDetectionMap = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(5);
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    
    private Counter processedEventsCounter;
    private Counter errorCounter;
    private Counter dlqCounter;
    private Counter rateLimitExceededCounter;
    private Counter quotaExceededCounter;
    private Counter throttledRequestsCounter;
    private Gauge activeApiKeysGauge;
    private Gauge totalRequestsGauge;
    private Gauge apiErrorRateGauge;
    private Timer processingTimer;
    private Timer apiLatencyTimer;
    
    @PostConstruct
    public void init() {
        initializeMetrics();
        initializeResilience();
        startScheduledTasks();
        initializeApiTracking();
        logger.info("ApiUsageTrackingConsumer initialized with rate limit threshold: {} requests", 
                    rateLimitThreshold);
    }
    
    private void initializeMetrics() {
        processedEventsCounter = Counter.builder("api.usage.processed")
            .description("Total API usage events processed")
            .register(meterRegistry);
            
        errorCounter = Counter.builder("api.usage.errors")
            .description("Total API usage processing errors")
            .register(meterRegistry);
            
        dlqCounter = Counter.builder("api.usage.dlq")
            .description("Total messages sent to DLQ")
            .register(meterRegistry);
            
        rateLimitExceededCounter = Counter.builder("api.rate_limit.exceeded")
            .description("Total rate limit exceeded events")
            .register(meterRegistry);
            
        quotaExceededCounter = Counter.builder("api.quota.exceeded")
            .description("Total quota exceeded events")
            .register(meterRegistry);
            
        throttledRequestsCounter = Counter.builder("api.requests.throttled")
            .description("Total throttled requests")
            .register(meterRegistry);
            
        activeApiKeysGauge = Gauge.builder("api.keys.active", this, 
            consumer -> countActiveApiKeys())
            .description("Number of active API keys")
            .register(meterRegistry);
            
        totalRequestsGauge = Gauge.builder("api.requests.total", this,
            consumer -> calculateTotalRequests())
            .description("Total API requests")
            .register(meterRegistry);
            
        apiErrorRateGauge = Gauge.builder("api.error.rate", this,
            consumer -> calculateOverallErrorRate())
            .description("Overall API error rate")
            .register(meterRegistry);
            
        processingTimer = Timer.builder("api.usage.processing.time")
            .description("API usage processing time")
            .register(meterRegistry);
            
        apiLatencyTimer = Timer.builder("api.latency")
            .description("API request latency")
            .publishPercentiles(0.5, 0.75, 0.95, 0.99)
            .register(meterRegistry);
    }
    
    private void initializeResilience() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .slidingWindowSize(10)
            .build();
            
        circuitBreaker = CircuitBreakerRegistry.of(circuitBreakerConfig)
            .circuitBreaker("api-usage-tracking", circuitBreakerConfig);
            
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .retryExceptions(Exception.class)
            .build();
            
        retry = RetryRegistry.of(retryConfig).retry("api-usage-tracking", retryConfig);
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> logger.warn("Circuit breaker state transition: {}", event));
    }
    
    private void startScheduledTasks() {
        scheduledExecutor.scheduleAtFixedRate(
            this::analyzeApiUsage, 
            0, usageWindowMinutes, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::checkRateLimits, 
            0, 1, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::monitorQuotas, 
            0, 5, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::calculateBilling, 
            0, billingCalculationIntervalHours, TimeUnit.HOURS
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::detectApiAbuse, 
            0, 2, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::analyzeApiTrends, 
            0, 15, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::generateApiReports, 
            0, 1, TimeUnit.HOURS
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::cleanupOldData, 
            0, 24, TimeUnit.HOURS
        );
    }
    
    private void initializeApiTracking() {
        List<String> criticalEndpoints = Arrays.asList(
            "/api/v1/payments", "/api/v1/transfers", "/api/v1/accounts",
            "/api/v1/transactions", "/api/v1/users", "/api/v1/auth"
        );
        
        criticalEndpoints.forEach(endpoint -> {
            ApiEndpointMetrics metrics = new ApiEndpointMetrics(endpoint);
            endpointMetricsMap.put(endpoint, metrics);
        });
        
        ApiAnalytics analytics = new ApiAnalytics();
        analyticsMap.put("global", analytics);
    }
    
    @KafkaListener(
        topics = "api-usage-tracking-events",
        groupId = CONSUMER_GROUP_ID,
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
        @Payload String message,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
        Acknowledgment acknowledgment
    ) {
        MDC.put("topic", topic);
        MDC.put("partition", String.valueOf(partition));
        MDC.put("offset", String.valueOf(offset));
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            processApiUsageEvent(message, timestamp);
            acknowledgment.acknowledge();
            processedEventsCounter.increment();
            
        } catch (Exception e) {
            handleProcessingError(message, e, acknowledgment);
            
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }
    
    private void processApiUsageEvent(String message, long timestamp) throws Exception {
        JsonNode event = objectMapper.readTree(message);
        String eventType = event.path("type").asText();
        String eventId = event.path("eventId").asText();
        
        logger.debug("Processing API usage event: {} - {}", eventType, eventId);
        
        Callable<Void> processTask = () -> {
            switch (eventType) {
                case "API_REQUEST":
                    handleApiRequest(event, timestamp);
                    break;
                case "API_RESPONSE":
                    handleApiResponse(event, timestamp);
                    break;
                case "RATE_LIMIT_CHECK":
                    handleRateLimitCheck(event, timestamp);
                    break;
                case "QUOTA_UPDATE":
                    handleQuotaUpdate(event, timestamp);
                    break;
                case "API_KEY_USAGE":
                    handleApiKeyUsage(event, timestamp);
                    break;
                case "AUTHENTICATION_EVENT":
                    handleAuthenticationEvent(event, timestamp);
                    break;
                case "THROTTLE_EVENT":
                    handleThrottleEvent(event, timestamp);
                    break;
                case "API_VERSION_USAGE":
                    handleApiVersionUsage(event, timestamp);
                    break;
                case "GEOGRAPHIC_USAGE":
                    handleGeographicUsage(event, timestamp);
                    break;
                case "BILLING_EVENT":
                    handleBillingEvent(event, timestamp);
                    break;
                case "DEPRECATION_WARNING":
                    handleDeprecationWarning(event, timestamp);
                    break;
                case "CLIENT_USAGE":
                    handleClientUsage(event, timestamp);
                    break;
                case "ERROR_TRACKING":
                    handleErrorTracking(event, timestamp);
                    break;
                case "WEBHOOK_USAGE":
                    handleWebhookUsage(event, timestamp);
                    break;
                case "SDK_USAGE":
                    handleSdkUsage(event, timestamp);
                    break;
                default:
                    logger.warn("Unknown API usage event type: {}", eventType);
            }
            return null;
        };
        
        Retry.decorateCallable(retry, processTask).call();
    }
    
    private void handleApiRequest(JsonNode event, long timestamp) {
        String endpoint = event.path("endpoint").asText();
        String method = event.path("method").asText();
        String apiKey = event.path("apiKey").asText();
        String clientId = event.path("clientId").asText();
        String userAgent = event.path("userAgent").asText("");
        String ipAddress = event.path("ipAddress").asText();
        int payloadSize = event.path("payloadSize").asInt();
        JsonNode headers = event.path("headers");
        JsonNode parameters = event.path("parameters");
        
        ApiEndpointMetrics endpointMetrics = endpointMetricsMap.computeIfAbsent(
            endpoint, k -> new ApiEndpointMetrics(endpoint)
        );
        
        endpointMetrics.recordRequest(method, payloadSize, timestamp);
        
        RequestMetrics requestMetrics = requestMetricsMap.computeIfAbsent(
            endpoint + "_" + method, k -> new RequestMetrics(endpoint, method)
        );
        
        requestMetrics.recordRequest(clientId, apiKey, userAgent, ipAddress, payloadSize, timestamp);
        
        ClientUsageMetrics clientMetrics = clientUsageMap.computeIfAbsent(
            clientId, k -> new ClientUsageMetrics(clientId)
        );
        
        clientMetrics.recordRequest(endpoint, method, timestamp);
        
        if (apiKey != null && !apiKey.isEmpty()) {
            ApiKeyMetrics keyMetrics = apiKeyMetricsMap.computeIfAbsent(
                apiKey, k -> new ApiKeyMetrics(apiKey)
            );
            
            keyMetrics.recordUsage(endpoint, method, timestamp);
        }
        
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(Timer.builder("api.request.processing")
            .tag("endpoint", endpoint)
            .tag("method", method)
            .register(meterRegistry));
        
        metricsService.recordMetric("api.request", 1.0,
            Map.of("endpoint", endpoint, "method", method, "client", clientId));
    }
    
    private void handleApiResponse(JsonNode event, long timestamp) {
        String endpoint = event.path("endpoint").asText();
        String method = event.path("method").asText();
        int statusCode = event.path("statusCode").asInt();
        long responseTime = event.path("responseTime").asLong();
        int responseSize = event.path("responseSize").asInt();
        String apiKey = event.path("apiKey").asText();
        String clientId = event.path("clientId").asText();
        boolean cached = event.path("cached").asBoolean(false);
        JsonNode errorDetails = event.path("errorDetails");
        
        ResponseMetrics responseMetrics = responseMetricsMap.computeIfAbsent(
            endpoint + "_" + method, k -> new ResponseMetrics(endpoint, method)
        );
        
        responseMetrics.recordResponse(statusCode, responseTime, responseSize, cached, timestamp);
        
        ApiEndpointMetrics endpointMetrics = endpointMetricsMap.get(endpoint);
        if (endpointMetrics != null) {
            endpointMetrics.recordResponse(statusCode, responseTime, responseSize, timestamp);
            
            if (statusCode >= 400) {
                endpointMetrics.incrementErrors();
                
                if (statusCode >= 500) {
                    endpointMetrics.incrementServerErrors();
                } else {
                    endpointMetrics.incrementClientErrors();
                }
            }
        }
        
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(Timer.builder("api.response.time")
            .tag("endpoint", endpoint)
            .tag("method", method)
            .tag("status", String.valueOf(statusCode))
            .register(meterRegistry));
        
        if (responseTime > latencyThresholdMs) {
            alertingService.sendAlert(
                "SLOW_API_RESPONSE",
                "Medium",
                String.format("Slow API response for %s %s: %dms",
                    method, endpoint, responseTime),
                Map.of(
                    "endpoint", endpoint,
                    "method", method,
                    "responseTime", String.valueOf(responseTime),
                    "statusCode", String.valueOf(statusCode),
                    "client", clientId
                )
            );
        }
        
        metricsService.recordMetric("api.response_time", responseTime,
            Map.of("endpoint", endpoint, "method", method, "status", String.valueOf(statusCode)));
    }
    
    private void handleRateLimitCheck(JsonNode event, long timestamp) {
        String clientId = event.path("clientId").asText();
        String apiKey = event.path("apiKey").asText();
        String endpoint = event.path("endpoint").asText();
        int requestCount = event.path("requestCount").asInt();
        int limitValue = event.path("limitValue").asInt();
        String windowType = event.path("windowType").asText();
        long windowDuration = event.path("windowDuration").asLong();
        boolean exceeded = event.path("exceeded").asBoolean();
        long resetTime = event.path("resetTime").asLong();
        
        RateLimitMetrics rateLimitMetrics = rateLimitMap.computeIfAbsent(
            clientId, k -> new RateLimitMetrics(clientId)
        );
        
        rateLimitMetrics.updateRateLimit(
            endpoint, requestCount, limitValue, windowType, windowDuration, exceeded, resetTime, timestamp
        );
        
        if (exceeded) {
            rateLimitExceededCounter.increment();
            
            alertingService.sendAlert(
                "RATE_LIMIT_EXCEEDED",
                "High",
                String.format("Rate limit exceeded for client %s on %s",
                    clientId, endpoint),
                Map.of(
                    "clientId", clientId,
                    "endpoint", endpoint,
                    "requestCount", String.valueOf(requestCount),
                    "limit", String.valueOf(limitValue),
                    "windowType", windowType,
                    "resetTime", String.valueOf(resetTime)
                )
            );
            
            if (abuseDetectionEnabled) {
                AbuseDetection abuse = abuseDetectionMap.computeIfAbsent(
                    clientId, k -> new AbuseDetection(clientId)
                );
                
                abuse.recordRateLimitViolation(endpoint, timestamp);
            }
        }
        
        double utilizationPercentage = (double) requestCount / limitValue * 100;
        if (utilizationPercentage > quotaWarningPercentage && !exceeded) {
            logger.warn("Client {} approaching rate limit: {}% utilized", 
                clientId, utilizationPercentage);
        }
        
        metricsService.recordMetric("api.rate_limit.utilization", utilizationPercentage,
            Map.of("client", clientId, "endpoint", endpoint));
    }
    
    private void handleQuotaUpdate(JsonNode event, long timestamp) {
        String clientId = event.path("clientId").asText();
        String quotaType = event.path("quotaType").asText();
        long used = event.path("used").asLong();
        long limit = event.path("limit").asLong();
        String period = event.path("period").asText();
        boolean exceeded = event.path("exceeded").asBoolean();
        long resetDate = event.path("resetDate").asLong();
        String tier = event.path("tier").asText("");
        
        QuotaMetrics quotaMetrics = quotaMetricsMap.computeIfAbsent(
            clientId, k -> new QuotaMetrics(clientId)
        );
        
        quotaMetrics.updateQuota(quotaType, used, limit, period, exceeded, resetDate, tier, timestamp);
        
        if (exceeded) {
            quotaExceededCounter.increment();
            
            alertingService.sendAlert(
                "QUOTA_EXCEEDED",
                "Critical",
                String.format("Quota exceeded for client %s: %s",
                    clientId, quotaType),
                Map.of(
                    "clientId", clientId,
                    "quotaType", quotaType,
                    "used", String.valueOf(used),
                    "limit", String.valueOf(limit),
                    "period", period,
                    "tier", tier
                )
            );
        }
        
        double usagePercentage = (double) used / limit * 100;
        if (usagePercentage > quotaWarningPercentage && !exceeded) {
            alertingService.sendAlert(
                "QUOTA_WARNING",
                "Medium",
                String.format("Client %s approaching quota limit: %.1f%% used",
                    clientId, usagePercentage),
                Map.of(
                    "clientId", clientId,
                    "quotaType", quotaType,
                    "usagePercentage", String.valueOf(usagePercentage),
                    "remaining", String.valueOf(limit - used)
                )
            );
        }
        
        metricsService.recordMetric("api.quota.usage", usagePercentage,
            Map.of("client", clientId, "type", quotaType, "tier", tier));
    }
    
    private void handleApiKeyUsage(JsonNode event, long timestamp) {
        String apiKey = event.path("apiKey").asText();
        String keyId = event.path("keyId").asText();
        String clientId = event.path("clientId").asText();
        String action = event.path("action").asText();
        String scope = event.path("scope").asText("");
        boolean valid = event.path("valid").asBoolean();
        String environment = event.path("environment").asText("production");
        JsonNode permissions = event.path("permissions");
        
        ApiKeyMetrics keyMetrics = apiKeyMetricsMap.computeIfAbsent(
            apiKey, k -> new ApiKeyMetrics(apiKey)
        );
        
        keyMetrics.updateKeyInfo(keyId, clientId, scope, environment, timestamp);
        
        switch (action) {
            case "CREATE":
                keyMetrics.markCreated(timestamp);
                break;
            case "REVOKE":
                keyMetrics.markRevoked(timestamp);
                break;
            case "ROTATE":
                keyMetrics.markRotated(timestamp);
                break;
            case "VALIDATE":
                keyMetrics.recordValidation(valid, timestamp);
                break;
        }
        
        if (!valid) {
            keyMetrics.incrementInvalidAttempts();
            
            if (keyMetrics.getInvalidAttempts() > 10) {
                alertingService.sendAlert(
                    "EXCESSIVE_INVALID_API_KEY_ATTEMPTS",
                    "High",
                    String.format("Excessive invalid API key attempts for key %s",
                        keyId),
                    Map.of(
                        "keyId", keyId,
                        "clientId", clientId,
                        "invalidAttempts", String.valueOf(keyMetrics.getInvalidAttempts())
                    )
                );
            }
        }
        
        metricsService.recordMetric("api.key.usage", 1.0,
            Map.of("action", action, "valid", String.valueOf(valid), "environment", environment));
    }
    
    private void handleAuthenticationEvent(JsonNode event, long timestamp) {
        String authType = event.path("authType").asText();
        String clientId = event.path("clientId").asText();
        String method = event.path("method").asText();
        boolean success = event.path("success").asBoolean();
        String reason = event.path("reason").asText("");
        String ipAddress = event.path("ipAddress").asText();
        String userAgent = event.path("userAgent").asText("");
        
        AuthenticationMetrics authMetrics = authMetricsMap.computeIfAbsent(
            clientId, k -> new AuthenticationMetrics(clientId)
        );
        
        authMetrics.recordAuthentication(authType, method, success, reason, ipAddress, timestamp);
        
        if (!success) {
            authMetrics.incrementFailures();
            
            if (authMetrics.getFailureRate() > 0.3) {
                alertingService.sendAlert(
                    "HIGH_AUTH_FAILURE_RATE",
                    "High",
                    String.format("High authentication failure rate for client %s: %.1f%%",
                        clientId, authMetrics.getFailureRate() * 100),
                    Map.of(
                        "clientId", clientId,
                        "authType", authType,
                        "failureRate", String.valueOf(authMetrics.getFailureRate()),
                        "recentReason", reason
                    )
                );
            }
            
            if (abuseDetectionEnabled) {
                AbuseDetection abuse = abuseDetectionMap.computeIfAbsent(
                    clientId, k -> new AbuseDetection(clientId)
                );
                
                abuse.recordAuthFailure(ipAddress, timestamp);
            }
        }
        
        metricsService.recordMetric("api.auth", success ? 1.0 : 0.0,
            Map.of("type", authType, "method", method, "client", clientId));
    }
    
    private void handleThrottleEvent(JsonNode event, long timestamp) {
        String clientId = event.path("clientId").asText();
        String endpoint = event.path("endpoint").asText();
        String throttleType = event.path("throttleType").asText();
        int requestsThrottled = event.path("requestsThrottled").asInt();
        long delayMs = event.path("delayMs").asLong();
        String reason = event.path("reason").asText();
        
        ThrottleMetrics throttleMetrics = throttleMetricsMap.computeIfAbsent(
            clientId, k -> new ThrottleMetrics(clientId)
        );
        
        throttleMetrics.recordThrottle(endpoint, throttleType, requestsThrottled, delayMs, reason, timestamp);
        
        throttledRequestsCounter.increment(requestsThrottled);
        
        if (requestsThrottled > throttleThreshold) {
            alertingService.sendAlert(
                "EXCESSIVE_THROTTLING",
                "Medium",
                String.format("Excessive throttling for client %s on %s",
                    clientId, endpoint),
                Map.of(
                    "clientId", clientId,
                    "endpoint", endpoint,
                    "throttled", String.valueOf(requestsThrottled),
                    "type", throttleType,
                    "reason", reason
                )
            );
        }
        
        metricsService.recordMetric("api.throttle", requestsThrottled,
            Map.of("client", clientId, "endpoint", endpoint, "type", throttleType));
    }
    
    private void handleApiVersionUsage(JsonNode event, long timestamp) {
        String version = event.path("version").asText();
        String endpoint = event.path("endpoint").asText();
        String clientId = event.path("clientId").asText();
        boolean deprecated = event.path("deprecated").asBoolean();
        String latestVersion = event.path("latestVersion").asText("");
        long migrationDeadline = event.path("migrationDeadline").asLong(0);
        
        ApiVersionMetrics versionMetrics = versionMetricsMap.computeIfAbsent(
            version, k -> new ApiVersionMetrics(version)
        );
        
        versionMetrics.recordUsage(endpoint, clientId, deprecated, latestVersion, timestamp);
        
        if (deprecated) {
            DeprecationMetrics deprecationMetrics = deprecationMetricsMap.computeIfAbsent(
                version + "_" + endpoint, k -> new DeprecationMetrics(version, endpoint)
            );
            
            deprecationMetrics.recordUsage(clientId, migrationDeadline, timestamp);
            
            long daysUntilDeadline = migrationDeadline > 0 ? 
                (migrationDeadline - System.currentTimeMillis()) / (1000 * 60 * 60 * 24) : 0;
            
            if (daysUntilDeadline > 0 && daysUntilDeadline < deprecatedApiThresholdDays) {
                alertingService.sendAlert(
                    "DEPRECATED_API_USAGE",
                    "Medium",
                    String.format("Client %s using deprecated API %s v%s (deadline in %d days)",
                        clientId, endpoint, version, daysUntilDeadline),
                    Map.of(
                        "clientId", clientId,
                        "version", version,
                        "endpoint", endpoint,
                        "latestVersion", latestVersion,
                        "daysRemaining", String.valueOf(daysUntilDeadline)
                    )
                );
            }
        }
        
        metricsService.recordMetric("api.version.usage", 1.0,
            Map.of("version", version, "deprecated", String.valueOf(deprecated)));
    }
    
    private void handleGeographicUsage(JsonNode event, long timestamp) {
        String country = event.path("country").asText();
        String region = event.path("region").asText("");
        String city = event.path("city").asText("");
        String clientId = event.path("clientId").asText();
        String endpoint = event.path("endpoint").asText();
        long latency = event.path("latency").asLong();
        String datacenter = event.path("datacenter").asText("");
        
        GeographicMetrics geoMetrics = geographicMetricsMap.computeIfAbsent(
            country, k -> new GeographicMetrics(country)
        );
        
        geoMetrics.recordUsage(region, city, clientId, endpoint, latency, datacenter, timestamp);
        
        if (latency > latencyThresholdMs * 2) {
            logger.warn("High latency from {}: {}ms for endpoint {}",
                country, latency, endpoint);
        }
        
        metricsService.recordMetric("api.geo.usage", 1.0,
            Map.of("country", country, "region", region, "datacenter", datacenter));
        
        metricsService.recordMetric("api.geo.latency", latency,
            Map.of("country", country, "endpoint", endpoint));
    }
    
    private void handleBillingEvent(JsonNode event, long timestamp) {
        String clientId = event.path("clientId").asText();
        String billingType = event.path("billingType").asText();
        double amount = event.path("amount").asDouble();
        String currency = event.path("currency").asText("USD");
        String period = event.path("period").asText();
        long apiCalls = event.path("apiCalls").asLong();
        long dataTransfer = event.path("dataTransfer").asLong();
        String tier = event.path("tier").asText("");
        boolean overage = event.path("overage").asBoolean();
        
        BillingMetrics billingMetrics = billingMetricsMap.computeIfAbsent(
            clientId, k -> new BillingMetrics(clientId)
        );
        
        billingMetrics.recordBilling(
            billingType, amount, currency, period, apiCalls, dataTransfer, tier, overage, timestamp
        );
        
        if (overage) {
            alertingService.sendAlert(
                "BILLING_OVERAGE",
                "High",
                String.format("Billing overage for client %s: %.2f %s",
                    clientId, amount, currency),
                Map.of(
                    "clientId", clientId,
                    "amount", String.valueOf(amount),
                    "currency", currency,
                    "apiCalls", String.valueOf(apiCalls),
                    "tier", tier
                )
            );
        }
        
        if (costTrackingEnabled) {
            billingMetrics.updateCostProjection(amount, apiCalls);
        }
        
        metricsService.recordMetric("api.billing.amount", amount,
            Map.of("client", clientId, "type", billingType, "tier", tier));
    }
    
    private void handleDeprecationWarning(JsonNode event, long timestamp) {
        String feature = event.path("feature").asText();
        String version = event.path("version").asText();
        String clientId = event.path("clientId").asText();
        String alternativeFeature = event.path("alternative").asText("");
        String deprecationDate = event.path("deprecationDate").asText();
        String removalDate = event.path("removalDate").asText();
        String migrationGuide = event.path("migrationGuide").asText("");
        
        DeprecationMetrics deprecationMetrics = deprecationMetricsMap.computeIfAbsent(
            feature, k -> new DeprecationMetrics(version, feature)
        );
        
        deprecationMetrics.addWarning(
            clientId, alternativeFeature, deprecationDate, removalDate, migrationGuide, timestamp
        );
        
        alertingService.sendAlert(
            "DEPRECATION_WARNING",
            "Low",
            String.format("Deprecation warning for %s v%s used by %s",
                feature, version, clientId),
            Map.of(
                "feature", feature,
                "version", version,
                "clientId", clientId,
                "alternative", alternativeFeature,
                "removalDate", removalDate
            )
        );
        
        metricsService.recordMetric("api.deprecation.warning", 1.0,
            Map.of("feature", feature, "version", version));
    }
    
    private void handleClientUsage(JsonNode event, long timestamp) {
        String clientId = event.path("clientId").asText();
        String clientName = event.path("clientName").asText("");
        String clientType = event.path("clientType").asText();
        String sdkVersion = event.path("sdkVersion").asText("");
        String platform = event.path("platform").asText("");
        JsonNode endpoints = event.path("endpoints");
        JsonNode features = event.path("features");
        
        ClientUsageMetrics clientMetrics = clientUsageMap.computeIfAbsent(
            clientId, k -> new ClientUsageMetrics(clientId)
        );
        
        clientMetrics.updateClientInfo(clientName, clientType, sdkVersion, platform, timestamp);
        
        if (endpoints != null && endpoints.isArray()) {
            endpoints.forEach(endpoint -> 
                clientMetrics.addEndpointUsage(endpoint.asText())
            );
        }
        
        if (features != null && features.isArray()) {
            features.forEach(feature -> 
                clientMetrics.addFeatureUsage(feature.asText())
            );
        }
        
        metricsService.recordMetric("api.client.active", 1.0,
            Map.of("client", clientId, "type", clientType, "platform", platform));
    }
    
    private void handleErrorTracking(JsonNode event, long timestamp) {
        String endpoint = event.path("endpoint").asText();
        String errorCode = event.path("errorCode").asText();
        String errorMessage = event.path("errorMessage").asText();
        String clientId = event.path("clientId").asText();
        String category = event.path("category").asText();
        int occurrences = event.path("occurrences").asInt(1);
        JsonNode stackTrace = event.path("stackTrace");
        
        ResponseMetrics responseMetrics = responseMetricsMap.get(endpoint + "_*");
        if (responseMetrics != null) {
            responseMetrics.recordError(errorCode, errorMessage, category, occurrences, timestamp);
        }
        
        ApiEndpointMetrics endpointMetrics = endpointMetricsMap.get(endpoint);
        if (endpointMetrics != null) {
            endpointMetrics.addErrorDetails(errorCode, errorMessage, occurrences);
            
            if (endpointMetrics.getErrorRate() > errorRateThreshold) {
                alertingService.sendAlert(
                    "HIGH_API_ERROR_RATE",
                    "High",
                    String.format("High error rate for endpoint %s: %.2f%%",
                        endpoint, endpointMetrics.getErrorRate() * 100),
                    Map.of(
                        "endpoint", endpoint,
                        "errorRate", String.valueOf(endpointMetrics.getErrorRate()),
                        "recentError", errorCode,
                        "message", errorMessage
                    )
                );
            }
        }
        
        metricsService.recordMetric("api.errors", occurrences,
            Map.of("endpoint", endpoint, "code", errorCode, "category", category));
    }
    
    private void handleWebhookUsage(JsonNode event, long timestamp) {
        String webhookUrl = event.path("webhookUrl").asText();
        String eventType = event.path("eventType").asText();
        String clientId = event.path("clientId").asText();
        boolean delivered = event.path("delivered").asBoolean();
        int attempts = event.path("attempts").asInt();
        long responseTime = event.path("responseTime").asLong();
        int statusCode = event.path("statusCode").asInt();
        
        ClientUsageMetrics clientMetrics = clientUsageMap.get(clientId);
        if (clientMetrics != null) {
            clientMetrics.recordWebhook(webhookUrl, eventType, delivered, attempts, timestamp);
        }
        
        if (!delivered && attempts >= 3) {
            alertingService.sendAlert(
                "WEBHOOK_DELIVERY_FAILURE",
                "Medium",
                String.format("Webhook delivery failed for %s after %d attempts",
                    clientId, attempts),
                Map.of(
                    "clientId", clientId,
                    "webhookUrl", webhookUrl,
                    "eventType", eventType,
                    "attempts", String.valueOf(attempts),
                    "statusCode", String.valueOf(statusCode)
                )
            );
        }
        
        metricsService.recordMetric("api.webhook.delivery", delivered ? 1.0 : 0.0,
            Map.of("event", eventType, "client", clientId));
    }
    
    private void handleSdkUsage(JsonNode event, long timestamp) {
        String sdkType = event.path("sdkType").asText();
        String sdkVersion = event.path("sdkVersion").asText();
        String language = event.path("language").asText();
        String clientId = event.path("clientId").asText();
        String feature = event.path("feature").asText("");
        boolean outdated = event.path("outdated").asBoolean();
        String latestVersion = event.path("latestVersion").asText("");
        
        ClientUsageMetrics clientMetrics = clientUsageMap.get(clientId);
        if (clientMetrics != null) {
            clientMetrics.updateSdkInfo(sdkType, sdkVersion, language, timestamp);
        }
        
        if (outdated) {
            logger.warn("Client {} using outdated SDK {} v{} (latest: {})",
                clientId, sdkType, sdkVersion, latestVersion);
        }
        
        metricsService.recordMetric("api.sdk.usage", 1.0,
            Map.of("sdk", sdkType, "version", sdkVersion, "language", language));
    }
    
    private void analyzeApiUsage() {
        try {
            Map<String, UsageAnalysis> analyses = new HashMap<>();
            
            endpointMetricsMap.forEach((endpoint, metrics) -> {
                UsageAnalysis analysis = analyzeEndpointUsage(metrics);
                analyses.put(endpoint, analysis);
                
                if (analysis.hasIssues()) {
                    reportUsageIssues(endpoint, analysis);
                }
            });
            
            ApiAnalytics analytics = analyticsMap.get("global");
            if (analytics != null) {
                analytics.updateAnalytics(analyses, System.currentTimeMillis());
            }
            
        } catch (Exception e) {
            logger.error("Error analyzing API usage", e);
        }
    }
    
    private void checkRateLimits() {
        try {
            rateLimitMap.forEach((clientId, metrics) -> {
                if (metrics.isApproachingLimit()) {
                    logger.warn("Client {} approaching rate limit", clientId);
                }
                
                metrics.resetExpiredWindows();
            });
        } catch (Exception e) {
            logger.error("Error checking rate limits", e);
        }
    }
    
    private void monitorQuotas() {
        try {
            quotaMetricsMap.forEach((clientId, metrics) -> {
                metrics.checkQuotaStatus();
                
                if (metrics.needsUpgrade()) {
                    suggestTierUpgrade(clientId, metrics);
                }
            });
        } catch (Exception e) {
            logger.error("Error monitoring quotas", e);
        }
    }
    
    private void calculateBilling() {
        if (!costTrackingEnabled) {
            return;
        }
        
        try {
            billingMetricsMap.forEach((clientId, metrics) -> {
                double projectedCost = metrics.calculateProjectedCost();
                
                if (projectedCost > metrics.getBudget()) {
                    alertingService.sendAlert(
                        "BUDGET_EXCEEDED",
                        "High",
                        String.format("Projected cost for %s exceeds budget: %.2f",
                            clientId, projectedCost),
                        Map.of(
                            "clientId", clientId,
                            "projectedCost", String.valueOf(projectedCost),
                            "budget", String.valueOf(metrics.getBudget())
                        )
                    );
                }
            });
        } catch (Exception e) {
            logger.error("Error calculating billing", e);
        }
    }
    
    private void detectApiAbuse() {
        if (!abuseDetectionEnabled) {
            return;
        }
        
        try {
            abuseDetectionMap.forEach((clientId, abuse) -> {
                if (abuse.isAbusive()) {
                    alertingService.sendAlert(
                        "API_ABUSE_DETECTED",
                        "Critical",
                        String.format("API abuse detected for client %s", clientId),
                        Map.of(
                            "clientId", clientId,
                            "abuseScore", String.valueOf(abuse.getAbuseScore()),
                            "violations", abuse.getViolationSummary()
                        )
                    );
                    
                    abuse.markReported();
                }
            });
        } catch (Exception e) {
            logger.error("Error detecting API abuse", e);
        }
    }
    
    private void analyzeApiTrends() {
        try {
            Map<String, TrendAnalysis> trends = new HashMap<>();
            
            endpointMetricsMap.forEach((endpoint, metrics) -> {
                TrendAnalysis trend = metrics.analyzeTrend();
                trends.put(endpoint, trend);
            });
            
            trends.entrySet().stream()
                .filter(e -> e.getValue().isSignificant())
                .forEach(e -> {
                    logger.info("Significant trend for {}: {}", 
                        e.getKey(), e.getValue().getDescription());
                });
            
        } catch (Exception e) {
            logger.error("Error analyzing API trends", e);
        }
    }
    
    private void generateApiReports() {
        try {
            Map<String, Object> report = new HashMap<>();
            
            report.put("totalRequests", calculateTotalRequests());
            report.put("activeApiKeys", countActiveApiKeys());
            report.put("errorRate", calculateOverallErrorRate());
            report.put("averageLatency", calculateAverageLatency());
            report.put("topEndpoints", getTopEndpoints());
            report.put("topClients", getTopClients());
            report.put("deprecatedUsage", getDeprecatedUsage());
            report.put("timestamp", System.currentTimeMillis());
            
            logger.info("API usage report generated: {}", report);
            
            metricsService.recordMetric("api.report.generated", 1.0, Map.of());
            
        } catch (Exception e) {
            logger.error("Error generating API reports", e);
        }
    }
    
    private void cleanupOldData() {
        try {
            long cutoffTime = System.currentTimeMillis() - 
                TimeUnit.DAYS.toMillis(analyticsRetentionDays);
            
            endpointMetricsMap.values().forEach(metrics -> 
                metrics.cleanupOldData(cutoffTime)
            );
            
            clientUsageMap.entrySet().removeIf(entry ->
                entry.getValue().getLastSeen() < cutoffTime
            );
            
            logger.info("Cleaned up old API usage data");
            
        } catch (Exception e) {
            logger.error("Error cleaning up old data", e);
        }
    }
    
    private UsageAnalysis analyzeEndpointUsage(ApiEndpointMetrics metrics) {
        return new UsageAnalysis(
            metrics.getTotalRequests(),
            metrics.getErrorRate(),
            metrics.getAverageLatency(),
            metrics.hasIssues()
        );
    }
    
    private void reportUsageIssues(String endpoint, UsageAnalysis analysis) {
        if (analysis.hasHighErrorRate()) {
            logger.warn("High error rate for endpoint {}: {}%",
                endpoint, analysis.getErrorRate() * 100);
        }
        
        if (analysis.hasHighLatency()) {
            logger.warn("High latency for endpoint {}: {}ms",
                endpoint, analysis.getAverageLatency());
        }
    }
    
    private void suggestTierUpgrade(String clientId, QuotaMetrics metrics) {
        alertingService.sendAlert(
            "TIER_UPGRADE_SUGGESTED",
            "Low",
            String.format("Consider tier upgrade for client %s", clientId),
            Map.of(
                "clientId", clientId,
                "currentTier", metrics.getCurrentTier(),
                "usage", String.valueOf(metrics.getUsagePercentage())
            )
        );
    }
    
    private long countActiveApiKeys() {
        return apiKeyMetricsMap.values().stream()
            .filter(ApiKeyMetrics::isActive)
            .count();
    }
    
    private long calculateTotalRequests() {
        return endpointMetricsMap.values().stream()
            .mapToLong(ApiEndpointMetrics::getTotalRequests)
            .sum();
    }
    
    private double calculateOverallErrorRate() {
        long totalRequests = calculateTotalRequests();
        if (totalRequests == 0) return 0.0;
        
        long totalErrors = endpointMetricsMap.values().stream()
            .mapToLong(ApiEndpointMetrics::getTotalErrors)
            .sum();
        
        return (double) totalErrors / totalRequests;
    }
    
    private double calculateAverageLatency() {
        return endpointMetricsMap.values().stream()
            .mapToDouble(ApiEndpointMetrics::getAverageLatency)
            .average().orElse(0.0);
    }
    
    private List<String> getTopEndpoints() {
        return endpointMetricsMap.entrySet().stream()
            .sorted((e1, e2) -> Long.compare(
                e2.getValue().getTotalRequests(),
                e1.getValue().getTotalRequests()
            ))
            .limit(10)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    private List<String> getTopClients() {
        return clientUsageMap.entrySet().stream()
            .sorted((e1, e2) -> Long.compare(
                e2.getValue().getTotalRequests(),
                e1.getValue().getTotalRequests()
            ))
            .limit(10)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    private Map<String, Long> getDeprecatedUsage() {
        Map<String, Long> deprecated = new HashMap<>();
        
        deprecationMetricsMap.forEach((key, metrics) -> {
            deprecated.put(key, metrics.getUsageCount());
        });
        
        return deprecated;
    }
    
    private void handleProcessingError(String message, Exception e, Acknowledgment acknowledgment) {
        errorCounter.increment();
        logger.error("Error processing API usage event", e);
        
        try {
            if (retryHandler.shouldRetry(message, e)) {
                retryHandler.scheduleRetry(message, acknowledgment);
            } else {
                sendToDlq(message, e);
                acknowledgment.acknowledge();
                dlqCounter.increment();
            }
        } catch (Exception retryError) {
            logger.error("Error handling processing failure", retryError);
            acknowledgment.acknowledge();
        }
    }
    
    private void sendToDlq(String message, Exception error) {
        Map<String, Object> dlqMessage = new HashMap<>();
        dlqMessage.put("originalMessage", message);
        dlqMessage.put("error", error.getMessage());
        dlqMessage.put("timestamp", System.currentTimeMillis());
        dlqMessage.put("consumer", "ApiUsageTrackingConsumer");
        
        try {
            String dlqPayload = objectMapper.writeValueAsString(dlqMessage);
            logger.info("Sending message to DLQ: {}", dlqPayload);
        } catch (Exception e) {
            logger.error("Failed to send message to DLQ", e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        try {
            logger.info("Shutting down ApiUsageTrackingConsumer");
            
            scheduledExecutor.shutdown();
            executorService.shutdown();
            
            if (!scheduledExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            
            logger.info("ApiUsageTrackingConsumer shutdown complete");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Shutdown interrupted", e);
        }
    }
    
    private static class ApiEndpointMetrics {
        private final String endpoint;
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong totalErrors = new AtomicLong(0);
        private final AtomicLong clientErrors = new AtomicLong(0);
        private final AtomicLong serverErrors = new AtomicLong(0);
        private final List<Long> latencies = new CopyOnWriteArrayList<>();
        private final Map<String, Integer> errorDetails = new ConcurrentHashMap<>();
        private final List<RequestData> recentRequests = new CopyOnWriteArrayList<>();
        private volatile long lastRequestTime = 0;
        
        public ApiEndpointMetrics(String endpoint) {
            this.endpoint = endpoint;
        }
        
        public void recordRequest(String method, int payloadSize, long timestamp) {
            totalRequests.incrementAndGet();
            lastRequestTime = timestamp;
            
            recentRequests.add(new RequestData(method, payloadSize, timestamp));
            if (recentRequests.size() > 1000) {
                recentRequests.remove(0);
            }
        }
        
        public void recordResponse(int statusCode, long responseTime, int responseSize, long timestamp) {
            latencies.add(responseTime);
            if (latencies.size() > 10000) {
                latencies.remove(0);
            }
        }
        
        public void incrementErrors() {
            totalErrors.incrementAndGet();
        }
        
        public void incrementClientErrors() {
            clientErrors.incrementAndGet();
        }
        
        public void incrementServerErrors() {
            serverErrors.incrementAndGet();
        }
        
        public void addErrorDetails(String code, String message, int count) {
            errorDetails.merge(code, count, Integer::sum);
        }
        
        public void cleanupOldData(long cutoffTime) {
            recentRequests.removeIf(req -> req.timestamp < cutoffTime);
        }
        
        public double getErrorRate() {
            long total = totalRequests.get();
            if (total == 0) return 0.0;
            return (double) totalErrors.get() / total;
        }
        
        public double getAverageLatency() {
            if (latencies.isEmpty()) return 0.0;
            return latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        }
        
        public boolean hasIssues() {
            return getErrorRate() > 0.05 || getAverageLatency() > 500;
        }
        
        public TrendAnalysis analyzeTrend() {
            if (recentRequests.size() < 100) {
                return new TrendAnalysis(false, "INSUFFICIENT_DATA", 0.0);
            }
            
            long recentCount = recentRequests.stream()
                .filter(r -> r.timestamp > System.currentTimeMillis() - 300000)
                .count();
            
            double recentRate = recentCount / 5.0;
            double historicalRate = totalRequests.get() / 60.0;
            
            double change = (recentRate - historicalRate) / historicalRate;
            
            if (Math.abs(change) > 0.5) {
                return new TrendAnalysis(
                    true,
                    change > 0 ? "INCREASING" : "DECREASING",
                    change
                );
            }
            
            return new TrendAnalysis(false, "STABLE", change);
        }
        
        public long getTotalRequests() { return totalRequests.get(); }
        public long getTotalErrors() { return totalErrors.get(); }
        
        private static class RequestData {
            final String method;
            final int payloadSize;
            final long timestamp;
            
            RequestData(String method, int payloadSize, long timestamp) {
                this.method = method;
                this.payloadSize = payloadSize;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class ClientUsageMetrics {
        private final String clientId;
        private volatile String clientName = "";
        private volatile String clientType = "";
        private volatile String sdkVersion = "";
        private volatile String platform = "";
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final Map<String, Long> endpointUsage = new ConcurrentHashMap<>();
        private final Set<String> featuresUsed = ConcurrentHashMap.newKeySet();
        private final List<WebhookData> webhooks = new CopyOnWriteArrayList<>();
        private volatile long lastSeen = 0;
        
        public ClientUsageMetrics(String clientId) {
            this.clientId = clientId;
        }
        
        public void recordRequest(String endpoint, String method, long timestamp) {
            totalRequests.incrementAndGet();
            endpointUsage.merge(endpoint, 1L, Long::sum);
            lastSeen = timestamp;
        }
        
        public void updateClientInfo(String name, String type, String sdk, String platform, long timestamp) {
            this.clientName = name;
            this.clientType = type;
            this.sdkVersion = sdk;
            this.platform = platform;
            this.lastSeen = timestamp;
        }
        
        public void addEndpointUsage(String endpoint) {
            endpointUsage.merge(endpoint, 1L, Long::sum);
        }
        
        public void addFeatureUsage(String feature) {
            featuresUsed.add(feature);
        }
        
        public void recordWebhook(String url, String eventType, boolean delivered,
                                 int attempts, long timestamp) {
            webhooks.add(new WebhookData(url, eventType, delivered, attempts, timestamp));
            if (webhooks.size() > 1000) {
                webhooks.remove(0);
            }
        }
        
        public void updateSdkInfo(String type, String version, String language, long timestamp) {
            this.sdkVersion = version;
            this.lastSeen = timestamp;
        }
        
        public long getTotalRequests() { return totalRequests.get(); }
        public long getLastSeen() { return lastSeen; }
        
        private static class WebhookData {
            final String url;
            final String eventType;
            final boolean delivered;
            final int attempts;
            final long timestamp;
            
            WebhookData(String url, String eventType, boolean delivered,
                       int attempts, long timestamp) {
                this.url = url;
                this.eventType = eventType;
                this.delivered = delivered;
                this.attempts = attempts;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class RateLimitMetrics {
        private final String clientId;
        private final Map<String, WindowData> windows = new ConcurrentHashMap<>();
        private volatile long lastReset = System.currentTimeMillis();
        
        public RateLimitMetrics(String clientId) {
            this.clientId = clientId;
        }
        
        public void updateRateLimit(String endpoint, int count, int limit,
                                   String windowType, long duration,
                                   boolean exceeded, long resetTime, long timestamp) {
            String key = endpoint + "_" + windowType;
            WindowData window = new WindowData(count, limit, duration, exceeded, resetTime, timestamp);
            windows.put(key, window);
        }
        
        public boolean isApproachingLimit() {
            return windows.values().stream()
                .anyMatch(w -> (double) w.count / w.limit > 0.8);
        }
        
        public void resetExpiredWindows() {
            long now = System.currentTimeMillis();
            windows.entrySet().removeIf(e -> e.getValue().resetTime < now);
        }
        
        private static class WindowData {
            final int count;
            final int limit;
            final long duration;
            final boolean exceeded;
            final long resetTime;
            final long timestamp;
            
            WindowData(int count, int limit, long duration, boolean exceeded,
                      long resetTime, long timestamp) {
                this.count = count;
                this.limit = limit;
                this.duration = duration;
                this.exceeded = exceeded;
                this.resetTime = resetTime;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class QuotaMetrics {
        private final String clientId;
        private final Map<String, QuotaData> quotas = new ConcurrentHashMap<>();
        private volatile String currentTier = "free";
        private volatile double budget = 0.0;
        
        public QuotaMetrics(String clientId) {
            this.clientId = clientId;
        }
        
        public void updateQuota(String type, long used, long limit, String period,
                              boolean exceeded, long resetDate, String tier, long timestamp) {
            QuotaData quota = new QuotaData(used, limit, period, exceeded, resetDate, tier, timestamp);
            quotas.put(type, quota);
            this.currentTier = tier;
        }
        
        public void checkQuotaStatus() {
            quotas.forEach((type, quota) -> {
                double usage = (double) quota.used / quota.limit;
                if (usage > 0.9 && !quota.exceeded) {
                    logger.warn("Client {} approaching quota limit for {}: {}%",
                        clientId, type, usage * 100);
                }
            });
        }
        
        public boolean needsUpgrade() {
            return quotas.values().stream()
                .anyMatch(q -> (double) q.used / q.limit > 0.95);
        }
        
        public String getCurrentTier() { return currentTier; }
        
        public double getUsagePercentage() {
            if (quotas.isEmpty()) return 0.0;
            
            return quotas.values().stream()
                .mapToDouble(q -> (double) q.used / q.limit)
                .average().orElse(0.0);
        }
        
        private static class QuotaData {
            final long used;
            final long limit;
            final String period;
            final boolean exceeded;
            final long resetDate;
            final String tier;
            final long timestamp;
            
            QuotaData(long used, long limit, String period, boolean exceeded,
                     long resetDate, String tier, long timestamp) {
                this.used = used;
                this.limit = limit;
                this.period = period;
                this.exceeded = exceeded;
                this.resetDate = resetDate;
                this.tier = tier;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class ApiKeyMetrics {
        private final String apiKey;
        private volatile String keyId = "";
        private volatile String clientId = "";
        private volatile String scope = "";
        private volatile String environment = "production";
        private final AtomicLong usageCount = new AtomicLong(0);
        private final AtomicInteger invalidAttempts = new AtomicInteger(0);
        private volatile boolean active = true;
        private volatile long createdAt = 0;
        private volatile long revokedAt = 0;
        private volatile long lastUsed = 0;
        private final Map<String, Long> endpointUsage = new ConcurrentHashMap<>();
        
        public ApiKeyMetrics(String apiKey) {
            this.apiKey = apiKey;
        }
        
        public void recordUsage(String endpoint, String method, long timestamp) {
            usageCount.incrementAndGet();
            endpointUsage.merge(endpoint, 1L, Long::sum);
            lastUsed = timestamp;
        }
        
        public void updateKeyInfo(String keyId, String clientId, String scope,
                                 String environment, long timestamp) {
            this.keyId = keyId;
            this.clientId = clientId;
            this.scope = scope;
            this.environment = environment;
            this.lastUsed = timestamp;
        }
        
        public void markCreated(long timestamp) {
            this.createdAt = timestamp;
            this.active = true;
        }
        
        public void markRevoked(long timestamp) {
            this.revokedAt = timestamp;
            this.active = false;
        }
        
        public void markRotated(long timestamp) {
            this.lastUsed = timestamp;
        }
        
        public void recordValidation(boolean valid, long timestamp) {
            if (!valid) {
                invalidAttempts.incrementAndGet();
            }
            lastUsed = timestamp;
        }
        
        public void incrementInvalidAttempts() {
            invalidAttempts.incrementAndGet();
        }
        
        public boolean isActive() { return active; }
        public int getInvalidAttempts() { return invalidAttempts.get(); }
    }
    
    private static class RequestMetrics {
        private final String endpoint;
        private final String method;
        private final List<RequestDetail> requests = new CopyOnWriteArrayList<>();
        
        public RequestMetrics(String endpoint, String method) {
            this.endpoint = endpoint;
            this.method = method;
        }
        
        public void recordRequest(String clientId, String apiKey, String userAgent,
                                 String ipAddress, int payloadSize, long timestamp) {
            requests.add(new RequestDetail(
                clientId, apiKey, userAgent, ipAddress, payloadSize, timestamp
            ));
            
            if (requests.size() > 10000) {
                requests.subList(0, 1000).clear();
            }
        }
        
        private static class RequestDetail {
            final String clientId;
            final String apiKey;
            final String userAgent;
            final String ipAddress;
            final int payloadSize;
            final long timestamp;
            
            RequestDetail(String clientId, String apiKey, String userAgent,
                         String ipAddress, int payloadSize, long timestamp) {
                this.clientId = clientId;
                this.apiKey = apiKey;
                this.userAgent = userAgent;
                this.ipAddress = ipAddress;
                this.payloadSize = payloadSize;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class ResponseMetrics {
        private final String endpoint;
        private final String method;
        private final List<ResponseDetail> responses = new CopyOnWriteArrayList<>();
        private final Map<String, Integer> errorCounts = new ConcurrentHashMap<>();
        
        public ResponseMetrics(String endpoint, String method) {
            this.endpoint = endpoint;
            this.method = method;
        }
        
        public void recordResponse(int statusCode, long responseTime, int responseSize,
                                  boolean cached, long timestamp) {
            responses.add(new ResponseDetail(statusCode, responseTime, responseSize, cached, timestamp));
            
            if (responses.size() > 10000) {
                responses.subList(0, 1000).clear();
            }
        }
        
        public void recordError(String code, String message, String category,
                               int occurrences, long timestamp) {
            errorCounts.merge(code, occurrences, Integer::sum);
        }
        
        private static class ResponseDetail {
            final int statusCode;
            final long responseTime;
            final int responseSize;
            final boolean cached;
            final long timestamp;
            
            ResponseDetail(int statusCode, long responseTime, int responseSize,
                          boolean cached, long timestamp) {
                this.statusCode = statusCode;
                this.responseTime = responseTime;
                this.responseSize = responseSize;
                this.cached = cached;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class AuthenticationMetrics {
        private final String clientId;
        private final AtomicLong totalAttempts = new AtomicLong(0);
        private final AtomicLong failures = new AtomicLong(0);
        private final Map<String, Integer> failureReasons = new ConcurrentHashMap<>();
        private final List<AuthRecord> authHistory = new CopyOnWriteArrayList<>();
        
        public AuthenticationMetrics(String clientId) {
            this.clientId = clientId;
        }
        
        public void recordAuthentication(String type, String method, boolean success,
                                        String reason, String ipAddress, long timestamp) {
            totalAttempts.incrementAndGet();
            if (!success) {
                failures.incrementAndGet();
                failureReasons.merge(reason, 1, Integer::sum);
            }
            
            authHistory.add(new AuthRecord(type, method, success, reason, ipAddress, timestamp));
            if (authHistory.size() > 1000) {
                authHistory.remove(0);
            }
        }
        
        public void incrementFailures() {
            failures.incrementAndGet();
        }
        
        public double getFailureRate() {
            long total = totalAttempts.get();
            if (total == 0) return 0.0;
            return (double) failures.get() / total;
        }
        
        private static class AuthRecord {
            final String type;
            final String method;
            final boolean success;
            final String reason;
            final String ipAddress;
            final long timestamp;
            
            AuthRecord(String type, String method, boolean success,
                      String reason, String ipAddress, long timestamp) {
                this.type = type;
                this.method = method;
                this.success = success;
                this.reason = reason;
                this.ipAddress = ipAddress;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class ThrottleMetrics {
        private final String clientId;
        private final AtomicLong totalThrottled = new AtomicLong(0);
        private final Map<String, ThrottleData> throttleHistory = new ConcurrentHashMap<>();
        
        public ThrottleMetrics(String clientId) {
            this.clientId = clientId;
        }
        
        public void recordThrottle(String endpoint, String type, int count,
                                  long delayMs, String reason, long timestamp) {
            totalThrottled.addAndGet(count);
            
            String key = endpoint + "_" + timestamp;
            throttleHistory.put(key, new ThrottleData(type, count, delayMs, reason, timestamp));
            
            if (throttleHistory.size() > 100) {
                throttleHistory.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue((a, b) -> Long.compare(a.timestamp, b.timestamp)))
                    .limit(10)
                    .forEach(e -> throttleHistory.remove(e.getKey()));
            }
        }
        
        private static class ThrottleData {
            final String type;
            final int count;
            final long delayMs;
            final String reason;
            final long timestamp;
            
            ThrottleData(String type, int count, long delayMs, String reason, long timestamp) {
                this.type = type;
                this.count = count;
                this.delayMs = delayMs;
                this.reason = reason;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class ApiVersionMetrics {
        private final String version;
        private final AtomicLong usageCount = new AtomicLong(0);
        private final Set<String> clients = ConcurrentHashMap.newKeySet();
        private final Map<String, Long> endpointUsage = new ConcurrentHashMap<>();
        private volatile boolean deprecated = false;
        private volatile String latestVersion = "";
        
        public ApiVersionMetrics(String version) {
            this.version = version;
        }
        
        public void recordUsage(String endpoint, String clientId, boolean deprecated,
                               String latest, long timestamp) {
            usageCount.incrementAndGet();
            clients.add(clientId);
            endpointUsage.merge(endpoint, 1L, Long::sum);
            this.deprecated = deprecated;
            this.latestVersion = latest;
        }
    }
    
    private static class GeographicMetrics {
        private final String country;
        private final Map<String, RegionData> regions = new ConcurrentHashMap<>();
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final List<Long> latencies = new CopyOnWriteArrayList<>();
        
        public GeographicMetrics(String country) {
            this.country = country;
        }
        
        public void recordUsage(String region, String city, String clientId,
                               String endpoint, long latency, String datacenter, long timestamp) {
            totalRequests.incrementAndGet();
            latencies.add(latency);
            
            if (latencies.size() > 1000) {
                latencies.remove(0);
            }
            
            RegionData regionData = regions.computeIfAbsent(region, k -> new RegionData(region));
            regionData.recordUsage(city, clientId, endpoint, datacenter);
        }
        
        private static class RegionData {
            final String region;
            final AtomicLong requests = new AtomicLong(0);
            final Set<String> cities = ConcurrentHashMap.newKeySet();
            final Set<String> clients = ConcurrentHashMap.newKeySet();
            
            RegionData(String region) {
                this.region = region;
            }
            
            void recordUsage(String city, String clientId, String endpoint, String datacenter) {
                requests.incrementAndGet();
                cities.add(city);
                clients.add(clientId);
            }
        }
    }
    
    private static class BillingMetrics {
        private final String clientId;
        private volatile double totalCost = 0.0;
        private volatile double budget = 1000.0;
        private volatile String tier = "free";
        private final Map<String, BillingData> billingHistory = new ConcurrentHashMap<>();
        private volatile double projectedCost = 0.0;
        
        public BillingMetrics(String clientId) {
            this.clientId = clientId;
        }
        
        public void recordBilling(String type, double amount, String currency,
                                 String period, long apiCalls, long dataTransfer,
                                 String tier, boolean overage, long timestamp) {
            totalCost += amount;
            this.tier = tier;
            
            String key = period + "_" + timestamp;
            billingHistory.put(key, new BillingData(
                type, amount, currency, period, apiCalls, dataTransfer, tier, overage, timestamp
            ));
        }
        
        public void updateCostProjection(double amount, long apiCalls) {
            double costPerCall = apiCalls > 0 ? amount / apiCalls : 0;
            projectedCost = costPerCall * apiCalls * 30;
        }
        
        public double calculateProjectedCost() {
            return projectedCost;
        }
        
        public double getBudget() { return budget; }
        
        private static class BillingData {
            final String type;
            final double amount;
            final String currency;
            final String period;
            final long apiCalls;
            final long dataTransfer;
            final String tier;
            final boolean overage;
            final long timestamp;
            
            BillingData(String type, double amount, String currency, String period,
                       long apiCalls, long dataTransfer, String tier,
                       boolean overage, long timestamp) {
                this.type = type;
                this.amount = amount;
                this.currency = currency;
                this.period = period;
                this.apiCalls = apiCalls;
                this.dataTransfer = dataTransfer;
                this.tier = tier;
                this.overage = overage;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class DeprecationMetrics {
        private final String version;
        private final String feature;
        private final AtomicLong usageCount = new AtomicLong(0);
        private final Set<String> affectedClients = ConcurrentHashMap.newKeySet();
        private final List<WarningData> warnings = new CopyOnWriteArrayList<>();
        
        public DeprecationMetrics(String version, String feature) {
            this.version = version;
            this.feature = feature;
        }
        
        public void recordUsage(String clientId, long migrationDeadline, long timestamp) {
            usageCount.incrementAndGet();
            affectedClients.add(clientId);
        }
        
        public void addWarning(String clientId, String alternative, String deprecationDate,
                              String removalDate, String migrationGuide, long timestamp) {
            warnings.add(new WarningData(
                clientId, alternative, deprecationDate, removalDate, migrationGuide, timestamp
            ));
            affectedClients.add(clientId);
        }
        
        public long getUsageCount() { return usageCount.get(); }
        
        private static class WarningData {
            final String clientId;
            final String alternative;
            final String deprecationDate;
            final String removalDate;
            final String migrationGuide;
            final long timestamp;
            
            WarningData(String clientId, String alternative, String deprecationDate,
                       String removalDate, String migrationGuide, long timestamp) {
                this.clientId = clientId;
                this.alternative = alternative;
                this.deprecationDate = deprecationDate;
                this.removalDate = removalDate;
                this.migrationGuide = migrationGuide;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class ApiAnalytics {
        private final Map<String, EndpointAnalytics> endpointAnalytics = new ConcurrentHashMap<>();
        private volatile long lastAnalysisTime = 0;
        
        public void updateAnalytics(Map<String, UsageAnalysis> analyses, long timestamp) {
            analyses.forEach((endpoint, analysis) -> {
                EndpointAnalytics analytics = endpointAnalytics.computeIfAbsent(
                    endpoint, k -> new EndpointAnalytics(endpoint)
                );
                analytics.updateAnalysis(analysis);
            });
            lastAnalysisTime = timestamp;
        }
        
        private static class EndpointAnalytics {
            final String endpoint;
            final List<UsageAnalysis> history = new CopyOnWriteArrayList<>();
            
            EndpointAnalytics(String endpoint) {
                this.endpoint = endpoint;
            }
            
            void updateAnalysis(UsageAnalysis analysis) {
                history.add(analysis);
                if (history.size() > 100) {
                    history.remove(0);
                }
            }
        }
    }
    
    private static class AbuseDetection {
        private final String clientId;
        private final AtomicInteger rateLimitViolations = new AtomicInteger(0);
        private final AtomicInteger authFailures = new AtomicInteger(0);
        private final Set<String> suspiciousIps = ConcurrentHashMap.newKeySet();
        private volatile boolean reported = false;
        
        public AbuseDetection(String clientId) {
            this.clientId = clientId;
        }
        
        public void recordRateLimitViolation(String endpoint, long timestamp) {
            rateLimitViolations.incrementAndGet();
        }
        
        public void recordAuthFailure(String ipAddress, long timestamp) {
            authFailures.incrementAndGet();
            if (authFailures.get() > 5) {
                suspiciousIps.add(ipAddress);
            }
        }
        
        public boolean isAbusive() {
            return getAbuseScore() > 10;
        }
        
        public int getAbuseScore() {
            return rateLimitViolations.get() * 2 + authFailures.get() + suspiciousIps.size() * 3;
        }
        
        public void markReported() {
            reported = true;
        }
        
        public String getViolationSummary() {
            return String.format("RateLimits: %d, AuthFailures: %d, SuspiciousIPs: %d",
                rateLimitViolations.get(), authFailures.get(), suspiciousIps.size());
        }
    }
    
    private static class UsageAnalysis {
        private final long totalRequests;
        private final double errorRate;
        private final double averageLatency;
        private final boolean hasIssues;
        
        public UsageAnalysis(long totalRequests, double errorRate,
                            double averageLatency, boolean hasIssues) {
            this.totalRequests = totalRequests;
            this.errorRate = errorRate;
            this.averageLatency = averageLatency;
            this.hasIssues = hasIssues;
        }
        
        public boolean hasIssues() { return hasIssues; }
        public boolean hasHighErrorRate() { return errorRate > 0.05; }
        public boolean hasHighLatency() { return averageLatency > 500; }
        public double getErrorRate() { return errorRate; }
        public double getAverageLatency() { return averageLatency; }
    }
    
    private static class TrendAnalysis {
        private final boolean significant;
        private final String direction;
        private final double change;
        
        public TrendAnalysis(boolean significant, String direction, double change) {
            this.significant = significant;
            this.direction = direction;
            this.change = change;
        }
        
        public boolean isSignificant() { return significant; }
        
        public String getDescription() {
            return String.format("%s trend: %.1f%% change", direction, change * 100);
        }
    }
}