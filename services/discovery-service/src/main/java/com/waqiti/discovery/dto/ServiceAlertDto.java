package com.waqiti.discovery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Service Alert DTO
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceAlertDto {
    private String alertId;
    private String serviceName;
    private String severity;
    private String message;
    private String type;
    private Instant timestamp;
    private Boolean acknowledged;
    private String acknowledgedBy;
}
