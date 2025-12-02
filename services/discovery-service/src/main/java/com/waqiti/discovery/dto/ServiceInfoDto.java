package com.waqiti.discovery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Service Info DTO
 * Basic information about a registered service
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceInfoDto {
    private String name;
    private Integer instanceCount;
    private Integer healthyInstanceCount;
    private String status;
    private List<InstanceDto> instances;
}
