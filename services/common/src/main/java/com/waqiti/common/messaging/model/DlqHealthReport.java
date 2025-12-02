package com.waqiti.common.messaging.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Comprehensive DLQ health and statistics report
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqHealthReport {
    
    private int totalDlqTopics;
    private int totalMessages;
    private int totalQuarantinedMessages;
    private Map<String, DlqTopicStats> topicStats;
    private Map<String, DlqFailureStats> failureStats;
    private LocalDateTime generatedAt;
    
    // Health indicators
    private HealthStatus overallHealth;
    private Set<String> healthWarnings;
    private Set<String> criticalIssues;
    
    public double getQuarantineRate() {
        return totalMessages > 0 ? (double) totalQuarantinedMessages / totalMessages * 100 : 0;
    }
    
    public enum HealthStatus {
        HEALTHY, WARNING, CRITICAL, UNKNOWN
    }
}