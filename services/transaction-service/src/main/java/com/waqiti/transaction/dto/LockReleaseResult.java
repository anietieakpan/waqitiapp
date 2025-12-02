package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class LockReleaseResult {
    private String lockKey;
    private String lockOwner;
    private boolean released;
    private LocalDateTime releasedAt;
    private String failureReason;
}