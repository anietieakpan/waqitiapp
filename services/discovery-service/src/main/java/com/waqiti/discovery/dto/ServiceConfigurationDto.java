package com.waqiti.discovery.dto;

import com.waqiti.discovery.domain.LoadBalancerStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Service Configuration DTO
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceConfigurationDto {
    private String serviceName;
    private LoadBalancerStrategy loadBalancerStrategy;
    private Boolean healthCheckEnabled;
    private Integer healthCheckInterval;
    private Integer timeout;
    private Integer retryAttempts;
    private Boolean circuitBreakerEnabled;
    private Map<String, Object> metadata;
}
