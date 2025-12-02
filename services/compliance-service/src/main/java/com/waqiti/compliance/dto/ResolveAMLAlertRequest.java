package com.waqiti.compliance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveAMLAlertRequest {
    
    @NotNull
    private String resolution; // FALSE_POSITIVE, TRUE_POSITIVE, ESCALATED
    
    @NotNull
    private String notes;
    
    private String action; // APPROVE, REJECT, ESCALATE
    private String riskMitigation;
    private Boolean filesSAR;
}