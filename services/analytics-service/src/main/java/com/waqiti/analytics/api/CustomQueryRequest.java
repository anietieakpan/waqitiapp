package com.waqiti.analytics.api;

import java.util.Map;

@lombok.Data
@lombok.Builder
public class CustomQueryRequest {
    private String queryName;
    private String query;
    private Map<String, Object> parameters;
    private String outputFormat;
}
