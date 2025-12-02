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
import org.springframework.beans.factory.annotation.Autowired;
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
public class ResourceUtilizationConsumer extends BaseKafkaConsumer {

    public ResourceUtilizationConsumer(ResourceMetricRepository resourceMetricRepository,
                                      CpuUtilizationRepository cpuUtilizationRepository,
                                      MemoryUtilizationRepository memoryUtilizationRepository,
                                      DiskUtilizationRepository diskUtilizationRepository,
                                      NetworkUtilizationRepository networkUtilizationRepository,
                                      ContainerResourceRepository containerResourceRepository,
                                      ResourceAlertRepository resourceAlertRepository,
                                      ResourceTrendRepository resourceTrendRepository,
                                      AlertingService alertingService,
                                      MetricsService metricsService,
                                      NotificationService notificationService,
                                      ObjectMapper objectMapper,
                                      MeterRegistry meterRegistry) {
        this.resourceMetricRepository = resourceMetricRepository;
        this.cpuUtilizationRepository = cpuUtilizationRepository;
        this.memoryUtilizationRepository = memoryUtilizationRepository;
        this.diskUtilizationRepository = diskUtilizationRepository;
        this.networkUtilizationRepository = networkUtilizationRepository;
        this.containerResourceRepository = containerResourceRepository;
        this.resourceAlertRepository = resourceAlertRepository;
        this.resourceTrendRepository = resourceTrendRepository;
        this.alertingService = alertingService;
        this.metricsService = metricsService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    private static final Logger logger = LoggerFactory.getLogger(ResourceUtilizationConsumer.class);
    
    @Value("${waqiti.monitoring.resource.analysis-window-minutes:5}")
    private int analysisWindowMinutes;
    
    @Value("${waqiti.monitoring.resource.cpu-warning-threshold:75}")
    private double cpuWarningThreshold;
    
    @Value("${waqiti.monitoring.resource.cpu-critical-threshold:90}")
    private double cpuCriticalThreshold;
    
    @Value("${waqiti.monitoring.resource.memory-warning-threshold:80}")
    private double memoryWarningThreshold;
    
    @Value("${waqiti.monitoring.resource.memory-critical-threshold:95}")
    private double memoryCriticalThreshold;
    
    @Value("${waqiti.monitoring.resource.disk-warning-threshold:85}")
    private double diskWarningThreshold;
    
    @Value("${waqiti.monitoring.resource.disk-critical-threshold:95}")
    private double diskCriticalThreshold;

    private final ResourceMetricRepository resourceMetricRepository;
    private final CpuUtilizationRepository cpuUtilizationRepository;
    private final MemoryUtilizationRepository memoryUtilizationRepository;
    private final DiskUtilizationRepository diskUtilizationRepository;
    private final NetworkUtilizationRepository networkUtilizationRepository;
    private final ContainerResourceRepository containerResourceRepository;
    private final ResourceAlertRepository resourceAlertRepository;
    private final ResourceTrendRepository resourceTrendRepository;
    private final AlertingService alertingService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(4);
    private final Map<String, List<ResourceSample>> resourceBuffer = new ConcurrentHashMap<>();
    private final Map<String, ResourceBaseline> resourceBaselines = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastAnalysis = new ConcurrentHashMap<>();

    private Counter processedEventsCounter;
    private Counter processedResourceDataCounter;
    private Counter processedCpuUtilizationCounter;
    private Counter processedMemoryUtilizationCounter;
    private Counter processedDiskUtilizationCounter;
    private Counter processedNetworkUtilizationCounter;
    private Counter processedContainerResourceCounter;
    private Counter processedResourceAlertCounter;
    private Counter processedResourceTrendCounter;
    private Counter processedHighResourceUsageCounter;
    private Counter processedLowResourceUsageCounter;
    private Counter processedResourceExhaustionCounter;
    private Counter processedResourceRecoveryCounter;
    private Counter processedResourceBottleneckCounter;
    private Counter processedResourceOptimizationCounter;
    private Timer resourceProcessingTimer;
    private Timer resourceAnalysisTimer;
    
    private Gauge currentCpuUsageGauge;
    private Gauge currentMemoryUsageGauge;
    private Gauge currentDiskUsageGauge;
    private Gauge currentNetworkUsageGauge;

    @PostConstruct
    public void init() {
        this.processedEventsCounter = Counter.builder("resource_utilization_events_processed")
                .description("Number of resource utilization events processed")
                .register(meterRegistry);
        
        this.processedResourceDataCounter = Counter.builder("resource_data_processed")
                .description("Number of resource data events processed")
                .register(meterRegistry);
        
        this.processedCpuUtilizationCounter = Counter.builder("cpu_utilization_processed")
                .description("Number of CPU utilization events processed")
                .register(meterRegistry);
        
        this.processedMemoryUtilizationCounter = Counter.builder("memory_utilization_processed")
                .description("Number of memory utilization events processed")
                .register(meterRegistry);
        
        this.processedDiskUtilizationCounter = Counter.builder("disk_utilization_processed")
                .description("Number of disk utilization events processed")
                .register(meterRegistry);
        
        this.processedNetworkUtilizationCounter = Counter.builder("network_utilization_processed")
                .description("Number of network utilization events processed")
                .register(meterRegistry);
        
        this.processedContainerResourceCounter = Counter.builder("container_resource_processed")
                .description("Number of container resource events processed")
                .register(meterRegistry);
        
        this.processedResourceAlertCounter = Counter.builder("resource_alert_processed")
                .description("Number of resource alert events processed")
                .register(meterRegistry);
        
        this.processedResourceTrendCounter = Counter.builder("resource_trend_processed")
                .description("Number of resource trend events processed")
                .register(meterRegistry);
        
        this.processedHighResourceUsageCounter = Counter.builder("high_resource_usage_processed")
                .description("Number of high resource usage events processed")
                .register(meterRegistry);
        
        this.processedLowResourceUsageCounter = Counter.builder("low_resource_usage_processed")
                .description("Number of low resource usage events processed")
                .register(meterRegistry);
        
        this.processedResourceExhaustionCounter = Counter.builder("resource_exhaustion_processed")
                .description("Number of resource exhaustion events processed")
                .register(meterRegistry);
        
        this.processedResourceRecoveryCounter = Counter.builder("resource_recovery_processed")
                .description("Number of resource recovery events processed")
                .register(meterRegistry);
        
        this.processedResourceBottleneckCounter = Counter.builder("resource_bottleneck_processed")
                .description("Number of resource bottleneck events processed")
                .register(meterRegistry);
        
        this.processedResourceOptimizationCounter = Counter.builder("resource_optimization_processed")
                .description("Number of resource optimization events processed")
                .register(meterRegistry);
        
        this.resourceProcessingTimer = Timer.builder("resource_processing_duration")
                .description("Time taken to process resource events")
                .register(meterRegistry);
        
        this.resourceAnalysisTimer = Timer.builder("resource_analysis_duration")
                .description("Time taken to perform resource analysis")
                .register(meterRegistry);
        
        this.currentCpuUsageGauge = Gauge.builder("current_cpu_usage_percent", this, ResourceUtilizationConsumer::getCurrentCpuUsage)
                .description("Current CPU usage percentage")
                .register(meterRegistry);
        
        this.currentMemoryUsageGauge = Gauge.builder("current_memory_usage_percent", this, ResourceUtilizationConsumer::getCurrentMemoryUsage)
                .description("Current memory usage percentage")
                .register(meterRegistry);
        
        this.currentDiskUsageGauge = Gauge.builder("current_disk_usage_percent", this, ResourceUtilizationConsumer::getCurrentDiskUsage)
                .description("Current disk usage percentage")
                .register(meterRegistry);
        
        this.currentNetworkUsageGauge = Gauge.builder("current_network_usage_percent", this, ResourceUtilizationConsumer::getCurrentNetworkUsage)
                .description("Current network usage percentage")
                .register(meterRegistry);

        scheduledExecutor.scheduleAtFixedRate(this::performResourceAnalysis, 
                analysisWindowMinutes, analysisWindowMinutes, TimeUnit.MINUTES);
        
        scheduledExecutor.scheduleAtFixedRate(this::updateResourceBaselines, 
                1, 1, TimeUnit.HOURS);
        
        scheduledExecutor.scheduleAtFixedRate(this::generateResourceTrends, 
                10, 10, TimeUnit.MINUTES);
        
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

    @KafkaListener(topics = "resource-utilization", groupId = "resource-utilization-group", 
                   containerFactory = "kafkaListenerContainerFactory")
    @CircuitBreaker(name = "resource-utilization-consumer")
    @Retry(name = "resource-utilization-consumer")
    @Transactional
    public void handleResourceUtilizationEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "resource-utilization");

        try {
            logger.info("Processing resource utilization event: partition={}, offset={}", 
                    record.partition(), record.offset());

            JsonNode eventData = objectMapper.readTree(record.value());
            String eventType = eventData.path("eventType").asText();

            switch (eventType) {
                case "RESOURCE_DATA":
                    processResourceData(eventData);
                    processedResourceDataCounter.increment();
                    break;
                case "CPU_UTILIZATION":
                    processCpuUtilization(eventData);
                    processedCpuUtilizationCounter.increment();
                    break;
                case "MEMORY_UTILIZATION":
                    processMemoryUtilization(eventData);
                    processedMemoryUtilizationCounter.increment();
                    break;
                case "DISK_UTILIZATION":
                    processDiskUtilization(eventData);
                    processedDiskUtilizationCounter.increment();
                    break;
                case "NETWORK_UTILIZATION":
                    processNetworkUtilization(eventData);
                    processedNetworkUtilizationCounter.increment();
                    break;
                case "CONTAINER_RESOURCE":
                    processContainerResource(eventData);
                    processedContainerResourceCounter.increment();
                    break;
                case "RESOURCE_ALERT":
                    processResourceAlert(eventData);
                    processedResourceAlertCounter.increment();
                    break;
                case "RESOURCE_TREND":
                    processResourceTrend(eventData);
                    processedResourceTrendCounter.increment();
                    break;
                case "HIGH_RESOURCE_USAGE":
                    processHighResourceUsage(eventData);
                    processedHighResourceUsageCounter.increment();
                    break;
                case "LOW_RESOURCE_USAGE":
                    processLowResourceUsage(eventData);
                    processedLowResourceUsageCounter.increment();
                    break;
                case "RESOURCE_EXHAUSTION":
                    processResourceExhaustion(eventData);
                    processedResourceExhaustionCounter.increment();
                    break;
                case "RESOURCE_RECOVERY":
                    processResourceRecovery(eventData);
                    processedResourceRecoveryCounter.increment();
                    break;
                case "RESOURCE_BOTTLENECK":
                    processResourceBottleneck(eventData);
                    processedResourceBottleneckCounter.increment();
                    break;
                case "RESOURCE_OPTIMIZATION":
                    processResourceOptimization(eventData);
                    processedResourceOptimizationCounter.increment();
                    break;
                default:
                    logger.warn("Unknown resource utilization event type: {}", eventType);
            }

            processedEventsCounter.increment();
            ack.acknowledge();

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse resource utilization event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } catch (DataAccessException e) {
            logger.error("Database error processing resource utilization event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } catch (Exception e) {
            logger.error("Unexpected error processing resource utilization event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            sample.stop(resourceProcessingTimer);
            MDC.clear();
        }
    }

    private void processResourceData(JsonNode eventData) {
        try {
            ResourceMetric metric = new ResourceMetric();
            metric.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            metric.setNodeName(eventData.path("nodeName").asText());
            metric.setServiceName(eventData.path("serviceName").asText());
            metric.setCpuUsagePercent(eventData.path("cpuUsagePercent").asDouble());
            metric.setMemoryUsagePercent(eventData.path("memoryUsagePercent").asDouble());
            metric.setDiskUsagePercent(eventData.path("diskUsagePercent").asDouble());
            metric.setNetworkUsagePercent(eventData.path("networkUsagePercent").asDouble());
            metric.setLoadAverage(eventData.path("loadAverage").asDouble());
            metric.setAvailableMemoryMb(eventData.path("availableMemoryMb").asLong());
            metric.setTotalMemoryMb(eventData.path("totalMemoryMb").asLong());
            metric.setAvailableDiskGb(eventData.path("availableDiskGb").asLong());
            metric.setTotalDiskGb(eventData.path("totalDiskGb").asLong());
            
            JsonNode metadataNode = eventData.path("metadata");
            if (!metadataNode.isMissingNode()) {
                metric.setMetadata(metadataNode.toString());
            }
            
            resourceMetricRepository.save(metric);
            
            String nodeKey = metric.getNodeName();
            updateResourceBuffer(nodeKey, new ResourceSample(metric.getTimestamp(), 
                    metric.getCpuUsagePercent(), metric.getMemoryUsagePercent(), 
                    metric.getDiskUsagePercent(), metric.getNetworkUsagePercent()));
            
            metricsService.recordResourceMetric(metric.getNodeName(), metric.getServiceName(), metric);
            
            if (shouldTriggerRealTimeAnalysis(metric)) {
                performRealTimeResourceAnalysis(metric);
            }
            
            logger.debug("Processed resource data for node: {}, service: {}, CPU: {}%, Memory: {}%", 
                    metric.getNodeName(), metric.getServiceName(), 
                    metric.getCpuUsagePercent(), metric.getMemoryUsagePercent());
            
        } catch (Exception e) {
            logger.error("Error processing resource data: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processCpuUtilization(JsonNode eventData) {
        try {
            CpuUtilization cpuUtil = new CpuUtilization();
            cpuUtil.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            cpuUtil.setNodeName(eventData.path("nodeName").asText());
            cpuUtil.setServiceName(eventData.path("serviceName").asText());
            cpuUtil.setCpuUsagePercent(eventData.path("cpuUsagePercent").asDouble());
            cpuUtil.setUserCpuPercent(eventData.path("userCpuPercent").asDouble());
            cpuUtil.setSystemCpuPercent(eventData.path("systemCpuPercent").asDouble());
            cpuUtil.setIdleCpuPercent(eventData.path("idleCpuPercent").asDouble());
            cpuUtil.setIowaitPercent(eventData.path("iowaitPercent").asDouble());
            cpuUtil.setLoadAverage1m(eventData.path("loadAverage1m").asDouble());
            cpuUtil.setLoadAverage5m(eventData.path("loadAverage5m").asDouble());
            cpuUtil.setLoadAverage15m(eventData.path("loadAverage15m").asDouble());
            cpuUtil.setCpuCores(eventData.path("cpuCores").asInt());
            cpuUtil.setProcessCount(eventData.path("processCount").asInt());
            cpuUtil.setThreadCount(eventData.path("threadCount").asInt());
            
            cpuUtilizationRepository.save(cpuUtil);
            
            metricsService.recordCpuUtilizationMetrics(cpuUtil.getNodeName(), cpuUtil);
            
            if (cpuUtil.getCpuUsagePercent() > cpuCriticalThreshold) {
                generateCriticalCpuAlert(cpuUtil);
            } else if (cpuUtil.getCpuUsagePercent() > cpuWarningThreshold) {
                generateWarningCpuAlert(cpuUtil);
            }
            
            analyzeCpuTrend(cpuUtil);
            
            logger.debug("Processed CPU utilization for node: {}, usage: {}%", 
                    cpuUtil.getNodeName(), cpuUtil.getCpuUsagePercent());
            
        } catch (Exception e) {
            logger.error("Error processing CPU utilization: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processMemoryUtilization(JsonNode eventData) {
        try {
            MemoryUtilization memUtil = new MemoryUtilization();
            memUtil.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            memUtil.setNodeName(eventData.path("nodeName").asText());
            memUtil.setServiceName(eventData.path("serviceName").asText());
            memUtil.setMemoryUsagePercent(eventData.path("memoryUsagePercent").asDouble());
            memUtil.setUsedMemoryMb(eventData.path("usedMemoryMb").asLong());
            memUtil.setAvailableMemoryMb(eventData.path("availableMemoryMb").asLong());
            memUtil.setTotalMemoryMb(eventData.path("totalMemoryMb").asLong());
            memUtil.setBufferedMemoryMb(eventData.path("bufferedMemoryMb").asLong());
            memUtil.setCachedMemoryMb(eventData.path("cachedMemoryMb").asLong());
            memUtil.setSwapUsedMb(eventData.path("swapUsedMb").asLong());
            memUtil.setSwapTotalMb(eventData.path("swapTotalMb").asLong());
            memUtil.setHeapUsageMb(eventData.path("heapUsageMb").asLong());
            memUtil.setHeapMaxMb(eventData.path("heapMaxMb").asLong());
            memUtil.setGcCollections(eventData.path("gcCollections").asLong());
            memUtil.setGcTimeMs(eventData.path("gcTimeMs").asLong());
            
            memoryUtilizationRepository.save(memUtil);
            
            metricsService.recordMemoryUtilizationMetrics(memUtil.getNodeName(), memUtil);
            
            if (memUtil.getMemoryUsagePercent() > memoryCriticalThreshold) {
                generateCriticalMemoryAlert(memUtil);
            } else if (memUtil.getMemoryUsagePercent() > memoryWarningThreshold) {
                generateWarningMemoryAlert(memUtil);
            }
            
            analyzeMemoryLeaks(memUtil);
            analyzeGcPerformance(memUtil);
            
            logger.debug("Processed memory utilization for node: {}, usage: {}%", 
                    memUtil.getNodeName(), memUtil.getMemoryUsagePercent());
            
        } catch (Exception e) {
            logger.error("Error processing memory utilization: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processDiskUtilization(JsonNode eventData) {
        try {
            DiskUtilization diskUtil = new DiskUtilization();
            diskUtil.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            diskUtil.setNodeName(eventData.path("nodeName").asText());
            diskUtil.setMountPoint(eventData.path("mountPoint").asText());
            diskUtil.setDiskUsagePercent(eventData.path("diskUsagePercent").asDouble());
            diskUtil.setUsedSpaceGb(eventData.path("usedSpaceGb").asLong());
            diskUtil.setAvailableSpaceGb(eventData.path("availableSpaceGb").asLong());
            diskUtil.setTotalSpaceGb(eventData.path("totalSpaceGb").asLong());
            diskUtil.setInodeUsagePercent(eventData.path("inodeUsagePercent").asDouble());
            diskUtil.setUsedInodes(eventData.path("usedInodes").asLong());
            diskUtil.setTotalInodes(eventData.path("totalInodes").asLong());
            diskUtil.setReadIopsPerSec(eventData.path("readIopsPerSec").asDouble());
            diskUtil.setWriteIopsPerSec(eventData.path("writeIopsPerSec").asDouble());
            diskUtil.setReadThroughputMbps(eventData.path("readThroughputMbps").asDouble());
            diskUtil.setWriteThroughputMbps(eventData.path("writeThroughputMbps").asDouble());
            diskUtil.setAvgReadLatencyMs(eventData.path("avgReadLatencyMs").asDouble());
            diskUtil.setAvgWriteLatencyMs(eventData.path("avgWriteLatencyMs").asDouble());
            
            diskUtilizationRepository.save(diskUtil);
            
            metricsService.recordDiskUtilizationMetrics(diskUtil.getNodeName(), diskUtil);
            
            if (diskUtil.getDiskUsagePercent() > diskCriticalThreshold) {
                generateCriticalDiskAlert(diskUtil);
            } else if (diskUtil.getDiskUsagePercent() > diskWarningThreshold) {
                generateWarningDiskAlert(diskUtil);
            }
            
            analyzeDiskPerformance(diskUtil);
            
            logger.debug("Processed disk utilization for node: {}, mount: {}, usage: {}%", 
                    diskUtil.getNodeName(), diskUtil.getMountPoint(), diskUtil.getDiskUsagePercent());
            
        } catch (Exception e) {
            logger.error("Error processing disk utilization: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processNetworkUtilization(JsonNode eventData) {
        try {
            NetworkUtilization netUtil = new NetworkUtilization();
            netUtil.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            netUtil.setNodeName(eventData.path("nodeName").asText());
            netUtil.setInterfaceName(eventData.path("interfaceName").asText());
            netUtil.setNetworkUsagePercent(eventData.path("networkUsagePercent").asDouble());
            netUtil.setRxBytesPerSec(eventData.path("rxBytesPerSec").asLong());
            netUtil.setTxBytesPerSec(eventData.path("txBytesPerSec").asLong());
            netUtil.setRxPacketsPerSec(eventData.path("rxPacketsPerSec").asLong());
            netUtil.setTxPacketsPerSec(eventData.path("txPacketsPerSec").asLong());
            netUtil.setRxErrors(eventData.path("rxErrors").asLong());
            netUtil.setTxErrors(eventData.path("txErrors").asLong());
            netUtil.setRxDropped(eventData.path("rxDropped").asLong());
            netUtil.setTxDropped(eventData.path("txDropped").asLong());
            netUtil.setBandwidthCapacityMbps(eventData.path("bandwidthCapacityMbps").asLong());
            netUtil.setLatencyMs(eventData.path("latencyMs").asDouble());
            netUtil.setPacketLossPercent(eventData.path("packetLossPercent").asDouble());
            netUtil.setActiveConnections(eventData.path("activeConnections").asInt());
            
            networkUtilizationRepository.save(netUtil);
            
            metricsService.recordNetworkUtilizationMetrics(netUtil.getNodeName(), netUtil);
            
            analyzeNetworkPerformance(netUtil);
            
            logger.debug("Processed network utilization for node: {}, interface: {}, usage: {}%", 
                    netUtil.getNodeName(), netUtil.getInterfaceName(), netUtil.getNetworkUsagePercent());
            
        } catch (Exception e) {
            logger.error("Error processing network utilization: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processContainerResource(JsonNode eventData) {
        try {
            ContainerResource containerRes = new ContainerResource();
            containerRes.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            containerRes.setContainerName(eventData.path("containerName").asText());
            containerRes.setServiceName(eventData.path("serviceName").asText());
            containerRes.setNodeName(eventData.path("nodeName").asText());
            containerRes.setCpuUsagePercent(eventData.path("cpuUsagePercent").asDouble());
            containerRes.setMemoryUsagePercent(eventData.path("memoryUsagePercent").asDouble());
            containerRes.setCpuLimitMillicores(eventData.path("cpuLimitMillicores").asLong());
            containerRes.setCpuRequestMillicores(eventData.path("cpuRequestMillicores").asLong());
            containerRes.setMemoryLimitMb(eventData.path("memoryLimitMb").asLong());
            containerRes.setMemoryRequestMb(eventData.path("memoryRequestMb").asLong());
            containerRes.setMemoryUsageMb(eventData.path("memoryUsageMb").asLong());
            containerRes.setNetworkRxBytes(eventData.path("networkRxBytes").asLong());
            containerRes.setNetworkTxBytes(eventData.path("networkTxBytes").asLong());
            containerRes.setFilesystemUsageBytes(eventData.path("filesystemUsageBytes").asLong());
            containerRes.setRestartCount(eventData.path("restartCount").asInt());
            containerRes.setStatus(eventData.path("status").asText());
            
            containerResourceRepository.save(containerRes);
            
            metricsService.recordContainerResourceMetrics(containerRes.getContainerName(), containerRes);
            
            analyzeContainerResourceUsage(containerRes);
            
            logger.debug("Processed container resource for container: {}, service: {}, CPU: {}%, Memory: {}%", 
                    containerRes.getContainerName(), containerRes.getServiceName(), 
                    containerRes.getCpuUsagePercent(), containerRes.getMemoryUsagePercent());
            
        } catch (Exception e) {
            logger.error("Error processing container resource: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processResourceAlert(JsonNode eventData) {
        try {
            ResourceAlert alert = new ResourceAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setNodeName(eventData.path("nodeName").asText());
            alert.setResourceType(eventData.path("resourceType").asText());
            alert.setAlertType(eventData.path("alertType").asText());
            alert.setSeverity(eventData.path("severity").asText());
            alert.setCurrentValue(eventData.path("currentValue").asDouble());
            alert.setThreshold(eventData.path("threshold").asDouble());
            alert.setDescription(eventData.path("description").asText());
            alert.setResolved(eventData.path("resolved").asBoolean());
            
            if (eventData.has("resolvedAt")) {
                alert.setResolvedAt(parseTimestamp(eventData.path("resolvedAt").asText()));
            }
            
            resourceAlertRepository.save(alert);
            
            if (!alert.isResolved() && ("HIGH".equals(alert.getSeverity()) || "CRITICAL".equals(alert.getSeverity()))) {
                alertingService.sendAlert("RESOURCE_ALERT", alert.getDescription(), 
                        Map.of("nodeName", alert.getNodeName(), "resourceType", alert.getResourceType(), 
                               "severity", alert.getSeverity()));
            }
            
            logger.info("Processed resource alert for node: {}, resource: {}, severity: {}", 
                    alert.getNodeName(), alert.getResourceType(), alert.getSeverity());
            
        } catch (Exception e) {
            logger.error("Error processing resource alert: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processResourceTrend(JsonNode eventData) {
        try {
            ResourceTrend trend = new ResourceTrend();
            trend.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            trend.setNodeName(eventData.path("nodeName").asText());
            trend.setResourceType(eventData.path("resourceType").asText());
            trend.setTrendDirection(eventData.path("trendDirection").asText());
            trend.setTrendMagnitude(eventData.path("trendMagnitude").asDouble());
            trend.setConfidenceLevel(eventData.path("confidenceLevel").asDouble());
            trend.setPredictedValue(eventData.path("predictedValue").asDouble());
            trend.setCurrentValue(eventData.path("currentValue").asDouble());
            trend.setBaselineValue(eventData.path("baselineValue").asDouble());
            
            JsonNode trendDataNode = eventData.path("trendData");
            if (!trendDataNode.isMissingNode()) {
                trend.setTrendData(trendDataNode.toString());
            }
            
            resourceTrendRepository.save(trend);
            
            if ("INCREASING".equals(trend.getTrendDirection()) && trend.getConfidenceLevel() > 0.8) {
                generateTrendAlert(trend);
            }
            
            logger.debug("Processed resource trend for node: {}, resource: {}, direction: {}", 
                    trend.getNodeName(), trend.getResourceType(), trend.getTrendDirection());
            
        } catch (Exception e) {
            logger.error("Error processing resource trend: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processHighResourceUsage(JsonNode eventData) {
        try {
            String nodeName = eventData.path("nodeName").asText();
            String resourceType = eventData.path("resourceType").asText();
            double currentUsage = eventData.path("currentUsage").asDouble();
            double threshold = eventData.path("threshold").asDouble();
            String impact = eventData.path("impact").asText();
            
            ResourceAlert alert = new ResourceAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setNodeName(nodeName);
            alert.setResourceType(resourceType);
            alert.setAlertType("HIGH_USAGE");
            alert.setSeverity(currentUsage > threshold * 1.2 ? "CRITICAL" : "HIGH");
            alert.setCurrentValue(currentUsage);
            alert.setThreshold(threshold);
            alert.setDescription(String.format("High %s usage detected: %.1f%% (threshold: %.1f%%) - %s", 
                    resourceType, currentUsage, threshold, impact));
            alert.setResolved(false);
            
            resourceAlertRepository.save(alert);
            
            alertingService.sendAlert("HIGH_RESOURCE_USAGE", alert.getDescription(), 
                    Map.of("nodeName", nodeName, "resourceType", resourceType, "usage", String.valueOf(currentUsage)));
            
            if ("CRITICAL".equals(alert.getSeverity())) {
                initiateResourceScaling(nodeName, resourceType, currentUsage, threshold);
            }
            
            logger.warn("Processed high resource usage for node: {}, resource: {}, usage: {}%", 
                    nodeName, resourceType, currentUsage);
            
        } catch (Exception e) {
            logger.error("Error processing high resource usage: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processLowResourceUsage(JsonNode eventData) {
        try {
            String nodeName = eventData.path("nodeName").asText();
            String resourceType = eventData.path("resourceType").asText();
            double currentUsage = eventData.path("currentUsage").asDouble();
            double threshold = eventData.path("threshold").asDouble();
            String optimization = eventData.path("optimization").asText();
            
            if (currentUsage < threshold) {
                ResourceAlert alert = new ResourceAlert();
                alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
                alert.setNodeName(nodeName);
                alert.setResourceType(resourceType);
                alert.setAlertType("LOW_USAGE");
                alert.setSeverity("INFO");
                alert.setCurrentValue(currentUsage);
                alert.setThreshold(threshold);
                alert.setDescription(String.format("Low %s usage detected: %.1f%% - %s", 
                        resourceType, currentUsage, optimization));
                alert.setResolved(false);
                
                resourceAlertRepository.save(alert);
                
                suggestResourceOptimization(nodeName, resourceType, currentUsage, optimization);
            }
            
            logger.info("Processed low resource usage for node: {}, resource: {}, usage: {}%", 
                    nodeName, resourceType, currentUsage);
            
        } catch (Exception e) {
            logger.error("Error processing low resource usage: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processResourceExhaustion(JsonNode eventData) {
        try {
            String nodeName = eventData.path("nodeName").asText();
            String resourceType = eventData.path("resourceType").asText();
            double currentUsage = eventData.path("currentUsage").asDouble();
            String impact = eventData.path("impact").asText();
            String timeToExhaustion = eventData.path("timeToExhaustion").asText();
            
            ResourceAlert alert = new ResourceAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setNodeName(nodeName);
            alert.setResourceType(resourceType);
            alert.setAlertType("RESOURCE_EXHAUSTION");
            alert.setSeverity("CRITICAL");
            alert.setCurrentValue(currentUsage);
            alert.setThreshold(100.0);
            alert.setDescription(String.format("Resource exhaustion predicted: %s at %.1f%% - %s (ETA: %s)", 
                    resourceType, currentUsage, impact, timeToExhaustion));
            alert.setResolved(false);
            
            resourceAlertRepository.save(alert);
            
            alertingService.sendCriticalAlert("RESOURCE_EXHAUSTION", alert.getDescription(), 
                    Map.of("nodeName", nodeName, "resourceType", resourceType, 
                           "usage", String.valueOf(currentUsage), "eta", timeToExhaustion));
            
            notificationService.sendPagerDutyAlert("RESOURCE_EXHAUSTION", alert.getDescription());
            
            initiateEmergencyResourceAction(nodeName, resourceType, currentUsage, timeToExhaustion);
            
            logger.error("Processed resource exhaustion for node: {}, resource: {}, usage: {}%", 
                    nodeName, resourceType, currentUsage);
            
        } catch (Exception e) {
            logger.error("Error processing resource exhaustion: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processResourceRecovery(JsonNode eventData) {
        try {
            String nodeName = eventData.path("nodeName").asText();
            String resourceType = eventData.path("resourceType").asText();
            double currentUsage = eventData.path("currentUsage").asDouble();
            double previousUsage = eventData.path("previousUsage").asDouble();
            double improvementPercent = eventData.path("improvementPercent").asDouble();
            String recoveryDuration = eventData.path("recoveryDuration").asText();
            
            List<ResourceAlert> unresolvedAlerts = resourceAlertRepository.findUnresolvedByNodeAndResourceType(
                    nodeName, resourceType);
            
            for (ResourceAlert alert : unresolvedAlerts) {
                if (shouldResolveAlert(alert, currentUsage)) {
                    alert.setResolved(true);
                    alert.setResolvedAt(LocalDateTime.now());
                    resourceAlertRepository.save(alert);
                }
            }
            
            metricsService.recordResourceRecovery(nodeName, resourceType, improvementPercent);
            
            if (improvementPercent > 20) {
                notificationService.sendRecoveryNotification("RESOURCE_RECOVERY", 
                        String.format("Resource recovery detected: %s/%s - %.1f%% improvement in %s", 
                                nodeName, resourceType, improvementPercent, recoveryDuration));
            }
            
            logger.info("Processed resource recovery for node: {}, resource: {}, improvement: {}%", 
                    nodeName, resourceType, improvementPercent);
            
        } catch (Exception e) {
            logger.error("Error processing resource recovery: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processResourceBottleneck(JsonNode eventData) {
        try {
            String nodeName = eventData.path("nodeName").asText();
            String resourceType = eventData.path("resourceType").asText();
            double utilizationPercent = eventData.path("utilizationPercent").asDouble();
            String bottleneckType = eventData.path("bottleneckType").asText();
            String impact = eventData.path("impact").asText();
            
            JsonNode affectedServicesNode = eventData.path("affectedServices");
            String affectedServices = affectedServicesNode.isMissingNode() ? "" : affectedServicesNode.toString();
            
            ResourceAlert alert = new ResourceAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setNodeName(nodeName);
            alert.setResourceType(resourceType);
            alert.setAlertType("RESOURCE_BOTTLENECK");
            alert.setSeverity("HIGH");
            alert.setCurrentValue(utilizationPercent);
            alert.setDescription(String.format("Resource bottleneck detected: %s (%s) at %.1f%% - %s", 
                    resourceType, bottleneckType, utilizationPercent, impact));
            alert.setResolved(false);
            
            resourceAlertRepository.save(alert);
            
            alertingService.sendAlert("RESOURCE_BOTTLENECK", alert.getDescription(), 
                    Map.of("nodeName", nodeName, "resourceType", resourceType, 
                           "bottleneckType", bottleneckType, "utilization", String.valueOf(utilizationPercent)));
            
            analyzeBottleneckImpact(nodeName, resourceType, bottleneckType, affectedServices);
            
            logger.warn("Processed resource bottleneck for node: {}, resource: {}, type: {}", 
                    nodeName, resourceType, bottleneckType);
            
        } catch (Exception e) {
            logger.error("Error processing resource bottleneck: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processResourceOptimization(JsonNode eventData) {
        try {
            String nodeName = eventData.path("nodeName").asText();
            String resourceType = eventData.path("resourceType").asText();
            double currentUsage = eventData.path("currentUsage").asDouble();
            double recommendedLimit = eventData.path("recommendedLimit").asDouble();
            double potentialSavings = eventData.path("potentialSavings").asDouble();
            String optimizationType = eventData.path("optimizationType").asText();
            
            JsonNode recommendationsNode = eventData.path("recommendations");
            String recommendations = recommendationsNode.isMissingNode() ? "" : recommendationsNode.toString();
            
            metricsService.recordResourceOptimization(nodeName, resourceType, potentialSavings);
            
            if (potentialSavings > 15) {
                notificationService.sendOptimizationRecommendation("RESOURCE_OPTIMIZATION", 
                        String.format("Resource optimization opportunity: %s/%s - %.1f%% potential savings (%s)", 
                                nodeName, resourceType, potentialSavings, optimizationType),
                        recommendations);
            }
            
            logger.info("Processed resource optimization for node: {}, resource: {}, savings: {}%", 
                    nodeName, resourceType, potentialSavings);
            
        } catch (Exception e) {
            logger.error("Error processing resource optimization: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void performResourceAnalysis() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            logger.info("Starting scheduled resource analysis");
            
            for (Map.Entry<String, List<ResourceSample>> entry : resourceBuffer.entrySet()) {
                String nodeKey = entry.getKey();
                List<ResourceSample> samples = new ArrayList<>(entry.getValue());
                
                if (samples.size() >= 10) {
                    analyzeResourcePattern(nodeKey, samples);
                    entry.getValue().clear();
                }
            }
            
            analyzeSystemwideResourceTrends();
            detectResourceAnomalies();
            updateResourceBaselines();
            
        } catch (Exception e) {
            logger.error("Error in resource analysis: {}", e.getMessage(), e);
        } finally {
            sample.stop(resourceAnalysisTimer);
        }
    }

    private void analyzeResourcePattern(String nodeKey, List<ResourceSample> samples) {
        ResourceStatistics stats = calculateResourceStatistics(samples);
        
        ResourceBaseline baseline = resourceBaselines.get(nodeKey);
        if (baseline == null) {
            baseline = new ResourceBaseline(stats.getAvgCpu(), stats.getAvgMemory(), 
                    stats.getAvgDisk(), stats.getAvgNetwork());
            resourceBaselines.put(nodeKey, baseline);
        }
        
        if (stats.getMaxCpu() > baseline.getCpuBaseline() * 1.5) {
            generateResourceSpike(nodeKey, "CPU", stats.getMaxCpu(), baseline.getCpuBaseline());
        }
        
        if (stats.getMaxMemory() > baseline.getMemoryBaseline() * 1.3) {
            generateResourceSpike(nodeKey, "MEMORY", stats.getMaxMemory(), baseline.getMemoryBaseline());
        }
        
        metricsService.recordResourceStatistics(nodeKey, stats);
    }

    private void analyzeSystemwideResourceTrends() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneDayAgo = now.minusDays(1);
        
        List<ResourceMetric> recentMetrics = resourceMetricRepository.findByTimestampBetween(oneDayAgo, now);
        
        Map<String, List<ResourceMetric>> nodeMetrics = recentMetrics.stream()
                .collect(Collectors.groupingBy(ResourceMetric::getNodeName));
        
        for (Map.Entry<String, List<ResourceMetric>> entry : nodeMetrics.entrySet()) {
            String nodeName = entry.getKey();
            List<ResourceMetric> metrics = entry.getValue();
            
            if (metrics.size() >= 24) {
                analyzeDailyResourceTrend(nodeName, metrics);
            }
        }
    }

    private void analyzeDailyResourceTrend(String nodeName, List<ResourceMetric> metrics) {
        metrics.sort(Comparator.comparing(ResourceMetric::getTimestamp));
        
        List<Double> cpuValues = metrics.stream()
                .map(ResourceMetric::getCpuUsagePercent)
                .collect(Collectors.toList());
        
        List<Double> memoryValues = metrics.stream()
                .map(ResourceMetric::getMemoryUsagePercent)
                .collect(Collectors.toList());
        
        double cpuTrendSlope = calculateTrendSlope(cpuValues);
        double memoryTrendSlope = calculateTrendSlope(memoryValues);
        
        if (cpuTrendSlope > 0.5) {
            generateResourceTrendAlert(nodeName, "CPU", cpuTrendSlope, "INCREASING");
        }
        
        if (memoryTrendSlope > 0.3) {
            generateResourceTrendAlert(nodeName, "MEMORY", memoryTrendSlope, "INCREASING");
        }
    }

    private void updateResourceBaselines() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sevenDaysAgo = now.minusDays(7);
        
        List<String> distinctNodes = resourceMetricRepository.findDistinctNodeNames();
        
        for (String nodeName : distinctNodes) {
            List<ResourceMetric> metrics = resourceMetricRepository
                    .findByNodeNameAndTimestampBetween(nodeName, sevenDaysAgo, now);
            
            if (metrics.size() >= 100) {
                updateBaselineForNode(nodeName, metrics);
            }
        }
    }

    private void updateBaselineForNode(String nodeName, List<ResourceMetric> metrics) {
        double avgCpu = metrics.stream().mapToDouble(ResourceMetric::getCpuUsagePercent).average().orElse(0.0);
        double avgMemory = metrics.stream().mapToDouble(ResourceMetric::getMemoryUsagePercent).average().orElse(0.0);
        double avgDisk = metrics.stream().mapToDouble(ResourceMetric::getDiskUsagePercent).average().orElse(0.0);
        double avgNetwork = metrics.stream().mapToDouble(ResourceMetric::getNetworkUsagePercent).average().orElse(0.0);
        
        ResourceBaseline baseline = new ResourceBaseline(avgCpu, avgMemory, avgDisk, avgNetwork);
        resourceBaselines.put(nodeName, baseline);
        
        logger.debug("Updated baseline for node {}: CPU={}%, Memory={}%, Disk={}%, Network={}%", 
                nodeName, avgCpu, avgMemory, avgDisk, avgNetwork);
    }

    private void generateResourceTrends() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime twoHoursAgo = now.minusHours(2);
        
        List<ResourceMetric> recentMetrics = resourceMetricRepository.findByTimestampBetween(twoHoursAgo, now);
        
        Map<String, List<ResourceMetric>> nodeGroups = recentMetrics.stream()
                .collect(Collectors.groupingBy(ResourceMetric::getNodeName));
        
        for (Map.Entry<String, List<ResourceMetric>> entry : nodeGroups.entrySet()) {
            String nodeName = entry.getKey();
            List<ResourceMetric> metrics = entry.getValue();
            
            if (metrics.size() >= 6) {
                generateTrendForNode(nodeName, metrics);
            }
        }
    }

    private void generateTrendForNode(String nodeName, List<ResourceMetric> metrics) {
        metrics.sort(Comparator.comparing(ResourceMetric::getTimestamp));
        
        List<Double> cpuValues = metrics.stream()
                .map(ResourceMetric::getCpuUsagePercent)
                .collect(Collectors.toList());
        
        double trendSlope = calculateTrendSlope(cpuValues);
        double confidenceLevel = calculateTrendConfidence(cpuValues, trendSlope);
        
        if (confidenceLevel > 0.6) {
            String direction = trendSlope > 0.1 ? "INCREASING" : 
                              trendSlope < -0.1 ? "DECREASING" : "STABLE";
            
            ResourceTrend trend = new ResourceTrend();
            trend.setTimestamp(LocalDateTime.now());
            trend.setNodeName(nodeName);
            trend.setResourceType("CPU");
            trend.setTrendDirection(direction);
            trend.setTrendMagnitude(Math.abs(trendSlope));
            trend.setConfidenceLevel(confidenceLevel);
            trend.setCurrentValue(cpuValues.get(cpuValues.size() - 1));
            trend.setBaselineValue(cpuValues.get(0));
            
            resourceTrendRepository.save(trend);
        }
    }

    private void detectResourceAnomalies() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minusHours(1);
        
        List<ResourceMetric> recentMetrics = resourceMetricRepository.findByTimestampBetween(oneHourAgo, now);
        
        Map<String, List<ResourceMetric>> nodeGroups = recentMetrics.stream()
                .collect(Collectors.groupingBy(ResourceMetric::getNodeName));
        
        for (Map.Entry<String, List<ResourceMetric>> entry : nodeGroups.entrySet()) {
            String nodeName = entry.getKey();
            List<ResourceMetric> metrics = entry.getValue();
            
            ResourceBaseline baseline = resourceBaselines.get(nodeName);
            if (baseline != null) {
                detectAnomaliesInMetrics(nodeName, metrics, baseline);
            }
        }
    }

    private void detectAnomaliesInMetrics(String nodeName, List<ResourceMetric> metrics, ResourceBaseline baseline) {
        for (ResourceMetric metric : metrics) {
            double cpuZScore = Math.abs((metric.getCpuUsagePercent() - baseline.getCpuBaseline()) / 
                    (baseline.getCpuBaseline() * 0.3));
            double memoryZScore = Math.abs((metric.getMemoryUsagePercent() - baseline.getMemoryBaseline()) / 
                    (baseline.getMemoryBaseline() * 0.3));
            
            if (cpuZScore > 3.0) {
                generateAnomalyEvent(nodeName, "CPU", metric.getCpuUsagePercent(), 
                        baseline.getCpuBaseline(), cpuZScore);
            }
            
            if (memoryZScore > 3.0) {
                generateAnomalyEvent(nodeName, "MEMORY", metric.getMemoryUsagePercent(), 
                        baseline.getMemoryBaseline(), memoryZScore);
            }
        }
    }

    private void cleanupOldData() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        
        try {
            int deletedMetrics = resourceMetricRepository.deleteByTimestampBefore(cutoff);
            int deletedAlerts = resourceAlertRepository.deleteByTimestampBefore(cutoff);
            int deletedTrends = resourceTrendRepository.deleteByTimestampBefore(cutoff);
            
            logger.info("Cleaned up old resource data: {} metrics, {} alerts, {} trends", 
                    deletedMetrics, deletedAlerts, deletedTrends);
            
        } catch (Exception e) {
            logger.error("Error cleaning up old resource data: {}", e.getMessage(), e);
        }
    }

    private boolean shouldTriggerRealTimeAnalysis(ResourceMetric metric) {
        return metric.getCpuUsagePercent() > cpuWarningThreshold || 
               metric.getMemoryUsagePercent() > memoryWarningThreshold ||
               metric.getDiskUsagePercent() > diskWarningThreshold;
    }

    private void performRealTimeResourceAnalysis(ResourceMetric metric) {
        if (metric.getCpuUsagePercent() > cpuCriticalThreshold) {
            generateCriticalResourceAlert(metric, "CPU", metric.getCpuUsagePercent());
        }
        
        if (metric.getMemoryUsagePercent() > memoryCriticalThreshold) {
            generateCriticalResourceAlert(metric, "MEMORY", metric.getMemoryUsagePercent());
        }
        
        if (metric.getDiskUsagePercent() > diskCriticalThreshold) {
            generateCriticalResourceAlert(metric, "DISK", metric.getDiskUsagePercent());
        }
    }

    private void updateResourceBuffer(String nodeKey, ResourceSample sample) {
        resourceBuffer.computeIfAbsent(nodeKey, k -> new ArrayList<>()).add(sample);
        
        List<ResourceSample> samples = resourceBuffer.get(nodeKey);
        if (samples.size() > 100) {
            samples.subList(0, samples.size() - 100).clear();
        }
    }

    private void generateCriticalCpuAlert(CpuUtilization cpuUtil) {
        ResourceAlert alert = new ResourceAlert();
        alert.setTimestamp(LocalDateTime.now());
        alert.setNodeName(cpuUtil.getNodeName());
        alert.setResourceType("CPU");
        alert.setAlertType("CRITICAL_USAGE");
        alert.setSeverity("CRITICAL");
        alert.setCurrentValue(cpuUtil.getCpuUsagePercent());
        alert.setThreshold(cpuCriticalThreshold);
        alert.setDescription(String.format("Critical CPU usage: %.1f%% on node %s", 
                cpuUtil.getCpuUsagePercent(), cpuUtil.getNodeName()));
        alert.setResolved(false);
        
        resourceAlertRepository.save(alert);
        
        alertingService.sendCriticalAlert("CRITICAL_CPU_USAGE", alert.getDescription(), 
                Map.of("nodeName", cpuUtil.getNodeName(), "usage", String.valueOf(cpuUtil.getCpuUsagePercent())));
    }

    private void generateWarningCpuAlert(CpuUtilization cpuUtil) {
        ResourceAlert alert = new ResourceAlert();
        alert.setTimestamp(LocalDateTime.now());
        alert.setNodeName(cpuUtil.getNodeName());
        alert.setResourceType("CPU");
        alert.setAlertType("WARNING_USAGE");
        alert.setSeverity("WARNING");
        alert.setCurrentValue(cpuUtil.getCpuUsagePercent());
        alert.setThreshold(cpuWarningThreshold);
        alert.setDescription(String.format("High CPU usage: %.1f%% on node %s", 
                cpuUtil.getCpuUsagePercent(), cpuUtil.getNodeName()));
        alert.setResolved(false);
        
        resourceAlertRepository.save(alert);
        
        metricsService.recordWarningResourceUsage(cpuUtil.getNodeName(), "CPU", cpuUtil.getCpuUsagePercent());
    }

    private void generateCriticalMemoryAlert(MemoryUtilization memUtil) {
        ResourceAlert alert = new ResourceAlert();
        alert.setTimestamp(LocalDateTime.now());
        alert.setNodeName(memUtil.getNodeName());
        alert.setResourceType("MEMORY");
        alert.setAlertType("CRITICAL_USAGE");
        alert.setSeverity("CRITICAL");
        alert.setCurrentValue(memUtil.getMemoryUsagePercent());
        alert.setThreshold(memoryCriticalThreshold);
        alert.setDescription(String.format("Critical memory usage: %.1f%% on node %s", 
                memUtil.getMemoryUsagePercent(), memUtil.getNodeName()));
        alert.setResolved(false);
        
        resourceAlertRepository.save(alert);
        
        alertingService.sendCriticalAlert("CRITICAL_MEMORY_USAGE", alert.getDescription(), 
                Map.of("nodeName", memUtil.getNodeName(), "usage", String.valueOf(memUtil.getMemoryUsagePercent())));
    }

    private void generateWarningMemoryAlert(MemoryUtilization memUtil) {
        ResourceAlert alert = new ResourceAlert();
        alert.setTimestamp(LocalDateTime.now());
        alert.setNodeName(memUtil.getNodeName());
        alert.setResourceType("MEMORY");
        alert.setAlertType("WARNING_USAGE");
        alert.setSeverity("WARNING");
        alert.setCurrentValue(memUtil.getMemoryUsagePercent());
        alert.setThreshold(memoryWarningThreshold);
        alert.setDescription(String.format("High memory usage: %.1f%% on node %s", 
                memUtil.getMemoryUsagePercent(), memUtil.getNodeName()));
        alert.setResolved(false);
        
        resourceAlertRepository.save(alert);
        
        metricsService.recordWarningResourceUsage(memUtil.getNodeName(), "MEMORY", memUtil.getMemoryUsagePercent());
    }

    private void generateCriticalDiskAlert(DiskUtilization diskUtil) {
        ResourceAlert alert = new ResourceAlert();
        alert.setTimestamp(LocalDateTime.now());
        alert.setNodeName(diskUtil.getNodeName());
        alert.setResourceType("DISK");
        alert.setAlertType("CRITICAL_USAGE");
        alert.setSeverity("CRITICAL");
        alert.setCurrentValue(diskUtil.getDiskUsagePercent());
        alert.setThreshold(diskCriticalThreshold);
        alert.setDescription(String.format("Critical disk usage: %.1f%% on %s:%s", 
                diskUtil.getDiskUsagePercent(), diskUtil.getNodeName(), diskUtil.getMountPoint()));
        alert.setResolved(false);
        
        resourceAlertRepository.save(alert);
        
        alertingService.sendCriticalAlert("CRITICAL_DISK_USAGE", alert.getDescription(), 
                Map.of("nodeName", diskUtil.getNodeName(), "mountPoint", diskUtil.getMountPoint(), 
                       "usage", String.valueOf(diskUtil.getDiskUsagePercent())));
    }

    private void generateWarningDiskAlert(DiskUtilization diskUtil) {
        ResourceAlert alert = new ResourceAlert();
        alert.setTimestamp(LocalDateTime.now());
        alert.setNodeName(diskUtil.getNodeName());
        alert.setResourceType("DISK");
        alert.setAlertType("WARNING_USAGE");
        alert.setSeverity("WARNING");
        alert.setCurrentValue(diskUtil.getDiskUsagePercent());
        alert.setThreshold(diskWarningThreshold);
        alert.setDescription(String.format("High disk usage: %.1f%% on %s:%s", 
                diskUtil.getDiskUsagePercent(), diskUtil.getNodeName(), diskUtil.getMountPoint()));
        alert.setResolved(false);
        
        resourceAlertRepository.save(alert);
        
        metricsService.recordWarningResourceUsage(diskUtil.getNodeName(), "DISK", diskUtil.getDiskUsagePercent());
    }

    private void generateCriticalResourceAlert(ResourceMetric metric, String resourceType, double usage) {
        ResourceAlert alert = new ResourceAlert();
        alert.setTimestamp(LocalDateTime.now());
        alert.setNodeName(metric.getNodeName());
        alert.setResourceType(resourceType);
        alert.setAlertType("CRITICAL_USAGE");
        alert.setSeverity("CRITICAL");
        alert.setCurrentValue(usage);
        alert.setDescription(String.format("Critical %s usage: %.1f%% on node %s", 
                resourceType, usage, metric.getNodeName()));
        alert.setResolved(false);
        
        resourceAlertRepository.save(alert);
        
        alertingService.sendCriticalAlert("CRITICAL_RESOURCE_USAGE", alert.getDescription(), 
                Map.of("nodeName", metric.getNodeName(), "resourceType", resourceType, 
                       "usage", String.valueOf(usage)));
    }

    private void generateTrendAlert(ResourceTrend trend) {
        alertingService.sendAlert("RESOURCE_TREND", 
                String.format("Resource trend alert: %s/%s showing %s trend (confidence: %.1f%%)", 
                        trend.getNodeName(), trend.getResourceType(), trend.getTrendDirection(), 
                        trend.getConfidenceLevel() * 100),
                Map.of("nodeName", trend.getNodeName(), "resourceType", trend.getResourceType(), 
                       "direction", trend.getTrendDirection()));
    }

    private void generateResourceSpike(String nodeName, String resourceType, double currentValue, double baseline) {
        double spikeRatio = currentValue / baseline;
        
        ResourceAlert alert = new ResourceAlert();
        alert.setTimestamp(LocalDateTime.now());
        alert.setNodeName(nodeName);
        alert.setResourceType(resourceType);
        alert.setAlertType("RESOURCE_SPIKE");
        alert.setSeverity(spikeRatio > 2.0 ? "HIGH" : "MEDIUM");
        alert.setCurrentValue(currentValue);
        alert.setDescription(String.format("Resource spike: %s at %.1f%% (%.1fx baseline)", 
                resourceType, currentValue, spikeRatio));
        alert.setResolved(false);
        
        resourceAlertRepository.save(alert);
        
        if (spikeRatio > 1.5) {
            alertingService.sendAlert("RESOURCE_SPIKE", alert.getDescription(), 
                    Map.of("nodeName", nodeName, "resourceType", resourceType, 
                           "spikeRatio", String.valueOf(spikeRatio)));
        }
    }

    private void generateResourceTrendAlert(String nodeName, String resourceType, double trendSlope, String direction) {
        alertingService.sendAlert("RESOURCE_TREND_ALERT", 
                String.format("Resource trend detected: %s/%s %s (slope: %.2f)", 
                        nodeName, resourceType, direction, trendSlope),
                Map.of("nodeName", nodeName, "resourceType", resourceType, 
                       "direction", direction, "slope", String.valueOf(trendSlope)));
    }

    private void generateAnomalyEvent(String nodeName, String resourceType, double currentValue, 
                                      double baseline, double zScore) {
        try {
            Map<String, Object> anomalyData = Map.of(
                    "eventType", "RESOURCE_ANOMALY",
                    "timestamp", LocalDateTime.now().toString(),
                    "nodeName", nodeName,
                    "resourceType", resourceType,
                    "currentValue", currentValue,
                    "baseline", baseline,
                    "zScore", zScore,
                    "severity", zScore > 5.0 ? "HIGH" : "MEDIUM"
            );
            
            String anomalyJson = objectMapper.writeValueAsString(anomalyData);
            
        } catch (JsonProcessingException e) {
            logger.error("Error generating anomaly event: {}", e.getMessage(), e);
        }
    }

    private void analyzeCpuTrend(CpuUtilization cpuUtil) {
        if (cpuUtil.getLoadAverage15m() > cpuUtil.getCpuCores() * 1.5) {
            alertingService.sendAlert("HIGH_LOAD_AVERAGE", 
                    String.format("High load average on %s: %.2f (cores: %d)", 
                            cpuUtil.getNodeName(), cpuUtil.getLoadAverage15m(), cpuUtil.getCpuCores()),
                    Map.of("nodeName", cpuUtil.getNodeName(), "loadAverage", String.valueOf(cpuUtil.getLoadAverage15m())));
        }
    }

    private void analyzeMemoryLeaks(MemoryUtilization memUtil) {
        if (memUtil.getHeapUsageMb() > memUtil.getHeapMaxMb() * 0.9) {
            alertingService.sendAlert("HEAP_MEMORY_WARNING", 
                    String.format("High heap usage on %s: %d MB (max: %d MB)", 
                            memUtil.getNodeName(), memUtil.getHeapUsageMb(), memUtil.getHeapMaxMb()),
                    Map.of("nodeName", memUtil.getNodeName(), "heapUsage", String.valueOf(memUtil.getHeapUsageMb())));
        }
    }

    private void analyzeGcPerformance(MemoryUtilization memUtil) {
        if (memUtil.getGcTimeMs() > 5000) {
            alertingService.sendAlert("HIGH_GC_TIME", 
                    String.format("High GC time on %s: %d ms (collections: %d)", 
                            memUtil.getNodeName(), memUtil.getGcTimeMs(), memUtil.getGcCollections()),
                    Map.of("nodeName", memUtil.getNodeName(), "gcTime", String.valueOf(memUtil.getGcTimeMs())));
        }
    }

    private void analyzeDiskPerformance(DiskUtilization diskUtil) {
        if (diskUtil.getAvgReadLatencyMs() > 50 || diskUtil.getAvgWriteLatencyMs() > 50) {
            alertingService.sendAlert("HIGH_DISK_LATENCY", 
                    String.format("High disk latency on %s:%s - Read: %.1fms, Write: %.1fms", 
                            diskUtil.getNodeName(), diskUtil.getMountPoint(), 
                            diskUtil.getAvgReadLatencyMs(), diskUtil.getAvgWriteLatencyMs()),
                    Map.of("nodeName", diskUtil.getNodeName(), "mountPoint", diskUtil.getMountPoint()));
        }
    }

    private void analyzeNetworkPerformance(NetworkUtilization netUtil) {
        if (netUtil.getPacketLossPercent() > 1.0) {
            alertingService.sendAlert("HIGH_PACKET_LOSS", 
                    String.format("High packet loss on %s:%s - %.2f%%", 
                            netUtil.getNodeName(), netUtil.getInterfaceName(), netUtil.getPacketLossPercent()),
                    Map.of("nodeName", netUtil.getNodeName(), "interface", netUtil.getInterfaceName(), 
                           "packetLoss", String.valueOf(netUtil.getPacketLossPercent())));
        }
        
        if (netUtil.getRxErrors() > 100 || netUtil.getTxErrors() > 100) {
            alertingService.sendAlert("NETWORK_ERRORS", 
                    String.format("Network errors on %s:%s - RX: %d, TX: %d", 
                            netUtil.getNodeName(), netUtil.getInterfaceName(), 
                            netUtil.getRxErrors(), netUtil.getTxErrors()),
                    Map.of("nodeName", netUtil.getNodeName(), "interface", netUtil.getInterfaceName()));
        }
    }

    private void analyzeContainerResourceUsage(ContainerResource containerRes) {
        double cpuUtilization = (double) containerRes.getCpuLimitMillicores() / containerRes.getCpuRequestMillicores();
        double memoryUtilization = (double) containerRes.getMemoryLimitMb() / containerRes.getMemoryRequestMb();
        
        if (cpuUtilization > 0.9) {
            alertingService.sendAlert("CONTAINER_CPU_LIMIT", 
                    String.format("Container %s approaching CPU limit: %.1f%% (limit: %d mcores)", 
                            containerRes.getContainerName(), containerRes.getCpuUsagePercent(), 
                            containerRes.getCpuLimitMillicores()),
                    Map.of("containerName", containerRes.getContainerName(), 
                           "cpuUsage", String.valueOf(containerRes.getCpuUsagePercent())));
        }
        
        if (memoryUtilization > 0.9) {
            alertingService.sendAlert("CONTAINER_MEMORY_LIMIT", 
                    String.format("Container %s approaching memory limit: %.1f%% (limit: %d MB)", 
                            containerRes.getContainerName(), containerRes.getMemoryUsagePercent(), 
                            containerRes.getMemoryLimitMb()),
                    Map.of("containerName", containerRes.getContainerName(), 
                           "memoryUsage", String.valueOf(containerRes.getMemoryUsagePercent())));
        }
        
        if (containerRes.getRestartCount() > 5) {
            alertingService.sendAlert("CONTAINER_RESTART_LOOP", 
                    String.format("Container %s has restarted %d times", 
                            containerRes.getContainerName(), containerRes.getRestartCount()),
                    Map.of("containerName", containerRes.getContainerName(), 
                           "restartCount", String.valueOf(containerRes.getRestartCount())));
        }
    }

    private void initiateResourceScaling(String nodeName, String resourceType, double currentUsage, double threshold) {
    }

    private void suggestResourceOptimization(String nodeName, String resourceType, double currentUsage, String optimization) {
        notificationService.sendOptimizationRecommendation("RESOURCE_OPTIMIZATION", 
                String.format("Resource optimization opportunity: %s/%s at %.1f%% - %s", 
                        nodeName, resourceType, currentUsage, optimization),
                optimization);
    }

    private void initiateEmergencyResourceAction(String nodeName, String resourceType, double currentUsage, String timeToExhaustion) {
    }

    private void analyzeBottleneckImpact(String nodeName, String resourceType, String bottleneckType, String affectedServices) {
    }

    private boolean shouldResolveAlert(ResourceAlert alert, double currentUsage) {
        if ("HIGH_USAGE".equals(alert.getAlertType()) || "CRITICAL_USAGE".equals(alert.getAlertType())) {
            return currentUsage < alert.getThreshold() * 0.9;
        }
        return false;
    }

    private ResourceStatistics calculateResourceStatistics(List<ResourceSample> samples) {
        if (samples.isEmpty()) {
            return new ResourceStatistics();
        }
        
        double avgCpu = samples.stream().mapToDouble(ResourceSample::getCpuUsage).average().orElse(0.0);
        double avgMemory = samples.stream().mapToDouble(ResourceSample::getMemoryUsage).average().orElse(0.0);
        double avgDisk = samples.stream().mapToDouble(ResourceSample::getDiskUsage).average().orElse(0.0);
        double avgNetwork = samples.stream().mapToDouble(ResourceSample::getNetworkUsage).average().orElse(0.0);
        
        double maxCpu = samples.stream().mapToDouble(ResourceSample::getCpuUsage).max().orElse(0.0);
        double maxMemory = samples.stream().mapToDouble(ResourceSample::getMemoryUsage).max().orElse(0.0);
        double maxDisk = samples.stream().mapToDouble(ResourceSample::getDiskUsage).max().orElse(0.0);
        double maxNetwork = samples.stream().mapToDouble(ResourceSample::getNetworkUsage).max().orElse(0.0);
        
        return new ResourceStatistics(avgCpu, avgMemory, avgDisk, avgNetwork, 
                maxCpu, maxMemory, maxDisk, maxNetwork);
    }

    private double calculateTrendSlope(List<Double> values) {
        int n = values.size();
        if (n < 2) return 0.0;
        
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += values.get(i);
            sumXY += i * values.get(i);
            sumX2 += i * i;
        }
        
        return (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
    }

    private double calculateTrendConfidence(List<Double> values, double slope) {
        if (values.size() < 3) return 0.0;
        
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = values.stream()
                .mapToDouble(val -> Math.pow(val - mean, 2))
                .average().orElse(0.0);
        
        if (variance == 0) return 0.0;
        
        double trendStrength = Math.abs(slope) / Math.sqrt(variance);
        return Math.min(trendStrength, 1.0);
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        try {
            return LocalDateTime.parse(timestamp.replace("Z", ""));
        } catch (Exception e) {
            logger.warn("Failed to parse timestamp: {}, using current time", timestamp);
            return LocalDateTime.now();
        }
    }

    private double getCurrentCpuUsage() {
        return resourceBuffer.values().stream()
                .flatMap(List::stream)
                .mapToDouble(ResourceSample::getCpuUsage)
                .filter(usage -> usage > 0)
                .average()
                .orElse(0.0);
    }

    private double getCurrentMemoryUsage() {
        return resourceBuffer.values().stream()
                .flatMap(List::stream)
                .mapToDouble(ResourceSample::getMemoryUsage)
                .filter(usage -> usage > 0)
                .average()
                .orElse(0.0);
    }

    private double getCurrentDiskUsage() {
        return resourceBuffer.values().stream()
                .flatMap(List::stream)
                .mapToDouble(ResourceSample::getDiskUsage)
                .filter(usage -> usage > 0)
                .average()
                .orElse(0.0);
    }

    private double getCurrentNetworkUsage() {
        return resourceBuffer.values().stream()
                .flatMap(List::stream)
                .mapToDouble(ResourceSample::getNetworkUsage)
                .filter(usage -> usage > 0)
                .average()
                .orElse(0.0);
    }

    private static class ResourceSample {
        private final LocalDateTime timestamp;
        private final double cpuUsage;
        private final double memoryUsage;
        private final double diskUsage;
        private final double networkUsage;
        
        public ResourceSample(LocalDateTime timestamp, double cpuUsage, double memoryUsage, 
                              double diskUsage, double networkUsage) {
            this.timestamp = timestamp;
            this.cpuUsage = cpuUsage;
            this.memoryUsage = memoryUsage;
            this.diskUsage = diskUsage;
            this.networkUsage = networkUsage;
        }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public double getCpuUsage() { return cpuUsage; }
        public double getMemoryUsage() { return memoryUsage; }
        public double getDiskUsage() { return diskUsage; }
        public double getNetworkUsage() { return networkUsage; }
    }

    private static class ResourceStatistics {
        private final double avgCpu;
        private final double avgMemory;
        private final double avgDisk;
        private final double avgNetwork;
        private final double maxCpu;
        private final double maxMemory;
        private final double maxDisk;
        private final double maxNetwork;
        
        public ResourceStatistics() {
            this(0, 0, 0, 0, 0, 0, 0, 0);
        }
        
        public ResourceStatistics(double avgCpu, double avgMemory, double avgDisk, double avgNetwork,
                                  double maxCpu, double maxMemory, double maxDisk, double maxNetwork) {
            this.avgCpu = avgCpu;
            this.avgMemory = avgMemory;
            this.avgDisk = avgDisk;
            this.avgNetwork = avgNetwork;
            this.maxCpu = maxCpu;
            this.maxMemory = maxMemory;
            this.maxDisk = maxDisk;
            this.maxNetwork = maxNetwork;
        }
        
        public double getAvgCpu() { return avgCpu; }
        public double getAvgMemory() { return avgMemory; }
        public double getAvgDisk() { return avgDisk; }
        public double getAvgNetwork() { return avgNetwork; }
        public double getMaxCpu() { return maxCpu; }
        public double getMaxMemory() { return maxMemory; }
        public double getMaxDisk() { return maxDisk; }
        public double getMaxNetwork() { return maxNetwork; }
    }

    private static class ResourceBaseline {
        private final double cpuBaseline;
        private final double memoryBaseline;
        private final double diskBaseline;
        private final double networkBaseline;
        
        public ResourceBaseline(double cpuBaseline, double memoryBaseline, 
                                double diskBaseline, double networkBaseline) {
            this.cpuBaseline = cpuBaseline;
            this.memoryBaseline = memoryBaseline;
            this.diskBaseline = diskBaseline;
            this.networkBaseline = networkBaseline;
        }
        
        public double getCpuBaseline() { return cpuBaseline; }
        public double getMemoryBaseline() { return memoryBaseline; }
        public double getDiskBaseline() { return diskBaseline; }
        public double getNetworkBaseline() { return networkBaseline; }
    }
}