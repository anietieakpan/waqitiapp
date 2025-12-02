package com.waqiti.common.messaging.recovery;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Result of a message recovery attempt
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryResult {
    private String messageId;
    private boolean success;
    private String originalTopic;
    private LocalDateTime recoveredAt;
    private LocalDateTime failedAt;
    private String failureReason;
    private int attemptNumber;
    private Map<String, Object> metadata;
}