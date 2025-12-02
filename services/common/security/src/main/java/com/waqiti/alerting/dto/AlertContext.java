package com.waqiti.alerting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertContext {
    private String severity;
    private String title;
    private String description;
    private String source;
}
