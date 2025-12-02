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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LogAggregationConsumer extends BaseKafkaConsumer {

    private static final Logger logger = LoggerFactory.getLogger(LogAggregationConsumer.class);
    
    @Value("${waqiti.monitoring.log.analysis-window-minutes:5}")
    private int analysisWindowMinutes;
    
    @Value("${waqiti.monitoring.log.error-threshold:10}")
    private int errorThreshold;
    
    @Value("${waqiti.monitoring.log.warning-threshold:50}")
    private int warningThreshold;
    
    @Value("${waqiti.monitoring.log.pattern-match-threshold:5}")
    private int patternMatchThreshold;
    
    @Value("${waqiti.monitoring.log.anomaly-threshold:3.0}")
    private double anomalyThreshold;
    
    @Value("${waqiti.monitoring.log.retention-days:30}")
    private int retentionDays;

    private final LogEntryRepository logEntryRepository;
    private final LogAggregationRepository logAggregationRepository;
    private final LogPatternRepository logPatternRepository;
    private final LogAlertRepository logAlertRepository;
    private final LogMetricsRepository logMetricsRepository;
    private final ErrorLogRepository errorLogRepository;
    private final LogSearchIndexRepository logSearchIndexRepository;
    private final AlertingService alertingService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(4);
    private final Map<String, List<LogEntryBuffer>> logBuffer = new ConcurrentHashMap<>();
    private final Map<String, Pattern> knownPatterns = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> patternCounts = new ConcurrentHashMap<>();
    private final Map<String, LogStatistics> serviceLogStats = new ConcurrentHashMap<>();

    private Counter processedEventsCounter;
    private Counter processedLogDataCounter;
    private Counter processedLogAggregationCounter;
    private Counter processedLogPatternCounter;
    private Counter processedLogAlertCounter;
    private Counter processedLogMetricsCounter;
    private Counter processedErrorLogCounter;
    private Counter processedLogSearchIndexCounter;
    private Counter processedLogAnomalyCounter;
    private Counter processedLogVolumeSpike;
    private Counter processedLogErrorSpike;
    private Counter processedLogPatternDiscoveryCounter;
    private Counter processedLogCorrelationCounter;
    private Counter processedLogTrendCounter;
    private Counter processedLogSamplingCounter;
    private Timer logProcessingTimer;
    private Timer logAnalysisTimer;
    
    private Gauge activeLogsPerSecondGauge;
    private Gauge errorLogsPerSecondGauge;
    private Gauge warningLogsPerSecondGauge;
    private Gauge uniquePatternsGauge;

    @PostConstruct
    public void init() {
        this.processedEventsCounter = Counter.builder("log_aggregation_events_processed")
                .description("Number of log aggregation events processed")
                .register(meterRegistry);
        
        this.processedLogDataCounter = Counter.builder("log_data_processed")
                .description("Number of log data events processed")
                .register(meterRegistry);
        
        this.processedLogAggregationCounter = Counter.builder("log_aggregation_processed")
                .description("Number of log aggregation events processed")
                .register(meterRegistry);
        
        this.processedLogPatternCounter = Counter.builder("log_pattern_processed")
                .description("Number of log pattern events processed")
                .register(meterRegistry);
        
        this.processedLogAlertCounter = Counter.builder("log_alert_processed")
                .description("Number of log alert events processed")
                .register(meterRegistry);
        
        this.processedLogMetricsCounter = Counter.builder("log_metrics_processed")
                .description("Number of log metrics events processed")
                .register(meterRegistry);
        
        this.processedErrorLogCounter = Counter.builder("error_log_processed")
                .description("Number of error log events processed")
                .register(meterRegistry);
        
        this.processedLogSearchIndexCounter = Counter.builder("log_search_index_processed")
                .description("Number of log search index events processed")
                .register(meterRegistry);
        
        this.processedLogAnomalyCounter = Counter.builder("log_anomaly_processed")
                .description("Number of log anomaly events processed")
                .register(meterRegistry);
        
        this.processedLogVolumeSpike = Counter.builder("log_volume_spike_processed")
                .description("Number of log volume spike events processed")
                .register(meterRegistry);
        
        this.processedLogErrorSpike = Counter.builder("log_error_spike_processed")
                .description("Number of log error spike events processed")
                .register(meterRegistry);
        
        this.processedLogPatternDiscoveryCounter = Counter.builder("log_pattern_discovery_processed")
                .description("Number of log pattern discovery events processed")
                .register(meterRegistry);
        
        this.processedLogCorrelationCounter = Counter.builder("log_correlation_processed")
                .description("Number of log correlation events processed")
                .register(meterRegistry);
        
        this.processedLogTrendCounter = Counter.builder("log_trend_processed")
                .description("Number of log trend events processed")
                .register(meterRegistry);
        
        this.processedLogSamplingCounter = Counter.builder("log_sampling_processed")
                .description("Number of log sampling events processed")
                .register(meterRegistry);
        
        this.logProcessingTimer = Timer.builder("log_processing_duration")
                .description("Time taken to process log events")
                .register(meterRegistry);
        
        this.logAnalysisTimer = Timer.builder("log_analysis_duration")
                .description("Time taken to perform log analysis")
                .register(meterRegistry);
        
        this.activeLogsPerSecondGauge = Gauge.builder("active_logs_per_second", this, LogAggregationConsumer::getActiveLogsPerSecond)
                .description("Number of active logs per second")
                .register(meterRegistry);
        
        this.errorLogsPerSecondGauge = Gauge.builder("error_logs_per_second", this, LogAggregationConsumer::getErrorLogsPerSecond)
                .description("Number of error logs per second")
                .register(meterRegistry);
        
        this.warningLogsPerSecondGauge = Gauge.builder("warning_logs_per_second", this, LogAggregationConsumer::getWarningLogsPerSecond)
                .description("Number of warning logs per second")
                .register(meterRegistry);
        
        this.uniquePatternsGauge = Gauge.builder("unique_log_patterns_count", this, LogAggregationConsumer::getUniquePatternsCount)
                .description("Number of unique log patterns detected")
                .register(meterRegistry);

        initializeKnownPatterns();

        scheduledExecutor.scheduleAtFixedRate(this::performLogAnalysis, 
                analysisWindowMinutes, analysisWindowMinutes, TimeUnit.MINUTES);
        
        scheduledExecutor.scheduleAtFixedRate(this::aggregateLogMetrics, 
                1, 1, TimeUnit.MINUTES);
        
        scheduledExecutor.scheduleAtFixedRate(this::discoverNewPatterns, 
                30, 30, TimeUnit.MINUTES);
        
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

    @KafkaListener(topics = "log-aggregation", groupId = "log-aggregation-group", 
                   containerFactory = "kafkaListenerContainerFactory")
    @CircuitBreaker(name = "log-aggregation-consumer")
    @Retry(name = "log-aggregation-consumer")
    @Transactional
    public void handleLogAggregationEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "log-aggregation");

        try {
            logger.info("Processing log aggregation event: partition={}, offset={}", 
                    record.partition(), record.offset());

            JsonNode eventData = objectMapper.readTree(record.value());
            String eventType = eventData.path("eventType").asText();

            switch (eventType) {
                case "LOG_DATA":
                    processLogData(eventData);
                    processedLogDataCounter.increment();
                    break;
                case "LOG_AGGREGATION":
                    processLogAggregation(eventData);
                    processedLogAggregationCounter.increment();
                    break;
                case "LOG_PATTERN":
                    processLogPattern(eventData);
                    processedLogPatternCounter.increment();
                    break;
                case "LOG_ALERT":
                    processLogAlert(eventData);
                    processedLogAlertCounter.increment();
                    break;
                case "LOG_METRICS":
                    processLogMetrics(eventData);
                    processedLogMetricsCounter.increment();
                    break;
                case "ERROR_LOG":
                    processErrorLog(eventData);
                    processedErrorLogCounter.increment();
                    break;
                case "LOG_SEARCH_INDEX":
                    processLogSearchIndex(eventData);
                    processedLogSearchIndexCounter.increment();
                    break;
                case "LOG_ANOMALY":
                    processLogAnomaly(eventData);
                    processedLogAnomalyCounter.increment();
                    break;
                case "LOG_VOLUME_SPIKE":
                    processLogVolumeSpike(eventData);
                    processedLogVolumeSpike.increment();
                    break;
                case "LOG_ERROR_SPIKE":
                    processLogErrorSpike(eventData);
                    processedLogErrorSpike.increment();
                    break;
                case "LOG_PATTERN_DISCOVERY":
                    processLogPatternDiscovery(eventData);
                    processedLogPatternDiscoveryCounter.increment();
                    break;
                case "LOG_CORRELATION":
                    processLogCorrelation(eventData);
                    processedLogCorrelationCounter.increment();
                    break;
                case "LOG_TREND":
                    processLogTrend(eventData);
                    processedLogTrendCounter.increment();
                    break;
                case "LOG_SAMPLING":
                    processLogSampling(eventData);
                    processedLogSamplingCounter.increment();
                    break;
                default:
                    logger.warn("Unknown log aggregation event type: {}", eventType);
            }

            processedEventsCounter.increment();
            ack.acknowledge();

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse log aggregation event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } catch (DataAccessException e) {
            logger.error("Database error processing log aggregation event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } catch (Exception e) {
            logger.error("Unexpected error processing log aggregation event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            sample.stop(logProcessingTimer);
            MDC.clear();
        }
    }

    private void processLogData(JsonNode eventData) {
        try {
            LogEntry logEntry = new LogEntry();
            logEntry.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            logEntry.setServiceName(eventData.path("serviceName").asText());
            logEntry.setLogLevel(eventData.path("logLevel").asText());
            logEntry.setMessage(eventData.path("message").asText());
            logEntry.setLogger(eventData.path("logger").asText());
            logEntry.setThread(eventData.path("thread").asText());
            logEntry.setHost(eventData.path("host").asText());
            logEntry.setTraceId(eventData.path("traceId").asText());
            logEntry.setSpanId(eventData.path("spanId").asText());
            logEntry.setUserId(eventData.path("userId").asText());
            logEntry.setSessionId(eventData.path("sessionId").asText());
            
            JsonNode contextNode = eventData.path("context");
            if (!contextNode.isMissingNode()) {
                logEntry.setContext(contextNode.toString());
            }
            
            JsonNode exceptionNode = eventData.path("exception");
            if (!exceptionNode.isMissingNode()) {
                logEntry.setException(exceptionNode.toString());
            }
            
            logEntryRepository.save(logEntry);
            
            String serviceKey = logEntry.getServiceName();
            updateLogBuffer(serviceKey, new LogEntryBuffer(logEntry));
            updateLogStatistics(logEntry);
            
            metricsService.recordLogEntry(logEntry.getServiceName(), logEntry.getLogLevel(), logEntry);
            
            if (shouldTriggerRealTimeAnalysis(logEntry)) {
                performRealTimeLogAnalysis(logEntry);
            }
            
            detectPatternMatch(logEntry);
            updateSearchIndex(logEntry);
            
            logger.debug("Processed log entry: service={}, level={}, message length={}", 
                    logEntry.getServiceName(), logEntry.getLogLevel(), logEntry.getMessage().length());
            
        } catch (Exception e) {
            logger.error("Error processing log data: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processLogAggregation(JsonNode eventData) {
        try {
            LogAggregation aggregation = new LogAggregation();
            aggregation.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            aggregation.setServiceName(eventData.path("serviceName").asText());
            aggregation.setLogLevel(eventData.path("logLevel").asText());
            aggregation.setTimeWindow(eventData.path("timeWindow").asText());
            aggregation.setLogCount(eventData.path("logCount").asLong());
            aggregation.setUniqueMessages(eventData.path("uniqueMessages").asInt());
            aggregation.setErrorCount(eventData.path("errorCount").asLong());
            aggregation.setWarningCount(eventData.path("warningCount").asLong());
            aggregation.setInfoCount(eventData.path("infoCount").asLong());
            aggregation.setDebugCount(eventData.path("debugCount").asLong());
            aggregation.setAverageMessageLength(eventData.path("averageMessageLength").asDouble());
            aggregation.setTopLoggers(eventData.path("topLoggers").asText());
            
            JsonNode patternsNode = eventData.path("patterns");
            if (!patternsNode.isMissingNode()) {
                aggregation.setPatterns(patternsNode.toString());
            }
            
            logAggregationRepository.save(aggregation);
            
            metricsService.recordLogAggregation(aggregation.getServiceName(), aggregation);
            
            if (aggregation.getErrorCount() > errorThreshold) {
                generateErrorCountAlert(aggregation);
            }
            
            if (aggregation.getLogCount() > getExpectedLogVolume(aggregation.getServiceName()) * 2) {
                generateVolumeAlert(aggregation);
            }
            
            logger.debug("Processed log aggregation: service={}, window={}, logs={}, errors={}", 
                    aggregation.getServiceName(), aggregation.getTimeWindow(), 
                    aggregation.getLogCount(), aggregation.getErrorCount());
            
        } catch (Exception e) {
            logger.error("Error processing log aggregation: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processLogPattern(JsonNode eventData) {
        try {
            LogPattern pattern = new LogPattern();
            pattern.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            pattern.setPatternId(eventData.path("patternId").asText());
            pattern.setServiceName(eventData.path("serviceName").asText());
            pattern.setPatternRegex(eventData.path("patternRegex").asText());
            pattern.setPatternDescription(eventData.path("patternDescription").asText());
            pattern.setSeverity(eventData.path("severity").asText());
            pattern.setOccurrenceCount(eventData.path("occurrenceCount").asLong());
            pattern.setFirstSeen(parseTimestamp(eventData.path("firstSeen").asText()));
            pattern.setLastSeen(parseTimestamp(eventData.path("lastSeen").asText()));
            pattern.setIsAnomaly(eventData.path("isAnomaly").asBoolean());
            pattern.setConfidenceScore(eventData.path("confidenceScore").asDouble());
            
            JsonNode examplesNode = eventData.path("examples");
            if (!examplesNode.isMissingNode()) {
                pattern.setExamples(examplesNode.toString());
            }
            
            logPatternRepository.save(pattern);
            
            if (!knownPatterns.containsKey(pattern.getPatternId())) {
                knownPatterns.put(pattern.getPatternId(), Pattern.compile(pattern.getPatternRegex()));
                patternCounts.put(pattern.getPatternId(), new AtomicLong(0));
            }
            
            metricsService.recordLogPattern(pattern.getServiceName(), pattern);
            
            if (pattern.isAnomaly() && pattern.getConfidenceScore() > 0.8) {
                generatePatternAnomalyAlert(pattern);
            }
            
            if ("HIGH".equals(pattern.getSeverity()) && pattern.getOccurrenceCount() > patternMatchThreshold) {
                generatePatternAlert(pattern);
            }
            
            logger.debug("Processed log pattern: id={}, service={}, regex={}, occurrences={}", 
                    pattern.getPatternId(), pattern.getServiceName(), 
                    pattern.getPatternRegex(), pattern.getOccurrenceCount());
            
        } catch (Exception e) {
            logger.error("Error processing log pattern: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processLogAlert(JsonNode eventData) {
        try {
            LogAlert alert = new LogAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setServiceName(eventData.path("serviceName").asText());
            alert.setAlertType(eventData.path("alertType").asText());
            alert.setSeverity(eventData.path("severity").asText());
            alert.setDescription(eventData.path("description").asText());
            alert.setLogPattern(eventData.path("logPattern").asText());
            alert.setOccurrenceCount(eventData.path("occurrenceCount").asLong());
            alert.setTimeWindow(eventData.path("timeWindow").asText());
            alert.setRecommendedAction(eventData.path("recommendedAction").asText());
            alert.setResolved(eventData.path("resolved").asBoolean());
            
            if (eventData.has("resolvedAt")) {
                alert.setResolvedAt(parseTimestamp(eventData.path("resolvedAt").asText()));
            }
            
            logAlertRepository.save(alert);
            
            if (!alert.isResolved() && ("HIGH".equals(alert.getSeverity()) || "CRITICAL".equals(alert.getSeverity()))) {
                alertingService.sendAlert("LOG_ALERT", alert.getDescription(), 
                        Map.of("serviceName", alert.getServiceName(), "alertType", alert.getAlertType(), 
                               "severity", alert.getSeverity()));
            }
            
            logger.info("Processed log alert: service={}, type={}, severity={}, occurrences={}", 
                    alert.getServiceName(), alert.getAlertType(), alert.getSeverity(), alert.getOccurrenceCount());
            
        } catch (Exception e) {
            logger.error("Error processing log alert: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processLogMetrics(JsonNode eventData) {
        try {
            LogMetrics metrics = new LogMetrics();
            metrics.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            metrics.setServiceName(eventData.path("serviceName").asText());
            metrics.setTimeWindow(eventData.path("timeWindow").asText());
            metrics.setTotalLogs(eventData.path("totalLogs").asLong());
            metrics.setLogsPerSecond(eventData.path("logsPerSecond").asDouble());
            metrics.setErrorRate(eventData.path("errorRate").asDouble());
            metrics.setWarningRate(eventData.path("warningRate").asDouble());
            metrics.setAverageLogSize(eventData.path("averageLogSize").asDouble());
            metrics.setUniqueLoggers(eventData.path("uniqueLoggers").asInt());
            metrics.setTopErrorPatterns(eventData.path("topErrorPatterns").asText());
            metrics.setLogVelocity(eventData.path("logVelocity").asDouble());
            metrics.setPatternDiversity(eventData.path("patternDiversity").asDouble());
            
            logMetricsRepository.save(metrics);
            
            metricsService.recordAggregatedLogMetrics(metrics.getServiceName(), metrics);
            
            updateServiceLogStatistics(metrics);
            
            logger.debug("Processed log metrics: service={}, window={}, total={}, rate={}/s", 
                    metrics.getServiceName(), metrics.getTimeWindow(), 
                    metrics.getTotalLogs(), metrics.getLogsPerSecond());
            
        } catch (Exception e) {
            logger.error("Error processing log metrics: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processErrorLog(JsonNode eventData) {
        try {
            ErrorLog errorLog = new ErrorLog();
            errorLog.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            errorLog.setServiceName(eventData.path("serviceName").asText());
            errorLog.setErrorType(eventData.path("errorType").asText());
            errorLog.setErrorMessage(eventData.path("errorMessage").asText());
            errorLog.setStackTrace(eventData.path("stackTrace").asText());
            errorLog.setLogger(eventData.path("logger").asText());
            errorLog.setTraceId(eventData.path("traceId").asText());
            errorLog.setUserId(eventData.path("userId").asText());
            errorLog.setErrorHash(eventData.path("errorHash").asText());
            errorLog.setOccurrenceCount(eventData.path("occurrenceCount").asLong());
            errorLog.setSeverity(eventData.path("severity").asText());
            
            JsonNode contextNode = eventData.path("context");
            if (!contextNode.isMissingNode()) {
                errorLog.setContext(contextNode.toString());
            }
            
            errorLogRepository.save(errorLog);
            
            metricsService.recordErrorLog(errorLog.getServiceName(), errorLog);
            
            if ("CRITICAL".equals(errorLog.getSeverity()) || errorLog.getOccurrenceCount() > errorThreshold) {
                generateErrorLogAlert(errorLog);
            }
            
            analyzeErrorPattern(errorLog);
            
            logger.warn("Processed error log: service={}, type={}, hash={}, occurrences={}", 
                    errorLog.getServiceName(), errorLog.getErrorType(), 
                    errorLog.getErrorHash(), errorLog.getOccurrenceCount());
            
        } catch (Exception e) {
            logger.error("Error processing error log: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processLogSearchIndex(JsonNode eventData) {
        try {
            LogSearchIndex searchIndex = new LogSearchIndex();
            searchIndex.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            searchIndex.setServiceName(eventData.path("serviceName").asText());
            searchIndex.setLogLevel(eventData.path("logLevel").asText());
            searchIndex.setKeywords(eventData.path("keywords").asText());
            searchIndex.setMessageHash(eventData.path("messageHash").asText());
            searchIndex.setFrequency(eventData.path("frequency").asLong());
            searchIndex.setFirstOccurrence(parseTimestamp(eventData.path("firstOccurrence").asText()));
            searchIndex.setLastOccurrence(parseTimestamp(eventData.path("lastOccurrence").asText()));
            searchIndex.setTags(eventData.path("tags").asText());
            
            JsonNode indexDataNode = eventData.path("indexData");
            if (!indexDataNode.isMissingNode()) {
                searchIndex.setIndexData(indexDataNode.toString());
            }
            
            logSearchIndexRepository.save(searchIndex);
            
            updateSearchIndexMetrics(searchIndex);
            
            logger.debug("Processed log search index: service={}, level={}, hash={}, frequency={}", 
                    searchIndex.getServiceName(), searchIndex.getLogLevel(), 
                    searchIndex.getMessageHash(), searchIndex.getFrequency());
            
        } catch (Exception e) {
            logger.error("Error processing log search index: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processLogAnomaly(JsonNode eventData) {
        try {
            String serviceName = eventData.path("serviceName").asText();
            String anomalyType = eventData.path("anomalyType").asText();
            double anomalyScore = eventData.path("anomalyScore").asDouble();
            String description = eventData.path("description").asText();
            String expectedPattern = eventData.path("expectedPattern").asText();
            String actualPattern = eventData.path("actualPattern").asText();
            
            LogAlert alert = new LogAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setServiceName(serviceName);
            alert.setAlertType("LOG_ANOMALY");
            alert.setSeverity(anomalyScore > 0.8 ? "HIGH" : "MEDIUM");
            alert.setDescription(String.format("Log anomaly detected: %s in %s (score: %.2f) - %s", 
                    anomalyType, serviceName, anomalyScore, description));
            alert.setLogPattern(String.format("Expected: %s, Actual: %s", expectedPattern, actualPattern));
            alert.setRecommendedAction("Investigate service behavior and log patterns");
            alert.setResolved(false);
            
            logAlertRepository.save(alert);
            
            if (anomalyScore > anomalyThreshold) {
                alertingService.sendAlert("LOG_ANOMALY", alert.getDescription(), 
                        Map.of("serviceName", serviceName, "anomalyType", anomalyType, 
                               "anomalyScore", String.valueOf(anomalyScore)));
            }
            
            analyzeAnomalyCorrelation(serviceName, anomalyType, anomalyScore, description);
            
            logger.warn("Processed log anomaly: service={}, type={}, score={}, description={}", 
                    serviceName, anomalyType, anomalyScore, description);
            
        } catch (Exception e) {
            logger.error("Error processing log anomaly: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processLogVolumeSpike(JsonNode eventData) {
        try {
            String serviceName = eventData.path("serviceName").asText();
            long currentVolume = eventData.path("currentVolume").asLong();
            long baselineVolume = eventData.path("baselineVolume").asLong();
            double spikeRatio = eventData.path("spikeRatio").asDouble();
            String timeWindow = eventData.path("timeWindow").asText();
            String cause = eventData.path("cause").asText();
            
            LogAlert alert = new LogAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setServiceName(serviceName);
            alert.setAlertType("LOG_VOLUME_SPIKE");
            alert.setSeverity(spikeRatio > 5.0 ? "HIGH" : "MEDIUM");
            alert.setDescription(String.format("Log volume spike: %s generating %d logs (%.1fx baseline) in %s - %s", 
                    serviceName, currentVolume, spikeRatio, timeWindow, cause));
            alert.setOccurrenceCount(currentVolume);
            alert.setTimeWindow(timeWindow);
            alert.setRecommendedAction("Check service health and log configuration");
            alert.setResolved(false);
            
            logAlertRepository.save(alert);
            
            alertingService.sendAlert("LOG_VOLUME_SPIKE", alert.getDescription(), 
                    Map.of("serviceName", serviceName, "currentVolume", String.valueOf(currentVolume), 
                           "spikeRatio", String.valueOf(spikeRatio)));
            
            investigateVolumeSpike(serviceName, currentVolume, baselineVolume, cause);
            
            logger.warn("Processed log volume spike: service={}, volume={}, ratio={}x, cause={}", 
                    serviceName, currentVolume, spikeRatio, cause);
            
        } catch (Exception e) {
            logger.error("Error processing log volume spike: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processLogErrorSpike(JsonNode eventData) {
        try {
            String serviceName = eventData.path("serviceName").asText();
            long errorCount = eventData.path("errorCount").asLong();
            double errorRate = eventData.path("errorRate").asDouble();
            String timeWindow = eventData.path("timeWindow").asText();
            String topErrorPattern = eventData.path("topErrorPattern").asText();
            String impactAssessment = eventData.path("impactAssessment").asText();
            
            LogAlert alert = new LogAlert();
            alert.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            alert.setServiceName(serviceName);
            alert.setAlertType("LOG_ERROR_SPIKE");
            alert.setSeverity(errorRate > 0.1 ? "HIGH" : "MEDIUM");
            alert.setDescription(String.format("Error log spike: %s with %d errors (%.1f%% rate) in %s", 
                    serviceName, errorCount, errorRate * 100, timeWindow));
            alert.setLogPattern(topErrorPattern);
            alert.setOccurrenceCount(errorCount);
            alert.setTimeWindow(timeWindow);
            alert.setRecommendedAction(String.format("Investigate error pattern: %s. Impact: %s", 
                    topErrorPattern, impactAssessment));
            alert.setResolved(false);
            
            logAlertRepository.save(alert);
            
            alertingService.sendAlert("LOG_ERROR_SPIKE", alert.getDescription(), 
                    Map.of("serviceName", serviceName, "errorCount", String.valueOf(errorCount), 
                           "errorRate", String.valueOf(errorRate)));
            
            analyzeErrorSpike(serviceName, errorCount, errorRate, topErrorPattern, impactAssessment);
            
            logger.error("Processed log error spike: service={}, errors={}, rate={}%, pattern={}", 
                    serviceName, errorCount, errorRate * 100, topErrorPattern);
            
        } catch (Exception e) {
            logger.error("Error processing log error spike: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processLogPatternDiscovery(JsonNode eventData) {
        try {
            String serviceName = eventData.path("serviceName").asText();
            String newPattern = eventData.path("newPattern").asText();
            String patternType = eventData.path("patternType").asText();
            double confidence = eventData.path("confidence").asDouble();
            long frequency = eventData.path("frequency").asLong();
            String sampleMessages = eventData.path("sampleMessages").asText();
            
            if (confidence > 0.7 && frequency > patternMatchThreshold) {
                String patternId = generatePatternId(serviceName, newPattern);
                
                LogPattern pattern = new LogPattern();
                pattern.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
                pattern.setPatternId(patternId);
                pattern.setServiceName(serviceName);
                pattern.setPatternRegex(newPattern);
                pattern.setPatternDescription(String.format("Auto-discovered %s pattern", patternType));
                pattern.setSeverity(determinePatternSeverity(patternType));
                pattern.setOccurrenceCount(frequency);
                pattern.setFirstSeen(parseTimestamp(eventData.path("timestamp").asText()));
                pattern.setLastSeen(parseTimestamp(eventData.path("timestamp").asText()));
                pattern.setIsAnomaly(false);
                pattern.setConfidenceScore(confidence);
                pattern.setExamples(sampleMessages);
                
                logPatternRepository.save(pattern);
                
                knownPatterns.put(patternId, Pattern.compile(newPattern));
                patternCounts.put(patternId, new AtomicLong(frequency));
                
                metricsService.recordPatternDiscovery(serviceName, patternType, confidence, frequency);
                
                logger.info("Discovered new log pattern: service={}, type={}, confidence={}, frequency={}", 
                        serviceName, patternType, confidence, frequency);
            }
            
        } catch (Exception e) {
            logger.error("Error processing log pattern discovery: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processLogCorrelation(JsonNode eventData) {
        try {
            String primaryService = eventData.path("primaryService").asText();
            String correlatedService = eventData.path("correlatedService").asText();
            String correlationType = eventData.path("correlationType").asText();
            double correlationStrength = eventData.path("correlationStrength").asDouble();
            String timeWindow = eventData.path("timeWindow").asText();
            String correlationReason = eventData.path("correlationReason").asText();
            
            if (correlationStrength > 0.8) {
                metricsService.recordLogCorrelation(primaryService, correlatedService, 
                        correlationType, correlationStrength);
                
                analyzeCorrelatedLogs(primaryService, correlatedService, correlationType, 
                        correlationStrength, correlationReason);
                
                logger.info("Processed log correlation: {} <-> {} (type: {}, strength: {}, reason: {})", 
                        primaryService, correlatedService, correlationType, correlationStrength, correlationReason);
            }
            
        } catch (Exception e) {
            logger.error("Error processing log correlation: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processLogTrend(JsonNode eventData) {
        try {
            String serviceName = eventData.path("serviceName").asText();
            String trendType = eventData.path("trendType").asText();
            String trendDirection = eventData.path("trendDirection").asText();
            double trendMagnitude = eventData.path("trendMagnitude").asDouble();
            double confidence = eventData.path("confidence").asDouble();
            String timeWindow = eventData.path("timeWindow").asText();
            
            metricsService.recordLogTrend(serviceName, trendType, trendDirection, trendMagnitude, confidence);
            
            if ("INCREASING".equals(trendDirection) && "ERROR".equals(trendType) && confidence > 0.8) {
                generateTrendAlert(serviceName, trendType, trendDirection, trendMagnitude, timeWindow);
            }
            
            logger.info("Processed log trend: service={}, type={}, direction={}, magnitude={}, confidence={}", 
                    serviceName, trendType, trendDirection, trendMagnitude, confidence);
            
        } catch (Exception e) {
            logger.error("Error processing log trend: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processLogSampling(JsonNode eventData) {
        try {
            String serviceName = eventData.path("serviceName").asText();
            String samplingStrategy = eventData.path("samplingStrategy").asText();
            double samplingRate = eventData.path("samplingRate").asDouble();
            long totalLogs = eventData.path("totalLogs").asLong();
            long sampledLogs = eventData.path("sampledLogs").asLong();
            String criteria = eventData.path("criteria").asText();
            
            metricsService.recordLogSampling(serviceName, samplingStrategy, samplingRate, 
                    totalLogs, sampledLogs);
            
            if (samplingRate < 0.1) {
                logger.warn("Low sampling rate for service {}: {}% (strategy: {}, criteria: {})", 
                        serviceName, samplingRate * 100, samplingStrategy, criteria);
            }
            
            logger.debug("Processed log sampling: service={}, strategy={}, rate={}%, sampled={}/{}", 
                    serviceName, samplingStrategy, samplingRate * 100, sampledLogs, totalLogs);
            
        } catch (Exception e) {
            logger.error("Error processing log sampling: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void performLogAnalysis() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            logger.info("Starting scheduled log analysis");
            
            analyzeLogPatterns();
            detectLogAnomalies();
            analyzeLogVolumeTrends();
            correlateLogEvents();
            
        } catch (Exception e) {
            logger.error("Error in log analysis: {}", e.getMessage(), e);
        } finally {
            sample.stop(logAnalysisTimer);
        }
    }

    private void aggregateLogMetrics() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneMinuteAgo = now.minusMinutes(1);
        
        for (Map.Entry<String, List<LogEntryBuffer>> entry : logBuffer.entrySet()) {
            String serviceName = entry.getKey();
            List<LogEntryBuffer> logs = entry.getValue();
            
            List<LogEntryBuffer> recentLogs = logs.stream()
                    .filter(log -> log.getTimestamp().isAfter(oneMinuteAgo))
                    .collect(Collectors.toList());
            
            if (!recentLogs.isEmpty()) {
                generateLogMetrics(serviceName, recentLogs, "1_MINUTE");
            }
        }
    }

    private void discoverNewPatterns() {
        try {
            logger.info("Starting pattern discovery analysis");
            
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime thirtyMinutesAgo = now.minusMinutes(30);
            
            for (String serviceName : logBuffer.keySet()) {
                List<LogEntryBuffer> recentLogs = logBuffer.get(serviceName).stream()
                        .filter(log -> log.getTimestamp().isAfter(thirtyMinutesAgo))
                        .collect(Collectors.toList());
                
                if (recentLogs.size() > 50) {
                    analyzeForNewPatterns(serviceName, recentLogs);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error in pattern discovery: {}", e.getMessage(), e);
        }
    }

    private void cleanupOldData() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        
        try {
            int deletedLogs = logEntryRepository.deleteByTimestampBefore(cutoff);
            int deletedAggregations = logAggregationRepository.deleteByTimestampBefore(cutoff);
            int deletedAlerts = logAlertRepository.deleteByTimestampBefore(cutoff);
            int deletedMetrics = logMetricsRepository.deleteByTimestampBefore(cutoff);
            
            logger.info("Cleaned up old log data: {} logs, {} aggregations, {} alerts, {} metrics", 
                    deletedLogs, deletedAggregations, deletedAlerts, deletedMetrics);
            
            // Cleanup in-memory buffers
            for (List<LogEntryBuffer> buffer : logBuffer.values()) {
                buffer.removeIf(log -> log.getTimestamp().isBefore(cutoff));
            }
            
        } catch (Exception e) {
            logger.error("Error cleaning up old log data: {}", e.getMessage(), e);
        }
    }

    private void initializeKnownPatterns() {
        // Common error patterns
        knownPatterns.put("java.exception", Pattern.compile(".*Exception.*"));
        knownPatterns.put("connection.timeout", Pattern.compile(".*[Tt]imeout.*"));
        knownPatterns.put("out.of.memory", Pattern.compile(".*OutOfMemoryError.*"));
        knownPatterns.put("null.pointer", Pattern.compile(".*NullPointerException.*"));
        knownPatterns.put("sql.exception", Pattern.compile(".*SQLException.*"));
        knownPatterns.put("authentication.failed", Pattern.compile(".*[Aa]uthentication.*[Ff]ailed.*"));
        knownPatterns.put("access.denied", Pattern.compile(".*[Aa]ccess.*[Dd]enied.*"));
        knownPatterns.put("service.unavailable", Pattern.compile(".*[Ss]ervice.*[Uu]navailable.*"));
        
        // Initialize counters
        for (String patternId : knownPatterns.keySet()) {
            patternCounts.put(patternId, new AtomicLong(0));
        }
    }

    private boolean shouldTriggerRealTimeAnalysis(LogEntry logEntry) {
        return "ERROR".equals(logEntry.getLogLevel()) || 
               "FATAL".equals(logEntry.getLogLevel()) ||
               logEntry.getException() != null;
    }

    private void performRealTimeLogAnalysis(LogEntry logEntry) {
        if ("ERROR".equals(logEntry.getLogLevel()) || "FATAL".equals(logEntry.getLogLevel())) {
            generateRealTimeErrorAlert(logEntry);
        }
        
        if (logEntry.getException() != null) {
            analyzeException(logEntry);
        }
    }

    private void detectPatternMatch(LogEntry logEntry) {
        for (Map.Entry<String, Pattern> entry : knownPatterns.entrySet()) {
            String patternId = entry.getKey();
            Pattern pattern = entry.getValue();
            
            if (pattern.matcher(logEntry.getMessage()).matches()) {
                long count = patternCounts.get(patternId).incrementAndGet();
                
                if (count % patternMatchThreshold == 0) {
                    generatePatternMatchAlert(logEntry, patternId, count);
                }
            }
        }
    }

    private void updateSearchIndex(LogEntry logEntry) {
        String messageHash = generateMessageHash(logEntry.getMessage());
        
        LogSearchIndex searchIndex = new LogSearchIndex();
        searchIndex.setTimestamp(logEntry.getTimestamp());
        searchIndex.setServiceName(logEntry.getServiceName());
        searchIndex.setLogLevel(logEntry.getLogLevel());
        searchIndex.setKeywords(extractKeywords(logEntry.getMessage()));
        searchIndex.setMessageHash(messageHash);
        searchIndex.setFrequency(1L);
        searchIndex.setFirstOccurrence(logEntry.getTimestamp());
        searchIndex.setLastOccurrence(logEntry.getTimestamp());
        searchIndex.setTags(generateTags(logEntry));
        
        // This would normally be optimized to batch updates
        logSearchIndexRepository.save(searchIndex);
    }

    private void updateLogBuffer(String serviceKey, LogEntryBuffer logEntry) {
        logBuffer.computeIfAbsent(serviceKey, k -> new ArrayList<>()).add(logEntry);
        
        List<LogEntryBuffer> buffer = logBuffer.get(serviceKey);
        if (buffer.size() > 10000) {
            buffer.subList(0, buffer.size() - 10000).clear();
        }
    }

    private void updateLogStatistics(LogEntry logEntry) {
        LogStatistics stats = serviceLogStats.computeIfAbsent(logEntry.getServiceName(), 
                k -> new LogStatistics());
        stats.update(logEntry);
    }

    private void updateServiceLogStatistics(LogMetrics metrics) {
        LogStatistics stats = serviceLogStats.computeIfAbsent(metrics.getServiceName(), 
                k -> new LogStatistics());
        stats.updateFromMetrics(metrics);
    }

    private void updateSearchIndexMetrics(LogSearchIndex searchIndex) {
        metricsService.recordSearchIndexUpdate(searchIndex.getServiceName(), 
                searchIndex.getLogLevel(), searchIndex.getFrequency());
    }

    private long getExpectedLogVolume(String serviceName) {
        LogStatistics stats = serviceLogStats.get(serviceName);
        return stats != null ? (long) stats.getAvgLogsPerMinute() : 100L;
    }

    private void generateErrorCountAlert(LogAggregation aggregation) {
        alertingService.sendAlert("LOG_ERROR_COUNT", 
                String.format("High error count: %s has %d errors in %s", 
                        aggregation.getServiceName(), aggregation.getErrorCount(), aggregation.getTimeWindow()),
                Map.of("serviceName", aggregation.getServiceName(), "errorCount", String.valueOf(aggregation.getErrorCount()), 
                       "timeWindow", aggregation.getTimeWindow()));
    }

    private void generateVolumeAlert(LogAggregation aggregation) {
        alertingService.sendAlert("LOG_VOLUME_HIGH", 
                String.format("High log volume: %s generated %d logs in %s", 
                        aggregation.getServiceName(), aggregation.getLogCount(), aggregation.getTimeWindow()),
                Map.of("serviceName", aggregation.getServiceName(), "logCount", String.valueOf(aggregation.getLogCount()), 
                       "timeWindow", aggregation.getTimeWindow()));
    }

    private void generatePatternAnomalyAlert(LogPattern pattern) {
        alertingService.sendAlert("LOG_PATTERN_ANOMALY", 
                String.format("Anomalous log pattern: %s in %s (confidence: %.2f, occurrences: %d)", 
                        pattern.getPatternDescription(), pattern.getServiceName(), 
                        pattern.getConfidenceScore(), pattern.getOccurrenceCount()),
                Map.of("patternId", pattern.getPatternId(), "serviceName", pattern.getServiceName(), 
                       "confidence", String.valueOf(pattern.getConfidenceScore())));
    }

    private void generatePatternAlert(LogPattern pattern) {
        alertingService.sendAlert("LOG_PATTERN_MATCH", 
                String.format("High severity pattern: %s in %s (%d occurrences)", 
                        pattern.getPatternDescription(), pattern.getServiceName(), pattern.getOccurrenceCount()),
                Map.of("patternId", pattern.getPatternId(), "serviceName", pattern.getServiceName(), 
                       "severity", pattern.getSeverity()));
    }

    private void generateErrorLogAlert(ErrorLog errorLog) {
        alertingService.sendAlert("ERROR_LOG", 
                String.format("Error log alert: %s in %s - %s (%d occurrences)", 
                        errorLog.getErrorType(), errorLog.getServiceName(), 
                        errorLog.getErrorMessage(), errorLog.getOccurrenceCount()),
                Map.of("serviceName", errorLog.getServiceName(), "errorType", errorLog.getErrorType(), 
                       "errorHash", errorLog.getErrorHash(), "severity", errorLog.getSeverity()));
    }

    private void generateRealTimeErrorAlert(LogEntry logEntry) {
        alertingService.sendAlert("REAL_TIME_ERROR", 
                String.format("Real-time error: %s in %s - %s", 
                        logEntry.getLogLevel(), logEntry.getServiceName(), logEntry.getMessage()),
                Map.of("serviceName", logEntry.getServiceName(), "logLevel", logEntry.getLogLevel(), 
                       "logger", logEntry.getLogger()));
    }

    private void generatePatternMatchAlert(LogEntry logEntry, String patternId, long count) {
        alertingService.sendAlert("PATTERN_MATCH", 
                String.format("Pattern match threshold reached: %s in %s (count: %d)", 
                        patternId, logEntry.getServiceName(), count),
                Map.of("patternId", patternId, "serviceName", logEntry.getServiceName(), 
                       "count", String.valueOf(count)));
    }

    private void generateTrendAlert(String serviceName, String trendType, String trendDirection, 
                                   double trendMagnitude, String timeWindow) {
        alertingService.sendAlert("LOG_TREND", 
                String.format("Log trend alert: %s %s trend in %s (magnitude: %.2f) over %s", 
                        trendType, trendDirection, serviceName, trendMagnitude, timeWindow),
                Map.of("serviceName", serviceName, "trendType", trendType, 
                       "trendDirection", trendDirection, "magnitude", String.valueOf(trendMagnitude)));
    }

    private void generateLogMetrics(String serviceName, List<LogEntryBuffer> logs, String timeWindow) {
        long totalLogs = logs.size();
        long errorCount = logs.stream().mapToLong(log -> 
                "ERROR".equals(log.getLogLevel()) || "FATAL".equals(log.getLogLevel()) ? 1 : 0).sum();
        long warningCount = logs.stream().mapToLong(log -> "WARN".equals(log.getLogLevel()) ? 1 : 0).sum();
        
        double errorRate = totalLogs > 0 ? (double) errorCount / totalLogs : 0.0;
        double warningRate = totalLogs > 0 ? (double) warningCount / totalLogs : 0.0;
        
        LogMetrics metrics = new LogMetrics();
        metrics.setTimestamp(LocalDateTime.now());
        metrics.setServiceName(serviceName);
        metrics.setTimeWindow(timeWindow);
        metrics.setTotalLogs(totalLogs);
        metrics.setLogsPerSecond(totalLogs / 60.0); // 1 minute window
        metrics.setErrorRate(errorRate);
        metrics.setWarningRate(warningRate);
        
        logMetricsRepository.save(metrics);
    }

    private void analyzeException(LogEntry logEntry) {
        if (logEntry.getException() != null) {
            try {
                JsonNode exceptionNode = objectMapper.readTree(logEntry.getException());
                String exceptionType = exceptionNode.path("type").asText();
                String exceptionMessage = exceptionNode.path("message").asText();
                
                metricsService.recordException(logEntry.getServiceName(), exceptionType, exceptionMessage);
                
            } catch (Exception e) {
                logger.warn("Failed to parse exception data: {}", e.getMessage());
            }
        }
    }

    private void analyzeErrorPattern(ErrorLog errorLog) {
    }

    private void analyzeAnomalyCorrelation(String serviceName, String anomalyType, double anomalyScore, String description) {
    }

    private void investigateVolumeSpike(String serviceName, long currentVolume, long baselineVolume, String cause) {
    }

    private void analyzeErrorSpike(String serviceName, long errorCount, double errorRate, 
                                   String topErrorPattern, String impactAssessment) {
    }

    private void analyzeCorrelatedLogs(String primaryService, String correlatedService, String correlationType, 
                                       double correlationStrength, String correlationReason) {
    }

    private void analyzeLogPatterns() {
    }

    private void detectLogAnomalies() {
    }

    private void analyzeLogVolumeTrends() {
    }

    private void correlateLogEvents() {
    }

    private void analyzeForNewPatterns(String serviceName, List<LogEntryBuffer> logs) {
    }

    private String generatePatternId(String serviceName, String pattern) {
        return serviceName + "." + pattern.hashCode();
    }

    private String determinePatternSeverity(String patternType) {
        return switch (patternType.toLowerCase()) {
            case "error", "exception", "fatal" -> "HIGH";
            case "warning", "timeout" -> "MEDIUM";
            default -> "LOW";
        };
    }

    private String generateMessageHash(String message) {
        return String.valueOf(message.hashCode());
    }

    private String extractKeywords(String message) {
        // Simple keyword extraction - would be more sophisticated in practice
        return Arrays.stream(message.split("\\s+"))
                .filter(word -> word.length() > 3)
                .limit(10)
                .collect(Collectors.joining(","));
    }

    private String generateTags(LogEntry logEntry) {
        List<String> tags = new ArrayList<>();
        tags.add("service:" + logEntry.getServiceName());
        tags.add("level:" + logEntry.getLogLevel());
        if (logEntry.getTraceId() != null && !logEntry.getTraceId().isEmpty()) {
            tags.add("traced");
        }
        if (logEntry.getException() != null) {
            tags.add("exception");
        }
        return String.join(",", tags);
    }

    private double getActiveLogsPerSecond() {
        return serviceLogStats.values().stream()
                .mapToDouble(LogStatistics::getLogsPerSecond)
                .sum();
    }

    private double getErrorLogsPerSecond() {
        return serviceLogStats.values().stream()
                .mapToDouble(LogStatistics::getErrorLogsPerSecond)
                .sum();
    }

    private double getWarningLogsPerSecond() {
        return serviceLogStats.values().stream()
                .mapToDouble(LogStatistics::getWarningLogsPerSecond)
                .sum();
    }

    private double getUniquePatternsCount() {
        return knownPatterns.size();
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        try {
            return LocalDateTime.parse(timestamp.replace("Z", ""));
        } catch (Exception e) {
            logger.warn("Failed to parse timestamp: {}, using current time", timestamp);
            return LocalDateTime.now();
        }
    }

    private static class LogEntryBuffer {
        private final LocalDateTime timestamp;
        private final String serviceName;
        private final String logLevel;
        private final String message;
        private final String logger;
        
        public LogEntryBuffer(LogEntry logEntry) {
            this.timestamp = logEntry.getTimestamp();
            this.serviceName = logEntry.getServiceName();
            this.logLevel = logEntry.getLogLevel();
            this.message = logEntry.getMessage();
            this.logger = logEntry.getLogger();
        }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getServiceName() { return serviceName; }
        public String getLogLevel() { return logLevel; }
        public String getMessage() { return message; }
        public String getLogger() { return logger; }
    }

    private static class LogStatistics {
        private long totalLogs = 0;
        private long errorLogs = 0;
        private long warningLogs = 0;
        private double avgLogsPerMinute = 0.0;
        private double logsPerSecond = 0.0;
        private double errorLogsPerSecond = 0.0;
        private double warningLogsPerSecond = 0.0;
        private LocalDateTime lastUpdate = LocalDateTime.now();
        
        public void update(LogEntry logEntry) {
            totalLogs++;
            if ("ERROR".equals(logEntry.getLogLevel()) || "FATAL".equals(logEntry.getLogLevel())) {
                errorLogs++;
            }
            if ("WARN".equals(logEntry.getLogLevel())) {
                warningLogs++;
            }
            updateRates();
        }
        
        public void updateFromMetrics(LogMetrics metrics) {
            this.logsPerSecond = metrics.getLogsPerSecond();
            this.errorLogsPerSecond = metrics.getLogsPerSecond() * metrics.getErrorRate();
            this.warningLogsPerSecond = metrics.getLogsPerSecond() * metrics.getWarningRate();
            this.avgLogsPerMinute = metrics.getLogsPerSecond() * 60;
            this.lastUpdate = LocalDateTime.now();
        }
        
        private void updateRates() {
            LocalDateTime now = LocalDateTime.now();
            Duration duration = Duration.between(lastUpdate, now);
            if (duration.getSeconds() > 0) {
                double seconds = duration.getSeconds();
                logsPerSecond = totalLogs / seconds;
                errorLogsPerSecond = errorLogs / seconds;
                warningLogsPerSecond = warningLogs / seconds;
                avgLogsPerMinute = logsPerSecond * 60;
            }
        }
        
        public double getAvgLogsPerMinute() { return avgLogsPerMinute; }
        public double getLogsPerSecond() { return logsPerSecond; }
        public double getErrorLogsPerSecond() { return errorLogsPerSecond; }
        public double getWarningLogsPerSecond() { return warningLogsPerSecond; }
    }
}