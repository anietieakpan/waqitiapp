package com.waqiti.analytics.api;

import java.util.Map;

@lombok.Data
@lombok.Builder
public class StartStreamRequest {
    private String streamName;
    private Map<String, Object> configuration;
}
