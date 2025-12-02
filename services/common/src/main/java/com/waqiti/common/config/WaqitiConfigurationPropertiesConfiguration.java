package com.waqiti.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class to enable all Waqiti configuration properties
 * 
 * This class ensures all configuration property classes are properly
 * registered and available for dependency injection across all services.
 * 
 * Resolves Qodana "Cannot resolve configuration property" issues by
 * explicitly enabling all property classes.
 * 
 * @author Waqiti Platform Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Configuration
@EnableConfigurationProperties({
    // Production Properties
    WaqitiProductionProperties.FraudDetectionProperties.class,
    WaqitiProductionProperties.FraudAlertProperties.class,
    WaqitiProductionProperties.GeolocationProperties.class,
    WaqitiProductionProperties.MonitoringProperties.class,
    WaqitiProductionProperties.EncryptionProperties.class,
    WaqitiProductionProperties.RateLimitingProperties.class,
    WaqitiProductionProperties.CacheProperties.class,
    WaqitiProductionProperties.SpringManagementProperties.class,
    WaqitiProductionProperties.SpringKafkaProperties.class,
    WaqitiProductionProperties.Resilience4jProperties.class,
    WaqitiProductionProperties.LoggingProperties.class
})
public class WaqitiConfigurationPropertiesConfiguration {
    
    // This class serves as a central registry for all configuration properties
    // No additional implementation needed - the @EnableConfigurationProperties
    // annotation handles the registration automatically
}