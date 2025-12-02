package com.waqiti.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for validation results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResultDto {
    private Boolean valid;
    private List<String> errors;
    private List<String> warnings;
    private String message;
}
