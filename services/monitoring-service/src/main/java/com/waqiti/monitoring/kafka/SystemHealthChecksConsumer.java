package com.waqiti.monitoring.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.monitoring.model.HealthStatus;
import com.waqiti.monitoring.model.ServiceHealth;
import com.waqiti.monitoring.model.DependencyHealth;
import com.waqiti.monitoring.model.SystemHealth;
import com.waqiti.monitoring.service.HealthMonitoringService;
import com.waqiti.monitoring.service.DependencyCheckService;
import com.waqiti.monitoring.service.AlertingService;
import com.waqiti.monitoring.service.RecoveryOrchestrationService;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.exception.ValidationException;
import com.waqiti.common.exception.SystemException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SystemHealthChecksConsumer {

    private static final Logger logger = LoggerFactory.getLogger(SystemHealthChecksConsumer.class);
    private static final String CONSUMER_NAME = "system-health-checks-consumer";
    private static final String DLQ_TOPIC = "system-health-checks-dlq";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int PROCESSING_TIMEOUT_SECONDS = 10;

    private final ObjectMapper objectMapper;
    private final HealthMonitoringService healthMonitoringService;
    private final DependencyCheckService dependencyCheckService;
    private final AlertingService alertingService;
    private final RecoveryOrchestrationService recoveryOrchestrationService;
    private final MetricsService metricsService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${kafka.consumer.system-health-checks.enabled:true}")
    private boolean consumerEnabled;

    @Value("${health.check.interval-seconds:30}")
    private int healthCheckIntervalSeconds;

    @Value("${health.check.timeout-seconds:5}")
    private int healthCheckTimeoutSeconds;

    @Value("${health.check.failure-threshold:3}")
    private int failureThreshold;

    @Value("${health.check.recovery-threshold:2}")
    private int recoveryThreshold;

    @Value("${health.check.enable-auto-recovery:true}")
    private boolean enableAutoRecovery;

    @Value("${health.check.deep-check-interval-minutes:5}")
    private int deepCheckIntervalMinutes;

    @Value("${health.check.enable-predictive-health:true}")
    private boolean enablePredictiveHealth;

    private Counter processedCounter;
    private Counter errorCounter;
    private Counter dlqCounter;
    private Timer processingTimer;
    private Gauge healthScoreGauge;
    private Gauge unhealthyServicesGauge;

    private final ConcurrentHashMap<String, ServiceHealth> serviceHealthCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DependencyHealth> dependencyHealthCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> failureCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> lastHealthCheckTime = new ConcurrentHashMap<>();
    private final AtomicLong totalHealthChecks = new AtomicLong(0);
    private final AtomicReference<Double> overallHealthScore = new AtomicReference<>(100.0);
    private ScheduledExecutorService scheduledExecutor;
    private ExecutorService healthCheckExecutor;

    @PostConstruct
    public void initializeMetrics() {
        this.processedCounter = Counter.builder("system_health_checks_processed_total")
                .description("Total processed system health check events")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.errorCounter = Counter.builder("system_health_checks_errors_total")
                .description("Total system health check processing errors")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.dlqCounter = Counter.builder("system_health_checks_dlq_total")
                .description("Total system health check events sent to DLQ")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.processingTimer = Timer.builder("system_health_checks_processing_duration")
                .description("System health check processing duration")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.healthScoreGauge = Gauge.builder("system_overall_health_score", overallHealthScore, AtomicReference::get)
                .description("Overall system health score (0-100)")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.unhealthyServicesGauge = Gauge.builder("system_unhealthy_services_count", serviceHealthCache, 
                cache -> cache.values().stream()
                    .filter(health -> health.getStatus() != HealthStatus.HEALTHY)
                    .count())
                .description("Number of unhealthy services")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.scheduledExecutor = Executors.newScheduledThreadPool(4);
        scheduledExecutor.scheduleWithFixedDelay(this::performScheduledHealthChecks, 
                healthCheckIntervalSeconds, healthCheckIntervalSeconds, TimeUnit.SECONDS);
        scheduledExecutor.scheduleWithFixedDelay(this::performDeepHealthChecks, 
                deepCheckIntervalMinutes, deepCheckIntervalMinutes, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::calculateOverallHealth, 
                60, 60, TimeUnit.SECONDS);
        scheduledExecutor.scheduleWithFixedDelay(this::performPredictiveHealthAnalysis, 
                10, 10, TimeUnit.MINUTES);

        this.healthCheckExecutor = Executors.newFixedThreadPool(6);

        logger.info("SystemHealthChecksConsumer initialized with check interval: {} seconds", healthCheckIntervalSeconds);
    }

    @KafkaListener(
        topics = "${kafka.topics.system-health-checks:system-health-checks}",
        groupId = "${kafka.consumer.group-id:monitoring-service-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "system-health-checks-circuit-breaker", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "system-health-checks-retry")
    public void processSystemHealthCheck(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(name = KafkaHeaders.CORRELATION_ID, required = false) String correlationId,
            @Header(name = KafkaHeaders.TRACE_ID, required = false) String traceId,
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String messageId = UUID.randomUUID().toString();

        try {
            MDC.put("messageId", messageId);
            MDC.put("correlationId", correlationId != null ? correlationId : messageId);
            MDC.put("traceId", traceId != null ? traceId : messageId);
            MDC.put("topic", topic);
            MDC.put("partition", String.valueOf(partition));
            MDC.put("offset", String.valueOf(offset));

            if (!consumerEnabled) {
                logger.warn("System health checks consumer is disabled, skipping message processing");
                acknowledgment.acknowledge();
                return;
            }

            logger.debug("Processing system health check message: messageId={}, topic={}, partition={}, offset={}",
                    messageId, topic, partition, offset);

            if (!StringUtils.hasText(message)) {
                logger.warn("Received empty or null message, skipping processing");
                acknowledgment.acknowledge();
                return;
            }

            JsonNode messageNode = objectMapper.readTree(message);
            
            if (!isValidHealthCheckMessage(messageNode)) {
                logger.error("Invalid health check message format: {}", message);
                sendToDlq(message, topic, "Invalid message format", null, correlationId, traceId);
                acknowledgment.acknowledge();
                return;
            }

            String eventType = messageNode.get("eventType").asText();
            
            CompletableFuture<Void> processingFuture = CompletableFuture.runAsync(() -> {
                try {
                    switch (eventType) {
                        case "SERVICE_HEALTH_CHECK":
                            handleServiceHealthCheck(messageNode, correlationId, traceId);
                            break;
                        case "DEPENDENCY_CHECK":
                            handleDependencyCheck(messageNode, correlationId, traceId);
                            break;
                        case "LIVENESS_PROBE":
                            handleLivenessProbe(messageNode, correlationId, traceId);
                            break;
                        case "READINESS_PROBE":
                            handleReadinessProbe(messageNode, correlationId, traceId);
                            break;
                        case "STARTUP_PROBE":
                            handleStartupProbe(messageNode, correlationId, traceId);
                            break;
                        case "DATABASE_HEALTH":
                            handleDatabaseHealth(messageNode, correlationId, traceId);
                            break;
                        case "CACHE_HEALTH":
                            handleCacheHealth(messageNode, correlationId, traceId);
                            break;
                        case "MESSAGE_QUEUE_HEALTH":
                            handleMessageQueueHealth(messageNode, correlationId, traceId);
                            break;
                        case "EXTERNAL_SERVICE_HEALTH":
                            handleExternalServiceHealth(messageNode, correlationId, traceId);
                            break;
                        case "INFRASTRUCTURE_HEALTH":
                            handleInfrastructureHealth(messageNode, correlationId, traceId);
                            break;
                        case "CIRCUIT_BREAKER_STATUS":
                            handleCircuitBreakerStatus(messageNode, correlationId, traceId);
                            break;
                        case "RESOURCE_HEALTH":
                            handleResourceHealth(messageNode, correlationId, traceId);
                            break;
                        case "CLUSTER_HEALTH":
                            handleClusterHealth(messageNode, correlationId, traceId);
                            break;
                        case "HEALTH_DEGRADATION":
                            handleHealthDegradation(messageNode, correlationId, traceId);
                            break;
                        case "HEALTH_RECOVERY":
                            handleHealthRecovery(messageNode, correlationId, traceId);
                            break;
                        default:
                            logger.warn("Unknown health check event type: {}", eventType);
                    }
                } catch (Exception e) {
                    logger.error("Error processing health check event: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }, healthCheckExecutor).orTimeout(PROCESSING_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            processingFuture.get();

            totalHealthChecks.incrementAndGet();
            processedCounter.increment();
            acknowledgment.acknowledge();

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse health check message: messageId={}, error={}", messageId, e.getMessage());
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } catch (Exception e) {
            logger.error("Unexpected error processing health check: messageId={}, error={}", messageId, e.getMessage(), e);
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }

    @Transactional
    private void handleServiceHealthCheck(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String serviceName = messageNode.get("serviceName").asText();
            String instanceId = messageNode.get("instanceId").asText();
            String status = messageNode.get("status").asText();
            LocalDateTime checkTime = LocalDateTime.parse(messageNode.get("timestamp").asText());
            
            ServiceHealth health = serviceHealthCache.computeIfAbsent(
                serviceName + ":" + instanceId, 
                k -> new ServiceHealth()
            );
            
            health.setServiceName(serviceName);
            health.setInstanceId(instanceId);
            health.setStatus(HealthStatus.valueOf(status));
            health.setLastCheckTime(checkTime);
            
            if (messageNode.has("responseTimeMs")) {
                health.setResponseTimeMs(messageNode.get("responseTimeMs").asLong());
            }
            
            if (messageNode.has("details")) {
                JsonNode details = messageNode.get("details");
                Map<String, Object> healthDetails = new HashMap<>();
                
                if (details.has("cpuUsage")) {
                    healthDetails.put("cpuUsage", details.get("cpuUsage").asDouble());
                }
                if (details.has("memoryUsage")) {
                    healthDetails.put("memoryUsage", details.get("memoryUsage").asDouble());
                }
                if (details.has("diskUsage")) {
                    healthDetails.put("diskUsage", details.get("diskUsage").asDouble());
                }
                if (details.has("activeConnections")) {
                    healthDetails.put("activeConnections", details.get("activeConnections").asInt());
                }
                if (details.has("errorRate")) {
                    healthDetails.put("errorRate", details.get("errorRate").asDouble());
                }
                
                health.setDetails(healthDetails);
            }
            
            evaluateServiceHealth(health);
            
            if (health.getStatus() != HealthStatus.HEALTHY) {
                handleUnhealthyService(health);
            } else {
                handleHealthyService(health);
            }
            
            healthMonitoringService.recordServiceHealth(health);
            
            logger.debug("Service health check: service={}, instance={}, status={}", 
                       serviceName, instanceId, status);
            
        } catch (Exception e) {
            logger.error("Error handling service health check: {}", e.getMessage(), e);
            throw new SystemException("Failed to process service health check", e);
        }
    }

    @Transactional
    private void handleDependencyCheck(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String serviceName = messageNode.get("serviceName").asText();
            String dependencyName = messageNode.get("dependencyName").asText();
            String dependencyType = messageNode.get("dependencyType").asText();
            boolean isHealthy = messageNode.get("isHealthy").asBoolean();
            long responseTimeMs = messageNode.get("responseTimeMs").asLong();
            
            String key = serviceName + "->" + dependencyName;
            DependencyHealth health = dependencyHealthCache.computeIfAbsent(
                key, 
                k -> new DependencyHealth()
            );
            
            health.setServiceName(serviceName);
            health.setDependencyName(dependencyName);
            health.setDependencyType(dependencyType);
            health.setHealthy(isHealthy);
            health.setResponseTimeMs(responseTimeMs);
            health.setLastCheckTime(LocalDateTime.now());
            
            if (messageNode.has("errorMessage") && !isHealthy) {
                health.setErrorMessage(messageNode.get("errorMessage").asText());
            }
            
            if (!isHealthy) {
                incrementDependencyFailure(key);
                
                if (shouldTriggerDependencyAlert(key)) {
                    createDependencyAlert(health);
                }
                
                if (isDependencyCritical(dependencyType)) {
                    evaluateCascadingImpact(serviceName, dependencyName);
                }
            } else {
                resetDependencyFailure(key);
            }
            
            dependencyCheckService.recordDependencyHealth(health);
            
            logger.debug("Dependency check: {} -> {} ({}), healthy={}, response={}ms", 
                       serviceName, dependencyName, dependencyType, isHealthy, responseTimeMs);
            
        } catch (Exception e) {
            logger.error("Error handling dependency check: {}", e.getMessage(), e);
            throw new SystemException("Failed to process dependency check", e);
        }
    }

    @Transactional
    private void handleLivenessProbe(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String serviceName = messageNode.get("serviceName").asText();
            String instanceId = messageNode.get("instanceId").asText();
            boolean alive = messageNode.get("alive").asBoolean();
            LocalDateTime probeTime = LocalDateTime.parse(messageNode.get("timestamp").asText());
            
            Map<String, Object> livenessData = new HashMap<>();
            livenessData.put("serviceName", serviceName);
            livenessData.put("instanceId", instanceId);
            livenessData.put("alive", alive);
            livenessData.put("probeTime", probeTime.toString());
            
            if (!alive) {
                livenessData.put("severity", "CRITICAL");
                
                if (messageNode.has("failureReason")) {
                    livenessData.put("failureReason", messageNode.get("failureReason").asText());
                }
                
                handleLivenessFailure(serviceName, instanceId, livenessData);
                
                if (enableAutoRecovery) {
                    initiateServiceRecovery(serviceName, instanceId, "LIVENESS_FAILURE");
                }
            } else {
                updateServiceLiveness(serviceName, instanceId, true);
            }
            
            healthMonitoringService.recordLivenessProbe(livenessData);
            
            logger.info("Liveness probe: service={}, instance={}, alive={}", 
                       serviceName, instanceId, alive);
            
        } catch (Exception e) {
            logger.error("Error handling liveness probe: {}", e.getMessage(), e);
            throw new SystemException("Failed to process liveness probe", e);
        }
    }

    @Transactional
    private void handleReadinessProbe(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String serviceName = messageNode.get("serviceName").asText();
            String instanceId = messageNode.get("instanceId").asText();
            boolean ready = messageNode.get("ready").asBoolean();
            LocalDateTime probeTime = LocalDateTime.parse(messageNode.get("timestamp").asText());
            
            Map<String, Object> readinessData = new HashMap<>();
            readinessData.put("serviceName", serviceName);
            readinessData.put("instanceId", instanceId);
            readinessData.put("ready", ready);
            readinessData.put("probeTime", probeTime.toString());
            
            if (messageNode.has("checks")) {
                JsonNode checks = messageNode.get("checks");
                Map<String, Boolean> readinessChecks = new HashMap<>();
                
                checks.fields().forEachRemaining(entry -> {
                    readinessChecks.put(entry.getKey(), entry.getValue().asBoolean());
                });
                
                readinessData.put("checks", readinessChecks);
                
                List<String> failedChecks = readinessChecks.entrySet().stream()
                    .filter(entry -> !entry.getValue())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
                
                if (!failedChecks.isEmpty()) {
                    readinessData.put("failedChecks", failedChecks);
                    handleReadinessFailure(serviceName, instanceId, failedChecks);
                }
            }
            
            updateServiceReadiness(serviceName, instanceId, ready);
            
            if (!ready && shouldRemoveFromLoadBalancer(serviceName, instanceId)) {
                removeFromLoadBalancer(serviceName, instanceId);
            } else if (ready && canAddToLoadBalancer(serviceName, instanceId)) {
                addToLoadBalancer(serviceName, instanceId);
            }
            
            healthMonitoringService.recordReadinessProbe(readinessData);
            
            logger.info("Readiness probe: service={}, instance={}, ready={}", 
                       serviceName, instanceId, ready);
            
        } catch (Exception e) {
            logger.error("Error handling readiness probe: {}", e.getMessage(), e);
            throw new SystemException("Failed to process readiness probe", e);
        }
    }

    @Transactional
    private void handleStartupProbe(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String serviceName = messageNode.get("serviceName").asText();
            String instanceId = messageNode.get("instanceId").asText();
            boolean started = messageNode.get("started").asBoolean();
            int attemptNumber = messageNode.get("attemptNumber").asInt();
            int maxAttempts = messageNode.get("maxAttempts").asInt();
            
            Map<String, Object> startupData = new HashMap<>();
            startupData.put("serviceName", serviceName);
            startupData.put("instanceId", instanceId);
            startupData.put("started", started);
            startupData.put("attemptNumber", attemptNumber);
            startupData.put("maxAttempts", maxAttempts);
            startupData.put("timestamp", LocalDateTime.now().toString());
            
            if (!started) {
                if (attemptNumber >= maxAttempts) {
                    startupData.put("severity", "CRITICAL");
                    startupData.put("action", "STARTUP_FAILURE");
                    
                    handleStartupFailure(serviceName, instanceId);
                    
                    if (enableAutoRecovery) {
                        initiateServiceRestart(serviceName, instanceId);
                    }
                } else {
                    startupData.put("severity", "WARNING");
                    startupData.put("remainingAttempts", maxAttempts - attemptNumber);
                }
            } else {
                handleSuccessfulStartup(serviceName, instanceId);
            }
            
            healthMonitoringService.recordStartupProbe(startupData);
            
            logger.info("Startup probe: service={}, instance={}, started={}, attempt={}/{}", 
                       serviceName, instanceId, started, attemptNumber, maxAttempts);
            
        } catch (Exception e) {
            logger.error("Error handling startup probe: {}", e.getMessage(), e);
            throw new SystemException("Failed to process startup probe", e);
        }
    }

    @Transactional
    private void handleDatabaseHealth(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String databaseName = messageNode.get("databaseName").asText();
            String connectionPool = messageNode.get("connectionPool").asText();
            boolean isHealthy = messageNode.get("isHealthy").asBoolean();
            int activeConnections = messageNode.get("activeConnections").asInt();
            int maxConnections = messageNode.get("maxConnections").asInt();
            long avgQueryTimeMs = messageNode.get("avgQueryTimeMs").asLong();
            
            Map<String, Object> dbHealth = new HashMap<>();
            dbHealth.put("databaseName", databaseName);
            dbHealth.put("connectionPool", connectionPool);
            dbHealth.put("isHealthy", isHealthy);
            dbHealth.put("activeConnections", activeConnections);
            dbHealth.put("maxConnections", maxConnections);
            dbHealth.put("connectionUtilization", (double) activeConnections / maxConnections);
            dbHealth.put("avgQueryTimeMs", avgQueryTimeMs);
            dbHealth.put("timestamp", LocalDateTime.now().toString());
            
            if (messageNode.has("slowQueries")) {
                dbHealth.put("slowQueries", messageNode.get("slowQueries").asInt());
            }
            
            if (messageNode.has("deadlocks")) {
                dbHealth.put("deadlocks", messageNode.get("deadlocks").asInt());
            }
            
            if (!isHealthy || activeConnections > maxConnections * 0.8) {
                createDatabaseHealthAlert(dbHealth);
                
                if (activeConnections >= maxConnections) {
                    handleConnectionPoolExhaustion(databaseName, connectionPool);
                }
            }
            
            if (avgQueryTimeMs > 1000) {
                analyzeDatabasePerformance(databaseName, avgQueryTimeMs);
            }
            
            healthMonitoringService.recordDatabaseHealth(dbHealth);
            
            logger.debug("Database health: db={}, pool={}, healthy={}, connections={}/{}, avgQuery={}ms", 
                       databaseName, connectionPool, isHealthy, activeConnections, maxConnections, avgQueryTimeMs);
            
        } catch (Exception e) {
            logger.error("Error handling database health: {}", e.getMessage(), e);
            throw new SystemException("Failed to process database health", e);
        }
    }

    @Transactional
    private void handleCacheHealth(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String cacheName = messageNode.get("cacheName").asText();
            String cacheType = messageNode.get("cacheType").asText();
            boolean isHealthy = messageNode.get("isHealthy").asBoolean();
            double hitRate = messageNode.get("hitRate").asDouble();
            long evictions = messageNode.get("evictions").asLong();
            long memoryUsedMB = messageNode.get("memoryUsedMB").asLong();
            long maxMemoryMB = messageNode.get("maxMemoryMB").asLong();
            
            Map<String, Object> cacheHealth = new HashMap<>();
            cacheHealth.put("cacheName", cacheName);
            cacheHealth.put("cacheType", cacheType);
            cacheHealth.put("isHealthy", isHealthy);
            cacheHealth.put("hitRate", hitRate);
            cacheHealth.put("evictions", evictions);
            cacheHealth.put("memoryUsedMB", memoryUsedMB);
            cacheHealth.put("maxMemoryMB", maxMemoryMB);
            cacheHealth.put("memoryUtilization", (double) memoryUsedMB / maxMemoryMB);
            cacheHealth.put("timestamp", LocalDateTime.now().toString());
            
            if (hitRate < 0.5) {
                cacheHealth.put("issue", "LOW_HIT_RATE");
                createCachePerformanceAlert(cacheName, hitRate, "LOW_HIT_RATE");
            }
            
            if (evictions > 1000) {
                cacheHealth.put("issue", "HIGH_EVICTION_RATE");
                analyzeCacheEvictionPattern(cacheName, evictions);
            }
            
            if (memoryUsedMB > maxMemoryMB * 0.9) {
                cacheHealth.put("issue", "HIGH_MEMORY_USAGE");
                handleCacheMemoryPressure(cacheName, memoryUsedMB, maxMemoryMB);
            }
            
            healthMonitoringService.recordCacheHealth(cacheHealth);
            
            logger.debug("Cache health: name={}, type={}, healthy={}, hitRate={}, memory={}/{}MB", 
                       cacheName, cacheType, isHealthy, hitRate, memoryUsedMB, maxMemoryMB);
            
        } catch (Exception e) {
            logger.error("Error handling cache health: {}", e.getMessage(), e);
            throw new SystemException("Failed to process cache health", e);
        }
    }

    @Transactional
    private void handleMessageQueueHealth(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String queueName = messageNode.get("queueName").asText();
            String queueType = messageNode.get("queueType").asText();
            boolean isHealthy = messageNode.get("isHealthy").asBoolean();
            long pendingMessages = messageNode.get("pendingMessages").asLong();
            long consumerLag = messageNode.get("consumerLag").asLong();
            int activeConsumers = messageNode.get("activeConsumers").asInt();
            
            Map<String, Object> queueHealth = new HashMap<>();
            queueHealth.put("queueName", queueName);
            queueHealth.put("queueType", queueType);
            queueHealth.put("isHealthy", isHealthy);
            queueHealth.put("pendingMessages", pendingMessages);
            queueHealth.put("consumerLag", consumerLag);
            queueHealth.put("activeConsumers", activeConsumers);
            queueHealth.put("timestamp", LocalDateTime.now().toString());
            
            if (messageNode.has("errorRate")) {
                double errorRate = messageNode.get("errorRate").asDouble();
                queueHealth.put("errorRate", errorRate);
                
                if (errorRate > 0.05) {
                    createQueueErrorAlert(queueName, errorRate);
                }
            }
            
            if (pendingMessages > 10000) {
                queueHealth.put("issue", "HIGH_QUEUE_DEPTH");
                handleHighQueueDepth(queueName, pendingMessages);
            }
            
            if (consumerLag > 60000) {
                queueHealth.put("issue", "HIGH_CONSUMER_LAG");
                handleHighConsumerLag(queueName, consumerLag);
            }
            
            if (activeConsumers == 0) {
                queueHealth.put("issue", "NO_ACTIVE_CONSUMERS");
                handleNoActiveConsumers(queueName);
            }
            
            healthMonitoringService.recordMessageQueueHealth(queueHealth);
            
            logger.debug("Message queue health: name={}, type={}, healthy={}, pending={}, lag={}, consumers={}", 
                       queueName, queueType, isHealthy, pendingMessages, consumerLag, activeConsumers);
            
        } catch (Exception e) {
            logger.error("Error handling message queue health: {}", e.getMessage(), e);
            throw new SystemException("Failed to process message queue health", e);
        }
    }

    @Transactional
    private void handleExternalServiceHealth(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String serviceName = messageNode.get("serviceName").asText();
            String endpoint = messageNode.get("endpoint").asText();
            boolean isReachable = messageNode.get("isReachable").asBoolean();
            long responseTimeMs = messageNode.get("responseTimeMs").asLong();
            int statusCode = messageNode.get("statusCode").asInt();
            
            Map<String, Object> externalHealth = new HashMap<>();
            externalHealth.put("serviceName", serviceName);
            externalHealth.put("endpoint", endpoint);
            externalHealth.put("isReachable", isReachable);
            externalHealth.put("responseTimeMs", responseTimeMs);
            externalHealth.put("statusCode", statusCode);
            externalHealth.put("timestamp", LocalDateTime.now().toString());
            
            if (!isReachable) {
                handleUnreachableExternalService(serviceName, endpoint);
                
                if (hasAlternativeEndpoint(serviceName)) {
                    switchToAlternativeEndpoint(serviceName);
                }
            } else if (responseTimeMs > 5000) {
                externalHealth.put("issue", "SLOW_RESPONSE");
                handleSlowExternalService(serviceName, endpoint, responseTimeMs);
            } else if (statusCode >= 500) {
                externalHealth.put("issue", "SERVER_ERROR");
                handleExternalServiceError(serviceName, endpoint, statusCode);
            }
            
            if (messageNode.has("certificateExpiry")) {
                LocalDateTime certExpiry = LocalDateTime.parse(messageNode.get("certificateExpiry").asText());
                long daysUntilExpiry = ChronoUnit.DAYS.between(LocalDateTime.now(), certExpiry);
                
                if (daysUntilExpiry < 30) {
                    createCertificateExpiryWarning(serviceName, daysUntilExpiry);
                }
            }
            
            healthMonitoringService.recordExternalServiceHealth(externalHealth);
            
            logger.debug("External service health: service={}, endpoint={}, reachable={}, response={}ms, status={}", 
                       serviceName, endpoint, isReachable, responseTimeMs, statusCode);
            
        } catch (Exception e) {
            logger.error("Error handling external service health: {}", e.getMessage(), e);
            throw new SystemException("Failed to process external service health", e);
        }
    }

    @Transactional
    private void handleInfrastructureHealth(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String componentType = messageNode.get("componentType").asText();
            String componentId = messageNode.get("componentId").asText();
            String status = messageNode.get("status").asText();
            
            Map<String, Object> infraHealth = new HashMap<>();
            infraHealth.put("componentType", componentType);
            infraHealth.put("componentId", componentId);
            infraHealth.put("status", status);
            infraHealth.put("timestamp", LocalDateTime.now().toString());
            
            switch (componentType) {
                case "LOAD_BALANCER":
                    handleLoadBalancerHealth(componentId, status, messageNode);
                    break;
                case "DNS":
                    handleDnsHealth(componentId, status, messageNode);
                    break;
                case "FIREWALL":
                    handleFirewallHealth(componentId, status, messageNode);
                    break;
                case "NETWORK":
                    handleNetworkHealth(componentId, status, messageNode);
                    break;
                case "STORAGE":
                    handleStorageHealth(componentId, status, messageNode);
                    break;
                case "CONTAINER_ORCHESTRATION":
                    handleContainerOrchestrationHealth(componentId, status, messageNode);
                    break;
            }
            
            if (!"HEALTHY".equals(status)) {
                evaluateInfrastructureImpact(componentType, componentId);
                
                if (isCriticalInfrastructure(componentType)) {
                    triggerInfrastructureFailover(componentType, componentId);
                }
            }
            
            healthMonitoringService.recordInfrastructureHealth(infraHealth);
            
            logger.info("Infrastructure health: type={}, id={}, status={}", 
                       componentType, componentId, status);
            
        } catch (Exception e) {
            logger.error("Error handling infrastructure health: {}", e.getMessage(), e);
            throw new SystemException("Failed to process infrastructure health", e);
        }
    }

    @Transactional
    private void handleCircuitBreakerStatus(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String serviceName = messageNode.get("serviceName").asText();
            String circuitBreakerName = messageNode.get("circuitBreakerName").asText();
            String state = messageNode.get("state").asText();
            double failureRate = messageNode.get("failureRate").asDouble();
            int bufferedCalls = messageNode.get("bufferedCalls").asInt();
            int failedCalls = messageNode.get("failedCalls").asInt();
            
            Map<String, Object> cbStatus = new HashMap<>();
            cbStatus.put("serviceName", serviceName);
            cbStatus.put("circuitBreakerName", circuitBreakerName);
            cbStatus.put("state", state);
            cbStatus.put("failureRate", failureRate);
            cbStatus.put("bufferedCalls", bufferedCalls);
            cbStatus.put("failedCalls", failedCalls);
            cbStatus.put("timestamp", LocalDateTime.now().toString());
            
            switch (state) {
                case "OPEN":
                    handleOpenCircuitBreaker(serviceName, circuitBreakerName, failureRate);
                    break;
                case "HALF_OPEN":
                    handleHalfOpenCircuitBreaker(serviceName, circuitBreakerName);
                    break;
                case "CLOSED":
                    handleClosedCircuitBreaker(serviceName, circuitBreakerName);
                    break;
                case "DISABLED":
                case "FORCED_OPEN":
                    handleForcedCircuitBreakerState(serviceName, circuitBreakerName, state);
                    break;
            }
            
            if (failureRate > 0.5 && !"OPEN".equals(state)) {
                recommendCircuitBreakerAdjustment(serviceName, circuitBreakerName, failureRate);
            }
            
            healthMonitoringService.recordCircuitBreakerStatus(cbStatus);
            
            logger.info("Circuit breaker status: service={}, name={}, state={}, failureRate={}", 
                       serviceName, circuitBreakerName, state, failureRate);
            
        } catch (Exception e) {
            logger.error("Error handling circuit breaker status: {}", e.getMessage(), e);
            throw new SystemException("Failed to process circuit breaker status", e);
        }
    }

    @Transactional
    private void handleResourceHealth(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String resourceType = messageNode.get("resourceType").asText();
            String resourceId = messageNode.get("resourceId").asText();
            double utilization = messageNode.get("utilization").asDouble();
            double threshold = messageNode.get("threshold").asDouble();
            
            Map<String, Object> resourceHealth = new HashMap<>();
            resourceHealth.put("resourceType", resourceType);
            resourceHealth.put("resourceId", resourceId);
            resourceHealth.put("utilization", utilization);
            resourceHealth.put("threshold", threshold);
            resourceHealth.put("timestamp", LocalDateTime.now().toString());
            
            boolean isHealthy = utilization < threshold;
            resourceHealth.put("isHealthy", isHealthy);
            
            if (!isHealthy) {
                resourceHealth.put("severity", utilization > threshold * 1.2 ? "CRITICAL" : "WARNING");
                
                switch (resourceType) {
                    case "CPU":
                        handleHighCpuUtilization(resourceId, utilization);
                        break;
                    case "MEMORY":
                        handleHighMemoryUtilization(resourceId, utilization);
                        break;
                    case "DISK":
                        handleHighDiskUtilization(resourceId, utilization);
                        break;
                    case "NETWORK":
                        handleHighNetworkUtilization(resourceId, utilization);
                        break;
                    case "THREAD_POOL":
                        handleThreadPoolExhaustion(resourceId, utilization);
                        break;
                }
                
                if (utilization > threshold * 1.5) {
                    triggerResourceScaling(resourceType, resourceId);
                }
            }
            
            healthMonitoringService.recordResourceHealth(resourceHealth);
            
            logger.debug("Resource health: type={}, id={}, utilization={}%, threshold={}%", 
                       resourceType, resourceId, utilization * 100, threshold * 100);
            
        } catch (Exception e) {
            logger.error("Error handling resource health: {}", e.getMessage(), e);
            throw new SystemException("Failed to process resource health", e);
        }
    }

    @Transactional
    private void handleClusterHealth(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String clusterName = messageNode.get("clusterName").asText();
            int totalNodes = messageNode.get("totalNodes").asInt();
            int healthyNodes = messageNode.get("healthyNodes").asInt();
            int unhealthyNodes = messageNode.get("unhealthyNodes").asInt();
            String clusterState = messageNode.get("clusterState").asText();
            
            Map<String, Object> clusterHealth = new HashMap<>();
            clusterHealth.put("clusterName", clusterName);
            clusterHealth.put("totalNodes", totalNodes);
            clusterHealth.put("healthyNodes", healthyNodes);
            clusterHealth.put("unhealthyNodes", unhealthyNodes);
            clusterHealth.put("clusterState", clusterState);
            clusterHealth.put("nodeHealthPercentage", (double) healthyNodes / totalNodes * 100);
            clusterHealth.put("timestamp", LocalDateTime.now().toString());
            
            if (messageNode.has("leader")) {
                clusterHealth.put("leader", messageNode.get("leader").asText());
            }
            
            if (messageNode.has("partitionStatus")) {
                clusterHealth.put("partitionStatus", messageNode.get("partitionStatus").asText());
                
                if ("PARTITIONED".equals(messageNode.get("partitionStatus").asText())) {
                    handleClusterPartition(clusterName);
                }
            }
            
            if (unhealthyNodes > 0) {
                double unhealthyPercentage = (double) unhealthyNodes / totalNodes * 100;
                
                if (unhealthyPercentage > 50) {
                    clusterHealth.put("severity", "CRITICAL");
                    handleClusterCriticalState(clusterName, unhealthyNodes, totalNodes);
                } else if (unhealthyPercentage > 25) {
                    clusterHealth.put("severity", "WARNING");
                    handleClusterDegradedState(clusterName, unhealthyNodes, totalNodes);
                }
            }
            
            if (!"GREEN".equals(clusterState)) {
                analyzeClusterHealth(clusterName, clusterState);
            }
            
            healthMonitoringService.recordClusterHealth(clusterHealth);
            
            logger.info("Cluster health: name={}, state={}, nodes={}/{} healthy", 
                       clusterName, clusterState, healthyNodes, totalNodes);
            
        } catch (Exception e) {
            logger.error("Error handling cluster health: {}", e.getMessage(), e);
            throw new SystemException("Failed to process cluster health", e);
        }
    }

    @Transactional
    private void handleHealthDegradation(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String serviceName = messageNode.get("serviceName").asText();
            String degradationType = messageNode.get("degradationType").asText();
            double previousScore = messageNode.get("previousScore").asDouble();
            double currentScore = messageNode.get("currentScore").asDouble();
            double degradationRate = ((previousScore - currentScore) / previousScore) * 100;
            
            Map<String, Object> degradation = new HashMap<>();
            degradation.put("serviceName", serviceName);
            degradation.put("degradationType", degradationType);
            degradation.put("previousScore", previousScore);
            degradation.put("currentScore", currentScore);
            degradation.put("degradationRate", degradationRate);
            degradation.put("timestamp", LocalDateTime.now().toString());
            
            if (degradationRate > 50) {
                degradation.put("severity", "CRITICAL");
                handleCriticalDegradation(serviceName, degradationType, degradationRate);
            } else if (degradationRate > 25) {
                degradation.put("severity", "HIGH");
                handleHighDegradation(serviceName, degradationType, degradationRate);
            } else {
                degradation.put("severity", "MEDIUM");
            }
            
            if (messageNode.has("rootCause")) {
                degradation.put("rootCause", messageNode.get("rootCause").asText());
                addressRootCause(serviceName, messageNode.get("rootCause").asText());
            }
            
            if (enablePredictiveHealth) {
                predictFutureDegradation(serviceName, degradationType, degradationRate);
            }
            
            healthMonitoringService.recordHealthDegradation(degradation);
            
            logger.warn("Health degradation: service={}, type={}, degradation={}%", 
                       serviceName, degradationType, degradationRate);
            
        } catch (Exception e) {
            logger.error("Error handling health degradation: {}", e.getMessage(), e);
            throw new SystemException("Failed to process health degradation", e);
        }
    }

    @Transactional
    private void handleHealthRecovery(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String serviceName = messageNode.get("serviceName").asText();
            String recoveryType = messageNode.get("recoveryType").asText();
            double previousScore = messageNode.get("previousScore").asDouble();
            double currentScore = messageNode.get("currentScore").asDouble();
            long recoveryTimeMs = messageNode.get("recoveryTimeMs").asLong();
            
            Map<String, Object> recovery = new HashMap<>();
            recovery.put("serviceName", serviceName);
            recovery.put("recoveryType", recoveryType);
            recovery.put("previousScore", previousScore);
            recovery.put("currentScore", currentScore);
            recovery.put("recoveryTimeMs", recoveryTimeMs);
            recovery.put("improvementRate", ((currentScore - previousScore) / previousScore) * 100);
            recovery.put("timestamp", LocalDateTime.now().toString());
            
            resetServiceFailureCounters(serviceName);
            updateServiceHealthStatus(serviceName, HealthStatus.HEALTHY);
            
            if (messageNode.has("recoveryAction")) {
                recovery.put("recoveryAction", messageNode.get("recoveryAction").asText());
                recordSuccessfulRecoveryAction(serviceName, messageNode.get("recoveryAction").asText());
            }
            
            healthMonitoringService.recordHealthRecovery(recovery);
            
            logger.info("Health recovery: service={}, type={}, score improved from {} to {} in {}ms", 
                       serviceName, recoveryType, previousScore, currentScore, recoveryTimeMs);
            
        } catch (Exception e) {
            logger.error("Error handling health recovery: {}", e.getMessage(), e);
            throw new SystemException("Failed to process health recovery", e);
        }
    }

    private void performScheduledHealthChecks() {
        try {
            logger.debug("Performing scheduled health checks for {} services", serviceHealthCache.size());
            
            for (Map.Entry<String, ServiceHealth> entry : serviceHealthCache.entrySet()) {
                ServiceHealth health = entry.getValue();
                
                if (shouldPerformHealthCheck(health)) {
                    performHealthCheck(health.getServiceName(), health.getInstanceId());
                }
            }
            
            calculateOverallHealth();
            
        } catch (Exception e) {
            logger.error("Error performing scheduled health checks: {}", e.getMessage(), e);
        }
    }

    private void performDeepHealthChecks() {
        try {
            logger.info("Performing deep health checks");
            
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (ServiceHealth health : serviceHealthCache.values()) {
                futures.add(CompletableFuture.runAsync(() -> {
                    performDeepHealthCheck(health.getServiceName(), health.getInstanceId());
                }, healthCheckExecutor));
            }
            
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .orTimeout(30, TimeUnit.SECONDS)
                .join();
            
        } catch (Exception e) {
            logger.error("Error performing deep health checks: {}", e.getMessage(), e);
        }
    }

    private void calculateOverallHealth() {
        try {
            int totalServices = serviceHealthCache.size();
            if (totalServices == 0) {
                overallHealthScore.set(100.0);
                return;
            }
            
            long healthyServices = serviceHealthCache.values().stream()
                .filter(health -> health.getStatus() == HealthStatus.HEALTHY)
                .count();
            
            long degradedServices = serviceHealthCache.values().stream()
                .filter(health -> health.getStatus() == HealthStatus.DEGRADED)
                .count();
            
            long unhealthyServices = serviceHealthCache.values().stream()
                .filter(health -> health.getStatus() == HealthStatus.UNHEALTHY)
                .count();
            
            double score = (healthyServices * 100.0 + degradedServices * 50.0) / totalServices;
            overallHealthScore.set(score);
            
            SystemHealth systemHealth = new SystemHealth();
            systemHealth.setOverallScore(score);
            systemHealth.setTotalServices(totalServices);
            systemHealth.setHealthyServices((int) healthyServices);
            systemHealth.setDegradedServices((int) degradedServices);
            systemHealth.setUnhealthyServices((int) unhealthyServices);
            systemHealth.setTimestamp(LocalDateTime.now());
            
            healthMonitoringService.recordSystemHealth(systemHealth);
            
            if (score < 50) {
                createSystemHealthAlert("CRITICAL", score);
            } else if (score < 75) {
                createSystemHealthAlert("WARNING", score);
            }
            
            logger.info("Overall system health: score={}, healthy={}, degraded={}, unhealthy={}", 
                       score, healthyServices, degradedServices, unhealthyServices);
            
        } catch (Exception e) {
            logger.error("Error calculating overall health: {}", e.getMessage(), e);
        }
    }

    private void performPredictiveHealthAnalysis() {
        if (!enablePredictiveHealth) {
            return;
        }
        
        try {
            logger.debug("Performing predictive health analysis");
            
            for (ServiceHealth health : serviceHealthCache.values()) {
                List<ServiceHealth> historicalData = healthMonitoringService.getHistoricalHealth(
                    health.getServiceName(), 
                    health.getInstanceId(), 
                    24
                );
                
                if (historicalData.size() >= 10) {
                    PredictedHealth prediction = healthMonitoringService.predictHealth(
                        health, 
                        historicalData
                    );
                    
                    if (prediction.getPredictedStatus() != HealthStatus.HEALTHY) {
                        createPredictiveHealthAlert(health, prediction);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error performing predictive health analysis: {}", e.getMessage(), e);
        }
    }

    // Helper methods
    private boolean isValidHealthCheckMessage(JsonNode messageNode) {
        return messageNode != null &&
               messageNode.has("eventType") && 
               StringUtils.hasText(messageNode.get("eventType").asText()) &&
               messageNode.has("timestamp");
    }

    private void evaluateServiceHealth(ServiceHealth health) {
        if (health.getResponseTimeMs() > 5000) {
            health.setStatus(HealthStatus.DEGRADED);
        }
        
        if (health.getDetails() != null) {
            Double cpuUsage = (Double) health.getDetails().get("cpuUsage");
            Double memoryUsage = (Double) health.getDetails().get("memoryUsage");
            Double errorRate = (Double) health.getDetails().get("errorRate");
            
            if ((cpuUsage != null && cpuUsage > 0.9) ||
                (memoryUsage != null && memoryUsage > 0.9) ||
                (errorRate != null && errorRate > 0.1)) {
                health.setStatus(HealthStatus.UNHEALTHY);
            }
        }
    }

    private void handleUnhealthyService(ServiceHealth health) {
        String key = health.getServiceName() + ":" + health.getInstanceId();
        AtomicInteger failureCount = failureCounters.computeIfAbsent(key, k -> new AtomicInteger(0));
        failureCount.incrementAndGet();
        
        if (failureCount.get() >= failureThreshold) {
            createServiceHealthAlert(health);
            
            if (enableAutoRecovery) {
                initiateServiceRecovery(health.getServiceName(), health.getInstanceId(), "UNHEALTHY");
            }
        }
    }

    private void handleHealthyService(ServiceHealth health) {
        String key = health.getServiceName() + ":" + health.getInstanceId();
        failureCounters.remove(key);
    }

    private void createServiceHealthAlert(ServiceHealth health) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "SERVICE_UNHEALTHY");
        alert.put("serviceName", health.getServiceName());
        alert.put("instanceId", health.getInstanceId());
        alert.put("status", health.getStatus());
        alert.put("details", health.getDetails());
        alert.put("timestamp", LocalDateTime.now().toString());
        alert.put("severity", health.getStatus() == HealthStatus.UNHEALTHY ? "CRITICAL" : "WARNING");
        
        kafkaTemplate.send("health-alerts", alert);
    }

    private void initiateServiceRecovery(String serviceName, String instanceId, String reason) {
        Map<String, Object> recovery = new HashMap<>();
        recovery.put("serviceName", serviceName);
        recovery.put("instanceId", instanceId);
        recovery.put("reason", reason);
        recovery.put("action", "INITIATE_RECOVERY");
        recovery.put("timestamp", LocalDateTime.now().toString());
        
        recoveryOrchestrationService.initiateRecovery(recovery);
    }

    private void handleProcessingError(String message, String topic, Exception error, 
                                     String correlationId, String traceId, Acknowledgment acknowledgment) {
        try {
            errorCounter.increment();
            logger.error("Error processing health check message: {}", error.getMessage(), error);
            
            sendToDlq(message, topic, error.getMessage(), error, correlationId, traceId);
            acknowledgment.acknowledge();
            
        } catch (Exception dlqError) {
            logger.error("Failed to send message to DLQ: {}", dlqError.getMessage(), dlqError);
            acknowledgment.nack();
        }
    }

    private void sendToDlq(String originalMessage, String originalTopic, String errorReason, 
                          Exception error, String correlationId, String traceId) {
        try {
            Map<String, Object> dlqMessage = new HashMap<>();
            dlqMessage.put("originalMessage", originalMessage);
            dlqMessage.put("originalTopic", originalTopic);
            dlqMessage.put("errorReason", errorReason);
            dlqMessage.put("errorTimestamp", LocalDateTime.now().toString());
            dlqMessage.put("correlationId", correlationId);
            dlqMessage.put("traceId", traceId);
            dlqMessage.put("consumerName", CONSUMER_NAME);
            
            if (error != null) {
                dlqMessage.put("errorClass", error.getClass().getSimpleName());
                dlqMessage.put("errorMessage", error.getMessage());
            }
            
            kafkaTemplate.send(DLQ_TOPIC, dlqMessage);
            dlqCounter.increment();
            
            logger.info("Sent message to DLQ: topic={}, reason={}", DLQ_TOPIC, errorReason);
            
        } catch (Exception e) {
            logger.error("Failed to send message to DLQ: {}", e.getMessage(), e);
        }
    }

    public void handleCircuitBreakerFallback(String message, String topic, int partition, long offset,
                                           String correlationId, String traceId, ConsumerRecord<String, String> record,
                                           Acknowledgment acknowledgment, Exception ex) {
        logger.error("Circuit breaker fallback triggered for health checks consumer: {}", ex.getMessage());
        
        errorCounter.increment();
        sendToDlq(message, topic, "Circuit breaker fallback: " + ex.getMessage(), ex, correlationId, traceId);
        acknowledgment.acknowledge();
    }

    // Additional helper methods would be implemented here for all the specific handlers...

    public static class PredictedHealth {
        private HealthStatus predictedStatus;
        private double confidence;
        private LocalDateTime predictionTime;
        private String trend;
        
        public HealthStatus getPredictedStatus() { return predictedStatus; }
        public void setPredictedStatus(HealthStatus predictedStatus) { this.predictedStatus = predictedStatus; }
        
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        
        public LocalDateTime getPredictionTime() { return predictionTime; }
        public void setPredictionTime(LocalDateTime predictionTime) { this.predictionTime = predictionTime; }
        
        public String getTrend() { return trend; }
        public void setTrend(String trend) { this.trend = trend; }
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down SystemHealthChecksConsumer");
        
        calculateOverallHealth();
        
        scheduledExecutor.shutdown();
        healthCheckExecutor.shutdown();
        
        try {
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            if (!healthCheckExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                healthCheckExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Error shutting down executors", e);
            scheduledExecutor.shutdownNow();
            healthCheckExecutor.shutdownNow();
        }
        
        logger.info("SystemHealthChecksConsumer shutdown complete. Total health checks: {}", 
                   totalHealthChecks.get());
    }

    // Stub methods for helper functionality
    private void incrementDependencyFailure(String key) {
        failureCounters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
    }
    
    private void resetDependencyFailure(String key) {
        failureCounters.remove(key);
    }
    
    private boolean shouldTriggerDependencyAlert(String key) {
        AtomicInteger count = failureCounters.get(key);
        return count != null && count.get() >= failureThreshold;
    }
    
    private void createDependencyAlert(DependencyHealth health) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "DEPENDENCY_FAILURE");
        alert.put("serviceName", health.getServiceName());
        alert.put("dependencyName", health.getDependencyName());
        alert.put("timestamp", LocalDateTime.now().toString());
        kafkaTemplate.send("dependency-alerts", alert);
    }
    
    private boolean isDependencyCritical(String dependencyType) {
        return "DATABASE".equals(dependencyType) || "AUTH_SERVICE".equals(dependencyType);
    }
    
    private void evaluateCascadingImpact(String serviceName, String dependencyName) {
        dependencyCheckService.evaluateCascadingImpact(serviceName, dependencyName);
    }
    
    private void handleLivenessFailure(String serviceName, String instanceId, Map<String, Object> data) {
        kafkaTemplate.send("liveness-failures", data);
    }
    
    private void updateServiceLiveness(String serviceName, String instanceId, boolean alive) {
        String key = serviceName + ":" + instanceId;
        ServiceHealth health = serviceHealthCache.get(key);
        if (health != null) {
            health.setAlive(alive);
        }
    }
    
    private void initiateServiceRestart(String serviceName, String instanceId) {
        recoveryOrchestrationService.restartService(serviceName, instanceId);
    }
    
    private void handleReadinessFailure(String serviceName, String instanceId, List<String> failedChecks) {
        Map<String, Object> failure = new HashMap<>();
        failure.put("serviceName", serviceName);
        failure.put("instanceId", instanceId);
        failure.put("failedChecks", failedChecks);
        failure.put("timestamp", LocalDateTime.now().toString());
        kafkaTemplate.send("readiness-failures", failure);
    }
    
    private void updateServiceReadiness(String serviceName, String instanceId, boolean ready) {
        String key = serviceName + ":" + instanceId;
        ServiceHealth health = serviceHealthCache.get(key);
        if (health != null) {
            health.setReady(ready);
        }
    }
    
    private boolean shouldRemoveFromLoadBalancer(String serviceName, String instanceId) {
        String key = serviceName + ":" + instanceId;
        AtomicInteger count = failureCounters.get(key);
        return count != null && count.get() >= recoveryThreshold;
    }
    
    private void removeFromLoadBalancer(String serviceName, String instanceId) {
        healthMonitoringService.removeFromLoadBalancer(serviceName, instanceId);
    }
    
    private boolean canAddToLoadBalancer(String serviceName, String instanceId) {
        String key = serviceName + ":" + instanceId;
        return !failureCounters.containsKey(key);
    }
    
    private void addToLoadBalancer(String serviceName, String instanceId) {
        healthMonitoringService.addToLoadBalancer(serviceName, instanceId);
    }
    
    private void handleStartupFailure(String serviceName, String instanceId) {
        Map<String, Object> failure = new HashMap<>();
        failure.put("serviceName", serviceName);
        failure.put("instanceId", instanceId);
        failure.put("failureType", "STARTUP_FAILURE");
        failure.put("timestamp", LocalDateTime.now().toString());
        kafkaTemplate.send("startup-failures", failure);
    }
    
    private void handleSuccessfulStartup(String serviceName, String instanceId) {
        resetServiceFailureCounters(serviceName + ":" + instanceId);
    }
    
    private void createDatabaseHealthAlert(Map<String, Object> dbHealth) {
        kafkaTemplate.send("database-health-alerts", dbHealth);
    }
    
    private void handleConnectionPoolExhaustion(String databaseName, String connectionPool) {
        Map<String, Object> exhaustion = new HashMap<>();
        exhaustion.put("databaseName", databaseName);
        exhaustion.put("connectionPool", connectionPool);
        exhaustion.put("action", "CONNECTION_POOL_EXHAUSTION");
        exhaustion.put("timestamp", LocalDateTime.now().toString());
        kafkaTemplate.send("connection-pool-alerts", exhaustion);
    }
    
    private void analyzeDatabasePerformance(String databaseName, long avgQueryTimeMs) {
        healthMonitoringService.analyzeDatabasePerformance(databaseName, avgQueryTimeMs);
    }
    
    private void createCachePerformanceAlert(String cacheName, double hitRate, String alertType) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("cacheName", cacheName);
        alert.put("hitRate", hitRate);
        alert.put("alertType", alertType);
        alert.put("timestamp", LocalDateTime.now().toString());
        kafkaTemplate.send("cache-performance-alerts", alert);
    }
    
    private void analyzeCacheEvictionPattern(String cacheName, long evictions) {
        healthMonitoringService.analyzeCacheEvictionPattern(cacheName, evictions);
    }
    
    private void handleCacheMemoryPressure(String cacheName, long memoryUsed, long maxMemory) {
        Map<String, Object> pressure = new HashMap<>();
        pressure.put("cacheName", cacheName);
        pressure.put("memoryUsedMB", memoryUsed);
        pressure.put("maxMemoryMB", maxMemory);
        pressure.put("action", "MEMORY_PRESSURE");
        pressure.put("timestamp", LocalDateTime.now().toString());
        kafkaTemplate.send("cache-memory-alerts", pressure);
    }
    
    private void createQueueErrorAlert(String queueName, double errorRate) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("queueName", queueName);
        alert.put("errorRate", errorRate);
        alert.put("alertType", "HIGH_QUEUE_ERROR_RATE");
        alert.put("timestamp", LocalDateTime.now().toString());
        kafkaTemplate.send("queue-error-alerts", alert);
    }
    
    private void handleHighQueueDepth(String queueName, long pendingMessages) {
        healthMonitoringService.handleHighQueueDepth(queueName, pendingMessages);
    }
    
    private void handleHighConsumerLag(String queueName, long consumerLag) {
        healthMonitoringService.handleHighConsumerLag(queueName, consumerLag);
    }
    
    private void handleNoActiveConsumers(String queueName) {
        healthMonitoringService.handleNoActiveConsumers(queueName);
    }
    
    private void handleUnreachableExternalService(String serviceName, String endpoint) {
        dependencyCheckService.handleUnreachableService(serviceName, endpoint);
    }
    
    private boolean hasAlternativeEndpoint(String serviceName) {
        return dependencyCheckService.hasAlternativeEndpoint(serviceName);
    }
    
    private void switchToAlternativeEndpoint(String serviceName) {
        dependencyCheckService.switchToAlternativeEndpoint(serviceName);
    }
    
    private void handleSlowExternalService(String serviceName, String endpoint, long responseTime) {
        healthMonitoringService.handleSlowExternalService(serviceName, endpoint, responseTime);
    }
    
    private void handleExternalServiceError(String serviceName, String endpoint, int statusCode) {
        healthMonitoringService.handleExternalServiceError(serviceName, endpoint, statusCode);
    }
    
    private void createCertificateExpiryWarning(String serviceName, long daysUntilExpiry) {
        Map<String, Object> warning = new HashMap<>();
        warning.put("serviceName", serviceName);
        warning.put("daysUntilExpiry", daysUntilExpiry);
        warning.put("warningType", "CERTIFICATE_EXPIRY");
        warning.put("timestamp", LocalDateTime.now().toString());
        kafkaTemplate.send("certificate-warnings", warning);
    }
    
    private void handleLoadBalancerHealth(String componentId, String status, JsonNode details) {
        healthMonitoringService.handleLoadBalancerHealth(componentId, status, details);
    }
    
    private void handleDnsHealth(String componentId, String status, JsonNode details) {
        healthMonitoringService.handleDnsHealth(componentId, status, details);
    }
    
    private void handleFirewallHealth(String componentId, String status, JsonNode details) {
        healthMonitoringService.handleFirewallHealth(componentId, status, details);
    }
    
    private void handleNetworkHealth(String componentId, String status, JsonNode details) {
        healthMonitoringService.handleNetworkHealth(componentId, status, details);
    }
    
    private void handleStorageHealth(String componentId, String status, JsonNode details) {
        healthMonitoringService.handleStorageHealth(componentId, status, details);
    }
    
    private void handleContainerOrchestrationHealth(String componentId, String status, JsonNode details) {
        healthMonitoringService.handleContainerOrchestrationHealth(componentId, status, details);
    }
    
    private void evaluateInfrastructureImpact(String componentType, String componentId) {
        healthMonitoringService.evaluateInfrastructureImpact(componentType, componentId);
    }
    
    private boolean isCriticalInfrastructure(String componentType) {
        return "LOAD_BALANCER".equals(componentType) || "DATABASE".equals(componentType);
    }
    
    private void triggerInfrastructureFailover(String componentType, String componentId) {
        recoveryOrchestrationService.triggerInfrastructureFailover(componentType, componentId);
    }
    
    private void handleOpenCircuitBreaker(String serviceName, String circuitBreakerName, double failureRate) {
        healthMonitoringService.handleOpenCircuitBreaker(serviceName, circuitBreakerName, failureRate);
    }
    
    private void handleHalfOpenCircuitBreaker(String serviceName, String circuitBreakerName) {
        healthMonitoringService.handleHalfOpenCircuitBreaker(serviceName, circuitBreakerName);
    }
    
    private void handleClosedCircuitBreaker(String serviceName, String circuitBreakerName) {
        healthMonitoringService.handleClosedCircuitBreaker(serviceName, circuitBreakerName);
    }
    
    private void handleForcedCircuitBreakerState(String serviceName, String circuitBreakerName, String state) {
        healthMonitoringService.handleForcedCircuitBreakerState(serviceName, circuitBreakerName, state);
    }
    
    private void recommendCircuitBreakerAdjustment(String serviceName, String circuitBreakerName, double failureRate) {
        healthMonitoringService.recommendCircuitBreakerAdjustment(serviceName, circuitBreakerName, failureRate);
    }
    
    private void handleHighCpuUtilization(String resourceId, double utilization) {
        healthMonitoringService.handleHighCpuUtilization(resourceId, utilization);
    }
    
    private void handleHighMemoryUtilization(String resourceId, double utilization) {
        healthMonitoringService.handleHighMemoryUtilization(resourceId, utilization);
    }
    
    private void handleHighDiskUtilization(String resourceId, double utilization) {
        healthMonitoringService.handleHighDiskUtilization(resourceId, utilization);
    }
    
    private void handleHighNetworkUtilization(String resourceId, double utilization) {
        healthMonitoringService.handleHighNetworkUtilization(resourceId, utilization);
    }
    
    private void handleThreadPoolExhaustion(String resourceId, double utilization) {
        healthMonitoringService.handleThreadPoolExhaustion(resourceId, utilization);
    }
    
    private void triggerResourceScaling(String resourceType, String resourceId) {
        recoveryOrchestrationService.triggerResourceScaling(resourceType, resourceId);
    }
    
    private void handleClusterPartition(String clusterName) {
        healthMonitoringService.handleClusterPartition(clusterName);
    }
    
    private void handleClusterCriticalState(String clusterName, int unhealthyNodes, int totalNodes) {
        healthMonitoringService.handleClusterCriticalState(clusterName, unhealthyNodes, totalNodes);
    }
    
    private void handleClusterDegradedState(String clusterName, int unhealthyNodes, int totalNodes) {
        healthMonitoringService.handleClusterDegradedState(clusterName, unhealthyNodes, totalNodes);
    }
    
    private void analyzeClusterHealth(String clusterName, String clusterState) {
        healthMonitoringService.analyzeClusterHealth(clusterName, clusterState);
    }
    
    private void handleCriticalDegradation(String serviceName, String degradationType, double degradationRate) {
        recoveryOrchestrationService.handleCriticalDegradation(serviceName, degradationType, degradationRate);
    }
    
    private void handleHighDegradation(String serviceName, String degradationType, double degradationRate) {
        healthMonitoringService.handleHighDegradation(serviceName, degradationType, degradationRate);
    }
    
    private void addressRootCause(String serviceName, String rootCause) {
        recoveryOrchestrationService.addressRootCause(serviceName, rootCause);
    }
    
    private void predictFutureDegradation(String serviceName, String degradationType, double degradationRate) {
        healthMonitoringService.predictFutureDegradation(serviceName, degradationType, degradationRate);
    }
    
    private void resetServiceFailureCounters(String key) {
        failureCounters.remove(key);
    }
    
    private void updateServiceHealthStatus(String serviceName, HealthStatus status) {
        serviceHealthCache.values().stream()
            .filter(health -> health.getServiceName().equals(serviceName))
            .forEach(health -> health.setStatus(status));
    }
    
    private void recordSuccessfulRecoveryAction(String serviceName, String recoveryAction) {
        healthMonitoringService.recordSuccessfulRecoveryAction(serviceName, recoveryAction);
    }
    
    private boolean shouldPerformHealthCheck(ServiceHealth health) {
        LocalDateTime lastCheck = lastHealthCheckTime.get(health.getServiceName() + ":" + health.getInstanceId());
        if (lastCheck == null) {
            return true;
        }
        return ChronoUnit.SECONDS.between(lastCheck, LocalDateTime.now()) >= healthCheckIntervalSeconds;
    }
    
    private void performHealthCheck(String serviceName, String instanceId) {
        healthMonitoringService.performHealthCheck(serviceName, instanceId);
        lastHealthCheckTime.put(serviceName + ":" + instanceId, LocalDateTime.now());
    }
    
    private void performDeepHealthCheck(String serviceName, String instanceId) {
        healthMonitoringService.performDeepHealthCheck(serviceName, instanceId);
    }
    
    private void createSystemHealthAlert(String severity, double score) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "SYSTEM_HEALTH");
        alert.put("severity", severity);
        alert.put("healthScore", score);
        alert.put("timestamp", LocalDateTime.now().toString());
        kafkaTemplate.send("system-health-alerts", alert);
    }
    
    private void createPredictiveHealthAlert(ServiceHealth health, PredictedHealth prediction) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "PREDICTIVE_HEALTH");
        alert.put("serviceName", health.getServiceName());
        alert.put("instanceId", health.getInstanceId());
        alert.put("currentStatus", health.getStatus());
        alert.put("predictedStatus", prediction.getPredictedStatus());
        alert.put("confidence", prediction.getConfidence());
        alert.put("trend", prediction.getTrend());
        alert.put("timestamp", LocalDateTime.now().toString());
        kafkaTemplate.send("predictive-health-alerts", alert);
    }
}