package com.waqiti.tax.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IRSRefundStatus {
    private RefundStatusType status;
    private String message;
    private LocalDate estimatedDate;
    private LocalDateTime lastUpdated;
    private String trackingId;
}
