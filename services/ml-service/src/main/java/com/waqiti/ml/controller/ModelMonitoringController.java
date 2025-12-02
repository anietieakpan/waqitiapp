package com.waqiti.ml.controller;

import com.waqiti.ml.entity.ModelPerformanceMetrics;
import com.waqiti.ml.service.ModelPerformanceMonitoringService;
import com.waqiti.ml.service.ModelPerformanceMonitoringService.ModelHealthStatus;
import com.waqiti.ml.service.ModelPerformanceMonitoringService.PerformanceTrend;
import com.waqiti.ml.service.ModelPerformanceMonitoringService.PredictionEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST Controller for ML Model Performance Monitoring
 */
@RestController
@RequestMapping("/api/ml/monitoring")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Model Monitoring", description = "ML model performance monitoring and metrics API")
public class ModelMonitoringController {

    private final ModelPerformanceMonitoringService monitoringService;

    @PostMapping("/models/{modelName}/versions/{modelVersion}/predictions")
    @Operation(summary = "Record prediction event", 
               description = "Records a prediction event for model monitoring")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Prediction event recorded successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PreAuthorize("hasRole('SYSTEM') or hasRole('ML_SERVICE') or hasRole('ADMIN')")
    public ResponseEntity<Void> recordPrediction(
            @PathVariable String modelName,
            @PathVariable String modelVersion,
            @RequestBody @Validated PredictionEvent event) {
        
        log.debug("Recording prediction event for model: {} v{}", modelName, modelVersion);
        
        try {
            monitoringService.recordPrediction(modelName, modelVersion, event);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error recording prediction: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/models/{modelName}/versions/{modelVersion}/metrics")
    @Operation(summary = "Get model performance metrics", 
               description = "Gets the current performance metrics for a model")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Metrics retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Model not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PreAuthorize("hasRole('ADMIN') or hasRole('ML_ENGINEER') or hasRole('DATA_SCIENTIST')")
    public ResponseEntity<ModelPerformanceMetrics> getMetrics(
            @PathVariable String modelName,
            @PathVariable String modelVersion) {
        
        log.debug("Getting metrics for model: {} v{}", modelName, modelVersion);
        
        try {
            ModelPerformanceMetrics metrics = monitoringService.getCurrentMetrics(modelName, modelVersion);
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            log.error("Error getting metrics: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/models/{modelName}/versions/{modelVersion}/health")
    @Operation(summary = "Get model health status", 
               description = "Gets the current health status and alerts for a model")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Health status retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Model not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PreAuthorize("hasRole('ADMIN') or hasRole('ML_ENGINEER') or hasRole('DATA_SCIENTIST')")
    public ResponseEntity<ModelHealthStatus> getHealthStatus(
            @PathVariable String modelName,
            @PathVariable String modelVersion) {
        
        log.debug("Getting health status for model: {} v{}", modelName, modelVersion);
        
        try {
            ModelHealthStatus status = monitoringService.getModelHealthStatus(modelName, modelVersion);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error getting health status: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/models/{modelName}/versions/{modelVersion}/trends")
    @Operation(summary = "Get performance trends", 
               description = "Gets the performance trends for a model over time")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Trends retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Model not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PreAuthorize("hasRole('ADMIN') or hasRole('ML_ENGINEER') or hasRole('DATA_SCIENTIST')")
    public ResponseEntity<List<PerformanceTrend>> getPerformanceTrends(
            @PathVariable String modelName,
            @PathVariable String modelVersion,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        log.debug("Getting performance trends for model: {} v{} from {} to {}", 
            modelName, modelVersion, startDate, endDate);
        
        try {
            List<PerformanceTrend> trends = monitoringService.getPerformanceTrends(
                modelName, modelVersion, startDate, endDate);
            return ResponseEntity.ok(trends);
        } catch (Exception e) {
            log.error("Error getting performance trends: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}