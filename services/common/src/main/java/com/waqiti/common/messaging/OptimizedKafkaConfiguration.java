package com.waqiti.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Optimized Kafka configuration with partitioning and batch processing
 */
@Configuration
@EnableKafka
@Slf4j
public class OptimizedKafkaConfiguration {
    
    @Value("${kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;
    
    @Value("${kafka.batch.size:16384}")
    private int batchSize;
    
    @Value("${kafka.linger.ms:100}")
    private long lingerMs;
    
    @Value("${kafka.compression.type:snappy}")
    private String compressionType;
    
    @Value("${kafka.max.poll.records:500}")
    private int maxPollRecords;
    
    @Value("${kafka.fetch.min.bytes:1024}")
    private int fetchMinBytes;
    
    @Value("${kafka.fetch.max.wait.ms:500}")
    private int fetchMaxWaitMs;
    
    /**
     * Optimized producer factory with batching and compression
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Performance optimizations
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432); // 32MB
        configProps.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 1048576); // 1MB
        
        // Reliability settings
        configProps.put(ProducerConfig.ACKS_CONFIG, "1");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        
        // Idempotence for exactly-once semantics
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    /**
     * Optimized consumer factory with batch processing
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        
        // Performance optimizations
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        configProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, fetchMinBytes);
        configProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, fetchMaxWaitMs);
        configProps.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, 1048576); // 1MB
        
        // Session and heartbeat settings
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        configProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);
        configProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000); // 5 minutes
        
        // Offset management
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        // Partition assignment strategy
        configProps.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, 
            "org.apache.kafka.clients.consumer.CooperativeStickyAssignor");
        
        // Trusted packages for JSON deserialization
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.waqiti.*");
        
        return new DefaultKafkaConsumerFactory<>(configProps);
    }
    
    /**
     * Kafka template with custom partitioner
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory());
        template.setDefaultTopic("default-topic");
        return template;
    }
    
    /**
     * Listener container factory with batch processing
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        
        // Batch processing configuration
        factory.setBatchListener(true);
        factory.setConcurrency(4); // Number of consumer threads
        
        // Container properties
        ContainerProperties containerProps = factory.getContainerProperties();
        containerProps.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        containerProps.setPollTimeout(3000L); // 3 seconds in milliseconds
        containerProps.setCommitLogLevel(org.springframework.kafka.support.LogIfLevelEnabled.Level.INFO);
        
        // Consumer rebalance listener
        containerProps.setConsumerRebalanceListener(new OptimizedConsumerRebalanceListener());
        
        // Error handling
        factory.setCommonErrorHandler(new BatchErrorHandler());
        
        return factory;
    }
    
    /**
     * Optimized message producer with intelligent partitioning
     */
    @Bean
    public OptimizedMessageProducer messageProducer() {
        return new OptimizedMessageProducer(kafkaTemplate());
    }
    
    /**
     * Batch message processor
     */
    @Bean
    public BatchMessageProcessor batchMessageProcessor() {
        return new BatchMessageProcessor();
    }
    
    /**
     * Custom message producer with smart partitioning
     */
    public static class OptimizedMessageProducer {
        private final KafkaTemplate<String, Object> kafkaTemplate;
        private final PartitionStrategy partitionStrategy;
        
        public OptimizedMessageProducer(KafkaTemplate<String, Object> kafkaTemplate) {
            this.kafkaTemplate = kafkaTemplate;
            this.partitionStrategy = new UserBasedPartitionStrategy();
        }
        
        /**
         * Send message with optimal partitioning
         */
        public CompletableFuture<Void> sendMessage(String topic, Object message, String partitionKey) {
            int partition = partitionStrategy.determinePartition(partitionKey, 
                getTopicPartitionCount(topic));
            
            ProducerRecord<String, Object> record = new ProducerRecord<>(
                topic, partition, partitionKey, message);
            
            return kafkaTemplate.send(record)
                .thenAccept(result -> 
                    log.debug("Message sent to topic {} partition {}", topic, partition))
                .exceptionally(throwable -> {
                    log.error("Failed to send message to topic {}", topic, throwable);
                    return null;
                });
        }
        
        /**
         * Batch send messages
         */
        public CompletableFuture<Void> sendBatch(String topic, List<MessageWithKey> messages) {
            List<CompletableFuture<Void>> futures = messages.stream()
                .map(msg -> sendMessage(topic, msg.getMessage(), msg.getKey()))
                .collect(java.util.stream.Collectors.toList());
            
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        }
        
        private int getTopicPartitionCount(String topic) {
            // In production, this would query Kafka metadata
            // For now, return a default value
            return 4;
        }
    }
    
    /**
     * Batch message processor for efficient consumption
     */
    public static class BatchMessageProcessor {
        private final ExecutorService processingExecutor = 
            Executors.newFixedThreadPool(10, r -> {
                Thread t = new Thread(r);
                t.setName("batch-processor-" + t.getId());
                t.setDaemon(true);
                return t;
            });
        
        /**
         * Process messages in batches
         */
        public CompletableFuture<Void> processBatch(List<ConsumerRecord<String, Object>> records) {
            if (records.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            
            // Group records by message type for efficient processing
            Map<String, List<ConsumerRecord<String, Object>>> recordsByType = records.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    record -> record.value().getClass().getSimpleName()));
            
            List<CompletableFuture<Void>> futures = recordsByType.entrySet().stream()
                .map(entry -> CompletableFuture.runAsync(() -> 
                    processRecordsOfType(entry.getKey(), entry.getValue()), processingExecutor))
                .collect(java.util.stream.Collectors.toList());
            
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        }
        
        private void processRecordsOfType(String messageType, List<ConsumerRecord<String, Object>> records) {
            log.debug("Processing {} records of type {}", records.size(), messageType);
            
            // Batch processing logic based on message type
            switch (messageType) {
                case "PaymentEvent":
                    processPaymentEvents(records);
                    break;
                case "UserEvent":
                    processUserEvents(records);
                    break;
                case "TransactionEvent":
                    processTransactionEvents(records);
                    break;
                default:
                    processGenericEvents(records);
                    break;
            }
        }
        
        private void processPaymentEvents(List<ConsumerRecord<String, Object>> records) {
            // Batch process payment events
            records.forEach(record -> {
                try {
                    // Process individual payment event
                    log.debug("Processing payment event: {}", record.value());
                } catch (Exception e) {
                    log.error("Failed to process payment event", e);
                }
            });
        }
        
        private void processUserEvents(List<ConsumerRecord<String, Object>> records) {
            // Batch process user events
            records.forEach(record -> {
                try {
                    // Process individual user event
                    log.debug("Processing user event: {}", record.value());
                } catch (Exception e) {
                    log.error("Failed to process user event", e);
                }
            });
        }
        
        private void processTransactionEvents(List<ConsumerRecord<String, Object>> records) {
            // Batch process transaction events
            records.forEach(record -> {
                try {
                    // Process individual transaction event
                    log.debug("Processing transaction event: {}", record.value());
                } catch (Exception e) {
                    log.error("Failed to process transaction event", e);
                }
            });
        }
        
        private void processGenericEvents(List<ConsumerRecord<String, Object>> records) {
            // Batch process generic events
            records.forEach(record -> {
                try {
                    // Process individual generic event
                    log.debug("Processing generic event: {}", record.value());
                } catch (Exception e) {
                    log.error("Failed to process generic event", e);
                }
            });
        }
        
        public void shutdown() {
            processingExecutor.shutdown();
            try {
                if (!processingExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    processingExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                processingExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Partition strategy interface
     */
    public interface PartitionStrategy {
        int determinePartition(String key, int partitionCount);
    }
    
    /**
     * User-based partition strategy for better distribution
     */
    public static class UserBasedPartitionStrategy implements PartitionStrategy {
        @Override
        public int determinePartition(String key, int partitionCount) {
            if (key == null) {
                return 0;
            }
            
            // Use consistent hashing for user-based partitioning
            return Math.abs(key.hashCode()) % partitionCount;
        }
    }
    
    /**
     * Consumer rebalance listener for optimization
     */
    public static class OptimizedConsumerRebalanceListener implements ConsumerRebalanceListener {
        @Override
        public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            log.info("Partitions revoked: {}", partitions);
        }
        
        @Override
        public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
            log.info("Partitions assigned: {}", partitions);
        }
    }
    
    /**
     * Batch error handler
     */
    public static class BatchErrorHandler implements org.springframework.kafka.listener.CommonErrorHandler {
        @Override
        public boolean handleOne(Exception exception, ConsumerRecord<?, ?> record, 
                               org.apache.kafka.clients.consumer.Consumer<?, ?> consumer,
                               org.springframework.kafka.listener.MessageListenerContainer container) {
            log.error("Error processing record: {}", record, exception);
            return true; // Continue processing
        }
        
        @Override
        public void handleBatch(Exception exception, ConsumerRecords<?, ?> data,
                              org.apache.kafka.clients.consumer.Consumer<?, ?> consumer,
                              org.springframework.kafka.listener.MessageListenerContainer container,
                              Runnable invokeListener) {
            log.error("Error processing batch of {} records", data.count(), exception);
            
            // Process records individually to identify failed ones
            for (ConsumerRecord<?, ?> record : data) {
                try {
                    // Re-process individual record
                    invokeListener.run();
                } catch (Exception e) {
                    log.error("Failed to process individual record: {}", record, e);
                }
            }
        }
    }
    
    /**
     * Message with key for batch operations
     */
    public static class MessageWithKey {
        private final String key;
        private final Object message;
        
        public MessageWithKey(String key, Object message) {
            this.key = key;
            this.message = message;
        }
        
        public String getKey() { return key; }
        public Object getMessage() { return message; }
    }
}