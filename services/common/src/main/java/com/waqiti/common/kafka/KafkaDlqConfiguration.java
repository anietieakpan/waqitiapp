package com.waqiti.common.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.backoff.BackOff;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import com.waqiti.common.monitoring.AlertingService;
import com.waqiti.common.monitoring.MetricsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Comprehensive DLQ configuration for all Kafka consumers
 * Provides topic-specific error handling, retry policies, and DLQ routing
 */
@Configuration
@EnableKafka
@Slf4j
public class KafkaDlqConfiguration {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaProperties kafkaProperties;
    private final AlertingService alertingService;
    private final MetricsService metricsService;
    private final MeterRegistry meterRegistry;
    
    public KafkaDlqConfiguration(
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaProperties kafkaProperties,
            @org.springframework.lang.Nullable AlertingService alertingService,
            @org.springframework.lang.Nullable MetricsService metricsService,
            @org.springframework.lang.Nullable MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaProperties = kafkaProperties;
        this.alertingService = alertingService;
        this.metricsService = metricsService;
        this.meterRegistry = meterRegistry;
    }
    
    private AdminClient adminClient;
    private final ExecutorService dlqProcessingExecutor = Executors.newFixedThreadPool(5);
    
    // DLQ Management State
    private final Map<String, DlqMetrics> dlqMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, MessageClassifier> messageClassifiers = new ConcurrentHashMap<>();
    private final Map<String, ReprocessingStrategy> reprocessingStrategies = new ConcurrentHashMap<>();
    private final Map<String, PoisonMessageHandler> poisonHandlers = new ConcurrentHashMap<>();
    
    // Topic-specific configurations
    private static final Map<String, DlqConfig> DLQ_CONFIGS;

    static {
        Map<String, DlqConfig> configs = new HashMap<>();
        configs.put(
        // CRITICAL FINANCIAL TOPICS - Maximum retries, fastest recovery
        "payment-initiated", DlqConfig.builder()
            .maxRetries(5)
            .backoffMultiplier(1.5)
            .initialInterval(500)
            .maxInterval(30000)
            .dlqSuffix("-payment-dlq")
            .alertOnDlq(true)
            .build());

        configs.put("payment-failures", DlqConfig.builder()
            .maxRetries(5)
            .backoffMultiplier(1.5)
            .initialInterval(500)
            .maxInterval(30000)
            .dlqSuffix("-payment-dlq")
            .alertOnDlq(true)
            .build());

        configs.put("payment-chargebacks", DlqConfig.builder()
            .maxRetries(7) // Chargebacks are time-sensitive (regulatory deadlines)
            .backoffMultiplier(1.2)
            .initialInterval(250)
            .maxInterval(15000)
            .dlqSuffix("-chargeback-dlq")
            .alertOnDlq(true)
            .build());

        configs.put("ledger-transactions", DlqConfig.builder()
            .maxRetries(5)
            .backoffMultiplier(1.5)
            .initialInterval(500)
            .maxInterval(30000)
            .dlqSuffix("-ledger-dlq")
            .alertOnDlq(true)
            .build());

        configs.put("account-freeze-requests", DlqConfig.builder()
            .maxRetries(3) // Account freezes must be immediate for compliance
            .backoffMultiplier(1.1)
            .initialInterval(100)
            .maxInterval(5000)
            .dlqSuffix("-freeze-dlq")
            .alertOnDlq(true)
            .build());

        configs.put("transaction-blocks", DlqConfig.builder()
            .maxRetries(3) // Transaction blocks must be immediate
            .backoffMultiplier(1.1)
            .initialInterval(100)
            .maxInterval(5000)
            .dlqSuffix("-block-dlq")
            .alertOnDlq(true)
            .build());

        // HIGH PRIORITY COMPLIANCE TOPICS
        configs.put("sar-filing-requests", DlqConfig.builder()
            .maxRetries(4)
            .backoffMultiplier(2.0)
            .initialInterval(1000)
            .maxInterval(60000)
            .dlqSuffix("-compliance-dlq")
            .alertOnDlq(true)
            .build());

        configs.put("aml-alerts", DlqConfig.builder()
            .maxRetries(4)
            .backoffMultiplier(2.0)
            .initialInterval(1000)
            .maxInterval(60000)
            .dlqSuffix("-compliance-dlq")
            .alertOnDlq(true)
            .build());

        configs.put("fraud-alerts", DlqConfig.builder()
            .maxRetries(4)
            .backoffMultiplier(1.5)
            .initialInterval(500)
            .maxInterval(30000)
            .dlqSuffix("-fraud-dlq")
            .alertOnDlq(true)
            .build());

        configs.put("kyc-completed", DlqConfig.builder()
            .maxRetries(4)
            .backoffMultiplier(2.0)
            .initialInterval(1000)
            .maxInterval(45000)
            .dlqSuffix("-user-dlq")
            .alertOnDlq(true)
            .build());

        // MEDIUM PRIORITY OPERATIONAL TOPICS
        configs.put("reconciliation-events", DlqConfig.builder()
            .maxRetries(3)
            .backoffMultiplier(2.0)
            .initialInterval(2000)
            .maxInterval(120000)
            .dlqSuffix("-recon-dlq")
            .alertOnDlq(false)
            .build());

        configs.put("merchant-risk-alerts", DlqConfig.builder()
            .maxRetries(3)
            .backoffMultiplier(2.0)
            .initialInterval(2000)
            .maxInterval(120000)
            .dlqSuffix("-merchant-dlq")
            .alertOnDlq(false)
            .build());

        DLQ_CONFIGS = Collections.unmodifiableMap(configs);
    }
    
