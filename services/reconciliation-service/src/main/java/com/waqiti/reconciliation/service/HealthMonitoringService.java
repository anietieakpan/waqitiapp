package com.waqiti.reconciliation.service;

import com.waqiti.reconciliation.domain.SystemHealth;
import com.waqiti.reconciliation.domain.Alert;

import java.util.List;

public interface HealthMonitoringService {
    
    /**
     * Get current system health status
     */
    SystemHealth getCurrentHealth();
    
    /**
     * Get active alerts
     */
    List<Alert> getActiveAlerts();
    
    /**
     * Check specific service health
     */
    SystemHealth.HealthCheck checkServiceHealth(String serviceName);
    
    /**
     * Generate health alerts based on current metrics
     */
    List<Alert> generateHealthAlerts(SystemHealth health);
}