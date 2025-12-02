package com.waqiti.arpayment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ARSessionStatusResponse {
    
    private UUID sessionId;
    private String sessionToken;
    private String status;
    private String sessionType;
    private boolean isActive;
    private boolean canProcessPayment;
    private Double arQualityScore;
    private String trackingQuality;
    private Integer frameRate;
    private Integer interactionCount;
    private Integer gestureCount;
    private BigDecimal paymentAmount;
    private String currency;
    private UUID recipientId;
    private String recipientName;
    private LocalDateTime startedAt;
    private LocalDateTime lastActiveAt;
    private Long durationSeconds;
    private List<Map<String, Object>> activeOverlays;
    private Integer detectedSurfaces;
    private Integer recognizedObjects;
}