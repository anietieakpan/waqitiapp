package com.waqiti.analytics.api;

import java.util.List;
import java.util.Map;

@lombok.Data
@lombok.Builder
public class DashboardRequest {
    private String dashboardName;
    private List<Map<String, Object>> widgets;
    private Integer refreshInterval; // in seconds
    private String createdBy;
}
