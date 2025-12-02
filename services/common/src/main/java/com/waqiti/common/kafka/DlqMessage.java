package com.waqiti.common.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String messageId;
    private String originalTopic;
    private Integer originalPartition;
    private Long originalOffset;
    private String originalKey;
    private Object originalValue;
    private Map<String, String> originalHeaders;
    
    private String errorMessage;
    private String errorClass;
    private String errorStackTrace;
    private String errorType;
    private String severity;
    private boolean retryable;
    
    private int retryCount;
    private int maxRetries;
    private Instant lastRetryAt;
    
    private String context;
    private Map<String, Object> metadata;
    
    private Instant timestamp;
    private Instant expiresAt;
    
    private boolean permanentFailure;
    private Instant permanentFailureAt;
}