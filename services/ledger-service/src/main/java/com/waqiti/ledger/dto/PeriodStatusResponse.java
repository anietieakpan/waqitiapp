package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodStatusResponse {
    private LocalDate currentPeriodStart;
    private LocalDate currentPeriodEnd;
    private UUID currentPeriodId;
    private String currentPeriodStatus;
    private boolean periodClosed;
    private boolean trialBalanced;
    private boolean canClosePeriod;
    private List<String> blockingIssues;
    private LocalDate lastCloseDate;
    private LocalDateTime checkedAt;
    private List<PeriodInfo> openPeriods;
    private List<PeriodInfo> recentlyClosedPeriods;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PeriodInfo {
    private UUID periodId;
    private String periodCode;
    private String periodName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private LocalDateTime lastModified;
}