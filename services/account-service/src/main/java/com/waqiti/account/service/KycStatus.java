package com.waqiti.account.service;

import com.waqiti.account.entity.Account;

import java.time.LocalDateTime;
import java.util.UUID;

@lombok.Data
@lombok.Builder
public class KycStatus {
    private UUID userId;
    private Account.KycLevel currentLevel;
    private VerificationStatus verificationStatus;
    private DocumentStatus documentStatus;
    private ComplianceStatus complianceStatus;
    private LocalDateTime lastVerifiedAt;
    private LocalDateTime nextReviewDate;
    private LocalDateTime expiresAt;
}
