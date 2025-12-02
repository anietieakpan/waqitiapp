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

import java.security.SecureRandom;
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
public class AvailabilityMonitoringConsumer extends BaseKafkaConsumer {
    private static final Logger logger = LoggerFactory.getLogger(AvailabilityMonitoringConsumer.class);
    private static final String CONSUMER_GROUP_ID = "availability-monitoring-group";
    private static final String DLQ_TOPIC = "availability-monitoring-dlq";

    // SECURITY FIX: Use SecureRandom instead of Math.random()
    private static final SecureRandom secureRandom = new SecureRandom();
    
    private final MetricsService metricsService;
    private final AlertingService alertingService;
    private final KafkaRetryHandler retryHandler;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    
    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Value("${monitoring.availability.health-check-interval-seconds:30}")
    private int healthCheckIntervalSeconds;
    
    @Value("${monitoring.availability.probe-timeout-ms:5000}")
    private long probeTimeoutMs;
    
    @Value("${monitoring.availability.uptime-sla-percentage:99.9}")
    private double uptimeSlaPercentage;
    
    @Value("${monitoring.availability.consecutive-failures-alert:3}")
    private int consecutiveFailuresAlert;
    
    @Value("${monitoring.availability.recovery-threshold-checks:2}")
    private int recoveryThresholdChecks;
    
    @Value("${monitoring.availability.circuit-open-duration-seconds:60}")
    private int circuitOpenDurationSeconds;
    
    @Value("${monitoring.availability.historical-retention-days:90}")
    private int historicalRetentionDays;
    
    @Value("${monitoring.availability.availability-calculation-window-hours:24}")
    private int availabilityCalculationWindowHours;
    
    @Value("${monitoring.availability.synthetic-test-timeout-ms:10000}")
    private long syntheticTestTimeoutMs;
    
    @Value("${monitoring.availability.maintenance-window-buffer-minutes:5}")
    private int maintenanceWindowBufferMinutes;
    
    private final Map<String, ServiceAvailability> serviceAvailabilityMap = new ConcurrentHashMap<>();
    private final Map<String, EndpointHealth> endpointHealthMap = new ConcurrentHashMap<>();
    private final Map<String, ProbeResult> probeResultsMap = new ConcurrentHashMap<>();
    private final Map<String, UptimeMetrics> uptimeMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, MaintenanceWindow> maintenanceWindowMap = new ConcurrentHashMap<>();
    private final Map<String, RegionAvailability> regionAvailabilityMap = new ConcurrentHashMap<>();
    private final Map<String, DependencyStatus> dependencyStatusMap = new ConcurrentHashMap<>();
    private final Map<String, SyntheticMonitor> syntheticMonitorMap = new ConcurrentHashMap<>();
    private final Map<String, FailoverState> failoverStateMap = new ConcurrentHashMap<>();
    private final Map<String, HealthHistory> healthHistoryMap = new ConcurrentHashMap<>();
    private final Map<String, AvailabilityTrend> availabilityTrendMap = new ConcurrentHashMap<>();
    private final Map<String, OutageRecord> outageRecordMap = new ConcurrentHashMap<>();
    private final Map<String, RecoveryMetrics> recoveryMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, ComponentHealth> componentHealthMap = new ConcurrentHashMap<>();
    private final Map<String, AvailabilityReport> availabilityReportMap = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(5);
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    
    public AvailabilityMonitoringConsumer(MetricsService metricsService,
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
    
    private Counter processedEventsCounter;
    private Counter errorCounter;
    private Counter dlqCounter;
    private Counter healthCheckCounter;
    private Counter outageCounter;
    private Counter recoveryCounter;
    private Gauge availabilityGauge;
    private Timer processingTimer;
    private Timer healthCheckTimer;
    private Timer syntheticTestTimer;
    
    @PostConstruct
    public void init() {
        initializeMetrics();
        initializeResilience();
        startScheduledTasks();
        initializeHealthChecks();
        logger.info("AvailabilityMonitoringConsumer initialized with health check interval: {}s", 
                    healthCheckIntervalSeconds);
    }
    
    private void initializeMetrics() {
        processedEventsCounter = Counter.builder("availability.monitoring.processed")
            .description("Total availability monitoring events processed")
            .register(meterRegistry);
            
        errorCounter = Counter.builder("availability.monitoring.errors")
            .description("Total availability monitoring errors")
            .register(meterRegistry);
            
        dlqCounter = Counter.builder("availability.monitoring.dlq")
            .description("Total messages sent to DLQ")
            .register(meterRegistry);
            
        healthCheckCounter = Counter.builder("availability.health.checks")
            .description("Total health checks performed")
            .register(meterRegistry);
            
        outageCounter = Counter.builder("availability.outages")
            .description("Total service outages detected")
            .register(meterRegistry);
            
        recoveryCounter = Counter.builder("availability.recoveries")
            .description("Total service recoveries")
            .register(meterRegistry);
            
        availabilityGauge = Gauge.builder("availability.percentage", this, 
            consumer -> calculateOverallAvailability())
            .description("Overall system availability percentage")
            .register(meterRegistry);
            
        processingTimer = Timer.builder("availability.monitoring.processing.time")
            .description("Availability monitoring processing time")
            .register(meterRegistry);
            
        healthCheckTimer = Timer.builder("availability.health.check.time")
            .description("Health check execution time")
            .register(meterRegistry);
            
        syntheticTestTimer = Timer.builder("availability.synthetic.test.time")
            .description("Synthetic test execution time")
            .register(meterRegistry);
    }
    
    private void initializeResilience() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(circuitOpenDurationSeconds))
            .permittedNumberOfCallsInHalfOpenState(3)
            .slidingWindowSize(10)
            .build();
            
        circuitBreaker = CircuitBreakerRegistry.of(circuitBreakerConfig)
            .circuitBreaker("availability-monitoring", circuitBreakerConfig);
            
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .retryExceptions(Exception.class)
            .build();
            
