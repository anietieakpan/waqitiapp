package com.waqiti.common.saga;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.*;

/**
 * Defines a saga workflow with steps, dependencies, and configuration.
 * Used to orchestrate complex distributed transactions across microservices.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaDefinition {
    
    /**
     * Unique identifier for this saga type
     */
    private String sagaType;
    
    /**
     * Human-readable name for the saga
     */
    private String name;
    
    /**
     * Description of what this saga accomplishes
     */
    private String description;
    
    /**
     * Version of this saga definition
     */
    private String version;
    
    /**
     * Ordered list of steps to execute
     */
    @Builder.Default
    private List<SagaStep> steps = new ArrayList<>();
    
    /**
     * Global timeout for the entire saga (in minutes)
     */
    @Builder.Default
    private int timeoutMinutes = 30;
    
    /**
     * Timeout for individual steps (in minutes)
     */
    @Builder.Default
    private int stepTimeoutMinutes = 5;
    
    /**
     * Maximum number of retry attempts for failed steps
     */
    @Builder.Default
    private int maxRetries = 3;
    
    /**
     * Whether to enable parallel execution of independent steps
     */
    @Builder.Default
    private boolean parallelExecution = true;
    
    /**
     * Compensation strategy for this saga
     */
    @Builder.Default
    private CompensationStrategy compensationStrategy = CompensationStrategy.REVERSE_ORDER;
    
    /**
     * Custom configuration properties
     */
    @Builder.Default
    private Map<String, Object> properties = new HashMap<>();
    
    /**
     * Tags for categorizing and filtering sagas
     */
    @Builder.Default
    private Set<String> tags = new HashSet<>();
    
    /**
     * Add a step to the saga
     */
    public SagaDefinition addStep(SagaStep step) {
        this.steps.add(step);
        return this;
    }
    
    /**
     * Add a step with dependencies
     */
    public SagaDefinition addStep(String stepId, String stepType, String serviceEndpoint, 
                                  Map<String, Object> parameters, String... dependencies) {
        SagaStep step = SagaStep.builder()
            .stepId(stepId)
            .stepType(stepType)
            .serviceEndpoint(serviceEndpoint)
            .parameters(parameters)
            .dependencies(Arrays.asList(dependencies))
            .build();
        
        return addStep(step);
    }
    
    /**
     * Add a compensation step
     */
    public SagaDefinition addCompensationStep(String stepId, String compensationEndpoint, 
                                              Map<String, Object> parameters) {
        // Find the step and add compensation
        steps.stream()
            .filter(step -> step.getStepId().equals(stepId))
            .findFirst()
            .ifPresent(step -> {
                step.setCompensationEndpoint(compensationEndpoint);
                step.setCompensationParameters(parameters);
            });
        
        return this;
    }
    
    /**
     * Set global timeout
     */
    public SagaDefinition withTimeout(int timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
        return this;
    }
    
    /**
     * Set step timeout
     */
    public SagaDefinition withStepTimeout(int stepTimeoutMinutes) {
        this.stepTimeoutMinutes = stepTimeoutMinutes;
        return this;
    }
    
    /**
     * Set retry configuration
     */
    public SagaDefinition withRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }
    
    /**
     * Enable/disable parallel execution
     */
    public SagaDefinition withParallelExecution(boolean enabled) {
        this.parallelExecution = enabled;
        return this;
    }
    
    /**
     * Set compensation strategy
     */
    public SagaDefinition withCompensationStrategy(CompensationStrategy strategy) {
        this.compensationStrategy = strategy;
        return this;
    }
    
    /**
     * Add custom property
     */
    public SagaDefinition withProperty(String key, Object value) {
        this.properties.put(key, value);
        return this;
    }
    
    /**
     * Add tag
     */
    public SagaDefinition withTag(String tag) {
        this.tags.add(tag);
        return this;
    }
    
    /**
     * Validate the saga definition
     */
    public void validate() {
        if (sagaType == null || sagaType.trim().isEmpty()) {
            throw new IllegalArgumentException("Saga type is required");
        }
        
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("At least one step is required");
        }
        
        // Validate step IDs are unique
        Set<String> stepIds = new HashSet<>();
        for (SagaStep step : steps) {
            if (stepIds.contains(step.getStepId())) {
                throw new IllegalArgumentException("Duplicate step ID: " + step.getStepId());
            }
            stepIds.add(step.getStepId());
        }
        
        // Validate dependencies exist
        for (SagaStep step : steps) {
            for (String dependency : step.getDependencies()) {
                if (!stepIds.contains(dependency)) {
                    throw new IllegalArgumentException("Step " + step.getStepId() + 
                        " has unknown dependency: " + dependency);
                }
            }
        }
        
        // Check for circular dependencies
        validateNoCycles();
        
        // Validate timeouts
        if (timeoutMinutes <= 0) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        
        if (stepTimeoutMinutes <= 0) {
            throw new IllegalArgumentException("Step timeout must be positive");
        }
        
        if (stepTimeoutMinutes > timeoutMinutes) {
            throw new IllegalArgumentException("Step timeout cannot exceed saga timeout");
        }
    }
    
    /**
     * Get steps that have no dependencies (can be executed first)
     */
    public List<SagaStep> getRootSteps() {
        return steps.stream()
            .filter(step -> step.getDependencies().isEmpty())
            .collect(ArrayList::new, List::add, List::addAll);
    }
    
    /**
     * Get steps that depend on the given step
     */
    public List<SagaStep> getDependentSteps(String stepId) {
        return steps.stream()
            .filter(step -> step.getDependencies().contains(stepId))
            .collect(ArrayList::new, List::add, List::addAll);
    }
    
    /**
     * Get the maximum depth of the dependency graph
     */
    public int getMaxDepth() {
        Map<String, Integer> depths = new HashMap<>();
        
        for (SagaStep step : steps) {
            calculateDepth(step.getStepId(), depths);
        }
        
        return depths.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }
    
    /**
     * Create a copy of this saga definition
     */
    public SagaDefinition copy() {
        return SagaDefinition.builder()
            .sagaType(this.sagaType)
            .name(this.name)
            .description(this.description)
            .version(this.version)
            .steps(new ArrayList<>(this.steps))
            .timeoutMinutes(this.timeoutMinutes)
            .stepTimeoutMinutes(this.stepTimeoutMinutes)
            .maxRetries(this.maxRetries)
            .parallelExecution(this.parallelExecution)
            .compensationStrategy(this.compensationStrategy)
            .properties(new HashMap<>(this.properties))
            .tags(new HashSet<>(this.tags))
            .build();
    }
    
    // Private helper methods
    
    private void validateNoCycles() {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        
        for (SagaStep step : steps) {
            if (!visited.contains(step.getStepId())) {
                if (hasCycle(step.getStepId(), visiting, visited)) {
                    throw new IllegalArgumentException("Circular dependency detected in saga definition");
                }
            }
        }
    }
    
    private boolean hasCycle(String stepId, Set<String> visiting, Set<String> visited) {
        if (visiting.contains(stepId)) {
            return true; // Cycle detected
        }
        
        if (visited.contains(stepId)) {
            return false; // Already processed
        }
        
        visiting.add(stepId);
        
        // Find the step
        SagaStep step = steps.stream()
            .filter(s -> s.getStepId().equals(stepId))
            .findFirst()
            .orElse(null);
        
        if (step != null) {
            for (String dependency : step.getDependencies()) {
                if (hasCycle(dependency, visiting, visited)) {
                    return true;
                }
            }
        }
        
        visiting.remove(stepId);
        visited.add(stepId);
        
        return false;
    }
    
    private int calculateDepth(String stepId, Map<String, Integer> depths) {
        if (depths.containsKey(stepId)) {
            return depths.get(stepId);
        }
        
        // Find the step
        SagaStep step = steps.stream()
            .filter(s -> s.getStepId().equals(stepId))
            .findFirst()
            .orElse(null);
        
        if (step == null || step.getDependencies().isEmpty()) {
            depths.put(stepId, 0);
            return 0;
        }
        
        int maxDependencyDepth = step.getDependencies().stream()
            .mapToInt(depId -> calculateDepth(depId, depths))
            .max()
            .orElse(0);
        
        int depth = maxDependencyDepth + 1;
        depths.put(stepId, depth);
        
        return depth;
    }
}

/**
 * Compensation strategy for saga failures
 */
enum CompensationStrategy {
    /**
     * Compensate steps in reverse order of execution
     */
    REVERSE_ORDER,
    
    /**
     * Compensate all steps in parallel
     */
    PARALLEL,
    
    /**
     * Custom compensation order defined by step priorities
     */
    CUSTOM_ORDER,
    
    /**
     * No automatic compensation (manual intervention required)
     */
    MANUAL
}