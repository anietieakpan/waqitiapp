package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime; /**
 * Privacy risk identified in PIA
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivacyRisk {
    private String riskId;
    private String riskDescription;
    private String likelihood;
    private String impact;
    private String riskLevel;
    private String mitigationStrategy;
    private String residualRisk;
    private String owner;
    private LocalDateTime targetDate;
}
