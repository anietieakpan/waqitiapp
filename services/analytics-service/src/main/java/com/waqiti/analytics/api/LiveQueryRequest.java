package com.waqiti.analytics.api;

import java.util.Map;

@lombok.Data
@lombok.Builder
public class LiveQueryRequest {
    private String queryName;
    private Map<String, Object> parameters;
    private Integer timeWindow; // in minutes
}
