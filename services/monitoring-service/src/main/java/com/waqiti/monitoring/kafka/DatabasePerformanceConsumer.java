package com.waqiti.monitoring.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.kafka.KafkaRetryHandler;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.monitoring.model.MonitoringEvent;
import com.waqiti.monitoring.service.AlertingService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
public class DatabasePerformanceConsumer extends BaseKafkaConsumer {
    private static final Logger logger = LoggerFactory.getLogger(DatabasePerformanceConsumer.class);
    private static final String CONSUMER_GROUP_ID = "database-performance-group";
    private static final String DLQ_TOPIC = "database-performance-dlq";
    
    private final MetricsService metricsService;
    private final AlertingService alertingService;
    private final KafkaRetryHandler retryHandler;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    
    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Value("${monitoring.database.query-time-threshold-ms:1000}")
    private long queryTimeThresholdMs;
    
    @Value("${monitoring.database.connection-pool-warning-threshold:80}")
    private int connectionPoolWarningThreshold;
    
    @Value("${monitoring.database.deadlock-detection-enabled:true}")
    private boolean deadlockDetectionEnabled;
    
    @Value("${monitoring.database.slow-query-log-threshold-ms:5000}")
    private long slowQueryLogThresholdMs;
    
    @Value("${monitoring.database.table-lock-warning-seconds:30}")
    private int tableLockWarningSeconds;
    
    @Value("${monitoring.database.replication-lag-threshold-ms:1000}")
    private long replicationLagThresholdMs;
    
    @Value("${monitoring.database.cache-hit-ratio-threshold:0.9}")
    private double cacheHitRatioThreshold;
    
    @Value("${monitoring.database.index-usage-threshold:0.7}")
    private double indexUsageThreshold;
    
    @Value("${monitoring.database.transaction-rollback-threshold:0.05}")
    private double transactionRollbackThreshold;
    
    @Value("${monitoring.database.query-optimization-enabled:true}")
    private boolean queryOptimizationEnabled;
    
    @Value("${monitoring.database.vacuum-analysis-interval-hours:6}")
    private int vacuumAnalysisIntervalHours;
    
    @Value("${monitoring.database.statistics-collection-interval-minutes:15}")
    private int statisticsCollectionIntervalMinutes;
    
    private final Map<String, QueryMetrics> queryMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, ConnectionPoolMetrics> connectionPoolMap = new ConcurrentHashMap<>();
    private final Map<String, TransactionMetrics> transactionMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, TableMetrics> tableMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, IndexMetrics> indexMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, ReplicationMetrics> replicationMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, LockMetrics> lockMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, CacheMetrics> cacheMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, DeadlockInfo> deadlockMap = new ConcurrentHashMap<>();
    private final Map<String, SchemaMetrics> schemaMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, BackupMetrics> backupMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, MaintenanceMetrics> maintenanceMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, QueryPlan> queryPlanMap = new ConcurrentHashMap<>();
    private final Map<String, DatabaseHealth> databaseHealthMap = new ConcurrentHashMap<>();
    private final Map<String, PerformanceBaseline> performanceBaselineMap = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(5);
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    
    public DatabasePerformanceConsumer(MetricsService metricsService,
                                     AlertingService alertingService,
                                     KafkaRetryHandler retryHandler,
                                     ObjectMapper objectMapper,
                                     MeterRegistry meterRegistry) {
        this.metricsService = metricsService;
        this.alertingService = alertingService;
        this.retryHandler = retryHandler;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }
    
    private Counter processedEventsCounter;
    private Counter errorCounter;
    private Counter dlqCounter;
    private Counter slowQueryCounter;
    private Counter deadlockCounter;
    private Counter rollbackCounter;
    private Gauge connectionPoolGauge;
    private Gauge replicationLagGauge;
    private Gauge cacheHitRatioGauge;
    private Timer processingTimer;
    private Timer queryExecutionTimer;
    
    @PostConstruct
    public void init() {
        initializeMetrics();
        initializeResilience();
        startScheduledTasks();
        initializeDatabaseTracking();
        logger.info("DatabasePerformanceConsumer initialized with query threshold: {}ms", 
                    queryTimeThresholdMs);
    }
    
    private void initializeMetrics() {
        processedEventsCounter = Counter.builder("database.performance.processed")
            .description("Total database performance events processed")
            .register(meterRegistry);
            
        errorCounter = Counter.builder("database.performance.errors")
            .description("Total database performance errors")
            .register(meterRegistry);
            
        dlqCounter = Counter.builder("database.performance.dlq")
            .description("Total messages sent to DLQ")
            .register(meterRegistry);
            
        slowQueryCounter = Counter.builder("database.slow_queries")
            .description("Total slow queries detected")
            .register(meterRegistry);
            
        deadlockCounter = Counter.builder("database.deadlocks")
            .description("Total deadlocks detected")
            .register(meterRegistry);
            
        rollbackCounter = Counter.builder("database.rollbacks")
            .description("Total transaction rollbacks")
            .register(meterRegistry);
            
        connectionPoolGauge = Gauge.builder("database.connection_pool.usage", this, 
            consumer -> calculateConnectionPoolUsage())
            .description("Database connection pool usage")
            .register(meterRegistry);
            
        replicationLagGauge = Gauge.builder("database.replication.lag", this,
            consumer -> calculateReplicationLag())
            .description("Database replication lag")
            .register(meterRegistry);
            
        cacheHitRatioGauge = Gauge.builder("database.cache.hit_ratio", this,
            consumer -> calculateCacheHitRatio())
            .description("Database cache hit ratio")
            .register(meterRegistry);
            
        processingTimer = Timer.builder("database.performance.processing.time")
            .description("Database performance processing time")
            .register(meterRegistry);
            
        queryExecutionTimer = Timer.builder("database.query.execution.time")
            .description("Query execution time")
            .publishPercentiles(0.5, 0.75, 0.95, 0.99)
            .register(meterRegistry);
    }
    
    private void initializeResilience() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .slidingWindowSize(10)
            .build();
            
        circuitBreaker = CircuitBreakerRegistry.of(circuitBreakerConfig)
            .circuitBreaker("database-performance", circuitBreakerConfig);
            
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .retryExceptions(Exception.class)
            .build();
            
