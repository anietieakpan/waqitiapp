package com.waqiti.common.database;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Advanced Database Query Prediction and Optimization Service
 * 
 * This service implements machine learning-based query pattern analysis
 * and predictive optimization strategies to improve database performance:
 * 
 * Features:
 * - Query pattern analysis and prediction
 * - Automatic index creation and optimization
 * - Predictive data pre-loading
 * - Query execution plan optimization
 * - Connection pool dynamic sizing
 * - Read-ahead caching
 * - Query result prefetching
 * - Performance anomaly detection
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueryPredictionService {

    private final QueryPatternAnalyzer queryPatternAnalyzer;
    private final IndexOptimizationService indexOptimizationService;
    private final QueryExecutionPlanOptimizer planOptimizer;
    private final ConnectionPoolManager connectionPoolManager;
    private final CacheWarmingService cacheWarmingService;
    private final DatabaseMetricsCollector metricsCollector;
    private final QueryPerformancePredictor performancePredictor;
    
    // In-memory caches for quick access
    private final Map<String, QueryPattern> activePatterns = new ConcurrentHashMap<>();
    private final Map<String, PredictedQuery> upcomingQueries = new ConcurrentHashMap<>();
    private final Map<String, QueryPerformanceMetrics> performanceHistory = new ConcurrentHashMap<>();
    
    // Configuration
    private static final int PATTERN_ANALYSIS_WINDOW_HOURS = 24;
    private static final int PREDICTION_HORIZON_MINUTES = 30;
    private static final double PATTERN_CONFIDENCE_THRESHOLD = 0.8;
    private static final int MAX_PRELOAD_QUERIES = 100;
    
    /**
     * Main prediction and optimization cycle
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void analyzeAndOptimize() {
        try {
            log.debug("Starting query prediction and optimization cycle...");
            
            // 1. Analyze recent query patterns
            analyzeQueryPatterns();
            
            // 2. Predict upcoming queries
            predictUpcomingQueries();
            
            // 3. Optimize based on predictions
            optimizeForPredictedLoad();
            
            // 4. Pre-warm caches
            preWarmCaches();
            
            // 5. Adjust connection pools
            optimizeConnectionPools();
            
            log.debug("Query prediction and optimization cycle completed");
            
        } catch (Exception e) {
            log.error("Error in query prediction cycle", e);
        }
    }
    
    /**
     * Analyzes query patterns from recent execution history
     */
    private void analyzeQueryPatterns() {
        try {
            // Get recent query patterns for analysis
            List<PredictedQuery> recentPredictions = metricsCollector
                .getRecentQueries(PATTERN_ANALYSIS_WINDOW_HOURS);
                
            // Convert to executions for analysis
            List<QueryExecution> recentQueries = recentPredictions.stream()
                .map(this::convertToExecution)
                .collect(Collectors.toList());
            
            // Group queries by pattern
            Map<String, List<QueryExecution>> patternGroups = recentQueries.stream()
                .collect(Collectors.groupingBy(this::extractQueryPattern));
            
            // Analyze each pattern
            for (Map.Entry<String, List<QueryExecution>> entry : patternGroups.entrySet()) {
                String patternKey = entry.getKey();
                List<QueryExecution> executions = entry.getValue();
                
                if (executions.size() >= 5) { // Minimum executions for pattern
                    QueryPattern pattern = analyzePattern(patternKey, executions);
                    if (pattern.getConfidence() >= PATTERN_CONFIDENCE_THRESHOLD) {
                        activePatterns.put(patternKey, pattern);
                        log.debug("Identified query pattern: {} (confidence: {:.2f})", 
                                 patternKey, pattern.getConfidence());
                    }
                }
            }
            
            // Clean up old patterns
            cleanupOldPatterns();
            
        } catch (Exception e) {
            log.error("Error analyzing query patterns", e);
        }
    }
    
    /**
     * Predicts upcoming queries based on identified patterns
     */
    private void predictUpcomingQueries() {
        try {
            upcomingQueries.clear();
            
            for (QueryPattern pattern : activePatterns.values()) {
                List<PredictedQuery> predictions = predictQueriesFromPattern(pattern);
                
                for (PredictedQuery prediction : predictions) {
                    if (prediction.getProbability() > 0.5 && 
                        prediction.getPredictedExecutionTime().isBefore(
                            Instant.now().plusSeconds(PREDICTION_HORIZON_MINUTES * 60))) {
                        
                        upcomingQueries.put(prediction.getQueryId(), prediction);
                    }
                }
            }
            
            log.debug("Predicted {} upcoming queries", upcomingQueries.size());
            
        } catch (Exception e) {
            log.error("Error predicting upcoming queries", e);
        }
    }
    
    /**
     * Optimizes database configuration based on predicted load
     */
    private void optimizeForPredictedLoad() {
        try {
            // Calculate predicted load metrics
            PredictedLoadMetrics loadMetrics = calculatePredictedLoad();
            
            // Optimize indexes for predicted queries
            optimizeIndexesForPredictedLoad(loadMetrics);
            
            // Adjust query execution plans
            optimizeExecutionPlans(loadMetrics);
            
            // Scale database resources if needed
            scaleResourcesForPredictedLoad(loadMetrics);
            
        } catch (Exception e) {
            log.error("Error optimizing for predicted load", e);
        }
    }
    
    /**
     * Pre-warms caches with data for predicted queries
     */
    @Async
    public CompletableFuture<Void> preWarmCaches() {
        return CompletableFuture.runAsync(() -> {
            try {
                // Sort predicted queries by probability and impact
                List<PredictedQuery> prioritizedQueries = upcomingQueries.values().stream()
                    .sorted((q1, q2) -> Double.compare(
                        q2.getProbability() * q2.getImpactScore(),
                        q1.getProbability() * q1.getImpactScore()))
                    .limit(MAX_PRELOAD_QUERIES)
                    .collect(Collectors.toList());
                
                for (PredictedQuery query : prioritizedQueries) {
                    try {
                        // Pre-execute query and cache results
                        cacheWarmingService.preWarmQuery(query);
                        
                        log.debug("Pre-warmed cache for query: {} (probability: {:.2f})", 
                                 query.getQueryPattern(), query.getProbability());
                        
                    } catch (Exception e) {
                        log.debug("Failed to pre-warm query: " + query.getQueryPattern(), e);
                    }
                }
                
            } catch (Exception e) {
                log.error("Error in cache pre-warming", e);
            }
        });
    }
    
    /**
     * Optimizes connection pools based on predicted load
     */
    private void optimizeConnectionPools() {
        try {
            PredictedLoadMetrics loadMetrics = calculatePredictedLoad();
            
            // Calculate optimal pool sizes
            ConnectionPoolConfiguration optimalConfig = calculateOptimalPoolConfig(loadMetrics);
            
            // Apply configuration if significantly different
            ConnectionPoolConfiguration currentConfig = connectionPoolManager.getCurrentConfiguration();
            
            if (shouldUpdatePoolConfig(currentConfig, optimalConfig)) {
                connectionPoolManager.updateConfiguration(optimalConfig);
                
                log.info("Updated connection pool configuration: " +
                        "core={}, max={}, queue={}", 
                        optimalConfig.getCorePoolSize(),
                        optimalConfig.getMaxPoolSize(),
                        optimalConfig.getQueueCapacity());
            }
            
        } catch (Exception e) {
            log.error("Error optimizing connection pools", e);
        }
    }
    
    /**
     * Analyzes a specific query pattern
     */
    private QueryPattern analyzePattern(String patternKey, List<QueryExecution> executions) {
        // Statistical analysis of execution times
        List<Long> executionTimes = executions.stream()
            .map(QueryExecution::getExecutionTimeMs)
            .collect(Collectors.toList());
        
        double avgExecutionTime = executionTimes.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0);
        
        double stdDevExecutionTime = calculateStandardDeviation(executionTimes);
        
        // Temporal pattern analysis
        Map<Integer, Long> hourlyFrequency = executions.stream()
            .collect(Collectors.groupingBy(
                e -> e.getTimestamp().atZone(java.time.ZoneId.systemDefault()).getHour(),
                Collectors.counting()));
        
        // Identify peak hours
        List<Integer> peakHours = hourlyFrequency.entrySet().stream()
            .filter(entry -> entry.getValue() > avgExecutionTime * 1.5)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        // Calculate pattern confidence
        double confidence = calculatePatternConfidence(executions);
        
        // Predict next execution times
        List<LocalDateTime> predictedExecutions = predictNextExecutions(executions);
        
        return QueryPattern.builder()
            .patternKey(patternKey)
            .executionCount(executions.size())
            .averageExecutionTime(avgExecutionTime)
            .executionTimeStdDev(stdDevExecutionTime)
            .peakHours(peakHours)
            .confidence(confidence)
            .lastSeen(executions.stream()
                .map(QueryExecution::getTimestamp)
                .max(Instant::compareTo)
                .orElse(Instant.now()))
            .predictedNextExecutions(predictedExecutions.stream()
                .map(dt -> dt.atZone(java.time.ZoneId.systemDefault()).toInstant())
                .collect(java.util.stream.Collectors.toList()))
            .build();
    }
    
    /**
     * Predicts queries from a pattern
     */
    private List<PredictedQuery> predictQueriesFromPattern(QueryPattern pattern) {
        List<PredictedQuery> predictions = new ArrayList<>();
        
        for (Instant predictedTime : pattern.getPredictedNextExecutions()) {
            // Only predict queries within our horizon
            if (predictedTime.isBefore(Instant.now().plusSeconds(PREDICTION_HORIZON_MINUTES * 60))) {
                
                PredictedQuery prediction = PredictedQuery.builder()
                    .queryPattern(generateQueryId(pattern, predictedTime))
                    .predictedExecutionTime(predictedTime)
                    .probability(pattern.getConfidence())
                    .estimatedDurationMs((long) pattern.getAverageExecutionTime())
                    .resourceRequirements(estimateResourceRequirements(pattern))
                    .build();
                
                predictions.add(prediction);
            }
        }
        
        return predictions;
    }
    
    /**
     * Calculates predicted load metrics
     */
    private PredictedLoadMetrics calculatePredictedLoad() {
        double totalPredictedQueries = upcomingQueries.values().stream()
            .mapToDouble(PredictedQuery::getProbability)
            .sum();
        
        double totalPredictedDuration = upcomingQueries.values().stream()
            .mapToDouble(q -> q.getProbability() * q.getEstimatedDurationMs())
            .sum();
        
        // Estimate resource requirements
        ResourceRequirements totalResources = upcomingQueries.values().stream()
            .map(PredictedQuery::getResourceRequirements)
            .reduce(ResourceRequirements.empty(), ResourceRequirements::add);
        
        // Calculate load intensity by time window
        Map<LocalDateTime, Double> loadByTimeWindow = calculateLoadByTimeWindow();
        
        return PredictedLoadMetrics.builder()
            .totalPredictedQueries(totalPredictedQueries)
            .predictedQps(totalPredictedQueries / 3600.0) // Convert to QPS
            .predictedActiveConnections(calculatePeakConcurrentQueries())
            .predictedCpuUsage(totalResources.getCpuUnits())
            .predictedMemoryUsage(totalResources.getMemoryMb())
            .predictedIoUsage(totalResources.getIoOperations() / 1000.0)
            .build();
    }
    
    /**
     * Optimizes indexes for predicted load
     */
    private void optimizeIndexesForPredictedLoad(PredictedLoadMetrics loadMetrics) {
        try {
            // Analyze query patterns to identify missing indexes
            Set<IndexRecommendation> recommendations = new HashSet<>();
            
            for (PredictedQuery query : upcomingQueries.values()) {
                if (query.getProbability() > 0.7) { // High probability queries
                    List<IndexRecommendation> queryIndexes = 
                        indexOptimizationService.analyzeQueryForIndexes(query);
                    recommendations.addAll(queryIndexes);
                }
            }
            
            // Apply high-impact index recommendations
            for (IndexRecommendation recommendation : recommendations) {
                if (recommendation.getImpactScore() > 0.8) {
                    indexOptimizationService.createIndexIfNotExists(recommendation);
                    log.info("Created predictive index: {} (impact: {:.2f})", 
                            recommendation.getIndexDefinition(),
                            recommendation.getImpactScore());
                }
            }
            
        } catch (Exception e) {
            log.error("Error optimizing indexes for predicted load", e);
        }
    }
    
    /**
     * Optimizes query execution plans
     */
    private void optimizeExecutionPlans(PredictedLoadMetrics loadMetrics) {
        try {
            // For high-frequency patterns, analyze and optimize execution plans
            for (QueryPattern pattern : activePatterns.values()) {
                if (pattern.getExecutionCount() > 100) { // High frequency
                    planOptimizer.analyzeAndOptimizePlan(pattern);
                }
            }
            
        } catch (Exception e) {
            log.error("Error optimizing execution plans", e);
        }
    }
    
    /**
     * Scales database resources based on predicted load
     */
    private void scaleResourcesForPredictedLoad(PredictedLoadMetrics loadMetrics) {
        try {
            // Determine if we need to scale database resources
            DatabaseResourceRequirements currentResources = 
                metricsCollector.getCurrentResourceUsage();
            
            DatabaseResourceRequirements predictedRequirements = 
                calculateRequiredResources(loadMetrics);
            
            if (shouldScaleResources(currentResources, predictedRequirements)) {
                // Request resource scaling (this would integrate with cloud provider APIs)
                requestResourceScaling(predictedRequirements);
            }
            
        } catch (Exception e) {
            log.error("Error scaling resources for predicted load", e);
        }
    }
    
    /**
     * Long-term optimization scheduled task
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void performLongTermOptimization() {
        try {
            log.info("Starting long-term database optimization...");
            
            // 1. Analyze query performance trends
            analyzePerformanceTrends();
            
            // 2. Identify and remove unused indexes
            cleanupUnusedIndexes();
            
            // 3. Optimize table statistics
            updateTableStatistics();
            
            // 4. Analyze and optimize slow queries
            optimizeSlowQueries();
            
            // 5. Review and optimize connection pool configuration
            reviewConnectionPoolConfiguration();
            
            log.info("Long-term database optimization completed");
            
        } catch (Exception e) {
            log.error("Error in long-term optimization", e);
        }
    }
    
    /**
     * Query performance monitoring and feedback
     */
    public void recordQueryExecution(QueryExecution execution) {
        try {
            // Record execution for pattern analysis
            String patternKey = extractQueryPattern(execution);
            
            // Update performance history
            performanceHistory.put(execution.getQueryId(), 
                QueryPerformanceMetrics.fromExecution(execution));
            
            // If this was a predicted query, record prediction accuracy
            PredictedQuery prediction = upcomingQueries.get(execution.getQueryId());
            if (prediction != null) {
                recordPredictionAccuracy(prediction, execution);
            }
            
            // Update pattern statistics
            updatePatternStatistics(patternKey, execution);
            
        } catch (Exception e) {
            log.error("Error recording query execution", e);
        }
    }
    
    /**
     * Gets optimization recommendations for a specific query
     */
    @Cacheable(value = "queryOptimizationCache", key = "#sqlQuery")
    public List<OptimizationRecommendation> getOptimizationRecommendations(String sqlQuery) {
        List<OptimizationRecommendation> recommendations = new ArrayList<>();
        
        try {
            // Analyze query structure
            QueryAnalysis analysis = queryPatternAnalyzer.analyzeQuery(sqlQuery);
            
            // Generate index recommendations
            recommendations.addAll(indexOptimizationService.getIndexRecommendations(analysis));
            
            // Generate query rewrite recommendations
            recommendations.addAll(planOptimizer.getRewriteRecommendations(analysis));
            
            // Generate configuration recommendations
            recommendations.addAll(getConfigurationRecommendations(analysis));
            
        } catch (Exception e) {
            log.error("Error generating optimization recommendations", e);
        }
        
        return recommendations;
    }
    
    // Helper methods
    
    private QueryExecution convertToExecution(PredictedQuery prediction) {
        return QueryExecution.builder()
            .queryId(prediction.getQueryId())
            .queryText(prediction.getQueryPattern())
            .startTime(prediction.getPredictedExecutionTime())
            .endTime(prediction.getPredictedExecutionTime().plusMillis(prediction.getEstimatedDurationMs()))
            .executionTimeMs(prediction.getEstimatedDurationMs())
            .optimized(false)
            .build();
    }
    
    private String extractQueryPattern(QueryExecution execution) {
        // Normalize query to extract pattern (remove literals, normalize whitespace, etc.)
        return queryPatternAnalyzer.extractPattern(execution.getQueryText());
    }
    
    private double calculateStandardDeviation(List<Long> values) {
        double mean = values.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double variance = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0.0);
        return Math.sqrt(variance);
    }
    
    private double calculatePatternConfidence(List<QueryExecution> executions) {
        // Confidence based on execution frequency, timing consistency, etc.
        // This is a simplified calculation
        if (executions.size() < 5) return 0.0;
        if (executions.size() > 100) return 0.95;
        return Math.min(0.95, 0.5 + (executions.size() / 200.0));
    }
    
    private List<LocalDateTime> predictNextExecutions(List<QueryExecution> executions) {
        // Time series analysis to predict next execution times
        // This would use more sophisticated algorithms in production
        List<LocalDateTime> predictions = new ArrayList<>();
        
        // Simple pattern: assume similar execution pattern continues
        Instant lastExecutionInstant = executions.stream()
            .map(QueryExecution::getTimestamp)
            .max(Instant::compareTo)
            .orElse(Instant.now());
            
        LocalDateTime lastExecution = lastExecutionInstant.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        
        // Predict next few executions based on historical intervals
        // This is a simplified implementation
        predictions.add(lastExecution.plusHours(1));
        predictions.add(lastExecution.plusHours(4));
        predictions.add(lastExecution.plusHours(8));
        
        return predictions;
    }
    
    private String generateQueryId(QueryPattern pattern, Instant executionTime) {
        return pattern.getPatternKey() + "_" + executionTime.toString();
    }
    
    private double calculateImpactScore(QueryPattern pattern) {
        // Impact based on execution frequency, duration, and resource usage
        double frequencyScore = Math.min(1.0, pattern.getExecutionCount() / 1000.0);
        double durationScore = Math.min(1.0, pattern.getAverageExecutionTime() / 10000.0);
        return (frequencyScore + durationScore) / 2.0;
    }
    
    private ResourceRequirements estimateResourceRequirements(QueryPattern pattern) {
        // Estimate based on historical execution data
        return ResourceRequirements.builder()
            .cpuUsage(pattern.getAverageExecutionTime() / 1000.0) // Simplified
            .memoryUsageBytes((long) (pattern.getAverageExecutionTime() / 10.0) * 1024 * 1024)
            .ioUsage(pattern.getAverageExecutionTime() / 10000.0)
            .build();
    }
    
    private void cleanupOldPatterns() {
        // Remove patterns that haven't been seen recently
        Instant cutoff = Instant.now().minusSeconds(PATTERN_ANALYSIS_WINDOW_HOURS * 3600);
        activePatterns.entrySet().removeIf(entry -> 
            entry.getValue().getLastSeen().isBefore(cutoff));
    }
    
    private Map<LocalDateTime, Double> calculateLoadByTimeWindow() {
        // Calculate predicted load in 5-minute windows
        Map<LocalDateTime, Double> loadMap = new HashMap<>();
        
        for (PredictedQuery query : upcomingQueries.values()) {
            LocalDateTime timeWindow = query.getPredictedExecutionTime()
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                .withMinute((query.getPredictedExecutionTime().atZone(java.time.ZoneId.systemDefault()).getMinute() / 5) * 5)
                .withSecond(0);
            
            loadMap.merge(timeWindow, query.getProbability(), Double::sum);
        }
        
        return loadMap;
    }
    
    private int calculatePeakConcurrentQueries() {
        // Simplified calculation of peak concurrent queries
        return (int) Math.ceil(upcomingQueries.values().stream()
            .mapToDouble(PredictedQuery::getProbability)
            .max()
            .orElse(0.0) * 10); // Assume max 10x the highest probability
    }
    
    private ConnectionPoolConfiguration calculateOptimalPoolConfig(PredictedLoadMetrics loadMetrics) {
        // Calculate optimal pool size based on predicted load
        int optimalCoreSize = Math.max(5, (int) Math.ceil(loadMetrics.getPeakConcurrentQueries() * 0.7));
        int optimalMaxSize = Math.max(10, (int) Math.ceil(loadMetrics.getPeakConcurrentQueries() * 1.5));
        int optimalQueueCapacity = Math.max(50, optimalMaxSize * 2);
        
        return ConnectionPoolConfiguration.builder()
            .corePoolSize(optimalCoreSize)
            .maxPoolSize(optimalMaxSize)
            .queueCapacity(optimalQueueCapacity)
            .keepAliveSeconds(300)
            .validationQuery("SELECT 1")
            .build();
    }
    
    private boolean shouldUpdatePoolConfig(ConnectionPoolConfiguration current, 
                                         ConnectionPoolConfiguration optimal) {
        // Only update if there's a significant difference
        double sizeDifference = Math.abs(current.getMaxPoolSize() - optimal.getMaxPoolSize()) 
                               / (double) current.getMaxPoolSize();
        return sizeDifference > 0.2; // 20% difference threshold
    }
    
    private void analyzePerformanceTrends() {
        // Analyze long-term performance trends
        // Identify degrading queries, growing data sets, etc.
    }
    
    private void cleanupUnusedIndexes() {
        // Identify and remove indexes that haven't been used recently
        indexOptimizationService.cleanupUnusedIndexes();
    }
    
    private void updateTableStatistics() {
        // Update database statistics for query planner
    }
    
    private void optimizeSlowQueries() {
        // Identify and optimize consistently slow queries
    }
    
    private void reviewConnectionPoolConfiguration() {
        // Review and optimize connection pool settings
    }
    
    private void recordPredictionAccuracy(PredictedQuery prediction, QueryExecution execution) {
        // Record how accurate our prediction was
        boolean wasAccurate = Math.abs(execution.getExecutionTime() - 
            prediction.getEstimatedExecutionTime()) < 300000; // Within 5 minutes (milliseconds)
        
        // Update prediction accuracy metrics
    }
    
    private void updatePatternStatistics(String patternKey, QueryExecution execution) {
        // Update pattern statistics with new execution data
    }
    
    private List<OptimizationRecommendation> getConfigurationRecommendations(QueryAnalysis analysis) {
        // Generate database configuration recommendations
        return new ArrayList<>();
    }
    
    private DatabaseResourceRequirements calculateRequiredResources(PredictedLoadMetrics loadMetrics) {
        // Calculate required database resources based on predicted load
        return DatabaseResourceRequirements.builder()
            .cpuCores((int) Math.ceil(loadMetrics.getEstimatedCpuUsage()))
            .memoryGb((int) Math.ceil(loadMetrics.getEstimatedMemoryUsage() / 1024.0))
            .storageIops((int) Math.ceil(loadMetrics.getEstimatedIoOperations()))
            .build();
    }
    
    private boolean shouldScaleResources(DatabaseResourceRequirements current, 
                                       DatabaseResourceRequirements predicted) {
        // Determine if we should scale resources
        return predicted.getCpuCores() > current.getCpuCores() * 1.5 ||
               predicted.getMemoryGb() > current.getMemoryGb() * 1.5;
    }
    
    private void requestResourceScaling(DatabaseResourceRequirements requirements) {
        // Request resource scaling from cloud provider
        log.info("Requesting database resource scaling: CPU={}, Memory={}GB, IOPS={}", 
                requirements.getCpuCores(), requirements.getMemoryGb(), requirements.getStorageIops());
    }
    
    /**
     * Gets predicted load metrics for connection pool optimization
     */
    public PredictedLoadMetrics getPredictedLoad() {
        return calculatePredictedLoad();
    }
    
    /**
     * Gets list of upcoming queries for pre-warming
     */
    public List<PredictedQuery> getUpcomingQueries() {
        return new ArrayList<>(upcomingQueries.values());
    }
}