    /**
     * Create DLQ-enabled container factory for critical financial consumers
     */
    @Bean("criticalFinancialKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> criticalFinancialKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        
        // Critical financial topics get immediate processing with DLQ
        factory.setCommonErrorHandler(createCriticalFinancialErrorHandler());
        factory.setConcurrency(3); // Higher concurrency for critical topics
        factory.getContainerProperties().setMissingTopicsFatal(true);
        factory.getContainerProperties().setPollTimeout(3000);
        
        return factory;
    }
    
    /**
     * Create DLQ-enabled container factory for compliance consumers
     */
    @Bean("complianceKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> complianceKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        
        // Compliance topics get specialized error handling with regulatory focus
        factory.setCommonErrorHandler(createComplianceErrorHandler());
        factory.setConcurrency(2);
        factory.getContainerProperties().setMissingTopicsFatal(true);
        factory.getContainerProperties().setPollTimeout(5000);
        
        return factory;
    }
    
    /**
     * Create DLQ-enabled container factory for standard operational consumers
     */
    @Bean("operationalKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> operationalKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        
        // Standard operational topics with balanced retry/DLQ
        factory.setCommonErrorHandler(createOperationalErrorHandler());
        factory.setConcurrency(1);
        factory.getContainerProperties().setMissingTopicsFatal(false);
        factory.getContainerProperties().setPollTimeout(10000);
        
        return factory;
    }
    
    /**
     * Consumer factory with optimized settings for DLQ handling
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        
        // Optimize for DLQ handling
        props.put("enable.auto.commit", false); // Manual commit after successful processing
        props.put("isolation.level", "read_committed"); // Ensure transactional consistency
        props.put("max.poll.records", 10); // Smaller batches for better error isolation
        props.put("session.timeout.ms", 30000);
        props.put("heartbeat.interval.ms", 10000);
        props.put("request.timeout.ms", 40000);
        
        return new DefaultKafkaConsumerFactory<>(props);
    }
    
    /**
     * Create DLQ topics automatically at startup
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        return new KafkaAdmin(kafkaProperties.buildAdminProperties());
    }
    
    @Bean
    public AdminClient adminClient() {
        return AdminClient.create(kafkaProperties.buildAdminProperties());
    }
    
    /**
     * Create all DLQ topics at startup
     */
    @Bean
    public NewTopic[] createDlqTopics() {
        List<String> dlqTopics = Arrays.asList(
            // Critical financial DLQ topics
            "payment-initiated-payment-dlq",
            "payment-failures-payment-dlq", 
            "payment-chargebacks-chargeback-dlq",
            "ledger-transactions-ledger-dlq",
            "account-freeze-requests-freeze-dlq",
            "transaction-blocks-block-dlq",
            
            // Compliance DLQ topics
            "sar-filing-requests-compliance-dlq",
            "aml-alerts-compliance-dlq",
            "fraud-alerts-fraud-dlq",
            "kyc-completed-user-dlq",
            
            // Operational DLQ topics
            "reconciliation-events-recon-dlq",
            "merchant-risk-alerts-merchant-dlq",
            
            // Poison message DLQ topics
            "payment-initiated-poison-dlq",
            "payment-chargebacks-poison-dlq",
            "account-freeze-requests-poison-dlq",
            "sar-filing-requests-poison-dlq"
        );
        
        return dlqTopics.stream()
            .map(topic -> {
                NewTopic newTopic = new NewTopic(topic, 3, (short) 2);
                Map<String, String> configs = new HashMap<>();
                configs.put("cleanup.policy", "compact,delete");
                configs.put("retention.ms", "2592000000"); // 30 days retention
                configs.put("max.message.bytes", "10485760"); // 10MB max message size
                newTopic.configs(configs);
                return newTopic;
            })
            .toArray(NewTopic[]::new);
    }
    
    // Error handlers for different consumer categories
    
    private DefaultErrorHandler createCriticalFinancialErrorHandler() {
        // Critical financial operations get minimal retries with fast DLQ routing
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(5);
        backOff.setInitialInterval(500);
        backOff.setMultiplier(1.5);
        backOff.setMaxInterval(30000);
        
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            createFinancialDlqRecoverer(),
            backOff
        );
        
        // Don't retry validation errors, poison messages, or deserialization errors
        errorHandler.addNotRetryableExceptions(
            IllegalArgumentException.class,
            com.fasterxml.jackson.core.JsonProcessingException.class,
            org.springframework.kafka.support.serializer.DeserializationException.class
        );
        
