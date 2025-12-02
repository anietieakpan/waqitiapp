package com.waqiti.compliance.contracts.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Health status of an individual component
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComponentHealth {

    /**
     * Component status
     */
    private HealthStatus status;

    /**
     * Component details
     */
    private String details;

    /**
     * Error message if unhealthy
     */
    private String error;
}
