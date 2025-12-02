package com.waqiti.account.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@lombok.Data
@lombok.Builder
public class SanctionsCheckResponse {
    private UUID userId;
    private boolean onSanctionsList;
    private List<String> matchedLists;
    private LocalDateTime checkedAt;
}
