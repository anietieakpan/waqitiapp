package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Payment service health status model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentServiceHealth {
    
    @Builder.Default
    private Map<ProviderType, Boolean> providerStatuses = new HashMap<>();
    
    private int strategiesCount;
    private boolean validationServiceActive;
    private LocalDateTime lastChecked;
    
    @Builder.Default
    private HealthStatus overallStatus = HealthStatus.UNKNOWN;
    
    public static class PaymentServiceHealthBuilder {
        public PaymentServiceHealthBuilder providerStatus(ProviderType type, boolean healthy) {
            if (this.providerStatuses == null) {
                this.providerStatuses = new HashMap<>();
            }
            this.providerStatuses.put(type, healthy);
            return this;
        }
        
        public PaymentServiceHealth build() {
            if (this.lastChecked == null) {
                this.lastChecked = LocalDateTime.now();
            }
            
            PaymentServiceHealth health = new PaymentServiceHealth(
                this.providerStatuses != null ? this.providerStatuses : new HashMap<>(),
                this.strategiesCount,
                this.validationServiceActive,
                this.lastChecked,
                calculateOverallStatus()
            );
            
            return health;
        }
        
        private HealthStatus calculateOverallStatus() {
            if (this.providerStatuses == null || this.providerStatuses.isEmpty()) {
                return HealthStatus.DOWN;
            }
            
            boolean allHealthy = this.providerStatuses.values().stream().allMatch(Boolean::booleanValue);
            boolean anyHealthy = this.providerStatuses.values().stream().anyMatch(Boolean::booleanValue);
            
            if (allHealthy && this.validationServiceActive) {
                return HealthStatus.UP;
            } else if (anyHealthy) {
                return HealthStatus.DEGRADED;
            } else {
                return HealthStatus.DOWN;
            }
        }
    }
    
    public boolean isHealthy() {
        return overallStatus == HealthStatus.UP;
    }
    
    public int getHealthyProvidersCount() {
        return (int) providerStatuses.values().stream().mapToLong(healthy -> healthy ? 1 : 0).sum();
    }
    
    public int getTotalProvidersCount() {
        return providerStatuses.size();
    }
}