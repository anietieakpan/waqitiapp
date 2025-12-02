package com.waqiti.account.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@lombok.Data
@lombok.Builder
public class PepScreeningResult {
    private UUID userId;
    private boolean isPep;
    private String riskLevel;
    private List<String> pepCategories;
    private LocalDateTime screenedAt;
}
