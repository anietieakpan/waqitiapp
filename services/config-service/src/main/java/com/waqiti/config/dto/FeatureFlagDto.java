package com.waqiti.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for feature flag
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureFlagDto {
    private UUID id;
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
    private Instant createdAt;
    private Instant lastModified;
    private String createdBy;
    private String modifiedBy;
}
