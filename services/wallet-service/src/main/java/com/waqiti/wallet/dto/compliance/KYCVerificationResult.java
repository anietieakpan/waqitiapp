package com.waqiti.wallet.dto.compliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KYCVerificationResult {
    private String verificationId;
    private String userId;
    private boolean verified;
    private String riskLevel;
    private Double verificationScore;
    private List<String> flags;
    private Map<String, Object> recommendations;
    private LocalDateTime timestamp;
}