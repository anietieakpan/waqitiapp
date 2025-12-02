package com.waqiti.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AML Screening Result DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmlScreeningResult {
    private String transactionId;
    private boolean passed;
    private String riskLevel;
    private List<String> flags;
    private Map<String, Object> details;
    private LocalDateTime screenedAt;
}
