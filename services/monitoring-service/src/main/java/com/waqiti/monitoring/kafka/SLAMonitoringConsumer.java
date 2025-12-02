package com.waqiti.monitoring.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.monitoring.model.SLA;
import com.waqiti.monitoring.model.SLAMetric;
import com.waqiti.monitoring.model.SLAStatus;
import com.waqiti.monitoring.model.SLABreach;
import com.waqiti.monitoring.service.SLAMonitoringService;
import com.waqiti.monitoring.service.SLAReportingService;
import com.waqiti.monitoring.service.AlertingService;
import com.waqiti.monitoring.service.CompensationService;
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
public class SLAMonitoringConsumer {

    private static final Logger logger = LoggerFactory.getLogger(SLAMonitoringConsumer.class);
    private static final String CONSUMER_NAME = "sla-monitoring-consumer";
    private static final String DLQ_TOPIC = "sla-monitoring-dlq";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int PROCESSING_TIMEOUT_SECONDS = 10;

    private final ObjectMapper objectMapper;
    private final SLAMonitoringService slaMonitoringService;
    private final SLAReportingService slaReportingService;
    private final AlertingService alertingService;
    private final CompensationService compensationService;
    private final MetricsService metricsService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${kafka.consumer.sla-monitoring.enabled:true}")
    private boolean consumerEnabled;

    @Value("${sla.monitoring.interval-seconds:60}")
    private int monitoringIntervalSeconds;

    @Value("${sla.monitoring.rolling-window-minutes:60}")
    private int rollingWindowMinutes;

    @Value("${sla.breach.threshold-consecutive:3}")
    private int breachThresholdConsecutive;

    @Value("${sla.breach.auto-compensation:true}")
    private boolean autoCompensationEnabled;

    @Value("${sla.reporting.interval-hours:24}")
    private int reportingIntervalHours;

    @Value("${sla.prediction.enabled:true}")
    private boolean predictionEnabled;

    @Value("${sla.prediction.horizon-hours:4}")
    private int predictionHorizonHours;

    private Counter processedCounter;
    private Counter errorCounter;
    private Counter dlqCounter;
    private Timer processingTimer;
    private Counter breachCounter;
    private Gauge complianceRateGauge;
    private DistributionSummary slaPerformance;

    private final ConcurrentHashMap<String, SLA> activeSLAs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SLAMetric> currentMetrics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<SLABreach>> breachHistory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> complianceRates = new ConcurrentHashMap<>();
    private final AtomicLong totalSLAChecks = new AtomicLong(0);
    private final AtomicReference<Double> overallCompliance = new AtomicReference<>(100.0);
    private ScheduledExecutorService scheduledExecutor;
    private ExecutorService slaProcessingExecutor;

    public SLAMonitoringConsumer(
            ObjectMapper objectMapper,
            SLAMonitoringService slaMonitoringService,
            SLAReportingService slaReportingService,
            AlertingService alertingService,
            CompensationService compensationService,
            MetricsService metricsService,
            KafkaTemplate<String, Object> kafkaTemplate,
            MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.slaMonitoringService = slaMonitoringService;
        this.slaReportingService = slaReportingService;
        this.alertingService = alertingService;
        this.compensationService = compensationService;
        this.metricsService = metricsService;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void initializeMetrics() {
        this.processedCounter = Counter.builder("sla_monitoring_processed_total")
                .description("Total processed SLA monitoring events")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.errorCounter = Counter.builder("sla_monitoring_errors_total")
                .description("Total SLA monitoring processing errors")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.dlqCounter = Counter.builder("sla_monitoring_dlq_total")
                .description("Total SLA monitoring events sent to DLQ")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.processingTimer = Timer.builder("sla_monitoring_processing_duration")
                .description("SLA monitoring processing duration")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.breachCounter = Counter.builder("sla_breaches_total")
                .description("Total SLA breaches detected")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.complianceRateGauge = Gauge.builder("sla_compliance_rate", overallCompliance, AtomicReference::get)
                .description("Overall SLA compliance rate")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.slaPerformance = DistributionSummary.builder("sla_performance")
                .description("SLA performance distribution")
                .tag("consumer", CONSUMER_NAME)
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry);

        this.scheduledExecutor = Executors.newScheduledThreadPool(4);
        scheduledExecutor.scheduleWithFixedDelay(this::performSLAChecks, 
                monitoringIntervalSeconds, monitoringIntervalSeconds, TimeUnit.SECONDS);
        scheduledExecutor.scheduleWithFixedDelay(this::calculateComplianceRates, 
                5, 5, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::generateSLAReports, 
                reportingIntervalHours, reportingIntervalHours, TimeUnit.HOURS);
        scheduledExecutor.scheduleWithFixedDelay(this::performSLAPrediction, 
                30, 30, TimeUnit.MINUTES);

        this.slaProcessingExecutor = Executors.newFixedThreadPool(6);

        logger.info("SLAMonitoringConsumer initialized with monitoring interval: {} seconds", 
                   monitoringIntervalSeconds);
    }

