package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DistributedLockResult {
    private String lockKey;
    private String lockOwner;
    private boolean acquired;
    private String lockValue;
    private long expirationTime;
    private LocalDateTime acquiredAt;
    private String currentLockHolder;
    private String failureReason;
}