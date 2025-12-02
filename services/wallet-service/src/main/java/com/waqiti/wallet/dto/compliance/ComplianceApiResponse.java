package com.waqiti.wallet.dto.compliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceApiResponse {
    private String checkId;
    private String status;
    private Double riskScore;
    private String riskLevel;
    private List<String> flags;
    private Map<String, Object> recommendations;
    private String externalCheckId;
    private Map<String, Object> details;
}