    @KafkaListener(
        topics = "${kafka.topics.sla-monitoring:sla-monitoring}",
        groupId = "${kafka.consumer.group-id:monitoring-service-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "sla-monitoring-circuit-breaker", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "sla-monitoring-retry")
    public void processSLAMonitoring(
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
                logger.warn("SLA monitoring consumer is disabled, skipping message processing");
                acknowledgment.acknowledge();
                return;
            }

            logger.debug("Processing SLA monitoring message: messageId={}, topic={}, partition={}, offset={}",
                    messageId, topic, partition, offset);

            if (!StringUtils.hasText(message)) {
                logger.warn("Received empty or null message, skipping processing");
                acknowledgment.acknowledge();
                return;
            }

            JsonNode messageNode = objectMapper.readTree(message);
            
            if (!isValidSLAMessage(messageNode)) {
                logger.error("Invalid SLA message format: {}", message);
                sendToDlq(message, topic, "Invalid message format", null, correlationId, traceId);
                acknowledgment.acknowledge();
                return;
            }

            String eventType = messageNode.get("eventType").asText();
            
            CompletableFuture<Void> processingFuture = CompletableFuture.runAsync(() -> {
                try {
                    switch (eventType) {
                        case "SLA_DEFINITION":
                            handleSLADefinition(messageNode, correlationId, traceId);
                            break;
                        case "SLA_UPDATE":
                            handleSLAUpdate(messageNode, correlationId, traceId);
                            break;
                        case "METRIC_UPDATE":
                            handleMetricUpdate(messageNode, correlationId, traceId);
                            break;
                        case "AVAILABILITY_CHECK":
                            handleAvailabilityCheck(messageNode, correlationId, traceId);
                            break;
                        case "PERFORMANCE_CHECK":
                            handlePerformanceCheck(messageNode, correlationId, traceId);
                            break;
                        case "ERROR_RATE_CHECK":
                            handleErrorRateCheck(messageNode, correlationId, traceId);
                            break;
                        case "RESPONSE_TIME_CHECK":
                            handleResponseTimeCheck(messageNode, correlationId, traceId);
                            break;
                        case "THROUGHPUT_CHECK":
                            handleThroughputCheck(messageNode, correlationId, traceId);
                            break;
                        case "BREACH_DETECTION":
                            handleBreachDetection(messageNode, correlationId, traceId);
                            break;
                        case "BREACH_RESOLUTION":
                            handleBreachResolution(messageNode, correlationId, traceId);
                            break;
                        case "COMPLIANCE_REPORT":
                            handleComplianceReport(messageNode, correlationId, traceId);
                            break;
                        case "COMPENSATION_REQUEST":
                            handleCompensationRequest(messageNode, correlationId, traceId);
                            break;
                        case "SLA_SUSPENSION":
                            handleSLASuspension(messageNode, correlationId, traceId);
                            break;
                        case "SLA_REINSTATEMENT":
                            handleSLAReinstatement(messageNode, correlationId, traceId);
                            break;
                        case "CUSTOMER_IMPACT":
                            handleCustomerImpact(messageNode, correlationId, traceId);
                            break;
                        default:
                            logger.warn("Unknown SLA event type: {}", eventType);
                    }
                } catch (Exception e) {
                    logger.error("Error processing SLA event: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }, slaProcessingExecutor).orTimeout(PROCESSING_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            processingFuture.get();

            totalSLAChecks.incrementAndGet();
            processedCounter.increment();
            acknowledgment.acknowledge();

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse SLA message: messageId={}, error={}", messageId, e.getMessage());
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } catch (Exception e) {
            logger.error("Unexpected error processing SLA: messageId={}, error={}", messageId, e.getMessage(), e);
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }

    @Transactional
    private void handleSLADefinition(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String slaId = messageNode.get("slaId").asText();
            String serviceName = messageNode.get("serviceName").asText();
            String customerTier = messageNode.get("customerTier").asText();
            
            SLA sla = new SLA();
            sla.setSlaId(slaId);
            sla.setServiceName(serviceName);
            sla.setCustomerTier(customerTier);
            sla.setStatus(SLAStatus.ACTIVE);
            sla.setCreatedAt(LocalDateTime.now());
            
            if (messageNode.has("availability")) {
                sla.setAvailabilityTarget(messageNode.get("availability").asDouble());
            }
            
            if (messageNode.has("responseTime")) {
                JsonNode responseTime = messageNode.get("responseTime");
                sla.setResponseTimeP50(responseTime.get("p50").asLong());
                sla.setResponseTimeP95(responseTime.get("p95").asLong());
                sla.setResponseTimeP99(responseTime.get("p99").asLong());
            }
            
            if (messageNode.has("errorRate")) {
                sla.setMaxErrorRate(messageNode.get("errorRate").asDouble());
            }
            
            if (messageNode.has("throughput")) {
                sla.setMinThroughput(messageNode.get("throughput").asDouble());
            }
            
            if (messageNode.has("maintenanceWindow")) {
                JsonNode maintenance = messageNode.get("maintenanceWindow");
                sla.setMaintenanceWindowStart(maintenance.get("start").asText());
                sla.setMaintenanceWindowEnd(maintenance.get("end").asText());
                sla.setMaintenanceWindowDuration(maintenance.get("durationHours").asInt());
            }
            
            if (messageNode.has("penalties")) {
                JsonNode penalties = messageNode.get("penalties");
                Map<String, Double> penaltyStructure = new HashMap<>();
                penalties.fields().forEachRemaining(entry -> {
                    penaltyStructure.put(entry.getKey(), entry.getValue().asDouble());
                });
                sla.setPenaltyStructure(penaltyStructure);
            }
            
            activeSLAs.put(slaId, sla);
            slaMonitoringService.saveSLA(sla);
            
            initializeMetrics(sla);
            
            logger.info("SLA defined: id={}, service={}, tier={}, availability={}%", 
                       slaId, serviceName, customerTier, sla.getAvailabilityTarget() * 100);
            
        } catch (Exception e) {
            logger.error("Error handling SLA definition: {}", e.getMessage(), e);
            throw new SystemException("Failed to process SLA definition", e);
        }
    }

    @Transactional
    private void handleSLAUpdate(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String slaId = messageNode.get("slaId").asText();
            
            SLA sla = activeSLAs.get(slaId);
            if (sla == null) {
                sla = slaMonitoringService.getSLA(slaId);
            }
            
            if (sla != null) {
                if (messageNode.has("availability")) {
                    sla.setAvailabilityTarget(messageNode.get("availability").asDouble());
                }
                
                if (messageNode.has("responseTime")) {
                    JsonNode responseTime = messageNode.get("responseTime");
                    if (responseTime.has("p50")) {
                        sla.setResponseTimeP50(responseTime.get("p50").asLong());
                    }
                    if (responseTime.has("p95")) {
                        sla.setResponseTimeP95(responseTime.get("p95").asLong());
                    }
                    if (responseTime.has("p99")) {
                        sla.setResponseTimeP99(responseTime.get("p99").asLong());
                    }
                }
                
                if (messageNode.has("errorRate")) {
                    sla.setMaxErrorRate(messageNode.get("errorRate").asDouble());
                }
                
                sla.setUpdatedAt(LocalDateTime.now());
                
                activeSLAs.put(slaId, sla);
                slaMonitoringService.updateSLA(sla);
                
                logger.info("SLA updated: id={}", slaId);
            }
            
        } catch (Exception e) {
            logger.error("Error handling SLA update: {}", e.getMessage(), e);
            throw new SystemException("Failed to process SLA update", e);
        }
    }

    @Transactional
    private void handleMetricUpdate(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String slaId = messageNode.get("slaId").asText();
            String metricType = messageNode.get("metricType").asText();
            double value = messageNode.get("value").asDouble();
            LocalDateTime timestamp = LocalDateTime.parse(messageNode.get("timestamp").asText());
            
            String metricKey = slaId + ":" + metricType;
            SLAMetric metric = currentMetrics.computeIfAbsent(metricKey, k -> new SLAMetric());
            
            metric.setSlaId(slaId);
            metric.setMetricType(metricType);
            metric.setValue(value);
            metric.setTimestamp(timestamp);
            
            if (messageNode.has("aggregation")) {
                metric.setAggregationType(messageNode.get("aggregation").asText());
            }
            
            updateRollingWindow(metric);
            
            SLA sla = activeSLAs.get(slaId);
            if (sla != null) {
                boolean isCompliant = checkMetricCompliance(sla, metric);
                metric.setCompliant(isCompliant);
                
                if (!isCompliant) {
                    handleNonCompliantMetric(sla, metric);
                }
            }
            
            slaMonitoringService.recordMetric(metric);
            
            logger.debug("Metric updated: sla={}, type={}, value={}, compliant={}", 
                       slaId, metricType, value, metric.isCompliant());
            
        } catch (Exception e) {
            logger.error("Error handling metric update: {}", e.getMessage(), e);
            throw new SystemException("Failed to process metric update", e);
        }
    }

    @Transactional
    private void handleAvailabilityCheck(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String serviceName = messageNode.get("serviceName").asText();
            double availability = messageNode.get("availability").asDouble();
            String period = messageNode.get("period").asText();
            int downtimeMinutes = messageNode.get("downtimeMinutes").asInt();
            
            List<SLA> affectedSLAs = findSLAsByService(serviceName);
            
            for (SLA sla : affectedSLAs) {
                boolean isCompliant = availability >= sla.getAvailabilityTarget();
                
                SLAMetric metric = new SLAMetric();
                metric.setSlaId(sla.getSlaId());
                metric.setMetricType("AVAILABILITY");
                metric.setValue(availability);
                metric.setCompliant(isCompliant);
                metric.setTimestamp(LocalDateTime.now());
                
                if (!isCompliant) {
                    SLABreach breach = new SLABreach();
                    breach.setSlaId(sla.getSlaId());
                    breach.setBreachType("AVAILABILITY");
                    breach.setExpectedValue(sla.getAvailabilityTarget());
                    breach.setActualValue(availability);
                    breach.setBreachTime(LocalDateTime.now());
                    breach.setSeverity(calculateBreachSeverity(sla.getAvailabilityTarget(), availability));
                    breach.setDowntimeMinutes(downtimeMinutes);
                    
                    processBreach(breach, sla);
                }
                
                slaPerformance.record(availability);
                
                logger.info("Availability check: service={}, sla={}, availability={}%, target={}%, compliant={}", 
                           serviceName, sla.getSlaId(), availability * 100, 
                           sla.getAvailabilityTarget() * 100, isCompliant);
            }
            
        } catch (Exception e) {
            logger.error("Error handling availability check: {}", e.getMessage(), e);
            throw new SystemException("Failed to process availability check", e);
        }
    }

