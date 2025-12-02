package com.waqiti.common.fraud.dto;

import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

@Data
@Builder
public class FraudMonitoringHealth {
    private HealthStatus overallStatus;
    private HealthStatus mlModelStatus;
    private HealthStatus alertServiceStatus;
    private HealthStatus kafkaStatus;
    private HealthStatus databaseStatus;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastHealthCheck;
    private String message;
}