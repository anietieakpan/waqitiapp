package com.waqiti.analytics.api;

import java.util.List;

@lombok.Data
@lombok.Builder
public class AlertConfigRequest {
    private String alertName;
    private String metric;
    private String condition; // GREATER_THAN, LESS_THAN, EQUALS, etc.
    private Double threshold;
    private List<String> recipients;
}
