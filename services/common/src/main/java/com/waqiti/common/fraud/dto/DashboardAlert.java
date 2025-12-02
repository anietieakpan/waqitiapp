package com.waqiti.common.fraud.dto;

import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.waqiti.common.fraud.model.AlertLevel;
import java.time.LocalDateTime;

@Data
@Builder
public class DashboardAlert {
    private String alertId;
    private AlertLevel level;
    private String title;
    private String description;
    private String transactionId;
    private String userId;
    private double fraudScore;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
}