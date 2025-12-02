package com.waqiti.monitoring.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.monitoring.entity.QueueMetrics;
import com.waqiti.monitoring.repository.QueueMetricsRepository;
import com.waqiti.monitoring.service.*;
import com.waqiti.monitoring.model.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Component
public class MessageQueueMonitoringConsumer {

    private static final String TOPIC = "message-queue-monitoring";
    private static final String GROUP_ID = "monitoring-queue-group";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 500;
    private static final int QUEUE_DEPTH_THRESHOLD = 10000;
    private static final double CONSUMER_LAG_THRESHOLD_MS = 5000;
    private static final double THROUGHPUT_DEGRADATION_THRESHOLD = 0.20;
    private static final int DLQ_MESSAGE_THRESHOLD = 100;
    private static final double PRODUCER_FAILURE_RATE_THRESHOLD = 0.05;
    private static final int PARTITION_SKEW_THRESHOLD = 30;
    private static final double MESSAGE_PROCESSING_TIME_THRESHOLD_MS = 1000;
    private static final int REBALANCE_FREQUENCY_THRESHOLD = 5;
    private static final double BACKPRESSURE_THRESHOLD = 0.80;
    private static final int POISON_MESSAGE_THRESHOLD = 10;
    private static final double CONSUMER_UTILIZATION_THRESHOLD = 0.90;
    private static final int TOPIC_CREATION_TIME_THRESHOLD_MS = 5000;
    private static final double MESSAGE_SIZE_THRESHOLD_KB = 100;
    private static final int BROKER_CONNECTION_LOSS_THRESHOLD = 3;
    private static final double OFFSET_COMMIT_FAILURE_THRESHOLD = 0.10;
    private static final int ANALYSIS_WINDOW_MINUTES = 10;
    
    private final QueueMetricsRepository metricsRepository;
    private final AlertService alertService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final QueueOptimizationService optimizationService;
    private final ConsumerGroupAnalysisService consumerGroupService;
    private final PartitionManagementService partitionService;
    private final MessageFlowAnalysisService flowAnalysisService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public MessageQueueMonitoringConsumer(
            QueueMetricsRepository metricsRepository,
            AlertService alertService,
            MetricsService metricsService,
            NotificationService notificationService,
            QueueOptimizationService optimizationService,
            ConsumerGroupAnalysisService consumerGroupService,
            PartitionManagementService partitionService,
            MessageFlowAnalysisService flowAnalysisService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.metricsRepository = metricsRepository;
        this.alertService = alertService;
        this.metricsService = metricsService;
        this.notificationService = notificationService;
        this.optimizationService = optimizationService;
        this.consumerGroupService = consumerGroupService;
        this.partitionService = partitionService;
        this.flowAnalysisService = flowAnalysisService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }
    
    private final Map<String, QueueMonitoringState> queueStates = new ConcurrentHashMap<>();
    private final Map<String, ConsumerGroupTracker> consumerTrackers = new ConcurrentHashMap<>();
    private final Map<String, ProducerMetrics> producerMetrics = new ConcurrentHashMap<>();
    private final Map<String, PartitionAnalyzer> partitionAnalyzers = new ConcurrentHashMap<>();
    private final Map<String, ThroughputMonitor> throughputMonitors = new ConcurrentHashMap<>();
    private final Map<String, BackpressureDetector> backpressureDetectors = new ConcurrentHashMap<>();
    private final Map<String, MessagePatternAnalyzer> patternAnalyzers = new ConcurrentHashMap<>();
    private final Map<String, DlqMonitor> dlqMonitors = new ConcurrentHashMap<>();
    private final Map<String, RebalanceTracker> rebalanceTrackers = new ConcurrentHashMap<>();
    private final Map<String, OffsetManager> offsetManagers = new ConcurrentHashMap<>();
    private final Map<String, BrokerHealthMonitor> brokerMonitors = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(5);
    private final ExecutorService analysisExecutor = Executors.newFixedThreadPool(10);
    private final BlockingQueue<QueueEvent> eventQueue = new LinkedBlockingQueue<>(10000);
    
    private Counter processedEventsCounter;
    private Counter errorCounter;
    private Timer processingTimer;
    private Gauge queueSizeGauge;
    private Gauge consumerLagGauge;
    private Gauge throughputGauge;
    private Gauge dlqSizeGauge;
    
    @PostConstruct
    public void init() {
        initializeMetrics();
        startBackgroundTasks();
        initializeMonitors();
        establishBaselines();
        log.info("MessageQueueMonitoringConsumer initialized successfully");
    }
    
    private void initializeMetrics() {
        processedEventsCounter = meterRegistry.counter("queue.monitoring.events.processed");
        errorCounter = meterRegistry.counter("queue.monitoring.events.errors");
        processingTimer = meterRegistry.timer("queue.monitoring.processing.time");
        queueSizeGauge = meterRegistry.gauge("queue.monitoring.queue.size", eventQueue, Queue::size);
        
        consumerLagGauge = meterRegistry.gauge("queue.monitoring.consumer.lag", 
            consumerTrackers, trackers -> calculateAverageConsumerLag(trackers));
        throughputGauge = meterRegistry.gauge("queue.monitoring.throughput",
            throughputMonitors, monitors -> calculateAverageThroughput(monitors));
        dlqSizeGauge = meterRegistry.gauge("queue.monitoring.dlq.size",
            dlqMonitors, monitors -> calculateTotalDlqSize(monitors));
    }
    
    private void startBackgroundTasks() {
        scheduledExecutor.scheduleAtFixedRate(this::analyzeQueueHealth, 1, 1, TimeUnit.MINUTES);
        scheduledExecutor.scheduleAtFixedRate(this::detectAnomalies, 2, 2, TimeUnit.MINUTES);
        scheduledExecutor.scheduleAtFixedRate(this::generateOptimizationRecommendations, 5, 5, TimeUnit.MINUTES);
        scheduledExecutor.scheduleAtFixedRate(this::cleanupOldData, 1, 6, TimeUnit.HOURS);
        scheduledExecutor.scheduleAtFixedRate(this::performHealthChecks, 30, 30, TimeUnit.SECONDS);
    }
    
    private void initializeMonitors() {
        Arrays.asList("kafka", "rabbitmq", "sqs", "activemq", "pulsar").forEach(queueType -> {
            consumerTrackers.put(queueType, new ConsumerGroupTracker(queueType));
            producerMetrics.put(queueType, new ProducerMetrics(queueType));
            partitionAnalyzers.put(queueType, new PartitionAnalyzer(queueType));
            throughputMonitors.put(queueType, new ThroughputMonitor(queueType));
            backpressureDetectors.put(queueType, new BackpressureDetector(queueType));
            patternAnalyzers.put(queueType, new MessagePatternAnalyzer(queueType));
            dlqMonitors.put(queueType, new DlqMonitor(queueType));
            rebalanceTrackers.put(queueType, new RebalanceTracker(queueType));
            offsetManagers.put(queueType, new OffsetManager(queueType));
            brokerMonitors.put(queueType, new BrokerHealthMonitor(queueType));
            queueStates.put(queueType, new QueueMonitoringState(queueType));
        });
    }
    
