package com.waqiti.analytics.ml;

import com.waqiti.analytics.ml.exception.MLServiceException;
import com.waqiti.analytics.ml.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating appropriate ML model wrappers based on model file type
 */
@Slf4j
@Component
public class ModelFactory {
    
    private final ConcurrentHashMap<String, MLModel> modelCache = new ConcurrentHashMap<>();
    
    /**
     * Create appropriate model wrapper based on file extension and availability
     */
    public MLModel createModel(File modelFile) {
        String modelPath = modelFile.getAbsolutePath();
        String fileName = modelFile.getName().toLowerCase();
        
        // Check cache first
        if (modelCache.containsKey(modelPath)) {
            return modelCache.get(modelPath);
        }
        
        MLModel model;
        
        try {
            if (fileName.endsWith(".joblib") || fileName.contains("sklearn")) {
                model = createScikitLearnModel(modelPath);
            } else if (fileName.endsWith(".h5") || fileName.endsWith(".pb") || 
                      modelFile.isDirectory() && new File(modelFile, "saved_model.pb").exists()) {
                model = createTensorFlowModel(modelPath);
            } else if (fileName.endsWith(".onnx")) {
                model = createONNXModel(modelPath);
            } else if (fileName.endsWith(".pkl") || fileName.endsWith(".pickle")) {
                model = createPickleModel(modelPath);
            } else {
                throw new MLServiceException("Unsupported model format: " + fileName);
            }
            
            // Cache the model
            modelCache.put(modelPath, model);
            return model;
            
        } catch (Exception e) {
            log.error("Failed to create model for file: {}", modelPath, e);
            throw new MLServiceException("Model creation failed: " + e.getMessage(), e);
        }
    }
    
    private MLModel createScikitLearnModel(String modelPath) {
        // Try JEP first, fall back to ProcessBuilder
        if (JEPModelWrapper.isJEPAvailable() && JEPModelWrapper.areRequiredPackagesAvailable()) {
            log.info("Using JEP for scikit-learn model: {}", modelPath);
            return new JEPModelWrapper(modelPath, "scikit-learn");
        } else if (PythonModelWrapper.isPythonAvailable() && 
                   PythonModelWrapper.areRequiredPackagesAvailable()) {
            log.info("Using ProcessBuilder for scikit-learn model: {}", modelPath);
            return new PythonModelWrapper(modelPath, "scikit-learn");
        } else {
            throw new MLServiceException("No Python environment available for scikit-learn model");
        }
    }
    
    private MLModel createTensorFlowModel(String modelPath) {
        if (TensorFlowModelWrapper.isTensorFlowAvailable()) {
            log.info("Using TensorFlow Java for model: {}", modelPath);
            return new TensorFlowModelWrapper(modelPath);
        } else {
            throw new MLServiceException("TensorFlow Java not available");
        }
    }
    
    private MLModel createONNXModel(String modelPath) {
        if (ONNXModelWrapper.isONNXRuntimeAvailable()) {
            log.info("Using ONNX Runtime for model: {}", modelPath);
            return new ONNXModelWrapper(modelPath);
        } else {
            throw new MLServiceException("ONNX Runtime not available");
        }
    }
    
    private MLModel createPickleModel(String modelPath) {
        // Try JEP first for better performance, fall back to ProcessBuilder
        if (JEPModelWrapper.isJEPAvailable() && JEPModelWrapper.areRequiredPackagesAvailable()) {
            log.info("Using JEP for pickle model: {}", modelPath);
            return new JEPModelWrapper(modelPath, "pickle");
        } else if (PythonModelWrapper.isPythonAvailable() && 
                   PythonModelWrapper.areRequiredPackagesAvailable()) {
            log.info("Using ProcessBuilder for pickle model: {}", modelPath);
            return new PythonModelWrapper(modelPath, "pickle");
        } else {
            throw new MLServiceException("No Python environment available for pickle model");
        }
    }
    
    /**
     * Clear model cache and close resources
     */
    public void clearCache() {
        modelCache.values().forEach(model -> {
            if (model instanceof TensorFlowModelWrapper) {
                ((TensorFlowModelWrapper) model).close();
            } else if (model instanceof ONNXModelWrapper) {
                ((ONNXModelWrapper) model).close();
            } else if (model instanceof JEPModelWrapper) {
                ((JEPModelWrapper) model).close();
            }
        });
        modelCache.clear();
        log.info("Model cache cleared");
    }
    
    /**
     * Get cached model if available
     */
    public MLModel getCachedModel(String modelPath) {
        return modelCache.get(modelPath);
    }
    
    /**
     * Check if model is cached
     */
    public boolean isModelCached(String modelPath) {
        return modelCache.containsKey(modelPath);
    }
    
    /**
     * Get cache size
     */
    public int getCacheSize() {
        return modelCache.size();
    }
    
    /**
     * Check system capabilities for different model types
     */
    public ModelCapabilities checkCapabilities() {
        return ModelCapabilities.builder()
                .tensorFlowAvailable(TensorFlowModelWrapper.isTensorFlowAvailable())
                .onnxRuntimeAvailable(ONNXModelWrapper.isONNXRuntimeAvailable())
                .jepAvailable(JEPModelWrapper.isJEPAvailable())
                .pythonAvailable(PythonModelWrapper.isPythonAvailable())
                .pythonPackagesAvailable(PythonModelWrapper.areRequiredPackagesAvailable())
                .jepPackagesAvailable(JEPModelWrapper.areRequiredPackagesAvailable())
                .build();
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ModelCapabilities {
        private boolean tensorFlowAvailable;
        private boolean onnxRuntimeAvailable;
        private boolean jepAvailable;
        private boolean pythonAvailable;
        private boolean pythonPackagesAvailable;
        private boolean jepPackagesAvailable;
        
        public boolean canLoadScikitLearn() {
            return (jepAvailable && jepPackagesAvailable) || 
                   (pythonAvailable && pythonPackagesAvailable);
        }
        
        public boolean canLoadTensorFlow() {
            return tensorFlowAvailable;
        }
        
        public boolean canLoadONNX() {
            return onnxRuntimeAvailable;
        }
        
        public boolean canLoadPickle() {
            return (jepAvailable && jepPackagesAvailable) || 
                   (pythonAvailable && pythonPackagesAvailable);
        }
    }
}