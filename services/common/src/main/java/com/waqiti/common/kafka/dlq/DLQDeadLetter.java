package com.waqiti.common.kafka.dlq;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Entity representing a permanently failed message in dead letter storage
 */
@Data
@Builder
public class DLQDeadLetter {
    private String messageId;
    private String topic;
    private int partition;
    private long offset;
    private String key;
    private String value;
    private Map<String, String> headers;
    private String failureReason;
    private Instant timestamp;
    private String stackTrace;
    private int totalRetryAttempts;
}
