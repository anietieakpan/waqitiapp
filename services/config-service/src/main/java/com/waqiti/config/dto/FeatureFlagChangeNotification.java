package com.waqiti.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureFlagChangeNotification {
    private String flagKey;
    private Boolean oldValue;
    private Boolean newValue;
    private String changedBy;
    private String environment;
    private String timestamp;
}
