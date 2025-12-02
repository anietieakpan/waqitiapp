package com.waqiti.discovery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Service Topology DTO
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceTopologyDto {
    private List<ServiceNodeDto> services;
    private List<ServiceEdgeDto> connections;
    private Map<String, List<String>> clusters;
    private List<String> criticalPath;
}
