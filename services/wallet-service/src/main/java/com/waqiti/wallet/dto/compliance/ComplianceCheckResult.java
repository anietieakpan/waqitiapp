package com.waqiti.wallet.dto.compliance;

import com.waqiti.wallet.entity.ComplianceCheck.CheckType;
import com.waqiti.wallet.entity.ComplianceCheck.CheckStatus;
import com.waqiti.wallet.entity.ComplianceCheck.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceCheckResult {
    private String checkId;
    private String walletId;
    private String userId;
    private CheckType checkType;
    private CheckStatus status;
    private RiskLevel riskLevel;
    private Double riskScore;
    private List<String> flags;
    private boolean passed;
    private boolean blocked;
    private boolean requiresReview;
    private boolean highRisk;
    private LocalDateTime timestamp;
    private LocalDateTime completedAt;
    private boolean valid;
}