package com.waqiti.analytics.api;

import java.util.Map;

@lombok.Data
@lombok.Builder
public class CustomSegmentRequest {
    private String segmentName;
    private Map<String, Object> criteria;
    private String description;
}
