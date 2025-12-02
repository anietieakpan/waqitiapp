package com.waqiti.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for secure storage operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecureStorageResponse {
    private boolean success;
    private String message;
    private String value;
    private String token;
}
