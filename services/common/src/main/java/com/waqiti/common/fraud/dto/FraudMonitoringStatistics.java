package com.waqiti.common.fraud.dto;

import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

@Data
@Builder
public class FraudMonitoringStatistics {
    private long totalTransactionsAnalyzed;
    private long fraudDetected;
    private long falsePositives;
    private long truePositives;
    private double averageFraudScore;
    private double averageProcessingTime;
    private long highRiskTransactions;
    private long blockedTransactions;
    private double detectionRate;
    private double precision;
    private double recall;
    private double f1Score;
    private double falsePositiveRate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime periodStart;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime periodEnd;
}