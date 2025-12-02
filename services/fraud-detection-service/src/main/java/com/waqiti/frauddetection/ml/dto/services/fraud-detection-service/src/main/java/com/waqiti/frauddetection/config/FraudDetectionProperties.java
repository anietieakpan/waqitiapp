package com.waqiti.frauddetection.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "fraud-detection")
@Data
public class FraudDetectionProperties {
    private double scoreThreshold = 0.7;
    private boolean enableMLModels = true;
    private int maxRiskScore = 100;
}
