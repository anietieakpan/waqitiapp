package com.waqiti.discovery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Registry Export DTO
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistryExportDto {
    private Instant exportTimestamp;
    private String format;
    private List<ServiceInfoDto> services;
    private RegistryStatisticsDto statistics;
    private String version;
}
