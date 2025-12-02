package com.waqiti.scaling.controller;

import com.waqiti.scaling.dto.*;
import com.waqiti.scaling.service.PredictiveScalingService;
import com.waqiti.scaling.service.MLPredictionService;
import com.waqiti.scaling.service.MetricsCollectionService;
import com.waqiti.scaling.service.CostOptimizationService;
import com.waqiti.scaling.service.AnomalyDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/predictive-scaling")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Predictive Scaling", description = "ML-powered predictive scaling operations")
public class PredictiveScalingController {
    
    private final PredictiveScalingService predictiveScalingService;
    private final MLPredictionService mlPredictionService;
    private final MetricsCollectionService metricsCollectionService;
    private final CostOptimizationService costOptimizationService;
    private final AnomalyDetectionService anomalyDetectionService;
    
    // Prediction Endpoints
    
    @PostMapping("/predictions")
    @Operation(summary = "Generate scaling prediction",
              description = "Generate ML-based scaling prediction for a service")
    @ApiResponse(responseCode = "200", description = "Prediction generated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid prediction request")
    @PreAuthorize("hasRole('SCALING_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public CompletableFuture<ResponseEntity<ScalingPredictionResponse>> generatePrediction(
            @Valid @RequestBody ScalingPredictionRequest request) {
        
        log.info("Generating scaling prediction for service: {} in namespace: {}", 
                request.getServiceName(), request.getNamespace());
        
        return predictiveScalingService.generateScalingPrediction(request)
                .thenApply(response -> {
                    if (response.isSuccessful()) {
                        return ResponseEntity.ok(response);
                    } else {
                        return ResponseEntity.badRequest().body(response);
                    }
                })
                .exceptionally(throwable -> {
                    log.error("Failed to generate prediction", throwable);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ScalingPredictionResponse.builder()
                                    .successful(false)
                                    .errorMessage("Internal server error")
                                    .build());
                });
    }
    
    @GetMapping("/predictions")
    @Operation(summary = "Get scaling predictions",
              description = "Retrieve scaling predictions with filtering and pagination")
    @ApiResponse(responseCode = "200", description = "Predictions retrieved successfully")
    @PreAuthorize("hasRole('SCALING_VIEWER') or hasRole('SCALING_ADMIN')")
    public ResponseEntity<Page<ScalingPredictionSummary>> getPredictions(
            @Parameter(description = "Service name filter") @RequestParam(required = false) String serviceName,
            @Parameter(description = "Namespace filter") @RequestParam(required = false) String namespace,
            @Parameter(description = "Prediction status filter") @RequestParam(required = false) String status,
            @Parameter(description = "Start date filter") @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date filter") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Pageable pageable) {
        
        log.debug("Getting scaling predictions with filters: service={}, namespace={}, status={}",
                 serviceName, namespace, status);
        
        try {
            PredictionsFilterRequest filterRequest = PredictionsFilterRequest.builder()
                    .serviceName(serviceName)
                    .namespace(namespace)
                    .status(status)
                    .startDate(startDate)
                    .endDate(endDate)
                    .build();
            
            Page<ScalingPredictionSummary> predictions = predictiveScalingService
                    .getPredictions(filterRequest, pageable);
            
            return ResponseEntity.ok(predictions);
            
        } catch (Exception e) {
            log.error("Failed to get predictions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/predictions/{predictionId}")
    @Operation(summary = "Get prediction details",
              description = "Retrieve detailed information about a specific prediction")
    @ApiResponse(responseCode = "200", description = "Prediction details retrieved")
    @ApiResponse(responseCode = "404", description = "Prediction not found")
    @PreAuthorize("hasRole('SCALING_VIEWER') or hasRole('SCALING_ADMIN')")
    public ResponseEntity<ScalingPredictionDetail> getPredictionDetails(
            @Parameter(description = "Prediction ID") @PathVariable String predictionId) {
        
        log.debug("Getting prediction details for: {}", predictionId);
        
        try {
            ScalingPredictionDetail details = predictiveScalingService
                    .getPredictionDetails(predictionId);
            
            if (details != null) {
                return ResponseEntity.ok(details);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("Failed to get prediction details for: {}", predictionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/predictions/{predictionId}/validate")
    @Operation(summary = "Validate prediction accuracy",
              description = "Manually validate prediction accuracy against actual metrics")
    @ApiResponse(responseCode = "200", description = "Prediction validated successfully")
    @PreAuthorize("hasRole('SCALING_ADMIN')")
    public ResponseEntity<PredictionValidationResponse> validatePrediction(
            @Parameter(description = "Prediction ID") @PathVariable String predictionId,
            @Valid @RequestBody PredictionValidationRequest request) {
        
        log.info("Validating prediction: {}", predictionId);
        
        try {
            PredictionValidationResponse response = predictiveScalingService
                    .validatePrediction(predictionId, request);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to validate prediction: {}", predictionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(PredictionValidationResponse.builder()
                            .successful(false)
                            .errorMessage("Validation failed: " + e.getMessage())
                            .build());
        }
    }
    
    // Scaling Action Endpoints
    
    @PostMapping("/actions")
    @Operation(summary = "Create scaling action",
              description = "Create a new scaling action based on prediction or manual input")
    @ApiResponse(responseCode = "201", description = "Scaling action created")
    @ApiResponse(responseCode = "400", description = "Invalid action request")
    @PreAuthorize("hasRole('SCALING_ADMIN')")
    public ResponseEntity<ScalingActionResponse> createScalingAction(
            @Valid @RequestBody ScalingActionRequest request) {
        
        log.info("Creating scaling action for service: {} - {} to {} replicas", 
                request.getServiceName(), request.getCurrentReplicas(), request.getTargetReplicas());
        
        try {
            ScalingActionResponse response = predictiveScalingService
                    .createScalingAction(request);
            
            if (response.isSuccessful()) {
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            log.error("Failed to create scaling action", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ScalingActionResponse.builder()
                            .successful(false)
                            .errorMessage("Failed to create action: " + e.getMessage())
                            .build());
        }
    }
    
    @PostMapping("/actions/{actionId}/execute")
    @Operation(summary = "Execute scaling action",
              description = "Execute a pending scaling action")
    @ApiResponse(responseCode = "200", description = "Action executed successfully")
    @ApiResponse(responseCode = "404", description = "Action not found")
    @PreAuthorize("hasRole('SCALING_ADMIN')")
    public CompletableFuture<ResponseEntity<ScalingExecutionResponse>> executeAction(
            @Parameter(description = "Action ID") @PathVariable String actionId,
            @Valid @RequestBody(required = false) ScalingExecutionRequest request) {
        
        log.info("Executing scaling action: {}", actionId);
        
        if (request == null) {
            request = ScalingExecutionRequest.builder()
                    .forceExecution(false)
                    .dryRun(false)
                    .build();
        }
        
        return predictiveScalingService.executeScalingAction(actionId, request)
                .thenApply(response -> {
                    if (response.isSuccessful()) {
                        return ResponseEntity.ok(response);
                    } else {
                        return ResponseEntity.badRequest().body(response);
                    }
                })
                .exceptionally(throwable -> {
                    log.error("Failed to execute action: {}", actionId, throwable);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ScalingExecutionResponse.builder()
                                    .successful(false)
                                    .errorMessage("Execution failed: " + throwable.getMessage())
                                    .build());
                });
    }
    
    @GetMapping("/actions")
    @Operation(summary = "Get scaling actions",
              description = "Retrieve scaling actions with filtering and pagination")
    @ApiResponse(responseCode = "200", description = "Actions retrieved successfully")
    @PreAuthorize("hasRole('SCALING_VIEWER') or hasRole('SCALING_ADMIN')")
    public ResponseEntity<Page<ScalingActionSummary>> getScalingActions(
            @Parameter(description = "Service name filter") @RequestParam(required = false) String serviceName,
            @Parameter(description = "Namespace filter") @RequestParam(required = false) String namespace,
            @Parameter(description = "Action status filter") @RequestParam(required = false) String status,
            @Parameter(description = "Action type filter") @RequestParam(required = false) String actionType,
            @Parameter(description = "Start date filter") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date filter") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Pageable pageable) {
        
        log.debug("Getting scaling actions with filters: service={}, namespace={}, status={}",
                 serviceName, namespace, status);
        
        try {
            ActionsFilterRequest filterRequest = ActionsFilterRequest.builder()
                    .serviceName(serviceName)
                    .namespace(namespace)
                    .status(status)
                    .actionType(actionType)
                    .startDate(startDate)
                    .endDate(endDate)
                    .build();
            
            Page<ScalingActionSummary> actions = predictiveScalingService
                    .getScalingActions(filterRequest, pageable);
            
            return ResponseEntity.ok(actions);
            
        } catch (Exception e) {
            log.error("Failed to get scaling actions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/actions/{actionId}")
    @Operation(summary = "Get action details",
              description = "Retrieve detailed information about a specific scaling action")
    @ApiResponse(responseCode = "200", description = "Action details retrieved")
    @ApiResponse(responseCode = "404", description = "Action not found")
    @PreAuthorize("hasRole('SCALING_VIEWER') or hasRole('SCALING_ADMIN')")
    public ResponseEntity<ScalingActionDetail> getActionDetails(
            @Parameter(description = "Action ID") @PathVariable String actionId) {
        
        log.debug("Getting action details for: {}", actionId);
        
        try {
            ScalingActionDetail details = predictiveScalingService
                    .getActionDetails(actionId);
            
            if (details != null) {
                return ResponseEntity.ok(details);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("Failed to get action details for: {}", actionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PutMapping("/actions/{actionId}/cancel")
    @Operation(summary = "Cancel scaling action",
              description = "Cancel a pending or scheduled scaling action")
    @ApiResponse(responseCode = "200", description = "Action cancelled successfully")
    @ApiResponse(responseCode = "404", description = "Action not found")
    @PreAuthorize("hasRole('SCALING_ADMIN')")
    public ResponseEntity<ScalingActionResponse> cancelAction(
            @Parameter(description = "Action ID") @PathVariable String actionId,
            @Valid @RequestBody ActionCancellationRequest request) {
        
        log.info("Cancelling scaling action: {} - reason: {}", actionId, request.getReason());
        
        try {
            ScalingActionResponse response = predictiveScalingService
                    .cancelScalingAction(actionId, request);
            
            if (response.isSuccessful()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            log.error("Failed to cancel action: {}", actionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ScalingActionResponse.builder()
                            .successful(false)
                            .errorMessage("Cancellation failed: " + e.getMessage())
                            .build());
        }
    }
    
    // Recommendations Endpoints
    
    @GetMapping("/services/{serviceName}/recommendations")
    @Operation(summary = "Get scaling recommendations",
              description = "Get AI-powered scaling recommendations for a service")
    @ApiResponse(responseCode = "200", description = "Recommendations retrieved successfully")
    @PreAuthorize("hasRole('SCALING_VIEWER') or hasRole('SCALING_ADMIN')")
    public ResponseEntity<ScalingRecommendationsResponse> getRecommendations(
            @Parameter(description = "Service name") @PathVariable String serviceName,
            @Parameter(description = "Namespace") @RequestParam(defaultValue = "default") String namespace,
            @Parameter(description = "Recommendation type filter") @RequestParam(required = false) String type,
            @Parameter(description = "Time horizon in hours") @RequestParam(defaultValue = "24") Integer timeHorizonHours) {
        
        log.debug("Getting scaling recommendations for service: {} in namespace: {}", 
                 serviceName, namespace);
        
        try {
            RecommendationsRequest request = RecommendationsRequest.builder()
                    .type(type)
                    .timeHorizonHours(timeHorizonHours)
                    .build();
            
            ScalingRecommendationsResponse response = predictiveScalingService
                    .getScalingRecommendations(serviceName, namespace, request);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get recommendations for service: {}", serviceName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ScalingRecommendationsResponse.builder()
                            .serviceName(serviceName)
                            .namespace(namespace)
                            .errorMessage("Failed to get recommendations: " + e.getMessage())
                            .build());
        }
    }
    
    // Metrics and Analytics Endpoints
    
    @GetMapping("/services/{serviceName}/metrics")
    @Operation(summary = "Get service metrics",
              description = "Retrieve current and historical metrics for a service")
    @ApiResponse(responseCode = "200", description = "Metrics retrieved successfully")
    @PreAuthorize("hasRole('SCALING_VIEWER') or hasRole('SCALING_ADMIN')")
    public ResponseEntity<ServiceMetricsResponse> getServiceMetrics(
            @Parameter(description = "Service name") @PathVariable String serviceName,
            @Parameter(description = "Namespace") @RequestParam(defaultValue = "default") String namespace,
            @Parameter(description = "Time range in hours") @RequestParam(defaultValue = "24") Integer timeRangeHours,
            @Parameter(description = "Aggregation interval in minutes") @RequestParam(defaultValue = "5") Integer intervalMinutes) {
        
        log.debug("Getting metrics for service: {} in namespace: {}", serviceName, namespace);
        
        try {
            MetricsRequest request = MetricsRequest.builder()
                    .timeRangeHours(timeRangeHours)
                    .intervalMinutes(intervalMinutes)
                    .build();
            
            ServiceMetricsResponse response = metricsCollectionService
                    .getServiceMetrics(serviceName, namespace, request);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get metrics for service: {}", serviceName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ServiceMetricsResponse.builder()
                            .serviceName(serviceName)
                            .namespace(namespace)
                            .errorMessage("Failed to get metrics: " + e.getMessage())
                            .build());
        }
    }
    
    @GetMapping("/analytics/dashboard")
    @Operation(summary = "Get scaling analytics dashboard",
              description = "Retrieve comprehensive scaling analytics and performance metrics")
    @ApiResponse(responseCode = "200", description = "Dashboard data retrieved successfully")
    @PreAuthorize("hasRole('SCALING_VIEWER') or hasRole('SCALING_ADMIN')")
    public ResponseEntity<ScalingDashboardResponse> getDashboard(
            @Parameter(description = "Time range in days") @RequestParam(defaultValue = "7") Integer timeRangeDays,
            @Parameter(description = "Service name filter") @RequestParam(required = false) String serviceName,
            @Parameter(description = "Namespace filter") @RequestParam(required = false) String namespace) {
        
        log.debug("Getting scaling dashboard for timeRange: {} days", timeRangeDays);
        
        try {
            DashboardRequest request = DashboardRequest.builder()
                    .timeRangeDays(timeRangeDays)
                    .serviceName(serviceName)
                    .namespace(namespace)
                    .build();
            
            ScalingDashboardResponse response = predictiveScalingService
                    .getDashboard(request);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get dashboard data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ScalingDashboardResponse.builder()
                            .errorMessage("Failed to get dashboard: " + e.getMessage())
                            .build());
        }
    }
    
    // Cost Optimization Endpoints
    
    @GetMapping("/cost-optimization/analysis")
    @Operation(summary = "Get cost optimization analysis",
              description = "Retrieve cost optimization opportunities and recommendations")
    @ApiResponse(responseCode = "200", description = "Cost analysis retrieved successfully")
    @PreAuthorize("hasRole('SCALING_VIEWER') or hasRole('SCALING_ADMIN')")
    public ResponseEntity<CostOptimizationResponse> getCostOptimizationAnalysis(
            @Parameter(description = "Service name filter") @RequestParam(required = false) String serviceName,
            @Parameter(description = "Namespace filter") @RequestParam(required = false) String namespace,
            @Parameter(description = "Analysis period in days") @RequestParam(defaultValue = "30") Integer analysisDays) {
        
        log.debug("Getting cost optimization analysis for period: {} days", analysisDays);
        
        try {
            CostOptimizationRequest request = CostOptimizationRequest.builder()
                    .serviceName(serviceName)
                    .namespace(namespace)
                    .analysisDays(analysisDays)
                    .build();
            
            CostOptimizationResponse response = costOptimizationService
                    .performAnalysis(request);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get cost optimization analysis", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CostOptimizationResponse.builder()
                            .errorMessage("Failed to analyze costs: " + e.getMessage())
                            .build());
        }
    }
    
    // Anomaly Detection Endpoints
    
    @GetMapping("/anomalies")
    @Operation(summary = "Get detected anomalies",
              description = "Retrieve detected anomalies in service metrics")
    @ApiResponse(responseCode = "200", description = "Anomalies retrieved successfully")
    @PreAuthorize("hasRole('SCALING_VIEWER') or hasRole('SCALING_ADMIN')")
    public ResponseEntity<List<AnomalyDetectionResult>> getAnomalies(
            @Parameter(description = "Service name filter") @RequestParam(required = false) String serviceName,
            @Parameter(description = "Namespace filter") @RequestParam(required = false) String namespace,
            @Parameter(description = "Severity filter") @RequestParam(required = false) String severity,
            @Parameter(description = "Time range in hours") @RequestParam(defaultValue = "24") Integer timeRangeHours) {
        
        log.debug("Getting anomalies for timeRange: {} hours", timeRangeHours);
        
        try {
            AnomalyFilterRequest request = AnomalyFilterRequest.builder()
                    .serviceName(serviceName)
                    .namespace(namespace)
                    .severity(severity)
                    .timeRangeHours(timeRangeHours)
                    .build();
            
            List<AnomalyDetectionResult> anomalies = anomalyDetectionService
                    .getAnomalies(request);
            
            return ResponseEntity.ok(anomalies);
            
        } catch (Exception e) {
            log.error("Failed to get anomalies", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // Model Management Endpoints
    
    @PostMapping("/models/retrain")
    @Operation(summary = "Trigger model retraining",
              description = "Manually trigger ML model retraining for specified services")
    @ApiResponse(responseCode = "202", description = "Retraining initiated")
    @PreAuthorize("hasRole('SCALING_ADMIN')")
    public ResponseEntity<ModelRetrainingResponse> retrainModels(
            @Valid @RequestBody ModelRetrainingRequest request) {
        
        log.info("Triggering model retraining for services: {}", request.getServiceNames());
        
        try {
            ModelRetrainingResponse response = mlPredictionService
                    .triggerRetraining(request);
            
            return ResponseEntity.accepted().body(response);
            
        } catch (Exception e) {
            log.error("Failed to trigger model retraining", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ModelRetrainingResponse.builder()
                            .successful(false)
                            .errorMessage("Retraining failed: " + e.getMessage())
                            .build());
        }
    }
    
    @GetMapping("/models/performance")
    @Operation(summary = "Get model performance metrics",
              description = "Retrieve ML model performance and accuracy metrics")
    @ApiResponse(responseCode = "200", description = "Model performance retrieved")
    @PreAuthorize("hasRole('SCALING_VIEWER') or hasRole('SCALING_ADMIN')")
    public ResponseEntity<ModelPerformanceResponse> getModelPerformance(
            @Parameter(description = "Model ID filter") @RequestParam(required = false) String modelId,
            @Parameter(description = "Service name filter") @RequestParam(required = false) String serviceName,
            @Parameter(description = "Performance period in days") @RequestParam(defaultValue = "30") Integer periodDays) {
        
        log.debug("Getting model performance for period: {} days", periodDays);
        
        try {
            ModelPerformanceRequest request = ModelPerformanceRequest.builder()
                    .modelId(modelId)
                    .serviceName(serviceName)
                    .periodDays(periodDays)
                    .build();
            
            ModelPerformanceResponse response = mlPredictionService
                    .getModelPerformance(request);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get model performance", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ModelPerformanceResponse.builder()
                            .errorMessage("Failed to get performance: " + e.getMessage())
                            .build());
        }
    }
    
    // Health and Status Endpoints
    
    @GetMapping("/health")
    @Operation(summary = "Get predictive scaling health",
              description = "Check the health status of the predictive scaling system")
    @ApiResponse(responseCode = "200", description = "Health status retrieved")
    public ResponseEntity<PredictiveScalingHealthResponse> getHealth() {
        
        try {
            PredictiveScalingHealthResponse health = predictiveScalingService.getHealth();
            
            if (health.isHealthy()) {
                return ResponseEntity.ok(health);
            } else {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
            }
            
        } catch (Exception e) {
            log.error("Failed to get health status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(PredictiveScalingHealthResponse.builder()
                            .healthy(false)
                            .errorMessage("Health check failed: " + e.getMessage())
                            .build());
        }
    }
}