    private void establishBaselines() {
        LocalDateTime oneWeekAgo = LocalDateTime.now().minusWeeks(1);
        metricsRepository.findByTimestampAfter(oneWeekAgo)
            .forEach(metric -> {
                String queueType = metric.getQueueType();
                QueueMonitoringState state = queueStates.get(queueType);
                if (state != null) {
                    state.updateBaseline(metric);
                }
            });
        log.info("Established baselines for {} queue types", queueStates.size());
    }
    
    @KafkaListener(
        topics = TOPIC,
        groupId = GROUP_ID,
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "queueMonitoring", fallbackMethod = "handleMessageFallback")
    @Retry(name = "queueMonitoring", fallbackMethod = "handleMessageFallback")
    public void consume(
            @Payload ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        MDC.put("topic", topic);
        MDC.put("partition", String.valueOf(partition));
        MDC.put("offset", String.valueOf(offset));
        MDC.put("traceId", UUID.randomUUID().toString());
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.debug("Processing queue monitoring event from partition {} offset {}", partition, offset);
            
            JsonNode eventData = objectMapper.readTree(record.value());
            String eventType = eventData.get("eventType").asText();
            
            processEventByType(eventType, eventData);
            
            processedEventsCounter.increment();
            acknowledgment.acknowledge();
            
            sample.stop(processingTimer);
            
        } catch (Exception e) {
            log.error("Error processing queue monitoring event: {}", e.getMessage(), e);
            errorCounter.increment();
            handleProcessingError(record, e, acknowledgment);
        } finally {
            MDC.clear();
        }
    }
    
    private void processEventByType(String eventType, JsonNode eventData) {
        try {
            switch (eventType) {
                case "QUEUE_DEPTH":
                    processQueueDepth(eventData);
                    break;
                case "CONSUMER_LAG":
                    processConsumerLag(eventData);
                    break;
                case "MESSAGE_PRODUCTION":
                    processMessageProduction(eventData);
                    break;
                case "MESSAGE_CONSUMPTION":
                    processMessageConsumption(eventData);
                    break;
                case "DLQ_MESSAGE":
                    processDlqMessage(eventData);
                    break;
                case "PARTITION_STATUS":
                    processPartitionStatus(eventData);
                    break;
                case "CONSUMER_GROUP_REBALANCE":
                    processConsumerGroupRebalance(eventData);
                    break;
                case "THROUGHPUT_METRICS":
                    processThroughputMetrics(eventData);
                    break;
                case "BACKPRESSURE_DETECTED":
                    processBackpressureDetected(eventData);
                    break;
                case "MESSAGE_PROCESSING_TIME":
                    processMessageProcessingTime(eventData);
                    break;
                case "POISON_MESSAGE":
                    processPoisonMessage(eventData);
                    break;
                case "BROKER_CONNECTION":
                    processBrokerConnection(eventData);
                    break;
                case "OFFSET_COMMIT":
                    processOffsetCommit(eventData);
                    break;
                case "TOPIC_MANAGEMENT":
                    processTopicManagement(eventData);
                    break;
                case "QUEUE_ANOMALY":
                    processQueueAnomaly(eventData);
                    break;
                default:
                    log.warn("Unknown queue monitoring event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Error processing event type {}: {}", eventType, e.getMessage(), e);
            errorCounter.increment();
        }
    }
    
    private void processQueueDepth(JsonNode eventData) {
        String queueType = eventData.get("queueType").asText();
        String queueName = eventData.get("queueName").asText();
        int depth = eventData.get("depth").asInt();
        int maxDepth = eventData.get("maxDepth").asInt();
        double growthRate = eventData.get("growthRate").asDouble();
        long timestamp = eventData.get("timestamp").asLong();
        
        updateQueueState(queueType, queueName, state -> {
            state.updateQueueDepth(depth, maxDepth);
            state.updateGrowthRate(growthRate);
        });
        
        if (depth > QUEUE_DEPTH_THRESHOLD) {
            String message = String.format("High queue depth detected for %s/%s: %d messages (max: %d)", 
                queueType, queueName, depth, maxDepth);
            alertService.createAlert("HIGH_QUEUE_DEPTH", "WARNING", message,
                Map.of("queueType", queueType, "queueName", queueName, 
                       "depth", depth, "growthRate", growthRate));
            
            analyzeQueueDepthCause(queueType, queueName, depth, growthRate);
        }
        
        if (growthRate > 0.5) {
            handleRapidQueueGrowth(queueType, queueName, depth, growthRate);
        }
        
        metricsService.recordQueueDepth(queueType, queueName, depth);
        
        QueueMetrics metrics = QueueMetrics.builder()
            .queueType(queueType)
            .queueName(queueName)
            .depth(depth)
            .growthRate(growthRate)
            .timestamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()))
            .build();
        
        metricsRepository.save(metrics);
    }
    
    private void processConsumerLag(JsonNode eventData) {
        String queueType = eventData.get("queueType").asText();
        String consumerGroup = eventData.get("consumerGroup").asText();
        String topic = eventData.get("topic").asText();
        long lagMessages = eventData.get("lagMessages").asLong();
        long lagTimeMs = eventData.get("lagTimeMs").asLong();
        JsonNode partitionLags = eventData.get("partitionLags");
        long timestamp = eventData.get("timestamp").asLong();
        
        ConsumerGroupTracker tracker = consumerTrackers.get(queueType);
        if (tracker != null) {
            tracker.updateConsumerLag(consumerGroup, topic, lagMessages, lagTimeMs, partitionLags, timestamp);
            
            if (lagTimeMs > CONSUMER_LAG_THRESHOLD_MS) {
                String message = String.format("High consumer lag for %s/%s: %d messages (%.2f seconds behind)", 
                    consumerGroup, topic, lagMessages, lagTimeMs / 1000.0);
                alertService.createAlert("HIGH_CONSUMER_LAG", "WARNING", message,
                    Map.of("queueType", queueType, "consumerGroup", consumerGroup, 
                           "topic", topic, "lagMessages", lagMessages, "lagTimeMs", lagTimeMs));
                
                analyzeConsumerLagCause(queueType, consumerGroup, topic, lagMessages, lagTimeMs);
            }
            
            detectLagTrend(tracker, consumerGroup, topic, lagMessages);
        }
        
        consumerGroupService.analyzeConsumerGroup(queueType, consumerGroup, topic, partitionLags);
        
        updateQueueState(queueType, topic, state -> {
            state.updateConsumerLag(consumerGroup, lagMessages, lagTimeMs);
        });
        
        metricsService.recordConsumerLag(queueType, consumerGroup, topic, lagMessages, lagTimeMs);
    }
    
    private void processMessageProduction(JsonNode eventData) {
        String queueType = eventData.get("queueType").asText();
        String producerId = eventData.get("producerId").asText();
        String topic = eventData.get("topic").asText();
        int messageCount = eventData.get("messageCount").asInt();
        long totalBytes = eventData.get("totalBytes").asLong();
        int failedCount = eventData.get("failedCount").asInt();
        double avgLatencyMs = eventData.get("avgLatencyMs").asDouble();
        long timestamp = eventData.get("timestamp").asLong();
        
        ProducerMetrics metrics = producerMetrics.get(queueType);
        if (metrics != null) {
            metrics.recordProduction(producerId, topic, messageCount, totalBytes, 
                                    failedCount, avgLatencyMs, timestamp);
            
            double failureRate = (double) failedCount / messageCount;
            if (failureRate > PRODUCER_FAILURE_RATE_THRESHOLD) {
                String message = String.format("High producer failure rate for %s on %s: %.2f%% (%d/%d failed)", 
                    producerId, topic, failureRate * 100, failedCount, messageCount);
                alertService.createAlert("HIGH_PRODUCER_FAILURE", "ERROR", message,
                    Map.of("queueType", queueType, "producerId", producerId, 
                           "topic", topic, "failureRate", failureRate));
                
                investigateProducerFailures(queueType, producerId, topic, failedCount);
            }
        }
        
        updateQueueState(queueType, topic, state -> {
            state.recordProduction(messageCount, totalBytes, failedCount);
        });
        
        metricsService.recordMessageProduction(queueType, producerId, topic, messageCount, failedCount);
    }
    
    private void processMessageConsumption(JsonNode eventData) {
        String queueType = eventData.get("queueType").asText();
        String consumerGroup = eventData.get("consumerGroup").asText();
        String topic = eventData.get("topic").asText();
        int messageCount = eventData.get("messageCount").asInt();
        long totalBytes = eventData.get("totalBytes").asLong();
        int failedCount = eventData.get("failedCount").asInt();
        double avgProcessingTimeMs = eventData.get("avgProcessingTimeMs").asDouble();
        long timestamp = eventData.get("timestamp").asLong();
        
        ConsumerGroupTracker tracker = consumerTrackers.get(queueType);
        if (tracker != null) {
            tracker.recordConsumption(consumerGroup, topic, messageCount, totalBytes, 
                                    failedCount, avgProcessingTimeMs, timestamp);
            
            if (avgProcessingTimeMs > MESSAGE_PROCESSING_TIME_THRESHOLD_MS) {
                String message = String.format("Slow message processing for %s on %s: %.2fms avg", 
                    consumerGroup, topic, avgProcessingTimeMs);
                alertService.createAlert("SLOW_MESSAGE_PROCESSING", "WARNING", message,
                    Map.of("queueType", queueType, "consumerGroup", consumerGroup, 
                           "topic", topic, "avgProcessingTimeMs", avgProcessingTimeMs));
                
                analyzeSlowProcessing(queueType, consumerGroup, topic, avgProcessingTimeMs);
            }
            
            double utilization = tracker.getConsumerUtilization(consumerGroup);
            if (utilization > CONSUMER_UTILIZATION_THRESHOLD) {
                handleHighConsumerUtilization(queueType, consumerGroup, utilization);
            }
        }
        
        updateQueueState(queueType, topic, state -> {
            state.recordConsumption(messageCount, totalBytes, failedCount);
        });
        
        metricsService.recordMessageConsumption(queueType, consumerGroup, topic, messageCount, failedCount);
    }
    
    private void processDlqMessage(JsonNode eventData) {
        String queueType = eventData.get("queueType").asText();
        String dlqName = eventData.get("dlqName").asText();
        String originalTopic = eventData.get("originalTopic").asText();
        String messageId = eventData.get("messageId").asText();
        String errorReason = eventData.get("errorReason").asText();
        int retryCount = eventData.get("retryCount").asInt();
        long timestamp = eventData.get("timestamp").asLong();
        
        DlqMonitor monitor = dlqMonitors.get(queueType);
        if (monitor != null) {
            monitor.recordDlqMessage(dlqName, originalTopic, messageId, errorReason, 
                                    retryCount, timestamp);
            
            int dlqSize = monitor.getDlqSize(dlqName);
            if (dlqSize > DLQ_MESSAGE_THRESHOLD) {
                String message = String.format("High DLQ message count for %s: %d messages", 
                    dlqName, dlqSize);
                alertService.createAlert("HIGH_DLQ_COUNT", "WARNING", message,
                    Map.of("queueType", queueType, "dlqName", dlqName, 
                           "dlqSize", dlqSize, "originalTopic", originalTopic));
                
                analyzeDlqPattern(monitor, dlqName, originalTopic);
            }
            
            if (retryCount >= MAX_RETRY_ATTEMPTS) {
                handleMaxRetriesExceeded(queueType, dlqName, messageId, errorReason);
            }
        }
        
        updateQueueState(queueType, dlqName, state -> {
            state.recordDlqMessage(originalTopic, errorReason);
        });
        
        metricsService.recordDlqMessage(queueType, dlqName, originalTopic);
    }
    
    private void processPartitionStatus(JsonNode eventData) {
        String queueType = eventData.get("queueType").asText();
        String topic = eventData.get("topic").asText();
        int partitionId = eventData.get("partitionId").asInt();
        String leader = eventData.get("leader").asText();
        JsonNode replicas = eventData.get("replicas");
        JsonNode isr = eventData.get("isr");
        long endOffset = eventData.get("endOffset").asLong();
        int underReplicatedCount = eventData.get("underReplicatedCount").asInt();
        long timestamp = eventData.get("timestamp").asLong();
        
        PartitionAnalyzer analyzer = partitionAnalyzers.get(queueType);
        if (analyzer != null) {
            analyzer.updatePartitionStatus(topic, partitionId, leader, replicas, isr, 
                                         endOffset, underReplicatedCount, timestamp);
            
            if (underReplicatedCount > 0) {
                String message = String.format("Under-replicated partitions for %s: %d partitions", 
                    topic, underReplicatedCount);
                alertService.createAlert("UNDER_REPLICATED_PARTITIONS", "WARNING", message,
                    Map.of("queueType", queueType, "topic", topic, 
                           "underReplicatedCount", underReplicatedCount));
            }
            
            detectPartitionSkew(analyzer, topic);
        }
        
        partitionService.analyzePartition(queueType, topic, partitionId, endOffset);
        
        updateQueueState(queueType, topic, state -> {
            state.updatePartitionStatus(partitionId, leader, isr.size());
        });
        
        metricsService.recordPartitionStatus(queueType, topic, partitionId, isr.size());
    }
    
    private void processConsumerGroupRebalance(JsonNode eventData) {
        String queueType = eventData.get("queueType").asText();
        String consumerGroup = eventData.get("consumerGroup").asText();
        String rebalanceReason = eventData.get("reason").asText();
        int affectedPartitions = eventData.get("affectedPartitions").asInt();
        long durationMs = eventData.get("durationMs").asLong();
        JsonNode memberAssignments = eventData.get("memberAssignments");
        long timestamp = eventData.get("timestamp").asLong();
        
        RebalanceTracker tracker = rebalanceTrackers.get(queueType);
        if (tracker != null) {
            tracker.recordRebalance(consumerGroup, rebalanceReason, affectedPartitions, 
                                   durationMs, memberAssignments, timestamp);
            
            int rebalanceFrequency = tracker.getRebalanceFrequency(consumerGroup);
            if (rebalanceFrequency > REBALANCE_FREQUENCY_THRESHOLD) {
                String message = String.format("Frequent rebalancing for consumer group %s: %d rebalances in window", 
                    consumerGroup, rebalanceFrequency);
                alertService.createAlert("FREQUENT_REBALANCING", "WARNING", message,
                    Map.of("queueType", queueType, "consumerGroup", consumerGroup, 
                           "frequency", rebalanceFrequency, "reason", rebalanceReason));
                
                investigateRebalancing(queueType, consumerGroup, rebalanceReason);
            }
            
            if (durationMs > 30000) {
                handleLongRebalance(queueType, consumerGroup, durationMs);
            }
        }
        
        consumerGroupService.handleRebalance(queueType, consumerGroup, memberAssignments);
        
        updateQueueState(queueType, consumerGroup, state -> {
            state.recordRebalance(rebalanceReason, durationMs);
        });
        
        metricsService.recordRebalance(queueType, consumerGroup, durationMs);
    }
    
    private void processThroughputMetrics(JsonNode eventData) {
        String queueType = eventData.get("queueType").asText();
        String topic = eventData.get("topic").asText();
        double incomingRate = eventData.get("incomingRate").asDouble();
        double outgoingRate = eventData.get("outgoingRate").asDouble();
        double bytesInRate = eventData.get("bytesInRate").asDouble();
        double bytesOutRate = eventData.get("bytesOutRate").asDouble();
        long timestamp = eventData.get("timestamp").asLong();
        
        ThroughputMonitor monitor = throughputMonitors.get(queueType);
        if (monitor != null) {
            monitor.updateThroughput(topic, incomingRate, outgoingRate, 
                                    bytesInRate, bytesOutRate, timestamp);
            
            double degradation = monitor.getThroughputDegradation(topic);
            if (degradation > THROUGHPUT_DEGRADATION_THRESHOLD) {
                String message = String.format("Throughput degradation for %s: %.2f%% decrease", 
                    topic, degradation * 100);
                alertService.createAlert("THROUGHPUT_DEGRADATION", "WARNING", message,
                    Map.of("queueType", queueType, "topic", topic, 
                           "degradation", degradation));
                
                analyzeThroughputBottleneck(queueType, topic, incomingRate, outgoingRate);
            }
            
            if (incomingRate > outgoingRate * 1.5) {
                handleBacklogBuildup(queueType, topic, incomingRate, outgoingRate);
            }
        }
        
        flowAnalysisService.analyzeMessageFlow(queueType, topic, incomingRate, outgoingRate);
        
        updateQueueState(queueType, topic, state -> {
            state.updateThroughput(incomingRate, outgoingRate);
        });
        
        metricsService.recordThroughput(queueType, topic, incomingRate, outgoingRate);
    }
    
    private void processBackpressureDetected(JsonNode eventData) {
        String queueType = eventData.get("queueType").asText();
        String topic = eventData.get("topic").asText();
        String component = eventData.get("component").asText();
        double pressure = eventData.get("pressure").asDouble();
        String cause = eventData.get("cause").asText();
        JsonNode metrics = eventData.get("metrics");
        long timestamp = eventData.get("timestamp").asLong();
        
        BackpressureDetector detector = backpressureDetectors.get(queueType);
        if (detector != null) {
            detector.recordBackpressure(topic, component, pressure, cause, metrics, timestamp);
            
            if (pressure > BACKPRESSURE_THRESHOLD) {
                String message = String.format("High backpressure detected for %s/%s: %.2f", 
                    topic, component, pressure);
                alertService.createAlert("HIGH_BACKPRESSURE", "WARNING", message,
                    Map.of("queueType", queueType, "topic", topic, 
                           "component", component, "pressure", pressure, "cause", cause));
                
                mitigateBackpressure(queueType, topic, component, pressure, cause);
            }
        }
        
        updateQueueState(queueType, topic, state -> {
            state.updateBackpressure(component, pressure);
        });
        
        metricsService.recordBackpressure(queueType, topic, component, pressure);
    }
    
    private void processMessageProcessingTime(JsonNode eventData) {
        String queueType = eventData.get("queueType").asText();
        String consumerGroup = eventData.get("consumerGroup").asText();
        String topic = eventData.get("topic").asText();
        double p50 = eventData.get("p50").asDouble();
        double p95 = eventData.get("p95").asDouble();
        double p99 = eventData.get("p99").asDouble();
        double max = eventData.get("max").asDouble();
        long timestamp = eventData.get("timestamp").asLong();
        
        ConsumerGroupTracker tracker = consumerTrackers.get(queueType);
        if (tracker != null) {
            tracker.updateProcessingTimePercentiles(consumerGroup, topic, p50, p95, p99, max, timestamp);
            
            if (p95 > MESSAGE_PROCESSING_TIME_THRESHOLD_MS) {
                String message = String.format("High message processing time for %s/%s: p95=%.2fms, p99=%.2fms", 
                    consumerGroup, topic, p95, p99);
                alertService.createAlert("HIGH_PROCESSING_TIME", "WARNING", message,
                    Map.of("queueType", queueType, "consumerGroup", consumerGroup, 
                           "topic", topic, "p95", p95, "p99", p99));
                
                optimizeProcessingTime(queueType, consumerGroup, topic, p95, p99);
            }
        }
        
        updateQueueState(queueType, topic, state -> {
            state.updateProcessingTimePercentiles(p50, p95, p99);
        });
        
        metricsService.recordProcessingTime(queueType, consumerGroup, topic, p95);
    }
    
    private void processPoisonMessage(JsonNode eventData) {
        String queueType = eventData.get("queueType").asText();
        String topic = eventData.get("topic").asText();
        String messageId = eventData.get("messageId").asText();
        int failureCount = eventData.get("failureCount").asInt();
        String lastError = eventData.get("lastError").asText();
        JsonNode messageHeaders = eventData.get("messageHeaders");
        long timestamp = eventData.get("timestamp").asLong();
        
        updateQueueState(queueType, topic, state -> {
            state.recordPoisonMessage(messageId, failureCount, lastError);
        });
        
        if (failureCount > POISON_MESSAGE_THRESHOLD) {
            String message = String.format("Poison message detected in %s: %s failed %d times", 
                topic, messageId, failureCount);
            alertService.createAlert("POISON_MESSAGE", "ERROR", message,
                Map.of("queueType", queueType, "topic", topic, 
                       "messageId", messageId, "failureCount", failureCount));
            
            handlePoisonMessage(queueType, topic, messageId, lastError, messageHeaders);
        }
        
        metricsService.recordPoisonMessage(queueType, topic, messageId);
    }
    
    private void processBrokerConnection(JsonNode eventData) {
        String queueType = eventData.get("queueType").asText();
        String brokerId = eventData.get("brokerId").asText();
        String connectionStatus = eventData.get("status").asText();
        int activeConnections = eventData.get("activeConnections").asInt();
        int failedAttempts = eventData.get("failedAttempts").asInt();
        long lastSuccessfulConnection = eventData.get("lastSuccessfulConnection").asLong();
        long timestamp = eventData.get("timestamp").asLong();
        
        BrokerHealthMonitor monitor = brokerMonitors.get(queueType);
        if (monitor != null) {
            monitor.updateConnectionStatus(brokerId, connectionStatus, activeConnections, 
                                          failedAttempts, lastSuccessfulConnection, timestamp);
            
            if (failedAttempts > BROKER_CONNECTION_LOSS_THRESHOLD) {
                String message = String.format("Broker connection issues for %s: %d failed attempts", 
                    brokerId, failedAttempts);
                alertService.createAlert("BROKER_CONNECTION_ISSUE", "ERROR", message,
                    Map.of("queueType", queueType, "brokerId", brokerId, 
                           "failedAttempts", failedAttempts));
                
                handleBrokerConnectionIssue(queueType, brokerId, failedAttempts);
            }
            
            if ("DISCONNECTED".equals(connectionStatus)) {
                handleBrokerDisconnection(queueType, brokerId);
            }
        }
        
        updateQueueState(queueType, brokerId, state -> {
            state.updateBrokerConnection(connectionStatus, activeConnections);
        });
        
        metricsService.recordBrokerConnection(queueType, brokerId, connectionStatus);
    }
    
    private void processOffsetCommit(JsonNode eventData) {
        String queueType = eventData.get("queueType").asText();
        String consumerGroup = eventData.get("consumerGroup").asText();
        String topic = eventData.get("topic").asText();
        int partition = eventData.get("partition").asInt();
        long offset = eventData.get("offset").asLong();
        boolean success = eventData.get("success").asBoolean();
        String errorMessage = eventData.get("errorMessage").asText("");
        long timestamp = eventData.get("timestamp").asLong();
        
        OffsetManager manager = offsetManagers.get(queueType);
        if (manager != null) {
            manager.recordOffsetCommit(consumerGroup, topic, partition, offset, 
                                      success, errorMessage, timestamp);
            
            double failureRate = manager.getOffsetCommitFailureRate(consumerGroup);
            if (failureRate > OFFSET_COMMIT_FAILURE_THRESHOLD) {
                String message = String.format("High offset commit failure rate for %s: %.2f%%", 
                    consumerGroup, failureRate * 100);
                alertService.createAlert("OFFSET_COMMIT_FAILURES", "ERROR", message,
                    Map.of("queueType", queueType, "consumerGroup", consumerGroup, 
                           "failureRate", failureRate));
                
                analyzeOffsetCommitFailures(queueType, consumerGroup, errorMessage);
            }
        }
        
        if (!success) {
            handleOffsetCommitFailure(queueType, consumerGroup, topic, partition, errorMessage);
        }
        
        updateQueueState(queueType, topic, state -> {
            state.recordOffsetCommit(consumerGroup, partition, offset, success);
        });
        
        metricsService.recordOffsetCommit(queueType, consumerGroup, topic, success);
    }
    
    private void processTopicManagement(JsonNode eventData) {
        String queueType = eventData.get("queueType").asText();
        String action = eventData.get("action").asText();
        String topicName = eventData.get("topicName").asText();
        int partitionCount = eventData.get("partitionCount").asInt();
        int replicationFactor = eventData.get("replicationFactor").asInt();
        long creationTimeMs = eventData.get("creationTimeMs").asLong();
        boolean success = eventData.get("success").asBoolean();
        long timestamp = eventData.get("timestamp").asLong();
        
        if ("CREATE".equals(action)) {
            if (creationTimeMs > TOPIC_CREATION_TIME_THRESHOLD_MS) {
                String message = String.format("Slow topic creation for %s: %dms", 
                    topicName, creationTimeMs);
                alertService.createAlert("SLOW_TOPIC_CREATION", "WARNING", message,
                    Map.of("queueType", queueType, "topicName", topicName, 
                           "creationTimeMs", creationTimeMs));
            }
            
            validateTopicConfiguration(queueType, topicName, partitionCount, replicationFactor);
        }
        
        if (!success) {
            handleTopicManagementFailure(queueType, action, topicName);
        }
        
        updateQueueState(queueType, topicName, state -> {
            state.updateTopicConfiguration(partitionCount, replicationFactor);
        });
        
        metricsService.recordTopicManagement(queueType, action, topicName, success);
    }
    
    private void processQueueAnomaly(JsonNode eventData) {
        String queueType = eventData.get("queueType").asText();
        String queueName = eventData.get("queueName").asText();
        String anomalyType = eventData.get("anomalyType").asText();
        double severity = eventData.get("severity").asDouble();
        String description = eventData.get("description").asText();
        JsonNode metrics = eventData.get("metrics");
        long timestamp = eventData.get("timestamp").asLong();
        
        String message = String.format("Queue anomaly detected for %s/%s: %s (severity: %.2f)", 
            queueType, queueName, anomalyType, severity);
        
        String alertLevel = severity > 0.8 ? "CRITICAL" : severity > 0.5 ? "WARNING" : "INFO";
        alertService.createAlert("QUEUE_ANOMALY", alertLevel, message,
            Map.of("queueType", queueType, "queueName", queueName, 
                   "anomalyType", anomalyType, "severity", severity));
        
        investigateAnomaly(queueType, queueName, anomalyType, metrics);
        
        if (severity > 0.7) {
            implementMitigation(queueType, queueName, anomalyType, severity);
        }
        
        updateQueueState(queueType, queueName, state -> {
            state.recordAnomaly(anomalyType, severity, description);
        });
        
        metricsService.recordQueueAnomaly(queueType, queueName, anomalyType, severity);
    }
    
    private void updateQueueState(String queueType, String queueName, 
                                  java.util.function.Consumer<QueueMonitoringState> updater) {
        String key = queueType + ":" + queueName;
        queueStates.computeIfAbsent(key, k -> new QueueMonitoringState(queueType))
                   .update(updater);
    }
    
    private void analyzeQueueDepthCause(String queueType, String queueName, int depth, double growthRate) {
        analysisExecutor.execute(() -> {
            try {
                List<String> possibleCauses = new ArrayList<>();
                
                QueueMonitoringState state = queueStates.get(queueType + ":" + queueName);
                if (state != null) {
                    double consumptionRate = state.getConsumptionRate();
                    double productionRate = state.getProductionRate();
                    
                    if (productionRate > consumptionRate * 1.5) {
                        possibleCauses.add("Production rate exceeds consumption rate");
                    }
                    
                    if (state.hasRecentRebalances()) {
                        possibleCauses.add("Recent consumer group rebalances affecting consumption");
                    }
                    
                    if (state.getConsumerLag() > 1000) {
                        possibleCauses.add("Consumer lag contributing to queue depth");
                    }
                }
                
                optimizationService.analyzeQueueDepth(queueType, queueName, depth, growthRate, possibleCauses);
                
            } catch (Exception e) {
                log.error("Error analyzing queue depth cause: {}", e.getMessage(), e);
            }
        });
    }
    
    private void handleRapidQueueGrowth(String queueType, String queueName, int depth, double growthRate) {
        optimizationService.handleRapidGrowth(queueType, queueName, depth, growthRate);
        
        if (growthRate > 1.0) {
            consumerGroupService.scaleConsumers(queueType, queueName, growthRate);
        }
    }
    
    private void analyzeConsumerLagCause(String queueType, String consumerGroup, 
                                        String topic, long lagMessages, long lagTimeMs) {
        analysisExecutor.execute(() -> {
            try {
                ConsumerGroupTracker tracker = consumerTrackers.get(queueType);
                if (tracker != null) {
                    Map<String, Object> analysis = tracker.analyzeLagCause(consumerGroup, topic);
                    consumerGroupService.optimizeConsumerGroup(queueType, consumerGroup, topic, analysis);
                }
            } catch (Exception e) {
                log.error("Error analyzing consumer lag: {}", e.getMessage(), e);
            }
        });
    }
    
    private void detectLagTrend(ConsumerGroupTracker tracker, String consumerGroup, 
                               String topic, long currentLag) {
        double lagTrend = tracker.getLagTrend(consumerGroup, topic);
        
        if (lagTrend > 0.2) {
            alertService.createAlert("INCREASING_LAG_TREND", "WARNING",
                String.format("Consumer lag increasing for %s/%s: %.2f%% growth", 
                    consumerGroup, topic, lagTrend * 100),
                Map.of("consumerGroup", consumerGroup, "topic", topic, "trend", lagTrend));
        }
    }
    
    private void investigateProducerFailures(String queueType, String producerId, 
                                            String topic, int failedCount) {
        ProducerMetrics metrics = producerMetrics.get(queueType);
        if (metrics != null) {
            Map<String, Integer> failureReasons = metrics.getFailureReasons(producerId, topic);
            optimizationService.analyzeProducerFailures(queueType, producerId, topic, failureReasons);
        }
    }
    
    private void analyzeSlowProcessing(String queueType, String consumerGroup, 
                                      String topic, double avgProcessingTimeMs) {
        consumerGroupService.analyzeProcessingBottleneck(queueType, consumerGroup, topic, avgProcessingTimeMs);
    }
    
    private void handleHighConsumerUtilization(String queueType, String consumerGroup, double utilization) {
        consumerGroupService.handleHighUtilization(queueType, consumerGroup, utilization);
        
        if (utilization > 0.95) {
            consumerGroupService.scaleConsumerGroup(queueType, consumerGroup);
        }
    }
    
    private void analyzeDlqPattern(DlqMonitor monitor, String dlqName, String originalTopic) {
        Map<String, Integer> errorDistribution = monitor.getErrorDistribution(dlqName);
        List<String> topErrors = monitor.getTopErrors(dlqName, 5);
        
        optimizationService.analyzeDlqPattern(dlqName, originalTopic, errorDistribution, topErrors);
    }
    
    private void handleMaxRetriesExceeded(String queueType, String dlqName, 
                                         String messageId, String errorReason) {
        flowAnalysisService.handlePermanentFailure(queueType, dlqName, messageId, errorReason);
    }
    
    private void detectPartitionSkew(PartitionAnalyzer analyzer, String topic) {
        Map<Integer, Long> partitionOffsets = analyzer.getPartitionOffsets(topic);
        if (partitionOffsets.isEmpty()) return;
        
        long max = Collections.max(partitionOffsets.values());
        long min = Collections.min(partitionOffsets.values());
        double skewPercentage = ((double)(max - min) / max) * 100;
        
        if (skewPercentage > PARTITION_SKEW_THRESHOLD) {
            alertService.createAlert("PARTITION_SKEW", "WARNING",
                String.format("Partition skew detected for %s: %.2f%% difference", topic, skewPercentage),
                Map.of("topic", topic, "skewPercentage", skewPercentage));
            
            partitionService.rebalancePartitions(topic, partitionOffsets);
        }
    }
    
    private void investigateRebalancing(String queueType, String consumerGroup, String reason) {
        RebalanceTracker tracker = rebalanceTrackers.get(queueType);
        if (tracker != null) {
            List<String> rebalanceHistory = tracker.getRebalanceHistory(consumerGroup);
            consumerGroupService.optimizeRebalancing(queueType, consumerGroup, reason, rebalanceHistory);
        }
    }
    
    private void handleLongRebalance(String queueType, String consumerGroup, long durationMs) {
        consumerGroupService.handleLongRebalance(queueType, consumerGroup, durationMs);
    }
    
    private void analyzeThroughputBottleneck(String queueType, String topic, 
                                            double incomingRate, double outgoingRate) {
        flowAnalysisService.identifyBottleneck(queueType, topic, incomingRate, outgoingRate);
    }
    
    private void handleBacklogBuildup(String queueType, String topic, 
                                     double incomingRate, double outgoingRate) {
        double backlogRate = incomingRate - outgoingRate;
        optimizationService.handleBacklog(queueType, topic, backlogRate);
    }
    
    private void mitigateBackpressure(String queueType, String topic, String component, 
                                     double pressure, String cause) {
        optimizationService.mitigateBackpressure(queueType, topic, component, pressure, cause);
    }
    
    private void optimizeProcessingTime(String queueType, String consumerGroup, 
                                       String topic, double p95, double p99) {
        consumerGroupService.optimizeProcessing(queueType, consumerGroup, topic, p95, p99);
    }
    
    private void handlePoisonMessage(String queueType, String topic, String messageId, 
                                    String lastError, JsonNode messageHeaders) {
        flowAnalysisService.quarantinePoisonMessage(queueType, topic, messageId, lastError, messageHeaders);
    }
    
    private void handleBrokerConnectionIssue(String queueType, String brokerId, int failedAttempts) {
        BrokerHealthMonitor monitor = brokerMonitors.get(queueType);
        if (monitor != null) {
            monitor.handleConnectionFailure(brokerId, failedAttempts);
        }
    }
    
    private void handleBrokerDisconnection(String queueType, String brokerId) {
        partitionService.handleBrokerFailure(queueType, brokerId);
    }
    
    private void analyzeOffsetCommitFailures(String queueType, String consumerGroup, String errorMessage) {
        OffsetManager manager = offsetManagers.get(queueType);
        if (manager != null) {
            Map<String, Integer> failureReasons = manager.getFailureReasons(consumerGroup);
            consumerGroupService.handleOffsetCommitIssues(queueType, consumerGroup, failureReasons);
        }
    }
    
    private void handleOffsetCommitFailure(String queueType, String consumerGroup, 
                                          String topic, int partition, String errorMessage) {
        consumerGroupService.retryOffsetCommit(queueType, consumerGroup, topic, partition);
    }
    
    private void validateTopicConfiguration(String queueType, String topicName, 
                                           int partitionCount, int replicationFactor) {
        if (partitionCount < 3) {
            optimizationService.suggestPartitionIncrease(queueType, topicName, partitionCount);
        }
        
        if (replicationFactor < 2) {
            optimizationService.suggestReplicationIncrease(queueType, topicName, replicationFactor);
        }
    }
    
    private void handleTopicManagementFailure(String queueType, String action, String topicName) {
        partitionService.handleTopicOperationFailure(queueType, action, topicName);
    }
    
    private void investigateAnomaly(String queueType, String queueName, 
                                   String anomalyType, JsonNode metrics) {
        Map<String, Object> metricsMap = objectMapper.convertValue(metrics, Map.class);
        flowAnalysisService.investigateAnomaly(queueType, queueName, anomalyType, metricsMap);
    }
    
    private void implementMitigation(String queueType, String queueName, 
                                    String anomalyType, double severity) {
        optimizationService.implementAnomalyMitigation(queueType, queueName, anomalyType, severity);
    }
    
    @Scheduled(fixedDelay = 60000)
    private void analyzeQueueHealth() {
        try {
            queueStates.forEach((key, state) -> {
                String[] parts = key.split(":");
                String queueType = parts[0];
                String queueName = parts.length > 1 ? parts[1] : "default";
                
                double healthScore = calculateHealthScore(state);
                
                if (healthScore < 0.5) {
                    alertService.createAlert("QUEUE_UNHEALTHY", "WARNING",
                        String.format("Queue %s/%s health score: %.2f", queueType, queueName, healthScore),
                        Map.of("queueType", queueType, "queueName", queueName, "healthScore", healthScore));
                }
                
                detectQueueAnomalies(queueType, queueName, state);
                generateHealthReport(queueType, queueName, state, healthScore);
            });
        } catch (Exception e) {
            log.error("Error analyzing queue health: {}", e.getMessage(), e);
        }
    }
    
    private double calculateHealthScore(QueueMonitoringState state) {
        double depthScore = 1.0 - Math.min(state.getQueueDepth() / 20000.0, 1.0);
        double lagScore = 1.0 - Math.min(state.getAverageConsumerLag() / 10000.0, 1.0);
        double throughputScore = state.getThroughputEfficiency();
        double errorScore = 1.0 - state.getErrorRate();
        
        return (depthScore + lagScore + throughputScore + errorScore) / 4.0;
    }
    
    private void detectQueueAnomalies(String queueType, String queueName, QueueMonitoringState state) {
        if (state.hasAnomalousPattern()) {
            flowAnalysisService.analyzeAnomalousPattern(queueType, queueName, state.getAnomalyDetails());
        }
    }
    
    private void generateHealthReport(String queueType, String queueName, 
                                     QueueMonitoringState state, double healthScore) {
        Map<String, Object> report = new HashMap<>();
        report.put("queueType", queueType);
        report.put("queueName", queueName);
        report.put("healthScore", healthScore);
        report.put("queueDepth", state.getQueueDepth());
        report.put("consumerLag", state.getAverageConsumerLag());
        report.put("throughputEfficiency", state.getThroughputEfficiency());
        report.put("errorRate", state.getErrorRate());
        
        metricsService.recordHealthReport(report);
    }
    
    @Scheduled(fixedDelay = 120000)
    private void detectAnomalies() {
        try {
            Map<String, List<QueueMetrics>> recentMetrics = getRecentMetricsByQueueType();
            
            recentMetrics.forEach((queueType, metrics) -> {
                detectDepthAnomalies(queueType, metrics);
                detectThroughputAnomalies(queueType, metrics);
                detectLagAnomalies(queueType, metrics);
            });
        } catch (Exception e) {
            log.error("Error detecting anomalies: {}", e.getMessage(), e);
        }
    }
    
    private Map<String, List<QueueMetrics>> getRecentMetricsByQueueType() {
        LocalDateTime since = LocalDateTime.now().minusMinutes(ANALYSIS_WINDOW_MINUTES);
        return metricsRepository.findByTimestampAfter(since).stream()
            .collect(Collectors.groupingBy(QueueMetrics::getQueueType));
    }
    
    private void detectDepthAnomalies(String queueType, List<QueueMetrics> metrics) {
        if (metrics.size() < 10) return;
        
        double[] depths = metrics.stream()
            .mapToDouble(QueueMetrics::getDepth)
            .toArray();
        
        double mean = Arrays.stream(depths).average().orElse(0.0);
        double stdDev = calculateStandardDeviation(depths, mean);
        
        for (QueueMetrics metric : metrics) {
            if (metric.getDepth() > mean + (3 * stdDev)) {
                alertService.createAlert("DEPTH_ANOMALY", "INFO",
                    String.format("Queue depth anomaly for %s: %d (mean: %.0f, stddev: %.0f)",
                        metric.getQueueName(), metric.getDepth(), mean, stdDev),
                    Map.of("queueType", queueType, "queueName", metric.getQueueName(),
                           "depth", metric.getDepth(), "mean", mean, "stdDev", stdDev));
            }
        }
    }
    
    private void detectThroughputAnomalies(String queueType, List<QueueMetrics> metrics) {
        Map<String, List<Double>> throughputByQueue = new HashMap<>();
        
        metrics.forEach(metric -> {
            if (metric.getThroughput() != null) {
                throughputByQueue.computeIfAbsent(metric.getQueueName(), k -> new ArrayList<>())
                    .add(metric.getThroughput());
            }
        });
        
        throughputByQueue.forEach((queueName, throughputs) -> {
            if (throughputs.size() >= 5) {
                double mean = throughputs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                
                boolean suddenDrop = throughputs.stream()
                    .anyMatch(tp -> tp < mean * 0.5);
                
                if (suddenDrop) {
                    alertService.createAlert("THROUGHPUT_DROP", "WARNING",
                        String.format("Sudden throughput drop for %s/%s", queueType, queueName),
                        Map.of("queueType", queueType, "queueName", queueName));
                }
            }
        });
    }
    
    private void detectLagAnomalies(String queueType, List<QueueMetrics> metrics) {
        Map<String, List<Long>> lagByConsumer = new HashMap<>();
        
        metrics.forEach(metric -> {
            if (metric.getConsumerLag() != null) {
                lagByConsumer.computeIfAbsent(metric.getConsumerGroup(), k -> new ArrayList<>())
                    .add(metric.getConsumerLag());
            }
        });
        
        lagByConsumer.forEach((consumerGroup, lags) -> {
            if (lags.size() >= 5) {
                long maxLag = Collections.max(lags);
                long minLag = Collections.min(lags);
                
                if (maxLag > minLag * 10 && maxLag > 1000) {
                    alertService.createAlert("LAG_SPIKE", "WARNING",
                        String.format("Consumer lag spike for %s: %d messages", consumerGroup, maxLag),
                        Map.of("queueType", queueType, "consumerGroup", consumerGroup, "maxLag", maxLag));
                }
            }
        });
    }
    
    private double calculateStandardDeviation(double[] values, double mean) {
        double variance = Arrays.stream(values)
            .map(v -> Math.pow(v - mean, 2))
            .sum() / values.length;
        return Math.sqrt(variance);
    }
    
    @Scheduled(fixedDelay = 300000)
    private void generateOptimizationRecommendations() {
        try {
            queueStates.forEach((key, state) -> {
                if (state.needsOptimization()) {
                    String[] parts = key.split(":");
                    String queueType = parts[0];
                    String queueName = parts.length > 1 ? parts[1] : "default";
                    
                    List<String> recommendations = generateRecommendations(state);
                    optimizationService.processRecommendations(queueType, queueName, recommendations);
                }
            });
        } catch (Exception e) {
            log.error("Error generating optimization recommendations: {}", e.getMessage(), e);
        }
    }
    
    private List<String> generateRecommendations(QueueMonitoringState state) {
        List<String> recommendations = new ArrayList<>();
        
        if (state.getQueueDepth() > 5000) {
            recommendations.add("Increase consumer capacity or optimize processing");
        }
        
        if (state.getAverageConsumerLag() > 3000) {
            recommendations.add("Scale consumer groups or improve processing efficiency");
        }
        
        if (state.getThroughputEfficiency() < 0.7) {
            recommendations.add("Optimize message flow and reduce bottlenecks");
        }
        
        if (state.getErrorRate() > 0.05) {
            recommendations.add("Investigate and fix message processing errors");
        }
        
        if (state.getRebalanceFrequency() > 10) {
            recommendations.add("Stabilize consumer group membership");
        }
        
        return recommendations;
    }
    
    @Scheduled(fixedDelay = 21600000)
    private void cleanupOldData() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
            int deleted = metricsRepository.deleteByTimestampBefore(cutoff);
            log.info("Cleaned up {} old queue metrics records", deleted);
            
            queueStates.values().forEach(state -> state.cleanupOldData(cutoff));
            consumerTrackers.values().forEach(tracker -> tracker.cleanup(cutoff));
            dlqMonitors.values().forEach(monitor -> monitor.cleanup(cutoff));
            
        } catch (Exception e) {
            log.error("Error cleaning up old data: {}", e.getMessage(), e);
        }
    }
    
    @Scheduled(fixedDelay = 30000)
    private void performHealthChecks() {
        try {
            brokerMonitors.forEach((queueType, monitor) -> {
                Map<String, String> brokerStatuses = monitor.getBrokerStatuses();
                brokerStatuses.forEach((brokerId, status) -> {
                    if (!"HEALTHY".equals(status)) {
                        handleUnhealthyBroker(queueType, brokerId, status);
                    }
                });
            });
            
            partitionAnalyzers.forEach((queueType, analyzer) -> {
                Map<String, Integer> underReplicatedTopics = analyzer.getUnderReplicatedTopics();
                if (!underReplicatedTopics.isEmpty()) {
                    handleUnderReplicatedTopics(queueType, underReplicatedTopics);
                }
            });
        } catch (Exception e) {
            log.error("Error performing health checks: {}", e.getMessage(), e);
        }
    }
    
    private void handleUnhealthyBroker(String queueType, String brokerId, String status) {
        alertService.createAlert("BROKER_UNHEALTHY", "ERROR",
            String.format("Broker %s is %s", brokerId, status),
            Map.of("queueType", queueType, "brokerId", brokerId, "status", status));
        
        partitionService.handleBrokerIssue(queueType, brokerId, status);
    }
    
    private void handleUnderReplicatedTopics(String queueType, Map<String, Integer> topics) {
        topics.forEach((topic, count) -> {
            alertService.createAlert("UNDER_REPLICATED_TOPIC", "WARNING",
                String.format("Topic %s has %d under-replicated partitions", topic, count),
                Map.of("queueType", queueType, "topic", topic, "count", count));
        });
        
        partitionService.fixUnderReplication(queueType, topics);
    }
    
    private void handleProcessingError(ConsumerRecord<String, String> record, Exception error, 
                                      Acknowledgment acknowledgment) {
        try {
            log.error("Failed to process queue monitoring event after {} attempts. Sending to DLQ.", 
                MAX_RETRY_ATTEMPTS, error);
            
            Map<String, Object> errorContext = Map.of(
                "topic", record.topic(),
                "partition", record.partition(),
                "offset", record.offset(),
                "error", error.getMessage(),
                "timestamp", Instant.now().toEpochMilli()
            );
            
            notificationService.notifyError("QUEUE_MONITORING_PROCESSING_ERROR", errorContext);
            sendToDeadLetterQueue(record, error);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error handling processing failure: {}", e.getMessage(), e);
        }
    }
    
    private void sendToDeadLetterQueue(ConsumerRecord<String, String> record, Exception error) {
        try {
            Map<String, Object> dlqMessage = Map.of(
                "originalTopic", record.topic(),
                "originalValue", record.value(),
                "errorMessage", error.getMessage(),
                "errorType", error.getClass().getName(),
                "timestamp", Instant.now().toEpochMilli(),
                "retryCount", MAX_RETRY_ATTEMPTS
            );
            
            log.info("Message sent to DLQ: {}", dlqMessage);
            
        } catch (Exception e) {
            log.error("Failed to send message to DLQ: {}", e.getMessage(), e);
        }
    }
    
    public void handleMessageFallback(ConsumerRecord<String, String> record, Exception ex) {
        log.error("Fallback triggered for queue monitoring event processing", ex);
        errorCounter.increment();
    }
    
    private double calculateAverageConsumerLag(Map<String, ConsumerGroupTracker> trackers) {
        return trackers.values().stream()
            .mapToDouble(ConsumerGroupTracker::getAverageLag)
            .average()
            .orElse(0.0);
    }
    
    private double calculateAverageThroughput(Map<String, ThroughputMonitor> monitors) {
        return monitors.values().stream()
            .mapToDouble(ThroughputMonitor::getAverageThroughput)
            .average()
            .orElse(0.0);
    }
    
    private double calculateTotalDlqSize(Map<String, DlqMonitor> monitors) {
        return monitors.values().stream()
            .mapToDouble(DlqMonitor::getTotalDlqSize)
            .sum();
    }
    
    @PreDestroy
    public void shutdown() {
        try {
            log.info("Shutting down MessageQueueMonitoringConsumer...");
            scheduledExecutor.shutdown();
            analysisExecutor.shutdown();
            
            if (!scheduledExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            
            if (!analysisExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                analysisExecutor.shutdownNow();
            }
            
            log.info("MessageQueueMonitoringConsumer shut down successfully");
        } catch (InterruptedException e) {
            log.error("Error during shutdown: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }
}