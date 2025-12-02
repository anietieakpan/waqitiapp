package com.waqiti.monitoring.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.monitoring.entity.*;
import com.waqiti.monitoring.repository.*;
import com.waqiti.monitoring.service.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class ServiceDependencyTrackingConsumer extends BaseKafkaConsumer {

    private static final Logger logger = LoggerFactory.getLogger(ServiceDependencyTrackingConsumer.class);
    
    @Value("${waqiti.monitoring.dependency.analysis-window-minutes:10}")
    private int analysisWindowMinutes;
    
    @Value("${waqiti.monitoring.dependency.failure-threshold:5}")
    private int failureThreshold;
    
    @Value("${waqiti.monitoring.dependency.cascade-threshold:3}")
    private int cascadeThreshold;
    
    @Value("${waqiti.monitoring.dependency.critical-path-depth:5}")
    private int criticalPathDepth;
    
    @Value("${waqiti.monitoring.dependency.health-check-interval:60}")
    private int healthCheckIntervalSeconds;

    private final ServiceDependencyRepository serviceDependencyRepository;
    private final DependencyHealthRepository dependencyHealthRepository;
    private final DependencyFailureRepository dependencyFailureRepository;
    private final ServiceMapRepository serviceMapRepository;
    private final DependencyAlertRepository dependencyAlertRepository;
    private final CriticalPathRepository criticalPathRepository;
    private final CircuitBreakerStateRepository circuitBreakerStateRepository;
    private final AlertingService alertingService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(4);
    private final Map<String, ServiceDependencyNode> dependencyGraph = new ConcurrentHashMap<>();
    private final Map<String, List<DependencyCall>> callBuffer = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastHealthCheck = new ConcurrentHashMap<>();
    private final Map<String, Integer> consecutiveFailures = new ConcurrentHashMap<>();

    private Counter processedEventsCounter;
    private Counter processedDependencyDataCounter;
    private Counter processedDependencyHealthCounter;
    private Counter processedDependencyFailureCounter;
    private Counter processedServiceMapCounter;
    private Counter processedDependencyAlertCounter;
    private Counter processedCriticalPathCounter;
    private Counter processedCircuitBreakerCounter;
    private Counter processedDependencyTimeoutCounter;
    private Counter processedDependencyRetryCounter;
    private Counter processedDependencyRecoveryCounter;
    private Counter processedCascadeFailureCounter;
    private Counter processedDependencyOptimizationCounter;
    private Counter processedServiceIsolationCounter;
    private Counter processedDependencyDiscoveryCounter;
    private Timer dependencyProcessingTimer;
    private Timer dependencyAnalysisTimer;
    
    private Gauge activeDependenciesGauge;
    private Gauge failedDependenciesGauge;
    private Gauge criticalPathLengthGauge;

    public ServiceDependencyTrackingConsumer(
            ServiceDependencyRepository serviceDependencyRepository,
            DependencyHealthRepository dependencyHealthRepository,
            DependencyFailureRepository dependencyFailureRepository,
            ServiceMapRepository serviceMapRepository,
            DependencyAlertRepository dependencyAlertRepository,
            CriticalPathRepository criticalPathRepository,
            CircuitBreakerStateRepository circuitBreakerStateRepository,
            AlertingService alertingService,
            MetricsService metricsService,
            NotificationService notificationService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.serviceDependencyRepository = serviceDependencyRepository;
        this.dependencyHealthRepository = dependencyHealthRepository;
        this.dependencyFailureRepository = dependencyFailureRepository;
        this.serviceMapRepository = serviceMapRepository;
        this.dependencyAlertRepository = dependencyAlertRepository;
        this.criticalPathRepository = criticalPathRepository;
        this.circuitBreakerStateRepository = circuitBreakerStateRepository;
        this.alertingService = alertingService;
        this.metricsService = metricsService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        this.processedEventsCounter = Counter.builder("dependency_tracking_events_processed")
                .description("Number of service dependency tracking events processed")
                .register(meterRegistry);
        
        this.processedDependencyDataCounter = Counter.builder("dependency_data_processed")
                .description("Number of dependency data events processed")
                .register(meterRegistry);
        
        this.processedDependencyHealthCounter = Counter.builder("dependency_health_processed")
                .description("Number of dependency health events processed")
                .register(meterRegistry);
        
        this.processedDependencyFailureCounter = Counter.builder("dependency_failure_processed")
                .description("Number of dependency failure events processed")
                .register(meterRegistry);
        
        this.processedServiceMapCounter = Counter.builder("service_map_processed")
                .description("Number of service map events processed")
                .register(meterRegistry);
        
        this.processedDependencyAlertCounter = Counter.builder("dependency_alert_processed")
                .description("Number of dependency alert events processed")
                .register(meterRegistry);
        
        this.processedCriticalPathCounter = Counter.builder("critical_path_processed")
                .description("Number of critical path events processed")
                .register(meterRegistry);
        
        this.processedCircuitBreakerCounter = Counter.builder("circuit_breaker_processed")
                .description("Number of circuit breaker events processed")
                .register(meterRegistry);
        
        this.processedDependencyTimeoutCounter = Counter.builder("dependency_timeout_processed")
                .description("Number of dependency timeout events processed")
                .register(meterRegistry);
        
        this.processedDependencyRetryCounter = Counter.builder("dependency_retry_processed")
                .description("Number of dependency retry events processed")
                .register(meterRegistry);
        
        this.processedDependencyRecoveryCounter = Counter.builder("dependency_recovery_processed")
                .description("Number of dependency recovery events processed")
                .register(meterRegistry);
        
        this.processedCascadeFailureCounter = Counter.builder("cascade_failure_processed")
                .description("Number of cascade failure events processed")
                .register(meterRegistry);
        
        this.processedDependencyOptimizationCounter = Counter.builder("dependency_optimization_processed")
                .description("Number of dependency optimization events processed")
                .register(meterRegistry);
        
        this.processedServiceIsolationCounter = Counter.builder("service_isolation_processed")
                .description("Number of service isolation events processed")
                .register(meterRegistry);
        
        this.processedDependencyDiscoveryCounter = Counter.builder("dependency_discovery_processed")
                .description("Number of dependency discovery events processed")
                .register(meterRegistry);
        
        this.dependencyProcessingTimer = Timer.builder("dependency_processing_duration")
                .description("Time taken to process dependency events")
                .register(meterRegistry);
        
        this.dependencyAnalysisTimer = Timer.builder("dependency_analysis_duration")
                .description("Time taken to perform dependency analysis")
                .register(meterRegistry);
        
        this.activeDependenciesGauge = Gauge.builder("active_dependencies_count", this, ServiceDependencyTrackingConsumer::getActiveDependenciesCount)
                .description("Number of active service dependencies")
                .register(meterRegistry);
        
        this.failedDependenciesGauge = Gauge.builder("failed_dependencies_count", this, ServiceDependencyTrackingConsumer::getFailedDependenciesCount)
                .description("Number of failed service dependencies")
                .register(meterRegistry);
        
        this.criticalPathLengthGauge = Gauge.builder("critical_path_length", this, ServiceDependencyTrackingConsumer::getCriticalPathLength)
                .description("Length of the critical dependency path")
                .register(meterRegistry);

        scheduledExecutor.scheduleAtFixedRate(this::performDependencyAnalysis, 
                analysisWindowMinutes, analysisWindowMinutes, TimeUnit.MINUTES);
        
        scheduledExecutor.scheduleAtFixedRate(this::performHealthChecks, 
                healthCheckIntervalSeconds, healthCheckIntervalSeconds, TimeUnit.SECONDS);
        
        scheduledExecutor.scheduleAtFixedRate(this::analyzeCriticalPaths, 
                15, 15, TimeUnit.MINUTES);
        
        scheduledExecutor.scheduleAtFixedRate(this::cleanupOldData, 
                24, 24, TimeUnit.HOURS);
    }

    @PreDestroy
    public void cleanup() {
        scheduledExecutor.shutdown();
        try {
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @KafkaListener(topics = "service-dependency-tracking", groupId = "service-dependency-tracking-group", 
                   containerFactory = "kafkaListenerContainerFactory")
    @CircuitBreaker(name = "service-dependency-tracking-consumer")
    @Retry(name = "service-dependency-tracking-consumer")
    @Transactional
    public void handleServiceDependencyTrackingEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "service-dependency-tracking");

        try {
            logger.info("Processing service dependency tracking event: partition={}, offset={}", 
                    record.partition(), record.offset());

            JsonNode eventData = objectMapper.readTree(record.value());
            String eventType = eventData.path("eventType").asText();

            switch (eventType) {
                case "DEPENDENCY_DATA":
                    processDependencyData(eventData);
                    processedDependencyDataCounter.increment();
                    break;
                case "DEPENDENCY_HEALTH":
                    processDependencyHealth(eventData);
                    processedDependencyHealthCounter.increment();
                    break;
                case "DEPENDENCY_FAILURE":
                    processDependencyFailure(eventData);
                    processedDependencyFailureCounter.increment();
                    break;
                case "SERVICE_MAP":
                    processServiceMap(eventData);
                    processedServiceMapCounter.increment();
                    break;
                case "DEPENDENCY_ALERT":
                    processDependencyAlert(eventData);
                    processedDependencyAlertCounter.increment();
                    break;
                case "CRITICAL_PATH":
                    processCriticalPath(eventData);
                    processedCriticalPathCounter.increment();
                    break;
                case "CIRCUIT_BREAKER":
                    processCircuitBreaker(eventData);
                    processedCircuitBreakerCounter.increment();
                    break;
                case "DEPENDENCY_TIMEOUT":
                    processDependencyTimeout(eventData);
                    processedDependencyTimeoutCounter.increment();
                    break;
                case "DEPENDENCY_RETRY":
                    processDependencyRetry(eventData);
                    processedDependencyRetryCounter.increment();
                    break;
                case "DEPENDENCY_RECOVERY":
                    processDependencyRecovery(eventData);
                    processedDependencyRecoveryCounter.increment();
                    break;
                case "CASCADE_FAILURE":
                    processCascadeFailure(eventData);
                    processedCascadeFailureCounter.increment();
                    break;
                case "DEPENDENCY_OPTIMIZATION":
                    processDependencyOptimization(eventData);
                    processedDependencyOptimizationCounter.increment();
                    break;
                case "SERVICE_ISOLATION":
                    processServiceIsolation(eventData);
                    processedServiceIsolationCounter.increment();
                    break;
                case "DEPENDENCY_DISCOVERY":
                    processDependencyDiscovery(eventData);
                    processedDependencyDiscoveryCounter.increment();
                    break;
                default:
                    logger.warn("Unknown service dependency tracking event type: {}", eventType);
            }

            processedEventsCounter.increment();
            ack.acknowledge();

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse service dependency tracking event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } catch (DataAccessException e) {
            logger.error("Database error processing service dependency tracking event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } catch (Exception e) {
            logger.error("Unexpected error processing service dependency tracking event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            sample.stop(dependencyProcessingTimer);
            MDC.clear();
        }
    }

    private void processDependencyData(JsonNode eventData) {
        try {
            ServiceDependency dependency = new ServiceDependency();
            dependency.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            dependency.setSourceService(eventData.path("sourceService").asText());
            dependency.setTargetService(eventData.path("targetService").asText());
            dependency.setDependencyType(eventData.path("dependencyType").asText());
            dependency.setCallCount(eventData.path("callCount").asLong());
            dependency.setSuccessCount(eventData.path("successCount").asLong());
            dependency.setFailureCount(eventData.path("failureCount").asLong());
            dependency.setAvgLatencyMs(eventData.path("avgLatencyMs").asDouble());
            dependency.setMaxLatencyMs(eventData.path("maxLatencyMs").asDouble());
            dependency.setMinLatencyMs(eventData.path("minLatencyMs").asDouble());
            dependency.setTimeoutCount(eventData.path("timeoutCount").asLong());
            dependency.setRetryCount(eventData.path("retryCount").asLong());
            dependency.setCircuitBreakerState(eventData.path("circuitBreakerState").asText());
            
            JsonNode metadataNode = eventData.path("metadata");
            if (!metadataNode.isMissingNode()) {
                dependency.setMetadata(metadataNode.toString());
            }
            
            serviceDependencyRepository.save(dependency);
            
            String dependencyKey = dependency.getSourceService() + "->" + dependency.getTargetService();
            updateDependencyGraph(dependency);
            updateCallBuffer(dependencyKey, new DependencyCall(dependency.getTimestamp(), 
                    dependency.getCallCount(), dependency.getSuccessCount(), dependency.getFailureCount()));
            
            metricsService.recordDependencyMetric(dependency.getSourceService(), dependency.getTargetService(), dependency);
            
            if (shouldTriggerRealTimeAnalysis(dependency)) {
                performRealTimeDependencyAnalysis(dependency);
            }
            
            logger.debug("Processed dependency data: {} -> {}, calls: {}, success rate: {}%", 
                    dependency.getSourceService(), dependency.getTargetService(), dependency.getCallCount(),
                    calculateSuccessRate(dependency.getSuccessCount(), dependency.getCallCount()));
            
        } catch (Exception e) {
            logger.error("Error processing dependency data: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processDependencyHealth(JsonNode eventData) {
        try {
            DependencyHealth health = new DependencyHealth();
            health.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            health.setSourceService(eventData.path("sourceService").asText());
            health.setTargetService(eventData.path("targetService").asText());
            health.setHealthStatus(eventData.path("healthStatus").asText());
            health.setResponseTimeMs(eventData.path("responseTimeMs").asDouble());
            health.setSuccessRate(eventData.path("successRate").asDouble());
            health.setThroughputRps(eventData.path("throughputRps").asDouble());
            health.setErrorRate(eventData.path("errorRate").asDouble());
            health.setAvailability(eventData.path("availability").asDouble());
            health.setLastCheckTime(parseTimestamp(eventData.path("lastCheckTime").asText()));
            health.setConsecutiveFailures(eventData.path("consecutiveFailures").asInt());
            health.setHealthScore(eventData.path("healthScore").asDouble());
            
            JsonNode healthDetailsNode = eventData.path("healthDetails");
            if (!healthDetailsNode.isMissingNode()) {
                health.setHealthDetails(healthDetailsNode.toString());
            }
            
            dependencyHealthRepository.save(health);
            
            metricsService.recordDependencyHealthMetrics(health.getSourceService(), health.getTargetService(), health);
            
            String dependencyKey = health.getSourceService() + "->" + health.getTargetService();
            lastHealthCheck.put(dependencyKey, health.getLastCheckTime());
            consecutiveFailures.put(dependencyKey, health.getConsecutiveFailures());
            
            if ("UNHEALTHY".equals(health.getHealthStatus()) || health.getHealthScore() < 0.5) {
                generateHealthAlert(health);
            }
            
            logger.debug("Processed dependency health: {} -> {}, status: {}, score: {}", 
                    health.getSourceService(), health.getTargetService(), 
                    health.getHealthStatus(), health.getHealthScore());
            
        } catch (Exception e) {
            logger.error("Error processing dependency health: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processDependencyFailure(JsonNode eventData) {
        try {
            DependencyFailure failure = new DependencyFailure();
            failure.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            failure.setSourceService(eventData.path("sourceService").asText());
            failure.setTargetService(eventData.path("targetService").asText());
            failure.setFailureType(eventData.path("failureType").asText());
            failure.setErrorCode(eventData.path("errorCode").asText());
            failure.setErrorMessage(eventData.path("errorMessage").asText());
            failure.setDurationMs(eventData.path("durationMs").asLong());
            failure.setImpactLevel(eventData.path("impactLevel").asText());
            failure.setRootCause(eventData.path("rootCause").asText());
            failure.setResolved(eventData.path("resolved").asBoolean());
            
            if (eventData.has("resolvedAt")) {
                failure.setResolvedAt(parseTimestamp(eventData.path("resolvedAt").asText()));
            }
            
            JsonNode stackTraceNode = eventData.path("stackTrace");
            if (!stackTraceNode.isMissingNode()) {
                failure.setStackTrace(stackTraceNode.toString());
            }
            
            dependencyFailureRepository.save(failure);
            
            String dependencyKey = failure.getSourceService() + "->" + failure.getTargetService();
            int failures = consecutiveFailures.getOrDefault(dependencyKey, 0) + 1;
            consecutiveFailures.put(dependencyKey, failures);
            
            metricsService.recordDependencyFailure(failure.getSourceService(), failure.getTargetService(), failure);
            
            if (failures >= failureThreshold) {
                generateFailureAlert(failure, failures);
            }
            
            if ("HIGH".equals(failure.getImpactLevel()) || "CRITICAL".equals(failure.getImpactLevel())) {
                analyzeCascadeFailureRisk(failure);
            }
            
            logger.warn("Processed dependency failure: {} -> {}, type: {}, impact: {}", 
                    failure.getSourceService(), failure.getTargetService(), 
                    failure.getFailureType(), failure.getImpactLevel());
            
        } catch (Exception e) {
            logger.error("Error processing dependency failure: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processServiceMap(JsonNode eventData) {
        try {
            ServiceMap serviceMap = new ServiceMap();
            serviceMap.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            serviceMap.setServiceName(eventData.path("serviceName").asText());
            serviceMap.setServiceType(eventData.path("serviceType").asText());
            serviceMap.setServiceVersion(eventData.path("serviceVersion").asText());
            serviceMap.setEndpoints(eventData.path("endpoints").asText());
            serviceMap.setUpstreamServices(eventData.path("upstreamServices").asText());
            serviceMap.setDownstreamServices(eventData.path("downstreamServices").asText());
            serviceMap.setDependencyCount(eventData.path("dependencyCount").asInt());
            serviceMap.setDependentCount(eventData.path("dependentCount").asInt());
            serviceMap.setCriticalityScore(eventData.path("criticalityScore").asDouble());
            serviceMap.setClusterName(eventData.path("clusterName").asText());
            serviceMap.setNamespace(eventData.path("namespace").asText());
            
            JsonNode topologyNode = eventData.path("topology");
            if (!topologyNode.isMissingNode()) {
                serviceMap.setTopology(topologyNode.toString());
            }
            
            serviceMapRepository.save(serviceMap);
            
            updateServiceMapGraph(serviceMap);
            
            metricsService.recordServiceMapMetrics(serviceMap.getServiceName(), serviceMap);
            
            if (serviceMap.getCriticalityScore() > 0.8) {
                identifyCriticalService(serviceMap);
            }
            
            logger.debug("Processed service map for service: {}, dependencies: {}, dependents: {}", 
                    serviceMap.getServiceName(), serviceMap.getDependencyCount(), serviceMap.getDependentCount());
            
        } catch (Exception e) {
            logger.error("Error processing service map: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processDependencyAlert(JsonNode eventData) {
        try {
            DependencyAlert alert = new DependencyAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setSourceService(eventData.path("sourceService").asText());
            alert.setTargetService(eventData.path("targetService").asText());
            alert.setAlertType(eventData.path("alertType").asText());
            alert.setSeverity(eventData.path("severity").asText());
            alert.setDescription(eventData.path("description").asText());
            alert.setImpactAnalysis(eventData.path("impactAnalysis").asText());
            alert.setRecommendedAction(eventData.path("recommendedAction").asText());
            alert.setResolved(eventData.path("resolved").asBoolean());
            
            if (eventData.has("resolvedAt")) {
                alert.setResolvedAt(parseTimestamp(eventData.path("resolvedAt").asText()));
            }
            
            dependencyAlertRepository.save(alert);
            
            if (!alert.isResolved() && ("HIGH".equals(alert.getSeverity()) || "CRITICAL".equals(alert.getSeverity()))) {
                alertingService.sendAlert("DEPENDENCY_ALERT", alert.getDescription(), 
                        Map.of("sourceService", alert.getSourceService(), "targetService", alert.getTargetService(), 
                               "severity", alert.getSeverity()));
            }
            
            logger.info("Processed dependency alert: {} -> {}, type: {}, severity: {}", 
                    alert.getSourceService(), alert.getTargetService(), alert.getAlertType(), alert.getSeverity());
            
        } catch (Exception e) {
            logger.error("Error processing dependency alert: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processCriticalPath(JsonNode eventData) {
        try {
            CriticalPath criticalPath = new CriticalPath();
            criticalPath.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            criticalPath.setPathId(eventData.path("pathId").asText());
            criticalPath.setStartService(eventData.path("startService").asText());
            criticalPath.setEndService(eventData.path("endService").asText());
            criticalPath.setPathLength(eventData.path("pathLength").asInt());
            criticalPath.setTotalLatencyMs(eventData.path("totalLatencyMs").asDouble());
            criticalPath.setFailureRisk(eventData.path("failureRisk").asDouble());
            criticalPath.setBottleneckService(eventData.path("bottleneckService").asText());
            criticalPath.setOptimizationOpportunity(eventData.path("optimizationOpportunity").asDouble());
            
            JsonNode pathServicesNode = eventData.path("pathServices");
            if (!pathServicesNode.isMissingNode()) {
                criticalPath.setPathServices(pathServicesNode.toString());
            }
            
            JsonNode pathMetricsNode = eventData.path("pathMetrics");
            if (!pathMetricsNode.isMissingNode()) {
                criticalPath.setPathMetrics(pathMetricsNode.toString());
            }
            
            criticalPathRepository.save(criticalPath);
            
            metricsService.recordCriticalPathMetrics(criticalPath.getPathId(), criticalPath);
            
            if (criticalPath.getFailureRisk() > 0.7) {
                generateCriticalPathAlert(criticalPath);
            }
            
            logger.debug("Processed critical path: {} -> {}, length: {}, risk: {}", 
                    criticalPath.getStartService(), criticalPath.getEndService(), 
                    criticalPath.getPathLength(), criticalPath.getFailureRisk());
            
        } catch (Exception e) {
            logger.error("Error processing critical path: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processCircuitBreaker(JsonNode eventData) {
        try {
            CircuitBreakerState cbState = new CircuitBreakerState();
            cbState.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            cbState.setSourceService(eventData.path("sourceService").asText());
            cbState.setTargetService(eventData.path("targetService").asText());
            cbState.setState(eventData.path("state").asText());
            cbState.setPreviousState(eventData.path("previousState").asText());
            cbState.setFailureCount(eventData.path("failureCount").asInt());
            cbState.setFailureThreshold(eventData.path("failureThreshold").asInt());
            cbState.setSuccessThreshold(eventData.path("successThreshold").asInt());
            cbState.setTimeoutMs(eventData.path("timeoutMs").asLong());
            cbState.setRetryCount(eventData.path("retryCount").asInt());
            cbState.setLastFailureTime(parseTimestamp(eventData.path("lastFailureTime").asText()));
            cbState.setNextRetryTime(parseTimestamp(eventData.path("nextRetryTime").asText()));
            
            JsonNode configNode = eventData.path("configuration");
            if (!configNode.isMissingNode()) {
                cbState.setConfiguration(configNode.toString());
            }
            
            circuitBreakerStateRepository.save(cbState);
            
            metricsService.recordCircuitBreakerMetrics(cbState.getSourceService(), cbState.getTargetService(), cbState);
            
            if ("OPEN".equals(cbState.getState()) && !"OPEN".equals(cbState.getPreviousState())) {
                generateCircuitBreakerAlert(cbState);
            }
            
            updateDependencyGraphWithCircuitBreaker(cbState);
            
            logger.info("Processed circuit breaker state: {} -> {}, state: {} -> {}", 
                    cbState.getSourceService(), cbState.getTargetService(), 
                    cbState.getPreviousState(), cbState.getState());
            
        } catch (Exception e) {
            logger.error("Error processing circuit breaker: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processDependencyTimeout(JsonNode eventData) {
        try {
            String sourceService = eventData.path("sourceService").asText();
            String targetService = eventData.path("targetService").asText();
            long timeoutMs = eventData.path("timeoutMs").asLong();
            long actualDurationMs = eventData.path("actualDurationMs").asLong();
            String operation = eventData.path("operation").asText();
            String cause = eventData.path("cause").asText();
            
            DependencyFailure failure = new DependencyFailure();
            failure.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            failure.setSourceService(sourceService);
            failure.setTargetService(targetService);
            failure.setFailureType("TIMEOUT");
            failure.setErrorCode("TIMEOUT");
            failure.setErrorMessage(String.format("Timeout after %dms (expected: %dms) for operation: %s - %s", 
                    actualDurationMs, timeoutMs, operation, cause));
            failure.setDurationMs(actualDurationMs);
            failure.setImpactLevel(actualDurationMs > timeoutMs * 2 ? "HIGH" : "MEDIUM");
            failure.setRootCause(cause);
            failure.setResolved(false);
            
            dependencyFailureRepository.save(failure);
            
            String dependencyKey = sourceService + "->" + targetService;
            int failures = consecutiveFailures.getOrDefault(dependencyKey, 0) + 1;
            consecutiveFailures.put(dependencyKey, failures);
            
            alertingService.sendAlert("DEPENDENCY_TIMEOUT", failure.getErrorMessage(), 
                    Map.of("sourceService", sourceService, "targetService", targetService, 
                           "timeoutMs", String.valueOf(timeoutMs), "actualMs", String.valueOf(actualDurationMs)));
            
            analyzeTimeoutPattern(sourceService, targetService, timeoutMs, actualDurationMs);
            
            logger.warn("Processed dependency timeout: {} -> {}, timeout: {}ms, actual: {}ms", 
                    sourceService, targetService, timeoutMs, actualDurationMs);
            
        } catch (Exception e) {
            logger.error("Error processing dependency timeout: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processDependencyRetry(JsonNode eventData) {
        try {
            String sourceService = eventData.path("sourceService").asText();
            String targetService = eventData.path("targetService").asText();
            int attemptNumber = eventData.path("attemptNumber").asInt();
            int maxAttempts = eventData.path("maxAttempts").asInt();
            String retryReason = eventData.path("retryReason").asText();
            long delayMs = eventData.path("delayMs").asLong();
            boolean finalAttempt = eventData.path("finalAttempt").asBoolean();
            boolean successful = eventData.path("successful").asBoolean();
            
            metricsService.recordDependencyRetry(sourceService, targetService, attemptNumber, maxAttempts, successful);
            
            if (finalAttempt && !successful) {
                DependencyFailure failure = new DependencyFailure();
                failure.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
                failure.setSourceService(sourceService);
                failure.setTargetService(targetService);
                failure.setFailureType("RETRY_EXHAUSTED");
                failure.setErrorCode("MAX_RETRIES_EXCEEDED");
                failure.setErrorMessage(String.format("All %d retry attempts failed - %s", maxAttempts, retryReason));
                failure.setImpactLevel("HIGH");
                failure.setRootCause(retryReason);
                failure.setResolved(false);
                
                dependencyFailureRepository.save(failure);
                
                alertingService.sendAlert("DEPENDENCY_RETRY_EXHAUSTED", failure.getErrorMessage(), 
                        Map.of("sourceService", sourceService, "targetService", targetService, 
                               "maxAttempts", String.valueOf(maxAttempts)));
            }
            
            analyzeRetryPattern(sourceService, targetService, attemptNumber, maxAttempts, retryReason);
            
            logger.debug("Processed dependency retry: {} -> {}, attempt: {}/{}, successful: {}", 
                    sourceService, targetService, attemptNumber, maxAttempts, successful);
            
        } catch (Exception e) {
            logger.error("Error processing dependency retry: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processDependencyRecovery(JsonNode eventData) {
        try {
            String sourceService = eventData.path("sourceService").asText();
            String targetService = eventData.path("targetService").asText();
            String recoveryType = eventData.path("recoveryType").asText();
            long recoveryDurationMs = eventData.path("recoveryDurationMs").asLong();
            double previousSuccessRate = eventData.path("previousSuccessRate").asDouble();
            double currentSuccessRate = eventData.path("currentSuccessRate").asDouble();
            
            String dependencyKey = sourceService + "->" + targetService;
            consecutiveFailures.put(dependencyKey, 0);
            
            List<DependencyAlert> unresolvedAlerts = dependencyAlertRepository
                    .findUnresolvedBySourceAndTarget(sourceService, targetService);
            
            for (DependencyAlert alert : unresolvedAlerts) {
                if (shouldResolveAlert(alert, currentSuccessRate)) {
                    alert.setResolved(true);
                    alert.setResolvedAt(LocalDateTime.now());
                    dependencyAlertRepository.save(alert);
                }
            }
            
            metricsService.recordDependencyRecovery(sourceService, targetService, recoveryDurationMs);
            
            double improvementPercent = ((currentSuccessRate - previousSuccessRate) / previousSuccessRate) * 100;
            
            if (improvementPercent > 10) {
                notificationService.sendRecoveryNotification("DEPENDENCY_RECOVERY", 
                        String.format("Dependency recovery: %s -> %s (%s) - %.1f%% improvement in %dms", 
                                sourceService, targetService, recoveryType, improvementPercent, recoveryDurationMs));
            }
            
            updateDependencyGraphWithRecovery(sourceService, targetService, currentSuccessRate);
            
            logger.info("Processed dependency recovery: {} -> {}, type: {}, improvement: {}%", 
                    sourceService, targetService, recoveryType, improvementPercent);
            
        } catch (Exception e) {
            logger.error("Error processing dependency recovery: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processCascadeFailure(JsonNode eventData) {
        try {
            String originService = eventData.path("originService").asText();
            String failurePattern = eventData.path("failurePattern").asText();
            int affectedServicesCount = eventData.path("affectedServicesCount").asInt();
            long cascadeDurationMs = eventData.path("cascadeDurationMs").asLong();
            String impactLevel = eventData.path("impactLevel").asText();
            
            JsonNode affectedServicesNode = eventData.path("affectedServices");
            String affectedServices = affectedServicesNode.isMissingNode() ? "" : affectedServicesNode.toString();
            
            DependencyAlert alert = new DependencyAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setSourceService(originService);
            alert.setTargetService("MULTIPLE");
            alert.setAlertType("CASCADE_FAILURE");
            alert.setSeverity(affectedServicesCount > cascadeThreshold ? "CRITICAL" : "HIGH");
            alert.setDescription(String.format("Cascade failure from %s: %s pattern affecting %d services in %dms", 
                    originService, failurePattern, affectedServicesCount, cascadeDurationMs));
            alert.setImpactAnalysis(String.format("Impact: %s, Affected services: %s", impactLevel, affectedServices));
            alert.setRecommendedAction("Isolate origin service and implement circuit breakers");
            alert.setResolved(false);
            
            dependencyAlertRepository.save(alert);
            
            alertingService.sendCriticalAlert("CASCADE_FAILURE", alert.getDescription(), 
                    Map.of("originService", originService, "affectedCount", String.valueOf(affectedServicesCount), 
                           "pattern", failurePattern, "impactLevel", impactLevel));
            
            notificationService.sendPagerDutyAlert("CASCADE_FAILURE", alert.getDescription());
            
            initiateFailureContainment(originService, affectedServices, failurePattern);
            
            logger.error("Processed cascade failure from {}: {} pattern, {} services affected in {}ms", 
                    originService, failurePattern, affectedServicesCount, cascadeDurationMs);
            
        } catch (Exception e) {
            logger.error("Error processing cascade failure: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processDependencyOptimization(JsonNode eventData) {
        try {
            String sourceService = eventData.path("sourceService").asText();
            String targetService = eventData.path("targetService").asText();
            String optimizationType = eventData.path("optimizationType").asText();
            double currentLatencyMs = eventData.path("currentLatencyMs").asDouble();
            double optimizedLatencyMs = eventData.path("optimizedLatencyMs").asDouble();
            double potentialSavings = eventData.path("potentialSavings").asDouble();
            
            JsonNode recommendationsNode = eventData.path("recommendations");
            String recommendations = recommendationsNode.isMissingNode() ? "" : recommendationsNode.toString();
            
            metricsService.recordDependencyOptimization(sourceService, targetService, potentialSavings);
            
            double improvementPercent = ((currentLatencyMs - optimizedLatencyMs) / currentLatencyMs) * 100;
            
            if (improvementPercent > 15) {
                notificationService.sendOptimizationRecommendation("DEPENDENCY_OPTIMIZATION", 
                        String.format("Dependency optimization opportunity: %s -> %s (%s) - %.1f%% latency reduction", 
                                sourceService, targetService, optimizationType, improvementPercent),
                        recommendations);
            }
            
            logger.info("Processed dependency optimization: {} -> {}, type: {}, improvement: {}%", 
                    sourceService, targetService, optimizationType, improvementPercent);
            
        } catch (Exception e) {
            logger.error("Error processing dependency optimization: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processServiceIsolation(JsonNode eventData) {
        try {
            String isolatedService = eventData.path("isolatedService").asText();
            String isolationReason = eventData.path("isolationReason").asText();
            String isolationType = eventData.path("isolationType").asText();
            long isolationDurationMs = eventData.path("isolationDurationMs").asLong();
            
            JsonNode affectedDependenciesNode = eventData.path("affectedDependencies");
            String affectedDependencies = affectedDependenciesNode.isMissingNode() ? "" : affectedDependenciesNode.toString();
            
            DependencyAlert alert = new DependencyAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setSourceService(isolatedService);
            alert.setTargetService("ISOLATED");
            alert.setAlertType("SERVICE_ISOLATION");
            alert.setSeverity("HIGH");
            alert.setDescription(String.format("Service %s isolated (%s): %s for %dms", 
                    isolatedService, isolationType, isolationReason, isolationDurationMs));
            alert.setImpactAnalysis(String.format("Affected dependencies: %s", affectedDependencies));
            alert.setRecommendedAction("Monitor service health and plan gradual re-integration");
            alert.setResolved(false);
            
            dependencyAlertRepository.save(alert);
            
            alertingService.sendAlert("SERVICE_ISOLATION", alert.getDescription(), 
                    Map.of("isolatedService", isolatedService, "isolationType", isolationType, 
                           "reason", isolationReason));
            
            updateDependencyGraphWithIsolation(isolatedService, isolationType);
            
            logger.warn("Processed service isolation: {} ({}): {} for {}ms", 
                    isolatedService, isolationType, isolationReason, isolationDurationMs);
            
        } catch (Exception e) {
            logger.error("Error processing service isolation: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processDependencyDiscovery(JsonNode eventData) {
        try {
            String sourceService = eventData.path("sourceService").asText();
            String targetService = eventData.path("targetService").asText();
            String discoveryMethod = eventData.path("discoveryMethod").asText();
            String dependencyType = eventData.path("dependencyType").asText();
            double confidence = eventData.path("confidence").asDouble();
            
            JsonNode metadataNode = eventData.path("metadata");
            String metadata = metadataNode.isMissingNode() ? "" : metadataNode.toString();
            
            if (confidence > 0.8) {
                ServiceDependency newDependency = new ServiceDependency();
                newDependency.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
                newDependency.setSourceService(sourceService);
                newDependency.setTargetService(targetService);
                newDependency.setDependencyType(dependencyType);
                newDependency.setCallCount(0L);
                newDependency.setSuccessCount(0L);
                newDependency.setFailureCount(0L);
                newDependency.setAvgLatencyMs(0.0);
                newDependency.setMetadata(String.format("Discovered via %s (confidence: %.2f) - %s", 
                        discoveryMethod, confidence, metadata));
                
                serviceDependencyRepository.save(newDependency);
                
                updateDependencyGraph(newDependency);
                
                metricsService.recordDependencyDiscovery(sourceService, targetService, discoveryMethod, confidence);
                
                logger.info("Discovered new dependency: {} -> {} ({}), confidence: {}, method: {}", 
                        sourceService, targetService, dependencyType, confidence, discoveryMethod);
            }
            
        } catch (Exception e) {
            logger.error("Error processing dependency discovery: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void performDependencyAnalysis() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            logger.info("Starting scheduled dependency analysis");
            
            analyzeDependencyPatterns();
            detectAnomalousConnections();
            analyzeDependencyHealth();
            updateServiceCriticality();
            
        } catch (Exception e) {
            logger.error("Error in dependency analysis: {}", e.getMessage(), e);
        } finally {
            sample.stop(dependencyAnalysisTimer);
        }
    }

    private void performHealthChecks() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleThreshold = now.minusSeconds(healthCheckIntervalSeconds * 2);
        
        for (Map.Entry<String, LocalDateTime> entry : lastHealthCheck.entrySet()) {
            String dependencyKey = entry.getKey();
            LocalDateTime lastCheck = entry.getValue();
            
            if (lastCheck.isBefore(staleThreshold)) {
                String[] parts = dependencyKey.split("->");
                generateStaleHealthAlert(parts[0], parts[1], Duration.between(lastCheck, now));
            }
        }
    }

    private void analyzeCriticalPaths() {
        try {
            logger.info("Analyzing critical dependency paths");
            
            for (ServiceDependencyNode rootNode : dependencyGraph.values()) {
                if (isRootService(rootNode)) {
                    List<List<String>> paths = findAllPaths(rootNode, criticalPathDepth);
                    
                    for (List<String> path : paths) {
                        if (path.size() >= 3) {
                            analyzePath(path);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error analyzing critical paths: {}", e.getMessage(), e);
        }
    }

    private void cleanupOldData() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        
        try {
            int deletedDependencies = serviceDependencyRepository.deleteByTimestampBefore(cutoff);
            int deletedHealthRecords = dependencyHealthRepository.deleteByTimestampBefore(cutoff);
            int deletedFailures = dependencyFailureRepository.deleteByTimestampBefore(cutoff);
            int deletedAlerts = dependencyAlertRepository.deleteByTimestampBefore(cutoff);
            
            logger.info("Cleaned up old dependency data: {} dependencies, {} health records, {} failures, {} alerts", 
                    deletedDependencies, deletedHealthRecords, deletedFailures, deletedAlerts);
            
        } catch (Exception e) {
            logger.error("Error cleaning up old dependency data: {}", e.getMessage(), e);
        }
    }

    private boolean shouldTriggerRealTimeAnalysis(ServiceDependency dependency) {
        double successRate = calculateSuccessRate(dependency.getSuccessCount(), dependency.getCallCount());
        return successRate < 0.9 || dependency.getFailureCount() > 0 || 
               "OPEN".equals(dependency.getCircuitBreakerState());
    }

    private void performRealTimeDependencyAnalysis(ServiceDependency dependency) {
        double successRate = calculateSuccessRate(dependency.getSuccessCount(), dependency.getCallCount());
        
        if (successRate < 0.5) {
            generateCriticalDependencyAlert(dependency, successRate);
        } else if (successRate < 0.9) {
            generateWarningDependencyAlert(dependency, successRate);
        }
        
        if ("OPEN".equals(dependency.getCircuitBreakerState())) {
            generateCircuitBreakerOpenAlert(dependency);
        }
    }

    private void updateDependencyGraph(ServiceDependency dependency) {
        String sourceKey = dependency.getSourceService();
        String targetKey = dependency.getTargetService();
        
        ServiceDependencyNode sourceNode = dependencyGraph.computeIfAbsent(sourceKey, 
                k -> new ServiceDependencyNode(k));
        ServiceDependencyNode targetNode = dependencyGraph.computeIfAbsent(targetKey, 
                k -> new ServiceDependencyNode(k));
        
        sourceNode.addDownstream(targetNode);
        targetNode.addUpstream(sourceNode);
        
        sourceNode.updateMetrics(dependency);
    }

    private void updateCallBuffer(String dependencyKey, DependencyCall call) {
        callBuffer.computeIfAbsent(dependencyKey, k -> new ArrayList<>()).add(call);
        
        List<DependencyCall> calls = callBuffer.get(dependencyKey);
        if (calls.size() > 100) {
            calls.subList(0, calls.size() - 100).clear();
        }
    }

    private double calculateSuccessRate(long successCount, long totalCount) {
        return totalCount > 0 ? (double) successCount / totalCount : 1.0;
    }

    private void generateHealthAlert(DependencyHealth health) {
        DependencyAlert alert = new DependencyAlert();
        alert.setTimestamp(LocalDateTime.now());
        alert.setSourceService(health.getSourceService());
        alert.setTargetService(health.getTargetService());
        alert.setAlertType("HEALTH_DEGRADATION");
        alert.setSeverity(health.getHealthScore() < 0.3 ? "CRITICAL" : "HIGH");
        alert.setDescription(String.format("Dependency health degraded: %s -> %s (score: %.2f, status: %s)", 
                health.getSourceService(), health.getTargetService(), health.getHealthScore(), health.getHealthStatus()));
        alert.setImpactAnalysis(String.format("Success rate: %.1f%%, Response time: %.1fms, Availability: %.1f%%", 
                health.getSuccessRate() * 100, health.getResponseTimeMs(), health.getAvailability() * 100));
        alert.setRecommendedAction("Investigate target service health and network connectivity");
        alert.setResolved(false);
        
        dependencyAlertRepository.save(alert);
        
        alertingService.sendAlert("DEPENDENCY_HEALTH", alert.getDescription(), 
                Map.of("sourceService", health.getSourceService(), "targetService", health.getTargetService(), 
                       "healthScore", String.valueOf(health.getHealthScore())));
    }

    private void generateFailureAlert(DependencyFailure failure, int consecutiveFailures) {
        DependencyAlert alert = new DependencyAlert();
        alert.setTimestamp(LocalDateTime.now());
        alert.setSourceService(failure.getSourceService());
        alert.setTargetService(failure.getTargetService());
        alert.setAlertType("CONSECUTIVE_FAILURES");
        alert.setSeverity(consecutiveFailures >= failureThreshold * 2 ? "CRITICAL" : "HIGH");
        alert.setDescription(String.format("Consecutive dependency failures: %s -> %s (%d failures, type: %s)", 
                failure.getSourceService(), failure.getTargetService(), consecutiveFailures, failure.getFailureType()));
        alert.setImpactAnalysis(String.format("Impact: %s, Root cause: %s", failure.getImpactLevel(), failure.getRootCause()));
        alert.setRecommendedAction("Enable circuit breaker and investigate target service");
        alert.setResolved(false);
        
        dependencyAlertRepository.save(alert);
        
        alertingService.sendAlert("DEPENDENCY_FAILURES", alert.getDescription(), 
                Map.of("sourceService", failure.getSourceService(), "targetService", failure.getTargetService(), 
                       "failureCount", String.valueOf(consecutiveFailures)));
    }

    private void generateCriticalDependencyAlert(ServiceDependency dependency, double successRate) {
        alertingService.sendCriticalAlert("CRITICAL_DEPENDENCY_FAILURE", 
                String.format("Critical dependency failure: %s -> %s (success rate: %.1f%%)", 
                        dependency.getSourceService(), dependency.getTargetService(), successRate * 100),
                Map.of("sourceService", dependency.getSourceService(), "targetService", dependency.getTargetService(), 
                       "successRate", String.valueOf(successRate)));
    }

    private void generateWarningDependencyAlert(ServiceDependency dependency, double successRate) {
        alertingService.sendAlert("DEPENDENCY_WARNING", 
                String.format("Dependency degradation: %s -> %s (success rate: %.1f%%)", 
                        dependency.getSourceService(), dependency.getTargetService(), successRate * 100),
                Map.of("sourceService", dependency.getSourceService(), "targetService", dependency.getTargetService(), 
                       "successRate", String.valueOf(successRate)));
    }

    private void generateCircuitBreakerOpenAlert(ServiceDependency dependency) {
        alertingService.sendAlert("CIRCUIT_BREAKER_OPEN", 
                String.format("Circuit breaker opened: %s -> %s", 
                        dependency.getSourceService(), dependency.getTargetService()),
                Map.of("sourceService", dependency.getSourceService(), "targetService", dependency.getTargetService()));
    }

    private void generateCircuitBreakerAlert(CircuitBreakerState cbState) {
        alertingService.sendAlert("CIRCUIT_BREAKER_STATE_CHANGE", 
                String.format("Circuit breaker state changed: %s -> %s (%s to %s)", 
                        cbState.getSourceService(), cbState.getTargetService(), 
                        cbState.getPreviousState(), cbState.getState()),
                Map.of("sourceService", cbState.getSourceService(), "targetService", cbState.getTargetService(), 
                       "state", cbState.getState()));
    }

    private void generateCriticalPathAlert(CriticalPath criticalPath) {
        alertingService.sendAlert("CRITICAL_PATH_RISK", 
                String.format("High risk critical path: %s -> %s (risk: %.1f%%, bottleneck: %s)", 
                        criticalPath.getStartService(), criticalPath.getEndService(), 
                        criticalPath.getFailureRisk() * 100, criticalPath.getBottleneckService()),
                Map.of("startService", criticalPath.getStartService(), "endService", criticalPath.getEndService(), 
                       "risk", String.valueOf(criticalPath.getFailureRisk())));
    }

    private void generateStaleHealthAlert(String sourceService, String targetService, Duration staleDuration) {
        alertingService.sendAlert("STALE_DEPENDENCY_HEALTH", 
                String.format("Stale dependency health data: %s -> %s (last update: %d minutes ago)", 
                        sourceService, targetService, staleDuration.toMinutes()),
                Map.of("sourceService", sourceService, "targetService", targetService, 
                       "staleMinutes", String.valueOf(staleDuration.toMinutes())));
    }

    private void analyzeCascadeFailureRisk(DependencyFailure failure) {
        ServiceDependencyNode failedNode = dependencyGraph.get(failure.getTargetService());
        if (failedNode != null) {
            int dependentCount = failedNode.getUpstreamNodes().size();
            if (dependentCount >= cascadeThreshold) {
                alertingService.sendAlert("CASCADE_FAILURE_RISK", 
                        String.format("High cascade failure risk: %s failure could affect %d dependent services", 
                                failure.getTargetService(), dependentCount),
                        Map.of("failedService", failure.getTargetService(), "dependentCount", String.valueOf(dependentCount)));
            }
        }
    }

    private void updateServiceMapGraph(ServiceMap serviceMap) {
        ServiceDependencyNode node = dependencyGraph.computeIfAbsent(serviceMap.getServiceName(), 
                k -> new ServiceDependencyNode(k));
        node.updateServiceMapInfo(serviceMap);
    }

    private void identifyCriticalService(ServiceMap serviceMap) {
        alertingService.sendAlert("CRITICAL_SERVICE_IDENTIFIED", 
                String.format("Critical service identified: %s (criticality score: %.2f, dependencies: %d, dependents: %d)", 
                        serviceMap.getServiceName(), serviceMap.getCriticalityScore(), 
                        serviceMap.getDependencyCount(), serviceMap.getDependentCount()),
                Map.of("serviceName", serviceMap.getServiceName(), "criticalityScore", String.valueOf(serviceMap.getCriticalityScore())));
    }

    private void updateDependencyGraphWithCircuitBreaker(CircuitBreakerState cbState) {
        ServiceDependencyNode sourceNode = dependencyGraph.get(cbState.getSourceService());
        if (sourceNode != null) {
            sourceNode.updateCircuitBreakerState(cbState.getTargetService(), cbState.getState());
        }
    }

    private void updateDependencyGraphWithRecovery(String sourceService, String targetService, double successRate) {
        ServiceDependencyNode sourceNode = dependencyGraph.get(sourceService);
        if (sourceNode != null) {
            sourceNode.updateSuccessRate(targetService, successRate);
        }
    }

    private void updateDependencyGraphWithIsolation(String isolatedService, String isolationType) {
        ServiceDependencyNode node = dependencyGraph.get(isolatedService);
        if (node != null) {
            node.markAsIsolated(isolationType);
        }
    }

    private void analyzeTimeoutPattern(String sourceService, String targetService, long timeoutMs, long actualMs) {
    }

    private void analyzeRetryPattern(String sourceService, String targetService, int attemptNumber, int maxAttempts, String retryReason) {
    }

    private void initiateFailureContainment(String originService, String affectedServices, String failurePattern) {
    }

    private boolean shouldResolveAlert(DependencyAlert alert, double currentSuccessRate) {
        return currentSuccessRate > 0.95;
    }

    private void analyzeDependencyPatterns() {
    }

    private void detectAnomalousConnections() {
    }

    private void analyzeDependencyHealth() {
    }

    private void updateServiceCriticality() {
    }

    private boolean isRootService(ServiceDependencyNode node) {
        return node.getUpstreamNodes().isEmpty() && !node.getDownstreamNodes().isEmpty();
    }

    private List<List<String>> findAllPaths(ServiceDependencyNode startNode, int maxDepth) {
        List<List<String>> allPaths = new ArrayList<>();
        List<String> currentPath = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        findPathsRecursive(startNode, currentPath, visited, allPaths, maxDepth);
        return allPaths;
    }

    private void findPathsRecursive(ServiceDependencyNode node, List<String> currentPath, 
                                   Set<String> visited, List<List<String>> allPaths, int maxDepth) {
        if (currentPath.size() >= maxDepth || visited.contains(node.getServiceName())) {
            return;
        }
        
        currentPath.add(node.getServiceName());
        visited.add(node.getServiceName());
        
        if (node.getDownstreamNodes().isEmpty()) {
            allPaths.add(new ArrayList<>(currentPath));
        } else {
            for (ServiceDependencyNode downstream : node.getDownstreamNodes()) {
                findPathsRecursive(downstream, currentPath, visited, allPaths, maxDepth);
            }
        }
        
        currentPath.remove(currentPath.size() - 1);
        visited.remove(node.getServiceName());
    }

    private void analyzePath(List<String> path) {
    }

    private double getActiveDependenciesCount() {
        return dependencyGraph.values().stream()
                .mapToLong(node -> node.getDownstreamNodes().size())
                .sum();
    }

    private double getFailedDependenciesCount() {
        return consecutiveFailures.values().stream()
                .mapToInt(Integer::intValue)
                .filter(failures -> failures > 0)
                .count();
    }

    private double getCriticalPathLength() {
        return dependencyGraph.values().stream()
                .filter(this::isRootService)
                .mapToInt(this::calculateMaxDepth)
                .max()
                .orElse(0);
    }

    private int calculateMaxDepth(ServiceDependencyNode node) {
        if (node.getDownstreamNodes().isEmpty()) {
            return 1;
        }
        
        return 1 + node.getDownstreamNodes().stream()
                .mapToInt(this::calculateMaxDepth)
                .max()
                .orElse(0);
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        try {
            return LocalDateTime.parse(timestamp.replace("Z", ""));
        } catch (Exception e) {
            logger.warn("Failed to parse timestamp: {}, using current time", timestamp);
            return LocalDateTime.now();
        }
    }

    private static class DependencyCall {
        private final LocalDateTime timestamp;
        private final long callCount;
        private final long successCount;
        private final long failureCount;
        
        public DependencyCall(LocalDateTime timestamp, long callCount, long successCount, long failureCount) {
            this.timestamp = timestamp;
            this.callCount = callCount;
            this.successCount = successCount;
            this.failureCount = failureCount;
        }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public long getCallCount() { return callCount; }
        public long getSuccessCount() { return successCount; }
        public long getFailureCount() { return failureCount; }
    }

    private static class ServiceDependencyNode {
        private final String serviceName;
        private final Set<ServiceDependencyNode> upstreamNodes = new HashSet<>();
        private final Set<ServiceDependencyNode> downstreamNodes = new HashSet<>();
        private final Map<String, String> circuitBreakerStates = new HashMap<>();
        private final Map<String, Double> successRates = new HashMap<>();
        private boolean isolated = false;
        private String isolationType = "";
        private ServiceMap serviceMapInfo;
        
        public ServiceDependencyNode(String serviceName) {
            this.serviceName = serviceName;
        }
        
        public String getServiceName() { return serviceName; }
        public Set<ServiceDependencyNode> getUpstreamNodes() { return upstreamNodes; }
        public Set<ServiceDependencyNode> getDownstreamNodes() { return downstreamNodes; }
        
        public void addUpstream(ServiceDependencyNode node) { upstreamNodes.add(node); }
        public void addDownstream(ServiceDependencyNode node) { downstreamNodes.add(node); }
        
        public void updateMetrics(ServiceDependency dependency) {
            String targetService = dependency.getTargetService();
            double successRate = dependency.getCallCount() > 0 ? 
                    (double) dependency.getSuccessCount() / dependency.getCallCount() : 1.0;
            successRates.put(targetService, successRate);
            
            if (dependency.getCircuitBreakerState() != null) {
                circuitBreakerStates.put(targetService, dependency.getCircuitBreakerState());
            }
        }
        
        public void updateCircuitBreakerState(String targetService, String state) {
            circuitBreakerStates.put(targetService, state);
        }
        
        public void updateSuccessRate(String targetService, double successRate) {
            successRates.put(targetService, successRate);
        }
        
        public void markAsIsolated(String isolationType) {
            this.isolated = true;
            this.isolationType = isolationType;
        }
        
        public void updateServiceMapInfo(ServiceMap serviceMap) {
            this.serviceMapInfo = serviceMap;
        }
        
        public boolean isIsolated() { return isolated; }
        public String getIsolationType() { return isolationType; }
        public ServiceMap getServiceMapInfo() { return serviceMapInfo; }
    }
}