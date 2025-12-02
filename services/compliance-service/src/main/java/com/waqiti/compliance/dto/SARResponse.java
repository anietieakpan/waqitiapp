package com.waqiti.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SARResponse {
    private UUID sarId;
    private String filingNumber;
    private String status; // DRAFT, SUBMITTED, ACKNOWLEDGED, REJECTED
    private UUID subjectUserId;
    private LocalDateTime filedAt;
    private String filedBy;
    private String acknowledgmentNumber;
    private LocalDateTime acknowledgmentDate;
    private String reviewStatus;
    private String regulatoryBody;
}