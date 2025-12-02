package com.waqiti.common.security.awareness.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import com.waqiti.common.security.awareness.validation.ValidQuarter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcknowledgmentRequest {
    @NotBlank(message = "Signature is required")
    private String signature;

    @NotBlank(message = "IP address is required")
    private String ipAddress;
}