package com.waqiti.common.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Security alert DTO for observability
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAlert {
    private String id;
    private String type;
    private com.waqiti.common.enums.ViolationSeverity severity;
    private String description;
    private LocalDateTime timestamp;
    private String source;
    private String status;
}