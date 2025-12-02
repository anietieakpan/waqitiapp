package com.waqiti.discovery.dto;

import com.waqiti.discovery.domain.DependencyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Service Edge DTO
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceEdgeDto {
    private String source;
    private String target;
    private DependencyType type;
    private Boolean critical;
}
