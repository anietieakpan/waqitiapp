package com.waqiti.common.messaging.recovery.model;

import lombok.*;
import java.time.LocalDateTime;
import java.util.*;



@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchRecoveryResult {
    private Integer totalEvents;
    private Integer successCount;
    private Integer failureCount;
    private List<RecoveryResult> results;
    private LocalDateTime completedAt;
}