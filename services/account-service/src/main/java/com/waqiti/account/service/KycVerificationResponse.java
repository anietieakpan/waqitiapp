package com.waqiti.account.service;

import com.waqiti.account.entity.Account;

import java.time.LocalDateTime;
import java.util.UUID;

@lombok.Data
@lombok.Builder
public class KycVerificationResponse {
    private UUID requestId;
    private UUID userId;
    private VerificationStatus status;
    private Account.KycLevel verifiedLevel;
    private String failureReason;
    private LocalDateTime completedAt;
    
    public boolean isSuccessful() {
        return status == VerificationStatus.VERIFIED;
    }
}
