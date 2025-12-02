package com.waqiti.config.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator for Config Server
 * Checks if the configuration repository is accessible
 */
@Component
public class ConfigServerHealthIndicator implements HealthIndicator {

    private final EnvironmentRepository environmentRepository;
    
    public ConfigServerHealthIndicator(EnvironmentRepository environmentRepository) {
        this.environmentRepository = environmentRepository;
    }

    @Override
    public Health health() {
        try {
            // Try to fetch configuration for a test application
            environmentRepository.findOne("health-check", "default", "master");
            
            return Health.up()
                .withDetail("repository", "accessible")
                .withDetail("status", "Configuration server is healthy")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("repository", "not accessible")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}