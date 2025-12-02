package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * DTO for push notification requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PushNotificationRequest {
    
    @NotBlank
    private String userId;
    
    @NotBlank
    private String title;
    
    @NotBlank
    private String message;
    
    private String template;
    private Map<String, Object> templateData;
    
    @Builder.Default
    private String priority = "NORMAL";
    
    private String deviceToken;
    private String platform; // IOS, ANDROID, WEB
    
    // iOS specific
    private Integer badge;
    private String sound;
    private String category;
    
    // Android specific
    private String icon;
    private String color;
    private String channelId;
    
    // Action buttons
    private Map<String, String> actions;
    
    // Deep linking
    private String deepLink;
    private Map<String, Object> payload;
    
    // Scheduling
    private String scheduledAt;
    private Integer timeToLive;
    
    private Map<String, String> metadata;
}