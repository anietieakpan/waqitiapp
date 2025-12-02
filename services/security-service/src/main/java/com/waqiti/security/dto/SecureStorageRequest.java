package com.waqiti.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for secure storage operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecureStorageRequest {

    @NotBlank(message = "Key is required")
    @Size(max = 128, message = "Key must not exceed 128 characters")
    private String key;

    private String value;

    private StorageOptions options;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StorageOptions {
        private Boolean secure;
        private String sameSite;
        private Integer maxAge;
        private String path;
    }
}
