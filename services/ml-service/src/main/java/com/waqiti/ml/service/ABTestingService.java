package com.waqiti.ml.service;

import com.waqiti.ml.entity.ModelPerformanceMetrics;
import com.waqiti.ml.repository.ModelPerformanceMetricsRepository;
import com.waqiti.common.exception.MLProcessingException;
import com.waqiti.common.tracing.Traced;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Production-ready A/B Testing Service for ML models.
 * Provides statistical A/B testing, traffic splitting, and model comparison capabilities.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ABTestingService {

    private final ModelPerformanceMetricsRepository metricsRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ModelPerformanceMonitoringService monitoringService;

    @Value("${ml.ab.testing.enabled:true}")
    private boolean abTestingEnabled;

    @Value("${ml.ab.testing.min.confidence:0.95}")
    private double minConfidence;

    @Value("${ml.ab.testing.min.sample.size:1000}")
    private int minSampleSize;

    @Value("${ml.ab.testing.max.duration.days:14}")
    private int maxTestDurationDays;

    @Value("${ml.ab.testing.early.stopping:true}")
    private boolean earlyStoppingEnabled;

    @Value("${ml.ab.testing.significance.level:0.05}")
    private double significanceLevel;

    private static final String TEST_CACHE_PREFIX = "ml:ab:test:";
    private static final String EXPERIMENT_CACHE_PREFIX = "ml:ab:experiment:";
    private static final String ALLOCATION_CACHE_PREFIX = "ml:ab:allocation:";

    // Active experiments
    private final Map<String, ABTestExperiment> activeExperiments = new ConcurrentHashMap<>();
    
    // Traffic allocation cache
    private final Map<String, TrafficAllocation> trafficAllocations = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        if (abTestingEnabled) {
            log.info("A/B Testing Service initialized");
            log.info("Min confidence: {}, Min sample size: {}", minConfidence, minSampleSize);
            loadActiveExperiments();
        } else {
            log.info("A/B Testing Service disabled");
        }
    }

    /**
     * Create a new A/B test experiment
     */
    @Traced(operation = "create_ab_test")
    @Transactional
    public ABTestExperiment createABTest(CreateABTestRequest request) {
        if (!abTestingEnabled) {
            throw new IllegalStateException("A/B testing is not enabled");
        }
        
        try {
            log.info("Creating A/B test: {} with models: {}", 
                request.getExperimentName(), request.getModelVariants());
            
            // Validate request
            validateABTestRequest(request);
            
            // Generate experiment ID
            String experimentId = UUID.randomUUID().toString();
            
            // Create experiment
            ABTestExperiment experiment = ABTestExperiment.builder()
                .experimentId(experimentId)
                .experimentName(request.getExperimentName())
                .description(request.getDescription())
                .modelVariants(request.getModelVariants())
                .trafficAllocation(request.getTrafficAllocation())
                .primaryMetric(request.getPrimaryMetric())
                .secondaryMetrics(request.getSecondaryMetrics())
                .targetAudience(request.getTargetAudience())
                .startDate(LocalDateTime.now())
                .endDate(request.getEndDate() != null ? request.getEndDate() : 
                    LocalDateTime.now().plusDays(maxTestDurationDays))
                .status(ExperimentStatus.RUNNING)
                .createdBy(request.getCreatedBy())
                .minSampleSize(Math.max(request.getMinSampleSize(), minSampleSize))
                .confidenceLevel(request.getConfidenceLevel() != null ? 
                    request.getConfidenceLevel() : minConfidence)
                .build();
            
            // Initialize variant statistics
            Map<String, VariantStatistics> variantStats = new HashMap<>();
            for (ModelVariant variant : request.getModelVariants()) {
                variantStats.put(variant.getVariantId(), 
                    VariantStatistics.builder()
                        .variantId(variant.getVariantId())
                        .sampleCount(0L)
                        .conversionCount(0L)
                        .conversionRate(0.0)
                        .totalLatency(0L)
                        .averageLatency(0.0)
                        .errorCount(0L)
                        .errorRate(0.0)
                        .build());
            }
            experiment.setVariantStatistics(variantStats);
            
            // Store experiment
            activeExperiments.put(experimentId, experiment);
            cacheExperiment(experiment);
            
            // Setup traffic allocation
            setupTrafficAllocation(experiment);
            
            // Publish event
            publishExperimentEvent(experiment, "EXPERIMENT_STARTED");
            
            log.info("Created A/B test experiment: {} with ID: {}", 
                request.getExperimentName(), experimentId);
            
            return experiment;
            
        } catch (Exception e) {
            log.error("Error creating A/B test: {}", e.getMessage());
            throw new MLProcessingException("Failed to create A/B test", e);
        }
    }

    /**
     * Get model variant for a user/request
     */
    @Traced(operation = "get_model_variant")
    public ModelVariant getModelVariant(String experimentId, String userId, 
                                       Map<String, Object> context) {
        if (!abTestingEnabled) {
            return null;
        }
        
        try {
            ABTestExperiment experiment = getExperiment(experimentId);
            if (experiment == null || experiment.getStatus() != ExperimentStatus.RUNNING) {
                return null;
            }
            
            // Check if user is in target audience
            if (!isUserInTargetAudience(userId, context, experiment.getTargetAudience())) {
                return null;
            }
            
            // Get assigned variant
            String variantId = getAssignedVariant(experimentId, userId, experiment);
            
            ModelVariant variant = experiment.getModelVariants().stream()
                .filter(v -> v.getVariantId().equals(variantId))
                .findFirst()
                .orElse(null);
            
            if (variant != null) {
                log.debug("Assigned user {} to variant {} in experiment {}", 
                    userId, variantId, experimentId);
                
                // Record assignment
                recordVariantAssignment(experimentId, variantId, userId);
            }
            
            return variant;
            
        } catch (Exception e) {
            log.error("Error getting model variant: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Record experiment result
     */
    @Traced(operation = "record_experiment_result")
    public void recordExperimentResult(String experimentId, String userId, 
                                     ExperimentResult result) {
        if (!abTestingEnabled) return;
        
        try {
            ABTestExperiment experiment = getExperiment(experimentId);
            if (experiment == null || experiment.getStatus() != ExperimentStatus.RUNNING) {
                return;
            }
            
            // Get user's variant
            String variantId = getAssignedVariant(experimentId, userId, experiment);
            if (variantId == null) {
                return;
            }
            
            // Update variant statistics
            updateVariantStatistics(experimentId, variantId, result);
            
            // Check if we should evaluate the experiment
            if (shouldEvaluateExperiment(experiment)) {
                evaluateExperiment(experimentId);
            }
            
            log.debug("Recorded result for experiment {} variant {} user {}", 
                experimentId, variantId, userId);
            
        } catch (Exception e) {
            log.error("Error recording experiment result: {}", e.getMessage());
        }
    }

    /**
     * Get experiment results
     */
    @Traced(operation = "get_experiment_results")
    public ExperimentAnalysis getExperimentResults(String experimentId) {
        try {
            ABTestExperiment experiment = getExperiment(experimentId);
            if (experiment == null) {
                return null;
            }
            
            // Calculate statistical significance
            StatisticalTestResult testResult = calculateStatisticalSignificance(experiment);
            
            // Determine winner
            String winnerVariant = determineWinner(experiment, testResult);
            
            // Calculate lift and confidence intervals
            Map<String, VariantAnalysis> variantAnalyses = calculateVariantAnalyses(experiment);
            
            return ExperimentAnalysis.builder()
                .experimentId(experimentId)
                .experimentName(experiment.getExperimentName())
                .status(experiment.getStatus())
                .startDate(experiment.getStartDate())
                .endDate(experiment.getEndDate())
                .duration(ChronoUnit.DAYS.between(experiment.getStartDate(), 
                    experiment.getEndDate() != null ? experiment.getEndDate() : LocalDateTime.now()))
                .totalSampleSize(getTotalSampleSize(experiment))
                .statisticalSignificance(testResult.isSignificant())
                .pValue(testResult.getPValue())
                .confidence(testResult.getConfidence())
                .winnerVariant(winnerVariant)
                .variantAnalyses(variantAnalyses)
                .recommendations(generateRecommendations(experiment, testResult))
                .build();
                
        } catch (Exception e) {
            log.error("Error getting experiment results: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Stop an experiment
     */
    @Transactional
    public void stopExperiment(String experimentId, String reason) {
        try {
            ABTestExperiment experiment = getExperiment(experimentId);
            if (experiment == null) {
                return;
            }
            
            experiment.setStatus(ExperimentStatus.STOPPED);
            experiment.setEndDate(LocalDateTime.now());
            experiment.setStopReason(reason);
            
            // Update cache
            cacheExperiment(experiment);
            
            // Remove from active experiments
            activeExperiments.remove(experimentId);
            
            // Clean up traffic allocation
            trafficAllocations.remove(experimentId);
            
            // Publish event
            publishExperimentEvent(experiment, "EXPERIMENT_STOPPED");
            
            log.info("Stopped experiment: {} - Reason: {}", experimentId, reason);
            
        } catch (Exception e) {
            log.error("Error stopping experiment: {}", e.getMessage());
        }
    }

    /**
     * Scheduled task to evaluate running experiments
     */
    @Scheduled(fixedDelayString = "${ml.ab.testing.evaluation.interval.ms:3600000}") // 1 hour
    public void evaluateExperiments() {
        if (!abTestingEnabled) return;
        
        try {
            log.debug("Evaluating {} active experiments", activeExperiments.size());
            
            for (String experimentId : new ArrayList<>(activeExperiments.keySet())) {
                evaluateExperiment(experimentId);
            }
            
        } catch (Exception e) {
            log.error("Error evaluating experiments: {}", e.getMessage());
        }
    }

    // Private helper methods

    private void loadActiveExperiments() {
        try {
            Set<String> experimentIds = redisTemplate.keys(EXPERIMENT_CACHE_PREFIX + "*")
                .stream()
                .map(key -> key.toString().substring(EXPERIMENT_CACHE_PREFIX.length()))
                .collect(Collectors.toSet());
            
            for (String experimentId : experimentIds) {
                ABTestExperiment experiment = (ABTestExperiment) 
                    redisTemplate.opsForValue().get(EXPERIMENT_CACHE_PREFIX + experimentId);
                
                if (experiment != null && experiment.getStatus() == ExperimentStatus.RUNNING) {
                    activeExperiments.put(experimentId, experiment);
                    setupTrafficAllocation(experiment);
                }
            }
            
            log.info("Loaded {} active experiments", activeExperiments.size());
            
        } catch (Exception e) {
            log.error("Error loading active experiments: {}", e.getMessage());
        }
    }

    private void validateABTestRequest(CreateABTestRequest request) {
        if (request.getExperimentName() == null || request.getExperimentName().trim().isEmpty()) {
            throw new IllegalArgumentException("Experiment name is required");
        }
        
        if (request.getModelVariants() == null || request.getModelVariants().size() < 2) {
            throw new IllegalArgumentException("At least 2 model variants are required");
        }
        
        if (request.getTrafficAllocation() == null || request.getTrafficAllocation().isEmpty()) {
            throw new IllegalArgumentException("Traffic allocation is required");
        }
        
        // Validate traffic allocation sums to 100%
        double totalAllocation = request.getTrafficAllocation().values().stream()
            .mapToDouble(Double::doubleValue)
            .sum();
        
        if (Math.abs(totalAllocation - 1.0) > 0.001) {
            throw new IllegalArgumentException("Traffic allocation must sum to 100%");
        }
        
        if (request.getPrimaryMetric() == null || request.getPrimaryMetric().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary metric is required");
        }
    }

    private ABTestExperiment getExperiment(String experimentId) {
        ABTestExperiment experiment = activeExperiments.get(experimentId);
        if (experiment != null) {
            return experiment;
        }
        
        // Try to load from cache
        experiment = (ABTestExperiment) redisTemplate.opsForValue()
            .get(EXPERIMENT_CACHE_PREFIX + experimentId);
        
        if (experiment != null && experiment.getStatus() == ExperimentStatus.RUNNING) {
            activeExperiments.put(experimentId, experiment);
        }
        
        return experiment;
    }

    private void cacheExperiment(ABTestExperiment experiment) {
        try {
            String key = EXPERIMENT_CACHE_PREFIX + experiment.getExperimentId();
            redisTemplate.opsForValue().set(key, experiment, maxTestDurationDays + 1, TimeUnit.DAYS);
            
        } catch (Exception e) {
            log.error("Error caching experiment: {}", e.getMessage());
        }
    }

    private void setupTrafficAllocation(ABTestExperiment experiment) {
        TrafficAllocation allocation = new TrafficAllocation();
        
        double cumulativeProbability = 0.0;
        for (Map.Entry<String, Double> entry : experiment.getTrafficAllocation().entrySet()) {
            cumulativeProbability += entry.getValue();
            allocation.addVariant(entry.getKey(), cumulativeProbability);
        }
        
        trafficAllocations.put(experiment.getExperimentId(), allocation);
    }

    private boolean isUserInTargetAudience(String userId, Map<String, Object> context, 
                                         Map<String, Object> targetAudience) {
        if (targetAudience == null || targetAudience.isEmpty()) {
            return true; // No targeting criteria means all users
        }
        
        // Implement targeting logic based on user attributes and context
        // This is a simplified version - real implementation would be more sophisticated
        
        for (Map.Entry<String, Object> criterion : targetAudience.entrySet()) {
            String key = criterion.getKey();
            Object expectedValue = criterion.getValue();
            Object actualValue = context.get(key);
            
            if (!Objects.equals(expectedValue, actualValue)) {
                return false;
            }
        }
        
        return true;
    }

    private String getAssignedVariant(String experimentId, String userId, ABTestExperiment experiment) {
        // Check if user already has an assignment
        String assignmentKey = ALLOCATION_CACHE_PREFIX + experimentId + ":" + userId;
        String cached = (String) redisTemplate.opsForValue().get(assignmentKey);
        if (cached != null) {
            return cached;
        }
        
        // Assign variant based on user ID hash and traffic allocation
        TrafficAllocation allocation = trafficAllocations.get(experimentId);
        if (allocation == null) {
            return null;
        }
        
        // Use consistent hashing based on user ID
        int hash = Math.abs(userId.hashCode());
        double probability = (hash % 100000) / 100000.0;
        
        String assignedVariant = allocation.getVariantForProbability(probability);
        
        if (assignedVariant != null) {
            // Cache assignment
            redisTemplate.opsForValue().set(assignmentKey, assignedVariant, 
                maxTestDurationDays, TimeUnit.DAYS);
        }
        
        return assignedVariant;
    }

    private void recordVariantAssignment(String experimentId, String variantId, String userId) {
        try {
            String key = TEST_CACHE_PREFIX + experimentId + ":" + variantId + ":assignments";
            redisTemplate.opsForSet().add(key, userId);
            redisTemplate.expire(key, maxTestDurationDays, TimeUnit.DAYS);
            
        } catch (Exception e) {
            log.error("Error recording variant assignment: {}", e.getMessage());
        }
    }

    private void updateVariantStatistics(String experimentId, String variantId, 
                                       ExperimentResult result) {
        ABTestExperiment experiment = getExperiment(experimentId);
        if (experiment == null) {
            return;
        }
        
        VariantStatistics stats = experiment.getVariantStatistics().get(variantId);
        if (stats == null) {
            stats = VariantStatistics.builder()
                .variantId(variantId)
                .sampleCount(0L)
                .conversionCount(0L)
                .totalLatency(0L)
                .errorCount(0L)
                .build();
            experiment.getVariantStatistics().put(variantId, stats);
        }
        
        // Update statistics
        stats.setSampleCount(stats.getSampleCount() + 1);
        
        if (result.isConversion()) {
            stats.setConversionCount(stats.getConversionCount() + 1);
        }
        
        if (result.getLatencyMs() != null) {
            stats.setTotalLatency(stats.getTotalLatency() + result.getLatencyMs());
        }
        
        if (result.isError()) {
            stats.setErrorCount(stats.getErrorCount() + 1);
        }
        
        // Calculate rates
        stats.setConversionRate((double) stats.getConversionCount() / stats.getSampleCount());
        stats.setAverageLatency(stats.getSampleCount() > 0 ? 
            (double) stats.getTotalLatency() / stats.getSampleCount() : 0.0);
        stats.setErrorRate((double) stats.getErrorCount() / stats.getSampleCount());
        
        // Update experiment
        cacheExperiment(experiment);
    }

    private boolean shouldEvaluateExperiment(ABTestExperiment experiment) {
        // Check if minimum sample size is reached
        long totalSamples = getTotalSampleSize(experiment);
        if (totalSamples < experiment.getMinSampleSize()) {
            return false;
        }
        
        // Check if experiment duration is sufficient (at least 1 day)
        long daysSinceStart = ChronoUnit.DAYS.between(experiment.getStartDate(), LocalDateTime.now());
        if (daysSinceStart < 1) {
            return false;
        }
        
        // Check if experiment has reached maximum duration
        if (daysSinceStart >= maxTestDurationDays) {
            return true;
        }
        
        // Check if early stopping criteria are met
        if (earlyStoppingEnabled) {
            StatisticalTestResult testResult = calculateStatisticalSignificance(experiment);
            return testResult.isSignificant() && testResult.getConfidence() >= minConfidence;
        }
        
        return false;
    }

    private void evaluateExperiment(String experimentId) {
        try {
            ABTestExperiment experiment = getExperiment(experimentId);
            if (experiment == null || experiment.getStatus() != ExperimentStatus.RUNNING) {
                return;
            }
            
            // Check if experiment should be stopped
            long daysSinceStart = ChronoUnit.DAYS.between(experiment.getStartDate(), LocalDateTime.now());
            
            if (daysSinceStart >= maxTestDurationDays) {
                stopExperiment(experimentId, "Maximum duration reached");
                return;
            }
            
            // Check for early stopping
            if (earlyStoppingEnabled && shouldEvaluateExperiment(experiment)) {
                StatisticalTestResult testResult = calculateStatisticalSignificance(experiment);
                
                if (testResult.isSignificant() && testResult.getConfidence() >= minConfidence) {
                    String winner = determineWinner(experiment, testResult);
                    stopExperiment(experimentId, "Statistical significance achieved - Winner: " + winner);
                }
            }
            
        } catch (Exception e) {
            log.error("Error evaluating experiment {}: {}", experimentId, e.getMessage());
        }
    }

    private StatisticalTestResult calculateStatisticalSignificance(ABTestExperiment experiment) {
        // Get control and treatment variants
        List<VariantStatistics> variants = new ArrayList<>(experiment.getVariantStatistics().values());
        
        if (variants.size() < 2) {
            return StatisticalTestResult.builder()
                .isSignificant(false)
                .pValue(1.0)
                .confidence(0.0)
                .testStatistic(0.0)
                .build();
        }
        
        // Use first variant as control
        VariantStatistics control = variants.get(0);
        VariantStatistics treatment = variants.get(1);
        
        // Perform two-proportion z-test
        double p1 = control.getConversionRate();
        double p2 = treatment.getConversionRate();
        long n1 = control.getSampleCount();
        long n2 = treatment.getSampleCount();
        
        if (n1 == 0 || n2 == 0) {
            return StatisticalTestResult.builder()
                .isSignificant(false)
                .pValue(1.0)
                .confidence(0.0)
                .testStatistic(0.0)
                .build();
        }
        
        // Calculate pooled proportion
        double pPooled = (control.getConversionCount() + treatment.getConversionCount()) / 
                        (double) (n1 + n2);
        
        // Calculate standard error
        double se = Math.sqrt(pPooled * (1 - pPooled) * (1.0/n1 + 1.0/n2));
        
        // Calculate z-statistic
        double z = se > 0 ? (p2 - p1) / se : 0.0;
        
        // Calculate two-tailed p-value
        double pValue = 2 * (1 - normalCDF(Math.abs(z)));
        
        boolean isSignificant = pValue < significanceLevel;
        double confidence = 1 - pValue;
        
        return StatisticalTestResult.builder()
            .isSignificant(isSignificant)
            .pValue(pValue)
            .confidence(confidence)
            .testStatistic(z)
            .effectSize(p2 - p1)
            .build();
    }

    private String determineWinner(ABTestExperiment experiment, StatisticalTestResult testResult) {
        if (!testResult.isSignificant()) {
            return "INCONCLUSIVE";
        }
        
        // Find variant with best performance on primary metric
        String bestVariant = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        
        for (VariantStatistics stats : experiment.getVariantStatistics().values()) {
            double value = getMetricValue(stats, experiment.getPrimaryMetric());
            if (value > bestValue) {
                bestValue = value;
                bestVariant = stats.getVariantId();
            }
        }
        
        return bestVariant;
    }

    private double getMetricValue(VariantStatistics stats, String metric) {
        switch (metric.toLowerCase()) {
            case "conversion_rate":
                return stats.getConversionRate();
            case "latency":
                return -stats.getAverageLatency(); // Lower is better, so negate
            case "error_rate":
                return -stats.getErrorRate(); // Lower is better, so negate
            default:
                return stats.getConversionRate(); // Default to conversion rate
        }
    }

    private Map<String, VariantAnalysis> calculateVariantAnalyses(ABTestExperiment experiment) {
        Map<String, VariantAnalysis> analyses = new HashMap<>();
        
        // Find control variant (first one)
        VariantStatistics control = experiment.getVariantStatistics().values().iterator().next();
        
        for (VariantStatistics variant : experiment.getVariantStatistics().values()) {
            ConfidenceInterval ci = calculateConfidenceInterval(variant);
            double lift = calculateLift(variant, control);
            
            analyses.put(variant.getVariantId(), 
                VariantAnalysis.builder()
                    .variantId(variant.getVariantId())
                    .sampleSize(variant.getSampleCount())
                    .conversionRate(variant.getConversionRate())
                    .errorRate(variant.getErrorRate())
                    .averageLatency(variant.getAverageLatency())
                    .lift(lift)
                    .confidenceInterval(ci)
                    .isStatisticallySignificant(isVariantSignificant(variant, control))
                    .build());
        }
        
        return analyses;
    }

    private ConfidenceInterval calculateConfidenceInterval(VariantStatistics variant) {
        double p = variant.getConversionRate();
        long n = variant.getSampleCount();
        
        if (n == 0) {
            return ConfidenceInterval.builder().lower(0.0).upper(0.0).build();
        }
        
        double z = 1.96; // 95% confidence level
        double se = Math.sqrt(p * (1 - p) / n);
        double margin = z * se;
        
        return ConfidenceInterval.builder()
            .lower(Math.max(0.0, p - margin))
            .upper(Math.min(1.0, p + margin))
            .build();
    }

    private double calculateLift(VariantStatistics variant, VariantStatistics control) {
        if (control.getConversionRate() == 0) {
            return 0.0;
        }
        
        return (variant.getConversionRate() - control.getConversionRate()) / 
               control.getConversionRate();
    }

    private boolean isVariantSignificant(VariantStatistics variant, VariantStatistics control) {
        // Simplified significance test
        double p1 = control.getConversionRate();
        double p2 = variant.getConversionRate();
        long n1 = control.getSampleCount();
        long n2 = variant.getSampleCount();
        
        if (n1 == 0 || n2 == 0) return false;
        
        double pPooled = (control.getConversionCount() + variant.getConversionCount()) / 
                        (double) (n1 + n2);
        double se = Math.sqrt(pPooled * (1 - pPooled) * (1.0/n1 + 1.0/n2));
        double z = se > 0 ? Math.abs(p2 - p1) / se : 0.0;
        double pValue = 2 * (1 - normalCDF(z));
        
        return pValue < significanceLevel;
    }

    private long getTotalSampleSize(ABTestExperiment experiment) {
        return experiment.getVariantStatistics().values().stream()
            .mapToLong(VariantStatistics::getSampleCount)
            .sum();
    }

    private List<String> generateRecommendations(ABTestExperiment experiment, 
                                               StatisticalTestResult testResult) {
        List<String> recommendations = new ArrayList<>();
        
        if (!testResult.isSignificant()) {
            recommendations.add("Test did not reach statistical significance. Consider running longer or increasing sample size.");
        } else {
            String winner = determineWinner(experiment, testResult);
            if (!"INCONCLUSIVE".equals(winner)) {
                recommendations.add("Deploy " + winner + " variant to 100% of traffic.");
            }
        }
        
        // Add sample size recommendations
        long totalSamples = getTotalSampleSize(experiment);
        if (totalSamples < experiment.getMinSampleSize()) {
            recommendations.add("Insufficient sample size. Continue test to reach minimum " + 
                experiment.getMinSampleSize() + " samples.");
        }
        
        // Add duration recommendations
        long days = ChronoUnit.DAYS.between(experiment.getStartDate(), 
            experiment.getEndDate() != null ? experiment.getEndDate() : LocalDateTime.now());
        if (days < 7) {
            recommendations.add("Consider running test for at least one full week to account for weekly seasonality.");
        }
        
        return recommendations;
    }

    private double normalCDF(double x) {
        // Approximate normal CDF using the error function approximation
        return 0.5 * (1 + erf(x / Math.sqrt(2)));
    }

    private double erf(double x) {
        // Abramowitz and Stegun approximation
        double a1 = 0.254829592;
        double a2 = -0.284496736;
        double a3 = 1.421413741;
        double a4 = -1.453152027;
        double a5 = 1.061405429;
        double p = 0.3275911;

        int sign = x < 0 ? -1 : 1;
        x = Math.abs(x);

        double t = 1.0 / (1.0 + p * x);
        double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);

        return sign * y;
    }

    private void publishExperimentEvent(ABTestExperiment experiment, String eventType) {
        try {
            Map<String, Object> event = Map.of(
                "event_type", eventType,
                "experiment_id", experiment.getExperimentId(),
                "experiment_name", experiment.getExperimentName(),
                "status", experiment.getStatus().toString(),
                "variant_count", experiment.getModelVariants().size(),
                "timestamp", LocalDateTime.now().toString()
            );
            
            kafkaTemplate.send("ml-ab-test-events", experiment.getExperimentId(), event);
            
        } catch (Exception e) {
            log.error("Error publishing experiment event: {}", e.getMessage());
        }
    }

    // Inner classes and DTOs

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateABTestRequest {
        private String experimentName;
        private String description;
        private List<ModelVariant> modelVariants;
        private Map<String, Double> trafficAllocation;
        private String primaryMetric;
        private List<String> secondaryMetrics;
        private Map<String, Object> targetAudience;
        private LocalDateTime endDate;
        private String createdBy;
        private Integer minSampleSize;
        private Double confidenceLevel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelVariant {
        private String variantId;
        private String variantName;
        private String modelName;
        private String modelVersion;
        private Map<String, Object> configuration;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ABTestExperiment {
        private String experimentId;
        private String experimentName;
        private String description;
        private List<ModelVariant> modelVariants;
        private Map<String, Double> trafficAllocation;
        private String primaryMetric;
        private List<String> secondaryMetrics;
        private Map<String, Object> targetAudience;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private ExperimentStatus status;
        private String createdBy;
        private Integer minSampleSize;
        private Double confidenceLevel;
        private Map<String, VariantStatistics> variantStatistics;
        private String stopReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantStatistics {
        private String variantId;
        private Long sampleCount;
        private Long conversionCount;
        private Double conversionRate;
        private Long totalLatency;
        private Double averageLatency;
        private Long errorCount;
        private Double errorRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentResult {
        private String userId;
        private String variantId;
        private boolean conversion;
        private Long latencyMs;
        private boolean error;
        private Map<String, Object> metadata;
        private LocalDateTime timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentAnalysis {
        private String experimentId;
        private String experimentName;
        private ExperimentStatus status;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private Long duration;
        private Long totalSampleSize;
        private boolean statisticalSignificance;
        private Double pValue;
        private Double confidence;
        private String winnerVariant;
        private Map<String, VariantAnalysis> variantAnalyses;
        private List<String> recommendations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantAnalysis {
        private String variantId;
        private Long sampleSize;
        private Double conversionRate;
        private Double errorRate;
        private Double averageLatency;
        private Double lift;
        private ConfidenceInterval confidenceInterval;
        private boolean isStatisticallySignificant;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfidenceInterval {
        private Double lower;
        private Double upper;
    }

    @Data
    @Builder
    private static class StatisticalTestResult {
        private boolean isSignificant;
        private Double pValue;
        private Double confidence;
        private Double testStatistic;
        private Double effectSize;
    }

    public enum ExperimentStatus {
        DRAFT, RUNNING, STOPPED, COMPLETED, PAUSED
    }

    private static class TrafficAllocation {
        private final List<VariantAllocation> allocations = new ArrayList<>();
        
        public void addVariant(String variantId, double cumulativeProbability) {
            allocations.add(new VariantAllocation(variantId, cumulativeProbability));
        }
        
        public String getVariantForProbability(double probability) {
            for (VariantAllocation allocation : allocations) {
                if (probability <= allocation.cumulativeProbability) {
                    return allocation.variantId;
                }
            }
            return allocations.isEmpty() ? null : allocations.get(allocations.size() - 1).variantId;
        }
        
        private static class VariantAllocation {
            final String variantId;
            final double cumulativeProbability;
            
            VariantAllocation(String variantId, double cumulativeProbability) {
                this.variantId = variantId;
                this.cumulativeProbability = cumulativeProbability;
            }
        }
    }
}