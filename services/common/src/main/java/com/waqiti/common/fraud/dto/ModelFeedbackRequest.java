package com.waqiti.common.fraud.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class ModelFeedbackRequest {
    @NotBlank(message = "Transaction ID is required")
    private String transactionId;
    private boolean actualFraud;
    private double actualLoss;
    private String notes;

    @NotBlank(message = "Provided by is required")
    private String providedBy;
}