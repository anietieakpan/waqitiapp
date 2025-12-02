package com.waqiti.account.service;

import com.waqiti.account.entity.Account;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@lombok.Data
@lombok.Builder
public class KycVerificationHistory {
    private UUID historyId;
    private UUID userId;
    private Account.KycLevel level;
    private VerificationStatus status;
    private LocalDateTime verifiedAt;
    private String verifiedBy;
    private Map<String, Object> details;
}
