package com.waqiti.user.service;

import java.math.BigDecimal;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class SignatureExtractionResult {
    private boolean successful;
    private String signatureImage;
    private BigDecimal qualityScore;
    private BigDecimal confidenceScore;
    private String errorMessage;
}
