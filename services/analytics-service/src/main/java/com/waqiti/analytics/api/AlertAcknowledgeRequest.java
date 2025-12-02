package com.waqiti.analytics.api;

@lombok.Data
@lombok.Builder
public class AlertAcknowledgeRequest {
    private String acknowledgedBy;
    private String notes;
}
