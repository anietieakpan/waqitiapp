package com.waqiti.common.fraud.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime; /**
 * Context summary
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextSummary {
    private String contextId;
    private String transactionId;
    private String userId;
    private double overallRiskScore;
    private int violationCount;
    private int alertCount;
    private FraudContextManager.EnrichmentLevel enrichmentLevel;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
    private FraudContextManager.ContextStatus status;
}
