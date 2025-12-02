package com.waqiti.common.kafka.dlq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a message in the Dead Letter Queue.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DLQRecord {
    
    private String messageKey;
    private String originalTopic;
    private String dlqTopic;
    private Object payload;
    private String errorMessage;
    private String errorType;
    private String errorStackTrace;
    private int retryAttempt;
    private LocalDateTime firstFailedAt;
    private LocalDateTime lastFailedAt;
    private String consumerGroup;
    private Integer partition;
    private Long offset;

    public static DLQRecord fromRecoveryRecord(DLQRecoveryRecord record) {
        return DLQRecord.builder()
            .messageKey(record.getMessageKey())
            .originalTopic(record.getOriginalTopic())
            .dlqTopic(record.getDlqTopic())
            .retryAttempt(record.getRetryAttempts())
            .errorMessage(record.getErrorMessage())
            .build();
    }
}
