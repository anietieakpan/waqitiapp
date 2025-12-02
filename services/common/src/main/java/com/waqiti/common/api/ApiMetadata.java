package com.waqiti.common.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * API Metadata for additional response information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiMetadata {
    private String version;
    private String requestId;
    private long processingTime;
    private Map<String, Object> additionalInfo;
    private String serverInstance;
    private boolean cached;
    private Instant timestamp;
}