package com.waqiti.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result DTO for bulk update operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkUpdateResultDto {
    private Integer totalRequested;
    private Integer successful;
    private Integer failed;
    private List<String> errors;
}
