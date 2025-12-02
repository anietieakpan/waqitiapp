package com.waqiti.arpayment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ARWalletVisualizationResponse {
    
    private boolean success;
    private String message;
    private String visualizationType;
    private Map<String, Object> visualizationData;
    private List<Map<String, Object>> interactionPoints;
    private Map<String, Object> animations;
    
    public static ARWalletVisualizationResponse error(String message) {
        return ARWalletVisualizationResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}