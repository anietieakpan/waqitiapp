package com.waqiti.user.service;

import java.util.List;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class OngoingMonitoringRequest {
    private String userId;
    private String monitoringType;
    private String monitoringFrequency;
    private List<String> monitoringCategories;
    private java.time.Instant requestTimestamp;
}
