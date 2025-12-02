package com.waqiti.common.encryption;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * HSM health status information
 */
@Data
@Builder
public class HsmHealthStatus {
    private boolean available;
    private boolean healthy;
    private int slotId;
    private LocalDateTime lastHealthCheck;
    private String lastError;
    private int keyCacheSize;
    private boolean failoverEnabled;
}