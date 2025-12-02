package com.waqiti.common.security.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Data access security event for audit trail
 */
@Data
@Builder
@Jacksonized
public class DataAccessEvent {
    private String eventId;
    private String userId;
    private String dataType;
    private String operation;
    private List<String> resourceIds;
    private String classification;
    private boolean authorizedAccess;
    private String accessMethod;
    private String ipAddress;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Instant timestamp;
    
    private Map<String, Object> metadata;
    private String complianceFlags;
    private boolean sensitiveData;
    private Integer recordCount;

    public boolean isSensitiveData() {
        return sensitiveData || "SENSITIVE".equals(classification) || "PII".equals(classification);
    }

    public Integer getRecordCount() {
        return recordCount != null ? recordCount : (resourceIds != null ? resourceIds.size() : 0);
    }
}