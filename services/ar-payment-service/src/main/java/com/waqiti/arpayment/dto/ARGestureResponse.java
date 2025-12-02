package com.waqiti.arpayment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ARGestureResponse {
    
    private String recognizedGesture;
    private String mappedAction;
    private Double accuracy;
    private boolean gestureAccepted;
    private String experienceId;
    private Map<String, Object> actionResult;
    private String nextStep;
    private Map<String, Object> visualFeedback;
    private String message;
    private boolean success;
    
    public static ARGestureResponse error(String message) {
        return ARGestureResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}