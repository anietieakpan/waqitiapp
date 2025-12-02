package com.waqiti.ml.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka topics configuration properties
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "kafka.topics")
public class KafkaTopicsProperties {
    
    private String fraudDetection = "fraud-detection-events";
    private String riskScoring = "risk-scoring-events";
    private String modelInference = "model-inference-events";
    private String transactionAnalysis = "transaction-analysis-events";
    private String modelTraining = "model-training-events";
    private String notification = "notification-events";
}