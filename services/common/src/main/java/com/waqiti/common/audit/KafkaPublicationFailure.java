package com.waqiti.common.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Record representing a Kafka publication failure for audit events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KafkaPublicationFailure {
    
    private String failureId;
    private String eventId;
    private String topic;
    private String partitionKey;
    private String eventData;
    private String topicName;
    private String messageKey;
    private String messagePayload;
    private String errorMessage;
    private String errorCode;
    private java.time.Instant failureTimestamp;
    private LocalDateTime failedAt;
    private LocalDateTime retryAt;
    private int retryCount;
    private int maxRetries;
    private String partitionInfo;
    private Map<String, Object> messageHeaders;
    private boolean resolved;
    private String resolutionMethod;
    private LocalDateTime resolvedAt;
}