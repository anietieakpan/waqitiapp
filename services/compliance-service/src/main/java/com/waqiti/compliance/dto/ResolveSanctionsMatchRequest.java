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
public class ResolveSanctionsMatchRequest {
    
    @NotNull
    private String resolution; // FALSE_POSITIVE, CONFIRMED_MATCH, NEEDS_ESCALATION
    
    @NotNull
    private String resolvedBy;
    
    private String notes;
    private String mitigationMeasures;
    private Boolean blockEntity;
    private Boolean reportToAuthorities;
}