package com.waqiti.account.service;

import com.waqiti.account.entity.Account;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@lombok.Data
@lombok.Builder
public class KycVerificationRequest {
    private UUID requestId;
    private UUID userId;
    private Account.KycLevel requestedLevel;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata;
}
