package com.waqiti.common.fraud.dto;

import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@Builder
public class UserRiskProfile {
    @NotBlank(message = "User ID is required")
    private String userId;
    private String email; // PRODUCTION FIX: Email for fraud context
    private Integer accountAgeDays; // PRODUCTION FIX: Account age for risk assessment
    private Double riskScore; // PRODUCTION FIX: Risk score
    private String customerSegment; // PRODUCTION FIX: Customer segment

    @Builder.Default
    private List<String> riskFlags = new ArrayList<>(); // PRODUCTION FIX: Risk flags

    private BigDecimal typicalTransactionAmount;

    @Builder.Default
    private Set<Integer> typicalActiveHours = new HashSet<>();

    @Builder.Default
    private List<Location> typicalLocations = new ArrayList<>();

    @Builder.Default
    private Set<String> knownDevices = new HashSet<>();

    private int typicalDailyTransactions;
    private double overallRiskScore;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastUpdated;

    public static UserRiskProfile createDefault(String userId) {
        return UserRiskProfile.builder()
                .userId(userId)
                .typicalTransactionAmount(BigDecimal.valueOf(100))
                .typicalActiveHours(Set.of(9, 10, 11, 12, 13, 14, 15, 16, 17))
                .typicalLocations(new ArrayList<>())
                .knownDevices(new HashSet<>())
                .typicalDailyTransactions(3)
                .overallRiskScore(0.2)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    public void updateFromAnalysis(FraudAnalysisResult result) {
        if (result.getRiskLevel().ordinal() >= FraudRiskLevel.HIGH.ordinal()) {
            this.overallRiskScore = Math.min(1.0, this.overallRiskScore + 0.1);
        }
        this.lastUpdated = LocalDateTime.now();
    }
}