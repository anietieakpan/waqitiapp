package com.waqiti.common.kafka.schema;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.Collection;

/**
 * Health indicator for Schema Registry
 * 
 * Monitors:
 * - Schema Registry connectivity
 * - Number of registered schemas
 * - Schema Registry responsiveness
 */
@Slf4j
@RequiredArgsConstructor
public class SchemaRegistryHealthIndicator implements HealthIndicator {

    private final SchemaRegistryClient schemaRegistryClient;
    private final String schemaRegistryUrl;

    @Override
    public Health health() {
        try {
            // Check if we can connect to schema registry
            Collection<String> subjects = schemaRegistryClient.getAllSubjects();
            
            // Get schema count
            int schemaCount = subjects.size();
            
            log.debug("Schema Registry health check - Subjects: {}", schemaCount);
            
            return Health.up()
                    .withDetail("url", schemaRegistryUrl)
                    .withDetail("status", "Connected")
                    .withDetail("registeredSubjects", schemaCount)
                    .withDetail("subjects", subjects)
                    .build();
                    
        } catch (Exception e) {
            log.error("Schema Registry health check failed", e);
            
            return Health.down()
                    .withDetail("url", schemaRegistryUrl)
                    .withDetail("status", "Disconnected")
                    .withDetail("error", e.getMessage())
                    .withException(e)
                    .build();
        }
    }
}