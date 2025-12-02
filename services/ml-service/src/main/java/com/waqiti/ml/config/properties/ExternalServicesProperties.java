package com.waqiti.ml.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * External services configuration properties
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "external-services")
public class ExternalServicesProperties {
    
    private ServiceConfig fraudDetection = new ServiceConfig();
    private ServiceConfig riskAssessment = new ServiceConfig();
    private ServiceConfig transactionService = new ServiceConfig();
    private ServiceConfig notificationService = new ServiceConfig();
    
    @Data
    public static class ServiceConfig {
        private String url;
        private Duration timeout = Duration.ofSeconds(5);
        private int retryAttempts = 3;
    }
}