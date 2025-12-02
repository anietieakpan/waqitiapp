package com.waqiti.discovery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Service Node DTO
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceNodeDto {
    private String name;
    private String type;
    private Boolean healthy;
    private Integer instanceCount;
}
