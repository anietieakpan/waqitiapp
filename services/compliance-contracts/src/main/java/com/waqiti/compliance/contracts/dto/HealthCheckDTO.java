package com.waqiti.compliance.contracts.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Health check response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthCheckDTO {

    /**
     * Overall health status
     */
    private HealthStatus status;

    /**
     * Service name
     */
    private String serviceName;

    /**
     * Service version
     */
    private String version;

    /**
     * Uptime in seconds
     */
    private Long uptimeSeconds;

    /**
     * Individual component health
     */
    private Map<String, ComponentHealth> components;

    /**
     * Additional details
     */
    private Map<String, Object> details;

    /**
     * Timestamp
     */
    private String timestamp;
}
