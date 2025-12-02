package com.waqiti.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result DTO for configuration import
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigImportResultDto {
    private Integer totalRequested;
    private Integer imported;
    private Integer updated;
    private Integer skipped;
    private Integer failed;
    private List<String> errors;
}
