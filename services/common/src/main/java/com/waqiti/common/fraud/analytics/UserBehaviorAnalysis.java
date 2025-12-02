package com.waqiti.common.fraud.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime; /**
 * User behavior analysis result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBehaviorAnalysis {
    private String userId;
    private int analysisWindow;
    private int transactionCount;
    private BehaviorMetrics behaviorMetrics;
    private BehaviorPatterns behaviorPatterns;
    private RiskProfile riskProfile;
    private LocalDateTime analysisTimestamp;
    private String error;
    
    public static UserBehaviorAnalysis noData(String userId) {
        return UserBehaviorAnalysis.builder()
                .userId(userId)
                .transactionCount(0)
                .analysisTimestamp(LocalDateTime.now())
                .build();
    }
    
    public static UserBehaviorAnalysis error(String userId, String error) {
        return UserBehaviorAnalysis.builder()
                .userId(userId)
                .error(error)
                .analysisTimestamp(LocalDateTime.now())
                .build();
    }
}
