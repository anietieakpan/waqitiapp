package com.waqiti.common.encryption;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Information about a specific key version
 */
@Data
@Builder
public class KeyVersionInfo {
    private int version;
    private LocalDateTime createdAt;
    private AdvancedEncryptionService.KeyStatus status;
    private String algorithm;
    private boolean isActive;
}