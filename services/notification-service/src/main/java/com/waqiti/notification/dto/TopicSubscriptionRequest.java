package com.waqiti.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicSubscriptionRequest {
    
    @NotBlank(message = "Device ID is required")
    private String deviceId;
}