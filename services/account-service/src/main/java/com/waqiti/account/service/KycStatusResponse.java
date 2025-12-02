package com.waqiti.account.service;

import com.waqiti.account.entity.Account;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@lombok.Data
@lombok.Builder
public class KycStatusResponse {
    private UUID userId;
    private Account.KycLevel currentLevel;
    private VerificationStatus status;
    private LocalDateTime lastVerifiedAt;
    private LocalDateTime nextReviewDate;
    private Map<String, Object> metadata;
    
    public boolean isVerifiedAtLevel(Account.KycLevel level) {
        return currentLevel != null && currentLevel.ordinal() >= level.ordinal() &&
               status == VerificationStatus.VERIFIED;
    }
}
