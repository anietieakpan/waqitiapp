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
public class DistributedTracingConsumer extends BaseKafkaConsumer {

    private static final Logger logger = LoggerFactory.getLogger(DistributedTracingConsumer.class);
    
    @Value("${waqiti.monitoring.tracing.analysis-window-minutes:5}")
    private int analysisWindowMinutes;
    
    @Value("${waqiti.monitoring.tracing.trace-timeout-minutes:10}")
    private int traceTimeoutMinutes;
    
    @Value("${waqiti.monitoring.tracing.slow-trace-threshold-ms:5000}")
    private long slowTraceThresholdMs;
    
    @Value("${waqiti.monitoring.tracing.error-trace-threshold:5}")
    private int errorTraceThreshold;
    
    @Value("${waqiti.monitoring.tracing.sampling-rate:0.1}")
    private double samplingRate;

    private final TraceRepository traceRepository;
    private final SpanRepository spanRepository;
    private final TraceAnalysisRepository traceAnalysisRepository;
    private final TracingAlertRepository tracingAlertRepository;
    private final ServiceMapRepository serviceMapRepository;
    private final TraceMetricsRepository traceMetricsRepository;
    private final SpanErrorRepository spanErrorRepository;
    private final AlertingService alertingService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(4);
    private final Map<String, TraceBuilder> activeTraces = new ConcurrentHashMap<>();
    private final Map<String, List<SpanData>> spanBuffer = new ConcurrentHashMap<>();
    private final Map<String, TraceStatistics> traceStats = new ConcurrentHashMap<>();
    private final Set<String> sampledTraces = ConcurrentHashMap.newKeySet();

    private Counter processedEventsCounter;
    private Counter processedTraceDataCounter;
    private Counter processedSpanDataCounter;
    private Counter processedTraceAnalysisCounter;
    private Counter processedTracingAlertCounter;
    private Counter processedTraceMetricsCounter;
    private Counter processedSpanErrorCounter;
    private Counter processedSlowTraceCounter;
    private Counter processedErrorTraceCounter;
    private Counter processedTraceTimeoutCounter;
    private Counter processedSpanAnomalyCounter;
    private Counter processedServiceMapUpdateCounter;
    private Counter processedTraceCorrelationCounter;
    private Counter processedBottleneckDetectionCounter;
    private Counter processedCriticalPathCounter;
    private Timer tracingProcessingTimer;
    private Timer tracingAnalysisTimer;
    
    private Gauge activeTracesGauge;
    private Gauge pendingSpansGauge;
    private Gauge avgTraceLatencyGauge;
    private Gauge traceErrorRateGauge;

