package com.waqiti.arpayment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ARPaymentExperienceDto {
    
    private String experienceId;
    private String experienceType;
    private String status;
    private String paymentMethod;
    private BigDecimal amount;
    private String currency;
    private UUID recipientId;
    private String recipientName;
    private UUID merchantId;
    private String merchantName;
    private Map<String, Object> arVisualizationData;
    private String confirmationMethod;
    private LocalDateTime confirmationTimestamp;
    private Double securityScore;
    private Boolean faceIdVerified;
    private Double gestureAccuracy;
    private Integer pointsEarned;
    private String achievementUnlocked;
    private Boolean isSharedToFeed;
    private String arScreenshotUrl;
    private Long interactionDurationSeconds;
    private UUID paymentId;
    private String transactionId;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}