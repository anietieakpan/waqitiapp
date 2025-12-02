package com.waqiti.discovery.dto;

import com.waqiti.discovery.domain.ServiceHealthStatus;
import com.waqiti.discovery.domain.ServiceMetrics;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Service Details DTO
 * Detailed information about a service
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDetailsDto {
    private String serviceName;
    private List<InstanceDto> instances;
    private ServiceHealthStatus healthStatus;
    private ServiceMetrics metrics;
    private List<ServiceDependencyDto> dependencies;
    private Integer totalInstances;
    private Integer healthyInstances;
}
