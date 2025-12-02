package com.waqiti.support.dto;

import com.waqiti.support.domain.TicketCategory;
import com.waqiti.support.domain.TicketSubCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategorizationConfidence {
    
    private TicketCategory category;
    private TicketSubCategory subCategory;
    
    private double confidence;
    private double probability;
    
    private List<String> supportingKeywords;
    private List<String> contradictingKeywords;
    
    private String explanation;
    private int historicalSupport; // Number of similar tickets in this category
    
    private boolean isHighConfidence;
    private String modelSource; // Which model/rule contributed to this
}