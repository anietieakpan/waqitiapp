package com.waqiti.account.service;

import java.time.LocalDateTime;
import java.util.UUID;

@lombok.Data
@lombok.Builder
public class ReVerificationRequest {
    private UUID userId;
    private String reason;
    private String triggeredBy;
    private LocalDateTime timestamp;
}
