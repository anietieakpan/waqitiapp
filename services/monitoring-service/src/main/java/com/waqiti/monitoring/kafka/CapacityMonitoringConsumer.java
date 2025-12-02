package com.waqiti.monitoring.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.monitoring.model.CapacityMetric;
import com.waqiti.monitoring.model.ResourceCapacity;
import com.waqiti.monitoring.model.ScalingRecommendation;
import com.waqiti.monitoring.model.CapacityForecast;
import com.waqiti.monitoring.service.CapacityPlanningService;
import com.waqiti.monitoring.service.AutoScalingService;
import com.waqiti.monitoring.service.ResourceOptimizationService;
import com.waqiti.monitoring.service.AlertingService;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.exception.ValidationException;
import com.waqiti.common.exception.SystemException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class CapacityMonitoringConsumer {

    private static final Logger logger = LoggerFactory.getLogger(CapacityMonitoringConsumer.class);
    private static final String CONSUMER_NAME = "capacity-monitoring-consumer";
    private static final String DLQ_TOPIC = "capacity-monitoring-dlq";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int PROCESSING_TIMEOUT_SECONDS = 10;

    private final ObjectMapper objectMapper;
    private final CapacityPlanningService capacityPlanningService;
    private final AutoScalingService autoScalingService;
    private final ResourceOptimizationService resourceOptimizationService;
    private final AlertingService alertingService;
    private final MetricsService metricsService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${kafka.consumer.capacity-monitoring.enabled:true}")
    private boolean consumerEnabled;

    @Value("${capacity.monitoring.interval-seconds:60}")
    private int monitoringIntervalSeconds;

    @Value("${capacity.threshold.warning:0.75}")
    private double warningThreshold;

    @Value("${capacity.threshold.critical:0.90}")
    private double criticalThreshold;

    @Value("${capacity.autoscaling.enabled:true}")
    private boolean autoScalingEnabled;

    @Value("${capacity.autoscaling.cooldown-minutes:5}")
    private int scalingCooldownMinutes;

    @Value("${capacity.prediction.enabled:true}")
    private boolean predictionEnabled;

    @Value("${capacity.prediction.horizon-hours:24}")
    private int predictionHorizonHours;

    @Value("${capacity.optimization.enabled:true}")
    private boolean optimizationEnabled;

    private Counter processedCounter;
    private Counter errorCounter;
    private Counter dlqCounter;
    private Timer processingTimer;
    private Counter scalingCounter;
    private Gauge capacityUtilizationGauge;
    private DistributionSummary resourceUtilization;

    private final ConcurrentHashMap<String, ResourceCapacity> resourceCapacities = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CapacityMetric> currentMetrics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> lastScalingTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<CapacityMetric>> historicalMetrics = new ConcurrentHashMap<>();
    private final AtomicLong totalCapacityChecks = new AtomicLong(0);
    private final AtomicReference<Double> overallUtilization = new AtomicReference<>(0.0);
    private ScheduledExecutorService scheduledExecutor;
    private ExecutorService capacityProcessingExecutor;

    public CapacityMonitoringConsumer(ObjectMapper objectMapper, 
                                      CapacityPlanningService capacityPlanningService,
                                      AutoScalingService autoScalingService,
                                      ResourceOptimizationService resourceOptimizationService,
                                      AlertingService alertingService,
                                      MetricsService metricsService,
                                      KafkaTemplate<String, Object> kafkaTemplate,
                                      MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.capacityPlanningService = capacityPlanningService;
        this.autoScalingService = autoScalingService;
        this.resourceOptimizationService = resourceOptimizationService;
        this.alertingService = alertingService;
        this.metricsService = metricsService;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void initializeMetrics() {
        this.processedCounter = Counter.builder("capacity_monitoring_processed_total")
                .description("Total processed capacity monitoring events")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.errorCounter = Counter.builder("capacity_monitoring_errors_total")
                .description("Total capacity monitoring processing errors")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.dlqCounter = Counter.builder("capacity_monitoring_dlq_total")
                .description("Total capacity monitoring events sent to DLQ")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.processingTimer = Timer.builder("capacity_monitoring_processing_duration")
                .description("Capacity monitoring processing duration")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.scalingCounter = Counter.builder("capacity_scaling_triggered_total")
                .description("Total scaling operations triggered")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.capacityUtilizationGauge = Gauge.builder("capacity_overall_utilization", overallUtilization, AtomicReference::get)
                .description("Overall capacity utilization percentage")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.resourceUtilization = DistributionSummary.builder("capacity_resource_utilization")
                .description("Resource utilization distribution")
                .tag("consumer", CONSUMER_NAME)
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry);

        this.scheduledExecutor = Executors.newScheduledThreadPool(4);
        scheduledExecutor.scheduleWithFixedDelay(this::performCapacityChecks, 
                monitoringIntervalSeconds, monitoringIntervalSeconds, TimeUnit.SECONDS);
        scheduledExecutor.scheduleWithFixedDelay(this::performCapacityPrediction, 
                30, 30, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::performResourceOptimization, 
                1, 1, TimeUnit.HOURS);
        scheduledExecutor.scheduleWithFixedDelay(this::cleanupOldMetrics, 
                6, 6, TimeUnit.HOURS);

        this.capacityProcessingExecutor = Executors.newFixedThreadPool(6);

        logger.info("CapacityMonitoringConsumer initialized with monitoring interval: {} seconds", 
                   monitoringIntervalSeconds);
    }

    @KafkaListener(
        topics = "${kafka.topics.capacity-monitoring:capacity-monitoring}",
        groupId = "${kafka.consumer.group-id:monitoring-service-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "capacity-monitoring-circuit-breaker", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "capacity-monitoring-retry")
    public void processCapacityMonitoring(
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
                logger.warn("Capacity monitoring consumer is disabled, skipping message processing");
                acknowledgment.acknowledge();
                return;
            }

            logger.debug("Processing capacity monitoring message: messageId={}, topic={}, partition={}, offset={}",
                    messageId, topic, partition, offset);

            if (!StringUtils.hasText(message)) {
                logger.warn("Received empty or null message, skipping processing");
                acknowledgment.acknowledge();
                return;
            }

            JsonNode messageNode = objectMapper.readTree(message);
            
            if (!isValidCapacityMessage(messageNode)) {
                logger.error("Invalid capacity message format: {}", message);
                sendToDlq(message, topic, "Invalid message format", null, correlationId, traceId);
                acknowledgment.acknowledge();
                return;
            }

            String eventType = messageNode.get("eventType").asText();
            
            CompletableFuture<Void> processingFuture = CompletableFuture.runAsync(() -> {
                try {
                    switch (eventType) {
                        case "RESOURCE_UTILIZATION":
                            handleResourceUtilization(messageNode, correlationId, traceId);
                            break;
                        case "CPU_UTILIZATION":
                            handleCpuUtilization(messageNode, correlationId, traceId);
                            break;
                        case "MEMORY_UTILIZATION":
                            handleMemoryUtilization(messageNode, correlationId, traceId);
                            break;
                        case "DISK_UTILIZATION":
                            handleDiskUtilization(messageNode, correlationId, traceId);
                            break;
                        case "NETWORK_UTILIZATION":
                            handleNetworkUtilization(messageNode, correlationId, traceId);
                            break;
                        case "CONTAINER_UTILIZATION":
                            handleContainerUtilization(messageNode, correlationId, traceId);
                            break;
                        case "DATABASE_CAPACITY":
                            handleDatabaseCapacity(messageNode, correlationId, traceId);
                            break;
                        case "QUEUE_CAPACITY":
                            handleQueueCapacity(messageNode, correlationId, traceId);
                            break;
                        case "THREAD_POOL_CAPACITY":
                            handleThreadPoolCapacity(messageNode, correlationId, traceId);
                            break;
                        case "SCALING_REQUEST":
                            handleScalingRequest(messageNode, correlationId, traceId);
                            break;
                        case "CAPACITY_FORECAST":
                            handleCapacityForecast(messageNode, correlationId, traceId);
                            break;
                        case "RESOURCE_ALLOCATION":
                            handleResourceAllocation(messageNode, correlationId, traceId);
                            break;
                        case "CAPACITY_BREACH":
                            handleCapacityBreach(messageNode, correlationId, traceId);
                            break;
                        case "OPTIMIZATION_RECOMMENDATION":
                            handleOptimizationRecommendation(messageNode, correlationId, traceId);
                            break;
                        case "CAPACITY_RESERVATION":
                            handleCapacityReservation(messageNode, correlationId, traceId);
                            break;
                        default:
                            logger.warn("Unknown capacity event type: {}", eventType);
                    }
                } catch (Exception e) {
                    logger.error("Error processing capacity event: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }, capacityProcessingExecutor).orTimeout(PROCESSING_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            processingFuture.get();

            totalCapacityChecks.incrementAndGet();
            processedCounter.increment();
            acknowledgment.acknowledge();

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse capacity message: messageId={}, error={}", messageId, e.getMessage());
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } catch (Exception e) {
            logger.error("Unexpected error processing capacity: messageId={}, error={}", messageId, e.getMessage(), e);
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }

    @Transactional
    private void handleResourceUtilization(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String resourceId = messageNode.get("resourceId").asText();
            String resourceType = messageNode.get("resourceType").asText();
            double utilization = messageNode.get("utilization").asDouble();
            double capacity = messageNode.get("capacity").asDouble();
            double used = messageNode.get("used").asDouble();
            LocalDateTime timestamp = LocalDateTime.parse(messageNode.get("timestamp").asText());
            
            CapacityMetric metric = new CapacityMetric();
            metric.setResourceId(resourceId);
            metric.setResourceType(resourceType);
            metric.setUtilization(utilization);
            metric.setCapacity(capacity);
            metric.setUsed(used);
            metric.setAvailable(capacity - used);
            metric.setTimestamp(timestamp);
            
            currentMetrics.put(resourceId, metric);
            recordHistoricalMetric(resourceId, metric);
            
            resourceUtilization.record(utilization * 100);
            
            evaluateCapacityThresholds(metric);
            
            if (utilization > warningThreshold) {
                handleHighUtilization(metric);
            }
            
            if (autoScalingEnabled && shouldScale(metric)) {
                triggerAutoScaling(metric);
            }
            
            capacityPlanningService.recordMetric(metric);
            
            logger.debug("Resource utilization: id={}, type={}, utilization={}%, used={}/{}", 
                       resourceId, resourceType, utilization * 100, used, capacity);
            
        } catch (Exception e) {
            logger.error("Error handling resource utilization: {}", e.getMessage(), e);
            throw new SystemException("Failed to process resource utilization", e);
        }
    }

    @Transactional
    private void handleCpuUtilization(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String instanceId = messageNode.get("instanceId").asText();
            double cpuUsage = messageNode.get("cpuUsage").asDouble();
            int cores = messageNode.get("cores").asInt();
            double loadAverage = messageNode.get("loadAverage").asDouble();
            
            CapacityMetric metric = new CapacityMetric();
            metric.setResourceId(instanceId);
            metric.setResourceType("CPU");
            metric.setUtilization(cpuUsage);
            metric.setCapacity(cores);
            metric.setUsed(cpuUsage * cores);
            metric.setTimestamp(LocalDateTime.now());
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("loadAverage", loadAverage);
            metadata.put("cores", cores);
            
            if (messageNode.has("processCount")) {
                metadata.put("processCount", messageNode.get("processCount").asInt());
            }
            
            if (messageNode.has("topProcesses")) {
                JsonNode processes = messageNode.get("topProcesses");
                List<String> topProcesses = new ArrayList<>();
                processes.forEach(p -> topProcesses.add(p.asText()));
                metadata.put("topProcesses", topProcesses);
            }
            
            metric.setMetadata(metadata);
            
            currentMetrics.put(instanceId + ":CPU", metric);
            
            if (cpuUsage > criticalThreshold) {
                createCpuAlert(instanceId, cpuUsage, loadAverage);
                
                if (autoScalingEnabled) {
                    recommendCpuScaling(instanceId, cpuUsage, cores);
                }
            }
            
            logger.info("CPU utilization: instance={}, usage={}%, cores={}, load={}", 
                       instanceId, cpuUsage * 100, cores, loadAverage);
            
        } catch (Exception e) {
            logger.error("Error handling CPU utilization: {}", e.getMessage(), e);
            throw new SystemException("Failed to process CPU utilization", e);
        }
    }

    @Transactional
    private void handleMemoryUtilization(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String instanceId = messageNode.get("instanceId").asText();
            long totalMemoryMB = messageNode.get("totalMemoryMB").asLong();
            long usedMemoryMB = messageNode.get("usedMemoryMB").asLong();
            long freeMemoryMB = messageNode.get("freeMemoryMB").asLong();
            double memoryUsage = (double) usedMemoryMB / totalMemoryMB;
            
            CapacityMetric metric = new CapacityMetric();
            metric.setResourceId(instanceId);
            metric.setResourceType("MEMORY");
            metric.setUtilization(memoryUsage);
            metric.setCapacity(totalMemoryMB);
            metric.setUsed(usedMemoryMB);
            metric.setAvailable(freeMemoryMB);
            metric.setTimestamp(LocalDateTime.now());
            
            if (messageNode.has("swapUsedMB")) {
                long swapUsed = messageNode.get("swapUsedMB").asLong();
                if (swapUsed > 0) {
                    handleSwapUsage(instanceId, swapUsed);
                }
            }
            
            currentMetrics.put(instanceId + ":MEMORY", metric);
            
            if (memoryUsage > criticalThreshold) {
                createMemoryAlert(instanceId, memoryUsage, usedMemoryMB, totalMemoryMB);
                analyzeMemoryConsumers(instanceId);
            }
            
            if (freeMemoryMB < 500) {
                triggerMemoryOptimization(instanceId);
            }
            
            logger.info("Memory utilization: instance={}, usage={}%, used={}MB, free={}MB", 
                       instanceId, memoryUsage * 100, usedMemoryMB, freeMemoryMB);
            
        } catch (Exception e) {
            logger.error("Error handling memory utilization: {}", e.getMessage(), e);
            throw new SystemException("Failed to process memory utilization", e);
        }
    }

    @Transactional
    private void handleDiskUtilization(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String volumeId = messageNode.get("volumeId").asText();
            String mountPoint = messageNode.get("mountPoint").asText();
            long totalSpaceGB = messageNode.get("totalSpaceGB").asLong();
            long usedSpaceGB = messageNode.get("usedSpaceGB").asLong();
            long freeSpaceGB = messageNode.get("freeSpaceGB").asLong();
            double diskUsage = (double) usedSpaceGB / totalSpaceGB;
            
            CapacityMetric metric = new CapacityMetric();
            metric.setResourceId(volumeId);
            metric.setResourceType("DISK");
            metric.setUtilization(diskUsage);
            metric.setCapacity(totalSpaceGB);
            metric.setUsed(usedSpaceGB);
            metric.setAvailable(freeSpaceGB);
            metric.setTimestamp(LocalDateTime.now());
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("mountPoint", mountPoint);
            
            if (messageNode.has("iops")) {
                metadata.put("iops", messageNode.get("iops").asInt());
            }
            
            if (messageNode.has("throughputMBps")) {
                metadata.put("throughputMBps", messageNode.get("throughputMBps").asDouble());
            }
            
            metric.setMetadata(metadata);
            
            currentMetrics.put(volumeId, metric);
            
            if (diskUsage > warningThreshold) {
                predictDiskExhaustion(volumeId, usedSpaceGB, totalSpaceGB);
            }
            
            if (freeSpaceGB < 10) {
                createDiskSpaceAlert(volumeId, mountPoint, freeSpaceGB);
                initiateCleanup(volumeId, mountPoint);
            }
            
            logger.info("Disk utilization: volume={}, mount={}, usage={}%, free={}GB", 
                       volumeId, mountPoint, diskUsage * 100, freeSpaceGB);
            
        } catch (Exception e) {
            logger.error("Error handling disk utilization: {}", e.getMessage(), e);
            throw new SystemException("Failed to process disk utilization", e);
        }
    }

    @Transactional
    private void handleNetworkUtilization(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String interfaceId = messageNode.get("interfaceId").asText();
            double bandwidthGbps = messageNode.get("bandwidthGbps").asDouble();
            double inboundGbps = messageNode.get("inboundGbps").asDouble();
            double outboundGbps = messageNode.get("outboundGbps").asDouble();
            double utilization = Math.max(inboundGbps, outboundGbps) / bandwidthGbps;
            
            CapacityMetric metric = new CapacityMetric();
            metric.setResourceId(interfaceId);
            metric.setResourceType("NETWORK");
            metric.setUtilization(utilization);
            metric.setCapacity(bandwidthGbps);
            metric.setUsed(inboundGbps + outboundGbps);
            metric.setTimestamp(LocalDateTime.now());
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("inboundGbps", inboundGbps);
            metadata.put("outboundGbps", outboundGbps);
            
            if (messageNode.has("packetLoss")) {
                double packetLoss = messageNode.get("packetLoss").asDouble();
                metadata.put("packetLoss", packetLoss);
                
                if (packetLoss > 0.01) {
                    handlePacketLoss(interfaceId, packetLoss);
                }
            }
            
            if (messageNode.has("latencyMs")) {
                metadata.put("latencyMs", messageNode.get("latencyMs").asDouble());
            }
            
            metric.setMetadata(metadata);
            
            currentMetrics.put(interfaceId, metric);
            
            if (utilization > warningThreshold) {
                analyzeNetworkBottlenecks(interfaceId, inboundGbps, outboundGbps);
            }
            
            logger.debug("Network utilization: interface={}, utilization={}%, in={}Gbps, out={}Gbps", 
                       interfaceId, utilization * 100, inboundGbps, outboundGbps);
            
        } catch (Exception e) {
            logger.error("Error handling network utilization: {}", e.getMessage(), e);
            throw new SystemException("Failed to process network utilization", e);
        }
    }

    @Transactional
    private void handleContainerUtilization(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String containerId = messageNode.get("containerId").asText();
            String containerName = messageNode.get("containerName").asText();
            double cpuUsage = messageNode.get("cpuUsage").asDouble();
            long memoryUsedMB = messageNode.get("memoryUsedMB").asLong();
            long memoryLimitMB = messageNode.get("memoryLimitMB").asLong();
            
            CapacityMetric metric = new CapacityMetric();
            metric.setResourceId(containerId);
            metric.setResourceType("CONTAINER");
            metric.setUtilization(Math.max(cpuUsage, (double) memoryUsedMB / memoryLimitMB));
            metric.setTimestamp(LocalDateTime.now());
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("containerName", containerName);
            metadata.put("cpuUsage", cpuUsage);
            metadata.put("memoryUsedMB", memoryUsedMB);
            metadata.put("memoryLimitMB", memoryLimitMB);
            
            if (messageNode.has("restartCount")) {
                int restartCount = messageNode.get("restartCount").asInt();
                metadata.put("restartCount", restartCount);
                
                if (restartCount > 3) {
                    handleContainerInstability(containerId, containerName, restartCount);
                }
            }
            
            metric.setMetadata(metadata);
            
            currentMetrics.put(containerId, metric);
            
            if (shouldScaleContainer(metric)) {
                recommendContainerScaling(containerId, containerName, metric);
            }
            
            logger.debug("Container utilization: id={}, name={}, cpu={}%, memory={}MB/{}MB", 
                       containerId, containerName, cpuUsage * 100, memoryUsedMB, memoryLimitMB);
            
        } catch (Exception e) {
            logger.error("Error handling container utilization: {}", e.getMessage(), e);
            throw new SystemException("Failed to process container utilization", e);
        }
    }

    @Transactional
    private void handleDatabaseCapacity(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String databaseId = messageNode.get("databaseId").asText();
            String databaseName = messageNode.get("databaseName").asText();
            int activeConnections = messageNode.get("activeConnections").asInt();
            int maxConnections = messageNode.get("maxConnections").asInt();
            long storageSizeGB = messageNode.get("storageSizeGB").asLong();
            long maxStorageGB = messageNode.get("maxStorageGB").asLong();
            
            double connectionUtilization = (double) activeConnections / maxConnections;
            double storageUtilization = (double) storageSizeGB / maxStorageGB;
            
            CapacityMetric metric = new CapacityMetric();
            metric.setResourceId(databaseId);
            metric.setResourceType("DATABASE");
            metric.setUtilization(Math.max(connectionUtilization, storageUtilization));
            metric.setTimestamp(LocalDateTime.now());
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("databaseName", databaseName);
            metadata.put("activeConnections", activeConnections);
            metadata.put("maxConnections", maxConnections);
            metadata.put("storageSizeGB", storageSizeGB);
            metadata.put("maxStorageGB", maxStorageGB);
            
            if (messageNode.has("replicationLag")) {
                long replicationLag = messageNode.get("replicationLag").asLong();
                metadata.put("replicationLag", replicationLag);
                
                if (replicationLag > 1000) {
                    handleReplicationLag(databaseId, databaseName, replicationLag);
                }
            }
            
            metric.setMetadata(metadata);
            
            currentMetrics.put(databaseId, metric);
            
            if (connectionUtilization > 0.8) {
                handleConnectionPoolPressure(databaseId, activeConnections, maxConnections);
            }
            
            if (storageUtilization > 0.85) {
                predictStorageExhaustion(databaseId, storageSizeGB, maxStorageGB);
            }
            
            logger.info("Database capacity: id={}, connections={}/{}, storage={}GB/{}GB", 
                       databaseId, activeConnections, maxConnections, storageSizeGB, maxStorageGB);
            
        } catch (Exception e) {
            logger.error("Error handling database capacity: {}", e.getMessage(), e);
            throw new SystemException("Failed to process database capacity", e);
        }
    }

    @Transactional
    private void handleQueueCapacity(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String queueId = messageNode.get("queueId").asText();
            String queueName = messageNode.get("queueName").asText();
            long currentSize = messageNode.get("currentSize").asLong();
            long maxSize = messageNode.get("maxSize").asLong();
            double throughput = messageNode.get("throughput").asDouble();
            
            double utilization = (double) currentSize / maxSize;
            
            CapacityMetric metric = new CapacityMetric();
            metric.setResourceId(queueId);
            metric.setResourceType("QUEUE");
            metric.setUtilization(utilization);
            metric.setCapacity(maxSize);
            metric.setUsed(currentSize);
            metric.setTimestamp(LocalDateTime.now());
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("queueName", queueName);
            metadata.put("throughput", throughput);
            
            if (messageNode.has("oldestMessageAge")) {
                long oldestAge = messageNode.get("oldestMessageAge").asLong();
                metadata.put("oldestMessageAge", oldestAge);
                
                if (oldestAge > 60000) {
                    handleQueueBacklog(queueId, queueName, oldestAge);
                }
            }
            
            metric.setMetadata(metadata);
            
            currentMetrics.put(queueId, metric);
            
            if (utilization > 0.9) {
                handleQueueNearCapacity(queueId, queueName, currentSize, maxSize);
            }
            
            analyzeQueuePerformance(queueId, currentSize, throughput);
            
            logger.debug("Queue capacity: id={}, name={}, size={}/{}, throughput={}/s", 
                       queueId, queueName, currentSize, maxSize, throughput);
            
        } catch (Exception e) {
            logger.error("Error handling queue capacity: {}", e.getMessage(), e);
            throw new SystemException("Failed to process queue capacity", e);
        }
    }

    @Transactional
    private void handleThreadPoolCapacity(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String poolId = messageNode.get("poolId").asText();
            String poolName = messageNode.get("poolName").asText();
            int activeThreads = messageNode.get("activeThreads").asInt();
            int poolSize = messageNode.get("poolSize").asInt();
            int maxPoolSize = messageNode.get("maxPoolSize").asInt();
            int queueSize = messageNode.get("queueSize").asInt();
            
            double utilization = (double) activeThreads / maxPoolSize;
            
            CapacityMetric metric = new CapacityMetric();
            metric.setResourceId(poolId);
            metric.setResourceType("THREAD_POOL");
            metric.setUtilization(utilization);
            metric.setCapacity(maxPoolSize);
            metric.setUsed(activeThreads);
            metric.setTimestamp(LocalDateTime.now());
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("poolName", poolName);
            metadata.put("poolSize", poolSize);
            metadata.put("queueSize", queueSize);
            
            if (messageNode.has("rejectedTasks")) {
                long rejectedTasks = messageNode.get("rejectedTasks").asLong();
                metadata.put("rejectedTasks", rejectedTasks);
                
                if (rejectedTasks > 0) {
                    handleTaskRejection(poolId, poolName, rejectedTasks);
                }
            }
            
            metric.setMetadata(metadata);
            
            currentMetrics.put(poolId, metric);
            
            if (activeThreads == maxPoolSize && queueSize > 0) {
                handleThreadPoolSaturation(poolId, poolName, queueSize);
            }
            
            logger.debug("Thread pool capacity: id={}, name={}, active={}/{}, queue={}", 
                       poolId, poolName, activeThreads, maxPoolSize, queueSize);
            
        } catch (Exception e) {
            logger.error("Error handling thread pool capacity: {}", e.getMessage(), e);
            throw new SystemException("Failed to process thread pool capacity", e);
        }
    }

    @Transactional
    private void handleScalingRequest(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String resourceId = messageNode.get("resourceId").asText();
            String resourceType = messageNode.get("resourceType").asText();
            String scalingAction = messageNode.get("scalingAction").asText();
            int targetCapacity = messageNode.get("targetCapacity").asInt();
            String reason = messageNode.get("reason").asText();
            
            if (!canScale(resourceId)) {
                logger.warn("Scaling request rejected - cooldown period active: {}", resourceId);
                return;
            }
            
            ScalingRecommendation recommendation = new ScalingRecommendation();
            recommendation.setResourceId(resourceId);
            recommendation.setResourceType(resourceType);
            recommendation.setScalingAction(scalingAction);
            recommendation.setTargetCapacity(targetCapacity);
            recommendation.setReason(reason);
            recommendation.setTimestamp(LocalDateTime.now());
            
            if (autoScalingEnabled) {
                executeScaling(recommendation);
                scalingCounter.increment();
                lastScalingTime.put(resourceId, LocalDateTime.now());
            } else {
                sendScalingRecommendation(recommendation);
            }
            
            logger.info("Scaling request: resource={}, type={}, action={}, target={}, reason={}", 
                       resourceId, resourceType, scalingAction, targetCapacity, reason);
            
        } catch (Exception e) {
            logger.error("Error handling scaling request: {}", e.getMessage(), e);
            throw new SystemException("Failed to process scaling request", e);
        }
    }

    @Transactional
    private void handleCapacityForecast(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String resourceId = messageNode.get("resourceId").asText();
            String resourceType = messageNode.get("resourceType").asText();
            LocalDateTime forecastTime = LocalDateTime.parse(messageNode.get("forecastTime").asText());
            double predictedUtilization = messageNode.get("predictedUtilization").asDouble();
            double confidence = messageNode.get("confidence").asDouble();
            
            CapacityForecast forecast = new CapacityForecast();
            forecast.setResourceId(resourceId);
            forecast.setResourceType(resourceType);
            forecast.setForecastTime(forecastTime);
            forecast.setPredictedUtilization(predictedUtilization);
            forecast.setConfidence(confidence);
            
            if (messageNode.has("predictedExhaustionTime")) {
                forecast.setPredictedExhaustionTime(
                    LocalDateTime.parse(messageNode.get("predictedExhaustionTime").asText()));
            }
            
            capacityPlanningService.saveForecast(forecast);
            
            if (predictedUtilization > criticalThreshold && confidence > 0.8) {
                createPredictiveCapacityAlert(forecast);
                
                if (autoScalingEnabled) {
                    planProactiveScaling(forecast);
                }
            }
            
            logger.info("Capacity forecast: resource={}, predicted={}% at {}, confidence={}", 
                       resourceId, predictedUtilization * 100, forecastTime, confidence);
            
        } catch (Exception e) {
            logger.error("Error handling capacity forecast: {}", e.getMessage(), e);
            throw new SystemException("Failed to process capacity forecast", e);
        }
    }

    @Transactional
    private void handleResourceAllocation(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String allocationId = messageNode.get("allocationId").asText();
            String resourceType = messageNode.get("resourceType").asText();
            String requestedBy = messageNode.get("requestedBy").asText();
            double requestedCapacity = messageNode.get("requestedCapacity").asDouble();
            
            ResourceCapacity available = findAvailableCapacity(resourceType, requestedCapacity);
            
            if (available != null) {
                allocateResource(allocationId, available, requestedCapacity, requestedBy);
            } else {
                handleAllocationFailure(allocationId, resourceType, requestedCapacity, requestedBy);
            }
            
            logger.info("Resource allocation: id={}, type={}, requested={}, by={}", 
                       allocationId, resourceType, requestedCapacity, requestedBy);
            
        } catch (Exception e) {
            logger.error("Error handling resource allocation: {}", e.getMessage(), e);
            throw new SystemException("Failed to process resource allocation", e);
        }
    }

    @Transactional
    private void handleCapacityBreach(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String resourceId = messageNode.get("resourceId").asText();
            String resourceType = messageNode.get("resourceType").asText();
            double currentUtilization = messageNode.get("currentUtilization").asDouble();
            double threshold = messageNode.get("threshold").asDouble();
            String severity = messageNode.get("severity").asText();
            
            Map<String, Object> breach = new HashMap<>();
            breach.put("breachId", UUID.randomUUID().toString());
            breach.put("resourceId", resourceId);
            breach.put("resourceType", resourceType);
            breach.put("currentUtilization", currentUtilization);
            breach.put("threshold", threshold);
            breach.put("severity", severity);
            breach.put("timestamp", LocalDateTime.now().toString());
            
            kafkaTemplate.send("capacity-breaches", breach);
            
            if ("CRITICAL".equals(severity)) {
                initiateEmergencyScaling(resourceId, resourceType, currentUtilization);
            }
            
            analyzeBreachPattern(resourceId, resourceType);
            
            logger.error("Capacity breach: resource={}, type={}, utilization={}%, threshold={}%, severity={}", 
                        resourceId, resourceType, currentUtilization * 100, threshold * 100, severity);
            
        } catch (Exception e) {
            logger.error("Error handling capacity breach: {}", e.getMessage(), e);
            throw new SystemException("Failed to process capacity breach", e);
        }
    }

    @Transactional
    private void handleOptimizationRecommendation(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String resourceId = messageNode.get("resourceId").asText();
            String resourceType = messageNode.get("resourceType").asText();
            String recommendationType = messageNode.get("recommendationType").asText();
            String recommendation = messageNode.get("recommendation").asText();
            double potentialSavings = messageNode.get("potentialSavings").asDouble();
            
            Map<String, Object> optimization = new HashMap<>();
            optimization.put("resourceId", resourceId);
            optimization.put("resourceType", resourceType);
            optimization.put("recommendationType", recommendationType);
            optimization.put("recommendation", recommendation);
            optimization.put("potentialSavings", potentialSavings);
            optimization.put("timestamp", LocalDateTime.now().toString());
            
            if (messageNode.has("implementationSteps")) {
                JsonNode steps = messageNode.get("implementationSteps");
                List<String> implementationSteps = new ArrayList<>();
                steps.forEach(step -> implementationSteps.add(step.asText()));
                optimization.put("implementationSteps", implementationSteps);
            }
            
            resourceOptimizationService.saveRecommendation(optimization);
            
            if (optimizationEnabled && potentialSavings > 1000) {
                evaluateAutoOptimization(optimization);
            }
            
            logger.info("Optimization recommendation: resource={}, type={}, recommendation={}, savings=${}", 
                       resourceId, recommendationType, recommendation, potentialSavings);
            
        } catch (Exception e) {
            logger.error("Error handling optimization recommendation: {}", e.getMessage(), e);
            throw new SystemException("Failed to process optimization recommendation", e);
        }
    }

    @Transactional
    private void handleCapacityReservation(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String reservationId = messageNode.get("reservationId").asText();
            String resourceType = messageNode.get("resourceType").asText();
            double reservedCapacity = messageNode.get("reservedCapacity").asDouble();
            LocalDateTime startTime = LocalDateTime.parse(messageNode.get("startTime").asText());
            LocalDateTime endTime = LocalDateTime.parse(messageNode.get("endTime").asText());
            String reservedBy = messageNode.get("reservedBy").asText();
            
            Map<String, Object> reservation = new HashMap<>();
            reservation.put("reservationId", reservationId);
            reservation.put("resourceType", resourceType);
            reservation.put("reservedCapacity", reservedCapacity);
            reservation.put("startTime", startTime.toString());
            reservation.put("endTime", endTime.toString());
            reservation.put("reservedBy", reservedBy);
            reservation.put("status", "ACTIVE");
            
            capacityPlanningService.createReservation(reservation);
            
            updateAvailableCapacity(resourceType, reservedCapacity, false);
            
            logger.info("Capacity reservation: id={}, type={}, capacity={}, from={}, to={}, by={}", 
                       reservationId, resourceType, reservedCapacity, startTime, endTime, reservedBy);
            
        } catch (Exception e) {
            logger.error("Error handling capacity reservation: {}", e.getMessage(), e);
            throw new SystemException("Failed to process capacity reservation", e);
        }
    }

    private void performCapacityChecks() {
        try {
            logger.debug("Performing scheduled capacity checks for {} resources", currentMetrics.size());
            
            calculateOverallUtilization();
            
            for (CapacityMetric metric : currentMetrics.values()) {
                evaluateCapacityHealth(metric);
            }
            
            identifyCapacityTrends();
            
        } catch (Exception e) {
            logger.error("Error performing capacity checks: {}", e.getMessage(), e);
        }
    }

    private void performCapacityPrediction() {
        if (!predictionEnabled) {
            return;
        }
        
        try {
            logger.info("Performing capacity prediction for {} resources", historicalMetrics.size());
            
            for (Map.Entry<String, List<CapacityMetric>> entry : historicalMetrics.entrySet()) {
                String resourceId = entry.getKey();
                List<CapacityMetric> history = entry.getValue();
                
                if (history.size() >= 10) {
                    CapacityForecast forecast = capacityPlanningService.predictCapacity(
                        resourceId, history, predictionHorizonHours);
                    
                    if (forecast.getPredictedUtilization() > criticalThreshold) {
                        createPredictiveCapacityAlert(forecast);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error performing capacity prediction: {}", e.getMessage(), e);
        }
    }

    private void performResourceOptimization() {
        if (!optimizationEnabled) {
            return;
        }
        
        try {
            logger.info("Performing resource optimization analysis");
            
            List<Map<String, Object>> recommendations = 
                resourceOptimizationService.analyzeResourceUsage(currentMetrics);
            
            for (Map<String, Object> recommendation : recommendations) {
                kafkaTemplate.send("optimization-recommendations", recommendation);
            }
            
        } catch (Exception e) {
            logger.error("Error performing resource optimization: {}", e.getMessage(), e);
        }
    }

    private void cleanupOldMetrics() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(48);
            
            for (List<CapacityMetric> metrics : historicalMetrics.values()) {
                metrics.removeIf(m -> m.getTimestamp().isBefore(cutoff));
            }
            
            logger.debug("Cleaned up old capacity metrics older than {}", cutoff);
            
        } catch (Exception e) {
            logger.error("Error cleaning up old metrics: {}", e.getMessage(), e);
        }
    }

    // Helper methods
    private boolean isValidCapacityMessage(JsonNode messageNode) {
        return messageNode != null &&
               messageNode.has("eventType") && 
               StringUtils.hasText(messageNode.get("eventType").asText());
    }

    private void recordHistoricalMetric(String resourceId, CapacityMetric metric) {
        historicalMetrics.computeIfAbsent(resourceId, k -> new CopyOnWriteArrayList<>()).add(metric);
        
        // Keep list size manageable
        List<CapacityMetric> history = historicalMetrics.get(resourceId);
        if (history.size() > 1000) {
            history.remove(0);
        }
    }

    private void evaluateCapacityThresholds(CapacityMetric metric) {
        if (metric.getUtilization() > criticalThreshold) {
            createCapacityAlert(metric, "CRITICAL");
        } else if (metric.getUtilization() > warningThreshold) {
            createCapacityAlert(metric, "WARNING");
        }
    }

    private void handleHighUtilization(CapacityMetric metric) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("resourceId", metric.getResourceId());
        alert.put("resourceType", metric.getResourceType());
        alert.put("utilization", metric.getUtilization());
        alert.put("severity", metric.getUtilization() > criticalThreshold ? "CRITICAL" : "WARNING");
        alert.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("high-utilization-alerts", alert);
    }

    private boolean shouldScale(CapacityMetric metric) {
        return metric.getUtilization() > criticalThreshold && canScale(metric.getResourceId());
    }

    private boolean canScale(String resourceId) {
        LocalDateTime lastScaling = lastScalingTime.get(resourceId);
        if (lastScaling == null) {
            return true;
        }
        
        long minutesSinceLastScaling = ChronoUnit.MINUTES.between(lastScaling, LocalDateTime.now());
        return minutesSinceLastScaling >= scalingCooldownMinutes;
    }

    private void triggerAutoScaling(CapacityMetric metric) {
        ScalingRecommendation recommendation = autoScalingService.calculateScaling(metric);
        
        if (recommendation != null) {
            executeScaling(recommendation);
            lastScalingTime.put(metric.getResourceId(), LocalDateTime.now());
            scalingCounter.increment();
        }
    }

    private void executeScaling(ScalingRecommendation recommendation) {
        autoScalingService.executeScaling(recommendation);
    }

    private void sendScalingRecommendation(ScalingRecommendation recommendation) {
        kafkaTemplate.send("scaling-recommendations", recommendation);
    }

    private void createCapacityAlert(CapacityMetric metric, String severity) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "CAPACITY");
        alert.put("resourceId", metric.getResourceId());
        alert.put("resourceType", metric.getResourceType());
        alert.put("utilization", metric.getUtilization());
        alert.put("severity", severity);
        alert.put("timestamp", LocalDateTime.now().toString());
        
        alertingService.createAlert(alert);
    }

    private void calculateOverallUtilization() {
        if (!currentMetrics.isEmpty()) {
            double avgUtilization = currentMetrics.values().stream()
                .mapToDouble(CapacityMetric::getUtilization)
                .average()
                .orElse(0.0);
            
            overallUtilization.set(avgUtilization);
        }
    }

    private void evaluateCapacityHealth(CapacityMetric metric) {
        capacityPlanningService.evaluateHealth(metric);
    }

    private void identifyCapacityTrends() {
        capacityPlanningService.identifyTrends(historicalMetrics);
    }

    private void createPredictiveCapacityAlert(CapacityForecast forecast) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "PREDICTIVE_CAPACITY");
        alert.put("resourceId", forecast.getResourceId());
        alert.put("predictedUtilization", forecast.getPredictedUtilization());
        alert.put("forecastTime", forecast.getForecastTime().toString());
        alert.put("confidence", forecast.getConfidence());
        alert.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("predictive-capacity-alerts", alert);
    }

    private void handleProcessingError(String message, String topic, Exception error, 
                                     String correlationId, String traceId, Acknowledgment acknowledgment) {
        try {
            errorCounter.increment();
            logger.error("Error processing capacity monitoring message: {}", error.getMessage(), error);
            
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
        logger.error("Circuit breaker fallback triggered for capacity monitoring consumer: {}", ex.getMessage());
        
        errorCounter.increment();
        sendToDlq(message, topic, "Circuit breaker fallback: " + ex.getMessage(), ex, correlationId, traceId);
        acknowledgment.acknowledge();
    }

    // Additional helper methods (stubs for brevity)
    private void createCpuAlert(String instanceId, double cpuUsage, double loadAverage) {
        // Implementation
    }
    
    private void recommendCpuScaling(String instanceId, double cpuUsage, int cores) {
        // Implementation
    }
    
    private void handleSwapUsage(String instanceId, long swapUsed) {
        // Implementation
    }
    
    private void createMemoryAlert(String instanceId, double memoryUsage, long used, long total) {
        // Implementation
    }
    
    private void analyzeMemoryConsumers(String instanceId) {
        // Implementation
    }
    
    private void triggerMemoryOptimization(String instanceId) {
        // Implementation
    }
    
    private void predictDiskExhaustion(String volumeId, long used, long total) {
        // Implementation
    }
    
    private void createDiskSpaceAlert(String volumeId, String mountPoint, long freeSpace) {
        // Implementation
    }
    
    private void initiateCleanup(String volumeId, String mountPoint) {
        // Implementation
    }
    
    private void handlePacketLoss(String interfaceId, double packetLoss) {
        // Implementation
    }
    
    private void analyzeNetworkBottlenecks(String interfaceId, double inbound, double outbound) {
        // Implementation
    }
    
    private void handleContainerInstability(String containerId, String name, int restarts) {
        // Implementation
    }
    
    private boolean shouldScaleContainer(CapacityMetric metric) {
        return metric.getUtilization() > warningThreshold;
    }
    
    private void recommendContainerScaling(String containerId, String name, CapacityMetric metric) {
        // Implementation
    }
    
    private void handleReplicationLag(String databaseId, String name, long lag) {
        // Implementation
    }
    
    private void handleConnectionPoolPressure(String databaseId, int active, int max) {
        // Implementation
    }
    
    private void predictStorageExhaustion(String databaseId, long used, long max) {
        // Implementation
    }
    
    private void handleQueueBacklog(String queueId, String name, long oldestAge) {
        // Implementation
    }
    
    private void handleQueueNearCapacity(String queueId, String name, long current, long max) {
        // Implementation
    }
    
    private void analyzeQueuePerformance(String queueId, long size, double throughput) {
        // Implementation
    }
    
    private void handleTaskRejection(String poolId, String name, long rejected) {
        // Implementation
    }
    
    private void handleThreadPoolSaturation(String poolId, String name, int queueSize) {
        // Implementation
    }
    
    private void planProactiveScaling(CapacityForecast forecast) {
        // Implementation
    }
    
    private ResourceCapacity findAvailableCapacity(String resourceType, double requested) {
        // Implementation
        return null;
    }
    
    private void allocateResource(String allocationId, ResourceCapacity available, double requested, String requestedBy) {
        // Implementation
    }
    
    private void handleAllocationFailure(String allocationId, String resourceType, double requested, String requestedBy) {
        // Implementation
    }
    
    private void initiateEmergencyScaling(String resourceId, String resourceType, double utilization) {
        // Implementation
    }
    
    private void analyzeBreachPattern(String resourceId, String resourceType) {
        // Implementation
    }
    
    private void evaluateAutoOptimization(Map<String, Object> optimization) {
        // Implementation
    }
    
    private void updateAvailableCapacity(String resourceType, double capacity, boolean increase) {
        // Implementation
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down CapacityMonitoringConsumer");
        
        performCapacityPrediction();
        
        scheduledExecutor.shutdown();
        capacityProcessingExecutor.shutdown();
        
        try {
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            if (!capacityProcessingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                capacityProcessingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Error shutting down executors", e);
            scheduledExecutor.shutdownNow();
            capacityProcessingExecutor.shutdownNow();
        }
        
        logger.info("CapacityMonitoringConsumer shutdown complete. Total capacity checks: {}", 
                   totalCapacityChecks.get());
    }
}