    @Transactional
    private void handlePerformanceCheck(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String serviceName = messageNode.get("serviceName").asText();
            String endpoint = messageNode.get("endpoint").asText();
            double p50 = messageNode.get("p50").asDouble();
            double p95 = messageNode.get("p95").asDouble();
            double p99 = messageNode.get("p99").asDouble();
            String period = messageNode.get("period").asText();
            
            List<SLA> affectedSLAs = findSLAsByService(serviceName);
            
            for (SLA sla : affectedSLAs) {
                boolean p50Compliant = p50 <= sla.getResponseTimeP50();
                boolean p95Compliant = p95 <= sla.getResponseTimeP95();
                boolean p99Compliant = p99 <= sla.getResponseTimeP99();
                boolean isCompliant = p50Compliant && p95Compliant && p99Compliant;
                
                SLAMetric metric = new SLAMetric();
                metric.setSlaId(sla.getSlaId());
                metric.setMetricType("PERFORMANCE");
                metric.setCompliant(isCompliant);
                metric.setTimestamp(LocalDateTime.now());
                
                Map<String, Object> performanceData = new HashMap<>();
                performanceData.put("p50", p50);
                performanceData.put("p95", p95);
                performanceData.put("p99", p99);
                performanceData.put("endpoint", endpoint);
                metric.setMetadata(performanceData);
                
                if (!isCompliant) {
                    SLABreach breach = new SLABreach();
                    breach.setSlaId(sla.getSlaId());
                    breach.setBreachType("PERFORMANCE");
                    breach.setBreachTime(LocalDateTime.now());
                    
                    if (!p99Compliant) {
                        breach.setExpectedValue(sla.getResponseTimeP99());
                        breach.setActualValue(p99);
                        breach.setBreachDetail("P99 latency exceeded");
                        breach.setSeverity("HIGH");
                    } else if (!p95Compliant) {
                        breach.setExpectedValue(sla.getResponseTimeP95());
                        breach.setActualValue(p95);
                        breach.setBreachDetail("P95 latency exceeded");
                        breach.setSeverity("MEDIUM");
                    } else {
                        breach.setExpectedValue(sla.getResponseTimeP50());
                        breach.setActualValue(p50);
                        breach.setBreachDetail("P50 latency exceeded");
                        breach.setSeverity("LOW");
                    }
                    
                    processBreach(breach, sla);
                }
                
                logger.info("Performance check: service={}, endpoint={}, p50={}ms, p95={}ms, p99={}ms, compliant={}", 
                           serviceName, endpoint, p50, p95, p99, isCompliant);
            }
            
        } catch (Exception e) {
            logger.error("Error handling performance check: {}", e.getMessage(), e);
            throw new SystemException("Failed to process performance check", e);
        }
    }

    @Transactional
    private void handleErrorRateCheck(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String serviceName = messageNode.get("serviceName").asText();
            double errorRate = messageNode.get("errorRate").asDouble();
            int errorCount = messageNode.get("errorCount").asInt();
            int totalRequests = messageNode.get("totalRequests").asInt();
            String period = messageNode.get("period").asText();
            
            List<SLA> affectedSLAs = findSLAsByService(serviceName);
            
            for (SLA sla : affectedSLAs) {
                boolean isCompliant = errorRate <= sla.getMaxErrorRate();
                
                SLAMetric metric = new SLAMetric();
                metric.setSlaId(sla.getSlaId());
                metric.setMetricType("ERROR_RATE");
                metric.setValue(errorRate);
                metric.setCompliant(isCompliant);
                metric.setTimestamp(LocalDateTime.now());
                
                if (!isCompliant) {
                    SLABreach breach = new SLABreach();
                    breach.setSlaId(sla.getSlaId());
                    breach.setBreachType("ERROR_RATE");
                    breach.setExpectedValue(sla.getMaxErrorRate());
                    breach.setActualValue(errorRate);
                    breach.setBreachTime(LocalDateTime.now());
                    breach.setSeverity(errorRate > sla.getMaxErrorRate() * 2 ? "HIGH" : "MEDIUM");
                    
                    Map<String, Object> details = new HashMap<>();
                    details.put("errorCount", errorCount);
                    details.put("totalRequests", totalRequests);
                    breach.setDetails(details);
                    
                    processBreach(breach, sla);
                }
                
                logger.info("Error rate check: service={}, errorRate={}%, target={}%, compliant={}", 
                           serviceName, errorRate * 100, sla.getMaxErrorRate() * 100, isCompliant);
            }
            
        } catch (Exception e) {
            logger.error("Error handling error rate check: {}", e.getMessage(), e);
            throw new SystemException("Failed to process error rate check", e);
        }
    }

    @Transactional
    private void handleResponseTimeCheck(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String serviceName = messageNode.get("serviceName").asText();
            String operation = messageNode.get("operation").asText();
            long responseTime = messageNode.get("responseTime").asLong();
            String percentile = messageNode.get("percentile").asText();
            
            List<SLA> affectedSLAs = findSLAsByService(serviceName);
            
            for (SLA sla : affectedSLAs) {
                long threshold = getResponseTimeThreshold(sla, percentile);
                boolean isCompliant = responseTime <= threshold;
                
                SLAMetric metric = new SLAMetric();
                metric.setSlaId(sla.getSlaId());
                metric.setMetricType("RESPONSE_TIME");
                metric.setValue(responseTime);
                metric.setCompliant(isCompliant);
                metric.setTimestamp(LocalDateTime.now());
                
                if (!isCompliant) {
                    SLABreach breach = new SLABreach();
                    breach.setSlaId(sla.getSlaId());
                    breach.setBreachType("RESPONSE_TIME");
                    breach.setExpectedValue(threshold);
                    breach.setActualValue(responseTime);
                    breach.setBreachTime(LocalDateTime.now());
                    breach.setSeverity(calculateResponseTimeSeverity(responseTime, threshold));
                    breach.setBreachDetail(String.format("%s response time for %s", percentile, operation));
                    
                    processBreach(breach, sla);
                }
                
                logger.debug("Response time check: service={}, operation={}, time={}ms, percentile={}, compliant={}", 
                           serviceName, operation, responseTime, percentile, isCompliant);
            }
            
        } catch (Exception e) {
            logger.error("Error handling response time check: {}", e.getMessage(), e);
            throw new SystemException("Failed to process response time check", e);
        }
    }

    @Transactional
    private void handleThroughputCheck(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String serviceName = messageNode.get("serviceName").asText();
            double throughput = messageNode.get("throughput").asDouble();
            String unit = messageNode.get("unit").asText();
            String period = messageNode.get("period").asText();
            
            List<SLA> affectedSLAs = findSLAsByService(serviceName);
            
            for (SLA sla : affectedSLAs) {
                boolean isCompliant = throughput >= sla.getMinThroughput();
                
                SLAMetric metric = new SLAMetric();
                metric.setSlaId(sla.getSlaId());
                metric.setMetricType("THROUGHPUT");
                metric.setValue(throughput);
                metric.setCompliant(isCompliant);
                metric.setTimestamp(LocalDateTime.now());
                
                if (!isCompliant) {
                    SLABreach breach = new SLABreach();
                    breach.setSlaId(sla.getSlaId());
                    breach.setBreachType("THROUGHPUT");
                    breach.setExpectedValue(sla.getMinThroughput());
                    breach.setActualValue(throughput);
                    breach.setBreachTime(LocalDateTime.now());
                    breach.setSeverity(throughput < sla.getMinThroughput() * 0.5 ? "HIGH" : "MEDIUM");
                    breach.setBreachDetail(String.format("Throughput: %.2f %s", throughput, unit));
                    
                    processBreach(breach, sla);
                }
                
                logger.info("Throughput check: service={}, throughput={} {}, target={}, compliant={}", 
                           serviceName, throughput, unit, sla.getMinThroughput(), isCompliant);
            }
            
        } catch (Exception e) {
            logger.error("Error handling throughput check: {}", e.getMessage(), e);
            throw new SystemException("Failed to process throughput check", e);
        }
    }

    @Transactional
    private void handleBreachDetection(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String slaId = messageNode.get("slaId").asText();
            String breachType = messageNode.get("breachType").asText();
            double expectedValue = messageNode.get("expectedValue").asDouble();
            double actualValue = messageNode.get("actualValue").asDouble();
            String severity = messageNode.get("severity").asText();
            
            SLA sla = activeSLAs.get(slaId);
            if (sla == null) {
                sla = slaMonitoringService.getSLA(slaId);
            }
            
            if (sla != null) {
                SLABreach breach = new SLABreach();
                breach.setSlaId(slaId);
                breach.setBreachType(breachType);
                breach.setExpectedValue(expectedValue);
                breach.setActualValue(actualValue);
                breach.setSeverity(severity);
                breach.setBreachTime(LocalDateTime.now());
                
                if (messageNode.has("impactedUsers")) {
                    breach.setImpactedUsers(messageNode.get("impactedUsers").asInt());
                }
                
                if (messageNode.has("estimatedRevenueLoss")) {
                    breach.setEstimatedRevenueLoss(messageNode.get("estimatedRevenueLoss").asDouble());
                }
                
                processBreach(breach, sla);
                
                logger.error("SLA breach detected: slaId={}, type={}, expected={}, actual={}, severity={}", 
                            slaId, breachType, expectedValue, actualValue, severity);
            }
            
        } catch (Exception e) {
            logger.error("Error handling breach detection: {}", e.getMessage(), e);
            throw new SystemException("Failed to process breach detection", e);
        }
    }

    @Transactional
    private void handleBreachResolution(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String breachId = messageNode.get("breachId").asText();
            String resolutionType = messageNode.get("resolutionType").asText();
            String resolvedBy = messageNode.get("resolvedBy").asText();
            LocalDateTime resolvedAt = LocalDateTime.parse(messageNode.get("resolvedAt").asText());
            
            SLABreach breach = slaMonitoringService.getBreach(breachId);
            if (breach != null) {
                breach.setResolved(true);
                breach.setResolutionType(resolutionType);
                breach.setResolvedBy(resolvedBy);
                breach.setResolvedAt(resolvedAt);
                
                long resolutionTimeMinutes = ChronoUnit.MINUTES.between(breach.getBreachTime(), resolvedAt);
                breach.setResolutionTimeMinutes(resolutionTimeMinutes);
                
                if (messageNode.has("compensationApplied")) {
                    breach.setCompensationApplied(messageNode.get("compensationApplied").asBoolean());
                    breach.setCompensationAmount(messageNode.get("compensationAmount").asDouble());
                }
                
                slaMonitoringService.updateBreach(breach);
                
                updateBreachHistory(breach);
                
                logger.info("SLA breach resolved: id={}, type={}, resolutionTime={} minutes", 
                           breachId, resolutionType, resolutionTimeMinutes);
            }
            
        } catch (Exception e) {
            logger.error("Error handling breach resolution: {}", e.getMessage(), e);
            throw new SystemException("Failed to process breach resolution", e);
        }
    }

    @Transactional
    private void handleComplianceReport(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String slaId = messageNode.get("slaId").asText();
            String reportPeriod = messageNode.get("reportPeriod").asText();
            LocalDateTime startTime = LocalDateTime.parse(messageNode.get("startTime").asText());
            LocalDateTime endTime = LocalDateTime.parse(messageNode.get("endTime").asText());
            
            SLA sla = activeSLAs.get(slaId);
            if (sla != null) {
                Map<String, Object> report = slaReportingService.generateComplianceReport(
                    sla, startTime, endTime);
                
                double complianceRate = (double) report.get("complianceRate");
                int totalBreaches = (int) report.get("totalBreaches");
                int totalChecks = (int) report.get("totalChecks");
                
                report.put("slaId", slaId);
                report.put("serviceName", sla.getServiceName());
                report.put("reportPeriod", reportPeriod);
                report.put("generatedAt", LocalDateTime.now().toString());
                
                if (messageNode.has("includeDetails") && messageNode.get("includeDetails").asBoolean()) {
                    report.put("breachDetails", getBreachDetails(slaId, startTime, endTime));
                    report.put("metricTrends", getMetricTrends(slaId, startTime, endTime));
                }
                
                kafkaTemplate.send("sla-compliance-reports", report);
                
                updateComplianceRate(slaId, complianceRate);
                
                logger.info("Compliance report generated: sla={}, period={}, compliance={}%, breaches={}/{}", 
                           slaId, reportPeriod, complianceRate * 100, totalBreaches, totalChecks);
            }
            
        } catch (Exception e) {
            logger.error("Error handling compliance report: {}", e.getMessage(), e);
            throw new SystemException("Failed to process compliance report", e);
        }
    }

    @Transactional
    private void handleCompensationRequest(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String slaId = messageNode.get("slaId").asText();
            String breachId = messageNode.get("breachId").asText();
            String customerId = messageNode.get("customerId").asText();
            String compensationType = messageNode.get("compensationType").asText();
            
            SLA sla = activeSLAs.get(slaId);
            SLABreach breach = slaMonitoringService.getBreach(breachId);
            
            if (sla != null && breach != null) {
                double compensationAmount = calculateCompensation(sla, breach, compensationType);
                
                Map<String, Object> compensation = new HashMap<>();
                compensation.put("compensationId", UUID.randomUUID().toString());
                compensation.put("slaId", slaId);
                compensation.put("breachId", breachId);
                compensation.put("customerId", customerId);
                compensation.put("compensationType", compensationType);
                compensation.put("amount", compensationAmount);
                compensation.put("currency", "USD");
                compensation.put("status", "PENDING");
                compensation.put("requestedAt", LocalDateTime.now().toString());
                
                if (autoCompensationEnabled && compensationAmount > 0) {
                    compensation.put("status", "APPROVED");
                    compensation.put("approvedAt", LocalDateTime.now().toString());
                    compensationService.processCompensation(compensation);
                }
                
                kafkaTemplate.send("sla-compensations", compensation);
                
                logger.info("Compensation request: sla={}, breach={}, customer={}, amount={}", 
                           slaId, breachId, customerId, compensationAmount);
            }
            
        } catch (Exception e) {
            logger.error("Error handling compensation request: {}", e.getMessage(), e);
            throw new SystemException("Failed to process compensation request", e);
        }
    }

    @Transactional
    private void handleSLASuspension(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String slaId = messageNode.get("slaId").asText();
            String reason = messageNode.get("reason").asText();
            LocalDateTime suspendedFrom = LocalDateTime.parse(messageNode.get("suspendedFrom").asText());
            LocalDateTime suspendedUntil = null;
            
            if (messageNode.has("suspendedUntil")) {
                suspendedUntil = LocalDateTime.parse(messageNode.get("suspendedUntil").asText());
            }
            
            SLA sla = activeSLAs.get(slaId);
            if (sla != null) {
                sla.setStatus(SLAStatus.SUSPENDED);
                sla.setSuspendedAt(suspendedFrom);
                sla.setSuspendedUntil(suspendedUntil);
                sla.setSuspensionReason(reason);
                
                slaMonitoringService.updateSLA(sla);
                
                Map<String, Object> suspension = new HashMap<>();
                suspension.put("slaId", slaId);
                suspension.put("serviceName", sla.getServiceName());
                suspension.put("reason", reason);
                suspension.put("suspendedFrom", suspendedFrom.toString());
                suspension.put("suspendedUntil", suspendedUntil != null ? suspendedUntil.toString() : "INDEFINITE");
                suspension.put("timestamp", LocalDateTime.now().toString());
                
                kafkaTemplate.send("sla-suspensions", suspension);
                
                logger.warn("SLA suspended: id={}, reason={}, from={}, until={}", 
                           slaId, reason, suspendedFrom, suspendedUntil);
            }
            
        } catch (Exception e) {
            logger.error("Error handling SLA suspension: {}", e.getMessage(), e);
            throw new SystemException("Failed to process SLA suspension", e);
        }
    }

    @Transactional
    private void handleSLAReinstatement(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String slaId = messageNode.get("slaId").asText();
            String reinstatedBy = messageNode.get("reinstatedBy").asText();
            String notes = messageNode.get("notes").asText();
            
            SLA sla = activeSLAs.get(slaId);
            if (sla != null && sla.getStatus() == SLAStatus.SUSPENDED) {
                sla.setStatus(SLAStatus.ACTIVE);
                sla.setReinstatedAt(LocalDateTime.now());
                sla.setReinstatedBy(reinstatedBy);
                
                slaMonitoringService.updateSLA(sla);
                
                resetSLAMetrics(slaId);
                
                Map<String, Object> reinstatement = new HashMap<>();
                reinstatement.put("slaId", slaId);
                reinstatement.put("serviceName", sla.getServiceName());
                reinstatement.put("reinstatedBy", reinstatedBy);
                reinstatement.put("notes", notes);
                reinstatement.put("timestamp", LocalDateTime.now().toString());
                
                kafkaTemplate.send("sla-reinstatements", reinstatement);
                
                logger.info("SLA reinstated: id={}, by={}", slaId, reinstatedBy);
            }
            
        } catch (Exception e) {
            logger.error("Error handling SLA reinstatement: {}", e.getMessage(), e);
            throw new SystemException("Failed to process SLA reinstatement", e);
        }
    }

    @Transactional
    private void handleCustomerImpact(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String slaId = messageNode.get("slaId").asText();
            String breachId = messageNode.get("breachId").asText();
            int impactedCustomers = messageNode.get("impactedCustomers").asInt();
            double revenueLoss = messageNode.get("revenueLoss").asDouble();
            String impactLevel = messageNode.get("impactLevel").asText();
            
            Map<String, Object> impact = new HashMap<>();
            impact.put("slaId", slaId);
            impact.put("breachId", breachId);
            impact.put("impactedCustomers", impactedCustomers);
            impact.put("revenueLoss", revenueLoss);
            impact.put("impactLevel", impactLevel);
            impact.put("timestamp", LocalDateTime.now().toString());
            
            if (messageNode.has("customerSegments")) {
                JsonNode segments = messageNode.get("customerSegments");
                Map<String, Integer> segmentBreakdown = new HashMap<>();
                segments.fields().forEachRemaining(entry -> {
                    segmentBreakdown.put(entry.getKey(), entry.getValue().asInt());
                });
                impact.put("segmentBreakdown", segmentBreakdown);
            }
            
            slaMonitoringService.recordCustomerImpact(impact);
            
            if ("HIGH".equals(impactLevel) || "CRITICAL".equals(impactLevel)) {
                triggerCustomerCommunication(slaId, impactedCustomers, impactLevel);
            }
            
            logger.warn("Customer impact recorded: sla={}, breach={}, customers={}, revenue={}, level={}", 
                       slaId, breachId, impactedCustomers, revenueLoss, impactLevel);
            
        } catch (Exception e) {
            logger.error("Error handling customer impact: {}", e.getMessage(), e);
            throw new SystemException("Failed to process customer impact", e);
        }
    }

    private void performSLAChecks() {
        try {
            logger.debug("Performing scheduled SLA checks for {} SLAs", activeSLAs.size());
            
            for (SLA sla : activeSLAs.values()) {
                if (sla.getStatus() == SLAStatus.ACTIVE) {
                    performSLACheck(sla);
                }
            }
            
            calculateOverallCompliance();
            
        } catch (Exception e) {
            logger.error("Error performing SLA checks: {}", e.getMessage(), e);
        }
    }

    private void performSLACheck(SLA sla) {
        Map<String, SLAMetric> metrics = slaMonitoringService.getCurrentMetrics(sla.getSlaId());
        
        for (SLAMetric metric : metrics.values()) {
            boolean isCompliant = checkMetricCompliance(sla, metric);
            
            if (!isCompliant) {
                checkForConsecutiveBreaches(sla, metric);
            }
        }
    }

    private void calculateComplianceRates() {
        try {
            for (SLA sla : activeSLAs.values()) {
                double complianceRate = slaMonitoringService.calculateComplianceRate(
                    sla.getSlaId(), rollingWindowMinutes);
                
                complianceRates.put(sla.getSlaId(), complianceRate);
                
                if (complianceRate < 0.95) {
                    createComplianceAlert(sla, complianceRate);
                }
            }
            
            calculateOverallCompliance();
            
        } catch (Exception e) {
            logger.error("Error calculating compliance rates: {}", e.getMessage(), e);
        }
    }

    private void calculateOverallCompliance() {
        if (!complianceRates.isEmpty()) {
            double avgCompliance = complianceRates.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(100.0);
            
            overallCompliance.set(avgCompliance);
        }
    }

    private void generateSLAReports() {
        try {
            logger.info("Generating SLA reports");
            
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusHours(reportingIntervalHours);
            
            Map<String, Object> report = slaReportingService.generateOverallReport(
                activeSLAs.values(), startTime, endTime);
            
            report.put("reportType", "PERIODIC");
            report.put("interval", reportingIntervalHours + " hours");
            report.put("generatedAt", LocalDateTime.now().toString());
            
            kafkaTemplate.send("sla-reports", report);
            
        } catch (Exception e) {
            logger.error("Error generating SLA reports: {}", e.getMessage(), e);
        }
    }

    private void performSLAPrediction() {
        if (!predictionEnabled) {
            return;
        }
        
        try {
            for (SLA sla : activeSLAs.values()) {
                Map<String, Object> prediction = slaMonitoringService.predictSLACompliance(
                    sla, predictionHorizonHours);
                
                double predictedCompliance = (double) prediction.get("predictedCompliance");
                
                if (predictedCompliance < sla.getAvailabilityTarget()) {
                    createPredictiveAlert(sla, prediction);
                }
            }
        } catch (Exception e) {
            logger.error("Error performing SLA prediction: {}", e.getMessage(), e);
        }
    }

    // Helper methods
    private boolean isValidSLAMessage(JsonNode messageNode) {
        return messageNode != null &&
               messageNode.has("eventType") && 
               StringUtils.hasText(messageNode.get("eventType").asText());
    }

    private void initializeMetrics(SLA sla) {
        SLAMetric availabilityMetric = new SLAMetric();
        availabilityMetric.setSlaId(sla.getSlaId());
        availabilityMetric.setMetricType("AVAILABILITY");
        availabilityMetric.setValue(100.0);
        availabilityMetric.setCompliant(true);
        availabilityMetric.setTimestamp(LocalDateTime.now());
        
        currentMetrics.put(sla.getSlaId() + ":AVAILABILITY", availabilityMetric);
    }

    private void updateRollingWindow(SLAMetric metric) {
        slaMonitoringService.updateRollingWindow(metric, rollingWindowMinutes);
    }

    private boolean checkMetricCompliance(SLA sla, SLAMetric metric) {
        switch (metric.getMetricType()) {
            case "AVAILABILITY":
                return metric.getValue() >= sla.getAvailabilityTarget();
            case "ERROR_RATE":
                return metric.getValue() <= sla.getMaxErrorRate();
            case "THROUGHPUT":
                return metric.getValue() >= sla.getMinThroughput();
            default:
                return true;
        }
    }

    private void handleNonCompliantMetric(SLA sla, SLAMetric metric) {
        logger.warn("Non-compliant metric: sla={}, type={}, value={}", 
                   sla.getSlaId(), metric.getMetricType(), metric.getValue());
        
        checkForConsecutiveBreaches(sla, metric);
    }

    private List<SLA> findSLAsByService(String serviceName) {
        return activeSLAs.values().stream()
            .filter(sla -> sla.getServiceName().equals(serviceName))
            .collect(Collectors.toList());
    }

    private String calculateBreachSeverity(double expected, double actual) {
        double deviation = Math.abs(expected - actual) / expected;
        
        if (deviation > 0.5) return "CRITICAL";
        if (deviation > 0.2) return "HIGH";
        if (deviation > 0.1) return "MEDIUM";
        return "LOW";
    }

    private void processBreach(SLABreach breach, SLA sla) {
        breach.setBreachId(UUID.randomUUID().toString());
        
        breachCounter.increment();
        
        String historyKey = breach.getSlaId();
        breachHistory.computeIfAbsent(historyKey, k -> new CopyOnWriteArrayList<>()).add(breach);
        
        slaMonitoringService.saveBreach(breach);
        
        createBreachAlert(breach, sla);
        
        if (autoCompensationEnabled && isCompensationEligible(breach)) {
            initiateAutoCompensation(breach, sla);
        }
        
        updateBreachHistory(breach);
    }

    private long getResponseTimeThreshold(SLA sla, String percentile) {
        switch (percentile) {
            case "P50":
                return sla.getResponseTimeP50();
            case "P95":
                return sla.getResponseTimeP95();
            case "P99":
                return sla.getResponseTimeP99();
            default:
                return sla.getResponseTimeP99();
        }
    }

    private String calculateResponseTimeSeverity(long actual, long threshold) {
        double ratio = (double) actual / threshold;
        
        if (ratio > 3) return "CRITICAL";
        if (ratio > 2) return "HIGH";
        if (ratio > 1.5) return "MEDIUM";
        return "LOW";
    }

    private void checkForConsecutiveBreaches(SLA sla, SLAMetric metric) {
        String key = sla.getSlaId() + ":" + metric.getMetricType();
        List<SLABreach> recentBreaches = slaMonitoringService.getRecentBreaches(
            sla.getSlaId(), metric.getMetricType(), breachThresholdConsecutive);
        
        if (recentBreaches.size() >= breachThresholdConsecutive) {
            escalateBreach(sla, metric, recentBreaches);
        }
    }

    private void escalateBreach(SLA sla, SLAMetric metric, List<SLABreach> breaches) {
        Map<String, Object> escalation = new HashMap<>();
        escalation.put("slaId", sla.getSlaId());
        escalation.put("metricType", metric.getMetricType());
        escalation.put("consecutiveBreaches", breaches.size());
        escalation.put("severity", "CRITICAL");
        escalation.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("sla-breach-escalations", escalation);
    }

    private void updateBreachHistory(SLABreach breach) {
        // Keep breach history limited to avoid memory issues
        String key = breach.getSlaId();
        List<SLABreach> history = breachHistory.get(key);
        if (history != null && history.size() > 100) {
            history.remove(0);
        }
    }

    private List<Map<String, Object>> getBreachDetails(String slaId, LocalDateTime start, LocalDateTime end) {
        return slaMonitoringService.getBreachDetails(slaId, start, end);
    }

    private Map<String, Object> getMetricTrends(String slaId, LocalDateTime start, LocalDateTime end) {
        return slaMonitoringService.getMetricTrends(slaId, start, end);
    }

    private void updateComplianceRate(String slaId, double rate) {
        complianceRates.put(slaId, rate);
    }

    private double calculateCompensation(SLA sla, SLABreach breach, String compensationType) {
        Map<String, Double> penalties = sla.getPenaltyStructure();
        
        if (penalties != null && penalties.containsKey(breach.getBreachType())) {
            double basePenalty = penalties.get(breach.getBreachType());
            
            switch (breach.getSeverity()) {
                case "CRITICAL":
                    return basePenalty * 2.0;
                case "HIGH":
                    return basePenalty * 1.5;
                case "MEDIUM":
                    return basePenalty;
                default:
                    return basePenalty * 0.5;
            }
        }
        
        return 0.0;
    }

    private boolean isCompensationEligible(SLABreach breach) {
        return "HIGH".equals(breach.getSeverity()) || "CRITICAL".equals(breach.getSeverity());
    }

    private void initiateAutoCompensation(SLABreach breach, SLA sla) {
        Map<String, Object> compensation = new HashMap<>();
        compensation.put("breachId", breach.getBreachId());
        compensation.put("slaId", sla.getSlaId());
        compensation.put("autoInitiated", true);
        compensation.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("compensation-requests", compensation);
    }

    private void resetSLAMetrics(String slaId) {
        currentMetrics.entrySet().removeIf(entry -> entry.getKey().startsWith(slaId + ":"));
        initializeMetrics(activeSLAs.get(slaId));
    }

    private void triggerCustomerCommunication(String slaId, int impactedCustomers, String impactLevel) {
        Map<String, Object> communication = new HashMap<>();
        communication.put("slaId", slaId);
        communication.put("impactedCustomers", impactedCustomers);
        communication.put("impactLevel", impactLevel);
        communication.put("action", "NOTIFY_CUSTOMERS");
        communication.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("customer-communications", communication);
    }

    private void createComplianceAlert(SLA sla, double complianceRate) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "SLA_COMPLIANCE");
        alert.put("slaId", sla.getSlaId());
        alert.put("serviceName", sla.getServiceName());
        alert.put("complianceRate", complianceRate);
        alert.put("threshold", 0.95);
        alert.put("severity", complianceRate < 0.9 ? "HIGH" : "MEDIUM");
        alert.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("sla-alerts", alert);
    }

    private void createBreachAlert(SLABreach breach, SLA sla) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "SLA_BREACH");
        alert.put("breachId", breach.getBreachId());
        alert.put("slaId", sla.getSlaId());
        alert.put("serviceName", sla.getServiceName());
        alert.put("breachType", breach.getBreachType());
        alert.put("severity", breach.getSeverity());
        alert.put("expectedValue", breach.getExpectedValue());
        alert.put("actualValue", breach.getActualValue());
        alert.put("timestamp", breach.getBreachTime().toString());
        
        kafkaTemplate.send("sla-breach-alerts", alert);
    }

    private void createPredictiveAlert(SLA sla, Map<String, Object> prediction) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "SLA_PREDICTION");
        alert.put("slaId", sla.getSlaId());
        alert.put("serviceName", sla.getServiceName());
        alert.put("prediction", prediction);
        alert.put("severity", "WARNING");
        alert.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("sla-predictive-alerts", alert);
    }

    private void handleProcessingError(String message, String topic, Exception error, 
                                     String correlationId, String traceId, Acknowledgment acknowledgment) {
        try {
            errorCounter.increment();
            logger.error("Error processing SLA monitoring message: {}", error.getMessage(), error);
            
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
        logger.error("Circuit breaker fallback triggered for SLA monitoring consumer: {}", ex.getMessage());
        
        errorCounter.increment();
        sendToDlq(message, topic, "Circuit breaker fallback: " + ex.getMessage(), ex, correlationId, traceId);
        acknowledgment.acknowledge();
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down SLAMonitoringConsumer");
        
        generateSLAReports();
        
        scheduledExecutor.shutdown();
        slaProcessingExecutor.shutdown();
        
        try {
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            if (!slaProcessingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                slaProcessingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Error shutting down executors", e);
            scheduledExecutor.shutdownNow();
            slaProcessingExecutor.shutdownNow();
        }
        
        logger.info("SLAMonitoringConsumer shutdown complete. Total SLA checks: {}", 
                   totalSLAChecks.get());
    }
}