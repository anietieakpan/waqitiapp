package com.waqiti.analytics.ml;

import ai.onnxruntime.*;
import lombok.extern.slf4j.Slf4j;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Tensor;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;

import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Production-ready ML Model Loader
 * Supports ONNX, TensorFlow, and DL4J models
 */
@Slf4j
public class ModelLoader {
    
    private static final OrtEnvironment ortEnv = OrtEnvironment.getEnvironment();
    
    /**
     * Load ONNX model (for scikit-learn and other Python models converted to ONNX)
     */
    public static class ONNXModel implements AutoCloseable {
        private final OrtSession session;
        private final Map<String, NodeInfo> inputInfo;
        private final Map<String, NodeInfo> outputInfo;
        
        public ONNXModel(File modelFile) throws OrtException {
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            
            this.session = ortEnv.createSession(modelFile.getAbsolutePath(), options);
            this.inputInfo = session.getInputInfo();
            this.outputInfo = session.getOutputInfo();
            
            log.info("Loaded ONNX model from: {} with {} inputs and {} outputs", 
                modelFile.getName(), inputInfo.size(), outputInfo.size());
        }
        
        public float[] predict(float[] input) throws OrtException {
            String inputName = inputInfo.keySet().iterator().next();
            long[] shape = ((TensorInfo) inputInfo.get(inputName).getInfo()).getShape();
            
            try (OnnxTensor inputTensor = OnnxTensor.createTensor(ortEnv, 
                    FloatBuffer.wrap(input), shape)) {
                
                Map<String, OnnxTensor> inputs = new HashMap<>();
                inputs.put(inputName, inputTensor);
                
                try (OrtSession.Result result = session.run(inputs)) {
                    String outputName = outputInfo.keySet().iterator().next();
                    float[][] output = (float[][]) result.get(outputName).get().getValue();
                    return output[0];
                }
            }
        }
        
        public Map<String, Float> predictWithProbabilities(float[] input) throws OrtException {
            float[] predictions = predict(input);
            Map<String, Float> probabilities = new HashMap<>();
            
            if (predictions.length == 1) {
                // Binary classification
                probabilities.put("positive", predictions[0]);
                probabilities.put("negative", 1 - predictions[0]);
            } else {
                // Multi-class classification
                for (int i = 0; i < predictions.length; i++) {
                    probabilities.put("class_" + i, predictions[i]);
                }
            }
            
            return probabilities;
        }
        
        @Override
        public void close() throws Exception {
            if (session != null) {
                session.close();
            }
        }
    }
    
    /**
     * Load TensorFlow SavedModel
     */
    public static class TensorFlowModel implements AutoCloseable {
        private final SavedModelBundle model;
        private final Session session;
        private final String inputOpName;
        private final String outputOpName;
        
        public TensorFlowModel(File modelDir) {
            this(modelDir, "serving_default", "input", "output");
        }
        
        public TensorFlowModel(File modelDir, String tagSet, String inputOp, String outputOp) {
            this.model = SavedModelBundle.load(modelDir.getAbsolutePath(), tagSet);
            this.session = model.session();
            this.inputOpName = inputOp;
            this.outputOpName = outputOp;
            
            log.info("Loaded TensorFlow model from: {} with tag: {}", modelDir.getName(), tagSet);
        }
        
        public float[] predict(float[][] input) {
            try (Tensor<?> inputTensor = Tensor.create(input)) {
                try (Tensor<?> result = session.runner()
                        .feed(inputOpName, inputTensor)
                        .fetch(outputOpName)
                        .run()
                        .get(0)) {
                    
                    float[][] output = new float[1][(int) result.shape()[1]];
                    result.copyTo(output);
                    return output[0];
                }
            }
        }
        
        public Map<String, Object> predictWithMetadata(float[][] input) {
            Map<String, Object> metadata = new HashMap<>();
            
            long startTime = System.currentTimeMillis();
            float[] predictions = predict(input);
            long inferenceTime = System.currentTimeMillis() - startTime;
            
            metadata.put("predictions", predictions);
            metadata.put("inference_time_ms", inferenceTime);
            metadata.put("model_type", "tensorflow");
            metadata.put("input_shape", new int[]{input.length, input[0].length});
            
            return metadata;
        }
        
        @Override
        public void close() {
            if (session != null) {
                session.close();
            }
            if (model != null) {
                model.close();
            }
        }
    }
    
    /**
     * Load DeepLearning4J model (native Java deep learning)
     */
    public static class DL4JModel implements AutoCloseable {
        private final MultiLayerNetwork network;
        
        public DL4JModel(File modelFile) throws IOException {
            this.network = ModelSerializer.restoreMultiLayerNetwork(modelFile);
            network.init();
            
            log.info("Loaded DL4J model from: {} with {} layers", 
                modelFile.getName(), network.getnLayers());
        }
        
        public float[] predict(float[] input) {
            org.nd4j.linalg.api.ndarray.INDArray inputArray = 
                org.nd4j.linalg.factory.Nd4j.create(input);
            
            org.nd4j.linalg.api.ndarray.INDArray output = network.output(inputArray);
            
            float[] result = new float[output.columns()];
            for (int i = 0; i < result.length; i++) {
                result[i] = output.getFloat(i);
            }
            
            return result;
        }
        
        public double getScore() {
            return network.score();
        }
        
        @Override
        public void close() {
            // DL4J models don't need explicit closing
        }
    }
    
    /**
     * Factory method to load appropriate model based on file extension
     */
    public static Object loadModel(File modelFile) throws Exception {
        String fileName = modelFile.getName().toLowerCase();
        
        if (fileName.endsWith(".onnx")) {
            return new ONNXModel(modelFile);
        } else if (fileName.endsWith(".pb") || modelFile.isDirectory()) {
            return new TensorFlowModel(modelFile);
        } else if (fileName.endsWith(".zip") || fileName.endsWith(".bin")) {
            return new DL4JModel(modelFile);
        } else {
            throw new IllegalArgumentException("Unsupported model format: " + fileName);
        }
    }
}