package com.waqiti.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleanupResponse {
    private int tokensRemoved;
    private int subscriptionsRemoved;
    private int logsRemoved;
    private LocalDateTime timestamp;
    private long durationMs;
    private String status;
}