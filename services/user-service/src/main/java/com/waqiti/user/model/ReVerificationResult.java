package com.waqiti.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReVerificationResult {
    private boolean reVerificationComplete;
    private double overallScore;
    private int verificationsUpdated;
    private int verificationsRequired;
    private LocalDateTime nextReVerificationDate;
    private boolean complianceUpdated;
}
