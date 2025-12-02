package com.waqiti.discovery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Service Dependency Graph DTO
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDependencyGraphDto {
    private List<ServiceNodeDto> nodes;
    private List<ServiceEdgeDto> edges;
    private List<List<String>> circularDependencies;
    private List<String> isolatedServices;
}
