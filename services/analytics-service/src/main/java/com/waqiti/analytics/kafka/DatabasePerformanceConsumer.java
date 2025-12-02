package com.waqiti.analytics.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.DatabasePerformanceEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.analytics.service.DatabaseMonitoringService;
import com.waqiti.analytics.service.AlertService;
import com.waqiti.analytics.model.DatabasePerformanceMetric;
import com.waqiti.analytics.repository.DatabasePerformanceRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Consumer for processing database performance events.
 * Monitors database metrics, query performance, and connection health.
 */
@Slf4j
@Component
public class DatabasePerformanceConsumer extends BaseKafkaConsumer<DatabasePerformanceEvent> {

    private static final String TOPIC = "database-performance-events";

    private final DatabaseMonitoringService databaseMonitoringService;
    private final AlertService alertService;
    private final DatabasePerformanceRepository databasePerformanceRepository;

    // Metrics
    private final Counter processedCounter;
    private final Counter slowQueryCounter;
    private final Counter connectionIssueCounter;
    private final Timer processingTimer;

    @Autowired
    public DatabasePerformanceConsumer(
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            DatabaseMonitoringService databaseMonitoringService,
            AlertService alertService,
            DatabasePerformanceRepository databasePerformanceRepository) {
        super(objectMapper, TOPIC);
        this.databaseMonitoringService = databaseMonitoringService;
        this.alertService = alertService;
        this.databasePerformanceRepository = databasePerformanceRepository;

        this.processedCounter = Counter.builder("database_performance_processed_total")
                .description("Total database performance events processed")
                .register(meterRegistry);
        this.slowQueryCounter = Counter.builder("slow_query_detected_total")
                .description("Total slow queries detected")
                .register(meterRegistry);
        this.connectionIssueCounter = Counter.builder("database_connection_issue_total")
                .description("Total database connection issues")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("database_performance_processing_duration")
                .description("Time taken to process database performance events")
                .register(meterRegistry);
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(topics = TOPIC, groupId = "analytics-service-database-performance-group")
    @Transactional
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment ack) {

        Timer.Sample sample = Timer.start();

        try {
            log.info("Processing database performance event - Key: {}, Partition: {}, Offset: {}",
                    key, partition, record.offset());

            DatabasePerformanceEvent event = deserializeEvent(record.value(), DatabasePerformanceEvent.class);

            // Validate required fields
            validateEvent(event);

            // Check for duplicate processing
            if (isAlreadyProcessed(event.getDatabaseId(), event.getMetricName(), event.getTimestamp())) {
                log.info("Database performance metric already processed: {} - {}",
                        event.getDatabaseId(), event.getMetricName());
                ack.acknowledge();
                return;
            }

            // Process the database performance event
            processDatabasePerformanceEvent(event);

            processedCounter.increment();
            log.info("Successfully processed database performance event: {} - {}",
                    event.getDatabaseId(), event.getMetricName());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing database performance event: {}", record.value(), e);
            throw new RuntimeException("Failed to process database performance event", e);
        } finally {
            sample.stop(processingTimer);
        }
    }

    private void processDatabasePerformanceEvent(DatabasePerformanceEvent event) {
        try {
            // Create database performance metric record
            DatabasePerformanceMetric metric = createDatabasePerformanceMetric(event);

            // Check for slow queries
            if (isSlowQuery(event)) {
                handleSlowQuery(event, metric);
                slowQueryCounter.increment();
            }

            // Check for connection issues
            if (hasConnectionIssues(event)) {
                handleConnectionIssues(event, metric);
                connectionIssueCounter.increment();
            }

            // Check for deadlocks
            if (hasDeadlocks(event)) {
                handleDeadlocks(event, metric);
            }

            // Monitor resource utilization
            checkResourceUtilization(event, metric);

            // Analyze query patterns
            analyzeQueryPatterns(event, metric);

            // Update database health
            updateDatabaseHealth(event, metric);

            // Save the metric
            databasePerformanceRepository.save(metric);

            // Generate optimization recommendations
            generateOptimizationRecommendations(event, metric);

            log.info("Processed database performance metric: {} - {} = {}",
                    event.getDatabaseId(), event.getMetricName(), event.getMetricValue());

        } catch (Exception e) {
            log.error("Error processing database performance event: {} - {}",
                    event.getDatabaseId(), event.getMetricName(), e);
            throw new RuntimeException("Failed to process database performance event", e);
        }
    }

