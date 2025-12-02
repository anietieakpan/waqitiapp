package com.waqiti.chaos.core;

public interface ChaosExperiment {
    
    /**
     * Get the name of the chaos experiment
     */
    String getName();
    
    /**
     * Get detailed description of what this experiment tests
     */
    String getDescription();
    
    /**
     * Execute the chaos experiment
     * @return Result of the experiment including metrics and outcomes
     */
    ChaosResult execute();
    
    /**
     * Cleanup any resources or restore system state after experiment
     */
    void cleanup();
    
    /**
     * Check if experiment can be safely run in current environment
     */
    default boolean canRun() {
        return true;
    }
    
    /**
     * Get estimated duration of the experiment in seconds
     */
    default int getEstimatedDurationSeconds() {
        return 300; // 5 minutes default
    }
}