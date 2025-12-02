package com.waqiti.analytics.ml.model;

import jep.Jep;
import jep.JepConfig;
import jep.JepException;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Wrapper for Python models using JEP (Java Embedded Python)
 * Supports pickle files and any Python-based ML models
 */
@Slf4j
public class JEPModelWrapper implements MLModel {
    
    private final String modelPath;
    private final String modelType;
    private final String modelVersion;
    private Jep jep;
    private final ReentrantLock jepLock = new ReentrantLock();
    private volatile boolean initialized = false;
    
    public JEPModelWrapper(String modelPath, String modelType) {
        this.modelPath = modelPath;
        this.modelType = modelType;
        this.modelVersion = extractVersionFromPath(modelPath);
        initialize();
    }
    
    private void initialize() {
        jepLock.lock();
        try {
            if (initialized) {
                return;
            }
            
            JepConfig config = new JepConfig()
                    .addIncludePaths(".")
                    .setInteractive(false)
                    .setRedirectOutputStreams(true);
            
            jep = new Jep(config);
            
            // Import required Python packages
            jep.eval("import numpy as np");
            jep.eval("import warnings");
            jep.eval("warnings.filterwarnings('ignore')");
            
            // Load model based on type
            if (modelType.toLowerCase().contains("pickle") || modelType.toLowerCase().contains("pkl")) {
                jep.eval("import pickle");
                jep.eval(String.format("with open('%s', 'rb') as f: model = pickle.load(f)", modelPath));
            } else if (modelType.toLowerCase().contains("joblib") || modelType.toLowerCase().contains("sklearn")) {
                jep.eval("import joblib");
                jep.eval(String.format("model = joblib.load('%s')", modelPath));
            } else {
                throw new RuntimeException("Unsupported model type for JEP: " + modelType);
            }
            
            initialized = true;
            log.info("JEP model initialized successfully: {} ({})", modelPath, modelType);
            
        } catch (JepException e) {
            log.error("Failed to initialize JEP model: {}", modelPath, e);
            throw new RuntimeException("JEP model initialization failed", e);
        } finally {
            jepLock.unlock();
        }
    }
    
    @Override
    public double[] predict(double[] features) {
        if (!initialized) {
            throw new IllegalStateException("JEP model not initialized");
        }
        
        jepLock.lock();
        try {
            // Set features in Python environment
            jep.set("features", features);
            jep.eval("features_array = np.array(features).reshape(1, -1)");
            
            // Make prediction
            if (hasMethod("predict_proba")) {
                // For classification models with probability prediction
                jep.eval("predictions = model.predict_proba(features_array)[0]");
            } else {
                // For regression or simple classification
                jep.eval("pred = model.predict(features_array)");
                jep.eval("predictions = pred if hasattr(pred, '__len__') and len(pred) > 1 else [pred[0]]");
            }
            
            // Get predictions back to Java
            Object result = jep.getValue("predictions");
            
            if (result instanceof double[]) {
                return (double[]) result;
            } else if (result instanceof float[]) {
                float[] floatResult = (float[]) result;
                double[] doubleResult = new double[floatResult.length];
                for (int i = 0; i < floatResult.length; i++) {
                    doubleResult[i] = floatResult[i];
                }
                return doubleResult;
            } else if (result instanceof Object[]) {
                Object[] objResult = (Object[]) result;
                double[] doubleResult = new double[objResult.length];
                for (int i = 0; i < objResult.length; i++) {
                    doubleResult[i] = ((Number) objResult[i]).doubleValue();
                }
                return doubleResult;
            } else {
                // Single value result
                return new double[]{((Number) result).doubleValue()};
            }
            
        } catch (JepException e) {
            log.error("JEP prediction failed for model: {}", modelPath, e);
            throw new RuntimeException("JEP model prediction failed", e);
        } finally {
            jepLock.unlock();
        }
    }
    
    @Override
    public double predict(double[] features) {
        double[] predictions = predict(features);
        return predictions.length > 0 ? predictions[0] : 0.0;
    }
    
    @Override
    public String getModelVersion() {
        return modelVersion;
    }
    
    @Override
    public String getModelType() {
        return modelType;
    }
    
    private boolean hasMethod(String methodName) {
        jepLock.lock();
        try {
            jep.eval(String.format("has_%s = hasattr(model, '%s')", methodName, methodName));
            return (Boolean) jep.getValue(String.format("has_%s", methodName));
        } catch (JepException e) {
            log.debug("Error checking for method {}: {}", methodName, e.getMessage());
            return false;
        } finally {
            jepLock.unlock();
        }
    }
    
    private String extractVersionFromPath(String path) {
        if (path.contains("_v")) {
            String[] parts = path.split("_v");
            if (parts.length > 1) {
                return "v" + parts[1].replaceAll("[^0-9.]", "");
            }
        }
        return "v1.0.0";
    }
    
    public void close() {
        jepLock.lock();
        try {
            if (jep != null) {
                jep.close();
                jep = null;
                initialized = false;
            }
        } catch (JepException e) {
            log.warn("Error closing JEP model", e);
        } finally {
            jepLock.unlock();
        }
    }
    
    public static boolean isJEPAvailable() {
        try {
            // Test JEP availability
            JepConfig config = new JepConfig().setInteractive(false);
            Jep testJep = new Jep(config);
            testJep.eval("print('JEP available')");
            testJep.close();
            return true;
        } catch (Exception e) {
            log.warn("JEP not available: {}", e.getMessage());
            return false;
        }
    }
    
    public static boolean areRequiredPackagesAvailable() {
        try {
            JepConfig config = new JepConfig().setInteractive(false);
            Jep testJep = new Jep(config);
            testJep.eval("import numpy, sklearn, joblib, pickle");
            testJep.close();
            return true;
        } catch (Exception e) {
            log.warn("Required Python packages not available via JEP: {}", e.getMessage());
            return false;
        }
    }
}