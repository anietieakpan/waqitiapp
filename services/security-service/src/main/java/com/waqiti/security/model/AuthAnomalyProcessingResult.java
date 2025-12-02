package com.waqiti.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Authentication Anomaly Processing Result
 * Contains the complete result of processing authentication anomalies
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthAnomalyProcessingResult {

    private String eventId;
    private String userId;
    private AnomalyDetectionResult anomalyResult;
    private Instant processingStartTime;
    private Instant processingEndTime;
    private Long processingTimeMs;
    private AuthenticationEvent savedEvent;
    private List<AuthAnomaly> savedAnomalies;
    private List<String> appliedControls;
    private DeviceAnalysisResult deviceAnalysis;
    private ProcessingStatus status;
    private String errorMessage;
    private String processingNotes;
}
