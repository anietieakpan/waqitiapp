package com.waqiti.analytics.api;

import java.util.Map;

@lombok.Data
@lombok.Builder
public class EventTrackingRequest {
    private String eventType;
    private String userId;
    private Map<String, Object> properties;
    private Long timestamp;
}
