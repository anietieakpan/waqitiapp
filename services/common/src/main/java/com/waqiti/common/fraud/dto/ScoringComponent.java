package com.waqiti.common.fraud.dto;

import lombok.Builder;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Data
@Builder
public class ScoringComponent {
    @NotBlank(message = "Component name is required")
    private String name;
    private double score;
    private double weight;
    private double confidence;
    private String description;
    private List<String> features;
    private List<String> factors;
}