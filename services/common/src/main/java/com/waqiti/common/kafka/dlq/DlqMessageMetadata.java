package com.waqiti.common.kafka.dlq;

import lombok.Data;

/**
 * Metadata for DLQ messages
 * Contains information about the original message and failure details
 */
@Data
public class DlqMessageMetadata {
    private String key;
    private String originalTopic;
    private Integer originalPartition;
    private Long originalOffset;
    private Long originalTimestamp;
    private String dlqTopic;
    private String exceptionMessage;
    private String exceptionStacktrace;
    private int retryCount;
}
