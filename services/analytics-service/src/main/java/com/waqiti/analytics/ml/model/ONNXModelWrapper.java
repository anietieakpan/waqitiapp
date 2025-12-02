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
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wrapper for ONNX models using both ONNX Runtime and DJL
 */
@Slf4j
public class ONNXModelWrapper implements MLModel {
    
    private final String modelPath;
    private final String modelVersion;
    private OrtEnvironment ortEnvironment;
    private OrtSession ortSession;
    private ZooModel<float[], float[]> djlModel;
    private Predictor<float[], float[]> predictor;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final boolean useDJL;
    
    public ONNXModelWrapper(String modelPath) {
        this.modelPath = modelPath;
        this.modelVersion = extractVersionFromPath(modelPath);
        this.useDJL = shouldUseDJL();
        initialize();
    }
    
    private void initialize() {
        try {
            if (useDJL) {
                initializeDJL();
            } else {
                initializeONNXRuntime();
            }
            initialized.set(true);
            log.info("ONNX model initialized successfully: {}", modelPath);
        } catch (Exception e) {
            log.error("Failed to initialize ONNX model: {}", modelPath, e);
            throw new RuntimeException("ONNX model initialization failed", e);
        }
    }
    
    private void initializeDJL() throws Exception {
        Criteria<float[], float[]> criteria = Criteria.builder()
                .setTypes(float[].class, float[].class)
                .optModelPath(new File(modelPath).toPath())
                .optTranslator(new ONNXTranslator())
                .optEngine("OnnxRuntime")
                .build();
        
        djlModel = ModelZoo.loadModel(criteria);
        predictor = djlModel.newPredictor();
        
        log.debug("DJL ONNX model loaded from: {}", modelPath);
    }
    
    private void initializeONNXRuntime() throws Exception {
        ortEnvironment = OrtEnvironment.getEnvironment();
        ortSession = ortEnvironment.createSession(modelPath);
        
        log.debug("ONNX Runtime model loaded from: {}", modelPath);
        log.debug("Model input names: {}", ortSession.getInputNames());
        log.debug("Model output names: {}", ortSession.getOutputNames());
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
                return predictWithONNXRuntime(features);
            }
        } catch (Exception e) {
            log.error("Prediction failed for ONNX model: {}", modelPath, e);
            throw new RuntimeException("ONNX prediction failed", e);
        }
    }
    
    private double[] predictWithDJL(double[] features) throws Exception {
        float[] floatFeatures = doubleToFloat(features);
        float[] predictions = predictor.predict(floatFeatures);
        return floatToDouble(predictions);
    }
    
    private double[] predictWithONNXRuntime(double[] features) throws Exception {
        String inputName = ortSession.getInputNames().iterator().next();
        
        // Create input tensor
        float[] floatFeatures = doubleToFloat(features);
        long[] shape = {1, features.length};
        
        try (OnnxTensor inputTensor = OnnxTensor.createTensor(ortEnvironment, 
                FloatBuffer.wrap(floatFeatures), shape)) {
            
            Map<String, OnnxTensor> inputs = Collections.singletonMap(inputName, inputTensor);
            
            // Run inference
            OrtSession.Result result = ortSession.run(inputs);
            
            // Extract output
            OnnxTensor outputTensor = (OnnxTensor) result.get(0);
            float[][] output = (float[][]) outputTensor.getValue();
            
            result.close();
            
            return floatToDouble(output[0]);
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
        return "onnx";
    }
    
    private boolean shouldUseDJL() {
        // For now, prefer ONNX Runtime directly for better performance
        // Can be configured based on model complexity or requirements
        return Boolean.parseBoolean(System.getProperty("onnx.use.djl", "false"));
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
            if (ortSession != null) {
                ortSession.close();
            }
            if (ortEnvironment != null) {
                ortEnvironment.close();
            }
        } catch (Exception e) {
            log.warn("Error closing ONNX model resources", e);
        }
    }
    
    /**
     * Custom translator for DJL ONNX models
     */
    private static class ONNXTranslator implements Translator<float[], float[]> {
        
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
    
    public static boolean isONNXRuntimeAvailable() {
        try {
            // Test ONNX Runtime availability
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            log.debug("ONNX Runtime available, version: {}", env.toString());
            return true;
        } catch (Exception e) {
            log.warn("ONNX Runtime not available: {}", e.getMessage());
            return false;
        }
    }
}