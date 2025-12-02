package com.waqiti.ml.api;

import com.waqiti.ml.service.ModelInferenceService;
import com.waqiti.ml.service.model.ModelEnsemble;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ml/models")
@RequiredArgsConstructor
@Slf4j
public class ModelManagementController {

    private final ModelInferenceService modelInferenceService;
    private final ModelEnsemble modelEnsemble;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ML_ENGINEER')")
    public ResponseEntity<List<Map<String, Object>>> getAllModels() {
        log.info("Getting all ML models");
        
        List<Map<String, Object>> models = modelInferenceService.getAllModels();
        return ResponseEntity.ok(models);
    }

    @GetMapping("/{modelId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ML_ENGINEER')")
    public ResponseEntity<Map<String, Object>> getModel(@PathVariable String modelId) {
        log.info("Getting ML model: {}", modelId);
        
        Map<String, Object> model = modelInferenceService.getModel(modelId);
        return ResponseEntity.ok(model);
    }

    @PostMapping("/upload")
    @PreAuthorize("hasRole('ML_ENGINEER')")
    public ResponseEntity<Map<String, Object>> uploadModel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("modelName") String modelName,
            @RequestParam("modelType") String modelType,
            @RequestParam("version") String version,
            @RequestParam(value = "description", required = false) String description) {
        log.info("Uploading ML model: {} v{} of type: {}", modelName, version, modelType);
        
        try {
            String modelId = modelInferenceService.uploadModel(file, modelName, modelType, version, description);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "modelId", modelId,
                "modelName", modelName,
                "version", version,
                "status", "uploaded",
                "uploadedAt", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to upload model", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/{modelId}/deploy")
    @PreAuthorize("hasRole('ML_ENGINEER')")
    public ResponseEntity<Map<String, Object>> deployModel(@PathVariable String modelId,
                                                          @RequestBody @Valid DeploymentRequest request) {
        log.info("Deploying ML model: {} to environment: {}", modelId, request.getEnvironment());
        
        try {
            String deploymentId = modelInferenceService.deployModel(modelId, request.getEnvironment(), request.getConfig());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "modelId", modelId,
                "deploymentId", deploymentId,
                "environment", request.getEnvironment(),
                "status", "deploying",
                "deployedAt", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to deploy model", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/{modelId}/validate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ML_ENGINEER')")
    public ResponseEntity<Map<String, Object>> validateModel(@PathVariable String modelId,
                                                            @RequestBody @Valid ValidationRequest request) {
        log.info("Validating ML model: {} with test data", modelId);
        
        try {
            Map<String, Object> validationResults = modelInferenceService.validateModel(
                modelId, 
                request.getTestData(),
                request.getValidationMetrics()
            );
            
            return ResponseEntity.ok(validationResults);
        } catch (Exception e) {
            log.error("Failed to validate model", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/{modelId}/predict")
    @PreAuthorize("hasAnyRole('USER', 'SYSTEM', 'ML_ENGINEER')")
    public ResponseEntity<Map<String, Object>> predict(@PathVariable String modelId,
                                                      @RequestBody @Valid PredictionRequest request) {
        log.info("Making prediction with model: {} for request type: {}", modelId, request.getPredictionType());
        
        try {
            Map<String, Object> prediction = modelInferenceService.predict(modelId, request.getFeatures());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "modelId", modelId,
                "prediction", prediction,
                "confidence", prediction.get("confidence"),
                "predictedAt", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to make prediction", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/ensemble/predict")
    @PreAuthorize("hasAnyRole('USER', 'SYSTEM', 'ML_ENGINEER')")
    public ResponseEntity<Map<String, Object>> ensemblePredict(@RequestBody @Valid EnsemblePredictionRequest request) {
        log.info("Making ensemble prediction for models: {}", request.getModelIds());
        
        try {
            Map<String, Object> prediction = modelEnsemble.predict(request.getModelIds(), request.getFeatures(), request.getAggregationMethod());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "modelIds", request.getModelIds(),
                "prediction", prediction,
                "aggregationMethod", request.getAggregationMethod(),
                "predictedAt", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to make ensemble prediction", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/batch-predict")
    @PreAuthorize("hasAnyRole('ADMIN', 'ML_ENGINEER')")
    public ResponseEntity<Map<String, Object>> batchPredict(@RequestBody @Valid BatchPredictionRequest request) {
        log.info("Starting batch prediction job with model: {} for {} records", request.getModelId(), request.getBatchData().size());
        
        try {
            String jobId = modelInferenceService.startBatchPrediction(request.getModelId(), request.getBatchData());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "jobId", jobId,
                "modelId", request.getModelId(),
                "recordCount", request.getBatchData().size(),
                "status", "started",
                "startedAt", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to start batch prediction", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/batch-jobs/{jobId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ML_ENGINEER')")
    public ResponseEntity<Map<String, Object>> getBatchJobStatus(@PathVariable String jobId) {
        log.info("Getting batch job status: {}", jobId);
        
        try {
            Map<String, Object> jobStatus = modelInferenceService.getBatchJobStatus(jobId);
            return ResponseEntity.ok(jobStatus);
        } catch (Exception e) {
            log.error("Failed to get batch job status", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/{modelId}/retrain")
    @PreAuthorize("hasRole('ML_ENGINEER')")
    public ResponseEntity<Map<String, Object>> retrainModel(@PathVariable String modelId,
                                                           @RequestBody @Valid RetrainingRequest request) {
        log.info("Starting model retraining for: {}", modelId);
        
        try {
            String retrainingJobId = modelInferenceService.startRetraining(
                modelId, 
                request.getTrainingData(),
                request.getHyperparameters()
            );
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "modelId", modelId,
                "retrainingJobId", retrainingJobId,
                "status", "started",
                "startedAt", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to start model retraining", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/{modelId}/performance")
    @PreAuthorize("hasAnyRole('ADMIN', 'ML_ENGINEER')")
    public ResponseEntity<Map<String, Object>> getModelPerformance(@PathVariable String modelId,
                                                                  @RequestParam(defaultValue = "7") int days) {
        log.info("Getting performance metrics for model: {} over {} days", modelId, days);
        
        try {
            Map<String, Object> performance = modelInferenceService.getModelPerformance(modelId, days);
            return ResponseEntity.ok(performance);
        } catch (Exception e) {
            log.error("Failed to get model performance", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/{modelId}/version")
    @PreAuthorize("hasRole('ML_ENGINEER')")
    public ResponseEntity<Map<String, Object>> createModelVersion(@PathVariable String modelId,
                                                                 @RequestBody @Valid VersionRequest request) {
        log.info("Creating new version for model: {}", modelId);
        
        try {
            String versionId = modelInferenceService.createModelVersion(
                modelId,
                request.getVersionNumber(),
                request.getChanges(),
                request.getModelFile()
            );
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "modelId", modelId,
                "versionId", versionId,
                "versionNumber", request.getVersionNumber(),
                "createdAt", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to create model version", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @DELETE("/{modelId}")
    @PreAuthorize("hasRole('ML_ENGINEER')")
    public ResponseEntity<Map<String, Object>> deleteModel(@PathVariable String modelId) {
        log.info("Deleting ML model: {}", modelId);
        
        try {
            modelInferenceService.deleteModel(modelId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "modelId", modelId,
                "status", "deleted",
                "deletedAt", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to delete model", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<Map<String, Object>> getModelsHealth() {
        log.info("Checking ML models health");
        
        Map<String, Object> health = modelInferenceService.getModelsHealth();
        return ResponseEntity.ok(health);
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasAnyRole('ADMIN', 'ML_ENGINEER')")
    public ResponseEntity<Map<String, Object>> getGlobalMetrics() {
        log.info("Getting global ML metrics");
        
        Map<String, Object> metrics = modelInferenceService.getGlobalMetrics();
        return ResponseEntity.ok(metrics);
    }
}

// Request DTOs
@lombok.Data
@lombok.Builder
class DeploymentRequest {
    private String environment; // dev, staging, production
    private Map<String, Object> config;
}

@lombok.Data
@lombok.Builder
class ValidationRequest {
    private List<Map<String, Object>> testData;
    private List<String> validationMetrics;
}

@lombok.Data
@lombok.Builder
class PredictionRequest {
    private String predictionType;
    private Map<String, Object> features;
}

@lombok.Data
@lombok.Builder
class EnsemblePredictionRequest {
    private List<String> modelIds;
    private Map<String, Object> features;
    private String aggregationMethod; // weighted_average, majority_vote, max_confidence
}

@lombok.Data
@lombok.Builder
class BatchPredictionRequest {
    private String modelId;
    private List<Map<String, Object>> batchData;
}

@lombok.Data
@lombok.Builder
class RetrainingRequest {
    private List<Map<String, Object>> trainingData;
    private Map<String, Object> hyperparameters;
}

@lombok.Data
@lombok.Builder
class VersionRequest {
    private String versionNumber;
    private String changes;
    private String modelFile; // file path or reference
}