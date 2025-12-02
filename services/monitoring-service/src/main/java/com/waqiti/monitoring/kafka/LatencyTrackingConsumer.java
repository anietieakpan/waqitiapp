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
import java.util.stream.Collectors;

@Component
public class LatencyTrackingConsumer extends BaseKafkaConsumer {

    private static final Logger logger = LoggerFactory.getLogger(LatencyTrackingConsumer.class);
    
    @Value("${waqiti.monitoring.latency.analysis-window-minutes:10}")
    private int analysisWindowMinutes;
    
    @Value("${waqiti.monitoring.latency.percentile-threshold:95}")
    private double percentileThreshold;
    
    @Value("${waqiti.monitoring.latency.spike-multiplier:3.0}")
    private double spikeMultiplier;
    
    @Value("${waqiti.monitoring.latency.baseline-samples:100}")
    private int baselineSamples;
    
    @Value("${waqiti.monitoring.latency.trend-analysis-days:7}")
    private int trendAnalysisDays;

    private final LatencyMetricRepository latencyMetricRepository;
    private final LatencyTrendRepository latencyTrendRepository;
    private final LatencyBaselineRepository latencyBaselineRepository;
    private final LatencyAlertRepository latencyAlertRepository;
    private final ServiceLatencyRepository serviceLatencyRepository;
    private final EndpointLatencyRepository endpointLatencyRepository;
    private final DatabaseLatencyRepository databaseLatencyRepository;
    private final ExternalServiceLatencyRepository externalServiceLatencyRepository;
    private final AlertingService alertingService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public LatencyTrackingConsumer(
            LatencyMetricRepository latencyMetricRepository,
            LatencyTrendRepository latencyTrendRepository,
            LatencyBaselineRepository latencyBaselineRepository,
            LatencyAlertRepository latencyAlertRepository,
            ServiceLatencyRepository serviceLatencyRepository,
            EndpointLatencyRepository endpointLatencyRepository,
            DatabaseLatencyRepository databaseLatencyRepository,
            ExternalServiceLatencyRepository externalServiceLatencyRepository,
            AlertingService alertingService,
            MetricsService metricsService,
            NotificationService notificationService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.latencyMetricRepository = latencyMetricRepository;
        this.latencyTrendRepository = latencyTrendRepository;
        this.latencyBaselineRepository = latencyBaselineRepository;
        this.latencyAlertRepository = latencyAlertRepository;
        this.serviceLatencyRepository = serviceLatencyRepository;
        this.endpointLatencyRepository = endpointLatencyRepository;
        this.databaseLatencyRepository = databaseLatencyRepository;
        this.externalServiceLatencyRepository = externalServiceLatencyRepository;
        this.alertingService = alertingService;
        this.metricsService = metricsService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(4);
    private final Map<String, List<Double>> latencyBuffer = new ConcurrentHashMap<>();
    private final Map<String, Double> baselineLatencies = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastAnalysis = new ConcurrentHashMap<>();

    private Counter processedEventsCounter;
    private Counter processedLatencyDataCounter;
    private Counter processedEndpointLatencyCounter;
    private Counter processedServiceLatencyCounter;
    private Counter processedDatabaseLatencyCounter;
    private Counter processedExternalServiceLatencyCounter;
    private Counter processedLatencyTrendCounter;
    private Counter processedLatencyBaselineCounter;
    private Counter processedLatencyAnomalyCounter;
    private Counter processedLatencyAlertCounter;
    private Counter processedCriticalLatencyCounter;
    private Counter processedWarningLatencyCounter;
    private Counter processedLatencySpikesCounter;
    private Counter processedLatencyDegradationCounter;
    private Counter processedLatencyRecoveryCounter;
    private Timer latencyProcessingTimer;
    private Timer latencyAnalysisTimer;

    @PostConstruct
    public void init() {
        this.processedEventsCounter = Counter.builder("latency_tracking_events_processed")
                .description("Number of latency tracking events processed")
                .register(meterRegistry);
        
        this.processedLatencyDataCounter = Counter.builder("latency_data_processed")
                .description("Number of latency data events processed")
                .register(meterRegistry);
        
        this.processedEndpointLatencyCounter = Counter.builder("endpoint_latency_processed")
                .description("Number of endpoint latency events processed")
                .register(meterRegistry);
        
        this.processedServiceLatencyCounter = Counter.builder("service_latency_processed")
                .description("Number of service latency events processed")
                .register(meterRegistry);
        
        this.processedDatabaseLatencyCounter = Counter.builder("database_latency_processed")
                .description("Number of database latency events processed")
                .register(meterRegistry);
        
        this.processedExternalServiceLatencyCounter = Counter.builder("external_service_latency_processed")
                .description("Number of external service latency events processed")
                .register(meterRegistry);
        
        this.processedLatencyTrendCounter = Counter.builder("latency_trend_processed")
                .description("Number of latency trend events processed")
                .register(meterRegistry);
        
        this.processedLatencyBaselineCounter = Counter.builder("latency_baseline_processed")
                .description("Number of latency baseline events processed")
                .register(meterRegistry);
        
        this.processedLatencyAnomalyCounter = Counter.builder("latency_anomaly_processed")
                .description("Number of latency anomaly events processed")
                .register(meterRegistry);
        
        this.processedLatencyAlertCounter = Counter.builder("latency_alert_processed")
                .description("Number of latency alert events processed")
                .register(meterRegistry);
        
        this.processedCriticalLatencyCounter = Counter.builder("critical_latency_processed")
                .description("Number of critical latency events processed")
                .register(meterRegistry);
        
        this.processedWarningLatencyCounter = Counter.builder("warning_latency_processed")
                .description("Number of warning latency events processed")
                .register(meterRegistry);
        
        this.processedLatencySpikesCounter = Counter.builder("latency_spikes_processed")
                .description("Number of latency spike events processed")
                .register(meterRegistry);
        
        this.processedLatencyDegradationCounter = Counter.builder("latency_degradation_processed")
                .description("Number of latency degradation events processed")
                .register(meterRegistry);
        
        this.processedLatencyRecoveryCounter = Counter.builder("latency_recovery_processed")
                .description("Number of latency recovery events processed")
                .register(meterRegistry);
        
        this.latencyProcessingTimer = Timer.builder("latency_processing_duration")
                .description("Time taken to process latency events")
                .register(meterRegistry);
        
        this.latencyAnalysisTimer = Timer.builder("latency_analysis_duration")
                .description("Time taken to perform latency analysis")
                .register(meterRegistry);

        scheduledExecutor.scheduleAtFixedRate(this::performLatencyAnalysis, 
                analysisWindowMinutes, analysisWindowMinutes, TimeUnit.MINUTES);
        
        scheduledExecutor.scheduleAtFixedRate(this::updateLatencyBaselines, 
                1, 1, TimeUnit.HOURS);
        
        scheduledExecutor.scheduleAtFixedRate(this::generateLatencyTrends, 
                5, 5, TimeUnit.MINUTES);
        
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

    @KafkaListener(topics = "latency-tracking", groupId = "latency-tracking-group", 
                   containerFactory = "kafkaListenerContainerFactory")
    @CircuitBreaker(name = "latency-tracking-consumer")
    @Retry(name = "latency-tracking-consumer")
    @Transactional
    public void handleLatencyTrackingEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "latency-tracking");

        try {
            logger.info("Processing latency tracking event: partition={}, offset={}", 
                    record.partition(), record.offset());

            JsonNode eventData = objectMapper.readTree(record.value());
            String eventType = eventData.path("eventType").asText();

            switch (eventType) {
                case "LATENCY_DATA":
                    processLatencyData(eventData);
                    processedLatencyDataCounter.increment();
                    break;
                case "ENDPOINT_LATENCY":
                    processEndpointLatency(eventData);
                    processedEndpointLatencyCounter.increment();
                    break;
                case "SERVICE_LATENCY":
                    processServiceLatency(eventData);
                    processedServiceLatencyCounter.increment();
                    break;
                case "DATABASE_LATENCY":
                    processDatabaseLatency(eventData);
                    processedDatabaseLatencyCounter.increment();
                    break;
                case "EXTERNAL_SERVICE_LATENCY":
                    processExternalServiceLatency(eventData);
                    processedExternalServiceLatencyCounter.increment();
                    break;
                case "LATENCY_TREND":
                    processLatencyTrend(eventData);
                    processedLatencyTrendCounter.increment();
                    break;
                case "LATENCY_BASELINE":
                    processLatencyBaseline(eventData);
                    processedLatencyBaselineCounter.increment();
                    break;
                case "LATENCY_ANOMALY":
                    processLatencyAnomaly(eventData);
                    processedLatencyAnomalyCounter.increment();
                    break;
                case "LATENCY_ALERT":
                    processLatencyAlert(eventData);
                    processedLatencyAlertCounter.increment();
                    break;
                case "CRITICAL_LATENCY":
                    processCriticalLatency(eventData);
                    processedCriticalLatencyCounter.increment();
                    break;
                case "WARNING_LATENCY":
                    processWarningLatency(eventData);
                    processedWarningLatencyCounter.increment();
                    break;
                case "LATENCY_SPIKE":
                    processLatencySpike(eventData);
                    processedLatencySpikesCounter.increment();
                    break;
                case "LATENCY_DEGRADATION":
                    processLatencyDegradation(eventData);
                    processedLatencyDegradationCounter.increment();
                    break;
                case "LATENCY_RECOVERY":
                    processLatencyRecovery(eventData);
                    processedLatencyRecoveryCounter.increment();
                    break;
                default:
                    logger.warn("Unknown latency tracking event type: {}", eventType);
            }

            processedEventsCounter.increment();
            ack.acknowledge();

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse latency tracking event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } catch (DataAccessException e) {
            logger.error("Database error processing latency tracking event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } catch (Exception e) {
            logger.error("Unexpected error processing latency tracking event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            sample.stop(latencyProcessingTimer);
            MDC.clear();
        }
    }

    private void processLatencyData(JsonNode eventData) {
        try {
            LatencyMetric metric = new LatencyMetric();
            metric.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            metric.setServiceName(eventData.path("serviceName").asText());
            metric.setEndpoint(eventData.path("endpoint").asText());
            metric.setLatencyMs(eventData.path("latencyMs").asDouble());
            metric.setRequestId(eventData.path("requestId").asText());
            metric.setUserId(eventData.path("userId").asText());
            metric.setResponseStatus(eventData.path("responseStatus").asText());
            metric.setMethod(eventData.path("method").asText());
            
            JsonNode contextNode = eventData.path("context");
            if (!contextNode.isMissingNode()) {
                metric.setContext(contextNode.toString());
            }
            
            latencyMetricRepository.save(metric);
            
            String key = metric.getServiceName() + ":" + metric.getEndpoint();
            latencyBuffer.computeIfAbsent(key, k -> new ArrayList<>()).add(metric.getLatencyMs());
            
            metricsService.recordLatencyMetric(metric.getServiceName(), metric.getEndpoint(), 
                    metric.getLatencyMs());
            
            if (shouldTriggerRealTimeAnalysis(metric)) {
                performRealTimeLatencyAnalysis(metric);
            }
            
            logger.debug("Processed latency data for service: {}, endpoint: {}, latency: {}ms", 
                    metric.getServiceName(), metric.getEndpoint(), metric.getLatencyMs());
            
        } catch (Exception e) {
            logger.error("Error processing latency data: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processEndpointLatency(JsonNode eventData) {
        try {
            EndpointLatency endpointLatency = new EndpointLatency();
            endpointLatency.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            endpointLatency.setServiceName(eventData.path("serviceName").asText());
            endpointLatency.setEndpoint(eventData.path("endpoint").asText());
            endpointLatency.setMethod(eventData.path("method").asText());
            endpointLatency.setP50(eventData.path("p50").asDouble());
            endpointLatency.setP95(eventData.path("p95").asDouble());
            endpointLatency.setP99(eventData.path("p99").asDouble());
            endpointLatency.setAvgLatency(eventData.path("avgLatency").asDouble());
            endpointLatency.setMaxLatency(eventData.path("maxLatency").asDouble());
            endpointLatency.setMinLatency(eventData.path("minLatency").asDouble());
            endpointLatency.setRequestCount(eventData.path("requestCount").asLong());
            endpointLatency.setErrorRate(eventData.path("errorRate").asDouble());
            
            endpointLatencyRepository.save(endpointLatency);
            
            metricsService.recordEndpointLatencyMetrics(endpointLatency.getServiceName(), 
                    endpointLatency.getEndpoint(), endpointLatency);
            
            if (endpointLatency.getP95() > getLatencyThreshold(endpointLatency.getServiceName(), 
                    endpointLatency.getEndpoint())) {
                generateLatencyAlert(endpointLatency);
            }
            
            logger.debug("Processed endpoint latency for service: {}, endpoint: {}", 
                    endpointLatency.getServiceName(), endpointLatency.getEndpoint());
            
        } catch (Exception e) {
            logger.error("Error processing endpoint latency: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processServiceLatency(JsonNode eventData) {
        try {
            ServiceLatency serviceLatency = new ServiceLatency();
            serviceLatency.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            serviceLatency.setServiceName(eventData.path("serviceName").asText());
            serviceLatency.setAvgLatency(eventData.path("avgLatency").asDouble());
            serviceLatency.setP50(eventData.path("p50").asDouble());
            serviceLatency.setP95(eventData.path("p95").asDouble());
            serviceLatency.setP99(eventData.path("p99").asDouble());
            serviceLatency.setMaxLatency(eventData.path("maxLatency").asDouble());
            serviceLatency.setMinLatency(eventData.path("minLatency").asDouble());
            serviceLatency.setRequestCount(eventData.path("requestCount").asLong());
            serviceLatency.setErrorCount(eventData.path("errorCount").asLong());
            serviceLatency.setTimeoutCount(eventData.path("timeoutCount").asLong());
            
            JsonNode distributionNode = eventData.path("latencyDistribution");
            if (!distributionNode.isMissingNode()) {
                serviceLatency.setLatencyDistribution(distributionNode.toString());
            }
            
            serviceLatencyRepository.save(serviceLatency);
            
            metricsService.recordServiceLatencyMetrics(serviceLatency.getServiceName(), serviceLatency);
            
            analyzeServiceLatencyTrend(serviceLatency);
            
            logger.debug("Processed service latency for service: {}", serviceLatency.getServiceName());
            
        } catch (Exception e) {
            logger.error("Error processing service latency: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processDatabaseLatency(JsonNode eventData) {
        try {
            DatabaseLatency dbLatency = new DatabaseLatency();
            dbLatency.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            dbLatency.setDatabaseName(eventData.path("databaseName").asText());
            dbLatency.setQueryType(eventData.path("queryType").asText());
            dbLatency.setTableName(eventData.path("tableName").asText());
            dbLatency.setQueryLatencyMs(eventData.path("queryLatencyMs").asDouble());
            dbLatency.setConnectionLatencyMs(eventData.path("connectionLatencyMs").asDouble());
            dbLatency.setRowsAffected(eventData.path("rowsAffected").asLong());
            dbLatency.setQueryHash(eventData.path("queryHash").asText());
            dbLatency.setIndexesUsed(eventData.path("indexesUsed").asText());
            dbLatency.setExecutionPlan(eventData.path("executionPlan").asText());
            
            databaseLatencyRepository.save(dbLatency);
            
            metricsService.recordDatabaseLatencyMetrics(dbLatency.getDatabaseName(), 
                    dbLatency.getQueryType(), dbLatency.getQueryLatencyMs());
            
            if (dbLatency.getQueryLatencyMs() > getDatabaseLatencyThreshold(dbLatency.getDatabaseName(), 
                    dbLatency.getQueryType())) {
                generateDatabaseLatencyAlert(dbLatency);
            }
            
            analyzeDatabaseQueryPerformance(dbLatency);
            
            logger.debug("Processed database latency for database: {}, query type: {}", 
                    dbLatency.getDatabaseName(), dbLatency.getQueryType());
            
        } catch (Exception e) {
            logger.error("Error processing database latency: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processExternalServiceLatency(JsonNode eventData) {
        try {
            ExternalServiceLatency extLatency = new ExternalServiceLatency();
            extLatency.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            extLatency.setServiceName(eventData.path("serviceName").asText());
            extLatency.setEndpoint(eventData.path("endpoint").asText());
            extLatency.setMethod(eventData.path("method").asText());
            extLatency.setLatencyMs(eventData.path("latencyMs").asDouble());
            extLatency.setResponseStatus(eventData.path("responseStatus").asText());
            extLatency.setRequestSize(eventData.path("requestSize").asLong());
            extLatency.setResponseSize(eventData.path("responseSize").asLong());
            extLatency.setConnectionTime(eventData.path("connectionTime").asDouble());
            extLatency.setSslHandshakeTime(eventData.path("sslHandshakeTime").asDouble());
            extLatency.setDnsLookupTime(eventData.path("dnsLookupTime").asDouble());
            
            externalServiceLatencyRepository.save(extLatency);
            
            metricsService.recordExternalServiceLatencyMetrics(extLatency.getServiceName(), 
                    extLatency.getEndpoint(), extLatency.getLatencyMs());
            
            analyzeExternalServiceHealth(extLatency);
            
            logger.debug("Processed external service latency for service: {}, endpoint: {}", 
                    extLatency.getServiceName(), extLatency.getEndpoint());
            
        } catch (Exception e) {
            logger.error("Error processing external service latency: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processLatencyTrend(JsonNode eventData) {
        try {
            LatencyTrend trend = new LatencyTrend();
            trend.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            trend.setServiceName(eventData.path("serviceName").asText());
            trend.setEndpoint(eventData.path("endpoint").asText());
            trend.setTrendDirection(eventData.path("trendDirection").asText());
            trend.setTrendMagnitude(eventData.path("trendMagnitude").asDouble());
            trend.setConfidenceLevel(eventData.path("confidenceLevel").asDouble());
            trend.setPredictedLatency(eventData.path("predictedLatency").asDouble());
            trend.setCurrentLatency(eventData.path("currentLatency").asDouble());
            trend.setBaselineLatency(eventData.path("baselineLatency").asDouble());
            
            JsonNode trendDataNode = eventData.path("trendData");
            if (!trendDataNode.isMissingNode()) {
                trend.setTrendData(trendDataNode.toString());
            }
            
            latencyTrendRepository.save(trend);
            
            if ("DEGRADING".equals(trend.getTrendDirection()) && trend.getConfidenceLevel() > 0.8) {
                generateTrendAlert(trend);
            }
            
            logger.debug("Processed latency trend for service: {}, endpoint: {}, direction: {}", 
                    trend.getServiceName(), trend.getEndpoint(), trend.getTrendDirection());
            
        } catch (Exception e) {
            logger.error("Error processing latency trend: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processLatencyBaseline(JsonNode eventData) {
        try {
            LatencyBaseline baseline = new LatencyBaseline();
            baseline.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            baseline.setServiceName(eventData.path("serviceName").asText());
            baseline.setEndpoint(eventData.path("endpoint").asText());
            baseline.setBaselineLatency(eventData.path("baselineLatency").asDouble());
            baseline.setP50Baseline(eventData.path("p50Baseline").asDouble());
            baseline.setP95Baseline(eventData.path("p95Baseline").asDouble());
            baseline.setP99Baseline(eventData.path("p99Baseline").asDouble());
            baseline.setStandardDeviation(eventData.path("standardDeviation").asDouble());
            baseline.setSampleSize(eventData.path("sampleSize").asLong());
            baseline.setConfidenceInterval(eventData.path("confidenceInterval").asDouble());
            baseline.setValidFrom(parseTimestamp(eventData.path("validFrom").asText()));
            baseline.setValidTo(parseTimestamp(eventData.path("validTo").asText()));
            
            latencyBaselineRepository.save(baseline);
            
            String key = baseline.getServiceName() + ":" + baseline.getEndpoint();
            baselineLatencies.put(key, baseline.getBaselineLatency());
            
            logger.debug("Processed latency baseline for service: {}, endpoint: {}, baseline: {}ms", 
                    baseline.getServiceName(), baseline.getEndpoint(), baseline.getBaselineLatency());
            
        } catch (Exception e) {
            logger.error("Error processing latency baseline: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processLatencyAnomaly(JsonNode eventData) {
        try {
            String serviceName = eventData.path("serviceName").asText();
            String endpoint = eventData.path("endpoint").asText();
            double latency = eventData.path("latency").asDouble();
            double baseline = eventData.path("baseline").asDouble();
            double severity = eventData.path("severity").asDouble();
            String anomalyType = eventData.path("anomalyType").asText();
            
            LatencyAlert alert = new LatencyAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setServiceName(serviceName);
            alert.setEndpoint(endpoint);
            alert.setAlertType("ANOMALY");
            alert.setSeverity(severity > 0.8 ? "HIGH" : severity > 0.5 ? "MEDIUM" : "LOW");
            alert.setCurrentLatency(latency);
            alert.setBaselineLatency(baseline);
            alert.setThreshold(baseline * (1 + severity));
            alert.setDescription(String.format("Latency anomaly detected: %s - Current: %.2fms, Baseline: %.2fms", 
                    anomalyType, latency, baseline));
            alert.setResolved(false);
            
            latencyAlertRepository.save(alert);
            
            if (severity > 0.7) {
                alertingService.sendAlert("LATENCY_ANOMALY", alert.getDescription(), 
                        Map.of("serviceName", serviceName, "endpoint", endpoint, "severity", String.valueOf(severity)));
            }
            
            logger.info("Processed latency anomaly for service: {}, endpoint: {}, severity: {}", 
                    serviceName, endpoint, severity);
            
        } catch (Exception e) {
            logger.error("Error processing latency anomaly: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processLatencyAlert(JsonNode eventData) {
        try {
            LatencyAlert alert = new LatencyAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setServiceName(eventData.path("serviceName").asText());
            alert.setEndpoint(eventData.path("endpoint").asText());
            alert.setAlertType(eventData.path("alertType").asText());
            alert.setSeverity(eventData.path("severity").asText());
            alert.setCurrentLatency(eventData.path("currentLatency").asDouble());
            alert.setBaselineLatency(eventData.path("baselineLatency").asDouble());
            alert.setThreshold(eventData.path("threshold").asDouble());
            alert.setDescription(eventData.path("description").asText());
            alert.setResolved(eventData.path("resolved").asBoolean());
            
            if (eventData.has("resolvedAt")) {
                alert.setResolvedAt(parseTimestamp(eventData.path("resolvedAt").asText()));
            }
            
            latencyAlertRepository.save(alert);
            
            if (!alert.isResolved() && ("HIGH".equals(alert.getSeverity()) || "CRITICAL".equals(alert.getSeverity()))) {
                alertingService.sendAlert("LATENCY_ALERT", alert.getDescription(), 
                        Map.of("serviceName", alert.getServiceName(), "endpoint", alert.getEndpoint(), 
                               "severity", alert.getSeverity()));
            }
            
            logger.info("Processed latency alert for service: {}, endpoint: {}, severity: {}", 
                    alert.getServiceName(), alert.getEndpoint(), alert.getSeverity());
            
        } catch (Exception e) {
            logger.error("Error processing latency alert: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processCriticalLatency(JsonNode eventData) {
        try {
            String serviceName = eventData.path("serviceName").asText();
            String endpoint = eventData.path("endpoint").asText();
            double latency = eventData.path("latency").asDouble();
            double threshold = eventData.path("threshold").asDouble();
            String impact = eventData.path("impact").asText();
            
            LatencyAlert alert = new LatencyAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setServiceName(serviceName);
            alert.setEndpoint(endpoint);
            alert.setAlertType("CRITICAL_LATENCY");
            alert.setSeverity("CRITICAL");
            alert.setCurrentLatency(latency);
            alert.setThreshold(threshold);
            alert.setDescription(String.format("Critical latency detected: %.2fms (threshold: %.2fms) - %s", 
                    latency, threshold, impact));
            alert.setResolved(false);
            
            latencyAlertRepository.save(alert);
            
            alertingService.sendCriticalAlert("CRITICAL_LATENCY", alert.getDescription(), 
                    Map.of("serviceName", serviceName, "endpoint", endpoint, "latency", String.valueOf(latency)));
            
            notificationService.sendPagerDutyAlert("CRITICAL_LATENCY", alert.getDescription());
            
            initiateAutomaticMitigation(serviceName, endpoint, latency, threshold);
            
            logger.warn("Processed critical latency for service: {}, endpoint: {}, latency: {}ms", 
                    serviceName, endpoint, latency);
            
        } catch (Exception e) {
            logger.error("Error processing critical latency: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processWarningLatency(JsonNode eventData) {
        try {
            String serviceName = eventData.path("serviceName").asText();
            String endpoint = eventData.path("endpoint").asText();
            double latency = eventData.path("latency").asDouble();
            double warningThreshold = eventData.path("warningThreshold").asDouble();
            
            LatencyAlert alert = new LatencyAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setServiceName(serviceName);
            alert.setEndpoint(endpoint);
            alert.setAlertType("WARNING_LATENCY");
            alert.setSeverity("WARNING");
            alert.setCurrentLatency(latency);
            alert.setThreshold(warningThreshold);
            alert.setDescription(String.format("Warning latency detected: %.2fms (threshold: %.2fms)", 
                    latency, warningThreshold));
            alert.setResolved(false);
            
            latencyAlertRepository.save(alert);
            
            metricsService.recordWarningLatency(serviceName, endpoint, latency);
            
            logger.info("Processed warning latency for service: {}, endpoint: {}, latency: {}ms", 
                    serviceName, endpoint, latency);
            
        } catch (Exception e) {
            logger.error("Error processing warning latency: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processLatencySpike(JsonNode eventData) {
        try {
            String serviceName = eventData.path("serviceName").asText();
            String endpoint = eventData.path("endpoint").asText();
            double currentLatency = eventData.path("currentLatency").asDouble();
            double previousLatency = eventData.path("previousLatency").asDouble();
            double spikeRatio = eventData.path("spikeRatio").asDouble();
            String cause = eventData.path("cause").asText();
            
            LatencyAlert alert = new LatencyAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setServiceName(serviceName);
            alert.setEndpoint(endpoint);
            alert.setAlertType("LATENCY_SPIKE");
            alert.setSeverity(spikeRatio > 5.0 ? "HIGH" : "MEDIUM");
            alert.setCurrentLatency(currentLatency);
            alert.setBaselineLatency(previousLatency);
            alert.setDescription(String.format("Latency spike detected: %.2fms (%.1fx increase) - %s", 
                    currentLatency, spikeRatio, cause));
            alert.setResolved(false);
            
            latencyAlertRepository.save(alert);
            
            if (spikeRatio > 3.0) {
                alertingService.sendAlert("LATENCY_SPIKE", alert.getDescription(), 
                        Map.of("serviceName", serviceName, "endpoint", endpoint, "spikeRatio", String.valueOf(spikeRatio)));
            }
            
            investigateLatencySpike(serviceName, endpoint, currentLatency, previousLatency, cause);
            
            logger.warn("Processed latency spike for service: {}, endpoint: {}, spike ratio: {}x", 
                    serviceName, endpoint, spikeRatio);
            
        } catch (Exception e) {
            logger.error("Error processing latency spike: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processLatencyDegradation(JsonNode eventData) {
        try {
            String serviceName = eventData.path("serviceName").asText();
            String endpoint = eventData.path("endpoint").asText();
            double currentLatency = eventData.path("currentLatency").asDouble();
            double baselineLatency = eventData.path("baselineLatency").asDouble();
            double degradationPercent = eventData.path("degradationPercent").asDouble();
            String duration = eventData.path("duration").asText();
            
            LatencyAlert alert = new LatencyAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setServiceName(serviceName);
            alert.setEndpoint(endpoint);
            alert.setAlertType("LATENCY_DEGRADATION");
            alert.setSeverity(degradationPercent > 50 ? "HIGH" : "MEDIUM");
            alert.setCurrentLatency(currentLatency);
            alert.setBaselineLatency(baselineLatency);
            alert.setDescription(String.format("Sustained latency degradation: %.1f%% increase over %s", 
                    degradationPercent, duration));
            alert.setResolved(false);
            
            latencyAlertRepository.save(alert);
            
            if (degradationPercent > 30) {
                alertingService.sendAlert("LATENCY_DEGRADATION", alert.getDescription(), 
                        Map.of("serviceName", serviceName, "endpoint", endpoint, 
                               "degradation", String.valueOf(degradationPercent)));
            }
            
            analyzePerformanceDegradation(serviceName, endpoint, currentLatency, baselineLatency);
            
            logger.warn("Processed latency degradation for service: {}, endpoint: {}, degradation: {}%", 
                    serviceName, endpoint, degradationPercent);
            
        } catch (Exception e) {
            logger.error("Error processing latency degradation: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processLatencyRecovery(JsonNode eventData) {
        try {
            String serviceName = eventData.path("serviceName").asText();
            String endpoint = eventData.path("endpoint").asText();
            double currentLatency = eventData.path("currentLatency").asDouble();
            double previousLatency = eventData.path("previousLatency").asDouble();
            double improvementPercent = eventData.path("improvementPercent").asDouble();
            String recoveryDuration = eventData.path("recoveryDuration").asText();
            
            List<LatencyAlert> unresolvedAlerts = latencyAlertRepository.findUnresolvedByServiceAndEndpoint(
                    serviceName, endpoint);
            
            for (LatencyAlert alert : unresolvedAlerts) {
                if (shouldResolveAlert(alert, currentLatency)) {
                    alert.setResolved(true);
                    alert.setResolvedAt(LocalDateTime.now());
                    latencyAlertRepository.save(alert);
                }
            }
            
            metricsService.recordLatencyRecovery(serviceName, endpoint, improvementPercent);
            
            if (improvementPercent > 25) {
                notificationService.sendRecoveryNotification("LATENCY_RECOVERY", 
                        String.format("Latency recovery detected: %s/%s - %.1f%% improvement in %s", 
                                serviceName, endpoint, improvementPercent, recoveryDuration));
            }
            
            logger.info("Processed latency recovery for service: {}, endpoint: {}, improvement: {}%", 
                    serviceName, endpoint, improvementPercent);
            
        } catch (Exception e) {
            logger.error("Error processing latency recovery: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void performLatencyAnalysis() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            logger.info("Starting scheduled latency analysis");
            
            for (Map.Entry<String, List<Double>> entry : latencyBuffer.entrySet()) {
                String key = entry.getKey();
                List<Double> latencies = new ArrayList<>(entry.getValue());
                
                if (latencies.size() >= 10) {
                    analyzeLatencyPattern(key, latencies);
                    entry.getValue().clear();
                }
            }
            
            analyzeSystemwideLatencyTrends();
            updateLatencyBaselines();
            detectLatencyAnomalies();
            
        } catch (Exception e) {
            logger.error("Error in latency analysis: {}", e.getMessage(), e);
        } finally {
            sample.stop(latencyAnalysisTimer);
        }
    }

    private void analyzeLatencyPattern(String serviceEndpointKey, List<Double> latencies) {
        String[] parts = serviceEndpointKey.split(":");
        String serviceName = parts[0];
        String endpoint = parts[1];
        
        LatencyStatistics stats = calculateLatencyStatistics(latencies);
        
        double baseline = baselineLatencies.getOrDefault(serviceEndpointKey, stats.getP95());
        
        if (stats.getP95() > baseline * spikeMultiplier) {
            generateLatencySpike(serviceName, endpoint, stats, baseline);
        }
        
        if (detectLatencyTrendDegradation(serviceEndpointKey, stats)) {
            generateDegradationAlert(serviceName, endpoint, stats, baseline);
        }
        
        metricsService.recordLatencyStatistics(serviceName, endpoint, stats);
    }

    private void analyzeSystemwideLatencyTrends() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneDayAgo = now.minusDays(1);
        
        List<ServiceLatency> recentMetrics = serviceLatencyRepository.findByTimestampBetween(oneDayAgo, now);
        
        Map<String, List<ServiceLatency>> serviceMetrics = recentMetrics.stream()
                .collect(Collectors.groupingBy(ServiceLatency::getServiceName));
        
        for (Map.Entry<String, List<ServiceLatency>> entry : serviceMetrics.entrySet()) {
            String serviceName = entry.getKey();
            List<ServiceLatency> metrics = entry.getValue();
            
            if (metrics.size() >= 24) {
                analyzeDailyLatencyTrend(serviceName, metrics);
            }
        }
    }

    private void analyzeDailyLatencyTrend(String serviceName, List<ServiceLatency> metrics) {
        metrics.sort(Comparator.comparing(ServiceLatency::getTimestamp));
        
        List<Double> p95Values = metrics.stream()
                .map(ServiceLatency::getP95)
                .collect(Collectors.toList());
        
        double trendSlope = calculateTrendSlope(p95Values);
        double confidenceLevel = calculateTrendConfidence(p95Values, trendSlope);
        
        if (Math.abs(trendSlope) > 0.1 && confidenceLevel > 0.7) {
            String direction = trendSlope > 0 ? "DEGRADING" : "IMPROVING";
            double magnitude = Math.abs(trendSlope);
            
            LatencyTrend trend = new LatencyTrend();
            trend.setTimestamp(LocalDateTime.now());
            trend.setServiceName(serviceName);
            trend.setEndpoint("*");
            trend.setTrendDirection(direction);
            trend.setTrendMagnitude(magnitude);
            trend.setConfidenceLevel(confidenceLevel);
            trend.setCurrentLatency(metrics.get(metrics.size() - 1).getP95());
            trend.setBaselineLatency(metrics.get(0).getP95());
            
            latencyTrendRepository.save(trend);
            
            if ("DEGRADING".equals(direction) && magnitude > 0.2) {
                alertingService.sendAlert("LATENCY_TREND_DEGRADATION", 
                        String.format("Service %s showing degrading latency trend", serviceName),
                        Map.of("serviceName", serviceName, "magnitude", String.valueOf(magnitude)));
            }
        }
    }

    private void updateLatencyBaselines() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sevenDaysAgo = now.minusDays(7);
        
        List<String> distinctServices = latencyMetricRepository.findDistinctServiceNames();
        
        for (String serviceName : distinctServices) {
            List<String> endpoints = latencyMetricRepository.findDistinctEndpointsByService(serviceName);
            
            for (String endpoint : endpoints) {
                List<LatencyMetric> recentMetrics = latencyMetricRepository
                        .findByServiceNameAndEndpointAndTimestampBetween(serviceName, endpoint, sevenDaysAgo, now);
                
                if (recentMetrics.size() >= baselineSamples) {
                    updateBaselineForServiceEndpoint(serviceName, endpoint, recentMetrics);
                }
            }
        }
    }

    private void updateBaselineForServiceEndpoint(String serviceName, String endpoint, 
                                                  List<LatencyMetric> metrics) {
        List<Double> latencies = metrics.stream()
                .map(LatencyMetric::getLatencyMs)
                .sorted()
                .collect(Collectors.toList());
        
        LatencyStatistics stats = calculateLatencyStatistics(latencies);
        
        LatencyBaseline baseline = new LatencyBaseline();
        baseline.setTimestamp(LocalDateTime.now());
        baseline.setServiceName(serviceName);
        baseline.setEndpoint(endpoint);
        baseline.setBaselineLatency(stats.getMean());
        baseline.setP50Baseline(stats.getP50());
        baseline.setP95Baseline(stats.getP95());
        baseline.setP99Baseline(stats.getP99());
        baseline.setStandardDeviation(stats.getStandardDeviation());
        baseline.setSampleSize((long) latencies.size());
        baseline.setValidFrom(LocalDateTime.now());
        baseline.setValidTo(LocalDateTime.now().plusDays(7));
        
        latencyBaselineRepository.save(baseline);
        
        String key = serviceName + ":" + endpoint;
        baselineLatencies.put(key, baseline.getBaselineLatency());
        
        logger.debug("Updated baseline for {}: {}ms", key, baseline.getBaselineLatency());
    }

    private void generateLatencyTrends() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime twoHoursAgo = now.minusHours(2);
        
        List<EndpointLatency> recentEndpointMetrics = endpointLatencyRepository
                .findByTimestampBetween(twoHoursAgo, now);
        
        Map<String, List<EndpointLatency>> endpointGroups = recentEndpointMetrics.stream()
                .collect(Collectors.groupingBy(el -> el.getServiceName() + ":" + el.getEndpoint()));
        
        for (Map.Entry<String, List<EndpointLatency>> entry : endpointGroups.entrySet()) {
            String[] parts = entry.getKey().split(":");
            String serviceName = parts[0];
            String endpoint = parts[1];
            List<EndpointLatency> metrics = entry.getValue();
            
            if (metrics.size() >= 6) {
                generateTrendForEndpoint(serviceName, endpoint, metrics);
            }
        }
    }

    private void generateTrendForEndpoint(String serviceName, String endpoint, 
                                          List<EndpointLatency> metrics) {
        metrics.sort(Comparator.comparing(EndpointLatency::getTimestamp));
        
        List<Double> p95Values = metrics.stream()
                .map(EndpointLatency::getP95)
                .collect(Collectors.toList());
        
        double trendSlope = calculateTrendSlope(p95Values);
        double confidenceLevel = calculateTrendConfidence(p95Values, trendSlope);
        
        if (confidenceLevel > 0.6) {
            String direction = trendSlope > 0.05 ? "DEGRADING" : 
                              trendSlope < -0.05 ? "IMPROVING" : "STABLE";
            
            LatencyTrend trend = new LatencyTrend();
            trend.setTimestamp(LocalDateTime.now());
            trend.setServiceName(serviceName);
            trend.setEndpoint(endpoint);
            trend.setTrendDirection(direction);
            trend.setTrendMagnitude(Math.abs(trendSlope));
            trend.setConfidenceLevel(confidenceLevel);
            trend.setCurrentLatency(p95Values.get(p95Values.size() - 1));
            trend.setBaselineLatency(p95Values.get(0));
            
            latencyTrendRepository.save(trend);
        }
    }

    private void detectLatencyAnomalies() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minusHours(1);
        
        List<LatencyMetric> recentMetrics = latencyMetricRepository.findByTimestampBetween(oneHourAgo, now);
        
        Map<String, List<LatencyMetric>> serviceEndpointGroups = recentMetrics.stream()
                .collect(Collectors.groupingBy(lm -> lm.getServiceName() + ":" + lm.getEndpoint()));
        
        for (Map.Entry<String, List<LatencyMetric>> entry : serviceEndpointGroups.entrySet()) {
            String key = entry.getKey();
            String[] parts = key.split(":");
            String serviceName = parts[0];
            String endpoint = parts[1];
            
            List<LatencyMetric> metrics = entry.getValue();
            double baseline = baselineLatencies.getOrDefault(key, 100.0);
            
            detectAnomaliesInMetrics(serviceName, endpoint, metrics, baseline);
        }
    }

    private void detectAnomaliesInMetrics(String serviceName, String endpoint, 
                                          List<LatencyMetric> metrics, double baseline) {
        for (LatencyMetric metric : metrics) {
            double zscore = Math.abs((metric.getLatencyMs() - baseline) / (baseline * 0.3));
            
            if (zscore > 3.0) {
                String anomalyType = metric.getLatencyMs() > baseline ? "HIGH_LATENCY" : "LOW_LATENCY";
                double severity = Math.min(zscore / 5.0, 1.0);
                
                generateAnomalyEvent(serviceName, endpoint, metric, baseline, anomalyType, severity);
            }
        }
    }

    private void cleanupOldData() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        
        try {
            int deletedMetrics = latencyMetricRepository.deleteByTimestampBefore(cutoff);
            int deletedAlerts = latencyAlertRepository.deleteByTimestampBefore(cutoff);
            int deletedTrends = latencyTrendRepository.deleteByTimestampBefore(cutoff);
            
            logger.info("Cleaned up old latency data: {} metrics, {} alerts, {} trends", 
                    deletedMetrics, deletedAlerts, deletedTrends);
            
        } catch (Exception e) {
            logger.error("Error cleaning up old latency data: {}", e.getMessage(), e);
        }
    }

    private boolean shouldTriggerRealTimeAnalysis(LatencyMetric metric) {
        String key = metric.getServiceName() + ":" + metric.getEndpoint();
        double baseline = baselineLatencies.getOrDefault(key, 100.0);
        
        return metric.getLatencyMs() > baseline * 2.0;
    }

    private void performRealTimeLatencyAnalysis(LatencyMetric metric) {
        String key = metric.getServiceName() + ":" + metric.getEndpoint();
        double baseline = baselineLatencies.getOrDefault(key, 100.0);
        
        if (metric.getLatencyMs() > baseline * 5.0) {
            generateCriticalLatencyAlert(metric, baseline);
        } else if (metric.getLatencyMs() > baseline * 2.0) {
            generateWarningLatencyAlert(metric, baseline);
        }
    }

    private double getLatencyThreshold(String serviceName, String endpoint) {
        String key = serviceName + ":" + endpoint;
        double baseline = baselineLatencies.getOrDefault(key, 100.0);
        return baseline * 2.0;
    }

    private double getDatabaseLatencyThreshold(String databaseName, String queryType) {
        return switch (queryType.toUpperCase()) {
            case "SELECT" -> 50.0;
            case "INSERT", "UPDATE" -> 100.0;
            case "DELETE" -> 150.0;
            case "DDL" -> 1000.0;
            default -> 100.0;
        };
    }

    private void generateLatencyAlert(EndpointLatency endpointLatency) {
        LatencyAlert alert = new LatencyAlert();
        alert.setTimestamp(LocalDateTime.now());
        alert.setServiceName(endpointLatency.getServiceName());
        alert.setEndpoint(endpointLatency.getEndpoint());
        alert.setAlertType("THRESHOLD_EXCEEDED");
        alert.setSeverity("MEDIUM");
        alert.setCurrentLatency(endpointLatency.getP95());
        alert.setThreshold(getLatencyThreshold(endpointLatency.getServiceName(), endpointLatency.getEndpoint()));
        alert.setDescription(String.format("Endpoint latency threshold exceeded: P95 = %.2fms", 
                endpointLatency.getP95()));
        alert.setResolved(false);
        
        latencyAlertRepository.save(alert);
        
        alertingService.sendAlert("LATENCY_THRESHOLD", alert.getDescription(), 
                Map.of("serviceName", alert.getServiceName(), "endpoint", alert.getEndpoint()));
    }

    private void generateDatabaseLatencyAlert(DatabaseLatency dbLatency) {
        LatencyAlert alert = new LatencyAlert();
        alert.setTimestamp(LocalDateTime.now());
        alert.setServiceName("DATABASE");
        alert.setEndpoint(dbLatency.getDatabaseName() + ":" + dbLatency.getQueryType());
        alert.setAlertType("DATABASE_LATENCY");
        alert.setSeverity(dbLatency.getQueryLatencyMs() > 1000 ? "HIGH" : "MEDIUM");
        alert.setCurrentLatency(dbLatency.getQueryLatencyMs());
        alert.setThreshold(getDatabaseLatencyThreshold(dbLatency.getDatabaseName(), dbLatency.getQueryType()));
        alert.setDescription(String.format("Database query latency threshold exceeded: %.2fms for %s on %s", 
                dbLatency.getQueryLatencyMs(), dbLatency.getQueryType(), dbLatency.getDatabaseName()));
        alert.setResolved(false);
        
        latencyAlertRepository.save(alert);
        
        alertingService.sendAlert("DATABASE_LATENCY", alert.getDescription(), 
                Map.of("database", dbLatency.getDatabaseName(), "queryType", dbLatency.getQueryType()));
    }

    private void generateCriticalLatencyAlert(LatencyMetric metric, double baseline) {
        LatencyAlert alert = new LatencyAlert();
        alert.setTimestamp(LocalDateTime.now());
        alert.setServiceName(metric.getServiceName());
        alert.setEndpoint(metric.getEndpoint());
        alert.setAlertType("CRITICAL_LATENCY");
        alert.setSeverity("CRITICAL");
        alert.setCurrentLatency(metric.getLatencyMs());
        alert.setBaselineLatency(baseline);
        alert.setThreshold(baseline * 5.0);
        alert.setDescription(String.format("Critical latency detected: %.2fms (baseline: %.2fms)", 
                metric.getLatencyMs(), baseline));
        alert.setResolved(false);
        
        latencyAlertRepository.save(alert);
        
        alertingService.sendCriticalAlert("CRITICAL_LATENCY", alert.getDescription(), 
                Map.of("serviceName", metric.getServiceName(), "endpoint", metric.getEndpoint(), 
                       "latency", String.valueOf(metric.getLatencyMs())));
    }

    private void generateWarningLatencyAlert(LatencyMetric metric, double baseline) {
        LatencyAlert alert = new LatencyAlert();
        alert.setTimestamp(LocalDateTime.now());
        alert.setServiceName(metric.getServiceName());
        alert.setEndpoint(metric.getEndpoint());
        alert.setAlertType("WARNING_LATENCY");
        alert.setSeverity("WARNING");
        alert.setCurrentLatency(metric.getLatencyMs());
        alert.setBaselineLatency(baseline);
        alert.setThreshold(baseline * 2.0);
        alert.setDescription(String.format("Warning latency detected: %.2fms (baseline: %.2fms)", 
                metric.getLatencyMs(), baseline));
        alert.setResolved(false);
        
        latencyAlertRepository.save(alert);
        
        metricsService.recordWarningLatency(metric.getServiceName(), metric.getEndpoint(), metric.getLatencyMs());
    }

    private void generateTrendAlert(LatencyTrend trend) {
        alertingService.sendAlert("LATENCY_TREND", 
                String.format("Latency trend alert: %s/%s showing %s trend (confidence: %.1f%%)", 
                        trend.getServiceName(), trend.getEndpoint(), trend.getTrendDirection(), 
                        trend.getConfidenceLevel() * 100),
                Map.of("serviceName", trend.getServiceName(), "endpoint", trend.getEndpoint(), 
                       "direction", trend.getTrendDirection()));
    }

    private void generateLatencySpike(String serviceName, String endpoint, 
                                      LatencyStatistics stats, double baseline) {
        double spikeRatio = stats.getP95() / baseline;
        
        LatencyAlert alert = new LatencyAlert();
        alert.setTimestamp(LocalDateTime.now());
        alert.setServiceName(serviceName);
        alert.setEndpoint(endpoint);
        alert.setAlertType("LATENCY_SPIKE");
        alert.setSeverity(spikeRatio > 5.0 ? "HIGH" : "MEDIUM");
        alert.setCurrentLatency(stats.getP95());
        alert.setBaselineLatency(baseline);
        alert.setDescription(String.format("Latency spike detected: %.2fms (%.1fx baseline)", 
                stats.getP95(), spikeRatio));
        alert.setResolved(false);
        
        latencyAlertRepository.save(alert);
        
        if (spikeRatio > 3.0) {
            alertingService.sendAlert("LATENCY_SPIKE", alert.getDescription(), 
                    Map.of("serviceName", serviceName, "endpoint", endpoint, 
                           "spikeRatio", String.valueOf(spikeRatio)));
        }
    }

    private void generateDegradationAlert(String serviceName, String endpoint, 
                                          LatencyStatistics stats, double baseline) {
        double degradationPercent = ((stats.getP95() - baseline) / baseline) * 100;
        
        LatencyAlert alert = new LatencyAlert();
        alert.setTimestamp(LocalDateTime.now());
        alert.setServiceName(serviceName);
        alert.setEndpoint(endpoint);
        alert.setAlertType("LATENCY_DEGRADATION");
        alert.setSeverity(degradationPercent > 50 ? "HIGH" : "MEDIUM");
        alert.setCurrentLatency(stats.getP95());
        alert.setBaselineLatency(baseline);
        alert.setDescription(String.format("Latency degradation detected: %.1f%% increase", degradationPercent));
        alert.setResolved(false);
        
        latencyAlertRepository.save(alert);
        
        if (degradationPercent > 30) {
            alertingService.sendAlert("LATENCY_DEGRADATION", alert.getDescription(), 
                    Map.of("serviceName", serviceName, "endpoint", endpoint, 
                           "degradation", String.valueOf(degradationPercent)));
        }
    }

    private void generateAnomalyEvent(String serviceName, String endpoint, LatencyMetric metric, 
                                      double baseline, String anomalyType, double severity) {
        try {
            Map<String, Object> anomalyData = Map.of(
                    "eventType", "LATENCY_ANOMALY",
                    "timestamp", metric.getTimestamp().toString(),
                    "serviceName", serviceName,
                    "endpoint", endpoint,
                    "latency", metric.getLatencyMs(),
                    "baseline", baseline,
                    "anomalyType", anomalyType,
                    "severity", severity
            );
            
            String anomalyJson = objectMapper.writeValueAsString(anomalyData);
            
        } catch (JsonProcessingException e) {
            logger.error("Error generating anomaly event: {}", e.getMessage(), e);
        }
    }

    private void analyzeServiceLatencyTrend(ServiceLatency serviceLatency) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime twentyFourHoursAgo = now.minusHours(24);
        
        List<ServiceLatency> historicalData = serviceLatencyRepository
                .findByServiceNameAndTimestampBetween(serviceLatency.getServiceName(), twentyFourHoursAgo, now);
        
        if (historicalData.size() >= 24) {
            List<Double> p95Values = historicalData.stream()
                    .map(ServiceLatency::getP95)
                    .collect(Collectors.toList());
            
            double trendSlope = calculateTrendSlope(p95Values);
            
            if (trendSlope > 0.1) {
                alertingService.sendAlert("SERVICE_LATENCY_TREND", 
                        String.format("Service %s showing increasing latency trend", serviceLatency.getServiceName()),
                        Map.of("serviceName", serviceLatency.getServiceName(), "trend", String.valueOf(trendSlope)));
            }
        }
    }

    private void analyzeDatabaseQueryPerformance(DatabaseLatency dbLatency) {
        if (dbLatency.getQueryLatencyMs() > 5000) {
            alertingService.sendAlert("SLOW_DATABASE_QUERY", 
                    String.format("Very slow database query detected: %s on %s (%.2fms)", 
                            dbLatency.getQueryType(), dbLatency.getDatabaseName(), dbLatency.getQueryLatencyMs()),
                    Map.of("database", dbLatency.getDatabaseName(), "queryType", dbLatency.getQueryType(), 
                           "latency", String.valueOf(dbLatency.getQueryLatencyMs())));
        }
        
        if (dbLatency.getIndexesUsed() == null || dbLatency.getIndexesUsed().isEmpty()) {
            alertingService.sendAlert("MISSING_DATABASE_INDEX", 
                    String.format("Query without index usage detected: %s on %s", 
                            dbLatency.getQueryType(), dbLatency.getTableName()),
                    Map.of("database", dbLatency.getDatabaseName(), "table", dbLatency.getTableName()));
        }
    }

    private void analyzeExternalServiceHealth(ExternalServiceLatency extLatency) {
        if (extLatency.getLatencyMs() > 10000) {
            alertingService.sendAlert("SLOW_EXTERNAL_SERVICE", 
                    String.format("Slow external service response: %s (%.2fms)", 
                            extLatency.getServiceName(), extLatency.getLatencyMs()),
                    Map.of("externalService", extLatency.getServiceName(), 
                           "latency", String.valueOf(extLatency.getLatencyMs())));
        }
        
        if (extLatency.getDnsLookupTime() > 1000) {
            alertingService.sendAlert("SLOW_DNS_LOOKUP", 
                    String.format("Slow DNS lookup for %s (%.2fms)", 
                            extLatency.getServiceName(), extLatency.getDnsLookupTime()),
                    Map.of("externalService", extLatency.getServiceName(), 
                           "dnsTime", String.valueOf(extLatency.getDnsLookupTime())));
        }
    }

    private void initiateAutomaticMitigation(String serviceName, String endpoint, 
                                             double latency, double threshold) {
    }

    private void investigateLatencySpike(String serviceName, String endpoint, double currentLatency, 
                                         double previousLatency, String cause) {
    }

    private void analyzePerformanceDegradation(String serviceName, String endpoint, 
                                               double currentLatency, double baselineLatency) {
    }

    private boolean shouldResolveAlert(LatencyAlert alert, double currentLatency) {
        return currentLatency < alert.getThreshold() * 0.9;
    }

    private boolean detectLatencyTrendDegradation(String serviceEndpointKey, LatencyStatistics stats) {
        LocalDateTime lastAnalysisTime = lastAnalysis.get(serviceEndpointKey);
        if (lastAnalysisTime != null && lastAnalysisTime.isAfter(LocalDateTime.now().minusMinutes(30))) {
            return false;
        }
        
        lastAnalysis.put(serviceEndpointKey, LocalDateTime.now());
        
        double baseline = baselineLatencies.getOrDefault(serviceEndpointKey, 100.0);
        return stats.getP95() > baseline * 1.5;
    }

    private LatencyStatistics calculateLatencyStatistics(List<Double> latencies) {
        if (latencies.isEmpty()) {
            return new LatencyStatistics();
        }
        
        List<Double> sorted = latencies.stream().sorted().collect(Collectors.toList());
        int size = sorted.size();
        
        double mean = sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double p50 = sorted.get(size / 2);
        double p95 = sorted.get((int) (size * 0.95));
        double p99 = sorted.get((int) (size * 0.99));
        double min = sorted.get(0);
        double max = sorted.get(size - 1);
        
        double variance = sorted.stream()
                .mapToDouble(val -> Math.pow(val - mean, 2))
                .average().orElse(0.0);
        double stdDev = Math.sqrt(variance);
        
        return new LatencyStatistics(mean, p50, p95, p99, min, max, stdDev);
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

    private static class LatencyStatistics {
        private final double mean;
        private final double p50;
        private final double p95;
        private final double p99;
        private final double min;
        private final double max;
        private final double standardDeviation;
        
        public LatencyStatistics() {
            this(0, 0, 0, 0, 0, 0, 0);
        }
        
        public LatencyStatistics(double mean, double p50, double p95, double p99, 
                                 double min, double max, double standardDeviation) {
            this.mean = mean;
            this.p50 = p50;
            this.p95 = p95;
            this.p99 = p99;
            this.min = min;
            this.max = max;
            this.standardDeviation = standardDeviation;
        }
        
        public double getMean() { return mean; }
        public double getP50() { return p50; }
        public double getP95() { return p95; }
        public double getP99() { return p99; }
        public double getMin() { return min; }
        public double getMax() { return max; }
        public double getStandardDeviation() { return standardDeviation; }
    }
}