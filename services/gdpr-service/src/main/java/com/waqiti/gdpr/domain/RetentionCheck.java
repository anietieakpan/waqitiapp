package com.waqiti.gdpr.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetentionCheck {
    private boolean hasLegalHold;
    private String holdReason;
    private boolean requiresTransactionRetention;
    private List<String> retainedCategories = new ArrayList<>();
}