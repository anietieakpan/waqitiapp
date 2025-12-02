package com.waqiti.monitoring.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.monitoring.model.MetricType;
import com.waqiti.monitoring.model.AggregationInterval;
import com.waqiti.monitoring.model.MetricDataPoint;
import com.waqiti.monitoring.model.AggregatedMetric;
import com.waqiti.monitoring.service.MetricsStorageService;
import com.waqiti.monitoring.service.TimeSeriesService;
import com.waqiti.monitoring.service.AlertingService;
import com.waqiti.monitoring.service.MetricsAnalysisService;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.exception.ValidationException;
import com.waqiti.common.exception.SystemException;
import com.waqiti.common.kafka.KafkaMessage;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.DistributionSummary;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class MetricsAggregationConsumer {

    private static final Logger logger = LoggerFactory.getLogger(MetricsAggregationConsumer.class);
    private static final String CONSUMER_NAME = "metrics-aggregation-consumer";
    private static final String DLQ_TOPIC = "metrics-aggregation-dlq";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int PROCESSING_TIMEOUT_SECONDS = 10;

    private final ObjectMapper objectMapper;
    private final MetricsStorageService metricsStorageService;
    private final TimeSeriesService timeSeriesService;
    private final AlertingService alertingService;
    private final MetricsAnalysisService metricsAnalysisService;
    private final MetricsService metricsService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public MetricsAggregationConsumer(
            ObjectMapper objectMapper,
            MetricsStorageService metricsStorageService,
            TimeSeriesService timeSeriesService,
            AlertingService alertingService,
            MetricsAnalysisService metricsAnalysisService,
            MetricsService metricsService,
            KafkaTemplate<String, Object> kafkaTemplate,
            MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.metricsStorageService = metricsStorageService;
        this.timeSeriesService = timeSeriesService;
        this.alertingService = alertingService;
        this.metricsAnalysisService = metricsAnalysisService;
        this.metricsService = metricsService;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Value("${kafka.consumer.metrics-aggregation.enabled:true}")
    private boolean consumerEnabled;

    @Value("${metrics.aggregation.batch-size:100}")
    private int batchSize;

    @Value("${metrics.aggregation.flush-interval-seconds:30}")
    private int flushIntervalSeconds;

    @Value("${metrics.aggregation.enable-real-time-alerts:true}")
    private boolean enableRealTimeAlerts;

    @Value("${metrics.aggregation.retention-days:90}")
    private int retentionDays;

    @Value("${metrics.aggregation.enable-compression:true}")
    private boolean enableCompression;

    @Value("${metrics.aggregation.enable-downsampling:true}")
    private boolean enableDownsampling;

    @Value("${metrics.aggregation.parallel-processing-threads:4}")
    private int parallelProcessingThreads;

    private Counter processedCounter;
    private Counter errorCounter;
    private Counter dlqCounter;
    private Timer processingTimer;
    private DistributionSummary batchSizeDistribution;
    private Gauge queueSizeGauge;

    private final ConcurrentHashMap<String, List<MetricDataPoint>> metricBuffer = new ConcurrentHashMap<>();
    private final AtomicLong totalMetricsProcessed = new AtomicLong(0);
    private final AtomicReference<LocalDateTime> lastFlushTime = new AtomicReference<>(LocalDateTime.now());
    private ScheduledExecutorService scheduledExecutor;
    private ExecutorService processingExecutor;

    @PostConstruct
    public void initializeMetrics() {
        this.processedCounter = Counter.builder("metrics_aggregation_processed_total")
                .description("Total processed metrics aggregation events")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.errorCounter = Counter.builder("metrics_aggregation_errors_total")
                .description("Total metrics aggregation processing errors")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.dlqCounter = Counter.builder("metrics_aggregation_dlq_total")
                .description("Total metrics aggregation events sent to DLQ")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.processingTimer = Timer.builder("metrics_aggregation_processing_duration")
                .description("Metrics aggregation processing duration")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.batchSizeDistribution = DistributionSummary.builder("metrics_aggregation_batch_size")
                .description("Distribution of metrics batch sizes")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.queueSizeGauge = Gauge.builder("metrics_aggregation_queue_size", metricBuffer, map -> map.values().stream()
                .mapToInt(List::size).sum())
                .description("Current size of metrics buffer queue")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        // Initialize scheduled executor for periodic flush
        this.scheduledExecutor = Executors.newScheduledThreadPool(2);
        scheduledExecutor.scheduleWithFixedDelay(this::flushMetrics, 
                flushIntervalSeconds, flushIntervalSeconds, TimeUnit.SECONDS);

        // Initialize processing executor for parallel processing
        this.processingExecutor = Executors.newFixedThreadPool(parallelProcessingThreads);

        logger.info("MetricsAggregationConsumer initialized with batch size: {}, flush interval: {}s", 
                   batchSize, flushIntervalSeconds);
    }

    @KafkaListener(
        topics = "${kafka.topics.metrics-aggregation:metrics-aggregation}",
        groupId = "${kafka.consumer.group-id:monitoring-service-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "metrics-aggregation-circuit-breaker", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "metrics-aggregation-retry")
    public void processMetricsAggregation(
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
                logger.warn("Metrics aggregation consumer is disabled, skipping message processing");
                acknowledgment.acknowledge();
                return;
            }

            logger.debug("Processing metrics aggregation message: messageId={}, topic={}, partition={}, offset={}",
                    messageId, topic, partition, offset);

            if (!StringUtils.hasText(message)) {
                logger.warn("Received empty or null message, skipping processing");
                acknowledgment.acknowledge();
                return;
            }

            JsonNode messageNode = objectMapper.readTree(message);
            
            if (!isValidMetricsMessage(messageNode)) {
                logger.error("Invalid metrics message format: {}", message);
                sendToDlq(message, topic, "Invalid message format", null, correlationId, traceId);
                acknowledgment.acknowledge();
                return;
            }

            MetricEvent metricEvent = parseMetricEvent(messageNode);
            
            CompletableFuture<Void> processingFuture = CompletableFuture.runAsync(() -> {
                try {
                    processMetricEvent(metricEvent, correlationId, traceId);
                } catch (Exception e) {
                    logger.error("Error in async processing: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }, processingExecutor).orTimeout(PROCESSING_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            processingFuture.get();

            totalMetricsProcessed.incrementAndGet();
            processedCounter.increment();
            acknowledgment.acknowledge();

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse metrics message: messageId={}, error={}", messageId, e.getMessage());
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } catch (Exception e) {
            logger.error("Unexpected error processing metrics: messageId={}, error={}", messageId, e.getMessage(), e);
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }

    private boolean isValidMetricsMessage(JsonNode messageNode) {
        try {
            return messageNode != null &&
                   messageNode.has("metricName") && StringUtils.hasText(messageNode.get("metricName").asText()) &&
                   messageNode.has("value") &&
                   messageNode.has("timestamp") &&
                   messageNode.has("source");
        } catch (Exception e) {
            logger.error("Error validating metrics message: {}", e.getMessage());
            return false;
        }
    }

    private MetricEvent parseMetricEvent(JsonNode messageNode) {
        try {
            MetricEvent event = new MetricEvent();
            event.setMetricName(messageNode.get("metricName").asText());
            event.setValue(messageNode.get("value").asDouble());
            event.setTimestamp(LocalDateTime.parse(messageNode.get("timestamp").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            event.setSource(messageNode.get("source").asText());
            
            if (messageNode.has("metricType")) {
                event.setMetricType(MetricType.valueOf(messageNode.get("metricType").asText()));
            } else {
                event.setMetricType(determineMetricType(event.getMetricName()));
            }
            
            if (messageNode.has("unit")) {
                event.setUnit(messageNode.get("unit").asText());
            }
            
            if (messageNode.has("tags")) {
                JsonNode tagsNode = messageNode.get("tags");
                Map<String, String> tags = new HashMap<>();
                tagsNode.fields().forEachRemaining(entry -> {
                    tags.put(entry.getKey(), entry.getValue().asText());
                });
                event.setTags(tags);
            }
            
            if (messageNode.has("dimensions")) {
                JsonNode dimensionsNode = messageNode.get("dimensions");
                Map<String, Object> dimensions = new HashMap<>();
                dimensionsNode.fields().forEachRemaining(entry -> {
                    dimensions.put(entry.getKey(), entry.getValue().asText());
                });
                event.setDimensions(dimensions);
            }
            
            if (messageNode.has("aggregationType")) {
                event.setAggregationType(messageNode.get("aggregationType").asText());
            }
            
            if (messageNode.has("metadata")) {
                JsonNode metadataNode = messageNode.get("metadata");
                Map<String, Object> metadata = new HashMap<>();
                metadataNode.fields().forEachRemaining(entry -> {
                    metadata.put(entry.getKey(), entry.getValue().asText());
                });
                event.setMetadata(metadata);
            }

            return event;
        } catch (Exception e) {
            logger.error("Error parsing metric event: {}", e.getMessage(), e);
            throw new ValidationException("Invalid metric event format: " + e.getMessage());
        }
    }

    @Transactional
    private void processMetricEvent(MetricEvent metricEvent, String correlationId, String traceId) {
        try {
            // Create data point
            MetricDataPoint dataPoint = new MetricDataPoint();
            dataPoint.setMetricName(metricEvent.getMetricName());
            dataPoint.setValue(metricEvent.getValue());
            dataPoint.setTimestamp(metricEvent.getTimestamp());
            dataPoint.setSource(metricEvent.getSource());
            dataPoint.setTags(metricEvent.getTags());
            dataPoint.setDimensions(metricEvent.getDimensions());
            
            // Add to buffer for batch processing
            String bufferKey = generateBufferKey(metricEvent);
            metricBuffer.compute(bufferKey, (key, list) -> {
                if (list == null) {
                    list = new CopyOnWriteArrayList<>();
                }
                list.add(dataPoint);
                return list;
            });
            
            // Check if batch size reached
            List<MetricDataPoint> currentBatch = metricBuffer.get(bufferKey);
            if (currentBatch.size() >= batchSize) {
                processBatch(bufferKey, currentBatch);
            }
            
            // Real-time alerting
            if (enableRealTimeAlerts) {
                checkRealTimeAlerts(metricEvent);
            }
            
            // Update statistics
            updateMetricStatistics(metricEvent);
            
            logger.debug("Metric event buffered: {}, buffer size: {}", 
                       metricEvent.getMetricName(), currentBatch.size());
            
        } catch (Exception e) {
            logger.error("Error processing metric event: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processBatch(String bufferKey, List<MetricDataPoint> batch) {
        try {
            logger.info("Processing metric batch: key={}, size={}", bufferKey, batch.size());
            
            // Record batch size
            batchSizeDistribution.record(batch.size());
            
            // Perform aggregations
            Map<AggregationInterval, AggregatedMetric> aggregations = performAggregations(batch);
            
            // Store aggregated metrics
            for (Map.Entry<AggregationInterval, AggregatedMetric> entry : aggregations.entrySet()) {
                AggregationInterval interval = entry.getKey();
                AggregatedMetric aggregated = entry.getValue();
                
                // Store in time series database
                timeSeriesService.storeAggregatedMetric(aggregated, interval);
                
                // Apply downsampling if enabled
                if (enableDownsampling && shouldDownsample(interval, aggregated)) {
                    downsampleMetric(aggregated, interval);
                }
                
                // Apply compression if enabled
                if (enableCompression) {
                    compressMetricData(aggregated);
                }
            }
            
            // Clear processed batch from buffer
            metricBuffer.compute(bufferKey, (key, list) -> {
                if (list != null) {
                    list.removeAll(batch);
                    return list.isEmpty() ? null : list;
                }
                return null;
            });
            
            // Check for threshold breaches
            checkThresholdBreaches(aggregations);
            
            // Perform anomaly detection
            performAnomalyDetection(aggregations);
            
            logger.info("Batch processed successfully: key={}, aggregations={}", 
                       bufferKey, aggregations.size());
            
        } catch (Exception e) {
            logger.error("Error processing batch: key={}, error={}", bufferKey, e.getMessage(), e);
            throw new SystemException("Failed to process metric batch", e);
        }
    }

    private Map<AggregationInterval, AggregatedMetric> performAggregations(List<MetricDataPoint> batch) {
        Map<AggregationInterval, AggregatedMetric> aggregations = new HashMap<>();
        
        if (batch.isEmpty()) {
            return aggregations;
        }
        
        // Sort by timestamp
        batch.sort(Comparator.comparing(MetricDataPoint::getTimestamp));
        
        MetricDataPoint first = batch.get(0);
        String metricName = first.getMetricName();
        
        // Calculate statistics
        DoubleSummaryStatistics stats = batch.stream()
                .mapToDouble(MetricDataPoint::getValue)
                .summaryStatistics();
        
        // Create aggregations for different intervals
        for (AggregationInterval interval : AggregationInterval.values()) {
            AggregatedMetric aggregated = new AggregatedMetric();
            aggregated.setMetricName(metricName);
            aggregated.setInterval(interval);
            aggregated.setStartTime(getIntervalStartTime(first.getTimestamp(), interval));
            aggregated.setEndTime(getIntervalEndTime(batch.get(batch.size() - 1).getTimestamp(), interval));
            aggregated.setCount(stats.getCount());
            aggregated.setSum(stats.getSum());
            aggregated.setMin(stats.getMin());
            aggregated.setMax(stats.getMax());
            aggregated.setAverage(stats.getAverage());
            
            // Calculate percentiles
            List<Double> values = batch.stream()
                    .map(MetricDataPoint::getValue)
                    .sorted()
                    .collect(Collectors.toList());
            
            aggregated.setP50(calculatePercentile(values, 50));
            aggregated.setP90(calculatePercentile(values, 90));
            aggregated.setP95(calculatePercentile(values, 95));
            aggregated.setP99(calculatePercentile(values, 99));
            
            // Calculate standard deviation
            double mean = stats.getAverage();
            double variance = batch.stream()
                    .mapToDouble(dp -> Math.pow(dp.getValue() - mean, 2))
                    .average()
                    .orElse(0.0);
            aggregated.setStdDev(Math.sqrt(variance));
            
            // Store tags and dimensions from first data point
            aggregated.setTags(first.getTags());
            aggregated.setDimensions(first.getDimensions());
            
            aggregations.put(interval, aggregated);
        }
        
        return aggregations;
    }

    private double calculatePercentile(List<Double> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) {
            return 0.0;
        }
        
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, Math.min(index, sortedValues.size() - 1)));
    }

    private LocalDateTime getIntervalStartTime(LocalDateTime timestamp, AggregationInterval interval) {
        switch (interval) {
            case MINUTE:
                return timestamp.truncatedTo(ChronoUnit.MINUTES);
            case FIVE_MINUTES:
                int minute = timestamp.getMinute();
                int roundedMinute = (minute / 5) * 5;
                return timestamp.withMinute(roundedMinute).truncatedTo(ChronoUnit.MINUTES);
            case FIFTEEN_MINUTES:
                minute = timestamp.getMinute();
                roundedMinute = (minute / 15) * 15;
                return timestamp.withMinute(roundedMinute).truncatedTo(ChronoUnit.MINUTES);
            case HOUR:
                return timestamp.truncatedTo(ChronoUnit.HOURS);
            case DAY:
                return timestamp.truncatedTo(ChronoUnit.DAYS);
            case WEEK:
                return timestamp.truncatedTo(ChronoUnit.DAYS)
                        .minusDays(timestamp.getDayOfWeek().getValue() - 1);
            case MONTH:
                return timestamp.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
            default:
                return timestamp;
        }
    }

    private LocalDateTime getIntervalEndTime(LocalDateTime timestamp, AggregationInterval interval) {
        LocalDateTime startTime = getIntervalStartTime(timestamp, interval);
        switch (interval) {
            case MINUTE:
                return startTime.plusMinutes(1);
            case FIVE_MINUTES:
                return startTime.plusMinutes(5);
            case FIFTEEN_MINUTES:
                return startTime.plusMinutes(15);
            case HOUR:
                return startTime.plusHours(1);
            case DAY:
                return startTime.plusDays(1);
            case WEEK:
                return startTime.plusWeeks(1);
            case MONTH:
                return startTime.plusMonths(1);
            default:
                return timestamp;
        }
    }

    private void checkRealTimeAlerts(MetricEvent metricEvent) {
        try {
            // Check if metric exceeds configured thresholds
            List<AlertRule> alertRules = alertingService.getActiveRulesForMetric(metricEvent.getMetricName());
            
            for (AlertRule rule : alertRules) {
                if (evaluateAlertRule(rule, metricEvent)) {
                    triggerAlert(rule, metricEvent);
                }
            }
        } catch (Exception e) {
            logger.error("Error checking real-time alerts: {}", e.getMessage(), e);
        }
    }

    private boolean evaluateAlertRule(AlertRule rule, MetricEvent metricEvent) {
        double value = metricEvent.getValue();
        
        switch (rule.getOperator()) {
            case GREATER_THAN:
                return value > rule.getThreshold();
            case LESS_THAN:
                return value < rule.getThreshold();
            case EQUALS:
                return Math.abs(value - rule.getThreshold()) < 0.001;
            case NOT_EQUALS:
                return Math.abs(value - rule.getThreshold()) >= 0.001;
            case GREATER_THAN_OR_EQUALS:
                return value >= rule.getThreshold();
            case LESS_THAN_OR_EQUALS:
                return value <= rule.getThreshold();
            default:
                return false;
        }
    }

    private void triggerAlert(AlertRule rule, MetricEvent metricEvent) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertId", UUID.randomUUID().toString());
        alert.put("ruleName", rule.getName());
        alert.put("metricName", metricEvent.getMetricName());
        alert.put("value", metricEvent.getValue());
        alert.put("threshold", rule.getThreshold());
        alert.put("operator", rule.getOperator());
        alert.put("severity", rule.getSeverity());
        alert.put("timestamp", metricEvent.getTimestamp().toString());
        alert.put("source", metricEvent.getSource());
        alert.put("tags", metricEvent.getTags());
        
        kafkaTemplate.send("alert-triggers", alert);
        
        logger.warn("Alert triggered: rule={}, metric={}, value={}, threshold={}", 
                   rule.getName(), metricEvent.getMetricName(), metricEvent.getValue(), rule.getThreshold());
    }

    private void checkThresholdBreaches(Map<AggregationInterval, AggregatedMetric> aggregations) {
        for (AggregatedMetric aggregated : aggregations.values()) {
            // Check static thresholds
            List<ThresholdConfig> thresholds = metricsStorageService.getThresholdsForMetric(aggregated.getMetricName());
            
            for (ThresholdConfig threshold : thresholds) {
                boolean breached = false;
                String breachType = "";
                
                switch (threshold.getStatistic()) {
                    case "average":
                        if (aggregated.getAverage() > threshold.getValue()) {
                            breached = true;
                            breachType = "Average exceeded";
                        }
                        break;
                    case "max":
                        if (aggregated.getMax() > threshold.getValue()) {
                            breached = true;
                            breachType = "Maximum exceeded";
                        }
                        break;
                    case "min":
                        if (aggregated.getMin() < threshold.getValue()) {
                            breached = true;
                            breachType = "Minimum breached";
                        }
                        break;
                    case "p99":
                        if (aggregated.getP99() > threshold.getValue()) {
                            breached = true;
                            breachType = "P99 exceeded";
                        }
                        break;
                }
                
                if (breached) {
                    createThresholdBreachAlert(aggregated, threshold, breachType);
                }
            }
        }
    }

    private void createThresholdBreachAlert(AggregatedMetric aggregated, ThresholdConfig threshold, String breachType) {
        Map<String, Object> breachAlert = new HashMap<>();
        breachAlert.put("alertType", "THRESHOLD_BREACH");
        breachAlert.put("metricName", aggregated.getMetricName());
        breachAlert.put("breachType", breachType);
        breachAlert.put("threshold", threshold.getValue());
        breachAlert.put("actualValue", getStatisticValue(aggregated, threshold.getStatistic()));
        breachAlert.put("interval", aggregated.getInterval());
        breachAlert.put("timestamp", LocalDateTime.now().toString());
        breachAlert.put("severity", threshold.getSeverity());
        
        kafkaTemplate.send("alert-triggers", breachAlert);
        
        logger.warn("Threshold breach: metric={}, type={}, threshold={}, actual={}", 
                   aggregated.getMetricName(), breachType, threshold.getValue(), 
                   getStatisticValue(aggregated, threshold.getStatistic()));
    }

    private double getStatisticValue(AggregatedMetric aggregated, String statistic) {
        switch (statistic) {
            case "average":
                return aggregated.getAverage();
            case "max":
                return aggregated.getMax();
            case "min":
                return aggregated.getMin();
            case "p99":
                return aggregated.getP99();
            case "p95":
                return aggregated.getP95();
            case "p90":
                return aggregated.getP90();
            case "p50":
                return aggregated.getP50();
            default:
                return aggregated.getAverage();
        }
    }

    private void performAnomalyDetection(Map<AggregationInterval, AggregatedMetric> aggregations) {
        try {
            for (AggregatedMetric aggregated : aggregations.values()) {
                // Use historical data for anomaly detection
                List<AggregatedMetric> historicalData = timeSeriesService.getHistoricalData(
                    aggregated.getMetricName(), 
                    aggregated.getInterval(), 
                    30 // Last 30 intervals
                );
                
                if (historicalData.size() >= 10) { // Need minimum data for detection
                    boolean isAnomaly = metricsAnalysisService.detectAnomaly(aggregated, historicalData);
                    
                    if (isAnomaly) {
                        createAnomalyAlert(aggregated, historicalData);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error performing anomaly detection: {}", e.getMessage(), e);
        }
    }

    private void createAnomalyAlert(AggregatedMetric current, List<AggregatedMetric> historical) {
        // Calculate baseline statistics
        DoubleSummaryStatistics historicalStats = historical.stream()
                .mapToDouble(AggregatedMetric::getAverage)
                .summaryStatistics();
        
        double baseline = historicalStats.getAverage();
        double stdDev = Math.sqrt(historical.stream()
                .mapToDouble(m -> Math.pow(m.getAverage() - baseline, 2))
                .average()
                .orElse(0.0));
        
        double deviationScore = Math.abs(current.getAverage() - baseline) / stdDev;
        
        Map<String, Object> anomalyAlert = new HashMap<>();
        anomalyAlert.put("alertType", "ANOMALY_DETECTED");
        anomalyAlert.put("metricName", current.getMetricName());
        anomalyAlert.put("currentValue", current.getAverage());
        anomalyAlert.put("baseline", baseline);
        anomalyAlert.put("standardDeviation", stdDev);
        anomalyAlert.put("deviationScore", deviationScore);
        anomalyAlert.put("interval", current.getInterval());
        anomalyAlert.put("timestamp", LocalDateTime.now().toString());
        anomalyAlert.put("severity", deviationScore > 4 ? "CRITICAL" : deviationScore > 3 ? "HIGH" : "MEDIUM");
        
        kafkaTemplate.send("alert-triggers", anomalyAlert);
        
        logger.warn("Anomaly detected: metric={}, current={}, baseline={}, deviation={}", 
                   current.getMetricName(), current.getAverage(), baseline, deviationScore);
    }

    private boolean shouldDownsample(AggregationInterval interval, AggregatedMetric metric) {
        // Downsample older data to save storage
        long ageInDays = ChronoUnit.DAYS.between(metric.getStartTime(), LocalDateTime.now());
        
        switch (interval) {
            case MINUTE:
                return ageInDays > 7; // Downsample minute data older than 7 days
            case FIVE_MINUTES:
                return ageInDays > 30; // Downsample 5-minute data older than 30 days
            case FIFTEEN_MINUTES:
                return ageInDays > 90; // Downsample 15-minute data older than 90 days
            default:
                return false;
        }
    }

    private void downsampleMetric(AggregatedMetric metric, AggregationInterval currentInterval) {
        // Convert to coarser granularity
        AggregationInterval targetInterval = getNextInterval(currentInterval);
        if (targetInterval != null) {
            timeSeriesService.downsample(metric, currentInterval, targetInterval);
            logger.debug("Downsampled metric: {} from {} to {}", 
                       metric.getMetricName(), currentInterval, targetInterval);
        }
    }

    private AggregationInterval getNextInterval(AggregationInterval current) {
        switch (current) {
            case MINUTE:
                return AggregationInterval.FIVE_MINUTES;
            case FIVE_MINUTES:
                return AggregationInterval.FIFTEEN_MINUTES;
            case FIFTEEN_MINUTES:
                return AggregationInterval.HOUR;
            case HOUR:
                return AggregationInterval.DAY;
            case DAY:
                return AggregationInterval.WEEK;
            case WEEK:
                return AggregationInterval.MONTH;
            default:
                return null;
        }
    }

    private void compressMetricData(AggregatedMetric metric) {
        try {
            byte[] compressed = metricsStorageService.compress(metric);
            metricsStorageService.storeCompressed(metric.getMetricName(), compressed);
            logger.debug("Compressed metric data: {}, original size: {}, compressed size: {}", 
                       metric.getMetricName(), 
                       objectMapper.writeValueAsBytes(metric).length, 
                       compressed.length);
        } catch (Exception e) {
            logger.error("Error compressing metric data: {}", e.getMessage(), e);
        }
    }

    private void updateMetricStatistics(MetricEvent metricEvent) {
        // Update real-time statistics
        metricsService.recordMetricValue(metricEvent.getMetricName(), metricEvent.getValue());
        
        // Update metric metadata
        metricsStorageService.updateMetricMetadata(metricEvent.getMetricName(), metricEvent.getTags());
    }

    private String generateBufferKey(MetricEvent metricEvent) {
        StringBuilder key = new StringBuilder();
        key.append(metricEvent.getMetricName());
        key.append("_").append(metricEvent.getSource());
        
        if (metricEvent.getTags() != null && !metricEvent.getTags().isEmpty()) {
            key.append("_").append(metricEvent.getTags().hashCode());
        }
        
        return key.toString();
    }

    private MetricType determineMetricType(String metricName) {
        if (metricName.contains("count") || metricName.contains("total")) {
            return MetricType.COUNTER;
        } else if (metricName.contains("gauge") || metricName.contains("current")) {
            return MetricType.GAUGE;
        } else if (metricName.contains("duration") || metricName.contains("latency")) {
            return MetricType.TIMER;
        } else if (metricName.contains("rate") || metricName.contains("throughput")) {
            return MetricType.RATE;
        } else {
            return MetricType.GAUGE;
        }
    }

    private void flushMetrics() {
        try {
            LocalDateTime now = LocalDateTime.now();
            long secondsSinceLastFlush = ChronoUnit.SECONDS.between(lastFlushTime.get(), now);
            
            if (secondsSinceLastFlush >= flushIntervalSeconds) {
                logger.info("Flushing metrics buffer: {} entries", metricBuffer.size());
                
                for (Map.Entry<String, List<MetricDataPoint>> entry : metricBuffer.entrySet()) {
                    if (!entry.getValue().isEmpty()) {
                        processBatch(entry.getKey(), new ArrayList<>(entry.getValue()));
                    }
                }
                
                lastFlushTime.set(now);
                
                // Clean up old metrics beyond retention period
                cleanupOldMetrics();
            }
        } catch (Exception e) {
            logger.error("Error during metric flush: {}", e.getMessage(), e);
        }
    }

    private void cleanupOldMetrics() {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
            int deleted = metricsStorageService.deleteMetricsOlderThan(cutoffDate);
            
            if (deleted > 0) {
                logger.info("Cleaned up {} old metric records older than {}", deleted, cutoffDate);
            }
        } catch (Exception e) {
            logger.error("Error cleaning up old metrics: {}", e.getMessage(), e);
        }
    }

    private void handleProcessingError(String message, String topic, Exception error, 
                                     String correlationId, String traceId, Acknowledgment acknowledgment) {
        try {
            errorCounter.increment();
            logger.error("Error processing metrics aggregation message: {}", error.getMessage(), error);
            
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
        logger.error("Circuit breaker fallback triggered for metrics aggregation consumer: {}", ex.getMessage());
        
        errorCounter.increment();
        sendToDlq(message, topic, "Circuit breaker fallback: " + ex.getMessage(), ex, correlationId, traceId);
        acknowledgment.acknowledge();
    }

    public static class MetricEvent {
        private String metricName;
        private double value;
        private LocalDateTime timestamp;
        private String source;
        private MetricType metricType;
        private String unit;
        private Map<String, String> tags;
        private Map<String, Object> dimensions;
        private String aggregationType;
        private Map<String, Object> metadata;

        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }

        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public MetricType getMetricType() { return metricType; }
        public void setMetricType(MetricType metricType) { this.metricType = metricType; }

        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }

        public Map<String, String> getTags() { return tags; }
        public void setTags(Map<String, String> tags) { this.tags = tags; }

        public Map<String, Object> getDimensions() { return dimensions; }
        public void setDimensions(Map<String, Object> dimensions) { this.dimensions = dimensions; }

        public String getAggregationType() { return aggregationType; }
        public void setAggregationType(String aggregationType) { this.aggregationType = aggregationType; }

        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    public static class AlertRule {
        private String name;
        private String metricName;
        private ComparisonOperator operator;
        private double threshold;
        private String severity;
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }
        
        public ComparisonOperator getOperator() { return operator; }
        public void setOperator(ComparisonOperator operator) { this.operator = operator; }
        
        public double getThreshold() { return threshold; }
        public void setThreshold(double threshold) { this.threshold = threshold; }
        
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
    }

    public enum ComparisonOperator {
        GREATER_THAN,
        LESS_THAN,
        EQUALS,
        NOT_EQUALS,
        GREATER_THAN_OR_EQUALS,
        LESS_THAN_OR_EQUALS
    }

    public static class ThresholdConfig {
        private String metricName;
        private String statistic;
        private double value;
        private String severity;
        
        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }
        
        public String getStatistic() { return statistic; }
        public void setStatistic(String statistic) { this.statistic = statistic; }
        
        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }
        
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down MetricsAggregationConsumer");
        
        // Flush remaining metrics
        flushMetrics();
        
        // Shutdown executors
        scheduledExecutor.shutdown();
        processingExecutor.shutdown();
        
        try {
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            if (!processingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                processingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Error shutting down executors", e);
            scheduledExecutor.shutdownNow();
            processingExecutor.shutdownNow();
        }
        
        logger.info("MetricsAggregationConsumer shutdown complete. Total metrics processed: {}", 
                   totalMetricsProcessed.get());
    }
}