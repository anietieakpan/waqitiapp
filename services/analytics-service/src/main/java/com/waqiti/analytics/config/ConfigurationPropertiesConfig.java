package com.waqiti.analytics.config;

import com.waqiti.analytics.config.properties.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration Properties Enablement
 *
 * <p>Enables all @ConfigurationProperties classes for the Analytics Service.
 * This configuration class ensures all custom properties from application.yml
 * are properly bound to their corresponding Java configuration classes with
 * full validation and type safety.
 *
 * <p>Enabled Configuration Classes:
 * <ul>
 *   <li>{@link AnalyticsProperties} - Core analytics configuration (analytics.*)</li>
 *   <li>{@link InfluxDBProperties} - InfluxDB time-series database (influxdb.*)</li>
 *   <li>{@link SparkProperties} - Apache Spark distributed computing (spark.*)</li>
 *   <li>{@link HealthProperties} - Health check and resilience patterns (health.*)</li>
 *   <li>{@link FeatureFlagProperties} - Feature flags and toggles (feature.flags.*)</li>
 * </ul>
 *
 * <p>Benefits:
 * <ul>
 *   <li>Compile-time type safety for all configuration properties</li>
 *   <li>Runtime validation with Bean Validation annotations</li>
 *   <li>IDE autocomplete support via Spring Boot Configuration Processor</li>
 *   <li>Centralized configuration documentation</li>
 *   <li>Easy testing with property-based configuration</li>
 * </ul>
 *
 * @author Waqiti Analytics Team
 * @since 1.0.0
 * @version 1.0
 */
@Configuration
@EnableConfigurationProperties({
    AnalyticsProperties.class,
    InfluxDBProperties.class,
    SparkProperties.class,
    HealthProperties.class,
    FeatureFlagProperties.class
})
@Slf4j
public class ConfigurationPropertiesConfig {

    public ConfigurationPropertiesConfig() {
        log.info("Initializing Analytics Service Configuration Properties");
        log.info("Enabled configuration classes: AnalyticsProperties, InfluxDBProperties, " +
                "SparkProperties, HealthProperties, FeatureFlagProperties");
    }
}