    private DatabasePerformanceMetric createDatabasePerformanceMetric(DatabasePerformanceEvent event) {
        return DatabasePerformanceMetric.builder()
                .databaseId(event.getDatabaseId())
                .databaseName(event.getDatabaseName())
                .databaseType(event.getDatabaseType())
                .instanceId(event.getInstanceId())
                .metricName(event.getMetricName())
                .metricValue(event.getMetricValue())
                .metricUnit(event.getMetricUnit())
                .queryId(event.getQueryId())
                .queryText(event.getQueryText())
                .queryExecutionTime(event.getQueryExecutionTime())
                .activeConnections(event.getActiveConnections())
                .maxConnections(event.getMaxConnections())
                .lockWaitTime(event.getLockWaitTime())
                .deadlockCount(event.getDeadlockCount())
                .bufferHitRatio(event.getBufferHitRatio())
                .indexUsage(event.getIndexUsage())
                .timestamp(event.getTimestamp())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private boolean isSlowQuery(DatabasePerformanceEvent event) {
        if (event.getQueryExecutionTime() == null) {
            return false;
        }

        // Get slow query threshold for this database
        double threshold = databaseMonitoringService.getSlowQueryThreshold(event.getDatabaseId());
        return event.getQueryExecutionTime() > threshold;
    }

    private void handleSlowQuery(DatabasePerformanceEvent event, DatabasePerformanceMetric metric) {
        try {
            metric.setSlowQuery(true);

            // Analyze query for optimization opportunities
            var queryAnalysis = databaseMonitoringService.analyzeQuery(
                event.getDatabaseId(), event.getQueryText(), event.getQueryExecutionTime()
            );

            metric.setQueryAnalysis(queryAnalysis);

            // Create slow query alert
            alertService.createSlowQueryAlert(
                event.getDatabaseId(),
                event.getQueryId(),
                event.getQueryText(),
                event.getQueryExecutionTime()
            );

            // Check if this is a frequently slow query
            boolean frequentlySlow = databaseMonitoringService.isFrequentlySlowQuery(
                event.getDatabaseId(), event.getQueryId()
            );

            if (frequentlySlow) {
                metric.setFrequentlySlowQuery(true);

                // Create high priority alert for frequently slow queries
                alertService.createHighPriorityAlert(event.getDatabaseId(),
                    "FREQUENTLY_SLOW_QUERY", metric);

                // Trigger automatic query optimization if enabled
                databaseMonitoringService.triggerQueryOptimization(
                    event.getDatabaseId(), event.getQueryId(), event.getQueryText()
                );
            }

            log.warn("Slow query detected: {} - Query ID: {} ({}ms)",
                    event.getDatabaseId(), event.getQueryId(), event.getQueryExecutionTime());

        } catch (Exception e) {
            log.error("Error handling slow query: {} - {}",
                    event.getDatabaseId(), event.getQueryId(), e);
            throw new RuntimeException("Failed to handle slow query", e);
        }
    }

    private boolean hasConnectionIssues(DatabasePerformanceEvent event) {
        if (event.getActiveConnections() == null || event.getMaxConnections() == null) {
            return false;
        }

        // Check if connection usage is above threshold
        double connectionUsageRatio = (double) event.getActiveConnections() / event.getMaxConnections();
        return connectionUsageRatio > 0.8; // 80% threshold
    }

    private void handleConnectionIssues(DatabasePerformanceEvent event, DatabasePerformanceMetric metric) {
        try {
            metric.setConnectionIssue(true);

            double connectionUsageRatio = (double) event.getActiveConnections() / event.getMaxConnections();
            metric.setConnectionUsageRatio(connectionUsageRatio);

            // Create connection issue alert
            alertService.createConnectionAlert(
                event.getDatabaseId(),
                event.getActiveConnections(),
                event.getMaxConnections(),
                connectionUsageRatio
            );

            // Check for connection pool exhaustion
            if (connectionUsageRatio > 0.95) {
                alertService.createCriticalAlert(event.getDatabaseId(),
                    "CONNECTION_POOL_EXHAUSTION", metric);

                // Trigger connection pool scaling if enabled
                databaseMonitoringService.triggerConnectionPoolScaling(
                    event.getDatabaseId(), event.getActiveConnections()
                );
            }

            log.warn("Database connection issue: {} - {}/{} connections ({}%)",
                    event.getDatabaseId(), event.getActiveConnections(),
                    event.getMaxConnections(), Math.round(connectionUsageRatio * 100));

        } catch (Exception e) {
            log.error("Error handling connection issues: {}", event.getDatabaseId(), e);
            throw new RuntimeException("Failed to handle connection issues", e);
        }
    }

    private boolean hasDeadlocks(DatabasePerformanceEvent event) {
        return event.getDeadlockCount() != null && event.getDeadlockCount() > 0;
    }

    private void handleDeadlocks(DatabasePerformanceEvent event, DatabasePerformanceMetric metric) {
        try {
            metric.setDeadlockDetected(true);

            // Create deadlock alert
            alertService.createDeadlockAlert(
                event.getDatabaseId(),
                event.getDeadlockCount(),
                event.getQueryId()
            );

            // Analyze deadlock patterns
            var deadlockAnalysis = databaseMonitoringService.analyzeDeadlockPatterns(
                event.getDatabaseId(), event.getQueryId()
            );

            if (deadlockAnalysis.isRecurringPattern()) {
                alertService.createHighPriorityAlert(event.getDatabaseId(),
                    "RECURRING_DEADLOCK_PATTERN", metric);

                // Suggest deadlock prevention strategies
                databaseMonitoringService.generateDeadlockPreventionStrategies(
                    event.getDatabaseId(), deadlockAnalysis
                );
            }

            log.warn("Database deadlock detected: {} - Count: {} (Query: {})",
                    event.getDatabaseId(), event.getDeadlockCount(), event.getQueryId());

        } catch (Exception e) {
            log.error("Error handling deadlocks: {}", event.getDatabaseId(), e);
            throw new RuntimeException("Failed to handle deadlocks", e);
        }
    }

    private void checkResourceUtilization(DatabasePerformanceEvent event, DatabasePerformanceMetric metric) {
        try {
            // Check buffer hit ratio
            if (event.getBufferHitRatio() != null && event.getBufferHitRatio() < 0.9) {
                metric.setLowBufferHitRatio(true);
                alertService.createResourceAlert(event.getDatabaseId(),
                    "LOW_BUFFER_HIT_RATIO", event.getBufferHitRatio());
            }

            // Check index usage
            if (event.getIndexUsage() != null && event.getIndexUsage() < 0.8) {
                metric.setLowIndexUsage(true);
                alertService.createResourceAlert(event.getDatabaseId(),
                    "LOW_INDEX_USAGE", event.getIndexUsage());

                // Suggest index optimization
                databaseMonitoringService.suggestIndexOptimization(
                    event.getDatabaseId(), event.getQueryId()
                );
            }

        } catch (Exception e) {
            log.error("Error checking resource utilization: {}", event.getDatabaseId(), e);
            // Don't fail the processing for resource utilization check errors
        }
    }

    private void analyzeQueryPatterns(DatabasePerformanceEvent event, DatabasePerformanceMetric metric) {
        try {
            if (event.getQueryText() != null) {
                // Analyze query patterns for optimization opportunities
                var patternAnalysis = databaseMonitoringService.analyzeQueryPatterns(
                    event.getDatabaseId(), event.getQueryText()
                );

                metric.setQueryPatternAnalysis(patternAnalysis);

                // Identify inefficient query patterns
                if (patternAnalysis.hasInefficientPatterns()) {
                    alertService.createQueryOptimizationAlert(
                        event.getDatabaseId(), patternAnalysis
                    );
                }
            }

        } catch (Exception e) {
            log.error("Error analyzing query patterns: {}", event.getDatabaseId(), e);
            // Don't fail the processing for query pattern analysis errors
        }
    }

    private void updateDatabaseHealth(DatabasePerformanceEvent event, DatabasePerformanceMetric metric) {
        try {
            // Calculate overall database health score
            double healthScore = databaseMonitoringService.calculateDatabaseHealthScore(
                event.getDatabaseId(), metric
            );

            metric.setHealthScore(healthScore);

            // Update database health status
            String healthStatus = databaseMonitoringService.determineHealthStatus(healthScore);
            databaseMonitoringService.updateDatabaseHealth(event.getDatabaseId(), healthStatus);

            // Check for health degradation
            String previousStatus = databaseMonitoringService.getPreviousHealthStatus(event.getDatabaseId());
            if (databaseMonitoringService.isHealthDegradation(previousStatus, healthStatus)) {
                alertService.createHealthDegradationAlert(
                    event.getDatabaseId(), previousStatus, healthStatus
                );
            }

        } catch (Exception e) {
            log.error("Error updating database health: {}", event.getDatabaseId(), e);
            // Don't fail the processing for health update errors
        }
    }

    private void generateOptimizationRecommendations(DatabasePerformanceEvent event,
                                                   DatabasePerformanceMetric metric) {
        try {
            // Generate performance optimization recommendations
            var recommendations = databaseMonitoringService.generateOptimizationRecommendations(
                event.getDatabaseId(), metric
            );

            if (!recommendations.isEmpty()) {
                // Store recommendations for DBA review
                databaseMonitoringService.storeOptimizationRecommendations(
                    event.getDatabaseId(), recommendations
                );

                // Send recommendations to database administrators
                alertService.sendOptimizationRecommendations(
                    event.getDatabaseId(), recommendations
                );
            }

        } catch (Exception e) {
            log.error("Error generating optimization recommendations: {}", event.getDatabaseId(), e);
            // Don't fail the processing for recommendation generation errors
        }
    }

    private boolean isAlreadyProcessed(String databaseId, String metricName, LocalDateTime timestamp) {
        return databasePerformanceRepository.existsByDatabaseIdAndMetricNameAndTimestamp(
            databaseId, metricName, timestamp
        );
    }

    private void validateEvent(DatabasePerformanceEvent event) {
        if (event.getDatabaseId() == null || event.getDatabaseId().trim().isEmpty()) {
            throw new IllegalArgumentException("Database ID cannot be null or empty");
        }
        if (event.getMetricName() == null || event.getMetricName().trim().isEmpty()) {
            throw new IllegalArgumentException("Metric name cannot be null or empty");
        }
        if (event.getMetricValue() == null) {
            throw new IllegalArgumentException("Metric value cannot be null");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
    }

    @Override
    protected void handleProcessingError(String message, Exception error) {
        log.error("Database performance processing error: {}", message, error);
        // Additional error handling logic
    }

    @Override
    protected void logProcessingMetrics(String key, long processingTime) {
        log.debug("Processed database performance event - Key: {}, Time: {}ms", key, processingTime);
    }
}