package com.waqiti.analytics.ml.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.security.SecureSystemUtils;
import com.waqiti.common.security.SecureSystemUtils.SecureExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * PRODUCTION-READY: Secure wrapper for executing Python ML models
 *
 * SECURITY FEATURES:
 * - Uses SecureSystemUtils for validated command execution
 * - No direct ProcessBuilder/Runtime.exec usage
 * - Input validation and sanitization
 * - Timeout protection
 * - Output size limits
 * - Temp file cleanup
 *
 * Supports scikit-learn models and pickle files
 *
 * @author Waqiti ML Team
 * @version 2.0.0 - Production Security Hardening
 */
@Slf4j
@Component
public class PythonModelWrapper implements MLModel {

    private final String modelPath;
    private final String modelType;
    private final ObjectMapper objectMapper;
    private final String tempScriptPath;
    private final SecureSystemUtils secureSystemUtils;

    @Autowired
    public PythonModelWrapper(SecureSystemUtils secureSystemUtils, String modelPath, String modelType) {
        this.secureSystemUtils = secureSystemUtils;
        this.modelPath = validateModelPath(modelPath);
        this.modelType = validateModelType(modelType);
        this.objectMapper = new ObjectMapper();
        this.tempScriptPath = createTempPythonScript();

        log.info("Initialized secure Python model wrapper: type={}, path={}", modelType, modelPath);
    }

