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
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ThroughputMonitoringConsumer extends BaseKafkaConsumer {

    private static final Logger logger = LoggerFactory.getLogger(ThroughputMonitoringConsumer.class);
    
    @Value("${waqiti.monitoring.throughput.analysis-window-minutes:5}")
    private int analysisWindowMinutes;
    
    @Value("${waqiti.monitoring.throughput.min-threshold-percentage:80}")
    private double minThresholdPercentage;
    
    @Value("${waqiti.monitoring.throughput.max-threshold-percentage:120}")
    private double maxThresholdPercentage;
    
    @Value("${waqiti.monitoring.throughput.spike-multiplier:3.0}")
    private double spikeMultiplier;
    
    @Value("${waqiti.monitoring.throughput.degradation-threshold:0.7}")
    private double degradationThreshold;
    
    @Value("${waqiti.monitoring.throughput.baseline-days:7}")
    private int baselineDays;

    private final ThroughputMetricRepository throughputMetricRepository;
    private final ServiceThroughputRepository serviceThroughputRepository;
    private final EndpointThroughputRepository endpointThroughputRepository;
    private final ThroughputTrendRepository throughputTrendRepository;
    private final ThroughputAlertRepository throughputAlertRepository;
    private final ThroughputBaselineRepository throughputBaselineRepository;
    private final QueueThroughputRepository queueThroughputRepository;
    private final DatabaseThroughputRepository databaseThroughputRepository;
    private final AlertingService alertingService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(4);
    private final Map<String, AtomicLong> requestCounters = new ConcurrentHashMap<>();
    private final Map<String, List<ThroughputSample>> throughputBuffer = new ConcurrentHashMap<>();
    private final Map<String, Double> baselineThroughput = new ConcurrentHashMap<>();
    private final Map<String, ThroughputWindow> currentWindows = new ConcurrentHashMap<>();

    private Counter processedEventsCounter;
    private Counter processedThroughputDataCounter;
    private Counter processedServiceThroughputCounter;
    private Counter processedEndpointThroughputCounter;
    private Counter processedQueueThroughputCounter;
    private Counter processedDatabaseThroughputCounter;
    private Counter processedThroughputTrendCounter;
    private Counter processedThroughputBaselineCounter;
    private Counter processedThroughputAlertCounter;
    private Counter processedThroughputSpikeCounter;
    private Counter processedThroughputDropCounter;
    private Counter processedThroughputDegradationCounter;
    private Counter processedThroughputRecoveryCounter;
    private Counter processedHighThroughputCounter;
    private Counter processedLowThroughputCounter;
    private Timer throughputProcessingTimer;
    private Timer throughputAnalysisTimer;

    @PostConstruct
    public void init() {
        this.processedEventsCounter = Counter.builder("throughput_monitoring_events_processed")
                .description("Number of throughput monitoring events processed")
                .register(meterRegistry);
        
        this.processedThroughputDataCounter = Counter.builder("throughput_data_processed")
                .description("Number of throughput data events processed")
                .register(meterRegistry);
        
        this.processedServiceThroughputCounter = Counter.builder("service_throughput_processed")
                .description("Number of service throughput events processed")
                .register(meterRegistry);
        
        this.processedEndpointThroughputCounter = Counter.builder("endpoint_throughput_processed")
                .description("Number of endpoint throughput events processed")
                .register(meterRegistry);
        
        this.processedQueueThroughputCounter = Counter.builder("queue_throughput_processed")
                .description("Number of queue throughput events processed")
                .register(meterRegistry);
        
        this.processedDatabaseThroughputCounter = Counter.builder("database_throughput_processed")
                .description("Number of database throughput events processed")
                .register(meterRegistry);
        
        this.processedThroughputTrendCounter = Counter.builder("throughput_trend_processed")
                .description("Number of throughput trend events processed")
                .register(meterRegistry);
        
        this.processedThroughputBaselineCounter = Counter.builder("throughput_baseline_processed")
                .description("Number of throughput baseline events processed")
                .register(meterRegistry);
        
        this.processedThroughputAlertCounter = Counter.builder("throughput_alert_processed")
                .description("Number of throughput alert events processed")
                .register(meterRegistry);
        
        this.processedThroughputSpikeCounter = Counter.builder("throughput_spike_processed")
                .description("Number of throughput spike events processed")
                .register(meterRegistry);
        
        this.processedThroughputDropCounter = Counter.builder("throughput_drop_processed")
                .description("Number of throughput drop events processed")
                .register(meterRegistry);
        
        this.processedThroughputDegradationCounter = Counter.builder("throughput_degradation_processed")
                .description("Number of throughput degradation events processed")
                .register(meterRegistry);
        
        this.processedThroughputRecoveryCounter = Counter.builder("throughput_recovery_processed")
                .description("Number of throughput recovery events processed")
                .register(meterRegistry);
        
        this.processedHighThroughputCounter = Counter.builder("high_throughput_processed")
                .description("Number of high throughput events processed")
                .register(meterRegistry);
        
        this.processedLowThroughputCounter = Counter.builder("low_throughput_processed")
                .description("Number of low throughput events processed")
                .register(meterRegistry);
        
        this.throughputProcessingTimer = Timer.builder("throughput_processing_duration")
                .description("Time taken to process throughput events")
                .register(meterRegistry);
        
        this.throughputAnalysisTimer = Timer.builder("throughput_analysis_duration")
                .description("Time taken to perform throughput analysis")
                .register(meterRegistry);

        scheduledExecutor.scheduleAtFixedRate(this::performThroughputAnalysis, 
                analysisWindowMinutes, analysisWindowMinutes, TimeUnit.MINUTES);
        
        scheduledExecutor.scheduleAtFixedRate(this::updateThroughputBaselines, 
                1, 1, TimeUnit.HOURS);
        
        scheduledExecutor.scheduleAtFixedRate(this::generateThroughputTrends, 
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

    @KafkaListener(topics = "throughput-monitoring", groupId = "throughput-monitoring-group", 
                   containerFactory = "kafkaListenerContainerFactory")
    @CircuitBreaker(name = "throughput-monitoring-consumer")
    @Retry(name = "throughput-monitoring-consumer")
    @Transactional
    public void handleThroughputMonitoringEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "throughput-monitoring");

        try {
            logger.info("Processing throughput monitoring event: partition={}, offset={}", 
                    record.partition(), record.offset());

            JsonNode eventData = objectMapper.readTree(record.value());
            String eventType = eventData.path("eventType").asText();

            switch (eventType) {
                case "THROUGHPUT_DATA":
                    processThroughputData(eventData);
                    processedThroughputDataCounter.increment();
                    break;
                case "SERVICE_THROUGHPUT":
                    processServiceThroughput(eventData);
                    processedServiceThroughputCounter.increment();
                    break;
                case "ENDPOINT_THROUGHPUT":
                    processEndpointThroughput(eventData);
                    processedEndpointThroughputCounter.increment();
                    break;
                case "QUEUE_THROUGHPUT":
                    processQueueThroughput(eventData);
                    processedQueueThroughputCounter.increment();
                    break;
                case "DATABASE_THROUGHPUT":
                    processDatabaseThroughput(eventData);
                    processedDatabaseThroughputCounter.increment();
                    break;
                case "THROUGHPUT_TREND":
                    processThroughputTrend(eventData);
                    processedThroughputTrendCounter.increment();
                    break;
                case "THROUGHPUT_BASELINE":
                    processThroughputBaseline(eventData);
                    processedThroughputBaselineCounter.increment();
                    break;
                case "THROUGHPUT_ALERT":
                    processThroughputAlert(eventData);
                    processedThroughputAlertCounter.increment();
                    break;
                case "THROUGHPUT_SPIKE":
                    processThroughputSpike(eventData);
                    processedThroughputSpikeCounter.increment();
                    break;
                case "THROUGHPUT_DROP":
                    processThroughputDrop(eventData);
                    processedThroughputDropCounter.increment();
                    break;
                case "THROUGHPUT_DEGRADATION":
                    processThroughputDegradation(eventData);
                    processedThroughputDegradationCounter.increment();
                    break;
                case "THROUGHPUT_RECOVERY":
                    processThroughputRecovery(eventData);
                    processedThroughputRecoveryCounter.increment();
                    break;
                case "HIGH_THROUGHPUT":
                    processHighThroughput(eventData);
                    processedHighThroughputCounter.increment();
                    break;
                case "LOW_THROUGHPUT":
                    processLowThroughput(eventData);
                    processedLowThroughputCounter.increment();
                    break;
                default:
                    logger.warn("Unknown throughput monitoring event type: {}", eventType);
            }

            processedEventsCounter.increment();
            ack.acknowledge();

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse throughput monitoring event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } catch (DataAccessException e) {
            logger.error("Database error processing throughput monitoring event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } catch (Exception e) {
            logger.error("Unexpected error processing throughput monitoring event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            sample.stop(throughputProcessingTimer);
            MDC.clear();
        }
    }

    private void processThroughputData(JsonNode eventData) {
        try {
            ThroughputMetric metric = new ThroughputMetric();
            metric.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            metric.setServiceName(eventData.path("serviceName").asText());
            metric.setEndpoint(eventData.path("endpoint").asText());
            metric.setRequestsPerSecond(eventData.path("requestsPerSecond").asDouble());
            metric.setRequestsPerMinute(eventData.path("requestsPerMinute").asDouble());
            metric.setRequestsPerHour(eventData.path("requestsPerHour").asDouble());
            metric.setTotalRequests(eventData.path("totalRequests").asLong());
            metric.setSuccessfulRequests(eventData.path("successfulRequests").asLong());
            metric.setFailedRequests(eventData.path("failedRequests").asLong());
            metric.setErrorRate(eventData.path("errorRate").asDouble());
            metric.setDataVolumeBytes(eventData.path("dataVolumeBytes").asLong());
            
            JsonNode metadataNode = eventData.path("metadata");
            if (!metadataNode.isMissingNode()) {
                metric.setMetadata(metadataNode.toString());
            }
            
            throughputMetricRepository.save(metric);
            
            String key = metric.getServiceName() + ":" + metric.getEndpoint();
            updateThroughputBuffer(key, new ThroughputSample(metric.getTimestamp(), metric.getRequestsPerSecond()));
            
            metricsService.recordThroughputMetric(metric.getServiceName(), metric.getEndpoint(), 
                    metric.getRequestsPerSecond());
            
            if (shouldTriggerRealTimeAnalysis(metric)) {
                performRealTimeThroughputAnalysis(metric);
            }
            
            logger.debug("Processed throughput data for service: {}, endpoint: {}, rps: {}", 
                    metric.getServiceName(), metric.getEndpoint(), metric.getRequestsPerSecond());
            
        } catch (Exception e) {
            logger.error("Error processing throughput data: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processServiceThroughput(JsonNode eventData) {
        try {
            ServiceThroughput serviceThroughput = new ServiceThroughput();
            serviceThroughput.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            serviceThroughput.setServiceName(eventData.path("serviceName").asText());
            serviceThroughput.setAvgRequestsPerSecond(eventData.path("avgRequestsPerSecond").asDouble());
            serviceThroughput.setPeakRequestsPerSecond(eventData.path("peakRequestsPerSecond").asDouble());
            serviceThroughput.setMinRequestsPerSecond(eventData.path("minRequestsPerSecond").asDouble());
            serviceThroughput.setTotalRequests(eventData.path("totalRequests").asLong());
            serviceThroughput.setActiveConnections(eventData.path("activeConnections").asLong());
            serviceThroughput.setConcurrentUsers(eventData.path("concurrentUsers").asLong());
            serviceThroughput.setDataThroughputMbps(eventData.path("dataThroughputMbps").asDouble());
            serviceThroughput.setCapacityUtilization(eventData.path("capacityUtilization").asDouble());
            serviceThroughput.setQueueDepth(eventData.path("queueDepth").asLong());
            
            serviceThroughputRepository.save(serviceThroughput);
            
            metricsService.recordServiceThroughputMetrics(serviceThroughput.getServiceName(), serviceThroughput);
            
            analyzeServiceThroughputTrend(serviceThroughput);
            
            if (serviceThroughput.getCapacityUtilization() > 0.9) {
                generateCapacityAlert(serviceThroughput);
            }
            
            logger.debug("Processed service throughput for service: {}, avg rps: {}", 
                    serviceThroughput.getServiceName(), serviceThroughput.getAvgRequestsPerSecond());
            
        } catch (Exception e) {
            logger.error("Error processing service throughput: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processEndpointThroughput(JsonNode eventData) {
        try {
            EndpointThroughput endpointThroughput = new EndpointThroughput();
            endpointThroughput.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            endpointThroughput.setServiceName(eventData.path("serviceName").asText());
            endpointThroughput.setEndpoint(eventData.path("endpoint").asText());
            endpointThroughput.setMethod(eventData.path("method").asText());
            endpointThroughput.setRequestsPerSecond(eventData.path("requestsPerSecond").asDouble());
            endpointThroughput.setRequestsPerMinute(eventData.path("requestsPerMinute").asDouble());
            endpointThroughput.setSuccessRate(eventData.path("successRate").asDouble());
            endpointThroughput.setAvgResponseTimeMs(eventData.path("avgResponseTimeMs").asDouble());
            endpointThroughput.setBytesPerSecond(eventData.path("bytesPerSecond").asLong());
            endpointThroughput.setCacheHitRate(eventData.path("cacheHitRate").asDouble());
            
            endpointThroughputRepository.save(endpointThroughput);
            
            metricsService.recordEndpointThroughputMetrics(endpointThroughput.getServiceName(), 
                    endpointThroughput.getEndpoint(), endpointThroughput);
            
            double baseline = getThroughputBaseline(endpointThroughput.getServiceName(), 
                    endpointThroughput.getEndpoint());
            
            if (endpointThroughput.getRequestsPerSecond() < baseline * degradationThreshold) {
                generateThroughputDegradationAlert(endpointThroughput, baseline);
            }
            
            logger.debug("Processed endpoint throughput for service: {}, endpoint: {}, rps: {}", 
                    endpointThroughput.getServiceName(), endpointThroughput.getEndpoint(), 
                    endpointThroughput.getRequestsPerSecond());
            
        } catch (Exception e) {
            logger.error("Error processing endpoint throughput: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processQueueThroughput(JsonNode eventData) {
        try {
            QueueThroughput queueThroughput = new QueueThroughput();
            queueThroughput.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            queueThroughput.setQueueName(eventData.path("queueName").asText());
            queueThroughput.setQueueType(eventData.path("queueType").asText());
            queueThroughput.setMessagesPerSecond(eventData.path("messagesPerSecond").asDouble());
            queueThroughput.setProducerRate(eventData.path("producerRate").asDouble());
            queueThroughput.setConsumerRate(eventData.path("consumerRate").asDouble());
            queueThroughput.setQueueDepth(eventData.path("queueDepth").asLong());
            queueThroughput.setMaxQueueDepth(eventData.path("maxQueueDepth").asLong());
            queueThroughput.setAvgMessageSize(eventData.path("avgMessageSize").asLong());
            queueThroughput.setDlqMessageCount(eventData.path("dlqMessageCount").asLong());
            queueThroughput.setPartitionCount(eventData.path("partitionCount").asInt());
            
            queueThroughputRepository.save(queueThroughput);
            
            metricsService.recordQueueThroughputMetrics(queueThroughput.getQueueName(), queueThroughput);
            
            analyzeQueuePerformance(queueThroughput);
            
            logger.debug("Processed queue throughput for queue: {}, messages/sec: {}", 
                    queueThroughput.getQueueName(), queueThroughput.getMessagesPerSecond());
            
        } catch (Exception e) {
            logger.error("Error processing queue throughput: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processDatabaseThroughput(JsonNode eventData) {
        try {
            DatabaseThroughput dbThroughput = new DatabaseThroughput();
            dbThroughput.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            dbThroughput.setDatabaseName(eventData.path("databaseName").asText());
            dbThroughput.setTransactionsPerSecond(eventData.path("transactionsPerSecond").asDouble());
            dbThroughput.setQueriesPerSecond(eventData.path("queriesPerSecond").asDouble());
            dbThroughput.setInsertsPerSecond(eventData.path("insertsPerSecond").asDouble());
            dbThroughput.setUpdatesPerSecond(eventData.path("updatesPerSecond").asDouble());
            dbThroughput.setDeletesPerSecond(eventData.path("deletsPerSecond").asDouble());
            dbThroughput.setSelectsPerSecond(eventData.path("selectsPerSecond").asDouble());
            dbThroughput.setConnectionPoolSize(eventData.path("connectionPoolSize").asInt());
            dbThroughput.setActiveConnections(eventData.path("activeConnections").asInt());
            dbThroughput.setIoOperationsPerSecond(eventData.path("ioOperationsPerSecond").asDouble());
            
            databaseThroughputRepository.save(dbThroughput);
            
            metricsService.recordDatabaseThroughputMetrics(dbThroughput.getDatabaseName(), dbThroughput);
            
            analyzeDatabasePerformance(dbThroughput);
            
            logger.debug("Processed database throughput for database: {}, tps: {}", 
                    dbThroughput.getDatabaseName(), dbThroughput.getTransactionsPerSecond());
            
        } catch (Exception e) {
            logger.error("Error processing database throughput: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processThroughputTrend(JsonNode eventData) {
        try {
            ThroughputTrend trend = new ThroughputTrend();
            trend.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            trend.setServiceName(eventData.path("serviceName").asText());
            trend.setEndpoint(eventData.path("endpoint").asText());
            trend.setTrendDirection(eventData.path("trendDirection").asText());
            trend.setTrendMagnitude(eventData.path("trendMagnitude").asDouble());
            trend.setConfidenceLevel(eventData.path("confidenceLevel").asDouble());
            trend.setPredictedThroughput(eventData.path("predictedThroughput").asDouble());
            trend.setCurrentThroughput(eventData.path("currentThroughput").asDouble());
            trend.setBaselineThroughput(eventData.path("baselineThroughput").asDouble());
            
            JsonNode trendDataNode = eventData.path("trendData");
            if (!trendDataNode.isMissingNode()) {
                trend.setTrendData(trendDataNode.toString());
            }
            
            throughputTrendRepository.save(trend);
            
            if ("DECLINING".equals(trend.getTrendDirection()) && trend.getConfidenceLevel() > 0.8) {
                generateTrendAlert(trend);
            }
            
            logger.debug("Processed throughput trend for service: {}, endpoint: {}, direction: {}", 
                    trend.getServiceName(), trend.getEndpoint(), trend.getTrendDirection());
            
        } catch (Exception e) {
            logger.error("Error processing throughput trend: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processThroughputBaseline(JsonNode eventData) {
        try {
            ThroughputBaseline baseline = new ThroughputBaseline();
            baseline.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            baseline.setServiceName(eventData.path("serviceName").asText());
            baseline.setEndpoint(eventData.path("endpoint").asText());
            baseline.setBaselineThroughput(eventData.path("baselineThroughput").asDouble());
            baseline.setMinThroughput(eventData.path("minThroughput").asDouble());
            baseline.setMaxThroughput(eventData.path("maxThroughput").asDouble());
            baseline.setStandardDeviation(eventData.path("standardDeviation").asDouble());
            baseline.setSampleSize(eventData.path("sampleSize").asLong());
            baseline.setValidFrom(parseTimestamp(eventData.path("validFrom").asText()));
            baseline.setValidTo(parseTimestamp(eventData.path("validTo").asText()));
            
            throughputBaselineRepository.save(baseline);
            
            String key = baseline.getServiceName() + ":" + baseline.getEndpoint();
            baselineThroughput.put(key, baseline.getBaselineThroughput());
            
            logger.debug("Processed throughput baseline for service: {}, endpoint: {}, baseline: {} rps", 
                    baseline.getServiceName(), baseline.getEndpoint(), baseline.getBaselineThroughput());
            
        } catch (Exception e) {
            logger.error("Error processing throughput baseline: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processThroughputAlert(JsonNode eventData) {
        try {
            ThroughputAlert alert = new ThroughputAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setServiceName(eventData.path("serviceName").asText());
            alert.setEndpoint(eventData.path("endpoint").asText());
            alert.setAlertType(eventData.path("alertType").asText());
            alert.setSeverity(eventData.path("severity").asText());
            alert.setCurrentThroughput(eventData.path("currentThroughput").asDouble());
            alert.setBaselineThroughput(eventData.path("baselineThroughput").asDouble());
            alert.setThreshold(eventData.path("threshold").asDouble());
            alert.setDescription(eventData.path("description").asText());
            alert.setResolved(eventData.path("resolved").asBoolean());
            
            if (eventData.has("resolvedAt")) {
                alert.setResolvedAt(parseTimestamp(eventData.path("resolvedAt").asText()));
            }
            
            throughputAlertRepository.save(alert);
            
            if (!alert.isResolved() && ("HIGH".equals(alert.getSeverity()) || "CRITICAL".equals(alert.getSeverity()))) {
                alertingService.sendAlert("THROUGHPUT_ALERT", alert.getDescription(), 
                        Map.of("serviceName", alert.getServiceName(), "endpoint", alert.getEndpoint(), 
                               "severity", alert.getSeverity()));
            }
            
            logger.info("Processed throughput alert for service: {}, endpoint: {}, severity: {}", 
                    alert.getServiceName(), alert.getEndpoint(), alert.getSeverity());
            
        } catch (Exception e) {
            logger.error("Error processing throughput alert: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processThroughputSpike(JsonNode eventData) {
        try {
            String serviceName = eventData.path("serviceName").asText();
            String endpoint = eventData.path("endpoint").asText();
            double currentThroughput = eventData.path("currentThroughput").asDouble();
            double baselineThroughput = eventData.path("baselineThroughput").asDouble();
            double spikeRatio = eventData.path("spikeRatio").asDouble();
            String cause = eventData.path("cause").asText();
            
            ThroughputAlert alert = new ThroughputAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setServiceName(serviceName);
            alert.setEndpoint(endpoint);
            alert.setAlertType("THROUGHPUT_SPIKE");
            alert.setSeverity(spikeRatio > 5.0 ? "HIGH" : "MEDIUM");
            alert.setCurrentThroughput(currentThroughput);
            alert.setBaselineThroughput(baselineThroughput);
            alert.setDescription(String.format("Throughput spike detected: %.1f rps (%.1fx baseline) - %s", 
                    currentThroughput, spikeRatio, cause));
            alert.setResolved(false);
            
            throughputAlertRepository.save(alert);
            
            if (spikeRatio > 3.0) {
                alertingService.sendAlert("THROUGHPUT_SPIKE", alert.getDescription(), 
                        Map.of("serviceName", serviceName, "endpoint", endpoint, "spikeRatio", String.valueOf(spikeRatio)));
            }
            
            investigateThroughputSpike(serviceName, endpoint, currentThroughput, baselineThroughput, cause);
            
            logger.warn("Processed throughput spike for service: {}, endpoint: {}, spike ratio: {}x", 
                    serviceName, endpoint, spikeRatio);
            
        } catch (Exception e) {
            logger.error("Error processing throughput spike: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processThroughputDrop(JsonNode eventData) {
        try {
            String serviceName = eventData.path("serviceName").asText();
            String endpoint = eventData.path("endpoint").asText();
            double currentThroughput = eventData.path("currentThroughput").asDouble();
            double previousThroughput = eventData.path("previousThroughput").asDouble();
            double dropPercentage = eventData.path("dropPercentage").asDouble();
            String cause = eventData.path("cause").asText();
            
            ThroughputAlert alert = new ThroughputAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setServiceName(serviceName);
            alert.setEndpoint(endpoint);
            alert.setAlertType("THROUGHPUT_DROP");
            alert.setSeverity(dropPercentage > 50 ? "HIGH" : "MEDIUM");
            alert.setCurrentThroughput(currentThroughput);
            alert.setBaselineThroughput(previousThroughput);
            alert.setDescription(String.format("Throughput drop detected: %.1f%% decrease - %s", 
                    dropPercentage, cause));
            alert.setResolved(false);
            
            throughputAlertRepository.save(alert);
            
            if (dropPercentage > 30) {
                alertingService.sendAlert("THROUGHPUT_DROP", alert.getDescription(), 
                        Map.of("serviceName", serviceName, "endpoint", endpoint, "dropPercentage", String.valueOf(dropPercentage)));
            }
            
            investigateThroughputDrop(serviceName, endpoint, currentThroughput, previousThroughput, cause);
            
            logger.warn("Processed throughput drop for service: {}, endpoint: {}, drop: {}%", 
                    serviceName, endpoint, dropPercentage);
            
        } catch (Exception e) {
            logger.error("Error processing throughput drop: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processThroughputDegradation(JsonNode eventData) {
        try {
            String serviceName = eventData.path("serviceName").asText();
            String endpoint = eventData.path("endpoint").asText();
            double currentThroughput = eventData.path("currentThroughput").asDouble();
            double baselineThroughput = eventData.path("baselineThroughput").asDouble();
            double degradationPercent = eventData.path("degradationPercent").asDouble();
            String duration = eventData.path("duration").asText();
            
            ThroughputAlert alert = new ThroughputAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setServiceName(serviceName);
            alert.setEndpoint(endpoint);
            alert.setAlertType("THROUGHPUT_DEGRADATION");
            alert.setSeverity(degradationPercent > 40 ? "HIGH" : "MEDIUM");
            alert.setCurrentThroughput(currentThroughput);
            alert.setBaselineThroughput(baselineThroughput);
            alert.setDescription(String.format("Sustained throughput degradation: %.1f%% decrease over %s", 
                    degradationPercent, duration));
            alert.setResolved(false);
            
            throughputAlertRepository.save(alert);
            
            if (degradationPercent > 25) {
                alertingService.sendAlert("THROUGHPUT_DEGRADATION", alert.getDescription(), 
                        Map.of("serviceName", serviceName, "endpoint", endpoint, 
                               "degradation", String.valueOf(degradationPercent)));
            }
            
            analyzeThroughputDegradation(serviceName, endpoint, currentThroughput, baselineThroughput);
            
            logger.warn("Processed throughput degradation for service: {}, endpoint: {}, degradation: {}%", 
                    serviceName, endpoint, degradationPercent);
            
        } catch (Exception e) {
            logger.error("Error processing throughput degradation: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processThroughputRecovery(JsonNode eventData) {
        try {
            String serviceName = eventData.path("serviceName").asText();
            String endpoint = eventData.path("endpoint").asText();
            double currentThroughput = eventData.path("currentThroughput").asDouble();
            double previousThroughput = eventData.path("previousThroughput").asDouble();
            double improvementPercent = eventData.path("improvementPercent").asDouble();
            String recoveryDuration = eventData.path("recoveryDuration").asText();
            
            List<ThroughputAlert> unresolvedAlerts = throughputAlertRepository.findUnresolvedByServiceAndEndpoint(
                    serviceName, endpoint);
            
            for (ThroughputAlert alert : unresolvedAlerts) {
                if (shouldResolveAlert(alert, currentThroughput)) {
                    alert.setResolved(true);
                    alert.setResolvedAt(LocalDateTime.now());
                    throughputAlertRepository.save(alert);
                }
            }
            
            metricsService.recordThroughputRecovery(serviceName, endpoint, improvementPercent);
            
            if (improvementPercent > 20) {
                notificationService.sendRecoveryNotification("THROUGHPUT_RECOVERY", 
                        String.format("Throughput recovery detected: %s/%s - %.1f%% improvement in %s", 
                                serviceName, endpoint, improvementPercent, recoveryDuration));
            }
            
            logger.info("Processed throughput recovery for service: {}, endpoint: {}, improvement: {}%", 
                    serviceName, endpoint, improvementPercent);
            
        } catch (Exception e) {
            logger.error("Error processing throughput recovery: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processHighThroughput(JsonNode eventData) {
        try {
            String serviceName = eventData.path("serviceName").asText();
            String endpoint = eventData.path("endpoint").asText();
            double throughput = eventData.path("throughput").asDouble();
            double threshold = eventData.path("threshold").asDouble();
            double capacityUtilization = eventData.path("capacityUtilization").asDouble();
            
            ThroughputAlert alert = new ThroughputAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setServiceName(serviceName);
            alert.setEndpoint(endpoint);
            alert.setAlertType("HIGH_THROUGHPUT");
            alert.setSeverity(capacityUtilization > 0.9 ? "HIGH" : "MEDIUM");
            alert.setCurrentThroughput(throughput);
            alert.setThreshold(threshold);
            alert.setDescription(String.format("High throughput detected: %.1f rps (capacity: %.1f%%)", 
                    throughput, capacityUtilization * 100));
            alert.setResolved(false);
            
            throughputAlertRepository.save(alert);
            
            if (capacityUtilization > 0.85) {
                alertingService.sendAlert("HIGH_THROUGHPUT", alert.getDescription(), 
                        Map.of("serviceName", serviceName, "endpoint", endpoint, 
                               "throughput", String.valueOf(throughput)));
                
                initiateCapacityScaling(serviceName, endpoint, throughput, capacityUtilization);
            }
            
            logger.info("Processed high throughput for service: {}, endpoint: {}, throughput: {} rps", 
                    serviceName, endpoint, throughput);
            
        } catch (Exception e) {
            logger.error("Error processing high throughput: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processLowThroughput(JsonNode eventData) {
        try {
            String serviceName = eventData.path("serviceName").asText();
            String endpoint = eventData.path("endpoint").asText();
            double throughput = eventData.path("throughput").asDouble();
            double minThreshold = eventData.path("minThreshold").asDouble();
            String impact = eventData.path("impact").asText();
            
            ThroughputAlert alert = new ThroughputAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setServiceName(serviceName);
            alert.setEndpoint(endpoint);
            alert.setAlertType("LOW_THROUGHPUT");
            alert.setSeverity(throughput < minThreshold * 0.5 ? "HIGH" : "MEDIUM");
            alert.setCurrentThroughput(throughput);
            alert.setThreshold(minThreshold);
            alert.setDescription(String.format("Low throughput detected: %.1f rps (min: %.1f rps) - %s", 
                    throughput, minThreshold, impact));
            alert.setResolved(false);
            
            throughputAlertRepository.save(alert);
            
            if (throughput < minThreshold * 0.7) {
                alertingService.sendAlert("LOW_THROUGHPUT", alert.getDescription(), 
                        Map.of("serviceName", serviceName, "endpoint", endpoint, 
                               "throughput", String.valueOf(throughput)));
            }
            
            investigateLowThroughput(serviceName, endpoint, throughput, minThreshold, impact);
            
            logger.info("Processed low throughput for service: {}, endpoint: {}, throughput: {} rps", 
                    serviceName, endpoint, throughput);
            
        } catch (Exception e) {
            logger.error("Error processing low throughput: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void performThroughputAnalysis() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            logger.info("Starting scheduled throughput analysis");
            
            for (Map.Entry<String, List<ThroughputSample>> entry : throughputBuffer.entrySet()) {
                String key = entry.getKey();
                List<ThroughputSample> samples = new ArrayList<>(entry.getValue());
                
                if (samples.size() >= 10) {
                    analyzeThroughputPattern(key, samples);
                    entry.getValue().clear();
                }
            }
            
            analyzeSystemwideThroughputTrends();
            detectThroughputAnomalies();
            updateThroughputBaselines();
            
        } catch (Exception e) {
            logger.error("Error in throughput analysis: {}", e.getMessage(), e);
        } finally {
            sample.stop(throughputAnalysisTimer);
        }
    }

    private void analyzeThroughputPattern(String serviceEndpointKey, List<ThroughputSample> samples) {
        String[] parts = serviceEndpointKey.split(":");
        String serviceName = parts[0];
        String endpoint = parts[1];
        
        ThroughputStatistics stats = calculateThroughputStatistics(samples);
        
        double baseline = baselineThroughput.getOrDefault(serviceEndpointKey, stats.getAverage());
        
        if (stats.getMax() > baseline * spikeMultiplier) {
            generateThroughputSpike(serviceName, endpoint, stats, baseline);
        }
        
        if (stats.getAverage() < baseline * degradationThreshold) {
            generateThroughputDegradationAlert(serviceName, endpoint, stats, baseline);
        }
        
        metricsService.recordThroughputStatistics(serviceName, endpoint, stats);
    }

    private void analyzeSystemwideThroughputTrends() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneDayAgo = now.minusDays(1);
        
        List<ServiceThroughput> recentMetrics = serviceThroughputRepository.findByTimestampBetween(oneDayAgo, now);
        
        Map<String, List<ServiceThroughput>> serviceMetrics = recentMetrics.stream()
                .collect(Collectors.groupingBy(ServiceThroughput::getServiceName));
        
        for (Map.Entry<String, List<ServiceThroughput>> entry : serviceMetrics.entrySet()) {
            String serviceName = entry.getKey();
            List<ServiceThroughput> metrics = entry.getValue();
            
            if (metrics.size() >= 24) {
                analyzeDailyThroughputTrend(serviceName, metrics);
            }
        }
    }

    private void analyzeDailyThroughputTrend(String serviceName, List<ServiceThroughput> metrics) {
        metrics.sort(Comparator.comparing(ServiceThroughput::getTimestamp));
        
        List<Double> throughputValues = metrics.stream()
                .map(ServiceThroughput::getAvgRequestsPerSecond)
                .collect(Collectors.toList());
        
        double trendSlope = calculateTrendSlope(throughputValues);
        double confidenceLevel = calculateTrendConfidence(throughputValues, trendSlope);
        
        if (Math.abs(trendSlope) > 0.1 && confidenceLevel > 0.7) {
            String direction = trendSlope > 0 ? "INCREASING" : "DECLINING";
            double magnitude = Math.abs(trendSlope);
            
            ThroughputTrend trend = new ThroughputTrend();
            trend.setTimestamp(LocalDateTime.now());
            trend.setServiceName(serviceName);
            trend.setEndpoint("*");
            trend.setTrendDirection(direction);
            trend.setTrendMagnitude(magnitude);
            trend.setConfidenceLevel(confidenceLevel);
            trend.setCurrentThroughput(metrics.get(metrics.size() - 1).getAvgRequestsPerSecond());
            trend.setBaselineThroughput(metrics.get(0).getAvgRequestsPerSecond());
            
            throughputTrendRepository.save(trend);
            
            if ("DECLINING".equals(direction) && magnitude > 0.2) {
                alertingService.sendAlert("THROUGHPUT_TREND_DECLINE", 
                        String.format("Service %s showing declining throughput trend", serviceName),
                        Map.of("serviceName", serviceName, "magnitude", String.valueOf(magnitude)));
            }
        }
    }

    private void updateThroughputBaselines() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime baselinePeriod = now.minusDays(baselineDays);
        
        List<String> distinctServices = throughputMetricRepository.findDistinctServiceNames();
        
        for (String serviceName : distinctServices) {
            List<String> endpoints = throughputMetricRepository.findDistinctEndpointsByService(serviceName);
            
            for (String endpoint : endpoints) {
                List<ThroughputMetric> metrics = throughputMetricRepository
                        .findByServiceNameAndEndpointAndTimestampBetween(serviceName, endpoint, baselinePeriod, now);
                
                if (metrics.size() >= 100) {
                    updateBaselineForServiceEndpoint(serviceName, endpoint, metrics);
                }
            }
        }
    }

    private void updateBaselineForServiceEndpoint(String serviceName, String endpoint, 
                                                  List<ThroughputMetric> metrics) {
        List<Double> throughputValues = metrics.stream()
                .map(ThroughputMetric::getRequestsPerSecond)
                .sorted()
                .collect(Collectors.toList());
        
        ThroughputStatistics stats = calculateThroughputStatistics(
                throughputValues.stream()
                        .map(val -> new ThroughputSample(LocalDateTime.now(), val))
                        .collect(Collectors.toList()));
        
        ThroughputBaseline baseline = new ThroughputBaseline();
        baseline.setTimestamp(LocalDateTime.now());
        baseline.setServiceName(serviceName);
        baseline.setEndpoint(endpoint);
        baseline.setBaselineThroughput(stats.getAverage());
        baseline.setMinThroughput(stats.getMin());
        baseline.setMaxThroughput(stats.getMax());
        baseline.setStandardDeviation(stats.getStandardDeviation());
        baseline.setSampleSize((long) throughputValues.size());
        baseline.setValidFrom(LocalDateTime.now());
        baseline.setValidTo(LocalDateTime.now().plusDays(baselineDays));
        
        throughputBaselineRepository.save(baseline);
        
        String key = serviceName + ":" + endpoint;
        baselineThroughput.put(key, baseline.getBaselineThroughput());
        
        logger.debug("Updated baseline for {}: {} rps", key, baseline.getBaselineThroughput());
    }

    private void generateThroughputTrends() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime twoHoursAgo = now.minusHours(2);
        
        List<EndpointThroughput> recentMetrics = endpointThroughputRepository
                .findByTimestampBetween(twoHoursAgo, now);
        
        Map<String, List<EndpointThroughput>> endpointGroups = recentMetrics.stream()
                .collect(Collectors.groupingBy(et -> et.getServiceName() + ":" + et.getEndpoint()));
        
        for (Map.Entry<String, List<EndpointThroughput>> entry : endpointGroups.entrySet()) {
            String[] parts = entry.getKey().split(":");
            String serviceName = parts[0];
            String endpoint = parts[1];
            List<EndpointThroughput> metrics = entry.getValue();
            
            if (metrics.size() >= 6) {
                generateTrendForEndpoint(serviceName, endpoint, metrics);
            }
        }
    }

    private void generateTrendForEndpoint(String serviceName, String endpoint, 
                                          List<EndpointThroughput> metrics) {
        metrics.sort(Comparator.comparing(EndpointThroughput::getTimestamp));
        
        List<Double> throughputValues = metrics.stream()
                .map(EndpointThroughput::getRequestsPerSecond)
                .collect(Collectors.toList());
        
        double trendSlope = calculateTrendSlope(throughputValues);
        double confidenceLevel = calculateTrendConfidence(throughputValues, trendSlope);
        
        if (confidenceLevel > 0.6) {
            String direction = trendSlope > 0.05 ? "INCREASING" : 
                              trendSlope < -0.05 ? "DECLINING" : "STABLE";
            
            ThroughputTrend trend = new ThroughputTrend();
            trend.setTimestamp(LocalDateTime.now());
            trend.setServiceName(serviceName);
            trend.setEndpoint(endpoint);
            trend.setTrendDirection(direction);
            trend.setTrendMagnitude(Math.abs(trendSlope));
            trend.setConfidenceLevel(confidenceLevel);
            trend.setCurrentThroughput(throughputValues.get(throughputValues.size() - 1));
            trend.setBaselineThroughput(throughputValues.get(0));
            
            throughputTrendRepository.save(trend);
        }
    }

    private void detectThroughputAnomalies() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minusHours(1);
        
        List<ThroughputMetric> recentMetrics = throughputMetricRepository.findByTimestampBetween(oneHourAgo, now);
        
        Map<String, List<ThroughputMetric>> serviceEndpointGroups = recentMetrics.stream()
                .collect(Collectors.groupingBy(tm -> tm.getServiceName() + ":" + tm.getEndpoint()));
        
        for (Map.Entry<String, List<ThroughputMetric>> entry : serviceEndpointGroups.entrySet()) {
            String key = entry.getKey();
            String[] parts = key.split(":");
            String serviceName = parts[0];
            String endpoint = parts[1];
            
            List<ThroughputMetric> metrics = entry.getValue();
            double baseline = baselineThroughput.getOrDefault(key, 10.0);
            
            detectAnomaliesInMetrics(serviceName, endpoint, metrics, baseline);
        }
    }

    private void detectAnomaliesInMetrics(String serviceName, String endpoint, 
                                          List<ThroughputMetric> metrics, double baseline) {
        for (ThroughputMetric metric : metrics) {
            double zscore = Math.abs((metric.getRequestsPerSecond() - baseline) / (baseline * 0.3));
            
            if (zscore > 3.0) {
                String anomalyType = metric.getRequestsPerSecond() > baseline ? "HIGH_THROUGHPUT" : "LOW_THROUGHPUT";
                double severity = Math.min(zscore / 5.0, 1.0);
                
                generateAnomalyEvent(serviceName, endpoint, metric, baseline, anomalyType, severity);
            }
        }
    }

    private void cleanupOldData() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        
        try {
            int deletedMetrics = throughputMetricRepository.deleteByTimestampBefore(cutoff);
            int deletedAlerts = throughputAlertRepository.deleteByTimestampBefore(cutoff);
            int deletedTrends = throughputTrendRepository.deleteByTimestampBefore(cutoff);
            
            logger.info("Cleaned up old throughput data: {} metrics, {} alerts, {} trends", 
                    deletedMetrics, deletedAlerts, deletedTrends);
            
        } catch (Exception e) {
            logger.error("Error cleaning up old throughput data: {}", e.getMessage(), e);
        }
    }

    private boolean shouldTriggerRealTimeAnalysis(ThroughputMetric metric) {
        String key = metric.getServiceName() + ":" + metric.getEndpoint();
        double baseline = baselineThroughput.getOrDefault(key, 10.0);
        
        return metric.getRequestsPerSecond() > baseline * 2.0 || 
               metric.getRequestsPerSecond() < baseline * 0.5;
    }

    private void performRealTimeThroughputAnalysis(ThroughputMetric metric) {
        String key = metric.getServiceName() + ":" + metric.getEndpoint();
        double baseline = baselineThroughput.getOrDefault(key, 10.0);
        
        if (metric.getRequestsPerSecond() > baseline * 3.0) {
            generateHighThroughputAlert(metric, baseline);
        } else if (metric.getRequestsPerSecond() < baseline * 0.3) {
            generateLowThroughputAlert(metric, baseline);
        }
    }

    private double getThroughputBaseline(String serviceName, String endpoint) {
        String key = serviceName + ":" + endpoint;
        return baselineThroughput.getOrDefault(key, 10.0);
    }

    private void updateThroughputBuffer(String key, ThroughputSample sample) {
        throughputBuffer.computeIfAbsent(key, k -> new ArrayList<>()).add(sample);
        
        List<ThroughputSample> samples = throughputBuffer.get(key);
        if (samples.size() > 100) {
            samples.subList(0, samples.size() - 100).clear();
        }
    }

    private void generateCapacityAlert(ServiceThroughput serviceThroughput) {
        alertingService.sendAlert("CAPACITY_WARNING", 
                String.format("Service %s approaching capacity limit: %.1f%% utilization", 
                        serviceThroughput.getServiceName(), serviceThroughput.getCapacityUtilization() * 100),
                Map.of("serviceName", serviceThroughput.getServiceName(), 
                       "utilization", String.valueOf(serviceThroughput.getCapacityUtilization())));
    }

    private void generateThroughputDegradationAlert(EndpointThroughput endpointThroughput, double baseline) {
        double degradationPercent = ((baseline - endpointThroughput.getRequestsPerSecond()) / baseline) * 100;
        
        ThroughputAlert alert = new ThroughputAlert();
        alert.setTimestamp(LocalDateTime.now());
        alert.setServiceName(endpointThroughput.getServiceName());
        alert.setEndpoint(endpointThroughput.getEndpoint());
        alert.setAlertType("THROUGHPUT_DEGRADATION");
        alert.setSeverity(degradationPercent > 40 ? "HIGH" : "MEDIUM");
        alert.setCurrentThroughput(endpointThroughput.getRequestsPerSecond());
        alert.setBaselineThroughput(baseline);
        alert.setDescription(String.format("Throughput degradation: %.1f%% below baseline", degradationPercent));
        alert.setResolved(false);
        
        throughputAlertRepository.save(alert);
        
        if (degradationPercent > 25) {
            alertingService.sendAlert("THROUGHPUT_DEGRADATION", alert.getDescription(), 
                    Map.of("serviceName", alert.getServiceName(), "endpoint", alert.getEndpoint()));
        }
    }

    private void generateHighThroughputAlert(ThroughputMetric metric, double baseline) {
        ThroughputAlert alert = new ThroughputAlert();
        alert.setTimestamp(LocalDateTime.now());
        alert.setServiceName(metric.getServiceName());
        alert.setEndpoint(metric.getEndpoint());
        alert.setAlertType("HIGH_THROUGHPUT");
        alert.setSeverity("MEDIUM");
        alert.setCurrentThroughput(metric.getRequestsPerSecond());
        alert.setBaselineThroughput(baseline);
        alert.setDescription(String.format("High throughput detected: %.1f rps (baseline: %.1f rps)", 
                metric.getRequestsPerSecond(), baseline));
        alert.setResolved(false);
        
        throughputAlertRepository.save(alert);
        
        metricsService.recordHighThroughput(metric.getServiceName(), metric.getEndpoint(), 
                metric.getRequestsPerSecond());
    }

    private void generateLowThroughputAlert(ThroughputMetric metric, double baseline) {
        ThroughputAlert alert = new ThroughputAlert();
        alert.setTimestamp(LocalDateTime.now());
        alert.setServiceName(metric.getServiceName());
        alert.setEndpoint(metric.getEndpoint());
        alert.setAlertType("LOW_THROUGHPUT");
        alert.setSeverity("MEDIUM");
        alert.setCurrentThroughput(metric.getRequestsPerSecond());
        alert.setBaselineThroughput(baseline);
        alert.setDescription(String.format("Low throughput detected: %.1f rps (baseline: %.1f rps)", 
                metric.getRequestsPerSecond(), baseline));
        alert.setResolved(false);
        
        throughputAlertRepository.save(alert);
        
        metricsService.recordLowThroughput(metric.getServiceName(), metric.getEndpoint(), 
                metric.getRequestsPerSecond());
    }

    private void generateTrendAlert(ThroughputTrend trend) {
        alertingService.sendAlert("THROUGHPUT_TREND", 
                String.format("Throughput trend alert: %s/%s showing %s trend (confidence: %.1f%%)", 
                        trend.getServiceName(), trend.getEndpoint(), trend.getTrendDirection(), 
                        trend.getConfidenceLevel() * 100),
                Map.of("serviceName", trend.getServiceName(), "endpoint", trend.getEndpoint(), 
                       "direction", trend.getTrendDirection()));
    }

    private void generateThroughputSpike(String serviceName, String endpoint, 
                                         ThroughputStatistics stats, double baseline) {
        double spikeRatio = stats.getMax() / baseline;
        
        ThroughputAlert alert = new ThroughputAlert();
        alert.setTimestamp(LocalDateTime.now());
        alert.setServiceName(serviceName);
        alert.setEndpoint(endpoint);
        alert.setAlertType("THROUGHPUT_SPIKE");
        alert.setSeverity(spikeRatio > 5.0 ? "HIGH" : "MEDIUM");
        alert.setCurrentThroughput(stats.getMax());
        alert.setBaselineThroughput(baseline);
        alert.setDescription(String.format("Throughput spike: %.1f rps (%.1fx baseline)", 
                stats.getMax(), spikeRatio));
        alert.setResolved(false);
        
        throughputAlertRepository.save(alert);
        
        if (spikeRatio > 3.0) {
            alertingService.sendAlert("THROUGHPUT_SPIKE", alert.getDescription(), 
                    Map.of("serviceName", serviceName, "endpoint", endpoint, 
                           "spikeRatio", String.valueOf(spikeRatio)));
        }
    }

    private void generateThroughputDegradationAlert(String serviceName, String endpoint, 
                                                    ThroughputStatistics stats, double baseline) {
        double degradationPercent = ((baseline - stats.getAverage()) / baseline) * 100;
        
        ThroughputAlert alert = new ThroughputAlert();
        alert.setTimestamp(LocalDateTime.now());
        alert.setServiceName(serviceName);
        alert.setEndpoint(endpoint);
        alert.setAlertType("THROUGHPUT_DEGRADATION");
        alert.setSeverity(degradationPercent > 40 ? "HIGH" : "MEDIUM");
        alert.setCurrentThroughput(stats.getAverage());
        alert.setBaselineThroughput(baseline);
        alert.setDescription(String.format("Throughput degradation: %.1f%% below baseline", degradationPercent));
        alert.setResolved(false);
        
        throughputAlertRepository.save(alert);
        
        if (degradationPercent > 25) {
            alertingService.sendAlert("THROUGHPUT_DEGRADATION", alert.getDescription(), 
                    Map.of("serviceName", serviceName, "endpoint", endpoint, 
                           "degradation", String.valueOf(degradationPercent)));
        }
    }

    private void generateAnomalyEvent(String serviceName, String endpoint, ThroughputMetric metric, 
                                      double baseline, String anomalyType, double severity) {
        try {
            Map<String, Object> anomalyData = Map.of(
                    "eventType", "THROUGHPUT_ANOMALY",
                    "timestamp", metric.getTimestamp().toString(),
                    "serviceName", serviceName,
                    "endpoint", endpoint,
                    "throughput", metric.getRequestsPerSecond(),
                    "baseline", baseline,
                    "anomalyType", anomalyType,
                    "severity", severity
            );
            
            String anomalyJson = objectMapper.writeValueAsString(anomalyData);
            
        } catch (JsonProcessingException e) {
            logger.error("Error generating anomaly event: {}", e.getMessage(), e);
        }
    }

    private void analyzeServiceThroughputTrend(ServiceThroughput serviceThroughput) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime twentyFourHoursAgo = now.minusHours(24);
        
        List<ServiceThroughput> historicalData = serviceThroughputRepository
                .findByServiceNameAndTimestampBetween(serviceThroughput.getServiceName(), twentyFourHoursAgo, now);
        
        if (historicalData.size() >= 24) {
            List<Double> throughputValues = historicalData.stream()
                    .map(ServiceThroughput::getAvgRequestsPerSecond)
                    .collect(Collectors.toList());
            
            double trendSlope = calculateTrendSlope(throughputValues);
            
            if (trendSlope < -0.1) {
                alertingService.sendAlert("SERVICE_THROUGHPUT_DECLINE", 
                        String.format("Service %s showing declining throughput trend", serviceThroughput.getServiceName()),
                        Map.of("serviceName", serviceThroughput.getServiceName(), "trend", String.valueOf(trendSlope)));
            }
        }
    }

    private void analyzeQueuePerformance(QueueThroughput queueThroughput) {
        if (queueThroughput.getProducerRate() > queueThroughput.getConsumerRate() * 1.5) {
            alertingService.sendAlert("QUEUE_BACKLOG", 
                    String.format("Queue %s showing backlog: producer %.1f/s > consumer %.1f/s", 
                            queueThroughput.getQueueName(), queueThroughput.getProducerRate(), 
                            queueThroughput.getConsumerRate()),
                    Map.of("queueName", queueThroughput.getQueueName(), 
                           "producerRate", String.valueOf(queueThroughput.getProducerRate()),
                           "consumerRate", String.valueOf(queueThroughput.getConsumerRate())));
        }
        
        double queueUtilization = (double) queueThroughput.getQueueDepth() / queueThroughput.getMaxQueueDepth();
        if (queueUtilization > 0.8) {
            alertingService.sendAlert("QUEUE_CAPACITY_WARNING", 
                    String.format("Queue %s at %.1f%% capacity", 
                            queueThroughput.getQueueName(), queueUtilization * 100),
                    Map.of("queueName", queueThroughput.getQueueName(), 
                           "utilization", String.valueOf(queueUtilization)));
        }
    }

    private void analyzeDatabasePerformance(DatabaseThroughput dbThroughput) {
        if (dbThroughput.getTransactionsPerSecond() > 1000) {
            alertingService.sendAlert("HIGH_DATABASE_TPS", 
                    String.format("High database TPS: %s at %.1f tps", 
                            dbThroughput.getDatabaseName(), dbThroughput.getTransactionsPerSecond()),
                    Map.of("database", dbThroughput.getDatabaseName(), 
                           "tps", String.valueOf(dbThroughput.getTransactionsPerSecond())));
        }
        
        double connectionUtilization = (double) dbThroughput.getActiveConnections() / dbThroughput.getConnectionPoolSize();
        if (connectionUtilization > 0.8) {
            alertingService.sendAlert("DATABASE_CONNECTION_POOL_WARNING", 
                    String.format("Database %s connection pool at %.1f%% utilization", 
                            dbThroughput.getDatabaseName(), connectionUtilization * 100),
                    Map.of("database", dbThroughput.getDatabaseName(), 
                           "utilization", String.valueOf(connectionUtilization)));
        }
    }

    private void investigateThroughputSpike(String serviceName, String endpoint, 
                                            double currentThroughput, double baselineThroughput, String cause) {
    }

    private void investigateThroughputDrop(String serviceName, String endpoint, 
                                           double currentThroughput, double previousThroughput, String cause) {
    }

    private void analyzeThroughputDegradation(String serviceName, String endpoint, 
                                              double currentThroughput, double baselineThroughput) {
    }

    private void initiateCapacityScaling(String serviceName, String endpoint, 
                                         double throughput, double capacityUtilization) {
    }

    private void investigateLowThroughput(String serviceName, String endpoint, 
                                          double throughput, double minThreshold, String impact) {
    }

    private boolean shouldResolveAlert(ThroughputAlert alert, double currentThroughput) {
        if ("LOW_THROUGHPUT".equals(alert.getAlertType()) || "THROUGHPUT_DEGRADATION".equals(alert.getAlertType())) {
            return currentThroughput > alert.getThreshold() * 1.1;
        } else if ("HIGH_THROUGHPUT".equals(alert.getAlertType()) || "THROUGHPUT_SPIKE".equals(alert.getAlertType())) {
            return currentThroughput < alert.getThreshold() * 0.9;
        }
        return false;
    }

    private ThroughputStatistics calculateThroughputStatistics(List<ThroughputSample> samples) {
        if (samples.isEmpty()) {
            return new ThroughputStatistics();
        }
        
        List<Double> values = samples.stream()
                .map(ThroughputSample::getThroughput)
                .sorted()
                .collect(Collectors.toList());
        
        double average = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double min = values.get(0);
        double max = values.get(values.size() - 1);
        
        double variance = values.stream()
                .mapToDouble(val -> Math.pow(val - average, 2))
                .average().orElse(0.0);
        double stdDev = Math.sqrt(variance);
        
        return new ThroughputStatistics(average, min, max, stdDev);
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

    private static class ThroughputSample {
        private final LocalDateTime timestamp;
        private final double throughput;
        
        public ThroughputSample(LocalDateTime timestamp, double throughput) {
            this.timestamp = timestamp;
            this.throughput = throughput;
        }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public double getThroughput() { return throughput; }
    }

    private static class ThroughputStatistics {
        private final double average;
        private final double min;
        private final double max;
        private final double standardDeviation;
        
        public ThroughputStatistics() {
            this(0, 0, 0, 0);
        }
        
        public ThroughputStatistics(double average, double min, double max, double standardDeviation) {
            this.average = average;
            this.min = min;
            this.max = max;
            this.standardDeviation = standardDeviation;
        }
        
        public double getAverage() { return average; }
        public double getMin() { return min; }
        public double getMax() { return max; }
        public double getStandardDeviation() { return standardDeviation; }
    }

    private static class ThroughputWindow {
        private final LocalDateTime startTime;
        private final List<ThroughputSample> samples;
        
        public ThroughputWindow(LocalDateTime startTime) {
            this.startTime = startTime;
            this.samples = new ArrayList<>();
        }
        
        public LocalDateTime getStartTime() { return startTime; }
        public List<ThroughputSample> getSamples() { return samples; }
        public void addSample(ThroughputSample sample) { samples.add(sample); }
    }
}