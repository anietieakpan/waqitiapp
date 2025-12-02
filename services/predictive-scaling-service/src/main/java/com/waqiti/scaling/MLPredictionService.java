package com.waqiti.scaling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * ML-based Prediction Service for Proactive Auto-Scaling
 * 
 * This service implements sophisticated machine learning algorithms to predict
 * resource demand and proactively scale Kubernetes deployments before
 * load spikes occur.
 * 
 * Features:
 * - Multi-algorithm ensemble (LSTM, Prophet, ARIMA, XGBoost)
 * - Real-time feature engineering
 * - Seasonal pattern detection
 * - Business event integration
 * - Economic indicator correlation
 * - Continuous model retraining
 * - Prediction confidence scoring
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MLPredictionService {

    private final TimeSeriesDataCollector dataCollector;
    private final FeatureEngineering featureEngineering;
    private final ModelEnsemble modelEnsemble;
    private final MetricsCollector metricsCollector;
    private final BusinessEventService businessEventService;
    private final EconomicDataService economicDataService;
    private final KubernetesScalingService scalingService;
    
    // Prediction configuration
    private static final int DEFAULT_PREDICTION_HORIZON_MINUTES = 30;
    private static final int HISTORICAL_DATA_DAYS = 30;
    private static final double MIN_PREDICTION_CONFIDENCE = 0.7;
    private static final int MODEL_RETRAIN_INTERVAL_HOURS = 24;
    
    /**
     * Main entry point for demand prediction and scaling decisions
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void predictAndScale() {
        try {
            log.info("Starting predictive scaling cycle...");
            
            // Get all services that have predictive scaling enabled
            List<ScalableService> services = scalingService.getScalableServices();
            
            // Process each service in parallel
            List<CompletableFuture<ScalingDecision>> futures = services.stream()
                .map(service -> CompletableFuture.supplyAsync(() -> 
                    processSingleService(service)))
                .toList();
            
            // Wait for all predictions to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .join();
            
            // Collect results and apply scaling decisions
            futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .forEach(this::applyScalingDecision);
            
            log.info("Predictive scaling cycle completed");
            
        } catch (Exception e) {
            log.error("Error in predictive scaling cycle", e);
        }
    }
    
    /**
     * Processes a single service for scaling prediction
     */
    private ScalingDecision processSingleService(ScalableService service) {
        try {
            log.debug("Processing service: {}", service.getName());
            
            // 1. Collect historical data
            TimeSeriesData historicalData = dataCollector.collectData(
                service, HISTORICAL_DATA_DAYS);
            
            // 2. Engineer features
            FeatureMatrix features = featureEngineering.createFeatures(
                historicalData, service);
            
            // 3. Add business context
            enrichWithBusinessContext(features, service);
            
            // 4. Generate predictions
            PredictionResult prediction = modelEnsemble.predict(
                features, DEFAULT_PREDICTION_HORIZON_MINUTES);
            
            // 5. Calculate scaling decision
            ScalingDecision decision = calculateScalingDecision(
                service, prediction);
            
            // 6. Validate and apply safety checks
            decision = applySafetyChecks(service, decision);
            
            log.debug("Scaling decision for {}: current={}, predicted={}, target={}, confidence={}", 
                     service.getName(), 
                     service.getCurrentReplicas(),
                     prediction.getPredictedLoad(),
                     decision.getTargetReplicas(),
                     prediction.getConfidence());
            
            return decision;
            
        } catch (Exception e) {
            log.error("Error processing service: " + service.getName(), e);
            return null;
        }
    }
    
    /**
     * Enriches feature matrix with business context
     */
    private void enrichWithBusinessContext(FeatureMatrix features, ScalableService service) {
        // Add business events
        List<BusinessEvent> upcomingEvents = businessEventService
            .getUpcomingEvents(DEFAULT_PREDICTION_HORIZON_MINUTES);
        features.addBusinessEvents(upcomingEvents);
        
        // Add economic indicators
        EconomicIndicators indicators = economicDataService.getCurrentIndicators();
        features.addEconomicContext(indicators);
        
        // Add seasonal patterns
        SeasonalPatterns patterns = extractSeasonalPatterns(service);
        features.addSeasonalContext(patterns);
        
        // Add service-specific context
        ServiceContext context = buildServiceContext(service);
        features.addServiceContext(context);
    }
    
    /**
     * Calculates scaling decision based on prediction
     */
    private ScalingDecision calculateScalingDecision(ScalableService service, 
                                                   PredictionResult prediction) {
        
        // Base calculation: convert predicted load to required replicas
        int baseReplicas = calculateRequiredReplicas(
            prediction.getPredictedLoad(), service);
        
        // Apply confidence-based adjustments
        int adjustedReplicas = applyConfidenceAdjustment(
            baseReplicas, prediction.getConfidence(), service);
        
        // Apply business rules
        adjustedReplicas = applyBusinessRules(adjustedReplicas, service, prediction);
        
        // Ensure within bounds
        int targetReplicas = Math.max(service.getMinReplicas(),
            Math.min(service.getMaxReplicas(), adjustedReplicas));
        
        return ScalingDecision.builder()
            .serviceName(service.getName())
            .currentReplicas(service.getCurrentReplicas())
            .targetReplicas(targetReplicas)
            .predictionConfidence(prediction.getConfidence())
            .predictedLoad(prediction.getPredictedLoad())
            .scalingReason(buildScalingReason(service, prediction, targetReplicas))
            .timestamp(Instant.now())
            .safetyChecksApplied(false)
            .businessContext(prediction.getBusinessContext())
            .build();
    }
    
    /**
     * Calculates required replicas based on predicted load
     */
    private int calculateRequiredReplicas(double predictedLoad, ScalableService service) {
        // Get service capacity per replica
        double capacityPerReplica = service.getCapacityPerReplica();
        
        // Calculate base replicas needed
        double baseReplicas = predictedLoad / capacityPerReplica;
        
        // Add buffer for safety (configurable per service)
        double bufferMultiplier = 1.0 + (service.getSafetyBufferPercentage() / 100.0);
        
        return (int) Math.ceil(baseReplicas * bufferMultiplier);
    }
    
    /**
     * Applies confidence-based adjustments to replica count
     */
    private int applyConfidenceAdjustment(int baseReplicas, double confidence, 
                                        ScalableService service) {
        
        if (confidence >= 0.9) {
            // High confidence - use prediction as-is
            return baseReplicas;
        } else if (confidence >= 0.7) {
            // Medium confidence - add small buffer
            return (int) Math.ceil(baseReplicas * 1.1);
        } else if (confidence >= 0.5) {
            // Low confidence - add larger buffer
            return (int) Math.ceil(baseReplicas * 1.2);
        } else {
            // Very low confidence - fall back to conservative scaling
            return Math.max(baseReplicas, service.getCurrentReplicas());
        }
    }
    
    /**
     * Applies business rules for scaling decisions
     */
    private int applyBusinessRules(int replicas, ScalableService service, 
                                 PredictionResult prediction) {
        
        // Rule 1: High-value business events - always scale up aggressively
        if (prediction.hasHighValueBusinessEvent()) {
            return (int) Math.ceil(replicas * 1.5);
        }
        
        // Rule 2: Maintenance windows - scale down to minimum
        if (prediction.isMaintenanceWindow()) {
            return service.getMinReplicas();
        }
        
        // Rule 3: Cost optimization hours - prefer smaller scale
        if (prediction.isCostOptimizationPeriod()) {
            return Math.max(service.getMinReplicas(), 
                           (int) Math.ceil(replicas * 0.8));
        }
        
        // Rule 4: Peak business hours - ensure sufficient capacity
        if (prediction.isPeakBusinessHours()) {
            return Math.max(replicas, service.getMinReplicasForPeak());
        }
        
        return replicas;
    }
    
    /**
     * Applies safety checks to scaling decision
     */
    private ScalingDecision applySafetyChecks(ScalableService service, 
                                            ScalingDecision decision) {
        
        if (decision == null) {
            return null;
        }
        
        int currentReplicas = service.getCurrentReplicas();
        int targetReplicas = decision.getTargetReplicas();
        
        // Safety check 1: Maximum scale-up rate
        int maxScaleUpDelta = service.getMaxScaleUpDelta();
        if (targetReplicas > currentReplicas + maxScaleUpDelta) {
            targetReplicas = currentReplicas + maxScaleUpDelta;
            decision = decision.withAdjustedTarget(targetReplicas, 
                "Limited by max scale-up rate");
        }
        
        // Safety check 2: Maximum scale-down rate
        int maxScaleDownDelta = service.getMaxScaleDownDelta();
        if (targetReplicas < currentReplicas - maxScaleDownDelta) {
            targetReplicas = currentReplicas - maxScaleDownDelta;
            decision = decision.withAdjustedTarget(targetReplicas, 
                "Limited by max scale-down rate");
        }
        
        // Safety check 3: Cooldown periods
        if (service.isInScalingCooldown()) {
            log.debug("Service {} is in cooldown period, skipping scaling", 
                     service.getName());
            return null;
        }
        
        // Safety check 4: Resource availability
        if (!scalingService.hasAvailableResources(service, targetReplicas)) {
            log.warn("Insufficient cluster resources for scaling service {} to {} replicas", 
                    service.getName(), targetReplicas);
            return null;
        }
        
        // Safety check 5: Circuit breaker for frequent scaling
        if (service.getScalingFrequency() > service.getMaxScalingFrequency()) {
            log.warn("Service {} is scaling too frequently, applying circuit breaker", 
                    service.getName());
            return null;
        }
        
        return decision.markSafetyChecksApplied();
    }
    
    /**
     * Applies the scaling decision to Kubernetes
     */
    private void applyScalingDecision(ScalingDecision decision) {
        try {
            if (decision.getTargetReplicas() == decision.getCurrentReplicas()) {
                log.debug("No scaling needed for service: {}", decision.getServiceName());
                return;
            }
            
            log.info("Applying scaling decision for {}: {} -> {} replicas (confidence: {:.2f})",
                    decision.getServiceName(),
                    decision.getCurrentReplicas(),
                    decision.getTargetReplicas(),
                    decision.getPredictionConfidence());
            
            // Apply the scaling
            boolean success = scalingService.scaleService(
                decision.getServiceName(), 
                decision.getTargetReplicas());
            
            if (success) {
                // Record scaling event
                recordScalingEvent(decision);
                
                // Update metrics
                updateScalingMetrics(decision);
                
                log.info("Successfully scaled service: {}", decision.getServiceName());
            } else {
                log.error("Failed to scale service: {}", decision.getServiceName());
            }
            
        } catch (Exception e) {
            log.error("Error applying scaling decision for service: " + 
                     decision.getServiceName(), e);
        }
    }
    
    /**
     * Model retraining scheduled task
     */
    @Scheduled(fixedRate = MODEL_RETRAIN_INTERVAL_HOURS * 3600000L)
    public void retrainModels() {
        try {
            log.info("Starting model retraining cycle...");
            
            List<ScalableService> services = scalingService.getScalableServices();
            
            for (ScalableService service : services) {
                try {
                    retrainServiceModel(service);
                } catch (Exception e) {
                    log.error("Error retraining model for service: " + service.getName(), e);
                }
            }
            
            log.info("Model retraining cycle completed");
            
        } catch (Exception e) {
            log.error("Error in model retraining cycle", e);
        }
    }
    
    /**
     * Retrains the ML model for a specific service
     */
    @Async
    public CompletableFuture<Void> retrainServiceModel(ScalableService service) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Retraining model for service: {}", service.getName());
                
                // Collect extended historical data for training
                TimeSeriesData trainingData = dataCollector.collectData(
                    service, HISTORICAL_DATA_DAYS * 2);
                
                // Prepare training features
                FeatureMatrix features = featureEngineering.createTrainingFeatures(
                    trainingData, service);
                
                // Retrain the model ensemble
                ModelTrainingResult result = modelEnsemble.retrain(service, features);
                
                // Validate model performance
                if (result.getAccuracy() >= service.getMinModelAccuracy()) {
                    // Deploy the new model
                    modelEnsemble.deployModel(service.getName(), result.getModel());
                    
                    log.info("Successfully retrained and deployed model for service: {} " +
                            "(accuracy: {:.3f})", service.getName(), result.getAccuracy());
                } else {
                    log.warn("Model retraining for service {} did not meet accuracy threshold: " +
                            "{:.3f} < {:.3f}", service.getName(), result.getAccuracy(), 
                            service.getMinModelAccuracy());
                }
                
            } catch (Exception e) {
                log.error("Error retraining model for service: " + service.getName(), e);
            }
        });
    }
    
    /**
     * Extracts seasonal patterns for a service
     */
    private SeasonalPatterns extractSeasonalPatterns(ScalableService service) {
        // Implementation would analyze historical data for:
        // - Daily patterns (peak hours)
        // - Weekly patterns (weekday vs weekend)
        // - Monthly patterns (end of month, pay days)
        // - Yearly patterns (holidays, seasonal events)
        
        return SeasonalPatterns.builder()
            .dailyPeakHours(Arrays.asList(9, 12, 15, 18)) // Business hours
            .weeklyPeakDays(Arrays.asList(2, 3, 4)) // Tue, Wed, Thu
            .monthlyPeakDays(Arrays.asList(1, 15, 30)) // Pay days
            .seasonalMultipliers(Map.of(
                "Q1", 0.9,  // Slower after holidays
                "Q2", 1.1,  // Spring activity increase
                "Q3", 0.95, // Summer slowdown
                "Q4", 1.2   // Holiday season peak
            ))
            .build();
    }
    
    /**
     * Builds service-specific context
     */
    private ServiceContext buildServiceContext(ScalableService service) {
        return ServiceContext.builder()
            .serviceName(service.getName())
            .serviceType(service.getType())
            .currentReplicas(service.getCurrentReplicas())
            .averageResponseTime(metricsCollector.getAverageResponseTime(service))
            .errorRate(metricsCollector.getErrorRate(service))
            .cpuUtilization(metricsCollector.getCpuUtilization(service))
            .memoryUtilization(metricsCollector.getMemoryUtilization(service))
            .requestRate(metricsCollector.getRequestRate(service))
            .queueLength(metricsCollector.getQueueLength(service))
            .build();
    }
    
    /**
     * Builds human-readable scaling reason
     */
    private String buildScalingReason(ScalableService service, PredictionResult prediction, 
                                    int targetReplicas) {
        StringBuilder reason = new StringBuilder();
        
        if (targetReplicas > service.getCurrentReplicas()) {
            reason.append("Scale up predicted due to: ");
        } else if (targetReplicas < service.getCurrentReplicas()) {
            reason.append("Scale down predicted due to: ");
        } else {
            return "No scaling needed";
        }
        
        // Add specific reasons based on prediction analysis
        if (prediction.hasHighValueBusinessEvent()) {
            reason.append("high-value business event, ");
        }
        if (prediction.isPeakBusinessHours()) {
            reason.append("peak business hours, ");
        }
        if (prediction.getLoadIncreasePercentage() > 20) {
            reason.append(String.format("%.1f%% load increase predicted, ", 
                         prediction.getLoadIncreasePercentage()));
        }
        
        reason.append(String.format("(confidence: %.1f%%)", 
                     prediction.getConfidence() * 100));
        
        return reason.toString();
    }
    
    /**
     * Records scaling event for audit and analysis
     */
    private void recordScalingEvent(ScalingDecision decision) {
        ScalingEvent event = ScalingEvent.builder()
            .serviceName(decision.getServiceName())
            .fromReplicas(decision.getCurrentReplicas())
            .toReplicas(decision.getTargetReplicas())
            .predictionConfidence(decision.getPredictionConfidence())
            .reason(decision.getScalingReason())
            .timestamp(decision.getTimestamp())
            .businessContext(decision.getBusinessContext())
            .build();
        
        // Store in database for analysis
        // Send to monitoring system
        // Publish to Kafka for real-time monitoring
    }
    
    /**
     * Updates scaling metrics for monitoring
     */
    private void updateScalingMetrics(ScalingDecision decision) {
        // Update Prometheus metrics
        // - scaling_events_total
        // - prediction_accuracy
        // - cost_optimization_percentage
        // - scaling_decision_latency
    }
}