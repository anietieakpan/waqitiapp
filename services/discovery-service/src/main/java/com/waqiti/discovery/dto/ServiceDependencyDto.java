package com.waqiti.discovery.dto;

import com.waqiti.discovery.domain.DependencyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Service Dependency DTO
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDependencyDto {
    private String dependencyName;
    private DependencyType type;
    private Boolean critical;
    private Boolean healthy;
}
