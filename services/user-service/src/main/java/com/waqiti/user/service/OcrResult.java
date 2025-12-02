package com.waqiti.user.service;

import java.math.BigDecimal;
import java.util.Map;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class OcrResult {
    private boolean successful;
    private Map<String, String> extractedData;
    private BigDecimal confidenceScore;
    private String errorMessage;
}
