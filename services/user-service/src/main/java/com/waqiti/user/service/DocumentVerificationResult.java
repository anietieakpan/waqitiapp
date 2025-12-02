package com.waqiti.user.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class DocumentVerificationResult {
    private boolean authentic;
    private BigDecimal overallScore;
    private BigDecimal qualityScore;
    private BigDecimal authenticityScore;
    private boolean extractedDataMatch;
    private String provider;
    private boolean textExtractionSuccessful;
    private BigDecimal ocrConfidence;
    private List<String> fraudIndicators;
    private boolean requiresManualReview;
    private Map<String, String> extractedData;
}
