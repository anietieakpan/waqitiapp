package com.waqiti.analytics.dto.recommendation;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityRecommendation {
    private String id;
    private String type; // MFA, PASSWORD, MONITORING, ALERT
    private String title;
    private String description;
    private String severity; // HIGH, MEDIUM, LOW
    private String actionRequired;
    private Boolean implemented;
}