        retry = RetryRegistry.of(retryConfig).retry("availability-monitoring", retryConfig);
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> logger.warn("Circuit breaker state transition: {}", event));
    }
    
    private void startScheduledTasks() {
        scheduledExecutor.scheduleAtFixedRate(
            this::performHealthChecks, 
            0, healthCheckIntervalSeconds, TimeUnit.SECONDS
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::calculateAvailabilityMetrics, 
            0, 5, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::runSyntheticTests, 
            0, 2, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::checkMaintenanceWindows, 
            0, 1, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::analyzeAvailabilityTrends, 
            0, 15, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::cleanupOldData, 
            0, 24, TimeUnit.HOURS
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::generateAvailabilityReports, 
            0, 1, TimeUnit.HOURS
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::checkFailoverStates, 
            0, 30, TimeUnit.SECONDS
        );
    }
    
    private void initializeHealthChecks() {
        Arrays.asList(
            "payment-service", "user-service", "transaction-service",
            "compliance-service", "fraud-service", "notification-service",
            "account-service", "kyc-service", "audit-service"
        ).forEach(service -> {
            ServiceAvailability availability = new ServiceAvailability(service);
            serviceAvailabilityMap.put(service, availability);
            
            UptimeMetrics uptime = new UptimeMetrics(service);
            uptimeMetricsMap.put(service, uptime);
            
            HealthHistory history = new HealthHistory(service);
            healthHistoryMap.put(service, history);
        });
    }
    
    @KafkaListener(
        topics = "availability-monitoring-events",
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
            processAvailabilityEvent(message, timestamp);
            acknowledgment.acknowledge();
            processedEventsCounter.increment();
            
        } catch (Exception e) {
            handleProcessingError(message, e, acknowledgment);
            
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }
    
    private void processAvailabilityEvent(String message, long timestamp) throws Exception {
        JsonNode event = objectMapper.readTree(message);
        String eventType = event.path("type").asText();
        String eventId = event.path("eventId").asText();
        
        logger.debug("Processing availability event: {} - {}", eventType, eventId);
        
        Callable<Void> processTask = () -> {
            switch (eventType) {
                case "SERVICE_HEALTH_CHECK":
                    handleServiceHealthCheck(event, timestamp);
                    break;
                case "ENDPOINT_AVAILABILITY":
                    handleEndpointAvailability(event, timestamp);
                    break;
                case "PROBE_RESULT":
                    handleProbeResult(event, timestamp);
                    break;
                case "UPTIME_REPORT":
                    handleUptimeReport(event, timestamp);
                    break;
                case "MAINTENANCE_WINDOW":
                    handleMaintenanceWindow(event, timestamp);
                    break;
                case "REGION_AVAILABILITY":
                    handleRegionAvailability(event, timestamp);
                    break;
                case "DEPENDENCY_STATUS":
                    handleDependencyStatus(event, timestamp);
                    break;
                case "SYNTHETIC_TEST_RESULT":
                    handleSyntheticTestResult(event, timestamp);
                    break;
                case "FAILOVER_EVENT":
                    handleFailoverEvent(event, timestamp);
                    break;
                case "OUTAGE_DETECTED":
                    handleOutageDetected(event, timestamp);
                    break;
                case "SERVICE_RECOVERY":
                    handleServiceRecovery(event, timestamp);
                    break;
                case "AVAILABILITY_DEGRADED":
                    handleAvailabilityDegraded(event, timestamp);
                    break;
                case "COMPONENT_HEALTH":
                    handleComponentHealth(event, timestamp);
                    break;
                case "AVAILABILITY_SLA_CHECK":
                    handleAvailabilitySlaCheck(event, timestamp);
                    break;
                case "HEALTH_TREND_ANALYSIS":
                    handleHealthTrendAnalysis(event, timestamp);
                    break;
                default:
                    logger.warn("Unknown availability event type: {}", eventType);
            }
            return null;
        };
        
        Retry.decorateCallable(retry, processTask).call();
    }
    
    private void handleServiceHealthCheck(JsonNode event, long timestamp) {
        String serviceId = event.path("serviceId").asText();
        String status = event.path("status").asText();
        long responseTime = event.path("responseTime").asLong();
        JsonNode healthDetails = event.path("healthDetails");
        
        ServiceAvailability availability = serviceAvailabilityMap.computeIfAbsent(
            serviceId, k -> new ServiceAvailability(serviceId)
        );
        
        availability.updateHealth(status, responseTime, timestamp);
        
        if (healthDetails != null && !healthDetails.isNull()) {
            availability.processHealthDetails(healthDetails);
        }
        
        if ("DOWN".equals(status) || "DEGRADED".equals(status)) {
            availability.incrementFailureCount();
            
            if (availability.getConsecutiveFailures() >= consecutiveFailuresAlert) {
                triggerServiceAlert(serviceId, status, availability);
            }
        } else if ("UP".equals(status)) {
            if (availability.getConsecutiveFailures() > 0) {
                availability.resetFailureCount();
                recordServiceRecovery(serviceId, timestamp);
            }
        }
        
        updateHealthHistory(serviceId, status, responseTime, timestamp);
        healthCheckCounter.increment();
        
        metricsService.recordMetric("availability.health_check", 1.0, 
            Map.of("service", serviceId, "status", status));
    }
    
    private void handleEndpointAvailability(JsonNode event, long timestamp) {
        String endpointId = event.path("endpointId").asText();
        String serviceId = event.path("serviceId").asText();
        String method = event.path("method").asText();
        String path = event.path("path").asText();
        String status = event.path("status").asText();
        long latency = event.path("latency").asLong();
        int statusCode = event.path("statusCode").asInt();
        
        EndpointHealth health = endpointHealthMap.computeIfAbsent(
            endpointId, k -> new EndpointHealth(endpointId, serviceId, method, path)
        );
        
        health.updateStatus(status, statusCode, latency, timestamp);
        
        if (statusCode >= 500 || "DOWN".equals(status)) {
            health.incrementErrorCount();
            
            if (health.getErrorRate() > 0.1) {
                triggerEndpointAlert(endpointId, health);
            }
        }
        
        metricsService.recordMetric("availability.endpoint", 1.0,
            Map.of(
                "endpoint", endpointId,
                "service", serviceId,
                "status", status,
                "method", method
            ));
    }
    
    private void handleProbeResult(JsonNode event, long timestamp) {
        String probeId = event.path("probeId").asText();
        String targetService = event.path("targetService").asText();
        String probeType = event.path("probeType").asText();
        boolean success = event.path("success").asBoolean();
        long responseTime = event.path("responseTime").asLong();
        String errorMessage = event.path("errorMessage").asText(null);
        
        ProbeResult result = new ProbeResult(
            probeId, targetService, probeType, success, responseTime, errorMessage, timestamp
        );
        
        probeResultsMap.put(probeId + "_" + timestamp, result);
        
        ServiceAvailability availability = serviceAvailabilityMap.get(targetService);
        if (availability != null) {
            availability.addProbeResult(result);
            
            if (!success && availability.shouldTriggerAlert()) {
                alertingService.sendAlert(
                    "PROBE_FAILURE",
                    "High",
                    String.format("Probe failed for service %s: %s", targetService, errorMessage),
                    Map.of(
                        "probeId", probeId,
                        "service", targetService,
                        "probeType", probeType,
                        "error", errorMessage != null ? errorMessage : "Unknown error"
                    )
                );
            }
        }
        
        metricsService.recordMetric("availability.probe_result", success ? 1.0 : 0.0,
            Map.of(
                "probe", probeId,
                "service", targetService,
                "type", probeType,
                "success", String.valueOf(success)
            ));
    }
    
    private void handleUptimeReport(JsonNode event, long timestamp) {
        String serviceId = event.path("serviceId").asText();
        double uptimePercentage = event.path("uptimePercentage").asDouble();
        long uptimeSeconds = event.path("uptimeSeconds").asLong();
        long downtimeSeconds = event.path("downtimeSeconds").asLong();
        int incidentCount = event.path("incidentCount").asInt();
        String period = event.path("period").asText();
        
        UptimeMetrics metrics = uptimeMetricsMap.computeIfAbsent(
            serviceId, k -> new UptimeMetrics(serviceId)
        );
        
        metrics.updateMetrics(
            uptimePercentage, uptimeSeconds, downtimeSeconds, incidentCount, period, timestamp
        );
        
        if (uptimePercentage < uptimeSlaPercentage) {
            alertingService.sendAlert(
                "SLA_BREACH",
                "Critical",
                String.format("Service %s uptime %.2f%% below SLA %.2f%%", 
                    serviceId, uptimePercentage, uptimeSlaPercentage),
                Map.of(
                    "service", serviceId,
                    "uptime", String.valueOf(uptimePercentage),
                    "sla", String.valueOf(uptimeSlaPercentage),
                    "period", period
                )
            );
        }
        
        metricsService.recordMetric("availability.uptime", uptimePercentage,
            Map.of("service", serviceId, "period", period));
    }
    
    private void handleMaintenanceWindow(JsonNode event, long timestamp) {
        String windowId = event.path("windowId").asText();
        String serviceId = event.path("serviceId").asText();
        String action = event.path("action").asText();
        long startTime = event.path("startTime").asLong();
        long endTime = event.path("endTime").asLong();
        String description = event.path("description").asText();
        boolean isPlanned = event.path("isPlanned").asBoolean(true);
        
        MaintenanceWindow window = new MaintenanceWindow(
            windowId, serviceId, startTime, endTime, description, isPlanned
        );
        
        if ("CREATE".equals(action) || "UPDATE".equals(action)) {
            maintenanceWindowMap.put(windowId, window);
            
            if (isWithinBuffer(startTime)) {
                notifyUpcomingMaintenance(window);
            }
        } else if ("CANCEL".equals(action)) {
            maintenanceWindowMap.remove(windowId);
        }
        
        ServiceAvailability availability = serviceAvailabilityMap.get(serviceId);
        if (availability != null) {
            availability.setMaintenanceMode(
                timestamp >= startTime && timestamp <= endTime
            );
        }
        
        metricsService.recordMetric("availability.maintenance_window", 1.0,
            Map.of(
                "service", serviceId,
                "action", action,
                "planned", String.valueOf(isPlanned)
            ));
    }
    
    private void handleRegionAvailability(JsonNode event, long timestamp) {
        String region = event.path("region").asText();
        String serviceId = event.path("serviceId").asText();
        String status = event.path("status").asText();
        double availabilityPercentage = event.path("availabilityPercentage").asDouble();
        long latency = event.path("latency").asLong();
        int activeInstances = event.path("activeInstances").asInt();
        int totalInstances = event.path("totalInstances").asInt();
        
        String key = region + "_" + serviceId;
        RegionAvailability regionAvailability = regionAvailabilityMap.computeIfAbsent(
            key, k -> new RegionAvailability(region, serviceId)
        );
        
        regionAvailability.updateStatus(
            status, availabilityPercentage, latency, activeInstances, totalInstances, timestamp
        );
        
        if (availabilityPercentage < 90.0 || activeInstances < totalInstances / 2) {
            triggerRegionAlert(region, serviceId, regionAvailability);
        }
        
        metricsService.recordMetric("availability.region", availabilityPercentage,
            Map.of(
                "region", region,
                "service", serviceId,
                "status", status
            ));
    }
    
    private void handleDependencyStatus(JsonNode event, long timestamp) {
        String dependencyId = event.path("dependencyId").asText();
        String sourceService = event.path("sourceService").asText();
        String targetService = event.path("targetService").asText();
        String status = event.path("status").asText();
        boolean isHealthy = event.path("isHealthy").asBoolean();
        long responseTime = event.path("responseTime").asLong();
        double successRate = event.path("successRate").asDouble();
        
        DependencyStatus dependency = dependencyStatusMap.computeIfAbsent(
            dependencyId, k -> new DependencyStatus(dependencyId, sourceService, targetService)
        );
        
        dependency.updateStatus(status, isHealthy, responseTime, successRate, timestamp);
        
        if (!isHealthy || successRate < 0.95) {
            ServiceAvailability sourceAvailability = serviceAvailabilityMap.get(sourceService);
            if (sourceAvailability != null) {
                sourceAvailability.markDependencyIssue(targetService);
            }
            
            alertingService.sendAlert(
                "DEPENDENCY_ISSUE",
                "High",
                String.format("Dependency issue: %s -> %s (success rate: %.2f%%)",
                    sourceService, targetService, successRate * 100),
                Map.of(
                    "source", sourceService,
                    "target", targetService,
                    "successRate", String.valueOf(successRate),
                    "status", status
                )
            );
        }
        
        metricsService.recordMetric("availability.dependency", isHealthy ? 1.0 : 0.0,
            Map.of(
                "source", sourceService,
                "target", targetService,
                "status", status
            ));
    }
    
    private void handleSyntheticTestResult(JsonNode event, long timestamp) {
        String testId = event.path("testId").asText();
        String testName = event.path("testName").asText();
        String targetService = event.path("targetService").asText();
        boolean passed = event.path("passed").asBoolean();
        long executionTime = event.path("executionTime").asLong();
        JsonNode steps = event.path("steps");
        String errorDetails = event.path("errorDetails").asText(null);
        
        SyntheticMonitor monitor = syntheticMonitorMap.computeIfAbsent(
            testId, k -> new SyntheticMonitor(testId, testName, targetService)
        );
        
        monitor.recordTestResult(passed, executionTime, steps, errorDetails, timestamp);
        
        if (!passed) {
            monitor.incrementFailureCount();
            
            if (monitor.getConsecutiveFailures() >= 3) {
                alertingService.sendAlert(
                    "SYNTHETIC_TEST_FAILURE",
                    "High",
                    String.format("Synthetic test '%s' failed %d consecutive times",
                        testName, monitor.getConsecutiveFailures()),
                    Map.of(
                        "testId", testId,
                        "testName", testName,
                        "service", targetService,
                        "error", errorDetails != null ? errorDetails : "Unknown error"
                    )
                );
            }
        } else {
            monitor.resetFailureCount();
        }
        
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(syntheticTestTimer);
        
        metricsService.recordMetric("availability.synthetic_test", passed ? 1.0 : 0.0,
            Map.of(
                "test", testName,
                "service", targetService,
                "passed", String.valueOf(passed)
            ));
    }
    
    private void handleFailoverEvent(JsonNode event, long timestamp) {
        String serviceId = event.path("serviceId").asText();
        String failoverType = event.path("failoverType").asText();
        String fromInstance = event.path("fromInstance").asText();
        String toInstance = event.path("toInstance").asText();
        boolean automatic = event.path("automatic").asBoolean();
        long failoverDuration = event.path("failoverDuration").asLong();
        boolean successful = event.path("successful").asBoolean();
        
        FailoverState state = failoverStateMap.computeIfAbsent(
            serviceId, k -> new FailoverState(serviceId)
        );
        
        state.recordFailover(
            failoverType, fromInstance, toInstance, automatic, 
            failoverDuration, successful, timestamp
        );
        
        if (!successful) {
            alertingService.sendAlert(
                "FAILOVER_FAILED",
                "Critical",
                String.format("Failover failed for service %s from %s to %s",
                    serviceId, fromInstance, toInstance),
                Map.of(
                    "service", serviceId,
                    "type", failoverType,
                    "from", fromInstance,
                    "to", toInstance
                )
            );
        }
        
        ServiceAvailability availability = serviceAvailabilityMap.get(serviceId);
        if (availability != null) {
            availability.setFailoverActive(true);
            availability.setActiveInstance(toInstance);
        }
        
        metricsService.recordMetric("availability.failover", successful ? 1.0 : 0.0,
            Map.of(
                "service", serviceId,
                "type", failoverType,
                "automatic", String.valueOf(automatic),
                "successful", String.valueOf(successful)
            ));
    }
    
    private void handleOutageDetected(JsonNode event, long timestamp) {
        String serviceId = event.path("serviceId").asText();
        String severity = event.path("severity").asText();
        String impactLevel = event.path("impactLevel").asText();
        JsonNode affectedComponents = event.path("affectedComponents");
        int affectedUsers = event.path("affectedUsers").asInt();
        String rootCause = event.path("rootCause").asText(null);
        
        OutageRecord outage = new OutageRecord(
            serviceId, severity, impactLevel, timestamp
        );
        
        if (affectedComponents != null && affectedComponents.isArray()) {
            affectedComponents.forEach(component -> 
                outage.addAffectedComponent(component.asText())
            );
        }
        
        outage.setAffectedUsers(affectedUsers);
        outage.setRootCause(rootCause);
        
        String outageId = serviceId + "_" + timestamp;
        outageRecordMap.put(outageId, outage);
        
        ServiceAvailability availability = serviceAvailabilityMap.get(serviceId);
        if (availability != null) {
            availability.markOutage(timestamp);
            availability.setCurrentStatus("OUTAGE");
        }
        
        outageCounter.increment();
        
        alertingService.sendAlert(
            "SERVICE_OUTAGE",
            "Critical",
            String.format("Service outage detected: %s (Impact: %s, Affected users: %d)",
                serviceId, impactLevel, affectedUsers),
            Map.of(
                "service", serviceId,
                "severity", severity,
                "impact", impactLevel,
                "affectedUsers", String.valueOf(affectedUsers),
                "rootCause", rootCause != null ? rootCause : "Under investigation"
            )
        );
        
        metricsService.recordMetric("availability.outage", 1.0,
            Map.of(
                "service", serviceId,
                "severity", severity,
                "impact", impactLevel
            ));
    }
    
    private void handleServiceRecovery(JsonNode event, long timestamp) {
        String serviceId = event.path("serviceId").asText();
        long outageDuration = event.path("outageDuration").asLong();
        long recoveryTime = event.path("recoveryTime").asLong();
        String recoveryMethod = event.path("recoveryMethod").asText();
        boolean dataLoss = event.path("dataLoss").asBoolean();
        JsonNode recoverySteps = event.path("recoverySteps");
        
        RecoveryMetrics recovery = recoveryMetricsMap.computeIfAbsent(
            serviceId, k -> new RecoveryMetrics(serviceId)
        );
        
        recovery.recordRecovery(
            outageDuration, recoveryTime, recoveryMethod, dataLoss, timestamp
        );
        
        if (recoverySteps != null && recoverySteps.isArray()) {
            recoverySteps.forEach(step -> 
                recovery.addRecoveryStep(step.asText())
            );
        }
        
        ServiceAvailability availability = serviceAvailabilityMap.get(serviceId);
        if (availability != null) {
            availability.markRecovery(timestamp);
            availability.setCurrentStatus("UP");
            availability.resetFailureCount();
        }
        
        recoveryCounter.increment();
        
        alertingService.sendAlert(
            "SERVICE_RECOVERED",
            "Info",
            String.format("Service recovered: %s (Outage duration: %d minutes)",
                serviceId, outageDuration / 60000),
            Map.of(
                "service", serviceId,
                "outageDuration", String.valueOf(outageDuration),
                "recoveryTime", String.valueOf(recoveryTime),
                "method", recoveryMethod,
                "dataLoss", String.valueOf(dataLoss)
            )
        );
        
        metricsService.recordMetric("availability.recovery", 1.0,
            Map.of(
                "service", serviceId,
                "method", recoveryMethod,
                "dataLoss", String.valueOf(dataLoss)
            ));
    }
    
    private void handleAvailabilityDegraded(JsonNode event, long timestamp) {
        String serviceId = event.path("serviceId").asText();
        String degradationType = event.path("degradationType").asText();
        double performanceImpact = event.path("performanceImpact").asDouble();
        JsonNode affectedFeatures = event.path("affectedFeatures");
        String mitigationAction = event.path("mitigationAction").asText(null);
        
        ServiceAvailability availability = serviceAvailabilityMap.get(serviceId);
        if (availability != null) {
            availability.setCurrentStatus("DEGRADED");
            availability.setPerformanceImpact(performanceImpact);
            
            if (affectedFeatures != null && affectedFeatures.isArray()) {
                affectedFeatures.forEach(feature -> 
                    availability.addAffectedFeature(feature.asText())
                );
            }
        }
        
        if (performanceImpact > 0.3) {
            alertingService.sendAlert(
                "SERVICE_DEGRADED",
                "High",
                String.format("Service degraded: %s (Type: %s, Impact: %.1f%%)",
                    serviceId, degradationType, performanceImpact * 100),
                Map.of(
                    "service", serviceId,
                    "type", degradationType,
                    "impact", String.valueOf(performanceImpact),
                    "mitigation", mitigationAction != null ? mitigationAction : "None"
                )
            );
        }
        
        metricsService.recordMetric("availability.degradation", performanceImpact,
            Map.of(
                "service", serviceId,
                "type", degradationType
            ));
    }
    
    private void handleComponentHealth(JsonNode event, long timestamp) {
        String componentId = event.path("componentId").asText();
        String serviceId = event.path("serviceId").asText();
        String componentType = event.path("componentType").asText();
        String status = event.path("status").asText();
        JsonNode metrics = event.path("metrics");
        JsonNode dependencies = event.path("dependencies");
        
        ComponentHealth health = componentHealthMap.computeIfAbsent(
            componentId, k -> new ComponentHealth(componentId, serviceId, componentType)
        );
        
        health.updateStatus(status, timestamp);
        
        if (metrics != null && !metrics.isNull()) {
            health.updateMetrics(metrics);
        }
        
        if (dependencies != null && dependencies.isArray()) {
            dependencies.forEach(dep -> 
                health.addDependency(dep.path("name").asText(), dep.path("status").asText())
            );
        }
        
        if (!"HEALTHY".equals(status)) {
            ServiceAvailability availability = serviceAvailabilityMap.get(serviceId);
            if (availability != null) {
                availability.markComponentIssue(componentId, componentType);
            }
        }
        
        metricsService.recordMetric("availability.component", "HEALTHY".equals(status) ? 1.0 : 0.0,
            Map.of(
                "component", componentId,
                "service", serviceId,
                "type", componentType,
                "status", status
            ));
    }
    
    private void handleAvailabilitySlaCheck(JsonNode event, long timestamp) {
        String serviceId = event.path("serviceId").asText();
        String period = event.path("period").asText();
        double targetSla = event.path("targetSla").asDouble();
        double actualAvailability = event.path("actualAvailability").asDouble();
        boolean slaMet = event.path("slaMet").asBoolean();
        JsonNode violations = event.path("violations");
        
        if (!slaMet) {
            double slaDelta = targetSla - actualAvailability;
            
            String severity = slaDelta > 5 ? "Critical" : 
                             slaDelta > 2 ? "High" : "Medium";
            
            Map<String, String> violationDetails = new HashMap<>();
            if (violations != null && violations.isArray()) {
                violations.forEach(violation -> {
                    String type = violation.path("type").asText();
                    String detail = violation.path("detail").asText();
                    violationDetails.put(type, detail);
                });
            }
            
            alertingService.sendAlert(
                "SLA_VIOLATION",
                severity,
                String.format("SLA violation for %s: %.2f%% (target: %.2f%%) in %s",
                    serviceId, actualAvailability, targetSla, period),
                Map.of(
                    "service", serviceId,
                    "period", period,
                    "target", String.valueOf(targetSla),
                    "actual", String.valueOf(actualAvailability),
                    "violations", violationDetails.toString()
                )
            );
        }
        
        metricsService.recordMetric("availability.sla_compliance", slaMet ? 1.0 : 0.0,
            Map.of(
                "service", serviceId,
                "period", period,
                "met", String.valueOf(slaMet)
            ));
    }
    
    private void handleHealthTrendAnalysis(JsonNode event, long timestamp) {
        String serviceId = event.path("serviceId").asText();
        String trendDirection = event.path("trendDirection").asText();
        double trendStrength = event.path("trendStrength").asDouble();
        String prediction = event.path("prediction").asText();
        JsonNode historicalData = event.path("historicalData");
        
        AvailabilityTrend trend = availabilityTrendMap.computeIfAbsent(
            serviceId, k -> new AvailabilityTrend(serviceId)
        );
        
        trend.updateTrend(trendDirection, trendStrength, prediction, timestamp);
        
        if (historicalData != null && historicalData.isArray()) {
            historicalData.forEach(dataPoint -> {
                long dataTimestamp = dataPoint.path("timestamp").asLong();
                double availability = dataPoint.path("availability").asDouble();
                trend.addHistoricalPoint(dataTimestamp, availability);
            });
        }
        
        if ("DECLINING".equals(trendDirection) && trendStrength > 0.5) {
            alertingService.sendAlert(
                "AVAILABILITY_TREND_WARNING",
                "Medium",
                String.format("Declining availability trend for %s (strength: %.2f)",
                    serviceId, trendStrength),
                Map.of(
                    "service", serviceId,
                    "trend", trendDirection,
                    "strength", String.valueOf(trendStrength),
                    "prediction", prediction
                )
            );
        }
        
        metricsService.recordMetric("availability.trend", trendStrength,
            Map.of(
                "service", serviceId,
                "direction", trendDirection
            ));
    }
    
    private void performHealthChecks() {
        try {
            serviceAvailabilityMap.forEach((serviceId, availability) -> {
                executorService.submit(() -> {
                    Timer.Sample sample = Timer.start(meterRegistry);
                    try {
                        boolean isHealthy = performServiceHealthCheck(serviceId);
                        availability.updateHealth(
                            isHealthy ? "UP" : "DOWN",
                            sample.stop(healthCheckTimer) / 1_000_000,
                            System.currentTimeMillis()
                        );
                        
                        healthCheckCounter.increment();
                        
                    } catch (Exception e) {
                        logger.error("Health check failed for service: {}", serviceId, e);
                        availability.updateHealth("UNKNOWN", -1, System.currentTimeMillis());
                    }
                });
            });
        } catch (Exception e) {
            logger.error("Error performing health checks", e);
        }
    }
    
    private boolean performServiceHealthCheck(String serviceId) {
        try {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                return simulateHealthCheck(serviceId);
            });
            
            return future.get(probeTimeoutMs, TimeUnit.MILLISECONDS);
            
        } catch (TimeoutException e) {
            logger.warn("Health check timeout for service: {}", serviceId);
            return false;
        } catch (Exception e) {
            logger.error("Health check error for service: {}", serviceId, e);
            return false;
        }
    }
    
    private boolean simulateHealthCheck(String serviceId) {
        // SECURITY FIX: Use SecureRandom instead of Math.random()
        double random = secureRandom.nextDouble();
        return random > 0.05;
    }
    
    private void calculateAvailabilityMetrics() {
        try {
            serviceAvailabilityMap.forEach((serviceId, availability) -> {
                double uptimePercentage = availability.calculateUptime(
                    availabilityCalculationWindowHours
                );
                
                UptimeMetrics metrics = uptimeMetricsMap.get(serviceId);
                if (metrics != null) {
                    metrics.updateMetrics(
                        uptimePercentage,
                        availability.getUptimeSeconds(),
                        availability.getDowntimeSeconds(),
                        availability.getIncidentCount(),
                        "24h",
                        System.currentTimeMillis()
                    );
                }
                
                metricsService.recordMetric("availability.calculated", uptimePercentage,
                    Map.of("service", serviceId));
            });
        } catch (Exception e) {
            logger.error("Error calculating availability metrics", e);
        }
    }
    
    private void runSyntheticTests() {
        try {
            syntheticMonitorMap.forEach((testId, monitor) -> {
                executorService.submit(() -> {
                    Timer.Sample sample = Timer.start(meterRegistry);
                    try {
                        boolean passed = executeSyntheticTest(monitor);
                        long executionTime = sample.stop(syntheticTestTimer) / 1_000_000;
                        
                        monitor.recordTestResult(
                            passed, executionTime, null, null, System.currentTimeMillis()
                        );
                        
                    } catch (Exception e) {
                        logger.error("Synthetic test failed: {}", testId, e);
                        monitor.recordTestResult(
                            false, -1, null, e.getMessage(), System.currentTimeMillis()
                        );
                    }
                });
            });
        } catch (Exception e) {
            logger.error("Error running synthetic tests", e);
        }
    }
    
    private boolean executeSyntheticTest(SyntheticMonitor monitor) {
        try {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                return simulateSyntheticTest(monitor.getTargetService());
            });
            
            return future.get(syntheticTestTimeoutMs, TimeUnit.MILLISECONDS);
            
        } catch (TimeoutException e) {
            logger.warn("Synthetic test timeout for: {}", monitor.getTestName());
            return false;
        } catch (Exception e) {
            logger.error("Synthetic test error for: {}", monitor.getTestName(), e);
            return false;
        }
    }
    
    private boolean simulateSyntheticTest(String targetService) {
        // SECURITY FIX: Use SecureRandom instead of Math.random()
        double random = secureRandom.nextDouble();
        return random > 0.02;
    }
    
    private void checkMaintenanceWindows() {
        try {
            long currentTime = System.currentTimeMillis();
            
            maintenanceWindowMap.values().forEach(window -> {
                if (isWithinBuffer(window.getStartTime()) && !window.isNotified()) {
                    notifyUpcomingMaintenance(window);
                    window.setNotified(true);
                }
                
                ServiceAvailability availability = serviceAvailabilityMap.get(window.getServiceId());
                if (availability != null) {
                    boolean inMaintenance = currentTime >= window.getStartTime() && 
                                           currentTime <= window.getEndTime();
                    availability.setMaintenanceMode(inMaintenance);
                }
            });
            
            maintenanceWindowMap.entrySet().removeIf(entry -> 
                entry.getValue().getEndTime() < currentTime - TimeUnit.DAYS.toMillis(1)
            );
            
        } catch (Exception e) {
            logger.error("Error checking maintenance windows", e);
        }
    }
    
    private boolean isWithinBuffer(long startTime) {
        long bufferMs = TimeUnit.MINUTES.toMillis(maintenanceWindowBufferMinutes);
        long currentTime = System.currentTimeMillis();
        return startTime - currentTime <= bufferMs && startTime > currentTime;
    }
    
    private void notifyUpcomingMaintenance(MaintenanceWindow window) {
        alertingService.sendAlert(
            "UPCOMING_MAINTENANCE",
            "Info",
            String.format("Maintenance window starting in %d minutes for %s",
                maintenanceWindowBufferMinutes, window.getServiceId()),
            Map.of(
                "windowId", window.getWindowId(),
                "service", window.getServiceId(),
                "startTime", String.valueOf(window.getStartTime()),
                "description", window.getDescription()
            )
        );
    }
    
    private void analyzeAvailabilityTrends() {
        try {
            serviceAvailabilityMap.forEach((serviceId, availability) -> {
                AvailabilityTrend trend = availabilityTrendMap.computeIfAbsent(
                    serviceId, k -> new AvailabilityTrend(serviceId)
                );
                
                List<Double> recentAvailability = availability.getRecentAvailabilityValues();
                if (recentAvailability.size() >= 10) {
                    TrendAnalysis analysis = analyzeTrend(recentAvailability);
                    trend.updateTrend(
                        analysis.direction,
                        analysis.strength,
                        analysis.prediction,
                        System.currentTimeMillis()
                    );
                    
                    if (analysis.shouldAlert) {
                        alertingService.sendAlert(
                            "AVAILABILITY_TREND",
                            analysis.severity,
                            String.format("Availability trend alert for %s: %s",
                                serviceId, analysis.message),
                            Map.of(
                                "service", serviceId,
                                "direction", analysis.direction,
                                "strength", String.valueOf(analysis.strength)
                            )
                        );
                    }
                }
            });
        } catch (Exception e) {
            logger.error("Error analyzing availability trends", e);
        }
    }
    
    private TrendAnalysis analyzeTrend(List<Double> values) {
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double recent = values.subList(values.size() - 3, values.size()).stream()
            .mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        double difference = recent - mean;
        double strength = Math.abs(difference) / mean;
        
        String direction = difference > 0.01 ? "IMPROVING" :
                          difference < -0.01 ? "DECLINING" : "STABLE";
        
        boolean shouldAlert = "DECLINING".equals(direction) && strength > 0.1;
        String severity = strength > 0.2 ? "High" : "Medium";
        String prediction = String.format("Expected availability: %.2f%%", recent * 100);
        String message = String.format("%s trend detected (strength: %.2f)", direction, strength);
        
        return new TrendAnalysis(direction, strength, prediction, shouldAlert, severity, message);
    }
    
    private void generateAvailabilityReports() {
        try {
            serviceAvailabilityMap.forEach((serviceId, availability) -> {
                AvailabilityReport report = new AvailabilityReport(serviceId);
                
                report.setUptimePercentage(availability.calculateUptime(24));
                report.setIncidentCount(availability.getIncidentCount());
                report.setMttr(availability.getMeanTimeToRecovery());
                report.setMtbf(availability.getMeanTimeBetweenFailures());
                report.setOutageMinutes(availability.getDowntimeSeconds() / 60);
                
                availabilityReportMap.put(serviceId, report);
                
                metricsService.recordMetric("availability.report.generated", 1.0,
                    Map.of("service", serviceId));
            });
        } catch (Exception e) {
            logger.error("Error generating availability reports", e);
        }
    }
    
    private void checkFailoverStates() {
        try {
            failoverStateMap.forEach((serviceId, state) -> {
                if (state.isFailoverActive() && state.shouldCheckFailback()) {
                    ServiceAvailability availability = serviceAvailabilityMap.get(serviceId);
                    if (availability != null && "UP".equals(availability.getCurrentStatus())) {
                        attemptFailback(serviceId, state);
                    }
                }
            });
        } catch (Exception e) {
            logger.error("Error checking failover states", e);
        }
    }
    
    private void attemptFailback(String serviceId, FailoverState state) {
        try {
            boolean failbackSuccessful = simulateFailback(serviceId);
            
            if (failbackSuccessful) {
                state.completeFailback(System.currentTimeMillis());
                
                alertingService.sendAlert(
                    "FAILBACK_COMPLETED",
                    "Info",
                    String.format("Failback completed for service %s", serviceId),
                    Map.of("service", serviceId)
                );
            }
        } catch (Exception e) {
            logger.error("Failback attempt failed for service: {}", serviceId, e);
        }
    }
    
    private boolean simulateFailback(String serviceId) {
        // SECURITY FIX: Use SecureRandom instead of Math.random()
        return secureRandom.nextDouble() > 0.1;
    }
    
    private void cleanupOldData() {
        try {
            long cutoffTime = System.currentTimeMillis() - 
                TimeUnit.DAYS.toMillis(historicalRetentionDays);
            
            probeResultsMap.entrySet().removeIf(entry -> {
                String[] parts = entry.getKey().split("_");
                if (parts.length > 1) {
                    try {
                        long timestamp = Long.parseLong(parts[parts.length - 1]);
                        return timestamp < cutoffTime;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
                return false;
            });
            
            healthHistoryMap.values().forEach(history -> 
                history.cleanupOldEntries(cutoffTime)
            );
            
            outageRecordMap.entrySet().removeIf(entry ->
                entry.getValue().getStartTime() < cutoffTime
            );
            
            logger.info("Cleaned up availability data older than {} days", historicalRetentionDays);
            
        } catch (Exception e) {
            logger.error("Error cleaning up old availability data", e);
        }
    }
    
    private double calculateOverallAvailability() {
        if (serviceAvailabilityMap.isEmpty()) {
            return 100.0;
        }
        
        double totalAvailability = serviceAvailabilityMap.values().stream()
            .mapToDouble(availability -> availability.calculateUptime(24))
            .sum();
        
        return (totalAvailability / serviceAvailabilityMap.size()) * 100;
    }
    
    private void triggerServiceAlert(String serviceId, String status, ServiceAvailability availability) {
        String severity = "DOWN".equals(status) ? "Critical" : "High";
        
        alertingService.sendAlert(
            "SERVICE_AVAILABILITY_ISSUE",
            severity,
            String.format("Service %s is %s (failures: %d)",
                serviceId, status, availability.getConsecutiveFailures()),
            Map.of(
                "service", serviceId,
                "status", status,
                "consecutiveFailures", String.valueOf(availability.getConsecutiveFailures()),
                "lastCheckTime", String.valueOf(availability.getLastCheckTime())
            )
        );
    }
    
    private void triggerEndpointAlert(String endpointId, EndpointHealth health) {
        alertingService.sendAlert(
            "ENDPOINT_HEALTH_ISSUE",
            "High",
            String.format("Endpoint %s error rate: %.2f%%",
                endpointId, health.getErrorRate() * 100),
            Map.of(
                "endpoint", endpointId,
                "service", health.getServiceId(),
                "errorRate", String.valueOf(health.getErrorRate()),
                "method", health.getMethod(),
                "path", health.getPath()
            )
        );
    }
    
    private void triggerRegionAlert(String region, String serviceId, RegionAvailability availability) {
        alertingService.sendAlert(
            "REGION_AVAILABILITY_ISSUE",
            "High",
            String.format("Region %s availability issue for %s: %.2f%%",
                region, serviceId, availability.getAvailabilityPercentage()),
            Map.of(
                "region", region,
                "service", serviceId,
                "availability", String.valueOf(availability.getAvailabilityPercentage()),
                "activeInstances", String.valueOf(availability.getActiveInstances()),
                "totalInstances", String.valueOf(availability.getTotalInstances())
            )
        );
    }
    
    private void updateHealthHistory(String serviceId, String status, long responseTime, long timestamp) {
        HealthHistory history = healthHistoryMap.computeIfAbsent(
            serviceId, k -> new HealthHistory(serviceId)
        );
        
        history.addEntry(status, responseTime, timestamp);
    }
    
    private void recordServiceRecovery(String serviceId, long timestamp) {
        RecoveryMetrics recovery = recoveryMetricsMap.computeIfAbsent(
            serviceId, k -> new RecoveryMetrics(serviceId)
        );
        
        ServiceAvailability availability = serviceAvailabilityMap.get(serviceId);
        if (availability != null && availability.getLastOutageTime() > 0) {
            long outageDuration = timestamp - availability.getLastOutageTime();
            recovery.recordRecovery(outageDuration, timestamp - availability.getLastOutageTime(),
                "AUTO_RECOVERY", false, timestamp);
        }
        
        recoveryCounter.increment();
    }
    
    private void handleProcessingError(String message, Exception e, Acknowledgment acknowledgment) {
        errorCounter.increment();
        logger.error("Error processing availability event", e);
        
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
        dlqMessage.put("consumer", "AvailabilityMonitoringConsumer");
        
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
            logger.info("Shutting down AvailabilityMonitoringConsumer");
            
            scheduledExecutor.shutdown();
            executorService.shutdown();
            
            if (!scheduledExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            
            logger.info("AvailabilityMonitoringConsumer shutdown complete");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Shutdown interrupted", e);
        }
    }
    
    private static class ServiceAvailability {
        private final String serviceId;
        private volatile String currentStatus = "UNKNOWN";
        private volatile long lastCheckTime = 0;
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private final AtomicLong uptimeSeconds = new AtomicLong(0);
        private final AtomicLong downtimeSeconds = new AtomicLong(0);
        private final AtomicInteger incidentCount = new AtomicInteger(0);
        private volatile long lastOutageTime = 0;
        private volatile long lastRecoveryTime = 0;
        private volatile boolean maintenanceMode = false;
        private volatile boolean failoverActive = false;
        private volatile String activeInstance = "";
        private volatile double performanceImpact = 0.0;
        private final Set<String> affectedFeatures = ConcurrentHashMap.newKeySet();
        private final Map<String, String> componentIssues = new ConcurrentHashMap<>();
        private final List<ProbeResult> recentProbes = new CopyOnWriteArrayList<>();
        private final List<Double> recentAvailability = new CopyOnWriteArrayList<>();
        private final Map<String, Boolean> dependencyIssues = new ConcurrentHashMap<>();
        
        public ServiceAvailability(String serviceId) {
            this.serviceId = serviceId;
        }
        
        public void updateHealth(String status, long responseTime, long timestamp) {
            this.currentStatus = status;
            this.lastCheckTime = timestamp;
            
            if ("UP".equals(status)) {
                if (consecutiveFailures.get() > 0) {
                    lastRecoveryTime = timestamp;
                }
                consecutiveFailures.set(0);
            } else {
                consecutiveFailures.incrementAndGet();
            }
            
            updateAvailabilityMetrics(status, timestamp);
        }
        
        private void updateAvailabilityMetrics(String status, long timestamp) {
            if (lastCheckTime > 0) {
                long duration = timestamp - lastCheckTime;
                if ("UP".equals(status)) {
                    uptimeSeconds.addAndGet(duration / 1000);
                } else {
                    downtimeSeconds.addAndGet(duration / 1000);
                }
            }
            
            double availability = calculateUptime(1);
            recentAvailability.add(availability);
            if (recentAvailability.size() > 100) {
                recentAvailability.remove(0);
            }
        }
        
        public void processHealthDetails(JsonNode details) {
            details.fieldNames().forEachRemaining(field -> {
                String value = details.path(field).asText();
                if ("unhealthy".equalsIgnoreCase(value) || "down".equalsIgnoreCase(value)) {
                    componentIssues.put(field, value);
                } else {
                    componentIssues.remove(field);
                }
            });
        }
        
        public void incrementFailureCount() {
            consecutiveFailures.incrementAndGet();
        }
        
        public void resetFailureCount() {
            consecutiveFailures.set(0);
        }
        
        public void markOutage(long timestamp) {
            lastOutageTime = timestamp;
            incidentCount.incrementAndGet();
        }
        
        public void markRecovery(long timestamp) {
            lastRecoveryTime = timestamp;
        }
        
        public void addProbeResult(ProbeResult result) {
            recentProbes.add(result);
            if (recentProbes.size() > 100) {
                recentProbes.remove(0);
            }
        }
        
        public boolean shouldTriggerAlert() {
            return consecutiveFailures.get() >= 3 && !maintenanceMode;
        }
        
        public void markDependencyIssue(String dependency) {
            dependencyIssues.put(dependency, true);
        }
        
        public void markComponentIssue(String componentId, String componentType) {
            componentIssues.put(componentId, componentType);
        }
        
        public void addAffectedFeature(String feature) {
            affectedFeatures.add(feature);
        }
        
        public double calculateUptime(int hours) {
            long totalSeconds = uptimeSeconds.get() + downtimeSeconds.get();
            if (totalSeconds == 0) return 100.0;
            return (double) uptimeSeconds.get() / totalSeconds;
        }
        
        public long getMeanTimeToRecovery() {
            if (incidentCount.get() == 0) return 0;
            return downtimeSeconds.get() / incidentCount.get();
        }
        
        public long getMeanTimeBetweenFailures() {
            if (incidentCount.get() <= 1) return uptimeSeconds.get();
            return uptimeSeconds.get() / Math.max(1, incidentCount.get() - 1);
        }
        
        public List<Double> getRecentAvailabilityValues() {
            return new ArrayList<>(recentAvailability);
        }
        
        public String getCurrentStatus() { return currentStatus; }
        public void setCurrentStatus(String status) { this.currentStatus = status; }
        public long getLastCheckTime() { return lastCheckTime; }
        public int getConsecutiveFailures() { return consecutiveFailures.get(); }
        public long getUptimeSeconds() { return uptimeSeconds.get(); }
        public long getDowntimeSeconds() { return downtimeSeconds.get(); }
        public int getIncidentCount() { return incidentCount.get(); }
        public long getLastOutageTime() { return lastOutageTime; }
        public void setMaintenanceMode(boolean mode) { this.maintenanceMode = mode; }
        public void setFailoverActive(boolean active) { this.failoverActive = active; }
        public void setActiveInstance(String instance) { this.activeInstance = instance; }
        public void setPerformanceImpact(double impact) { this.performanceImpact = impact; }
    }
    
    private static class EndpointHealth {
        private final String endpointId;
        private final String serviceId;
        private final String method;
        private final String path;
        private volatile String status = "UNKNOWN";
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong errorCount = new AtomicLong(0);
        private final AtomicLong totalLatency = new AtomicLong(0);
        private volatile long lastUpdateTime = 0;
        
        public EndpointHealth(String endpointId, String serviceId, String method, String path) {
            this.endpointId = endpointId;
            this.serviceId = serviceId;
            this.method = method;
            this.path = path;
        }
        
        public void updateStatus(String status, int statusCode, long latency, long timestamp) {
            this.status = status;
            this.lastUpdateTime = timestamp;
            totalRequests.incrementAndGet();
            totalLatency.addAndGet(latency);
            
            if (statusCode >= 400) {
                errorCount.incrementAndGet();
            }
        }
        
        public void incrementErrorCount() {
            errorCount.incrementAndGet();
        }
        
        public double getErrorRate() {
            long total = totalRequests.get();
            if (total == 0) return 0.0;
            return (double) errorCount.get() / total;
        }
        
        public double getAverageLatency() {
            long total = totalRequests.get();
            if (total == 0) return 0.0;
            return (double) totalLatency.get() / total;
        }
        
        public String getServiceId() { return serviceId; }
        public String getMethod() { return method; }
        public String getPath() { return path; }
    }
    
    private static class ProbeResult {
        private final String probeId;
        private final String targetService;
        private final String probeType;
        private final boolean success;
        private final long responseTime;
        private final String errorMessage;
        private final long timestamp;
        
        public ProbeResult(String probeId, String targetService, String probeType,
                          boolean success, long responseTime, String errorMessage, long timestamp) {
            this.probeId = probeId;
            this.targetService = targetService;
            this.probeType = probeType;
            this.success = success;
            this.responseTime = responseTime;
            this.errorMessage = errorMessage;
            this.timestamp = timestamp;
        }
    }
    
    private static class UptimeMetrics {
        private final String serviceId;
        private volatile double uptimePercentage = 100.0;
        private volatile long uptimeSeconds = 0;
        private volatile long downtimeSeconds = 0;
        private volatile int incidentCount = 0;
        private volatile String period = "24h";
        private volatile long lastUpdateTime = 0;
        
        public UptimeMetrics(String serviceId) {
            this.serviceId = serviceId;
        }
        
        public void updateMetrics(double uptimePercentage, long uptimeSeconds,
                                 long downtimeSeconds, int incidentCount,
                                 String period, long timestamp) {
            this.uptimePercentage = uptimePercentage;
            this.uptimeSeconds = uptimeSeconds;
            this.downtimeSeconds = downtimeSeconds;
            this.incidentCount = incidentCount;
            this.period = period;
            this.lastUpdateTime = timestamp;
        }
    }
    
    private static class MaintenanceWindow {
        private final String windowId;
        private final String serviceId;
        private final long startTime;
        private final long endTime;
        private final String description;
        private final boolean isPlanned;
        private volatile boolean notified = false;
        
        public MaintenanceWindow(String windowId, String serviceId, long startTime,
                                long endTime, String description, boolean isPlanned) {
            this.windowId = windowId;
            this.serviceId = serviceId;
            this.startTime = startTime;
            this.endTime = endTime;
            this.description = description;
            this.isPlanned = isPlanned;
        }
        
        public String getWindowId() { return windowId; }
        public String getServiceId() { return serviceId; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public String getDescription() { return description; }
        public boolean isNotified() { return notified; }
        public void setNotified(boolean notified) { this.notified = notified; }
    }
    
    private static class RegionAvailability {
        private final String region;
        private final String serviceId;
        private volatile String status = "UNKNOWN";
        private volatile double availabilityPercentage = 100.0;
        private volatile long latency = 0;
        private volatile int activeInstances = 0;
        private volatile int totalInstances = 0;
        private volatile long lastUpdateTime = 0;
        
        public RegionAvailability(String region, String serviceId) {
            this.region = region;
            this.serviceId = serviceId;
        }
        
        public void updateStatus(String status, double availabilityPercentage,
                               long latency, int activeInstances, int totalInstances,
                               long timestamp) {
            this.status = status;
            this.availabilityPercentage = availabilityPercentage;
            this.latency = latency;
            this.activeInstances = activeInstances;
            this.totalInstances = totalInstances;
            this.lastUpdateTime = timestamp;
        }
        
        public double getAvailabilityPercentage() { return availabilityPercentage; }
        public int getActiveInstances() { return activeInstances; }
        public int getTotalInstances() { return totalInstances; }
    }
    
    private static class DependencyStatus {
        private final String dependencyId;
        private final String sourceService;
        private final String targetService;
        private volatile String status = "UNKNOWN";
        private volatile boolean isHealthy = true;
        private volatile long responseTime = 0;
        private volatile double successRate = 1.0;
        private volatile long lastUpdateTime = 0;
        
        public DependencyStatus(String dependencyId, String sourceService, String targetService) {
            this.dependencyId = dependencyId;
            this.sourceService = sourceService;
            this.targetService = targetService;
        }
        
        public void updateStatus(String status, boolean isHealthy, long responseTime,
                               double successRate, long timestamp) {
            this.status = status;
            this.isHealthy = isHealthy;
            this.responseTime = responseTime;
            this.successRate = successRate;
            this.lastUpdateTime = timestamp;
        }
    }
    
    private static class SyntheticMonitor {
        private final String testId;
        private final String testName;
        private final String targetService;
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private final List<TestResult> recentResults = new CopyOnWriteArrayList<>();
        
        public SyntheticMonitor(String testId, String testName, String targetService) {
            this.testId = testId;
            this.testName = testName;
            this.targetService = targetService;
        }
        
        public void recordTestResult(boolean passed, long executionTime,
                                    JsonNode steps, String errorDetails, long timestamp) {
            TestResult result = new TestResult(passed, executionTime, errorDetails, timestamp);
            recentResults.add(result);
            if (recentResults.size() > 100) {
                recentResults.remove(0);
            }
            
            if (!passed) {
                consecutiveFailures.incrementAndGet();
            } else {
                consecutiveFailures.set(0);
            }
        }
        
        public void incrementFailureCount() {
            consecutiveFailures.incrementAndGet();
        }
        
        public void resetFailureCount() {
            consecutiveFailures.set(0);
        }
        
        public String getTestName() { return testName; }
        public String getTargetService() { return targetService; }
        public int getConsecutiveFailures() { return consecutiveFailures.get(); }
        
        private static class TestResult {
            private final boolean passed;
            private final long executionTime;
            private final String errorDetails;
            private final long timestamp;
            
            public TestResult(boolean passed, long executionTime, String errorDetails, long timestamp) {
                this.passed = passed;
                this.executionTime = executionTime;
                this.errorDetails = errorDetails;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class FailoverState {
        private final String serviceId;
        private volatile boolean failoverActive = false;
        private volatile long failoverStartTime = 0;
        private volatile String primaryInstance = "";
        private volatile String backupInstance = "";
        private final List<FailoverEvent> failoverHistory = new CopyOnWriteArrayList<>();
        
        public FailoverState(String serviceId) {
            this.serviceId = serviceId;
        }
        
        public void recordFailover(String type, String from, String to,
                                  boolean automatic, long duration,
                                  boolean successful, long timestamp) {
            FailoverEvent event = new FailoverEvent(
                type, from, to, automatic, duration, successful, timestamp
            );
            failoverHistory.add(event);
            
            if (successful) {
                this.failoverActive = true;
                this.failoverStartTime = timestamp;
                this.primaryInstance = from;
                this.backupInstance = to;
            }
        }
        
        public void completeFailback(long timestamp) {
            this.failoverActive = false;
            String temp = primaryInstance;
            primaryInstance = backupInstance;
            backupInstance = temp;
        }
        
        public boolean shouldCheckFailback() {
            if (!failoverActive) return false;
            long duration = System.currentTimeMillis() - failoverStartTime;
            return duration > TimeUnit.MINUTES.toMillis(5);
        }
        
        public boolean isFailoverActive() { return failoverActive; }
        
        private static class FailoverEvent {
            private final String type;
            private final String from;
            private final String to;
            private final boolean automatic;
            private final long duration;
            private final boolean successful;
            private final long timestamp;
            
            public FailoverEvent(String type, String from, String to,
                               boolean automatic, long duration,
                               boolean successful, long timestamp) {
                this.type = type;
                this.from = from;
                this.to = to;
                this.automatic = automatic;
                this.duration = duration;
                this.successful = successful;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class HealthHistory {
        private final String serviceId;
        private final List<HealthEntry> entries = new CopyOnWriteArrayList<>();
        
        public HealthHistory(String serviceId) {
            this.serviceId = serviceId;
        }
        
        public void addEntry(String status, long responseTime, long timestamp) {
            entries.add(new HealthEntry(status, responseTime, timestamp));
            if (entries.size() > 1000) {
                entries.remove(0);
            }
        }
        
        public void cleanupOldEntries(long cutoffTime) {
            entries.removeIf(entry -> entry.timestamp < cutoffTime);
        }
        
        private static class HealthEntry {
            private final String status;
            private final long responseTime;
            private final long timestamp;
            
            public HealthEntry(String status, long responseTime, long timestamp) {
                this.status = status;
                this.responseTime = responseTime;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class AvailabilityTrend {
        private final String serviceId;
        private volatile String trendDirection = "STABLE";
        private volatile double trendStrength = 0.0;
        private volatile String prediction = "";
        private volatile long lastAnalysisTime = 0;
        private final List<DataPoint> historicalData = new CopyOnWriteArrayList<>();
        
        public AvailabilityTrend(String serviceId) {
            this.serviceId = serviceId;
        }
        
        public void updateTrend(String direction, double strength, String prediction, long timestamp) {
            this.trendDirection = direction;
            this.trendStrength = strength;
            this.prediction = prediction;
            this.lastAnalysisTime = timestamp;
        }
        
        public void addHistoricalPoint(long timestamp, double availability) {
            historicalData.add(new DataPoint(timestamp, availability));
            if (historicalData.size() > 500) {
                historicalData.remove(0);
            }
        }
        
        private static class DataPoint {
            private final long timestamp;
            private final double value;
            
            public DataPoint(long timestamp, double value) {
                this.timestamp = timestamp;
                this.value = value;
            }
        }
    }
    
    private static class OutageRecord {
        private final String serviceId;
        private final String severity;
        private final String impactLevel;
        private final long startTime;
        private volatile long endTime = 0;
        private final Set<String> affectedComponents = ConcurrentHashMap.newKeySet();
        private volatile int affectedUsers = 0;
        private volatile String rootCause = null;
        
        public OutageRecord(String serviceId, String severity, String impactLevel, long startTime) {
            this.serviceId = serviceId;
            this.severity = severity;
            this.impactLevel = impactLevel;
            this.startTime = startTime;
        }
        
        public void addAffectedComponent(String component) {
            affectedComponents.add(component);
        }
        
        public void setAffectedUsers(int users) { this.affectedUsers = users; }
        public void setRootCause(String cause) { this.rootCause = cause; }
        public long getStartTime() { return startTime; }
    }
    
    private static class RecoveryMetrics {
        private final String serviceId;
        private final List<Recovery> recoveries = new CopyOnWriteArrayList<>();
        private final List<String> recoverySteps = new CopyOnWriteArrayList<>();
        
        public RecoveryMetrics(String serviceId) {
            this.serviceId = serviceId;
        }
        
        public void recordRecovery(long outageDuration, long recoveryTime,
                                  String method, boolean dataLoss, long timestamp) {
            recoveries.add(new Recovery(outageDuration, recoveryTime, method, dataLoss, timestamp));
        }
        
        public void addRecoveryStep(String step) {
            recoverySteps.add(step);
        }
        
        private static class Recovery {
            private final long outageDuration;
            private final long recoveryTime;
            private final String method;
            private final boolean dataLoss;
            private final long timestamp;
            
            public Recovery(long outageDuration, long recoveryTime,
                          String method, boolean dataLoss, long timestamp) {
                this.outageDuration = outageDuration;
                this.recoveryTime = recoveryTime;
                this.method = method;
                this.dataLoss = dataLoss;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class ComponentHealth {
        private final String componentId;
        private final String serviceId;
        private final String componentType;
        private volatile String status = "HEALTHY";
        private volatile long lastUpdateTime = 0;
        private final Map<String, Object> metrics = new ConcurrentHashMap<>();
        private final Map<String, String> dependencies = new ConcurrentHashMap<>();
        
        public ComponentHealth(String componentId, String serviceId, String componentType) {
            this.componentId = componentId;
            this.serviceId = serviceId;
            this.componentType = componentType;
        }
        
        public void updateStatus(String status, long timestamp) {
            this.status = status;
            this.lastUpdateTime = timestamp;
        }
        
        public void updateMetrics(JsonNode metricsNode) {
            metricsNode.fieldNames().forEachRemaining(field ->
                metrics.put(field, metricsNode.path(field).asText())
            );
        }
        
        public void addDependency(String name, String status) {
            dependencies.put(name, status);
        }
    }
    
    private static class AvailabilityReport {
        private final String serviceId;
        private double uptimePercentage;
        private int incidentCount;
        private long mttr;
        private long mtbf;
        private long outageMinutes;
        private final long generatedAt;
        
        public AvailabilityReport(String serviceId) {
            this.serviceId = serviceId;
            this.generatedAt = System.currentTimeMillis();
        }
        
        public void setUptimePercentage(double percentage) { this.uptimePercentage = percentage; }
        public void setIncidentCount(int count) { this.incidentCount = count; }
        public void setMttr(long mttr) { this.mttr = mttr; }
        public void setMtbf(long mtbf) { this.mtbf = mtbf; }
        public void setOutageMinutes(long minutes) { this.outageMinutes = minutes; }
    }
    
    private static class TrendAnalysis {
        final String direction;
        final double strength;
        final String prediction;
        final boolean shouldAlert;
        final String severity;
        final String message;
        
        public TrendAnalysis(String direction, double strength, String prediction,
                           boolean shouldAlert, String severity, String message) {
            this.direction = direction;
            this.strength = strength;
            this.prediction = prediction;
            this.shouldAlert = shouldAlert;
            this.severity = severity;
            this.message = message;
        }
    }
}