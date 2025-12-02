package com.waqiti.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigurationChangeNotification {
    private String configKey;
    private String oldValue;
    private String newValue;
    private String changedBy;
    private String environment;
    private String serviceName;
    private String timestamp;
}
