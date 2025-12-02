package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Predicted event model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictedEvent {
    private String eventType;
    private String description;
    private LocalDate predictedDate;
    private BigDecimal predictedAmount;
    private BigDecimal confidence;
    private String category;
}