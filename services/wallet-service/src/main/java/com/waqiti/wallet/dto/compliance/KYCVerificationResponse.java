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
public class KYCVerificationResponse {
    private String verificationId;
    private boolean verified;
    private String riskLevel;
    private Double verificationScore;
    private List<String> flags;
    private Map<String, Object> recommendations;
    private Map<String, Object> verificationDetails;
}