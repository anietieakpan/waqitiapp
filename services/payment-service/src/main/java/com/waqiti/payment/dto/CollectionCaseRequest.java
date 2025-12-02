package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for creating collection cases
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionCaseRequest {
    private UUID userId;
    private String type;
    private BigDecimal amount;
    private String referenceId;
    private String description;
}