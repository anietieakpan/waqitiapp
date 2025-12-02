package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for external check processor
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalCheckRequest {
    private UUID depositId;
    private String frontImageUrl;
    private String backImageUrl;
    private BigDecimal amount;
    private Map<String, String> micrData;
    private Map<String, String> accountDetails;
}