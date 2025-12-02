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
public class ARSessionEndResponse {
    
    private UUID sessionId;
    private boolean success;
    private String message;
    private LocalDateTime endedAt;
    private Long totalDuration;
    private Integer totalInteractions;
    private Integer totalGestures;
    private Map<String, Object> sessionStatistics;
    
    public static ARSessionEndResponse error(String message) {
        return ARSessionEndResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}