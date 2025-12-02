package com.waqiti.common.saga;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;
import java.util.Set;

/**
 * Registry for managing saga definitions and metadata.
 * Provides centralized access to saga configurations and execution templates.
 */
@Component
@Slf4j
public class SagaRegistry {

    private final Map<String, SagaDefinition> sagaDefinitions = new ConcurrentHashMap<>();
    private final Map<String, SagaMetadata> sagaMetadata = new ConcurrentHashMap<>();

    /**
     * Register a saga definition
     */
    public void registerDefinition(String sagaType, SagaDefinition definition) {
        if (sagaType == null || definition == null) {
            throw new IllegalArgumentException("Saga type and definition cannot be null");
        }
        
        log.info("Registering saga definition: type={}, steps={}", 
            sagaType, definition.getSteps().size());
        
        sagaDefinitions.put(sagaType, definition);
        
        // Store metadata
        SagaMetadata metadata = SagaMetadata.builder()
            .sagaType(sagaType)
            .stepCount(definition.getSteps().size())
            .timeoutMinutes(definition.getTimeoutMinutes())
            .parallelExecution(definition.isParallelExecution())
            .maxRetries(definition.getMaxRetries())
            .build();
            
        sagaMetadata.put(sagaType, metadata);
    }

    /**
     * Get saga definition by type
     */
    public SagaDefinition getDefinition(String sagaType) {
        return sagaDefinitions.get(sagaType);
    }

    /**
     * Check if saga type is registered
     */
    public boolean isRegistered(String sagaType) {
        return sagaDefinitions.containsKey(sagaType);
    }

    /**
     * Get all registered saga types
     */
    public Set<String> getRegisteredTypes() {
        return sagaDefinitions.keySet();
    }

    /**
     * Get saga metadata
     */
    public Optional<SagaMetadata> getMetadata(String sagaType) {
        return Optional.ofNullable(sagaMetadata.get(sagaType));
    }

    /**
     * Remove saga definition
     */
    public void unregisterDefinition(String sagaType) {
        log.info("Unregistering saga definition: type={}", sagaType);
        sagaDefinitions.remove(sagaType);
        sagaMetadata.remove(sagaType);
    }

    /**
     * Clear all registered definitions
     */
    public void clear() {
        log.info("Clearing all saga definitions: count={}", sagaDefinitions.size());
        sagaDefinitions.clear();
        sagaMetadata.clear();
    }

    /**
     * Get registry statistics
     */
    public RegistryStats getStats() {
        return RegistryStats.builder()
            .totalDefinitions(sagaDefinitions.size())
            .registeredTypes(sagaDefinitions.keySet())
            .build();
    }

    /**
     * Saga metadata for tracking
     */
    @lombok.Builder
    @lombok.Data
    public static class SagaMetadata {
        private String sagaType;
        private int stepCount;
        private int timeoutMinutes;
        private boolean parallelExecution;
        private int maxRetries;
    }

    /**
     * Registry statistics
     */
    @lombok.Builder
    @lombok.Data
    public static class RegistryStats {
        private int totalDefinitions;
        private Set<String> registeredTypes;
    }
}