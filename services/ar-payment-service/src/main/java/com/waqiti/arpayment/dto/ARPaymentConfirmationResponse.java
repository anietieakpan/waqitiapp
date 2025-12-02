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
public class ARPaymentConfirmationResponse {
    
    private UUID paymentId;
    private String transactionId;
    private String status;
    private BigDecimal amount;
    private String currency;
    private UUID recipientId;
    private String recipientName;
    private LocalDateTime completedAt;
    private Map<String, Object> visualizationData;
    private Map<String, Object> gamificationRewards;
    private Integer pointsEarned;
    private String achievementUnlocked;
    private String arScreenshotUrl;
    private boolean shareToSocialFeed;
    private String message;
    private boolean success;
    
    public static ARPaymentConfirmationResponse error(String message) {
        return ARPaymentConfirmationResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}