package com.waqiti.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandoffAnalysis {
    private boolean shouldHandoff;
    private String reason;
    private double frustrationScore;
    private String conversationSummary;
    private String suggestedAgent;
    private String priority;
    private int conversationLength;
    private boolean explicitRequest;
    private boolean complexIssue;
}