package com.waqiti.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Timing Analysis Result
 * Contains the result of authentication timing behavioral analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimingAnalysisResult {

    private boolean anomalous;
    private Double confidence;
    private Long completionTimeMs;
    private Map<String, Long> typicalRange;
    private Double deviation;
    private String deviationType;
}