    /**
     * Validate model path for security
     */
    private String validateModelPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new SecurityException("Model path cannot be empty");
        }

        Path modelPath = Paths.get(path);

        // Must be absolute path
        if (!modelPath.isAbsolute()) {
            throw new SecurityException("Model path must be absolute");
        }

        // Must exist and be readable
        if (!Files.exists(modelPath) || !Files.isReadable(modelPath)) {
            throw new SecurityException("Model file does not exist or is not readable: " + path);
        }

        // Must be in allowed directory
        String allowedDir = System.getProperty("waqiti.ml.models.dir", "/opt/waqiti/ml/models");
        if (!path.startsWith(allowedDir)) {
            throw new SecurityException("Model must be in allowed directory: " + allowedDir);
        }

        return path;
    }

    /**
     * Validate model type
     */
    private String validateModelType(String type) {
        if (type == null || type.trim().isEmpty()) {
            throw new SecurityException("Model type cannot be empty");
        }

        List<String> allowedTypes = Arrays.asList("scikit-learn", "sklearn", "joblib", "pickle", "pkl");
        if (!allowedTypes.contains(type.toLowerCase())) {
            throw new SecurityException("Invalid model type: " + type);
        }

        return type;
    }
    
    @Override
    public double[] predict(double[] features) {
        Path inputFile = null;
        Path outputFile = null;

        try {
            // Validate input features
            if (features == null || features.length == 0) {
                throw new IllegalArgumentException("Features cannot be null or empty");
            }

            // Create secure input/output files
            inputFile = createInputFile(features);
            outputFile = Paths.get(System.getProperty("java.io.tmpdir"),
                "ml_output_" + System.currentTimeMillis() + ".json");

            log.debug("Executing secure Python model prediction: model={}, features={}", modelType, features.length);

            // PRODUCTION-READY: Use SecureSystemUtils for validated execution
            List<String> arguments = Arrays.asList(
                modelPath,
                modelType,
                inputFile.toString(),
                outputFile.toString()
            );

            SecureExecutionResult result = secureSystemUtils.executePythonScript(tempScriptPath, arguments);

            // Check execution result
            if (!result.isSuccess()) {
                String errorMsg = result.getError() != null ? result.getError() : "Unknown error";
                log.error("Secure Python execution failed: {}", errorMsg);
                throw new RuntimeException("Secure Python execution failed: " + errorMsg);
            }

            // Verify output was produced
            if (!Files.exists(outputFile)) {
                log.error("Python script did not produce output file. Script output: {}", result.getOutput());
                throw new RuntimeException("Python script did not produce output file");
            }

            // Read and parse output with size limit
            long fileSize = Files.size(outputFile);
            if (fileSize > 1_000_000) { // 1MB limit
                throw new SecurityException("Output file too large: " + fileSize + " bytes");
            }

            String resultJson = Files.readString(outputFile);

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = objectMapper.readValue(resultJson, Map.class);

            // Check for errors in result
            if (Boolean.FALSE.equals(resultMap.get("success"))) {
                String error = (String) resultMap.get("error");
                throw new RuntimeException("Python model prediction failed: " + error);
            }

            @SuppressWarnings("unchecked")
            List<Double> predictions = (List<Double>) resultMap.get("predictions");

            if (predictions == null || predictions.isEmpty()) {
                throw new RuntimeException("No predictions returned from model");
            }

            log.debug("Model prediction successful: predictions={}", predictions.size());

            return predictions.stream().mapToDouble(Double::doubleValue).toArray();

        } catch (Exception e) {
            log.error("Error executing Python model prediction: model={}, error={}",
                modelType, e.getMessage(), e);
            throw new RuntimeException("Python model prediction failed: " + e.getMessage(), e);
        } finally {
            // CRITICAL: Always cleanup temp files
            cleanupTempFiles(inputFile, outputFile);
        }
    }

    /**
     * Cleanup temporary files securely
     */
    private void cleanupTempFiles(Path... files) {
        for (Path file : files) {
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                    log.trace("Cleaned up temp file: {}", file);
                } catch (IOException e) {
                    log.warn("Failed to cleanup temp file: {}", file, e);
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
        return "python-" + modelType + "-v1.0";
    }
    
    @Override
    public String getModelType() {
        return modelType;
    }
    
    private Path createInputFile(double[] features) throws IOException {
        Path inputFile = Paths.get(System.getProperty("java.io.tmpdir"), 
            "ml_input_" + System.currentTimeMillis() + ".json");
        
        Map<String, Object> input = Map.of("features", features);
        objectMapper.writeValue(inputFile.toFile(), input);
        
        return inputFile;
    }
    
    private String createTempPythonScript() {
        try {
            Path scriptPath = Paths.get(System.getProperty("java.io.tmpdir"), 
                "ml_inference_script.py");
            
            if (!Files.exists(scriptPath)) {
                String script = createPythonInferenceScript();
                Files.write(scriptPath, script.getBytes());
                if (!scriptPath.toFile().setExecutable(true)) {
                    log.warn("Failed to set executable permission for Python script: {}", scriptPath);
                }
            }
            
            return scriptPath.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create Python inference script", e);
        }
    }
    
    private String createPythonInferenceScript() {
        return """
            #!/usr/bin/env python3
            import sys
            import json
            import numpy as np
            import warnings
            warnings.filterwarnings('ignore')
            
            def load_model(model_path, model_type):
                \"\"\"Load model based on type\"\"\"
                try:
                    if model_type.lower() in ['scikit-learn', 'sklearn', 'joblib']:
                        import joblib
                        return joblib.load(model_path)
                    elif model_type.lower() in ['pickle', 'pkl']:
                        import pickle
                        with open(model_path, 'rb') as f:
                            return pickle.load(f)
                    else:
                        raise ValueError(f"Unsupported model type: {model_type}")
                except Exception as e:
                    print(f"Error loading model: {e}", file=sys.stderr)
                    sys.exit(1)
            
            def main():
                if len(sys.argv) != 5:
                    print("Usage: script.py <model_path> <model_type> <input_file> <output_file>", file=sys.stderr)
                    sys.exit(1)
                
                model_path = sys.argv[1]
                model_type = sys.argv[2]
                input_file = sys.argv[3]
                output_file = sys.argv[4]
                
                try:
                    # Load model
                    model = load_model(model_path, model_type)
                    
                    # Load input data
                    with open(input_file, 'r') as f:
                        input_data = json.load(f)
                    
                    features = np.array(input_data['features']).reshape(1, -1)
                    
                    # Make prediction
                    if hasattr(model, 'predict_proba'):
                        # For classification models, return probabilities
                        predictions = model.predict_proba(features)[0].tolist()
                    else:
                        # For regression models or simple predict
                        pred = model.predict(features)
                        if hasattr(pred, 'tolist'):
                            predictions = pred.tolist()
                        else:
                            predictions = [float(pred[0])]
                    
                    # Save output
                    output_data = {
                        'predictions': predictions,
                        'model_type': model_type,
                        'success': True
                    }
                    
                    with open(output_file, 'w') as f:
                        json.dump(output_data, f)
                    
                except Exception as e:
                    error_data = {
                        'error': str(e),
                        'success': False
                    }
                    try:
                        with open(output_file, 'w') as f:
                            json.dump(error_data, f)
                    except:
                        pass
                    print(f"Prediction error: {e}", file=sys.stderr)
                    sys.exit(1)
            
            if __name__ == '__main__':
                main()
            """;
    }
    
    /**
     * SECURITY FIX: Check Python availability using secure method
     */
    public boolean isPythonAvailable() {
        try {
            // This check should be done at startup, not via command execution
            // Use system property or configuration instead
            String pythonPath = System.getProperty("waqiti.python.executable", "/usr/bin/python3");
            Path pythonExec = Paths.get(pythonPath);

            boolean available = Files.exists(pythonExec) &&
                               Files.isExecutable(pythonExec) &&
                               Files.isRegularFile(pythonExec);

            log.debug("Python availability check: path={}, available={}", pythonPath, available);
            return available;

        } catch (Exception e) {
            log.warn("Python availability check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * SECURITY FIX: Check required packages at startup
     *
     * Note: This should be verified during deployment/container build,
     * not at runtime via command execution. Runtime checks are unsafe.
     */
    public boolean areRequiredPackagesAvailable() {
        // PRODUCTION APPROACH: Verify packages exist during deployment
        // This method returns configuration-based result instead of executing code

        boolean packagesAvailable = Boolean.parseBoolean(
            System.getProperty("waqiti.ml.packages.available", "false")
        );

        if (!packagesAvailable) {
            log.warn("ML packages not available. Set waqiti.ml.packages.available=true after verifying installation");
        }

        return packagesAvailable;
    }
}