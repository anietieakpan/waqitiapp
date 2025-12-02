package com.waqiti.user.service;

import java.time.LocalDateTime;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class OngoingMonitoringResult {
    private boolean monitoringActive;
    private double setupScore;
    private LocalDateTime nextReviewDate;
    private String provider;
    private int alertsConfigured;
    private boolean baselineEstablished;
}
