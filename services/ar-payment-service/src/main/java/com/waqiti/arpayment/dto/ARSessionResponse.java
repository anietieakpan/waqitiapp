package com.waqiti.arpayment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ARSessionResponse {
    
    private UUID sessionId;
    private String sessionToken;
    private String status;
    private String sessionType;
    private LocalDateTime startedAt;
    private Map<String, Object> initialConfiguration;
    private Map<String, Object> supportedFeatures;
    private String message;
    private boolean success;
    
    public static ARSessionResponse error(String message) {
        return ARSessionResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}