package com.waqiti.ml.controller;

import com.waqiti.ml.service.ABTestingService;
import com.waqiti.ml.service.ABTestingService.CreateABTestRequest;
import com.waqiti.ml.service.ABTestingService.ABTestExperiment;
import com.waqiti.ml.service.ABTestingService.ModelVariant;
import com.waqiti.ml.service.ABTestingService.ExperimentResult;
import com.waqiti.ml.service.ABTestingService.ExperimentAnalysis;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for A/B Testing ML models
 */
@RestController
@RequestMapping("/api/ml/ab-testing")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "A/B Testing", description = "ML model A/B testing and experimentation API")
public class ABTestingController {

    private final ABTestingService abTestingService;

    @PostMapping("/experiments")
    @Operation(summary = "Create A/B test experiment", 
               description = "Creates a new A/B test experiment for comparing ML models")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Experiment created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PreAuthorize("hasRole('ADMIN') or hasRole('ML_ENGINEER') or hasRole('DATA_SCIENTIST')")
    public ResponseEntity<ABTestExperiment> createExperiment(
            @RequestBody @Validated CreateABTestRequest request) {
        
        log.info("Creating A/B test experiment: {}", request.getExperimentName());
        
        try {
            ABTestExperiment experiment = abTestingService.createABTest(request);
            return ResponseEntity.ok(experiment);
        } catch (Exception e) {
            log.error("Error creating A/B test experiment: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/experiments/{experimentId}/variant")
    @Operation(summary = "Get model variant for user", 
               description = "Gets the assigned model variant for a user in an A/B test")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Variant assigned successfully"),
        @ApiResponse(responseCode = "404", description = "Experiment not found or not active"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PreAuthorize("hasRole('SYSTEM') or hasRole('ML_SERVICE') or hasRole('ADMIN')")
    public ResponseEntity<ModelVariant> getModelVariant(
            @PathVariable String experimentId,
            @RequestParam String userId,
            @RequestParam(required = false) Map<String, Object> context) {
        
        log.debug("Getting model variant for experiment: {} user: {}", experimentId, userId);
        
        try {
            ModelVariant variant = abTestingService.getModelVariant(experimentId, userId, context);
            if (variant != null) {
                return ResponseEntity.ok(variant);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error getting model variant: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/experiments/{experimentId}/results")
    @Operation(summary = "Record experiment result", 
               description = "Records the result of a user interaction in an A/B test")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Result recorded successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "Experiment not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PreAuthorize("hasRole('SYSTEM') or hasRole('ML_SERVICE') or hasRole('ADMIN')")
    public ResponseEntity<Void> recordResult(
            @PathVariable String experimentId,
            @RequestParam String userId,
            @RequestBody @Validated ExperimentResult result) {
        
        log.debug("Recording result for experiment: {} user: {}", experimentId, userId);
        
        try {
            abTestingService.recordExperimentResult(experimentId, userId, result);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error recording experiment result: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/experiments/{experimentId}/analysis")
    @Operation(summary = "Get experiment analysis", 
               description = "Gets the statistical analysis and results of an A/B test experiment")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Analysis retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Experiment not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PreAuthorize("hasRole('ADMIN') or hasRole('ML_ENGINEER') or hasRole('DATA_SCIENTIST')")
    public ResponseEntity<ExperimentAnalysis> getExperimentAnalysis(
            @PathVariable String experimentId) {
        
        log.info("Getting analysis for experiment: {}", experimentId);
        
        try {
            ExperimentAnalysis analysis = abTestingService.getExperimentResults(experimentId);
            if (analysis != null) {
                return ResponseEntity.ok(analysis);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error getting experiment analysis: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/experiments/{experimentId}/stop")
    @Operation(summary = "Stop experiment", 
               description = "Stops a running A/B test experiment")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Experiment stopped successfully"),
        @ApiResponse(responseCode = "404", description = "Experiment not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PreAuthorize("hasRole('ADMIN') or hasRole('ML_ENGINEER')")
    public ResponseEntity<Void> stopExperiment(
            @PathVariable String experimentId,
            @RequestParam String reason) {
        
        log.info("Stopping experiment: {} - Reason: {}", experimentId, reason);
        
        try {
            abTestingService.stopExperiment(experimentId, reason);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error stopping experiment: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}