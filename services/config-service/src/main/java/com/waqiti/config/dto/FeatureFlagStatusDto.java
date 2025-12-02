package com.waqiti.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for feature flag status
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureFlagStatusDto {
    private String flagName;
    private Boolean enabled;
    private String userId;
    private String reason;
}
