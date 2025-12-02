package com.waqiti.monitoring.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.monitoring.model.PerformanceMetric;
import com.waqiti.monitoring.model.ServiceMetrics;
import com.waqiti.monitoring.model.EndpointMetrics;
import com.waqiti.monitoring.model.LatencyDistribution;
import com.waqiti.monitoring.service.PerformanceAnalysisService;
import com.waqiti.monitoring.service.SLAMonitoringService;
import com.waqiti.monitoring.service.CapacityPlanningService;
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
public class PerformanceMetricsConsumer {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceMetricsConsumer.class);
    private static final String CONSUMER_NAME = "performance-metrics-consumer";
    private static final String DLQ_TOPIC = "performance-metrics-dlq";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int PROCESSING_TIMEOUT_SECONDS = 10;

    private final ObjectMapper objectMapper;
    private final PerformanceAnalysisService performanceAnalysisService;
    private final SLAMonitoringService slaMonitoringService;
    private final CapacityPlanningService capacityPlanningService;
    private final AlertingService alertingService;
    private final MetricsService metricsService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public PerformanceMetricsConsumer(
            ObjectMapper objectMapper,
            PerformanceAnalysisService performanceAnalysisService,
            SLAMonitoringService slaMonitoringService,
            CapacityPlanningService capacityPlanningService,
            AlertingService alertingService,
            MetricsService metricsService,
            KafkaTemplate<String, Object> kafkaTemplate,
            MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.performanceAnalysisService = performanceAnalysisService;
        this.slaMonitoringService = slaMonitoringService;
        this.capacityPlanningService = capacityPlanningService;
        this.alertingService = alertingService;
        this.metricsService = metricsService;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Value("${kafka.consumer.performance-metrics.enabled:true}")
    private boolean consumerEnabled;

    @Value("${performance.metrics.window-size-minutes:5}")
    private int windowSizeMinutes;

    @Value("${performance.metrics.percentile-accuracy:0.01}")
    private double percentileAccuracy;

    @Value("${performance.metrics.enable-profiling:true}")
    private boolean enableProfiling;

    @Value("${performance.metrics.alert-threshold-p99-ms:1000}")
    private long alertThresholdP99Ms;

    @Value("${performance.metrics.alert-threshold-error-rate:0.01}")
    private double alertThresholdErrorRate;

    @Value("${performance.metrics.enable-trend-analysis:true}")
    private boolean enableTrendAnalysis;

    @Value("${performance.metrics.baseline-window-hours:24}")
    private int baselineWindowHours;

    private Counter processedCounter;
    private Counter errorCounter;
    private Counter dlqCounter;
    private Timer processingTimer;
    private DistributionSummary latencyDistribution;
    private Gauge activeRequestsGauge;

    private final ConcurrentHashMap<String, ServiceMetrics> serviceMetricsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EndpointMetrics> endpointMetricsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LatencyDistribution> latencyDistributions = new ConcurrentHashMap<>();
    private final AtomicLong totalMetricsProcessed = new AtomicLong(0);
    private final AtomicLong activeRequests = new AtomicLong(0);
    private ScheduledExecutorService scheduledExecutor;
    private ExecutorService analysisExecutor;

    @PostConstruct
    public void initializeMetrics() {
        this.processedCounter = Counter.builder("performance_metrics_processed_total")
                .description("Total processed performance metrics events")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.errorCounter = Counter.builder("performance_metrics_errors_total")
                .description("Total performance metrics processing errors")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.dlqCounter = Counter.builder("performance_metrics_dlq_total")
                .description("Total performance metrics events sent to DLQ")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.processingTimer = Timer.builder("performance_metrics_processing_duration")
                .description("Performance metrics processing duration")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.latencyDistribution = DistributionSummary.builder("performance_metrics_latency_distribution")
                .description("Distribution of service latencies")
                .tag("consumer", CONSUMER_NAME)
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry);

        this.activeRequestsGauge = Gauge.builder("performance_metrics_active_requests", activeRequests, AtomicLong::get)
                .description("Number of active requests being tracked")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.scheduledExecutor = Executors.newScheduledThreadPool(3);
        scheduledExecutor.scheduleWithFixedDelay(this::aggregateMetrics, 
                windowSizeMinutes, windowSizeMinutes, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::performTrendAnalysis, 
                15, 15, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::cleanupOldMetrics, 
                1, 1, TimeUnit.HOURS);

        this.analysisExecutor = Executors.newFixedThreadPool(4);

        logger.info("PerformanceMetricsConsumer initialized with window size: {} minutes", windowSizeMinutes);
    }

    @KafkaListener(
        topics = "${kafka.topics.performance-metrics:performance-metrics}",
        groupId = "${kafka.consumer.group-id:monitoring-service-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "performance-metrics-circuit-breaker", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "performance-metrics-retry")
    public void processPerformanceMetrics(
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
                logger.warn("Performance metrics consumer is disabled, skipping message processing");
                acknowledgment.acknowledge();
                return;
            }

            logger.debug("Processing performance metrics message: messageId={}, topic={}, partition={}, offset={}",
                    messageId, topic, partition, offset);

            if (!StringUtils.hasText(message)) {
                logger.warn("Received empty or null message, skipping processing");
                acknowledgment.acknowledge();
                return;
            }

            JsonNode messageNode = objectMapper.readTree(message);
            
            if (!isValidPerformanceMessage(messageNode)) {
                logger.error("Invalid performance metrics message format: {}", message);
                sendToDlq(message, topic, "Invalid message format", null, correlationId, traceId);
                acknowledgment.acknowledge();
                return;
            }

            String eventType = messageNode.get("eventType").asText();
            
            CompletableFuture<Void> processingFuture = CompletableFuture.runAsync(() -> {
                try {
                    switch (eventType) {
                        case "REQUEST_STARTED":
                            handleRequestStarted(messageNode, correlationId, traceId);
                            break;
                        case "REQUEST_COMPLETED":
                            handleRequestCompleted(messageNode, correlationId, traceId);
                            break;
                        case "REQUEST_FAILED":
                            handleRequestFailed(messageNode, correlationId, traceId);
                            break;
                        case "DATABASE_QUERY":
                            handleDatabaseQuery(messageNode, correlationId, traceId);
                            break;
                        case "CACHE_OPERATION":
                            handleCacheOperation(messageNode, correlationId, traceId);
                            break;
                        case "EXTERNAL_API_CALL":
                            handleExternalApiCall(messageNode, correlationId, traceId);
                            break;
                        case "MESSAGE_PROCESSING":
                            handleMessageProcessing(messageNode, correlationId, traceId);
                            break;
                        case "BATCH_JOB_EXECUTION":
                            handleBatchJobExecution(messageNode, correlationId, traceId);
                            break;
                        case "TRANSACTION_TIMING":
                            handleTransactionTiming(messageNode, correlationId, traceId);
                            break;
                        case "SERVICE_DEPENDENCY":
                            handleServiceDependency(messageNode, correlationId, traceId);
                            break;
                        case "RESOURCE_USAGE":
                            handleResourceUsage(messageNode, correlationId, traceId);
                            break;
                        case "THROUGHPUT_MEASUREMENT":
                            handleThroughputMeasurement(messageNode, correlationId, traceId);
                            break;
                        case "LATENCY_SPIKE":
                            handleLatencySpike(messageNode, correlationId, traceId);
                            break;
                        case "PERFORMANCE_DEGRADATION":
                            handlePerformanceDegradation(messageNode, correlationId, traceId);
                            break;
                        case "CAPACITY_WARNING":
                            handleCapacityWarning(messageNode, correlationId, traceId);
                            break;
                        default:
                            logger.warn("Unknown performance event type: {}", eventType);
                    }
                } catch (Exception e) {
                    logger.error("Error processing performance event: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }, analysisExecutor).orTimeout(PROCESSING_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            processingFuture.get();

            totalMetricsProcessed.incrementAndGet();
            processedCounter.increment();
            acknowledgment.acknowledge();

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse performance metrics message: messageId={}, error={}", messageId, e.getMessage());
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } catch (Exception e) {
            logger.error("Unexpected error processing performance metrics: messageId={}, error={}", messageId, e.getMessage(), e);
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }

    @Transactional
    private void handleRequestStarted(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String requestId = messageNode.get("requestId").asText();
            String serviceName = messageNode.get("serviceName").asText();
            String endpoint = messageNode.get("endpoint").asText();
            String method = messageNode.get("method").asText();
            LocalDateTime startTime = LocalDateTime.parse(messageNode.get("timestamp").asText());
            
            activeRequests.incrementAndGet();
            
            PerformanceMetric metric = new PerformanceMetric();
            metric.setRequestId(requestId);
            metric.setServiceName(serviceName);
            metric.setEndpoint(endpoint);
            metric.setMethod(method);
            metric.setStartTime(startTime);
            metric.setStatus("IN_PROGRESS");
            
            if (messageNode.has("userId")) {
                metric.setUserId(messageNode.get("userId").asText());
            }
            
            if (messageNode.has("metadata")) {
                Map<String, Object> metadata = objectMapper.convertValue(
                    messageNode.get("metadata"), Map.class);
                metric.setMetadata(metadata);
            }
            
            performanceAnalysisService.recordRequestStart(metric);
            
            updateServiceMetrics(serviceName, endpoint, "REQUEST_STARTED");
            
            logger.debug("Request started: requestId={}, service={}, endpoint={}", 
                       requestId, serviceName, endpoint);
            
        } catch (Exception e) {
            logger.error("Error handling request started event: {}", e.getMessage(), e);
            throw new SystemException("Failed to process request started event", e);
        }
    }

    @Transactional
    private void handleRequestCompleted(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String requestId = messageNode.get("requestId").asText();
            String serviceName = messageNode.get("serviceName").asText();
            String endpoint = messageNode.get("endpoint").asText();
            LocalDateTime endTime = LocalDateTime.parse(messageNode.get("timestamp").asText());
            long durationMs = messageNode.get("durationMs").asLong();
            int statusCode = messageNode.get("statusCode").asInt();
            
            activeRequests.decrementAndGet();
            
            PerformanceMetric metric = performanceAnalysisService.getRequestMetric(requestId);
            if (metric == null) {
                metric = new PerformanceMetric();
                metric.setRequestId(requestId);
                metric.setServiceName(serviceName);
                metric.setEndpoint(endpoint);
                metric.setStartTime(endTime.minusNanos(durationMs * 1_000_000));
            }
            
            metric.setEndTime(endTime);
            metric.setDurationMs(durationMs);
            metric.setStatusCode(statusCode);
            metric.setStatus("COMPLETED");
            metric.setSuccess(statusCode >= 200 && statusCode < 400);
            
            if (messageNode.has("responseSize")) {
                metric.setResponseSizeBytes(messageNode.get("responseSize").asLong());
            }
            
            performanceAnalysisService.recordRequestCompletion(metric);
            
            latencyDistribution.record(durationMs);
            updateLatencyDistribution(serviceName, endpoint, durationMs);
            
            checkSLACompliance(metric);
            
            if (durationMs > alertThresholdP99Ms) {
                createPerformanceAlert("HIGH_LATENCY", metric, durationMs);
            }
            
            updateServiceMetrics(serviceName, endpoint, "REQUEST_COMPLETED", durationMs);
            
            logger.debug("Request completed: requestId={}, duration={}ms, status={}", 
                       requestId, durationMs, statusCode);
            
        } catch (Exception e) {
            logger.error("Error handling request completed event: {}", e.getMessage(), e);
            throw new SystemException("Failed to process request completed event", e);
        }
    }

    @Transactional
    private void handleRequestFailed(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String requestId = messageNode.get("requestId").asText();
            String serviceName = messageNode.get("serviceName").asText();
            String endpoint = messageNode.get("endpoint").asText();
            String errorType = messageNode.get("errorType").asText();
            String errorMessage = messageNode.get("errorMessage").asText();
            LocalDateTime failureTime = LocalDateTime.parse(messageNode.get("timestamp").asText());
            
            activeRequests.decrementAndGet();
            
            PerformanceMetric metric = performanceAnalysisService.getRequestMetric(requestId);
            if (metric == null) {
                metric = new PerformanceMetric();
                metric.setRequestId(requestId);
                metric.setServiceName(serviceName);
                metric.setEndpoint(endpoint);
            }
            
            metric.setEndTime(failureTime);
            metric.setStatus("FAILED");
            metric.setSuccess(false);
            metric.setErrorType(errorType);
            metric.setErrorMessage(errorMessage);
            
            if (messageNode.has("stackTrace")) {
                metric.setStackTrace(messageNode.get("stackTrace").asText());
            }
            
            performanceAnalysisService.recordRequestFailure(metric);
            
            updateErrorRate(serviceName, endpoint);
            
            ServiceMetrics serviceMetrics = serviceMetricsCache.get(serviceName);
            if (serviceMetrics != null && serviceMetrics.getErrorRate() > alertThresholdErrorRate) {
                createPerformanceAlert("HIGH_ERROR_RATE", metric, serviceMetrics.getErrorRate());
            }
            
            if (isCircuitBreakerCandidate(serviceName, endpoint)) {
                triggerCircuitBreakerEvaluation(serviceName, endpoint);
            }
            
            logger.warn("Request failed: requestId={}, service={}, error={}", 
                       requestId, serviceName, errorType);
            
        } catch (Exception e) {
            logger.error("Error handling request failed event: {}", e.getMessage(), e);
            throw new SystemException("Failed to process request failed event", e);
        }
    }

    @Transactional
    private void handleDatabaseQuery(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String queryId = messageNode.get("queryId").asText();
            String database = messageNode.get("database").asText();
            String queryType = messageNode.get("queryType").asText();
            long executionTimeMs = messageNode.get("executionTimeMs").asLong();
            int rowsAffected = messageNode.get("rowsAffected").asInt();
            
            Map<String, Object> queryMetrics = new HashMap<>();
            queryMetrics.put("queryId", queryId);
            queryMetrics.put("database", database);
            queryMetrics.put("queryType", queryType);
            queryMetrics.put("executionTimeMs", executionTimeMs);
            queryMetrics.put("rowsAffected", rowsAffected);
            queryMetrics.put("timestamp", LocalDateTime.now().toString());
            
            if (messageNode.has("queryPlan")) {
                queryMetrics.put("queryPlan", messageNode.get("queryPlan").asText());
                analyzeDatabasePerformance(queryMetrics);
            }
            
            if (executionTimeMs > 1000) {
                queryMetrics.put("slowQuery", true);
                kafkaTemplate.send("slow-query-alerts", queryMetrics);
                logger.warn("Slow database query detected: database={}, type={}, duration={}ms", 
                          database, queryType, executionTimeMs);
            }
            
            performanceAnalysisService.recordDatabaseMetrics(queryMetrics);
            
            updateDatabaseMetrics(database, queryType, executionTimeMs);
            
        } catch (Exception e) {
            logger.error("Error handling database query event: {}", e.getMessage(), e);
            throw new SystemException("Failed to process database query event", e);
        }
    }

    @Transactional
    private void handleCacheOperation(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String cacheType = messageNode.get("cacheType").asText();
            String operation = messageNode.get("operation").asText();
            boolean hit = messageNode.get("hit").asBoolean();
            long latencyMs = messageNode.get("latencyMs").asLong();
            
            Map<String, Object> cacheMetrics = new HashMap<>();
            cacheMetrics.put("cacheType", cacheType);
            cacheMetrics.put("operation", operation);
            cacheMetrics.put("hit", hit);
            cacheMetrics.put("latencyMs", latencyMs);
            cacheMetrics.put("timestamp", LocalDateTime.now().toString());
            
            if (messageNode.has("keySize")) {
                cacheMetrics.put("keySize", messageNode.get("keySize").asInt());
            }
            
            if (messageNode.has("valueSize")) {
                cacheMetrics.put("valueSize", messageNode.get("valueSize").asInt());
            }
            
            performanceAnalysisService.recordCacheMetrics(cacheMetrics);
            
            updateCacheStatistics(cacheType, operation, hit, latencyMs);
            
            double hitRate = calculateCacheHitRate(cacheType);
            if (hitRate < 0.5) {
                createCachePerformanceAlert(cacheType, hitRate, "LOW_HIT_RATE");
            }
            
            logger.debug("Cache operation: type={}, operation={}, hit={}, latency={}ms", 
                       cacheType, operation, hit, latencyMs);
            
        } catch (Exception e) {
            logger.error("Error handling cache operation event: {}", e.getMessage(), e);
            throw new SystemException("Failed to process cache operation event", e);
        }
    }

    @Transactional
    private void handleExternalApiCall(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String apiName = messageNode.get("apiName").asText();
            String endpoint = messageNode.get("endpoint").asText();
            String method = messageNode.get("method").asText();
            long responseTimeMs = messageNode.get("responseTimeMs").asLong();
            int statusCode = messageNode.get("statusCode").asInt();
            boolean success = statusCode >= 200 && statusCode < 400;
            
            Map<String, Object> apiMetrics = new HashMap<>();
            apiMetrics.put("apiName", apiName);
            apiMetrics.put("endpoint", endpoint);
            apiMetrics.put("method", method);
            apiMetrics.put("responseTimeMs", responseTimeMs);
            apiMetrics.put("statusCode", statusCode);
            apiMetrics.put("success", success);
            apiMetrics.put("timestamp", LocalDateTime.now().toString());
            
            if (messageNode.has("retryCount")) {
                apiMetrics.put("retryCount", messageNode.get("retryCount").asInt());
            }
            
            if (messageNode.has("timeout") && messageNode.get("timeout").asBoolean()) {
                apiMetrics.put("timeout", true);
                handleApiTimeout(apiName, endpoint);
            }
            
            performanceAnalysisService.recordExternalApiMetrics(apiMetrics);
            
            updateExternalApiStatistics(apiName, endpoint, responseTimeMs, success);
            
            if (responseTimeMs > 5000) {
                createPerformanceAlert("SLOW_EXTERNAL_API", apiMetrics, responseTimeMs);
            }
            
            if (!success) {
                incrementApiFailureCount(apiName, endpoint);
                evaluateApiCircuitBreaker(apiName, endpoint);
            }
            
            logger.debug("External API call: api={}, endpoint={}, duration={}ms, status={}", 
                       apiName, endpoint, responseTimeMs, statusCode);
            
        } catch (Exception e) {
            logger.error("Error handling external API call event: {}", e.getMessage(), e);
            throw new SystemException("Failed to process external API call event", e);
        }
    }

    @Transactional
    private void handleMessageProcessing(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String queueName = messageNode.get("queueName").asText();
            String messageType = messageNode.get("messageType").asText();
            long processingTimeMs = messageNode.get("processingTimeMs").asLong();
            boolean processed = messageNode.get("processed").asBoolean();
            
            Map<String, Object> messageMetrics = new HashMap<>();
            messageMetrics.put("queueName", queueName);
            messageMetrics.put("messageType", messageType);
            messageMetrics.put("processingTimeMs", processingTimeMs);
            messageMetrics.put("processed", processed);
            messageMetrics.put("timestamp", LocalDateTime.now().toString());
            
            if (messageNode.has("messageSize")) {
                messageMetrics.put("messageSize", messageNode.get("messageSize").asInt());
            }
            
            if (messageNode.has("lagMs")) {
                long lagMs = messageNode.get("lagMs").asLong();
                messageMetrics.put("lagMs", lagMs);
                
                if (lagMs > 10000) {
                    createQueueLagAlert(queueName, lagMs);
                }
            }
            
            performanceAnalysisService.recordMessageProcessingMetrics(messageMetrics);
            
            updateQueueStatistics(queueName, messageType, processingTimeMs, processed);
            
            logger.debug("Message processing: queue={}, type={}, duration={}ms, processed={}", 
                       queueName, messageType, processingTimeMs, processed);
            
        } catch (Exception e) {
            logger.error("Error handling message processing event: {}", e.getMessage(), e);
            throw new SystemException("Failed to process message processing event", e);
        }
    }

    @Transactional
    private void handleBatchJobExecution(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String jobName = messageNode.get("jobName").asText();
            String jobId = messageNode.get("jobId").asText();
            long executionTimeMs = messageNode.get("executionTimeMs").asLong();
            int recordsProcessed = messageNode.get("recordsProcessed").asInt();
            String status = messageNode.get("status").asText();
            
            Map<String, Object> jobMetrics = new HashMap<>();
            jobMetrics.put("jobName", jobName);
            jobMetrics.put("jobId", jobId);
            jobMetrics.put("executionTimeMs", executionTimeMs);
            jobMetrics.put("recordsProcessed", recordsProcessed);
            jobMetrics.put("status", status);
            jobMetrics.put("timestamp", LocalDateTime.now().toString());
            
            if (recordsProcessed > 0) {
                double throughput = (recordsProcessed / (executionTimeMs / 1000.0));
                jobMetrics.put("throughput", throughput);
                
                if (throughput < getBatchJobMinThroughput(jobName)) {
                    createBatchJobAlert(jobName, throughput, "LOW_THROUGHPUT");
                }
            }
            
            if (messageNode.has("errorCount")) {
                int errorCount = messageNode.get("errorCount").asInt();
                jobMetrics.put("errorCount", errorCount);
                double errorRate = (double) errorCount / recordsProcessed;
                
                if (errorRate > 0.05) {
                    createBatchJobAlert(jobName, errorRate, "HIGH_ERROR_RATE");
                }
            }
            
            performanceAnalysisService.recordBatchJobMetrics(jobMetrics);
            
            updateBatchJobStatistics(jobName, executionTimeMs, recordsProcessed, status);
            
            logger.info("Batch job execution: job={}, id={}, duration={}ms, records={}, status={}", 
                       jobName, jobId, executionTimeMs, recordsProcessed, status);
            
        } catch (Exception e) {
            logger.error("Error handling batch job execution event: {}", e.getMessage(), e);
            throw new SystemException("Failed to process batch job execution event", e);
        }
    }

    @Transactional
    private void handleTransactionTiming(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String transactionType = messageNode.get("transactionType").asText();
            String transactionId = messageNode.get("transactionId").asText();
            long totalTimeMs = messageNode.get("totalTimeMs").asLong();
            
            Map<String, Object> timingMetrics = new HashMap<>();
            timingMetrics.put("transactionType", transactionType);
            timingMetrics.put("transactionId", transactionId);
            timingMetrics.put("totalTimeMs", totalTimeMs);
            timingMetrics.put("timestamp", LocalDateTime.now().toString());
            
            if (messageNode.has("breakdown")) {
                JsonNode breakdown = messageNode.get("breakdown");
                Map<String, Long> timingBreakdown = new HashMap<>();
                
                if (breakdown.has("validationMs")) {
                    timingBreakdown.put("validation", breakdown.get("validationMs").asLong());
                }
                if (breakdown.has("processingMs")) {
                    timingBreakdown.put("processing", breakdown.get("processingMs").asLong());
                }
                if (breakdown.has("persistenceMs")) {
                    timingBreakdown.put("persistence", breakdown.get("persistenceMs").asLong());
                }
                if (breakdown.has("notificationMs")) {
                    timingBreakdown.put("notification", breakdown.get("notificationMs").asLong());
                }
                
                timingMetrics.put("breakdown", timingBreakdown);
                identifyBottlenecks(transactionType, timingBreakdown);
            }
            
            performanceAnalysisService.recordTransactionTiming(timingMetrics);
            
            checkTransactionSLA(transactionType, totalTimeMs);
            
            updateTransactionStatistics(transactionType, totalTimeMs);
            
            logger.debug("Transaction timing: type={}, id={}, duration={}ms", 
                       transactionType, transactionId, totalTimeMs);
            
        } catch (Exception e) {
            logger.error("Error handling transaction timing event: {}", e.getMessage(), e);
            throw new SystemException("Failed to process transaction timing event", e);
        }
    }

    @Transactional
    private void handleServiceDependency(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String sourceService = messageNode.get("sourceService").asText();
            String targetService = messageNode.get("targetService").asText();
            long latencyMs = messageNode.get("latencyMs").asLong();
            boolean success = messageNode.get("success").asBoolean();
            
            Map<String, Object> dependencyMetrics = new HashMap<>();
            dependencyMetrics.put("sourceService", sourceService);
            dependencyMetrics.put("targetService", targetService);
            dependencyMetrics.put("latencyMs", latencyMs);
            dependencyMetrics.put("success", success);
            dependencyMetrics.put("timestamp", LocalDateTime.now().toString());
            
            if (messageNode.has("traceId")) {
                dependencyMetrics.put("traceId", messageNode.get("traceId").asText());
            }
            
            performanceAnalysisService.recordServiceDependency(dependencyMetrics);
            
            updateDependencyMap(sourceService, targetService, latencyMs, success);
            
            if (!success) {
                evaluateCascadingFailureRisk(sourceService, targetService);
            }
            
            if (latencyMs > getDependencyThreshold(sourceService, targetService)) {
                createDependencyAlert(sourceService, targetService, latencyMs);
            }
            
            logger.debug("Service dependency: {} -> {}, latency={}ms, success={}", 
                       sourceService, targetService, latencyMs, success);
            
        } catch (Exception e) {
            logger.error("Error handling service dependency event: {}", e.getMessage(), e);
            throw new SystemException("Failed to process service dependency event", e);
        }
    }

    @Transactional
    private void handleResourceUsage(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String resourceType = messageNode.get("resourceType").asText();
            String resourceId = messageNode.get("resourceId").asText();
            double utilization = messageNode.get("utilization").asDouble();
            
            Map<String, Object> resourceMetrics = new HashMap<>();
            resourceMetrics.put("resourceType", resourceType);
            resourceMetrics.put("resourceId", resourceId);
            resourceMetrics.put("utilization", utilization);
            resourceMetrics.put("timestamp", LocalDateTime.now().toString());
            
            switch (resourceType) {
                case "CPU":
                    handleCpuUsage(resourceId, utilization, messageNode);
                    break;
                case "MEMORY":
                    handleMemoryUsage(resourceId, utilization, messageNode);
                    break;
                case "DISK":
                    handleDiskUsage(resourceId, utilization, messageNode);
                    break;
                case "NETWORK":
                    handleNetworkUsage(resourceId, utilization, messageNode);
                    break;
                case "THREAD_POOL":
                    handleThreadPoolUsage(resourceId, utilization, messageNode);
                    break;
                case "CONNECTION_POOL":
                    handleConnectionPoolUsage(resourceId, utilization, messageNode);
                    break;
            }
            
            performanceAnalysisService.recordResourceUsage(resourceMetrics);
            
            if (utilization > 0.8) {
                createResourceAlert(resourceType, resourceId, utilization, "HIGH_UTILIZATION");
            }
            
            capacityPlanningService.updateResourceTrends(resourceType, resourceId, utilization);
            
            logger.debug("Resource usage: type={}, id={}, utilization={}%", 
                       resourceType, resourceId, utilization * 100);
            
        } catch (Exception e) {
            logger.error("Error handling resource usage event: {}", e.getMessage(), e);
            throw new SystemException("Failed to process resource usage event", e);
        }
    }

    @Transactional
    private void handleThroughputMeasurement(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String serviceName = messageNode.get("serviceName").asText();
            String metricType = messageNode.get("metricType").asText();
            double throughput = messageNode.get("throughput").asDouble();
            String unit = messageNode.get("unit").asText();
            
            Map<String, Object> throughputMetrics = new HashMap<>();
            throughputMetrics.put("serviceName", serviceName);
            throughputMetrics.put("metricType", metricType);
            throughputMetrics.put("throughput", throughput);
            throughputMetrics.put("unit", unit);
            throughputMetrics.put("timestamp", LocalDateTime.now().toString());
            
            performanceAnalysisService.recordThroughput(throughputMetrics);
            
            double expectedThroughput = getExpectedThroughput(serviceName, metricType);
            double throughputRatio = throughput / expectedThroughput;
            
            if (throughputRatio < 0.7) {
                createThroughputAlert(serviceName, metricType, throughput, expectedThroughput);
            }
            
            updateThroughputTrends(serviceName, metricType, throughput);
            
            logger.debug("Throughput measurement: service={}, type={}, value={} {}", 
                       serviceName, metricType, throughput, unit);
            
        } catch (Exception e) {
            logger.error("Error handling throughput measurement event: {}", e.getMessage(), e);
            throw new SystemException("Failed to process throughput measurement event", e);
        }
    }

    @Transactional
    private void handleLatencySpike(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String serviceName = messageNode.get("serviceName").asText();
            String endpoint = messageNode.get("endpoint").asText();
            long spikeLatencyMs = messageNode.get("spikeLatencyMs").asLong();
            long normalLatencyMs = messageNode.get("normalLatencyMs").asLong();
            double spikeRatio = (double) spikeLatencyMs / normalLatencyMs;
            
            Map<String, Object> spikeAlert = new HashMap<>();
            spikeAlert.put("alertType", "LATENCY_SPIKE");
            spikeAlert.put("serviceName", serviceName);
            spikeAlert.put("endpoint", endpoint);
            spikeAlert.put("spikeLatencyMs", spikeLatencyMs);
            spikeAlert.put("normalLatencyMs", normalLatencyMs);
            spikeAlert.put("spikeRatio", spikeRatio);
            spikeAlert.put("timestamp", LocalDateTime.now().toString());
            spikeAlert.put("severity", spikeRatio > 5 ? "CRITICAL" : spikeRatio > 3 ? "HIGH" : "MEDIUM");
            
            kafkaTemplate.send("performance-alerts", spikeAlert);
            
            performanceAnalysisService.recordLatencySpike(spikeAlert);
            
            if (spikeRatio > 5) {
                initiateRootCauseAnalysis(serviceName, endpoint, spikeLatencyMs);
            }
            
            logger.warn("Latency spike detected: service={}, endpoint={}, spike={}ms ({}x normal)", 
                       serviceName, endpoint, spikeLatencyMs, String.format("%.1f", spikeRatio));
            
        } catch (Exception e) {
            logger.error("Error handling latency spike event: {}", e.getMessage(), e);
            throw new SystemException("Failed to process latency spike event", e);
        }
    }

    @Transactional
    private void handlePerformanceDegradation(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String serviceName = messageNode.get("serviceName").asText();
            String degradationType = messageNode.get("degradationType").asText();
            double currentPerformance = messageNode.get("currentPerformance").asDouble();
            double baselinePerformance = messageNode.get("baselinePerformance").asDouble();
            double degradationPercent = ((baselinePerformance - currentPerformance) / baselinePerformance) * 100;
            
            Map<String, Object> degradationAlert = new HashMap<>();
            degradationAlert.put("alertType", "PERFORMANCE_DEGRADATION");
            degradationAlert.put("serviceName", serviceName);
            degradationAlert.put("degradationType", degradationType);
            degradationAlert.put("currentPerformance", currentPerformance);
            degradationAlert.put("baselinePerformance", baselinePerformance);
            degradationAlert.put("degradationPercent", degradationPercent);
            degradationAlert.put("timestamp", LocalDateTime.now().toString());
            
            String severity = degradationPercent > 50 ? "CRITICAL" : 
                            degradationPercent > 30 ? "HIGH" : 
                            degradationPercent > 10 ? "MEDIUM" : "LOW";
            degradationAlert.put("severity", severity);
            
            kafkaTemplate.send("performance-alerts", degradationAlert);
            
            performanceAnalysisService.recordPerformanceDegradation(degradationAlert);
            
            if (degradationPercent > 30) {
                recommendOptimizations(serviceName, degradationType, degradationPercent);
            }
            
            logger.warn("Performance degradation: service={}, type={}, degradation={}%", 
                       serviceName, degradationType, String.format("%.1f", degradationPercent));
            
        } catch (Exception e) {
            logger.error("Error handling performance degradation event: {}", e.getMessage(), e);
            throw new SystemException("Failed to process performance degradation event", e);
        }
    }

    @Transactional
    private void handleCapacityWarning(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String resourceType = messageNode.get("resourceType").asText();
            String resourceId = messageNode.get("resourceId").asText();
            double currentCapacity = messageNode.get("currentCapacity").asDouble();
            double maxCapacity = messageNode.get("maxCapacity").asDouble();
            double utilizationPercent = (currentCapacity / maxCapacity) * 100;
            
            Map<String, Object> capacityWarning = new HashMap<>();
            capacityWarning.put("alertType", "CAPACITY_WARNING");
            capacityWarning.put("resourceType", resourceType);
            capacityWarning.put("resourceId", resourceId);
            capacityWarning.put("currentCapacity", currentCapacity);
            capacityWarning.put("maxCapacity", maxCapacity);
            capacityWarning.put("utilizationPercent", utilizationPercent);
            capacityWarning.put("timestamp", LocalDateTime.now().toString());
            
            if (messageNode.has("projectedTimeToLimit")) {
                int hoursToLimit = messageNode.get("projectedTimeToLimit").asInt();
                capacityWarning.put("projectedTimeToLimit", hoursToLimit);
                
                if (hoursToLimit < 24) {
                    capacityWarning.put("urgency", "IMMEDIATE");
                    triggerCapacityScaling(resourceType, resourceId);
                } else if (hoursToLimit < 72) {
                    capacityWarning.put("urgency", "HIGH");
                } else {
                    capacityWarning.put("urgency", "MEDIUM");
                }
            }
            
            kafkaTemplate.send("capacity-alerts", capacityWarning);
            
            capacityPlanningService.updateCapacityProjections(resourceType, resourceId, utilizationPercent);
            
            logger.warn("Capacity warning: type={}, id={}, utilization={}%", 
                       resourceType, resourceId, String.format("%.1f", utilizationPercent));
            
        } catch (Exception e) {
            logger.error("Error handling capacity warning event: {}", e.getMessage(), e);
            throw new SystemException("Failed to process capacity warning event", e);
        }
    }

    private void updateServiceMetrics(String serviceName, String endpoint, String eventType) {
        updateServiceMetrics(serviceName, endpoint, eventType, 0);
    }

    private void updateServiceMetrics(String serviceName, String endpoint, String eventType, long durationMs) {
        String key = serviceName + ":" + endpoint;
        serviceMetricsCache.compute(key, (k, metrics) -> {
            if (metrics == null) {
                metrics = new ServiceMetrics();
                metrics.setServiceName(serviceName);
                metrics.setEndpoint(endpoint);
            }
            
            switch (eventType) {
                case "REQUEST_STARTED":
                    metrics.incrementActiveRequests();
                    break;
                case "REQUEST_COMPLETED":
                    metrics.decrementActiveRequests();
                    metrics.incrementSuccessCount();
                    metrics.addLatency(durationMs);
                    break;
                case "REQUEST_FAILED":
                    metrics.decrementActiveRequests();
                    metrics.incrementErrorCount();
                    break;
            }
            
            metrics.updateStatistics();
            return metrics;
        });
    }

    private void updateLatencyDistribution(String serviceName, String endpoint, long latencyMs) {
        String key = serviceName + ":" + endpoint;
        latencyDistributions.compute(key, (k, distribution) -> {
            if (distribution == null) {
                distribution = new LatencyDistribution();
            }
            distribution.addLatency(latencyMs);
            return distribution;
        });
    }

    private void checkSLACompliance(PerformanceMetric metric) {
        SLAConfiguration sla = slaMonitoringService.getSLAForEndpoint(
            metric.getServiceName(), metric.getEndpoint());
        
        if (sla != null) {
            boolean compliant = slaMonitoringService.checkCompliance(metric, sla);
            
            if (!compliant) {
                Map<String, Object> slaViolation = new HashMap<>();
                slaViolation.put("violationType", "SLA_VIOLATION");
                slaViolation.put("serviceName", metric.getServiceName());
                slaViolation.put("endpoint", metric.getEndpoint());
                slaViolation.put("actualLatency", metric.getDurationMs());
                slaViolation.put("slaThreshold", sla.getMaxLatencyMs());
                slaViolation.put("requestId", metric.getRequestId());
                slaViolation.put("timestamp", LocalDateTime.now().toString());
                
                kafkaTemplate.send("sla-violations", slaViolation);
            }
        }
    }

    private void createPerformanceAlert(String alertType, Object metric, double value) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", alertType);
        alert.put("metric", metric);
        alert.put("value", value);
        alert.put("timestamp", LocalDateTime.now().toString());
        alert.put("severity", determineAlertSeverity(alertType, value));
        
        kafkaTemplate.send("performance-alerts", alert);
    }

    private String determineAlertSeverity(String alertType, double value) {
        switch (alertType) {
            case "HIGH_LATENCY":
                return value > 5000 ? "CRITICAL" : value > 2000 ? "HIGH" : "MEDIUM";
            case "HIGH_ERROR_RATE":
                return value > 0.1 ? "CRITICAL" : value > 0.05 ? "HIGH" : "MEDIUM";
            default:
                return "MEDIUM";
        }
    }

    private void aggregateMetrics() {
        try {
            logger.info("Aggregating performance metrics for {} services", serviceMetricsCache.size());
            
            for (Map.Entry<String, ServiceMetrics> entry : serviceMetricsCache.entrySet()) {
                ServiceMetrics metrics = entry.getValue();
                
                Map<String, Object> aggregated = new HashMap<>();
                aggregated.put("serviceName", metrics.getServiceName());
                aggregated.put("endpoint", metrics.getEndpoint());
                aggregated.put("period", windowSizeMinutes + " minutes");
                aggregated.put("totalRequests", metrics.getTotalRequests());
                aggregated.put("successCount", metrics.getSuccessCount());
                aggregated.put("errorCount", metrics.getErrorCount());
                aggregated.put("errorRate", metrics.getErrorRate());
                aggregated.put("avgLatency", metrics.getAvgLatency());
                aggregated.put("p50Latency", metrics.getP50Latency());
                aggregated.put("p95Latency", metrics.getP95Latency());
                aggregated.put("p99Latency", metrics.getP99Latency());
                aggregated.put("timestamp", LocalDateTime.now().toString());
                
                kafkaTemplate.send("aggregated-performance-metrics", aggregated);
            }
            
        } catch (Exception e) {
            logger.error("Error aggregating metrics: {}", e.getMessage(), e);
        }
    }

    private void performTrendAnalysis() {
        if (!enableTrendAnalysis) {
            return;
        }
        
        try {
            logger.info("Performing trend analysis for performance metrics");
            
            for (Map.Entry<String, ServiceMetrics> entry : serviceMetricsCache.entrySet()) {
                ServiceMetrics current = entry.getValue();
                ServiceMetrics baseline = performanceAnalysisService.getBaseline(
                    current.getServiceName(), 
                    current.getEndpoint(), 
                    baselineWindowHours
                );
                
                if (baseline != null) {
                    analyzeTrends(current, baseline);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error performing trend analysis: {}", e.getMessage(), e);
        }
    }

    private void analyzeTrends(ServiceMetrics current, ServiceMetrics baseline) {
        double latencyChange = (current.getAvgLatency() - baseline.getAvgLatency()) / baseline.getAvgLatency();
        double errorRateChange = current.getErrorRate() - baseline.getErrorRate();
        
        if (Math.abs(latencyChange) > 0.2 || Math.abs(errorRateChange) > 0.05) {
            Map<String, Object> trend = new HashMap<>();
            trend.put("serviceName", current.getServiceName());
            trend.put("endpoint", current.getEndpoint());
            trend.put("latencyChange", latencyChange);
            trend.put("errorRateChange", errorRateChange);
            trend.put("currentAvgLatency", current.getAvgLatency());
            trend.put("baselineAvgLatency", baseline.getAvgLatency());
            trend.put("currentErrorRate", current.getErrorRate());
            trend.put("baselineErrorRate", baseline.getErrorRate());
            trend.put("timestamp", LocalDateTime.now().toString());
            trend.put("trend", latencyChange > 0 ? "DEGRADING" : "IMPROVING");
            
            kafkaTemplate.send("performance-trends", trend);
        }
    }

    private void cleanupOldMetrics() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
            int removed = performanceAnalysisService.removeMetricsOlderThan(cutoff);
            
            if (removed > 0) {
                logger.info("Cleaned up {} old performance metrics", removed);
            }
            
            serviceMetricsCache.entrySet().removeIf(entry -> 
                entry.getValue().getLastUpdated().isBefore(cutoff)
            );
            
        } catch (Exception e) {
            logger.error("Error cleaning up old metrics: {}", e.getMessage(), e);
        }
    }

    private boolean isValidPerformanceMessage(JsonNode messageNode) {
        try {
            return messageNode != null &&
                   messageNode.has("eventType") && 
                   StringUtils.hasText(messageNode.get("eventType").asText()) &&
                   messageNode.has("timestamp");
        } catch (Exception e) {
            logger.error("Error validating performance message: {}", e.getMessage());
            return false;
        }
    }

    private void handleProcessingError(String message, String topic, Exception error, 
                                     String correlationId, String traceId, Acknowledgment acknowledgment) {
        try {
            errorCounter.increment();
            logger.error("Error processing performance metrics message: {}", error.getMessage(), error);
            
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
        logger.error("Circuit breaker fallback triggered for performance metrics consumer: {}", ex.getMessage());
        
        errorCounter.increment();
        sendToDlq(message, topic, "Circuit breaker fallback: " + ex.getMessage(), ex, correlationId, traceId);
        acknowledgment.acknowledge();
    }

    // Helper methods
    private void updateErrorRate(String serviceName, String endpoint) {
        String key = serviceName + ":" + endpoint;
        ServiceMetrics metrics = serviceMetricsCache.get(key);
        if (metrics != null) {
            metrics.incrementErrorCount();
            metrics.updateStatistics();
        }
    }

    private boolean isCircuitBreakerCandidate(String serviceName, String endpoint) {
        String key = serviceName + ":" + endpoint;
        ServiceMetrics metrics = serviceMetricsCache.get(key);
        return metrics != null && metrics.getErrorRate() > 0.5 && metrics.getTotalRequests() > 10;
    }

    private void triggerCircuitBreakerEvaluation(String serviceName, String endpoint) {
        Map<String, Object> evaluation = new HashMap<>();
        evaluation.put("serviceName", serviceName);
        evaluation.put("endpoint", endpoint);
        evaluation.put("action", "EVALUATE_CIRCUIT_BREAKER");
        evaluation.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("circuit-breaker-evaluations", evaluation);
    }

    private void analyzeDatabasePerformance(Map<String, Object> queryMetrics) {
        performanceAnalysisService.analyzeDatabasePerformance(queryMetrics);
    }

    private void updateDatabaseMetrics(String database, String queryType, long executionTimeMs) {
        metricsService.recordDatabaseQuery(database, queryType, executionTimeMs);
    }

    private void updateCacheStatistics(String cacheType, String operation, boolean hit, long latencyMs) {
        metricsService.recordCacheOperation(cacheType, operation, hit, latencyMs);
    }

    private double calculateCacheHitRate(String cacheType) {
        return performanceAnalysisService.calculateCacheHitRate(cacheType);
    }

    private void createCachePerformanceAlert(String cacheType, double hitRate, String alertType) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", alertType);
        alert.put("cacheType", cacheType);
        alert.put("hitRate", hitRate);
        alert.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("cache-performance-alerts", alert);
    }

    private void handleApiTimeout(String apiName, String endpoint) {
        Map<String, Object> timeout = new HashMap<>();
        timeout.put("apiName", apiName);
        timeout.put("endpoint", endpoint);
        timeout.put("eventType", "API_TIMEOUT");
        timeout.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("api-timeout-events", timeout);
    }

    private void updateExternalApiStatistics(String apiName, String endpoint, long responseTimeMs, boolean success) {
        performanceAnalysisService.updateExternalApiStatistics(apiName, endpoint, responseTimeMs, success);
    }

    private void incrementApiFailureCount(String apiName, String endpoint) {
        performanceAnalysisService.incrementApiFailureCount(apiName, endpoint);
    }

    private void evaluateApiCircuitBreaker(String apiName, String endpoint) {
        if (performanceAnalysisService.shouldTripCircuitBreaker(apiName, endpoint)) {
            Map<String, Object> trip = new HashMap<>();
            trip.put("apiName", apiName);
            trip.put("endpoint", endpoint);
            trip.put("action", "TRIP_CIRCUIT_BREAKER");
            trip.put("timestamp", LocalDateTime.now().toString());
            
            kafkaTemplate.send("api-circuit-breaker", trip);
        }
    }

    private void createQueueLagAlert(String queueName, long lagMs) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "QUEUE_LAG");
        alert.put("queueName", queueName);
        alert.put("lagMs", lagMs);
        alert.put("timestamp", LocalDateTime.now().toString());
        alert.put("severity", lagMs > 60000 ? "CRITICAL" : lagMs > 30000 ? "HIGH" : "MEDIUM");
        
        kafkaTemplate.send("queue-lag-alerts", alert);
    }

    private void updateQueueStatistics(String queueName, String messageType, long processingTimeMs, boolean processed) {
        performanceAnalysisService.updateQueueStatistics(queueName, messageType, processingTimeMs, processed);
    }

    private double getBatchJobMinThroughput(String jobName) {
        return performanceAnalysisService.getBatchJobMinThroughput(jobName);
    }

    private void createBatchJobAlert(String jobName, double metric, String alertType) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", alertType);
        alert.put("jobName", jobName);
        alert.put("metric", metric);
        alert.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("batch-job-alerts", alert);
    }

    private void updateBatchJobStatistics(String jobName, long executionTimeMs, int recordsProcessed, String status) {
        performanceAnalysisService.updateBatchJobStatistics(jobName, executionTimeMs, recordsProcessed, status);
    }

    private void identifyBottlenecks(String transactionType, Map<String, Long> timingBreakdown) {
        String bottleneck = timingBreakdown.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("unknown");
        
        if (timingBreakdown.get(bottleneck) > 1000) {
            Map<String, Object> bottleneckAlert = new HashMap<>();
            bottleneckAlert.put("transactionType", transactionType);
            bottleneckAlert.put("bottleneck", bottleneck);
            bottleneckAlert.put("duration", timingBreakdown.get(bottleneck));
            bottleneckAlert.put("timestamp", LocalDateTime.now().toString());
            
            kafkaTemplate.send("bottleneck-alerts", bottleneckAlert);
        }
    }

    private void checkTransactionSLA(String transactionType, long totalTimeMs) {
        slaMonitoringService.checkTransactionSLA(transactionType, totalTimeMs);
    }

    private void updateTransactionStatistics(String transactionType, long totalTimeMs) {
        performanceAnalysisService.updateTransactionStatistics(transactionType, totalTimeMs);
    }

    private void updateDependencyMap(String sourceService, String targetService, long latencyMs, boolean success) {
        performanceAnalysisService.updateDependencyMap(sourceService, targetService, latencyMs, success);
    }

    private void evaluateCascadingFailureRisk(String sourceService, String targetService) {
        if (performanceAnalysisService.hasCascadingFailureRisk(sourceService, targetService)) {
            Map<String, Object> risk = new HashMap<>();
            risk.put("sourceService", sourceService);
            risk.put("targetService", targetService);
            risk.put("riskType", "CASCADING_FAILURE");
            risk.put("timestamp", LocalDateTime.now().toString());
            
            kafkaTemplate.send("cascading-failure-risks", risk);
        }
    }

    private long getDependencyThreshold(String sourceService, String targetService) {
        return performanceAnalysisService.getDependencyThreshold(sourceService, targetService);
    }

    private void createDependencyAlert(String sourceService, String targetService, long latencyMs) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "DEPENDENCY_LATENCY");
        alert.put("sourceService", sourceService);
        alert.put("targetService", targetService);
        alert.put("latencyMs", latencyMs);
        alert.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("dependency-alerts", alert);
    }

    private void handleCpuUsage(String resourceId, double utilization, JsonNode messageNode) {
        if (utilization > 0.9) {
            createResourceAlert("CPU", resourceId, utilization, "CRITICAL_CPU_USAGE");
        }
    }

    private void handleMemoryUsage(String resourceId, double utilization, JsonNode messageNode) {
        if (messageNode.has("availableMemoryMB")) {
            long availableMemoryMB = messageNode.get("availableMemoryMB").asLong();
            if (availableMemoryMB < 500) {
                createResourceAlert("MEMORY", resourceId, utilization, "LOW_MEMORY");
            }
        }
    }

    private void handleDiskUsage(String resourceId, double utilization, JsonNode messageNode) {
        if (messageNode.has("availableSpaceGB")) {
            long availableSpaceGB = messageNode.get("availableSpaceGB").asLong();
            if (availableSpaceGB < 10) {
                createResourceAlert("DISK", resourceId, utilization, "LOW_DISK_SPACE");
            }
        }
    }

    private void handleNetworkUsage(String resourceId, double utilization, JsonNode messageNode) {
        if (messageNode.has("packetLossRate")) {
            double packetLossRate = messageNode.get("packetLossRate").asDouble();
            if (packetLossRate > 0.01) {
                createResourceAlert("NETWORK", resourceId, packetLossRate, "HIGH_PACKET_LOSS");
            }
        }
    }

    private void handleThreadPoolUsage(String resourceId, double utilization, JsonNode messageNode) {
        if (messageNode.has("queueSize")) {
            int queueSize = messageNode.get("queueSize").asInt();
            if (queueSize > 1000) {
                createResourceAlert("THREAD_POOL", resourceId, queueSize, "HIGH_QUEUE_SIZE");
            }
        }
    }

    private void handleConnectionPoolUsage(String resourceId, double utilization, JsonNode messageNode) {
        if (messageNode.has("waitingConnections")) {
            int waitingConnections = messageNode.get("waitingConnections").asInt();
            if (waitingConnections > 10) {
                createResourceAlert("CONNECTION_POOL", resourceId, waitingConnections, "CONNECTION_POOL_EXHAUSTION");
            }
        }
    }

    private void createResourceAlert(String resourceType, String resourceId, double value, String alertType) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", alertType);
        alert.put("resourceType", resourceType);
        alert.put("resourceId", resourceId);
        alert.put("value", value);
        alert.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("resource-alerts", alert);
    }

    private void createThroughputAlert(String serviceName, String metricType, double actual, double expected) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "LOW_THROUGHPUT");
        alert.put("serviceName", serviceName);
        alert.put("metricType", metricType);
        alert.put("actualThroughput", actual);
        alert.put("expectedThroughput", expected);
        alert.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("throughput-alerts", alert);
    }

    private double getExpectedThroughput(String serviceName, String metricType) {
        return performanceAnalysisService.getExpectedThroughput(serviceName, metricType);
    }

    private void updateThroughputTrends(String serviceName, String metricType, double throughput) {
        performanceAnalysisService.updateThroughputTrends(serviceName, metricType, throughput);
    }

    private void initiateRootCauseAnalysis(String serviceName, String endpoint, long latencyMs) {
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("serviceName", serviceName);
        analysis.put("endpoint", endpoint);
        analysis.put("latencyMs", latencyMs);
        analysis.put("action", "INITIATE_ROOT_CAUSE_ANALYSIS");
        analysis.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("root-cause-analysis", analysis);
    }

    private void recommendOptimizations(String serviceName, String degradationType, double degradationPercent) {
        List<String> recommendations = performanceAnalysisService.getOptimizationRecommendations(
            serviceName, degradationType, degradationPercent);
        
        Map<String, Object> optimizations = new HashMap<>();
        optimizations.put("serviceName", serviceName);
        optimizations.put("degradationType", degradationType);
        optimizations.put("degradationPercent", degradationPercent);
        optimizations.put("recommendations", recommendations);
        optimizations.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("optimization-recommendations", optimizations);
    }

    private void triggerCapacityScaling(String resourceType, String resourceId) {
        Map<String, Object> scaling = new HashMap<>();
        scaling.put("resourceType", resourceType);
        scaling.put("resourceId", resourceId);
        scaling.put("action", "SCALE_UP");
        scaling.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("auto-scaling-triggers", scaling);
    }

    public static class SLAConfiguration {
        private String serviceName;
        private String endpoint;
        private long maxLatencyMs;
        private double maxErrorRate;
        
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        
        public long getMaxLatencyMs() { return maxLatencyMs; }
        public void setMaxLatencyMs(long maxLatencyMs) { this.maxLatencyMs = maxLatencyMs; }
        
        public double getMaxErrorRate() { return maxErrorRate; }
        public void setMaxErrorRate(double maxErrorRate) { this.maxErrorRate = maxErrorRate; }
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down PerformanceMetricsConsumer");
        
        aggregateMetrics();
        
        scheduledExecutor.shutdown();
        analysisExecutor.shutdown();
        
        try {
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            if (!analysisExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                analysisExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Error shutting down executors", e);
            scheduledExecutor.shutdownNow();
            analysisExecutor.shutdownNow();
        }
        
        logger.info("PerformanceMetricsConsumer shutdown complete. Total metrics processed: {}", 
                   totalMetricsProcessed.get());
    }
}