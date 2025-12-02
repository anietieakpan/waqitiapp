package com.waqiti.arpayment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ARSpatialPaymentResponse {
    
    private String experienceId;
    private String spatialPaymentId;
    private Map<String, Double> dropLocation;
    private String visualizationUrl;
    private Map<String, Object> arAnchorData;
    private LocalDateTime expiresAt;
    private String shareableLink;
    private Map<String, Object> animationData;
    private String status;
    private String message;
    private boolean success;
    
    public static ARSpatialPaymentResponse error(String message) {
        return ARSpatialPaymentResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}