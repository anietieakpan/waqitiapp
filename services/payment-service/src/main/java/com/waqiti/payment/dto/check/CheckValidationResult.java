package com.waqiti.payment.dto.check;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckValidationResult {
    private boolean valid;
    private List<String> errors;
    private List<String> warnings;
    private Map<String, Object> metadata;
    private double qualityScore;
}