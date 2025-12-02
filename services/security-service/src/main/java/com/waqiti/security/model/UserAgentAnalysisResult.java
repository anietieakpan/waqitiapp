package com.waqiti.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * User Agent Analysis Result
 * Contains the result of user agent behavioral analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAgentAnalysisResult {

    private boolean anomalous;
    private Double confidence;
    private String currentUserAgent;
    private List<String> typicalUserAgents;
    private String deviationType;
    private String analysisNotes;
}
