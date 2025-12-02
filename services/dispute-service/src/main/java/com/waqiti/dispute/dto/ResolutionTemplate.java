package com.waqiti.dispute.dto;

import com.waqiti.dispute.entity.DisputeType;
import com.waqiti.dispute.entity.ResolutionDecision;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolutionTemplate {

    private UUID templateId;
    private String templateName;
    private DisputeType disputeType;
    private ResolutionDecision recommendedDecision;
    private String standardResponse;
    private String requiredEvidence;
    private Integer averageResolutionDays;
    private boolean requiresManagerApproval;

    private String reasonTemplate; // added by aniix from a previous refactoring
}