    public DistributedTracingConsumer(TraceRepository traceRepository,
                                      SpanRepository spanRepository,
                                      TraceAnalysisRepository traceAnalysisRepository,
                                      TracingAlertRepository tracingAlertRepository,
                                      ServiceMapRepository serviceMapRepository,
                                      TraceMetricsRepository traceMetricsRepository,
                                      SpanErrorRepository spanErrorRepository,
                                      AlertingService alertingService,
                                      MetricsService metricsService,
                                      NotificationService notificationService,
                                      ObjectMapper objectMapper,
                                      MeterRegistry meterRegistry) {
        this.traceRepository = traceRepository;
        this.spanRepository = spanRepository;
        this.traceAnalysisRepository = traceAnalysisRepository;
        this.tracingAlertRepository = tracingAlertRepository;
        this.serviceMapRepository = serviceMapRepository;
        this.traceMetricsRepository = traceMetricsRepository;
        this.spanErrorRepository = spanErrorRepository;
        this.alertingService = alertingService;
        this.metricsService = metricsService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        this.processedEventsCounter = Counter.builder("distributed_tracing_events_processed")
                .description("Number of distributed tracing events processed")
                .register(meterRegistry);
        
        this.processedTraceDataCounter = Counter.builder("trace_data_processed")
                .description("Number of trace data events processed")
                .register(meterRegistry);
        
        this.processedSpanDataCounter = Counter.builder("span_data_processed")
                .description("Number of span data events processed")
                .register(meterRegistry);
        
        this.processedTraceAnalysisCounter = Counter.builder("trace_analysis_processed")
                .description("Number of trace analysis events processed")
                .register(meterRegistry);
        
        this.processedTracingAlertCounter = Counter.builder("tracing_alert_processed")
                .description("Number of tracing alert events processed")
                .register(meterRegistry);
        
        this.processedTraceMetricsCounter = Counter.builder("trace_metrics_processed")
                .description("Number of trace metrics events processed")
                .register(meterRegistry);
        
        this.processedSpanErrorCounter = Counter.builder("span_error_processed")
                .description("Number of span error events processed")
                .register(meterRegistry);
        
        this.processedSlowTraceCounter = Counter.builder("slow_trace_processed")
                .description("Number of slow trace events processed")
                .register(meterRegistry);
        
        this.processedErrorTraceCounter = Counter.builder("error_trace_processed")
                .description("Number of error trace events processed")
                .register(meterRegistry);
        
        this.processedTraceTimeoutCounter = Counter.builder("trace_timeout_processed")
                .description("Number of trace timeout events processed")
                .register(meterRegistry);
        
        this.processedSpanAnomalyCounter = Counter.builder("span_anomaly_processed")
                .description("Number of span anomaly events processed")
                .register(meterRegistry);
        
        this.processedServiceMapUpdateCounter = Counter.builder("service_map_update_processed")
                .description("Number of service map update events processed")
                .register(meterRegistry);
        
        this.processedTraceCorrelationCounter = Counter.builder("trace_correlation_processed")
                .description("Number of trace correlation events processed")
                .register(meterRegistry);
        
        this.processedBottleneckDetectionCounter = Counter.builder("bottleneck_detection_processed")
                .description("Number of bottleneck detection events processed")
                .register(meterRegistry);
        
        this.processedCriticalPathCounter = Counter.builder("critical_path_processed")
                .description("Number of critical path events processed")
                .register(meterRegistry);
        
        this.tracingProcessingTimer = Timer.builder("tracing_processing_duration")
                .description("Time taken to process tracing events")
                .register(meterRegistry);
        
        this.tracingAnalysisTimer = Timer.builder("tracing_analysis_duration")
                .description("Time taken to perform tracing analysis")
                .register(meterRegistry);
        
        this.activeTracesGauge = Gauge.builder("active_traces_count", this, DistributedTracingConsumer::getActiveTracesCount)
                .description("Number of active traces")
                .register(meterRegistry);
        
        this.pendingSpansGauge = Gauge.builder("pending_spans_count", this, DistributedTracingConsumer::getPendingSpansCount)
                .description("Number of pending spans")
                .register(meterRegistry);
        
        this.avgTraceLatencyGauge = Gauge.builder("avg_trace_latency_ms", this, DistributedTracingConsumer::getAvgTraceLatency)
                .description("Average trace latency in milliseconds")
                .register(meterRegistry);
        
        this.traceErrorRateGauge = Gauge.builder("trace_error_rate_percent", this, DistributedTracingConsumer::getTraceErrorRate)
                .description("Trace error rate percentage")
                .register(meterRegistry);

        scheduledExecutor.scheduleAtFixedRate(this::performTracingAnalysis, 
                analysisWindowMinutes, analysisWindowMinutes, TimeUnit.MINUTES);
        
        scheduledExecutor.scheduleAtFixedRate(this::processIncompleteTraces, 
                traceTimeoutMinutes, traceTimeoutMinutes, TimeUnit.MINUTES);
        
        scheduledExecutor.scheduleAtFixedRate(this::analyzeCriticalPaths, 
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

    @KafkaListener(topics = "distributed-tracing", groupId = "distributed-tracing-group", 
                   containerFactory = "kafkaListenerContainerFactory")
    @CircuitBreaker(name = "distributed-tracing-consumer")
    @Retry(name = "distributed-tracing-consumer")
    @Transactional
    public void handleDistributedTracingEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "distributed-tracing");

        try {
            logger.info("Processing distributed tracing event: partition={}, offset={}", 
                    record.partition(), record.offset());

            JsonNode eventData = objectMapper.readTree(record.value());
            String eventType = eventData.path("eventType").asText();

            switch (eventType) {
                case "TRACE_DATA":
                    processTraceData(eventData);
                    processedTraceDataCounter.increment();
                    break;
                case "SPAN_DATA":
                    processSpanData(eventData);
                    processedSpanDataCounter.increment();
                    break;
                case "TRACE_ANALYSIS":
                    processTraceAnalysis(eventData);
                    processedTraceAnalysisCounter.increment();
                    break;
                case "TRACING_ALERT":
                    processTracingAlert(eventData);
                    processedTracingAlertCounter.increment();
                    break;
                case "TRACE_METRICS":
                    processTraceMetrics(eventData);
                    processedTraceMetricsCounter.increment();
                    break;
                case "SPAN_ERROR":
                    processSpanError(eventData);
                    processedSpanErrorCounter.increment();
                    break;
                case "SLOW_TRACE":
                    processSlowTrace(eventData);
                    processedSlowTraceCounter.increment();
                    break;
                case "ERROR_TRACE":
                    processErrorTrace(eventData);
                    processedErrorTraceCounter.increment();
                    break;
                case "TRACE_TIMEOUT":
                    processTraceTimeout(eventData);
                    processedTraceTimeoutCounter.increment();
                    break;
                case "SPAN_ANOMALY":
                    processSpanAnomaly(eventData);
                    processedSpanAnomalyCounter.increment();
                    break;
                case "SERVICE_MAP_UPDATE":
                    processServiceMapUpdate(eventData);
                    processedServiceMapUpdateCounter.increment();
                    break;
                case "TRACE_CORRELATION":
                    processTraceCorrelation(eventData);
                    processedTraceCorrelationCounter.increment();
                    break;
                case "BOTTLENECK_DETECTION":
                    processBottleneckDetection(eventData);
                    processedBottleneckDetectionCounter.increment();
                    break;
                case "CRITICAL_PATH":
                    processCriticalPath(eventData);
                    processedCriticalPathCounter.increment();
                    break;
                default:
                    logger.warn("Unknown distributed tracing event type: {}", eventType);
            }

            processedEventsCounter.increment();
            ack.acknowledge();

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse distributed tracing event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } catch (DataAccessException e) {
            logger.error("Database error processing distributed tracing event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } catch (Exception e) {
            logger.error("Unexpected error processing distributed tracing event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            sample.stop(tracingProcessingTimer);
            MDC.clear();
        }
    }

    private void processTraceData(JsonNode eventData) {
        try {
            Trace trace = new Trace();
            trace.setTraceId(eventData.path("traceId").asText());
            trace.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            trace.setRootSpanId(eventData.path("rootSpanId").asText());
            trace.setServiceName(eventData.path("serviceName").asText());
            trace.setOperationName(eventData.path("operationName").asText());
            trace.setDurationMs(eventData.path("durationMs").asLong());
            trace.setStatus(eventData.path("status").asText());
            trace.setSpanCount(eventData.path("spanCount").asInt());
            trace.setServiceCount(eventData.path("serviceCount").asInt());
            trace.setErrorCount(eventData.path("errorCount").asInt());
            trace.setWarningCount(eventData.path("warningCount").asInt());
            trace.setSampled(eventData.path("sampled").asBoolean());
            
            JsonNode tagsNode = eventData.path("tags");
            if (!tagsNode.isMissingNode()) {
                trace.setTags(tagsNode.toString());
            }
            
            JsonNode baggage = eventData.path("baggage");
            if (!baggage.isMissingNode()) {
                trace.setBaggage(baggage.toString());
            }
            
            traceRepository.save(trace);
            
            updateTraceBuilder(trace);
            
            metricsService.recordTraceMetric(trace.getServiceName(), trace.getOperationName(), trace);
            
            if (shouldSample(trace.getTraceId())) {
                sampledTraces.add(trace.getTraceId());
                performRealTimeTraceAnalysis(trace);
            }
            
            logger.debug("Processed trace data: traceId={}, service={}, duration={}ms, spans={}", 
                    trace.getTraceId(), trace.getServiceName(), trace.getDurationMs(), trace.getSpanCount());
            
        } catch (Exception e) {
            logger.error("Error processing trace data: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processSpanData(JsonNode eventData) {
        try {
            Span span = new Span();
            span.setSpanId(eventData.path("spanId").asText());
            span.setTraceId(eventData.path("traceId").asText());
            span.setParentSpanId(eventData.path("parentSpanId").asText());
            span.setServiceName(eventData.path("serviceName").asText());
            span.setOperationName(eventData.path("operationName").asText());
            span.setStartTime(parseTimestamp(eventData.path("startTime").asText()));
            span.setEndTime(parseTimestamp(eventData.path("endTime").asText()));
            span.setDurationMs(eventData.path("durationMs").asLong());
            span.setStatus(eventData.path("status").asText());
            span.setKind(eventData.path("kind").asText());
            span.setRemoteEndpoint(eventData.path("remoteEndpoint").asText());
            
            JsonNode tagsNode = eventData.path("tags");
            if (!tagsNode.isMissingNode()) {
                span.setTags(tagsNode.toString());
            }
            
            JsonNode logsNode = eventData.path("logs");
            if (!logsNode.isMissingNode()) {
                span.setLogs(logsNode.toString());
            }
            
            spanRepository.save(span);
            
            updateSpanBuffer(span.getTraceId(), new SpanData(span));
            updateTraceBuilderWithSpan(span);
            
            metricsService.recordSpanMetric(span.getServiceName(), span.getOperationName(), span);
            
            if (shouldTriggerSpanAnalysis(span)) {
                performRealTimeSpanAnalysis(span);
            }
            
            updateServiceMapFromSpan(span);
            
            logger.debug("Processed span data: spanId={}, traceId={}, service={}, duration={}ms", 
                    span.getSpanId(), span.getTraceId(), span.getServiceName(), span.getDurationMs());
            
        } catch (Exception e) {
            logger.error("Error processing span data: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processTraceAnalysis(JsonNode eventData) {
        try {
            TraceAnalysis analysis = new TraceAnalysis();
            analysis.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            analysis.setTraceId(eventData.path("traceId").asText());
            analysis.setAnalysisType(eventData.path("analysisType").asText());
            analysis.setCriticalPath(eventData.path("criticalPath").asText());
            analysis.setBottleneckService(eventData.path("bottleneckService").asText());
            analysis.setPerformanceScore(eventData.path("performanceScore").asDouble());
            analysis.setErrorAnalysis(eventData.path("errorAnalysis").asText());
            analysis.setOptimizationSuggestions(eventData.path("optimizationSuggestions").asText());
            analysis.setServiceDependencies(eventData.path("serviceDependencies").asText());
            analysis.setLatencyBreakdown(eventData.path("latencyBreakdown").asText());
            
            JsonNode metricsNode = eventData.path("metrics");
            if (!metricsNode.isMissingNode()) {
                analysis.setMetrics(metricsNode.toString());
            }
            
            traceAnalysisRepository.save(analysis);
            
            if (analysis.getPerformanceScore() < 0.5) {
                generatePerformanceAlert(analysis);
            }
            
            if (analysis.getBottleneckService() != null && !analysis.getBottleneckService().isEmpty()) {
                analyzeBottleneckImpact(analysis);
            }
            
            logger.debug("Processed trace analysis: traceId={}, type={}, score={}, bottleneck={}", 
                    analysis.getTraceId(), analysis.getAnalysisType(), 
                    analysis.getPerformanceScore(), analysis.getBottleneckService());
            
        } catch (Exception e) {
            logger.error("Error processing trace analysis: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processTracingAlert(JsonNode eventData) {
        try {
            TracingAlert alert = new TracingAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setTraceId(eventData.path("traceId").asText());
            alert.setSpanId(eventData.path("spanId").asText());
            alert.setServiceName(eventData.path("serviceName").asText());
            alert.setAlertType(eventData.path("alertType").asText());
            alert.setSeverity(eventData.path("severity").asText());
            alert.setDescription(eventData.path("description").asText());
            alert.setRecommendedAction(eventData.path("recommendedAction").asText());
            alert.setResolved(eventData.path("resolved").asBoolean());
            
            if (eventData.has("resolvedAt")) {
                alert.setResolvedAt(parseTimestamp(eventData.path("resolvedAt").asText()));
            }
            
            tracingAlertRepository.save(alert);
            
            if (!alert.isResolved() && ("HIGH".equals(alert.getSeverity()) || "CRITICAL".equals(alert.getSeverity()))) {
                alertingService.sendAlert("TRACING_ALERT", alert.getDescription(), 
                        Map.of("traceId", alert.getTraceId(), "serviceName", alert.getServiceName(), 
                               "severity", alert.getSeverity()));
            }
            
            logger.info("Processed tracing alert: traceId={}, service={}, type={}, severity={}", 
                    alert.getTraceId(), alert.getServiceName(), alert.getAlertType(), alert.getSeverity());
            
        } catch (Exception e) {
            logger.error("Error processing tracing alert: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processTraceMetrics(JsonNode eventData) {
        try {
            TraceMetrics metrics = new TraceMetrics();
            metrics.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            metrics.setServiceName(eventData.path("serviceName").asText());
            metrics.setOperationName(eventData.path("operationName").asText());
            metrics.setTraceCount(eventData.path("traceCount").asLong());
            metrics.setAvgDurationMs(eventData.path("avgDurationMs").asDouble());
            metrics.setP50DurationMs(eventData.path("p50DurationMs").asDouble());
            metrics.setP95DurationMs(eventData.path("p95DurationMs").asDouble());
            metrics.setP99DurationMs(eventData.path("p99DurationMs").asDouble());
            metrics.setMaxDurationMs(eventData.path("maxDurationMs").asDouble());
            metrics.setMinDurationMs(eventData.path("minDurationMs").asDouble());
            metrics.setErrorRate(eventData.path("errorRate").asDouble());
            metrics.setThroughputRps(eventData.path("throughputRps").asDouble());
            
            traceMetricsRepository.save(metrics);
            
            metricsService.recordAggregatedTraceMetrics(metrics.getServiceName(), metrics.getOperationName(), metrics);
            
            updateTraceStatistics(metrics);
            
            logger.debug("Processed trace metrics: service={}, operation={}, traces={}, avg duration={}ms", 
                    metrics.getServiceName(), metrics.getOperationName(), 
                    metrics.getTraceCount(), metrics.getAvgDurationMs());
            
        } catch (Exception e) {
            logger.error("Error processing trace metrics: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processSpanError(JsonNode eventData) {
        try {
            SpanError spanError = new SpanError();
            spanError.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            spanError.setSpanId(eventData.path("spanId").asText());
            spanError.setTraceId(eventData.path("traceId").asText());
            spanError.setServiceName(eventData.path("serviceName").asText());
            spanError.setOperationName(eventData.path("operationName").asText());
            spanError.setErrorType(eventData.path("errorType").asText());
            spanError.setErrorMessage(eventData.path("errorMessage").asText());
            spanError.setErrorCode(eventData.path("errorCode").asText());
            spanError.setSeverity(eventData.path("severity").asText());
            spanError.setStackTrace(eventData.path("stackTrace").asText());
            spanError.setCause(eventData.path("cause").asText());
            spanError.setResolved(eventData.path("resolved").asBoolean());
            
            JsonNode contextNode = eventData.path("context");
            if (!contextNode.isMissingNode()) {
                spanError.setContext(contextNode.toString());
            }
            
            spanErrorRepository.save(spanError);
            
            metricsService.recordSpanError(spanError.getServiceName(), spanError.getOperationName(), spanError);
            
            if ("CRITICAL".equals(spanError.getSeverity()) || "HIGH".equals(spanError.getSeverity())) {
                generateSpanErrorAlert(spanError);
            }
            
            analyzeErrorPattern(spanError);
            
            logger.warn("Processed span error: spanId={}, traceId={}, service={}, type={}, severity={}", 
                    spanError.getSpanId(), spanError.getTraceId(), spanError.getServiceName(), 
                    spanError.getErrorType(), spanError.getSeverity());
            
        } catch (Exception e) {
            logger.error("Error processing span error: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processSlowTrace(JsonNode eventData) {
        try {
            String traceId = eventData.path("traceId").asText();
            String serviceName = eventData.path("serviceName").asText();
            String operationName = eventData.path("operationName").asText();
            long durationMs = eventData.path("durationMs").asLong();
            long threshold = eventData.path("threshold").asLong();
            String bottleneckSpan = eventData.path("bottleneckSpan").asText();
            
            TracingAlert alert = new TracingAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setTraceId(traceId);
            alert.setServiceName(serviceName);
            alert.setAlertType("SLOW_TRACE");
            alert.setSeverity(durationMs > threshold * 2 ? "HIGH" : "MEDIUM");
            alert.setDescription(String.format("Slow trace detected: %s.%s took %dms (threshold: %dms)", 
                    serviceName, operationName, durationMs, threshold));
            alert.setRecommendedAction(String.format("Investigate bottleneck in span: %s", bottleneckSpan));
            alert.setResolved(false);
            
            tracingAlertRepository.save(alert);
            
            alertingService.sendAlert("SLOW_TRACE", alert.getDescription(), 
                    Map.of("traceId", traceId, "serviceName", serviceName, "durationMs", String.valueOf(durationMs)));
            
            analyzeSlowTracePattern(traceId, serviceName, operationName, durationMs, bottleneckSpan);
            
            logger.warn("Processed slow trace: traceId={}, service={}, duration={}ms, bottleneck={}", 
                    traceId, serviceName, durationMs, bottleneckSpan);
            
        } catch (Exception e) {
            logger.error("Error processing slow trace: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processErrorTrace(JsonNode eventData) {
        try {
            String traceId = eventData.path("traceId").asText();
            String serviceName = eventData.path("serviceName").asText();
            String operationName = eventData.path("operationName").asText();
            int errorCount = eventData.path("errorCount").asInt();
            String errorPattern = eventData.path("errorPattern").asText();
            String failedService = eventData.path("failedService").asText();
            
            TracingAlert alert = new TracingAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setTraceId(traceId);
            alert.setServiceName(serviceName);
            alert.setAlertType("ERROR_TRACE");
            alert.setSeverity(errorCount > errorTraceThreshold ? "HIGH" : "MEDIUM");
            alert.setDescription(String.format("Error trace detected: %s.%s with %d errors (pattern: %s)", 
                    serviceName, operationName, errorCount, errorPattern));
            alert.setRecommendedAction(String.format("Investigate error source in service: %s", failedService));
            alert.setResolved(false);
            
            tracingAlertRepository.save(alert);
            
            alertingService.sendAlert("ERROR_TRACE", alert.getDescription(), 
                    Map.of("traceId", traceId, "serviceName", serviceName, "errorCount", String.valueOf(errorCount)));
            
            analyzeErrorTraceCorrelation(traceId, serviceName, operationName, errorPattern, failedService);
            
            logger.error("Processed error trace: traceId={}, service={}, errors={}, pattern={}", 
                    traceId, serviceName, errorCount, errorPattern);
            
        } catch (Exception e) {
            logger.error("Error processing error trace: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processTraceTimeout(JsonNode eventData) {
        try {
            String traceId = eventData.path("traceId").asText();
            String serviceName = eventData.path("serviceName").asText();
            long timeoutMs = eventData.path("timeoutMs").asLong();
            int incompleteSpans = eventData.path("incompleteSpans").asInt();
            String lastActivity = eventData.path("lastActivity").asText();
            
            TracingAlert alert = new TracingAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setTraceId(traceId);
            alert.setServiceName(serviceName);
            alert.setAlertType("TRACE_TIMEOUT");
            alert.setSeverity("HIGH");
            alert.setDescription(String.format("Trace timeout: %s incomplete after %dms with %d missing spans", 
                    traceId, timeoutMs, incompleteSpans));
            alert.setRecommendedAction(String.format("Check service health and connectivity. Last activity: %s", lastActivity));
            alert.setResolved(false);
            
            tracingAlertRepository.save(alert);
            
            alertingService.sendAlert("TRACE_TIMEOUT", alert.getDescription(), 
                    Map.of("traceId", traceId, "serviceName", serviceName, "timeoutMs", String.valueOf(timeoutMs)));
            
            cleanupIncompleteTrace(traceId);
            
            logger.warn("Processed trace timeout: traceId={}, service={}, timeout={}ms, incomplete spans={}", 
                    traceId, serviceName, timeoutMs, incompleteSpans);
            
        } catch (Exception e) {
            logger.error("Error processing trace timeout: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processSpanAnomaly(JsonNode eventData) {
        try {
            String spanId = eventData.path("spanId").asText();
            String traceId = eventData.path("traceId").asText();
            String serviceName = eventData.path("serviceName").asText();
            String anomalyType = eventData.path("anomalyType").asText();
            double anomalyScore = eventData.path("anomalyScore").asDouble();
            String expectedValue = eventData.path("expectedValue").asText();
            String actualValue = eventData.path("actualValue").asText();
            
            TracingAlert alert = new TracingAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setTraceId(traceId);
            alert.setSpanId(spanId);
            alert.setServiceName(serviceName);
            alert.setAlertType("SPAN_ANOMALY");
            alert.setSeverity(anomalyScore > 0.8 ? "HIGH" : "MEDIUM");
            alert.setDescription(String.format("Span anomaly detected: %s in %s (score: %.2f, expected: %s, actual: %s)", 
                    anomalyType, serviceName, anomalyScore, expectedValue, actualValue));
            alert.setRecommendedAction("Investigate service behavior and performance patterns");
            alert.setResolved(false);
            
            tracingAlertRepository.save(alert);
            
            if (anomalyScore > 0.7) {
                alertingService.sendAlert("SPAN_ANOMALY", alert.getDescription(), 
                        Map.of("spanId", spanId, "traceId", traceId, "serviceName", serviceName, 
                               "anomalyType", anomalyType));
            }
            
            analyzeAnomalyPattern(spanId, traceId, serviceName, anomalyType, anomalyScore);
            
            logger.warn("Processed span anomaly: spanId={}, traceId={}, service={}, type={}, score={}", 
                    spanId, traceId, serviceName, anomalyType, anomalyScore);
            
        } catch (Exception e) {
            logger.error("Error processing span anomaly: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processServiceMapUpdate(JsonNode eventData) {
        try {
            String serviceName = eventData.path("serviceName").asText();
            String targetService = eventData.path("targetService").asText();
            String operationType = eventData.path("operationType").asText();
            double avgLatencyMs = eventData.path("avgLatencyMs").asDouble();
            double callRate = eventData.path("callRate").asDouble();
            double errorRate = eventData.path("errorRate").asDouble();
            
            updateServiceMapMetrics(serviceName, targetService, operationType, avgLatencyMs, callRate, errorRate);
            
            metricsService.recordServiceMapUpdate(serviceName, targetService, avgLatencyMs, callRate, errorRate);
            
            logger.debug("Processed service map update: {} -> {}, operation={}, latency={}ms, rate={}/s", 
                    serviceName, targetService, operationType, avgLatencyMs, callRate);
            
        } catch (Exception e) {
            logger.error("Error processing service map update: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processTraceCorrelation(JsonNode eventData) {
        try {
            String primaryTraceId = eventData.path("primaryTraceId").asText();
            String correlatedTraceId = eventData.path("correlatedTraceId").asText();
            String correlationType = eventData.path("correlationType").asText();
            double confidence = eventData.path("confidence").asDouble();
            String correlationReason = eventData.path("correlationReason").asText();
            
            if (confidence > 0.8) {
                metricsService.recordTraceCorrelation(primaryTraceId, correlatedTraceId, correlationType, confidence);
                
                analyzeCorrelatedTraces(primaryTraceId, correlatedTraceId, correlationType, correlationReason);
                
                logger.info("Processed trace correlation: {} <-> {} (type: {}, confidence: {}, reason: {})", 
                        primaryTraceId, correlatedTraceId, correlationType, confidence, correlationReason);
            }
            
        } catch (Exception e) {
            logger.error("Error processing trace correlation: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processBottleneckDetection(JsonNode eventData) {
        try {
            String serviceName = eventData.path("serviceName").asText();
            String operationName = eventData.path("operationName").asText();
            String bottleneckType = eventData.path("bottleneckType").asText();
            double impact = eventData.path("impact").asDouble();
            long affectedTraces = eventData.path("affectedTraces").asLong();
            double avgDelayMs = eventData.path("avgDelayMs").asDouble();
            
            TracingAlert alert = new TracingAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setServiceName(serviceName);
            alert.setAlertType("BOTTLENECK_DETECTION");
            alert.setSeverity(impact > 0.7 ? "HIGH" : "MEDIUM");
            alert.setDescription(String.format("Bottleneck detected: %s in %s.%s (impact: %.1f%%, traces affected: %d, avg delay: %.1fms)", 
                    bottleneckType, serviceName, operationName, impact * 100, affectedTraces, avgDelayMs));
            alert.setRecommendedAction("Optimize bottleneck operation and consider scaling");
            alert.setResolved(false);
            
            tracingAlertRepository.save(alert);
            
            alertingService.sendAlert("BOTTLENECK_DETECTION", alert.getDescription(), 
                    Map.of("serviceName", serviceName, "operationName", operationName, 
                           "bottleneckType", bottleneckType, "impact", String.valueOf(impact)));
            
            analyzeBottleneckSolution(serviceName, operationName, bottleneckType, impact, avgDelayMs);
            
            logger.warn("Processed bottleneck detection: service={}, operation={}, type={}, impact={}%", 
                    serviceName, operationName, bottleneckType, impact * 100);
            
        } catch (Exception e) {
            logger.error("Error processing bottleneck detection: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processCriticalPath(JsonNode eventData) {
        try {
            String traceId = eventData.path("traceId").asText();
            String pathId = eventData.path("pathId").asText();
            String startService = eventData.path("startService").asText();
            String endService = eventData.path("endService").asText();
            long totalLatencyMs = eventData.path("totalLatencyMs").asLong();
            int pathLength = eventData.path("pathLength").asInt();
            double criticalityScore = eventData.path("criticalityScore").asDouble();
            
            JsonNode pathServicesNode = eventData.path("pathServices");
            String pathServices = pathServicesNode.isMissingNode() ? "" : pathServicesNode.toString();
            
            metricsService.recordCriticalPath(traceId, pathId, startService, endService, 
                    totalLatencyMs, pathLength, criticalityScore);
            
            if (criticalityScore > 0.8 || totalLatencyMs > slowTraceThresholdMs) {
                generateCriticalPathAlert(traceId, pathId, startService, endService, 
                        totalLatencyMs, criticalityScore, pathServices);
            }
            
            analyzeCriticalPathOptimization(pathId, pathServices, totalLatencyMs, criticalityScore);
            
            logger.info("Processed critical path: traceId={}, path={} -> {}, latency={}ms, criticality={}", 
                    traceId, startService, endService, totalLatencyMs, criticalityScore);
            
        } catch (Exception e) {
            logger.error("Error processing critical path: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void performTracingAnalysis() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            logger.info("Starting scheduled tracing analysis");
            
            analyzeTracePatterns();
            detectTracingAnomalies();
            updateServiceMapMetrics();
            generateAggregatedMetrics();
            
        } catch (Exception e) {
            logger.error("Error in tracing analysis: {}", e.getMessage(), e);
        } finally {
            sample.stop(tracingAnalysisTimer);
        }
    }

    private void processIncompleteTraces() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(traceTimeoutMinutes);
        
        for (Map.Entry<String, TraceBuilder> entry : activeTraces.entrySet()) {
            String traceId = entry.getKey();
            TraceBuilder builder = entry.getValue();
            
            if (builder.getLastActivity().isBefore(cutoff)) {
                processTimedOutTrace(traceId, builder);
                activeTraces.remove(traceId);
            }
        }
    }

    private void analyzeCriticalPaths() {
        try {
            logger.info("Analyzing critical paths across all traces");
            
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime oneHourAgo = now.minusHours(1);
            
            List<Trace> recentTraces = traceRepository.findByTimestampBetween(oneHourAgo, now);
            
            for (Trace trace : recentTraces) {
                if (trace.getDurationMs() > slowTraceThresholdMs) {
                    analyzeCriticalPathForTrace(trace);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error analyzing critical paths: {}", e.getMessage(), e);
        }
    }

    private void cleanupOldData() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        
        try {
            int deletedTraces = traceRepository.deleteByTimestampBefore(cutoff);
            int deletedSpans = spanRepository.deleteByStartTimeBefore(cutoff);
            int deletedAnalysis = traceAnalysisRepository.deleteByTimestampBefore(cutoff);
            int deletedAlerts = tracingAlertRepository.deleteByTimestampBefore(cutoff);
            
            logger.info("Cleaned up old tracing data: {} traces, {} spans, {} analysis, {} alerts", 
                    deletedTraces, deletedSpans, deletedAnalysis, deletedAlerts);
            
        } catch (Exception e) {
            logger.error("Error cleaning up old tracing data: {}", e.getMessage(), e);
        }
    }

    private boolean shouldSample(String traceId) {
        return traceId.hashCode() % 100 < (samplingRate * 100);
    }

    private void performRealTimeTraceAnalysis(Trace trace) {
        if (trace.getDurationMs() > slowTraceThresholdMs) {
            generateSlowTraceAlert(trace);
        }
        
        if (trace.getErrorCount() > 0) {
            generateErrorTraceAlert(trace);
        }
        
        if (trace.getServiceCount() > 10) {
            generateComplexTraceAlert(trace);
        }
    }

    private boolean shouldTriggerSpanAnalysis(Span span) {
        return span.getDurationMs() > slowTraceThresholdMs / 10 || 
               "ERROR".equals(span.getStatus()) || 
               "TIMEOUT".equals(span.getStatus());
    }

    private void performRealTimeSpanAnalysis(Span span) {
        if ("ERROR".equals(span.getStatus())) {
            generateSpanErrorAlert(span);
        }
        
        if (span.getDurationMs() > slowTraceThresholdMs / 5) {
            generateSlowSpanAlert(span);
        }
    }

    private void updateTraceBuilder(Trace trace) {
        TraceBuilder builder = activeTraces.computeIfAbsent(trace.getTraceId(), 
                k -> new TraceBuilder(trace.getTraceId()));
        builder.updateFromTrace(trace);
    }

    private void updateTraceBuilderWithSpan(Span span) {
        TraceBuilder builder = activeTraces.get(span.getTraceId());
        if (builder != null) {
            builder.addSpan(span);
        }
    }

    private void updateSpanBuffer(String traceId, SpanData spanData) {
        spanBuffer.computeIfAbsent(traceId, k -> new ArrayList<>()).add(spanData);
        
        List<SpanData> spans = spanBuffer.get(traceId);
        if (spans.size() > 1000) {
            spans.subList(0, spans.size() - 1000).clear();
        }
    }

    private void updateTraceStatistics(TraceMetrics metrics) {
        String key = metrics.getServiceName() + ":" + metrics.getOperationName();
        TraceStatistics stats = traceStats.computeIfAbsent(key, k -> new TraceStatistics());
        stats.update(metrics);
    }

    private void updateServiceMapFromSpan(Span span) {
        if (span.getRemoteEndpoint() != null && !span.getRemoteEndpoint().isEmpty()) {
            updateServiceMapMetrics(span.getServiceName(), span.getRemoteEndpoint(), 
                    span.getOperationName(), span.getDurationMs(), 1.0, 
                    "ERROR".equals(span.getStatus()) ? 1.0 : 0.0);
        }
    }

    private void updateServiceMapMetrics(String serviceName, String targetService, String operationType, 
                                         double avgLatencyMs, double callRate, double errorRate) {
    }

    private void generatePerformanceAlert(TraceAnalysis analysis) {
        alertingService.sendAlert("TRACE_PERFORMANCE", 
                String.format("Poor trace performance: %s (score: %.2f, bottleneck: %s)", 
                        analysis.getTraceId(), analysis.getPerformanceScore(), analysis.getBottleneckService()),
                Map.of("traceId", analysis.getTraceId(), "score", String.valueOf(analysis.getPerformanceScore()), 
                       "bottleneck", analysis.getBottleneckService()));
    }

    private void generateSpanErrorAlert(SpanError spanError) {
        alertingService.sendAlert("SPAN_ERROR", 
                String.format("Span error: %s in %s.%s (%s: %s)", 
                        spanError.getSpanId(), spanError.getServiceName(), spanError.getOperationName(), 
                        spanError.getErrorType(), spanError.getErrorMessage()),
                Map.of("spanId", spanError.getSpanId(), "traceId", spanError.getTraceId(), 
                       "serviceName", spanError.getServiceName(), "errorType", spanError.getErrorType()));
    }

    private void generateSpanErrorAlert(Span span) {
        alertingService.sendAlert("SPAN_ERROR", 
                String.format("Span error: %s in %s.%s (status: %s)", 
                        span.getSpanId(), span.getServiceName(), span.getOperationName(), span.getStatus()),
                Map.of("spanId", span.getSpanId(), "traceId", span.getTraceId(), 
                       "serviceName", span.getServiceName(), "status", span.getStatus()));
    }

    private void generateSlowTraceAlert(Trace trace) {
        alertingService.sendAlert("SLOW_TRACE", 
                String.format("Slow trace: %s in %s.%s took %dms", 
                        trace.getTraceId(), trace.getServiceName(), trace.getOperationName(), trace.getDurationMs()),
                Map.of("traceId", trace.getTraceId(), "serviceName", trace.getServiceName(), 
                       "durationMs", String.valueOf(trace.getDurationMs())));
    }

    private void generateErrorTraceAlert(Trace trace) {
        alertingService.sendAlert("ERROR_TRACE", 
                String.format("Error trace: %s in %s.%s with %d errors", 
                        trace.getTraceId(), trace.getServiceName(), trace.getOperationName(), trace.getErrorCount()),
                Map.of("traceId", trace.getTraceId(), "serviceName", trace.getServiceName(), 
                       "errorCount", String.valueOf(trace.getErrorCount())));
    }

    private void generateComplexTraceAlert(Trace trace) {
        alertingService.sendAlert("COMPLEX_TRACE", 
                String.format("Complex trace: %s spans across %d services", 
                        trace.getTraceId(), trace.getServiceCount()),
                Map.of("traceId", trace.getTraceId(), "serviceCount", String.valueOf(trace.getServiceCount()), 
                       "spanCount", String.valueOf(trace.getSpanCount())));
    }

    private void generateSlowSpanAlert(Span span) {
        alertingService.sendAlert("SLOW_SPAN", 
                String.format("Slow span: %s in %s.%s took %dms", 
                        span.getSpanId(), span.getServiceName(), span.getOperationName(), span.getDurationMs()),
                Map.of("spanId", span.getSpanId(), "traceId", span.getTraceId(), 
                       "serviceName", span.getServiceName(), "durationMs", String.valueOf(span.getDurationMs())));
    }

    private void generateCriticalPathAlert(String traceId, String pathId, String startService, String endService, 
                                           long totalLatencyMs, double criticalityScore, String pathServices) {
        alertingService.sendAlert("CRITICAL_PATH", 
                String.format("Critical path alert: %s -> %s in trace %s (latency: %dms, criticality: %.2f)", 
                        startService, endService, traceId, totalLatencyMs, criticalityScore),
                Map.of("traceId", traceId, "pathId", pathId, "startService", startService, 
                       "endService", endService, "latencyMs", String.valueOf(totalLatencyMs)));
    }

    private void analyzeBottleneckImpact(TraceAnalysis analysis) {
    }

    private void analyzeSlowTracePattern(String traceId, String serviceName, String operationName, 
                                         long durationMs, String bottleneckSpan) {
    }

    private void analyzeErrorTraceCorrelation(String traceId, String serviceName, String operationName, 
                                              String errorPattern, String failedService) {
    }

    private void cleanupIncompleteTrace(String traceId) {
        activeTraces.remove(traceId);
        spanBuffer.remove(traceId);
        sampledTraces.remove(traceId);
    }

    private void analyzeErrorPattern(SpanError spanError) {
    }

    private void analyzeAnomalyPattern(String spanId, String traceId, String serviceName, 
                                       String anomalyType, double anomalyScore) {
    }

    private void analyzeCorrelatedTraces(String primaryTraceId, String correlatedTraceId, 
                                         String correlationType, String correlationReason) {
    }

    private void analyzeBottleneckSolution(String serviceName, String operationName, String bottleneckType, 
                                           double impact, double avgDelayMs) {
    }

    private void analyzeCriticalPathOptimization(String pathId, String pathServices, long totalLatencyMs, 
                                                 double criticalityScore) {
    }

    private void analyzeTracePatterns() {
    }

    private void detectTracingAnomalies() {
    }

    private void generateAggregatedMetrics() {
    }

    private void processTimedOutTrace(String traceId, TraceBuilder builder) {
        logger.warn("Processing timed out trace: {}, incomplete spans: {}", 
                traceId, builder.getIncompleteSpanCount());
        
        TracingAlert alert = new TracingAlert();
        alert.setTimestamp(LocalDateTime.now());
        alert.setTraceId(traceId);
        alert.setServiceName(builder.getRootService());
        alert.setAlertType("TRACE_TIMEOUT");
        alert.setSeverity("HIGH");
        alert.setDescription(String.format("Trace %s timed out with %d incomplete spans", 
                traceId, builder.getIncompleteSpanCount()));
        alert.setRecommendedAction("Check service health and network connectivity");
        alert.setResolved(false);
        
        tracingAlertRepository.save(alert);
    }

    private void analyzeCriticalPathForTrace(Trace trace) {
    }

    private double getActiveTracesCount() {
        return activeTraces.size();
    }

    private double getPendingSpansCount() {
        return spanBuffer.values().stream().mapToInt(List::size).sum();
    }

    private double getAvgTraceLatency() {
        return traceStats.values().stream()
                .mapToDouble(TraceStatistics::getAvgLatency)
                .filter(latency -> latency > 0)
                .average()
                .orElse(0.0);
    }

    private double getTraceErrorRate() {
        return traceStats.values().stream()
                .mapToDouble(TraceStatistics::getErrorRate)
                .filter(rate -> rate >= 0)
                .average()
                .orElse(0.0);
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        try {
            return LocalDateTime.parse(timestamp.replace("Z", ""));
        } catch (Exception e) {
            logger.warn("Failed to parse timestamp: {}, using current time", timestamp);
            return LocalDateTime.now();
        }
    }

    private static class SpanData {
        private final String spanId;
        private final String traceId;
        private final String serviceName;
        private final String operationName;
        private final LocalDateTime startTime;
        private final LocalDateTime endTime;
        private final long durationMs;
        private final String status;
        
        public SpanData(Span span) {
            this.spanId = span.getSpanId();
            this.traceId = span.getTraceId();
            this.serviceName = span.getServiceName();
            this.operationName = span.getOperationName();
            this.startTime = span.getStartTime();
            this.endTime = span.getEndTime();
            this.durationMs = span.getDurationMs();
            this.status = span.getStatus();
        }
        
        public String getSpanId() { return spanId; }
        public String getTraceId() { return traceId; }
        public String getServiceName() { return serviceName; }
        public String getOperationName() { return operationName; }
        public LocalDateTime getStartTime() { return startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public long getDurationMs() { return durationMs; }
        public String getStatus() { return status; }
    }

    private static class TraceBuilder {
        private final String traceId;
        private String rootService;
        private String rootOperation;
        private LocalDateTime startTime;
        private LocalDateTime lastActivity;
        private final List<Span> spans = new ArrayList<>();
        private final Set<String> services = new HashSet<>();
        private int errorCount = 0;
        private long totalDuration = 0;
        
        public TraceBuilder(String traceId) {
            this.traceId = traceId;
            this.lastActivity = LocalDateTime.now();
        }
        
        public void updateFromTrace(Trace trace) {
            this.rootService = trace.getServiceName();
            this.rootOperation = trace.getOperationName();
            this.totalDuration = trace.getDurationMs();
            this.errorCount = trace.getErrorCount();
            this.lastActivity = LocalDateTime.now();
        }
        
        public void addSpan(Span span) {
            spans.add(span);
            services.add(span.getServiceName());
            if ("ERROR".equals(span.getStatus())) {
                errorCount++;
            }
            this.lastActivity = LocalDateTime.now();
            
            if (startTime == null || span.getStartTime().isBefore(startTime)) {
                startTime = span.getStartTime();
            }
        }
        
        public String getTraceId() { return traceId; }
        public String getRootService() { return rootService; }
        public String getRootOperation() { return rootOperation; }
        public LocalDateTime getLastActivity() { return lastActivity; }
        public int getIncompleteSpanCount() { 
            return (int) spans.stream().filter(span -> span.getEndTime() == null).count(); 
        }
        public List<Span> getSpans() { return spans; }
        public Set<String> getServices() { return services; }
        public int getErrorCount() { return errorCount; }
        public long getTotalDuration() { return totalDuration; }
    }

    private static class TraceStatistics {
        private double avgLatency = 0.0;
        private double errorRate = 0.0;
        private long traceCount = 0;
        private double totalLatency = 0.0;
        private long errorCount = 0;
        
        public void update(TraceMetrics metrics) {
            traceCount += metrics.getTraceCount();
            totalLatency += metrics.getAvgDurationMs() * metrics.getTraceCount();
            errorCount += (long) (metrics.getErrorRate() * metrics.getTraceCount());
            
            if (traceCount > 0) {
                avgLatency = totalLatency / traceCount;
                errorRate = (double) errorCount / traceCount;
            }
        }
        
        public double getAvgLatency() { return avgLatency; }
        public double getErrorRate() { return errorRate; }
        public long getTraceCount() { return traceCount; }
    }
}