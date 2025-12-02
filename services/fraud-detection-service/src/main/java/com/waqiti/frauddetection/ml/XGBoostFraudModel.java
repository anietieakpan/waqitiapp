package com.waqiti.frauddetection.ml;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * XGBoost Fraud Detection Model
 *
 * Primary ML model for fraud detection using XGBoost gradient boosting.
 * Model is exported to ONNX format and loaded via ONNX Runtime for
 * high-performance inference.
 *
 * PRODUCTION-GRADE IMPLEMENTATION
 * - ONNX Runtime for optimized inference
 * - Feature engineering with 50+ features
 * - Automatic model loading on startup
 * - Thread-safe prediction
 * - Graceful error handling with fallback
 *
 * MODEL FEATURES (50+):
 * - Transaction amount, hour, day, month
 * - Customer profile metrics (avg amount, tx count, fraud rate)
 * - Merchant profile metrics (chargeback rate, refund rate)
 * - Device metrics (user count, fraud rate)
 * - Location metrics (VPN flag, high-risk country)
 * - Velocity metrics (tx per hour, spending per hour)
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0 - Production Implementation
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class XGBoostFraudModel implements IFraudMLModel {

    private final MLModelLoader modelLoader;

    @Value("${fraud.ml.xgboost.model-path:models/xgboost_fraud_model.onnx}")
    private String modelPath;

    @Value("${fraud.ml.xgboost.enabled:true}")
    private boolean enabled;

    private OrtSession session;
    private volatile boolean modelLoaded = false;

    /**
     * Initialize model on startup
     */
    @PostConstruct
    public void initialize() {
        if (!enabled) {
            log.warn("XGBoost model is disabled via configuration");
            return;
        }

        try {
            log.info("Initializing XGBoost fraud model from: {}", modelPath);

            // Check if model file exists
            if (!modelLoader.modelExists(modelPath)) {
                log.error("XGBoost model file not found: {}", modelPath);
                log.error("Model will not be available. Please provide trained model at: {}", modelPath);
                return;
            }

            // Load model
            this.session = modelLoader.loadONNXModel(modelPath);

            // Validate model structure
            if (!modelLoader.validateONNXModel(session, 50)) {
                log.error("XGBoost model validation failed");
                modelLoader.closeONNXSession(session);
                this.session = null;
                return;
            }

            this.modelLoaded = true;
            log.info("XGBoost model loaded successfully (size: {:.2f} MB)",
                modelLoader.getModelSizeMB(modelPath));

        } catch (Exception e) {
            log.error("Failed to initialize XGBoost model", e);
            this.modelLoaded = false;
        }
    }

    /**
     * Predict fraud probability
     *
     * @param features Feature map with 50+ engineered features
     * @return Fraud probability (0.0 - 1.0)
     * @throws Exception if prediction fails
     */
    @Override
    public double predict(Map<String, Object> features) throws Exception {
        if (!modelLoaded || session == null) {
            throw new IllegalStateException("XGBoost model not loaded");
        }

        try {
            // Extract features to float array (order matters!)
            float[] featureVector = extractFeatures(features);

            // Create ONNX tensor
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            long[] shape = {1, featureVector.length}; // [batch_size, feature_count]

            OnnxTensor tensor = OnnxTensor.createTensor(env,
                FloatBuffer.wrap(featureVector), shape);

            // Run inference
            String inputName = session.getInputNames().iterator().next();
            Map<String, OnnxTensor> inputs = Map.of(inputName, tensor);

            OrtSession.Result result = session.run(inputs);

            // Extract fraud probability
            // XGBoost binary classification output: [[prob_class_0, prob_class_1]]
            float[][] predictions = (float[][]) result.get(0).getValue();
            double fraudProbability = predictions[0][1]; // Probability of fraud class

            // Cleanup
            tensor.close();
            result.close();

            log.debug("XGBoost prediction: {}", fraudProbability);
            return fraudProbability;

        } catch (Exception e) {
            log.error("XGBoost prediction failed", e);
            throw new Exception("XGBoost prediction error: " + e.getMessage(), e);
        }
    }

    /**
     * Extract features from feature map to ordered float array
     *
     * CRITICAL: Feature order must match training data order
     */
    private float[] extractFeatures(Map<String, Object> features) {
        List<Float> featureList = new ArrayList<>();

        // Transaction features (10 features)
        featureList.add(toFloat(features.get("amount")));
        featureList.add(toFloat(features.get("hour_of_day")));
        featureList.add(toFloat(features.get("day_of_week")));
        featureList.add(toFloat(features.get("day_of_month")));
        featureList.add(toFloat(features.get("month")));
        featureList.add(toFloat(features.get("is_weekend")));
        featureList.add(toFloat(features.get("is_night_time")));
        featureList.add(toFloat(features.get("amount_log")));
        featureList.add(toFloat(features.get("amount_squared")));
        featureList.add(toFloat(features.get("amount_sqrt")));

        // Customer profile features (10 features)
        featureList.add(toFloat(features.get("customer_total_transactions")));
        featureList.add(toFloat(features.get("customer_avg_amount")));
        featureList.add(toFloat(features.get("customer_max_amount")));
        featureList.add(toFloat(features.get("customer_fraud_count")));
        featureList.add(toFloat(features.get("customer_fraud_rate")));
        featureList.add(toFloat(features.get("customer_avg_risk_score")));
        featureList.add(toFloat(features.get("customer_account_age_days")));
        featureList.add(toFloat(features.get("customer_is_new")));
        featureList.add(toFloat(features.get("customer_kyc_status")));
        featureList.add(toFloat(features.get("customer_country_count")));

        // Merchant profile features (10 features)
        featureList.add(toFloat(features.get("merchant_total_transactions")));
        featureList.add(toFloat(features.get("merchant_avg_amount")));
        featureList.add(toFloat(features.get("merchant_chargeback_rate")));
        featureList.add(toFloat(features.get("merchant_refund_rate")));
        featureList.add(toFloat(features.get("merchant_fraud_rate")));
        featureList.add(toFloat(features.get("merchant_avg_risk_score")));
        featureList.add(toFloat(features.get("merchant_category_code")));
        featureList.add(toFloat(features.get("merchant_is_high_risk_mcc")));
        featureList.add(toFloat(features.get("merchant_enhanced_monitoring")));
        featureList.add(toFloat(features.get("merchant_age_days")));

        // Device profile features (8 features)
        featureList.add(toFloat(features.get("device_total_transactions")));
        featureList.add(toFloat(features.get("device_associated_users")));
        featureList.add(toFloat(features.get("device_fraud_rate")));
        featureList.add(toFloat(features.get("device_avg_risk_score")));
        featureList.add(toFloat(features.get("device_is_vpn")));
        featureList.add(toFloat(features.get("device_impossible_travel")));
        featureList.add(toFloat(features.get("device_country_count")));
        featureList.add(toFloat(features.get("device_age_days")));

        // Location features (7 features)
        featureList.add(toFloat(features.get("location_is_vpn_or_proxy")));
        featureList.add(toFloat(features.get("location_is_high_risk_country")));
        featureList.add(toFloat(features.get("location_fraud_rate")));
        featureList.add(toFloat(features.get("location_total_transactions")));
        featureList.add(toFloat(features.get("location_associated_users")));
        featureList.add(toFloat(features.get("location_avg_risk_score")));
        featureList.add(toFloat(features.get("location_is_blacklisted")));

        // Velocity features (5 features)
        featureList.add(toFloat(features.get("velocity_tx_per_hour")));
        featureList.add(toFloat(features.get("velocity_tx_per_day")));
        featureList.add(toFloat(features.get("velocity_amount_per_hour")));
        featureList.add(toFloat(features.get("velocity_amount_per_day")));
        featureList.add(toFloat(features.get("velocity_failed_count")));

        // Total: 50 features
        float[] result = new float[featureList.size()];
        for (int i = 0; i < featureList.size(); i++) {
            result[i] = featureList.get(i);
        }

        return result;
    }

    /**
     * Convert feature value to float (handles multiple types)
     */
    private float toFloat(Object value) {
        if (value == null) {
            return 0.0f;
        }

        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }

        if (value instanceof Boolean) {
            return ((Boolean) value) ? 1.0f : 0.0f;
        }

        if (value instanceof String) {
            try {
                return Float.parseFloat((String) value);
            } catch (NumberFormatException e) {
                return 0.0f;
            }
        }

        return 0.0f;
    }

    /**
     * Check if model is loaded and ready
     */
    @Override
    public boolean isReady() {
        return modelLoaded && session != null;
    }

    /**
     * Get model name
     */
    @Override
    public String getModelName() {
        return "XGBoost";
    }

    /**
     * Cleanup on shutdown
     */
    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up XGBoost model resources");
        if (session != null) {
            modelLoader.closeONNXSession(session);
            session = null;
        }
        modelLoaded = false;
    }
}