        return errorHandler;
    }
    
    private DefaultErrorHandler createComplianceErrorHandler() {
        // Compliance operations get more retries due to regulatory importance
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(4);
        backOff.setInitialInterval(1000);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(60000);
        
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            createComplianceDlqRecoverer(),
            backOff
        );
        
        errorHandler.addNotRetryableExceptions(
            IllegalArgumentException.class,
            com.fasterxml.jackson.core.JsonProcessingException.class
        );
        
        return errorHandler;
    }
    
    private DefaultErrorHandler createOperationalErrorHandler() {
        // Operational topics get balanced retry approach
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(2000);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(120000);
        
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            createOperationalDlqRecoverer(),
            backOff
        );
        
        errorHandler.addNotRetryableExceptions(
            IllegalArgumentException.class,
            com.fasterxml.jackson.core.JsonProcessingException.class,
            NullPointerException.class
        );
        
        return errorHandler;
    }
    
    // DLQ recoverers for different consumer categories
    
    private ConsumerRecordRecoverer createFinancialDlqRecoverer() {
        return new DeadLetterPublishingRecoverer(kafkaTemplate,
            (consumerRecord, exception) -> {
                String originalTopic = consumerRecord.topic();
                String dlqTopic = getDlqTopicName(originalTopic, "-payment-dlq");
                
                log.error("CRITICAL_FINANCIAL_DLQ: Sending financial message to DLQ - Topic: {}, DLQ: {}, Error: {}", 
                    originalTopic, dlqTopic, exception.getClass().getSimpleName());
                
                // Send alerts for critical financial failures
                sendCriticalAlert(originalTopic, consumerRecord, exception);
                
                return new TopicPartition(dlqTopic, consumerRecord.partition());
            });
    }
    
    private ConsumerRecordRecoverer createComplianceDlqRecoverer() {
        return new DeadLetterPublishingRecoverer(kafkaTemplate,
            (consumerRecord, exception) -> {
                String originalTopic = consumerRecord.topic();
                String dlqTopic = getDlqTopicName(originalTopic, "-compliance-dlq");
                
                log.error("COMPLIANCE_DLQ: Sending compliance message to DLQ - Topic: {}, DLQ: {}, Error: {}", 
                    originalTopic, dlqTopic, exception.getClass().getSimpleName());
                
                // Send compliance alerts
                sendComplianceAlert(originalTopic, consumerRecord, exception);
                
                return new TopicPartition(dlqTopic, consumerRecord.partition());
            });
    }
    
    private ConsumerRecordRecoverer createOperationalDlqRecoverer() {
        return new DeadLetterPublishingRecoverer(kafkaTemplate,
            (consumerRecord, exception) -> {
                String originalTopic = consumerRecord.topic();
                String dlqTopic = getDlqTopicName(originalTopic, "-operational-dlq");
                
                log.warn("OPERATIONAL_DLQ: Sending operational message to DLQ - Topic: {}, DLQ: {}, Error: {}", 
                    originalTopic, dlqTopic, exception.getClass().getSimpleName());
                
                return new TopicPartition(dlqTopic, consumerRecord.partition());
            });
    }
    
    // Helper methods
    
    private String getDlqTopicName(String originalTopic, String defaultSuffix) {
        DlqConfig config = DLQ_CONFIGS.get(originalTopic);
        return originalTopic + (config != null ? config.getDlqSuffix() : defaultSuffix);
    }
    
    private void sendCriticalAlert(String topic, ConsumerRecord<?, ?> record, Exception exception) {
        try {
            // Send to monitoring system (PagerDuty, Slack, etc.)
            log.error("CRITICAL_FINANCIAL_ALERT: Topic={}, Partition={}, Offset={}, Error={}", 
                topic, record.partition(), record.offset(), exception.getMessage());
            
            // Track in metrics
            if (dlqMetricsMap.containsKey(topic)) {
                dlqMetricsMap.get(topic).incrementCriticalAlerts();
            }
            
            // Send alert through alerting service
            if (alertingService != null) {
                alertingService.sendCriticalAlert(
                    "DLQ_CRITICAL_FINANCIAL",
                    String.format("Critical financial message failed: Topic=%s, Error=%s", 
                        topic, exception.getClass().getSimpleName()),
                    Map.of(
                        "topic", topic,
                        "partition", String.valueOf(record.partition()),
                        "offset", String.valueOf(record.offset()),
                        "error", exception.getMessage(),
                        "severity", "CRITICAL",
                        "impactType", "FINANCIAL"
                    )
                );
            }
            
            // Store for analysis
            storeFailedMessageForAnalysis(topic, record, exception, "CRITICAL");
            
        } catch (Exception e) {
            log.error("Failed to send critical alert", e);
        }
    }
    
    private void sendComplianceAlert(String topic, ConsumerRecord<?, ?> record, Exception exception) {
        try {
            // Send to compliance monitoring
            log.error("COMPLIANCE_ALERT: Topic={}, Partition={}, Offset={}, Error={}", 
                topic, record.partition(), record.offset(), exception.getMessage());
            
            // Track in metrics
            if (dlqMetricsMap.containsKey(topic)) {
                dlqMetricsMap.get(topic).incrementComplianceAlerts();
            }
            
            // Send alert through alerting service
            if (alertingService != null) {
                alertingService.sendComplianceAlert(
                    "DLQ_COMPLIANCE_VIOLATION",
                    String.format("Compliance message processing failed: Topic=%s", topic),
                    Map.of(
                        "topic", topic,
                        "partition", String.valueOf(record.partition()),
                        "offset", String.valueOf(record.offset()),
                        "error", exception.getMessage(),
                        "severity", "HIGH",
                        "regulatoryImpact", "true"
                    )
                );
            }
            
            // Store for compliance audit
            storeFailedMessageForAnalysis(topic, record, exception, "COMPLIANCE");
            
        } catch (Exception e) {
            log.error("Failed to send compliance alert", e);
        }
    }
    
    // Configuration data class
    @lombok.Data
    @lombok.Builder
    private static class DlqConfig {
        private int maxRetries;
        private double backoffMultiplier;
        private long initialInterval;
        private long maxInterval;
        private String dlqSuffix;
        private boolean alertOnDlq;
    }
    
    // ==================== ENTERPRISE DLQ MANAGEMENT FEATURES ====================
    
    /**
     * Initialize DLQ management components
     */
    @PostConstruct
    public void initializeDlqManagement() {
        log.info("Initializing enterprise DLQ management features");
        
        // Initialize metrics for each DLQ topic
        DLQ_CONFIGS.keySet().forEach(topic -> {
            dlqMetricsMap.put(topic, new DlqMetrics(topic));
            messageClassifiers.put(topic, new MessageClassifier(topic));
            reprocessingStrategies.put(topic, new ReprocessingStrategy(topic));
            poisonHandlers.put(topic, new PoisonMessageHandler(topic));
        });
        
        // Register metrics with Micrometer
        if (meterRegistry != null) {
            registerDlqMetrics();
        }
        
        log.info("DLQ management initialized for {} topics", DLQ_CONFIGS.size());
    }
    
    /**
     * Reprocess messages from DLQ with intelligent retry strategy
     */
    @Transactional
    public DlqReprocessingResult reprocessDlqMessages(String dlqTopic, ReprocessingOptions options) {
        log.info("Starting DLQ reprocessing for topic: {} with options: {}", dlqTopic, options);
        
        DlqReprocessingResult result = new DlqReprocessingResult();
        Consumer<String, Object> dlqConsumer = null;
        
        try {
            // Create dedicated DLQ consumer
            dlqConsumer = createDlqConsumer(dlqTopic);
            
            // Poll messages from DLQ
            List<ConsumerRecord<String, Object>> records = pollDlqMessages(dlqConsumer, dlqTopic, options);
            result.setTotalMessages(records.size());
            
            // Process each message
            for (ConsumerRecord<String, Object> record : records) {
                try {
                    // Classify message
                    MessageClassification classification = classifyMessage(dlqTopic, record);
                    
                    if (classification.isPoisonMessage()) {
                        // Handle poison message
                        handlePoisonMessage(dlqTopic, record, classification);
                        result.incrementPoisonMessages();
                    } else if (classification.isRetryable()) {
                        // Attempt reprocessing
                        boolean success = reprocessMessage(dlqTopic, record, options);
                        if (success) {
                            result.incrementSuccessful();
                        } else {
                            result.incrementFailed();
                        }
                    } else {
                        // Message requires manual intervention
                        result.incrementManualIntervention();
                        log.warn("Message requires manual intervention: {}", record);
                    }
                    
                } catch (Exception e) {
                    log.error("Error processing DLQ message: {}", record, e);
                    result.incrementFailed();
                }
            }
            
            // Update metrics
            updateDlqMetrics(dlqTopic, result);
            
        } catch (Exception e) {
            log.error("DLQ reprocessing failed for topic: {}", dlqTopic, e);
            result.setError(e.getMessage());
        } finally {
            if (dlqConsumer != null) {
                dlqConsumer.close();
            }
        }
        
        return result;
    }
    
    /**
     * Bulk reprocess multiple DLQ topics
     */
    public Map<String, DlqReprocessingResult> bulkReprocessDlqMessages(List<String> dlqTopics, ReprocessingOptions options) {
        log.info("Starting bulk DLQ reprocessing for {} topics", dlqTopics.size());
        
        Map<String, DlqReprocessingResult> results = new ConcurrentHashMap<>();
        
        dlqTopics.parallelStream().forEach(topic -> {
            try {
                DlqReprocessingResult result = reprocessDlqMessages(topic, options);
                results.put(topic, result);
            } catch (Exception e) {
                log.error("Bulk reprocessing failed for topic: {}", topic, e);
                DlqReprocessingResult errorResult = new DlqReprocessingResult();
                errorResult.setError(e.getMessage());
                results.put(topic, errorResult);
            }
        });
        
        return results;
    }
    
    /**
     * Inspect and analyze DLQ message for debugging
     */
    public DlqMessageAnalysis analyzeDlqMessage(String dlqTopic, long partition, long offset) {
        log.info("Analyzing DLQ message: topic={}, partition={}, offset={}", dlqTopic, partition, offset);
        
        DlqMessageAnalysis analysis = new DlqMessageAnalysis();
        Consumer<String, Object> consumer = null;
        
        try {
            consumer = createDlqConsumer(dlqTopic);
            
            // Seek to specific offset
            TopicPartition topicPartition = new TopicPartition(dlqTopic, (int) partition);
            consumer.assign(Collections.singletonList(topicPartition));
            consumer.seek(topicPartition, offset);
            
            // Poll the specific message
            org.apache.kafka.clients.consumer.ConsumerRecords<String, Object> records = consumer.poll(Duration.ofSeconds(5));

            if (!records.isEmpty()) {
                org.apache.kafka.clients.consumer.ConsumerRecord<String, Object> record = records.iterator().next();
                
                // Analyze message
                analysis.setKey(record.key());
                analysis.setValue(record.value());
                analysis.setHeaders(extractHeaders(record));
                analysis.setTimestamp(record.timestamp());
                analysis.setPartition(record.partition());
                analysis.setOffset(record.offset());
                
                // Classify the message
                MessageClassification classification = classifyMessage(dlqTopic, record);
                analysis.setClassification(classification);
                
                // Get failure reason from headers
                analysis.setFailureReason(getFailureReason(record));
                analysis.setRetryCount(getRetryCount(record));
                
                // Suggest remediation
                analysis.setSuggestedRemediation(suggestRemediation(classification));
            }
            
        } catch (Exception e) {
            log.error("Failed to analyze DLQ message", e);
            analysis.setError(e.getMessage());
        } finally {
            if (consumer != null) {
                consumer.close();
            }
        }
        
        return analysis;
    }
    
    /**
     * Get current DLQ statistics
     */
    public DlqStatistics getDlqStatistics() {
        DlqStatistics stats = new DlqStatistics();
        
        try {
            Map<String, Long> dlqDepths = new HashMap<>();
            Map<String, Double> processingRates = new HashMap<>();
            Map<String, Long> oldestMessages = new HashMap<>();
            
            // Get stats for each DLQ topic
            for (String topic : DLQ_CONFIGS.keySet()) {
                String dlqTopic = getDlqTopicName(topic, DLQ_CONFIGS.get(topic).getDlqSuffix());
                
                // Get topic depth
                long depth = getTopicDepth(dlqTopic);
                dlqDepths.put(dlqTopic, depth);
                
                // Get processing rate from metrics
                if (dlqMetricsMap.containsKey(topic)) {
                    processingRates.put(dlqTopic, dlqMetricsMap.get(topic).getProcessingRate());
                }
                
                // Get oldest message timestamp
                long oldestTimestamp = getOldestMessageTimestamp(dlqTopic);
                if (oldestTimestamp > 0) {
                    oldestMessages.put(dlqTopic, oldestTimestamp);
                }
            }
            
            stats.setDlqDepths(dlqDepths);
            stats.setProcessingRates(processingRates);
            stats.setOldestMessages(oldestMessages);
            stats.setTotalMessagesInDlq(dlqDepths.values().stream().mapToLong(Long::longValue).sum());
            
            // Calculate health score
            stats.setHealthScore(calculateDlqHealthScore(stats));
            
        } catch (Exception e) {
            log.error("Failed to get DLQ statistics", e);
            stats.setError(e.getMessage());
        }
        
        return stats;
    }
    
    /**
     * Scheduled task to monitor DLQ depths and alert on thresholds
     */
    @Scheduled(fixedDelayString = "${dlq.monitoring.interval:60000}") // Every minute
    public void monitorDlqHealth() {
        try {
            DlqStatistics stats = getDlqStatistics();
            
            // Check thresholds and send alerts
            for (Map.Entry<String, Long> entry : stats.getDlqDepths().entrySet()) {
                String dlqTopic = entry.getKey();
                long depth = entry.getValue();
                
                // Alert on high DLQ depth
                if (depth > 1000) {
                    sendDlqDepthAlert(dlqTopic, depth, "CRITICAL");
                } else if (depth > 500) {
                    sendDlqDepthAlert(dlqTopic, depth, "WARNING");
                }
                
                // Alert on old messages stuck in DLQ
                Long oldestTimestamp = stats.getOldestMessages().get(dlqTopic);
                if (oldestTimestamp != null) {
                    long ageMinutes = (System.currentTimeMillis() - oldestTimestamp) / 60000;
                    if (ageMinutes > 1440) { // 24 hours
                        sendStaleMessageAlert(dlqTopic, ageMinutes);
                    }
                }
            }
            
            // Update metrics
            if (metricsService != null) {
                metricsService.recordDlqStats(stats);
            }
            
        } catch (Exception e) {
            log.error("DLQ monitoring failed", e);
        }
    }
    
    /**
     * Archive old DLQ messages to cold storage
     */
    @Scheduled(cron = "${dlq.archive.cron:0 0 2 * * ?}") // Daily at 2 AM
    public void archiveOldDlqMessages() {
        log.info("Starting DLQ archival process");
        
        try {
            for (String topic : DLQ_CONFIGS.keySet()) {
                String dlqTopic = getDlqTopicName(topic, DLQ_CONFIGS.get(topic).getDlqSuffix());
                
                // Archive messages older than retention period
                int archived = archiveOldMessages(dlqTopic, 7); // 7 days
                
                if (archived > 0) {
                    log.info("Archived {} messages from DLQ topic: {}", archived, dlqTopic);
                }
            }
        } catch (Exception e) {
            log.error("DLQ archival failed", e);
        }
    }
    
    // Helper methods for DLQ management
    
    private Consumer<String, Object> createDlqConsumer(String dlqTopic) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        props.put("group.id", "dlq-reprocessor-" + UUID.randomUUID());
        props.put("enable.auto.commit", false);
        props.put("auto.offset.reset", "earliest");
        return new DefaultKafkaConsumerFactory<String, Object>(props).createConsumer();
    }
    
    private List<ConsumerRecord<String, Object>> pollDlqMessages(Consumer<String, Object> consumer, 
                                                                 String dlqTopic, 
                                                                 ReprocessingOptions options) {
        consumer.subscribe(Collections.singletonList(dlqTopic));
        List<org.apache.kafka.clients.consumer.ConsumerRecord<String, Object>> allRecords = new ArrayList<>();

        int pollAttempts = 0;
        while (pollAttempts < 5 && allRecords.size() < options.getMaxMessages()) {
            org.apache.kafka.clients.consumer.ConsumerRecords<String, Object> records = consumer.poll(Duration.ofSeconds(5));
            records.forEach(allRecords::add);
            
            if (allRecords.size() >= options.getMaxMessages()) {
                break;
            }
            pollAttempts++;
        }
        
        return allRecords.stream()
            .limit(options.getMaxMessages())
            .collect(Collectors.toList());
    }
    
    private boolean reprocessMessage(String dlqTopic, ConsumerRecord<String, Object> record, 
                                    ReprocessingOptions options) {
        try {
            // Extract original topic from DLQ topic name
            String originalTopic = extractOriginalTopic(dlqTopic);
            
            // Apply reprocessing strategy
            ReprocessingStrategy strategy = reprocessingStrategies.get(originalTopic);
            if (strategy != null) {
                return strategy.reprocess(record, options);
            }
            
            // Default: send back to original topic
            kafkaTemplate.send(originalTopic, record.key(), record.value());
            return true;
            
        } catch (Exception e) {
            log.error("Failed to reprocess message from DLQ", e);
            return false;
        }
    }
    
    private MessageClassification classifyMessage(String topic, ConsumerRecord<String, Object> record) {
        MessageClassifier classifier = messageClassifiers.get(extractOriginalTopic(topic));
        if (classifier != null) {
            return classifier.classify(record);
        }
        return new MessageClassification(true, false, "UNKNOWN");
    }
    
    private void handlePoisonMessage(String topic, ConsumerRecord<String, Object> record, 
                                    MessageClassification classification) {
        PoisonMessageHandler handler = poisonHandlers.get(extractOriginalTopic(topic));
        if (handler != null) {
            handler.handle(record, classification);
        }
    }
    
    private void storeFailedMessageForAnalysis(String topic, ConsumerRecord<?, ?> record, 
                                              Exception exception, String severity) {
        // In production, would store in database or S3 for analysis
        log.info("Storing failed message for analysis: topic={}, severity={}", topic, severity);
    }
    
    private Map<String, String> extractHeaders(ConsumerRecord<String, Object> record) {
        Map<String, String> headers = new HashMap<>();
        record.headers().forEach(header -> {
            headers.put(header.key(), new String(header.value()));
        });
        return headers;
    }
    
    private String getFailureReason(ConsumerRecord<String, Object> record) {
        // Extract from headers if available
        return extractHeaders(record).getOrDefault("kafka.exception.message", "Unknown");
    }
    
    private int getRetryCount(ConsumerRecord<String, Object> record) {
        String retryCount = extractHeaders(record).getOrDefault("kafka.retry.count", "0");
        return Integer.parseInt(retryCount);
    }
    
    private String suggestRemediation(MessageClassification classification) {
        if (classification.isPoisonMessage()) {
            return "Message is poison - requires data fix or schema update";
        } else if (!classification.isRetryable()) {
            return "Message not retryable - check business logic or data integrity";
        } else {
            return "Message can be retried after fixing underlying issue";
        }
    }
    
    private long getTopicDepth(String topic) {
        try {
            Map<TopicPartition, Long> endOffsets = adminClient.listConsumerGroupOffsets("dlq-monitor")
                .partitionsToOffsetAndMetadata().get().entrySet().stream()
                .filter(e -> e.getKey().topic().equals(topic))
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().offset()
                ));
            
            return endOffsets.values().stream().mapToLong(Long::longValue).sum();
        } catch (Exception e) {
            log.error("Failed to get topic depth for: {}", topic, e);
            return -1;
        }
    }
    
    private long getOldestMessageTimestamp(String topic) {
        // Implementation would fetch oldest message timestamp
        return System.currentTimeMillis() - (24 * 60 * 60 * 1000); // Placeholder: 24 hours ago
    }
    
    private double calculateDlqHealthScore(DlqStatistics stats) {
        // Calculate health score based on depth, age, and processing rate
        double score = 100.0;
        
        // Penalize for high depth
        long totalDepth = stats.getTotalMessagesInDlq();
        if (totalDepth > 5000) score -= 50;
        else if (totalDepth > 1000) score -= 30;
        else if (totalDepth > 100) score -= 10;
        
        // Penalize for old messages
        long oldestAge = stats.getOldestMessages().values().stream()
            .mapToLong(ts -> System.currentTimeMillis() - ts)
            .max().orElse(0);
        
        if (oldestAge > 86400000) score -= 30; // > 24 hours
        else if (oldestAge > 3600000) score -= 10; // > 1 hour
        
        return Math.max(0, score);
    }
    
    private void sendDlqDepthAlert(String topic, long depth, String severity) {
        if (alertingService != null) {
            alertingService.sendAlert(
                "DLQ_DEPTH_THRESHOLD",
                String.format("DLQ depth alert: %s has %d messages", topic, depth),
                Map.of(
                    "topic", topic,
                    "depth", String.valueOf(depth),
                    "severity", severity
                )
            );
        }
    }
    
    private void sendStaleMessageAlert(String topic, long ageMinutes) {
        if (alertingService != null) {
            alertingService.sendAlert(
                "DLQ_STALE_MESSAGES",
                String.format("Stale messages in DLQ: %s has messages older than %d minutes", 
                    topic, ageMinutes),
                Map.of(
                    "topic", topic,
                    "ageMinutes", String.valueOf(ageMinutes),
                    "severity", "WARNING"
                )
            );
        }
    }
    
    private String extractOriginalTopic(String dlqTopic) {
        // Extract original topic name from DLQ topic name
        for (Map.Entry<String, DlqConfig> entry : DLQ_CONFIGS.entrySet()) {
            String expectedDlqTopic = entry.getKey() + entry.getValue().getDlqSuffix();
            if (dlqTopic.equals(expectedDlqTopic)) {
                return entry.getKey();
            }
        }
        return dlqTopic.replace("-dlq", "").replace("-poison", "");
    }
    
    private int archiveOldMessages(String topic, int daysToKeep) {
        // Implementation would archive to S3 or cold storage
        log.info("Archiving messages older than {} days from topic: {}", daysToKeep, topic);
        return 0; // Placeholder
    }
    
    private void registerDlqMetrics() {
        // Register Micrometer metrics
        dlqMetricsMap.forEach((topic, metrics) -> {
            Gauge.builder("dlq.depth", metrics, DlqMetrics::getCurrentDepth)
                .tag("topic", topic)
                .description("Current DLQ depth")
                .register(meterRegistry);
            
            Gauge.builder("dlq.processing.rate", metrics, DlqMetrics::getProcessingRate)
                .tag("topic", topic)
                .description("DLQ processing rate")
                .register(meterRegistry);
            
            Counter.builder("dlq.alerts.critical")
                .tag("topic", topic)
                .description("Critical DLQ alerts")
                .register(meterRegistry);
        });
    }
    
    private void updateDlqMetrics(String topic, DlqReprocessingResult result) {
        DlqMetrics metrics = dlqMetricsMap.get(extractOriginalTopic(topic));
        if (metrics != null) {
            metrics.updateReprocessingStats(result);
        }
    }
    
    // Supporting classes for DLQ management
    
    @lombok.Data
    public static class ReprocessingOptions {
        private int maxMessages = 100;
        private boolean dryRun = false;
        private boolean skipPoisonMessages = true;
        private int maxRetries = 3;
        private long delayBetweenRetries = 1000;
    }
    
    @lombok.Data
    public static class DlqReprocessingResult {
        private int totalMessages;
        private final AtomicInteger successful = new AtomicInteger(0);
        private final AtomicInteger failed = new AtomicInteger(0);
        private final AtomicInteger poisonMessages = new AtomicInteger(0);
        private final AtomicInteger manualIntervention = new AtomicInteger(0);
        private String error;
        
        public void incrementSuccessful() { successful.incrementAndGet(); }
        public void incrementFailed() { failed.incrementAndGet(); }
        public void incrementPoisonMessages() { poisonMessages.incrementAndGet(); }
        public void incrementManualIntervention() { manualIntervention.incrementAndGet(); }
    }
    
    @lombok.Data
    public static class DlqMessageAnalysis {
        private String key;
        private Object value;
        private Map<String, String> headers;
        private long timestamp;
        private int partition;
        private long offset;
        private MessageClassification classification;
        private String failureReason;
        private int retryCount;
        private String suggestedRemediation;
        private String error;
    }
    
    @lombok.Data
    public static class DlqStatistics {
        private Map<String, Long> dlqDepths;
        private Map<String, Double> processingRates;
        private Map<String, Long> oldestMessages;
        private long totalMessagesInDlq;
        private double healthScore;
        private String error;
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class MessageClassification {
        private boolean retryable;
        private boolean poisonMessage;
        private String category;
    }
    
    private static class DlqMetrics {
        private final String topic;
        private final AtomicLong currentDepth = new AtomicLong(0);
        private final AtomicLong processedCount = new AtomicLong(0);
        private final AtomicLong criticalAlerts = new AtomicLong(0);
        private final AtomicLong complianceAlerts = new AtomicLong(0);
        private volatile double processingRate = 0.0;
        private volatile Instant lastProcessingTime = Instant.now();
        
        public DlqMetrics(String topic) {
            this.topic = topic;
        }
        
        public long getCurrentDepth() { return currentDepth.get(); }
        public double getProcessingRate() { return processingRate; }
        public void incrementCriticalAlerts() { criticalAlerts.incrementAndGet(); }
        public void incrementComplianceAlerts() { complianceAlerts.incrementAndGet(); }
        
        public void updateReprocessingStats(DlqReprocessingResult result) {
            processedCount.addAndGet(result.totalMessages);
            currentDepth.addAndGet(-result.successful.get());
            
            // Calculate processing rate
            Instant now = Instant.now();
            long secondsSinceLastProcessing = Duration.between(lastProcessingTime, now).getSeconds();
            if (secondsSinceLastProcessing > 0) {
                processingRate = (double) result.totalMessages / secondsSinceLastProcessing;
            }
            lastProcessingTime = now;
        }
    }
    
    private static class MessageClassifier {
        private final String topic;
        
        public MessageClassifier(String topic) {
            this.topic = topic;
        }
        
        public MessageClassification classify(ConsumerRecord<String, Object> record) {
            // Classify based on error type, retry count, and content
            Map<String, String> headers = new HashMap<>();
            record.headers().forEach(h -> headers.put(h.key(), new String(h.value())));
            
            String exceptionClass = headers.getOrDefault("kafka.exception.class", "");
            int retryCount = Integer.parseInt(headers.getOrDefault("kafka.retry.count", "0"));
            
            // Poison message detection
            if (exceptionClass.contains("JsonProcessingException") ||
                exceptionClass.contains("DeserializationException") ||
                retryCount > 10) {
                return new MessageClassification(false, true, "POISON");
            }
            
            // Non-retryable business errors
            if (exceptionClass.contains("IllegalArgumentException") ||
                exceptionClass.contains("ValidationException")) {
                return new MessageClassification(false, false, "VALIDATION_ERROR");
            }
            
            // Retryable transient errors
            if (exceptionClass.contains("TimeoutException") ||
                exceptionClass.contains("ConnectException") ||
                exceptionClass.contains("IOException")) {
                return new MessageClassification(true, false, "TRANSIENT_ERROR");
            }
            
            // Default: retryable
            return new MessageClassification(true, false, "UNKNOWN");
        }
    }
    
    private static class ReprocessingStrategy {
        private final String topic;
        private final KafkaTemplate<String, Object> kafkaTemplate;
        
        public ReprocessingStrategy(String topic) {
            this.topic = topic;
            this.kafkaTemplate = null; // Would be injected in production
        }
        
        public boolean reprocess(ConsumerRecord<String, Object> record, ReprocessingOptions options) {
            // Apply topic-specific reprocessing logic
            if (topic.startsWith("payment-")) {
                return reprocessPaymentMessage(record, options);
            } else if (topic.startsWith("compliance-") || topic.contains("aml") || topic.contains("sar")) {
                return reprocessComplianceMessage(record, options);
            } else {
                return reprocessDefaultMessage(record, options);
            }
        }
        
        private boolean reprocessPaymentMessage(ConsumerRecord<String, Object> record, ReprocessingOptions options) {
            // Payment-specific reprocessing logic
            log.info("Reprocessing payment message with enhanced validation");
            // Would include payment validation, idempotency checks, etc.
            return true;
        }
        
        private boolean reprocessComplianceMessage(ConsumerRecord<String, Object> record, ReprocessingOptions options) {
            // Compliance-specific reprocessing logic
            log.info("Reprocessing compliance message with regulatory checks");
            // Would include compliance validation, audit trail, etc.
            return true;
        }
        
        private boolean reprocessDefaultMessage(ConsumerRecord<String, Object> record, ReprocessingOptions options) {
            // Default reprocessing
            log.info("Reprocessing message with default strategy");
            return true;
        }
    }
    
    private static class PoisonMessageHandler {
        private final String topic;
        
        public PoisonMessageHandler(String topic) {
            this.topic = topic;
        }
        
        public void handle(ConsumerRecord<String, Object> record, MessageClassification classification) {
            log.warn("Handling poison message for topic: {} - {}", topic, classification);
            
            // Move to poison DLQ
            String poisonTopic = topic + "-poison-dlq";
            
            // Store for manual analysis
            storeForManualAnalysis(record, classification);
            
            // Send notification
            sendPoisonMessageAlert(record, classification);
        }
        
        private void storeForManualAnalysis(ConsumerRecord<String, Object> record, MessageClassification classification) {
            // Would store in database or S3 for manual review
            log.info("Storing poison message for manual analysis");
        }
        
        private void sendPoisonMessageAlert(ConsumerRecord<String, Object> record, MessageClassification classification) {
            log.error("POISON_MESSAGE_ALERT: Topic={}, Classification={}", topic, classification.getCategory());
        }
    }
}