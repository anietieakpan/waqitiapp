package com.waqiti.common.fraud.ml;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Repository for managing ML model persistence, versioning, and metadata.
 * Provides comprehensive model lifecycle management for fraud detection systems.
 */
@Slf4j
@Repository
public class ModelRepository {
    
    // In-memory storage for models and metadata (in production, use database)
    private final Map<String, MLModel> modelStorage = new ConcurrentHashMap<>();
    private final Map<String, ModelMetadata> modelMetadata = new ConcurrentHashMap<>();
    private final Map<String, List<ModelVersion>> modelVersionHistory = new ConcurrentHashMap<>();
    private final Map<String, ModelPerformanceMetrics> baselineMetrics = new ConcurrentHashMap<>();
    private final Map<String, List<ModelDeployment>> deploymentHistory = new ConcurrentHashMap<>();
    
    /**
     * Save ML model with versioning and metadata
     */
    public void saveModel(MLModel model) {
        if (model == null || model.getId() == null) {
            throw new IllegalArgumentException("Model and model ID cannot be null");
        }
        
        try {
            String modelId = model.getId();
            
            // Create model metadata if it doesn't exist
            ModelMetadata metadata = modelMetadata.computeIfAbsent(modelId, this::createModelMetadata);
            
            // Update metadata
            metadata.setLastUpdated(LocalDateTime.now());
            metadata.setCurrentVersion(model.getVersion());
            metadata.setTotalSaves(metadata.getTotalSaves() + 1);
            
            // Save model version history
            ModelVersion version = createModelVersion(model);
            modelVersionHistory.computeIfAbsent(modelId, k -> new ArrayList<>()).add(version);
            
            // Store the model
            modelStorage.put(modelId, model);
            
            log.info("Saved model: {} version: {} at {}", modelId, model.getVersion(), LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("Error saving model: {}", model.getId(), e);
            throw new RuntimeException("Failed to save model", e);
        }
    }
    
    /**
     * Load ML model by ID
     */
    public MLModel loadModel(String modelId) {
        if (modelId == null || modelId.trim().isEmpty()) {
            throw new IllegalArgumentException("Model ID cannot be null or empty");
        }
        
        MLModel model = modelStorage.get(modelId);
        if (model == null) {
            log.warn("Model not found: {}", modelId);
            return null;
        }
        
        // Update access metrics
        updateAccessMetrics(modelId);
        
        log.debug("Loaded model: {} version: {}", modelId, model.getVersion());
        return model;
    }
    
    /**
     * Load specific model version
     */
    public MLModel loadModelVersion(String modelId, String version) {
        List<ModelVersion> versions = modelVersionHistory.get(modelId);
        if (versions == null) {
            log.warn("No version history found for model: {}", modelId);
            return null;
        }
        
        ModelVersion targetVersion = versions.stream()
                .filter(v -> version.equals(v.getVersion()))
                .findFirst()
                .orElse(null);
        
        if (targetVersion == null) {
            log.warn("Version {} not found for model: {}", version, modelId);
            return null;
        }
        
        // In production, would reconstruct model from stored artifacts
        MLModel model = modelStorage.get(modelId);
        if (model != null) {
            // Create a copy with the requested version
            MLModel versionedModel = MLModel.builder()
                    .id(model.getId())
                    .type(model.getType())
                    .version(version)
                    .modelPath(targetVersion.getModelPath())
                    .trafficPercentage(model.getTrafficPercentage())
                    .isActive(model.isActive())
                    .createdAt(targetVersion.getCreatedAt())
                    .lastUpdated(model.getLastUpdated())
                    .hyperparameters(model.getHyperparameters())
                    .build();
            
            log.info("Loaded model: {} version: {}", modelId, version);
            return versionedModel;
        }
        
        return null;
    }
    
    /**
     * Get all available models
     */
    public List<MLModel> getAllModels() {
        return new ArrayList<>(modelStorage.values());
    }
    
    /**
     * Get models by type
     */
    public List<MLModel> getModelsByType(MachineLearningModelService.ModelType type) {
        return modelStorage.values().stream()
                .filter(model -> type.equals(model.getType()))
                .collect(Collectors.toList());
    }
    
    /**
     * Get active models only
     */
    public List<MLModel> getActiveModels() {
        return modelStorage.values().stream()
                .filter(MLModel::isActive)
                .collect(Collectors.toList());
    }
    
    /**
     * Delete model and all its history
     */
    public boolean deleteModel(String modelId) {
        if (modelId == null) {
            return false;
        }
        
        try {
            modelStorage.remove(modelId);
            modelMetadata.remove(modelId);
            modelVersionHistory.remove(modelId);
            baselineMetrics.remove(modelId);
            deploymentHistory.remove(modelId);
            
            log.info("Deleted model and all history: {}", modelId);
            return true;
            
        } catch (Exception e) {
            log.error("Error deleting model: {}", modelId, e);
            return false;
        }
    }
    
    /**
     * Archive old model versions
     */
    public int archiveOldVersions(String modelId, int keepRecentCount) {
        List<ModelVersion> versions = modelVersionHistory.get(modelId);
        if (versions == null || versions.size() <= keepRecentCount) {
            return 0;
        }
        
        // Sort by creation date (newest first)
        versions.sort((v1, v2) -> v2.getCreatedAt().compareTo(v1.getCreatedAt()));
        
        // Keep only recent versions
        List<ModelVersion> toKeep = versions.stream()
                .limit(keepRecentCount)
                .collect(Collectors.toList());
        
        int archivedCount = versions.size() - toKeep.size();
        
        modelVersionHistory.put(modelId, toKeep);
        
        log.info("Archived {} old versions for model: {}, kept {} recent versions", 
            archivedCount, modelId, keepRecentCount);
        
        return archivedCount;
    }
    
    /**
     * Get model metadata
     */
    public ModelMetadata getModelMetadata(String modelId) {
        return modelMetadata.get(modelId);
    }
    
    /**
     * Update model metadata
     */
    public void updateModelMetadata(String modelId, ModelMetadata metadata) {
        if (modelId != null && metadata != null) {
            modelMetadata.put(modelId, metadata);
        }
    }
    
    /**
     * Get model version history
     */
    public List<ModelVersion> getVersionHistory(String modelId) {
        List<ModelVersion> history = modelVersionHistory.get(modelId);
        return history != null ? new ArrayList<>(history) : new ArrayList<>();
    }
    
    /**
     * Save baseline performance metrics
     */
    public void saveBaselineMetrics(String modelId, ModelPerformanceMetrics metrics) {
        if (modelId != null && metrics != null) {
            baselineMetrics.put(modelId, metrics);
            log.debug("Saved baseline metrics for model: {}", modelId);
        }
    }
    
    /**
     * Get baseline performance metrics
     */
    public ModelPerformanceMetrics getBaselineMetrics(String modelId) {
        return baselineMetrics.get(modelId);
    }
    
    /**
     * Record model deployment
     */
    public void recordDeployment(String modelId, ModelDeployment deployment) {
        if (modelId != null && deployment != null) {
            deploymentHistory.computeIfAbsent(modelId, k -> new ArrayList<>()).add(deployment);
            log.info("Recorded deployment for model: {} at {}", modelId, deployment.getDeploymentTime());
        }
    }
    
    /**
     * Get deployment history
     */
    public List<ModelDeployment> getDeploymentHistory(String modelId) {
        List<ModelDeployment> history = deploymentHistory.get(modelId);
        return history != null ? new ArrayList<>(history) : new ArrayList<>();
    }
    
    /**
     * Find models by criteria
     */
    public List<MLModel> findModels(ModelSearchCriteria criteria) {
        return modelStorage.values().stream()
                .filter(criteria::matches)
                .collect(Collectors.toList());
    }
    
    /**
     * Get repository statistics
     */
    public RepositoryStats getRepositoryStats() {
        return RepositoryStats.builder()
                .totalModels(modelStorage.size())
                .activeModels((int) modelStorage.values().stream().filter(MLModel::isActive).count())
                .totalVersions(modelVersionHistory.values().stream().mapToInt(List::size).sum())
                .totalDeployments(deploymentHistory.values().stream().mapToInt(List::size).sum())
                .oldestModel(findOldestModel())
                .newestModel(findNewestModel())
                .lastUpdated(LocalDateTime.now())
                .build();
    }
    
    // Helper methods
    
    private ModelMetadata createModelMetadata(String modelId) {
        return ModelMetadata.builder()
                .modelId(modelId)
                .createdAt(LocalDateTime.now())
                .lastUpdated(LocalDateTime.now())
                .currentVersion("1.0")
                .totalSaves(0)
                .totalLoads(0)
                .status(ModelStatus.ACTIVE)
                .build();
    }
    
    private ModelVersion createModelVersion(MLModel model) {
        return ModelVersion.builder()
                .version(model.getVersion())
                .modelPath(model.getModelPath())
                .createdAt(LocalDateTime.now())
                .hyperparameters(model.getHyperparameters())
                .modelType(model.getType())
                .checksum(calculateModelChecksum(model))
                .build();
    }
    
    private void updateAccessMetrics(String modelId) {
        ModelMetadata metadata = modelMetadata.get(modelId);
        if (metadata != null) {
            metadata.setTotalLoads(metadata.getTotalLoads() + 1);
            metadata.setLastAccessed(LocalDateTime.now());
        }
    }
    
    private String calculateModelChecksum(MLModel model) {
        // Simplified checksum calculation
        return String.valueOf(Objects.hash(
                model.getId(),
                model.getVersion(),
                model.getType(),
                model.getHyperparameters()
        ));
    }
    
    private LocalDateTime findOldestModel() {
        return modelMetadata.values().stream()
                .map(ModelMetadata::getCreatedAt)
                .min(LocalDateTime::compareTo)
                .orElse(null);
    }
    
    private LocalDateTime findNewestModel() {
        return modelMetadata.values().stream()
                .map(ModelMetadata::getLastUpdated)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }
    
    // Supporting classes
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ModelMetadata {
        private String modelId;
        private LocalDateTime createdAt;
        private LocalDateTime lastUpdated;
        private LocalDateTime lastAccessed;
        private String currentVersion;
        private int totalSaves;
        private int totalLoads;
        private ModelStatus status;
        private String description;
        private Map<String, String> tags;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ModelVersion {
        private String version;
        private String modelPath;
        private LocalDateTime createdAt;
        private Map<String, Object> hyperparameters;
        private MachineLearningModelService.ModelType modelType;
        private String checksum;
        private long sizeBytes;
        private String commitHash;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ModelDeployment {
        private String environment;
        private String version;
        private LocalDateTime deploymentTime;
        private String deployedBy;
        private double trafficPercentage;
        private DeploymentStatus status;
        private String rollbackVersion;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RepositoryStats {
        private int totalModels;
        private int activeModels;
        private int totalVersions;
        private int totalDeployments;
        private LocalDateTime oldestModel;
        private LocalDateTime newestModel;
        private LocalDateTime lastUpdated;
    }
    
    @lombok.Data
    public static class ModelSearchCriteria {
        private MachineLearningModelService.ModelType type;
        private Boolean active;
        private LocalDateTime createdAfter;
        private LocalDateTime createdBefore;
        private String versionPattern;
        
        public boolean matches(MLModel model) {
            if (type != null && !type.equals(model.getType())) {
                return false;
            }
            if (active != null && !active.equals(model.isActive())) {
                return false;
            }
            if (createdAfter != null && model.getCreatedAt().isBefore(createdAfter)) {
                return false;
            }
            if (createdBefore != null && model.getCreatedAt().isAfter(createdBefore)) {
                return false;
            }
            if (versionPattern != null && !model.getVersion().matches(versionPattern)) {
                return false;
            }
            return true;
        }
    }
    
    public enum ModelStatus {
        ACTIVE, INACTIVE, ARCHIVED, DEPRECATED, TESTING
    }
    
    public enum DeploymentStatus {
        PENDING, DEPLOYED, FAILED, ROLLBACK, RETIRED
    }
}