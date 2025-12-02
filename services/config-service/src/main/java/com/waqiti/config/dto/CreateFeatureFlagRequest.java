package com.waqiti.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Request DTO for creating feature flag
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFeatureFlagRequest {

    @NotBlank(message = "Feature flag name is required")
    private String name;

    private String description;

    private Boolean enabled;

    private String environment;

    private String rules;

    private String targetUsers;

    private String targetGroups;

    private Integer rolloutPercentage;

    private Instant startDate;

    private Instant endDate;
}
