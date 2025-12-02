package com.waqiti.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Browser Configuration Analysis Result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrowserConfigAnalysisResult {

    private boolean anomalous;
    private Double confidence;
    private BrowserConfig currentConfig;
    private List<BrowserConfig> typicalConfigs;
    private List<String> differences;
    private String riskAssessment;
}
