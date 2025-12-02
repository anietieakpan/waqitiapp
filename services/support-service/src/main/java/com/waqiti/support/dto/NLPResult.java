package com.waqiti.support.dto;

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
public class NLPResult {
    private String intent;
    private double confidence;
    private Map<String, String> entities;
    private float[] embedding;
    private List<String> extractedTags;
    private String sentiment;
    private double sentimentScore;
    private List<String> keywords;
    private String processedText;
}