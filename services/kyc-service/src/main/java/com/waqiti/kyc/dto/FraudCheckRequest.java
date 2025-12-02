package com.waqiti.kyc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Fraud Check Request DTO
 *
 * @author Waqiti Platform Team
 * @since 2025-10-31
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckRequest {
    private String userId;
    private String checkType;
    private Map<String, Object> metadata;
}
