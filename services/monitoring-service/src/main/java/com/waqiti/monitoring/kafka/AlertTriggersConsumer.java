package com.waqiti.monitoring.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.monitoring.model.Alert;
import com.waqiti.monitoring.model.AlertSeverity;
import com.waqiti.monitoring.model.AlertStatus;
import com.waqiti.monitoring.model.AlertRule;
import com.waqiti.monitoring.service.AlertingService;
import com.waqiti.monitoring.service.AlertCorrelationService;
import com.waqiti.monitoring.service.AlertEscalationService;
import com.waqiti.monitoring.service.NotificationService;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class AlertTriggersConsumer {

    private static final Logger logger = LoggerFactory.getLogger(AlertTriggersConsumer.class);
    private static final String CONSUMER_NAME = "alert-triggers-consumer";
    private static final String DLQ_TOPIC = "alert-triggers-dlq";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int PROCESSING_TIMEOUT_SECONDS = 10;

    private final ObjectMapper objectMapper;
    private final AlertingService alertingService;
    private final AlertCorrelationService alertCorrelationService;
    private final AlertEscalationService alertEscalationService;
    private final NotificationService notificationService;
    private final MetricsService metricsService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${kafka.consumer.alert-triggers.enabled:true}")
    private boolean consumerEnabled;

    @Value("${alerts.deduplication.window-minutes:5}")
    private int deduplicationWindowMinutes;

    @Value("${alerts.suppression.enabled:true}")
    private boolean suppressionEnabled;

    @Value("${alerts.correlation.enabled:true}")
    private boolean correlationEnabled;

    @Value("${alerts.correlation.window-minutes:10}")
    private int correlationWindowMinutes;

    @Value("${alerts.escalation.enabled:true}")
    private boolean escalationEnabled;

    @Value("${alerts.storm.threshold:100}")
    private int alertStormThreshold;

    @Value("${alerts.storm.window-seconds:60}")
    private int alertStormWindowSeconds;

    @Value("${alerts.retention.days:30}")
    private int alertRetentionDays;

    private Counter processedCounter;
    private Counter errorCounter;
    private Counter dlqCounter;
    private Timer processingTimer;
    private Counter suppressedCounter;
    private Counter correlatedCounter;
    private Counter escalatedCounter;
    private Gauge activeAlertsGauge;

    private final ConcurrentHashMap<String, Alert> activeAlerts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> deduplicationCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Alert>> correlationWindow = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> alertFrequency = new ConcurrentHashMap<>();
    private final AtomicLong totalAlertsProcessed = new AtomicLong(0);
    private ScheduledExecutorService scheduledExecutor;
    private ExecutorService alertProcessingExecutor;

    public AlertTriggersConsumer(ObjectMapper objectMapper,
                                 AlertingService alertingService,
                                 AlertCorrelationService alertCorrelationService,
                                 AlertEscalationService alertEscalationService,
                                 NotificationService notificationService,
                                 MetricsService metricsService,
                                 KafkaTemplate<String, Object> kafkaTemplate,
                                 MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.alertingService = alertingService;
        this.alertCorrelationService = alertCorrelationService;
        this.alertEscalationService = alertEscalationService;
        this.notificationService = notificationService;
        this.metricsService = metricsService;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void initializeMetrics() {
        this.processedCounter = Counter.builder("alert_triggers_processed_total")
                .description("Total processed alert trigger events")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.errorCounter = Counter.builder("alert_triggers_errors_total")
                .description("Total alert trigger processing errors")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.dlqCounter = Counter.builder("alert_triggers_dlq_total")
                .description("Total alert trigger events sent to DLQ")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.processingTimer = Timer.builder("alert_triggers_processing_duration")
                .description("Alert trigger processing duration")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.suppressedCounter = Counter.builder("alert_triggers_suppressed_total")
                .description("Total suppressed alerts")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.correlatedCounter = Counter.builder("alert_triggers_correlated_total")
                .description("Total correlated alerts")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.escalatedCounter = Counter.builder("alert_triggers_escalated_total")
                .description("Total escalated alerts")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.activeAlertsGauge = Gauge.builder("alert_triggers_active_count", activeAlerts, Map::size)
                .description("Number of active alerts")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.scheduledExecutor = Executors.newScheduledThreadPool(4);
        scheduledExecutor.scheduleWithFixedDelay(this::cleanupDeduplicationCache, 
                deduplicationWindowMinutes, deduplicationWindowMinutes, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::processCorrelations, 
                1, 1, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::checkEscalations, 
                30, 30, TimeUnit.SECONDS);
        scheduledExecutor.scheduleWithFixedDelay(this::cleanupOldAlerts, 
                1, 1, TimeUnit.HOURS);

        this.alertProcessingExecutor = Executors.newFixedThreadPool(6);

        logger.info("AlertTriggersConsumer initialized with deduplication window: {} minutes", 
                   deduplicationWindowMinutes);
    }

    @KafkaListener(
        topics = "${kafka.topics.alert-triggers:alert-triggers}",
        groupId = "${kafka.consumer.group-id:monitoring-service-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "alert-triggers-circuit-breaker", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "alert-triggers-retry")
    public void processAlertTrigger(
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
                logger.warn("Alert triggers consumer is disabled, skipping message processing");
                acknowledgment.acknowledge();
                return;
            }

            logger.debug("Processing alert trigger message: messageId={}, topic={}, partition={}, offset={}",
                    messageId, topic, partition, offset);

            if (!StringUtils.hasText(message)) {
                logger.warn("Received empty or null message, skipping processing");
                acknowledgment.acknowledge();
                return;
            }

            JsonNode messageNode = objectMapper.readTree(message);
            
            if (!isValidAlertMessage(messageNode)) {
                logger.error("Invalid alert message format: {}", message);
                sendToDlq(message, topic, "Invalid message format", null, correlationId, traceId);
                acknowledgment.acknowledge();
                return;
            }

            String eventType = messageNode.get("eventType").asText();
            
            CompletableFuture<Void> processingFuture = CompletableFuture.runAsync(() -> {
                try {
                    switch (eventType) {
                        case "THRESHOLD_ALERT":
                            handleThresholdAlert(messageNode, correlationId, traceId);
                            break;
                        case "ANOMALY_ALERT":
                            handleAnomalyAlert(messageNode, correlationId, traceId);
                            break;
                        case "ERROR_RATE_ALERT":
                            handleErrorRateAlert(messageNode, correlationId, traceId);
                            break;
                        case "LATENCY_ALERT":
                            handleLatencyAlert(messageNode, correlationId, traceId);
                            break;
                        case "AVAILABILITY_ALERT":
                            handleAvailabilityAlert(messageNode, correlationId, traceId);
                            break;
                        case "CAPACITY_ALERT":
                            handleCapacityAlert(messageNode, correlationId, traceId);
                            break;
                        case "SECURITY_ALERT":
                            handleSecurityAlert(messageNode, correlationId, traceId);
                            break;
                        case "COMPLIANCE_ALERT":
                            handleComplianceAlert(messageNode, correlationId, traceId);
                            break;
                        case "BUSINESS_ALERT":
                            handleBusinessAlert(messageNode, correlationId, traceId);
                            break;
                        case "CUSTOM_ALERT":
                            handleCustomAlert(messageNode, correlationId, traceId);
                            break;
                        case "ALERT_RESOLUTION":
                            handleAlertResolution(messageNode, correlationId, traceId);
                            break;
                        case "ALERT_ACKNOWLEDGMENT":
                            handleAlertAcknowledgment(messageNode, correlationId, traceId);
                            break;
                        case "ALERT_ESCALATION":
                            handleAlertEscalation(messageNode, correlationId, traceId);
                            break;
                        case "ALERT_SUPPRESSION":
                            handleAlertSuppression(messageNode, correlationId, traceId);
                            break;
                        case "ALERT_UPDATE":
                            handleAlertUpdate(messageNode, correlationId, traceId);
                            break;
                        default:
                            logger.warn("Unknown alert event type: {}", eventType);
                    }
                } catch (Exception e) {
                    logger.error("Error processing alert event: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }, alertProcessingExecutor).orTimeout(PROCESSING_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            processingFuture.get();

            totalAlertsProcessed.incrementAndGet();
            processedCounter.increment();
            acknowledgment.acknowledge();

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse alert message: messageId={}, error={}", messageId, e.getMessage());
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } catch (Exception e) {
            logger.error("Unexpected error processing alert: messageId={}, error={}", messageId, e.getMessage(), e);
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }

    @Transactional
    private void handleThresholdAlert(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String alertName = messageNode.get("alertName").asText();
            String metricName = messageNode.get("metricName").asText();
            double currentValue = messageNode.get("currentValue").asDouble();
            double threshold = messageNode.get("threshold").asDouble();
            String condition = messageNode.get("condition").asText();
            String severity = messageNode.get("severity").asText();
            
            Alert alert = new Alert();
            alert.setAlertId(UUID.randomUUID().toString());
            alert.setAlertName(alertName);
            alert.setAlertType("THRESHOLD");
            alert.setMetricName(metricName);
            alert.setCurrentValue(currentValue);
            alert.setThreshold(threshold);
            alert.setCondition(condition);
            alert.setSeverity(AlertSeverity.valueOf(severity));
            alert.setStatus(AlertStatus.OPEN);
            alert.setTimestamp(LocalDateTime.now());
            
            if (messageNode.has("source")) {
                alert.setSource(messageNode.get("source").asText());
            }
            
            if (messageNode.has("tags")) {
                Map<String, String> tags = objectMapper.convertValue(
                    messageNode.get("tags"), Map.class);
                alert.setTags(tags);
            }
            
            if (isDuplicate(alert)) {
                suppressedCounter.increment();
                logger.debug("Duplicate threshold alert suppressed: {}", alertName);
                return;
            }
            
            if (isAlertStorm()) {
                handleAlertStorm();
                return;
            }
            
            processAlert(alert);
            
            if (correlationEnabled) {
                correlateAlert(alert);
            }
            
            if (escalationEnabled && shouldEscalate(alert)) {
                escalateAlert(alert);
            }
            
            notifyRecipients(alert);
            
            logger.info("Threshold alert triggered: name={}, metric={}, value={}, threshold={}, severity={}", 
                       alertName, metricName, currentValue, threshold, severity);
            
        } catch (Exception e) {
            logger.error("Error handling threshold alert: {}", e.getMessage(), e);
            throw new SystemException("Failed to process threshold alert", e);
        }
    }

    @Transactional
    private void handleAnomalyAlert(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String alertName = messageNode.get("alertName").asText();
            String metricName = messageNode.get("metricName").asText();
            double anomalyScore = messageNode.get("anomalyScore").asDouble();
            double baseline = messageNode.get("baseline").asDouble();
            double currentValue = messageNode.get("currentValue").asDouble();
            String anomalyType = messageNode.get("anomalyType").asText();
            
            Alert alert = new Alert();
            alert.setAlertId(UUID.randomUUID().toString());
            alert.setAlertName(alertName);
            alert.setAlertType("ANOMALY");
            alert.setMetricName(metricName);
            alert.setAnomalyScore(anomalyScore);
            alert.setBaseline(baseline);
            alert.setCurrentValue(currentValue);
            alert.setAnomalyType(anomalyType);
            alert.setStatus(AlertStatus.OPEN);
            alert.setTimestamp(LocalDateTime.now());
            
            AlertSeverity severity = determineAnomalySeverity(anomalyScore);
            alert.setSeverity(severity);
            
            if (messageNode.has("confidence")) {
                alert.setConfidence(messageNode.get("confidence").asDouble());
            }
            
            if (messageNode.has("historicalContext")) {
                JsonNode context = messageNode.get("historicalContext");
                Map<String, Object> historicalData = new HashMap<>();
                context.fields().forEachRemaining(entry -> {
                    historicalData.put(entry.getKey(), entry.getValue().asText());
                });
                alert.setHistoricalContext(historicalData);
            }
            
            if (suppressionEnabled && shouldSuppressAnomaly(alert)) {
                suppressedCounter.increment();
                logger.debug("Anomaly alert suppressed due to low confidence: {}", alertName);
                return;
            }
            
            processAlert(alert);
            
            analyzeAnomalyPattern(alert);
            
            if (correlationEnabled) {
                findRelatedAnomalies(alert);
            }
            
            notifyRecipients(alert);
            
            logger.info("Anomaly alert triggered: name={}, metric={}, score={}, type={}, severity={}", 
                       alertName, metricName, anomalyScore, anomalyType, severity);
            
        } catch (Exception e) {
            logger.error("Error handling anomaly alert: {}", e.getMessage(), e);
            throw new SystemException("Failed to process anomaly alert", e);
        }
    }

    @Transactional
    private void handleErrorRateAlert(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String serviceName = messageNode.get("serviceName").asText();
            String endpoint = messageNode.get("endpoint").asText();
            double errorRate = messageNode.get("errorRate").asDouble();
            int errorCount = messageNode.get("errorCount").asInt();
            int totalRequests = messageNode.get("totalRequests").asInt();
            String timeWindow = messageNode.get("timeWindow").asText();
            
            Alert alert = new Alert();
            alert.setAlertId(UUID.randomUUID().toString());
            alert.setAlertName("Error Rate Alert - " + serviceName);
            alert.setAlertType("ERROR_RATE");
            alert.setServiceName(serviceName);
            alert.setEndpoint(endpoint);
            alert.setErrorRate(errorRate);
            alert.setErrorCount(errorCount);
            alert.setTotalRequests(totalRequests);
            alert.setTimeWindow(timeWindow);
            alert.setStatus(AlertStatus.OPEN);
            alert.setTimestamp(LocalDateTime.now());
            
            AlertSeverity severity = errorRate > 0.1 ? AlertSeverity.CRITICAL :
                                   errorRate > 0.05 ? AlertSeverity.HIGH :
                                   errorRate > 0.01 ? AlertSeverity.MEDIUM : AlertSeverity.LOW;
            alert.setSeverity(severity);
            
            if (messageNode.has("errorTypes")) {
                JsonNode errorTypes = messageNode.get("errorTypes");
                Map<String, Integer> errorBreakdown = new HashMap<>();
                errorTypes.fields().forEachRemaining(entry -> {
                    errorBreakdown.put(entry.getKey(), entry.getValue().asInt());
                });
                alert.setErrorBreakdown(errorBreakdown);
                
                identifyTopErrors(alert, errorBreakdown);
            }
            
            processAlert(alert);
            
            if (errorRate > 0.05) {
                triggerErrorAnalysis(serviceName, endpoint, errorRate);
            }
            
            if (shouldTriggerCircuitBreaker(errorRate, totalRequests)) {
                recommendCircuitBreaker(serviceName, endpoint);
            }
            
            notifyRecipients(alert);
            
            logger.warn("Error rate alert: service={}, endpoint={}, rate={}%, errors={}/{}", 
                       serviceName, endpoint, errorRate * 100, errorCount, totalRequests);
            
        } catch (Exception e) {
            logger.error("Error handling error rate alert: {}", e.getMessage(), e);
            throw new SystemException("Failed to process error rate alert", e);
        }
    }

    @Transactional
    private void handleLatencyAlert(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String serviceName = messageNode.get("serviceName").asText();
            String endpoint = messageNode.get("endpoint").asText();
            double p50Latency = messageNode.get("p50Latency").asDouble();
            double p95Latency = messageNode.get("p95Latency").asDouble();
            double p99Latency = messageNode.get("p99Latency").asDouble();
            double avgLatency = messageNode.get("avgLatency").asDouble();
            String timeWindow = messageNode.get("timeWindow").asText();
            
            Alert alert = new Alert();
            alert.setAlertId(UUID.randomUUID().toString());
            alert.setAlertName("Latency Alert - " + serviceName);
            alert.setAlertType("LATENCY");
            alert.setServiceName(serviceName);
            alert.setEndpoint(endpoint);
            alert.setP50Latency(p50Latency);
            alert.setP95Latency(p95Latency);
            alert.setP99Latency(p99Latency);
            alert.setAvgLatency(avgLatency);
            alert.setTimeWindow(timeWindow);
            alert.setStatus(AlertStatus.OPEN);
            alert.setTimestamp(LocalDateTime.now());
            
            AlertSeverity severity = p99Latency > 5000 ? AlertSeverity.CRITICAL :
                                   p99Latency > 2000 ? AlertSeverity.HIGH :
                                   p99Latency > 1000 ? AlertSeverity.MEDIUM : AlertSeverity.LOW;
            alert.setSeverity(severity);
            
            if (messageNode.has("slowestOperations")) {
                JsonNode operations = messageNode.get("slowestOperations");
                List<String> slowOps = new ArrayList<>();
                operations.forEach(op -> slowOps.add(op.asText()));
                alert.setSlowestOperations(slowOps);
            }
            
            processAlert(alert);
            
            analyzeLatencyPattern(alert);
            
            if (p99Latency > 2000) {
                initiatePerformanceInvestigation(serviceName, endpoint, p99Latency);
            }
            
            notifyRecipients(alert);
            
            logger.warn("Latency alert: service={}, endpoint={}, p50={}ms, p95={}ms, p99={}ms", 
                       serviceName, endpoint, p50Latency, p95Latency, p99Latency);
            
        } catch (Exception e) {
            logger.error("Error handling latency alert: {}", e.getMessage(), e);
            throw new SystemException("Failed to process latency alert", e);
        }
    }

    @Transactional
    private void handleAvailabilityAlert(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String serviceName = messageNode.get("serviceName").asText();
            double availability = messageNode.get("availability").asDouble();
            int downtime = messageNode.get("downtimeMinutes").asInt();
            String checkPeriod = messageNode.get("checkPeriod").asText();
            boolean isDown = messageNode.get("isDown").asBoolean();
            
            Alert alert = new Alert();
            alert.setAlertId(UUID.randomUUID().toString());
            alert.setAlertName("Availability Alert - " + serviceName);
            alert.setAlertType("AVAILABILITY");
            alert.setServiceName(serviceName);
            alert.setAvailability(availability);
            alert.setDowntimeMinutes(downtime);
            alert.setCheckPeriod(checkPeriod);
            alert.setIsDown(isDown);
            alert.setStatus(AlertStatus.OPEN);
            alert.setTimestamp(LocalDateTime.now());
            
            AlertSeverity severity = isDown ? AlertSeverity.CRITICAL :
                                   availability < 0.95 ? AlertSeverity.HIGH :
                                   availability < 0.99 ? AlertSeverity.MEDIUM : AlertSeverity.LOW;
            alert.setSeverity(severity);
            
            if (messageNode.has("failedHealthChecks")) {
                JsonNode checks = messageNode.get("failedHealthChecks");
                List<String> failedChecks = new ArrayList<>();
                checks.forEach(check -> failedChecks.add(check.asText()));
                alert.setFailedHealthChecks(failedChecks);
            }
            
            processAlert(alert);
            
            if (isDown) {
                initiateFailoverProcedure(serviceName);
                createIncident(alert);
            }
            
            calculateSLAImpact(serviceName, availability, downtime);
            
            notifyRecipients(alert);
            
            logger.error("Availability alert: service={}, availability={}%, downtime={} minutes, isDown={}", 
                        serviceName, availability * 100, downtime, isDown);
            
        } catch (Exception e) {
            logger.error("Error handling availability alert: {}", e.getMessage(), e);
            throw new SystemException("Failed to process availability alert", e);
        }
    }

    @Transactional
    private void handleCapacityAlert(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String resourceType = messageNode.get("resourceType").asText();
            String resourceId = messageNode.get("resourceId").asText();
            double currentUtilization = messageNode.get("currentUtilization").asDouble();
            double threshold = messageNode.get("threshold").asDouble();
            String projectedTimeToLimit = messageNode.get("projectedTimeToLimit").asText();
            
            Alert alert = new Alert();
            alert.setAlertId(UUID.randomUUID().toString());
            alert.setAlertName("Capacity Alert - " + resourceType);
            alert.setAlertType("CAPACITY");
            alert.setResourceType(resourceType);
            alert.setResourceId(resourceId);
            alert.setCurrentUtilization(currentUtilization);
            alert.setThreshold(threshold);
            alert.setProjectedTimeToLimit(projectedTimeToLimit);
            alert.setStatus(AlertStatus.OPEN);
            alert.setTimestamp(LocalDateTime.now());
            
            AlertSeverity severity = currentUtilization > 0.95 ? AlertSeverity.CRITICAL :
                                   currentUtilization > 0.85 ? AlertSeverity.HIGH :
                                   currentUtilization > 0.75 ? AlertSeverity.MEDIUM : AlertSeverity.LOW;
            alert.setSeverity(severity);
            
            if (messageNode.has("recommendation")) {
                alert.setRecommendation(messageNode.get("recommendation").asText());
            }
            
            processAlert(alert);
            
            if (currentUtilization > 0.9) {
                initiateAutoScaling(resourceType, resourceId);
            }
            
            predictFutureCapacityNeeds(resourceType, resourceId, currentUtilization);
            
            notifyRecipients(alert);
            
            logger.warn("Capacity alert: type={}, id={}, utilization={}%, threshold={}%, timeToLimit={}", 
                       resourceType, resourceId, currentUtilization * 100, threshold * 100, projectedTimeToLimit);
            
        } catch (Exception e) {
            logger.error("Error handling capacity alert: {}", e.getMessage(), e);
            throw new SystemException("Failed to process capacity alert", e);
        }
    }

    @Transactional
    private void handleSecurityAlert(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String alertType = messageNode.get("securityAlertType").asText();
            String threatLevel = messageNode.get("threatLevel").asText();
            String source = messageNode.get("source").asText();
            String target = messageNode.get("target").asText();
            String description = messageNode.get("description").asText();
            
            Alert alert = new Alert();
            alert.setAlertId(UUID.randomUUID().toString());
            alert.setAlertName("Security Alert - " + alertType);
            alert.setAlertType("SECURITY");
            alert.setSecurityAlertType(alertType);
            alert.setThreatLevel(threatLevel);
            alert.setSource(source);
            alert.setTarget(target);
            alert.setDescription(description);
            alert.setStatus(AlertStatus.OPEN);
            alert.setTimestamp(LocalDateTime.now());
            
            AlertSeverity severity = "CRITICAL".equals(threatLevel) ? AlertSeverity.CRITICAL :
                                   "HIGH".equals(threatLevel) ? AlertSeverity.HIGH :
                                   "MEDIUM".equals(threatLevel) ? AlertSeverity.MEDIUM : AlertSeverity.LOW;
            alert.setSeverity(severity);
            
            if (messageNode.has("indicators")) {
                JsonNode indicators = messageNode.get("indicators");
                Map<String, Object> iocs = new HashMap<>();
                indicators.fields().forEachRemaining(entry -> {
                    iocs.put(entry.getKey(), entry.getValue().asText());
                });
                alert.setIndicatorsOfCompromise(iocs);
            }
            
            processAlert(alert);
            
            if (severity == AlertSeverity.CRITICAL) {
                initiateSecurityResponse(alert);
                blockThreatSource(source);
            }
            
            correlateWithThreatIntelligence(alert);
            
            notifySecurityTeam(alert);
            
            logger.error("Security alert: type={}, threat={}, source={}, target={}", 
                        alertType, threatLevel, source, target);
            
        } catch (Exception e) {
            logger.error("Error handling security alert: {}", e.getMessage(), e);
            throw new SystemException("Failed to process security alert", e);
        }
    }

    @Transactional
    private void handleComplianceAlert(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String complianceType = messageNode.get("complianceType").asText();
            String regulation = messageNode.get("regulation").asText();
            String violation = messageNode.get("violation").asText();
            String affectedSystem = messageNode.get("affectedSystem").asText();
            String remediationRequired = messageNode.get("remediationRequired").asText();
            
            Alert alert = new Alert();
            alert.setAlertId(UUID.randomUUID().toString());
            alert.setAlertName("Compliance Alert - " + regulation);
            alert.setAlertType("COMPLIANCE");
            alert.setComplianceType(complianceType);
            alert.setRegulation(regulation);
            alert.setViolation(violation);
            alert.setAffectedSystem(affectedSystem);
            alert.setRemediationRequired(remediationRequired);
            alert.setStatus(AlertStatus.OPEN);
            alert.setTimestamp(LocalDateTime.now());
            
            AlertSeverity severity = determineComplianceSeverity(regulation, violation);
            alert.setSeverity(severity);
            
            if (messageNode.has("deadline")) {
                alert.setRemediationDeadline(LocalDateTime.parse(messageNode.get("deadline").asText()));
            }
            
            if (messageNode.has("evidence")) {
                JsonNode evidence = messageNode.get("evidence");
                List<String> evidenceList = new ArrayList<>();
                evidence.forEach(item -> evidenceList.add(item.asText()));
                alert.setEvidence(evidenceList);
            }
            
            processAlert(alert);
            
            createComplianceTicket(alert);
            
            if ("IMMEDIATE".equals(remediationRequired)) {
                initiateEmergencyCompliance(alert);
            }
            
            notifyComplianceTeam(alert);
            
            logger.error("Compliance alert: type={}, regulation={}, violation={}, system={}", 
                        complianceType, regulation, violation, affectedSystem);
            
        } catch (Exception e) {
            logger.error("Error handling compliance alert: {}", e.getMessage(), e);
            throw new SystemException("Failed to process compliance alert", e);
        }
    }

    @Transactional
    private void handleBusinessAlert(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String businessMetric = messageNode.get("businessMetric").asText();
            String kpi = messageNode.get("kpi").asText();
            double currentValue = messageNode.get("currentValue").asDouble();
            double targetValue = messageNode.get("targetValue").asDouble();
            double variance = messageNode.get("variance").asDouble();
            String impact = messageNode.get("impact").asText();
            
            Alert alert = new Alert();
            alert.setAlertId(UUID.randomUUID().toString());
            alert.setAlertName("Business Alert - " + kpi);
            alert.setAlertType("BUSINESS");
            alert.setBusinessMetric(businessMetric);
            alert.setKpi(kpi);
            alert.setCurrentValue(currentValue);
            alert.setTargetValue(targetValue);
            alert.setVariance(variance);
            alert.setImpact(impact);
            alert.setStatus(AlertStatus.OPEN);
            alert.setTimestamp(LocalDateTime.now());
            
            AlertSeverity severity = Math.abs(variance) > 0.5 ? AlertSeverity.HIGH :
                                   Math.abs(variance) > 0.2 ? AlertSeverity.MEDIUM : AlertSeverity.LOW;
            alert.setSeverity(severity);
            
            if (messageNode.has("affectedRevenue")) {
                alert.setAffectedRevenue(messageNode.get("affectedRevenue").asDouble());
            }
            
            if (messageNode.has("affectedUsers")) {
                alert.setAffectedUsers(messageNode.get("affectedUsers").asInt());
            }
            
            processAlert(alert);
            
            analyzeBusinessImpact(alert);
            
            if (Math.abs(variance) > 0.3) {
                recommendBusinessActions(alert);
            }
            
            notifyBusinessStakeholders(alert);
            
            logger.warn("Business alert: metric={}, kpi={}, current={}, target={}, variance={}%", 
                       businessMetric, kpi, currentValue, targetValue, variance * 100);
            
        } catch (Exception e) {
            logger.error("Error handling business alert: {}", e.getMessage(), e);
            throw new SystemException("Failed to process business alert", e);
        }
    }

    @Transactional
    private void handleCustomAlert(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String alertName = messageNode.get("alertName").asText();
            String alertCategory = messageNode.get("alertCategory").asText();
            String severity = messageNode.get("severity").asText();
            String description = messageNode.get("description").asText();
            
            Alert alert = new Alert();
            alert.setAlertId(UUID.randomUUID().toString());
            alert.setAlertName(alertName);
            alert.setAlertType("CUSTOM");
            alert.setAlertCategory(alertCategory);
            alert.setSeverity(AlertSeverity.valueOf(severity));
            alert.setDescription(description);
            alert.setStatus(AlertStatus.OPEN);
            alert.setTimestamp(LocalDateTime.now());
            
            if (messageNode.has("metadata")) {
                JsonNode metadata = messageNode.get("metadata");
                Map<String, Object> customData = new HashMap<>();
                metadata.fields().forEachRemaining(entry -> {
                    customData.put(entry.getKey(), entry.getValue().asText());
                });
                alert.setMetadata(customData);
            }
            
            if (messageNode.has("actions")) {
                JsonNode actions = messageNode.get("actions");
                List<String> recommendedActions = new ArrayList<>();
                actions.forEach(action -> recommendedActions.add(action.asText()));
                alert.setRecommendedActions(recommendedActions);
            }
            
            processAlert(alert);
            
            executeCustomAlertLogic(alert);
            
            notifyRecipients(alert);
            
            logger.info("Custom alert: name={}, category={}, severity={}", 
                       alertName, alertCategory, severity);
            
        } catch (Exception e) {
            logger.error("Error handling custom alert: {}", e.getMessage(), e);
            throw new SystemException("Failed to process custom alert", e);
        }
    }

    @Transactional
    private void handleAlertResolution(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String alertId = messageNode.get("alertId").asText();
            String resolutionType = messageNode.get("resolutionType").asText();
            String resolvedBy = messageNode.get("resolvedBy").asText();
            String resolutionNotes = messageNode.get("resolutionNotes").asText();
            
            Alert alert = activeAlerts.get(alertId);
            if (alert == null) {
                alert = alertingService.getAlert(alertId);
            }
            
            if (alert != null) {
                alert.setStatus(AlertStatus.RESOLVED);
                alert.setResolutionType(resolutionType);
                alert.setResolvedBy(resolvedBy);
                alert.setResolutionNotes(resolutionNotes);
                alert.setResolvedAt(LocalDateTime.now());
                
                long resolutionTimeMinutes = ChronoUnit.MINUTES.between(alert.getTimestamp(), LocalDateTime.now());
                alert.setResolutionTimeMinutes(resolutionTimeMinutes);
                
                activeAlerts.remove(alertId);
                
                alertingService.updateAlert(alert);
                
                recordResolutionMetrics(alert);
                
                if ("AUTO_RESOLVED".equals(resolutionType)) {
                    validateAutoResolution(alert);
                }
                
                notifyResolution(alert);
                
                logger.info("Alert resolved: id={}, type={}, resolvedBy={}, time={} minutes", 
                           alertId, resolutionType, resolvedBy, resolutionTimeMinutes);
            }
            
        } catch (Exception e) {
            logger.error("Error handling alert resolution: {}", e.getMessage(), e);
            throw new SystemException("Failed to process alert resolution", e);
        }
    }

    @Transactional
    private void handleAlertAcknowledgment(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String alertId = messageNode.get("alertId").asText();
            String acknowledgedBy = messageNode.get("acknowledgedBy").asText();
            String notes = messageNode.get("notes").asText();
            
            Alert alert = activeAlerts.get(alertId);
            if (alert != null) {
                alert.setStatus(AlertStatus.ACKNOWLEDGED);
                alert.setAcknowledgedBy(acknowledgedBy);
                alert.setAcknowledgedAt(LocalDateTime.now());
                alert.setAcknowledgmentNotes(notes);
                
                alertingService.updateAlert(alert);
                
                stopEscalation(alertId);
                
                logger.info("Alert acknowledged: id={}, by={}", alertId, acknowledgedBy);
            }
            
        } catch (Exception e) {
            logger.error("Error handling alert acknowledgment: {}", e.getMessage(), e);
            throw new SystemException("Failed to process alert acknowledgment", e);
        }
    }

    @Transactional
    private void handleAlertEscalation(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String alertId = messageNode.get("alertId").asText();
            String escalationLevel = messageNode.get("escalationLevel").asText();
            String escalatedTo = messageNode.get("escalatedTo").asText();
            String reason = messageNode.get("reason").asText();
            
            Alert alert = activeAlerts.get(alertId);
            if (alert != null) {
                alert.setEscalationLevel(escalationLevel);
                alert.setEscalatedTo(escalatedTo);
                alert.setEscalatedAt(LocalDateTime.now());
                alert.setEscalationReason(reason);
                
                alertingService.updateAlert(alert);
                
                escalatedCounter.increment();
                
                notifyEscalation(alert, escalatedTo);
                
                logger.warn("Alert escalated: id={}, level={}, to={}, reason={}", 
                           alertId, escalationLevel, escalatedTo, reason);
            }
            
        } catch (Exception e) {
            logger.error("Error handling alert escalation: {}", e.getMessage(), e);
            throw new SystemException("Failed to process alert escalation", e);
        }
    }

    @Transactional
    private void handleAlertSuppression(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String suppressionRule = messageNode.get("suppressionRule").asText();
            String pattern = messageNode.get("pattern").asText();
            int duration = messageNode.get("durationMinutes").asInt();
            String reason = messageNode.get("reason").asText();
            
            Map<String, Object> suppression = new HashMap<>();
            suppression.put("rule", suppressionRule);
            suppression.put("pattern", pattern);
            suppression.put("duration", duration);
            suppression.put("reason", reason);
            suppression.put("startTime", LocalDateTime.now().toString());
            suppression.put("endTime", LocalDateTime.now().plusMinutes(duration).toString());
            
            alertingService.addSuppressionRule(suppression);
            
            logger.info("Alert suppression added: rule={}, pattern={}, duration={} minutes", 
                       suppressionRule, pattern, duration);
            
        } catch (Exception e) {
            logger.error("Error handling alert suppression: {}", e.getMessage(), e);
            throw new SystemException("Failed to process alert suppression", e);
        }
    }

    @Transactional
    private void handleAlertUpdate(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String alertId = messageNode.get("alertId").asText();
            
            Alert alert = activeAlerts.get(alertId);
            if (alert != null) {
                if (messageNode.has("severity")) {
                    alert.setSeverity(AlertSeverity.valueOf(messageNode.get("severity").asText()));
                }
                
                if (messageNode.has("status")) {
                    alert.setStatus(AlertStatus.valueOf(messageNode.get("status").asText()));
                }
                
                if (messageNode.has("assignedTo")) {
                    alert.setAssignedTo(messageNode.get("assignedTo").asText());
                }
                
                if (messageNode.has("notes")) {
                    alert.addNote(messageNode.get("notes").asText());
                }
                
                alert.setLastUpdated(LocalDateTime.now());
                
                alertingService.updateAlert(alert);
                
                logger.info("Alert updated: id={}", alertId);
            }
            
        } catch (Exception e) {
            logger.error("Error handling alert update: {}", e.getMessage(), e);
            throw new SystemException("Failed to process alert update", e);
        }
    }

    private void processAlert(Alert alert) {
        String alertKey = generateAlertKey(alert);
        
        activeAlerts.put(alert.getAlertId(), alert);
        
        deduplicationCache.put(alertKey, LocalDateTime.now());
        
        updateAlertFrequency(alert.getAlertType());
        
        alertingService.saveAlert(alert);
        
        enrichAlertWithContext(alert);
        
        applyAlertRules(alert);
    }

    private boolean isDuplicate(Alert alert) {
        String alertKey = generateAlertKey(alert);
        LocalDateTime lastOccurrence = deduplicationCache.get(alertKey);
        
        if (lastOccurrence != null) {
            long minutesSince = ChronoUnit.MINUTES.between(lastOccurrence, LocalDateTime.now());
            return minutesSince < deduplicationWindowMinutes;
        }
        
        return false;
    }

    private boolean isAlertStorm() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(alertStormWindowSeconds);
        long recentAlerts = deduplicationCache.values().stream()
            .filter(time -> time.isAfter(cutoff))
            .count();
        
        return recentAlerts > alertStormThreshold;
    }

    private void handleAlertStorm() {
        logger.error("Alert storm detected! Recent alerts exceed threshold: {}", alertStormThreshold);
        
        Map<String, Object> stormAlert = new HashMap<>();
        stormAlert.put("alertType", "ALERT_STORM");
        stormAlert.put("threshold", alertStormThreshold);
        stormAlert.put("window", alertStormWindowSeconds);
        stormAlert.put("action", "SUPPRESSING_ALERTS");
        stormAlert.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("critical-alerts", stormAlert);
    }

    private void correlateAlert(Alert alert) {
        String correlationKey = alert.getServiceName() != null ? alert.getServiceName() : alert.getAlertType();
        
        correlationWindow.compute(correlationKey, (key, alerts) -> {
            if (alerts == null) {
                alerts = new ArrayList<>();
            }
            alerts.add(alert);
            return alerts;
        });
        
        List<Alert> relatedAlerts = alertCorrelationService.findRelatedAlerts(alert, correlationWindowMinutes);
        
        if (!relatedAlerts.isEmpty()) {
            alert.setCorrelatedAlerts(relatedAlerts.stream()
                .map(Alert::getAlertId)
                .collect(Collectors.toList()));
            
            correlatedCounter.increment();
            
            analyzeCorrelationPattern(alert, relatedAlerts);
        }
    }

    private boolean shouldEscalate(Alert alert) {
        if (alert.getSeverity() == AlertSeverity.CRITICAL) {
            return true;
        }
        
        if (alert.getStatus() == AlertStatus.OPEN) {
            long minutesSinceCreated = ChronoUnit.MINUTES.between(alert.getTimestamp(), LocalDateTime.now());
            return minutesSinceCreated > alertEscalationService.getEscalationThreshold(alert.getSeverity());
        }
        
        return false;
    }

    private void escalateAlert(Alert alert) {
        String escalationLevel = alertEscalationService.getNextEscalationLevel(alert);
        String escalateTo = alertEscalationService.getEscalationTarget(escalationLevel);
        
        alert.setEscalationLevel(escalationLevel);
        alert.setEscalatedTo(escalateTo);
        alert.setEscalatedAt(LocalDateTime.now());
        
        escalatedCounter.increment();
        
        notifyEscalation(alert, escalateTo);
    }

    private void notifyRecipients(Alert alert) {
        List<String> recipients = alertingService.getAlertRecipients(alert);
        
        for (String recipient : recipients) {
            Map<String, Object> notification = new HashMap<>();
            notification.put("recipient", recipient);
            notification.put("alert", alert);
            notification.put("channel", determineNotificationChannel(alert.getSeverity()));
            notification.put("timestamp", LocalDateTime.now().toString());
            
            notificationService.sendNotification(notification);
        }
    }

    private void cleanupDeduplicationCache() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(deduplicationWindowMinutes);
        deduplicationCache.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    private void processCorrelations() {
        try {
            for (Map.Entry<String, List<Alert>> entry : correlationWindow.entrySet()) {
                List<Alert> alerts = entry.getValue();
                
                if (alerts.size() >= 3) {
                    Map<String, Object> correlation = alertCorrelationService.analyzeCorrelation(alerts);
                    
                    if (correlation != null && (boolean) correlation.get("isSignificant")) {
                        createCorrelatedIncident(correlation, alerts);
                    }
                }
            }
            
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(correlationWindowMinutes);
            correlationWindow.values().forEach(alerts -> 
                alerts.removeIf(alert -> alert.getTimestamp().isBefore(cutoff))
            );
            
        } catch (Exception e) {
            logger.error("Error processing correlations: {}", e.getMessage(), e);
        }
    }

    private void checkEscalations() {
        try {
            for (Alert alert : activeAlerts.values()) {
                if (alert.getStatus() == AlertStatus.OPEN && shouldEscalate(alert)) {
                    escalateAlert(alert);
                }
            }
        } catch (Exception e) {
            logger.error("Error checking escalations: {}", e.getMessage(), e);
        }
    }

    private void cleanupOldAlerts() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(alertRetentionDays);
            int removed = alertingService.removeAlertsOlderThan(cutoff);
            
            if (removed > 0) {
                logger.info("Cleaned up {} old alerts", removed);
            }
            
            activeAlerts.entrySet().removeIf(entry -> 
                entry.getValue().getTimestamp().isBefore(cutoff)
            );
            
        } catch (Exception e) {
            logger.error("Error cleaning up old alerts: {}", e.getMessage(), e);
        }
    }

    // Helper methods
    private boolean isValidAlertMessage(JsonNode messageNode) {
        return messageNode != null &&
               messageNode.has("eventType") && 
               StringUtils.hasText(messageNode.get("eventType").asText());
    }

    private String generateAlertKey(Alert alert) {
        return alert.getAlertType() + ":" + 
               alert.getMetricName() + ":" + 
               (alert.getServiceName() != null ? alert.getServiceName() : "");
    }

    private void updateAlertFrequency(String alertType) {
        alertFrequency.computeIfAbsent(alertType, k -> new AtomicInteger(0)).incrementAndGet();
    }

    private void enrichAlertWithContext(Alert alert) {
        alertingService.enrichAlert(alert);
    }

    private void applyAlertRules(Alert alert) {
        List<AlertRule> rules = alertingService.getApplicableRules(alert);
        for (AlertRule rule : rules) {
            rule.apply(alert);
        }
    }

    private AlertSeverity determineAnomalySeverity(double anomalyScore) {
        if (anomalyScore > 4) return AlertSeverity.CRITICAL;
        if (anomalyScore > 3) return AlertSeverity.HIGH;
        if (anomalyScore > 2) return AlertSeverity.MEDIUM;
        return AlertSeverity.LOW;
    }

    private boolean shouldSuppressAnomaly(Alert alert) {
        return alert.getConfidence() != null && alert.getConfidence() < 0.7;
    }

    private void analyzeAnomalyPattern(Alert alert) {
        alertingService.analyzeAnomalyPattern(alert);
    }

    private void findRelatedAnomalies(Alert alert) {
        alertCorrelationService.findRelatedAnomalies(alert);
    }

    private void identifyTopErrors(Alert alert, Map<String, Integer> errorBreakdown) {
        alert.setTopErrors(errorBreakdown.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(5)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList()));
    }

    private void triggerErrorAnalysis(String serviceName, String endpoint, double errorRate) {
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("serviceName", serviceName);
        analysis.put("endpoint", endpoint);
        analysis.put("errorRate", errorRate);
        analysis.put("action", "ANALYZE_ERRORS");
        analysis.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("error-analysis", analysis);
    }

    private boolean shouldTriggerCircuitBreaker(double errorRate, int totalRequests) {
        return errorRate > 0.5 && totalRequests > 100;
    }

    private void recommendCircuitBreaker(String serviceName, String endpoint) {
        Map<String, Object> recommendation = new HashMap<>();
        recommendation.put("serviceName", serviceName);
        recommendation.put("endpoint", endpoint);
        recommendation.put("action", "ENABLE_CIRCUIT_BREAKER");
        recommendation.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("circuit-breaker-recommendations", recommendation);
    }

    private void analyzeLatencyPattern(Alert alert) {
        alertingService.analyzeLatencyPattern(alert);
    }

    private void initiatePerformanceInvestigation(String serviceName, String endpoint, double p99Latency) {
        Map<String, Object> investigation = new HashMap<>();
        investigation.put("serviceName", serviceName);
        investigation.put("endpoint", endpoint);
        investigation.put("p99Latency", p99Latency);
        investigation.put("action", "INVESTIGATE_PERFORMANCE");
        investigation.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("performance-investigations", investigation);
    }

    private void initiateFailoverProcedure(String serviceName) {
        alertingService.initiateFailover(serviceName);
    }

    private void createIncident(Alert alert) {
        Map<String, Object> incident = new HashMap<>();
        incident.put("alertId", alert.getAlertId());
        incident.put("serviceName", alert.getServiceName());
        incident.put("severity", alert.getSeverity());
        incident.put("type", "SERVICE_DOWN");
        incident.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("incident-management", incident);
    }

    private void calculateSLAImpact(String serviceName, double availability, int downtime) {
        alertingService.calculateSLAImpact(serviceName, availability, downtime);
    }

    private void initiateAutoScaling(String resourceType, String resourceId) {
        Map<String, Object> scaling = new HashMap<>();
        scaling.put("resourceType", resourceType);
        scaling.put("resourceId", resourceId);
        scaling.put("action", "AUTO_SCALE");
        scaling.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("auto-scaling", scaling);
    }

    private void predictFutureCapacityNeeds(String resourceType, String resourceId, double utilization) {
        alertingService.predictCapacityNeeds(resourceType, resourceId, utilization);
    }

    private void initiateSecurityResponse(Alert alert) {
        alertingService.initiateSecurityResponse(alert);
    }

    private void blockThreatSource(String source) {
        Map<String, Object> block = new HashMap<>();
        block.put("source", source);
        block.put("action", "BLOCK");
        block.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("security-blocks", block);
    }

    private void correlateWithThreatIntelligence(Alert alert) {
        alertCorrelationService.correlateThreatIntelligence(alert);
    }

    private void notifySecurityTeam(Alert alert) {
        notificationService.notifySecurityTeam(alert);
    }

    private AlertSeverity determineComplianceSeverity(String regulation, String violation) {
        if ("GDPR".equals(regulation) || "PCI-DSS".equals(regulation)) {
            return AlertSeverity.CRITICAL;
        }
        return AlertSeverity.HIGH;
    }

    private void createComplianceTicket(Alert alert) {
        alertingService.createComplianceTicket(alert);
    }

    private void initiateEmergencyCompliance(Alert alert) {
        alertingService.initiateEmergencyCompliance(alert);
    }

    private void notifyComplianceTeam(Alert alert) {
        notificationService.notifyComplianceTeam(alert);
    }

    private void analyzeBusinessImpact(Alert alert) {
        alertingService.analyzeBusinessImpact(alert);
    }

    private void recommendBusinessActions(Alert alert) {
        alertingService.recommendBusinessActions(alert);
    }

    private void notifyBusinessStakeholders(Alert alert) {
        notificationService.notifyBusinessStakeholders(alert);
    }

    private void executeCustomAlertLogic(Alert alert) {
        alertingService.executeCustomLogic(alert);
    }

    private void recordResolutionMetrics(Alert alert) {
        metricsService.recordAlertResolution(alert);
    }

    private void validateAutoResolution(Alert alert) {
        alertingService.validateAutoResolution(alert);
    }

    private void notifyResolution(Alert alert) {
        notificationService.notifyResolution(alert);
    }

    private void stopEscalation(String alertId) {
        alertEscalationService.stopEscalation(alertId);
    }

    private void notifyEscalation(Alert alert, String escalateTo) {
        notificationService.notifyEscalation(alert, escalateTo);
    }

    private String determineNotificationChannel(AlertSeverity severity) {
        switch (severity) {
            case CRITICAL:
                return "PHONE,SMS,EMAIL,SLACK";
            case HIGH:
                return "SMS,EMAIL,SLACK";
            case MEDIUM:
                return "EMAIL,SLACK";
            default:
                return "SLACK";
        }
    }

    private void createCorrelatedIncident(Map<String, Object> correlation, List<Alert> alerts) {
        Map<String, Object> incident = new HashMap<>();
        incident.put("correlationId", correlation.get("correlationId"));
        incident.put("pattern", correlation.get("pattern"));
        incident.put("alerts", alerts.stream().map(Alert::getAlertId).collect(Collectors.toList()));
        incident.put("severity", AlertSeverity.HIGH);
        incident.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("correlated-incidents", incident);
    }

    private void handleProcessingError(String message, String topic, Exception error, 
                                     String correlationId, String traceId, Acknowledgment acknowledgment) {
        try {
            errorCounter.increment();
            logger.error("Error processing alert trigger message: {}", error.getMessage(), error);
            
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
        logger.error("Circuit breaker fallback triggered for alert triggers consumer: {}", ex.getMessage());
        
        errorCounter.increment();
        sendToDlq(message, topic, "Circuit breaker fallback: " + ex.getMessage(), ex, correlationId, traceId);
        acknowledgment.acknowledge();
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down AlertTriggersConsumer");
        
        processCorrelations();
        
        scheduledExecutor.shutdown();
        alertProcessingExecutor.shutdown();
        
        try {
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            if (!alertProcessingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                alertProcessingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Error shutting down executors", e);
            scheduledExecutor.shutdownNow();
            alertProcessingExecutor.shutdownNow();
        }
        
        logger.info("AlertTriggersConsumer shutdown complete. Total alerts processed: {}", 
                   totalAlertsProcessed.get());
    }
}