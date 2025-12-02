package com.waqiti.common.security.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Threat intelligence data
 */
@Data
@Builder
@Jacksonized
public class ThreatIntelligence {
    private String threatId;
    private String threatType;
    private String severity;
    private String source;
    private List<String> indicators;
    private Map<String, Object> attributes;
    private String description;
    private List<String> mitigations;
    private double confidence;
    private Instant firstSeen;
    private Instant lastSeen;
    private boolean active;
}