        retry = RetryRegistry.of(retryConfig).retry("database-performance", retryConfig);
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> logger.warn("Circuit breaker state transition: {}", event));
    }
    
    private void startScheduledTasks() {
        scheduledExecutor.scheduleAtFixedRate(
            this::analyzeDatabasePerformance, 
            0, 5, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::checkConnectionPools, 
            0, 1, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::monitorReplication, 
            0, 30, TimeUnit.SECONDS
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::detectSlowQueries, 
            0, 2, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::analyzeIndexUsage, 
            0, 30, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::collectStatistics, 
            0, statisticsCollectionIntervalMinutes, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::analyzeVacuumNeeds, 
            0, vacuumAnalysisIntervalHours, TimeUnit.HOURS
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::generatePerformanceReport, 
            0, 1, TimeUnit.HOURS
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::cleanupOldData, 
            0, 24, TimeUnit.HOURS
        );
    }
    
    private void initializeDatabaseTracking() {
        List<String> databases = Arrays.asList(
            "primary", "replica-1", "replica-2", "analytics", "archive"
        );
        
        databases.forEach(db -> {
            DatabaseHealth health = new DatabaseHealth(db);
            databaseHealthMap.put(db, health);
            
            PerformanceBaseline baseline = new PerformanceBaseline(db);
            performanceBaselineMap.put(db, baseline);
        });
    }
    
    @KafkaListener(
        topics = "database-performance-events",
        groupId = CONSUMER_GROUP_ID,
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
        @Payload String message,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
        Acknowledgment acknowledgment
    ) {
        MDC.put("topic", topic);
        MDC.put("partition", String.valueOf(partition));
        MDC.put("offset", String.valueOf(offset));
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            processDatabasePerformanceEvent(message, timestamp);
            acknowledgment.acknowledge();
            processedEventsCounter.increment();
            
        } catch (Exception e) {
            handleProcessingError(message, e, acknowledgment);
            
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }
    
    private void processDatabasePerformanceEvent(String message, long timestamp) throws Exception {
        JsonNode event = objectMapper.readTree(message);
        String eventType = event.path("type").asText();
        String eventId = event.path("eventId").asText();
        
        logger.debug("Processing database performance event: {} - {}", eventType, eventId);
        
        Callable<Void> processTask = () -> {
            switch (eventType) {
                case "QUERY_EXECUTION":
                    handleQueryExecution(event, timestamp);
                    break;
                case "CONNECTION_POOL_STATUS":
                    handleConnectionPoolStatus(event, timestamp);
                    break;
                case "TRANSACTION_METRICS":
                    handleTransactionMetrics(event, timestamp);
                    break;
                case "TABLE_STATISTICS":
                    handleTableStatistics(event, timestamp);
                    break;
                case "INDEX_USAGE":
                    handleIndexUsage(event, timestamp);
                    break;
                case "REPLICATION_STATUS":
                    handleReplicationStatus(event, timestamp);
                    break;
                case "LOCK_MONITORING":
                    handleLockMonitoring(event, timestamp);
                    break;
                case "CACHE_PERFORMANCE":
                    handleCachePerformance(event, timestamp);
                    break;
                case "DEADLOCK_DETECTION":
                    handleDeadlockDetection(event, timestamp);
                    break;
                case "SCHEMA_CHANGE":
                    handleSchemaChange(event, timestamp);
                    break;
                case "BACKUP_STATUS":
                    handleBackupStatus(event, timestamp);
                    break;
                case "MAINTENANCE_OPERATION":
                    handleMaintenanceOperation(event, timestamp);
                    break;
                case "QUERY_PLAN_ANALYSIS":
                    handleQueryPlanAnalysis(event, timestamp);
                    break;
                case "DATABASE_HEALTH":
                    handleDatabaseHealth(event, timestamp);
                    break;
                case "PERFORMANCE_ANOMALY":
                    handlePerformanceAnomaly(event, timestamp);
                    break;
                default:
                    logger.warn("Unknown database performance event type: {}", eventType);
            }
            return null;
        };
        
        Retry.decorateCallable(retry, processTask).call();
    }
    
    private void handleQueryExecution(JsonNode event, long timestamp) {
        String queryId = event.path("queryId").asText();
        String database = event.path("database").asText();
        String queryText = event.path("queryText").asText();
        String queryType = event.path("queryType").asText();
        long executionTime = event.path("executionTime").asLong();
        long rowsAffected = event.path("rowsAffected").asLong();
        long rowsExamined = event.path("rowsExamined").asLong();
        String executionPlan = event.path("executionPlan").asText("");
        boolean usedIndex = event.path("usedIndex").asBoolean();
        String indexUsed = event.path("indexUsed").asText("");
        
        QueryMetrics metrics = queryMetricsMap.computeIfAbsent(
            queryType, k -> new QueryMetrics(queryType)
        );
        
        metrics.recordExecution(
            queryId, database, executionTime, rowsAffected, rowsExamined, usedIndex, timestamp
        );
        
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(Timer.builder("database.query.time")
            .tag("database", database)
            .tag("type", queryType)
            .register(meterRegistry));
        
        if (executionTime > slowQueryLogThresholdMs) {
            slowQueryCounter.increment();
            
            QueryPlan plan = new QueryPlan(queryId, queryText, executionPlan, executionTime);
            queryPlanMap.put(queryId, plan);
            
            if (queryOptimizationEnabled) {
                suggestQueryOptimization(queryId, plan);
            }
            
            alertingService.sendAlert(
                "SLOW_QUERY_DETECTED",
                "Medium",
                String.format("Slow query detected in %s: %dms",
                    database, executionTime),
                Map.of(
                    "queryId", queryId,
                    "database", database,
                    "executionTime", String.valueOf(executionTime),
                    "queryType", queryType,
                    "rowsExamined", String.valueOf(rowsExamined),
                    "usedIndex", String.valueOf(usedIndex)
                )
            );
        }
        
        if (!usedIndex && rowsExamined > 10000) {
            alertingService.sendAlert(
                "FULL_TABLE_SCAN",
                "High",
                String.format("Full table scan detected in %s examining %d rows",
                    database, rowsExamined),
                Map.of(
                    "queryId", queryId,
                    "database", database,
                    "rowsExamined", String.valueOf(rowsExamined),
                    "queryType", queryType
                )
            );
        }
        
        metricsService.recordMetric("database.query_execution", executionTime,
            Map.of("database", database, "type", queryType, "indexed", String.valueOf(usedIndex)));
    }
    
    private void handleConnectionPoolStatus(JsonNode event, long timestamp) {
        String poolName = event.path("poolName").asText();
        String database = event.path("database").asText();
        int activeConnections = event.path("activeConnections").asInt();
        int idleConnections = event.path("idleConnections").asInt();
        int maxConnections = event.path("maxConnections").asInt();
        int waitingRequests = event.path("waitingRequests").asInt();
        long avgWaitTime = event.path("avgWaitTime").asLong();
        int failedConnections = event.path("failedConnections").asInt();
        
        ConnectionPoolMetrics metrics = connectionPoolMap.computeIfAbsent(
            poolName, k -> new ConnectionPoolMetrics(poolName, database)
        );
        
        metrics.updateStatus(
            activeConnections, idleConnections, maxConnections,
            waitingRequests, avgWaitTime, failedConnections, timestamp
        );
        
        double usagePercentage = (double) activeConnections / maxConnections * 100;
        
        if (usagePercentage > connectionPoolWarningThreshold) {
            alertingService.sendAlert(
                "CONNECTION_POOL_HIGH_USAGE",
                "High",
                String.format("Connection pool %s usage at %.1f%%",
                    poolName, usagePercentage),
                Map.of(
                    "pool", poolName,
                    "database", database,
                    "active", String.valueOf(activeConnections),
                    "max", String.valueOf(maxConnections),
                    "waiting", String.valueOf(waitingRequests)
                )
            );
        }
        
        if (waitingRequests > 10) {
            logger.warn("Connection pool {} has {} waiting requests", poolName, waitingRequests);
        }
        
        metricsService.recordMetric("database.connection_pool.usage", usagePercentage,
            Map.of("pool", poolName, "database", database));
        
        metricsService.recordMetric("database.connection_pool.waiting", waitingRequests,
            Map.of("pool", poolName, "database", database));
    }
    
    private void handleTransactionMetrics(JsonNode event, long timestamp) {
        String database = event.path("database").asText();
        String transactionId = event.path("transactionId").asText();
        String transactionType = event.path("transactionType").asText();
        long duration = event.path("duration").asLong();
        String status = event.path("status").asText();
        int statementsCount = event.path("statementsCount").asInt();
        boolean autoCommit = event.path("autoCommit").asBoolean();
        String isolationLevel = event.path("isolationLevel").asText();
        JsonNode locks = event.path("locks");
        
        TransactionMetrics metrics = transactionMetricsMap.computeIfAbsent(
            database, k -> new TransactionMetrics(database)
        );
        
        metrics.recordTransaction(
            transactionId, transactionType, duration, status,
            statementsCount, autoCommit, isolationLevel, timestamp
        );
        
        if ("ROLLBACK".equals(status)) {
            rollbackCounter.increment();
            metrics.incrementRollbacks();
            
            if (metrics.getRollbackRate() > transactionRollbackThreshold) {
                alertingService.sendAlert(
                    "HIGH_ROLLBACK_RATE",
                    "High",
                    String.format("High transaction rollback rate in %s: %.2f%%",
                        database, metrics.getRollbackRate() * 100),
                    Map.of(
                        "database", database,
                        "rollbackRate", String.valueOf(metrics.getRollbackRate()),
                        "recentTransaction", transactionId
                    )
                );
            }
        }
        
        if (duration > 30000) {
            alertingService.sendAlert(
                "LONG_RUNNING_TRANSACTION",
                "Medium",
                String.format("Long running transaction in %s: %dms",
                    database, duration),
                Map.of(
                    "database", database,
                    "transactionId", transactionId,
                    "duration", String.valueOf(duration),
                    "statements", String.valueOf(statementsCount)
                )
            );
        }
        
        metricsService.recordMetric("database.transaction.duration", duration,
            Map.of("database", database, "type", transactionType, "status", status));
    }
    
    private void handleTableStatistics(JsonNode event, long timestamp) {
        String database = event.path("database").asText();
        String tableName = event.path("tableName").asText();
        long rowCount = event.path("rowCount").asLong();
        long dataSize = event.path("dataSize").asLong();
        long indexSize = event.path("indexSize").asLong();
        double fragmentation = event.path("fragmentation").asDouble();
        long lastAnalyzed = event.path("lastAnalyzed").asLong();
        long deadRows = event.path("deadRows").asLong();
        double bloatRatio = event.path("bloatRatio").asDouble();
        
        TableMetrics metrics = tableMetricsMap.computeIfAbsent(
            database + "." + tableName, k -> new TableMetrics(database, tableName)
        );
        
        metrics.updateStatistics(
            rowCount, dataSize, indexSize, fragmentation,
            lastAnalyzed, deadRows, bloatRatio, timestamp
        );
        
        if (bloatRatio > 0.3) {
            alertingService.sendAlert(
                "TABLE_BLOAT_DETECTED",
                "Medium",
                String.format("Table %s.%s has %.1f%% bloat",
                    database, tableName, bloatRatio * 100),
                Map.of(
                    "database", database,
                    "table", tableName,
                    "bloatRatio", String.valueOf(bloatRatio),
                    "deadRows", String.valueOf(deadRows)
                )
            );
        }
        
        if (fragmentation > 0.2) {
            logger.warn("Table {}.{} fragmentation at {}%",
                database, tableName, fragmentation * 100);
        }
        
        long daysSinceAnalyzed = (System.currentTimeMillis() - lastAnalyzed) / (1000 * 60 * 60 * 24);
        if (daysSinceAnalyzed > 7) {
            logger.info("Table {}.{} statistics are {} days old",
                database, tableName, daysSinceAnalyzed);
        }
        
        metricsService.recordMetric("database.table.size", dataSize,
            Map.of("database", database, "table", tableName));
        
        metricsService.recordMetric("database.table.rows", rowCount,
            Map.of("database", database, "table", tableName));
    }
    
    private void handleIndexUsage(JsonNode event, long timestamp) {
        String database = event.path("database").asText();
        String indexName = event.path("indexName").asText();
        String tableName = event.path("tableName").asText();
        long scansCount = event.path("scansCount").asLong();
        long tuplesRead = event.path("tuplesRead").asLong();
        long tuplesFetched = event.path("tuplesFetched").asLong();
        double usageRatio = event.path("usageRatio").asDouble();
        long indexSize = event.path("indexSize").asLong();
        boolean isUnique = event.path("isUnique").asBoolean();
        boolean isPrimary = event.path("isPrimary").asBoolean();
        
        IndexMetrics metrics = indexMetricsMap.computeIfAbsent(
            database + "." + indexName, k -> new IndexMetrics(database, tableName, indexName)
        );
        
        metrics.updateUsage(
            scansCount, tuplesRead, tuplesFetched, usageRatio,
            indexSize, isUnique, isPrimary, timestamp
        );
        
        if (usageRatio < indexUsageThreshold && scansCount > 0) {
            logger.info("Index {}.{} has low usage ratio: {}%",
                database, indexName, usageRatio * 100);
        }
        
        if (scansCount == 0 && !isPrimary) {
            metrics.markUnused();
            
            if (metrics.getDaysSinceUsed() > 30) {
                alertingService.sendAlert(
                    "UNUSED_INDEX_DETECTED",
                    "Low",
                    String.format("Index %s.%s unused for %d days",
                        database, indexName, metrics.getDaysSinceUsed()),
                    Map.of(
                        "database", database,
                        "index", indexName,
                        "table", tableName,
                        "size", String.valueOf(indexSize)
                    )
                );
            }
        }
        
        metricsService.recordMetric("database.index.usage", usageRatio,
            Map.of("database", database, "index", indexName, "table", tableName));
    }
    
    private void handleReplicationStatus(JsonNode event, long timestamp) {
        String primary = event.path("primary").asText();
        String replica = event.path("replica").asText();
        long replicationLag = event.path("replicationLag").asLong();
        String state = event.path("state").asText();
        long lastSyncTime = event.path("lastSyncTime").asLong();
        long pendingTransactions = event.path("pendingTransactions").asLong();
        double throughput = event.path("throughput").asDouble();
        boolean isSynchronous = event.path("isSynchronous").asBoolean();
        
        ReplicationMetrics metrics = replicationMetricsMap.computeIfAbsent(
            primary + "_" + replica, k -> new ReplicationMetrics(primary, replica)
        );
        
        metrics.updateStatus(
            replicationLag, state, lastSyncTime, pendingTransactions,
            throughput, isSynchronous, timestamp
        );
        
        if (replicationLag > replicationLagThresholdMs) {
            alertingService.sendAlert(
                "HIGH_REPLICATION_LAG",
                "High",
                String.format("Replication lag between %s and %s: %dms",
                    primary, replica, replicationLag),
                Map.of(
                    "primary", primary,
                    "replica", replica,
                    "lag", String.valueOf(replicationLag),
                    "state", state,
                    "pending", String.valueOf(pendingTransactions)
                )
            );
        }
        
        if (!"STREAMING".equals(state) && !"SYNCHRONOUS".equals(state)) {
            alertingService.sendAlert(
                "REPLICATION_STATE_ISSUE",
                "Critical",
                String.format("Replication issue between %s and %s: %s",
                    primary, replica, state),
                Map.of(
                    "primary", primary,
                    "replica", replica,
                    "state", state
                )
            );
        }
        
        metricsService.recordMetric("database.replication.lag", replicationLag,
            Map.of("primary", primary, "replica", replica));
        
        metricsService.recordMetric("database.replication.throughput", throughput,
            Map.of("primary", primary, "replica", replica));
    }
    
    private void handleLockMonitoring(JsonNode event, long timestamp) {
        String database = event.path("database").asText();
        String lockType = event.path("lockType").asText();
        String objectName = event.path("objectName").asText();
        String lockMode = event.path("lockMode").asText();
        String holdingSession = event.path("holdingSession").asText();
        String waitingSession = event.path("waitingSession").asText("");
        long lockDuration = event.path("lockDuration").asLong();
        boolean isBlocking = event.path("isBlocking").asBoolean();
        int waitingCount = event.path("waitingCount").asInt();
        
        LockMetrics metrics = lockMetricsMap.computeIfAbsent(
            database, k -> new LockMetrics(database)
        );
        
        metrics.recordLock(
            lockType, objectName, lockMode, holdingSession,
            waitingSession, lockDuration, isBlocking, waitingCount, timestamp
        );
        
        if (lockDuration > tableLockWarningSeconds * 1000) {
            alertingService.sendAlert(
                "LONG_HELD_LOCK",
                "High",
                String.format("Lock held for %d seconds on %s.%s",
                    lockDuration / 1000, database, objectName),
                Map.of(
                    "database", database,
                    "object", objectName,
                    "lockType", lockType,
                    "session", holdingSession,
                    "duration", String.valueOf(lockDuration),
                    "waiting", String.valueOf(waitingCount)
                )
            );
        }
        
        if (isBlocking && waitingCount > 5) {
            alertingService.sendAlert(
                "LOCK_CONTENTION",
                "High",
                String.format("Lock contention on %s.%s with %d waiting sessions",
                    database, objectName, waitingCount),
                Map.of(
                    "database", database,
                    "object", objectName,
                    "holdingSession", holdingSession,
                    "waitingCount", String.valueOf(waitingCount)
                )
            );
        }
        
        metricsService.recordMetric("database.locks.active", 1.0,
            Map.of("database", database, "type", lockType, "blocking", String.valueOf(isBlocking)));
    }
    
    private void handleCachePerformance(JsonNode event, long timestamp) {
        String database = event.path("database").asText();
        String cacheType = event.path("cacheType").asText();
        long hits = event.path("hits").asLong();
        long misses = event.path("misses").asLong();
        double hitRatio = event.path("hitRatio").asDouble();
        long evictions = event.path("evictions").asLong();
        long cacheSize = event.path("cacheSize").asLong();
        long maxCacheSize = event.path("maxCacheSize").asLong();
        double fillRatio = event.path("fillRatio").asDouble();
        
        CacheMetrics metrics = cacheMetricsMap.computeIfAbsent(
            database + "_" + cacheType, k -> new CacheMetrics(database, cacheType)
        );
        
        metrics.updatePerformance(
            hits, misses, hitRatio, evictions, cacheSize, maxCacheSize, fillRatio, timestamp
        );
        
        if (hitRatio < cacheHitRatioThreshold) {
            alertingService.sendAlert(
                "LOW_CACHE_HIT_RATIO",
                "Medium",
                String.format("Low cache hit ratio for %s %s: %.2f%%",
                    database, cacheType, hitRatio * 100),
                Map.of(
                    "database", database,
                    "cacheType", cacheType,
                    "hitRatio", String.valueOf(hitRatio),
                    "hits", String.valueOf(hits),
                    "misses", String.valueOf(misses)
                )
            );
        }
        
        if (fillRatio > 0.9) {
            logger.warn("Cache {} for {} is {}% full",
                cacheType, database, fillRatio * 100);
        }
        
        metricsService.recordMetric("database.cache.hit_ratio", hitRatio,
            Map.of("database", database, "type", cacheType));
        
        metricsService.recordMetric("database.cache.evictions", evictions,
            Map.of("database", database, "type", cacheType));
    }
    
    private void handleDeadlockDetection(JsonNode event, long timestamp) {
        String database = event.path("database").asText();
        String deadlockId = event.path("deadlockId").asText();
        JsonNode participants = event.path("participants");
        String victimSession = event.path("victimSession").asText();
        String resolution = event.path("resolution").asText();
        JsonNode waitGraph = event.path("waitGraph");
        
        if (!deadlockDetectionEnabled) {
            return;
        }
        
        DeadlockInfo deadlock = new DeadlockInfo(database, deadlockId, timestamp);
        
        if (participants != null && participants.isArray()) {
            participants.forEach(participant -> {
                String sessionId = participant.path("sessionId").asText();
                String query = participant.path("query").asText();
                String lockType = participant.path("lockType").asText();
                deadlock.addParticipant(sessionId, query, lockType);
            });
        }
        
        deadlock.setVictimSession(victimSession);
        deadlock.setResolution(resolution);
        
        deadlockMap.put(deadlockId, deadlock);
        deadlockCounter.increment();
        
        alertingService.sendAlert(
            "DEADLOCK_DETECTED",
            "Critical",
            String.format("Deadlock detected in %s, victim session: %s",
                database, victimSession),
            Map.of(
                "database", database,
                "deadlockId", deadlockId,
                "victim", victimSession,
                "participants", String.valueOf(deadlock.getParticipantCount()),
                "resolution", resolution
            )
        );
        
        metricsService.recordMetric("database.deadlocks", 1.0,
            Map.of("database", database));
    }
    
    private void handleSchemaChange(JsonNode event, long timestamp) {
        String database = event.path("database").asText();
        String changeType = event.path("changeType").asText();
        String objectName = event.path("objectName").asText();
        String objectType = event.path("objectType").asText();
        String changeDescription = event.path("changeDescription").asText();
        String executedBy = event.path("executedBy").asText();
        long executionTime = event.path("executionTime").asLong();
        boolean success = event.path("success").asBoolean();
        
        SchemaMetrics metrics = schemaMetricsMap.computeIfAbsent(
            database, k -> new SchemaMetrics(database)
        );
        
        metrics.recordChange(
            changeType, objectName, objectType, changeDescription,
            executedBy, executionTime, success, timestamp
        );
        
        if (!success) {
            alertingService.sendAlert(
                "SCHEMA_CHANGE_FAILED",
                "High",
                String.format("Schema change failed in %s: %s on %s",
                    database, changeType, objectName),
                Map.of(
                    "database", database,
                    "changeType", changeType,
                    "object", objectName,
                    "executedBy", executedBy
                )
            );
        }
        
        if (executionTime > 60000) {
            logger.warn("Long running schema change in {}: {}ms for {} on {}",
                database, executionTime, changeType, objectName);
        }
        
        metricsService.recordMetric("database.schema.changes", 1.0,
            Map.of("database", database, "type", changeType, "success", String.valueOf(success)));
    }
    
    private void handleBackupStatus(JsonNode event, long timestamp) {
        String database = event.path("database").asText();
        String backupType = event.path("backupType").asText();
        String status = event.path("status").asText();
        long backupSize = event.path("backupSize").asLong();
        long duration = event.path("duration").asLong();
        String backupLocation = event.path("backupLocation").asText();
        double compressionRatio = event.path("compressionRatio").asDouble();
        long lastSuccessfulBackup = event.path("lastSuccessfulBackup").asLong();
        
        BackupMetrics metrics = backupMetricsMap.computeIfAbsent(
            database, k -> new BackupMetrics(database)
        );
        
        metrics.recordBackup(
            backupType, status, backupSize, duration,
            backupLocation, compressionRatio, lastSuccessfulBackup, timestamp
        );
        
        if ("FAILED".equals(status)) {
            alertingService.sendAlert(
                "BACKUP_FAILED",
                "Critical",
                String.format("Backup failed for database %s", database),
                Map.of(
                    "database", database,
                    "backupType", backupType,
                    "status", status
                )
            );
        }
        
        long hoursSinceLastBackup = (System.currentTimeMillis() - lastSuccessfulBackup) / (1000 * 60 * 60);
        if (hoursSinceLastBackup > 24) {
            alertingService.sendAlert(
                "BACKUP_OVERDUE",
                "High",
                String.format("No successful backup for %s in %d hours",
                    database, hoursSinceLastBackup),
                Map.of(
                    "database", database,
                    "hoursSinceBackup", String.valueOf(hoursSinceLastBackup)
                )
            );
        }
        
        metricsService.recordMetric("database.backup.size", backupSize,
            Map.of("database", database, "type", backupType));
        
        metricsService.recordMetric("database.backup.duration", duration,
            Map.of("database", database, "type", backupType));
    }
    
    private void handleMaintenanceOperation(JsonNode event, long timestamp) {
        String database = event.path("database").asText();
        String operationType = event.path("operationType").asText();
        String status = event.path("status").asText();
        long duration = event.path("duration").asLong();
        String tableName = event.path("tableName").asText("");
        long rowsProcessed = event.path("rowsProcessed").asLong();
        long spaceReclaimed = event.path("spaceReclaimed").asLong();
        
        MaintenanceMetrics metrics = maintenanceMetricsMap.computeIfAbsent(
            database, k -> new MaintenanceMetrics(database)
        );
        
        metrics.recordOperation(
            operationType, status, duration, tableName,
            rowsProcessed, spaceReclaimed, timestamp
        );
        
        if ("VACUUM".equals(operationType) || "ANALYZE".equals(operationType)) {
            logger.info("{} operation completed on {}.{} in {}ms",
                operationType, database, tableName, duration);
        }
        
        if ("FAILED".equals(status)) {
            alertingService.sendAlert(
                "MAINTENANCE_OPERATION_FAILED",
                "Medium",
                String.format("Maintenance operation %s failed on %s",
                    operationType, database),
                Map.of(
                    "database", database,
                    "operation", operationType,
                    "table", tableName
                )
            );
        }
        
        metricsService.recordMetric("database.maintenance.duration", duration,
            Map.of("database", database, "operation", operationType));
    }
    
    private void handleQueryPlanAnalysis(JsonNode event, long timestamp) {
        String queryId = event.path("queryId").asText();
        String database = event.path("database").asText();
        JsonNode planNodes = event.path("planNodes");
        double totalCost = event.path("totalCost").asDouble();
        long estimatedRows = event.path("estimatedRows").asLong();
        long actualRows = event.path("actualRows").asLong();
        String mostExpensiveNode = event.path("mostExpensiveNode").asText();
        JsonNode suggestions = event.path("suggestions");
        
        QueryPlan plan = queryPlanMap.get(queryId);
        if (plan == null) {
            plan = new QueryPlan(queryId, "", "", 0);
            queryPlanMap.put(queryId, plan);
        }
        
        plan.updateAnalysis(totalCost, estimatedRows, actualRows, mostExpensiveNode);
        
        if (planNodes != null && planNodes.isArray()) {
            planNodes.forEach(node -> {
                String nodeType = node.path("type").asText();
                double nodeCost = node.path("cost").asDouble();
                plan.addPlanNode(nodeType, nodeCost);
            });
        }
        
        if (suggestions != null && suggestions.isArray()) {
            suggestions.forEach(suggestion -> 
                plan.addSuggestion(suggestion.asText())
            );
        }
        
        double estimationError = Math.abs((double)(actualRows - estimatedRows) / estimatedRows);
        if (estimationError > 0.5) {
            logger.warn("Query {} has high estimation error: {}%",
                queryId, estimationError * 100);
        }
        
        metricsService.recordMetric("database.query_plan.cost", totalCost,
            Map.of("database", database));
    }
    
    private void handleDatabaseHealth(JsonNode event, long timestamp) {
        String database = event.path("database").asText();
        String status = event.path("status").asText();
        double cpuUsage = event.path("cpuUsage").asDouble();
        double memoryUsage = event.path("memoryUsage").asDouble();
        double diskUsage = event.path("diskUsage").asDouble();
        long activeConnections = event.path("activeConnections").asLong();
        long transactionsPerSecond = event.path("transactionsPerSecond").asLong();
        double readWriteRatio = event.path("readWriteRatio").asDouble();
        JsonNode warnings = event.path("warnings");
        
        DatabaseHealth health = databaseHealthMap.computeIfAbsent(
            database, k -> new DatabaseHealth(database)
        );
        
        health.updateHealth(
            status, cpuUsage, memoryUsage, diskUsage,
            activeConnections, transactionsPerSecond, readWriteRatio, timestamp
        );
        
        if (warnings != null && warnings.isArray()) {
            warnings.forEach(warning -> 
                health.addWarning(warning.asText())
            );
        }
        
        if (!"HEALTHY".equals(status)) {
            alertingService.sendAlert(
                "DATABASE_HEALTH_ISSUE",
                "High",
                String.format("Database %s health status: %s", database, status),
                Map.of(
                    "database", database,
                    "status", status,
                    "cpu", String.valueOf(cpuUsage),
                    "memory", String.valueOf(memoryUsage),
                    "disk", String.valueOf(diskUsage)
                )
            );
        }
        
        if (cpuUsage > 0.8 || memoryUsage > 0.9 || diskUsage > 0.85) {
            alertingService.sendAlert(
                "DATABASE_RESOURCE_HIGH",
                "Medium",
                String.format("High resource usage on %s", database),
                Map.of(
                    "database", database,
                    "cpu", String.valueOf(cpuUsage),
                    "memory", String.valueOf(memoryUsage),
                    "disk", String.valueOf(diskUsage)
                )
            );
        }
        
        metricsService.recordMetric("database.health.score", health.calculateHealthScore(),
            Map.of("database", database));
    }
    
    private void handlePerformanceAnomaly(JsonNode event, long timestamp) {
        String database = event.path("database").asText();
        String anomalyType = event.path("anomalyType").asText();
        String metric = event.path("metric").asText();
        double currentValue = event.path("currentValue").asDouble();
        double expectedValue = event.path("expectedValue").asDouble();
        double deviation = event.path("deviation").asDouble();
        String severity = event.path("severity").asText();
        String possibleCause = event.path("possibleCause").asText("");
        
        PerformanceBaseline baseline = performanceBaselineMap.get(database);
        if (baseline != null) {
            baseline.recordAnomaly(anomalyType, metric, currentValue, expectedValue, deviation, timestamp);
        }
        
        alertingService.sendAlert(
            "DATABASE_PERFORMANCE_ANOMALY",
            severity,
            String.format("Performance anomaly detected in %s: %s",
                database, anomalyType),
            Map.of(
                "database", database,
                "anomalyType", anomalyType,
                "metric", metric,
                "current", String.valueOf(currentValue),
                "expected", String.valueOf(expectedValue),
                "deviation", String.valueOf(deviation),
                "possibleCause", possibleCause
            )
        );
        
        metricsService.recordMetric("database.anomaly.detected", 1.0,
            Map.of("database", database, "type", anomalyType, "metric", metric));
    }
    
    private void analyzeDatabasePerformance() {
        try {
            databaseHealthMap.forEach((database, health) -> {
                PerformanceAnalysis analysis = analyzeDatabase(database);
                
                if (analysis.hasIssues()) {
                    reportPerformanceIssues(database, analysis);
                }
                
                PerformanceBaseline baseline = performanceBaselineMap.get(database);
                if (baseline != null) {
                    baseline.updateBaseline(analysis);
                }
            });
        } catch (Exception e) {
            logger.error("Error analyzing database performance", e);
        }
    }
    
    private void checkConnectionPools() {
        try {
            connectionPoolMap.forEach((poolName, metrics) -> {
                if (metrics.hasExhaustion()) {
                    logger.warn("Connection pool {} approaching exhaustion", poolName);
                }
                
                if (metrics.hasHighWaitTime()) {
                    logger.warn("Connection pool {} has high wait times", poolName);
                }
            });
        } catch (Exception e) {
            logger.error("Error checking connection pools", e);
        }
    }
    
    private void monitorReplication() {
        try {
            replicationMetricsMap.forEach((replicationPair, metrics) -> {
                if (metrics.hasHighLag()) {
                    logger.warn("Replication pair {} has high lag: {}ms",
                        replicationPair, metrics.getCurrentLag());
                }
                
                if (!metrics.isHealthy()) {
                    logger.error("Replication pair {} is unhealthy: {}",
                        replicationPair, metrics.getState());
                }
            });
        } catch (Exception e) {
            logger.error("Error monitoring replication", e);
        }
    }
    
    private void detectSlowQueries() {
        try {
            queryMetricsMap.forEach((queryType, metrics) -> {
                List<String> slowQueries = metrics.getSlowQueries(slowQueryLogThresholdMs);
                
                if (!slowQueries.isEmpty()) {
                    logger.info("Found {} slow {} queries", slowQueries.size(), queryType);
                    
                    if (queryOptimizationEnabled) {
                        slowQueries.forEach(queryId -> {
                            QueryPlan plan = queryPlanMap.get(queryId);
                            if (plan != null) {
                                suggestQueryOptimization(queryId, plan);
                            }
                        });
                    }
                }
            });
        } catch (Exception e) {
            logger.error("Error detecting slow queries", e);
        }
    }
    
    private void analyzeIndexUsage() {
        try {
            indexMetricsMap.forEach((indexKey, metrics) -> {
                if (metrics.isUnused() && metrics.getDaysSinceUsed() > 30) {
                    logger.info("Index {} is unused for {} days",
                        indexKey, metrics.getDaysSinceUsed());
                }
                
                if (metrics.hasLowUsage()) {
                    logger.info("Index {} has low usage ratio: {}%",
                        indexKey, metrics.getUsageRatio() * 100);
                }
            });
        } catch (Exception e) {
            logger.error("Error analyzing index usage", e);
        }
    }
    
    private void collectStatistics() {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            stats.put("totalQueries", calculateTotalQueries());
            stats.put("avgQueryTime", calculateAverageQueryTime());
            stats.put("cacheHitRatio", calculateCacheHitRatio());
            stats.put("connectionPoolUsage", calculateConnectionPoolUsage());
            stats.put("replicationLag", calculateReplicationLag());
            stats.put("deadlockCount", deadlockCounter.count());
            stats.put("rollbackRate", calculateRollbackRate());
            
            logger.info("Database statistics collected: {}", stats);
            
            metricsService.recordMetric("database.statistics.collected", 1.0, Map.of());
            
        } catch (Exception e) {
            logger.error("Error collecting statistics", e);
        }
    }
    
    private void analyzeVacuumNeeds() {
        try {
            tableMetricsMap.forEach((tableKey, metrics) -> {
                if (metrics.needsVacuum()) {
                    logger.info("Table {} needs vacuum: bloat ratio {}%",
                        tableKey, metrics.getBloatRatio() * 100);
                    
                    suggestMaintenance(tableKey, "VACUUM", metrics);
                }
                
                if (metrics.needsAnalyze()) {
                    logger.info("Table {} needs analyze: {} days since last analyze",
                        tableKey, metrics.getDaysSinceAnalyzed());
                    
                    suggestMaintenance(tableKey, "ANALYZE", metrics);
                }
            });
        } catch (Exception e) {
            logger.error("Error analyzing vacuum needs", e);
        }
    }
    
    private void generatePerformanceReport() {
        try {
            Map<String, Object> report = new HashMap<>();
            
            report.put("avgQueryTime", calculateAverageQueryTime());
            report.put("slowQueryCount", slowQueryCounter.count());
            report.put("deadlockCount", deadlockCounter.count());
            report.put("rollbackRate", calculateRollbackRate());
            report.put("cacheHitRatio", calculateCacheHitRatio());
            report.put("replicationLag", calculateReplicationLag());
            report.put("topSlowQueries", getTopSlowQueries());
            report.put("unusedIndexes", getUnusedIndexes());
            report.put("timestamp", System.currentTimeMillis());
            
            logger.info("Database performance report generated: {}", report);
            
            metricsService.recordMetric("database.report.generated", 1.0, Map.of());
            
        } catch (Exception e) {
            logger.error("Error generating performance report", e);
        }
    }
    
    private void cleanupOldData() {
        try {
            long cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
            
            queryMetricsMap.values().forEach(metrics -> 
                metrics.cleanupOldData(cutoffTime)
            );
            
            deadlockMap.entrySet().removeIf(entry ->
                entry.getValue().getTimestamp() < cutoffTime
            );
            
            queryPlanMap.entrySet().removeIf(entry ->
                entry.getValue().getLastAnalyzed() < cutoffTime
            );
            
            logger.info("Cleaned up old database performance data");
            
        } catch (Exception e) {
            logger.error("Error cleaning up old data", e);
        }
    }
    
    private void suggestQueryOptimization(String queryId, QueryPlan plan) {
        List<String> suggestions = new ArrayList<>();
        
        if (!plan.usesIndex()) {
            suggestions.add("Consider adding an index");
        }
        
        if (plan.hasHighCost()) {
            suggestions.add("Query has high execution cost");
        }
        
        if (plan.hasEstimationError()) {
            suggestions.add("Update table statistics");
        }
        
        plan.getSuggestions().addAll(suggestions);
        
        if (!suggestions.isEmpty()) {
            logger.info("Optimization suggestions for query {}: {}",
                queryId, suggestions);
        }
    }
    
    private void suggestMaintenance(String tableKey, String operation, TableMetrics metrics) {
        alertingService.sendAlert(
            "MAINTENANCE_SUGGESTED",
            "Low",
            String.format("Suggest %s operation for table %s", operation, tableKey),
            Map.of(
                "table", tableKey,
                "operation", operation,
                "bloatRatio", String.valueOf(metrics.getBloatRatio()),
                "deadRows", String.valueOf(metrics.getDeadRows())
            )
        );
    }
    
    private PerformanceAnalysis analyzeDatabase(String database) {
        DatabaseHealth health = databaseHealthMap.get(database);
        if (health == null) {
            return new PerformanceAnalysis(false, "");
        }
        
        boolean hasIssues = !health.isHealthy() || health.hasHighResourceUsage();
        String issueDescription = health.getIssueDescription();
        
        return new PerformanceAnalysis(hasIssues, issueDescription);
    }
    
    private void reportPerformanceIssues(String database, PerformanceAnalysis analysis) {
        if (analysis.hasIssues()) {
            logger.warn("Performance issues detected for database {}: {}",
                database, analysis.getIssueDescription());
        }
    }
    
    private long calculateTotalQueries() {
        return queryMetricsMap.values().stream()
            .mapToLong(QueryMetrics::getTotalExecutions)
            .sum();
    }
    
    private double calculateAverageQueryTime() {
        return queryMetricsMap.values().stream()
            .mapToDouble(QueryMetrics::getAverageExecutionTime)
            .average().orElse(0.0);
    }
    
    private double calculateConnectionPoolUsage() {
        if (connectionPoolMap.isEmpty()) return 0.0;
        
        return connectionPoolMap.values().stream()
            .mapToDouble(ConnectionPoolMetrics::getUsagePercentage)
            .average().orElse(0.0);
    }
    
    private double calculateReplicationLag() {
        if (replicationMetricsMap.isEmpty()) return 0.0;
        
        return replicationMetricsMap.values().stream()
            .mapToDouble(ReplicationMetrics::getCurrentLag)
            .average().orElse(0.0);
    }
    
    private double calculateCacheHitRatio() {
        if (cacheMetricsMap.isEmpty()) return 0.0;
        
        return cacheMetricsMap.values().stream()
            .mapToDouble(CacheMetrics::getHitRatio)
            .average().orElse(0.0);
    }
    
    private double calculateRollbackRate() {
        long totalTransactions = transactionMetricsMap.values().stream()
            .mapToLong(TransactionMetrics::getTotalTransactions)
            .sum();
        
        if (totalTransactions == 0) return 0.0;
        
        long rollbacks = transactionMetricsMap.values().stream()
            .mapToLong(TransactionMetrics::getRollbackCount)
            .sum();
        
        return (double) rollbacks / totalTransactions;
    }
    
    private List<String> getTopSlowQueries() {
        return queryPlanMap.values().stream()
            .sorted((a, b) -> Long.compare(b.getExecutionTime(), a.getExecutionTime()))
            .limit(10)
            .map(QueryPlan::getQueryId)
            .collect(Collectors.toList());
    }
    
    private List<String> getUnusedIndexes() {
        return indexMetricsMap.entrySet().stream()
            .filter(e -> e.getValue().isUnused())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    private void handleProcessingError(String message, Exception e, Acknowledgment acknowledgment) {
        errorCounter.increment();
        logger.error("Error processing database performance event", e);
        
        try {
            if (retryHandler.shouldRetry(message, e)) {
                retryHandler.scheduleRetry(message, acknowledgment);
            } else {
                sendToDlq(message, e);
                acknowledgment.acknowledge();
                dlqCounter.increment();
            }
        } catch (Exception retryError) {
            logger.error("Error handling processing failure", retryError);
            acknowledgment.acknowledge();
        }
    }
    
    private void sendToDlq(String message, Exception error) {
        Map<String, Object> dlqMessage = new HashMap<>();
        dlqMessage.put("originalMessage", message);
        dlqMessage.put("error", error.getMessage());
        dlqMessage.put("timestamp", System.currentTimeMillis());
        dlqMessage.put("consumer", "DatabasePerformanceConsumer");
        
        try {
            String dlqPayload = objectMapper.writeValueAsString(dlqMessage);
            logger.info("Sending message to DLQ: {}", dlqPayload);
        } catch (Exception e) {
            logger.error("Failed to send message to DLQ", e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        try {
            logger.info("Shutting down DatabasePerformanceConsumer");
            
            scheduledExecutor.shutdown();
            executorService.shutdown();
            
            if (!scheduledExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            
            logger.info("DatabasePerformanceConsumer shutdown complete");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Shutdown interrupted", e);
        }
    }
    
    private static class QueryMetrics {
        private final String queryType;
        private final AtomicLong totalExecutions = new AtomicLong(0);
        private final List<QueryExecution> executions = new CopyOnWriteArrayList<>();
        private final AtomicLong totalTime = new AtomicLong(0);
        private final AtomicLong slowQueries = new AtomicLong(0);
        
        public QueryMetrics(String queryType) {
            this.queryType = queryType;
        }
        
        public void recordExecution(String queryId, String database, long executionTime,
                                   long rowsAffected, long rowsExamined,
                                   boolean usedIndex, long timestamp) {
            totalExecutions.incrementAndGet();
            totalTime.addAndGet(executionTime);
            
            QueryExecution execution = new QueryExecution(
                queryId, database, executionTime, rowsAffected,
                rowsExamined, usedIndex, timestamp
            );
            
            executions.add(execution);
            if (executions.size() > 10000) {
                executions.subList(0, 1000).clear();
            }
            
            if (executionTime > 5000) {
                slowQueries.incrementAndGet();
            }
        }
        
        public List<String> getSlowQueries(long threshold) {
            return executions.stream()
                .filter(e -> e.executionTime > threshold)
                .map(e -> e.queryId)
                .collect(Collectors.toList());
        }
        
        public void cleanupOldData(long cutoffTime) {
            executions.removeIf(e -> e.timestamp < cutoffTime);
        }
        
        public long getTotalExecutions() { return totalExecutions.get(); }
        
        public double getAverageExecutionTime() {
            long total = totalExecutions.get();
            if (total == 0) return 0.0;
            return (double) totalTime.get() / total;
        }
        
        private static class QueryExecution {
            final String queryId;
            final String database;
            final long executionTime;
            final long rowsAffected;
            final long rowsExamined;
            final boolean usedIndex;
            final long timestamp;
            
            QueryExecution(String queryId, String database, long executionTime,
                         long rowsAffected, long rowsExamined,
                         boolean usedIndex, long timestamp) {
                this.queryId = queryId;
                this.database = database;
                this.executionTime = executionTime;
                this.rowsAffected = rowsAffected;
                this.rowsExamined = rowsExamined;
                this.usedIndex = usedIndex;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class ConnectionPoolMetrics {
        private final String poolName;
        private final String database;
        private volatile int activeConnections = 0;
        private volatile int maxConnections = 0;
        private volatile int waitingRequests = 0;
        private volatile long avgWaitTime = 0;
        private volatile long lastUpdateTime = 0;
        
        public ConnectionPoolMetrics(String poolName, String database) {
            this.poolName = poolName;
            this.database = database;
        }
        
        public void updateStatus(int active, int idle, int max, int waiting,
                               long avgWait, int failed, long timestamp) {
            this.activeConnections = active;
            this.maxConnections = max;
            this.waitingRequests = waiting;
            this.avgWaitTime = avgWait;
            this.lastUpdateTime = timestamp;
        }
        
        public boolean hasExhaustion() {
            return activeConnections >= maxConnections * 0.95;
        }
        
        public boolean hasHighWaitTime() {
            return avgWaitTime > 1000;
        }
        
        public double getUsagePercentage() {
            if (maxConnections == 0) return 0.0;
            return (double) activeConnections / maxConnections;
        }
    }
    
    private static class TransactionMetrics {
        private final String database;
        private final AtomicLong totalTransactions = new AtomicLong(0);
        private final AtomicLong rollbackCount = new AtomicLong(0);
        private final AtomicLong commitCount = new AtomicLong(0);
        private final List<TransactionRecord> transactions = new CopyOnWriteArrayList<>();
        
        public TransactionMetrics(String database) {
            this.database = database;
        }
        
        public void recordTransaction(String transactionId, String type, long duration,
                                     String status, int statements, boolean autoCommit,
                                     String isolationLevel, long timestamp) {
            totalTransactions.incrementAndGet();
            
            if ("ROLLBACK".equals(status)) {
                rollbackCount.incrementAndGet();
            } else if ("COMMIT".equals(status)) {
                commitCount.incrementAndGet();
            }
            
            transactions.add(new TransactionRecord(
                transactionId, type, duration, status, statements,
                autoCommit, isolationLevel, timestamp
            ));
            
            if (transactions.size() > 10000) {
                transactions.subList(0, 1000).clear();
            }
        }
        
        public void incrementRollbacks() {
            rollbackCount.incrementAndGet();
        }
        
        public double getRollbackRate() {
            long total = totalTransactions.get();
            if (total == 0) return 0.0;
            return (double) rollbackCount.get() / total;
        }
        
        public long getTotalTransactions() { return totalTransactions.get(); }
        public long getRollbackCount() { return rollbackCount.get(); }
        
        private static class TransactionRecord {
            final String transactionId;
            final String type;
            final long duration;
            final String status;
            final int statements;
            final boolean autoCommit;
            final String isolationLevel;
            final long timestamp;
            
            TransactionRecord(String transactionId, String type, long duration,
                            String status, int statements, boolean autoCommit,
                            String isolationLevel, long timestamp) {
                this.transactionId = transactionId;
                this.type = type;
                this.duration = duration;
                this.status = status;
                this.statements = statements;
                this.autoCommit = autoCommit;
                this.isolationLevel = isolationLevel;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class TableMetrics {
        private final String database;
        private final String tableName;
        private volatile long rowCount = 0;
        private volatile long dataSize = 0;
        private volatile long deadRows = 0;
        private volatile double bloatRatio = 0.0;
        private volatile long lastAnalyzed = 0;
        private volatile double fragmentation = 0.0;
        
        public TableMetrics(String database, String tableName) {
            this.database = database;
            this.tableName = tableName;
        }
        
        public void updateStatistics(long rowCount, long dataSize, long indexSize,
                                    double fragmentation, long lastAnalyzed,
                                    long deadRows, double bloatRatio, long timestamp) {
            this.rowCount = rowCount;
            this.dataSize = dataSize;
            this.deadRows = deadRows;
            this.bloatRatio = bloatRatio;
            this.lastAnalyzed = lastAnalyzed;
            this.fragmentation = fragmentation;
        }
        
        public boolean needsVacuum() {
            return bloatRatio > 0.3 || deadRows > rowCount * 0.2;
        }
        
        public boolean needsAnalyze() {
            long daysSince = (System.currentTimeMillis() - lastAnalyzed) / (1000 * 60 * 60 * 24);
            return daysSince > 7;
        }
        
        public long getDaysSinceAnalyzed() {
            return (System.currentTimeMillis() - lastAnalyzed) / (1000 * 60 * 60 * 24);
        }
        
        public double getBloatRatio() { return bloatRatio; }
        public long getDeadRows() { return deadRows; }
    }
    
    private static class IndexMetrics {
        private final String database;
        private final String tableName;
        private final String indexName;
        private volatile long scansCount = 0;
        private volatile double usageRatio = 0.0;
        private volatile boolean isUnused = false;
        private volatile long lastUsedTime = System.currentTimeMillis();
        
        public IndexMetrics(String database, String tableName, String indexName) {
            this.database = database;
            this.tableName = tableName;
            this.indexName = indexName;
        }
        
        public void updateUsage(long scans, long tuplesRead, long tuplesFetched,
                               double usageRatio, long indexSize, boolean isUnique,
                               boolean isPrimary, long timestamp) {
            this.scansCount = scans;
            this.usageRatio = usageRatio;
            
            if (scans > 0) {
                lastUsedTime = timestamp;
                isUnused = false;
            }
        }
        
        public void markUnused() {
            isUnused = true;
        }
        
        public boolean isUnused() { return isUnused; }
        
        public long getDaysSinceUsed() {
            return (System.currentTimeMillis() - lastUsedTime) / (1000 * 60 * 60 * 24);
        }
        
        public boolean hasLowUsage() {
            return usageRatio < 0.1 && scansCount > 0;
        }
        
        public double getUsageRatio() { return usageRatio; }
    }
    
    private static class ReplicationMetrics {
        private final String primary;
        private final String replica;
        private volatile long currentLag = 0;
        private volatile String state = "UNKNOWN";
        private volatile boolean isHealthy = true;
        
        public ReplicationMetrics(String primary, String replica) {
            this.primary = primary;
            this.replica = replica;
        }
        
        public void updateStatus(long lag, String state, long lastSync,
                               long pending, double throughput,
                               boolean synchronous, long timestamp) {
            this.currentLag = lag;
            this.state = state;
            this.isHealthy = "STREAMING".equals(state) || "SYNCHRONOUS".equals(state);
        }
        
        public boolean hasHighLag() {
            return currentLag > 5000;
        }
        
        public long getCurrentLag() { return currentLag; }
        public String getState() { return state; }
        public boolean isHealthy() { return isHealthy; }
    }
    
    private static class LockMetrics {
        private final String database;
        private final List<LockRecord> locks = new CopyOnWriteArrayList<>();
        private final AtomicLong blockingLocks = new AtomicLong(0);
        
        public LockMetrics(String database) {
            this.database = database;
        }
        
        public void recordLock(String type, String object, String mode,
                             String holdingSession, String waitingSession,
                             long duration, boolean blocking, int waiting, long timestamp) {
            locks.add(new LockRecord(
                type, object, mode, holdingSession, waitingSession,
                duration, blocking, waiting, timestamp
            ));
            
            if (locks.size() > 1000) {
                locks.subList(0, 100).clear();
            }
            
            if (blocking) {
                blockingLocks.incrementAndGet();
            }
        }
        
        private static class LockRecord {
            final String type;
            final String object;
            final String mode;
            final String holdingSession;
            final String waitingSession;
            final long duration;
            final boolean blocking;
            final int waiting;
            final long timestamp;
            
            LockRecord(String type, String object, String mode,
                      String holdingSession, String waitingSession,
                      long duration, boolean blocking, int waiting, long timestamp) {
                this.type = type;
                this.object = object;
                this.mode = mode;
                this.holdingSession = holdingSession;
                this.waitingSession = waitingSession;
                this.duration = duration;
                this.blocking = blocking;
                this.waiting = waiting;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class CacheMetrics {
        private final String database;
        private final String cacheType;
        private volatile double hitRatio = 0.0;
        private volatile long hits = 0;
        private volatile long misses = 0;
        
        public CacheMetrics(String database, String cacheType) {
            this.database = database;
            this.cacheType = cacheType;
        }
        
        public void updatePerformance(long hits, long misses, double hitRatio,
                                     long evictions, long cacheSize,
                                     long maxSize, double fillRatio, long timestamp) {
            this.hits = hits;
            this.misses = misses;
            this.hitRatio = hitRatio;
        }
        
        public double getHitRatio() { return hitRatio; }
    }
    
    private static class DeadlockInfo {
        private final String database;
        private final String deadlockId;
        private final long timestamp;
        private final List<Participant> participants = new ArrayList<>();
        private String victimSession;
        private String resolution;
        
        public DeadlockInfo(String database, String deadlockId, long timestamp) {
            this.database = database;
            this.deadlockId = deadlockId;
            this.timestamp = timestamp;
        }
        
        public void addParticipant(String sessionId, String query, String lockType) {
            participants.add(new Participant(sessionId, query, lockType));
        }
        
        public void setVictimSession(String victim) { this.victimSession = victim; }
        public void setResolution(String resolution) { this.resolution = resolution; }
        public int getParticipantCount() { return participants.size(); }
        public long getTimestamp() { return timestamp; }
        
        private static class Participant {
            final String sessionId;
            final String query;
            final String lockType;
            
            Participant(String sessionId, String query, String lockType) {
                this.sessionId = sessionId;
                this.query = query;
                this.lockType = lockType;
            }
        }
    }
    
    private static class SchemaMetrics {
        private final String database;
        private final List<SchemaChange> changes = new CopyOnWriteArrayList<>();
        
        public SchemaMetrics(String database) {
            this.database = database;
        }
        
        public void recordChange(String type, String object, String objectType,
                                String description, String executedBy,
                                long executionTime, boolean success, long timestamp) {
            changes.add(new SchemaChange(
                type, object, objectType, description,
                executedBy, executionTime, success, timestamp
            ));
            
            if (changes.size() > 1000) {
                changes.subList(0, 100).clear();
            }
        }
        
        private static class SchemaChange {
            final String type;
            final String object;
            final String objectType;
            final String description;
            final String executedBy;
            final long executionTime;
            final boolean success;
            final long timestamp;
            
            SchemaChange(String type, String object, String objectType,
                        String description, String executedBy,
                        long executionTime, boolean success, long timestamp) {
                this.type = type;
                this.object = object;
                this.objectType = objectType;
                this.description = description;
                this.executedBy = executedBy;
                this.executionTime = executionTime;
                this.success = success;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class BackupMetrics {
        private final String database;
        private volatile long lastSuccessfulBackup = 0;
        private volatile String lastBackupType = "";
        private volatile long lastBackupSize = 0;
        
        public BackupMetrics(String database) {
            this.database = database;
        }
        
        public void recordBackup(String type, String status, long size,
                                long duration, String location, double compressionRatio,
                                long lastSuccessful, long timestamp) {
            if ("SUCCESS".equals(status)) {
                this.lastSuccessfulBackup = timestamp;
                this.lastBackupType = type;
                this.lastBackupSize = size;
            }
        }
    }
    
    private static class MaintenanceMetrics {
        private final String database;
        private final List<MaintenanceOperation> operations = new CopyOnWriteArrayList<>();
        
        public MaintenanceMetrics(String database) {
            this.database = database;
        }
        
        public void recordOperation(String type, String status, long duration,
                                   String table, long rowsProcessed,
                                   long spaceReclaimed, long timestamp) {
            operations.add(new MaintenanceOperation(
                type, status, duration, table, rowsProcessed, spaceReclaimed, timestamp
            ));
            
            if (operations.size() > 100) {
                operations.subList(0, 10).clear();
            }
        }
        
        private static class MaintenanceOperation {
            final String type;
            final String status;
            final long duration;
            final String table;
            final long rowsProcessed;
            final long spaceReclaimed;
            final long timestamp;
            
            MaintenanceOperation(String type, String status, long duration,
                               String table, long rowsProcessed,
                               long spaceReclaimed, long timestamp) {
                this.type = type;
                this.status = status;
                this.duration = duration;
                this.table = table;
                this.rowsProcessed = rowsProcessed;
                this.spaceReclaimed = spaceReclaimed;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class QueryPlan {
        private final String queryId;
        private final String queryText;
        private String executionPlan;
        private final long executionTime;
        private double totalCost = 0.0;
        private long estimatedRows = 0;
        private long actualRows = 0;
        private String mostExpensiveNode = "";
        private final Map<String, Double> planNodes = new HashMap<>();
        private final List<String> suggestions = new ArrayList<>();
        private volatile long lastAnalyzed = System.currentTimeMillis();
        
        public QueryPlan(String queryId, String queryText, String executionPlan, long executionTime) {
            this.queryId = queryId;
            this.queryText = queryText;
            this.executionPlan = executionPlan;
            this.executionTime = executionTime;
        }
        
        public void updateAnalysis(double cost, long estimated, long actual, String expensive) {
            this.totalCost = cost;
            this.estimatedRows = estimated;
            this.actualRows = actual;
            this.mostExpensiveNode = expensive;
            this.lastAnalyzed = System.currentTimeMillis();
        }
        
        public void addPlanNode(String nodeType, double cost) {
            planNodes.put(nodeType, cost);
        }
        
        public void addSuggestion(String suggestion) {
            suggestions.add(suggestion);
        }
        
        public boolean usesIndex() {
            return planNodes.containsKey("INDEX_SCAN") || 
                   planNodes.containsKey("INDEX_SEEK");
        }
        
        public boolean hasHighCost() {
            return totalCost > 1000;
        }
        
        public boolean hasEstimationError() {
            if (estimatedRows == 0) return false;
            double error = Math.abs((double)(actualRows - estimatedRows) / estimatedRows);
            return error > 0.5;
        }
        
        public String getQueryId() { return queryId; }
        public long getExecutionTime() { return executionTime; }
        public List<String> getSuggestions() { return suggestions; }
        public long getLastAnalyzed() { return lastAnalyzed; }
    }
    
    private static class DatabaseHealth {
        private final String database;
        private volatile String status = "HEALTHY";
        private volatile double cpuUsage = 0.0;
        private volatile double memoryUsage = 0.0;
        private volatile double diskUsage = 0.0;
        private final List<String> warnings = new CopyOnWriteArrayList<>();
        
        public DatabaseHealth(String database) {
            this.database = database;
        }
        
        public void updateHealth(String status, double cpu, double memory, double disk,
                                long connections, long tps, double rwRatio, long timestamp) {
            this.status = status;
            this.cpuUsage = cpu;
            this.memoryUsage = memory;
            this.diskUsage = disk;
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
            if (warnings.size() > 100) {
                warnings.remove(0);
            }
        }
        
        public boolean isHealthy() {
            return "HEALTHY".equals(status);
        }
        
        public boolean hasHighResourceUsage() {
            return cpuUsage > 0.8 || memoryUsage > 0.9 || diskUsage > 0.85;
        }
        
        public String getIssueDescription() {
            if (!isHealthy()) {
                return "Status: " + status;
            }
            if (hasHighResourceUsage()) {
                return String.format("High resources - CPU: %.1f%%, Memory: %.1f%%, Disk: %.1f%%",
                    cpuUsage * 100, memoryUsage * 100, diskUsage * 100);
            }
            return "";
        }
        
        public double calculateHealthScore() {
            double score = 1.0;
            
            if (!"HEALTHY".equals(status)) score -= 0.5;
            if (cpuUsage > 0.8) score -= 0.2;
            if (memoryUsage > 0.9) score -= 0.2;
            if (diskUsage > 0.85) score -= 0.1;
            
            return Math.max(0, score);
        }
    }
    
    private static class PerformanceBaseline {
        private final String database;
        private final Map<String, BaselineMetric> metrics = new ConcurrentHashMap<>();
        private final List<Anomaly> anomalies = new CopyOnWriteArrayList<>();
        
        public PerformanceBaseline(String database) {
            this.database = database;
        }
        
        public void recordAnomaly(String type, String metric, double current,
                                 double expected, double deviation, long timestamp) {
            anomalies.add(new Anomaly(type, metric, current, expected, deviation, timestamp));
            
            if (anomalies.size() > 1000) {
                anomalies.subList(0, 100).clear();
            }
        }
        
        public void updateBaseline(PerformanceAnalysis analysis) {
            // Update baseline metrics based on analysis
        }
        
        private static class BaselineMetric {
            final String name;
            double baseline;
            double threshold;
            
            BaselineMetric(String name, double baseline, double threshold) {
                this.name = name;
                this.baseline = baseline;
                this.threshold = threshold;
            }
        }
        
        private static class Anomaly {
            final String type;
            final String metric;
            final double current;
            final double expected;
            final double deviation;
            final long timestamp;
            
            Anomaly(String type, String metric, double current,
                   double expected, double deviation, long timestamp) {
                this.type = type;
                this.metric = metric;
                this.current = current;
                this.expected = expected;
                this.deviation = deviation;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class PerformanceAnalysis {
        private final boolean hasIssues;
        private final String issueDescription;
        
        public PerformanceAnalysis(boolean hasIssues, String issueDescription) {
            this.hasIssues = hasIssues;
            this.issueDescription = issueDescription;
        }
        
        public boolean hasIssues() { return hasIssues; }
        public String getIssueDescription() { return issueDescription; }
    }
}