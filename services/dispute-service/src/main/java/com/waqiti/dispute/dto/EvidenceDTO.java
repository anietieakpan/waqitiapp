package com.waqiti.dispute.dto;

import com.waqiti.dispute.entity.EvidenceType;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceDTO {

    private UUID evidenceId;
    private UUID disputeId;
    private EvidenceType evidenceType;
    private String description;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String submittedBy;
    private LocalDateTime submittedAt;
    private boolean verified;

    private String verificationStatus; // added by aniix - from a previous refactoring exercise
}
