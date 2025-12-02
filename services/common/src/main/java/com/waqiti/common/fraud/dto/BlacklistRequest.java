package com.waqiti.common.fraud.dto;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

@Data
public class BlacklistRequest {
    @NotBlank(message = "Entity type is required")
    private String entityType; // USER, IP, MERCHANT, DEVICE

    @NotBlank(message = "Entity value is required")
    private String entityValue;

    @NotBlank(message = "Added by is required")
    private String addedBy;

    @NotBlank(message = "Reason is required")
    private String reason;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;
}