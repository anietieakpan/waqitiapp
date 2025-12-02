package com.waqiti.analytics.api;

import java.util.Map;

@lombok.Data
@lombok.Builder
public class ExportRequest {
    private String analyticsType;
    private Map<String, Object> parameters;
    private String format; // CSV, JSON, PDF, EXCEL
}
