package com.waqiti.chaos.orchestrator;

import lombok.Data;

import java.util.Map;

@Data
public class ValidationResult {
    private boolean systemHealthy;
    private double healthScore;
    private double cpuUsage;
    private double memoryUsage;
    private double diskUsage;
    private Map<String, Boolean> serviceHealth;
    private String error;
    
    public boolean isSystemHealthy() {
        return systemHealthy;
    }
}