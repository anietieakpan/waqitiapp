package com.waqiti.analytics.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@lombok.Data
@lombok.Builder
public class CustomReportRequest {
    private String reportName;
    private List<String> metrics;
    private List<String> dimensions;
    private Map<String, Object> filters;
    private LocalDate startDate;
    private LocalDate endDate;
}
