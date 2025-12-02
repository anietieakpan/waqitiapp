package com.waqiti.dispute.dto;

import com.waqiti.dispute.entity.ResolutionDecision;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveDisputeRequest {

    @NotNull(message = "Resolution decision is required")
    private ResolutionDecision decision;

    @NotNull(message = "Resolution notes are required")
    @Size(min = 10, max = 2000, message = "Resolution notes must be between 10 and 2000 characters")
    private String resolutionNotes;

    private BigDecimal refundAmount;
    private String internalNotes;

    private String resolverId; // aniix - from a previous refactoring exercise
}
