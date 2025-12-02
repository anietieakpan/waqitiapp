package com.waqiti.analytics.ml.model;

import ai.djl.Model;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import lombok.extern.slf4j.Slf4j;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Tensor;
import org.tensorflow.TensorFlow;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wrapper for TensorFlow models using both TensorFlow Java and DJL
 */
@Slf4j
public class TensorFlowModelWrapper implements MLModel {
    
    private final String modelPath;
    private final String modelVersion;
    private SavedModelBundle savedModelBundle;
    private ZooModel<float[], float[]> djlModel;
    private Predictor<float[], float[]> predictor;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final boolean useDJL;
    
    public TensorFlowModelWrapper(String modelPath) {
        this.modelPath = modelPath;
        this.modelVersion = extractVersionFromPath(modelPath);
        this.useDJL = shouldUseDJL(modelPath);
        initialize();
    }
    
    private void initialize() {
        try {
            if (useDJL) {
                initializeDJL();
            } else {
                initializeTensorFlowJava();
            }
            initialized.set(true);
            log.info("TensorFlow model initialized successfully: {}", modelPath);
        } catch (Exception e) {
            log.error("Failed to initialize TensorFlow model: {}", modelPath, e);
            throw new RuntimeException("TensorFlow model initialization failed", e);
        }
    }
    
    private void initializeDJL() throws Exception {
        Criteria<float[], float[]> criteria = Criteria.builder()
                .setTypes(float[].class, float[].class)
                .optModelPath(new File(modelPath).toPath())
                .optTranslator(new TensorFlowTranslator())
                .optEngine("TensorFlow")
                .build();
        
        djlModel = ModelZoo.loadModel(criteria);
        predictor = djlModel.newPredictor();
        
        log.debug("DJL TensorFlow model loaded from: {}", modelPath);
    }
    
    private void initializeTensorFlowJava() throws Exception {
        savedModelBundle = SavedModelBundle.load(modelPath, "serve");
        log.debug("TensorFlow Java model loaded from: {}", modelPath);
    }
    
    @Override
    public double[] predict(double[] features) {
        if (!initialized.get()) {
            throw new IllegalStateException("Model not initialized");
        }
        
        try {
            if (useDJL) {
                return predictWithDJL(features);
            } else {
                return predictWithTensorFlowJava(features);
            }
        } catch (Exception e) {
            log.error("Prediction failed for TensorFlow model: {}", modelPath, e);
            throw new RuntimeException("TensorFlow prediction failed", e);
        }
    }
    
    private double[] predictWithDJL(double[] features) throws Exception {
        float[] floatFeatures = doubleToFloat(features);
        float[] predictions = predictor.predict(floatFeatures);
        return floatToDouble(predictions);
    }
    
    private double[] predictWithTensorFlowJava(features) throws Exception {
        try (Session session = savedModelBundle.session()) {
            // Create input tensor
            float[] floatFeatures = doubleToFloat(features);
            
            try (Tensor<Float> inputTensor = Tensor.create(
                    new long[]{1, features.length}, 
                    FloatBuffer.wrap(floatFeatures))) {
                
                // Run inference
                Session.Runner runner = session.runner()
                        .feed("input", inputTensor)
                        .fetch("output");
                
                try (Tensor<?> result = runner.run().get(0)) {
                    // Extract results
                    float[][] output = new float[1][(int) result.shape()[1]];
                    result.copyTo(output);
                    
                    return floatToDouble(output[0]);
                }
            }
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
        return "tensorflow";
    }
    
    private boolean shouldUseDJL(String modelPath) {
        // Use DJL for newer TensorFlow models, TensorFlow Java for older ones
        // This can be determined by model format or configuration
        File modelFile = new File(modelPath);
        return modelFile.isDirectory() && new File(modelFile, "saved_model.pb").exists();
    }
    
    private String extractVersionFromPath(String path) {
        // Extract version from model path if available
        if (path.contains("_v")) {
            String[] parts = path.split("_v");
            if (parts.length > 1) {
                return "v" + parts[1].replaceAll("[^0-9.]", "");
            }
        }
        return "v1.0.0";
    }
    
    private float[] doubleToFloat(double[] input) {
        float[] output = new float[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = (float) input[i];
        }
        return output;
    }
    
    private double[] floatToDouble(float[] input) {
        double[] output = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }
    
    public void close() {
        try {
            if (predictor != null) {
                predictor.close();
            }
            if (djlModel != null) {
                djlModel.close();
            }
            if (savedModelBundle != null) {
                savedModelBundle.close();
            }
        } catch (Exception e) {
            log.warn("Error closing TensorFlow model resources", e);
        }
    }
    
    /**
     * Custom translator for DJL TensorFlow models
     */
    private static class TensorFlowTranslator implements Translator<float[], float[]> {
        
        @Override
        public NDList processInput(TranslatorContext ctx, float[] input) {
            NDManager manager = ctx.getNDManager();
            NDArray array = manager.create(input).reshape(1, -1);
            return new NDList(array);
        }
        
        @Override
        public float[] processOutput(TranslatorContext ctx, NDList list) {
            NDArray output = list.singletonOrThrow();
            return output.toFloatArray();
        }
    }
    
    public static boolean isTensorFlowAvailable() {
        try {
            // Test TensorFlow availability
            String version = TensorFlow.version();
            log.debug("TensorFlow version: {}", version);
            return true;
        } catch (Exception e) {
            log.warn("TensorFlow not available: {}", e.getMessage());
            return false;
        }
    }
}