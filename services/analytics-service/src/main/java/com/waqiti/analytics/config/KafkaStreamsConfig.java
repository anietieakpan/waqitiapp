package com.waqiti.analytics.config;

import com.waqiti.analytics.model.*;
import com.waqiti.analytics.service.RealTimeAnalyticsProcessor;
import com.waqiti.analytics.service.RealTimeMetricsAggregator;
import com.waqiti.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Kafka Streams configuration for real-time analytics processing
 */
@Slf4j
@Configuration
@EnableKafkaStreams
@RequiredArgsConstructor
public class KafkaStreamsConfig {

    private final KafkaProperties kafkaProperties;
    private final RealTimeMetricsAggregator metricsAggregator;

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration kStreamsConfig() {
        Map<String, Object> props = new HashMap<>();
        
        // Basic configuration
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "waqiti-analytics-processor");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JsonSerde.class.getName());
        
        // Performance optimization
        props.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, 4);
        props.put(StreamsConfig.BUFFERED_RECORDS_PER_PARTITION_CONFIG, 1000);
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1000); // 1 second commit interval
        props.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 10 * 1024 * 1024); // 10MB cache
        
        // Consumer configuration
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1000);
        
        // Error handling
        props.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                "org.apache.kafka.streams.errors.LogAndContinueExceptionHandler");
        props.put(StreamsConfig.DEFAULT_PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG,
                "org.apache.kafka.streams.errors.DefaultProductionExceptionHandler");
        
        // State store configuration
        props.put(StreamsConfig.STATE_DIR_CONFIG, "/tmp/kafka-streams");
        props.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, 3);
        
        // Security configuration if needed
        if (kafkaProperties.getSecurity() != null) {
            props.putAll(kafkaProperties.buildConsumerProperties());
        }
        
        return new KafkaStreamsConfiguration(props);
    }
    
    /**
     * Configure analytics streams topology
     */
    @Bean
    public StreamsBuilder analyticsStreamsBuilder() {
        StreamsBuilder builder = new StreamsBuilder();
        
        // Create state stores
        createStateStores(builder);
        
        // Configure transaction analytics stream
        configureTransactionStream(builder);
        
        // Configure payment analytics stream
        configurePaymentStream(builder);
        
        // Configure user activity stream
        configureUserActivityStream(builder);
        
        // Configure fraud detection stream
        configureFraudDetectionStream(builder);
        
        // Configure system metrics stream
        configureSystemMetricsStream(builder);
        
        return builder;
    }
    
    /**
     * Create state stores for maintaining aggregation state
     */
    private void createStateStores(StreamsBuilder builder) {
        // User metrics store
        StoreBuilder<KeyValueStore<String, TransactionMetrics>> userMetricsStore = 
                Stores.keyValueStoreBuilder(
                        Stores.persistentKeyValueStore("user-metrics-store"),
                        Serdes.String(),
                        new JsonSerde<>(TransactionMetrics.class))
                .withLoggingEnabled(Map.of("cleanup.policy", "compact"));
        builder.addStateStore(userMetricsStore);
        
        // Merchant metrics store
        StoreBuilder<KeyValueStore<String, MerchantMetrics>> merchantMetricsStore = 
                Stores.keyValueStoreBuilder(
                        Stores.persistentKeyValueStore("merchant-metrics-store"),
                        Serdes.String(),
                        new JsonSerde<>(MerchantMetrics.class))
                .withLoggingEnabled(Map.of("cleanup.policy", "compact"));
        builder.addStateStore(merchantMetricsStore);
        
        // Session metrics store
        StoreBuilder<KeyValueStore<String, UserSession>> sessionStore = 
                Stores.keyValueStoreBuilder(
                        Stores.persistentKeyValueStore("session-store"),
                        Serdes.String(),
                        new JsonSerde<>(UserSession.class))
                .withLoggingEnabled(Map.of("cleanup.policy", "compact"));
        builder.addStateStore(sessionStore);
    }
    
    /**
     * Configure transaction analytics stream processing
     */
    private void configureTransactionStream(StreamsBuilder builder) {
        KStream<String, TransactionEvent> transactionStream = builder
                .stream(KafkaTopics.TRANSACTION_EVENTS, 
                        Consumed.with(Serdes.String(), new JsonSerde<>(TransactionEvent.class)));
        
        // Real-time transaction metrics
        transactionStream
                .peek((key, value) -> log.debug("Processing transaction: {}", value.getTransactionId()))
                .foreach((key, value) -> metricsAggregator.recordTransaction(value));
        
        // Windowed aggregations for transaction volume
        transactionStream
                .selectKey((key, value) -> value.getUserId())
                .groupByKey(Grouped.with(Serdes.String(), new JsonSerde<>(TransactionEvent.class)))
                .windowedBy(TimeWindows.of(Duration.ofMinutes(5)).grace(Duration.ofMinutes(1)))
                .aggregate(
                        TransactionAggregate::new,
                        (key, value, aggregate) -> {
                            aggregate.addTransaction(value);
                            return aggregate;
                        },
                        Materialized.<String, TransactionAggregate, WindowStore<Bytes, byte[]>>as("transaction-aggregates")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(new JsonSerde<>(TransactionAggregate.class))
                )
                .toStream()
                .foreach((windowedKey, aggregate) -> {
                    log.debug("Transaction aggregate for user {}: {}", 
                            windowedKey.key(), aggregate.getCount());
                });
        
        // Branch stream by transaction type
        Map<String, KStream<String, TransactionEvent>> branches = transactionStream
                .split(Named.as("transaction-type-"))
                .branch((key, value) -> value.getType() == TransactionType.PAYMENT, 
                        Branched.as("payment"))
                .branch((key, value) -> value.getType() == TransactionType.TRANSFER, 
                        Branched.as("transfer"))
                .branch((key, value) -> value.getType() == TransactionType.WITHDRAWAL, 
                        Branched.as("withdrawal"))
                .defaultBranch(Branched.as("other"));
        
        // Process each branch differently
        branches.get("transaction-type-payment")
                .groupByKey()
                .windowedBy(TimeWindows.of(Duration.ofMinutes(1)))
                .count()
                .toStream()
                .foreach((key, count) -> {
                    metricsAggregator.recordPerformanceMetric("payments", "count", count, true);
                });
        
        // High-value transaction detection
        transactionStream
                .filter((key, value) -> value.getAmount().compareTo(java.math.BigDecimal.valueOf(10000)) > 0)
                .foreach((key, value) -> {
                    log.info("High-value transaction detected: {} for amount: {}", 
                            value.getTransactionId(), value.getAmount());
                    // Trigger high-value transaction workflow
                    publishHighValueAlert(value);
                });
        
        // Failed transaction analysis
        transactionStream
                .filter((key, value) -> value.getStatus() == TransactionStatus.FAILED)
                .groupBy((key, value) -> value.getFailureReason(),
                        Grouped.with(Serdes.String(), new JsonSerde<>(TransactionEvent.class)))
                .windowedBy(TimeWindows.of(Duration.ofHours(1)))
                .count()
                .toStream()
                .foreach((windowedKey, count) -> {
                    if (count > 10) {
                        log.warn("High failure count for reason '{}': {}", windowedKey.key(), count);
                        publishFailureAlert(windowedKey.key(), count);
                    }
                });
    }
    
    /**
     * Configure payment analytics stream processing
     */
    private void configurePaymentStream(StreamsBuilder builder) {
        KStream<String, PaymentEvent> paymentStream = builder
                .stream(KafkaTopics.PAYMENT_EVENTS, 
                        Consumed.with(Serdes.String(), new JsonSerde<>(PaymentEvent.class)));
        
        // Record payment metrics
        paymentStream
                .foreach((key, value) -> metricsAggregator.recordPayment(value));
        
        // Payment method distribution
        paymentStream
                .groupBy((key, value) -> value.getPaymentMethod().toString(),
                        Grouped.with(Serdes.String(), new JsonSerde<>(PaymentEvent.class)))
                .windowedBy(TimeWindows.of(Duration.ofMinutes(15)))
                .count()
                .toStream()
                .foreach((windowedKey, count) -> {
                    log.debug("Payment method {} usage: {}", windowedKey.key(), count);
                });
        
        // Merchant payment aggregations
        paymentStream
                .filter((key, value) -> value.getMerchantId() != null)
                .selectKey((key, value) -> value.getMerchantId())
                .groupByKey(Grouped.with(Serdes.String(), new JsonSerde<>(PaymentEvent.class)))
                .windowedBy(TimeWindows.of(Duration.ofMinutes(5)))
                .aggregate(
                        MerchantPaymentAggregate::new,
                        (merchantId, payment, aggregate) -> {
                            aggregate.addPayment(payment);
                            return aggregate;
                        },
                        Materialized.<String, MerchantPaymentAggregate, WindowStore<Bytes, byte[]>>as("merchant-payments")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(new JsonSerde<>(MerchantPaymentAggregate.class))
                )
                .toStream()
                .foreach((windowedKey, aggregate) -> {
                    // Update merchant dashboard metrics
                    updateMerchantDashboard(windowedKey.key(), aggregate);
                });
        
        // Payment processing time analysis
        paymentStream
                .filter((key, value) -> value.getProcessingTime() != null)
                .mapValues(value -> value.getProcessingTime())
                .groupBy((key, value) -> "global", 
                        Grouped.with(Serdes.String(), Serdes.Long()))
                .windowedBy(TimeWindows.of(Duration.ofMinutes(1)))
                .aggregate(
                        ProcessingTimeStats::new,
                        (key, processingTime, stats) -> {
                            stats.addProcessingTime(processingTime);
                            return stats;
                        },
                        Materialized.<String, ProcessingTimeStats, WindowStore<Bytes, byte[]>>as("processing-time-stats")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(new JsonSerde<>(ProcessingTimeStats.class))
                )
                .toStream()
                .foreach((windowedKey, stats) -> {
                    log.info("Average processing time: {} ms, P95: {} ms", 
                            stats.getAverage(), stats.getP95());
                });
    }
    
    /**
     * Configure user activity stream processing
     */
    private void configureUserActivityStream(StreamsBuilder builder) {
        KStream<String, UserActivityEvent> activityStream = builder
                .stream(KafkaTopics.USER_ACTIVITY, 
                        Consumed.with(Serdes.String(), new JsonSerde<>(UserActivityEvent.class)));
        
        // Record activity metrics
        activityStream
                .foreach((key, value) -> metricsAggregator.recordUserActivity(value));
        
        // Session tracking using session windows
        activityStream
                .selectKey((key, value) -> value.getUserId())
                .groupByKey(Grouped.with(Serdes.String(), new JsonSerde<>(UserActivityEvent.class)))
                .windowedBy(SessionWindows.with(Duration.ofMinutes(30)))
                .aggregate(
                        UserSession::new,
                        (userId, activity, session) -> {
                            session.addActivity(activity);
                            return session;
                        },
                        (key, session1, session2) -> session1.merge(session2),
                        Materialized.<String, UserSession, SessionStore<Bytes, byte[]>>as("user-sessions")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(new JsonSerde<>(UserSession.class))
                )
                .toStream()
                .foreach((windowedKey, session) -> {
                    log.debug("Session for user {}: duration {} minutes, {} activities",
                            windowedKey.key(), session.getDurationMinutes(), session.getActivityCount());
                });
        
        // Real-time active user tracking
        activityStream
                .selectKey((key, value) -> "global")
                .groupByKey(Grouped.with(Serdes.String(), new JsonSerde<>(UserActivityEvent.class)))
                .windowedBy(TimeWindows.of(Duration.ofMinutes(1)))
                .aggregate(
                        java.util.HashSet<String>::new,
                        (key, activity, userSet) -> {
                            userSet.add(activity.getUserId());
                            return userSet;
                        },
                        Materialized.<String, java.util.HashSet<String>, WindowStore<Bytes, byte[]>>as("active-users")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(new JsonSerde<>())
                )
                .toStream()
                .foreach((windowedKey, userSet) -> {
                    log.info("Active users in last minute: {}", userSet.size());
                    updateActiveUserMetric(userSet.size());
                });
        
        // Page view analytics
        activityStream
                .filter((key, value) -> value.getPage() != null)
                .groupBy((key, value) -> value.getPage(),
                        Grouped.with(Serdes.String(), new JsonSerde<>(UserActivityEvent.class)))
                .windowedBy(TimeWindows.of(Duration.ofMinutes(5)))
                .count()
                .toStream()
                .foreach((windowedKey, count) -> {
                    log.debug("Page '{}' views: {}", windowedKey.key(), count);
                });
    }
    
    /**
     * Configure fraud detection stream
     */
    private void configureFraudDetectionStream(StreamsBuilder builder) {
        KStream<String, FraudAlert> fraudStream = builder
                .stream(KafkaTopics.FRAUD_ALERTS, 
                        Consumed.with(Serdes.String(), new JsonSerde<>(FraudAlert.class)));
        
        // Real-time fraud metrics
        fraudStream
                .foreach((key, alert) -> {
                    log.warn("Fraud alert: {} for user: {} with risk score: {}", 
                            alert.getAlertType(), alert.getUserId(), alert.getRiskScore());
                });
        
        // Fraud pattern aggregation
        fraudStream
                .groupBy((key, value) -> value.getAlertType().toString(),
                        Grouped.with(Serdes.String(), new JsonSerde<>(FraudAlert.class)))
                .windowedBy(TimeWindows.of(Duration.ofHours(1)))
                .count()
                .toStream()
                .foreach((windowedKey, count) -> {
                    if (count > 50) {
                        log.error("High fraud alert count for type '{}': {}", windowedKey.key(), count);
                    }
                });
        
        // User fraud score tracking
        fraudStream
                .selectKey((key, value) -> value.getUserId())
                .groupByKey(Grouped.with(Serdes.String(), new JsonSerde<>(FraudAlert.class)))
                .windowedBy(TimeWindows.of(Duration.ofDays(1)))
                .aggregate(
                        FraudUserProfile::new,
                        (userId, alert, profile) -> {
                            profile.addAlert(alert);
                            return profile;
                        },
                        Materialized.<String, FraudUserProfile, WindowStore<Bytes, byte[]>>as("fraud-user-profiles")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(new JsonSerde<>(FraudUserProfile.class))
                )
                .toStream()
                .filter((windowedKey, profile) -> profile.getRiskScore() > 0.8)
                .foreach((windowedKey, profile) -> {
                    log.error("High-risk user detected: {} with score: {}", 
                            windowedKey.key(), profile.getRiskScore());
                });
    }
    
    /**
     * Configure system metrics stream
     */
    private void configureSystemMetricsStream(StreamsBuilder builder) {
        KStream<String, SystemMetric> metricsStream = builder
                .stream("system-metrics", 
                        Consumed.with(Serdes.String(), new JsonSerde<>(SystemMetric.class)));
        
        // System health monitoring
        metricsStream
                .filter((key, metric) -> metric.getMetricType() == SystemMetricType.LATENCY)
                .groupBy((key, metric) -> metric.getServiceName(),
                        Grouped.with(Serdes.String(), new JsonSerde<>(SystemMetric.class)))
                .windowedBy(TimeWindows.of(Duration.ofMinutes(1)))
                .aggregate(
                        LatencyStats::new,
                        (service, metric, stats) -> {
                            stats.addLatency(metric.getValue().longValue());
                            return stats;
                        },
                        Materialized.<String, LatencyStats, WindowStore<Bytes, byte[]>>as("service-latency")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(new JsonSerde<>(LatencyStats.class))
                )
                .toStream()
                .foreach((windowedKey, stats) -> {
                    if (stats.getP95() > 1000) {
                        log.warn("High latency detected for service {}: P95 = {} ms", 
                                windowedKey.key(), stats.getP95());
                    }
                });
        
        // Error rate monitoring
        metricsStream
                .filter((key, metric) -> metric.getMetricType() == SystemMetricType.ERROR_RATE)
                .groupBy((key, metric) -> metric.getServiceName(),
                        Grouped.with(Serdes.String(), new JsonSerde<>(SystemMetric.class)))
                .windowedBy(TimeWindows.of(Duration.ofMinutes(5)))
                .aggregate(
                        () -> new ErrorRateStats(),
                        (service, metric, stats) -> {
                            stats.addErrorRate(metric.getValue());
                            return stats;
                        },
                        Materialized.<String, ErrorRateStats, WindowStore<Bytes, byte[]>>as("service-errors")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(new JsonSerde<>(ErrorRateStats.class))
                )
                .toStream()
                .filter((windowedKey, stats) -> stats.getAverageErrorRate().compareTo(java.math.BigDecimal.valueOf(5)) > 0)
                .foreach((windowedKey, stats) -> {
                    log.error("High error rate for service {}: {}%", 
                            windowedKey.key(), stats.getAverageErrorRate());
                });
    }
    
    // Helper methods
    
    private void publishHighValueAlert(TransactionEvent transaction) {
        // Publish to high-value transaction topic for further processing
        log.info("Publishing high-value alert for transaction: {}", transaction.getTransactionId());
    }
    
    private void publishFailureAlert(String failureReason, Long count) {
        // Publish failure alert to monitoring system
        log.warn("Publishing failure alert: {} failures for reason: {}", count, failureReason);
    }
    
    private void updateMerchantDashboard(String merchantId, MerchantPaymentAggregate aggregate) {
        // Update merchant-specific dashboard metrics
        log.debug("Updating dashboard for merchant {}: {} payments, total: {}", 
                merchantId, aggregate.getPaymentCount(), aggregate.getTotalAmount());
    }
    
    private void updateActiveUserMetric(int activeUsers) {
        // Update active user metric in monitoring system
        log.debug("Active users: {}", activeUsers);
    }
    
    /**
     * Global exception handler for Kafka Streams
     */
    @Bean
    public StreamsUncaughtExceptionHandler streamsUncaughtExceptionHandler() {
        return (exception) -> {
            log.error("Kafka Streams uncaught exception", exception);
            
            // Determine response based on exception type
            if (exception instanceof org.apache.kafka.streams.errors.StreamsException) {
                // For streams exceptions, continue processing
                return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD;
            } else {
                // For other exceptions, shutdown gracefully
                return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_APPLICATION;
            }
        };
    }
}