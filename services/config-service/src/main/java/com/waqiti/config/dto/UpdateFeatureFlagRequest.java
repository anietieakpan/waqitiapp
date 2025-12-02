package com.waqiti.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Request DTO for updating feature flag
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFeatureFlagRequest {
    private String description;
    private Boolean enabled;
    private String rules;
    private String targetUsers;
    private String targetGroups;
    private Integer rolloutPercentage;
    private Instant startDate;
    private Instant endDate;
}
