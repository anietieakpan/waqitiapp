package com.waqiti.common.fraud.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class AlertAcknowledgmentRequest {
    @NotBlank(message = "Acknowledged by is required")
    private String acknowledgedBy;
    private String notes;
}